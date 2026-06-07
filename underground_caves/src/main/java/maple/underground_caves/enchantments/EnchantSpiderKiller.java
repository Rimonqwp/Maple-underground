package maple.underground_caves.enchantments;

/**
 * 蛛蛛殺手 (I-VII) 劍附魔 普通附魔
 * 對蜘蛛、洞穴蜘蛛和蠹魚造成的傷害增加 +N%。
 */
public final class EnchantSpiderKiller extends CustomEnchantment {

    private static final int MAX_LEVEL = 7;
    private static final int PER_LEVEL_PERCENT = 2;

    public EnchantSpiderKiller() {
        super("spider_killer", "蛛蛛殺手", MAX_LEVEL, EnchantType.NORMAL, CustomEnchantment.SWORDS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return "對蜘蛛、洞穴蜘蛛和蠹魚造成的傷害增加 +" + (PER_LEVEL_PERCENT * level) + "%";
    }

    public double getDamageBonusPercent(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return (PER_LEVEL_PERCENT * level) / 100.0;
    }
}
