package maple.underground_caves.enchantments;

/**
 * 屠龍者 (I-V) 劍、弓附魔 普通附魔
 * 對終界龍造成的傷害提升 +N%。
 */
public final class EnchantDragonSlayer extends CustomEnchantment {

    private static final int[] DAMAGE_PERCENT = { 5, 10, 15, 20, 25 };

    public EnchantDragonSlayer() {
        super("dragon_slayer", "屠龍者", 5, EnchantType.NORMAL, CustomEnchantment.SWORD_AND_BOW);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return "對終界龍造成的傷害提升 +" + DAMAGE_PERCENT[level - 1] + "%";
    }

    public double getDamageBonusPercent(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return DAMAGE_PERCENT[level - 1] / 100.0;
    }
}
