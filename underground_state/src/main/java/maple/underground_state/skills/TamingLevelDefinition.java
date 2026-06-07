package maple.underground_state.skills;

public record TamingLevelDefinition(
        int level,
        long xpRequired,
        long totalXpRequired,
        long coinReward,
        long totalCoinReward,
        int petLuckReward,
        int totalPetLuckReward,
        int petXpGainReward,
        int totalPetXpGainReward,
        double expShareRateReward,
        double totalExpShareRateReward,
        int mapleXpReward,
        int totalMapleXpReward
) {
}

