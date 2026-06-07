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
 * 地牢骷髏死亡時只擲一次骰：從「白骨箭手 / 白骨先鋒 / 冥界」三套中至多掉落一件（互斥）。
 * 總體「至少掉一件」機率與改版前一致：1 - (1-a)(1-b)(1-c)；再按比例 a:b:c 分配給三套。
 */
public final class UnifiedSkeletonGearDropListener implements Listener {

    private static final String PDC_ENTITY_TYPE = "dungeon_monster_entity_type";

    private final Test_dungeons plugin;
    private final ArcherSetManager archerSetManager;
    private final SkeletonSoldierSetManager skeletonSoldierSetManager;
    private final SkeletonMasterSetManager skeletonMasterSetManager;

    public UnifiedSkeletonGearDropListener(Test_dungeons plugin,
                                            ArcherSetManager archerSetManager,
                                            SkeletonSoldierSetManager skeletonSoldierSetManager,
                                            SkeletonMasterSetManager skeletonMasterSetManager) {
        this.plugin = plugin;
        this.archerSetManager = archerSetManager;
        this.skeletonSoldierSetManager = skeletonSoldierSetManager;
        this.skeletonMasterSetManager = skeletonMasterSetManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Skeleton skeleton)) return;
        if (!isDungeonSkeleton(skeleton)) return;
        Player killer = skeleton.getKiller();
        if (killer == null) return;
        if (plugin == null || !plugin.isDungeonWorld(killer.getWorld().getName())) return;

        double a = archerSetManager != null ? archerSetManager.getConfig().getDropChance() : 0;
        double b = skeletonSoldierSetManager != null ? skeletonSoldierSetManager.getConfig().getDropChance() : 0;
        double c = skeletonMasterSetManager != null ? skeletonMasterSetManager.getConfig().getDropChance() : 0;
        // 改版前「至少掉一件」機率 = 1 - (1-a)(1-b)(1-c)，維持不變；再按 a:b:c 分配給三套（互斥）
        double totalDropChance = 1.0 - (1.0 - a) * (1.0 - b) * (1.0 - c);
        if (totalDropChance <= 0) return;
        double sum = a + b + c;
        if (sum <= 0) return;
        double archerChance = totalDropChance * (a / sum);
        double soldierChance = totalDropChance * (b / sum);
        double masterChance = totalDropChance * (c / sum);

        double r = ThreadLocalRandom.current().nextDouble();
        String floor = plugin.getFloorForDungeonWorld(killer.getWorld().getName());

        if (r < archerChance && archerSetManager != null) {
            String slot = ArcherSetConfig.SLOTS[ThreadLocalRandom.current().nextInt(ArcherSetConfig.SLOTS.length)];
            ItemStack piece = archerSetManager.createArcherPiece(slot, floor);
            if (piece != null) skeleton.getWorld().dropItemNaturally(skeleton.getLocation(), piece);
            return;
        }
        r -= archerChance;
        if (r < soldierChance && skeletonSoldierSetManager != null) {
            String slot = SkeletonSoldierSetConfig.SLOTS[ThreadLocalRandom.current().nextInt(SkeletonSoldierSetConfig.SLOTS.length)];
            ItemStack piece = skeletonSoldierSetManager.createSkeletonSoldierPiece(slot, floor);
            if (piece != null) skeleton.getWorld().dropItemNaturally(skeleton.getLocation(), piece);
            return;
        }
        r -= soldierChance;
        if (r < masterChance && skeletonMasterSetManager != null) {
            String slot = SkeletonMasterSetConfig.SLOTS[ThreadLocalRandom.current().nextInt(SkeletonMasterSetConfig.SLOTS.length)];
            ItemStack piece = skeletonMasterSetManager.createSkeletonMasterPiece(slot, floor);
            if (piece != null) skeleton.getWorld().dropItemNaturally(skeleton.getLocation(), piece);
        }
    }

    private boolean isDungeonSkeleton(Skeleton entity) {
        if (plugin == null || entity == null) return false;
        String type = entity.getPersistentDataContainer().get(
                new NamespacedKey(plugin, PDC_ENTITY_TYPE), PersistentDataType.STRING);
        return "SKELETON".equals(type);
    }
}
