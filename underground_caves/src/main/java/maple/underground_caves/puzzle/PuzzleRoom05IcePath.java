package maple.underground_caves.puzzle;

import maple.underground_caves.Test_dungeons;
import maple.underground_caves.dungeon.DungeonSecrets;
import maple.underground_caves.dungeon.StructureTemplate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PuzzleRoom05IcePath {
    private static final boolean DEBUG = false;
    private static final long RECENT_ROOM_CLEANUP_TICKS = 20L * 60L;
    private static final int BOARD_PADDING_Y = 1;
    private static final Material WALK_ICE = Material.ICE;
    private static final Material VISITED_ICE = Material.PACKED_ICE;
    private static final Material WALL_BLOCK = Material.WHITE_WOOL;
    private static final Material FLOOR_BLOCK = Material.STONE;
    private static final int RESET_DELAY_TICKS = 20 * 3;
    private static final double JUMP_MIN_DELTA_Y = 0.15D;
    private static final Material TEMPLATE_WALK_MARKER = Material.GLOWSTONE;
    private static final Material TEMPLATE_OBSTACLE_MARKER = Material.WHITE_WOOL;
    private static final Material TEMPLATE_START_MARKER = Material.DIAMOND_BLOCK;
    private static final Material TEMPLATE_END_MARKER = Material.REDSTONE_BLOCK;
    private static final Material TEMPLATE_END_MARKER_ALT = Material.RED_NETHER_BRICKS;
    private static final List<String> STAGE_ORDER = List.of("small", "medium", "large");

    private final Test_dungeons plugin;
    private final Map<String, LevelConfig> levels = new HashMap<>();
    private final Map<UUID, RoomState> activeByRoomId = new HashMap<>();
    private BukkitRunnable cleanupTask;

    public PuzzleRoom05IcePath(Test_dungeons plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    public void onEnable() {
        startCleanupTask();
    }

    public void onDisable() {
        stopCleanupTask();
        for (RoomState room : List.copyOf(activeByRoomId.values())) {
            if (room == null) continue;
            for (StageState stage : List.copyOf(room.stages.values())) {
                if (stage == null) continue;
                if (stage.resetTask != null) {
                    stage.resetTask.cancel();
                    stage.resetTask = null;
                }
            }
        }
        activeByRoomId.clear();
    }

    public void reloadConfig() {
        levels.clear();
        File file = configFile();
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection lvl = cfg.getConfigurationSection("levels");
        if (lvl == null) return;
        for (String key : lvl.getKeys(false)) {
            ConfigurationSection s = lvl.getConfigurationSection(key);
            if (s == null) continue;
            LevelConfig parsed = readLevel(s);
            if (parsed == null) continue;
            levels.put(key.toLowerCase(Locale.ROOT), parsed);
        }
    }

    public void registerPuzzleRoom05(World world, Location roomOrigin, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
        if (world == null || roomOrigin == null || rotation == null) return;
        try {
            Location base = roomOrigin.getBlock().getLocation();
            UUID roomId = UUID.randomUUID();
            RoomState room = new RoomState(roomId, world.getName(), rotation, base.getBlockX(), base.getBlockY(), base.getBlockZ(), sizeX, sizeZ);

            Map<String, StageState> detected = detectStagesFromRoomAllY(world, base, rotation, sizeX, sizeZ);
            StageState small = detected != null ? detected.get("small") : null;
            StageState medium = detected != null ? detected.get("medium") : null;
            StageState large = detected != null ? detected.get("large") : null;

            if (small == null || medium == null || large == null) {
                small = createStage(world, base, rotation, sizeX, sizeZ, "small");
                medium = createStage(world, base, rotation, sizeX, sizeZ, "medium");
                large = createStage(world, base, rotation, sizeX, sizeZ, "large");
            }

            if (small == null || medium == null || large == null) {
                warn("puzzle_room_05 skipped: failed to build stages origin=" + base.getBlockX() + "," + base.getBlockY() + "," + base.getBlockZ());
                return;
            }

            room.stages.put("small", small);
            room.stages.put("medium", medium);
            room.stages.put("large", large);

            small.unlocked = true;
            medium.unlocked = false;
            large.unlocked = false;

            applyStage(room, large, true);
            applyStage(room, medium, true);
            applyStage(room, small, true);

            activeByRoomId.put(roomId, room);
            info("puzzle_room_05 registered world=" + world.getName() + " origin=" + base.getBlockX() + "," + base.getBlockY() + "," + base.getBlockZ()
                    + " rotation=" + rotation.name() + " size=" + sizeX + "x" + sizeZ);
        } catch (Throwable t) {
            error("puzzle_room_05 register failed: " + t.getMessage(), t);
        }
    }

    public void handleMove(PlayerMoveEvent event) {
        if (event == null) return;
        Player player = event.getPlayer();
        if (player == null) return;
        Location to = event.getTo();
        if (to == null) return;
        Location from = event.getFrom();
        if (from != null && sameBlock(from, to) && Math.abs(to.getY() - from.getY()) < 0.001D) return;

        World world = to.getWorld();
        if (world == null) return;
        int bx = to.getBlockX();
        int by = to.getBlockY();
        int bz = to.getBlockZ();

        try {
            RoomStage hit = findRoomAndStage(world.getName(), bx, by, bz);
            if (hit == null) return;

            RoomState room = hit.room;
            StageState stage = hit.stage;
            int floorY = stage.board.floorY;
            if (by != floorY + 1) return;

            CellPos cell = new CellPos(bx, floorY, bz);
            if (stage.completed) return;
            if (stage.resetting) return;
            if (!stage.unlocked) return;

            if (from != null) {
                double dy = to.getY() - from.getY();
                if (dy > JUMP_MIN_DELTA_Y) {
                    Material beneath = world.getBlockAt(cell.x, cell.y, cell.z).getType();
                    if (stage.walkCells.contains(cell) && (beneath == WALK_ICE || beneath == VISITED_ICE)) {
                        failStage(room, stage, player);
                        return;
                    }
                }
            }

            if (stage.safeCells.contains(cell)) return;

            Material at = world.getBlockAt(cell.x, cell.y, cell.z).getType();
            if (at == VISITED_ICE) {
                failStage(room, stage, player);
                return;
            }

            if (!stage.walkCells.contains(cell)) return;
            if (at != WALK_ICE) return;
            if (!stage.targetCells.contains(cell)) return;

            if (stage.visited.add(cell)) {
                world.getBlockAt(cell.x, cell.y, cell.z).setType(VISITED_ICE, false);
                if (stage.visited.size() >= stage.requiredCount) {
                    completeStage(room, stage, player);
                } else if (DEBUG) {
                    player.sendActionBar(Component.text("§b" + stage.levelKey + " " + stage.visited.size() + "/" + stage.requiredCount));
                }
            }
        } catch (Throwable t) {
            error("puzzle_room_05 move handler failed: " + t.getMessage(), t);
        }
    }

    private void completeStage(RoomState room, StageState stage, Player who) {
        if (room == null || stage == null) return;
        if (stage.completed) return;
        stage.completed = true;
        room.completedCount++;
        stage.resetting = false;
        if (stage.resetTask != null) {
            stage.resetTask.cancel();
            stage.resetTask = null;
        }

        World world = plugin.getServer().getWorld(room.worldName);
        if (world != null && stage.board.end != null) {
            CellPos end = stage.board.end;
            world.getBlockAt(end.x, end.y, end.z).setType(Material.EMERALD_BLOCK, false);
        }

        if (who != null) {
            who.sendMessage(Component.text("已通关 " + stage.levelKey + " (" + room.completedCount + "/3)", NamedTextColor.GREEN));
            if (room.completedCount >= 3) {
                who.sendMessage(Component.text("已通关全部 3 关！", NamedTextColor.GOLD));
            }
        }

        if (room.completedCount >= 3 && !room.puzzleCompleted) {
            room.puzzleCompleted = true;
            DungeonSecrets secrets = plugin.getDungeonSecrets();
            if (secrets != null && world != null) {
                Location roomBase = new Location(world, room.baseX, room.baseY, room.baseZ);
                BlockPos wp = toWorldBlock(roomBase, 15, 22, 27, room.rotation, room.sizeX, room.sizeZ);
                Location tntLoc = new Location(world, wp.x() + 0.5, wp.y(), wp.z() + 0.5);
                secrets.markPuzzleCompleted(world, tntLoc);
                secrets.spawnSpecialTnt(world, tntLoc, 1, 16.0f);
            }
        }

        unlockNextStage(room, stage, who);
    }

    private void unlockNextStage(RoomState room, StageState completed, Player who) {
        if (room == null || completed == null) return;
        int idx = STAGE_ORDER.indexOf(completed.levelKey);
        if (idx < 0) return;
        if (idx + 1 >= STAGE_ORDER.size()) return;
        StageState next = room.stages.get(STAGE_ORDER.get(idx + 1));
        if (next == null) return;
        if (next.unlocked) return;
        next.unlocked = true;
        if (who != null) {
            who.sendMessage(Component.text("已解锁下一层：" + next.levelKey, NamedTextColor.AQUA));
        }
    }

    private void failStage(RoomState room, StageState stage, Player who) {
        if (room == null || stage == null) return;
        if (stage.completed) return;
        if (stage.resetting) return;
        stage.resetting = true;
        World world = plugin.getServer().getWorld(room.worldName);
        if (world == null) {
            stage.resetting = false;
            return;
        }

        if (who != null) {
            who.sendMessage(Component.text("冰面破裂！3 秒后重置该关卡：" + stage.levelKey, NamedTextColor.RED));
        }

        breakStageSurface(world, stage);

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (stage.completed) {
                        stage.resetting = false;
                        return;
                    }
                    applyStage(room, stage, false);
                    stage.resetting = false;
                } catch (Throwable t) {
                    stage.resetting = false;
                    error("puzzle_room_05 stage reset failed (" + stage.levelKey + "): " + t.getMessage(), t);
                }
            }
        };
        stage.resetTask = task;
        task.runTaskLater(plugin, RESET_DELAY_TICKS);
    }

    private void breakStageSurface(World world, StageState stage) {
        if (world == null || stage == null) return;
        int floorY = stage.board.floorY;
        for (CellPos wool : stage.woolCells) {
            if (wool == null) continue;
            CellPos floorCell = new CellPos(wool.x, floorY, wool.z);
            if (stage.safeCells.contains(floorCell)) continue;
            world.getBlockAt(wool.x, wool.y, wool.z).setType(Material.AIR, false);
            world.getBlockAt(wool.x, floorY, wool.z).setType(Material.AIR, false);
        }
        for (CellPos p : stage.walkCells) {
            if (p == null) continue;
            CellPos floorCell = new CellPos(p.x, floorY, p.z);
            if (stage.safeCells.contains(floorCell)) continue;
            world.getBlockAt(p.x, floorY, p.z).setType(Material.AIR, false);
            if (world.getBlockAt(p.x, floorY + 1, p.z).getType() == Material.WHITE_WOOL)
                world.getBlockAt(p.x, floorY + 1, p.z).setType(Material.AIR, false);
            if (world.getBlockAt(p.x, floorY + 2, p.z).getType() == Material.WHITE_WOOL)
                world.getBlockAt(p.x, floorY + 2, p.z).setType(Material.AIR, false);
        }
        stage.visited.clear();
    }

    private void applyStage(RoomState room, StageState stage, boolean initialCarve) {
        if (room == null || stage == null) return;
        World world = plugin.getServer().getWorld(room.worldName);
        if (world == null) return;

        BoardWorld board = stage.board;
        int minX = board.minX;
        int maxX = board.maxX;
        int minZ = board.minZ;
        int maxZ = board.maxZ;
        int floorY = board.floorY;

        if (stage.scanOnly) {
            if (!stage.scanCaptured) {
                stage.woolCells.clear();
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (world.getBlockAt(x, floorY + 1, z).getType() == Material.WHITE_WOOL) {
                            stage.woolCells.add(new CellPos(x, floorY + 1, z));
                        }
                        if (world.getBlockAt(x, floorY + 2, z).getType() == Material.WHITE_WOOL) {
                            stage.woolCells.add(new CellPos(x, floorY + 2, z));
                        }
                    }
                }
                stage.scanCaptured = true;
            }

            stage.safeCells.clear();
            if (board.start != null) stage.safeCells.add(board.start);
            if (board.end != null) stage.safeCells.add(board.end);
            stage.noCountCells.clear();
            stage.visited.clear();
            stage.targetCells.clear();

            for (CellPos p : stage.walkCells) {
                if (p == null) continue;
                world.getBlockAt(p.x, floorY, p.z).setType(WALK_ICE, false);
            }
            for (CellPos wool : stage.woolCells) {
                if (wool == null) continue;
                CellPos floorCell = new CellPos(wool.x, floorY, wool.z);
                if (stage.safeCells.contains(floorCell)) continue;
                world.getBlockAt(wool.x, wool.y, wool.z).setType(Material.WHITE_WOOL, false);
                world.getBlockAt(wool.x, floorY, wool.z).setType(WALK_ICE, false);
            }
            placeStartEnd(world, stage);

            for (CellPos p : stage.walkCells) {
                if (p == null) continue;
                CellPos cell = new CellPos(p.x, floorY, p.z);
                if (stage.safeCells.contains(cell)) continue;
                if (world.getBlockAt(cell.x, cell.y + 1, cell.z).getType() != Material.AIR) continue;
                stage.targetCells.add(cell);
            }
            stage.requiredCount = stage.targetCells.size();
            int localY = stage.board.floorY - room.baseY;
            info("puzzle_room_05 stage_targets level=" + stage.levelKey
                    + " floorY=" + stage.board.floorY
                    + " localY=" + localY
                    + " targets=" + stage.requiredCount
                    + " walk=" + stage.walkCells.size()
                    + " blocked=" + stage.blockedCells.size()
                    + " templateMode=" + stage.templateMode
                    + " scanOnly=" + stage.scanOnly);
            return;
        }

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                world.getBlockAt(x, floorY, z).setType(FLOOR_BLOCK, false);
            }
        }

        Set<CellPos> walk = new HashSet<>();
        if (stage.templateMode && !stage.passableCells.isEmpty()) {
            walk.addAll(stage.passableCells);
        } else {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    walk.add(new CellPos(x, floorY, z));
                }
            }
        }
        walk.removeAll(stage.blockedCells);

        Set<CellPos> noCount = computeNoCountCells(board, walk);
        stage.safeCells.clear();
        if (board.start != null) stage.safeCells.add(board.start);
        if (board.end != null) stage.safeCells.add(board.end);
        int required = 0;
        for (CellPos p : walk) {
            if (noCount.contains(p)) continue;
            required++;
        }

        stage.walkCells.clear();
        stage.walkCells.addAll(walk);
        stage.noCountCells.clear();
        stage.noCountCells.addAll(noCount);
        stage.visited.clear();
        stage.targetCells.clear();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                CellPos cell = new CellPos(x, floorY, z);
                if (walk.contains(cell)) continue;
                world.getBlockAt(x, floorY, z).setType(FLOOR_BLOCK, false);
            }
        }

        for (CellPos p : walk) {
            world.getBlockAt(p.x, p.y, p.z).setType(WALK_ICE, false);
        }
        placeStartEnd(world, stage);

        for (CellPos p : stage.walkCells) {
            if (stage.noCountCells.contains(p)) continue;
            if (stage.safeCells.contains(p)) continue;
            if (world.getBlockAt(p.x, p.y, p.z).getType() != WALK_ICE) continue;
            if (world.getBlockAt(p.x, p.y + 1, p.z).getType() != Material.AIR) continue;
            stage.targetCells.add(p);
        }
        stage.requiredCount = stage.targetCells.size();
        int localY = stage.board.floorY - room.baseY;
        info("puzzle_room_05 stage_targets level=" + stage.levelKey
                + " floorY=" + stage.board.floorY
                + " localY=" + localY
                + " targets=" + stage.requiredCount
                + " walk=" + stage.walkCells.size()
                + " blocked=" + stage.blockedCells.size()
                + " templateMode=" + stage.templateMode
                + " scanOnly=" + stage.scanOnly);
    }

    private Set<CellPos> computeNoCountCells(BoardWorld board, Set<CellPos> walk) {
        Set<CellPos> out = new HashSet<>();
        if (board == null || walk == null) return out;
        if (board.start != null) {
            out.add(board.start);
            out.addAll(sideCells(board, board.start, walk));
        }
        if (board.end != null) {
            out.add(board.end);
            out.addAll(sideCells(board, board.end, walk));
        }
        return out;
    }

    private Set<CellPos> sideCells(BoardWorld board, CellPos p, Set<CellPos> walk) {
        Set<CellPos> out = new HashSet<>();
        if (board == null || p == null) return out;
        int cx = (board.minX + board.maxX) / 2;
        int cz = (board.minZ + board.maxZ) / 2;
        int dx = cx - p.x;
        int dz = cz - p.z;
        boolean forwardAlongX = Math.abs(dx) >= Math.abs(dz);
        int y = board.floorY;
        if (forwardAlongX) {
            CellPos a = new CellPos(p.x, y, p.z + 1);
            CellPos b = new CellPos(p.x, y, p.z - 1);
            if (walk.contains(a)) out.add(a);
            if (walk.contains(b)) out.add(b);
        } else {
            CellPos a = new CellPos(p.x + 1, y, p.z);
            CellPos b = new CellPos(p.x - 1, y, p.z);
            if (walk.contains(a)) out.add(a);
            if (walk.contains(b)) out.add(b);
        }
        return out;
    }

    private BoardWorld toWorldBoard(World world, Location roomBase, StructureTemplate.Rotation rotation, int sizeX, int sizeZ,
                                   LevelConfig cfg) {
        if (world == null || roomBase == null || rotation == null || cfg == null) return null;
        BlockPos a = toWorldBlock(roomBase, cfg.boardMin.x(), cfg.boardMin.y(), cfg.boardMin.z(), rotation, sizeX, sizeZ);
        BlockPos b = toWorldBlock(roomBase, cfg.boardMax.x(), cfg.boardMax.y(), cfg.boardMax.z(), rotation, sizeX, sizeZ);
        int minX = Math.min(a.x(), b.x());
        int maxX = Math.max(a.x(), b.x());
        int minZ = Math.min(a.z(), b.z());
        int maxZ = Math.max(a.z(), b.z());
        int floorY = roomBase.getBlockY() + cfg.boardMin.y();

        CellPos start = null;
        CellPos end = null;
        if (cfg.start != null) {
            BlockPos s = toWorldBlock(roomBase, cfg.start.x(), cfg.start.y(), cfg.start.z(), rotation, sizeX, sizeZ);
            start = new CellPos(s.x(), s.y(), s.z());
        }
        if (cfg.end != null) {
            BlockPos e = toWorldBlock(roomBase, cfg.end.x(), cfg.end.y(), cfg.end.z(), rotation, sizeX, sizeZ);
            end = new CellPos(e.x(), e.y(), e.z());
        }
        if (start == null || end == null) return null;
        if (start.y != floorY) start = new CellPos(start.x, floorY, start.z);
        if (end.y != floorY) end = new CellPos(end.x, floorY, end.z);
        return new BoardWorld(minX, maxX, minZ, maxZ, floorY, start, end);
    }

    private static boolean sameBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        if (a.getWorld() != b.getWorld()) return false;
        return a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }

    private void startCleanupTask() {
        if (plugin == null || !plugin.isEnabled()) return;
        if (cleanupTask != null) cleanupTask.cancel();
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (RoomState s : List.copyOf(activeByRoomId.values())) {
                    if (s == null) continue;
                    s.ageTicks += RECENT_ROOM_CLEANUP_TICKS;
                    if (s.ageTicks < RECENT_ROOM_CLEANUP_TICKS * 10) continue;
                    activeByRoomId.remove(s.roomId);
                }
            }
        };
        cleanupTask.runTaskTimer(plugin, RECENT_ROOM_CLEANUP_TICKS, RECENT_ROOM_CLEANUP_TICKS);
    }

    private void stopCleanupTask() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
    }

    private File configFile() {
        File dir = plugin.getDataFolder();
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "puzzle_room_05.yml");
    }

    private LevelConfig readLevel(ConfigurationSection section) {
        if (section == null) return null;
        CellPosLocal b1 = readPos(section.getConfigurationSection("boardMin"));
        CellPosLocal b2 = readPos(section.getConfigurationSection("boardMax"));
        CellPosLocal s = readPos(section.getConfigurationSection("start"));
        CellPosLocal e = readPos(section.getConfigurationSection("end"));
        if (b1 == null || b2 == null || s == null || e == null) return null;
        int minX = Math.min(b1.x, b2.x);
        int maxX = Math.max(b1.x, b2.x);
        int minZ = Math.min(b1.z, b2.z);
        int maxZ = Math.max(b1.z, b2.z);
        int y = b1.y;
        return new LevelConfig(new CellPosLocal(minX, y, minZ), new CellPosLocal(maxX, y, maxZ), s, e);
    }

    private CellPosLocal readPos(ConfigurationSection s) {
        if (s == null) return null;
        Integer x = s.getInt("x");
        Integer y = s.getInt("y");
        Integer z = s.getInt("z");
        return new CellPosLocal(x, y, z);
    }

    public static boolean saveLevelConfig(Test_dungeons plugin, String levelKey,
                                  Location roomOrigin, Location boardPos1, Location boardPos2,
                                  Location start, Location end) {
        if (plugin == null) return false;
        if (levelKey == null || levelKey.isBlank()) return false;
        if (roomOrigin == null || boardPos1 == null || boardPos2 == null || start == null || end == null) return false;
        if (roomOrigin.getWorld() == null) return false;
        World w = roomOrigin.getWorld();
        if (!w.equals(boardPos1.getWorld()) || !w.equals(boardPos2.getWorld()) || !w.equals(start.getWorld()) || !w.equals(end.getWorld()))
            return false;

        int ox = roomOrigin.getBlockX();
        int oy = roomOrigin.getBlockY();
        int oz = roomOrigin.getBlockZ();

        int minX = Math.min(boardPos1.getBlockX(), boardPos2.getBlockX()) - ox;
        int maxX = Math.max(boardPos1.getBlockX(), boardPos2.getBlockX()) - ox;
        int minZ = Math.min(boardPos1.getBlockZ(), boardPos2.getBlockZ()) - oz;
        int maxZ = Math.max(boardPos1.getBlockZ(), boardPos2.getBlockZ()) - oz;
        int y = boardPos1.getBlockY() - oy;
        int sx = start.getBlockX() - ox;
        int sy = start.getBlockY() - oy;
        int sz = start.getBlockZ() - oz;
        int ex = end.getBlockX() - ox;
        int ey = end.getBlockY() - oy;
        int ez = end.getBlockZ() - oz;

        File dir = plugin.getDataFolder();
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "puzzle_room_05.yml");
        YamlConfiguration cfg = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        String key = levelKey.toLowerCase(Locale.ROOT);
        cfg.set("version", 1);
        String base = "levels." + key + ".";
        cfg.set(base + "boardMin.x", minX);
        cfg.set(base + "boardMin.y", y);
        cfg.set(base + "boardMin.z", minZ);
        cfg.set(base + "boardMax.x", maxX);
        cfg.set(base + "boardMax.y", y);
        cfg.set(base + "boardMax.z", maxZ);
        cfg.set(base + "start.x", sx);
        cfg.set(base + "start.y", sy);
        cfg.set(base + "start.z", sz);
        cfg.set(base + "end.x", ex);
        cfg.set(base + "end.y", ey);
        cfg.set(base + "end.z", ez);
        try {
            cfg.save(file);
            return true;
        } catch (IOException e2) {
            return false;
        }
    }

    private void debug(String message) {
    }

    private void info(String message) {
    }

    private void warn(String message) {
    }

    private void error(String message, Throwable t) {
    }

    private StageState createStage(World world, Location roomBase, StructureTemplate.Rotation rotation, int sizeX, int sizeZ, String levelKey) {
        if (world == null || roomBase == null || rotation == null || levelKey == null) return null;
        String key = levelKey.toLowerCase(Locale.ROOT);
        LevelConfig cfg = levels.get(key);
        if (cfg == null) {
            warn("puzzle_room_05 stage missing: level=" + key + " reason=no_config");
            return null;
        }

        BoardWorld board = toWorldBoard(world, roomBase, rotation, sizeX, sizeZ, cfg);
        if (board == null) {
            warn("puzzle_room_05 stage missing: level=" + key + " reason=config_toWorldBoard_failed");
            return null;
        }

        TemplateScan scan = scanTemplateMarkers(world, board);
        if (scan == null) {
            StageState stage = new StageState(key, board);
            stage.buildSource = "config";
            info(stageLogLine("stage_config", stage, sizeX, sizeZ, rotation));
            return stage;
        }

        CellPos start = scan.start != null ? scan.start : board.start;
        CellPos end = scan.end != null ? scan.end : board.end;
        BoardWorld detected = new BoardWorld(board.minX, board.maxX, board.minZ, board.maxZ, board.floorY, start, end);
        StageState stage = new StageState(key, detected);
        stage.templateMode = true;
        stage.passableCells.addAll(scan.passableCells);
        stage.blockedCells.addAll(scan.blockedCells);
        if (start != null) stage.passableCells.add(start);
        if (end != null) stage.passableCells.add(end);
        stage.passableCells.removeAll(stage.blockedCells);
        stage.buildSource = "config+markers";
        info(stageLogLine("stage_config_markers", stage, sizeX, sizeZ, rotation));
        return stage;
    }

    private Map<String, StageState> detectStagesFromRoomAllY(World world, Location roomBase, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
        if (world == null || roomBase == null || rotation == null) return null;
        if (sizeX <= 0 || sizeZ <= 0) return null;

        RoomDetectedStage a = detectStageCandidateAtLocalY(world, roomBase, rotation, sizeX, sizeZ, 14);
        RoomDetectedStage b = detectStageCandidateAtLocalY(world, roomBase, rotation, sizeX, sizeZ, 15);
        RoomDetectedStage c = detectStageCandidateAtLocalY(world, roomBase, rotation, sizeX, sizeZ, 16);

        if (a == null || b == null || c == null) {
            List<RoomDetectedStage> candidates = new ArrayList<>();
            for (int localY = -64; localY <= 64; localY++) {
                RoomDetectedStage cand = detectStageCandidateAtLocalY(world, roomBase, rotation, sizeX, sizeZ, localY);
                if (cand == null) continue;
                candidates.add(cand);
            }

            candidates.sort(Comparator.comparingInt(RoomDetectedStage::floorY));
            List<RoomDetectedStage> distinct = new ArrayList<>();
            Integer lastY = null;
            for (RoomDetectedStage cand : candidates) {
                if (cand == null) continue;
                if (lastY != null && cand.floorY() == lastY) continue;
                distinct.add(cand);
                lastY = cand.floorY();
            }

            if (distinct.size() < 3) {
                info("puzzle_room_05 stage_room_scan insufficient_layers"
                        + " expected14=" + (a != null)
                        + " expected15=" + (b != null)
                        + " expected16=" + (c != null)
                        + " candidates=" + distinct.size()
                        + " floors=" + floorsList(distinct)
                        + " templateSize=" + sizeX + "x" + sizeZ
                        + " rotation=" + rotation.name()
                        + " origin=" + roomBase.getBlockX() + "," + roomBase.getBlockY() + "," + roomBase.getBlockZ());
                return null;
            }

            a = distinct.get(0);
            b = distinct.get(1);
            c = distinct.get(2);

            info("puzzle_room_05 stage_room_scan fallback_layers"
                    + " floors=" + floorsList(distinct)
                    + " picked=" + a.floorY() + "," + b.floorY() + "," + c.floorY()
                    + " templateSize=" + sizeX + "x" + sizeZ
                    + " rotation=" + rotation.name()
                    + " origin=" + roomBase.getBlockX() + "," + roomBase.getBlockY() + "," + roomBase.getBlockZ());
        }

        StageState small = stageFromCandidate("small", a);
        StageState medium = stageFromCandidate("medium", b);
        StageState large = stageFromCandidate("large", c);

        info(stageLogLine("stage_room_small", small, sizeX, sizeZ, rotation));
        info(stageLogLine("stage_room_medium", medium, sizeX, sizeZ, rotation));
        info(stageLogLine("stage_room_large", large, sizeX, sizeZ, rotation));

        Map<String, StageState> out = new HashMap<>();
        out.put("small", small);
        out.put("medium", medium);
        out.put("large", large);
        return out;
    }

    private String floorsList(List<RoomDetectedStage> candidates) {
        if (candidates == null || candidates.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        int count = 0;
        for (RoomDetectedStage c : candidates) {
            if (c == null) continue;
            if (count++ > 0) sb.append(",");
            sb.append(c.floorY());
        }
        sb.append("]");
        return sb.toString();
    }

    private StageState stageFromCandidate(String levelKey, RoomDetectedStage c) {
        if (c == null || levelKey == null) return null;
        BoardWorld board = new BoardWorld(c.minX(), c.maxX(), c.minZ(), c.maxZ(), c.floorY(), c.start(), c.end());
        StageState stage = new StageState(levelKey.toLowerCase(Locale.ROOT), board);
        stage.templateMode = false;
        stage.scanOnly = true;
        stage.passableCells.addAll(c.passable());
        stage.blockedCells.addAll(c.blocked());
        stage.walkCells.addAll(c.passable());
        stage.buildSource = "room_scan";
        return stage;
    }

    private RoomDetectedStage detectStageCandidateAtLocalY(World world, Location roomBase, StructureTemplate.Rotation rotation, int sizeX, int sizeZ, int localY) {
        if (world == null || roomBase == null || rotation == null) return null;
        if (sizeX <= 0 || sizeZ <= 0) return null;

        int floorY = roomBase.getBlockY() + localY;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        Set<CellPos> passable = new HashSet<>();
        Set<CellPos> blocked = new HashSet<>();
        CellPos start = null;
        CellPos end = null;
        boolean any = false;

        for (int lx = 0; lx < sizeX; lx++) {
            for (int lz = 0; lz < sizeZ; lz++) {
                BlockPos wp = toWorldBlock(roomBase, lx, localY, lz, rotation, sizeX, sizeZ);
                Material floor = world.getBlockAt(wp.x(), floorY, wp.z()).getType();
                Material top = world.getBlockAt(wp.x(), floorY + 1, wp.z()).getType();

                boolean isWalk = floor == TEMPLATE_WALK_MARKER || floor == WALK_ICE || floor == VISITED_ICE;
                boolean isBlocked = floor == TEMPLATE_OBSTACLE_MARKER || top == TEMPLATE_OBSTACLE_MARKER;
                boolean isStart = isStartMarker(floor) || isStartMarker(top);
                boolean isEnd = isEndMarker(floor) || isEndMarker(top);

                if (!(isWalk || isBlocked || isStart || isEnd)) continue;
                any = true;

                minX = Math.min(minX, wp.x());
                maxX = Math.max(maxX, wp.x());
                minZ = Math.min(minZ, wp.z());
                maxZ = Math.max(maxZ, wp.z());

                CellPos cell = new CellPos(wp.x(), floorY, wp.z());
                if (isWalk) passable.add(cell);
                if (isBlocked) blocked.add(cell);
                if (isStart) {
                    start = cell;
                    passable.add(cell);
                }
                if (isEnd) {
                    end = cell;
                    passable.add(cell);
                }
            }
        }

        if (!any) return null;
        boolean hasAnchors = start != null && end != null;
        if (passable.isEmpty() && hasAnchors) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    passable.add(new CellPos(x, floorY, z));
                }
            }
        }
        if (passable.isEmpty()) return null;
        passable.removeAll(blocked);

        return new RoomDetectedStage(minX, maxX, minZ, maxZ, floorY, start, end, passable, blocked);
    }

    private String stageLogLine(String prefix, StageState stage, int sizeX, int sizeZ, StructureTemplate.Rotation rotation) {
        if (stage == null || stage.board == null) return "puzzle_room_05 " + prefix + " stage=null";
        BoardWorld b = stage.board;
        String start = b.start != null ? (b.start.x + "," + b.start.y + "," + b.start.z) : "null";
        String end = b.end != null ? (b.end.x + "," + b.end.y + "," + b.end.z) : "null";
        return "puzzle_room_05 " + prefix
                + " level=" + stage.levelKey
                + " source=" + stage.buildSource
                + " bounds=(" + b.minX + "," + b.floorY + "," + b.minZ + ")-(" + b.maxX + "," + b.floorY + "," + b.maxZ + ")"
                + " start=" + start
                + " end=" + end
                + " passable=" + stage.passableCells.size()
                + " blocked=" + stage.blockedCells.size()
                + " templateSize=" + sizeX + "x" + sizeZ
                + " rotation=" + (rotation != null ? rotation.name() : "null");
    }

    private void placeStartEnd(World world, StageState stage) {
        if (world == null || stage == null) return;
        if (stage.board.start != null) {
            CellPos s = stage.board.start;
            world.getBlockAt(s.x, s.y, s.z).setType(WALK_ICE, false);
        }
        if (stage.board.end != null) {
            CellPos e = stage.board.end;
            if (stage.completed) {
                world.getBlockAt(e.x, e.y, e.z).setType(Material.EMERALD_BLOCK, false);
            } else {
                world.getBlockAt(e.x, e.y, e.z).setType(WALK_ICE, false);
            }
        }
    }

    private TemplateScan scanTemplateMarkers(World world, BoardWorld board) {
        if (world == null || board == null) return null;
        int y = board.floorY;
        Set<CellPos> passable = new HashSet<>();
        Set<CellPos> blocked = new HashSet<>();
        CellPos start = null;
        CellPos end = null;
        boolean any = false;

        for (int x = board.minX; x <= board.maxX; x++) {
            for (int z = board.minZ; z <= board.maxZ; z++) {
                Material floor = world.getBlockAt(x, y, z).getType();
                Material top = world.getBlockAt(x, y + 1, z).getType();
                CellPos cell = new CellPos(x, y, z);

                if (floor == TEMPLATE_WALK_MARKER) {
                    passable.add(cell);
                    any = true;
                }

                if (floor == TEMPLATE_OBSTACLE_MARKER || top == TEMPLATE_OBSTACLE_MARKER) {
                    blocked.add(cell);
                    any = true;
                }

                if (isStartMarker(floor) || isStartMarker(top)) {
                    start = cell;
                    passable.add(cell);
                    any = true;
                }

                if (isEndMarker(floor) || isEndMarker(top)) {
                    end = cell;
                    passable.add(cell);
                    any = true;
                }
            }
        }

        if (!any) return null;
        if (passable.isEmpty()) {
            for (int x = board.minX; x <= board.maxX; x++) {
                for (int z = board.minZ; z <= board.maxZ; z++) {
                    passable.add(new CellPos(x, y, z));
                }
            }
        }
        passable.removeAll(blocked);
        return new TemplateScan(passable, blocked, start, end);
    }

    private boolean isStartMarker(Material m) {
        if (m == null) return false;
        return m == TEMPLATE_START_MARKER;
    }

    private boolean isEndMarker(Material m) {
        if (m == null) return false;
        return m == TEMPLATE_END_MARKER || m == TEMPLATE_END_MARKER_ALT;
    }

    private RoomStage findRoomAndStage(String worldName, int x, int y, int z) {
        if (worldName == null) return null;
        for (RoomState r : List.copyOf(activeByRoomId.values())) {
            if (r == null) continue;
            if (!worldName.equals(r.worldName)) continue;
            for (StageState s : r.stages.values()) {
                if (s == null) continue;
                if (s.board.containsXZ(x, z) && y == s.board.floorY + 1) {
                    return new RoomStage(r, s);
                }
            }
        }
        for (RoomState r : List.copyOf(activeByRoomId.values())) {
            if (r == null) continue;
            if (!worldName.equals(r.worldName)) continue;
            for (StageState s : r.stages.values()) {
                if (s == null) continue;
                if (s.board.containsXZ(x, z) && y == s.board.floorY) {
                    return new RoomStage(r, s);
                }
            }
        }
        return null;
    }

    private static BlockPos toWorldBlock(Location base, int localX, int localY, int localZ,
                                        StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
        int[] rotated = rotateLocalXZ(localX, localZ, rotation, sizeX, sizeZ);
        int wx = base.getBlockX() + rotated[0];
        int wy = base.getBlockY() + localY;
        int wz = base.getBlockZ() + rotated[1];
        return new BlockPos(wx, wy, wz);
    }

    private static int[] rotateLocalXZ(int x, int z, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
        return switch (rotation) {
            case R0 -> new int[]{x, z};
            case R90 -> new int[]{z, sizeX - 1 - x};
            case R180 -> new int[]{sizeX - 1 - x, sizeZ - 1 - z};
            case R270 -> new int[]{sizeZ - 1 - z, x};
        };
    }

    private record BlockPos(int x, int y, int z) {}

    private record CellPos(int x, int y, int z) {}

    private record CellPosLocal(int x, int y, int z) {}

    private record LevelConfig(CellPosLocal boardMin, CellPosLocal boardMax, CellPosLocal start, CellPosLocal end) {}

    private static final class BoardWorld {
        private final int minX;
        private final int maxX;
        private final int minZ;
        private final int maxZ;
        private final int floorY;
        private final CellPos start;
        private final CellPos end;

        private BoardWorld(int minX, int maxX, int minZ, int maxZ, int floorY, CellPos start, CellPos end) {
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.floorY = floorY;
            this.start = start;
            this.end = end;
        }

        private boolean containsXZ(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    private static final class RoomState {
        private final UUID roomId;
        private final String worldName;
        private final StructureTemplate.Rotation rotation;
        private final int baseX;
        private final int baseY;
        private final int baseZ;
        private final int sizeX;
        private final int sizeZ;
        private final Map<String, StageState> stages = new HashMap<>();
        private int completedCount;
        private boolean puzzleCompleted;
        private long ageTicks;

        private RoomState(UUID roomId, String worldName, StructureTemplate.Rotation rotation, int baseX, int baseY, int baseZ, int sizeX, int sizeZ) {
            this.roomId = roomId;
            this.worldName = worldName;
            this.rotation = rotation;
            this.baseX = baseX;
            this.baseY = baseY;
            this.baseZ = baseZ;
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
        }
    }

    private static final class StageState {
        private final String levelKey;
        private final BoardWorld board;
        private final Set<CellPos> passableCells = new HashSet<>();
        private final Set<CellPos> blockedCells = new HashSet<>();
        private final Set<CellPos> walkCells = new HashSet<>();
        private final Set<CellPos> safeCells = new HashSet<>();
        private final Set<CellPos> noCountCells = new HashSet<>();
        private final Set<CellPos> targetCells = new HashSet<>();
        private final Set<CellPos> woolCells = new HashSet<>();
        private final Set<CellPos> visited = new HashSet<>();
        private int requiredCount;
        private boolean unlocked;
        private boolean completed;
        private boolean resetting;
        private boolean templateMode;
        private boolean scanOnly;
        private boolean scanCaptured;
        private BukkitRunnable resetTask;
        private String buildSource;

        private StageState(String levelKey, BoardWorld board) {
            this.levelKey = levelKey;
            this.board = board;
        }
    }

    private record RoomDetectedStage(int minX, int maxX, int minZ, int maxZ, int floorY,
                                     CellPos start, CellPos end,
                                     Set<CellPos> passable, Set<CellPos> blocked) {}

    private record TemplateScan(Set<CellPos> passableCells, Set<CellPos> blockedCells, CellPos start, CellPos end) {}

    private record RoomStage(RoomState room, StageState stage) {}
}
