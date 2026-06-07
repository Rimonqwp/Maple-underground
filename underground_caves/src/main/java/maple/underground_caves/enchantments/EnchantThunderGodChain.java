package maple.underground_caves.enchantments;

/**
 * 雷神連擊 (I-V) 劍 普通附魔
 * 每第 3 次擊中怪物時，召喚閃電，對目標額外造成該次攻擊傷害的 8%/18%/30%/45%/60%
 */
public final class EnchantThunderGodChain extends CustomEnchantment {

    private static final int MAX_LEVEL = 5;
    private static final int HIT_INTERVAL = 3;
    private static final double[] EXTRA_DAMAGE_PERCENT = { 8, 18, 30, 45, 60 };

    public EnchantThunderGodChain() {
        super("thunder_god_chain", "雷神連擊", MAX_LEVEL, EnchantType.NORMAL, CustomEnchantment.SWORDS);
    }

    @Override
    public String getDescription(int level) {
        if (level < 1 || level > getMaxLevel()) return "";
        return "每第 " + HIT_INTERVAL + " 次擊中怪物時，召喚閃電，對目標額外造成該次攻擊傷害的 " + EXTRA_DAMAGE_PERCENT[level - 1] + "%";
    }

    public int getHitInterval() { return HIT_INTERVAL; }
    public double getExtraDamagePercent(int level) {
        if (level < 1 || level > getMaxLevel()) return 0;
        return EXTRA_DAMAGE_PERCENT[level - 1] / 100.0;
    }
}
