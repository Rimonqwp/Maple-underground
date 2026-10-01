package maple.underground_collect;

final class CollectionMath {

    static final int[] TIER_REQUIREMENTS = {
            50, 100, 250, 500, 1_000, 2_500, 5_000, 10_000, 50_000
    };

    static boolean extended(maple.underground_state.gui.CollectionInfoGui.Category category) {
        return category == maple.underground_state.gui.CollectionInfoGui.Category.FARMING || category == maple.underground_state.gui.CollectionInfoGui.Category.MINING;
    }
    static int maxTier(maple.underground_state.gui.CollectionInfoGui.Category category) { return extended(category) ? 12 : 9; }
    static int currentTier(long amount, maple.underground_state.gui.CollectionInfoGui.Category category) {
        return maple.underground_state.professions.ProfessionMath.collectionTier(amount, extended(category));
    }
    static int tierXpReward(int tier, maple.underground_state.gui.CollectionInfoGui.Category category) {
        return maple.underground_state.professions.ProfessionMath.collectionXp(tier, extended(category));
    }
    private CollectionMath() {
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
        return Math.max(1, tier / 2);
    }

    static String roman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            case 11 -> "XI";
            case 12 -> "XII";
            default -> String.valueOf(number);
        };
    }
}
