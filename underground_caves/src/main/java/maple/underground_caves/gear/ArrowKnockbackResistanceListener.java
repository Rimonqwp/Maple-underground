package maple.underground_caves.gear;

import maple.underground_caves.Test_dungeons;

import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * 依裝備（如篩子身體套裝）提供的箭矢擊退抗性，在玩家被箭矢擊中後縮減擊退速度。
 * 虛擬血量會取消傷害事件，故需 {@code ignoreCancelled = false}，否則永遠收不到事件。
 */
public final class ArrowKnockbackResistanceListener implements Listener {

    private final JavaPlugin plugin;
    private final SieveSetManager sieveSetManager;

    public ArrowKnockbackResistanceListener(JavaPlugin plugin, SieveSetManager sieveSetManager) {
        this.plugin = plugin;
        this.sieveSetManager = sieveSetManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onDamageByArrow(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getDamager() instanceof AbstractArrow)) return;
        if (plugin instanceof Test_dungeons td) {
            var death = td.getDungeonDeathListener();
            if (death != null && death.isDungeonGhost(player.getUniqueId())) return;
        }
        double resistPercent = sieveSetManager.getArrowKnockbackResistancePercent(player);
        if (resistPercent <= 0) return;
        if (resistPercent >= 100) {
            runNextTick(() -> player.setVelocity(new Vector(0, 0, 0)));
            return;
        }
        double factor = 1.0 - (resistPercent / 100.0);
        runNextTick(() -> {
            Vector v = player.getVelocity();
            player.setVelocity(v.multiply(factor));
        });
    }

    private void runNextTick(Runnable run) {
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, run, 1L);
    }
}
