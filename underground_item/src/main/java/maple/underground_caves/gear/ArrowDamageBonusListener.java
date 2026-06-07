package maple.underground_caves.gear;

import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * 依裝備（骨弓套裝、白骨先鋒、冥界等）提供的箭矢傷害加成，在玩家以箭矢造成傷害時合併計算。
 */
public final class ArrowDamageBonusListener implements Listener {

    private final ArcherSetManager archerSetManager;
    private final SkeletonSoldierSetManager skeletonSoldierSetManager;
    private final SkeletonMasterSetManager skeletonMasterSetManager;

    public ArrowDamageBonusListener(ArcherSetManager archerSetManager, SkeletonSoldierSetManager skeletonSoldierSetManager,
                                   SkeletonMasterSetManager skeletonMasterSetManager) {
        this.archerSetManager = archerSetManager;
        this.skeletonSoldierSetManager = skeletonSoldierSetManager;
        this.skeletonMasterSetManager = skeletonMasterSetManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByArrow(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player player)) return;
        double percent = (archerSetManager != null ? archerSetManager.getArrowDamagePercent(player) : 0)
                + (skeletonSoldierSetManager != null ? skeletonSoldierSetManager.getArrowDamagePercent(player) : 0)
                + (skeletonMasterSetManager != null ? skeletonMasterSetManager.getArrowDamagePercent(player) : 0);
        if (percent <= 0) return;
        double multiplier = 1.0 + (percent / 100.0);
        event.setDamage(event.getDamage() * multiplier);
    }
}
