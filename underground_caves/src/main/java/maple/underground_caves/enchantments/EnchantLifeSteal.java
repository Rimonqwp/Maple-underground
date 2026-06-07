package maple.underground_caves.enchantments;

/**
 * 生命汲取 (I-V) 劍 普通附魔
 * 每次攻擊怪物時，恢復你最大生命值的 0.5%/1%/1.5%/2%/2.5%
 */
public final class EnchantLifeSteal extends CustomEnchantment {

    private static final int MAX_LEVEL = 5;
    private static final double[] HEAL_PERCENT = { 0.5, 1, 1.5, 2, 2.5 };

    public EnchantLifeSteal() {
        super("life_steal", "生命汲取", MAX_LEVEL, EnchantType.NORMAL, CustomEnchantment.SWORDS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return "每次攻擊怪物時，恢復你最大生命值的 " + HEAL_PERCENT[level - 1] + "%";
    }

    public double getHealPercentOfMaxHp(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return HEAL_PERCENT[level - 1] / 100.0;
    }
}
