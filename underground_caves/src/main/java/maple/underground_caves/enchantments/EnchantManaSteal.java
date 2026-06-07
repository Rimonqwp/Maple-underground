package maple.underground_caves.enchantments;

/**
 * 法力汲取 (I-V) 劍 普通附魔
 * 每次擊中目標時，回復你最大法力值的 0.25%/0.35%/0.45%/0.6%/0.75%
 */
public final class EnchantManaSteal extends CustomEnchantment {

    private static final int MAX_LEVEL = 5;
    private static final double[] MANA_PERCENT = { 0.25, 0.35, 0.45, 0.6, 0.75 };

    public EnchantManaSteal() {
        super("mana_steal", "法力汲取", MAX_LEVEL, EnchantType.NORMAL, CustomEnchantment.SWORDS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return "每次擊中目標時，回復你最大法力值的 " + MANA_PERCENT[level - 1] + "%";
    }

    public double getManaPercentOfMax(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return MANA_PERCENT[level - 1] / 100.0;
    }
}
