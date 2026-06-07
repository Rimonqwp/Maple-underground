package maple.underground_caves.enchantments;

/**
 * 孤狼 (I-III) 劍 普通附魔
 * 當你沒有裝備任何寵物時，獲得 +2/2.5/3 武器傷害
 */
public final class EnchantLoneWolf extends CustomEnchantment {

    private static final int MAX_LEVEL = 3;
    private static final double[] BONUS_DAMAGE = { 2, 2.5, 3 };

    public EnchantLoneWolf() {
        super("lone_wolf", "孤狼", MAX_LEVEL, EnchantType.NORMAL, CustomEnchantment.SWORDS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return "當你沒有裝備任何寵物時，獲得 +" + formatDamage(BONUS_DAMAGE[level - 1]) + " 武器傷害";
    }

    private static String formatDamage(double d) {
        return d == (long) d ? String.valueOf((long) d) : String.valueOf(d);
    }

    public double getBonusDamage(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return BONUS_DAMAGE[level - 1];
    }
}
