package maple.underground_caves.gear;

import maple.underground_state.stats.PlayerStatsData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 絕境逢生頭盔：瀕死時觸發 3 秒速度與傷害免疫，冷卻 30 秒 */
public final class SecondWindListener implements Listener {

    private final SecondWindHelmetManager manager;
    private final Map<UUID, Long> lastTriggerByPlayer = new ConcurrentHashMap<>();

    public SecondWindListener(SecondWindHelmetManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!manager.isWearingSecondWindHelmet(p)) return;
        SecondWindHelmetConfig cfg = manager.getConfig();
        if (cfg == null) return;

        double healthAfter = p.getHealth() - e.getFinalDamage();
        if (healthAfter > 0) return;

        long now = System.currentTimeMillis();
        long cooldownMs = cfg.getCooldownSeconds() * 1000L;
        Long last = lastTriggerByPlayer.get(p.getUniqueId());
        if (last != null && (now - last) < cooldownMs) return;

        e.setCancelled(true);
        lastTriggerByPlayer.put(p.getUniqueId(), now);
        boolean usedRuntimeHealth = false;

        // 只更新虛擬血量（PlayerStats），實體血條設為 1 心不提高上限
        if (manager.getPlugin() instanceof maple.underground_caves.Test_dungeons td && td.getPlayerStatsManager() != null) {
            double virtualMax = td.getEffectiveMaxHealth(p);
            if (td.getPlayerStatsRuntime() != null) {
                td.getPlayerStatsRuntime().setCurrentHealth(p, virtualMax * 0.05);
                usedRuntimeHealth = true;
            } else {
                PlayerStatsData stats = td.getPlayerStatsManager().get(p);
                stats.setCurrentHealthWithCap(virtualMax * 0.05, virtualMax);
            }
        }
        if (!usedRuntimeHealth) {
            p.setHealth(1.0);
        }

        int durationTicks = cfg.getEffectDurationSeconds() * 20;
        int speedBonus = Math.min(255, Math.max(0, cfg.getEffectSpeedBonus()));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, Math.max(0, speedBonus / 20), true, true));
        p.setNoDamageTicks(durationTicks);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) return;
                p.setNoDamageTicks(0);
            }
        }.runTaskLater(manager.getPlugin(), durationTicks);
    }
}
