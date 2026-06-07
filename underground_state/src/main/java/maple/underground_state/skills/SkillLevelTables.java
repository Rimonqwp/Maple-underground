package maple.underground_state.skills;

import org.jetbrains.annotations.NotNull;

public final class SkillLevelTables {

    private SkillLevelTables() {
    }

    public static int levelForXp(@NotNull SkillType skillType, long totalXp) {
        return switch (skillType) {
            case COMBAT -> CombatLevelTable.levelForXp(totalXp);
            case MINING -> MiningLevelTable.levelForXp(totalXp);
            case FORAGING -> ForagingLevelTable.levelForXp(totalXp);
            case FARMING -> FarmingLevelTable.levelForXp(totalXp);
            case FISHING -> FishingLevelTable.levelForXp(totalXp);
            case ENCHANTING -> EnchantingLevelTable.levelForXp(totalXp);
            case ALCHEMY -> AlchemyLevelTable.levelForXp(totalXp);
            case TAMING -> TamingLevelTable.levelForXp(totalXp);
            case EXPLORATION, CRAFTING, SOCIAL -> CombatLevelTable.levelForXp(totalXp);
        };
    }

    public static int maxLevel(@NotNull SkillType skillType) {
        return switch (skillType) {
            case COMBAT -> CombatLevelTable.maxLevel();
            case MINING -> MiningLevelTable.maxLevel();
            case FORAGING -> ForagingLevelTable.maxLevel();
            case FARMING -> FarmingLevelTable.maxLevel();
            case FISHING -> FishingLevelTable.maxLevel();
            case ENCHANTING -> EnchantingLevelTable.maxLevel();
            case ALCHEMY -> AlchemyLevelTable.maxLevel();
            case TAMING -> TamingLevelTable.maxLevel();
            case EXPLORATION, CRAFTING, SOCIAL -> CombatLevelTable.maxLevel();
        };
    }

    public static long totalXpRequiredForLevel(@NotNull SkillType skillType, int level) {
        if (level <= 0) {
            return 0L;
        }
        int clampedLevel = Math.min(level, maxLevel(skillType));
        return switch (skillType) {
            case COMBAT -> CombatLevelTable.definitionForLevel(clampedLevel).totalXpRequired();
            case MINING -> MiningLevelTable.definitionForLevel(clampedLevel).totalXpRequired();
            case FORAGING -> ForagingLevelTable.definitionForLevel(clampedLevel).totalXpRequired();
            case FARMING -> FarmingLevelTable.definitionForLevel(clampedLevel).totalXpRequired();
            case FISHING -> FishingLevelTable.definitionForLevel(clampedLevel).totalXpRequired();
            case ENCHANTING -> EnchantingLevelTable.definitionForLevel(clampedLevel).totalXpRequired();
            case ALCHEMY -> AlchemyLevelTable.definitionForLevel(clampedLevel).totalXpRequired();
            case TAMING -> TamingLevelTable.definitionForLevel(clampedLevel).totalXpRequired();
            case EXPLORATION, CRAFTING, SOCIAL -> CombatLevelTable.definitionForLevel(clampedLevel).totalXpRequired();
        };
    }
}
