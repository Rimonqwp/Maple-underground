package maple.underground_caves.dungeon;

import maple.underground_caves.Test_dungeons;
import maple.underground_caves.party.PartyManager;
import maple.underground_state.dungeon.panel.DungeonLevelXP;

import net.swofty.dungeons.DungeonRoomType;
import net.swofty.dungeons.SkyBlockDungeon;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 地牢記分板 - 側邊欄顯示。
 * 行從底部往上排：mc.maplenw.top 固定在最底部（score 0），其餘依人數動態增減。
 * 等級由本地 SQLite 經驗運算；職業一進地牢從 MySQL preferred_class 讀取，玩家在 GUI 更換職業時即時更新記分板並寫入 MySQL。
 * <p>
 * 評分結構參考 Hypixel Catacombs 常見模型：技能、探索、速度各 0–100（合計最高 300），死亡扣分（每倒一次 2 分）；
 * 另：特殊 TNT 每炸毀一座「坟墓」+2.5 分，最多 4 座共 +10，總分上限 310；炸可破牆（cracked）不計入。
 */
public final class DungeonScoreboard {
    private static final String OBJECTIVE_NAME = "dungeon_hud";
    private static final int UPDATE_TICKS = 20;
    /** 側欄行槽位（須唯一）；原 16 格在 5 人隊＋詳細分項時會截斷，故加 §r§* 擴充 */
    private static final String[] LINE_ENTRIES = {
            "§0§0", "§1§1", "§2§2", "§3§3", "§4§4", "§5§5", "§6§6", "§7§7",
            "§8§8", "§9§9", "§a§a", "§b§b", "§c§c", "§d§d", "§e§e", "§f§f",
            "§r§0", "§r§1", "§r§2", "§r§3", "§r§4", "§r§5", "§r§6", "§r§7",
            "§r§8", "§r§9", "§r§a", "§r§b", "§r§c", "§r§d", "§r§e", "§r§f"
    };

    // 職業：治療師[H]、法師[M]、戰士[B]、弓箭手[A]、坦克[T]
    private static final String[] CLASS_TAGS = {"§d[H]§r", "§b[M]§r", "§c[B]§r", "§a[A]§r", "§6[T]§r"};

    private final Test_dungeons plugin;
    private final PartyManager partyManager;
    private BukkitRunnable updateTask;
    private final Map<String, RunData> runByWorld = new ConcurrentHashMap<>();

    private static final class RunData {
        final String runId;
        volatile long startMs;  // 0 = 計時尚未開始（準備階段）
        List<UUID> partyMemberIds;
        Map<UUID, Integer> classIndexByPlayer;
        /** 佈局與評分（由 {@link #initRunFromSnapshot} 填入） */
        volatile SkyBlockDungeon dungeonLayout;
        volatile int gridWidth;
        volatile int gridHeight;
        volatile int totalRooms;
        volatile int entranceCellX = -1;
        volatile int entranceCellY = -1;
        volatile String floorLabel = "F1";
        /** 世界座標 -> 網格格子的換算（與地圖/開門邏輯一致） */
        volatile int originX = 0;
        volatile int originZ = 0;
        volatile int roomScale = 0;
        final Set<Integer> exploredRoomCells = ConcurrentHashMap.<Integer>newKeySet();
        final AtomicInteger mobKills = new AtomicInteger();
        final AtomicInteger deaths = new AtomicInteger();
        /** 特殊 TNT 已炸毀的坟墓數（每座 +2.5 分，最多計 4 座） */
        final AtomicInteger tombsDestroyedForScore = new AtomicInteger();
        /**
         * 管理員指令覆蓋的顯示總分（null 表示按公式計算）。
         * 不影響技能/探索/速度分項顯示，僅「当前分数」與等級字母用此值。
         */
        volatile Integer adminTotalOverride;

        RunData(String runId, long startMs) {
            this.runId = runId;
            this.startMs = startMs;
        }
    }

    public DungeonScoreboard(Test_dungeons plugin, PartyManager partyManager) {
        this.plugin = plugin;
        this.partyManager = partyManager != null ? partyManager : new PartyManager();
    }

