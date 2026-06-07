package maple.underground_caves.enchantments;

/**
 * 無限箭袋 (I-X) 弓附魔 普通附魔
 * 射箭時，有 N% 機率不消耗箭矢。
 */
public final class EnchantInfiniteQuiver extends CustomEnchantment {

    private static final int MAX_LEVEL_QUIVER = 10;
    private static final double BASE_CHANCE = 0.04; // 4% per level

    private static final String[] DESCRIPTIONS = {
        "射箭時，有 4%機率不消耗箭矢",
        "射箭時，有 8%機率不消耗箭矢",
        "射箭時，有 12%機率不消耗箭矢",
        "射箭時，有 16%機率不消耗箭矢",
        "射箭時，有 20%機率不消耗箭矢",
        "射箭時，有 24%機率不消耗箭矢",
        "射箭時，有 28%機率不消耗箭矢",
        "射箭時，有 32%機率不消耗箭矢",
        "射箭時，有 36%機率不消耗箭矢",
        "射箭時，有 40%機率不消耗箭矢"
    };

    public EnchantInfiniteQuiver() {
        super("infinite_quiver", "無限箭袋", MAX_LEVEL_QUIVER, EnchantType.NORMAL, CustomEnchantment.BOWS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return DESCRIPTIONS[level - 1];
    }

    /** 不消耗箭矢的機率 0~1 */
    public double getNoConsumeChance(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return Math.min(1.0, BASE_CHANCE * level);
    }
}
