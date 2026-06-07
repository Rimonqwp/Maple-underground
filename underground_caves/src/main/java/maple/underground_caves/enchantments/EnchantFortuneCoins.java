package maple.underground_caves.enchantments;

/**
 * 招財進寶 (I-V) 劍附魔 終極附魔
 * 擊殺敵人掉落金幣。
 */
public final class EnchantFortuneCoins extends CustomEnchantment {

    private static final double[] COINS = { 0.5, 1.0, 1.5, 2.0, 2.5 };

    /** 各等級完整效果描述 */
    private static final String[] DESCRIPTIONS = {
        "擊殺敵人掉落 +0.5 金幣",
        "擊殺敵人掉落 +1 金幣",
        "擊殺敵人掉落 +1.5 金幣",
        "擊殺敵人掉落 +2 金幣",
        "擊殺敵人掉落 +2.5 金幣"
    };

    public EnchantFortuneCoins() {
        super("fortune_coins", "招財進寶", 5, EnchantType.ULTIMATE, CustomEnchantment.SWORDS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return DESCRIPTIONS[level - 1];
    }

    public double getCoinsPerKill(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return COINS[level - 1];
    }
}
