package maple.underground_caves.dungeon.classskill;

import maple.underground_caves.Test_dungeons;
import maple.underground_state.dungeon.panel.DungeonClassSkills;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MageClassSkillListener extends AbstractDungeonClassSkillListener implements Listener {
    private final Map<UUID, Long> mageSheepCooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> mageStormCooldownUntil = new ConcurrentHashMap<>();
    private final NamespacedKey mageStaffDamageKey;
    private final ConcurrentMap<UUID, Double> mageLastMeleeDamage = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> mageStaffRateLimitUntil = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> mageLeftClickMs = new ConcurrentHashMap<>();

    private static final long[] MAGE_GUIDED_SHEEP_DAMAGE_TOTAL = {0L,10500L,11000L,11500L,12000L,12500L,13500L,14500L,15500L,16500L,17500L,20000L,22500L,25000L,27500L,30000L,32500L,35000L,40000L,45000L,50000L,55000L,60000L,70000L,80000L,90000L,100000L,110000L,135000L,160000L,185000L,210000L,235000L,260000L,285000L,310000L,335000L,360000L,385000L,410000L,435000L,460000L,485000L,510000L,535000L,560000L,585000L,610000L,635000L,660000L,710000L};
    private static final long[] MAGE_THUNDERSTORM_DAMAGE_TOTAL = {10000L,21000L,22000L,23000L,24000L,25000L,27000L,29000L,31000L,33000L,35000L,40000L,45000L,50000L,55000L,60000L,65000L,70000L,80000L,90000L,100000L,110000L,120000L,140000L,160000L,180000L,200000L,220000L,270000L,320000L,370000L,420000L,470000L,520000L,570000L,620000L,670000L,720000L,770000L,820000L,870000L,920000L,970000L,1020000L,1070000L,1120000L,1170000L,1220000L,1270000L,1320000L,1420000L};

    public MageClassSkillListener(Test_dungeons plugin) {
        super(plugin);
        this.mageStaffDamageKey = new NamespacedKey(plugin, "mage_staff_damage");
    }

    private static long sheepDamage(int lvl){ return MAGE_GUIDED_SHEEP_DAMAGE_TOTAL[Math.min(Math.max(0,lvl),50)]; }
    private static long stormDamage(int lvl){ return MAGE_THUNDERSTORM_DAMAGE_TOTAL[Math.min(Math.max(0,lvl),50)]; }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Arrow arrow) {
            Double stored = arrow.getPersistentDataContainer().get(mageStaffDamageKey, PersistentDataType.DOUBLE);
            if (stored != null) {
                // 靈魂模式：不套用法杖箭矢儲存傷害（職業效果來源失效）
                if (!(arrow.getShooter() instanceof Player sp) || !isDungeonSoulMode(sp)) {
                    event.setDamage(stored);
                }
            }
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (!(event.getDamager() instanceof Player damager)) return;
        if (!inDungeon(damager) || !dungeonStarted(damager) || classIndex(damager) != 1 || isDungeonSoulMode(damager)) return;
        if (victim instanceof Monster && !(event.getDamager() instanceof Projectile)) {
            mageLastMeleeDamage.put(damager.getUniqueId(), event.getDamage());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!inDungeon(player) || !dungeonStarted(player) || classIndex(player) != 1 || isDungeonSoulMode(player)) return;
        if (!(event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK)) return;
        int lvl = Math.max(1, classLevel(player));
        long now = System.currentTimeMillis();
        long last = mageLeftClickMs.getOrDefault(player.getUniqueId(), 0L);
        mageLeftClickMs.put(player.getUniqueId(), now);
        if (now - last <= 1000L) { tryCastMageGuidedSheep(player, lvl, now); return; }
        if (now < mageStaffRateLimitUntil.getOrDefault(player.getUniqueId(), 0L)) return;
        mageStaffRateLimitUntil.put(player.getUniqueId(), now + 120L);
        castMageStaffBeam(player);
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!inDungeon(player) || !dungeonStarted(player) || classIndex(player) != 1 || isDungeonSoulMode(player)) return;
        event.setCancelled(true);
        int lvl = Math.max(1, classLevel(player));
        long now = System.currentTimeMillis();
        long until = mageStormCooldownUntil.getOrDefault(player.getUniqueId(), 0L);
        if (now < until) return;
        long cdMs = (long) (120_000L * plugin.getCooldownMultiplier(player));
        mageStormCooldownUntil.put(player.getUniqueId(), now + cdMs);
        double baseDmg = stormDamage(lvl);
        Location loc = player.getLocation();
        for (Entity e : player.getWorld().getNearbyEntities(loc, 10, 10, 10)) {
            if (e instanceof Monster m && m.getLocation().distance(loc) <= 10) {
                m.getWorld().strikeLightningEffect(m.getLocation());
                m.damage(baseDmg, player);
            }
        }
    }

    private void castMageStaffBeam(Player player) {
        World w = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        int lvl = Math.max(1, classLevel(player));
        double maxRange = DungeonClassSkills.mageStaffRange(lvl);
        double blockDist = maxRange;
        try {
            var br = w.rayTraceBlocks(eye, dir, maxRange, org.bukkit.FluidCollisionMode.NEVER, true);
            if (br != null && br.getHitPosition() != null) blockDist = Math.min(blockDist, br.getHitPosition().distance(eye.toVector()));
        } catch (Throwable ignored) {}
        LivingEntity hit = null;
        double hitDist = blockDist;
        try {
            var er = w.rayTraceEntities(eye, dir, blockDist, 0.6, e -> (e instanceof Monster) && e.isValid() && !e.isDead());
            if (er != null && er.getHitEntity() instanceof LivingEntity le) {
                hit = le;
                if (er.getHitPosition() != null) hitDist = Math.min(hitDist, er.getHitPosition().distance(eye.toVector()));
            }
        } catch (Throwable ignored) {}
        spawnBlueBeamParticles(w, eye, dir, Math.max(0.5, Math.min(blockDist, hitDist)));
        if (hit == null) return;
        double baseMelee = mageLastMeleeDamage.getOrDefault(player.getUniqueId(), 50.0);
        double intelligence = plugin.getPlayerStatsManager() != null ? plugin.getPlayerStatsManager().get(player).getIntelligence() : 0;
        intelligence = plugin.applyDungeonBlessing(player, maple.underground_caves.dungeon.DungeonBlessingManager.BlessingStat.INTELLIGENCE, intelligence);
        double dmgIncrease = DungeonClassSkills.mageStaffDamagePercent(lvl) / 100.0 + 0.0009 * intelligence;
        hit.damage(Math.max(1.0, baseMelee * dmgIncrease), player);
    }

    private void spawnBlueBeamParticles(World w, Location eye, Vector dir, double dist) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(140, 210, 255), 0.9f);
        for (int i = 1, n = (int) Math.max(1, Math.floor(dist / 0.35)); i <= n; i++) {
            Vector p = eye.toVector().add(dir.clone().multiply(i * 0.35));
            w.spawnParticle(Particle.DUST, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0, dust);
        }
    }

    private void tryCastMageGuidedSheep(Player player, int lvl, long now) {
        long until = mageSheepCooldownUntil.getOrDefault(player.getUniqueId(), 0L);
        if (now < until) return;
        long cdMs = (long) (60_000L * plugin.getCooldownMultiplier(player));
        mageSheepCooldownUntil.put(player.getUniqueId(), now + cdMs);
        World w = player.getWorld();
        Location spawn = player.getEyeLocation().add(player.getLocation().getDirection().normalize().multiply(1.2));
        Vector dir = player.getLocation().getDirection().normalize();
        Sheep sheep = w.spawn(spawn, Sheep.class, s -> { s.setAI(false); s.setGravity(false); s.setSilent(true); s.setInvulnerable(true); });
        startGuidedSheepFlight(player, sheep, dir, sheepDamage(lvl));
    }

    private void startGuidedSheepFlight(Player caster, Sheep sheep, Vector dir, long damage) {
        World w = sheep.getWorld();
        Location start = sheep.getLocation().clone();
        new BukkitRunnable() {
            @Override public void run() {
                if (caster != null && (!caster.isOnline() || isDungeonSoulMode(caster))) {
                    try { sheep.remove(); } catch (Throwable ignored) {}
                    cancel();
                    return;
                }
                if (!sheep.isValid() || sheep.isDead()) { cancel(); return; }
                Location next = sheep.getLocation().clone().add(dir.clone().multiply(0.6));
                if (next.distance(start) >= 40 || next.getBlock().getType().isSolid()) { explode(); cancel(); return; }
                sheep.teleport(next);
                w.spawnParticle(Particle.SNOWFLAKE, next, 2, 0.05, 0.05, 0.05, 0);
            }
            private void explode() {
                if (caster != null && (!caster.isOnline() || isDungeonSoulMode(caster))) {
                    try { sheep.remove(); } catch (Throwable ignored) {}
                    return;
                }
                Location loc = sheep.getLocation();
                try { sheep.remove(); } catch (Throwable ignored) {}
                var secrets = plugin.getDungeonSecrets();
                if (secrets != null) secrets.spawnSpecialTnt(w, loc, 1, 16.0f); else w.createExplosion(loc, 4.0f, false, false);
                if (caster != null && caster.isOnline()) for (Entity e : w.getNearbyEntities(loc, 4, 4, 4)) if (e instanceof Monster m) m.damage(damage, caster);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
