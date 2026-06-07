package maple.underground_caves.dungeon.classskill;

import maple.underground_caves.Test_dungeons;
import maple.underground_state.dungeon.panel.DungeonClassSkills;
import maple.underground_state.stats.PlayerStatsData;
import maple.underground_state.stats.PlayerStatsFormulas;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WarriorClassSkillListener extends AbstractDungeonClassSkillListener implements Listener {
    private final DungeonClassSkillSharedState shared;
    private final Map<UUID, Long> warriorAxeCooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> warriorShoutCooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Double> warriorLastMaxMeleeDamage = new ConcurrentHashMap<>();
    private final Map<UUID, Long> warriorVampireBuffUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> warriorBloodStacks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> warriorBleedCooldownUntil = new ConcurrentHashMap<>();

    public WarriorClassSkillListener(Test_dungeons plugin, DungeonClassSkillSharedState shared) {
        super(plugin);
        this.shared = shared;
        startWarriorMeleeTrackTask();
    }

    private void startWarriorMeleeTrackTask() {
        new BukkitRunnable() {
            @Override public void run() {
                warriorLastMaxMeleeDamage.clear();
                warriorBloodStacks.replaceAll((u, n) -> Math.max(0, n - 1));
            }
        }.runTaskTimer(plugin, 20L * 60, 20L * 60);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (!(event.getDamager() instanceof Player damager)) return;
        if (!inDungeon(damager) || !dungeonStarted(damager) || classIndex(damager) != 2 || isDungeonSoulMode(damager)) return;
        int lvl = Math.max(1, classLevel(damager));
        double damage = event.getDamage();

        long now = System.currentTimeMillis();
        long dmgUntil = shared.warriorShoutDamageBuffUntil.getOrDefault(damager.getUniqueId(), 0L);
        if (now < dmgUntil) {
            double dmgPct = DungeonClassSkills.warriorShoutDamageBuffPercent(classLevel(damager));
            event.setDamage(event.getDamage() * (1.0 + dmgPct / 100.0));
        }

        if (!(event.getDamager() instanceof Projectile)) {
            warriorLastMaxMeleeDamage.merge(damager.getUniqueId(), damage, Math::max);
        }

        Long vampUntil = warriorVampireBuffUntil.get(damager.getUniqueId());
        if (vampUntil != null && now < vampUntil) {
            int vampPct = DungeonClassSkills.warriorVampPercent(lvl);
            event.setDamage(damage * (1 + vampPct / 100.0));
            warriorVampireBuffUntil.remove(damager.getUniqueId());
            if (plugin.getPlayerStatsManager() != null) {
                double maxH = plugin.getEffectiveMaxHealth(damager);
                PlayerStatsData data = plugin.getPlayerStatsManager().get(damager);
                double missing = maxH - data.getCurrentHealth();
                double heal = PlayerStatsFormulas.totalHealthGained(missing * 0.03, plugin.getEffectiveVitalityForSelfHeal(damager));
                if (plugin.getPlayerStatsRuntime() != null) {
                    plugin.getPlayerStatsRuntime().heal(damager, heal);
                } else {
                    data.setCurrentHealthWithCap(Math.min(data.getCurrentHealth() + heal, maxH), maxH);
                    plugin.syncVirtualHealthBar(damager);
                }
            }
        }

        double bloodPct = DungeonClassSkills.warriorBloodPercent(lvl);
        int stacks = Math.min(10, warriorBloodStacks.getOrDefault(damager.getUniqueId(), 0) + 1);
        warriorBloodStacks.put(damager.getUniqueId(), stacks);
        event.setDamage(event.getDamage() * (1.0 + (bloodPct / 100.0) * stacks));

        if (victim instanceof Monster) {
            if (warriorBleedCooldownUntil.getOrDefault(victim.getUniqueId(), 0L) <= now) {
                int chance = DungeonClassSkills.warriorRiftChancePercent(lvl);
                if (Math.random() * 100 < chance) {
                    warriorBleedCooldownUntil.put(victim.getUniqueId(), now + DungeonClassSkills.warriorRiftCooldownSec(lvl) * 1000L);
                    double dotPercent = DungeonClassSkills.warriorRiftDotPercent(lvl);
                    double dotPerTick = event.getDamage() * (dotPercent / 100.0) / 5.0;
                    for (int i = 1; i <= 5; i++) {
                        final int tick = i;
                        final LivingEntity target = victim;
                        final Player dotSource = damager;
                        new BukkitRunnable() {
                            @Override public void run() {
                                if (!target.isValid() || target.isDead()) return;
                                if (dotSource == null || !dotSource.isOnline() || isDungeonSoulMode(dotSource)) return;
                                target.damage(dotPerTick, dotSource);
                            }
                        }.runTaskLater(plugin, 20L * tick);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKillForWarriorVampire(EntityDeathEvent event) {
        if (event.getEntity() instanceof Monster && event.getEntity().getKiller() instanceof Player killer) {
            if (!inDungeon(killer) || classIndex(killer) != 2 || isDungeonSoulMode(killer)) return;
            warriorVampireBuffUntil.put(killer.getUniqueId(), System.currentTimeMillis() + 5000L);
        }
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!inDungeon(player) || !dungeonStarted(player) || classIndex(player) != 2 || isDungeonSoulMode(player)) return;
        event.setCancelled(true);
        int lvl = Math.max(1, classLevel(player));
        long now = System.currentTimeMillis();
        long until = warriorShoutCooldownUntil.getOrDefault(player.getUniqueId(), 0L);
        if (now < until) {
            player.sendActionBar(Component.text("狂戰怒吼冷卻中 " + ((until - now) / 1000 + 1) + " 秒", NamedTextColor.GRAY));
            return;
        }
        warriorShoutCooldownUntil.put(player.getUniqueId(), now + 180_000L);
        double radius = DungeonClassSkills.warriorShoutRadius(lvl);
        long durMs = (long) (DungeonClassSkills.warriorShoutDurationSec(lvl) * 1000L);
        for (Player p : player.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            if (isDungeonSoulMode(p)) continue;
            if (player.getLocation().distance(p.getLocation()) > radius) continue;
            shared.warriorShoutDamageBuffUntil.put(p.getUniqueId(), now + durMs);
            shared.warriorShoutReduceBuffUntil.put(p.getUniqueId(), now + durMs);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!inDungeon(player) || !dungeonStarted(player) || classIndex(player) != 2 || isDungeonSoulMode(player)) return;
        if (!player.isSneaking()) return;
        if (!(event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) return;
        long now = System.currentTimeMillis();
        long until = warriorAxeCooldownUntil.getOrDefault(player.getUniqueId(), 0L);
        if (now < until) return;
        warriorAxeCooldownUntil.put(player.getUniqueId(), now + 120_000L);
        throwWarriorAxe(player, warriorLastMaxMeleeDamage.getOrDefault(player.getUniqueId(), 50.0));
    }

    private void throwWarriorAxe(Player player, double damage) {
        World w = player.getWorld();
        Vector dir = player.getLocation().getDirection().normalize();
        Location start = player.getEyeLocation().add(dir.clone().multiply(1.2));
        ArmorStand stand = w.spawn(start, ArmorStand.class, as -> {
            as.setInvisible(true); as.setMarker(true); as.setSmall(true); as.setGravity(false); as.setInvulnerable(true); as.setSilent(true); as.setArms(true);
            as.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_AXE));
        });
        new BukkitRunnable() {
            double travelled = 0; float roll = 0;
            @Override public void run() {
                if (!player.isOnline() || isDungeonSoulMode(player)) { try { stand.remove(); } catch (Throwable ignored) {} cancel(); return; }
                if (!stand.isValid()) { cancel(); return; }
                Location next = stand.getLocation().clone().add(dir.clone().multiply(0.75));
                travelled += 0.75;
                if (next.getBlock().getType().isSolid() || travelled >= 15.0) { stand.remove(); cancel(); return; }
                for (Entity e : w.getNearbyEntities(next, 0.6, 0.6, 0.6)) {
                    if (e instanceof Monster m && m.isValid() && !m.isDead()) {
                        m.damage(Math.max(1.0, damage), player);
                        stand.remove();
                        cancel();
                        return;
                    }
                }
                stand.teleport(next);
                roll += 35f;
                stand.setRightArmPose(new org.bukkit.util.EulerAngle(Math.toRadians(roll), 0, 0));
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
