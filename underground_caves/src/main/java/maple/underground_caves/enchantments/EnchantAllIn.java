package maple.underground_caves.enchantments;

/**
 * 孤注一擲 (I) 武器附魔 終極附魔
 * 移除武器上所有其他附魔，但使你的傷害提高 500%！
 */
public final class EnchantAllIn extends CustomEnchantment {

    private static final double DAMAGE_MULTIPLIER = 5.0; // 500% = 5x

    public EnchantAllIn() {
        super("all_in", "孤注一擲", 1, EnchantType.ULTIMATE, CustomEnchantment.WEAPONS);
    }

    @Override
    public String getDescription(int level) {
        return "移除武器上所有其他附魔，但使你的傷害提高500%！";
    }

    public double getDamageMultiplier() { return DAMAGE_MULTIPLIER; }
}