    /** 地牢生成時呼叫，建立 RunData，計時尚未開始（等待準備階段完成） */
    public void prepareRun(String worldName) {
        if (worldName == null || worldName.isBlank()) return;
        String runId = generateRunId();
        runByWorld.put(worldName, new RunData(runId, 0));
    }

    /**
     * 地牢生成完成後立即呼叫，綁定網格與房間數供探索分、樓層與時間標準使用。
     * 須在 {@link #prepareRun(String)} 之後對同一 world 呼叫。
     */
    public void initRunFromSnapshot(Test_dungeons.DungeonSnapshot snapshot) {
        if (snapshot == null || snapshot.worldName() == null || snapshot.worldName().isBlank()) return;
        RunData run = runByWorld.get(snapshot.worldName());
        if (run == null) return;
        int w = Math.max(1, snapshot.width());
        int h = Math.max(1, snapshot.height());
        run.gridWidth = w;
        run.gridHeight = h;
        run.dungeonLayout = snapshot.dungeon();
        run.totalRooms = countPlacedRooms(snapshot.dungeon(), w, h);
        run.floorLabel = snapshot.floor() != null && !snapshot.floor().isBlank() ? snapshot.floor() : "F1";
        run.originX = snapshot.originX();
        run.originZ = snapshot.originZ();
        run.roomScale = Math.max(1, snapshot.roomScale());
        int[] entrance = findEntranceCell(snapshot.dungeon(), w, h);
        if (entrance != null) {
            run.entranceCellX = entrance[0];
            run.entranceCellY = entrance[1];
        } else {
            run.entranceCellX = -1;
            run.entranceCellY = -1;
        }
        run.exploredRoomCells.clear();
        run.mobKills.set(0);
        run.deaths.set(0);
        run.tombsDestroyedForScore.set(0);
        run.adminTotalOverride = null;
    }

    private record ScoreSnapshot(
            int skill,
            int exploration,
            int speed,
            int speedForTotal,
            int deathPenalty,
            int baseNow,
            double tombBonus,
            int naturalTotal
    ) {}

    private ScoreSnapshot computeScoreSnapshot(RunData run, String worldName) {
        int skill = computeSkillScore(run);
        int exploration = computeExplorationPercent(run, worldName);
        int speed = computeSpeedScore(run.startMs, run.floorLabel);
        int deathPen = deathPenaltyPoints(run);
        int speedForTotal = speed < 0 ? 0 : speed;
        int baseNow = Math.min(300, skill + exploration + speedForTotal);
        double tombBonus = computeTombBonusPoints(run);
        int naturalTotal = Math.max(0, (int) Math.round(baseNow + tombBonus - deathPen));
        return new ScoreSnapshot(skill, exploration, speed, speedForTotal, deathPen, baseNow, tombBonus, naturalTotal);
    }

    /** 不含 {@link RunData#adminTotalOverride} 的計算總分（與側邊欄公式一致） */
    public int computeNaturalTotalScore(String worldName) {
        if (worldName == null || worldName.isBlank()) return 0;
        RunData run = runByWorld.get(worldName);
        if (run == null) return 0;
        return computeScoreSnapshot(run, worldName).naturalTotal();
    }

    /** 設定或清除管理員總分覆蓋；{@code null} 表示恢復公式計分 */
    public void setAdminTotalScoreOverride(String worldName, Integer totalOrNull) {
        if (worldName == null || worldName.isBlank()) return;
        RunData run = runByWorld.get(worldName);
        if (run == null) return;
        if (totalOrNull == null) {
            run.adminTotalOverride = null;
            return;
        }
        run.adminTotalOverride = Math.max(0, Math.min(MAX_SCORE_TOTAL, totalOrNull));
    }

    public Integer getAdminTotalScoreOverride(String worldName) {
        if (worldName == null || worldName.isBlank()) return null;
        RunData run = runByWorld.get(worldName);
        return run == null ? null : run.adminTotalOverride;
    }

    /**
     * 特殊 TNT 成功清掉一座坟墓時呼叫（與炸牆分開，僅坟墓計分）。
     * 遊戲內可繼續炸更多座坟；<b>評分只累計前 4 座</b>（2.5×4=10 分封頂），之後再炸不加分。
     */
    public void recordTombDestroyedForScore(String worldName) {
        if (worldName == null || worldName.isBlank()) return;
        RunData run = runByWorld.get(worldName);
        if (run == null) return;
        run.tombsDestroyedForScore.updateAndGet(v -> v < MAX_TOMB_SCORE_COUNT ? v + 1 : v);
    }

