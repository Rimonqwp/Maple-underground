package maple.underground_caves.enchantments;

/**
 * 終界殺手 (I-VII) 劍附魔 普通附魔
 * 對終界使者、終界蟎和終界龍造成的傷害提升 +N%。
 */
public final class EnchantEnderSlayer extends CustomEnchantment {

    private static final int MAX_LEVEL = 7;
    private static final int[] DAMAGE_PERCENT = { 15, 30, 45, 60, 80, 100, 130 };

    public EnchantEnderSlayer() {
        super("ender_slayer", "終界殺手", MAX_LEVEL, EnchantType.NORMAL, CustomEnchantment.SWORDS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return "對終界使者、終界蟎和終界龍造成的傷害提升 +" + DAMAGE_PERCENT[level - 1] + "%";
    }

    public double getDamageBonusPercent(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return DAMAGE_PERCENT[level - 1] / 100.0;
    }
}
