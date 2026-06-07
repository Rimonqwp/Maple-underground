package maple.underground_caves.gear;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Collections;
import java.util.List;

/** 遠程蓄勢頭盔 YAML 配置：箭矢飛行超過 base_blocks 後，每 step_blocks 格增傷 damage_percent_step% */
public final class ArrowDistanceHelmetConfig {

    private final YamlConfiguration yml;

    public ArrowDistanceHelmetConfig(YamlConfiguration yml) {
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

    /** 超過此格數後開始按距離增傷 */
    public double getBaseBlocks() { return yml.getDouble("arrow_distance.base_blocks", 20); }
    /** 每幾格 +1% 傷害 */
    public double getStepBlocks() { return yml.getDouble("arrow_distance.step_blocks", 2); }
    /** 每 step 增加的傷害百分比 */
    public double getDamagePercentPerStep() { return yml.getDouble("arrow_distance.damage_percent_per_step", 1); }
}
