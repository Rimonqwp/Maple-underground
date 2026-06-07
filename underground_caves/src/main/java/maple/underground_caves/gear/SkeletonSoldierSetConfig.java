package maple.underground_caves.gear;

import org.bukkit.Color;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;

/**
 * 白骨先鋒套裝配置：擊殺地牢骷髏 2.5% 掉落；每件箭矢傷害 +5%，套裝獎勵額外 +25%。
 * 屬性為防禦、生命、暴擊傷害、暴擊機率，F1～F3 硬編碼。
 */
public final class SkeletonSoldierSetConfig {

    public static final String SET_ID = "skeleton_soldier_set";
    public static final String[] SLOTS = { "helmet", "chest", "leggings", "boots" };
    public static final String[] FLOORS = { "F1", "F2", "F3" };

    private final YamlConfiguration yml;

    public SkeletonSoldierSetConfig(YamlConfiguration yml) {
        this.yml = yml != null ? yml : new YamlConfiguration();
    }

    public String getSetId() {
        return yml.getString("set_id", SET_ID);
    }

    public String getPieceDisplayName(String slot) {
        String key = "pieces." + slot;
        if (yml.contains(key)) return yml.getString(key, "白骨先鋒 " + defaultSlotName(slot));
        return yml.getString("display_name_prefix", "白骨先鋒套裝") + defaultSlotName(slot);
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
        return Color.fromRGB(0x8B, 0x73, 0x55);
    }

    private static int parseHexRgb(String s) {
        if (s == null) return -1;
        s = s.trim().startsWith("#") ? s.trim().substring(1) : s.trim();
        if (s.length() != 6) return -1;
        try { return Integer.parseInt(s, 16); } catch (NumberFormatException e) { return -1; }
    }

    public double getDropChance() {
        return yml.getDouble("drop.chance", 0.025);
    }

    public String getDropEntityType() {
        return yml.getString("drop.entity_type", "SKELETON");
    }

    public boolean isUnbreakable() {
        return yml.getBoolean("unbreakable", true);
    }

    public int getArrowDamagePercentPerPiece() {
        return yml.getInt("arrow_damage_percent_per_piece", 5);
    }

    /** 套裝獎勵：穿齊 4 件時箭矢傷害額外增加的百分比。 */
    public int getSetBonusArrowDamagePercent() {
        return yml.getInt("set_bonus_arrow_damage_percent", 25);
    }

    public int getDefense(String floor, String slot) {
        return yml.getInt("floors." + floor + "." + slot + ".defense", 0);
    }

    public int getHealth(String floor, String slot) {
        return yml.getInt("floors." + floor + "." + slot + ".health", 0);
    }

    public int getCritDamage(String floor, String slot) {
        return yml.getInt("floors." + floor + "." + slot + ".crit_damage", 0);
    }

    public int getCritChance(String floor, String slot) {
        return yml.getInt("floors." + floor + "." + slot + ".crit_chance", 0);
    }

    public List<String> getLoreLines(String slot) {
        List<String> list = yml.getStringList("lore." + slot);
        return list != null ? list : List.of("&7白骨先鋒 " + slot);
    }
}
