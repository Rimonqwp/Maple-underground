package maple.underground_state.skills;

public record MiningLevelDefinition(
        int level,
        long xpRequired,
        long totalXpRequired,
        long coinReward,
        long totalCoinReward,
        int defenseReward,
        int totalDefenseReward,
        int miningFortuneReward,
        int totalMiningFortuneReward,
        int mapleXpReward,
        int totalMapleXpReward
) {
}
