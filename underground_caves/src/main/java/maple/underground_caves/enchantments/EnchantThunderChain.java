package maple.underground_caves.enchantments;

/**
 * 雷霆連擊 (I-V) 劍 普通附魔
 * 每第 3 次擊中怪物時，召喚閃電，對目標周圍 2 格內最多 10 個怪物造成該次攻擊傷害的 4%/8%/14%/22%/30%
 */
public final class EnchantThunderChain extends CustomEnchantment {

    private static final int MAX_LEVEL = 5;
    private static final int HIT_INTERVAL = 3;
    private static final double RADIUS = 2;
    private static final int MAX_TARGETS = 10;
    private static final double[] DAMAGE_PERCENT = { 4, 8, 14, 22, 30 };

    public EnchantThunderChain() {
        super("thunder_chain", "雷霆連擊", MAX_LEVEL, EnchantType.NORMAL, CustomEnchantment.SWORDS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return "每第 " + HIT_INTERVAL + " 次擊中怪物時，召喚閃電，對目標周圍 " + (int) RADIUS + " 格內最多 " + MAX_TARGETS + " 個怪物造成該次攻擊傷害的 " + DAMAGE_PERCENT[level - 1] + "%";
    }

    public int getHitInterval() { return HIT_INTERVAL; }
    public double getRadius() { return RADIUS; }
    public int getMaxTargets() { return MAX_TARGETS; }
    public double getDamagePercent(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return DAMAGE_PERCENT[level - 1] / 100.0;
    }
}
