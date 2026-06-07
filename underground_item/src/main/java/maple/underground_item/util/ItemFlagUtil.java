package maple.underground_item.util;

import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

public final class ItemFlagUtil {

    private ItemFlagUtil() {
    }

    public static void applyStandardItemFlags(@NotNull ItemMeta meta) {
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
    }
}
