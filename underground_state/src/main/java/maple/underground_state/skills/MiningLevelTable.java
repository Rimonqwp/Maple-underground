package maple.underground_state.skills;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MiningLevelTable {

    private static final List<MiningLevelDefinition> DEFINITIONS = buildDefinitions();

    private MiningLevelTable() {
    }

    public static int maxLevel() {
        return DEFINITIONS.size();
    }

    public static @NotNull List<MiningLevelDefinition> definitions() {
        return DEFINITIONS;
    }

    public static @NotNull MiningLevelDefinition definitionForLevel(int level) {
        if (level < 1 || level > DEFINITIONS.size()) {
            throw new IllegalArgumentException("Mining level out of range: " + level);
        }
        return DEFINITIONS.get(level - 1);
    }

    public static int levelForXp(long totalXp) {
        int level = 0;
        for (MiningLevelDefinition definition : DEFINITIONS) {
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

    public static @Nullable MiningLevelDefinition nextDefinitionForXp(long totalXp) {
        int currentLevel = levelForXp(totalXp);
        if (currentLevel >= maxLevel()) {
            return null;
        }
        return definitionForLevel(currentLevel + 1);
    }

    private static @NotNull List<MiningLevelDefinition> buildDefinitions() {
        List<MiningLevelDefinition> definitions = new ArrayList<>(CombatLevelTable.maxLevel());
        long totalCoins = 0L;
        int totalDefense = 0;
        int totalMiningFortune = 0;
        int totalMapleXp = 0;

        for (int level = 1; level <= CombatLevelTable.maxLevel(); level++) {
            CombatLevelDefinition base = CombatLevelTable.definitionForLevel(level);
            long coinReward = 100L;
            int defenseReward = level <= 14 ? 1 : 2;
            int miningFortuneReward = 4;
            int mapleXpReward = 5;

            totalCoins += coinReward;
            totalDefense += defenseReward;
            totalMiningFortune += miningFortuneReward;
            totalMapleXp += mapleXpReward;

            definitions.add(new MiningLevelDefinition(
                    level,
                    base.xpRequired(),
                    base.totalXpRequired(),
                    coinReward,
                    totalCoins,
                    defenseReward,
                    totalDefense,
                    miningFortuneReward,
                    totalMiningFortune,
                    mapleXpReward,
                    totalMapleXp
            ));
        }

        return Collections.unmodifiableList(definitions);
    }
}