    /**
     * 玩家打通門隧時呼叫，將門兩側格子標為已探索（補充；主要探索來自 {@link #tryMarkRoomExploredFromBlock} 進房統計）。
     */
    public void markRoomsDiscovered(String worldName, int cellX1, int cellY1, int cellX2, int cellY2) {
        if (worldName == null || worldName.isBlank()) return;
        RunData run = runByWorld.get(worldName);
        if (run == null) return;
        addExploredCell(run, cellX1, cellY1);
        addExploredCell(run, cellX2, cellY2);
    }

    /**
     * 玩家在地牢內移動時呼叫：依世界方塊座標換算所在房間格，有則標為已探索。
     * 使用 {@link RunData#exploredRoomCells}（每格只計一次，重進同一房不會重複加分）。
     * 僅在計時已開始（開場門後）才計入，與競速回合一致。
     */
    public void tryMarkRoomExploredFromBlock(String worldName, int blockX, int blockZ) {
        if (worldName == null || worldName.isBlank()) return;
        RunData run = runByWorld.get(worldName);
        if (run == null) return;
        if (run.startMs <= 0) return;
        if (run.dungeonLayout == null || run.gridWidth <= 0 || run.gridHeight <= 0) return;
        if (run.roomScale <= 0) return;
        int dx = blockX - run.originX;
        int dz = blockZ - run.originZ;
        if (dx < 0 || dz < 0) return;
        int cellX = dx / run.roomScale;
        int cellY = dz / run.roomScale;
        if (cellX < 0 || cellY < 0 || cellX >= run.gridWidth || cellY >= run.gridHeight) return;
        addExploredCell(run, cellX, cellY);
    }

    /** 地牢內擊殺帶 PDC 標記的怪物時呼叫（用於技能分） */
    public void recordMobKill(String worldName) {
        if (worldName == null || worldName.isBlank()) return;
        RunData run = runByWorld.get(worldName);
        if (run != null) run.mobKills.incrementAndGet();
    }

    /** 副本內倒地（計一次死亡懲罰） */
    public void recordDeath(String worldName) {
        if (worldName == null || worldName.isBlank()) return;
        RunData run = runByWorld.get(worldName);
        if (run != null) run.deaths.incrementAndGet();
    }

    /** 門開啟時呼叫，啟動計時器 */
    public void beginTimer(String worldName) {
        if (worldName == null || worldName.isBlank()) return;
        RunData run = runByWorld.get(worldName);
        if (run != null) {
            run.startMs = System.currentTimeMillis();
            if (run.entranceCellX >= 0 && run.entranceCellY >= 0) {
                run.exploredRoomCells.add(packCell(run.entranceCellX, run.entranceCellY));
            }
        }
    }

    /** 取得玩家在指定世界（地牢）的職業索引：0 治療師、1 法師、2 戰士、3 弓箭手、4 坦克。非地牢或未分配則回傳 -1。 */
    public int getClassIndexForPlayer(UUID playerId, String worldName) {
        if (worldName == null || playerId == null) return -1;
        RunData run = runByWorld.get(worldName);
        if (run == null || run.classIndexByPlayer == null) return -1;
        Integer idx = run.classIndexByPlayer.get(playerId);
        return idx != null ? idx : -1;
    }

    /** 地牢創建後呼叫，設定該地牢世界的隊伍成員；職業從 MySQL preferred_class 讀取，無則預設 0。 */
    void setPartyForWorld(String worldName, List<UUID> memberIds) {
        if (worldName == null || memberIds == null || memberIds.isEmpty()) return;
        RunData run = runByWorld.get(worldName);
        if (run == null) return;
        run.partyMemberIds = new ArrayList<>(memberIds);
        run.classIndexByPlayer = new ConcurrentHashMap<>();
        var remote = plugin != null ? plugin.getPlayerDungeonSettingsRemote() : null;
        for (UUID id : memberIds) {
            int idx = 0;
            if (remote != null) {
                String preferred = remote.getPreferredClass(id);
                idx = roleNameToClassIndex(preferred);
            }
            run.classIndexByPlayer.put(id, idx);
        }
    }

