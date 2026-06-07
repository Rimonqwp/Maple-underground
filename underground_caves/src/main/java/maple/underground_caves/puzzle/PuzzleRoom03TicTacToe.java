package maple.underground_caves.puzzle;

import maple.underground_caves.Test_dungeons;
import maple.underground_caves.dungeon.DungeonSecrets;
import maple.underground_caves.dungeon.StructureTemplate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.scheduler.BukkitRunnable;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.NamespacedKey;

@SuppressWarnings("ALL")
public final class PuzzleRoom03TicTacToe {
    private static final boolean DEBUG = false;
    private static final int STAGES_TOTAL = 3;
    private static final int SCAN_EXTRA_Y_MIN = -8;
    private static final int SCAN_EXTRA_Y_MAX = 40;

    private final Test_dungeons plugin;
    private final NamespacedKey frameKey;
    private final Map<String, RoomState> roomsByKey = new HashMap<>();

    public PuzzleRoom03TicTacToe(Test_dungeons plugin) {
        this.plugin = plugin;
        this.frameKey = plugin != null ? new NamespacedKey(plugin, "puzzle_room_03_frame") : null;
    }

    public boolean isPuzzleRoom03Frame(Entity entity) {
        if (!(entity instanceof ItemFrame frame)) return false;
        if (frameKey == null) return false;
        Byte b = frame.getPersistentDataContainer().get(frameKey, PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }

    private void debug(String message) {
    }

    public void registerPuzzleRoom03(World world, Location roomOrigin, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
        if (world == null || roomOrigin == null || rotation == null) return;
        Location base = roomOrigin.getBlock().getLocation();
        String roomKey = world.getName() + ":" + base.getBlockX() + ":" + base.getBlockY() + ":" + base.getBlockZ();
        if (roomsByKey.containsKey(roomKey)) return;

        debug("puzzle_room_03 register begin origin=" + base.getBlockX() + "," + base.getBlockY() + "," + base.getBlockZ()
                + " rotation=" + rotation.name() + " size=" + sizeX + "x" + sizeZ);
        RoomState state = scanRoom(world, base, rotation, sizeX, sizeZ, roomKey);
        if (state == null) {
            return;
        }

        roomsByKey.put(roomKey, state);
        startStage(state, null);
    }

    public boolean handleInteract(PlayerInteractEvent event) {
        if (event == null) return false;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return false;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return false;
        Player player = event.getPlayer();
        if (player == null) return false;

        BlockData data = clicked.getBlockData();
        if (!(data instanceof Switch)) return false;

        World world = clicked.getWorld();
        if (world == null) return false;

        long key = packBlock(clicked.getX(), clicked.getY(), clicked.getZ());
        RoomState room = findRoomByButton(world.getName(), key);
        if (room == null) return false;

        Integer cellIndex = room.cellIndexByButton.get(key);
        if (cellIndex == null) return false;

        debug("puzzle_room_03 click button=" + clicked.getX() + "," + clicked.getY() + "," + clicked.getZ()
                + " cell=" + cellIndex
                + " stage=" + room.stage
                + " completed=" + room.completed
                + " transitioning=" + room.transitioning);

        event.setCancelled(true);

        if (room.completed) return true;
        if (room.transitioning) return true;
        if (room.board[cellIndex] != ' ') {
            clearButton(room, cellIndex);
            return true;
        }

        room.board[cellIndex] = 'O';
        room.lastMoveBy = player.getUniqueId();
        clearButton(room, cellIndex);
        updateMaps(room);
        pushMapsToPlayer(room, player);

        GameResult afterPlayer = evaluate(room.board);
        if (afterPlayer != GameResult.ONGOING) {
            endStageIfNeeded(room, player, afterPlayer);
            return true;
        }

        room.transitioning = true;
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (room.completed) return;
                    int sys = pickSystemMove(room.board);
                    if (sys >= 0 && sys < 9 && room.board[sys] == ' ') {
                        room.board[sys] = 'X';
                        clearButton(room, sys);
                        updateMaps(room);
                        pushMapsToPlayer(room, player);
                    }
                    GameResult afterSys = evaluate(room.board);
                    endStageIfNeeded(room, player, afterSys);
                } finally {
                    if (!room.completed && !room.pendingReset) {
                        room.transitioning = false;
                    }
                }
            }
        }.runTaskLater(plugin, 30L);

        return true;
    }

    private void endStageIfNeeded(RoomState room, Player player, GameResult result) {
        if (room == null) return;
        if (result == GameResult.ONGOING) {
            room.transitioning = false;
            return;
        }

        room.pendingReset = true;
        room.transitioning = true;

        boolean win = result == GameResult.O_WIN || result == GameResult.DRAW;
        boolean lose = result == GameResult.X_WIN;

        if (player != null) {
            if (win) {
                player.sendMessage(Component.text("已通關！", NamedTextColor.GREEN));
            } else if (lose) {
                player.sendMessage(Component.text("解謎失敗！", NamedTextColor.RED));
            }
        }

        if (win) {
            finishRoom(room);
            return;
        }

        if (lose) {
            room.completed = true;
            World world = Bukkit.getWorld(room.worldName);
            if (world != null) {
                DungeonSecrets secrets = plugin.getDungeonSecrets();
                if (secrets != null) {
                    secrets.markPuzzleFailed(world, new Location(world, room.baseX, room.baseY, room.baseZ));
                }
            }
            room.pendingReset = false;
            room.transitioning = false;
        }
    }

    private void startStage(RoomState room, Player player) {
        if (room == null) return;
        debug("puzzle_room_03 stage start room=" + room.roomKey + " stage=" + room.stage);
        restoreButtons(room);
        for (int i = 0; i < room.board.length; i++) room.board[i] = ' ';
        room.board[4] = 'X';
        clearButton(room, 4);
        room.transitioning = false;
        room.pendingReset = false;
        updateMaps(room);
        pushMapsToPlayer(room, player);
    }

    private void finishRoom(RoomState room) {
        if (room == null) return;
        if (room.completed) return;
        room.completed = true;
        room.transitioning = true;
        debug("puzzle_room_03 completed room=" + room.roomKey);
        World world = Bukkit.getWorld(room.worldName);
        if (world != null) {
            DungeonSecrets secrets = plugin.getDungeonSecrets();
            if (secrets != null) {
                secrets.markPuzzleCompleted(world, new Location(world, room.baseX, room.baseY, room.baseZ));
                Location spawnAt = pickCenterTntLocation(world, room);
                if (spawnAt != null) {
                    secrets.spawnSpecialTnt(world, spawnAt, 1);
                }
            }
        }
        cleanupMaps(room);
    }

    private void cleanupMaps(RoomState room) {
        if (room == null) return;
        World world = Bukkit.getWorld(room.worldName);
        for (UUID id : List.copyOf(room.frames)) {
            if (id == null) continue;
            if (world == null) continue;
            Entity e = world.getEntity(id);
            if (e != null) e.remove();
        }
        room.frames.clear();
        room.views.clear();
    }

    private Location pickCenterTntLocation(World world, RoomState room) {
        if (world == null || room == null) return null;
        int idx = 4;
        int x = room.buttonX[idx];
        int y = room.buttonY[idx];
        int z = room.buttonZ[idx];
        for (int dy = 1; dy <= 3; dy++) {
            int yy = y + dy;
            if (yy < world.getMinHeight() || yy > world.getMaxHeight() - 1) continue;
            if (!world.getBlockAt(x, yy, z).getType().isAir()) continue;
            return new Location(world, x + 0.5, yy + 0.5, z + 0.5);
        }
        return new Location(world, x + 0.5, y + 1.0, z + 0.5);
    }

    private void updateMaps(RoomState room) {
        if (room == null) return;
        for (int i = 0; i < room.views.size(); i++) {
            MapView v = room.views.get(i);
            if (v == null) continue;
            for (var r : List.copyOf(v.getRenderers())) {
                if (r instanceof CellRenderer cr) {
                    cr.markDirty();
                }
            }
        }
    }

    private void pushMapsToPlayer(RoomState room, Player player) {
        if (room == null || player == null) return;
        if (player.getWorld() == null) return;
        if (!player.getWorld().getName().equals(room.worldName)) return;
        for (int i = 0; i < room.views.size(); i++) {
            MapView v = room.views.get(i);
            if (v == null) continue;
            player.sendMap(v);
        }
    }

    /** 井字棋 AI：X 方，採用最佳策略（先贏、再擋、中心、角落、邊） */
    private int pickSystemMove(char[] board) {
        if (board == null || board.length != 9) return -1;
        int win = findWinningMove(board, 'X');
        if (win >= 0) return win;
        int block = findWinningMove(board, 'O');
        if (block >= 0) return block;
        if (board[4] == ' ') return 4;
        int[] corners = {0, 2, 6, 8};
        for (int c : corners) {
            if (board[c] == ' ') return c;
        }
        int[] sides = {1, 3, 5, 7};
        for (int s : sides) {
            if (board[s] == ' ') return s;
        }
        return -1;
    }

    private int findWinningMove(char[] board, char player) {
        if (board == null || board.length != 9) return -1;
        int[][] lines = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, {0, 4, 8}, {2, 4, 6}};
        for (int[] ln : lines) {
            int empty = -1;
            int count = 0;
            for (int i = 0; i < 3; i++) {
                if (board[ln[i]] == player) count++;
                else if (board[ln[i]] == ' ') empty = ln[i];
            }
            if (count == 2 && empty >= 0) return empty;
        }
        return -1;
    }

    private GameResult evaluate(char[] b) {
        if (b == null || b.length != 9) return GameResult.ONGOING;
        int[][] lines = new int[][]{
                {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
                {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
                {0, 4, 8}, {2, 4, 6}
        };
        for (int[] ln : lines) {
            char a = b[ln[0]];
            if (a == ' ') continue;
            if (a == b[ln[1]] && a == b[ln[2]]) {
                return a == 'X' ? GameResult.X_WIN : GameResult.O_WIN;
            }
        }
        for (char c : b) if (c == ' ') return GameResult.ONGOING;
        return GameResult.DRAW;
    }

    private RoomState findRoomByButton(String worldName, long buttonKey) {
        if (worldName == null) return null;
        for (RoomState r : roomsByKey.values()) {
            if (r == null) continue;
            if (!worldName.equals(r.worldName)) continue;
            if (r.cellIndexByButton.containsKey(buttonKey)) return r;
        }
        return null;
    }

    private RoomState scanRoom(World world, Location base, StructureTemplate.Rotation rotation, int sizeX, int sizeZ, String roomKey) {
        int minX = base.getBlockX();
        int minY = base.getBlockY() + SCAN_EXTRA_Y_MIN;
        int minZ = base.getBlockZ();
        int maxX = base.getBlockX() + Math.max(1, sizeX) - 1;
        int maxY = base.getBlockY() + SCAN_EXTRA_Y_MAX;
        int maxZ = base.getBlockZ() + Math.max(1, sizeZ) - 1;
        minY = Math.max(world.getMinHeight(), minY);
        maxY = Math.min(world.getMaxHeight() - 1, maxY);

        debug("puzzle_room_03 scan bounds=(" + minX + "," + minY + "," + minZ + ")-(" + maxX + "," + maxY + "," + maxZ + ") roomKey=" + roomKey);

        List<ButtonRef> buttons = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (b == null) continue;
                    BlockData data = b.getBlockData();
                    if (!(data instanceof Switch sw)) continue;
                    Material m = b.getType();
                    String n = m != null ? m.name().toLowerCase(Locale.ROOT) : "";
                    if (!n.endsWith("_button")) continue;
                    BlockFace facing = sw.getFacing();
                    if (facing == null || facing == BlockFace.SELF) continue;
                    buttons.add(new ButtonRef(x, y, z, facing));
                }
            }
        }

        if (DEBUG) {
            Map<BlockFace, Integer> counts = new HashMap<>();
            for (ButtonRef br : buttons) {
                if (br == null) continue;
                counts.merge(br.facing, 1, Integer::sum);
            }
            debug("puzzle_room_03 scan buttonsFound=" + buttons.size() + " byFacing=" + counts);
        }

        GridPick pick = pickGrid(buttons);
        if (pick == null) return null;

        RoomState room = new RoomState(roomKey, world.getName(), base.getBlockX(), base.getBlockY(), base.getBlockZ(), rotation, sizeX, sizeZ);
        room.facing = pick.facing;
        room.stage = 1;

        List<ButtonRef> grid = pick.cells;
        Set<Integer> a1Set = new HashSet<>();
        Set<Integer> a2Set = new HashSet<>();
        for (ButtonRef br : grid) {
            if (br == null) continue;
            a1Set.add(br.a1);
            a2Set.add(br.a2);
        }
        if (a1Set.size() != 3 || a2Set.size() != 3) {
            debug("puzzle_room_03 pickGrid invalid axes a1=" + a1Set + " a2=" + a2Set + " facing=" + pick.facing);
            return null;
        }
        List<Integer> a1Order = new ArrayList<>(a1Set);
        List<Integer> a2Order = new ArrayList<>(a2Set);
        a1Order.sort(Integer::compare);
        a2Order.sort(Comparator.reverseOrder());

        ButtonRef[] byIndex = new ButtonRef[9];
        Map<Long, Integer> byPos = new HashMap<>();
        for (ButtonRef br : grid) {
            if (br == null) continue;
            int col = a1Order.indexOf(br.a1);
            int row = a2Order.indexOf(br.a2);
            if (row < 0 || col < 0) continue;
            int idx = row * 3 + col;
            byIndex[idx] = br;
            byPos.put(packBlock(br.x, br.y, br.z), idx);
        }

        List<ButtonRef> ordered = new ArrayList<>(9);
        for (int i = 0; i < byIndex.length; i++) {
            if (byIndex[i] == null) return null;
            ordered.add(byIndex[i]);
        }
        room.cellIndexByButton.putAll(byPos);
        for (int i = 0; i < ordered.size(); i++) {
            ButtonRef br = ordered.get(i);
            if (br == null) continue;
            room.buttonX[i] = br.x;
            room.buttonY[i] = br.y;
            room.buttonZ[i] = br.z;
            Block b = world.getBlockAt(br.x, br.y, br.z);
            room.buttonType[i] = b.getType();
            room.buttonData[i] = b.getBlockData().getAsString();
        }

        if (DEBUG) {
            StringBuilder sb = new StringBuilder();
            sb.append("puzzle_room_03 grid chosen facing=").append(pick.facing.name()).append(" area=").append(pick.area).append(" cells=");
            for (int i = 0; i < ordered.size(); i++) {
                ButtonRef br = ordered.get(i);
                if (i > 0) sb.append(";");
                sb.append(i).append("@").append(br.x).append(",").append(br.y).append(",").append(br.z);
            }
            debug(sb.toString());
        }

        spawnFramesAndMaps(world, room, ordered, pick);
        return room;
    }

    private void spawnFramesAndMaps(World world, RoomState room, List<ButtonRef> grid, GridPick pick) {
        if (world == null || room == null || grid == null || pick == null) return;
        room.frames.clear();
        room.views.clear();

        for (int i = 0; i < grid.size(); i++) {
            ButtonRef br = grid.get(i);
            Location frameLoc = findFrameLocation(world, br, pick);
            if (frameLoc == null) {
                debug("puzzle_room_03 frame skipped cell=" + i + " reason=no_location button=" + br.x + "," + br.y + "," + br.z + " facing=" + pick.facing.name());
                continue;
            }

            for (Entity e : world.getNearbyEntities(frameLoc, 0.2, 0.2, 0.2)) {
                if (e instanceof ItemFrame) e.remove();
            }

            MapView view = Bukkit.createMap(world);
            for (MapRenderer r : List.copyOf(view.getRenderers())) view.removeRenderer(r);
            view.setTrackingPosition(false);
            view.setUnlimitedTracking(false);
            view.setLocked(false);
            view.addRenderer(new CellRenderer(room, i));

            ItemStack map = new ItemStack(Material.FILLED_MAP, 1);
            if (map.getItemMeta() instanceof MapMeta meta) {
                meta.setMapView(view);
                map.setItemMeta(meta);
            }

            ItemFrame frame = world.spawn(frameLoc, ItemFrame.class, f -> {
                f.setFacingDirection(pick.facing, true);
                f.setFixed(true);
                f.setInvulnerable(true);
                f.setVisible(false);
                f.setItem(map);
                if (frameKey != null) {
                    f.getPersistentDataContainer().set(frameKey, PersistentDataType.BYTE, (byte) 1);
                }
            });

            room.frames.add(frame.getUniqueId());
            room.views.add(view);
            debug("puzzle_room_03 frame spawned cell=" + i
                    + " at=" + frameLoc.getBlockX() + "," + frameLoc.getBlockY() + "," + frameLoc.getBlockZ()
                    + " button=" + br.x + "," + br.y + "," + br.z
                    + " facing=" + pick.facing.name());
        }
        debug("puzzle_room_03 framesTotal=" + room.frames.size() + " mapsTotal=" + room.views.size() + " room=" + room.roomKey);
    }

    private Location findFrameLocation(World world, ButtonRef br, GridPick pick) {
        if (world == null || br == null || pick == null) return null;
        BlockFace facing = pick.facing;
        Block anchor = world.getBlockAt(br.x, br.y, br.z);
        Block back = anchor.getRelative(facing.getOppositeFace());
        if (!back.getType().isSolid()) return null;
        return new Location(world, br.x + 0.5, br.y + 0.5, br.z + 0.5);
    }

    private void clearButton(RoomState room, int cellIndex) {
        if (room == null) return;
        if (cellIndex < 0 || cellIndex >= 9) return;
        World world = Bukkit.getWorld(room.worldName);
        if (world == null) return;
        Material t = room.buttonType[cellIndex];
        if (t == null) return;
        String n = t.name().toLowerCase(Locale.ROOT);
        if (!n.endsWith("_button")) return;
        Block b = world.getBlockAt(room.buttonX[cellIndex], room.buttonY[cellIndex], room.buttonZ[cellIndex]);
        if (b.getType().isAir()) return;
        b.setType(Material.AIR, false);
    }

    private void restoreButtons(RoomState room) {
        if (room == null) return;
        World world = Bukkit.getWorld(room.worldName);
        if (world == null) return;
        for (int i = 0; i < 9; i++) {
            Material type = room.buttonType[i];
            String data = room.buttonData[i];
            if (type == null || data == null) continue;
            String n = type.name().toLowerCase(Locale.ROOT);
            if (!n.endsWith("_button")) continue;
            Block b = world.getBlockAt(room.buttonX[i], room.buttonY[i], room.buttonZ[i]);
            b.setType(type, false);
            b.setBlockData(Bukkit.createBlockData(data), false);
        }
    }

    private GridPick pickGrid(List<ButtonRef> buttons) {
        if (buttons == null || buttons.isEmpty()) return null;
        Map<BlockFace, List<ButtonRef>> byFacing = new HashMap<>();
        for (ButtonRef br : buttons) {
            if (br == null) continue;
            byFacing.computeIfAbsent(br.facing, k -> new ArrayList<>()).add(br);
        }

        GridPick best = null;
        for (var entry : byFacing.entrySet()) {
            BlockFace facing = entry.getKey();
            List<ButtonRef> list = entry.getValue();
            if (list == null || list.size() < 9) continue;

            Map<Integer, List<ButtonRef>> byPlane = new HashMap<>();
            for (ButtonRef br : list) {
                int plane = planeCoord(br, facing);
                byPlane.computeIfAbsent(plane, k -> new ArrayList<>()).add(br);
            }

            for (var pEntry : byPlane.entrySet()) {
                List<ButtonRef> planeButtons = pEntry.getValue();
                if (planeButtons == null || planeButtons.size() < 9) continue;
                GridPick found = find3x3InPlane(planeButtons, facing);
                if (found == null) continue;
                if (best == null || found.area < best.area) best = found;
            }
        }
        return best;
    }

    private int planeCoord(ButtonRef br, BlockFace facing) {
        return switch (facing) {
            case NORTH, SOUTH -> br.z;
            case EAST, WEST -> br.x;
            case UP, DOWN -> br.y;
            default -> br.z;
        };
    }

    private GridPick find3x3InPlane(List<ButtonRef> planeButtons, BlockFace facing) {
        if (planeButtons == null || planeButtons.size() < 9) return null;

        Map<Long, ButtonRef> byAx = new HashMap<>();
        Set<Integer> a1Set = new HashSet<>();
        Set<Integer> a2Set = new HashSet<>();
        for (ButtonRef br : planeButtons) {
            AxisPair pair = axisPair(br, facing);
            br.a1 = pair.a1;
            br.a2 = pair.a2;
            long k = packCell(pair.a1, pair.a2);
            byAx.put(k, br);
            a1Set.add(pair.a1);
            a2Set.add(pair.a2);
        }

        if (a1Set.size() < 3 || a2Set.size() < 3) return null;
        List<Integer> a1s = new ArrayList<>(a1Set);
        List<Integer> a2s = new ArrayList<>(a2Set);
        a1s.sort(Integer::compare);
        a2s.sort(Integer::compare);

        GridPick best = null;
        for (int i = 0; i + 2 < a1s.size(); i++) {
            for (int j = i + 1; j + 1 < a1s.size(); j++) {
                for (int k = j + 1; k < a1s.size(); k++) {
                    int a1a = a1s.get(i), a1b = a1s.get(j), a1c = a1s.get(k);
                    int span1 = Math.abs(a1c - a1a);
                    if (span1 <= 0) continue;
                    for (int p = 0; p + 2 < a2s.size(); p++) {
                        for (int q = p + 1; q + 1 < a2s.size(); q++) {
                            for (int r = q + 1; r < a2s.size(); r++) {
                                int a2a = a2s.get(p), a2b = a2s.get(q), a2c = a2s.get(r);
                                int span2 = Math.abs(a2c - a2a);
                                if (span2 <= 0) continue;
                                List<ButtonRef> cells = new ArrayList<>(9);
                                int[] xs = new int[]{a1a, a1b, a1c};
                                int[] ys = new int[]{a2a, a2b, a2c};
                                boolean ok = true;
                                for (int aa : xs) {
                                    for (int bb : ys) {
                                        ButtonRef hit = byAx.get(packCell(aa, bb));
                                        if (hit == null) {
                                            ok = false;
                                            break;
                                        }
                                        cells.add(hit);
                                    }
                                    if (!ok) break;
                                }
                                if (!ok) continue;
                                int area = span1 * span2;
                                GridPick cand = new GridPick(facing, cells, area);
                                if (best == null || cand.area < best.area) best = cand;
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    private AxisPair axisPair(ButtonRef br, BlockFace facing) {
        return switch (facing) {
            case NORTH, SOUTH -> new AxisPair(br.x, br.y);
            case EAST, WEST -> new AxisPair(br.z, br.y);
            case UP, DOWN -> new AxisPair(br.x, br.z);
            default -> new AxisPair(br.x, br.y);
        };
    }

    private static long packBlock(int x, int y, int z) {
        long xx = (x & 0x3FFFFFFL);
        long yy = (y & 0xFFFL);
        long zz = (z & 0x3FFFFFFL);
        return (xx << 38) | (zz << 12) | yy;
    }

    private static long packCell(int a1, int a2) {
        return (((long) a1) << 32) ^ (a2 & 0xffffffffL);
    }

    private enum GameResult {
        ONGOING,
        X_WIN,
        O_WIN,
        DRAW
    }

    private static final class CellRenderer extends MapRenderer {
        private static final byte CLEAR = MapPalette.TRANSPARENT;
        private static final byte X_COLOR = MapPalette.matchColor(new Color(220, 60, 60));
        private static final byte O_COLOR = MapPalette.matchColor(new Color(60, 130, 240));

        private final RoomState room;
        private final int cellIndex;
        private char last = 0;
        private boolean dirty = true;

        private CellRenderer(RoomState room, int cellIndex) {
            super(false);
            this.room = room;
            this.cellIndex = cellIndex;
        }

        private void markDirty() {
            dirty = true;
        }

        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            if (room == null || canvas == null) return;
            char now = (room.board != null && room.board.length == 9) ? room.board[cellIndex] : ' ';
            if (!dirty && now == last) return;
            last = now;
            dirty = false;

            for (int x = 0; x < 128; x++) {
                for (int y = 0; y < 128; y++) {
                    canvas.setPixel(x, y, CLEAR);
                }
            }

            if (now == 'X') {
                drawX(canvas, X_COLOR);
            } else if (now == 'O') {
                drawO(canvas, O_COLOR);
            }
        }

        private void drawX(MapCanvas canvas, byte color) {
            int margin = 10;
            int w = 11;
            for (int x = margin; x <= 127 - margin; x++) {
                for (int y = margin; y <= 127 - margin; y++) {
                    int d1 = Math.abs(x - y);
                    int d2 = Math.abs((x + y) - 127);
                    if (d1 <= w || d2 <= w) {
                        canvas.setPixel(x, y, color);
                    }
                }
            }
        }

        private void drawO(MapCanvas canvas, byte color) {
            int cx = 64;
            int cy = 64;
            int rOut = 46;
            int rIn = 30;
            int rOut2 = rOut * rOut;
            int rIn2 = rIn * rIn;
            for (int x = 0; x < 128; x++) {
                for (int y = 0; y < 128; y++) {
                    int dx = x - cx;
                    int dy = y - cy;
                    int d2 = dx * dx + dy * dy;
                    if (d2 <= rOut2 && d2 >= rIn2) {
                        canvas.setPixel(x, y, color);
                    }
                }
            }
        }

        private int clamp(int v) {
            if (v < 0) return 0;
            if (v > 127) return 127;
            return v;
        }
    }

    private record AxisPair(int a1, int a2) {
    }

    private static final class ButtonRef {
        private final int x;
        private final int y;
        private final int z;
        private final BlockFace facing;
        private int a1;
        private int a2;

        private ButtonRef(int x, int y, int z, BlockFace facing) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.facing = facing;
        }
    }

    private record GridPick(BlockFace facing, List<ButtonRef> cells, int area) {
    }

    private static final class RoomState {
        private final String roomKey;
        private final String worldName;
        private final int baseX;
        private final int baseY;
        private final int baseZ;
        private final StructureTemplate.Rotation rotation;
        private final int sizeX;
        private final int sizeZ;
        private final Map<Long, Integer> cellIndexByButton = new HashMap<>();
        private final List<UUID> frames = new ArrayList<>();
        private final List<MapView> views = new ArrayList<>();
        private final int[] buttonX = new int[9];
        private final int[] buttonY = new int[9];
        private final int[] buttonZ = new int[9];
        private final Material[] buttonType = new Material[9];
        private final String[] buttonData = new String[9];
        private final char[] board = new char[]{' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '};
        private BlockFace facing;
        private int stage = 1;
        private boolean transitioning;
        private boolean pendingReset;
        private boolean completed;
        private UUID lastMoveBy;

        private RoomState(String roomKey, String worldName, int baseX, int baseY, int baseZ, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
            this.roomKey = roomKey;
            this.worldName = worldName;
            this.baseX = baseX;
            this.baseY = baseY;
            this.baseZ = baseZ;
            this.rotation = rotation;
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
        }
    }
}
