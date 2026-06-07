package maple.underground_caves.enchantments;

/**
 * 挑戰者 (I-V) 劍 普通附魔
 * 目標當前生命值每比你高出 5%/4.5%/4%/3.5%/3%，對其造成的傷害增加 +0.1%/0.3%/0.5%/0.8%/1.2%
 */
public final class EnchantChallenger extends CustomEnchantment {

    private static final int MAX_LEVEL = 5;
    private static final double[] HP_RATIO_PER_PERCENT = { 5, 4.5, 4, 3.5, 3 };
    private static final double[] DAMAGE_PER_PERCENT = { 0.1, 0.3, 0.5, 0.8, 1.2 };

    public EnchantChallenger() {
        super("challenger", "挑戰者", MAX_LEVEL, EnchantType.NORMAL, CustomEnchantment.SWORDS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return "目標當前生命值每比你高出 " + HP_RATIO_PER_PERCENT[level - 1] + "%，對其造成的傷害增加 +" + DAMAGE_PER_PERCENT[level - 1] + "%";
    }

    public double getHpRatioPerPercent(int level) {
        if (level < 1 || level > getMaxLevel()) return 5;
        return HP_RATIO_PER_PERCENT[level - 1];
    }

    public double getDamagePerPercent(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return DAMAGE_PER_PERCENT[level - 1] / 100.0;
    }
}
