package maple.underground_caves.enchantments;

/**
 * 餘火追擊 (I-V) 弓附魔 終極附魔
 * 射擊時額外射出一支箭，造成主箭 N% 的傷害。被擊中的目標會受到持續火焰傷害，持續60秒!
 */
public final class EnchantEmberChase extends CustomEnchantment {

    private static final int[] EXTRA_ARROW_PERCENT = { 3, 6, 9, 12, 15 };
    private static final double[] FIRE_MULTIPLIER = { 1.1, 1.2, 1.3, 1.4, 1.5 };
    private static final int DURATION_SECONDS = 60;

    public EnchantEmberChase() {
        super("ember_chase", "餘火追擊", 5, EnchantType.ULTIMATE, CustomEnchantment.BOWS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return String.format("射擊時額外射出一支箭，造成主箭 %d%% 的傷害。被擊中的目標會受到%.1f倍持續火焰傷害，持續%d秒!",
            EXTRA_ARROW_PERCENT[level - 1], FIRE_MULTIPLIER[level - 1], DURATION_SECONDS);
    }

    public int getExtraArrowDamagePercent(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return EXTRA_ARROW_PERCENT[level - 1];
    }

    public double getFireMultiplier(int level) {
        if (level < 1 || level > getMaxLevel()) return 1.0;
        return FIRE_MULTIPLIER[level - 1];
    }

    public int getDurationSeconds() { return DURATION_SECONDS; }
}
