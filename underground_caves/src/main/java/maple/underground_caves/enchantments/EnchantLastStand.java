package maple.underground_caves.enchantments;

/**
 * 背水一戰 (I-V) 裝備附魔 終極附魔
 * 生命值低於 35% 時，受到攻擊時獲得額外 N% 防禦力。
 */
public final class EnchantLastStand extends CustomEnchantment {

    private static final double HP_THRESHOLD = 0.35;
    private static final int[] DEFENSE_PERCENT = { 5, 10, 15, 20, 25 };

    public EnchantLastStand() {
        super("last_stand", "背水一戰", 5, EnchantType.ULTIMATE, CustomEnchantment.ARMOR);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return String.format("生命值低於35%%時，受到攻擊時獲得額外 +%d%% 防禦力", DEFENSE_PERCENT[level - 1]);
    }

    public double getHpThreshold() { return HP_THRESHOLD; }

    public double getDefenseBonusPercent(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return DEFENSE_PERCENT[level - 1] / 100.0;
    }
}
