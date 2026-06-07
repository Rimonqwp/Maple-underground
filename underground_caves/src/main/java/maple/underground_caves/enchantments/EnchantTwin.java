package maple.underground_caves.enchantments;

/**
 * 雙子附魔 (I-V) 劍/鎬 普通附魔
 * 擊殺怪物時，有 12.5%/25%/37.5%/50%/62.5% 的機率獲得雙倍經驗值。
 */
public final class EnchantTwin extends CustomEnchantment {

    private static final int MAX_LEVEL = 5;
    /** 每級機率 12.5% */
    private static final double CHANCE_PER_LEVEL = 12.5;

    public EnchantTwin() {
        super("twin", "雙子附魔", MAX_LEVEL, EnchantType.NORMAL, CustomEnchantment.SWORD_AND_PICKAXE);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        double chance = CHANCE_PER_LEVEL * level;
        return "擊殺怪物時，有 " + formatChance(chance) + "% 的機率獲得雙倍經驗值";
    }

    private static String formatChance(double c) {
        return c == (long) c ? String.valueOf((long) c) : String.valueOf(c);
    }

    /** 該等級觸發雙倍經驗的機率（0~1） */
    public double getDoubleExpChance(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return (CHANCE_PER_LEVEL * level) / 100.0;
    }
}
