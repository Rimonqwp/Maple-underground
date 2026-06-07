package maple.underground_caves.gear;

import maple.underground_caves.dungeon.DungeonSecrets;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
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
 * 不死頭顱：建立頭顱物品（自訂材質）、PDC 標記、裝備時屬性加成。
 * 免死與冷卻由 ImmortalSkullListener 處理。
 */
public final class ImmortalSkullManager {

    private static final String PDC_IMMORTAL_SKULL = "immortal_skull";

    private final JavaPlugin plugin;
    private final NamespacedKey keyImmortal;
    private ImmortalSkullConfig config;

    public ImmortalSkullManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.keyImmortal = new NamespacedKey(plugin, PDC_IMMORTAL_SKULL);
        reload();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "gear/immortal_skull.yml");
        if (!f.exists()) {
            plugin.saveResource("gear/immortal_skull.yml", false);
            f = new File(plugin.getDataFolder(), "gear/immortal_skull.yml");
        }
        config = new ImmortalSkullConfig(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f));
    }

    public ImmortalSkullConfig getConfig() {
        return config;
    }

    public boolean isImmortalSkull(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD || !item.hasItemMeta()) return false;
        Boolean v = item.getItemMeta().getPersistentDataContainer().get(keyImmortal, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(v);
    }

    public boolean isWearingSkull(Player player) {
        if (player == null) return false;
        ItemStack helmet = player.getInventory().getHelmet();
        return isImmortalSkull(helmet);
    }

    /** 裝備不死頭顱時額外生命（未裝備則 0）。 */
    public int getBonusHealth(Player player) {
        return isWearingSkull(player) ? config.getStatHealth() : 0;
    }

    /** 裝備不死頭顱時額外防禦（未裝備則 0）。 */
    public int getBonusDefense(Player player) {
        return isWearingSkull(player) ? config.getStatDefense() : 0;
    }

    /** 裝備不死頭顱時額外智力（未裝備則 0）。 */
    public int getBonusIntelligence(Player player) {
        return isWearingSkull(player) ? config.getStatIntelligence() : 0;
    }

    /** 建立一個不死頭顱物品（僅能透過指令取得，不會掉落）。 */
    public ItemStack createImmortalSkull() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.getPersistentDataContainer().set(keyImmortal, PersistentDataType.BOOLEAN, true);
        meta.displayName(LegacyRgbParser.parse(config.getDisplayName()));
        List<Component> lore = new ArrayList<>();
        for (String line : config.getLore()) {
            line = line.replace("{health}", String.valueOf(config.getStatHealth()))
                    .replace("{defense}", String.valueOf(config.getStatDefense()))
                    .replace("{intelligence}", String.valueOf(config.getStatIntelligence()))
                    .replace("{cooldown_seconds}", String.valueOf(config.getCooldownSeconds()))
                    .replace("{effect_duration_seconds}", String.valueOf(config.getEffectDurationSeconds()))
                    .replace("{strength_bonus}", String.valueOf(config.getEffectStrengthBonus()));
            lore.add(LegacyRgbParser.parse(line));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        if (meta instanceof SkullMeta skullMeta) {
            DungeonSecrets.applySkullTextureToMeta(skullMeta, config.getTextureId(), "ImmortalSkullManager");
        }
        item.setItemMeta(meta);
        return item;
    }

    /** 依當前配置更新已有永恆的面具物品的顯示名稱與 lore（reload 後刷新背包內物品用）。 */
    public boolean updateItemFromConfig(ItemStack item) {
        if (!isImmortalSkull(item) || item.getItemMeta() == null) return false;
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LegacyRgbParser.parse(config.getDisplayName()));
        List<Component> lore = new ArrayList<>();
        for (String line : config.getLore()) {
            line = line.replace("{health}", String.valueOf(config.getStatHealth()))
                    .replace("{defense}", String.valueOf(config.getStatDefense()))
                    .replace("{intelligence}", String.valueOf(config.getStatIntelligence()))
                    .replace("{cooldown_seconds}", String.valueOf(config.getCooldownSeconds()))
                    .replace("{effect_duration_seconds}", String.valueOf(config.getEffectDurationSeconds()))
                    .replace("{strength_bonus}", String.valueOf(config.getEffectStrengthBonus()));
            lore.add(LegacyRgbParser.parse(line));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return true;
    }
}
