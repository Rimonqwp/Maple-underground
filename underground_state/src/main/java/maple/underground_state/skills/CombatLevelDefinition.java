package maple.underground_state.skills;

import org.jetbrains.annotations.Nullable;

public record CombatLevelDefinition(
        int level,
        long xpRequired,
        long totalXpRequired,
        int coinReward,
        int totalCoinReward,
        double critChanceReward,
        double totalCritChanceReward,
        int extraDamageRewardPercent,
        int totalExtraDamageRewardPercent,
        int mapleXpReward,
        int totalMapleXpReward,
        @Nullable String unlockNote
) {
}
