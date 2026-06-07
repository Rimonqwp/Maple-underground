package maple.underground_caves.enchantments;

/**
 * 鮮血暴擊 (I-V) 劍 普通附魔
 * 每次攻擊造成的暴擊傷害，每 100 點暴擊傷害治療你最大生命值的 0.2%/0.3%/0.4%/0.5%/0.6%，最多計算 1000 點暴擊傷害
 */
public final class EnchantBloodCrit extends CustomEnchantment {

    private static final int MAX_LEVEL = 5;
    private static final double[] HEAL_PER_100_CRIT = { 0.2, 0.3, 0.4, 0.5, 0.6 };
    public static final double MAX_CRIT_CAP = 1000;

    public EnchantBloodCrit() {
        super("blood_crit", "鮮血暴擊", MAX_LEVEL, EnchantType.NORMAL, CustomEnchantment.SWORDS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return "每次攻擊造成的暴擊傷害，每 100 點暴擊傷害治療你最大生命值的 " + HEAL_PER_100_CRIT[level - 1] + "%，最多計算 1000 點暴擊傷害";
    }

    public double getHealPercentPer100Crit(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return HEAL_PER_100_CRIT[level - 1] / 100.0;
    }
}
