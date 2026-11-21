package maple.underground_collect;

import maple.underground_state.gui.CollectionInfoGui;
import org.jetbrains.annotations.NotNull;

final class CollectionMath {

    static final int[] TIER_REQUIREMENTS = {
            50, 100, 250, 500, 1_000, 2_500, 5_000, 10_000, 50_000
    };

    private CollectionMath() {
    }

    static boolean extended(@NotNull CollectionInfoGui.Category category) {
        return CollectionInfoGui.extended(category);
    }

    static int maxTier(@NotNull CollectionInfoGui.Category category) {
        return CollectionInfoGui.maxTier(category);
    }

    static int currentTier(long amount, @NotNull CollectionInfoGui.Category category) {
        return CollectionInfoGui.tierFor(amount, category);
    }

    static int tierXpReward(int tier, @NotNull CollectionInfoGui.Category category) {
        return CollectionInfoGui.xpReward(category, tier);
    }

    static int maxTier() {
        return TIER_REQUIREMENTS.length;
    }

    static int currentTier(long collected) {
        int tier = 0;
        for (int requirement : TIER_REQUIREMENTS) {
            if (collected < requirement) {
                break;
            }
            tier++;
        }
        return tier;
    }

    static int tierXpReward(int tier) {
        return switch (tier) {
            case 1 -> 20;
            case 2 -> 50;
            case 3 -> 100;
            case 4 -> 250;
            case 5 -> 500;
            case 6 -> 1_000;
            case 7 -> 2_000;
            case 8 -> 3_500;
            default -> 5_000;
        };
    }

    static int mapleXpReward(int tier) {
        return CollectionInfoGui.mapleXpReward(tier);
    }

    static @NotNull String roman(int number) {
        return CollectionInfoGui.roman(number);
    }

    static @NotNull String formatNumber(long value) {
        return CollectionInfoGui.formatNumber(value);
    }
}
