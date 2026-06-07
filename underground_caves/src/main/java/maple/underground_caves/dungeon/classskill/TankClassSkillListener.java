package maple.underground_caves.dungeon.classskill;

import maple.underground_caves.Test_dungeons;
import maple.underground_state.dungeon.panel.DungeonClassSkills;
import maple.underground_state.stats.PlayerStatsData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TankClassSkillListener extends AbstractDungeonClassSkillListener implements Listener {
    private final DungeonClassSkillSharedState shared;
    private final Map<UUID, Long> tankTauntCooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> tankTauntDamageReduceUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> tankTauntPassiveRateLimitUntil = new ConcurrentHashMap<>();

    public TankClassSkillListener(Test_dungeons plugin, DungeonClassSkillSharedState shared) {
        super(plugin);
        this.shared = shared;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageForTankRedirect(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!inDungeon(victim) || isDungeonSoulMode(victim) || plugin.getPlayerStatsManager() == null) return;
        double rawDamage = event.getFinalDamage();
        if (rawDamage <= 0) return;
        long now = System.currentTimeMillis();
        long reduceUntil = shared.warriorShoutReduceBuffUntil.getOrDefault(victim.getUniqueId(), 0L);
        if (now < reduceUntil) {
            double reducePct = DungeonClassSkills.warriorShoutDamageReducePercent(classLevel(victim));
            rawDamage *= (1.0 - reducePct / 100.0);
        }

        Player tank = null;
        for (Player p : victim.getWorld().getPlayers()) {
            if (p.getUniqueId().equals(victim.getUniqueId())) continue;
            if (classIndex(p) != 4 || isDungeonSoulMode(p)) continue;
            int lvl = classLevel(p);
            if (lvl <= 0) continue;
            double radius = DungeonClassSkills.tankRedirectRadius(lvl);
            if (p.getLocation().distance(victim.getLocation()) <= radius) { tank = p; break; }
        }
        if (tank == null) return;
        event.setCancelled(true);
        double toVictim = rawDamage * 0.2;
        double toTank = rawDamage * 0.8;
        if (plugin.getPlayerStatsRuntime() != null) {
            plugin.getPlayerStatsRuntime().damage(victim, toVictim);
            plugin.getPlayerStatsRuntime().damage(tank, toTank);
        } else {
            double maxV = plugin.getEffectiveMaxHealth(victim);
            double maxT = plugin.getEffectiveMaxHealth(tank);
            PlayerStatsData dv = plugin.getPlayerStatsManager().get(victim);
            PlayerStatsData dt = plugin.getPlayerStatsManager().get(tank);
            dv.setCurrentHealthWithCap(Math.max(0, dv.getCurrentHealth() - toVictim), maxV);
            dt.setCurrentHealthWithCap(Math.max(0, dt.getCurrentHealth() - toTank), maxT);
            plugin.syncVirtualHealthBar(victim);
            plugin.syncVirtualHealthBar(tank);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageToPlayerForTank(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Mob damager)) return;
        if (!inDungeon(victim) || classIndex(victim) != 4 || isDungeonSoulMode(victim)) return;
        long now = System.currentTimeMillis();
        long until = tankTauntDamageReduceUntil.getOrDefault(victim.getUniqueId(), 0L);
        if (now < until) {
            int reduce = DungeonClassSkills.tankProvokeDamageReducePercent(classLevel(victim));
            event.setDamage(event.getDamage() * (1.0 - reduce / 100.0));
        }
        ItemStack off = victim.getInventory().getItemInOffHand();
        if (off.getType() != Material.SHIELD) return;
        int lvl = classLevel(victim);
        Vector toDamager = damager.getLocation().toVector().subtract(victim.getLocation().toVector()).normalize();
        double dot = toDamager.dot(victim.getLocation().getDirection());
        if (dot < 0) {
            int reduce = DungeonClassSkills.tankFrontReducePercent(lvl);
            event.setDamage(event.getDamage() * (1 - reduce / 100.0));
        } else {
            event.setDamage(event.getDamage() * 1.10);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTankTauntPassive(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Monster mob)) return;
        if (!(event.getTarget() instanceof Player currentTarget)) return;
        if (!inDungeon(currentTarget) || !dungeonStarted(currentTarget) || classIndex(currentTarget) == 4 || isDungeonSoulMode(currentTarget)) return;
        Player bestTank = null;
        int bestLvl = 0;
        for (Player p : currentTarget.getWorld().getPlayers()) {
            if (classIndex(p) != 4 || p.getGameMode() == GameMode.SPECTATOR || isDungeonSoulMode(p)) continue;
            double maxH = plugin.getEffectiveMaxHealth(p);
            if (maxH <= 0) continue;
            double curH = plugin.getPlayerStatsManager() != null ? plugin.getPlayerStatsManager().get(p).getCurrentHealth() : p.getHealth();
            double threshold = DungeonClassSkills.tankTauntHealthThresholdPercent(classLevel(p)) / 100.0;
            if ((curH / maxH) <= threshold) continue;
            if (p.getLocation().distanceSquared(mob.getLocation()) > 12 * 12) continue;
            int lvl = classLevel(p);
            if (lvl > bestLvl) { bestLvl = lvl; bestTank = p; }
        }
        if (bestTank == null) return;
        long now = System.currentTimeMillis();
        if (now < tankTauntPassiveRateLimitUntil.getOrDefault(bestTank.getUniqueId(), 0L)) return;
        tankTauntPassiveRateLimitUntil.put(bestTank.getUniqueId(), now + 1000L);
        int chance = (int) Math.min(60, Math.max(10, 10 + (50.0 * bestLvl / 50.0)));
        if (Math.random() * 100 < chance) event.setTarget(bestTank);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!inDungeon(player) || !dungeonStarted(player) || classIndex(player) != 4 || isDungeonSoulMode(player) || !player.isSneaking()) return;
        if (!(event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) return;
        long now = System.currentTimeMillis();
        int lvl = Math.max(1, classLevel(player));
        long until = tankTauntCooldownUntil.getOrDefault(player.getUniqueId(), 0L);
        if (now < until) {
            player.sendActionBar(Component.text("挑釁怒吼冷卻中 " + ((until - now) / 1000 + 1) + " 秒", NamedTextColor.GRAY));
            return;
        }
        tankTauntCooldownUntil.put(player.getUniqueId(), now + 120_000L);
        long durMs = (long) (DungeonClassSkills.tankProvokeDurationSec(lvl) * 1000L);
        int radius = DungeonClassSkills.tankProvokeRadius(lvl);
        tankTauntDamageReduceUntil.put(player.getUniqueId(), now + durMs);
        for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Mob mob) mob.setTarget(player);
        }
    }
}
