package maple.underground_caves.enchantments;

/**
 * 連擊 (I-V) 劍附魔 終極附魔
 * 連續攻擊目標，每次攻擊傷害增加，5秒未攻擊 重置。
 */
public final class EnchantCombo extends CustomEnchantment {

    private static final int[] MAX_STACKS = { 2, 3, 5, 7, 10 };
    private static final double[] PER_STACK_PERCENT = { 0.2, 0.3, 0.4, 0.5, 0.5 };

    /** 各等級完整效果描述 */
    private static final String[] DESCRIPTIONS = {
        "連續攻擊目標，每次攻擊傷害增加 +0.2%，5秒未攻擊 重置。最多疊加 2 層",
        "連續攻擊目標，每次攻擊傷害增加 +0.3%，5秒未攻擊 重置。最多疊加 3 層",
        "連續攻擊目標，每次攻擊傷害增加 +0.4%，5秒未攻擊 重置。最多疊加 5 層",
        "連續攻擊目標，每次攻擊傷害增加 +0.5%，5秒未攻擊 重置。最多疊加 7 層",
        "連續攻擊目標，每次攻擊傷害增加 +0.5%，5秒未攻擊 重置。最多疊加 10 層"
    };

    public EnchantCombo() {
        super("combo", "連擊", 5, EnchantType.ULTIMATE, CustomEnchantment.SWORDS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return DESCRIPTIONS[level - 1];
    }

    public int getMaxStacks(int level) {
        if (level < 1 || level > getMaxLevel()) return 2;
        return MAX_STACKS[level - 1];
    }

    public double getPerStackPercent(int level) {
        if (level < 1 || level > getMaxLevel()) return 0.2;
        return PER_STACK_PERCENT[level - 1];
    }
}
