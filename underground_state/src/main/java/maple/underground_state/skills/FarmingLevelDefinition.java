package maple.underground_state.skills;

public record FarmingLevelDefinition(
        int level,
        long xpRequired,
        long totalXpRequired,
        long coinReward,
        long totalCoinReward,
        int healthReward,
        int totalHealthReward,
        int farmingFortuneReward,
        int totalFarmingFortuneReward,
        int mapleXpReward,
        int totalMapleXpReward
) {
}
