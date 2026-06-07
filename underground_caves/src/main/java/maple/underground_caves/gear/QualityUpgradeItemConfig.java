package maple.underground_caves.gear;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Collections;
import java.util.List;

/**
 * 品質升級道具配置，從 gear/quality_upgrade.yml 讀取。
 */
public final class QualityUpgradeItemConfig {

    private final YamlConfiguration yml;

    public QualityUpgradeItemConfig(YamlConfiguration yml) {
        this.yml = yml != null ? yml : new YamlConfiguration();
    }

    public String getTextureId() {
        return yml.getString("texture_id", "7235f16ed4440cc29ece8f4333d0175548321f085e3d14b7cb1eeb6a4ee35b01");
    }

    public String getDisplayName() {
        return yml.getString("display_name", "&d品質升級");
    }

    public List<String> getLore() {
        List<String> list = yml.getStringList("lore");
        return list != null ? list : Collections.emptyList();
    }
}
