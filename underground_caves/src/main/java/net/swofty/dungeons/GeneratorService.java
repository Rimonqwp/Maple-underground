package net.swofty.dungeons;

import lombok.Getter;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("ALL")
public class GeneratorService {
    private static volatile Logger LOGGER = Logger.getLogger(GeneratorService.class.getName());
    private final DungeonsData data;
    @Getter
    private GenerationStage currentStage = GenerationStage.INITIALIZATION;
    private CompletableFuture<SkyBlockDungeon> generationFuture;
    private long generationStartTime;
    private boolean verbose;
    private boolean placed1x4;

    public GeneratorService(DungeonsData data) {
        this.data = data;
    }

    public GeneratorService withVerbose(boolean verbose) {
        this.verbose = verbose;
        return this;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public long getGenerationStartTime() {
        return generationStartTime;
    }

    private void vlog(Logger log, String message) {
        if (!verbose)
            return;
        log.info(message);
    }

    private static String fmtPos(int x, int y) {
        return "(" + x + "," + y + ")";
    }

    private String fmtRoomShort(SkyBlockDungeon.DungeonRoom room) {
        if (room == null)
            return "null";
        StringBuilder sb = new StringBuilder();
        sb.append(room.getRoomType().name());
        if (room.isCritical())
            sb.append(":C");
        if (room.getStage() > 0)
            sb.append(":S").append(room.getStage());
        if (room.getCorridorNumber() > 0)
            sb.append(":R").append(room.getCorridorNumber());
        if (room.getStructureType() != null)
            sb.append(":").append(room.getStructureType().name());
        if (room.getStructureId() != null)
            sb.append(":").append(room.getStructureId().toString(), 0, 8);
        return sb.toString();
    }

    private List<String> connectedFaces(SkyBlockDungeon dungeon, int x, int y) {
        ArrayList<String> faces = new ArrayList<>(4);
        if (x - 1 >= 0 && dungeon.isConnected(x, y, x - 1, y))
            faces.add("W");
        if (x + 1 < data.getWidth() && dungeon.isConnected(x, y, x + 1, y))
            faces.add("E");
        if (y - 1 >= 0 && dungeon.isConnected(x, y, x, y - 1))
            faces.add("N");
        if (y + 1 < data.getHeight() && dungeon.isConnected(x, y, x, y + 1))
            faces.add("S");
        return faces;
    }

    private static Logger logger() {
        Logger current = LOGGER;
        try {
            Class<?> pluginClass = Class.forName("maple.underground_caves.Test_dungeons");
            Object plugin = pluginClass.getMethod("getInstance").invoke(null);
            if (plugin != null) {
                Object pluginLogger = plugin.getClass().getMethod("getLogger").invoke(plugin);
                if (pluginLogger instanceof Logger resolved) {
                    if (current != resolved) {
                        LOGGER = resolved;
                    }
                    return resolved;
                }
            }
        } catch (Throwable ignored) {
        }
        return current;
    }

    private static class DSU {
        private final int[] p;
        private final int[] r;

        DSU(int n) {
            this.p = new int[n];
            this.r = new int[n];
            for (int i = 0; i < n; i++)
                p[i] = i;
        }

        int f(int x) {
            if (p[x] != x)
                p[x] = f(p[x]);
            return p[x];
        }

        boolean u(int a, int b) {
            int pa = f(a), pb = f(b);
            if (pa == pb)
                return false;
            if (r[pa] < r[pb]) {
                p[pa] = pb;
            } else if (r[pa] > r[pb]) {
                p[pb] = pa;
            } else {
                p[pb] = pa;
                r[pa]++;
            }
            return true;
        }
    }

    private int idx(int x, int y) {
        return y * data.getWidth() + x;
    }

    private boolean isAllowedStartExitNeighbor(SkyBlockDungeon.DungeonRoom room) {
        if (room == null)
            return false;
        if (room.getRoomType() == DungeonRoomType.PUZZLE)
            return false;
        if (room.getRoomType() == DungeonRoomType.FAIRY)
            return false;
        if (room.getRoomType() == DungeonRoomType.BLOOD)
            return false;
        if (room.getRoomType() == DungeonRoomType.TRAP)
            return false;
        if (room.getRoomType() == DungeonRoomType.MINI_BOSS)
            return false;
        if (room.getRoomType() == DungeonRoomType.ENTRANCE)
            return false;
        if (room.getRoomType() == DungeonRoomType.EXIT)
            return false;
        if (room.getStructureId() != null)
            return true;
        if (room.getStructureType() != null)
            return true;
        return room.getRoomType() == DungeonRoomType.BASE;
    }

    private DSU buildDSU(SkyBlockDungeon dungeon) {
        DSU dsu = new DSU(data.getWidth() * data.getHeight());
        for (int y = 0; y < data.getHeight(); y++) {
            for (int x = 0; x < data.getWidth(); x++) {
                SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(x, y);
                if (room != null && room.getStructureId() != null) {
                    if (x + 1 < data.getWidth()) {
                        SkyBlockDungeon.DungeonRoom right = dungeon.getRoom(x + 1, y);
                        if (right != null && room.getStructureId().equals(right.getStructureId())) {
                            dsu.u(idx(x, y), idx(x + 1, y));
                        }
                    }
                    if (y + 1 < data.getHeight()) {
                        SkyBlockDungeon.DungeonRoom down = dungeon.getRoom(x, y + 1);
                        if (down != null && room.getStructureId().equals(down.getStructureId())) {
                            dsu.u(idx(x, y), idx(x, y + 1));
                        }
                    }
                }
                if (x + 1 < data.getWidth() && dungeon.isConnected(x, y, x + 1, y)) {
                    dsu.u(idx(x, y), idx(x + 1, y));
                }
                if (y + 1 < data.getHeight() && dungeon.isConnected(x, y, x, y + 1)) {
                    dsu.u(idx(x, y), idx(x, y + 1));
                }
            }
        }
        return dsu;
    }

    private long edgeKey(int x1, int y1, int x2, int y2) {
        int a = idx(x1, y1);
        int b = idx(x2, y2);
        if (a > b) {
            int t = a;
            a = b;
            b = t;
        }
        return (((long) a) << 32) | (b & 0xffffffffL);
    }

    private List<SkyBlockDungeon.DungeonDoor> getUniqueDoors(SkyBlockDungeon dungeon) {
        LinkedHashMap<Long, SkyBlockDungeon.DungeonDoor> unique = new LinkedHashMap<>();
        for (SkyBlockDungeon.DungeonDoor door : dungeon.getAllDoors()) {
            unique.put(edgeKey(door.x1(), door.y1(), door.x2(), door.y2()), door);
        }
        return new ArrayList<>(unique.values());
    }

    private boolean isSingleComponent(DSU dsu) {
        int root = dsu.f(0);
        for (int i = 1; i < data.getWidth() * data.getHeight(); i++) {
            if (dsu.f(i) != root)
                return false;
        }
        return true;
    }

    private void normalizeConnections(SkyBlockDungeon dungeon) {
        boolean preserveExisting = true;
        if (preserveExisting) {
            dungeon.setAllDoors(getUniqueDoors(dungeon));
            return;
        }

        class DoorCandidate {
            final int x1;
            final int y1;
            final int x2;
            final int y2;
            final int score;

            DoorCandidate(int x1, int y1, int x2, int y2, int score) {
                this.x1 = x1;
                this.y1 = y1;
                this.x2 = x2;
                this.y2 = y2;
                this.score = score;
            }
        }

        int width = data.getWidth();
        int height = data.getHeight();

        int[] entrancePos = new int[] { -1, -1 };
        int[] exitPos = new int[] { -1, -1 };
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(x, y);
                if (room == null)
                    continue;
                if (room.getRoomType() == DungeonRoomType.ENTRANCE) {
                    entrancePos[0] = x;
                    entrancePos[1] = y;
                } else if (room.getRoomType() == DungeonRoomType.EXIT) {
                    exitPos[0] = x;
                    exitPos[1] = y;
                }
            }
        }

        int[][] degree = new int[width][height];
        Set<Long> existingDoorKeys = new HashSet<>();
        for (SkyBlockDungeon.DungeonDoor door : getUniqueDoors(dungeon)) {
            existingDoorKeys.add(edgeKey(door.x1(), door.y1(), door.x2(), door.y2()));
        }

        int[][] nodeOfCell = new int[width][height];
        Map<UUID, Integer> structureNode = new HashMap<>();
        List<Boolean> nodeIsStructure = new ArrayList<>();
        List<Boolean> nodeHasCritical = new ArrayList<>();

