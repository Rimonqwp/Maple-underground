package maple.underground_caves.enchantments;

/**
 * 連擊風暴 (I-V) 劍附魔 終極附魔
 * 每次攻擊會增加你的連擊，每層連擊造成 +N% 額外傷害。最後一次攻擊後3秒內，傷害上限提升至200%！
 */
public final class EnchantComboStorm extends CustomEnchantment {

    private static final int[] PER_STACK_PERCENT = { 10, 20, 30, 40, 50 };
    private static final int CAP_PERCENT = 200;
    private static final int WINDOW_SECONDS = 3;

    public EnchantComboStorm() {
        super("combo_storm", "連擊風暴", 5, EnchantType.ULTIMATE, CustomEnchantment.SWORDS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return String.format("每次攻擊會增加你的連擊，每層連擊造成 +%d%% 額外傷害。最後一次攻擊後%d秒內，傷害上限提升至%d%%！",
            PER_STACK_PERCENT[level - 1], WINDOW_SECONDS, CAP_PERCENT);
    }

    public int getPerStackPercent(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return PER_STACK_PERCENT[level - 1];
    }

    public int getDamageCapPercent() { return CAP_PERCENT; }
    public int getWindowSeconds() { return WINDOW_SECONDS; }
}
