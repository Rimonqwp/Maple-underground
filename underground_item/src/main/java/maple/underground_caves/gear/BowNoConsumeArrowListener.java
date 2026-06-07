package maple.underground_caves.gear;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 當玩家穿著冥界套裝胸甲時，弓射擊不消耗箭矢（射擊後補回 1 支箭）。
 */
public final class BowNoConsumeArrowListener implements Listener {

    private final SkeletonMasterSetManager skeletonMasterSetManager;
    private final JavaPlugin plugin;

    public BowNoConsumeArrowListener(SkeletonMasterSetManager skeletonMasterSetManager, JavaPlugin plugin) {
        this.skeletonMasterSetManager = skeletonMasterSetManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (skeletonMasterSetManager == null || !skeletonMasterSetManager.hasSkeletonMasterChestplate(player)) return;
        // 箭已在事件中被消耗，下一 tick 補回 1 支
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            ItemStack arrow = new ItemStack(Material.ARROW, 1);
            if (player.getInventory().firstEmpty() >= 0) {
                player.getInventory().addItem(arrow);
            } else {
                player.getWorld().dropItemNaturally(player.getLocation(), arrow);
            }
        }, 1L);
    }
}
