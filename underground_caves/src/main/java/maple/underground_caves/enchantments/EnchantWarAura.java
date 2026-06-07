package maple.underground_caves.enchantments;

/**
 * 征戰光環 (I-V) 裝備附魔 終極附魔
 * 提升你的所有戰鬥屬性，同時在你周圍30格範圍內，每位玩家都獲得稀有物品掉落率加成，最大玩家數 20 人。
 */
public final class EnchantWarAura extends CustomEnchantment {

    private static final double[] COMBAT_PERCENT = { 0.07, 0.14, 0.21, 0.28, 0.35 };
    private static final double AURA_RANGE = 30.0;
    private static final int MAX_PLAYERS = 20;

    public EnchantWarAura() {
        super("war_aura", "征戰光環", 5, EnchantType.ULTIMATE, CustomEnchantment.ARMOR);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return String.format("提升 +%.2f%% 你的所有戰鬥屬性，同時在你周圍30格範圍內，每位玩家都獲得 +%.2f%% 稀有物品掉落率加成，最大玩家數 %d 人",
            COMBAT_PERCENT[level - 1], COMBAT_PERCENT[level - 1], MAX_PLAYERS);
    }

    public double getCombatBonusPercent(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return COMBAT_PERCENT[level - 1] / 100.0;
    }

    public double getAuraRange() { return AURA_RANGE; }
    public int getMaxPlayers() { return MAX_PLAYERS; }
}
