package maple.underground_state.skills;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FishingLevelTable {

    private static final List<FishingLevelDefinition> DEFINITIONS = buildDefinitions();

    private FishingLevelTable() {
    }

    public static int maxLevel() {
        return DEFINITIONS.size();
    }

    public static @NotNull List<FishingLevelDefinition> definitions() {
        return DEFINITIONS;
    }

    public static @NotNull FishingLevelDefinition definitionForLevel(int level) {
        if (level < 1 || level > DEFINITIONS.size()) {
            throw new IllegalArgumentException("Fishing level out of range: " + level);
        }
        return DEFINITIONS.get(level - 1);
    }

    public static int levelForXp(long totalXp) {
        int level = 0;
        for (FishingLevelDefinition definition : DEFINITIONS) {
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

    public static @Nullable FishingLevelDefinition nextDefinitionForXp(long totalXp) {
        int currentLevel = levelForXp(totalXp);
        if (currentLevel >= maxLevel()) {
            return null;
        }
        return definitionForLevel(currentLevel + 1);
    }

    private static @NotNull List<FishingLevelDefinition> buildDefinitions() {
        List<FishingLevelDefinition> definitions = new ArrayList<>(CombatLevelTable.maxLevel());
        long totalCoins = 0L;
        int totalHealth = 0;
        double totalTreasureChance = 0D;
        int totalMapleXp = 0;

        for (int level = 1; level <= CombatLevelTable.maxLevel(); level++) {
            CombatLevelDefinition base = CombatLevelTable.definitionForLevel(level);
            long coinReward = 100L;
            int healthReward = level <= 14 ? 1 : level <= 19 ? 2 : level <= 25 ? 3 : 4;
            double treasureChanceReward = 0.1D;
            int mapleXpReward = 5;

            totalCoins += coinReward;
            totalHealth += healthReward;
            totalTreasureChance += treasureChanceReward;
            totalMapleXp += mapleXpReward;

            definitions.add(new FishingLevelDefinition(
                    level,
                    base.xpRequired(),
                    base.totalXpRequired(),
                    coinReward,
                    totalCoins,
                    healthReward,
                    totalHealth,
                    treasureChanceReward,
                    totalTreasureChance,
                    mapleXpReward,
                    totalMapleXp
            ));
        }

        return Collections.unmodifiableList(definitions);
    }
}
