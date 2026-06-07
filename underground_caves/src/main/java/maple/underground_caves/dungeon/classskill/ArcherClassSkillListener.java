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
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ArcherClassSkillListener extends AbstractDungeonClassSkillListener implements Listener {
    private final Map<UUID, Long> archerExplosiveCooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> archerRapidCooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Double> archerLastMaxBowDamage = new ConcurrentHashMap<>();
    private final Map<UUID, Long> archerStillSinceMs = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> archerFocusStacks = new ConcurrentHashMap<>();
    private final NamespacedKey archerExplosiveArrowKey;

    public ArcherClassSkillListener(Test_dungeons plugin) {
        super(plugin);
        this.archerExplosiveArrowKey = new NamespacedKey(plugin, "archer_explosive_arrow");
        startArcherBowTrackTask();
        startArcherFocusTask();
    }

    private void startArcherBowTrackTask() {
        new BukkitRunnable() {
            @Override public void run() { archerLastMaxBowDamage.clear(); }
        }.runTaskTimer(plugin, 20L * 60, 20L * 60);
    }

    private void startArcherFocusTask() {
        new BukkitRunnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!inDungeon(p) || !dungeonStarted(p) || classIndex(p) != 3 || isDungeonSoulMode(p)) {
                        archerStillSinceMs.remove(p.getUniqueId());
                        archerFocusStacks.remove(p.getUniqueId());
                        continue;
                    }
                    long since = archerStillSinceMs.getOrDefault(p.getUniqueId(), 0L);
                    if (since <= 0) {
                        archerStillSinceMs.put(p.getUniqueId(), now);
                        archerFocusStacks.put(p.getUniqueId(), 0);
                        continue;
                    }
                    long elapsed = now - since;
                    long standMs = (long) (DungeonClassSkills.archerFocusStandSec(classLevel(p)) * 1000L);
                    if (elapsed < standMs) { archerFocusStacks.put(p.getUniqueId(), 0); continue; }
                    int maxStacks = DungeonClassSkills.archerFocusMaxStacks(classLevel(p));
                    int stacks = (int) Math.min(maxStacks, 1 + ((elapsed - standMs) / 1000L));
                    archerFocusStacks.put(p.getUniqueId(), stacks);
                    if (stacks > 0) {
                        p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 40, 0, true, false, false));
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onProjectileLaunchForArcher(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player shooter)) return;
        if (!inDungeon(shooter) || classIndex(shooter) != 3 || isDungeonSoulMode(shooter)) return;
        int lvl = classLevel(shooter);
        int chance = DungeonClassSkills.archerDoubleShotChancePercent(lvl);
        if (event.getEntity() instanceof Arrow && Math.random() * 100 < chance) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Arrow second = shooter.getWorld().spawnArrow(shooter.getEyeLocation(), shooter.getLocation().getDirection(), 1.0f, 12f);
                second.setShooter(shooter);
            }, 3L);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBowDamageForArcher(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile proj) || !(proj.getShooter() instanceof Player shooter)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;
        if (!inDungeon(shooter) || classIndex(shooter) != 3 || isDungeonSoulMode(shooter)) return;
        int stacks = archerFocusStacks.getOrDefault(shooter.getUniqueId(), 0);
        if (stacks > 0) {
            double perStack = DungeonClassSkills.archerFocusDamagePerStackPercent(classLevel(shooter)) / 100.0;
            int maxStacks = DungeonClassSkills.archerFocusMaxStacks(classLevel(shooter));
            event.setDamage(event.getDamage() * (1.0 + perStack * Math.min(maxStacks, stacks)));
        }
        archerLastMaxBowDamage.merge(shooter.getUniqueId(), event.getDamage(), Math::max);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onArcherExplosiveArrowHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;
        Byte mark = arrow.getPersistentDataContainer().get(archerExplosiveArrowKey, PersistentDataType.BYTE);
        if (mark == null || mark == 0) return;
        arrow.getPersistentDataContainer().remove(archerExplosiveArrowKey);
        if (!(arrow.getShooter() instanceof Player shooter)) { arrow.remove(); return; }
        if (isDungeonSoulMode(shooter)) { arrow.remove(); return; }
        if (!(event.getHitEntity() instanceof Monster hit)) { arrow.remove(); return; }
        Location loc = hit.getLocation();
        World w = hit.getWorld();
        w.spawnParticle(Particle.EXPLOSION, loc, 1, 0, 0, 0, 0);
        w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.2f);
        double dmg = arrow.getDamage();
        for (Entity e : w.getNearbyEntities(loc, 4, 4, 4)) if (e instanceof Monster m) m.damage(dmg, shooter);
        arrow.remove();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArcherMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (!inDungeon(p) || !dungeonStarted(p) || classIndex(p) != 3 || isDungeonSoulMode(p)) return;
        if (event.getTo() == null) return;
        if (event.getFrom().getWorld() != event.getTo().getWorld() || event.getFrom().distanceSquared(event.getTo()) > 0.0004) {
            archerStillSinceMs.put(p.getUniqueId(), 0L);
            archerFocusStacks.put(p.getUniqueId(), 0);
        }
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!inDungeon(player) || !dungeonStarted(player) || classIndex(player) != 3 || isDungeonSoulMode(player)) return;
        event.setCancelled(true);
        int lvl = Math.max(1, classLevel(player));
        long now = System.currentTimeMillis();
        long until = archerRapidCooldownUntil.getOrDefault(player.getUniqueId(), 0L);
        if (now < until) {
            player.sendActionBar(Component.text("快速射擊冷卻中 " + ((until - now) / 1000 + 1) + " 秒", NamedTextColor.GRAY));
            return;
        }
        archerRapidCooldownUntil.put(player.getUniqueId(), now + 100_000L);
        double maxBow = archerLastMaxBowDamage.getOrDefault(player.getUniqueId(), 50.0);
        double dmgPerArrow = maxBow * (DungeonClassSkills.archerRapidDamagePercent(lvl) / 100.0);
        int totalArrows = (int) Math.round(DungeonClassSkills.archerRapidDurationSec(lvl) * DungeonClassSkills.archerRapidArrowCount(lvl));
        if (totalArrows < 1) totalArrows = 1;
        for (int i = 0; i < totalArrows; i++) {
            final int tick = i;
            new BukkitRunnable() {
                @Override public void run() {
                    if (!player.isOnline() || isDungeonSoulMode(player)) return;
                    Arrow arr = player.getWorld().spawnArrow(player.getEyeLocation(), player.getLocation().getDirection(), 1.0f, 12f);
                    arr.setShooter(player);
                    arr.setDamage(dmgPerArrow);
                }
            }.runTaskLater(plugin, 10L * tick);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!inDungeon(player) || !dungeonStarted(player) || classIndex(player) != 3 || isDungeonSoulMode(player) || !player.isSneaking()) return;
        if (!(event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) return;
        long now = System.currentTimeMillis();
        int lvl = Math.max(1, classLevel(player));
        long until = archerExplosiveCooldownUntil.getOrDefault(player.getUniqueId(), 0L);
        if (now < until) return;
        archerExplosiveCooldownUntil.put(player.getUniqueId(), now + 40_000L);
        double maxBow = archerLastMaxBowDamage.getOrDefault(player.getUniqueId(), 50.0);
        int arrowCount = DungeonClassSkills.archerExplosiveArrowCount(lvl);
        for (int i = 0; i < arrowCount; i++) {
            Arrow arr = player.launchProjectile(Arrow.class, player.getLocation().getDirection().multiply(2));
            arr.setShooter(player);
            arr.setDamage(maxBow);
            arr.getPersistentDataContainer().set(archerExplosiveArrowKey, PersistentDataType.BYTE, (byte) 1);
        }
    }
}
