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

/** 遠程蓄勢頭盔：箭矢飛行超過 N 格後每 M 格增傷 X% */
public final class ArrowDistanceHelmetManager {

    private static final String PDC_ARROW_DISTANCE_HELMET = "arrow_distance_helmet";
    public static final String HELMET_ID = "arrow_distance_helmet";

    private final JavaPlugin plugin;
    private final NamespacedKey keyHelmet;
    private ArrowDistanceHelmetConfig config;

    public ArrowDistanceHelmetManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.keyHelmet = new NamespacedKey(plugin, PDC_ARROW_DISTANCE_HELMET);
        reload();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "gear/" + HELMET_ID + ".yml");
        if (!f.exists()) {
            plugin.saveResource("gear/" + HELMET_ID + ".yml", false);
            f = new File(plugin.getDataFolder(), "gear/" + HELMET_ID + ".yml");
        }
        config = new ArrowDistanceHelmetConfig(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f));
    }

    public NamespacedKey getKeyHelmet() { return keyHelmet; }

    public boolean isArrowDistanceHelmet(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD || !item.hasItemMeta()) return false;
        return Boolean.TRUE.equals(item.getItemMeta().getPersistentDataContainer().get(keyHelmet, PersistentDataType.BOOLEAN));
    }

    public boolean isWearingArrowDistanceHelmet(Player player) {
        return player != null && isArrowDistanceHelmet(player.getInventory().getHelmet());
    }

    public int getBonusHealth(Player player) {
        return (player != null && isWearingArrowDistanceHelmet(player) && config != null) ? config.getStatHealth() : 0;
    }
    public int getBonusDefense(Player player) {
        return (player != null && isWearingArrowDistanceHelmet(player) && config != null) ? config.getStatDefense() : 0;
    }

    public ArrowDistanceHelmetConfig getConfig() { return config; }
    public JavaPlugin getPlugin() { return plugin; }

    /** 依飛行距離計算傷害倍率（≥ base 時每 step 格 + damagePercentPerStep%） */
    public double getDamageMultiplier(double distanceBlocks) {
        if (config == null || distanceBlocks < config.getBaseBlocks()) return 1.0;
        double step = config.getStepBlocks();
        if (step <= 0) return 1.0;
        double steps = Math.floor((distanceBlocks - config.getBaseBlocks()) / step);
        double percent = steps * config.getDamagePercentPerStep();
        return 1.0 + percent / 100.0;
    }

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
                    .replace("{defense}", String.valueOf(config.getStatDefense()))));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        if (meta instanceof SkullMeta skullMeta) {
            String tid = config.getTextureId();
            if (tid != null && !tid.isBlank()) DungeonSecrets.applySkullTextureToMeta(skullMeta, tid.trim(), "ArrowDistanceHelmet");
        }
        item.setItemMeta(meta);
        return item;
    }
}
