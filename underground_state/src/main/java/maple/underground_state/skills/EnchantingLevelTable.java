package maple.underground_state.skills;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EnchantingLevelTable {

    private static final List<EnchantingLevelDefinition> DEFINITIONS = buildDefinitions();

    private EnchantingLevelTable() {
    }

    public static int maxLevel() {
        return DEFINITIONS.size();
    }

    public static @NotNull List<EnchantingLevelDefinition> definitions() {
        return DEFINITIONS;
    }

    public static @NotNull EnchantingLevelDefinition definitionForLevel(int level) {
        if (level < 1 || level > DEFINITIONS.size()) {
            throw new IllegalArgumentException("Enchanting level out of range: " + level);
        }
        return DEFINITIONS.get(level - 1);
    }

    public static int levelForXp(long totalXp) {
        int level = 0;
        for (EnchantingLevelDefinition definition : DEFINITIONS) {
            if (totalXp >= definition.totalXpRequired()) {
                level = definition.level();
            } else {
                break;
            }
        }
        return level;
    }

    public static long currentLevelBaseXp(int currentLevel) {
        if (currentLevel <= 0) {
            return 0L;
        }
        return definitionForLevel(currentLevel).totalXpRequired();
    }

    public static @Nullable EnchantingLevelDefinition nextDefinitionForXp(long totalXp) {
        int currentLevel = levelForXp(totalXp);
        if (currentLevel >= maxLevel()) {
            return null;
        }
        return definitionForLevel(currentLevel + 1);
    }

    private static @NotNull List<EnchantingLevelDefinition> buildDefinitions() {
        List<EnchantingLevelDefinition> definitions = new ArrayList<>(CombatLevelTable.maxLevel());
        long totalCoins = 0L;
        int totalIntelligence = 0;
        double totalAbilityDamage = 0D;
        int totalBonusExp = 0;
        int totalMapleXp = 0;

        for (int level = 1; level <= CombatLevelTable.maxLevel(); level++) {
            CombatLevelDefinition base = CombatLevelTable.definitionForLevel(level);
            long coinReward = 100L;
            int intelligenceReward = level <= 14 ? 1 : 2;
            double abilityDamageReward = 0.5D;
            int bonusExpReward = 5;
            int mapleXpReward = 5;

            totalCoins += coinReward;
            totalIntelligence += intelligenceReward;
            totalAbilityDamage += abilityDamageReward;
            totalBonusExp += bonusExpReward;
            totalMapleXp += mapleXpReward;

            definitions.add(new EnchantingLevelDefinition(
                    level,
                    base.xpRequired(),
                    base.totalXpRequired(),
                    coinReward,
                    totalCoins,
                    intelligenceReward,
                    totalIntelligence,
                    abilityDamageReward,
                    totalAbilityDamage,
                    bonusExpReward,
                    totalBonusExp,
                    mapleXpReward,
                    totalMapleXp
            ));
        }

        return Collections.unmodifiableList(definitions);
    }
}
