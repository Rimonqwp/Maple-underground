package maple.underground_state.wardrobe;

import maple.underground_state.UndergroundState;
import maple.underground_state.gui.WardrobeInfoGui;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class WardrobeSyncListener implements Listener {

    private final UndergroundState plugin;

    public WardrobeSyncListener(@NotNull UndergroundState plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getSlotType() == InventoryType.SlotType.ARMOR
                || WardrobeInfoGui.isArmorItem(event.getCurrentItem())
                || WardrobeInfoGui.isArmorItem(event.getCursor())) {
            syncNextTick(player);
        }
    }

    @EventHandler
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (WardrobeInfoGui.isArmorItem(item)) {
            syncNextTick(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerItemBreak(@NotNull PlayerItemBreakEvent event) {
        if (WardrobeInfoGui.isArmorItem(event.getBrokenItem())) {
            syncNextTick(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerDeath(@NotNull PlayerDeathEvent event) {
        syncNextTick(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        WardrobeInfoGui.syncActiveSet(event.getPlayer());
    }

    private void syncNextTick(@NotNull Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                WardrobeInfoGui.syncActiveSet(player);
            }
        });
    }
}
