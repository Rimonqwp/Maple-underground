package maple.underground_caves.dungeon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 地牢祝福（Blessing）管理：依副本世界累積等級，並提供屬性最終值計算。
 * <p>
 * 計算規則（每個屬性）：
 * final = (base + flatSum) * product(1 + percent_i)
 * 其中 percent_i、flat 都由祝福等級線性累加。
 */
public final class DungeonBlessingManager {

    public enum BlessingType {
        LIFE,
        POWER,
        STONE,
        WISDOM,
        TIME
    }

    public enum BlessingStat {
        HEALTH,
        HEALTH_REGEN,
        STRENGTH,
        CRIT_DAMAGE,
        DEFENSE,
        DAMAGE,
        INTELLIGENCE,
        SPEED
    }

    private final Map<String, EnumMap<BlessingType, Integer>> levelsByWorld = new ConcurrentHashMap<>();

    public void resetWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) return;
        levelsByWorld.remove(worldName);
    }

    public int getLevel(String worldName, BlessingType type) {
        if (worldName == null || type == null) return 0;
        EnumMap<BlessingType, Integer> map = levelsByWorld.get(worldName);
        if (map == null) return 0;
        Integer v = map.get(type);
        return v == null ? 0 : Math.max(0, v);
    }

    public int addLevel(String worldName, BlessingType type, int amount) {
        if (worldName == null || worldName.isBlank() || type == null || amount <= 0) return getLevel(worldName, type);
        EnumMap<BlessingType, Integer> map = levelsByWorld.computeIfAbsent(worldName, k -> emptyLevels());
        int next = Math.max(0, map.getOrDefault(type, 0) + amount);
        map.put(type, next);
        return next;
    }

    /** 房間清怪完成：四大祝福（生命/力量/石頭/智慧）隨機 +1。 */
    public BlessingType onRoomCleared(String worldName) {
        BlessingType t = randomCoreBlessing();
        addLevel(worldName, t, 1);
        return t;
    }

    /** 非 puzzle 房秘密：四大祝福隨機 +1；puzzle 房秘密：時光 +1。 */
    public BlessingType onSecretFound(String worldName, boolean puzzleRoomSecret) {
        BlessingType t = puzzleRoomSecret ? BlessingType.TIME : randomCoreBlessing();
        addLevel(worldName, t, 1);
        return t;
    }

    /** 依當前祝福等級，計算指定屬性的最終值。 */
    public double apply(String worldName, BlessingStat stat, double base) {
        return apply(worldName, stat, base, 1.0);
    }

    /**
     * 依當前祝福等級與樓層乘數，計算指定屬性的最終值。
     * floorMultiplier 目前用於 F4+ 的 1.2 倍規則（作用於祝福提供的 percent/flat）。
     */
    public double apply(String worldName, BlessingStat stat, double base, double floorMultiplier) {
        if (worldName == null || stat == null) return base;
        EnumMap<BlessingType, Integer> lv = levelsByWorld.get(worldName);
        if (lv == null || lv.isEmpty()) return base;
        double fm = Math.max(0.0, floorMultiplier);

        double flat = 0.0;
        double mult = 1.0;
        for (BlessingType type : BlessingType.values()) {
            int level = Math.max(0, lv.getOrDefault(type, 0));
            if (level <= 0) continue;
            flat += flatPerLevel(type, stat) * level * fm;
            double percent = percentPerLevel(type, stat) * level * fm;
            if (percent != 0.0) {
                mult *= (1.0 + percent / 100.0);
            }
        }
        double out = (base + flat) * mult;
        return Math.max(0.0, out);
    }

    public String debugSummary(String worldName) {
        EnumMap<BlessingType, Integer> lv = levelsByWorld.get(worldName);
        if (lv == null || lv.isEmpty()) return "LIFE=0 POWER=0 STONE=0 WISDOM=0 TIME=0";
        return "LIFE=" + lv.getOrDefault(BlessingType.LIFE, 0)
                + " POWER=" + lv.getOrDefault(BlessingType.POWER, 0)
                + " STONE=" + lv.getOrDefault(BlessingType.STONE, 0)
                + " WISDOM=" + lv.getOrDefault(BlessingType.WISDOM, 0)
                + " TIME=" + lv.getOrDefault(BlessingType.TIME, 0);
    }

    /** 可選：掉落/清怪時向同世界玩家提示祝福增加。 */
    public void broadcastGain(World world, BlessingType type, int levelNow) {
        if (world == null || type == null) return;
        String name = blessingName(type);
        for (Player p : world.getPlayers()) {
            if (p == null || !p.isOnline()) continue;
            p.sendActionBar(Component.text("祝福提升: " + name + " Lv." + levelNow, NamedTextColor.AQUA));
        }
    }

    private static EnumMap<BlessingType, Integer> emptyLevels() {
        EnumMap<BlessingType, Integer> out = new EnumMap<>(BlessingType.class);
        for (BlessingType type : BlessingType.values()) out.put(type, 0);
        return out;
    }

    private static BlessingType randomCoreBlessing() {
        int r = ThreadLocalRandom.current().nextInt(4);
        return switch (r) {
            case 0 -> BlessingType.LIFE;
            case 1 -> BlessingType.POWER;
            case 2 -> BlessingType.STONE;
            default -> BlessingType.WISDOM;
        };
    }

    private static double percentPerLevel(BlessingType type, BlessingStat stat) {
        return switch (type) {
            case LIFE -> (stat == BlessingStat.HEALTH || stat == BlessingStat.HEALTH_REGEN) ? 3.0 : 0.0;
            case POWER -> (stat == BlessingStat.STRENGTH || stat == BlessingStat.CRIT_DAMAGE) ? 2.0 : 0.0;
            case STONE -> (stat == BlessingStat.DEFENSE) ? 2.0 : 0.0;
            case WISDOM -> (stat == BlessingStat.INTELLIGENCE) ? 2.0 : 0.0;
            case TIME -> (stat == BlessingStat.HEALTH || stat == BlessingStat.INTELLIGENCE
                    || stat == BlessingStat.STRENGTH || stat == BlessingStat.DEFENSE) ? 2.0 : 0.0;
        };
    }

    private static double flatPerLevel(BlessingType type, BlessingStat stat) {
        return switch (type) {
            case LIFE -> 0.0;
            case POWER -> (stat == BlessingStat.STRENGTH || stat == BlessingStat.CRIT_DAMAGE) ? 4.0 : 0.0;
            case STONE -> {
                if (stat == BlessingStat.DEFENSE) yield 4.0;
                if (stat == BlessingStat.DAMAGE) yield 6.0;
                yield 0.0;
            }
            case WISDOM -> {
                if (stat == BlessingStat.INTELLIGENCE || stat == BlessingStat.SPEED) yield 4.0;
                yield 0.0;
            }
            case TIME -> (stat == BlessingStat.HEALTH || stat == BlessingStat.INTELLIGENCE
                    || stat == BlessingStat.STRENGTH || stat == BlessingStat.DEFENSE) ? 4.0 : 0.0;
        };
    }

    private static String blessingName(BlessingType type) {
        if (type == null) return "未知";
        return switch (type) {
            case LIFE -> "生命的祝福";
            case POWER -> "力量的祝福";
            case STONE -> "石头的祝福";
            case WISDOM -> "智慧的祝福";
            case TIME -> "时光的祝福";
        };
    }
}

