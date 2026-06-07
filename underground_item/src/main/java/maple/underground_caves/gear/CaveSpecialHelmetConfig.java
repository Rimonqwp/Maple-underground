package maple.underground_caves.gear;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Collections;
import java.util.List;

/**
 * 洞窟特殊頭盔單一配置：癒合之冠／守護療癒／堅韌療癒（治療+活力）或 法術增幅／精通／宗師（能力傷害+智力）。
 */
public final class CaveSpecialHelmetConfig {

    private final YamlConfiguration yml;

    public CaveSpecialHelmetConfig(YamlConfiguration yml) {
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

    public int getStatHealth() {
        return yml.getInt("stats.health", 0);
    }

    /** 地牢內治療加成（等同於活力公式的加算，與 Vitality 疊加）。 */
    public int getMendingInDungeon() {
        return yml.getInt("ability.mending_in_dungeon", 0);
    }

    /** 地牢內活力加成。 */
    public int getVitalityInDungeon() {
        return yml.getInt("ability.vitality_in_dungeon", 0);
    }

    /** 能力傷害百分比（25/35/45）。 */
    public int getAbilityDamagePercent() {
        return yml.getInt("stats.ability_damage_percent", 0);
    }

    public int getIntelligence() {
        return yml.getInt("stats.intelligence", 0);
    }

    public boolean isMendingType() {
        return getMendingInDungeon() > 0 || getVitalityInDungeon() > 0;
    }

    public boolean isAbilityDamageType() {
        return getAbilityDamagePercent() > 0 || getIntelligence() > 0;
    }
}
