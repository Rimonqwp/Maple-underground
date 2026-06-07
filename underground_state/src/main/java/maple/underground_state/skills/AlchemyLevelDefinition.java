package maple.underground_state.skills;

public record AlchemyLevelDefinition(
        int level,
        long xpRequired,
        long totalXpRequired,
        long coinReward,
        long totalCoinReward,
        int intelligenceReward,
        int totalIntelligenceReward,
        int potionDurationReward,
        int totalPotionDurationReward,
        int mapleXpReward,
        int totalMapleXpReward
) {
}

