package maple.underground_state.professions;

/** Server-independent event gates shared by the implementation and balance tests. */
public final class ProfessionEventRules {
    private ProfessionEventRules() {}
    public static boolean mayRoll(boolean elite,int qualification,boolean active,long now,long cooldownUntil) {
        return !active && qualification>=(elite ? 2 : 1) && now>=cooldownUntil;
    }
    public static boolean mayReward(double ownerDamage,double totalDamage,boolean inRange) {
        return inRange && totalDamage>0 && Double.isFinite(ownerDamage) && Double.isFinite(totalDamage) && ownerDamage>=totalDamage*.2;
    }
    public static boolean expired(boolean online,boolean alive,boolean entityValid,boolean elite,long elapsed,int absentSeconds) {
        return !online || !alive || !entityValid || elapsed>=(elite ? 180000 : 90000) || absentSeconds>=10;
    }
}
