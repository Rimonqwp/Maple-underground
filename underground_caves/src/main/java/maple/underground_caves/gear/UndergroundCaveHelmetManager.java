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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 地下洞穴頭盔：F1 / F2 / F3 三種獨立頭盔，各自材質與固定屬性。
 */
public final class UndergroundCaveHelmetManager {

    private static final String PDC_CAVE_HELMET = "underground_cave_helmet";
    private static final String PDC_CAVE_HELMET_FLOOR = "cave_helmet_floor";

    private static final String FLOOR_F1 = "F1";
    private static final String FLOOR_F2 = "F2";
    private static final String FLOOR_F3 = "F3";

    private final JavaPlugin plugin;
    private final NamespacedKey keyCaveHelmet;
    private final NamespacedKey keyCaveHelmetFloor;
    private final Map<String, UndergroundCaveHelmetConfig> configs = new HashMap<>();

    public UndergroundCaveHelmetManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.keyCaveHelmet = new NamespacedKey(plugin, PDC_CAVE_HELMET);
        this.keyCaveHelmetFloor = new NamespacedKey(plugin, PDC_CAVE_HELMET_FLOOR);
        reload();
    }

    public void reload() {
        configs.clear();
        loadConfigForFloor(FLOOR_F1, "gear/underground_cave_helmet_f1.yml");
        loadConfigForFloor(FLOOR_F2, "gear/underground_cave_helmet_f2.yml");
        loadConfigForFloor(FLOOR_F3, "gear/underground_cave_helmet_f3.yml");
    }

    private void loadConfigForFloor(String floor, String path) {
        File f = new File(plugin.getDataFolder(), path);
        if (!f.exists()) {
            plugin.saveResource(path, false);
            f = new File(plugin.getDataFolder(), path);
        }
        configs.put(floor, new UndergroundCaveHelmetConfig(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f)));
    }

    public static boolean isValidFloor(String floor) {
        if (floor == null) return false;
        String f = floor.toUpperCase(Locale.ROOT);
        return FLOOR_F1.equals(f) || FLOOR_F2.equals(f) || FLOOR_F3.equals(f);
    }

    public static String toValidFloor(String floor) {
        if (floor == null) return FLOOR_F1;
        String f = floor.toUpperCase(Locale.ROOT);
        if (FLOOR_F1.equals(f) || FLOOR_F2.equals(f) || FLOOR_F3.equals(f)) return f;
        return FLOOR_F1;
    }

    public boolean isCaveHelmet(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD || !item.hasItemMeta()) return false;
        Boolean v = item.getItemMeta().getPersistentDataContainer().get(keyCaveHelmet, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(v);
    }

    /** 取得頭盔的樓層（F1/F2/F3），非本裝備或無則回傳 F1。 */
    public String getFloor(ItemStack item) {
        if (!isCaveHelmet(item) || !item.hasItemMeta()) return FLOOR_F1;
        String f = item.getItemMeta().getPersistentDataContainer().get(keyCaveHelmetFloor, PersistentDataType.STRING);
        return isValidFloor(f) ? toValidFloor(f) : FLOOR_F1;
    }

    public boolean isWearingCaveHelmet(Player player) {
        if (player == null) return false;
        ItemStack helmet = player.getInventory().getHelmet();
        return isCaveHelmet(helmet);
    }

    /** 裝備時額外力量（依頭盔樓層固定屬性）。 */
    public int getBonusStrength(Player player) {
        if (!isWearingCaveHelmet(player) || configs.isEmpty()) return 0;
        ItemStack helmet = player.getInventory().getHelmet();
        UndergroundCaveHelmetConfig cfg = configs.get(getFloor(helmet));
        return cfg != null ? cfg.getStatStrength() : 0;
    }

    /** 裝備時額外生命（依頭盔樓層固定屬性）。 */
    public int getBonusHealth(Player player) {
        if (!isWearingCaveHelmet(player) || configs.isEmpty()) return 0;
        ItemStack helmet = player.getInventory().getHelmet();
        UndergroundCaveHelmetConfig cfg = configs.get(getFloor(helmet));
        return cfg != null ? cfg.getStatHealth() : 0;
    }

    /** 裝備時額外防禦（依頭盔樓層固定屬性）。 */
    public int getBonusDefense(Player player) {
        if (!isWearingCaveHelmet(player) || configs.isEmpty()) return 0;
        ItemStack helmet = player.getInventory().getHelmet();
        UndergroundCaveHelmetConfig cfg = configs.get(getFloor(helmet));
        return cfg != null ? cfg.getStatDefense() : 0;
    }

    /** 建立指定樓層的地下洞穴頭盔（F1 / F2 / F3）。 */
    public ItemStack createCaveHelmet(String floor) {
        String f = toValidFloor(floor);
        UndergroundCaveHelmetConfig cfg = configs.get(f);
        if (cfg == null && !configs.isEmpty()) {
            cfg = configs.get(FLOOR_F1);
            f = FLOOR_F1;
        }
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.getPersistentDataContainer().set(keyCaveHelmet, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(keyCaveHelmetFloor, PersistentDataType.STRING, f);
        meta.displayName(LegacyRgbParser.parse(cfg.getDisplayName()));
        int s = cfg.getStatStrength();
        int h = cfg.getStatHealth();
        int d = cfg.getStatDefense();
        List<Component> lore = new ArrayList<>();
        for (String line : cfg.getLore()) {
            line = line.replace("{strength}", String.valueOf(s))
                    .replace("{health}", String.valueOf(h))
                    .replace("{defense}", String.valueOf(d));
            lore.add(LegacyRgbParser.parse(line));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        if (meta instanceof SkullMeta skullMeta) {
            String textureId = cfg.getTextureId();
            if (textureId != null && !textureId.isEmpty()) {
                DungeonSecrets.applySkullTextureToMeta(skullMeta, textureId, "UndergroundCaveHelmetManager." + f);
            }
        }
        item.setItemMeta(meta);
        return item;
    }
}
