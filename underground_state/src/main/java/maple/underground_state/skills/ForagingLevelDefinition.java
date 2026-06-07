package maple.underground_state.skills;

public record ForagingLevelDefinition(
        int level,
        long xpRequired,
        long totalXpRequired,
        long coinReward,
        long totalCoinReward,
        int strengthReward,
        int totalStrengthReward,
        int foragingFortuneReward,
        int totalForagingFortuneReward,
        int mapleXpReward,
        int totalMapleXpReward
) {
}

