package maple.underground_state.skills;

public record FishingLevelDefinition(
        int level,
        long xpRequired,
        long totalXpRequired,
        long coinReward,
        long totalCoinReward,
        int healthReward,
        int totalHealthReward,
        double treasureChanceReward,
        double totalTreasureChanceReward,
        int mapleXpReward,
        int totalMapleXpReward
) {
}

