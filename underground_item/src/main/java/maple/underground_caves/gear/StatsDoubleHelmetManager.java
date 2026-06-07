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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 黃金／鑽石村民／熊貓／綿羊頭盔：lore 顯示 x1 數值，戴上時實際屬性 x2。
 */
public final class StatsDoubleHelmetManager {

    private static final String PDC_STATS_DOUBLE_HELMET_ID = "stats_double_helmet_id";

    public static final String[] HELMET_IDS = {
            "golden_villager_helmet",
            "golden_panda_helmet",
            "golden_sheep_helmet",
            "diamond_villager_helmet",
            "diamond_panda_helmet",
            "diamond_sheep_helmet"
    };

    private static final int EQUIPPED_STAT_MULTIPLIER = 2;

    private final JavaPlugin plugin;
    private final NamespacedKey keyHelmetId;
    private final Map<String, StatsDoubleHelmetConfig> configs = new HashMap<>();

    public StatsDoubleHelmetManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.keyHelmetId = new NamespacedKey(plugin, PDC_STATS_DOUBLE_HELMET_ID);
        reload();
    }

    public void reload() {
        configs.clear();
        for (String id : HELMET_IDS) {
            String path = "gear/" + id + ".yml";
            File f = new File(plugin.getDataFolder(), path);
            if (!f.exists()) {
                plugin.saveResource(path, false);
                f = new File(plugin.getDataFolder(), path);
            }
            configs.put(id, new StatsDoubleHelmetConfig(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f)));
        }
    }

    public NamespacedKey getKeyHelmetId() {
        return keyHelmetId;
    }

    public boolean isStatsDoubleHelmet(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD || !item.hasItemMeta()) return false;
        String id = item.getItemMeta().getPersistentDataContainer().get(keyHelmetId, PersistentDataType.STRING);
        return id != null && configs.containsKey(id);
    }

    public String getHelmetId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(keyHelmetId, PersistentDataType.STRING);
    }

    /** 裝備時實際加成 = YAML 數值 × 2（lore 顯示 x1）。 */
    public int getBonusHealth(Player player) {
        if (player == null) return 0;
        ItemStack helmet = player.getInventory().getHelmet();
        String id = getHelmetId(helmet);
        if (id == null || !configs.containsKey(id)) return 0;
        return configs.get(id).getStatHealth() * EQUIPPED_STAT_MULTIPLIER;
    }

    public int getBonusDefense(Player player) {
        if (player == null) return 0;
        ItemStack helmet = player.getInventory().getHelmet();
        String id = getHelmetId(helmet);
        if (id == null || !configs.containsKey(id)) return 0;
        return configs.get(id).getStatDefense() * EQUIPPED_STAT_MULTIPLIER;
    }

    public int getBonusStrength(Player player) {
        if (player == null) return 0;
        ItemStack helmet = player.getInventory().getHelmet();
        String id = getHelmetId(helmet);
        if (id == null || !configs.containsKey(id)) return 0;
        return configs.get(id).getStatStrength() * EQUIPPED_STAT_MULTIPLIER;
    }

    public List<String> getHelmetIds() {
        return List.of(HELMET_IDS);
    }

    public ItemStack createHelmet(String id) {
        if (id == null || !configs.containsKey(id)) return null;
        StatsDoubleHelmetConfig cfg = configs.get(id);
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.getPersistentDataContainer().set(keyHelmetId, PersistentDataType.STRING, id);
        meta.displayName(LegacyRgbParser.parse(cfg.getDisplayName()));
        int h = cfg.getStatHealth();
        int d = cfg.getStatDefense();
        int s = cfg.getStatStrength();
        List<Component> lore = new ArrayList<>();
        for (String line : cfg.getLore()) {
            String replaced = line.replace("{health}", String.valueOf(h))
                    .replace("{defense}", String.valueOf(d))
                    .replace("{strength}", String.valueOf(s));
            lore.add(LegacyRgbParser.parse(replaced));
        }
        meta.lore(lore);
        maple.underground_item.util.ItemFlagUtil.applyStandardItemFlags(meta);
        if (meta instanceof SkullMeta skullMeta) {
            String textureId = cfg.getTextureId();
            if (textureId != null && !textureId.isBlank()) {
                SkullTextureUtil.applySkullTextureToMeta(skullMeta, textureId.trim(), "StatsDoubleHelmetManager." + id);
            }
        }
        item.setItemMeta(meta);
        return item;
    }
}
