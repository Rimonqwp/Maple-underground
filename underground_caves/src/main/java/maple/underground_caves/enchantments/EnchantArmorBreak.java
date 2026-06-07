package maple.underground_caves.enchantments;

/**
 * 碎甲 (I-V) 劍、弓附魔 終極附魔
 * 攻擊時有 N% 機率無視目標的防禦力，造成真實傷害。
 */
public final class EnchantArmorBreak extends CustomEnchantment {

    private static final double[] TRUE_DAMAGE_CHANCE = { 0.3, 0.6, 0.9, 1.2, 1.5 };

    public EnchantArmorBreak() {
        super("armor_break", "碎甲", 5, EnchantType.ULTIMATE, CustomEnchantment.SWORD_AND_BOW);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return String.format("攻擊時有 %.1f%% 機率無視目標的防禦力，造成真實傷害", TRUE_DAMAGE_CHANCE[level - 1]);
    }

    public double getTrueDamageChance(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return TRUE_DAMAGE_CHANCE[level - 1] / 100.0;
    }
}
