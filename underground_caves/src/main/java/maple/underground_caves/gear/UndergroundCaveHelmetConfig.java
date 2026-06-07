package maple.underground_caves.gear;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Collections;
import java.util.List;

/**
 * 單一樓層的地下洞穴頭盔配置（F1 / F2 / F3 各一個 yml）。
 */
public final class UndergroundCaveHelmetConfig {

    private final YamlConfiguration yml;

    public UndergroundCaveHelmetConfig(YamlConfiguration yml) {
        this.yml = yml != null ? yml : new YamlConfiguration();
    }

    public String getTextureId() {
        return yml.getString("texture_id", "");
    }

    public String getDisplayName() {
        return yml.getString("display_name", "&6&l地下洞穴頭盔");
    }

    public List<String> getLore() {
        List<String> list = yml.getStringList("lore");
        return list != null ? list : Collections.emptyList();
    }

    public int getStatStrength() {
        return yml.getInt("stats.strength", 5);
    }

    public int getStatHealth() {
        return yml.getInt("stats.health", 20);
    }

    public int getStatDefense() {
        return yml.getInt("stats.defense", 10);
    }
}

