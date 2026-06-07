package maple.underground_state.stats;

import org.bukkit.entity.Entity;

public final class PlayerStatsFormulas {

    public static final String TRUE_DAMAGE_SCOREBOARD_TAG = "underground_true_damage_once";

    private PlayerStatsFormulas() {
    }

    public static double totalHealthGained(double healthGained, double vitality) {
        return healthGained * (vitality / 100.0);
    }

    public static double totalHealthGainedFromHealer(double healthGained, double recipientVitality, double healerMending) {
        return healthGained * ((recipientVitality + healerMending) / 100.0);
    }

    public static double healthRegenPerTick(double maxHealth, double healthRegenStat) {
        return (1.5 + maxHealth / 100.0) * (healthRegenStat / 100.0);
    }

    public static double effectiveHealth(double health, double defense) {
        return health * (1.0 + defense / 100.0);
    }

    public static double trueEffectiveHealth(double health, double trueDefense) {
        return health * (1.0 + trueDefense / 100.0);
    }

    public static double damageReduction(double defense) {
        if (defense <= 0) return 0;
        return defense / (defense + 100.0);
    }

    public static double trueDamageReduction(double trueDefense) {
        if (trueDefense <= 0) return 0;
        return trueDefense / (trueDefense + 100.0);
    }

    public static double damageAfterDefense(double rawDamage, double defense) {
        return rawDamage * 100.0 / (defense + 100.0);
    }

    public static double damageAfterTrueDefense(double rawDamage, double trueDefense) {
        return rawDamage * 100.0 / (trueDefense + 100.0);
    }

    public static double critMultiplier(double critDamagePercent) {
        return 1.0 + critDamagePercent / 100.0;
    }

    public static boolean rollCrit(double critChancePercent, double random) {
        return random < Math.min(100, Math.max(0, critChancePercent)) / 100.0;
    }

    public static double damageWithStrengthAndCrit(double baseDamage, double strength, double critDamagePercent) {
        double withStrength = (5.0 + baseDamage) * (1.0 + strength / 100.0);
        return withStrength * critMultiplier(critDamagePercent);
    }

    public static double damageWithStrength(double baseDamage, double strength) {
        return (5.0 + baseDamage) * (1.0 + Math.max(0D, strength) / 100.0);
    }

    public static double attackCooldownDamageMultiplier(double cooldownProgress) {
        double clamped = Math.max(0D, Math.min(1D, cooldownProgress));
        return 0.2D + clamped * clamped * 0.8D;
    }

    public static double effectiveAttackCooldown(double rawCooldownProgress, double bonusAttackSpeed) {
        double clampedRaw = Math.max(0D, Math.min(1D, rawCooldownProgress));
        double clampedBas = Math.max(0D, Math.min(100D, bonusAttackSpeed));
        return Math.max(0D, Math.min(1D, clampedRaw * (1D + clampedBas / 100D)));
    }

    public static double adjustDamageForAttackCooldown(double currentDamage,
                                                       double rawCooldownProgress,
                                                       double effectiveCooldownProgress) {
        double rawFactor = attackCooldownDamageMultiplier(rawCooldownProgress);
        if (rawFactor <= 1.0E-9D) {
            return Math.max(0D, currentDamage);
        }
        double baseDamage = currentDamage / rawFactor;
        return Math.max(0D, baseDamage * attackCooldownDamageMultiplier(effectiveCooldownProgress));
    }

    public static void markNextHitAsTrueDamage(Entity entity) {
        if (entity == null) return;
        entity.addScoreboardTag(TRUE_DAMAGE_SCOREBOARD_TAG);
    }

    public static boolean consumeTrueDamageMark(Entity entity) {
        return entity != null && entity.removeScoreboardTag(TRUE_DAMAGE_SCOREBOARD_TAG);
    }

    public static double abilityDamage(double baseAbilityDamage,
                                       double intelligence,
                                       double abilityScaling,
                                       double abilityDamageStat,
                                       double additiveMultiplier,
                                       double multiplicativeMultiplier) {
        double intFactor = 1.0 + (intelligence / 100.0) * abilityScaling;
        double abilityStatMult = 1.0 + abilityDamageStat / 100.0;
        return baseAbilityDamage * intFactor * additiveMultiplier * multiplicativeMultiplier * abilityStatMult;
    }
}
