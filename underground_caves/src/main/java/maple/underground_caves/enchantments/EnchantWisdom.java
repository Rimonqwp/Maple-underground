package maple.underground_caves.enchantments;

/**
 * 智慧 (I-V) 裝備附魔 終極附魔
 * 每 5 經驗等級提升 N 點智力。上限為 M 點智力。
 */
public final class EnchantWisdom extends CustomEnchantment {

    private static final int[] INTELLIGENCE_PER_5_LEVELS = { 1, 2, 3, 4, 5 };
    private static final int[] CAP = { 20, 40, 60, 80, 100 };

    private static final String[] DESCRIPTIONS = {
        "每 5 經驗等級提升 1點 智力。上限為 20點 智力",
        "每 5 經驗等級提升 2點 智力。上限為 40點 智力",
        "每 5 經驗等級提升 3點 智力。上限為 60點 智力",
        "每 5 經驗等級提升 4點 智力。上限為 80點 智力",
        "每 5 經驗等級提升 5點 智力。上限為 100點 智力"
    };

    public EnchantWisdom() {
        super("wisdom", "智慧", 5, EnchantType.ULTIMATE, CustomEnchantment.ARMOR);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return DESCRIPTIONS[level - 1];
    }

    public int getIntelligencePer5Levels(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return INTELLIGENCE_PER_5_LEVELS[level - 1];
    }

    public int getIntelligenceCap(int level) {
        if (level < 1 || level > getMaxLevel()) return 20;
        return CAP[level - 1];
    }
}
