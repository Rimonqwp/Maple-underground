package maple.underground_caves.enchantments;

/**
 * 節流 (I-V) 武器附魔 終極附魔
 * 降低該物品技能 N% 的法力消耗量。
 */
public final class EnchantThrift extends CustomEnchantment {

    private static final int[] REDUCE_PERCENT = { 10, 20, 30, 40, 50 };

    public EnchantThrift() {
        super("thrift", "節流", 5, EnchantType.ULTIMATE, CustomEnchantment.WEAPONS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return "降低該物品技能 " + REDUCE_PERCENT[level - 1] + "% 的法力消耗量";
    }

    public double getManaReducePercent(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return REDUCE_PERCENT[level - 1] / 100.0;
    }
}
