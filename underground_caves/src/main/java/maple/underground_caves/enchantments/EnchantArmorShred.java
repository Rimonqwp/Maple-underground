package maple.underground_caves.enchantments;

/**
 * 剝甲專家 (I-V) 劍 普通附魔
 * 擊殺穿著盔甲的怪物時，使其掉落身上盔甲的機率提高 5%/10%/15%/25%/35%
 */
public final class EnchantArmorShred extends CustomEnchantment {

    private static final int MAX_LEVEL = 5;
    private static final double[] BONUS_DROP_PERCENT = { 5, 10, 15, 25, 35 };

    public EnchantArmorShred() {
        super("armor_shred", "剝甲專家", MAX_LEVEL, EnchantType.NORMAL, CustomEnchantment.SWORDS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return "擊殺穿著盔甲的怪物時，使其掉落身上盔甲的機率提高 " + BONUS_DROP_PERCENT[level - 1] + "%";
    }

    public double getArmorDropBonusPercent(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return BONUS_DROP_PERCENT[level - 1] / 100.0;
    }
}
