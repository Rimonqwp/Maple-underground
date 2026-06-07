package maple.underground_caves.enchantments;

/**
 * 殺戮回春 (I-V) 劍 普通附魔，不可鐵砧升級
 * 每次擊殺敵人時，恢復你已損失生命值的 1%/2.2%/3.5%/5%/6%
 */
public final class EnchantKillHeal extends CustomEnchantment {

    private static final int MAX_LEVEL = 5;
    private static final double[] HEAL_MISSING_PERCENT = { 1, 2.2, 3.5, 5, 6 };

    public EnchantKillHeal() {
        super("kill_heal", "殺戮回春", MAX_LEVEL, EnchantType.NORMAL, CustomEnchantment.SWORDS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return "每次擊殺敵人時，恢復你已損失生命值的 " + HEAL_MISSING_PERCENT[level - 1] + "%";
    }

    public double getHealMissingHpPercent(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return HEAL_MISSING_PERCENT[level - 1] / 100.0;
    }
}
