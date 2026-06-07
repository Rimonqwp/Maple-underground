package maple.underground_caves.enchantments;

/**
 * 洞窟祝福 (I-V) 裝備附魔 普通附魔
 * 身處世界時，獲得 每N秒 +1 生命值恢復。
 */
public final class EnchantCaveBlessing extends CustomEnchantment {

    private static final int[] INTERVAL_SECONDS = { 30, 25, 20, 18, 12 };

    private static final String[] DESCRIPTIONS = {
        "身處世界時，獲得 每30秒 +1 生命值恢復",
        "身處世界時，獲得 每25秒 +1 生命值恢復",
        "身處世界時，獲得 每20秒 +1 生命值恢復",
        "身處世界時，獲得 每18秒 +1 生命值恢復",
        "身處世界時，獲得 每12秒 +1 生命值恢復"
    };

    public EnchantCaveBlessing() {
        super("cave_blessing", "洞窟祝福", 5, EnchantType.NORMAL, CustomEnchantment.ARMOR);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return DESCRIPTIONS[level - 1];
    }

    public int getIntervalSeconds(int level) {
        if (level < 1 || level > getMaxLevel()) return 30;
        return INTERVAL_SECONDS[level - 1];
    }
}
