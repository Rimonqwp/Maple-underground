package maple.underground_caves.enchantments;

/**
 * 斂財 (I-V) 裝備附魔 終極附魔
 * 死亡時節省 N% 的金幣。
 */
public final class EnchantDeathPennies extends CustomEnchantment {

    private static final int[] SAVE_PERCENT = { 10, 20, 30, 40, 50 };

    public EnchantDeathPennies() {
        super("death_pennies", "斂財", 5, EnchantType.ULTIMATE, CustomEnchantment.ARMOR);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return "死亡時節省 " + SAVE_PERCENT[level - 1] + "% 的金幣";
    }

    public double getSavePercent(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return SAVE_PERCENT[level - 1] / 100.0;
    }
}
