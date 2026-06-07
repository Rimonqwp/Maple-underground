package maple.underground_caves.enchantments;

/**
 * 奇襲 (I-V) 劍 普通附魔
 * 對尚未對你造成過傷害的怪物進行第一次近戰攻擊時，傷害提高 +10%/20%/30%/40%/50%。
 */
public final class EnchantSurpriseStrike extends CustomEnchantment {

    private static final int MAX_LEVEL = 5;
    private static final double[] BONUS_PERCENT = { 10, 20, 30, 40, 50 };

    public EnchantSurpriseStrike() {
        super("surprise_strike", "奇襲", MAX_LEVEL, EnchantType.NORMAL, CustomEnchantment.SWORDS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return "對尚未對你造成過傷害的怪物進行第一次近戰攻擊時，傷害提高 +" + BONUS_PERCENT[level - 1] + "%";
    }

    public double getDamageBonusPercent(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return BONUS_PERCENT[level - 1] / 100.0;
    }
}
