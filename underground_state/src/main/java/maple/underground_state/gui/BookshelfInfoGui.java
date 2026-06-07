package maple.underground_state.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class BookshelfInfoGui {

    public static final String PAGE_ID = "bookshelf";
    private static final int MENU_SIZE = 54;

    private static final int BOOKSHELF_SLOT = 4;
    private static final int HOE_SLOT = 20;
    private static final int PICKAXE_SLOT = 21;
    private static final int SWORD_SLOT = 22;
    private static final int AXE_SLOT = 23;
    private static final int ROD_SLOT = 24;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 49;

    private static final Component TITLE = Component.text("收藏", NamedTextColor.WHITE);

    private BookshelfInfoGui() {
    }

    public static void open(@NotNull Player player) {
        InfoGuiHolder holder = new InfoGuiHolder(player.getUniqueId(), PAGE_ID);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, TITLE);
        holder.setInventory(inventory);

        ItemStack filler = InfoGuiItems.createFiller(Material.BLACK_STAINED_GLASS_PANE, NamedTextColor.BLACK);
        for (int slot = 0; slot < MENU_SIZE; slot++) {
            inventory.setItem(slot, filler.clone());
        }

        inventory.setItem(BOOKSHELF_SLOT, InfoGuiItems.createNamedItem(Material.BOOKSHELF, "收藏"));
        inventory.setItem(HOE_SLOT, CollectionInfoGui.createCategoryOverviewItem(CollectionInfoGui.Category.FARMING));
        inventory.setItem(PICKAXE_SLOT, CollectionInfoGui.createCategoryOverviewItem(CollectionInfoGui.Category.MINING));
        inventory.setItem(SWORD_SLOT, CollectionInfoGui.createCategoryOverviewItem(CollectionInfoGui.Category.COMBAT));
        inventory.setItem(AXE_SLOT, CollectionInfoGui.createCategoryOverviewItem(CollectionInfoGui.Category.FORAGING));
        inventory.setItem(ROD_SLOT, CollectionInfoGui.createCategoryOverviewItem(CollectionInfoGui.Category.FISHING));
        inventory.setItem(BACK_SLOT, InfoGuiItems.createNamedItem(
                Material.ARROW,
                "返回",
                List.of(Component.text("点击返回信息菜单", NamedTextColor.GRAY))
        ));
        inventory.setItem(CLOSE_SLOT, InfoGuiItems.createCloseItem());

        player.openInventory(inventory);
    }

    public static void handleClick(@NotNull Player player, int rawSlot) {
        if (rawSlot == HOE_SLOT) {
            CollectionInfoGui.open(player, CollectionInfoGui.Category.FARMING);
            return;
        }
        if (rawSlot == PICKAXE_SLOT) {
            CollectionInfoGui.open(player, CollectionInfoGui.Category.MINING);
            return;
        }
        if (rawSlot == SWORD_SLOT) {
            CollectionInfoGui.open(player, CollectionInfoGui.Category.COMBAT);
            return;
        }
        if (rawSlot == AXE_SLOT) {
            CollectionInfoGui.open(player, CollectionInfoGui.Category.FORAGING);
            return;
        }
        if (rawSlot == ROD_SLOT) {
            CollectionInfoGui.open(player, CollectionInfoGui.Category.FISHING);
            return;
        }
        if (rawSlot == BACK_SLOT) {
            MainInfoGui.open(player);
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
        }
    }
}
