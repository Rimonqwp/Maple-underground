package maple.underground_caves.enchantments;

/**
 * 幸運兒 (I-V) 劍附魔 普通附魔
 * 賦予 +N% 稀有物品掉落率加成。
 */
public final class EnchantLucky extends CustomEnchantment {

    private static final int[] DROP_PERCENT = { 1, 2, 3, 4, 5 };

    public EnchantLucky() {
        super("lucky", "幸運兒", 5, EnchantType.NORMAL, CustomEnchantment.SWORDS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return "賦予 +" + DROP_PERCENT[level - 1] + "% 稀有物品掉落率加成";
    }

    public double getRareDropPercent(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return DROP_PERCENT[level - 1] / 100.0;
    }
}
