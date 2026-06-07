package maple.underground_caves.enchantments;

/**
 * 孤軍奮戰 (I-V) 劍、弓附魔 終極附魔
 * 每有 10 格範圍內的敵人，你的傷害就會提高 +N%。
 */
public final class EnchantLoneFighter extends CustomEnchantment {

    private static final int[] DAMAGE_PERCENT_PER_10 = { 2, 4, 6, 8, 10 };
    private static final double RANGE = 10.0;

    public EnchantLoneFighter() {
        super("lone_fighter", "孤軍奮戰", 5, EnchantType.ULTIMATE, CustomEnchantment.SWORD_AND_BOW);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return String.format("每有10格範圍內的敵人，你的傷害就會提高 +%d%% 。", DAMAGE_PERCENT_PER_10[level - 1]);
    }

    public int getDamagePercentPerEnemy(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return DAMAGE_PERCENT_PER_10[level - 1];
    }

    public double getRange() { return RANGE; }
}
