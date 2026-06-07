package maple.underground_caves.enchantments;

/**
 * 枯木逢春 (I-V) 劍附魔 終極附魔
 * 最脆弱的木棍，也能斬斷鋼鐵，+N 點傷害。
 */
public final class EnchantDeadwoodRevival extends CustomEnchantment {

    private static final int[] BONUS_DAMAGE = { 2, 3, 4, 5, 6 };

    /** 各等級完整效果描述 */
    private static final String[] DESCRIPTIONS = {
        "最脆弱的木棍，也能斬斷鋼鐵，+2 點傷害",
        "最脆弱的木棍，也能斬斷鋼鐵，+3 點傷害",
        "最脆弱的木棍，也能斬斷鋼鐵，+4 點傷害",
        "最脆弱的木棍，也能斬斷鋼鐵，+5 點傷害",
        "最脆弱的木棍，也能斬斷鋼鐵，+6 點傷害"
    };

    public EnchantDeadwoodRevival() {
        super("deadwood_revival", "枯木逢春", 5, EnchantType.ULTIMATE, CustomEnchantment.SWORDS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return DESCRIPTIONS[level - 1];
    }

    public int getBonusDamage(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return BONUS_DAMAGE[level - 1];
    }
}
