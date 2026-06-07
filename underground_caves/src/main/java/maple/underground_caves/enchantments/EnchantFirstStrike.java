package maple.underground_caves.enchantments;

/**
 * 先發制人 (I-V) 劍 普通附魔
 * 對一個怪物進行的前 3 次近戰攻擊，每次傷害提高 10%/18%/28%/40%/50%
 */
public final class EnchantFirstStrike extends CustomEnchantment {

    private static final int MAX_LEVEL = 5;
    private static final int HIT_COUNT = 3;
    private static final double[] BONUS_PERCENT = { 10, 18, 28, 40, 50 };

    public EnchantFirstStrike() {
        super("first_strike", "先發制人", MAX_LEVEL, EnchantType.NORMAL, CustomEnchantment.SWORDS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return "對一個怪物進行的前 " + HIT_COUNT + " 次近戰攻擊，每次傷害提高 " + BONUS_PERCENT[level - 1] + "%";
    }

    public int getHitCount() { return HIT_COUNT; }
    public double getBonusPercent(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return BONUS_PERCENT[level - 1] / 100.0;
    }
}
