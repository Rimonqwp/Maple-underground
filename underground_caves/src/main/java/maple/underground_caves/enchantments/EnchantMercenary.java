package maple.underground_caves.enchantments;

/**
 * 傭兵生涯 (I-X) 劍附魔 普通附魔
 * 獲得額外戰鬥經驗值，連續攻擊怪物可賺取金幣和經驗球。累積戰鬥經驗可升級。
 */
public final class EnchantMercenary extends CustomEnchantment {

    private static final int MAX_LEVEL = 10;
    private static final int[] EXP_BONUS_PERCENT = { 3, 4, 4, 5, 6, 7, 8, 8, 9, 10 };
    private static final double[] COINS = { 1.4, 1.8, 1.2, 2.6, 3.0, 3.4, 3.8, 4.2, 4.6, 5.0 };
    private static final int[] EXP_ORBS = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
    private static final long[] UPGRADE_XP = { 0, 50_000, 100_000, 200_000, 400_000, 800_000, 1_600_000, 3_200_000, 6_400_000, 12_800_000 };

    public EnchantMercenary() {
        super("mercenary", "傭兵生涯", MAX_LEVEL, EnchantType.NORMAL, CustomEnchantment.SWORDS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        int idx = level - 1;
        String xpStr = idx == 0 ? "[0]" : "[" + UPGRADE_XP[idx] + "]";
        return String.format("獲得 +%d%% 的額外戰鬥經驗值，連續攻擊怪物可賺取 +%.1f 金幣和 +%d 經驗球。累積戰鬥經驗可升級%s",
            EXP_BONUS_PERCENT[idx], COINS[idx], EXP_ORBS[idx], xpStr);
    }

    public int getExpBonusPercent(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return EXP_BONUS_PERCENT[level - 1];
    }

    public double getCoinsPerHit(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return COINS[level - 1];
    }

    public int getExpOrbsPerHit(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return EXP_ORBS[level - 1];
    }

    public long getUpgradeXpForLevel(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return UPGRADE_XP[level - 1];
    }
}
