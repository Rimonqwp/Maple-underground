package maple.underground_caves.enchantments;

/**
 * 斬殺 (I-V) 劍 普通附魔
 * 目標每失去 1% 生命值，你對其造成的傷害增加 0.1%/0.3%/0.5%/0.7%/1%
 */
public final class EnchantExecute extends CustomEnchantment {

    private static final int MAX_LEVEL = 5;
    private static final double[] DAMAGE_PER_MISSING_PERCENT = { 0.1, 0.3, 0.5, 0.7, 1 };

    public EnchantExecute() {
        super("execute", "斬殺", MAX_LEVEL, EnchantType.NORMAL, CustomEnchantment.SWORDS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return "目標每失去 1% 生命值，你對其造成的傷害增加 " + DAMAGE_PER_MISSING_PERCENT[level - 1] + "%";
    }

    public double getDamagePerMissingHpPercent(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return DAMAGE_PER_MISSING_PERCENT[level - 1] / 100.0;
    }
}
