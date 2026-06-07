package maple.underground_caves.gear;

import org.bukkit.Color;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;

/**
 * 擊敗坦克套裝配置，從 gear/defeat_tank_set.yml 讀取，數值已寫死不隨機計算。
 */
public final class DefeatTankSetConfig {

    public static final String SET_ID = "defeat_tank";
    public static final String[] SLOTS = { "helmet", "chest", "leggings", "boots" };
    /** 裝備僅支援 F1～F3 樓層 */
    public static final String[] FLOORS = { "F1", "F2", "F3" };

    private final YamlConfiguration yml;

    public DefeatTankSetConfig(YamlConfiguration yml) {
        this.yml = yml != null ? yml : new YamlConfiguration();
    }

    public String getSetId() {
        return yml.getString("set_id", SET_ID);
    }

    public String getDisplayNamePrefix() {
        return yml.getString("display_name_prefix", "&7&l擊敗坦克");
    }

    /** 取得該部位的顯示名稱；若 yml 未設則用 prefix + 預設部位名。 */
    public String getPieceDisplayName(String slot) {
        String key = "pieces." + slot;
        if (yml.contains(key)) return yml.getString(key, getDisplayNamePrefix() + defaultSlotName(slot));
        return getDisplayNamePrefix() + defaultSlotName(slot);
    }

    private static String defaultSlotName(String slot) {
        return switch (slot == null ? "" : slot) {
            case "helmet" -> "頭盔";
            case "chest" -> "胸甲";
            case "leggings" -> "護腿";
            case "boots" -> "戰靴";
            default -> "";
        };
    }

    /** 支援 leather_color_hex: "#50505a" 或 leather_color_rgb: [80, 80, 90]，優先 hex。 */
    public Color getLeatherColor() {
        String hex = yml.getString("leather_color_hex");
        if (hex != null && !hex.isEmpty()) {
            int rgb = parseHexRgb(hex);
            if (rgb >= 0) {
                return Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
            }
        }
        List<Integer> rgb = yml.getIntegerList("leather_color_rgb");
        if (rgb != null && rgb.size() >= 3) {
            return Color.fromRGB(rgb.get(0), rgb.get(1), rgb.get(2));
        }
        return Color.fromRGB(80, 80, 90);
    }

    private static int parseHexRgb(String s) {
        if (s == null) return -1;
        s = s.trim().startsWith("#") ? s.trim().substring(1) : s.trim();
        if (s.length() != 6) return -1;
        try {
            return Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public double getDropChance() {
        return yml.getDouble("drop.chance", 0.10);
    }

    /** 僅擊殺此怪物類型時才掉落（對應 monsters.yml 的 key，與地牢怪 PDC entityType 一致，如 ZOMBIE_TANK＝坦克僵尸）。 */
    public String getDropMonsterType() {
        return yml.getString("drop.monster_type", "ZOMBIE_TANK");
    }

    /** 裝備是否為無法破壞（預設 true）。 */
    public boolean isUnbreakable() {
        return yml.getBoolean("unbreakable", true);
    }

    public int getSpeedPer50Defense() {
        return yml.getInt("set_bonus.speed_per_50_defense", 1);
    }

    public int getDefense(String floor, String slot) {
        return yml.getInt("floors." + floor + "." + slot + ".defense", 0);
    }

    public int getSpeed(String floor, String slot) {
        return yml.getInt("floors." + floor + "." + slot + ".speed", -5);
    }

    /** 取得指定部位的 lore 行列表；可含 {defense}、{speed} 佔位符，由 GearManager 替換。 */
    public java.util.List<String> getLoreLines(String slot) {
        java.util.List<String> list = yml.getStringList("lore." + slot);
        if (list != null && !list.isEmpty()) return list;
        list = yml.getStringList("lore");
        if (list != null && !list.isEmpty()) return list;
        return java.util.List.of(
                "&a❈ 防禦 {defense}",
                "&f✦ 速度 {speed}",
                "",
                "&7套裝：每 50 防禦 +1 行走速度"
        );
    }
}
