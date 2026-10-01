package maple.underground_state.professions;

/** 一次采集的不可变属性快照；概率加成为相对百分比。 */
public record ProfessionBonuses(double fortune, double xpPercent, double rarePercent,
                                double eventPercent, int eventTier) {
    public static final ProfessionBonuses ZERO = new ProfessionBonuses(0, 0, 0, 0, 0);

    public ProfessionBonuses bounded() {
        return new ProfessionBonuses(Double.isFinite(fortune) ? Math.max(0, fortune) : 0, clamp(xpPercent, 40),
                clamp(rarePercent, 75), clamp(eventPercent, 100), Math.clamp(eventTier, 0, 2));
    }

    public ProfessionBonuses add(ProfessionBonuses other) {
        return new ProfessionBonuses(fortune + other.fortune, xpPercent + other.xpPercent,
                rarePercent + other.rarePercent, eventPercent + other.eventPercent,
                Math.max(eventTier, other.eventTier));
    }

    private static double clamp(double value, double max) {
        return Double.isFinite(value) ? Math.clamp(value, 0, max) : 0;
    }
}
