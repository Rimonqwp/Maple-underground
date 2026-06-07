package maple.underground_caves.enchantments;

/**
 * 致命一擊 (I-V) 劍附魔 普通附魔
 * 提升 +N% 的暴擊傷害。
 */
public final class EnchantCriticalStrike extends CustomEnchantment {

    private static final int[] CRIT_PERCENT = { 10, 20, 30, 40, 50 };

    public EnchantCriticalStrike() {
        super("critical_strike", "致命一擊", 5, EnchantType.NORMAL, CustomEnchantment.SWORDS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return "提升 +" + CRIT_PERCENT[level - 1] + "% 的暴擊傷害";
    }

    public double getCritDamagePercent(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return CRIT_PERCENT[level - 1] / 100.0;
    }
}
