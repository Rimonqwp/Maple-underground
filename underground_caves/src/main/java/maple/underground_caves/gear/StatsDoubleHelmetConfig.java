package maple.underground_caves.gear;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Collections;
import java.util.List;

/**
 * 單一「戴上時屬性 x2」頭盔的配置（lore 顯示 x1，裝備時實際 x2）。
 */
public final class StatsDoubleHelmetConfig {

    private final YamlConfiguration yml;

    public StatsDoubleHelmetConfig(YamlConfiguration yml) {
        this.yml = yml != null ? yml : new YamlConfiguration();
    }

    public String getTextureId() {
        return yml.getString("texture_id", "");
    }

    public String getDisplayName() {
        return yml.getString("display_name", "");
    }

    public List<String> getLore() {
        List<String> list = yml.getStringList("lore");
        return list != null ? list : Collections.emptyList();
    }

    public int getStatStrength() {
        return yml.getInt("stats.strength", 0);
    }

    public int getStatHealth() {
        return yml.getInt("stats.health", 0);
    }

    public int getStatDefense() {
        return yml.getInt("stats.defense", 0);
    }
}
