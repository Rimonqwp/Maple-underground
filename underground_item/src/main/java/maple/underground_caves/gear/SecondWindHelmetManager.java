package maple.underground_caves.gear;

import maple.underground_item.util.SkullTextureUtil;
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

/** 絕境逢生頭盔：瀕死時觸發速度與傷害免疫，冷卻 30 秒 */
public final class SecondWindHelmetManager {

    private static final String PDC_SECOND_WIND_HELMET = "second_wind_helmet";
    public static final String HELMET_ID = "second_wind_helmet";

    private final JavaPlugin plugin;
    private final NamespacedKey keyHelmet;
    private SecondWindHelmetConfig config;

    public SecondWindHelmetManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.keyHelmet = new NamespacedKey(plugin, PDC_SECOND_WIND_HELMET);
        reload();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "gear/" + HELMET_ID + ".yml");
        if (!f.exists()) {
            plugin.saveResource("gear/" + HELMET_ID + ".yml", false);
            f = new File(plugin.getDataFolder(), "gear/" + HELMET_ID + ".yml");
        }
        config = new SecondWindHelmetConfig(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f));
    }

    public NamespacedKey getKeyHelmet() { return keyHelmet; }

    public boolean isSecondWindHelmet(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD || !item.hasItemMeta()) return false;
        return Boolean.TRUE.equals(item.getItemMeta().getPersistentDataContainer().get(keyHelmet, PersistentDataType.BOOLEAN));
    }

    public boolean isWearingSecondWindHelmet(Player player) {
        return player != null && isSecondWindHelmet(player.getInventory().getHelmet());
    }

    public int getBonusHealth(Player player) {
        return (player != null && isWearingSecondWindHelmet(player) && config != null) ? config.getStatHealth() : 0;
    }
    public int getBonusDefense(Player player) {
        return (player != null && isWearingSecondWindHelmet(player) && config != null) ? config.getStatDefense() : 0;
    }
    public int getBonusSpeed(Player player) {
        return (player != null && isWearingSecondWindHelmet(player) && config != null) ? config.getStatSpeed() : 0;
    }
    public int getBonusIntelligence(Player player) {
        return (player != null && isWearingSecondWindHelmet(player) && config != null) ? config.getStatIntelligence() : 0;
    }

    public SecondWindHelmetConfig getConfig() { return config; }
    public JavaPlugin getPlugin() { return plugin; }

    public ItemStack createHelmet() {
        if (config == null) return null;
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.getPersistentDataContainer().set(keyHelmet, PersistentDataType.BOOLEAN, true);
        meta.displayName(LegacyRgbParser.parse(config.getDisplayName()));
        List<Component> lore = new ArrayList<>();
        for (String line : config.getLore()) {
            lore.add(LegacyRgbParser.parse(line
                    .replace("{health}", String.valueOf(config.getStatHealth()))
                    .replace("{defense}", String.valueOf(config.getStatDefense()))
                    .replace("{intelligence}", String.valueOf(config.getStatIntelligence()))
                    .replace("{speed}", String.valueOf(config.getStatSpeed()))
                    .replace("{speed_bonus}", String.valueOf(config.getEffectSpeedBonus()))
                    .replace("{duration_seconds}", String.valueOf(config.getEffectDurationSeconds()))
                    .replace("{cooldown_seconds}", String.valueOf(config.getCooldownSeconds()))));
        }
        meta.lore(lore);
        maple.underground_item.util.ItemFlagUtil.applyStandardItemFlags(meta);
        if (meta instanceof SkullMeta skullMeta) {
            String tid = config.getTextureId();
            if (tid != null && !tid.isBlank()) SkullTextureUtil.applySkullTextureToMeta(skullMeta, tid.trim(), "SecondWindHelmet");
        }
        item.setItemMeta(meta);
        return item;
    }
}
