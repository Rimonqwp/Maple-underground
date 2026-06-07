package maple.underground_caves.enchantments;

/**
 * 羽毛翅膀 (I-X) 鞋子附魔 普通附魔
 * 減少摔落傷害，並且有機率完全免疫跌落傷害。
 */
public final class EnchantFeatherWing extends CustomEnchantment {

    private static final int MAX_LEVEL = 10;
    private static final double[] REDUCE_PERCENT = { 3, 6, 9, 12, 15, 18, 21, 24, 27, 30 };
    private static final double[] BASE_IMMUNE = { 0, 0, 0, 0, 0.5, 0.5, 1, 1.5, 2, 2.5 };
    private static final int[] HEIGHT_BONUS_BLOCKS = { 4, 4, 4, 4, 4, 5, 5, 6, 7, 8 };

    private static final String[] DESCRIPTIONS = {
        "減少 3% 摔落傷害，並且有 0% 完全免疫跌落傷害 [4格 高度免疫加成]",
        "減少 6% 摔落傷害，並且有 0% 完全免疫跌落傷害 [4格 高度免疫加成]",
        "減少 9% 摔落傷害，並且有 0% 完全免疫跌落傷害 [4格 高度免疫加成]",
        "減少 12% 摔落傷害，並且有 0% 完全免疫跌落傷害 [4格 高度免疫加成]",
        "減少 15% 摔落傷害，並且有 0.5% 完全免疫跌落傷害 [4格 高度免疫加成]",
        "減少 18% 摔落傷害，並且有 0.5% 完全免疫跌落傷害 [5格 高度免疫加成]",
        "減少 21% 摔落傷害，並且有 1% 完全免疫跌落傷害 [5格 高度免疫加成]",
        "減少 24% 摔落傷害，並且有 1.5% 完全免疫跌落傷害 [6格 高度免疫加成]",
        "減少 27% 摔落傷害，並且有 2% 完全免疫跌落傷害 [7格 高度免疫加成]",
        "減少 30% 摔落傷害，並且有 2.5% 完全免疫跌落傷害 [8格 高度免疫加成]"
    };

    public EnchantFeatherWing() {
        super("feather_wing", "羽毛翅膀", MAX_LEVEL, EnchantType.NORMAL, CustomEnchantment.BOOTS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return DESCRIPTIONS[level - 1];
    }

    public double getDamageReducePercent(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return REDUCE_PERCENT[level - 1];
    }

    public double getBaseImmuneChance(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return BASE_IMMUNE[level - 1] / 100.0;
    }

    public int getHeightBonusBlocks(int level) {
        if (level < 1 || level > getMaxLevel()) return 4;
        return HEIGHT_BONUS_BLOCKS[level - 1];
    }
}
