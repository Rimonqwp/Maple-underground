package maple.underground_state.skills;

public record EnchantingLevelDefinition(
        int level,
        long xpRequired,
        long totalXpRequired,
        long coinReward,
        long totalCoinReward,
        int intelligenceReward,
        int totalIntelligenceReward,
        double abilityDamageReward,
        double totalAbilityDamageReward,
        int bonusExpReward,
        int totalBonusExpReward,
        int mapleXpReward,
        int totalMapleXpReward
) {
}

