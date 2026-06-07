package maple.underground_caves.weapons;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * 單一武器的 YML 配置。每個武器一個 yml 檔。
 * lore 中可使用 {damage}、{intelligence} 等佔位符，對應 stats 區塊的鍵。
 */
public final class WeaponConfig {

    private final String id;
    private final String displayName;
    private final Material material;
    private final List<String> lore;
    private final SkillConfig skill;
    private final Map<String, String> stats;

    public WeaponConfig(String id, String displayName, Material material, List<String> lore, SkillConfig skill) {
        this(id, displayName, material, lore, skill, null);
    }

    public WeaponConfig(String id, String displayName, Material material, List<String> lore, SkillConfig skill, Map<String, String> stats) {
        this.id = id;
        this.displayName = displayName != null ? displayName : id;
        this.material = material != null ? material : Material.BLAZE_ROD;
        this.lore = lore != null ? lore : Collections.emptyList();
        this.skill = skill;
        this.stats = stats != null ? new HashMap<>(stats) : Collections.emptyMap();
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public Material getMaterial() { return material; }
    public List<String> getLore() { return lore; }
    public SkillConfig getSkill() { return skill; }
    /** 用於 lore 佔位符替換，如 {damage}、{intelligence} */
    public Map<String, String> getStats() { return stats; }

    /** 從 YML 的 skill 區塊讀取（type=freeze 等） */
    public static SkillConfig loadSkill(ConfigurationSection section) {
        if (section == null) return null;
        String type = section.getString("type");
        if (type == null || type.isBlank()) return null;
        if ("freeze".equalsIgnoreCase(type)) {
            return FreezeSkillConfig.from(section);
        }
        if ("showtime".equalsIgnoreCase(type)) {
            return ShowtimeSkillConfig.from(section);
        }
        return null;
    }

    /** 技能配置基底 */
    public interface SkillConfig {
        String getType();
    }

    /** 冷凍技能（可被多把武器引用），傷害依 Hypixel 技能傷害公式。可選螺旋擴散動畫。 */
    public static final class FreezeSkillConfig implements SkillConfig {
        private final String name;
        private final int manaCost;
        private final double cooldownSeconds;
        private final double baseAbilityDamage;
        private final double abilityScaling;
        private final int freezeDurationTicks;
        private final String castParticle;
        private final String frozenParticle;
        private final int frozenParticleCount;
        private final boolean spiralEnabled;
        private final int spiralDurationTicks;
        private final double spiralMaxRadius;
        private final String spiralParticle;
        private final int spiralRotations;
        private final int spiralPointsPerRing;

        public FreezeSkillConfig(String name, int manaCost, double cooldownSeconds, double baseAbilityDamage, double abilityScaling,
                                 int freezeDurationTicks, String castParticle, String frozenParticle, int frozenParticleCount,
                                 boolean spiralEnabled, int spiralDurationTicks, double spiralMaxRadius, String spiralParticle,
                                 int spiralRotations, int spiralPointsPerRing) {
            this.name = name != null ? name : "寒霜禁錮";
            this.manaCost = Math.max(0, manaCost);
            this.cooldownSeconds = Math.max(0, cooldownSeconds);
            this.baseAbilityDamage = Math.max(0, baseAbilityDamage);
            this.abilityScaling = Math.max(0, abilityScaling);
            this.freezeDurationTicks = Math.max(1, freezeDurationTicks);
            this.castParticle = castParticle != null ? castParticle : "SNOWFLAKE";
            this.frozenParticle = frozenParticle != null ? frozenParticle : "SNOWFLAKE";
            this.frozenParticleCount = Math.max(1, frozenParticleCount);
            this.spiralEnabled = spiralEnabled;
            this.spiralDurationTicks = Math.max(1, spiralDurationTicks);
            this.spiralMaxRadius = Math.max(0.5, spiralMaxRadius);
            this.spiralParticle = spiralParticle != null && !spiralParticle.isEmpty() ? spiralParticle : "SNOWFLAKE";
            this.spiralRotations = Math.max(1, spiralRotations);
            this.spiralPointsPerRing = Math.max(8, spiralPointsPerRing);
        }

        public static FreezeSkillConfig from(ConfigurationSection section) {
            ConfigurationSection spiral = section.getConfigurationSection("spiral_animation");
            boolean spiralEnabled = false;
            int spiralDurationTicks = 16;
            double spiralMaxRadius = 3.0;
            String spiralParticle = "SNOWFLAKE";
            int spiralRotations = 2;
            int spiralPointsPerRing = 24;
            if (spiral != null && spiral.getBoolean("enabled", false)) {
                spiralEnabled = true;
                spiralDurationTicks = spiral.getInt("duration_ticks", 16);
                spiralMaxRadius = spiral.getDouble("max_radius", 3.0);
                spiralParticle = spiral.getString("particle", "SNOWFLAKE");
                spiralRotations = spiral.getInt("rotations", 2);
                spiralPointsPerRing = spiral.getInt("points_per_ring", 24);
            }
            return new FreezeSkillConfig(
                    section.getString("name", "寒霜禁錮"),
                    section.getInt("mana_cost", 200),
                    section.getDouble("cooldown_seconds", 2.0),
                    section.getDouble("base_ability_damage", 300.0),
                    section.getDouble("ability_scaling", 0.2),
                    section.getInt("freeze_duration_ticks", 40),
                    section.getString("cast_particle", "SNOWFLAKE"),
                    section.getString("frozen_particle", "SNOWFLAKE"),
                    section.getInt("frozen_particle_count", 12),
                    spiralEnabled, spiralDurationTicks, spiralMaxRadius, spiralParticle, spiralRotations, spiralPointsPerRing
            );
        }

        @Override
        public String getType() { return "freeze"; }
        public String getName() { return name; }
        public int getManaCost() { return manaCost; }
        public double getCooldownSeconds() { return cooldownSeconds; }
        public double getBaseAbilityDamage() { return baseAbilityDamage; }
        public double getAbilityScaling() { return abilityScaling; }
        public int getFreezeDurationTicks() { return freezeDurationTicks; }
        public String getCastParticle() { return castParticle; }
        public String getFrozenParticle() { return frozenParticle; }
        public int getFrozenParticleCount() { return frozenParticleCount; }
        public boolean isSpiralEnabled() { return spiralEnabled; }
        public int getSpiralDurationTicks() { return spiralDurationTicks; }
        public double getSpiralMaxRadius() { return spiralMaxRadius; }
        public String getSpiralParticle() { return spiralParticle; }
        public int getSpiralRotations() { return spiralRotations; }
        public int getSpiralPointsPerRing() { return spiralPointsPerRing; }
    }

    /** Showtime 技能：發射氣球（盔甲架+隨機頭顱），命中爆炸，對範圍內怪物/Boss 造成 Base Ability Damage。 */
    public static final class ShowtimeSkillConfig implements SkillConfig {
        private final String name;
        private final int manaCost;
        private final double cooldownSeconds;
        private final double baseAbilityDamage;
        private final double explosionRadius;
        private final double balloonSpeed;
        private final List<String> balloonTextures;

        public ShowtimeSkillConfig(String name, int manaCost, double cooldownSeconds, double baseAbilityDamage,
                                   double explosionRadius, double balloonSpeed, List<String> balloonTextures) {
            this.name = name != null ? name : "Showtime";
            this.manaCost = Math.max(0, manaCost);
            this.cooldownSeconds = Math.max(0, cooldownSeconds);
            this.baseAbilityDamage = Math.max(0, baseAbilityDamage);
            this.explosionRadius = Math.max(0.5, explosionRadius);
            this.balloonSpeed = Math.max(0.1, balloonSpeed);
            this.balloonTextures = balloonTextures != null && !balloonTextures.isEmpty() ? new ArrayList<>(balloonTextures) : new ArrayList<>();
        }

        public static ShowtimeSkillConfig from(ConfigurationSection section) {
            List<String> textures = section.getStringList("balloon_textures");
            return new ShowtimeSkillConfig(
                    section.getString("name", "Showtime"),
                    section.getInt("mana_cost", 90),
                    section.getDouble("cooldown_seconds", 1.0),
                    section.getDouble("base_ability_damage", 1000.0),
                    section.getDouble("explosion_radius", 1.0),
                    section.getDouble("balloon_speed", 0.8),
                    textures
            );
        }

        @Override
        public String getType() { return "showtime"; }
        public String getName() { return name; }
        public int getManaCost() { return manaCost; }
        public double getCooldownSeconds() { return cooldownSeconds; }
        public double getBaseAbilityDamage() { return baseAbilityDamage; }
        public double getExplosionRadius() { return explosionRadius; }
        public double getBalloonSpeed() { return balloonSpeed; }
        public List<String> getBalloonTextures() { return balloonTextures; }
    }
}
