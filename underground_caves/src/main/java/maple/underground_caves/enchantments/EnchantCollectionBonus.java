package maple.underground_caves.enchantments;

/**
 * 元气小收藏 (I-II) 飾品附魔 終極附魔
 * 每完成一個滿級收藏，獲得 +N 生命和 +N 力量加成。
 */
public final class EnchantCollectionBonus extends CustomEnchantment {

    private static final double[] BONUS = { 0.1, 0.2 };

    public EnchantCollectionBonus() {
        super("collection_bonus", "元气小收藏", 2, EnchantType.ULTIMATE, CustomEnchantment.ARMOR);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return String.format("每完成一個滿級收藏，獲得 +%.1f 生命和 +%.1f 力量加成", BONUS[level - 1], BONUS[level - 1]);
    }

    public double getHealthAndStrengthPerCollection(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return BONUS[level - 1];
    }
}
