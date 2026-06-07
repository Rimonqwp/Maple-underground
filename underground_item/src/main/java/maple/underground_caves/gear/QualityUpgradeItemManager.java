package maple.underground_caves.gear;

import maple.underground_item.util.SkullTextureUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 品質升級道具：玩家頭顱，使用後可將對應物品的品質提升一級。
 * 從 gear/quality_upgrade.yml 讀取顯示名稱與 lore，僅能透過指令取得。
 */
public final class QualityUpgradeItemManager {

    private static final String PDC_QUALITY_UPGRADE = "quality_upgrade";

    private final JavaPlugin plugin;
    private final NamespacedKey key;
    private QualityUpgradeItemConfig config;

    public QualityUpgradeItemManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, PDC_QUALITY_UPGRADE);
        reload();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "gear/quality_upgrade.yml");
        if (!f.exists()) {
            plugin.saveResource("gear/quality_upgrade.yml", false);
            f = new File(plugin.getDataFolder(), "gear/quality_upgrade.yml");
        }
        config = new QualityUpgradeItemConfig(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f));
    }

    public QualityUpgradeItemConfig getConfig() {
        return config;
    }

    public boolean isQualityUpgradeItem(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD || !item.hasItemMeta()) return false;
        Boolean v = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(v);
    }

    /** 建立一個品質升級道具（頭顱 + lore，依 yml 配置）。 */
    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, true);
        meta.displayName(LegacyRgbParser.parse(config.getDisplayName()));
        List<Component> lore = new ArrayList<>();
        for (String line : config.getLore()) {
            lore.add(LegacyRgbParser.parse(line));
        }
        meta.lore(lore);
        maple.underground_item.util.ItemFlagUtil.applyStandardItemFlags(meta);
        if (meta instanceof SkullMeta skullMeta) {
            SkullTextureUtil.applySkullTextureToMeta(skullMeta, config.getTextureId(), "QualityUpgradeItemManager");
        }
        item.setItemMeta(meta);
        return item;
    }

    /** 依當前配置更新已有品質升級道具的顯示名稱與 lore（reload 後刷新背包內物品用）。 */
    public boolean updateItemFromConfig(ItemStack item) {
        if (!isQualityUpgradeItem(item) || item.getItemMeta() == null) return false;
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LegacyRgbParser.parse(config.getDisplayName()));
        List<Component> lore = new ArrayList<>();
        for (String line : config.getLore()) {
            lore.add(LegacyRgbParser.parse(line));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return true;
    }
}