        int nodeCount = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(x, y);
                UUID s = room != null ? room.getStructureId() : null;
                int node;
                if (s != null) {
                    Integer existing = structureNode.get(s);
                    if (existing == null) {
                        node = nodeCount++;
                        structureNode.put(s, node);
                        nodeIsStructure.add(true);
                        nodeHasCritical.add(room.isCritical());
                    } else {
                        node = existing;
                        if (room.isCritical()) {
                            nodeHasCritical.set(node, true);
                        }
                    }
                } else {
                    node = nodeCount++;
                    nodeIsStructure.add(false);
                    nodeHasCritical.add(room != null && room.isCritical());
                }
                nodeOfCell[x][y] = node;
            }
        }

        Map<Long, List<DoorCandidate>> candidatesByPair = new HashMap<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x + 1 < width) {
                    int n1 = nodeOfCell[x][y];
                    int n2 = nodeOfCell[x + 1][y];
                    if (n1 != n2) {
                        SkyBlockDungeon.DungeonRoom r1 = dungeon.getRoom(x, y);
                        SkyBlockDungeon.DungeonRoom r2 = dungeon.getRoom(x + 1, y);
                        int score = 0;
                        if ((r1 != null && r1.isCritical()) || (r2 != null && r2.isCritical()))
                            score += 1000;
                        if (nodeIsStructure.get(n1) != nodeIsStructure.get(n2))
                            score += 200;
                        if (r1 != null && r2 != null && r1.getStage() == r2.getStage())
                            score += 20;
                        if (existingDoorKeys.contains(edgeKey(x, y, x + 1, y)))
                            score += 5000;
                        long key = (((long) Math.min(n1, n2)) << 32) | (Math.max(n1, n2) & 0xffffffffL);
                        candidatesByPair.computeIfAbsent(key, k -> new ArrayList<>())
                                .add(new DoorCandidate(x, y, x + 1, y, score));
                    }
                }
                if (y + 1 < height) {
                    int n1 = nodeOfCell[x][y];
                    int n2 = nodeOfCell[x][y + 1];
                    if (n1 != n2) {
                        SkyBlockDungeon.DungeonRoom r1 = dungeon.getRoom(x, y);
                        SkyBlockDungeon.DungeonRoom r2 = dungeon.getRoom(x, y + 1);
                        int score = 0;
                        if ((r1 != null && r1.isCritical()) || (r2 != null && r2.isCritical()))
                            score += 1000;
                        if (nodeIsStructure.get(n1) != nodeIsStructure.get(n2))
                            score += 200;
                        if (r1 != null && r2 != null && r1.getStage() == r2.getStage())
                            score += 20;
                        if (existingDoorKeys.contains(edgeKey(x, y, x, y + 1)))
                            score += 5000;
                        long key = (((long) Math.min(n1, n2)) << 32) | (Math.max(n1, n2) & 0xffffffffL);
                        candidatesByPair.computeIfAbsent(key, k -> new ArrayList<>())
                                .add(new DoorCandidate(x, y, x, y + 1, score));
                    }
                }
            }
        }

        class EdgeOption {
            final int a;
            final int b;
            final DoorCandidate candidate;
            final int score;

            EdgeOption(int a, int b, DoorCandidate candidate, int score) {
                this.a = a;
                this.b = b;
                this.candidate = candidate;
                this.score = score;
            }
        }

        Random random = new Random();
        List<EdgeOption> options = new ArrayList<>();
        for (Map.Entry<Long, List<DoorCandidate>> entry : candidatesByPair.entrySet()) {
            long key = entry.getKey();
            int a = (int) (key >>> 32);
            int b = (int) key;
            List<DoorCandidate> cands = entry.getValue();
            DoorCandidate best = null;
            int bestScore = Integer.MIN_VALUE;
            for (DoorCandidate c : cands) {
                int s = c.score;
                if (nodeHasCritical.get(a) || nodeHasCritical.get(b))
                    s += 1000;
                if (s > bestScore) {
                    bestScore = s;
                    best = c;
                } else if (s == bestScore && random.nextBoolean()) {
                    best = c;
                }
            }
            if (best != null) {
                options.add(new EdgeOption(a, b, best, bestScore));
            }
        }

        options.sort((o1, o2) -> {
            int cmp = Integer.compare(o2.score, o1.score);
            if (cmp != 0)
                return cmp;
            return random.nextBoolean() ? -1 : 1;
        });

        DSU dsu = new DSU(nodeCount);
        LinkedHashMap<Long, SkyBlockDungeon.DungeonDoor> selected = new LinkedHashMap<>();

        for (EdgeOption option : options) {
            DoorCandidate c = option.candidate;

            SkyBlockDungeon.DungeonRoom r1 = dungeon.getRoom(c.x1, c.y1);
            SkyBlockDungeon.DungeonRoom r2 = dungeon.getRoom(c.x2, c.y2);

            if (r1 != null) {
                DungeonRoomType t1 = r1.getRoomType();
                if ((t1 == DungeonRoomType.PUZZLE || t1 == DungeonRoomType.TRAP || t1 == DungeonRoomType.MINI_BOSS || t1 == DungeonRoomType.BLOOD)
                        && degree[c.x1][c.y1] >= 1) {
                    continue;
                }
            }
            if (r2 != null) {
                DungeonRoomType t2 = r2.getRoomType();
                if ((t2 == DungeonRoomType.PUZZLE || t2 == DungeonRoomType.TRAP || t2 == DungeonRoomType.MINI_BOSS || t2 == DungeonRoomType.BLOOD)
                        && degree[c.x2][c.y2] >= 1) {
                    continue;
                }
            }

            boolean touchesEntrance = (c.x1 == entrancePos[0] && c.y1 == entrancePos[1])
                    || (c.x2 == entrancePos[0] && c.y2 == entrancePos[1]);
            boolean touchesExit = (c.x1 == exitPos[0] && c.y1 == exitPos[1])
                    || (c.x2 == exitPos[0] && c.y2 == exitPos[1]);

            if (touchesEntrance || touchesExit) {
                int sx = touchesEntrance ? entrancePos[0] : exitPos[0];
                int sy = touchesEntrance ? entrancePos[1] : exitPos[1];
                if (sx >= 0 && sy >= 0 && degree[sx][sy] >= 1) {
                    continue;
                }
                int ox = (c.x1 == sx && c.y1 == sy) ? c.x2 : c.x1;
                int oy = (c.y1 == sy && c.x1 == sx) ? c.y2 : c.y1;
                SkyBlockDungeon.DungeonRoom other = dungeon.getRoom(ox, oy);
                if (!isAllowedStartExitNeighbor(other))
                    continue;
            }

            if (!dsu.u(option.a, option.b))
                continue;
            SkyBlockDungeon.DungeonDoor door = new SkyBlockDungeon.DungeonDoor(c.x1, c.y1, c.x2, c.y2);
            long k = edgeKey(door.x1(), door.y1(), door.x2(), door.y2());
            if (!selected.containsKey(k)) {
                selected.put(k, door);
                degree[c.x1][c.y1]++;
                degree[c.x2][c.y2]++;
            }
        }

        if (entrancePos[0] >= 0 && entrancePos[1] >= 0 && degree[entrancePos[0]][entrancePos[1]] == 0) {
            int sx = entrancePos[0], sy = entrancePos[1];
            int startNode = nodeOfCell[sx][sy];
            int[][] dirs = new int[][] { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };
            for (int[] d : dirs) {
                int nx = sx + d[0], ny = sy + d[1];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height)
                    continue;
                SkyBlockDungeon.DungeonRoom other = dungeon.getRoom(nx, ny);
                if (!isAllowedStartExitNeighbor(other))
                    continue;
                int otherNode = nodeOfCell[nx][ny];
                if (!dsu.u(startNode, otherNode))
                    continue;
                SkyBlockDungeon.DungeonDoor door = new SkyBlockDungeon.DungeonDoor(sx, sy, nx, ny);
                long k = edgeKey(door.x1(), door.y1(), door.x2(), door.y2());
                if (!selected.containsKey(k)) {
                    selected.put(k, door);
                    degree[sx][sy]++;
                    degree[nx][ny]++;
                }
                break;
            }
        }
        if (exitPos[0] >= 0 && exitPos[1] >= 0 && degree[exitPos[0]][exitPos[1]] == 0) {
            int sx = exitPos[0], sy = exitPos[1];
            int startNode = nodeOfCell[sx][sy];
            int[][] dirs = new int[][] { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };
            for (int[] d : dirs) {
                int nx = sx + d[0], ny = sy + d[1];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height)
                    continue;
                SkyBlockDungeon.DungeonRoom other = dungeon.getRoom(nx, ny);
                if (!isAllowedStartExitNeighbor(other))
                    continue;
                int otherNode = nodeOfCell[nx][ny];
                if (!dsu.u(startNode, otherNode))
                    continue;
                SkyBlockDungeon.DungeonDoor door = new SkyBlockDungeon.DungeonDoor(sx, sy, nx, ny);
                long k = edgeKey(door.x1(), door.y1(), door.x2(), door.y2());
                if (!selected.containsKey(k)) {
                    selected.put(k, door);
                    degree[sx][sy]++;
                    degree[nx][ny]++;
                }
                break;
            }
        }

        dungeon.setAllDoors(new ArrayList<>(selected.values()));
    }

    public CompletableFuture<SkyBlockDungeon> generate() {
        CompletableFuture<SkyBlockDungeon> future = new CompletableFuture<>();
        generationFuture = future;
        generationStartTime = System.currentTimeMillis();

        Thread generatorThread = new Thread(() -> {
            Logger log = logger();
            try {
                Random random = new Random();
                Exception lastException = null;
                long overallStart = System.currentTimeMillis();
                int attempt = 0;
                while (!future.isDone() && !Thread.currentThread().isInterrupted()) {
                    attempt++;
                    long attemptStart = System.currentTimeMillis();
                    log.info("[Dungeons] Generate attempt " + attempt + " start (" + data.getWidth() + "x"
                            + data.getHeight() + ")");
                    try {
                        SkyBlockDungeon dungeon = generateOnce(random, attempt, log);
                        boolean fairyMandatoryBeforeFix = isFairyMandatory(dungeon);
                        ensureFairyMandatory(dungeon, random);
                        boolean fairyMandatoryAfterFix = isFairyMandatory(dungeon);
                        long attemptMs = System.currentTimeMillis() - attemptStart;

                        if (fairyMandatoryAfterFix) {
                            recomputeCriticalPathFromTraversal(dungeon);
                            validateEntranceNeighborNotFairy(dungeon);
                            validateExitNeighborNotFairy(dungeon);
                            log.info("[Dungeons] Generate attempt " + attempt + " success in " + attemptMs + "ms"
                                    + " (fairyMandatoryBeforeFix=" + fairyMandatoryBeforeFix
                                    + ", fairyMandatoryAfterFix=" + fairyMandatoryAfterFix + ")"
                                    + " total=" + (System.currentTimeMillis() - overallStart) + "ms");
                            future.complete(dungeon);
                            return;
                        }
                        log.info("[Dungeons] Generate attempt " + attempt + " failed in " + attemptMs + "ms"
                                + " (fairyMandatoryBeforeFix=" + fairyMandatoryBeforeFix + ", fairyMandatoryAfterFix="
                                + fairyMandatoryAfterFix + ")");
                    } catch (Exception e) {
                        lastException = e;
                        long attemptMs = System.currentTimeMillis() - attemptStart;
                        log.log(Level.SEVERE,
                                "[Dungeons] Generate attempt " + attempt + " exception in " + attemptMs + "ms: "
                                        + e.getClass().getSimpleName()
                                        + (e.getMessage() != null ? (": " + e.getMessage()) : ""),
                                e);
                    } catch (Throwable t) {
                        long attemptMs = System.currentTimeMillis() - attemptStart;
                        log.log(Level.SEVERE,
                                "[Dungeons] Generate attempt " + attempt + " fatal in " + attemptMs + "ms: "
                                        + t.getClass().getSimpleName()
                                        + (t.getMessage() != null ? (": " + t.getMessage()) : ""),
                                t);
                        future.completeExceptionally(t);
                        return;
                    }

                    try {
                        Thread.sleep(10L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                if (!future.isDone()) {
                    future.completeExceptionally(lastException != null ? lastException
                            : new IllegalStateException("Dungeon generation interrupted"));
                }
            } catch (Throwable t) {
                log.log(Level.SEVERE,
                        "[Dungeons] Unhandled error in generator thread: "
                                + t.getClass().getSimpleName()
                                + (t.getMessage() != null ? (": " + t.getMessage()) : ""),
                        t);
                future.completeExceptionally(t);
            }
        }, "Dungeons-Generator-" + data.getWidth() + "x" + data.getHeight() + "-" + generationStartTime);
        generatorThread.start();

        return future;
    }

    private void recomputeCriticalPathFromTraversal(SkyBlockDungeon dungeon) {
        int[] entrance = findRoom(dungeon, DungeonRoomType.ENTRANCE);
        int[] fairy = findRoom(dungeon, DungeonRoomType.FAIRY);
        int[] exit = findRoom(dungeon, DungeonRoomType.EXIT);
        if (entrance == null || fairy == null || exit == null) {
            throw new IllegalStateException("Missing ENTRANCE/FAIRY/EXIT room");
        }

        List<int[]> path = shortestPath(dungeon, entrance[0], entrance[1], exit[0], exit[1], null);
        if (path == null || path.isEmpty()) {
            throw new IllegalStateException("No path from ENTRANCE to EXIT");
        }

        boolean containsFairy = false;
        for (int[] p : path) {
            if (p[0] == fairy[0] && p[1] == fairy[1]) {
                containsFairy = true;
                break;
            }
        }
        if (!containsFairy) {
            throw new IllegalStateException("Entrance->Exit path does not include FAIRY");
        }

        for (int y = 0; y < data.getHeight(); y++) {
            for (int x = 0; x < data.getWidth(); x++) {
                SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(x, y);
                if (room == null)
                    continue;
                room.setCritical(false);
            }
        }

        for (int[] p : path) {
            SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(p[0], p[1]);
            if (room != null) {
                room.setCritical(true);
            }
        }
    }

    private void validateEntranceNeighborNotFairy(SkyBlockDungeon dungeon) {
        int[] entrance = findRoom(dungeon, DungeonRoomType.ENTRANCE);
        if (entrance == null)
            return;
        for (int[] n : connectedNeighbors(entrance[0], entrance[1], dungeon)) {
            SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(n[0], n[1]);
            if (room != null && room.getRoomType() == DungeonRoomType.FAIRY) {
                throw new IllegalStateException("Entrance neighbor cannot be FAIRY at (" + n[0] + "," + n[1] + ")");
            }
        }
    }

    private void validateExitNeighborNotFairy(SkyBlockDungeon dungeon) {
        int[] exit = findRoom(dungeon, DungeonRoomType.EXIT);
        if (exit == null)
            return;
        for (int[] n : connectedNeighbors(exit[0], exit[1], dungeon)) {
            SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(n[0], n[1]);
            if (room != null && room.getRoomType() == DungeonRoomType.FAIRY) {
                throw new IllegalStateException("Exit neighbor cannot be FAIRY at (" + n[0] + "," + n[1] + ")");
            }
        }
    }

    private void validateAllRoomsReachEntrance(SkyBlockDungeon dungeon) {
        int[] entrance = findRoom(dungeon, DungeonRoomType.ENTRANCE);
        if (entrance == null) {
            throw new IllegalStateException("Missing ENTRANCE room");
        }

        int width = data.getWidth();
        int height = data.getHeight();
        boolean[][] visited = new boolean[width][height];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        visited[entrance[0]][entrance[1]] = true;
        q.add(new int[] { entrance[0], entrance[1] });

        int[][] dirs = new int[][] { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };
        while (!q.isEmpty()) {
            int[] cur = q.poll();
            int x = cur[0], y = cur[1];
            for (int[] d : dirs) {
                int nx = x + d[0], ny = y + d[1];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height)
                    continue;
                if (visited[nx][ny])
                    continue;
                if (!canTraverse(dungeon, x, y, nx, ny))
                    continue;
                visited[nx][ny] = true;
                q.add(new int[] { nx, ny });
            }
        }

        ArrayList<String> unreachable = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!visited[x][y]) {
                    unreachable.add(fmtPos(x, y));
                }
            }
        }
        if (!unreachable.isEmpty()) {
            int limit = Math.min(20, unreachable.size());
            String sample = String.join(",", unreachable.subList(0, limit));
            throw new IllegalStateException(
                    "Unreachable rooms from ENTRANCE: count=" + unreachable.size() + " sample=" + sample);
        }
    }

    private SkyBlockDungeon generateOnce(Random random, int attempt, Logger log) {
        long start = System.currentTimeMillis();
        log.info("[Dungeons] Attempt " + attempt + " generateOnce start");
        Map<DungeonRoomType, Integer> roomAmounts = new HashMap<>();
        for (DungeonRoomType room : DungeonRoomType.values()) {
            DungeonsData.RoomData roomData = data.getDataForOrNull(room);
            if (roomData == null)
                continue;
            if (!room.isAutomaticallyPlace())
                continue;
            int min = roomData.minimumAmount();
            int max = roomData.maximumAmount();
            if (max < min) {
                int t = min;
                min = max;
                max = t;
            }
            int amount = min + random.nextInt(max - min + 1);
            roomAmounts.put(room, amount);
        }
        // 5x5+ 地牢保證：TRAP=1、MINI_BOSS=1，各自獨立計算
        if (data.getWidth() >= 5 && data.getHeight() >= 5) {
            roomAmounts.put(DungeonRoomType.TRAP, 1);
            roomAmounts.put(DungeonRoomType.MINI_BOSS, 1);
        }

        int entranceX = random.nextInt(data.getWidth());
        int exitX = random.nextInt(data.getWidth());

        // Calculate fairy candidates - optimized to avoid duplicate loops
        List<int[]> strictFairyCandidates = new ArrayList<>();
        List<int[]> relaxedFairyCandidates = new ArrayList<>();

        for (int y = 1; y < data.getHeight() - 1; y++) {
            for (int x = 1; x < data.getWidth() - 1; x++) {
                boolean nearEntrance = Math.abs(x - entranceX) <= 1 && y <= 1;
                boolean nearExit = Math.abs(x - exitX) <= 1 && Math.abs(y - (data.getHeight() - 1)) <= 1;

                if (!nearEntrance && !nearExit) {
                    strictFairyCandidates.add(new int[] { x, y });
                } else if (!nearEntrance && !nearExit) {
                    // This condition is actually the same, but kept for clarity
                    relaxedFairyCandidates.add(new int[] { x, y });
                }
            }
        }

        List<int[]> fairyCandidates = strictFairyCandidates;
        if (fairyCandidates.isEmpty()) {
            log.warning("[Dungeons] Attempt " + attempt + " fairy constraints too strict for "
                    + data.getWidth() + "x" + data.getHeight()
                    + " entranceX=" + entranceX
                    + " exitX=" + exitX
                    + " ; using relaxed candidates");
            fairyCandidates = relaxedFairyCandidates;
        }
        if (fairyCandidates.isEmpty()) {
            throw new IllegalStateException("No valid fairy candidate for " + data.getWidth() + "x" + data.getHeight());
        }
        int[] fairyPos = fairyCandidates.get(random.nextInt(fairyCandidates.size()));
        int fairyX = fairyPos[0];
        int fairyY = fairyPos[1];

        SkyBlockDungeon dungeon = new SkyBlockDungeon();
        DungeonUtilities.loopOverDungeonRooms(data).forEach(
                values -> dungeon.setRoom(values.getKey(), values.getValue(), SkyBlockDungeon.DungeonRoom.ofBase()));

        dungeon.setRoom(fairyX, fairyY, new SkyBlockDungeon.DungeonRoom(DungeonRoomType.FAIRY));
        dungeon.setRoom(entranceX, 0, new SkyBlockDungeon.DungeonRoom(DungeonRoomType.ENTRANCE));
        dungeon.setRoom(exitX, data.getHeight() - 1, new SkyBlockDungeon.DungeonRoom(DungeonRoomType.EXIT));

        vlog(log, "[Dungeons][V] Attempt " + attempt + " placed ENTRANCE=" + fmtPos(entranceX, 0)
                + " FAIRY=" + fmtPos(fairyX, fairyY)
                + " EXIT=" + fmtPos(exitX, data.getHeight() - 1));

        currentStage = GenerationStage.CRITICAL_PATH;
        long criticalStart = System.currentTimeMillis();

        List<int[]> preFairyCandidates = new ArrayList<>();
        for (int y = 1; y < data.getHeight() - 1; y++) {
            for (int x = 1; x < data.getWidth() - 1; x++) {
                if (x == fairyX && y == fairyY)
                    continue;
                if (x == entranceX && y == 0)
                    continue;
                if (x == exitX && y == data.getHeight() - 1)
                    continue;
                preFairyCandidates.add(new int[] { x, y });
            }
        }
        if (preFairyCandidates.isEmpty()) {
            throw new IllegalStateException(
                    "No valid preFairy candidate for " + data.getWidth() + "x" + data.getHeight());
        }
        int tries = Math.min(8, preFairyCandidates.size());
        int[] randomPointPreFairy = null;
        List<int[]> path = null;
        List<int[]> seg1 = List.of();
        List<int[]> seg2 = List.of();
        List<int[]> seg3 = List.of();

        for (int preTry = 1; preTry <= tries; preTry++) {
            randomPointPreFairy = preFairyCandidates.get(random.nextInt(preFairyCandidates.size()));
            log.info("[Dungeons] Attempt " + attempt + " init"
                    + " entrance=(" + entranceX + ",0)"
                    + " exit=(" + exitX + "," + (data.getHeight() - 1) + ")"
                    + " fairy=(" + fairyX + "," + fairyY + ")"
                    + " preFairy=(" + randomPointPreFairy[0] + "," + randomPointPreFairy[1] + ")"
                    + " preTry=" + preTry + "/" + tries);

            List<int[]> candidatePath = new ArrayList<>();

            long seg1Start = System.currentTimeMillis();
            log.info("[Dungeons] Attempt " + attempt + " A* seg1 start entrance->preFairy");
            seg1 = DungeonUtilities.aStar(candidatePath, entranceX, 0, randomPointPreFairy[0], randomPointPreFairy[1],
                    data.getWidth(), data.getHeight());
            log.info("[Dungeons] Attempt " + attempt + " A* seg1 done in " + (System.currentTimeMillis() - seg1Start)
                    + "ms len=" + seg1.size());
            if (seg1.isEmpty())
                continue;
            candidatePath.addAll(seg1.stream().map(cell -> new int[] { cell[0], cell[1], 1 }).toList());

            long seg2Start = System.currentTimeMillis();
            log.info("[Dungeons] Attempt " + attempt + " A* seg2 start preFairy->fairy");
            seg2 = DungeonUtilities.aStar(candidatePath, randomPointPreFairy[0], randomPointPreFairy[1], fairyX, fairyY,
                    data.getWidth(), data.getHeight());
            log.info("[Dungeons] Attempt " + attempt + " A* seg2 done in " + (System.currentTimeMillis() - seg2Start)
                    + "ms len=" + seg2.size());
            if (seg2.isEmpty())
                continue;
            if (seg2.size() > 1) {
                candidatePath.addAll(
                        seg2.subList(1, seg2.size()).stream().map(cell -> new int[] { cell[0], cell[1], 2 }).toList());
            }

            long seg3Start = System.currentTimeMillis();
            log.info("[Dungeons] Attempt " + attempt + " A* seg3 start fairy->exit");
            seg3 = DungeonUtilities.aStar(candidatePath, fairyX, fairyY, exitX, data.getHeight() - 1, data.getWidth(),
                    data.getHeight());
            log.info("[Dungeons] Attempt " + attempt + " A* seg3 done in " + (System.currentTimeMillis() - seg3Start)
                    + "ms len=" + seg3.size());
            if (seg3.isEmpty())
                continue;
            if (seg3.size() > 1) {
                candidatePath.addAll(
                        seg3.subList(1, seg3.size()).stream().map(cell -> new int[] { cell[0], cell[1], 3 }).toList());
            }

            path = candidatePath;
            break;
        }

        if (path == null) {
            throw new IllegalStateException("A* critical path failed (entrance->preFairy->fairy->exit)");
        }

        if (path.isEmpty()) {
            throw new IllegalStateException("Critical path empty");
        }
        log.info("[Dungeons] Attempt " + attempt + " critical path built in "
                + (System.currentTimeMillis() - criticalStart) + "ms"
                + " (seg1=" + seg1.size() + ", seg2=" + seg2.size() + ", seg3=" + seg3.size() + ", total=" + path.size()
                + ")");

        for (int i = 0; i < path.size(); i++) {
            int[] cell = path.get(i);
            dungeon.getRoom(cell[0], cell[1]).setCritical(true);
            dungeon.getRoom(cell[0], cell[1]).setStage(cell[2]);
            if (i + 1 < path.size()) {
                int[] nextCell = path.get(i + 1);
                dungeon.connectRooms(cell[0], cell[1], nextCell[0], nextCell[1]);
            }
        }

        if (verbose) {
            StringBuilder sb = new StringBuilder();
            sb.append("[Dungeons][V] Attempt ").append(attempt).append(" criticalPath=");
            for (int i = 0; i < path.size(); i++) {
                int[] cell = path.get(i);
                if (i > 0)
                    sb.append("->");
                sb.append(fmtPos(cell[0], cell[1])).append(":S").append(cell[2]);
            }
            vlog(log, sb.toString());
        }

        currentStage = GenerationStage.SIDE_PATHS;
        long sideStart = System.currentTimeMillis();
        log.info("[Dungeons] Attempt " + attempt + " side paths start");

        int amountOfRooms = data.getWidth() * data.getHeight();
        int filledRooms = path.size();

        Map<Integer, List<int[]>> stageToRooms = new HashMap<>();
        for (int[] cell : path) {
            if ((cell[0] == entranceX && cell[1] == 0) || (cell[0] == exitX && cell[1] == data.getHeight() - 1)) {
                continue;
            }
            stageToRooms.computeIfAbsent(cell[2], k -> new ArrayList<>()).add(new int[] { cell[0], cell[1], cell[2] });
        }

        for (int sidePathCycle = 1; filledRooms < amountOfRooms; sidePathCycle++) {
            List<int[]> roomsToExpand = new ArrayList<>();
            for (int stage = 1; stage <= sidePathCycle; stage++) {
                List<int[]> currentStageRooms = stageToRooms.getOrDefault(stage, new ArrayList<>());
                for (int[] cell : currentStageRooms) {
                    List<int[]> freeNeighbours = DungeonUtilities.getFreeNeighbours(cell[0], cell[1], dungeon, data);
                    for (int[] neighbour : freeNeighbours) {
                        dungeon.connectRooms(cell[0], cell[1], neighbour[0], neighbour[1]);
                        dungeon.getRoom(neighbour[0], neighbour[1]).setStage(cell[2]);
                        filledRooms++;
                        roomsToExpand.add(new int[] { neighbour[0], neighbour[1], cell[2] });
                    }
                }
            }
            if (roomsToExpand.isEmpty()) {
                throw new IllegalStateException("Side path expansion stuck (filledRooms=" + filledRooms + "/"
                        + amountOfRooms + ", cycle=" + sidePathCycle + ")");
            }
            roomsToExpand.forEach(room -> stageToRooms.computeIfAbsent(room[2], k -> new ArrayList<>()).add(room));
        }
        log.info("[Dungeons] Attempt " + attempt + " side paths filled in " + (System.currentTimeMillis() - sideStart)
                + "ms");

        currentStage = GenerationStage.STRUCTURE_GENERATION;
        long structuresStart = System.currentTimeMillis();
        assignStructures(dungeon, log);
        log.info("[Dungeons] Attempt " + attempt + " structures assigned in "
                + (System.currentTimeMillis() - structuresStart) + "ms");

        currentStage = GenerationStage.ROOM_ASSIGNMENT;
        long roomsStart = System.currentTimeMillis();
        assignRoomsAsync(roomAmounts, dungeon, log);
        log.info("[Dungeons] Attempt " + attempt + " rooms assigned in " + (System.currentTimeMillis() - roomsStart)
                + "ms");

        if (verbose) {
            for (int y = 0; y < data.getHeight(); y++) {
                for (int x = 0; x < data.getWidth(); x++) {
                    SkyBlockDungeon.DungeonRoom r = dungeon.getRoom(x, y);
                    if (r == null)
                        continue;
                    vlog(log, "[Dungeons][V] Attempt " + attempt + " room " + fmtPos(x, y)
                            + " " + fmtRoomShort(r) + " doors=" + doorCount(dungeon, x, y)
                            + " faces=" + connectedFaces(dungeon, x, y));
                }
            }
        }

        currentStage = GenerationStage.CORRIDOR_ASSIGNMENT;
        long corridorsStart = System.currentTimeMillis();
        assignCorridors(dungeon);
        log.info("[Dungeons] Attempt " + attempt + " corridors assigned in "
                + (System.currentTimeMillis() - corridorsStart) + "ms");

        currentStage = GenerationStage.FINAL_TOUCHES;
        long finalStart = System.currentTimeMillis();
        DSU dsuFinal = buildDSU(dungeon);
        for (int y = 0; y < data.getHeight(); y++) {
            for (int x = 0; x < data.getWidth(); x++) {
                SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(x, y);
                if (room.getRoomType() != DungeonRoomType.BASE)
                    continue;

                List<int[]> adjacentBases = DungeonUtilities.getAdjacentBaseRooms(x, y, dungeon, data);
                for (int[] adjacent : adjacentBases) {
                    SkyBlockDungeon.DungeonRoom adjacentRoom = dungeon.getRoom(adjacent[0], adjacent[1]);
                    if (adjacentRoom.getRoomType() != DungeonRoomType.BASE)
                        continue;
                    if (adjacentRoom.getStage() != room.getStage())
                        continue;

                    if (random.nextDouble() > 0.9) {
                        int a = idx(x, y);
                        int b = idx(adjacent[0], adjacent[1]);
                        if (dsuFinal.u(a, b)) {
                            dungeon.connectRooms(x, y, adjacent[0], adjacent[1]);
                        }
                    }
                }
            }
        }

        log.info("[Dungeons] Attempt " + attempt + " final edges in " + (System.currentTimeMillis() - finalStart)
                + "ms");

        long normalizeStart = System.currentTimeMillis();
        normalizeConnections(dungeon);
        long singleDoorStart = System.currentTimeMillis();
        enforceSingleDoorRooms(dungeon, log);
        validateEntranceNeighborNotFairy(dungeon);
        validateExitNeighborNotFairy(dungeon);
        ensureAllRoomsReachEntrance(dungeon, log);
        validateAllRoomsReachEntrance(dungeon);
        log.info("[Dungeons] Attempt " + attempt + " normalize connections in "
                + (System.currentTimeMillis() - normalizeStart) + "ms"
                + " singleDoor=" + (System.currentTimeMillis() - singleDoorStart) + "ms"
                + " totalGenerateOnce=" + (System.currentTimeMillis() - start) + "ms");

        if (verbose) {
            ArrayList<String> doors = new ArrayList<>();
            for (SkyBlockDungeon.DungeonDoor d : dungeon.getAllDoors()) {
                doors.add(fmtPos(d.x1(), d.y1()) + "<->" + fmtPos(d.x2(), d.y2()));
            }
            vlog(log, "[Dungeons][V] Attempt " + attempt + " doors=" + doors.size() + " " + doors);
        }
        return dungeon;
    }

    private boolean isFairyMandatory(SkyBlockDungeon dungeon) {
        int[] entrance = findRoom(dungeon, DungeonRoomType.ENTRANCE);
        int[] fairy = findRoom(dungeon, DungeonRoomType.FAIRY);
        int[] exit = findRoom(dungeon, DungeonRoomType.EXIT);
        if (entrance == null || fairy == null || exit == null)
            return false;

        if (!isReachable(dungeon, entrance[0], entrance[1], fairy[0], fairy[1], null))
            return false;
        if (!isReachable(dungeon, fairy[0], fairy[1], exit[0], exit[1], null))
            return false;

        boolean[][] blocked = new boolean[data.getWidth()][data.getHeight()];
        blocked[fairy[0]][fairy[1]] = true;
        return !isReachable(dungeon, entrance[0], entrance[1], exit[0], exit[1], blocked);
    }

    private void ensureFairyMandatory(SkyBlockDungeon dungeon, Random random) {
        int[] entrance = findRoom(dungeon, DungeonRoomType.ENTRANCE);
        int[] fairy = findRoom(dungeon, DungeonRoomType.FAIRY);
        int[] exit = findRoom(dungeon, DungeonRoomType.EXIT);
        if (entrance == null || fairy == null || exit == null)
            return;

        boolean[][] blocked = new boolean[data.getWidth()][data.getHeight()];
        blocked[fairy[0]][fairy[1]] = true;
        if (!isReachable(dungeon, entrance[0], entrance[1], fairy[0], fairy[1], null))
            return;
        if (!isReachable(dungeon, fairy[0], fairy[1], exit[0], exit[1], null))
            return;
        if (!isReachable(dungeon, entrance[0], entrance[1], exit[0], exit[1], blocked))
            return;

        List<int[]> path = shortestPath(dungeon, entrance[0], entrance[1], exit[0], exit[1], null);
        if (path == null || path.size() < 3)
            return;

        int[] replacement = pickFairyReplacementFromPath(dungeon, path, entrance, exit, random);
        if (replacement == null)
            return;

        SkyBlockDungeon.DungeonRoom fairyRoom = dungeon.getRoom(fairy[0], fairy[1]);
        SkyBlockDungeon.DungeonRoom replacementRoom = dungeon.getRoom(replacement[0], replacement[1]);
        if (fairyRoom == null || replacementRoom == null)
            return;

        DungeonRoomType t = replacementRoom.getRoomType();
        boolean c = replacementRoom.isCritical();
        replacementRoom.setRoomType(DungeonRoomType.FAIRY);
        replacementRoom.setCritical(fairyRoom.isCritical());
        dungeon.setRoom(replacement[0], replacement[1], replacementRoom);

        fairyRoom.setRoomType(t);
        fairyRoom.setCritical(c);
        dungeon.setRoom(fairy[0], fairy[1], fairyRoom);
    }

    private int[] pickFairyReplacementFromPath(SkyBlockDungeon dungeon, List<int[]> path, int[] entrance, int[] exit,
            Random random) {
        List<int[]> candidates = new ArrayList<>();
        for (int i = 1; i < path.size() - 1; i++) {
            int[] p = path.get(i);
            SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(p[0], p[1]);
            if (room == null)
                continue;
            if (room.getRoomType() != DungeonRoomType.BASE)
                continue;
            if (room.getStructureType() != null)
                continue;
            if (!isValidFairyLocation(p[0], p[1], entrance[0], entrance[1], exit[0], exit[1]))
                continue;
            candidates.add(p);
        }
        if (!candidates.isEmpty()) {
            return candidates.get(random.nextInt(candidates.size()));
        }

        for (int i = 1; i < path.size() - 1; i++) {
            int[] p = path.get(i);
            SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(p[0], p[1]);
            if (room == null)
                continue;
            if (room.getStructureType() != null)
                continue;
            if (room.getRoomType() == DungeonRoomType.ENTRANCE || room.getRoomType() == DungeonRoomType.EXIT)
                continue;
            if (room.getRoomType() == DungeonRoomType.PUZZLE || room.getRoomType() == DungeonRoomType.BLOOD
                    || room.getRoomType() == DungeonRoomType.TRAP || room.getRoomType() == DungeonRoomType.MINI_BOSS) {
                continue;
            }
            candidates.add(p);
        }
        if (!candidates.isEmpty()) {
            return candidates.get(random.nextInt(candidates.size()));
        }

        return null;
    }

    private boolean isValidFairyLocation(int x, int y, int entranceX, int entranceY, int exitX, int exitY) {
        if (x == 0 || x == data.getWidth() - 1)
            return false;
        if (y == 0 || y == data.getHeight() - 1)
            return false;
        if (Math.abs(x - entranceX) <= 1 && y <= 1)
            return false;
        return !(Math.abs(x - exitX) <= 1 && Math.abs(y - exitY) <= 1);
    }

    private int[] findRoom(SkyBlockDungeon dungeon, DungeonRoomType type) {
        for (int y = 0; y < data.getHeight(); y++) {
            for (int x = 0; x < data.getWidth(); x++) {
                SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(x, y);
                if (room != null && room.getRoomType() == type) {
                    return new int[] { x, y };
                }
            }
        }
        return null;
    }

    private boolean isReachable(SkyBlockDungeon dungeon, int sx, int sy, int tx, int ty, boolean[][] blocked) {
        boolean[][] visited = new boolean[data.getWidth()][data.getHeight()];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        if (blocked != null && blocked[sx][sy])
            return false;
        visited[sx][sy] = true;
        q.add(new int[] { sx, sy });
        int[][] dirs = new int[][] { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };

        while (!q.isEmpty()) {
            int[] cur = q.poll();
            int x = cur[0], y = cur[1];
            if (x == tx && y == ty)
                return true;
            for (int[] d : dirs) {
                int nx = x + d[0], ny = y + d[1];
                if (nx < 0 || ny < 0 || nx >= data.getWidth() || ny >= data.getHeight())
                    continue;
                if (blocked != null && blocked[nx][ny])
                    continue;
                if (visited[nx][ny])
                    continue;
                if (!canTraverse(dungeon, x, y, nx, ny))
                    continue;
                visited[nx][ny] = true;
                q.add(new int[] { nx, ny });
            }
        }
        return false;
    }

    private List<int[]> shortestPath(SkyBlockDungeon dungeon, int sx, int sy, int tx, int ty, boolean[][] blocked) {
        int width = data.getWidth();
        int height = data.getHeight();
        int n = width * height;
        int[] parent = new int[n];
        Arrays.fill(parent, -1);
        boolean[] visited = new boolean[n];

        int s = idx(sx, sy);
        int t = idx(tx, ty);
        if (blocked != null && blocked[sx][sy])
            return null;
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[] { sx, sy });
        visited[s] = true;

        int[][] dirs = new int[][] { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };
        while (!q.isEmpty()) {
            int[] cur = q.poll();
            int x = cur[0], y = cur[1];
            if (x == tx && y == ty)
                break;
            for (int[] d : dirs) {
                int nx = x + d[0], ny = y + d[1];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height)
                    continue;
                if (blocked != null && blocked[nx][ny])
                    continue;
                if (!canTraverse(dungeon, x, y, nx, ny))
                    continue;
                int ni = idx(nx, ny);
                if (visited[ni])
                    continue;
                visited[ni] = true;
                parent[ni] = idx(x, y);
                q.add(new int[] { nx, ny });
            }
        }

        if (!visited[t])
            return null;

        ArrayList<int[]> path = new ArrayList<>();
        int cur = t;
        while (cur != -1) {
            int x = cur % width;
            int y = cur / width;
            path.add(new int[] { x, y });
            if (cur == s)
                break;
            cur = parent[cur];
        }
        Collections.reverse(path);
        return path;
    }

    private boolean canTraverse(SkyBlockDungeon dungeon, int x1, int y1, int x2, int y2) {
        if (dungeon.isConnected(x1, y1, x2, y2))
            return true;
        SkyBlockDungeon.DungeonRoom r1 = dungeon.getRoom(x1, y1);
        SkyBlockDungeon.DungeonRoom r2 = dungeon.getRoom(x2, y2);
        if (r1 == null || r2 == null)
            return false;
        UUID s1 = r1.getStructureId();
        if (s1 == null)
            return false;
        return s1.equals(r2.getStructureId());
    }

    public void assignStructures(SkyBlockDungeon dungeon, Logger log) {
        Random random = new Random();
        placed1x4 = false;

        for (DungeonStructureType type : DungeonStructureType.values()) {
            int count = data.getStructureCount(type);
            int assigned = 0;
            int attempts = 0;

            while (assigned < count && attempts < 1000) {
                attempts++;
                int x = random.nextInt(data.getWidth());
                int y = random.nextInt(data.getHeight());

                if (tryPlaceStructure(dungeon, x, y, type, random, log)) {
                    assigned++;
                }
            }
        }
    }

    private boolean tryPlaceStructure(SkyBlockDungeon dungeon, int x, int y, DungeonStructureType type, Random random,
            Logger log) {
        List<int[]> coords = new ArrayList<>();
        coords.add(new int[] { x, y });

        switch (type) {
            case DOUBLE_1x2 -> coords.add(new int[] { x + 1, y });
            case DOUBLE_2x1 -> coords.add(new int[] { x, y + 1 });
            case TRIPLE_3x1 -> {
                int length = 3;
                if (!placed1x4 && data.getWidth() == 6 && data.getHeight() == 6) {
                    length = 4;
                }

                boolean horizontal = random.nextBoolean();
                if (horizontal) {
                    for (int i = 1; i < length; i++) {
                        coords.add(new int[] { x + i, y });
                    }
                } else {
                    for (int i = 1; i < length; i++) {
                        coords.add(new int[] { x, y + i });
                    }
                }
            }
            case QUAD_2x2 -> {
                coords.add(new int[] { x + 1, y });
                coords.add(new int[] { x, y + 1 });
                coords.add(new int[] { x + 1, y + 1 });
            }
            case L_SHAPE -> {
                int rotation = random.nextInt(4);
                if (rotation == 0) {
                    coords.add(new int[] { x + 1, y });
                    coords.add(new int[] { x, y + 1 });
                } else if (rotation == 1) {
                    coords.add(new int[] { x - 1, y });
                    coords.add(new int[] { x, y + 1 });
                } else if (rotation == 2) {
                    coords.add(new int[] { x - 1, y });
                    coords.add(new int[] { x, y - 1 });
                } else {
                    coords.add(new int[] { x + 1, y });
                    coords.add(new int[] { x, y - 1 });
                }
            }
        }

        // Validate
        for (int[] pos : coords) {
            if (pos[0] < 0 || pos[0] >= data.getWidth() || pos[1] < 0 || pos[1] >= data.getHeight())
                return false;
            SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(pos[0], pos[1]);
            if (room == null || room.getRoomType() != DungeonRoomType.BASE || room.getStructureType() != null)
                return false;
        }

        // Place
        UUID id = UUID.randomUUID();
        for (int[] pos : coords) {
            SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(pos[0], pos[1]);
            room.setStructureType(type);
            room.setStructureId(id);
        }

        if (!placed1x4 && type == DungeonStructureType.TRIPLE_3x1 && coords.size() == 4) {
            placed1x4 = true;
        }

        DSU dsu = buildDSU(dungeon);
        for (int i = 0; i < coords.size(); i++) {
            for (int j = i + 1; j < coords.size(); j++) {
                int[] p1 = coords.get(i);
                int[] p2 = coords.get(j);
                if (Math.abs(p1[0] - p2[0]) + Math.abs(p1[1] - p2[1]) == 1) {
                    int a = idx(p1[0], p1[1]);
                    int b = idx(p2[0], p2[1]);
                    if (dsu.u(a, b)) {
                        dungeon.connectRooms(p1[0], p1[1], p2[0], p2[1]);
                    }
                }
            }
        }

        if (verbose) {
            StringBuilder sb = new StringBuilder();
            sb.append("[Dungeons][V] placedStructure type=").append(type.name()).append(" id=")
                    .append(id.toString(), 0, 8).append(" cells=");
            for (int i = 0; i < coords.size(); i++) {
                int[] p = coords.get(i);
                if (i > 0)
                    sb.append(",");
                sb.append(fmtPos(p[0], p[1]));
            }
            vlog(log, sb.toString());
        }
        return true;
    }

    public void assignRoomsAsync(Map<DungeonRoomType, Integer> roomAmounts, SkyBlockDungeon dungeon, Logger log) {
        Random random = new Random();
        List<Map.Entry<DungeonRoomType, Integer>> entries = new ArrayList<>(roomAmounts.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        for (Map.Entry<DungeonRoomType, Integer> entry : entries) {
            DungeonRoomType type = entry.getKey();
            int target = entry.getValue() != null ? entry.getValue() : 0;
            int placedCount = 0;
            for (int placed = 0; placed < target; placed++) {
                int[] best = findBestAssignableCell(dungeon, type, random);
                if (best == null)
                    break;
                SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(best[0], best[1]);
                room.setRoomType(type);
                dungeon.setRoom(best[0], best[1], room);
                placedCount++;
            }
            if ((type == DungeonRoomType.TRAP || type == DungeonRoomType.MINI_BOSS) && placedCount == 0
                    && data.getWidth() >= 5 && data.getHeight() >= 5) {
                List<int[]> fallback = new ArrayList<>();
                for (int y = 0; y < data.getHeight(); y++) {
                    for (int x = 0; x < data.getWidth(); x++) {
                        SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(x, y);
                        if (room == null)
                            continue;
                        if (room.getRoomType() != DungeonRoomType.BASE)
                            continue;
                        if (room.getStructureType() != null)
                            continue;
                        if (room.isCritical())
                            continue;
                        if (doorCount(dungeon, x, y) == 0)
                            continue;
                        fallback.add(new int[] { x, y });
                    }
                }
                if (!fallback.isEmpty()) {
                    int[] chosen = fallback.get(random.nextInt(fallback.size()));
                    SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(chosen[0], chosen[1]);
                    room.setRoomType(type);
                    dungeon.setRoom(chosen[0], chosen[1], room);
                    placedCount++;
                }
            }
            if (placedCount < target) {
                log.warning("[Dungeons] Could not place all rooms for type=" + type.name() + " target=" + target
                        + " placed=" + placedCount);
            }
        }
    }

    private int[] findBestAssignableCell(SkyBlockDungeon dungeon, DungeonRoomType type, Random random) {
        List<int[]> candidates = new ArrayList<>();

        for (int y = 0; y < data.getHeight(); y++) {
            for (int x = 0; x < data.getWidth(); x++) {
                SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(x, y);
                if (room == null)
                    continue;
                if (room.getRoomType() != DungeonRoomType.BASE)
                    continue;
                if (room.getStructureType() != null)
                    continue;
                candidates.add(new int[] { x, y });
            }
        }

        if (candidates.isEmpty())
            return null;
        Collections.shuffle(candidates, random);

        int[] best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int[] c : candidates) {
            int x = c[0], y = c[1];
            SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(x, y);
            int doors = doorCount(dungeon, x, y);
            if (doors == 0)
                continue;

            if (type == DungeonRoomType.PUZZLE || type == DungeonRoomType.TRAP || type == DungeonRoomType.MINI_BOSS || type == DungeonRoomType.BLOOD) {
                if (room.isCritical())
                    continue;
                if (doors != 1)
                    continue;
            }

            int score = 0;
            if (doors == 1)
                score += 1000;
            else if (doors == 2)
                score += 300;
            else
                score += 50;

            if (!room.isCritical())
                score += 80;

            if (type == DungeonRoomType.PUZZLE || type == DungeonRoomType.TRAP || type == DungeonRoomType.MINI_BOSS || type == DungeonRoomType.BLOOD) {
                if (doors == 1)
                    score += 200;
            }

            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }

        if (best != null)
            return best;

        if (type == DungeonRoomType.PUZZLE || type == DungeonRoomType.TRAP || type == DungeonRoomType.MINI_BOSS || type == DungeonRoomType.BLOOD) {
            return null;
        }

        Collections.shuffle(candidates, random);
        for (int[] c : candidates) {
            int x = c[0], y = c[1];
            if (doorCount(dungeon, x, y) > 0)
                return c;
        }
        return null;
    }

    private int doorCount(SkyBlockDungeon dungeon, int x, int y) {
        int count = 0;
        if (x - 1 >= 0 && dungeon.isConnected(x, y, x - 1, y))
            count++;
        if (x + 1 < data.getWidth() && dungeon.isConnected(x, y, x + 1, y))
            count++;
        if (y - 1 >= 0 && dungeon.isConnected(x, y, x, y - 1))
            count++;
        if (y + 1 < data.getHeight() && dungeon.isConnected(x, y, x, y + 1))
            count++;
        return count;
    }

    private void enforceSingleDoorRooms(SkyBlockDungeon dungeon, Logger log) {
        int[] entrance = findRoom(dungeon, DungeonRoomType.ENTRANCE);
        int[] fairy = findRoom(dungeon, DungeonRoomType.FAIRY);
        int[] exit = findRoom(dungeon, DungeonRoomType.EXIT);
        if (entrance == null || fairy == null || exit == null) {
            throw new IllegalStateException("Missing ENTRANCE/FAIRY/EXIT room");
        }

        for (int y = 0; y < data.getHeight(); y++) {
            for (int x = 0; x < data.getWidth(); x++) {
                SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(x, y);
                if (room == null)
                    continue;
                DungeonRoomType type = room.getRoomType();
                if (type != DungeonRoomType.PUZZLE && type != DungeonRoomType.TRAP && type != DungeonRoomType.MINI_BOSS
                        && type != DungeonRoomType.BLOOD)
                    continue;

                List<int[]> neighbors = connectedNeighbors(x, y, dungeon);
                while (neighbors.size() > 1) {
                    int[][] degree = degreeByDoors(dungeon);
                    boolean removed = false;

                    for (int[] n : neighbors) {
                        if (degree[n[0]][n[1]] <= 1)
                            continue;

                        List<SkyBlockDungeon.DungeonDoor> originalDoors = dungeon.getAllDoors();
                        removeDoorBetween(dungeon, x, y, n[0], n[1]);

                        boolean ok = doorCount(dungeon, x, y) >= 1
                                && isReachable(dungeon, entrance[0], entrance[1], fairy[0], fairy[1], null)
                                && isReachable(dungeon, fairy[0], fairy[1], exit[0], exit[1], null)
                                && isReachable(dungeon, entrance[0], entrance[1], exit[0], exit[1], null)
                                && allCellsReachFrom(dungeon, entrance[0], entrance[1]);

                        if (ok) {
                            removed = true;
                            break;
                        }

                        dungeon.setAllDoors(originalDoors);
                    }

                    if (!removed) {
                        throw new IllegalStateException("Cannot enforce single door for " + type
                                + " at (" + x + "," + y + ") doors=" + neighbors.size());
                    }
                    neighbors = connectedNeighbors(x, y, dungeon);
                }

                int doors = doorCount(dungeon, x, y);
                if (doors != 1) {
                    throw new IllegalStateException("Single-door enforcement failed for " + type
                            + " at (" + x + "," + y + ") doors=" + doors);
                }
            }
        }
    }

    private boolean allCellsReachFrom(SkyBlockDungeon dungeon, int startX, int startY) {
        boolean[][] visited = visitedFrom(dungeon, startX, startY);
        for (int y = 0; y < data.getHeight(); y++) {
            for (int x = 0; x < data.getWidth(); x++) {
                if (dungeon.getRoom(x, y) == null)
                    continue;
                if (!visited[x][y])
                    return false;
            }
        }
        return true;
    }

    private boolean[][] visitedFrom(SkyBlockDungeon dungeon, int startX, int startY) {
        int width = data.getWidth();
        int height = data.getHeight();
        boolean[][] visited = new boolean[width][height];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        visited[startX][startY] = true;
        q.add(new int[] { startX, startY });

        int[][] dirs = new int[][] { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };
        while (!q.isEmpty()) {
            int[] cur = q.poll();
            int x = cur[0], y = cur[1];
            for (int[] d : dirs) {
                int nx = x + d[0], ny = y + d[1];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height)
                    continue;
                if (visited[nx][ny])
                    continue;
                if (!canTraverse(dungeon, x, y, nx, ny))
                    continue;
                visited[nx][ny] = true;
                q.add(new int[] { nx, ny });
            }
        }

        return visited;
    }

    private void ensureAllRoomsReachEntrance(SkyBlockDungeon dungeon, Logger log) {
        int[] entrance = findRoom(dungeon, DungeonRoomType.ENTRANCE);
        if (entrance == null)
            return;

        int width = data.getWidth();
        int height = data.getHeight();

        for (int pass = 0; pass < width * height; pass++) {
            boolean[][] visited = visitedFrom(dungeon, entrance[0], entrance[1]);
            int unreachableCount = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (dungeon.getRoom(x, y) != null && !visited[x][y])
                        unreachableCount++;
                }
            }
            if (unreachableCount == 0)
                return;

            boolean bridgedAny = false;
            for (int y = 0; y < height && !bridgedAny; y++) {
                for (int x = 0; x < width && !bridgedAny; x++) {
                    if (dungeon.getRoom(x, y) == null || visited[x][y])
                        continue;

                    int[][] dirs = new int[][] { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };
                    for (int[] d : dirs) {
                        int nx = x + d[0], ny = y + d[1];
                        if (nx < 0 || ny < 0 || nx >= width || ny >= height)
                            continue;
                        if (!visited[nx][ny])
                            continue;
                        if (dungeon.isConnected(x, y, nx, ny))
                            continue;

                        SkyBlockDungeon.DungeonRoom r1 = dungeon.getRoom(x, y);
                        SkyBlockDungeon.DungeonRoom r2 = dungeon.getRoom(nx, ny);
                        if (r1 == null || r2 == null)
                            continue;

                        if (!canAddRepairDoor(dungeon, x, y, r1) || !canAddRepairDoor(dungeon, nx, ny, r2)) {
                            continue;
                        }

                        dungeon.connectRooms(x, y, nx, ny);
                        vlog(log, "[Dungeons][V] repairDoor " + fmtPos(x, y) + "<->" + fmtPos(nx, ny));
                        bridgedAny = true;
                        break;
                    }
                }
            }

            if (!bridgedAny) {
                throw new IllegalStateException(
                        "Cannot repair unreachable rooms from ENTRANCE (unreachable=" + unreachableCount + ")");
            }
        }
    }

    private boolean canAddRepairDoor(SkyBlockDungeon dungeon, int x, int y, SkyBlockDungeon.DungeonRoom room) {
        DungeonRoomType type = room.getRoomType();
        if (type == DungeonRoomType.ENTRANCE || type == DungeonRoomType.EXIT) {
            return doorCount(dungeon, x, y) == 0;
        }
        if (type == DungeonRoomType.PUZZLE || type == DungeonRoomType.TRAP || type == DungeonRoomType.MINI_BOSS || type == DungeonRoomType.BLOOD) {
            return doorCount(dungeon, x, y) == 0;
        }
        return true;
    }

    private List<int[]> connectedNeighbors(int x, int y, SkyBlockDungeon dungeon) {
        ArrayList<int[]> out = new ArrayList<>(4);
        if (x - 1 >= 0 && dungeon.isConnected(x, y, x - 1, y))
            out.add(new int[] { x - 1, y });
        if (x + 1 < data.getWidth() && dungeon.isConnected(x, y, x + 1, y))
            out.add(new int[] { x + 1, y });
        if (y - 1 >= 0 && dungeon.isConnected(x, y, x, y - 1))
            out.add(new int[] { x, y - 1 });
        if (y + 1 < data.getHeight() && dungeon.isConnected(x, y, x, y + 1))
            out.add(new int[] { x, y + 1 });
        return out;
    }

    private int[][] degreeByDoors(SkyBlockDungeon dungeon) {
        int[][] degree = new int[data.getWidth()][data.getHeight()];
        for (SkyBlockDungeon.DungeonDoor d : dungeon.getAllDoors()) {
            if (d.x1() >= 0 && d.x1() < data.getWidth() && d.y1() >= 0 && d.y1() < data.getHeight()) {
                degree[d.x1()][d.y1()]++;
            }
            if (d.x2() >= 0 && d.x2() < data.getWidth() && d.y2() >= 0 && d.y2() < data.getHeight()) {
                degree[d.x2()][d.y2()]++;
            }
        }
        return degree;
    }

    private void removeDoorBetween(SkyBlockDungeon dungeon, int x1, int y1, int x2, int y2) {
        ArrayList<SkyBlockDungeon.DungeonDoor> next = new ArrayList<>();
        for (SkyBlockDungeon.DungeonDoor d : dungeon.getAllDoors()) {
            boolean same = (d.x1() == x1 && d.y1() == y1 && d.x2() == x2 && d.y2() == y2)
                    || (d.x1() == x2 && d.y1() == y2 && d.x2() == x1 && d.y2() == y1);
            if (!same)
                next.add(d);
        }
        dungeon.setAllDoors(next);
    }

    public void assignCorridors(SkyBlockDungeon dungeon) {
        Random random = new Random();
        int corridorID = 1;
        boolean[][] visited = new boolean[data.getWidth()][data.getHeight()]; // Track visited rooms

        for (int y = 0; y < data.getHeight(); y++) {
            for (int x = 0; x < data.getWidth(); x++) {
                SkyBlockDungeon.DungeonRoom initialRoom = dungeon.getRoom(x, y);
                if (!visited[x][y] && initialRoom.getRoomType() == DungeonRoomType.BASE) {
                    List<int[]> potentialCorridorSet = new ArrayList<>();
                    Queue<int[]> queue = new LinkedList<>();
                    queue.offer(new int[] { x, y });
                    int currentStage = initialRoom.getStage(); // Ensure same stage for corridor consistency

                    while (!queue.isEmpty() && potentialCorridorSet.size() < 3) {
                        int[] current = queue.poll();
                        int curX = current[0], curY = current[1];

                        if (visited[curX][curY])
                            continue;
                        visited[curX][curY] = true;

                        SkyBlockDungeon.DungeonRoom currentRoom = dungeon.getRoom(curX, curY);
                        if (currentRoom.getRoomType() == DungeonRoomType.BASE
                                && currentRoom.getStage() == currentStage) {
                            potentialCorridorSet.add(current);

                            // Check adjacent base rooms of the same stage for potential inclusion
                            List<int[]> adjacents = DungeonUtilities.getAdjacentBaseRooms(curX, curY, dungeon, data);
                            for (int[] adj : adjacents) {
                                SkyBlockDungeon.DungeonRoom adjRoom = dungeon.getRoom(adj[0], adj[1]);
                                if (!visited[adj[0]][adj[1]] && adjRoom.getStage() == currentStage
                                        && random.nextBoolean()) {
                                    queue.offer(adj);
                                }
                            }
                        }
                    }

                    // Validate and assign corridor ID only if the set connects two or more rooms
                    if (potentialCorridorSet.size() > 1) {
                        for (int[] coords : potentialCorridorSet) {
                            SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(coords[0], coords[1]);
                            room.setCorridorNumber(corridorID); // Assign corridor ID
                            dungeon.setRoom(coords[0], coords[1], room);
                        }
                        corridorID++; // Increment corridor ID for the next valid set
                    }
                }
            }
        }
    }

    public enum GenerationStage {
        INITIALIZATION,
        CRITICAL_PATH,
        SIDE_PATHS,
        STRUCTURE_GENERATION,
        ROOM_ASSIGNMENT,
        CORRIDOR_ASSIGNMENT,
        FINAL_TOUCHES
    }
}
