package maple.underground_state.skills;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TamingLevelTable {

    private static final List<TamingLevelDefinition> DEFINITIONS = buildDefinitions();

    private TamingLevelTable() {
    }

    public static int maxLevel() {
        return DEFINITIONS.size();
    }

    public static @NotNull List<TamingLevelDefinition> definitions() {
        return DEFINITIONS;
    }

    public static @NotNull TamingLevelDefinition definitionForLevel(int level) {
        if (level < 1 || level > DEFINITIONS.size()) {
            throw new IllegalArgumentException("Taming level out of range: " + level);
        }
        return DEFINITIONS.get(level - 1);
    }

    public static int levelForXp(long totalXp) {
        int level = 0;
        for (TamingLevelDefinition definition : DEFINITIONS) {
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

    public static @Nullable TamingLevelDefinition nextDefinitionForXp(long totalXp) {
        int currentLevel = levelForXp(totalXp);
        if (currentLevel >= maxLevel()) {
            return null;
        }
        return definitionForLevel(currentLevel + 1);
    }

    private static @NotNull List<TamingLevelDefinition> buildDefinitions() {
        List<TamingLevelDefinition> definitions = new ArrayList<>(CombatLevelTable.maxLevel());
        long totalCoins = 0L;
        int totalPetLuck = 0;
        int totalPetXpGain = 0;
        double totalExpShareRate = 0D;
        int totalMapleXp = 0;

        for (int level = 1; level <= CombatLevelTable.maxLevel(); level++) {
            CombatLevelDefinition base = CombatLevelTable.definitionForLevel(level);
            long coinReward = 100L;
            int petLuckReward = 1;
            int petXpGainReward = 1;
            double expShareRateReward = 0.2D;
            int mapleXpReward = 5;

            totalCoins += coinReward;
            totalPetLuck += petLuckReward;
            totalPetXpGain += petXpGainReward;
            totalExpShareRate += expShareRateReward;
            totalMapleXp += mapleXpReward;

            definitions.add(new TamingLevelDefinition(
                    level,
                    base.xpRequired(),
                    base.totalXpRequired(),
                    coinReward,
                    totalCoins,
                    petLuckReward,
                    totalPetLuck,
                    petXpGainReward,
                    totalPetXpGain,
                    expShareRateReward,
                    totalExpShareRate,
                    mapleXpReward,
                    totalMapleXp
            ));
        }

        return Collections.unmodifiableList(definitions);
    }
}
