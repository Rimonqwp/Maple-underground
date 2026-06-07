package maple.underground_state.dungeon.panel;

/**
 * 地牢等級經驗表：累積 XP，每升一級不清零。
 * Level 1 需 50 總 XP，Level 2 需 125 總 XP（即 Lv1 後再 75 XP），以此類推。
 */
public final class DungeonLevelXP {
    private DungeonLevelXP() {}

    /** 等級 1–50 的累積 XP 門檻 */
    private static final long[] CUMULATIVE_XP = {
        50, 125, 235, 395, 625, 955, 1_425, 2_095, 3_045, 4_385,
        6_275, 8_940, 12_700, 17_960, 25_340, 35_640, 50_040, 70_040, 97_640, 135_640,
        188_140, 259_640, 356_640, 488_640, 668_640, 911_640, 1_239_640, 1_684_640, 2_284_640, 3_084_640,
        4_149_640, 5_559_640, 7_459_640, 9_959_640, 13_259_640, 17_559_640, 23_159_640, 30_359_640, 39_559_640, 51_559_640,
        66_559_640, 85_559_640, 109_559_640, 139_559_640, 177_559_640, 225_559_640, 285_559_640, 360_559_640, 453_559_640, 569_809_640
    };

    public static final int MAX_LEVEL = 50;

    /** 取得指定等級的累積 XP 門檻 */
    public static long getCumulativeXPForLevel(int level) {
        if (level < 1 || level > MAX_LEVEL) return level <= 0 ? 0 : CUMULATIVE_XP[MAX_LEVEL - 1];
        return CUMULATIVE_XP[level - 1];
    }

    /** 根據累積 XP 計算當前等級 (1–50) */
    public static int getLevelFromCumulativeXP(long totalXP) {
        if (totalXP < 0) return 0;
        if (totalXP < CUMULATIVE_XP[0]) return 0;
        for (int i = MAX_LEVEL; i >= 1; i--) {
            if (totalXP >= CUMULATIVE_XP[i - 1]) return i;
        }
        return 0;
    }

    /** 取得當前等級內已累積的 XP（0 到下一級所需之間） */
    public static long getXPWithinLevel(long totalXP) {
        int lvl = getLevelFromCumulativeXP(totalXP);
        if (lvl <= 0) return totalXP;
        if (lvl >= MAX_LEVEL) return 0;
        long threshold = CUMULATIVE_XP[lvl - 1];
        long nextThreshold = CUMULATIVE_XP[lvl];
        return totalXP - threshold;
    }

    /** 升到下一級還需的 XP */
    public static long getXPToNextLevel(long totalXP) {
        int lvl = getLevelFromCumulativeXP(totalXP);
        if (lvl >= MAX_LEVEL) return 0;
        if (lvl <= 0) return CUMULATIVE_XP[0] - totalXP;
        return CUMULATIVE_XP[lvl] - totalXP;
    }

    /** 當前等級到下一級的總 XP 需求 */
    public static long getXPNeededForCurrentLevel(long totalXP) {
        int lvl = getLevelFromCumulativeXP(totalXP);
        if (lvl >= MAX_LEVEL) return 0;
        if (lvl <= 0) return CUMULATIVE_XP[0];
        return CUMULATIVE_XP[lvl] - CUMULATIVE_XP[lvl - 1];
    }
}
