package maple.underground_item.professions;

import maple.underground_state.UndergroundState;
import maple.underground_state.professions.*;
import maple.underground_state.skills.SkillType;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Events consume the gather snapshot; probability and damage accounting never read later rare gear. */
public final class ProfessionEvents implements Listener {
    private final JavaPlugin plugin;
    private final UndergroundState core;
    private final ProfessionCatalog catalog;
    private final ProfessionEquipment equipment;
    private final Map<UUID, Encounter> active=new HashMap<>();
    private final Map<UUID, Encounter> mobs=new HashMap<>();
    private final Map<EntityDamageEvent,Hit> hits=new IdentityHashMap<>();
    private final BukkitTask ticker;
    private static final class Encounter {
        UUID owner;SkillType skill;Husk entity;boolean elite,aoe;double rare,health,total,contribution;
        long start;int absent,ticks;
    }
    private record Hit(Encounter encounter,Player attacker,double damage) {}
    public ProfessionEvents(JavaPlugin plugin,UndergroundState core,ProfessionCatalog catalog,ProfessionEquipment equipment) {
        this.plugin=plugin;this.core=core;this.catalog=catalog;this.equipment=equipment;
        ticker=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20,20);
        // Remove tagged leftovers from a previous crashed process; they never grant rewards.
        for (World world:Bukkit.getWorlds()) for(Entity entity:world.getEntities()) if(entity.getPersistentDataContainer().has(ProfessionService.MOB_KEY)) entity.remove();
    }
    public void close() { ticker.cancel();new ArrayList<>(active.values()).forEach(this::end);hits.clear(); }
    private String prefix(SkillType skill) { return skill.name()+":"; }
    private String job(SkillType skill) { return skill==SkillType.FARMING ? "farming" : "mining"; }
    private boolean roll(UUID uuid,String key,double probability,double bonus,int guarantee) {
        var store=core.getProfessionService().store();
        long misses=store.get(uuid,key);
        boolean success=ProfessionMath.roll(probability,bonus,misses,guarantee,ThreadLocalRandom.current().nextDouble());
        store.set(uuid,key,success ? 0 : misses+1);return success;
    }
    @EventHandler public void gather(ProfessionGatherEvent e) {
        var player=e.player();var uuid=player.getUniqueId();var store=core.getProfessionService().store();
        String prefix=prefix(e.skill());
        if (roll(uuid,prefix+(e.stone() ? "stone_misses" : "rare_misses"),e.stone() ? .002 : .02,e.bonuses().rarePercent(),e.stone() ? 1500 : 150))
            ProfessionService.drop(e.location(),catalog.create(job(e.skill())+"_rare_fragment"));
        replant(e);
        if(!player.isOnline() || player.isDead())return;
        if(e.stone() || e.bonuses().eventTier()==0 || active.containsKey(uuid)) return;
        long now=System.currentTimeMillis();
        // Eligible rolls stop completely while their own cooldown is running.
        if(ProfessionEventRules.mayRoll(true,e.bonuses().eventTier(),false,now,store.get(uuid,prefix+"elite_cooldown")) && attempt(e,true)) return;
        if(ProfessionEventRules.mayRoll(false,e.bonuses().eventTier(),false,now,store.get(uuid,prefix+"normal_cooldown"))) attempt(e,false);
    }
    private boolean attempt(ProfessionGatherEvent e,boolean elite) {
        var uuid=e.player().getUniqueId();var store=core.getProfessionService().store();
        String key=prefix(e.skill())+(elite ? "elite" : "normal")+"_misses";
        long misses=store.get(uuid,key);
        if(!ProfessionMath.roll(elite ? .001 : .003,e.bonuses().eventPercent(),misses,elite ? 3000 : 1000,ThreadLocalRandom.current().nextDouble())) {
            store.set(uuid,key,misses+1);return false;
        }
        Location location=safe(e.player());
        if(location==null) { store.set(uuid,key,Math.max(misses,elite ? 2999 : 999));return false; }
        Encounter encounter=new Encounter();encounter.owner=uuid;encounter.skill=e.skill();encounter.elite=elite;
        encounter.rare=e.bonuses().rarePercent();encounter.health=elite ? 6000 : 400;encounter.start=System.currentTimeMillis();
        Husk entity=location.getWorld().spawn(location,Husk.class,mob -> {
            mob.setAdult();mob.setPersistent(false);mob.setRemoveWhenFarAway(false);
            mob.getPersistentDataContainer().set(ProfessionService.MOB_KEY,PersistentDataType.STRING,e.skill().name());
            mob.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(elite ? 35 : 12);
            mob.getAttribute(Attribute.ARMOR).setBaseValue(0);mob.getAttribute(Attribute.ARMOR_TOUGHNESS).setBaseValue(0);
            mob.setCanBreakDoors(false);
            mob.getEquipment().clear();mob.setCanPickupItems(false);mob.setCustomNameVisible(true);
        });
        if(!entity.isValid() || entity.isDead()) { store.set(uuid,key,Math.max(misses,elite ? 2999 : 999));return false; }
        encounter.entity=entity;entity.setTarget(e.player());active.put(uuid,encounter);mobs.put(entity.getUniqueId(),encounter);name(encounter);
        store.set(uuid,key,0);store.set(uuid,prefix(e.skill())+(elite ? "elite" : "normal")+"_cooldown",System.currentTimeMillis()+(elite ? 300000 : 90000));
        e.player().sendMessage(elite ? "精英职业事件出现！准备迎战。" : "普通职业事件出现！");return true;
    }
    private Location safe(Player player) {
        var origin=player.getLocation();
        for(int radius=2;radius<=4;radius++) for(int x=-radius;x<=radius;x++) for(int z=-radius;z<=radius;z++) {
            if(Math.abs(x)!=radius && Math.abs(z)!=radius)continue;
            for(int y=-2;y<=2;y++) {
                Block feet=origin.clone().add(x,y,z).getBlock();Block head=feet.getRelative(0,1,0);Block floor=feet.getRelative(0,-1,0);
                if(!feet.getType().isAir() || !head.getType().isAir() || !floor.getType().isSolid() || floor.getType().name().contains("MAGMA") || floor.getType()==Material.CACTUS)continue;
                Location spot=feet.getLocation().add(.5,0,.5);
                if(spot.getWorld().getNearbyEntities(spot,.6,1,.6).isEmpty()) return spot;
            }
        }
        return null;
    }
    @EventHandler(priority=EventPriority.LOW,ignoreCancelled=true)
    public void weapon(EntityDamageByEntityEvent e) {
        if(maple.underground_state.stats.PlayerStatsRuntimeListener.isClientDamagePulse() || maple.underground_state.stats.PlayerCritListener.isFerocityChainDamage())return;
        if(e.getDamager() instanceof Player p && (e.getCause()==EntityDamageEvent.DamageCause.ENTITY_ATTACK || e.getCause()==EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) {
            var d=catalog.identify(p.getInventory().getItemInMainHand());
            if(d!=null && d.kind().equals("weapon")) {
                if(!equipment.canUseProfessionItem(p,p.getInventory().getItemInMainHand())) { e.setCancelled(true);return; }
                if(equipment.usable(p,d)) e.setDamage(d.number("damage"));
            }
        }
        Encounter attacking=mobs.get(e.getDamager().getUniqueId());
        if(attacking!=null) {
            if(!(e.getEntity() instanceof Player p) || !p.getUniqueId().equals(attacking.owner)) { e.setCancelled(true);return; }
            e.setDamage((attacking.aoe ? 70 : attacking.elite ? 35 : 12)*(1-equipment.reduction(p,attacking.skill)));
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damage(EntityDamageEvent e) {
        Encounter encounter=mobs.get(e.getEntity().getUniqueId());if(encounter==null)return;
        Player player=null;
        if(e instanceof EntityDamageByEntityEvent by) {
            if(by.getDamager() instanceof Player p)player=p;
            else if(by.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player p)player=p;
        }
        if(player==null) { e.setCancelled(true);return; }
        // Virtual health supports 6000 HP without depending on Paper's max-health attribute limit.
        double damage=e.getFinalDamage()*100/(100+(encounter.elite ? 80 : 20));
        hits.put(e,new Hit(encounter,player,Math.min(encounter.health,Math.max(0,damage))));
        e.setDamage(.01); // Keep native hit feedback; the encounter owns its real health.
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void settled(EntityDamageEvent e) {
        Hit hit=hits.remove(e);if(hit==null || e.isCancelled() || !active.containsValue(hit.encounter))return;
        var encounter=hit.encounter;encounter.health-=hit.damage;encounter.total+=hit.damage;
        if(hit.attacker.getUniqueId().equals(encounter.owner))encounter.contribution+=hit.damage;
        if(encounter.health<=0) victory(encounter);else { encounter.entity.setHealth(20);name(encounter); }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void target(EntityTargetLivingEntityEvent e) {
        Encounter encounter=mobs.get(e.getEntity().getUniqueId());if(encounter==null)return;
        Player owner=Bukkit.getPlayer(encounter.owner);
        if(e.getTarget()!=owner)e.setCancelled(true);
    }
    @EventHandler public void chunk(org.bukkit.event.world.ChunkLoadEvent e) {
        for(Entity entity:e.getChunk().getEntities()) if(entity.getPersistentDataContainer().has(ProfessionService.MOB_KEY) && !mobs.containsKey(entity.getUniqueId()))entity.remove();
    }
    @EventHandler public void death(EntityDeathEvent e) {
        if(e.getEntity().getPersistentDataContainer().has(ProfessionService.MOB_KEY)) { e.getDrops().clear();e.setDroppedExp(0);var encounter=mobs.get(e.getEntity().getUniqueId());if(encounter!=null)end(encounter); }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void transform(EntityTransformEvent e) { if(e.getEntity().getPersistentDataContainer().has(ProfessionService.MOB_KEY))e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void portal(EntityPortalEvent e) { if(e.getEntity().getPersistentDataContainer().has(ProfessionService.MOB_KEY))e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent e) {
        if(e.getItem()!=null && !equipment.canUseProfessionItem(e.getPlayer(),e.getItem())) {
            e.setCancelled(true);e.getPlayer().sendActionBar(Component.text("技能等级不足，无法使用这件职业物品。"));
        }
    }
    @EventHandler public void quit(PlayerQuitEvent e) { var encounter=active.get(e.getPlayer().getUniqueId());if(encounter!=null)end(encounter); }
    @EventHandler public void death(PlayerDeathEvent e) { var encounter=active.get(e.getEntity().getUniqueId());if(encounter!=null)end(encounter); }
    private boolean nearby(Player p,Encounter encounter,double distance) {
        return p!=null && p.isOnline() && !p.isDead() && p.getWorld().equals(encounter.entity.getWorld()) && p.getLocation().distanceSquared(encounter.entity.getLocation())<=distance*distance;
    }
    private void tick() {
        for(var encounter:new ArrayList<>(active.values())) {
            Player owner=Bukkit.getPlayer(encounter.owner);
            encounter.absent=nearby(owner,encounter,24) ? 0 : encounter.absent+1;
            if(ProfessionEventRules.expired(owner!=null && owner.isOnline(),owner!=null && !owner.isDead(),encounter.entity.isValid(),encounter.elite,System.currentTimeMillis()-encounter.start,encounter.absent)) { end(encounter);continue; }
            encounter.entity.setTarget(owner);
            if(encounter.elite && ++encounter.ticks%8==0) {
                owner.sendActionBar(Component.text("精英蓄力：1.5 秒后范围攻击，离开 3 格！"));
                encounter.entity.getWorld().spawnParticle(Particle.FLAME,encounter.entity.getLocation().add(0,.2,0),35,3,.1,3,0);
                Bukkit.getScheduler().runTaskLater(plugin,() -> {
                    if(!active.containsValue(encounter) || !nearby(owner,encounter,3))return;
                    encounter.aoe=true;try { owner.damage(70,encounter.entity); } finally { encounter.aoe=false; }
                },30);
            }
        }
    }
    private void name(Encounter encounter) { encounter.entity.customName(Component.text((encounter.elite ? "精英" : "普通")+(encounter.skill==SkillType.FARMING ? "穗灵" : "矿灵")+" · "+(int)Math.ceil(encounter.health)+" HP")); }
    private void victory(Encounter encounter) {
        Player owner=Bukkit.getPlayer(encounter.owner);
        if(ProfessionEventRules.mayReward(encounter.contribution,encounter.total,nearby(owner,encounter,24))) {
            var store=core.getProfessionService().store();String prefix=prefix(encounter.skill);String job=job(encounter.skill);
            if(encounter.elite) {
                ProfessionService.drop(encounter.entity.getLocation(),catalog.create(job+"_elite_fragment"));
                if(roll(encounter.owner,prefix+"core_misses",.1,encounter.rare,20)) ProfessionService.drop(encounter.entity.getLocation(),catalog.create(job+"_elite_core"));
            } else ProfessionService.drop(encounter.entity.getLocation(),catalog.create(job+"_normal_core"));
            store.add(encounter.owner,prefix+(encounter.elite ? "elite_kills" : "normal_kills"),1);
            owner.sendMessage("职业事件挑战完成，材料已掉落。");
        }
        end(encounter);
    }
    private void end(Encounter encounter) { active.remove(encounter.owner,encounter);mobs.remove(encounter.entity.getUniqueId());encounter.entity.remove(); }
    private void replant(ProfessionGatherEvent e) {
        var d=catalog.identify(e.tool());if(d==null || !d.config().getBoolean("replant"))return;
        Material seed=switch(e.source()) { case WHEAT->Material.WHEAT_SEEDS;case CARROTS->Material.CARROT;case POTATOES->Material.POTATO;case BEETROOTS->Material.BEETROOT_SEEDS;case COCOA->Material.COCOA_BEANS;case NETHER_WART->Material.NETHER_WART;default->null; };
        if(seed==null)return;
        Bukkit.getScheduler().runTask(plugin,() -> {
            var player=e.player();if(!player.isOnline())return;Block block=e.location().getBlock();
            if(!block.getType().isAir())return;
            var inventory=player.getInventory();int found=-1;
            for(int i=0;i<inventory.getStorageContents().length;i++) if(inventory.getItem(i)!=null && inventory.getItem(i).getType()==seed && catalog.identify(inventory.getItem(i))==null) { found=i;break; }
            if(found<0)return;
            var data=e.source().createBlockData();if(!(data instanceof Ageable age))return;age.setAge(0);
            if(e.source()!=Material.COCOA && block.getRelative(0,-1,0).getType()!=(e.source()==Material.NETHER_WART ? Material.SOUL_SAND : Material.FARMLAND))return;
            if(e.source()==Material.COCOA) {
                var cocoa=(org.bukkit.block.data.type.Cocoa)data;boolean facing=false;
                for(var face:List.of(org.bukkit.block.BlockFace.NORTH,org.bukkit.block.BlockFace.SOUTH,org.bukkit.block.BlockFace.EAST,org.bukkit.block.BlockFace.WEST))
                    if(block.getRelative(face).getType()==Material.JUNGLE_LOG || block.getRelative(face).getType()==Material.JUNGLE_WOOD) { cocoa.setFacing(face);facing=true;break; }
                if(!facing)return;
            }
            var old=block.getState();block.setBlockData(data,false);
            BlockPlaceEvent placement=new BlockPlaceEvent(block,old,block.getRelative(0,-1,0),new ItemStack(seed),player,true,EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(placement);
            if(placement.isCancelled() || !placement.canBuild()) { old.update(true,false);return; }
            var item=inventory.getItem(found);item.setAmount(item.getAmount()-1);inventory.setItem(found,item.getAmount()==0 ? null : item);
        });
    }
}
