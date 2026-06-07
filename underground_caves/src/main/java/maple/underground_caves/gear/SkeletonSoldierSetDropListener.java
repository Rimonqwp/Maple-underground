package maple.underground_caves.gear;

import maple.underground_caves.Test_dungeons;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 地牢骷髏掉落已改由 UnifiedSkeletonGearDropListener 統一擲骰（與骨弓、冥界互斥）；此地牢骷髏時直接略過。
 */
public final class SkeletonSoldierSetDropListener implements Listener {

    private static final String PDC_ENTITY_TYPE = "dungeon_monster_entity_type";

    private final Test_dungeons plugin;
    private final SkeletonSoldierSetManager skeletonSoldierSetManager;

    public SkeletonSoldierSetDropListener(Test_dungeons plugin, SkeletonSoldierSetManager skeletonSoldierSetManager) {
        this.plugin = plugin;
        this.skeletonSoldierSetManager = skeletonSoldierSetManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Skeleton skeleton)) return;
        if (!isDungeonSkeleton(skeleton)) return;
        Player killer = skeleton.getKiller();
        if (killer == null) return;
        if (plugin != null && plugin.isDungeonWorld(killer.getWorld().getName())) return; // 地牢骷髏改由 UnifiedSkeletonGearDropListener 處理
        if (skeletonSoldierSetManager.getConfig().getDropChance() <= 0) return;
        if (ThreadLocalRandom.current().nextDouble() >= skeletonSoldierSetManager.getConfig().getDropChance()) return;
        String floor = resolveFloor(killer);
        String slot = SkeletonSoldierSetConfig.SLOTS[ThreadLocalRandom.current().nextInt(SkeletonSoldierSetConfig.SLOTS.length)];
        ItemStack piece = skeletonSoldierSetManager.createSkeletonSoldierPiece(slot, floor);
        if (piece != null) {
            skeleton.getWorld().dropItemNaturally(skeleton.getLocation(), piece);
        }
    }

    private String resolveFloor(Player player) {
        if (plugin == null || player == null) return "F1";
        return plugin.getFloorForDungeonWorld(player.getWorld().getName());
    }

    private boolean isDungeonSkeleton(Skeleton entity) {
        if (plugin == null || entity == null) return false;
        String configType = skeletonSoldierSetManager.getConfig().getDropEntityType();
        if (configType == null || configType.isBlank()) return false;
        String type = entity.getPersistentDataContainer().get(
                new NamespacedKey(plugin, PDC_ENTITY_TYPE), PersistentDataType.STRING);
        return configType.equals(type);
    }
}
