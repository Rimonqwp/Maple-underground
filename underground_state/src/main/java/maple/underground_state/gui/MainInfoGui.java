package maple.underground_state.gui;

import maple.underground_state.UndergroundState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public final class MainInfoGui {

    public static final String PAGE_ID = "main";
    private static final int MENU_SIZE = 54;

    private static final int PLAYER_HEAD_SLOT = 13;
    private static final int SWORD_SLOT = 19;
    private static final int BOOKSHELF_SLOT = 20;
    private static final int ITEM_FRAME_SLOT = 21;
    private static final int WRITABLE_BOOK_SLOT = 22;
    private static final int BOOK_SLOT = 23;
    private static final int CHEST_MINECART_SLOT = 24;
    private static final int ENDER_CHEST_SLOT = 25;
    private static final int BUNDLE_SLOT = 28;
    private static final int BANK_SLOT = 30;
    private static final int CRAFTING_TABLE_SLOT = 31;
    private static final int COPPER_CHESTPLATE_SLOT = 32;
    private static final int LEATHER_CHESTPLATE_SLOT = 34;
    private static final int CLOSE_SLOT = 49;
    private static final int REDSTONE_TORCH_SLOT = 50;

    private static final int[] GLASS_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
            10, 11, 12, 14, 15, 16, 17, 18,
            26, 27, 29, 33, 35,
            36, 37, 38, 39, 40, 41, 42, 43,
            44, 45, 46, 47, 48, 51, 52, 53
    };

    private static final Component TITLE = Component.text("主頁", NamedTextColor.WHITE);
    private static final TextColor NAME_RGB = TextColor.color(0x63E6A6);
    private static final TextColor ACTION_RGB = TextColor.color(0xFFE36E);
    private static final TextColor SYMBOL_RGB = TextColor.color(0xE0A83A);

    private MainInfoGui() {
    }

    public static void open(@NotNull Player player) {
        InfoGuiHolder holder = new InfoGuiHolder(player.getUniqueId(), PAGE_ID);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, TITLE);
        holder.setInventory(inventory);

        ItemStack filler = InfoGuiItems.createFiller(Material.BLACK_STAINED_GLASS_PANE, NamedTextColor.BLACK);
        Arrays.stream(GLASS_SLOTS).forEach(slot -> inventory.setItem(slot, filler.clone()));

        inventory.setItem(PLAYER_HEAD_SLOT, InfoGuiItems.createPlayerHead(player));
        inventory.setItem(SWORD_SLOT, InfoGuiItems.createNamedItem(
                Material.COPPER_SWORD,
                nameText("你的技能等級"),
                List.of(
                        Component.text("查看您的技能進度和獎勵。", NamedTextColor.GRAY),
                        Component.empty(),
                        actionText("點擊查看！")
                )
        ));
        inventory.setItem(BOOKSHELF_SLOT, InfoGuiItems.createNamedItem(
                Material.BOOKSHELF,
                nameText("收藏"),
                List.of(
                        Component.text("收集更多物品，解鎖獎勵，幫助你成為 楓葉伺服器 高手！", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("收藏物品已解鎖: ", NamedTextColor.GRAY)
                                .append(Component.text("0", ACTION_RGB))
                                .append(Component.text("%", SYMBOL_RGB)),
                        progressLine(),
                        Component.empty(),
                        actionText("點擊查看！")
                )
        ));
        inventory.setItem(ITEM_FRAME_SLOT, InfoGuiItems.createNamedItem(
                Material.ITEM_FRAME,
                nameText("配方書"),
                List.of(
                        Component.text("在你的地下城旅程中，你將解鎖各種特殊物品的配方！", NamedTextColor.GRAY),
                        Component.text("你可以在這裡查看這些物品。", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("配方書已解鎖: ", NamedTextColor.GRAY)
                                .append(Component.text("0", ACTION_RGB))
                                .append(Component.text("%", SYMBOL_RGB)),
                        progressLine(),
                        Component.empty(),
                        actionText("開發中")
                )
        ));
        inventory.setItem(WRITABLE_BOOK_SLOT, InfoGuiItems.createNamedItem(
                Material.WRITABLE_BOOK,
                nameText("任務日誌"),
                List.of(
                        Component.text("查看你目前正在進行的任務、目標與完成進度。", NamedTextColor.GRAY),
                        Component.empty(),
                        actionText("點擊查看！")
                )
        ));
        inventory.setItem(BOOK_SLOT, InfoGuiItems.createNamedItem(
                Material.BOOK,
                nameText("傳送點"),
                List.of(
                        Component.text("查看你目前已解鎖的傳送點並快速前往各個地區。", NamedTextColor.GRAY),
                        Component.empty(),
                        actionText("點擊查看！")
                )
        ));
        inventory.setItem(CHEST_MINECART_SLOT, InfoGuiItems.createNamedItem(
                Material.CHEST_MINECART,
                nameText("公會"),
                List.of(
                        Component.text("查看你的公會資訊、成員名單與目前的公會進度。", NamedTextColor.GRAY),
                        Component.empty(),
                        actionText("點擊查看！")
                )
        ));
        inventory.setItem(ENDER_CHEST_SLOT, InfoGuiItems.createNamedItem(
                Material.ENDER_CHEST,
                nameText("背包"),
                List.of(
                        Component.text("查看你目前隨身攜帶的物品與空間配置。", NamedTextColor.GRAY),
                        Component.empty(),
                        actionText("點擊查看！")
                )
        ));
        inventory.setItem(BUNDLE_SLOT, InfoGuiItems.createNamedItem(
                Material.BUNDLE,
                nameText("寵物"),
                List.of(
                        Component.text("查看你目前擁有的寵物與牠們提供的能力效果。", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("目前裝備: ", NamedTextColor.GRAY)
                                .append(Component.text("[ ", SYMBOL_RGB))
                                .append(Component.text("Lv.", SYMBOL_RGB))
                                .append(Component.text("100", ACTION_RGB))
                                .append(Component.text(" ] ", SYMBOL_RGB))
                                .append(Component.text("無", ACTION_RGB)),
                        Component.empty(),
                        actionText("開發中")
                )
        ));
        inventory.setItem(BANK_SLOT, InfoGuiItems.createCustomHead(
                "http://textures.minecraft.net/texture/84b32710cea560854e3fac192f20a633f729afa02de4b075fefb680b301658e0",
                nameText("銀行"),
                List.of(
                        Component.text("查看你的存款、提款與目前的金幣管理狀態。", NamedTextColor.GRAY),
                        Component.empty(),
                        actionText("點擊查看！")
                )
        ));
        inventory.setItem(CRAFTING_TABLE_SLOT, InfoGuiItems.createNamedItem(
                Material.CRAFTING_TABLE,
                nameText("工作台"),
                List.of(
                        Component.text("查看可製作的物品，並在這裡進行合成與製作。", NamedTextColor.GRAY),
                        Component.empty(),
                        actionText("開發中")
                )
        ));
        inventory.setItem(COPPER_CHESTPLATE_SLOT, InfoGuiItems.createNamedItem(
                Material.COPPER_CHESTPLATE,
                nameText("衣櫃"),
                List.of(
                        Component.text("查看並整理你目前收藏的裝備與穿搭配置。", NamedTextColor.GRAY),
                        Component.empty(),
                        actionText("點擊查看！")
                )
        ));
        inventory.setItem(LEATHER_CHESTPLATE_SLOT, InfoGuiItems.createColoredLeatherChestplate(
                nameText("身份組外觀"),
                List.of(
                        Component.text("先用固定顏色占位，之後再接身份組判斷。", NamedTextColor.GRAY),
                        Component.empty(),
                        actionText("開發中")
                ),
                Color.fromRGB(0x98, 0xF5, 0xB3)
        ));
        inventory.setItem(CLOSE_SLOT, InfoGuiItems.createCloseItem());
        inventory.setItem(REDSTONE_TORCH_SLOT, InfoGuiItems.createNamedItem(
                Material.REDSTONE_TORCH,
                nameText("個人設定"),
                List.of(
                        Component.text("調整你的個人偏好設定與介面顯示選項。", NamedTextColor.GRAY),
                        Component.empty(),
                        actionText("點擊查看！")
                )
        ));

        player.openInventory(inventory);
    }

    public static void handleClick(@NotNull Player player, int rawSlot) {
        if (rawSlot == SWORD_SLOT) {
            CopperSwordMenuGui.open(player);
            return;
        }
        if (rawSlot == BOOKSHELF_SLOT) {
            BookshelfInfoGui.open(player);
            return;
        }
        if (rawSlot == WRITABLE_BOOK_SLOT) {
            player.closeInventory();
            UndergroundState plugin = UndergroundState.getInstance();
            if (plugin == null || !plugin.isEnabled()) {
                player.performCommand("t");
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.performCommand("t");
                }
            });
            return;
        }
        if (rawSlot == BANK_SLOT) {
            BankInfoGui.open(player);
            return;
        }
        if (rawSlot == CHEST_MINECART_SLOT) {
            GuildInfoGui.open(player);
            return;
        }
        if (rawSlot == ENDER_CHEST_SLOT) {
            BackpackInfoGui.open(player);
            return;
        }
        if (rawSlot == COPPER_CHESTPLATE_SLOT) {
            WardrobeInfoGui.open(player);
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
        }
    }

    private static Component progressLine() {
        return Component.text("                         ", NamedTextColor.DARK_GRAY, TextDecoration.STRIKETHROUGH)
                .append(Component.text(" ").decoration(TextDecoration.STRIKETHROUGH, false))
                .append(Component.text("0", ACTION_RGB).decoration(TextDecoration.STRIKETHROUGH, false))
                .append(Component.text("/", SYMBOL_RGB).decoration(TextDecoration.STRIKETHROUGH, false))
                .append(Component.text("1", ACTION_RGB).decoration(TextDecoration.STRIKETHROUGH, false));
    }

    private static Component nameText(String text) {
        return Component.text(text, NAME_RGB)
                .decoration(TextDecoration.ITALIC, false)
                .decorate(TextDecoration.BOLD);
    }

    private static Component actionText(String text) {
        return Component.text(text, ACTION_RGB)
                .decoration(TextDecoration.ITALIC, false)
                .decorate(TextDecoration.BOLD);
    }
}
