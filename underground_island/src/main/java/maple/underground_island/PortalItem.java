package maple.underground_island;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

final class PortalItem {
    private final NamespacedKey key;

    PortalItem(JavaPlugin plugin) {
        this.key = new NamespacedKey(plugin, "portal_block");
    }

    ItemStack create() {
        ItemStack item = new ItemStack(Material.OBSIDIAN);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("传送门方块", NamedTextColor.LIGHT_PURPLE));
            meta.lore(List.of(
                    Component.text("放置后会自动建造通往主城的传送门。", NamedTextColor.GRAY),
                    Component.text("仅可在岛屿世界使用。", NamedTextColor.DARK_GRAY)
            ));
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    boolean isPortalBlock(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
