package maple.underground_state.professions;

import maple.underground_state.skills.SkillType;

public final class ProfessionMath {
    public static final int[] COLLECTION_REQUIREMENTS =
            {50, 100, 250, 500, 1000, 2500, 5000, 10000, 50000, 100000, 250000, 500000};

    private ProfessionMath() {}

    public static boolean isProfession(SkillType skill) {
        return skill == SkillType.FARMING || skill == SkillType.MINING;
    }

    public static double mastery(SkillType skill, int level) {
        int bounded = Math.clamp(level, 0, 60);
        return 1 + (skill == SkillType.FARMING ? .06 : .05) * bounded * bounded;
    }

    public static long xpUnits(int baseTenths, SkillType skill, int level, double bonusPercent) {
        return Math.round(baseTenths * 1000L * mastery(skill, level)
                * (1 + Math.clamp(bonusPercent, 0, 40) / 100));
    }

    public static int extraAmount(int baseAmount, double fortune, double random) {
        double hundreds = Math.max(0, fortune) / 100;
        long amount = (long) baseAmount * (long) Math.floor(hundreds);
        if (random < hundreds - Math.floor(hundreds)) amount += baseAmount;
        return (int) Math.min(Integer.MAX_VALUE - (long) baseAmount, amount);
    }

    public static boolean roll(double baseChance, double relativePercent, long misses,
                               int guarantee, double random) {
        return misses + 1 >= guarantee || random < baseChance * (1 + relativePercent / 100);
    }

    public static int collectionTier(long amount, boolean extended) {
        int tier = 0;
        for (int i = 0; i < (extended ? 12 : 9); i++) {
            if (amount < COLLECTION_REQUIREMENTS[i]) break;
            tier++;
        }
        return tier;
    }

    public static int collectionXp(int tier, boolean profession) {
        int[] legacy = {20, 50, 100, 250, 500, 1000, 2000, 3500, 5000};
        if (tier <= 0) return 0;
        if (tier <= 9) return profession ? legacy[tier - 1] / 10 : legacy[tier - 1];
        return switch (tier) { case 10 -> 750; case 11 -> 1000; default -> 1500; };
    }

    public static long coins(int level) {
        return level <= 10 ? 100 : level <= 25 ? 250 : level <= 40 ? 500 : 1000;
    }
}
