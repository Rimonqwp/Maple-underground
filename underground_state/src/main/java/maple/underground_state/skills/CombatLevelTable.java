package maple.underground_state.skills;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CombatLevelTable {

    private static final long[] XP_REQUIRED = {
            50L, 125L, 200L, 300L, 500L, 750L, 1_000L, 1_500L, 2_000L, 3_500L,
            5_000L, 7_500L, 10_000L, 15_000L, 20_000L, 30_000L, 50_000L, 75_000L, 100_000L, 200_000L,
            300_000L, 400_000L, 500_000L, 600_000L, 700_000L, 800_000L, 900_000L, 1_000_000L, 1_100_000L, 1_200_000L,
            1_300_000L, 1_400_000L, 1_500_000L, 1_600_000L, 1_700_000L, 1_800_000L, 1_900_000L, 2_000_000L, 2_100_000L, 2_200_000L,
            2_300_000L, 2_400_000L, 2_500_000L, 2_600_000L, 2_750_000L, 2_900_000L, 3_100_000L, 3_400_000L, 3_700_000L, 4_000_000L,
            4_300_000L, 4_600_000L, 4_900_000L, 5_200_000L, 5_500_000L, 5_800_000L, 6_100_000L, 6_400_000L, 6_700_000L, 7_000_000L
    };

    private static final String[] UNLOCK_NOTES = {
            "解锁蜘蛛巢穴", null, null, null, "解锁13件物品",
            null, null, null, "解锁15件物品", null,
            "解锁1件物品", null, "解锁终点和8件物品", "解锁1件物品", "解锁1件物品",
            "解锁中级能力和6件物品", "解锁28件物品", null, "解锁2件物品", null,
            "解锁狂热者斗士藏身处和13件物品", null, "解锁绯红岛和161件物品", "解锁1件物品", "解锁1件物品",
            "解锁10件物品", null, null, null, null,
            "解锁3件物品", null, null, null, null,
            "解锁1件物品", null, null, null, null,
            null, null, null, null, null,
            null, null, null, null, null,
            null, null, null, null, null,
            null, null, null, null, null
    };

    private static final List<CombatLevelDefinition> DEFINITIONS = buildDefinitions();

    private CombatLevelTable() {
    }

    public static int maxLevel() {
        return DEFINITIONS.size();
    }

    public static @NotNull List<CombatLevelDefinition> definitions() {
        return DEFINITIONS;
    }

    public static @NotNull CombatLevelDefinition definitionForLevel(int level) {
        if (level < 1 || level > DEFINITIONS.size()) {
            throw new IllegalArgumentException("Combat level out of range: " + level);
        }
        return DEFINITIONS.get(level - 1);
    }

    public static int levelForXp(long totalXp) {
        int level = 0;
        for (CombatLevelDefinition definition : DEFINITIONS) {
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

    public static @Nullable CombatLevelDefinition nextDefinitionForXp(long totalXp) {
        int currentLevel = levelForXp(totalXp);
        if (currentLevel >= maxLevel()) {
            return null;
        }
        return definitionForLevel(currentLevel + 1);
    }

    private static @NotNull List<CombatLevelDefinition> buildDefinitions() {
        List<CombatLevelDefinition> definitions = new ArrayList<>(XP_REQUIRED.length);
        long totalXp = 0L;
        int totalCoins = 0;
        double totalCritChance = 0D;
        int totalExtraDamage = 0;
        int totalMapleXp = 0;

        for (int index = 0; index < XP_REQUIRED.length; index++) {
            int level = index + 1;
            long xpRequired = XP_REQUIRED[index];
            int coinReward = 100;
            double critChanceReward = 0.5D;
            int extraDamageReward = level <= 50 ? 4 : 1;
            int mapleXpReward = 5;

            totalXp += xpRequired;
            totalCoins += coinReward;
            totalCritChance += critChanceReward;
            totalExtraDamage += extraDamageReward;
            totalMapleXp += mapleXpReward;

            definitions.add(new CombatLevelDefinition(
                    level,
                    xpRequired,
                    totalXp,
                    coinReward,
                    totalCoins,
                    critChanceReward,
                    totalCritChance,
                    extraDamageReward,
                    totalExtraDamage,
                    mapleXpReward,
                    totalMapleXp,
                    UNLOCK_NOTES[index]
            ));
        }

        return Collections.unmodifiableList(definitions);
    }
}