    /** 玩家在 GUI 更換職業時呼叫，即時更新記分板顯示（MySQL 由 StartRoomNpc 寫入）。 */
    public void updateClassForPlayer(String worldName, UUID playerId, int classIndex) {
        if (worldName == null || playerId == null || classIndex < 0 || classIndex > 4) return;
        RunData run = runByWorld.get(worldName);
        if (run != null && run.classIndexByPlayer != null) {
            run.classIndexByPlayer.put(playerId, classIndex);
        }
    }

    private static int roleNameToClassIndex(String role) {
        if (role == null || role.isEmpty()) return 0;
        return switch (role.toUpperCase(java.util.Locale.ROOT)) {
            case "HEALER" -> 0;
            case "MAGE" -> 1;
            case "WARRIOR", "戰士" -> 2;
            case "ARCHER" -> 3;
            case "TANK" -> 4;
            default -> 0;
        };
    }

    private static String generateRunId() {
        String hex = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return hex.substring(0, 16);
    }

    private static int packCell(int x, int y) {
        return (x << 16) ^ (y & 0xFFFF);
    }

    public Set<Integer> getExploredRoomCellsSnapshot(String worldName) {
        if (worldName == null || worldName.isBlank()) return Set.of();
        RunData run = runByWorld.get(worldName);
        if (run == null || run.exploredRoomCells.isEmpty()) return Set.of();
        return Set.copyOf(run.exploredRoomCells);
    }

