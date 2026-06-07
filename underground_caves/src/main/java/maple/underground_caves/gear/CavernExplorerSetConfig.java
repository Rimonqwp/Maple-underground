package maple.underground_caves.gear;

import org.bukkit.Color;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;

/** 深窟探險者套裝 F2，套裝效果：洞窟內 +5 速度、5% 傷害減免 */
public final class CavernExplorerSetConfig {

    public static final String SET_ID = "cavern_explorer_set";
    public static final String FLOOR = "F2";

    private final YamlConfiguration yml;

    public CavernExplorerSetConfig(YamlConfiguration yml) {
        this.yml = yml != null ? yml : new YamlConfiguration();
    }

    public String getSetId() { return yml.getString("set_id", SET_ID); }

    public String getPieceDisplayName(String slot) {
        String key = "pieces." + slot;
        return yml.contains(key) ? yml.getString(key, slot) : yml.getString("display_name_prefix", "深窟探險者") + slot;
    }

    public Color getLeatherColor() {
        String hex = yml.getString("leather_color_hex");
        if (hex != null && !hex.isEmpty()) {
            int rgb = parseHexRgb(hex);
            if (rgb >= 0) return Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        }
        return Color.fromRGB(90, 107, 138);
    }

    private static int parseHexRgb(String s) {
        if (s == null) return -1;
        s = s.trim().startsWith("#") ? s.trim().substring(1) : s.trim();
        if (s.length() != 6) return -1;
        try { return Integer.parseInt(s, 16); } catch (NumberFormatException e) { return -1; }
    }

    public boolean isUnbreakable() { return yml.getBoolean("unbreakable", true); }

    public int getWalkSpeedBonus() { return yml.getInt("set_bonus.walk_speed_bonus", 5); }
    public int getDamageReductionPercent() { return yml.getInt("set_bonus.damage_reduction_percent", 5); }

    public int getHealth(String slot) { return yml.getInt("floors." + FLOOR + "." + slot + ".health", 0); }
    public int getDefense(String slot) { return yml.getInt("floors." + FLOOR + "." + slot + ".defense", 0); }
    public int getStrength(String slot) { return yml.getInt("floors." + FLOOR + "." + slot + ".strength", 0); }
    public int getIntelligence(String slot) { return yml.getInt("floors." + FLOOR + "." + slot + ".intelligence", 0); }

    public List<String> getLoreLines(String slot) {
        List<String> list = yml.getStringList("lore." + slot);
        return list != null ? list : List.of();
    }
}
