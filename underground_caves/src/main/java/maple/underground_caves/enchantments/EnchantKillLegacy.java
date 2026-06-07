package maple.underground_caves.enchantments;

/**
 * 獵殺傳承 (I-V) 劍、弓附魔 終極附魔
 * 你的武器獲得最近擊殺的怪物的 N 倍傷害，並在你的下一次暴擊時加上該傷害。
 */
public final class EnchantKillLegacy extends CustomEnchantment {

    private static final double[] MULTIPLIER = { 1.5, 2.0, 2.5, 3.0, 4.0 };

    public EnchantKillLegacy() {
        super("kill_legacy", "獵殺傳承", 5, EnchantType.ULTIMATE, CustomEnchantment.SWORD_AND_BOW);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return String.format("你的武器獲得最近擊殺的怪物的 %.1f倍 傷害，並在你的下一次暴擊時加上該傷害", MULTIPLIER[level - 1]);
    }

    public double getDamageMultiplier(int level) {
        if (level < 1 || level > getMaxLevel()) return 1.0;
        return MULTIPLIER[level - 1];
    }
}