    private static int countPlacedRooms(SkyBlockDungeon dungeon, int width, int height) {
        if (dungeon == null) return 0;
        int c = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (dungeon.getRoom(x, y) != null) c++;
            }
        }
        return Math.max(1, c);
    }

    private static int[] findEntranceCell(SkyBlockDungeon dungeon, int width, int height) {
        if (dungeon == null) return null;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                var room = dungeon.getRoom(x, y);
                if (room != null && room.getRoomType() == DungeonRoomType.ENTRANCE) {
                    return new int[]{x, y};
                }
            }
        }
        return null;
    }

    private static void addExploredCell(RunData run, int cx, int cy) {
        if (run == null) return;
        if (cx < 0 || cy < 0 || cx >= run.gridWidth || cy >= run.gridHeight) return;
        if (run.dungeonLayout != null && run.dungeonLayout.getRoom(cx, cy) == null) return;
        run.exploredRoomCells.add(packCell(cx, cy));
    }

    /** 參考完成時間與零分時間（秒），可依樓層調整 */
    private static int[] parAndZeroSecondsForFloor(String floor) {
        String f = floor != null ? floor.trim().toUpperCase(java.util.Locale.ROOT) : "F1";
        return switch (f) {
            case "F2" -> new int[]{20 * 60, 46 * 60};
            case "F3" -> new int[]{25 * 60, 56 * 60};
            default -> new int[]{15 * 60, 40 * 60};
        };
    }

    private static int computeSpeedScore(long startMs, String floor) {
        if (startMs <= 0) return -1;
        long elapsedSec = Math.max(1L, (System.currentTimeMillis() - startMs) / 1000L);
        int[] pz = parAndZeroSecondsForFloor(floor);
        int par = pz[0];
        int zero = pz[1];
        if (elapsedSec <= par) return 100;
        if (elapsedSec >= zero) return 0;
        return (int) Math.round(100.0 * (zero - elapsedSec) / (double) (zero - par));
    }

    private int computeExplorationPercent(RunData run, String worldName) {
        if (run == null || run.totalRooms <= 0) return 0;
        double roomPart = Math.min(1.0, (double) run.exploredRoomCells.size() / (double) run.totalRooms);
        int secTot = 0;
        int secFound = 0;
        if (plugin != null && plugin.getDungeonSecrets() != null) {
            secTot = plugin.getDungeonSecrets().getScoringSecretsTotal(worldName);
            secFound = plugin.getDungeonSecrets().getScoringSecretsFound(worldName);
        }
        // F1：房間全探索且（無計分秘密 或 秘密全拿）時探索分固定 100，避免權重/取整與統計口徑差一點拿不到滿分
        String floor = run.floorLabel != null ? run.floorLabel.trim().toUpperCase(java.util.Locale.ROOT) : "F1";
        if ("F1".equals(floor)) {
            boolean fullRooms = run.exploredRoomCells.size() >= run.totalRooms;
            boolean fullSecrets = secTot <= 0 || secFound >= secTot;
            if (fullRooms && fullSecrets) {
                return 100;
            }
        }
        double secPart = secTot <= 0 ? 1.0 : Math.min(1.0, (double) secFound / (double) secTot);
        double combined = secTot <= 0 ? roomPart : 0.55 * roomPart + 0.45 * secPart;
        return (int) Math.round(100.0 * combined);
    }

    private static int computeSkillScore(RunData run) {
        if (run == null) return 0;
        int kills = run.mobKills.get();
        int expected = Math.max(28, run.totalRooms * 12);
        return Math.min(100, (int) Math.round(100.0 * kills / (double) expected));
    }

    private static int deathPenaltyPoints(RunData run) {
        if (run == null) return 0;
        return run.deaths.get() * 2;
    }

    /** 坟墓加分：每座 2.5，最多 4 座＝10；與 {@link #MAX_SCORE_TOTAL} 對齊 */
    private static final int MAX_TOMB_SCORE_COUNT = 4;
    private static final double TOMB_SCORE_EACH = 2.5;
    public static final int MAX_SCORE_TOTAL = 310;

    private static double computeTombBonusPoints(RunData run) {
        if (run == null) return 0;
        int n = Math.min(MAX_TOMB_SCORE_COUNT, run.tombsDestroyedForScore.get());
        return n * TOMB_SCORE_EACH;
    }

    private static String letterGrade(int totalScore) {
        int t = Math.max(0, Math.min(MAX_SCORE_TOTAL, totalScore));
        if (t >= 300) return "S+";
        if (t >= 270) return "S";
        if (t >= 230) return "A";
        if (t >= 190) return "B";
        if (t >= 150) return "C";
        return "D";
    }

    public void start() {
        if (updateTask != null) return;
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p == null || !p.isOnline()) continue;
                    if (isInDungeonWorld(p)) {
                        updateScoreboard(p);
                    } else {
                        clearScoreboard(p);
                    }
                }
            }
        };
        updateTask.runTaskTimer(plugin, 20L, UPDATE_TICKS);
    }

    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        runByWorld.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p != null && p.isOnline()) clearScoreboard(p);
        }
    }

    private static boolean isInDungeonWorld(Player player) {
        if (player == null) return false;
        World w = player.getWorld();
        if (w == null) return false;
        String name = w.getName();
        return name != null && name.startsWith("dungeon_");
    }

    private void updateScoreboard(Player player) {
        String worldName = player.getWorld().getName();
        RunData run = runByWorld.get(worldName);
        if (run == null) {
            prepareRun(worldName);
            run = runByWorld.get(worldName);
        }
        if (run != null && run.totalRooms <= 0 && plugin != null) {
            Test_dungeons.DungeonSnapshot snap = plugin.getDungeonSnapshot(worldName);
            if (snap != null && worldName.equals(snap.worldName())) {
                initRunFromSnapshot(snap);
            }
        }
        if (run == null) return;

        Scoreboard board = player.getScoreboard();
        if (board == Bukkit.getScoreboardManager().getMainScoreboard()) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(board);
        }

        Objective obj = board.getObjective(OBJECTIVE_NAME);
        if (obj == null) {
            obj = board.registerNewObjective(OBJECTIVE_NAME, "dummy",
                    ChatColor.GOLD + "地牢 " + ChatColor.YELLOW + "✦");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        List<String> lines = buildLines(player.getWorld(), run, worldName);
        setLines(obj, board, lines);
    }

    /** 從上到下的行順序（顯示時由上往下）；最後一項為 mc.maplenw.top，固定在最底部（score 0），其餘按人數動態。 */
    private List<String> buildLines(World world, RunData run, String worldName) {
        List<String> lines = new ArrayList<>();

        // 頂部：日期 + 16位識別符
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yy"));
        lines.add(ChatColor.DARK_GRAY + date + " " + ChatColor.GRAY + run.runId);
        lines.add("");

        // 地牢名稱
        String fl = run.floorLabel != null && !run.floorLabel.isBlank() ? run.floorLabel : "F1";
        lines.add(ChatColor.GRAY + "📌 " + ChatColor.YELLOW + "地底洞窟 " + ChatColor.DARK_GRAY + "( " + ChatColor.WHITE + fl + ChatColor.DARK_GRAY + " )");
        lines.add("");

        // 玩家行（1～5 人，依人數增減）
        List<Player> players = resolveDisplayPlayers(world, run);
        boolean isPreparation = run.startMs <= 0;
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            int classIdx = (run.classIndexByPlayer != null && run.classIndexByPlayer.containsKey(p.getUniqueId()))
                    ? run.classIndexByPlayer.get(p.getUniqueId()) : (i % CLASS_TAGS.length);
            String classTag = CLASS_TAGS[classIdx % CLASS_TAGS.length];
            String name = p.getName();
            if (name.length() > 10) name = name.substring(0, 8) + "..";
            String classLevel = getClassLevelFromRemoteCache(p.getUniqueId(), classIdx);
            if (isPreparation) {
                boolean ready = partyManager != null && partyManager.isEffectiveReady(p.getUniqueId());
                String readyStr = ready ? ChatColor.GREEN + "已准备" : ChatColor.GRAY + "未准备";
                lines.add(classTag + " " + ChatColor.WHITE + name + " " + readyStr + " " + ChatColor.DARK_GRAY + classLevel);
            } else {
                boolean dead = plugin != null && plugin.isPlayerDungeonGhost(p);
                if (dead) {
                    lines.add(classTag + " " + ChatColor.WHITE + name + " " + ChatColor.DARK_RED + "已死亡 " + ChatColor.DARK_GRAY + classLevel);
                } else {
                    int hp = scoreboardCurrentVirtualHp(p);
                    lines.add(classTag + " " + ChatColor.WHITE + name + " " + ChatColor.RED + hp + " ❤ " + ChatColor.DARK_GRAY + classLevel);
                }
            }
        }

        lines.add("");
        String timeStr = formatElapsedTime(run.startMs);
        lines.add(ChatColor.GRAY + "經過時間：" + ChatColor.WHITE + timeStr);

        ScoreSnapshot snap = computeScoreSnapshot(run, worldName);
        int skill = snap.skill();
        int exploration = snap.exploration();
        int speed = snap.speed();
        int deathPen = snap.deathPenalty();
        double tombBonus = snap.tombBonus();

        Integer ov = run.adminTotalOverride;
        int totalNow = ov != null ? Math.max(0, Math.min(MAX_SCORE_TOTAL, ov)) : snap.naturalTotal();

        lines.add("");
        String scoreExtra = ov != null ? ChatColor.DARK_GRAY + " (管理)" : "";
        lines.add(ChatColor.GOLD + "当前分数：" + ChatColor.YELLOW + totalNow + ChatColor.GRAY + "/" + MAX_SCORE_TOTAL
                + scoreExtra
                + (run.startMs <= 0
                ? ChatColor.DARK_GRAY + " (未含速度)"
                : ChatColor.DARK_AQUA + " [" + letterGrade(totalNow) + "]"));

        lines.add("");
        lines.add(ChatColor.GRAY + "技能：" + ChatColor.AQUA + skill + ChatColor.DARK_GRAY + "/100");
        lines.add(ChatColor.GRAY + "探索：" + ChatColor.YELLOW + exploration + ChatColor.DARK_GRAY + "/100");
        if (speed < 0) {
            lines.add(ChatColor.GRAY + "速度：" + ChatColor.DARK_GRAY + "—" + ChatColor.DARK_GRAY + " (開場後計)");
        } else {
            lines.add(ChatColor.GRAY + "速度：" + ChatColor.GREEN + speed + ChatColor.DARK_GRAY + "/100");
        }
        int tombN = Math.min(MAX_TOMB_SCORE_COUNT, run.tombsDestroyedForScore.get());
        lines.add(ChatColor.GRAY + "坟墓：" + ChatColor.LIGHT_PURPLE + tombN + ChatColor.DARK_GRAY + "/"
                + MAX_TOMB_SCORE_COUNT + ChatColor.GRAY + " (+" + String.format(Locale.ROOT, "%.1f", tombBonus) + ")");
        if (deathPen > 0) {
            lines.add(ChatColor.GRAY + "死亡：" + ChatColor.RED + run.deaths.get() + ChatColor.DARK_GRAY + " (-" + deathPen + ")");
        }
        lines.add("");

        // 最底部（score 0）固定為 mc.maplenw.top
        lines.add(ChatColor.GOLD + "mc.maplenw.top");

        return lines;
    }

    /**
     * 側邊欄 ❤：僅 stats 當前虛擬血量（{@code getCurrentHealth}），不顯示最大；無統計管理器時回退實體血量。
     */
    private int scoreboardCurrentVirtualHp(Player p) {
        if (p == null || !p.isOnline()) return 0;
        if (plugin != null && plugin.getPlayerStatsManager() != null) {
            double cur = plugin.getPlayerStatsManager().get(p).getCurrentHealth();
            return (int) Math.ceil(Math.max(0.0, cur));
        }
        return (int) Math.ceil(Math.max(0.0, p.getHealth()));
    }

    /** 從本地 SQLite 取得該玩家在該地牢的職業經驗並算出等級，顯示為 Lv.<n> */
    private String getClassLevelFromRemoteCache(UUID playerId, int classIndex) {
        if (plugin == null) return "Lv.-";
        var remote = plugin.getProfessionExperienceRemote();
        if (remote == null) return "Lv.-";
        long[] exp = remote.getCachedByPlayer(playerId);
        if (exp == null || exp.length < 6) return "Lv.-";
        // exp[0]=地底洞窟, exp[1..5]=職業1..5，classIndex 0..4 對應 exp[1]..exp[5]
        int expIdx = classIndex + 1;
        if (expIdx >= exp.length) return "Lv.-";
        int lvl = DungeonLevelXP.getLevelFromCumulativeXP(exp[expIdx]);
        return "Lv." + lvl;
    }

    /** 取得要顯示的玩家列表：有隊伍則顯示隊伍成員，否則顯示世界內玩家 */
    private List<Player> resolveDisplayPlayers(World world, RunData run) {
        if (run.partyMemberIds != null && !run.partyMemberIds.isEmpty()) {
            List<Player> list = new ArrayList<>();
            for (UUID id : run.partyMemberIds) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline() && world.equals(p.getWorld())) {
                    list.add(p);
                }
            }
            return list;
        }
        return world.getPlayers().stream()
                .filter(p -> p != null && p.isOnline())
                .limit(5)
                .toList();
    }

    /** 格式化經過時間：01h 01m 21s，若 00h 則不顯示 h。startMs=0 表示計時未開始 */
    private static String formatElapsedTime(long startMs) {
        if (startMs <= 0) return "00m 00s";
        long elapsedSec = Math.max(1, (System.currentTimeMillis() - startMs) / 1000);
        long h = elapsedSec / 3600;
        long m = (elapsedSec % 3600) / 60;
        long s = elapsedSec % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) {
            sb.append(String.format("%02dh ", h));
        }
        sb.append(String.format("%02dm %02ds", m, s));
        return sb.toString();
    }

    /** 從底部往上排：最後一行 mc.maplenw.top 固定為 score 0，其餘依人數動態；行數隨隊伍人數增減。未用到的行從計分板移除。 */
    private void setLines(Objective obj, Scoreboard board, List<String> lines) {
        int size = Math.min(lines.size(), LINE_ENTRIES.length);
        for (int i = 0; i < LINE_ENTRIES.length; i++) {
            String entry = LINE_ENTRIES[i];
            Team team = board.getTeam("line_" + i);
            if (team == null) {
                team = board.registerNewTeam("line_" + i);
                team.addEntry(entry);
            }
            String text = i < size ? lines.get(i) : "";
            if (text.length() > 64) text = text.substring(0, 61) + "...";
            team.prefix(LegacyComponentSerializer.legacySection().deserialize(text));
            team.suffix(net.kyori.adventure.text.Component.empty());
            if (i < size) {
                // 第一行(lines[0])在最上，最後一行(lines[size-1]=mc.maplenw.top)在最下 score=0
                int score = size - 1 - i;
                obj.getScore(entry).setScore(score);
            } else {
                board.resetScores(entry);
            }
        }
    }

    private void clearScoreboard(Player player) {
        if (player == null) return;
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }
}
