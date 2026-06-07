package maple.underground_caves.enchantments;

/**
 * 不勞無獲 (I-V) 裝備附魔 終極附魔
 * 每 10 次被怪物擊中時，有 N% 機率獲得經驗球。
 */
public final class EnchantNoPainNoGain extends CustomEnchantment {

    private static final int[] CHANCE_PERCENT = { 5, 15, 25, 35, 45 };
    private static final int HITS_THRESHOLD = 10;

    private static final String[] DESCRIPTIONS = {
        "每 10 次被怪物擊中時，有 5% 機率獲得經驗球",
        "每 10 次被怪物擊中時，有 15% 機率獲得經驗球",
        "每 10 次被怪物擊中時，有 25% 機率獲得經驗球",
        "每 10 次被怪物擊中時，有 35% 機率獲得經驗球",
        "每 10 次被怪物擊中時，有 45% 機率獲得經驗球"
    };

    public EnchantNoPainNoGain() {
        super("no_pain_no_gain", "不勞無獲", 5, EnchantType.ULTIMATE, CustomEnchantment.ARMOR);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return DESCRIPTIONS[level - 1];
    }

    public int getHitsThreshold() { return HITS_THRESHOLD; }

    public double getExpOrbChance(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return CHANCE_PERCENT[level - 1] / 100.0;
    }
}
