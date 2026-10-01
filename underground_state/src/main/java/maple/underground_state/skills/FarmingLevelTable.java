package maple.underground_state.skills;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FarmingLevelTable {

    private static final List<FarmingLevelDefinition> DEFINITIONS = buildDefinitions();

    private FarmingLevelTable() {
    }

    public static int maxLevel() {
        return DEFINITIONS.size();
    }

    public static @NotNull List<FarmingLevelDefinition> definitions() {
        return DEFINITIONS;
    }

    public static @NotNull FarmingLevelDefinition definitionForLevel(int level) {
        if (level < 1 || level > DEFINITIONS.size()) {
            throw new IllegalArgumentException("Farming level out of range: " + level);
        }
        return DEFINITIONS.get(level - 1);
    }

    public static int levelForXp(long totalXp) {
        int level = 0;
        for (FarmingLevelDefinition definition : DEFINITIONS) {
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

    public static @Nullable FarmingLevelDefinition nextDefinitionForXp(long totalXp) {
        int currentLevel = levelForXp(totalXp);
        if (currentLevel >= maxLevel()) {
            return null;
        }
        return definitionForLevel(currentLevel + 1);
    }

    private static @NotNull List<FarmingLevelDefinition> buildDefinitions() {
        List<FarmingLevelDefinition> definitions = new ArrayList<>(CombatLevelTable.maxLevel());
        long totalCoins = 0L;
        int totalHealth = 0;
        int totalFarmingFortune = 0;
        int totalMapleXp = 0;

        for (int level = 1; level <= CombatLevelTable.maxLevel(); level++) {
            CombatLevelDefinition base = CombatLevelTable.definitionForLevel(level);
            long coinReward = maple.underground_state.professions.ProfessionMath.coins(level);
            int healthReward = level <= 14 ? 1 : level <= 19 ? 2 : level <= 25 ? 3 : 4;
            int farmingFortuneReward = 2;
            int mapleXpReward = 5;

            totalCoins += coinReward;
            totalHealth += healthReward;
            totalFarmingFortune += farmingFortuneReward;
            totalMapleXp += mapleXpReward;

            definitions.add(new FarmingLevelDefinition(
                    level,
                    base.xpRequired(),
                    base.totalXpRequired(),
                    coinReward,
                    totalCoins,
                    healthReward,
                    totalHealth,
                    farmingFortuneReward,
                    totalFarmingFortune,
                    mapleXpReward,
                    totalMapleXp
            ));
        }

        return Collections.unmodifiableList(definitions);
    }
}
