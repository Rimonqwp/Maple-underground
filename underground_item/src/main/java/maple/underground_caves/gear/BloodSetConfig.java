package maple.underground_caves.gear;

import org.bukkit.Color;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;

/** 單一傳說套裝（血脈）YAML 配置 */
public final class BloodSetConfig {

    private final YamlConfiguration yml;
    private final String setId;

    public BloodSetConfig(String setId, YamlConfiguration yml) {
        this.setId = setId != null ? setId : "";
        this.yml = yml != null ? yml : new YamlConfiguration();
    }

    public String getSetId() { return setId; }

    public String getPieceDisplayName(String slot) {
        String key = "pieces." + slot;
        return yml.contains(key) ? yml.getString(key, slot) : yml.getString("display_name_prefix", "") + slot;
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
        return Color.fromRGB(196, 30, 58);
    }

    public boolean isUnbreakable() { return yml.getBoolean("unbreakable", true); }

    public int getHealth(String slot) { return yml.getInt("stats." + slot + ".health", 0); }
    public int getDefense(String slot) { return yml.getInt("stats." + slot + ".defense", 0); }
    public int getStrength(String slot) { return yml.getInt("stats." + slot + ".strength", 0); }
    public int getIntelligence(String slot) { return yml.getInt("stats." + slot + ".intelligence", 0); }
    public int getSpeed(String slot) { return yml.getInt("stats." + slot + ".speed", 0); }
    public int getCritChance(String slot) { return yml.getInt("stats." + slot + ".crit_chance", 0); }
    public int getCritDamage(String slot) { return yml.getInt("stats." + slot + ".crit_damage", 0); }

    public int getSetBonusRadiusBlocks() { return yml.getInt("set_bonus.radius_blocks", 0); }
    public double getSetBonusRegenMultiplier() { return yml.getDouble("set_bonus.regen_multiplier", 1); }
    public int getSetBonusDefensePerMissingHpPercent() { return yml.getInt("set_bonus.defense_per_missing_hp_percent", 0); }
    public double getSetBonusManaCostRatio() { return parseManaRatio(yml.getString("set_bonus.mana_cost_ratio", "1")); }
    public int getSetBonusHealthThresholdPercent() { return yml.getInt("set_bonus.health_threshold_percent", 0); }
    public int getSetBonusSpeedBonus() { return yml.getInt("set_bonus.speed_bonus", 0); }
    public int getSetBonusWalkSpeedCapBonus() { return yml.getInt("set_bonus.walk_speed_cap_bonus", 0); }
    public int getSetBonusCombatStatsPercent() { return yml.getInt("set_bonus.combat_stats_percent", 0); }

    private static double parseManaRatio(String s) {
        if (s == null) return 1.0;
        if (s.contains("/")) {
            String[] parts = s.trim().split("/");
            if (parts.length == 2) {
                try {
                    double a = Double.parseDouble(parts[0].trim());
                    double b = Double.parseDouble(parts[1].trim());
                    return b > 0 ? a / b : 1.0;
                } catch (NumberFormatException ignored) {}
            }
        }
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return 1.0; }
    }

    public List<String> getLoreLines(String slot) {
        List<String> list = yml.getStringList("lore." + slot);
        return list != null ? list : List.of();
    }
}
