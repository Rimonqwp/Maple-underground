package maple.underground_caves.gear;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Collections;
import java.util.List;

/**
 * 不死頭顱配置，從 gear/immortal_skull.yml 讀取。
 */
public final class ImmortalSkullConfig {

    private final YamlConfiguration yml;

    public ImmortalSkullConfig(YamlConfiguration yml) {
        this.yml = yml != null ? yml : new YamlConfiguration();
    }

    public String getTextureId() {
        return yml.getString("texture_id", "43931ab4b7f99ab96246ae704335f461810738cf5129932412dffc35ef920c4e");
    }

    public String getDisplayName() {
        return yml.getString("display_name", "&6&l◆ 永恆的面具 ◆");
    }

    public List<String> getLore() {
        List<String> list = yml.getStringList("lore");
        return list != null ? list : Collections.emptyList();
    }

    public int getStatHealth() {
        return yml.getInt("stats.health", 125);
    }

    public int getStatDefense() {
        return yml.getInt("stats.defense", 100);
    }

    public int getStatIntelligence() {
        return yml.getInt("stats.intelligence", 150);
    }

    public int getEffectStrengthBonus() {
        return yml.getInt("effect.strength_bonus", 20);
    }

    public int getEffectDurationSeconds() {
        return yml.getInt("effect.duration_seconds", 3);
    }

    public int getCooldownSeconds() {
        return yml.getInt("cooldown_seconds", 360);
    }
}
