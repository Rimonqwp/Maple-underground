package maple.underground_caves.enchantments;

/**
 * 賞金獵人 (I-V) 劍 普通附魔
 * 擊殺怪物時，獲得 0.3/0.6/0.9/1.3/1.8 枚金幣
 */
public final class EnchantBountyHunter extends CustomEnchantment {

    private static final int MAX_LEVEL = 5;
    private static final double[] COINS = { 0.3, 0.6, 0.9, 1.3, 1.8 };

    public EnchantBountyHunter() {
        super("bounty_hunter", "賞金獵人", MAX_LEVEL, EnchantType.NORMAL, CustomEnchantment.SWORDS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return "擊殺怪物時，獲得 " + COINS[level - 1] + " 枚金幣";
    }

    public double getCoinsPerKill(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return COINS[level - 1];
    }
}
