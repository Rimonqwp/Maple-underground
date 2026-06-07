package maple.underground_caves.enchantments;

/**
 * 伴生共鳴 (I-V) 劍附魔 終極附魔
 * 複製你目前寵物的 N% 屬性，增加到你自己身上。
 */
public final class EnchantCompanionResonance extends CustomEnchantment {

    private static final int[] PERCENT = { 20, 40, 60, 80, 100 };

    public EnchantCompanionResonance() {
        super("companion_resonance", "伴生共鳴", 5, EnchantType.ULTIMATE, CustomEnchantment.SWORDS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return "複製你目前寵物的 " + PERCENT[level - 1] + "% 屬性，增加到你自己身上";
    }

    public int getCopyPercent(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return PERCENT[level - 1];
    }
}
