package maple.underground_caves.gear;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Collections;
import java.util.List;

/** 絕境逢生頭盔 YAML 配置 */
public final class SecondWindHelmetConfig {

    private final YamlConfiguration yml;

    public SecondWindHelmetConfig(YamlConfiguration yml) {
        this.yml = yml != null ? yml : new YamlConfiguration();
    }

    public String getTextureId() { return yml.getString("texture_id", ""); }
    public String getDisplayName() { return yml.getString("display_name", ""); }
    public List<String> getLore() {
        List<String> list = yml.getStringList("lore");
        return list != null ? list : Collections.emptyList();
    }

    public int getStatHealth() { return yml.getInt("stats.health", 0); }
    public int getStatDefense() { return yml.getInt("stats.defense", 0); }
    public int getStatSpeed() { return yml.getInt("stats.speed", 0); }
    public int getStatIntelligence() { return yml.getInt("stats.intelligence", 0); }

    public int getEffectSpeedBonus() { return yml.getInt("effect.speed_bonus", 50); }
    public int getEffectDurationSeconds() { return yml.getInt("effect.duration_seconds", 3); }
    public int getCooldownSeconds() { return yml.getInt("cooldown_seconds", 30); }
}
