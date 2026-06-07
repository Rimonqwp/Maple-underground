package maple.underground_caves.gear;

import org.bukkit.Color;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;

/** F3 自適應套裝，依地牢職業獲得額外加成 */
public final class AdaptiveSetConfig {

    public static final String SET_ID = "adaptive_set";
    public static final String FLOOR = "F3";
    /** 0=治療師(healer), 1=法師(mage), 2=戰士(berserk), 3=弓箭手(archer), 4=坦克(tank) */
    private static final String[] CLASS_KEYS = { "healer", "mage", "berserk", "archer", "tank" };

    private final YamlConfiguration yml;

    public AdaptiveSetConfig(YamlConfiguration yml) {
        this.yml = yml != null ? yml : new YamlConfiguration();
    }

    public String getSetId() { return yml.getString("set_id", SET_ID); }

    public String getPieceDisplayName(String slot) {
        String key = "pieces." + slot;
        return yml.contains(key) ? yml.getString(key, slot) : yml.getString("display_name_prefix", "自適應") + slot;
    }

    public Color getLeatherColor() {
        String hex = yml.getString("leather_color_hex");
        if (hex != null && !hex.isEmpty()) {
            String s = hex.trim().startsWith("#") ? hex.trim().substring(1) : hex.trim();
            if (s.length() == 6) {
                try {
                    int rgb = Integer.parseInt(s, 16);
                    return Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
                } catch (NumberFormatException ignored) {}
            }
        }
        return Color.fromRGB(155, 123, 192);
    }

    public boolean isUnbreakable() { return yml.getBoolean("unbreakable", true); }

    public int getHealth(String slot) { return yml.getInt("floors." + FLOOR + "." + slot + ".health", 0); }
    public int getDefense(String slot) { return yml.getInt("floors." + FLOOR + "." + slot + ".defense", 0); }
    public int getStrength(String slot) { return yml.getInt("floors." + FLOOR + "." + slot + ".strength", 0); }
    public int getIntelligence(String slot) { return yml.getInt("floors." + FLOOR + "." + slot + ".intelligence", 0); }

    /** 職業加成：classIndex 0~4 對應 healer/mage/berserk/archer/tank */
    public int getClassBonusStrength(int classIndex) {
        if (classIndex < 0 || classIndex >= CLASS_KEYS.length) return 0;
        return yml.getInt("class_bonus." + CLASS_KEYS[classIndex] + ".strength", 0);
    }
    public int getClassBonusHealth(int classIndex) {
        if (classIndex < 0 || classIndex >= CLASS_KEYS.length) return 0;
        return yml.getInt("class_bonus." + CLASS_KEYS[classIndex] + ".health", 0);
    }
    public int getClassBonusDefense(int classIndex) {
        if (classIndex < 0 || classIndex >= CLASS_KEYS.length) return 0;
        return yml.getInt("class_bonus." + CLASS_KEYS[classIndex] + ".defense", 0);
    }
    public int getClassBonusIntelligence(int classIndex) {
        if (classIndex < 0 || classIndex >= CLASS_KEYS.length) return 0;
        return yml.getInt("class_bonus." + CLASS_KEYS[classIndex] + ".intelligence", 0);
    }
    public int getClassBonusMending(int classIndex) {
        if (classIndex < 0 || classIndex >= CLASS_KEYS.length) return 0;
        return yml.getInt("class_bonus." + CLASS_KEYS[classIndex] + ".mending", 0);
    }
    public int getClassBonusCritChance(int classIndex) {
        if (classIndex < 0 || classIndex >= CLASS_KEYS.length) return 0;
        return yml.getInt("class_bonus." + CLASS_KEYS[classIndex] + ".crit_chance", 0);
    }
    public int getClassBonusCritDamage(int classIndex) {
        if (classIndex < 0 || classIndex >= CLASS_KEYS.length) return 0;
        return yml.getInt("class_bonus." + CLASS_KEYS[classIndex] + ".crit_damage", 0);
    }

    public List<String> getLoreLines(String slot) {
        List<String> list = yml.getStringList("lore." + slot);
        return list != null ? list : List.of();
    }
}
