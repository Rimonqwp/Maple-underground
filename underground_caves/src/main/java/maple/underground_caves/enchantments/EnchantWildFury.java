package maple.underground_caves.enchantments;

/**
 * 狂野之怒 (I-III) 劍 普通附魔
 * 獲得 +3/4/5 combo
 */
public final class EnchantWildFury extends CustomEnchantment {

    private static final int MAX_LEVEL = 3;
    private static final int[] BONUS_COMBO = { 3, 4, 5 };

    public EnchantWildFury() {
        super("wild_fury", "狂野之怒", MAX_LEVEL, EnchantType.NORMAL, CustomEnchantment.SWORDS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return "獲得 +" + BONUS_COMBO[level - 1] + " combo";
    }

    public int getBonusCombo(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return BONUS_COMBO[level - 1];
    }
}
