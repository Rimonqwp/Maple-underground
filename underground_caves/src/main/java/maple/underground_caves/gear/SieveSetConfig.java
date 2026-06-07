package maple.underground_caves.gear;

import org.bukkit.Color;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;

/**
 * 篩子身體套裝配置：每件 15% 箭矢擊退抗性，套裝 +20%；擊殺地牢女巫 10% 掉落。
 * 屬性為生命 + 力量，F1～F3 硬編碼於 floors 區塊。
 */
public final class SieveSetConfig {

    public static final String SET_ID = "sieve_set";
    public static final String[] SLOTS = { "helmet", "chest", "leggings", "boots" };
    /** 裝備僅支援 F1～F3 樓層 */
    public static final String[] FLOORS = { "F1", "F2", "F3" };

    private final YamlConfiguration yml;

    public SieveSetConfig(YamlConfiguration yml) {
        this.yml = yml != null ? yml : new YamlConfiguration();
    }

    public String getSetId() {
        return yml.getString("set_id", SET_ID);
    }

    public String getPieceDisplayName(String slot) {
        String key = "pieces." + slot;
        if (yml.contains(key)) return yml.getString(key, "&a篩子身體 " + defaultSlotName(slot));
        return yml.getString("display_name_prefix", "&a篩子身體") + defaultSlotName(slot);
    }

    private static String defaultSlotName(String slot) {
        return switch (slot == null ? "" : slot) {
            case "helmet" -> " 頭盔";
            case "chest" -> " 胸甲";
            case "leggings" -> " 護腿";
            case "boots" -> " 戰靴";
            default -> "";
        };
    }

    public Color getLeatherColor() {
        String hex = yml.getString("leather_color_hex");
        if (hex != null && !hex.isEmpty()) {
            int rgb = parseHexRgb(hex);
            if (rgb >= 0) return Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        }
        List<Integer> rgb = yml.getIntegerList("leather_color_rgb");
        if (rgb != null && rgb.size() >= 3) return Color.fromRGB(rgb.get(0), rgb.get(1), rgb.get(2));
        return Color.fromRGB(74, 124, 74);
    }

    private static int parseHexRgb(String s) {
        if (s == null) return -1;
        s = s.trim().startsWith("#") ? s.trim().substring(1) : s.trim();
        if (s.length() != 6) return -1;
        try { return Integer.parseInt(s, 16); } catch (NumberFormatException e) { return -1; }
    }

    public double getDropChance() {
        return yml.getDouble("drop.chance", 0.10);
    }

    public String getDropEntityType() {
        return yml.getString("drop.entity_type", "WITCH");
    }

    public boolean isUnbreakable() {
        return yml.getBoolean("unbreakable", true);
    }

    public int getArrowResistPerPiece() {
        return yml.getInt("arrow_knockback_resistance_per_piece", 15);
    }

    public int getSetBonusArrowResist() {
        return yml.getInt("set_bonus_arrow_resistance", 20);
    }

    /** 指定樓層、部位的生命（floors.F1/F2/F3）。 */
    public int getHealth(String floor, String slot) {
        return yml.getInt("floors." + floor + "." + slot + ".health", 0);
    }

    /** 指定樓層、部位的力量（floors.F1/F2/F3）。 */
    public int getStrength(String floor, String slot) {
        return yml.getInt("floors." + floor + "." + slot + ".strength", 0);
    }

    public List<String> getLoreLines(String slot) {
        List<String> list = yml.getStringList("lore." + slot);
        return list != null ? list : List.of("&7篩子身體 " + slot);
    }
}
