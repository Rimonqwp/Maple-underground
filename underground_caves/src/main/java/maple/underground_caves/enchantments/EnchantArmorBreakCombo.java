package maple.underground_caves.enchantments;

/**
 * 碎甲連擊 (I-V) 劍 普通附魔
 * 每次近戰攻擊命中目標時，降低其 ❈防禦 1.2%/2.5%/4%/6%/9%，持續 4 秒。效果可疊加，最多疊加 4 層
 */
public final class EnchantArmorBreakCombo extends CustomEnchantment {

    private static final int MAX_LEVEL = 5;
    private static final double[] DEFENSE_REDUCE_PERCENT = { 1.2, 2.5, 4, 6, 9 };
    public static final int DURATION_SEC = 4;
    public static final int MAX_STACKS = 4;

    public EnchantArmorBreakCombo() {
        super("armor_break_combo", "碎甲連擊", MAX_LEVEL, EnchantType.NORMAL, CustomEnchantment.SWORDS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return "每次近戰攻擊命中目標時，降低其 ❈防禦 " + DEFENSE_REDUCE_PERCENT[level - 1] + "%，持續 " + DURATION_SEC + " 秒。效果可疊加，最多疊加 " + MAX_STACKS + " 層";
    }

    public double getDefenseReducePercent(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return DEFENSE_REDUCE_PERCENT[level - 1] / 100.0;
    }
}
