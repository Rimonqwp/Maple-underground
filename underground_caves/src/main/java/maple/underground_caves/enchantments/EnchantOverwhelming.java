package maple.underground_caves.enchantments;

/**
 * 排山倒海 (I-III) 劍 普通附魔
 * 攻擊時有 15%/30%/50% 的機率觸發強力擊退，將敵人擊退 3/4.5/6 格
 */
public final class EnchantOverwhelming extends CustomEnchantment {

    private static final int MAX_LEVEL = 3;
    private static final double[] CHANCE = { 15, 30, 50 };
    private static final double[] KNOCKBACK_BLOCKS = { 3, 4.5, 6 };

    public EnchantOverwhelming() {
        super("overwhelming", "排山倒海", MAX_LEVEL, EnchantType.NORMAL, CustomEnchantment.SWORDS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return "攻擊時有 " + CHANCE[level - 1] + "% 的機率觸發強力擊退，將敵人擊退 " + formatBlocks(KNOCKBACK_BLOCKS[level - 1]) + " 格";
    }

    private static String formatBlocks(double b) {
        return b == (long) b ? String.valueOf((long) b) : String.valueOf(b);
    }

    public double getKnockbackChance(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return CHANCE[level - 1] / 100.0;
    }

    public double getKnockbackBlocks(int level) {
        if (level < 1 || level > getMaxLevel()) return 3;
        return KNOCKBACK_BLOCKS[level - 1];
    }
}
