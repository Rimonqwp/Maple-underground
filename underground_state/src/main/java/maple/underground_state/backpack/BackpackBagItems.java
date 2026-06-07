package maple.underground_state.backpack;

import maple.underground_state.UndergroundState;
import maple.underground_state.gui.InfoGuiItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class BackpackBagItems {

    public static final int MIN_ROWS = 1;
    public static final int MAX_ROWS = 5;

    private static final TextColor NAME_RGB = TextColor.color(0x63E6A6);
    private static final TextColor ACTION_RGB = TextColor.color(0xFFE36E);
    private static final String[] TEXTURES = {
            "",
            "http://textures.minecraft.net/texture/1e583cb771581d3b27b23f617c7b8a43dcd7220447ff95ff1960414d2350bdb9",
            "http://textures.minecraft.net/texture/ddaf8edc32afb461aee0713058023101f924e2a7efa883dae72d5d57d4c053d7",
            "http://textures.minecraft.net/texture/cf87525ad84efd16806a26ca01984b280e5ba6403505b6f6c9803c24642abfc7",
            "http://textures.minecraft.net/texture/10d1b0732bf7a70de4dc01559cc5c9811068ef7b6095010382709f94093927f6",
            "http://textures.minecraft.net/texture/835d7cc09fffbca3e1c00d421afaa432cf71fcb09555f54523e5220d1af0f97d"
    };

    private BackpackBagItems() {
    }

    public static @NotNull ItemStack createBag(int rows) {
        int safeRows = clampRows(rows);
        ItemStack item = InfoGuiItems.createCustomHead(
                texture(safeRows),
                nameText(sizeName(safeRows)),
                List.of(
                        bodyText("可装在背包总览里的额外储物袋。"),
                        bodyText("适合存放材料、补给与临时战利品。"),
                        Component.empty(),
                        bodyText("容量: " + safeRows + " 行 / " + (safeRows * 9) + " 格"),
                        Component.empty(),
                        actionText("在背包总览中点击使用")
                )
        );
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(rowsKey(), PersistentDataType.INTEGER, safeRows);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isBag(@Nullable ItemStack item) {
        return rows(item) > 0;
    }

    public static int rows(@Nullable ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return 0;
        }
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        Integer rows = container.get(rowsKey(), PersistentDataType.INTEGER);
        if (rows == null || rows < MIN_ROWS || rows > MAX_ROWS) {
            return 0;
        }
        return rows;
    }

    public static @NotNull ItemStack createPlacedBag(int rows, int slot) {
        int safeRows = clampRows(rows);
        return InfoGuiItems.createCustomHead(
                texture(safeRows),
                nameText(sizeName(safeRows) + " #" + slot),
                List.of(
                        bodyText("已装入背包总览的额外储物袋。"),
                        bodyText("容量: " + safeRows + " 行 / " + (safeRows * 9) + " 格"),
                        Component.empty(),
                        actionText("点击打开")
                )
        );
    }

    public static @NotNull String texture(int rows) {
        return TEXTURES[clampRows(rows)];
    }

    public static @NotNull String sizeName(int rows) {
        return switch (clampRows(rows)) {
            case 1 -> "超小型袋子";
            case 2 -> "小型袋子";
            case 3 -> "中型袋子";
            case 4 -> "大型袋子";
            case 5 -> "超大型袋子";
            default -> "袋子";
        };
    }

    private static int clampRows(int rows) {
        return Math.max(MIN_ROWS, Math.min(MAX_ROWS, rows));
    }

    private static @NotNull NamespacedKey rowsKey() {
        return new NamespacedKey(UndergroundState.getInstance(), "backpack_bag_rows");
    }

    private static @NotNull Component nameText(@NotNull String text) {
        return Component.text(text, NAME_RGB)
                .decoration(TextDecoration.ITALIC, false)
                .decorate(TextDecoration.BOLD);
    }

    private static @NotNull Component actionText(@NotNull String text) {
        return Component.text(text, ACTION_RGB)
                .decoration(TextDecoration.ITALIC, false)
                .decorate(TextDecoration.BOLD);
    }

    private static @NotNull Component bodyText(@NotNull String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }
}
