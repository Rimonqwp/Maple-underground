package maple.underground_caves.dungeon;

import maple.underground_caves.Test_dungeons;
import maple.underground_caves.party.PartyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.swofty.dungeons.*;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Ladder;
import org.bukkit.block.data.type.Slab;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.concurrent.ThreadLocalRandom;

@SuppressWarnings("ALL")
public class DungeonCommand implements CommandExecutor, TabCompleter {
    private final DungeonSecrets dungeonSecrets;
    private final DungeonMonsters dungeonMonsters;
    private final PartyManager partyManager;
    private final Map<String, StructureTemplate> templateCache = new ConcurrentHashMap<>();
    private final Set<String> missingTemplateNames = ConcurrentHashMap.newKeySet();
    private final Map<String, List<StructureTemplate>> templatesByPrefixCache = new ConcurrentHashMap<>();
    private final Map<StructureTemplate, String> templateNames = new IdentityHashMap<>();

    private final Map<String, TemplatePair> pairCache = new ConcurrentHashMap<>();
    private final Set<String> missingPairBaseNames = ConcurrentHashMap.newKeySet();
    private final Map<String, List<TemplatePair>> pairsByPrefixCache = new ConcurrentHashMap<>();

    public DungeonCommand(DungeonSecrets dungeonSecrets, DungeonMonsters dungeonMonsters, PartyManager partyManager) {
        this.dungeonSecrets = dungeonSecrets;
        this.dungeonMonsters = dungeonMonsters;
        this.partyManager = partyManager;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        String name = command.getName();
        if (name == null) return List.of();

        if (name.equalsIgnoreCase("gen_room")) {
            if (args.length == 1) {
                return filterPrefix(List.of("door", "tunnel"), args[0]);
            }
            if (args.length == 2) {
                return filterPrefix(List.of("north", "south", "east", "west", "n", "s", "e", "w"), args[1]);
            }
            return List.of();
        }

        if (name.equalsIgnoreCase("gen_dungeon")) {
            if (args.length == 1) {
                return filterPrefix(List.of("F1", "F2", "F3"), args[0]);
            }
            if (args.length >= 2) {
                return filterPrefix(List.of("-v", "verbose", "debug", "0", "1", "2", "3", "4", "5"), args[args.length - 1]);
            }
            return List.of();
        }

        return List.of();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        String cmd = command.getName();
        if (cmd != null && cmd.equalsIgnoreCase("gen_room")) {
            return handleGenRoom(player, args);
        }

        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: /gen_dungeon F1|F2|F3 [debug]", NamedTextColor.RED));
            return true;
        }

        int width;
        int height;
        String floor;
        int double1x2 = 0;
        int double2x1 = 0;
        int triple3x1 = 0;
        int quad2x2 = 0;
        int lShape = 0;
        boolean verbose = false;

        try {
            var parsed = parseFloor(args[0]);
            if (parsed == null) {
                player.sendMessage(Component.text("Invalid floor. Use F1, F2 or F3 (F1/F2=4x4, F3=5x5)", NamedTextColor.RED));
                return true;
            }
            floor = parsed.floor;
            width = parsed.width;
            height = parsed.height;

            ArrayList<Integer> overrides = new ArrayList<>();
            for (int i = 1; i < args.length; i++) {
                String a = args[i];
                if ("verbose".equalsIgnoreCase(a) || "-v".equalsIgnoreCase(a) || "debug".equalsIgnoreCase(a)) {
                    verbose = true;
                    continue;
                }
                try {
                    overrides.add(Integer.parseInt(a));
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("Invalid argument: " + a, NamedTextColor.RED));
                    return true;
                }
            }

            if (!overrides.isEmpty()) {
                if (overrides.size() > 0) double1x2 = overrides.get(0);
                if (overrides.size() > 1) double2x1 = overrides.get(1);
                if (overrides.size() > 2) quad2x2 = overrides.get(2);
                if (overrides.size() > 3) lShape = overrides.get(3);
                if (overrides.size() > 4) triple3x1 = overrides.get(4);
            } else {
                int area = width * height;
                if (Math.max(width, height) <= 4) {
                    // 4x4：至少 1 個 L、至少 1 個 1x2 或 2x1，與之前 (4x4, 5x5) 算法一致
                    quad2x2 = 0;
                    lShape = 1;
                    int totalDoubles = 1 + (int) (Math.random() * 2.0);
                    double1x2 = (int) (Math.random() * (totalDoubles + 1));
                    double2x1 = totalDoubles - double1x2;
                    if (double1x2 + double2x1 < 1) double2x1 = 1;
                    triple3x1 = 0;
                } else {
                    double quad2x2Den = 52.0;
                    double lShapeDen = 18.0;
                    double double1x2Den = 36.0;
                    double double2x1Den = 36.0;
                    double triple3x1Den = 44.0;

                    if ((width == 6 && height == 6) || (width == 7 && height == 7)) {
                        quad2x2Den = 50.0;
                        lShapeDen = 17.0;
                        double1x2Den = 34.0;
                        double2x1Den = 34.0;
                        triple3x1Den = 42.0;
                    }

                    quad2x2 = (int) (Math.random() * (area / quad2x2Den) + 0.5);
                    lShape = (int) (Math.random() * (area / lShapeDen));
                    double1x2 = (int) (Math.random() * (area / double1x2Den));
                    double2x1 = (int) (Math.random() * (area / double2x1Den));
                    triple3x1 = (int) (Math.random() * (area / triple3x1Den));
                    if (area >= 25 && quad2x2 == 0) quad2x2 = 1;
                    if (area >= 16 && lShape == 0) lShape = 1;
                    if (area >= 20 && triple3x1 == 0) triple3x1 = 1;

                    if (width == 5 && height == 5) {
                        if (double2x1 < 1) double2x1 = 1;
                        if (double1x2 < 1) double1x2 = 1;
                    }
                    if (width == 6 && height == 6) {
                        if (double2x1 < 1) double2x1 = 1;
                        if (triple3x1 < 1) triple3x1 = 1;
                    }
                }
                
                player.sendMessage(Component.text("Auto-calculated structures: 2x2=" + quad2x2 + ", L=" + lShape + ", 1x2=" + double1x2 + ", 2x1=" + double2x1 + ", 3x1=" + triple3x1, NamedTextColor.GRAY));
            }

        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Invalid number format.", NamedTextColor.RED));
            return true;
        }

        player.sendMessage(Component.text("Generating dungeon...", NamedTextColor.YELLOW));

        // Initialize Data with some defaults
        boolean smallMap = Math.max(width, height) <= 4;
        // 4x4: puzzle 1-2 + mini_boss 1 (無 trap)
        // 5x5+: puzzle 2-3 + trap 1 + mini_boss 1
        DungeonsData data = new DungeonsData(width, height)
                .with(DungeonRoomType.FAIRY, new DungeonsData.RoomData(1, 1))
                .with(DungeonRoomType.PUZZLE, smallMap ? new DungeonsData.RoomData(1, 2) : new DungeonsData.RoomData(2, 3))
                .with(DungeonRoomType.BLOOD, new DungeonsData.RoomData(0, 0))  // 不使用 BLOOD (end_room)
                .with(DungeonRoomType.TRAP, smallMap ? new DungeonsData.RoomData(0, 0) : new DungeonsData.RoomData(1, 1))
                .with(DungeonRoomType.MINI_BOSS, new DungeonsData.RoomData(1, 1));
        data.withStructure(DungeonStructureType.DOUBLE_1x2, double1x2)
                .withStructure(DungeonStructureType.DOUBLE_2x1, double2x1)
                .withStructure(DungeonStructureType.QUAD_2x2, quad2x2)
                .withStructure(DungeonStructureType.L_SHAPE, lShape)
                .withStructure(DungeonStructureType.TRIPLE_3x1, triple3x1);

        GeneratorService service = DungeonsAPI.getGeneratorService(data);
        service.withVerbose(verbose);

        final int finalWidth = width;
        final int finalHeight = height;
        final String finalFloor = floor;
        final boolean finalVerbose = verbose;
        
        // Generate asynchronously
        CompletableFuture<SkyBlockDungeon> future = service.generate();
        CompletableFuture<Void> preloadTemplatesFuture = CompletableFuture.runAsync(this::preloadTemplates);

        future.thenCombine(preloadTemplatesFuture.exceptionally(e -> {
            Test_dungeons.getInstance().getLogger().warning("[Build] Template preload failed: " + e.getMessage());
            return null;
        }), (dungeon, ignored) -> dungeon).thenAccept(dungeon -> {
            // Schedule block placement on main thread
            player.getScheduler().run(Test_dungeons.getInstance(), (task) -> {
                Logger log = Test_dungeons.getInstance().getLogger();
                if (finalVerbose) {
                    log.info("[Build][V] Dungeon generated, floor=" + finalFloor + " width=" + finalWidth + " height=" + finalHeight);
                    log.info("[Build][V] Dungeon layout:\n" + dungeon);
                    logRoomsVerbose(dungeon, finalWidth, finalHeight, log);
                }
                pruneDungeonDoorsToTree(dungeon, finalWidth, finalHeight, finalVerbose, log);
                World world = createVoidWorld();
                Location dungeonOrigin = new Location(world, 0, 64, 0);
                player.sendMessage(Component.text("Building dungeon...", NamedTextColor.YELLOW));
                startBuildDungeonIncremental(player, dungeonOrigin, dungeon, finalWidth, finalHeight, finalFloor, finalVerbose, log, service.getGenerationStartTime());
            }, null);
        }).exceptionally(e -> {
            player.sendMessage(Component.text("Error generating dungeon: " + e.getMessage(), NamedTextColor.RED));
            e.printStackTrace();
            return null;
        });

        return true;
    }

    private boolean handleGenRoom(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: /gen_room door [north|south|east|west]", NamedTextColor.RED));
            return true;
        }
        String type = args[0].toLowerCase(Locale.ROOT);
        if (!"door".equals(type) && !"tunnel".equals(type)) {
            player.sendMessage(Component.text("Unknown room type: " + args[0], NamedTextColor.RED));
            return true;
        }
        BlockFace facing = player.getFacing();
        if (args.length >= 2) {
            BlockFace parsed = parseFacing(args[1]);
            if (parsed == null) {
                player.sendMessage(Component.text("Invalid facing: " + args[1], NamedTextColor.RED));
                return true;
            }
            facing = parsed;
        }
        Location base = player.getLocation().getBlock().getLocation();
        buildSingleDoorTunnel(base, facing);
        Test_dungeons plugin = Test_dungeons.getInstance();
        if (plugin != null) {
            plugin.registerDebugDoor(base, facing);
        }
        player.sendMessage(Component.text("Door generated.", NamedTextColor.GREEN));
        return true;
    }

    private BlockFace parseFacing(String raw) {
        if (raw == null) return null;
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "north", "n" -> BlockFace.NORTH;
            case "south", "s" -> BlockFace.SOUTH;
            case "east", "e" -> BlockFace.EAST;
            case "west", "w" -> BlockFace.WEST;
            default -> null;
        };
    }

    private void buildSingleDoorTunnel(Location base, BlockFace facing) {
        if (base == null || facing == null) return;
        World world = base.getWorld();
        if (world == null) return;
        int baseX = base.getBlockX();
        int baseY = base.getBlockY();
        int baseZ = base.getBlockZ();
        if (facing == BlockFace.EAST || facing == BlockFace.WEST) {
            for (int z = baseZ - 2; z <= baseZ + 2; z++) {
                for (int y = baseY - 1; y <= baseY + 4; y++) {
                    world.getBlockAt(baseX, y, z).setType(Material.STONE_BRICKS);
                }
            }
            for (int z = baseZ - 1; z <= baseZ + 1; z++) {
                for (int y = baseY; y <= baseY + 3; y++) {
                    world.getBlockAt(baseX, y, z).setType(Material.COAL_BLOCK);
                }
            }
            return;
        }
        for (int x = baseX - 2; x <= baseX + 2; x++) {
            for (int y = baseY - 1; y <= baseY + 4; y++) {
                world.getBlockAt(x, y, baseZ).setType(Material.STONE_BRICKS);
            }
        }
        for (int x = baseX - 1; x <= baseX + 1; x++) {
            for (int y = baseY; y <= baseY + 3; y++) {
                world.getBlockAt(x, y, baseZ).setType(Material.COAL_BLOCK);
            }
        }
    }

    private void preloadTemplates() {
        File dir = new File(Test_dungeons.getInstance().getDataFolder(), "structures");
        if (!dir.exists()) return;
        File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            for (File f : files) {
                String name = f.getName();
                if (!name.toLowerCase(Locale.ROOT).endsWith(".yml")) continue;
                String base = name.substring(0, name.length() - 4).toLowerCase(Locale.ROOT);
                if (templateCache.containsKey(base)) continue;
                try {
                    StructureTemplate loaded = StructureTemplate.load(f);
                    templateCache.put(base, loaded);
                    missingTemplateNames.remove(base);
                } catch (Throwable ignored) {
                }
            }
        }

        loadTemplatesByPrefix("puzzle_room_");
        loadTemplatePairsByPrefix("puzzle_room_");
        loadTemplatesByPrefix("exit_room_");
        loadTemplatePairsByPrefix("exit_room_");
        loadTemplatePairsByPrefix("trap_room_");
        loadTemplatePairsByPrefix("miniboss_room_");
        loadTemplatesByPrefix("base_room_1x1_");
        loadTemplatePairsByPrefix("base_room_1x1_");
        loadTemplatePairsByPrefix("base_room_1x2_");
        loadTemplatePairsByPrefix("base_room_2x1_");
        loadTemplatePairsByPrefix("base_room_2x2_");
        loadTemplatePairsByPrefix("base_room_3x1_");
        loadTemplatePairsByPrefix("base_room_1x4_");
        loadTemplatePairsByPrefix("base_room_l_");
    }

    /** 解析 F1/F2/F3，地牢大小：F1=4x4, F2=4x4, F3=5x5。 */
    private static FloorSize parseFloor(String arg) {
        if (arg == null || arg.isBlank()) return null;
        String s = arg.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (s) {
            case "F1" -> new FloorSize("F1", 4, 4);
            case "F2" -> new FloorSize("F2", 4, 4);
            case "F3" -> new FloorSize("F3", 5, 5);
            default -> null;
        };
    }

    private static record FloorSize(String floor, int width, int height) {}

    private static void pruneDungeonDoorsToTree(SkyBlockDungeon dungeon, int width, int height, boolean verbose, Logger log) {
        if (dungeon == null) return;
        List<SkyBlockDungeon.DungeonDoor> doors = dungeon.getAllDoors();
        if (doors.isEmpty()) return;

        Map<NodeRef, List<DoorEdge>> adj = new HashMap<>();
        for (SkyBlockDungeon.DungeonDoor d : doors) {
            NodeRef a = nodeRefOf(dungeon, d.x1(), d.y1());
            NodeRef b = nodeRefOf(dungeon, d.x2(), d.y2());
            if (a == null || b == null) continue;
            if (a.equals(b)) continue;

            long k = doorKey(d.x1(), d.y1(), d.x2(), d.y2());
            DoorEdge e = new DoorEdge(a, b, k);
            adj.computeIfAbsent(a, ignored -> new ArrayList<>()).add(e);
            adj.computeIfAbsent(b, ignored -> new ArrayList<>()).add(e);
        }
        if (adj.isEmpty()) return;

        NodeRef root = findEntranceNode(dungeon, width, height);
        if (root == null || !adj.containsKey(root)) {
            root = adj.keySet().iterator().next();
        }

        Set<NodeRef> visited = new HashSet<>();
        Set<Long> keep = new HashSet<>();
        ArrayDeque<NodeRef> q = new ArrayDeque<>();

        visited.add(root);
        q.add(root);
        while (!q.isEmpty()) {
            NodeRef cur = q.poll();
            List<DoorEdge> edges = adj.get(cur);
            if (edges == null) continue;
            for (DoorEdge e : edges) {
                NodeRef other = e.other(cur);
                if (other == null) continue;
                if (visited.add(other)) {
                    keep.add(e.key());
                    q.add(other);
                }
            }
        }

        for (NodeRef n : adj.keySet()) {
            if (visited.contains(n)) continue;
            visited.add(n);
            q.add(n);
            while (!q.isEmpty()) {
                NodeRef cur = q.poll();
                List<DoorEdge> edges = adj.get(cur);
                if (edges == null) continue;
                for (DoorEdge e : edges) {
                    NodeRef other = e.other(cur);
                    if (other == null) continue;
                    if (visited.add(other)) {
                        keep.add(e.key());
                        q.add(other);
                    }
                }
            }
        }

        Set<Long> seen = new HashSet<>();
        List<SkyBlockDungeon.DungeonDoor> next = new ArrayList<>();
        for (SkyBlockDungeon.DungeonDoor d : doors) {
            long k = doorKey(d.x1(), d.y1(), d.x2(), d.y2());
            if (!keep.contains(k)) continue;
            if (!seen.add(k)) continue;
            next.add(d);
        }
        dungeon.setFullDoorsBeforePrune(doors);
        dungeon.setAllDoors(next);

        if (verbose) {
            int removed = doors.size() - next.size();
            log.info("[Build][V] pruneDoorsToTree removed=" + removed + " before=" + doors.size() + " after=" + next.size());
        }
    }

    private static NodeRef findEntranceNode(SkyBlockDungeon dungeon, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                SkyBlockDungeon.DungeonRoom r = dungeon.getRoom(x, y);
                if (r == null) continue;
                if (r.getRoomType() == DungeonRoomType.ENTRANCE) {
                    return nodeRefOf(dungeon, x, y);
                }
            }
        }
        return null;
    }

    private static NodeRef nodeRefOf(SkyBlockDungeon dungeon, int x, int y) {
        SkyBlockDungeon.DungeonRoom r = dungeon.getRoom(x, y);
        if (r == null) return null;
        UUID id = r.getStructureId();
        if (id != null) return new NodeRef(id, -1, -1);
        return new NodeRef(null, x, y);
    }

    private static long doorKey(int x1, int y1, int x2, int y2) {
        int ax = x1, ay = y1, bx = x2, by = y2;
        if (ax > bx || (ax == bx && ay > by)) {
            ax = x2;
            ay = y2;
            bx = x1;
            by = y1;
        }
        return ((long) (ax & 0xFFFF) << 48)
                | ((long) (ay & 0xFFFF) << 32)
                | ((long) (bx & 0xFFFF) << 16)
                | ((long) (by & 0xFFFF));
    }

    private record NodeRef(UUID structureId, int x, int y) {}

    private record DoorEdge(NodeRef a, NodeRef b, long key) {
        NodeRef other(NodeRef n) {
            if (n == null) return null;
            if (n.equals(a)) return b;
            if (n.equals(b)) return a;
            return null;
        }
    }

    private interface BuildStep {
        int tick(int budget);

        boolean isDone();
    }

    private static final class PasteBuildStep implements BuildStep {
        private final StructureTemplate.PasteJob job;

        private PasteBuildStep(StructureTemplate.PasteJob job) {
            this.job = job;
        }

        @Override
        public int tick(int budget) {
            if (job == null) return 0;
            return job.tick(budget);
        }

        @Override
        public boolean isDone() {
            return job == null || job.isDone();
        }
    }

    private static final class RunnableBuildStep implements BuildStep {
        private final Runnable runnable;
        private boolean done;

        private RunnableBuildStep(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public int tick(int budget) {
            if (done) return 0;
            if (budget <= 0) return 0;
            if (runnable != null) {
                runnable.run();
            }
            done = true;
            return 1;
        }

        @Override
        public boolean isDone() {
            return done;
        }
    }

    private void startBuildDungeonIncremental(Player player, Location startLoc, SkyBlockDungeon dungeon, int width, int height, String floor, boolean verbose, Logger log, long generationStartTime) {
        StructureTemplate startRoomTemplate = loadTemplateOrNull("start_room");
        List<StructureTemplate> puzzleRoomTemplates = loadTemplatesByPrefix("puzzle_room_");
        TemplatePair startRoomPair = loadTemplatePairOrNull("start_room");
        List<TemplatePair> puzzleRoomPairs = loadTemplatePairsByPrefix("puzzle_room_");
        StructureTemplate healRoomTemplate = loadTemplateOrNull("heal_room");
        TemplatePair healRoomPair = loadTemplatePairOrNull("heal_room");
        StructureTemplate exitRoomTemplate = loadTemplateOrNull("exit_room");
        TemplatePair exitRoomPair = loadTemplatePairOrNull("exit_room");
        List<StructureTemplate> exitRoomTemplates = loadTemplatesByPrefix("exit_room_");
        List<TemplatePair> exitRoomPairs = loadTemplatePairsByPrefix("exit_room_");
        StructureTemplate exitRoomForFloor = loadTemplateOrNull("exit_room_" + (floor != null ? floor.toLowerCase(Locale.ROOT) : "f1"));
        List<TemplatePair> trapRoomPairs = loadTemplatePairsByPrefix("trap_room_");
        List<TemplatePair> minibossRoomPairs = loadTemplatePairsByPrefix("miniboss_room_");
        List<TemplatePair> baseRoom1x1Pairs = loadTemplatePairsByPrefix("base_room_1x1_");
        List<StructureTemplate> baseRoom1x1Templates = loadTemplatesByPrefix("base_room_1x1_");

        boolean bigRoomMode =
                startRoomTemplate != null
                        || startRoomPair != null
                        || healRoomTemplate != null
                        || healRoomPair != null
                        || exitRoomTemplate != null
                        || exitRoomPair != null
                        || (exitRoomTemplates != null && !exitRoomTemplates.isEmpty())
                        || (exitRoomPairs != null && !exitRoomPairs.isEmpty())
                        || (puzzleRoomTemplates != null && !puzzleRoomTemplates.isEmpty())
                        || (puzzleRoomPairs != null && !puzzleRoomPairs.isEmpty())
                        || (trapRoomPairs != null && !trapRoomPairs.isEmpty())
                        || (minibossRoomPairs != null && !minibossRoomPairs.isEmpty())
                        || (baseRoom1x1Pairs != null && !baseRoom1x1Pairs.isEmpty())
                        || (baseRoom1x1Templates != null && !baseRoom1x1Templates.isEmpty());
        int roomScale = bigRoomMode ? 32 : 5;
        Random random = new Random();
        Set<String> usedBaseRoomTemplateKeys = new HashSet<>();
        Set<String> usedTrapRoomKeys = new HashSet<>();
        Set<String> usedMinibossRoomKeys = new HashSet<>();
        Set<String> usedExitRoomKeys = new HashSet<>();
        Set<String> usedPuzzleRoomKeys = new HashSet<>();

        int startTop = 0;
        if (startRoomTemplate != null) {
            startTop = startRoomTemplate.maxFilledY();
        } else if (startRoomPair != null && startRoomPair.walls() != null) {
            startTop = startRoomPair.walls().maxFilledY();
        }

        int anchorDoorY = -1;
        if (bigRoomMode) {
            Integer startDoor = doorAnchorYFromStart(startRoomPair, startRoomTemplate);
            if (startDoor != null) anchorDoorY = startDoor;
        }

        Test_dungeons.getInstance().setLastDungeon(dungeon, width, height, startLoc, roomScale, anchorDoorY, floor);
        if (dungeonSecrets != null) {
            dungeonSecrets.beginRun(Test_dungeons.getInstance().getLastDungeon());
        }
        if (dungeonMonsters != null) {
            dungeonMonsters.beginRun(Test_dungeons.getInstance().getLastDungeon());
        }

        List<BuildStep> steps = new ArrayList<>();
        Set<UUID> processedStructures = new HashSet<>();
        Set<UUID> builtStructures = new HashSet<>();

        if (bigRoomMode) {
            List<TemplatePair> pairs1x2 = loadTemplatePairsByPrefix("base_room_1x2_");
            List<TemplatePair> pairs2x1 = loadTemplatePairsByPrefix("base_room_2x1_");
            List<TemplatePair> pairs2x2 = loadTemplatePairsByPrefix("base_room_2x2_");
            List<TemplatePair> pairs3x1 = loadTemplatePairsByPrefix("base_room_3x1_");
            List<TemplatePair> pairs1x4 = loadTemplatePairsByPrefix("base_room_1x4_");
            List<TemplatePair> pairsL = loadTemplatePairsByPrefix("base_room_l_");

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(x, y);
                    if (room == null) continue;
                    UUID id = room.getStructureId();
                    if (id == null) continue;
                    if (processedStructures.contains(id)) continue;

                    int[] bounds = findStructureBounds(dungeon, width, height, id);
                    int minX = bounds[0];
                    int minY = bounds[1];
                    int maxX = bounds[2];
                    int maxY = bounds[3];
                    int cellsX = (maxX - minX) + 1;
                    int cellsY = (maxY - minY) + 1;

                    DungeonStructureType structureType = room.getStructureType();
                    boolean uniqueRequired = room.getRoomType() == DungeonRoomType.BASE;
                    TemplatePair pair = pickPairForStructureOrFootprint(structureType, cellsX, cellsY, pairs1x2, pairs2x1, pairs2x2, pairs3x1, pairs1x4, pairsL, random, usedBaseRoomTemplateKeys, uniqueRequired);
                    if (pair == null || pair.walls() == null || pair.doors() == null) {
                        processedStructures.add(id);
                        continue;
                    }

                    StructureTemplate.Rotation rotation = pickRotationForFootprint(pair.walls(), cellsX, cellsY);
                    if (structureType == DungeonStructureType.L_SHAPE && cellsX == 2 && cellsY == 2) {
                        rotation = pickRotationForLShape(dungeon, id, minX, minY, maxX, maxY, pair.walls());
                    }
                    StructureTemplate walls = pair.walls().rotate(rotation);
                    StructureTemplate doors = pair.doors().rotate(rotation);

                    Location origin = startLoc.clone().add(minX * roomScale, 0, minY * roomScale);
                    int yOffset = 0;
                    Integer doorY = doorAnchorYFromPair(pair);
                    boolean doorAligned = anchorDoorY >= 0 && doorY != null;
                    if (doorAligned) {
                        yOffset = anchorDoorY - doorY;
                    } else if (startTop > 0) {
                        yOffset = (startTop - walls.maxFilledY()) + 1;
                    }
                    int gapCellX = -1, gapCellY = -1;
                    if (structureType == DungeonStructureType.L_SHAPE && cellsX == 2 && cellsY == 2) {
                        // L 形：找出缺口格（在 2x2 範圍內但不屬於此結構的格子），用於排除區，玩家在缺口內不觸發怪物
                        for (int cx = minX; cx <= maxX; cx++) {
                            for (int cy = minY; cy <= maxY; cy++) {
                                SkyBlockDungeon.DungeonRoom rc = dungeon.getRoom(cx, cy);
                                if (rc == null || !id.equals(rc.getStructureId())) {
                                    gapCellX = cx;
                                    gapCellY = cy;
                                    break;
                                }
                            }
                            if (gapCellX >= 0) break;
                        }
                    }
                    if (!doorAligned) {
                        if (structureType == DungeonStructureType.L_SHAPE) {
                            // 若 L 缺口剛好是 start_room，不額外加 yOffset，避免 L 地板與入口高度錯位導致血條懸空
                            boolean gapIsEntrance = gapCellX >= 0 && gapCellY >= 0;
                            if (gapIsEntrance) {
                                SkyBlockDungeon.DungeonRoom rc = dungeon.getRoom(gapCellX, gapCellY);
                                gapIsEntrance = rc != null && rc.getRoomType() == DungeonRoomType.ENTRANCE;
                            }
                            if (!gapIsEntrance) yOffset += 1;
                        } else if (structureType == DungeonStructureType.TRIPLE_3x1) {
                            yOffset -= 1;
                        }
                    }
                    Location base = yOffset == 0 ? origin : origin.clone().add(0, yOffset, 0);
                    String groupKey = "structure:" + id;
                    String templateName = pair.baseName();
                    if (dungeonSecrets != null && base.getWorld() != null) {
                        for (int cx = minX; cx <= maxX; cx++) {
                            for (int cy = minY; cy <= maxY; cy++) {
                                SkyBlockDungeon.DungeonRoom rc = dungeon.getRoom(cx, cy);
                                if (rc != null && id.equals(rc.getStructureId())) {
                                    dungeonSecrets.registerRoomGroup(base.getWorld().getName(), groupKey, templateName, cx, cy);
                                }
                            }
                        }
                    }
                    if (dungeonMonsters != null && base.getWorld() != null) {
                        for (int cx = minX; cx <= maxX; cx++) {
                            for (int cy = minY; cy <= maxY; cy++) {
                                SkyBlockDungeon.DungeonRoom rc = dungeon.getRoom(cx, cy);
                                if (rc != null && id.equals(rc.getStructureId())) {
                                    dungeonMonsters.registerRoomGroup(base.getWorld().getName(), groupKey, templateName, cx, cy);
                                }
                            }
                        }
                    }
                    String ctx = "[Build][V] structure type=" + (structureType != null ? structureType.name() : "null")
                            + " id=" + id.toString().substring(0, 8)
                            + " bounds=(" + minX + "," + minY + ")-(" + maxX + "," + maxY + ")"
                            + " rotation=" + rotation.name()
                            + " yOffset=" + yOffset;

                    steps.add(new PasteBuildStep(walls.pasteJob(base.getWorld(), base, null, templateName)));

                    Set<Long> include = new HashSet<>();
                    Set<Long> coalInclude = new HashSet<>();
                    for (StructureTemplate.DoorMarker m : doors.doorMarkers()) {
                        BlockFace resolvedFacing = StructureTemplate.resolveDoorFacing(m, doors.sizeX(), doors.sizeZ());
                        if (resolvedFacing == BlockFace.SELF) continue;
                        StructureTemplate.DoorMarker resolved = new StructureTemplate.DoorMarker(m.x(), m.y(), m.z(), resolvedFacing);
                        boolean open = shouldOpenDoorMarker(dungeon, width, height, id, minX, minY, resolved, roomScale, verbose, log, ctx);
                        if (!open) continue;
                        StructureTemplate.addDoorOverlayIncludePositions(include, m, doors.sizeX(), doors.sizeY(), doors.sizeZ());
                        int markerCellX = minX + (resolved.x() / roomScale);
                        int markerCellY = minY + (resolved.z() / roomScale);
                        if (shouldDoorMarkerHaveCoal(dungeon, markerCellX, markerCellY, resolvedFacing)) {
                            StructureTemplate.addDoorOverlayIncludePositions(coalInclude, m, doors.sizeX(), doors.sizeY(), doors.sizeZ());
                        }
                    }
                    if (!include.isEmpty()) {
                        steps.add(new PasteBuildStep(doors.pasteJob(base.getWorld(), base, include, null, true, coalInclude)));
                    }
                    StructureTemplate.Rotation finalRotation = rotation;
                    Location finalBase = base.clone();
                    int finalSizeX = pair.walls().sizeX();
                    int finalSizeY = pair.walls().sizeY();
                    int finalSizeZ = pair.walls().sizeZ();
                    String finalGroupKey = groupKey;
                    String finalTemplateName = templateName;
                    int finalGapCellX = gapCellX;
                    int finalGapCellY = gapCellY;
                    int finalMinX = minX;
                    int finalMinY = minY;
                    steps.add(new RunnableBuildStep(() -> {
                        if (dungeonSecrets != null) {
                            dungeonSecrets.spawnGroupSecrets(finalBase.getWorld(), finalBase, finalRotation, finalSizeX, finalSizeZ, finalGroupKey, finalTemplateName);
                        }
                        if (dungeonMonsters != null) {
                            int exMinX = -1, exMaxX = -1, exMinZ = -1, exMaxZ = -1;
                            if (finalGapCellX >= 0 && finalGapCellY >= 0 && finalBase.getWorld() != null) {
                                exMinX = finalBase.getBlockX() + (finalGapCellX - finalMinX) * roomScale;
                                exMinZ = finalBase.getBlockZ() + (finalGapCellY - finalMinY) * roomScale;
                                exMaxX = exMinX + roomScale - 1;
                                exMaxZ = exMinZ + roomScale - 1;
                            }
                            dungeonMonsters.spawnGroupMonsters(finalBase.getWorld(), finalBase, finalRotation, finalSizeX, finalSizeZ, finalGroupKey, finalTemplateName, exMinX, exMaxX, exMinZ, exMaxZ);
                        }
                        DungeonTemplateCommand.registerRoomPushers(Test_dungeons.getInstance(), finalBase.getWorld(), finalBase, finalRotation, finalSizeX, finalSizeZ, finalGroupKey, finalTemplateName);
                        registerPuzzleRoom06IfNeeded(finalBase, finalRotation, finalSizeX, finalSizeZ, finalTemplateName);
                        processRoomBlocks(finalBase, finalRotation, finalSizeX, finalSizeY, finalSizeZ, finalTemplateName);
                    }));

                    processedStructures.add(id);
                    builtStructures.add(id);
                }
            }
        }

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(x, y);
                if (room == null) continue;
                if (bigRoomMode && room.getStructureId() != null && builtStructures.contains(room.getStructureId())) continue;

                Location roomOrigin = startLoc.clone().add(x * roomScale, 0, y * roomScale);
                queueRoomBuild(steps, roomOrigin, room, x, y, dungeon, bigRoomMode, startRoomTemplate, startRoomPair, healRoomTemplate, healRoomPair, exitRoomTemplate, exitRoomPair, exitRoomForFloor, exitRoomTemplates, exitRoomPairs, puzzleRoomTemplates, puzzleRoomPairs, trapRoomPairs, minibossRoomPairs, baseRoom1x1Templates, baseRoom1x1Pairs, anchorDoorY, usedBaseRoomTemplateKeys, usedTrapRoomKeys, usedMinibossRoomKeys, usedExitRoomKeys, usedPuzzleRoomKeys, random, verbose, log, width, height);
            }
        }

        if (bigRoomMode) {
            int finalAnchorDoorY = anchorDoorY;
            int finalWidth = width;
            int finalHeight = height;
            steps.add(new RunnableBuildStep(() -> buildDoorTunnels(startLoc, dungeon, roomScale, finalAnchorDoorY, finalWidth, finalHeight)));
            steps.add(new RunnableBuildStep(() -> removeCoalFromTrapAndMinibossRooms(startLoc.getWorld(), dungeon, startLoc.getBlockX(), startLoc.getBlockY(), startLoc.getBlockZ(), width, height, roomScale)));
        }

        if (bigRoomMode && floor != null && "F1".equalsIgnoreCase(floor.trim())) {
            StructureTemplate lastBossTpl = loadTemplateOrNull("last_boss_room_f1");
            if (lastBossTpl != null) {
                int szX = lastBossTpl.sizeX();
                int szZ = lastBossTpl.sizeZ();
                int corner = random.nextInt(4);
                int outX, outZ;
                switch (corner) {
                    case 0 -> { outX = -szX; outZ = -szZ; }
                    case 1 -> { outX = width * roomScale; outZ = -szZ; }
                    case 2 -> { outX = -szX; outZ = height * roomScale; }
                    default -> { outX = width * roomScale; outZ = height * roomScale; }
                }
                Location lastBossOrigin = startLoc.clone().add(outX, 0, outZ);
                steps.add(new PasteBuildStep(lastBossTpl.rotate(StructureTemplate.Rotation.R0).pasteJob(startLoc.getWorld(), lastBossOrigin, null, "last_boss_room_f1")));
                steps.add(new RunnableBuildStep(() -> {
                    maple.underground_caves.dungeon.BossRoomManager bm = Test_dungeons.getInstance().getBossRoomManager();
                    if (bm != null) bm.registerLastBossRoom(startLoc.getWorld(), lastBossOrigin, "last_boss_room_f1", -1, -1);
                    // 第一隻 BOSS 改為玩家傳送到 last_boss_room 後 5 秒才生成（逃避型），不在 gen 時生成
                }));
            }
        }

        int finalStartTop = startTop;
        int finalAnchorDoorY = anchorDoorY;
        new BukkitRunnable() {
            int idx = 0;

            @Override
            public void run() {
                if (player == null || !player.isOnline()) {
                    cancel();
                    return;
                }
                int budget = 8000;
                while (budget > 0 && idx < steps.size()) {
                    BuildStep step = steps.get(idx);
                    int used = step.tick(budget);
                    budget -= used;
                    if (step.isDone()) {
                        idx++;
                    } else {
                        break;
                    }
                }

                if (idx >= steps.size()) {
                    cancel();
                    Location teleport = findStartRoomTeleport(startLoc, dungeon, width, height, roomScale, finalStartTop, verbose, log);
                    if (teleport != null && teleport.getWorld() != null) {
                        Test_dungeons.getInstance().setDungeonStartRoomLocation(teleport.getWorld().getName(), teleport.clone());
                    }
                    List<UUID> partyMemberIds = partyManager != null ? partyManager.getPartyMembers(player) : List.of(player.getUniqueId());
                    for (UUID memberId : partyMemberIds) {
                        Player p = Bukkit.getPlayer(memberId);
                        if (p != null && p.isOnline()) {
                            p.teleport(teleport);
                            Test_dungeons.getInstance().giveDungeonMap(p);
                            if (p.getUniqueId().equals(player.getUniqueId())) {
                                p.sendMessage(Component.text("Dungeon generated!", NamedTextColor.GREEN));
                                p.sendMessage(Component.text("Time taken: " + (System.currentTimeMillis() - generationStartTime) + "ms", NamedTextColor.GRAY));
                            } else {
                                p.sendMessage(Component.text("隊長已將你傳送至地牢！", NamedTextColor.GREEN));
                            }
                        }
                    }
                    if (Test_dungeons.getInstance().getDungeonScoreboard() != null && startLoc.getWorld() != null) {
                        Test_dungeons.getInstance().getDungeonScoreboard().setPartyForWorld(startLoc.getWorld().getName(), partyMemberIds);
                    }
                    // 從遠端 MySQL 拉取隊伍內每人的 6 項經驗（地底洞窟 + 5 職業）並寫入本地 SQLite
                    if (startLoc.getWorld() != null && partyMemberIds != null && !partyMemberIds.isEmpty()) {
                        Test_dungeons.getInstance().syncPartyProfessionExperienceFromRemote(startLoc.getWorld().getName(), partyMemberIds);
                    }
                    var readyMgr = Test_dungeons.getInstance().getStartRoomReadyManager();
                    if (readyMgr != null && startLoc.getWorld() != null && bigRoomMode) {
                        readyMgr.startReadyPhase(startLoc.getWorld().getName(), partyMemberIds, startLoc, dungeon, width, height, roomScale, finalAnchorDoorY);
                    }
                }
            }
        }.runTaskTimer(Test_dungeons.getInstance(), 1L, 1L);
    }

    private void queueRoomBuild(List<BuildStep> steps, Location origin, SkyBlockDungeon.DungeonRoom room, int x, int y, SkyBlockDungeon dungeon, boolean bigRoomMode, StructureTemplate startRoomTemplate, TemplatePair startRoomPair, StructureTemplate healRoomTemplate, TemplatePair healRoomPair, StructureTemplate exitRoomTemplate, TemplatePair exitRoomPair, StructureTemplate exitRoomForFloor, List<StructureTemplate> exitRoomTemplates, List<TemplatePair> exitRoomPairs, List<StructureTemplate> puzzleRoomTemplates, List<TemplatePair> puzzleRoomPairs, List<TemplatePair> trapRoomPairs, List<TemplatePair> minibossRoomPairs, List<StructureTemplate> baseRoom1x1Templates, List<TemplatePair> baseRoom1x1Pairs, int anchorDoorY, Set<String> usedBaseRoomTemplateKeys, Set<String> usedTrapRoomKeys, Set<String> usedMinibossRoomKeys, Set<String> usedExitRoomKeys, Set<String> usedPuzzleRoomKeys, Random random, boolean verbose, Logger log, int buildWidth, int buildHeight) {
        String ctx = "[Build][V] room (" + x + "," + y + ") " + fmtRoomShort(room);
        String groupKey = "cell:" + x + "," + y;

        if (room.getRoomType() == DungeonRoomType.ENTRANCE) {
            List<BlockFace> desired = connectedDirections(dungeon, x, y);
            if (startRoomPair != null) {
                if (dungeonSecrets != null && origin.getWorld() != null) {
                    dungeonSecrets.registerRoomGroup(origin.getWorld().getName(), groupKey, startRoomPair.baseName(), x, y);
                }
                if (dungeonMonsters != null && origin.getWorld() != null) {
                    dungeonMonsters.registerRoomGroup(origin.getWorld().getName(), groupKey, startRoomPair.baseName(), x, y);
                }
                queuePasteRoomPair(steps, origin, startRoomPair, desired, 0, groupKey, startRoomPair.baseName(), dungeon, x, y);
                return;
            }
            if (startRoomTemplate != null) {
                String tName = templateName(startRoomTemplate);
                if (dungeonSecrets != null && origin.getWorld() != null && tName != null) {
                    dungeonSecrets.registerRoomGroup(origin.getWorld().getName(), groupKey, tName, x, y);
                }
                if (dungeonMonsters != null && origin.getWorld() != null && tName != null) {
                    dungeonMonsters.registerRoomGroup(origin.getWorld().getName(), groupKey, tName, x, y);
                }
                StructureTemplate.Rotation rotation = pickBestRotation(startRoomTemplate, desired, verbose, log, ctx + " entrance");
                steps.add(new PasteBuildStep(startRoomTemplate.rotate(rotation).pasteJob(origin.getWorld(), origin)));
                steps.add(new RunnableBuildStep(() -> {
                    if (dungeonSecrets != null) {
                        dungeonSecrets.spawnGroupSecrets(origin.getWorld(), origin, rotation, startRoomTemplate.sizeX(), startRoomTemplate.sizeZ(), groupKey, tName);
                    }
                    if (dungeonMonsters != null) {
                        dungeonMonsters.spawnGroupMonsters(origin.getWorld(), origin, rotation, startRoomTemplate.sizeX(), startRoomTemplate.sizeZ(), groupKey, tName);
                    }
                    DungeonTemplateCommand.registerRoomPushers(Test_dungeons.getInstance(), origin.getWorld(), origin, rotation, startRoomTemplate.sizeX(), startRoomTemplate.sizeZ(), groupKey, tName);
                    registerPuzzleRoom06IfNeeded(origin, rotation, startRoomTemplate.sizeX(), startRoomTemplate.sizeZ(), tName);
                    registerBossRoomIfNeeded(origin.getWorld(), origin, rotation, startRoomTemplate.sizeX(), startRoomTemplate.sizeZ(), tName, startRoomTemplate, x, y);
                    if ("start_room".equalsIgnoreCase(tName)) {
                        Test_dungeons.getInstance().registerStartRoomNpc(origin.getWorld(), origin, rotation, startRoomTemplate.sizeX(), startRoomTemplate.sizeZ(), desired);
                    }
                    processRoomBlocks(origin, rotation, startRoomTemplate.sizeX(), startRoomTemplate.sizeY(), startRoomTemplate.sizeZ(), tName);
                }));
                return;
            }
        }

        if (bigRoomMode && room.getRoomType() == DungeonRoomType.FAIRY) {
            List<BlockFace> desired = connectedDirections(dungeon, x, y);
            int startTop = startRoomTemplate != null ? startRoomTemplate.maxFilledY() : (startRoomPair != null && startRoomPair.walls() != null ? startRoomPair.walls().maxFilledY() : 0);

            if (healRoomPair != null) {
                int yOffset = 0;
                Integer doorY = doorAnchorYFromPair(healRoomPair);
                if (anchorDoorY >= 0 && doorY != null) {
                    yOffset = anchorDoorY - doorY;
                } else {
                    int healTop = healRoomPair.walls() != null ? healRoomPair.walls().maxFilledY() : 0;
                    yOffset = (startTop - healTop);
                }
                if (dungeonSecrets != null && origin.getWorld() != null) {
                    dungeonSecrets.registerRoomGroup(origin.getWorld().getName(), groupKey, healRoomPair.baseName(), x, y);
                }
                if (dungeonMonsters != null && origin.getWorld() != null) {
                    dungeonMonsters.registerRoomGroup(origin.getWorld().getName(), groupKey, healRoomPair.baseName(), x, y);
                }
                queuePasteRoomPair(steps, origin.clone().add(0, yOffset, 0), healRoomPair, desired, 0, groupKey, healRoomPair.baseName(), dungeon, x, y);
                return;
            }

            if (healRoomTemplate != null) {
                String tName = templateName(healRoomTemplate);
                int yOffset = 0;
                Integer doorY = doorAnchorYFromTemplate(healRoomTemplate);
                if (anchorDoorY >= 0 && doorY != null) {
                    yOffset = anchorDoorY - doorY;
                } else {
                    int healTop = healRoomTemplate.maxFilledY();
                    yOffset = (startTop - healTop);
                }
                StructureTemplate.Rotation rotation = pickBestRotation(healRoomTemplate, desired, verbose, log, ctx + " heal");
                Location base = origin.clone().add(0, yOffset, 0);
                steps.add(new PasteBuildStep(healRoomTemplate.rotate(rotation).pasteJob(base.getWorld(), base, null, tName)));
                if (dungeonSecrets != null && base.getWorld() != null && tName != null) {
                    dungeonSecrets.registerRoomGroup(base.getWorld().getName(), groupKey, tName, x, y);
                }
                if (dungeonMonsters != null && base.getWorld() != null && tName != null) {
                    dungeonMonsters.registerRoomGroup(base.getWorld().getName(), groupKey, tName, x, y);
                }
                steps.add(new RunnableBuildStep(() -> {
                    if (dungeonSecrets != null) {
                        dungeonSecrets.spawnGroupSecrets(base.getWorld(), base, rotation, healRoomTemplate.sizeX(), healRoomTemplate.sizeZ(), groupKey, tName);
                    }
                    if (dungeonMonsters != null) {
                        dungeonMonsters.spawnGroupMonsters(base.getWorld(), base, rotation, healRoomTemplate.sizeX(), healRoomTemplate.sizeZ(), groupKey, tName);
                    }
                    DungeonTemplateCommand.registerRoomPushers(Test_dungeons.getInstance(), base.getWorld(), base, rotation, healRoomTemplate.sizeX(), healRoomTemplate.sizeZ(), groupKey, tName);
                    registerPuzzleRoom06IfNeeded(base, rotation, healRoomTemplate.sizeX(), healRoomTemplate.sizeZ(), tName);
                    processRoomBlocks(base, rotation, healRoomTemplate.sizeX(), healRoomTemplate.sizeY(), healRoomTemplate.sizeZ(), tName);
                    registerBossRoomIfNeeded(base.getWorld(), base, rotation, healRoomTemplate.sizeX(), healRoomTemplate.sizeZ(), tName, healRoomTemplate, x, y);
                }));
                return;
            }
        }

        if (bigRoomMode && room.getRoomType() == DungeonRoomType.TRAP) {
            List<BlockFace> desired = connectedDirections(dungeon, x, y);
            int startTop = startRoomTemplate != null ? startRoomTemplate.maxFilledY() : (startRoomPair != null && startRoomPair.walls() != null ? startRoomPair.walls().maxFilledY() : 0);

            if (trapRoomPairs != null && !trapRoomPairs.isEmpty()) {
                TemplatePair pair = pickPairPreferUnused(trapRoomPairs, random, usedTrapRoomKeys);
                if (pair != null) {
                    int yOffset = 0;
                    Integer doorY = doorAnchorYFromPair(pair);
                    if (anchorDoorY >= 0 && doorY != null) {
                        yOffset = anchorDoorY - doorY;
                    } else {
                        int top = pair.walls() != null ? pair.walls().maxFilledY() : 0;
                        yOffset = (startTop - top) + 1;
                    }
                    if (dungeonSecrets != null && origin.getWorld() != null) {
                        dungeonSecrets.registerRoomGroup(origin.getWorld().getName(), groupKey, pair.baseName(), x, y);
                    }
                    if (dungeonMonsters != null && origin.getWorld() != null) {
                        dungeonMonsters.registerRoomGroup(origin.getWorld().getName(), groupKey, pair.baseName(), x, y);
                    }
                    queuePasteRoomPair(steps, origin.clone().add(0, yOffset, 0), pair, desired, 0, groupKey, pair.baseName(), dungeon, x, y);
                    return;
                }
            }
        }

        if (bigRoomMode && room.getRoomType() == DungeonRoomType.MINI_BOSS) {
            List<BlockFace> desired = connectedDirections(dungeon, x, y);
            int startTop = startRoomTemplate != null ? startRoomTemplate.maxFilledY() : (startRoomPair != null && startRoomPair.walls() != null ? startRoomPair.walls().maxFilledY() : 0);

            if (minibossRoomPairs != null && !minibossRoomPairs.isEmpty()) {
                TemplatePair pair = pickPairPreferUnused(minibossRoomPairs, random, usedMinibossRoomKeys);
                if (pair != null) {
                    int yOffset = 0;
                    Integer doorY = doorAnchorYFromPair(pair);
                    if (anchorDoorY >= 0 && doorY != null) {
                        yOffset = anchorDoorY - doorY;
                    } else {
                        int top = pair.walls() != null ? pair.walls().maxFilledY() : 0;
                        yOffset = (startTop - top) + 1;
                    }
                    if (dungeonSecrets != null && origin.getWorld() != null) {
                        dungeonSecrets.registerRoomGroup(origin.getWorld().getName(), groupKey, pair.baseName(), x, y);
                    }
                    if (dungeonMonsters != null && origin.getWorld() != null) {
                        dungeonMonsters.registerRoomGroup(origin.getWorld().getName(), groupKey, pair.baseName(), x, y);
                    }
                    queuePasteRoomPair(steps, origin.clone().add(0, yOffset, 0), pair, desired, 0, groupKey, pair.baseName(), dungeon, x, y);
                    return;
                }
            }
        }

        if (bigRoomMode && room.getRoomType() == DungeonRoomType.BLOOD) {
            List<BlockFace> desired = connectedDirections(dungeon, x, y);
            int startTop = startRoomTemplate != null ? startRoomTemplate.maxFilledY() : (startRoomPair != null && startRoomPair.walls() != null ? startRoomPair.walls().maxFilledY() : 0);

            if (minibossRoomPairs != null && !minibossRoomPairs.isEmpty()) {
                TemplatePair pair = pickPairPreferUnused(minibossRoomPairs, random, usedMinibossRoomKeys);
                if (pair != null) {
                    int yOffset = 0;
                    Integer doorY = doorAnchorYFromPair(pair);
                    if (anchorDoorY >= 0 && doorY != null) {
                        yOffset = anchorDoorY - doorY;
                    } else {
                        int top = pair.walls() != null ? pair.walls().maxFilledY() : 0;
                        yOffset = (startTop - top) + 1;
                    }
                    if (dungeonSecrets != null && origin.getWorld() != null) {
                        dungeonSecrets.registerRoomGroup(origin.getWorld().getName(), groupKey, pair.baseName(), x, y);
                    }
                    if (dungeonMonsters != null && origin.getWorld() != null) {
                        dungeonMonsters.registerRoomGroup(origin.getWorld().getName(), groupKey, pair.baseName(), x, y);
                    }
                    queuePasteRoomPair(steps, origin.clone().add(0, yOffset, 0), pair, desired, 0, groupKey, pair.baseName(), dungeon, x, y);
                    return;
                }
            }
        }

        if (bigRoomMode && room.getRoomType() == DungeonRoomType.EXIT) {
            List<BlockFace> desired = connectedDirections(dungeon, x, y);
            int startTop = startRoomTemplate != null ? startRoomTemplate.maxFilledY() : (startRoomPair != null && startRoomPair.walls() != null ? startRoomPair.walls().maxFilledY() : 0);

            if (exitRoomForFloor != null) {
                String finalTName = templateName(exitRoomForFloor);
                int yOffset = 0;
                Integer doorY = doorAnchorYFromTemplate(exitRoomForFloor);
                if (anchorDoorY >= 0 && doorY != null) {
                    yOffset = anchorDoorY - doorY;
                } else {
                    int exitTop = exitRoomForFloor.maxFilledY();
                    yOffset = (startTop - exitTop);
                }
                StructureTemplate.Rotation rotation = pickBestRotation(exitRoomForFloor, desired, verbose, log, ctx + " exit");
                Location base = origin.clone().add(0, yOffset, 0);
                steps.add(new PasteBuildStep(exitRoomForFloor.rotate(rotation).pasteJob(base.getWorld(), base, null, finalTName)));
                if (dungeonSecrets != null && base.getWorld() != null && finalTName != null) {
                    dungeonSecrets.registerRoomGroup(base.getWorld().getName(), groupKey, finalTName, x, y);
                }
                if (dungeonMonsters != null && base.getWorld() != null && finalTName != null) {
                    dungeonMonsters.registerRoomGroup(base.getWorld().getName(), groupKey, finalTName, x, y);
                }
                steps.add(new RunnableBuildStep(() -> {
                    if (dungeonSecrets != null) {
                        dungeonSecrets.spawnGroupSecrets(base.getWorld(), base, rotation, exitRoomForFloor.sizeX(), exitRoomForFloor.sizeZ(), groupKey, finalTName);
                    }
                    if (dungeonMonsters != null && !finalTName.toLowerCase(java.util.Locale.ROOT).startsWith("exit_room_f")) {
                        dungeonMonsters.spawnGroupMonsters(base.getWorld(), base, rotation, exitRoomForFloor.sizeX(), exitRoomForFloor.sizeZ(), groupKey, finalTName);
                    }
                    DungeonTemplateCommand.registerRoomPushers(Test_dungeons.getInstance(), base.getWorld(), base, rotation, exitRoomForFloor.sizeX(), exitRoomForFloor.sizeZ(), groupKey, finalTName);
                    registerPuzzleRoom06IfNeeded(base, rotation, exitRoomForFloor.sizeX(), exitRoomForFloor.sizeZ(), finalTName);
                    processRoomBlocks(base, rotation, exitRoomForFloor.sizeX(), exitRoomForFloor.sizeY(), exitRoomForFloor.sizeZ(), finalTName);
                    registerBossRoomIfNeeded(base.getWorld(), base, rotation, exitRoomForFloor.sizeX(), exitRoomForFloor.sizeZ(), finalTName, exitRoomForFloor, x, y);
                    registerExitRoomIfNeeded(base.getWorld(), base, rotation, exitRoomForFloor.sizeX(), exitRoomForFloor.sizeZ(), finalTName, exitRoomForFloor, x, y);
                }));
                return;
            }

            if (exitRoomPairs != null && !exitRoomPairs.isEmpty()) {
                TemplatePair pair = pickPairPreferUnused(exitRoomPairs, random, usedExitRoomKeys);
                if (pair != null) {
                    int yOffset = 0;
                    Integer doorY = doorAnchorYFromPair(pair);
                    if (anchorDoorY >= 0 && doorY != null) {
                        yOffset = anchorDoorY - doorY;
                    } else {
                        int top = pair.walls() != null ? pair.walls().maxFilledY() : 0;
                        yOffset = (startTop - top) + 1;
                    }
                    if (dungeonSecrets != null && origin.getWorld() != null) {
                        dungeonSecrets.registerRoomGroup(origin.getWorld().getName(), groupKey, pair.baseName(), x, y);
                    }
                    if (dungeonMonsters != null && origin.getWorld() != null) {
                        dungeonMonsters.registerRoomGroup(origin.getWorld().getName(), groupKey, pair.baseName(), x, y);
                    }
                    queuePasteRoomPair(steps, origin.clone().add(0, yOffset, 0), pair, desired, 0, groupKey, pair.baseName(), dungeon, x, y);
                    return;
                }
            }

            if (exitRoomTemplates != null && !exitRoomTemplates.isEmpty()) {
                StructureTemplate template = pickTemplatePreferUnused(exitRoomTemplates, random, usedExitRoomKeys);
                String tName = templateName(template);
                int yOffset = 0;
                Integer doorY = doorAnchorYFromTemplate(template);
                if (anchorDoorY >= 0 && doorY != null) {
                    yOffset = anchorDoorY - doorY;
                } else {
                    int exitTop = template.maxFilledY();
                    yOffset = (startTop - exitTop);
                }
                StructureTemplate.Rotation rotation = pickBestRotation(template, desired, verbose, log, ctx + " exit");
                Location base = origin.clone().add(0, yOffset, 0);
                steps.add(new PasteBuildStep(template.rotate(rotation).pasteJob(base.getWorld(), base, null, tName)));
                if (dungeonSecrets != null && base.getWorld() != null && tName != null) {
                    dungeonSecrets.registerRoomGroup(base.getWorld().getName(), groupKey, tName, x, y);
                }
                if (dungeonMonsters != null && base.getWorld() != null && tName != null) {
                    dungeonMonsters.registerRoomGroup(base.getWorld().getName(), groupKey, tName, x, y);
                }
                steps.add(new RunnableBuildStep(() -> {
                    if (dungeonSecrets != null) {
                        dungeonSecrets.spawnGroupSecrets(base.getWorld(), base, rotation, template.sizeX(), template.sizeZ(), groupKey, tName);
                    }
                    if (dungeonMonsters != null) {
                        dungeonMonsters.spawnGroupMonsters(base.getWorld(), base, rotation, template.sizeX(), template.sizeZ(), groupKey, tName);
                    }
                    DungeonTemplateCommand.registerRoomPushers(Test_dungeons.getInstance(), base.getWorld(), base, rotation, template.sizeX(), template.sizeZ(), groupKey, tName);
                    registerPuzzleRoom06IfNeeded(base, rotation, template.sizeX(), template.sizeZ(), tName);
                    processRoomBlocks(base, rotation, template.sizeX(), template.sizeY(), template.sizeZ(), tName);
                    registerBossRoomIfNeeded(base.getWorld(), base, rotation, template.sizeX(), template.sizeZ(), tName, template, x, y);
                    registerExitRoomIfNeeded(base.getWorld(), base, rotation, template.sizeX(), template.sizeZ(), tName, template, x, y);
                }));
                return;
            }

            if (exitRoomPair != null) {
                int yOffset = 0;
                Integer doorY = doorAnchorYFromPair(exitRoomPair);
                if (anchorDoorY >= 0 && doorY != null) {
                    yOffset = anchorDoorY - doorY;
                } else {
                    int exitTop = exitRoomPair.walls() != null ? exitRoomPair.walls().maxFilledY() : 0;
                    yOffset = (startTop - exitTop);
                }
                if (dungeonSecrets != null && origin.getWorld() != null) {
                    dungeonSecrets.registerRoomGroup(origin.getWorld().getName(), groupKey, exitRoomPair.baseName(), x, y);
                }
                if (dungeonMonsters != null && origin.getWorld() != null) {
                    dungeonMonsters.registerRoomGroup(origin.getWorld().getName(), groupKey, exitRoomPair.baseName(), x, y);
                }
                queuePasteRoomPair(steps, origin.clone().add(0, yOffset, 0), exitRoomPair, desired, 0, groupKey, exitRoomPair.baseName(), dungeon, x, y);
                return;
            }

            if (exitRoomTemplate != null) {
                String tName = templateName(exitRoomTemplate);
                int yOffset = 0;
                Integer doorY = doorAnchorYFromTemplate(exitRoomTemplate);
                if (anchorDoorY >= 0 && doorY != null) {
                    yOffset = anchorDoorY - doorY;
                } else {
                    int exitTop = exitRoomTemplate.maxFilledY();
                    yOffset = (startTop - exitTop);
                }
                StructureTemplate.Rotation rotation = pickBestRotation(exitRoomTemplate, desired, verbose, log, ctx + " exit");
                Location base = origin.clone().add(0, yOffset, 0);
                steps.add(new PasteBuildStep(exitRoomTemplate.rotate(rotation).pasteJob(base.getWorld(), base, null, tName)));
                if (dungeonSecrets != null && base.getWorld() != null && tName != null) {
                    dungeonSecrets.registerRoomGroup(base.getWorld().getName(), groupKey, tName, x, y);
                }
                if (dungeonMonsters != null && base.getWorld() != null && tName != null) {
                    dungeonMonsters.registerRoomGroup(base.getWorld().getName(), groupKey, tName, x, y);
                }
                steps.add(new RunnableBuildStep(() -> {
                    if (dungeonSecrets != null) {
                        dungeonSecrets.spawnGroupSecrets(base.getWorld(), base, rotation, exitRoomTemplate.sizeX(), exitRoomTemplate.sizeZ(), groupKey, tName);
                    }
                    if (dungeonMonsters != null && !tName.toLowerCase(java.util.Locale.ROOT).startsWith("exit_room_f")) {
                        dungeonMonsters.spawnGroupMonsters(base.getWorld(), base, rotation, exitRoomTemplate.sizeX(), exitRoomTemplate.sizeZ(), groupKey, tName);
                    }
                    DungeonTemplateCommand.registerRoomPushers(Test_dungeons.getInstance(), base.getWorld(), base, rotation, exitRoomTemplate.sizeX(), exitRoomTemplate.sizeZ(), groupKey, tName);
                    registerPuzzleRoom06IfNeeded(base, rotation, exitRoomTemplate.sizeX(), exitRoomTemplate.sizeZ(), tName);
                    processRoomBlocks(base, rotation, exitRoomTemplate.sizeX(), exitRoomTemplate.sizeY(), exitRoomTemplate.sizeZ(), tName);
                    registerBossRoomIfNeeded(base.getWorld(), base, rotation, exitRoomTemplate.sizeX(), exitRoomTemplate.sizeZ(), tName, exitRoomTemplate, x, y);
                    registerExitRoomIfNeeded(base.getWorld(), base, rotation, exitRoomTemplate.sizeX(), exitRoomTemplate.sizeZ(), tName, exitRoomTemplate, x, y);
                }));
                return;
            }
        }

        if (bigRoomMode && room.getRoomType() == DungeonRoomType.PUZZLE) {
            List<BlockFace> desired = connectedDirections(dungeon, x, y);
            int startTop = startRoomTemplate != null ? startRoomTemplate.maxFilledY() : (startRoomPair != null && startRoomPair.walls() != null ? startRoomPair.walls().maxFilledY() : 0);

            if (puzzleRoomPairs != null && !puzzleRoomPairs.isEmpty()) {
                TemplatePair pair = pickPairPreferUnused(puzzleRoomPairs, random, usedPuzzleRoomKeys);
                if (pair != null) {
                    int yOffset = 0;
                    Integer doorY = doorAnchorYFromPair(pair);
                    if (anchorDoorY >= 0 && doorY != null) {
                        yOffset = anchorDoorY - doorY;
                    } else {
                        int puzzleTop = pair.walls() != null ? pair.walls().maxFilledY() : 0;
                        yOffset = (startTop - puzzleTop) + 1;
                    }
                    if (dungeonSecrets != null && origin.getWorld() != null) {
                        dungeonSecrets.registerRoomGroup(origin.getWorld().getName(), groupKey, pair.baseName(), x, y);
                    }
                    // 谜题房为解密房型，不注册地牢目标怪、不生成 DungeonMonsters（spawn 在 queuePasteRoomPair 内对 PUZZLE 跳过）
                    queuePasteRoomPair(steps, origin.clone().add(0, yOffset, 0), pair, desired, 0, groupKey, pair.baseName(), dungeon, x, y);
                    return;
                }
            }

            if (puzzleRoomTemplates != null && !puzzleRoomTemplates.isEmpty()) {
                StructureTemplate template = pickTemplatePreferUnused(puzzleRoomTemplates, random, usedPuzzleRoomKeys);
                String tName = templateName(template);
                int yOffset = 0;
                Integer doorY = doorAnchorYFromTemplate(template);
                if (anchorDoorY >= 0 && doorY != null) {
                    yOffset = anchorDoorY - doorY;
                } else {
                    int puzzleTop = template.maxFilledY();
                    yOffset = (startTop - puzzleTop) + 1;
                }
                StructureTemplate.Rotation rotation = pickBestRotation(template, desired, verbose, log, ctx + " puzzle");
                Location base = origin.clone().add(0, yOffset, 0);
                steps.add(new PasteBuildStep(template.rotate(rotation).pasteJob(base.getWorld(), base, null, tName)));
                if (dungeonSecrets != null && base.getWorld() != null && tName != null) {
                    dungeonSecrets.registerRoomGroup(base.getWorld().getName(), groupKey, tName, x, y);
                }
                // 谜题房为解密房型，不注册地牢目标怪、不生成 DungeonMonsters
                steps.add(new RunnableBuildStep(() -> {
                    if (dungeonSecrets != null) {
                        dungeonSecrets.spawnGroupSecrets(base.getWorld(), base, rotation, template.sizeX(), template.sizeZ(), groupKey, tName);
                    }
                    DungeonTemplateCommand.registerRoomPushers(Test_dungeons.getInstance(), base.getWorld(), base, rotation, template.sizeX(), template.sizeZ(), groupKey, tName);
                    registerPuzzleRoom06IfNeeded(base, rotation, template.sizeX(), template.sizeZ(), tName);
                    processRoomBlocks(base, rotation, template.sizeX(), template.sizeY(), template.sizeZ(), tName);
                    registerBossRoomIfNeeded(base.getWorld(), base, rotation, template.sizeX(), template.sizeZ(), tName, template, x, y);
                }));
                return;
            }
        }

        if (bigRoomMode && room.getRoomType() == DungeonRoomType.BASE && room.getStructureType() == null) {
            List<BlockFace> desired = connectedDirections(dungeon, x, y);
            int startTop = startRoomTemplate != null ? startRoomTemplate.maxFilledY() : (startRoomPair != null && startRoomPair.walls() != null ? startRoomPair.walls().maxFilledY() : 0);

            if (baseRoom1x1Pairs != null && !baseRoom1x1Pairs.isEmpty()) {
                int supportedCount = 0;
                List<TemplatePair> validPairs = new ArrayList<>();
                List<TemplatePair> supportedPairs = new ArrayList<>();
                for (TemplatePair p : baseRoom1x1Pairs) {
                    if (!supportsAllFaces(p, desired)) continue;
                    supportedCount++;
                    supportedPairs.add(p);
                    if (usedBaseRoomTemplateKeys != null && usedBaseRoomTemplateKeys.contains(keyForPair(p))) continue;
                    validPairs.add(p);
                }
                if (!validPairs.isEmpty()) {
                    TemplatePair pair = validPairs.get(random.nextInt(validPairs.size()));
                    if (usedBaseRoomTemplateKeys != null) usedBaseRoomTemplateKeys.add(keyForPair(pair));
                    int yOffset = 0;
                    Integer doorY = doorAnchorYFromPair(pair);
                    if (anchorDoorY >= 0 && doorY != null) {
                        yOffset = anchorDoorY - doorY;
                    } else {
                        int top = pair.walls() != null ? pair.walls().maxFilledY() : 0;
                        yOffset = (startTop - top) + 1;
                    }
                    if (dungeonSecrets != null && origin.getWorld() != null) {
                        dungeonSecrets.registerRoomGroup(origin.getWorld().getName(), groupKey, pair.baseName(), x, y);
                    }
                    if (dungeonMonsters != null && origin.getWorld() != null) {
                        dungeonMonsters.registerRoomGroup(origin.getWorld().getName(), groupKey, pair.baseName(), x, y);
                    }
                    queuePasteRoomPair(steps, origin.clone().add(0, yOffset, 0), pair, desired, 0, groupKey, pair.baseName(), dungeon, x, y);
                    return;
                }
                if (!supportedPairs.isEmpty()) {
                    TemplatePair pair = supportedPairs.get(random.nextInt(supportedPairs.size()));
                    if (usedBaseRoomTemplateKeys != null) usedBaseRoomTemplateKeys.add(keyForPair(pair));
                    int yOffset = 0;
                    Integer doorY = doorAnchorYFromPair(pair);
                    if (anchorDoorY >= 0 && doorY != null) {
                        yOffset = anchorDoorY - doorY;
                    } else {
                        int top = pair.walls() != null ? pair.walls().maxFilledY() : 0;
                        yOffset = (startTop - top) + 1;
                    }
                    if (verbose) {
                        log.info(ctx + " base reuse=base_room_1x1_pair desiredFaces=" + desired
                                + " supported=" + supportedCount + " used=" + (usedBaseRoomTemplateKeys != null ? usedBaseRoomTemplateKeys.size() : 0));
                    }
                    if (dungeonSecrets != null && origin.getWorld() != null) {
                        dungeonSecrets.registerRoomGroup(origin.getWorld().getName(), groupKey, pair.baseName(), x, y);
                    }
                    if (dungeonMonsters != null && origin.getWorld() != null) {
                        dungeonMonsters.registerRoomGroup(origin.getWorld().getName(), groupKey, pair.baseName(), x, y);
                    }
                    queuePasteRoomPair(steps, origin.clone().add(0, yOffset, 0), pair, desired, 0, groupKey, pair.baseName(), dungeon, x, y);
                    return;
                }
            }

            if (baseRoom1x1Templates != null && !baseRoom1x1Templates.isEmpty()) {
                int supportedCount = 0;
                List<StructureTemplate> validTemplates = new ArrayList<>();
                List<StructureTemplate> supportedTemplates = new ArrayList<>();
                for (StructureTemplate t : baseRoom1x1Templates) {
                    if (!supportsAllFaces(t, desired)) continue;
                    supportedCount++;
                    supportedTemplates.add(t);
                    if (usedBaseRoomTemplateKeys != null && usedBaseRoomTemplateKeys.contains(keyForTemplate(t))) continue;
                    validTemplates.add(t);
                }
                if (!validTemplates.isEmpty()) {
                    StructureTemplate template = validTemplates.get(random.nextInt(validTemplates.size()));
                    String tName = templateName(template);
                    if (usedBaseRoomTemplateKeys != null) usedBaseRoomTemplateKeys.add(keyForTemplate(template));
                    int yOffset = 0;
                    Integer doorY = doorAnchorYFromTemplate(template);
                    if (anchorDoorY >= 0 && doorY != null) {
                        yOffset = anchorDoorY - doorY;
                    } else {
                        int top = template.maxFilledY();
                        yOffset = (startTop - top) + 1;
                    }
                    StructureTemplate.Rotation rotation = pickBestRotation(template, desired, verbose, log, ctx + " base");
                    Location base = origin.clone().add(0, yOffset, 0);
                    steps.add(new PasteBuildStep(template.rotate(rotation).pasteJob(base.getWorld(), base, null, tName)));
                    if (dungeonSecrets != null && base.getWorld() != null && tName != null) {
                        dungeonSecrets.registerRoomGroup(base.getWorld().getName(), groupKey, tName, x, y);
                    }
                    if (dungeonMonsters != null && base.getWorld() != null && tName != null) {
                        dungeonMonsters.registerRoomGroup(base.getWorld().getName(), groupKey, tName, x, y);
                    }
                    steps.add(new RunnableBuildStep(() -> {
                        if (dungeonSecrets != null) {
                            dungeonSecrets.spawnGroupSecrets(base.getWorld(), base, rotation, template.sizeX(), template.sizeZ(), groupKey, tName);
                        }
                        if (dungeonMonsters != null) {
                            dungeonMonsters.spawnGroupMonsters(base.getWorld(), base, rotation, template.sizeX(), template.sizeZ(), groupKey, tName);
                        }
                        DungeonTemplateCommand.registerRoomPushers(Test_dungeons.getInstance(), base.getWorld(), base, rotation, template.sizeX(), template.sizeZ(), groupKey, tName);
                        registerPuzzleRoom06IfNeeded(base, rotation, template.sizeX(), template.sizeZ(), tName);
                        processRoomBlocks(base, rotation, template.sizeX(), template.sizeY(), template.sizeZ());
                        registerBossRoomIfNeeded(base.getWorld(), base, rotation, template.sizeX(), template.sizeZ(), tName, template, x, y);
                    }));
                    return;
                }
                if (!supportedTemplates.isEmpty()) {
                    StructureTemplate template = supportedTemplates.get(random.nextInt(supportedTemplates.size()));
                    String tName = templateName(template);
                    if (usedBaseRoomTemplateKeys != null) usedBaseRoomTemplateKeys.add(keyForTemplate(template));
                    int yOffset = 0;
                    Integer doorY = doorAnchorYFromTemplate(template);
                    if (anchorDoorY >= 0 && doorY != null) {
                        yOffset = anchorDoorY - doorY;
                    } else {
                        int top = template.maxFilledY();
                        yOffset = (startTop - top) + 1;
                    }
                    StructureTemplate.Rotation rotation = pickBestRotation(template, desired, verbose, log, ctx + " base");
                    if (verbose) {
                        log.info(ctx + " base reuse=base_room_1x1_template desiredFaces=" + desired
                                + " supported=" + supportedCount + " used=" + (usedBaseRoomTemplateKeys != null ? usedBaseRoomTemplateKeys.size() : 0));
                    }
                    Location base = origin.clone().add(0, yOffset, 0);
                    steps.add(new PasteBuildStep(template.rotate(rotation).pasteJob(base.getWorld(), base, null, tName)));
                    if (dungeonSecrets != null && base.getWorld() != null && tName != null) {
                        dungeonSecrets.registerRoomGroup(base.getWorld().getName(), groupKey, tName, x, y);
                    }
                    if (dungeonMonsters != null && base.getWorld() != null && tName != null) {
                        dungeonMonsters.registerRoomGroup(base.getWorld().getName(), groupKey, tName, x, y);
                    }
                    steps.add(new RunnableBuildStep(() -> {
                        if (dungeonSecrets != null) {
                            dungeonSecrets.spawnGroupSecrets(base.getWorld(), base, rotation, template.sizeX(), template.sizeZ(), groupKey, tName);
                        }
                        if (dungeonMonsters != null) {
                            dungeonMonsters.spawnGroupMonsters(base.getWorld(), base, rotation, template.sizeX(), template.sizeZ(), groupKey, tName);
                        }
                        DungeonTemplateCommand.registerRoomPushers(Test_dungeons.getInstance(), base.getWorld(), base, rotation, template.sizeX(), template.sizeZ(), groupKey, tName);
                        registerPuzzleRoom06IfNeeded(base, rotation, template.sizeX(), template.sizeZ(), tName);
                        processRoomBlocks(base, rotation, template.sizeX(), template.sizeY(), template.sizeZ());
                        registerBossRoomIfNeeded(base.getWorld(), base, rotation, template.sizeX(), template.sizeZ(), tName, template, x, y);
                    }));
                    return;
                }
            }
        }

        int finalBuildWidth = buildWidth;
        int finalBuildHeight = buildHeight;
        steps.add(new RunnableBuildStep(() -> drawRoom(origin, room, x, y, dungeon, bigRoomMode,
                startRoomTemplate, startRoomPair,
                healRoomTemplate, healRoomPair,
                exitRoomTemplate, exitRoomPair,
                exitRoomTemplates, exitRoomPairs,
                puzzleRoomTemplates, puzzleRoomPairs,
                trapRoomPairs, minibossRoomPairs,
                baseRoom1x1Templates, baseRoom1x1Pairs,
                anchorDoorY, usedBaseRoomTemplateKeys,
                usedTrapRoomKeys, usedMinibossRoomKeys, usedExitRoomKeys, usedPuzzleRoomKeys,
                random, verbose, log, finalBuildWidth, finalBuildHeight)));
    }

    private void queuePasteRoomPair(List<BuildStep> steps, Location origin, TemplatePair pair, List<BlockFace> desiredFaces, int yOffset, String groupKey, String templateName, SkyBlockDungeon dungeon, int cellX, int cellY) {
        if (pair == null || pair.walls() == null || pair.doors() == null) return;
        pair = resolvePuzzleRoom05VariantPair(pair);
        pair = normalizePair(pair);
        StructureTemplate walls = pair.walls();
        StructureTemplate doors = pair.doors();

        StructureTemplate.Rotation rotation = StructureTemplate.Rotation.R0;
        if (desiredFaces != null && !desiredFaces.isEmpty()) {
            List<StructureTemplate.Rotation> rotations = rankedRotations(doors, desiredFaces);
            if (!rotations.isEmpty()) rotation = rotations.get(0);
        }
        walls = walls.rotate(rotation);
        doors = doors.rotate(rotation);

        Location base = yOffset == 0 ? origin : origin.clone().add(0, yOffset, 0);
        steps.add(new PasteBuildStep(walls.pasteJob(base.getWorld(), base, null, templateName)));

        if (desiredFaces != null && !desiredFaces.isEmpty()) {
            Set<Long> include = new HashSet<>();
            Set<Long> coalInclude = new HashSet<>();
            for (BlockFace face : desiredFaces) {
                StructureTemplate.DoorMarker marker = pickBestMarkerForFacing(doors, face);
                if (marker == null) continue;
                StructureTemplate.addDoorOverlayIncludePositions(include, marker, doors.sizeX(), doors.sizeY(), doors.sizeZ());
                if (dungeon != null && shouldDoorMarkerHaveCoal(dungeon, cellX, cellY, face)) {
                    StructureTemplate.addDoorOverlayIncludePositions(coalInclude, marker, doors.sizeX(), doors.sizeY(), doors.sizeZ());
                }
            }
            if (dungeon != null) {
                SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(cellX, cellY);
                if (room != null && (room.getRoomType() == DungeonRoomType.TRAP || room.getRoomType() == DungeonRoomType.MINI_BOSS)) {
                    coalInclude.clear();
                }
            }
            if (!include.isEmpty()) {
                steps.add(new PasteBuildStep(doors.pasteJob(base.getWorld(), base, include, templateName, true, coalInclude)));
            }
        }
        StructureTemplate.Rotation finalRotation = rotation;
        Location finalBase = base.clone();
        int finalSizeX = pair.walls().sizeX();
        int finalSizeY = pair.walls().sizeY();
        int finalSizeZ = pair.walls().sizeZ();
        String finalGroupKey = groupKey;
        String finalTemplateName = templateName;
        List<BlockFace> desiredForNpc = desiredFaces != null ? List.copyOf(desiredFaces) : List.of();
        steps.add(new RunnableBuildStep(() -> {
            if (dungeonSecrets != null) {
                dungeonSecrets.spawnGroupSecrets(finalBase.getWorld(), finalBase, finalRotation, finalSizeX, finalSizeZ, finalGroupKey, finalTemplateName);
            }
            if (dungeonMonsters != null) {
                SkyBlockDungeon.DungeonRoom rr = dungeon != null ? dungeon.getRoom(cellX, cellY) : null;
                if (rr == null || rr.getRoomType() != DungeonRoomType.PUZZLE) {
                    dungeonMonsters.spawnGroupMonsters(finalBase.getWorld(), finalBase, finalRotation, finalSizeX, finalSizeZ, finalGroupKey, finalTemplateName);
                }
            }
            DungeonTemplateCommand.registerRoomPushers(Test_dungeons.getInstance(), finalBase.getWorld(), finalBase, finalRotation, finalSizeX, finalSizeZ, finalGroupKey, finalTemplateName);
            registerPuzzleRoom06IfNeeded(finalBase, finalRotation, finalSizeX, finalSizeZ, finalTemplateName);
            if ("start_room".equalsIgnoreCase(finalTemplateName)) {
                Test_dungeons.getInstance().registerStartRoomNpc(finalBase.getWorld(), finalBase, finalRotation, finalSizeX, finalSizeZ, desiredForNpc);
            }
            processRoomBlocks(finalBase, finalRotation, finalSizeX, finalSizeY, finalSizeZ, finalTemplateName);
        }));
    }

    private Location buildDungeon(Location startLoc, SkyBlockDungeon dungeon, int width, int height, boolean verbose, Logger log) {
        StructureTemplate startRoomTemplate = loadTemplateOrNull("start_room");
        List<StructureTemplate> puzzleRoomTemplates = loadTemplatesByPrefix("puzzle_room_");
        TemplatePair startRoomPair = loadTemplatePairOrNull("start_room");
        List<TemplatePair> puzzleRoomPairs = loadTemplatePairsByPrefix("puzzle_room_");
        StructureTemplate healRoomTemplate = loadTemplateOrNull("heal_room");
        TemplatePair healRoomPair = loadTemplatePairOrNull("heal_room");
        StructureTemplate exitRoomTemplate = loadTemplateOrNull("exit_room");
        TemplatePair exitRoomPair = loadTemplatePairOrNull("exit_room");
        List<StructureTemplate> exitRoomTemplates = loadTemplatesByPrefix("exit_room_");
        List<TemplatePair> exitRoomPairs = loadTemplatePairsByPrefix("exit_room_");
        List<TemplatePair> trapRoomPairs = loadTemplatePairsByPrefix("trap_room_");
        List<TemplatePair> minibossRoomPairs = loadTemplatePairsByPrefix("miniboss_room_");
        List<TemplatePair> baseRoom1x1Pairs = loadTemplatePairsByPrefix("base_room_1x1_");
        List<StructureTemplate> baseRoom1x1Templates = loadTemplatesByPrefix("base_room_1x1_");

        boolean bigRoomMode =
                startRoomTemplate != null
                        || startRoomPair != null
                        || healRoomTemplate != null
                        || healRoomPair != null
                        || exitRoomTemplate != null
                        || exitRoomPair != null
                        || (exitRoomTemplates != null && !exitRoomTemplates.isEmpty())
                        || (exitRoomPairs != null && !exitRoomPairs.isEmpty())
                        || (puzzleRoomTemplates != null && !puzzleRoomTemplates.isEmpty())
                        || (puzzleRoomPairs != null && !puzzleRoomPairs.isEmpty())
                        || (trapRoomPairs != null && !trapRoomPairs.isEmpty())
                        || (minibossRoomPairs != null && !minibossRoomPairs.isEmpty())
                        || (baseRoom1x1Pairs != null && !baseRoom1x1Pairs.isEmpty())
                        || (baseRoom1x1Templates != null && !baseRoom1x1Templates.isEmpty());
        int roomScale = bigRoomMode ? 32 : 5;
        Random random = new Random();
        Set<String> usedBaseRoomTemplateKeys = new HashSet<>();
        Set<String> usedTrapRoomKeys = new HashSet<>();
        Set<String> usedMinibossRoomKeys = new HashSet<>();
        Set<String> usedExitRoomKeys = new HashSet<>();
        Set<String> usedPuzzleRoomKeys = new HashSet<>();

        if (verbose) {
            log.info("[Build][V] bigRoomMode=" + bigRoomMode
                    + " roomScale=" + roomScale
                    + " startRoomTemplate=" + (startRoomTemplate != null)
                    + " startRoomPair=" + (startRoomPair != null)
                    + " healRoomTemplate=" + (healRoomTemplate != null)
                    + " healRoomPair=" + (healRoomPair != null)
                    + " exitRoomTemplate=" + (exitRoomTemplate != null)
                    + " exitRoomPair=" + (exitRoomPair != null)
                    + " puzzleRoomTemplates=" + (puzzleRoomTemplates != null ? puzzleRoomTemplates.size() : 0)
                    + " puzzleRoomPairs=" + (puzzleRoomPairs != null ? puzzleRoomPairs.size() : 0)
                    + " trapRoomPairs=" + (trapRoomPairs != null ? trapRoomPairs.size() : 0)
                    + " minibossRoomPairs=" + (minibossRoomPairs != null ? minibossRoomPairs.size() : 0));
        }

        int startTop = 0;
        if (startRoomTemplate != null) {
            startTop = startRoomTemplate.maxFilledY();
        } else if (startRoomPair != null && startRoomPair.walls() != null) {
            startTop = startRoomPair.walls().maxFilledY();
        }

        int anchorDoorY = -1;
        if (bigRoomMode) {
            Integer startDoor = doorAnchorYFromStart(startRoomPair, startRoomTemplate);
            if (startDoor != null) anchorDoorY = startDoor;
        }

        Test_dungeons.getInstance().setLastDungeon(dungeon, width, height, startLoc, roomScale, anchorDoorY);

        Set<UUID> pastedStructures = bigRoomMode ? pasteAllStructures(startLoc, dungeon, width, height, roomScale, random, startTop, anchorDoorY, usedBaseRoomTemplateKeys, verbose, log) : Set.of();

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(x, y);
                if (room == null) continue;
                if (bigRoomMode && room.getStructureId() != null && pastedStructures.contains(room.getStructureId())) continue;

                Location roomOrigin = startLoc.clone().add(x * roomScale, 0, y * roomScale);
                drawRoom(roomOrigin, room, x, y, dungeon, bigRoomMode, startRoomTemplate, startRoomPair, healRoomTemplate, healRoomPair, exitRoomTemplate, exitRoomPair, exitRoomTemplates, exitRoomPairs, puzzleRoomTemplates, puzzleRoomPairs, trapRoomPairs, minibossRoomPairs, baseRoom1x1Templates, baseRoom1x1Pairs, anchorDoorY, usedBaseRoomTemplateKeys, usedTrapRoomKeys, usedMinibossRoomKeys, usedExitRoomKeys, usedPuzzleRoomKeys, random, verbose, log, width, height);
            }
        }

        return findStartRoomTeleport(startLoc, dungeon, width, height, roomScale, startTop, verbose, log);
    }

    private World createVoidWorld() {
        WorldCreator creator = new WorldCreator("dungeon_" + UUID.randomUUID());
        creator.environment(World.Environment.NORMAL);
        creator.generateStructures(false);
        creator.generator(new VoidChunkGenerator());
        World world = Bukkit.createWorld(creator);
        if (world == null) {
            throw new IllegalStateException("Failed to create void world");
        }
        world.setDifficulty(Difficulty.HARD);
        world.setSpawnFlags(true, true);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, true);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        world.setTime(18000);
        world.setStorm(false);
        world.setThundering(false);
        return world;
    }

    private Location findStartRoomTeleport(Location dungeonOrigin, SkyBlockDungeon dungeon, int width, int height, int roomScale, int startRoomTopY, boolean verbose, Logger log) {
        int entranceX = -1;
        int entranceY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(x, y);
                if (room != null && room.getRoomType() == DungeonRoomType.ENTRANCE) {
                    entranceX = x;
                    entranceY = y;
                    break;
                }
            }
            if (entranceX != -1) break;
        }

        Location fallback = dungeonOrigin.clone().add(roomScale / 2.0, 1.0, roomScale / 2.0);
        if (entranceX == -1) {
            if (verbose) {
                log.info("[Build][V] teleport scan: no ENTRANCE room, fallback=" + fmtLoc(fallback));
            }
            return fallback;
        }

        World world = dungeonOrigin.getWorld();
        if (world == null) {
            if (verbose) {
                log.info("[Build][V] teleport scan: world=null, fallback=" + fmtLoc(fallback));
            }
            return fallback;
        }

        Location fixed = entranceFixedLocalSpawn(dungeonOrigin, dungeon, entranceX, entranceY, width, height, roomScale, verbose, log);
        if (fixed != null) return fixed;

        BlockFace doorFace = pickEntranceDoorFace(dungeon, entranceX, entranceY, width, height);
        if (doorFace != null && doorFace != BlockFace.SELF) {
            return entranceSpawnLocation(dungeonOrigin, entranceX, entranceY, roomScale, doorFace);
        }

        Location roomOrigin = dungeonOrigin.clone().add(entranceX * roomScale, 0, entranceY * roomScale);
        int minX = roomOrigin.getBlockX();
        int minZ = roomOrigin.getBlockZ();
        int minY = roomOrigin.getBlockY() - 2;
        int maxY = roomOrigin.getBlockY() + Math.max(startRoomTopY + 5, 12);

        Location best = null;
        int matches = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x < minX + roomScale; x++) {
                for (int z = minZ; z < minZ + roomScale; z++) {
                    if (world.getBlockAt(x, y, z).getType() != Material.LIME_CONCRETE) continue;
                    if (!world.getBlockAt(x, y + 1, z).getType().isAir()) continue;
                    best = new Location(world, x + 0.5, y + 1.0, z + 0.5);
                    matches++;
                    if (verbose) {
                        log.info("[Build][V] teleport scan: lime_concrete at (" + x + "," + y + "," + z + ") -> candidate=" + fmtLoc(best));
                    }
                }
            }
        }

        if (best != null) {
            if (verbose) {
                log.info("[Build][V] teleport scan: entranceCell=(" + entranceX + "," + entranceY + ") matches=" + matches + " chosen=" + fmtLoc(best));
            }
            return best;
        }
        Location center = new Location(world, roomOrigin.getX() + roomScale / 2.0, roomOrigin.getY() + 1.0, roomOrigin.getZ() + roomScale / 2.0);
        if (verbose) {
            log.info("[Build][V] teleport scan: no lime_concrete found, entranceCell=(" + entranceX + "," + entranceY + ") center=" + fmtLoc(center));
        }
        return center;
    }

    private Location entranceFixedLocalSpawn(Location dungeonOrigin, SkyBlockDungeon dungeon, int entranceX, int entranceY, int width, int height, int roomScale, boolean verbose, Logger log) {
        if (roomScale < 20) return null;
        if (dungeonOrigin == null || dungeon == null) return null;
        World world = dungeonOrigin.getWorld();
        if (world == null) return null;

        BlockFace doorFace = pickEntranceDoorFace(dungeon, entranceX, entranceY, width, height);
        if (doorFace == null || doorFace == BlockFace.SELF) return null;

        List<BlockFace> desired = connectedDirections(dungeon, entranceX, entranceY);

        int localX0 = 19;
        int localY0 = 7;
        int localZ0 = 15;

        StructureTemplate.Rotation rotation = StructureTemplate.Rotation.R0;
        int[] localXZ = null;

        TemplatePair startPair = loadTemplatePairOrNull("start_room");
        if (startPair != null && startPair.walls() != null && startPair.doors() != null) {
            TemplatePair normalized = normalizePair(startPair);
            StructureTemplate walls = normalized.walls();
            StructureTemplate doors = normalized.doors();

            if (desired != null && !desired.isEmpty()) {
                List<StructureTemplate.Rotation> rotations = rankedRotations(doors, desired);
                if (!rotations.isEmpty()) rotation = rotations.get(0);
            }
            localXZ = rotateXZLocal(localX0, localZ0, rotation, walls.sizeX(), walls.sizeZ());
        } else {
            StructureTemplate startTemplate = loadTemplateOrNull("start_room");
            if (startTemplate == null) return null;
            rotation = pickBestRotation(startTemplate, desired, verbose, log, "[Build][V] teleport fixedLocal");
            localXZ = rotateXZLocal(localX0, localZ0, rotation, startTemplate.sizeX(), startTemplate.sizeZ());
        }

        if (localXZ == null) return null;

        Location roomOrigin = dungeonOrigin.clone().add(entranceX * roomScale, 0, entranceY * roomScale);
        int minX = roomOrigin.getBlockX();
        int minZ = roomOrigin.getBlockZ();
        int maxX = minX + roomScale - 1;
        int maxZ = minZ + roomScale - 1;

        int spawnBlockX = clampInt(minX + localXZ[0], minX, maxX);
        int spawnBlockY = roomOrigin.getBlockY() + localY0;
        int spawnBlockZ = clampInt(minZ + localXZ[1], minZ, maxZ);

        int centerX = minX + roomScale / 2;
        int centerZ = minZ + roomScale / 2;
        int doorX = centerX;
        int doorZ = centerZ;
        switch (doorFace) {
            case NORTH -> doorZ = minZ;
            case SOUTH -> doorZ = maxZ;
            case EAST -> doorX = maxX;
            case WEST -> doorX = minX;
            default -> {
            }
        }

        Location spawn = new Location(world, spawnBlockX + 0.5, spawnBlockY, spawnBlockZ + 0.5);
        Location door = new Location(world, doorX + 0.5, spawnBlockY, doorZ + 0.5);
        spawn.setDirection(door.toVector().subtract(spawn.toVector()));
        return spawn;
    }

    private static int[] rotateXZLocal(int x, int z, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
        return switch (rotation) {
            case R0 -> new int[]{x, z};
            case R90 -> new int[]{z, sizeX - 1 - x};
            case R180 -> new int[]{sizeX - 1 - x, sizeZ - 1 - z};
            case R270 -> new int[]{sizeZ - 1 - z, x};
        };
    }

    private BlockFace pickEntranceDoorFace(SkyBlockDungeon dungeon, int x, int y, int width, int height) {
        if (dungeon == null) return null;
        if (y - 1 >= 0 && y - 1 < height && dungeon.getRoom(x, y - 1) != null && dungeon.isConnected(x, y, x, y - 1)) return BlockFace.NORTH;
        if (x + 1 >= 0 && x + 1 < width && dungeon.getRoom(x + 1, y) != null && dungeon.isConnected(x, y, x + 1, y)) return BlockFace.EAST;
        if (y + 1 >= 0 && y + 1 < height && dungeon.getRoom(x, y + 1) != null && dungeon.isConnected(x, y, x, y + 1)) return BlockFace.SOUTH;
        if (x - 1 >= 0 && x - 1 < width && dungeon.getRoom(x - 1, y) != null && dungeon.isConnected(x, y, x - 1, y)) return BlockFace.WEST;
        List<BlockFace> faces = connectedDirections(dungeon, x, y);
        if (faces == null || faces.isEmpty()) return null;
        return faces.get(0);
    }

    private Location entranceSpawnLocation(Location dungeonOrigin, int entranceX, int entranceY, int roomScale, BlockFace doorFace) {
        World world = dungeonOrigin.getWorld();
        if (world == null) return dungeonOrigin.clone().add(roomScale / 2.0, 70.0, roomScale / 2.0);

        Location roomOrigin = dungeonOrigin.clone().add(entranceX * roomScale, 0, entranceY * roomScale);
        int minX = roomOrigin.getBlockX();
        int minZ = roomOrigin.getBlockZ();
        int maxX = minX + roomScale - 1;
        int maxZ = minZ + roomScale - 1;

        int centerX = minX + roomScale / 2;
        int centerZ = minZ + roomScale / 2;

        int doorX = centerX;
        int doorZ = centerZ;
        int dx = 0;
        int dz = 0;
        switch (doorFace) {
            case NORTH -> {
                doorZ = minZ;
                dz = -1;
            }
            case SOUTH -> {
                doorZ = maxZ;
                dz = 1;
            }
            case EAST -> {
                doorX = maxX;
                dx = 1;
            }
            case WEST -> {
                doorX = minX;
                dx = -1;
            }
            default -> {
                dx = 0;
                dz = 0;
            }
        }

        int spawnX = clampInt(doorX - dx * 5, minX, maxX);
        int spawnZ = clampInt(doorZ - dz * 5, minZ, maxZ);

        Location spawn = new Location(world, spawnX + 0.5, 70.0, spawnZ + 0.5);
        Location door = new Location(world, doorX + 0.5, 70.0, doorZ + 0.5);
        spawn.setDirection(door.toVector().subtract(spawn.toVector()));
        return spawn;
    }

    private static int clampInt(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static String fmtLoc(Location loc) {
        if (loc == null) return "null";
        World w = loc.getWorld();
        String wn = w != null ? w.getName() : "null";
        return wn + "@" + loc.getX() + "," + loc.getY() + "," + loc.getZ();
    }

    private static String fmtRoomShort(SkyBlockDungeon.DungeonRoom room) {
        if (room == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append(room.getRoomType().name());
        if (room.isCritical()) sb.append(":C");
        if (room.getStage() > 0) sb.append(":S").append(room.getStage());
        if (room.getCorridorNumber() > 0) sb.append(":R").append(room.getCorridorNumber());
        if (room.getStructureType() != null) sb.append(":").append(room.getStructureType().name());
        if (room.getStructureId() != null) sb.append(":").append(room.getStructureId().toString(), 0, 8);
        return sb.toString();
    }

    private void logRoomsVerbose(SkyBlockDungeon dungeon, int width, int height, Logger log) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                SkyBlockDungeon.DungeonRoom r = dungeon.getRoom(x, y);
                if (r == null) continue;
                List<BlockFace> faces = connectedDirections(dungeon, x, y);
                log.info("[Build][V] room (" + x + "," + y + ") " + fmtRoomShort(r) + " doors=" + faces.size() + " faces=" + faces);
            }
        }
    }

    private static final class VoidChunkGenerator extends ChunkGenerator {
        @Override
        public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
            return createChunkData(world);
        }
    }

    private Set<UUID> pasteAllStructures(Location startLoc, SkyBlockDungeon dungeon, int width, int height, int roomScale, Random random, int referenceTopY, int anchorDoorY, Set<String> usedBaseRoomTemplateKeys, boolean verbose, Logger log) {
        List<TemplatePair> pairs1x2 = loadTemplatePairsByPrefix("base_room_1x2_");
        List<TemplatePair> pairs2x1 = loadTemplatePairsByPrefix("base_room_2x1_");
        List<TemplatePair> pairs2x2 = loadTemplatePairsByPrefix("base_room_2x2_");
        List<TemplatePair> pairs3x1 = loadTemplatePairsByPrefix("base_room_3x1_");
        List<TemplatePair> pairsL = loadTemplatePairsByPrefix("base_room_l_");

        Set<UUID> placed = new HashSet<>();
        Set<UUID> pasted = new HashSet<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(x, y);
                if (room == null) continue;
                UUID id = room.getStructureId();
                if (id == null) continue;
                if (placed.contains(id)) continue;

                int[] bounds = findStructureBounds(dungeon, width, height, id);
                int minX = bounds[0];
                int minY = bounds[1];
                int maxX = bounds[2];
                int maxY = bounds[3];
                int cellsX = (maxX - minX) + 1;
                int cellsY = (maxY - minY) + 1;

                DungeonStructureType structureType = room.getStructureType();
                boolean uniqueRequired = room.getRoomType() == DungeonRoomType.BASE;
                TemplatePair pair = pickPairForStructure(structureType, pairs1x2, pairs2x1, pairs2x2, pairs3x1, pairsL, random, usedBaseRoomTemplateKeys, uniqueRequired);
                if (pair == null || pair.walls() == null || pair.doors() == null) {
                    placed.add(id);
                    continue;
                }

                StructureTemplate.Rotation rotation = pickRotationForFootprint(pair.walls(), cellsX, cellsY);
                if (structureType == DungeonStructureType.L_SHAPE && cellsX == 2 && cellsY == 2) {
                    rotation = pickRotationForLShape(dungeon, id, minX, minY, maxX, maxY, pair.walls());
                }
                StructureTemplate walls = pair.walls().rotate(rotation);
                StructureTemplate doors = pair.doors().rotate(rotation);

                Location origin = startLoc.clone().add(minX * roomScale, 0, minY * roomScale);
                int yOffset = 0;
                Integer doorY = doorAnchorYFromPair(pair);
                boolean doorAligned = anchorDoorY >= 0 && doorY != null;
                if (doorAligned) {
                    yOffset = anchorDoorY - doorY;
                } else if (referenceTopY > 0) {
                    yOffset = (referenceTopY - walls.maxFilledY()) + 1;
                }
                if (!doorAligned) {
                    if (structureType == DungeonStructureType.L_SHAPE) {
                        yOffset += 1;
                    } else if (structureType == DungeonStructureType.TRIPLE_3x1) {
                        yOffset -= 1;
                    }
                }
                Location base = yOffset == 0 ? origin : origin.clone().add(0, yOffset, 0);
                String ctx = "[Build][V] structure type=" + (structureType != null ? structureType.name() : "null")
                        + " id=" + id.toString().substring(0, 8)
                        + " bounds=(" + minX + "," + minY + ")-(" + maxX + "," + maxY + ")"
                        + " rotation=" + rotation.name()
                        + " yOffset=" + yOffset;
                if (verbose) {
                    log.info(ctx + " walls=" + walls.sizeX() + "x" + walls.sizeY() + "x" + walls.sizeZ()
                            + " doors=" + doors.sizeX() + "x" + doors.sizeY() + "x" + doors.sizeZ()
                            + " markers=" + doors.doorMarkers().size());
                }
                boolean ok = false;
                for (int attempt = 0; attempt < 8 && !ok; attempt++) {
                    walls.paste(base.getWorld(), base);

                    Set<Long> include = new HashSet<>();
                    Set<Long> coalInclude = new HashSet<>();
                    List<ExpectedDoor> expected = new ArrayList<>();
                    for (StructureTemplate.DoorMarker m : doors.doorMarkers()) {
                        BlockFace resolvedFacing = StructureTemplate.resolveDoorFacing(m, doors.sizeX(), doors.sizeZ());
                        if (resolvedFacing == BlockFace.SELF) continue;
                        StructureTemplate.DoorMarker resolved = new StructureTemplate.DoorMarker(m.x(), m.y(), m.z(), resolvedFacing);
                        boolean open = shouldOpenDoorMarker(dungeon, width, height, id, minX, minY, resolved, roomScale, verbose, log, ctx);
                        if (!open) continue;
                        StructureTemplate.addDoorOverlayIncludePositions(include, m, doors.sizeX(), doors.sizeY(), doors.sizeZ());
                        int markerCellX = minX + (resolved.x() / roomScale);
                        int markerCellY = minY + (resolved.z() / roomScale);
                        if (shouldDoorMarkerHaveCoal(dungeon, markerCellX, markerCellY, resolvedFacing)) {
                            StructureTemplate.addDoorOverlayIncludePositions(coalInclude, m, doors.sizeX(), doors.sizeY(), doors.sizeZ());
                        }
                        expected.add(new ExpectedDoor(m, resolvedFacing));
                    }
                    if (verbose) {
                        log.info(ctx + " attempt=" + attempt + " openDoors=" + expected.size() + " includeBlocks=" + include.size());
                        for (ExpectedDoor e : expected) {
                            StructureTemplate.DoorMarker m = e.marker();
                            log.info(ctx + " open marker=(" + m.x() + "," + m.y() + "," + m.z() + ") expected=" + e.expectedFacing().name());
                        }
                    }
                    if (!include.isEmpty()) {
                        doors.paste(base.getWorld(), base, include, coalInclude);
                    }
                    ok = verifyExpectedDoors(base, doors, expected, verbose, log, ctx + " verify");
                    if (verbose) {
                        log.info(ctx + " attempt=" + attempt + " verifyOk=" + ok);
                    }
                }
                if (ok) {
                    processRoomBlocks(base, rotation, pair.walls().sizeX(), pair.walls().sizeY(), pair.walls().sizeZ(), pair.baseName());
                    pasted.add(id);
                } else if (verbose) {
                    log.info(ctx + " result=failedAllAttempts");
                }

                placed.add(id);
            }
        }
        return pasted;
    }

    private static int[] findStructureBounds(SkyBlockDungeon dungeon, int width, int height, UUID id) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                SkyBlockDungeon.DungeonRoom r = dungeon.getRoom(x, y);
                if (r == null) continue;
                if (!id.equals(r.getStructureId())) continue;
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
            }
        }

        if (minX == Integer.MAX_VALUE) return new int[]{0, 0, -1, -1};
        return new int[]{minX, minY, maxX, maxY};
    }

    private TemplatePair pickPairFromList(List<TemplatePair> list, Random random, Set<String> usedBaseRoomTemplateKeys, boolean uniqueRequired) {
        if (list == null || list.isEmpty()) return null;
        if (!uniqueRequired || usedBaseRoomTemplateKeys == null) {
            return normalizePair(list.get(random.nextInt(list.size())));
        }
        List<TemplatePair> candidates = new ArrayList<>();
        for (TemplatePair p : list) {
            if (p == null) continue;
            if (usedBaseRoomTemplateKeys.contains(keyForPair(p))) continue;
            candidates.add(p);
        }
        TemplatePair chosen = candidates.isEmpty()
                ? list.get(random.nextInt(list.size()))
                : candidates.get(random.nextInt(candidates.size()));
        usedBaseRoomTemplateKeys.add(keyForPair(chosen));
        return normalizePair(chosen);
    }

    /** 從列表中優先選未使用過的模板（本局不重複）；用罄後允許重用 */
    private TemplatePair pickPairPreferUnused(List<TemplatePair> list, Random random, Set<String> used) {
        if (list == null || list.isEmpty()) return null;
        if (used == null) return normalizePair(list.get(random.nextInt(list.size())));
        List<TemplatePair> candidates = new ArrayList<>();
        for (TemplatePair p : list) {
            if (p == null) continue;
            if (used.contains(keyForPair(p))) continue;
            candidates.add(p);
        }
        TemplatePair chosen = candidates.isEmpty()
                ? list.get(random.nextInt(list.size()))
                : candidates.get(random.nextInt(candidates.size()));
        used.add(keyForPair(chosen));
        return normalizePair(chosen);
    }

    /** 從模板列表中優先選未使用過的（本局不重複）；用罄後允許重用 */
    private StructureTemplate pickTemplatePreferUnused(List<StructureTemplate> list, Random random, Set<String> used) {
        if (list == null || list.isEmpty()) return null;
        if (used == null) return list.get(random.nextInt(list.size()));
        List<StructureTemplate> candidates = new ArrayList<>();
        for (StructureTemplate t : list) {
            if (t == null) continue;
            if (used.contains(keyForTemplate(t))) continue;
            candidates.add(t);
        }
        StructureTemplate chosen = candidates.isEmpty()
                ? list.get(random.nextInt(list.size()))
                : candidates.get(random.nextInt(candidates.size()));
        used.add(keyForTemplate(chosen));
        return chosen;
    }

    private TemplatePair pickPairForStructure(DungeonStructureType type, List<TemplatePair> pairs1x2, List<TemplatePair> pairs2x1, List<TemplatePair> pairs2x2, List<TemplatePair> pairs3x1, List<TemplatePair> pairsL, Random random, Set<String> usedBaseRoomTemplateKeys, boolean uniqueRequired) {
        if (type == null) return null;
        List<TemplatePair> list = switch (type) {
            case DOUBLE_1x2 -> pairs1x2;
            case DOUBLE_2x1 -> pairs2x1;
            case QUAD_2x2 -> pairs2x2;
            case TRIPLE_3x1 -> pairs3x1;
            case L_SHAPE -> pairsL;
        };
        return pickPairFromList(list, random, usedBaseRoomTemplateKeys, uniqueRequired);
    }

    private TemplatePair pickPairForStructureOrFootprint(
            DungeonStructureType type,
            int cellsX,
            int cellsY,
            List<TemplatePair> pairs1x2,
            List<TemplatePair> pairs2x1,
            List<TemplatePair> pairs2x2,
            List<TemplatePair> pairs3x1,
            List<TemplatePair> pairs1x4,
            List<TemplatePair> pairsL,
            Random random,
            Set<String> usedBaseRoomTemplateKeys,
            boolean uniqueRequired
    ) {
        if (type == DungeonStructureType.L_SHAPE) {
            return pickPairFromList(pairsL, random, usedBaseRoomTemplateKeys, uniqueRequired);
        }

        int a = Math.min(cellsX, cellsY);
        int b = Math.max(cellsX, cellsY);

        if (a == 1 && b == 4) {
            TemplatePair p = pickPairFromList(pairs1x4, random, usedBaseRoomTemplateKeys, uniqueRequired);
            if (p != null) return p;
        }

        if (a == 1 && b == 3) {
            TemplatePair p = pickPairFromList(pairs3x1, random, usedBaseRoomTemplateKeys, uniqueRequired);
            if (p != null) return p;
        }

        if (a == 1 && b == 2) {
            List<TemplatePair> primary = (cellsX == 2) ? pairs2x1 : pairs1x2;
            List<TemplatePair> fallback = (cellsX == 2) ? pairs1x2 : pairs2x1;
            TemplatePair p = pickPairFromList(primary, random, usedBaseRoomTemplateKeys, uniqueRequired);
            if (p != null) return p;
            p = pickPairFromList(fallback, random, usedBaseRoomTemplateKeys, uniqueRequired);
            if (p != null) return p;
        }

        if (cellsX == 2 && cellsY == 2) {
            TemplatePair p = pickPairFromList(pairs2x2, random, usedBaseRoomTemplateKeys, uniqueRequired);
            if (p != null) return p;
        }

        return pickPairForStructure(type, pairs1x2, pairs2x1, pairs2x2, pairs3x1, pairsL, random, usedBaseRoomTemplateKeys, uniqueRequired);
    }

    private static TemplatePair normalizePair(TemplatePair pair) {
        if (pair == null || pair.walls() == null || pair.doors() == null) return pair;
        StructureTemplate walls = pair.walls();
        StructureTemplate doors = pair.doors();

        int best = Integer.MAX_VALUE;
        StructureTemplate.Rotation bestRotation = StructureTemplate.Rotation.R0;
        for (StructureTemplate.Rotation r : StructureTemplate.Rotation.values()) {
            StructureTemplate rotated = doors.rotate(r);
            int diff = Math.abs(rotated.sizeX() - walls.sizeX()) + Math.abs(rotated.sizeZ() - walls.sizeZ());
            if (diff < best) {
                best = diff;
                bestRotation = r;
            }
        }

        if (bestRotation == StructureTemplate.Rotation.R0) return pair;
        return new TemplatePair(pair.baseName(), walls, doors.rotate(bestRotation));
    }

    private static Integer doorAnchorYFromStart(TemplatePair startRoomPair, StructureTemplate startRoomTemplate) {
        Integer y = doorAnchorYFromPair(startRoomPair);
        if (y != null) return y;
        return doorAnchorYFromTemplate(startRoomTemplate);
    }

    private static Integer doorAnchorYFromPair(TemplatePair pair) {
        if (pair == null) return null;
        TemplatePair normalized = normalizePair(pair);
        Integer y = doorAnchorYFromTemplate(normalized != null ? normalized.doors() : null);
        if (y != null) return y;
        return doorAnchorYFromTemplate(normalized != null ? normalized.walls() : null);
    }

    private static Integer doorAnchorYFromTemplate(StructureTemplate template) {
        if (template == null) return null;
        List<StructureTemplate.DoorMarker> markers = template.doorMarkers();
        if (markers == null || markers.isEmpty()) return null;

        HashMap<Integer, Integer> counts = new HashMap<>();
        for (StructureTemplate.DoorMarker m : markers) {
            counts.put(m.y(), counts.getOrDefault(m.y(), 0) + 1);
        }
        int bestY = markers.get(0).y();
        int bestCount = -1;
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            int y = e.getKey();
            int c = e.getValue();
            if (c > bestCount || (c == bestCount && y < bestY)) {
                bestCount = c;
                bestY = y;
            }
        }
        return bestY;
    }

    private static StructureTemplate.Rotation pickRotationForFootprint(StructureTemplate template, int cellsX, int cellsY) {
        int expectedX = cellsX * 32 - 1;
        int expectedZ = cellsY * 32 - 1;

        int best = Integer.MAX_VALUE;
        StructureTemplate.Rotation bestRotation = StructureTemplate.Rotation.R0;
        for (StructureTemplate.Rotation r : StructureTemplate.Rotation.values()) {
            StructureTemplate rotated = template.rotate(r);
            int diff = Math.abs(rotated.sizeX() - expectedX) + Math.abs(rotated.sizeZ() - expectedZ);
            if (diff < best) {
                best = diff;
                bestRotation = r;
            }
        }
        return bestRotation;
    }

    private enum Corner {
        NW,
        NE,
        SW,
        SE
    }

    private static StructureTemplate.Rotation pickRotationForLShape(SkyBlockDungeon dungeon, UUID structureId, int minX, int minY, int maxX, int maxY, StructureTemplate template) {
        Corner dungeonMissing = missingCornerInDungeon(dungeon, structureId, minX, minY, maxX, maxY);
        if (dungeonMissing == null) return StructureTemplate.Rotation.R0;
        Corner templateMissing = missingCornerInTemplate(template);
        if (templateMissing == null) return StructureTemplate.Rotation.R0;

        for (StructureTemplate.Rotation r : StructureTemplate.Rotation.values()) {
            if (rotateCorner(templateMissing, r) == dungeonMissing) {
                return r;
            }
        }
        return StructureTemplate.Rotation.R0;
    }

    private static Corner missingCornerInDungeon(SkyBlockDungeon dungeon, UUID structureId, int minX, int minY, int maxX, int maxY) {
        boolean nw = hasStructureId(dungeon, structureId, minX, minY);
        boolean ne = hasStructureId(dungeon, structureId, maxX, minY);
        boolean sw = hasStructureId(dungeon, structureId, minX, maxY);
        boolean se = hasStructureId(dungeon, structureId, maxX, maxY);

        int present = (nw ? 1 : 0) + (ne ? 1 : 0) + (sw ? 1 : 0) + (se ? 1 : 0);
        if (present != 3) return null;

        if (!nw) return Corner.NW;
        if (!ne) return Corner.NE;
        if (!sw) return Corner.SW;
        return Corner.SE;
    }

    private static boolean hasStructureId(SkyBlockDungeon dungeon, UUID structureId, int x, int y) {
        SkyBlockDungeon.DungeonRoom r = dungeon.getRoom(x, y);
        if (r == null) return false;
        UUID id = r.getStructureId();
        return id != null && id.equals(structureId);
    }

    private static Corner missingCornerInTemplate(StructureTemplate template) {
        int midX = (template.sizeX() + 1) / 2;
        int midZ = (template.sizeZ() + 1) / 2;

        long[] counts = new long[4];
        for (StructureTemplate.TemplateBlock b : template.blocks()) {
            int idx = 0;
            if (b.x() >= midX) idx += 1;
            if (b.z() >= midZ) idx += 2;
            counts[idx]++;
        }

        int minIdx = 0;
        long min = counts[0];
        for (int i = 1; i < counts.length; i++) {
            if (counts[i] < min) {
                min = counts[i];
                minIdx = i;
            }
        }

        return switch (minIdx) {
            case 0 -> Corner.NW;
            case 1 -> Corner.NE;
            case 2 -> Corner.SW;
            case 3 -> Corner.SE;
            default -> null;
        };
    }

    private static Corner rotateCorner(Corner corner, StructureTemplate.Rotation rotation) {
        if (rotation == StructureTemplate.Rotation.R0) return corner;

        return switch (rotation) {
            case R90 -> switch (corner) {
                case NW -> Corner.SW;
                case NE -> Corner.NW;
                case SW -> Corner.SE;
                case SE -> Corner.NE;
            };
            case R180 -> switch (corner) {
                case NW -> Corner.SE;
                case NE -> Corner.SW;
                case SW -> Corner.NE;
                case SE -> Corner.NW;
            };
            case R270 -> switch (corner) {
                case NW -> Corner.NE;
                case NE -> Corner.SE;
                case SW -> Corner.NW;
                case SE -> Corner.SW;
            };
            case R0 -> corner;
        };
    }

    private static boolean shouldOpenDoorMarker(SkyBlockDungeon dungeon, int width, int height, UUID structureId, int structureMinX, int structureMinY, StructureTemplate.DoorMarker marker, int cellStride, boolean verbose, Logger log, String ctx) {
        int cellLocalX = marker.x() / cellStride;
        int cellLocalY = marker.z() / cellStride;
        int x = structureMinX + cellLocalX;
        int y = structureMinY + cellLocalY;
        if (x < 0 || x >= width || y < 0 || y >= height) {
            if (verbose) {
                log.info(ctx + " doorDecision=reject reason=markerCellOutOfBounds marker=(" + marker.x() + "," + marker.y() + "," + marker.z() + "," + marker.facing().name() + ") cell=(" + x + "," + y + ")");
            }
            return false;
        }

        SkyBlockDungeon.DungeonRoom self = dungeon.getRoom(x, y);
        if (self == null) {
            if (verbose) {
                log.info(ctx + " doorDecision=reject reason=selfRoomNull markerCell=(" + x + "," + y + ") marker=(" + marker.x() + "," + marker.y() + "," + marker.z() + "," + marker.facing().name() + ")");
            }
            return false;
        }
        UUID selfId = self.getStructureId();
        if (selfId == null || !selfId.equals(structureId)) {
            if (verbose) {
                log.info(ctx + " doorDecision=reject reason=markerCellNotInStructure markerCell=(" + x + "," + y + ")"
                        + " marker=(" + marker.x() + "," + marker.y() + "," + marker.z() + "," + marker.facing().name() + ")"
                        + " selfStructureId=" + (selfId != null ? selfId.toString().substring(0, 8) : "null"));
            }
            return false;
        }

        int nx = x;
        int ny = y;
        switch (marker.facing()) {
            case NORTH -> ny = y - 1;
            case SOUTH -> ny = y + 1;
            case WEST -> nx = x - 1;
            case EAST -> nx = x + 1;
            default -> {
                if (verbose) {
                    log.info(ctx + " doorDecision=reject reason=invalidFacing marker=(" + marker.x() + "," + marker.y() + "," + marker.z() + "," + marker.facing().name() + ")");
                }
                return false;
            }
        }

        if (nx < 0 || nx >= width || ny < 0 || ny >= height) {
            if (verbose) {
                log.info(ctx + " doorDecision=reject reason=neighborOutOfBounds markerCell=(" + x + "," + y + ") neighbor=(" + nx + "," + ny + ") facing=" + marker.facing().name());
            }
            return false;
        }
        if (!dungeon.isConnected(x, y, nx, ny)) {
            if (verbose) {
                log.info(ctx + " doorDecision=reject reason=notConnected markerCell=(" + x + "," + y + ") neighbor=(" + nx + "," + ny + ") facing=" + marker.facing().name());
            }
            return false;
        }

        SkyBlockDungeon.DungeonRoom other = dungeon.getRoom(nx, ny);
        if (other == null) {
            if (verbose) {
                log.info(ctx + " doorDecision=reject reason=neighborRoomNull markerCell=(" + x + "," + y + ") neighbor=(" + nx + "," + ny + ") facing=" + marker.facing().name());
            }
            return false;
        }
        UUID otherId = other.getStructureId();
        boolean open = otherId == null || !otherId.equals(structureId);
        if (verbose) {
            log.info(ctx + " doorDecision=" + (open ? "open" : "reject") + " reason=" + (open ? "neighborDifferentStructure" : "neighborSameStructure")
                    + " markerCell=(" + x + "," + y + ") neighbor=(" + nx + "," + ny + ") facing=" + marker.facing().name()
                    + " neighborStructureId=" + (otherId != null ? otherId.toString().substring(0, 8) : "null"));
        }
        return open;
    }

    private void drawRoom(Location origin, SkyBlockDungeon.DungeonRoom room, int x, int y, SkyBlockDungeon dungeon, boolean bigRoomMode, StructureTemplate startRoomTemplate, TemplatePair startRoomPair, StructureTemplate healRoomTemplate, TemplatePair healRoomPair, StructureTemplate exitRoomTemplate, TemplatePair exitRoomPair, List<StructureTemplate> exitRoomTemplates, List<TemplatePair> exitRoomPairs, List<StructureTemplate> puzzleRoomTemplates, List<TemplatePair> puzzleRoomPairs, List<TemplatePair> trapRoomPairs, List<TemplatePair> minibossRoomPairs, List<StructureTemplate> baseRoom1x1Templates, List<TemplatePair> baseRoom1x1Pairs, int anchorDoorY, Set<String> usedBaseRoomTemplateKeys, Set<String> usedTrapRoomKeys, Set<String> usedMinibossRoomKeys, Set<String> usedExitRoomKeys, Set<String> usedPuzzleRoomKeys, Random random, boolean verbose, Logger log, int buildWidth, int buildHeight) {
        String ctx = "[Build][V] room (" + x + "," + y + ") " + fmtRoomShort(room);
        if (verbose) {
            log.info(ctx + " build start bigRoomMode=" + bigRoomMode + " origin=" + fmtLoc(origin));
        }
        if (room.getRoomType() == DungeonRoomType.ENTRANCE) {
            List<BlockFace> desired = connectedDirections(dungeon, x, y);
            if (startRoomPair != null) {
                if (verbose) {
                    log.info(ctx + " entrance using=start_room_pair desiredFaces=" + desired);
                }
                pasteSingleRoomPair(origin, startRoomPair, desired, 0, dungeon, x, y, verbose, log, ctx + " entrance");
                return;
            }
            if (startRoomTemplate != null) {
                StructureTemplate.Rotation rotation = pickBestRotation(startRoomTemplate, desired, verbose, log, ctx + " entrance");
                if (verbose) {
                    log.info(ctx + " entrance using=start_room_template desiredFaces=" + desired + " rotation=" + rotation.name());
                }
                startRoomTemplate.rotate(rotation).paste(origin.getWorld(), origin);
                Test_dungeons.getInstance().registerStartRoomNpc(origin.getWorld(), origin, rotation, startRoomTemplate.sizeX(), startRoomTemplate.sizeZ(), desired);
                processRoomBlocks(origin, rotation, startRoomTemplate.sizeX(), startRoomTemplate.sizeY(), startRoomTemplate.sizeZ(), "start_room");
                return;
            }
        }

        if (bigRoomMode && room.getRoomType() == DungeonRoomType.FAIRY) {
            List<BlockFace> desired = connectedDirections(dungeon, x, y);
            int startTop = startRoomTemplate != null ? startRoomTemplate.maxFilledY() : (startRoomPair != null && startRoomPair.walls() != null ? startRoomPair.walls().maxFilledY() : 0);

            if (healRoomPair != null) {
                int yOffset = 0;
                Integer doorY = doorAnchorYFromPair(healRoomPair);
                if (anchorDoorY >= 0 && doorY != null) {
                    yOffset = anchorDoorY - doorY;
                } else {
                    int healTop = healRoomPair.walls() != null ? healRoomPair.walls().maxFilledY() : 0;
                    yOffset = (startTop - healTop);
                }
                if (verbose) {
                    log.info(ctx + " heal using=heal_room_pair desiredFaces=" + desired + " yOffset=" + yOffset);
                }
                pasteSingleRoomPair(origin, healRoomPair, desired, yOffset, dungeon, x, y, verbose, log, ctx + " heal");
                return;
            }

            if (healRoomTemplate != null) {
                int yOffset = 0;
                Integer doorY = doorAnchorYFromTemplate(healRoomTemplate);
                if (anchorDoorY >= 0 && doorY != null) {
                    yOffset = anchorDoorY - doorY;
                } else {
                    int healTop = healRoomTemplate.maxFilledY();
                    yOffset = (startTop - healTop);
                }
                StructureTemplate.Rotation rotation = pickBestRotation(healRoomTemplate, desired, verbose, log, ctx + " heal");
                if (verbose) {
                    log.info(ctx + " heal using=heal_room_template desiredFaces=" + desired + " yOffset=" + yOffset + " rotation=" + rotation.name());
                }
                healRoomTemplate.rotate(rotation).paste(origin.getWorld(), origin.clone().add(0, yOffset, 0));
                processRoomBlocks(origin.clone().add(0, yOffset, 0), rotation, healRoomTemplate.sizeX(), healRoomTemplate.sizeY(), healRoomTemplate.sizeZ(), "heal_room");
                return;
            }
        }

        if (bigRoomMode && room.getRoomType() == DungeonRoomType.TRAP) {
            List<BlockFace> desired = connectedDirections(dungeon, x, y);
            int startTop = startRoomTemplate != null ? startRoomTemplate.maxFilledY() : (startRoomPair != null && startRoomPair.walls() != null ? startRoomPair.walls().maxFilledY() : 0);

            if (trapRoomPairs != null && !trapRoomPairs.isEmpty()) {
                TemplatePair pair = pickPairPreferUnused(trapRoomPairs, random, usedTrapRoomKeys);
                if (pair != null) {
                    int yOffset = 0;
                    Integer doorY = doorAnchorYFromPair(pair);
                    if (anchorDoorY >= 0 && doorY != null) {
                        yOffset = anchorDoorY - doorY;
                    } else {
                        int top = pair.walls() != null ? pair.walls().maxFilledY() : 0;
                        yOffset = (startTop - top) + 1;
                    }
                    if (verbose) {
                        log.info(ctx + " TRAP using=trap_room desiredFaces=" + desired + " yOffset=" + yOffset);
                    }
                    pasteSingleRoomPair(origin, pair, desired, yOffset, dungeon, x, y, verbose, log, ctx + " trap");
                    return;
                }
            }
        }

        if (bigRoomMode && room.getRoomType() == DungeonRoomType.MINI_BOSS) {
            List<BlockFace> desired = connectedDirections(dungeon, x, y);
            int startTop = startRoomTemplate != null ? startRoomTemplate.maxFilledY() : (startRoomPair != null && startRoomPair.walls() != null ? startRoomPair.walls().maxFilledY() : 0);

            if (minibossRoomPairs != null && !minibossRoomPairs.isEmpty()) {
                TemplatePair pair = pickPairPreferUnused(minibossRoomPairs, random, usedMinibossRoomKeys);
                if (pair != null) {
                    int yOffset = 0;
                    Integer doorY = doorAnchorYFromPair(pair);
                    if (anchorDoorY >= 0 && doorY != null) {
                        yOffset = anchorDoorY - doorY;
                    } else {
                        int top = pair.walls() != null ? pair.walls().maxFilledY() : 0;
                        yOffset = (startTop - top) + 1;
                    }
                    if (verbose) {
                        log.info(ctx + " MINI_BOSS using=miniboss_room desiredFaces=" + desired + " yOffset=" + yOffset);
                    }
                    pasteSingleRoomPair(origin, pair, desired, yOffset, dungeon, x, y, verbose, log, ctx + " miniboss");
                    return;
                }
            }
        }

        if (bigRoomMode && room.getRoomType() == DungeonRoomType.BLOOD) {
            List<BlockFace> desired = connectedDirections(dungeon, x, y);
            int startTop = startRoomTemplate != null ? startRoomTemplate.maxFilledY() : (startRoomPair != null && startRoomPair.walls() != null ? startRoomPair.walls().maxFilledY() : 0);

            if (minibossRoomPairs != null && !minibossRoomPairs.isEmpty()) {
                TemplatePair pair = pickPairPreferUnused(minibossRoomPairs, random, usedMinibossRoomKeys);
                if (pair != null) {
                    int yOffset = 0;
                    Integer doorY = doorAnchorYFromPair(pair);
                    if (anchorDoorY >= 0 && doorY != null) {
                        yOffset = anchorDoorY - doorY;
                    } else {
                        int top = pair.walls() != null ? pair.walls().maxFilledY() : 0;
                        yOffset = (startTop - top) + 1;
                    }
                    if (verbose) {
                        log.info(ctx + " miniboss using=miniboss_room_pair desiredFaces=" + desired + " yOffset=" + yOffset);
                    }
                    pasteSingleRoomPair(origin, pair, desired, yOffset, dungeon, x, y, verbose, log, ctx + " miniboss");
                    return;
                }
            }
        }

        if (bigRoomMode && room.getRoomType() == DungeonRoomType.EXIT) {
            List<BlockFace> desired = connectedDirections(dungeon, x, y);
            int startTop = startRoomTemplate != null ? startRoomTemplate.maxFilledY() : (startRoomPair != null && startRoomPair.walls() != null ? startRoomPair.walls().maxFilledY() : 0);

            if (exitRoomPairs != null && !exitRoomPairs.isEmpty()) {
                TemplatePair pair = pickPairPreferUnused(exitRoomPairs, random, usedExitRoomKeys);
                if (pair != null) {
                    int yOffset = 0;
                    Integer doorY = doorAnchorYFromPair(pair);
                    if (anchorDoorY >= 0 && doorY != null) {
                        yOffset = anchorDoorY - doorY;
                    } else {
                        int exitTop = pair.walls() != null ? pair.walls().maxFilledY() : 0;
                        yOffset = (startTop - exitTop);
                    }
                    if (verbose) {
                        log.info(ctx + " exit using=exit_room_pair_variant desiredFaces=" + desired + " yOffset=" + yOffset);
                    }
                    pasteSingleRoomPair(origin, pair, desired, yOffset, dungeon, x, y, verbose, log, ctx + " exit");
                    return;
                }
            }

            if (exitRoomTemplates != null && !exitRoomTemplates.isEmpty()) {
                StructureTemplate template = pickTemplatePreferUnused(exitRoomTemplates, random, usedExitRoomKeys);
                int yOffset = 0;
                Integer doorY = doorAnchorYFromTemplate(template);
                if (anchorDoorY >= 0 && doorY != null) {
                    yOffset = anchorDoorY - doorY;
                } else {
                    int exitTop = template.maxFilledY();
                    yOffset = (startTop - exitTop);
                }
                StructureTemplate.Rotation rotation = pickBestRotation(template, desired, verbose, log, ctx + " exit");
                if (verbose) {
                    log.info(ctx + " exit using=exit_room_template_variant desiredFaces=" + desired + " yOffset=" + yOffset + " rotation=" + rotation.name());
                }
                template.rotate(rotation).paste(origin.getWorld(), origin.clone().add(0, yOffset, 0));
                processRoomBlocks(origin.clone().add(0, yOffset, 0), rotation, template.sizeX(), template.sizeY(), template.sizeZ(), templateName(template));
                return;
            }

            if (exitRoomPair != null) {
                int yOffset = 0;
                Integer doorY = doorAnchorYFromPair(exitRoomPair);
                if (anchorDoorY >= 0 && doorY != null) {
                    yOffset = anchorDoorY - doorY;
                } else {
                    int exitTop = exitRoomPair.walls() != null ? exitRoomPair.walls().maxFilledY() : 0;
                    yOffset = (startTop - exitTop);
                }
                if (verbose) {
                    log.info(ctx + " exit using=exit_room_pair desiredFaces=" + desired + " yOffset=" + yOffset);
                }
                pasteSingleRoomPair(origin, exitRoomPair, desired, yOffset, dungeon, x, y, verbose, log, ctx + " exit");
                return;
            }

            if (exitRoomTemplate != null) {
                int yOffset = 0;
                Integer doorY = doorAnchorYFromTemplate(exitRoomTemplate);
                if (anchorDoorY >= 0 && doorY != null) {
                    yOffset = anchorDoorY - doorY;
                } else {
                    int exitTop = exitRoomTemplate.maxFilledY();
                    yOffset = (startTop - exitTop);
                }
                StructureTemplate.Rotation rotation = pickBestRotation(exitRoomTemplate, desired, verbose, log, ctx + " exit");
                if (verbose) {
                    log.info(ctx + " exit using=exit_room_template desiredFaces=" + desired + " yOffset=" + yOffset + " rotation=" + rotation.name());
                }
                exitRoomTemplate.rotate(rotation).paste(origin.getWorld(), origin.clone().add(0, yOffset, 0));
                processRoomBlocks(origin.clone().add(0, yOffset, 0), rotation, exitRoomTemplate.sizeX(), exitRoomTemplate.sizeY(), exitRoomTemplate.sizeZ(), "exit_room");
                return;
            }
        }

        if (bigRoomMode && room.getRoomType() == DungeonRoomType.PUZZLE) {
            List<BlockFace> desired = connectedDirections(dungeon, x, y);
            int startTop = startRoomTemplate != null ? startRoomTemplate.maxFilledY() : (startRoomPair != null && startRoomPair.walls() != null ? startRoomPair.walls().maxFilledY() : 0);

            if (puzzleRoomPairs != null && !puzzleRoomPairs.isEmpty()) {
                TemplatePair pair = pickPairPreferUnused(puzzleRoomPairs, random, usedPuzzleRoomKeys);
                if (pair != null) {
                    int yOffset = 0;
                    Integer doorY = doorAnchorYFromPair(pair);
                    if (anchorDoorY >= 0 && doorY != null) {
                        yOffset = anchorDoorY - doorY;
                    } else {
                        int puzzleTop = pair.walls() != null ? pair.walls().maxFilledY() : 0;
                        yOffset = (startTop - puzzleTop) + 1;
                    }
                    if (verbose) {
                        log.info(ctx + " puzzle using=puzzle_room_pair desiredFaces=" + desired + " yOffset=" + yOffset);
                    }
                    pasteSingleRoomPair(origin, pair, desired, yOffset, dungeon, x, y, verbose, log, ctx + " puzzle");
                    return;
                }
            }

            if (puzzleRoomTemplates != null && !puzzleRoomTemplates.isEmpty()) {
                StructureTemplate template = pickTemplatePreferUnused(puzzleRoomTemplates, random, usedPuzzleRoomKeys);
                int yOffset = 0;
                Integer doorY = doorAnchorYFromTemplate(template);
                if (anchorDoorY >= 0 && doorY != null) {
                    yOffset = anchorDoorY - doorY;
                } else {
                    int puzzleTop = template.maxFilledY();
                    yOffset = (startTop - puzzleTop) + 1;
                }
                StructureTemplate.Rotation rotation = pickBestRotation(template, desired, verbose, log, ctx + " puzzle");
                if (verbose) {
                    log.info(ctx + " puzzle using=puzzle_room_template desiredFaces=" + desired + " yOffset=" + yOffset + " rotation=" + rotation.name());
                }
                Location base = origin.clone().add(0, yOffset, 0);
                StructureTemplate rotated = template.rotate(rotation);
                rotated.paste(origin.getWorld(), base);
                registerPuzzleRoom06IfNeeded(base, rotation, rotated.sizeX(), rotated.sizeZ(), templateName(template));
                processRoomBlocks(base, rotation, template.sizeX(), template.sizeY(), template.sizeZ(), templateName(template));
                return;
            }
        }

        if (bigRoomMode && room.getRoomType() == DungeonRoomType.BASE && room.getStructureType() == null) {
            List<BlockFace> desired = connectedDirections(dungeon, x, y);
            int startTop = startRoomTemplate != null ? startRoomTemplate.maxFilledY() : (startRoomPair != null && startRoomPair.walls() != null ? startRoomPair.walls().maxFilledY() : 0);

            if (baseRoom1x1Pairs != null && !baseRoom1x1Pairs.isEmpty()) {
                int supportedCount = 0;
                List<TemplatePair> validPairs = new ArrayList<>();
                List<TemplatePair> supportedPairs = new ArrayList<>();
                for (TemplatePair p : baseRoom1x1Pairs) {
                    if (!supportsAllFaces(p, desired)) continue;
                    supportedCount++;
                    supportedPairs.add(p);
                    if (usedBaseRoomTemplateKeys != null && usedBaseRoomTemplateKeys.contains(keyForPair(p))) continue;
                    validPairs.add(p);
                }
                if (validPairs.isEmpty() && supportedCount > 0) {
                    if (verbose) log.warning(ctx + " No unused base_room_1x1_pair left for desiredFaces=" + desired
                            + " supported=" + supportedCount + " used=" + (usedBaseRoomTemplateKeys != null ? usedBaseRoomTemplateKeys.size() : 0)
                            + " falling back to reuse");
                    validPairs.addAll(supportedPairs);
                }
                if (validPairs.isEmpty()) {
                    if (verbose) {
                        log.info(ctx + " base noValidPairs desiredFaces=" + desired + " pairs=" + baseRoom1x1Pairs.size());
                    }
                } else {
                    TemplatePair pair = validPairs.get(random.nextInt(validPairs.size()));
                    if (usedBaseRoomTemplateKeys != null) {
                        usedBaseRoomTemplateKeys.add(keyForPair(pair));
                    }
                    int yOffset = 0;
                    Integer doorY = doorAnchorYFromPair(pair);
                    if (anchorDoorY >= 0 && doorY != null) {
                        yOffset = anchorDoorY - doorY;
                    } else {
                        int top = pair.walls() != null ? pair.walls().maxFilledY() : 0;
                        yOffset = (startTop - top) + 1;
                    }
                    if (verbose) {
                        log.info(ctx + " base using=base_room_1x1_pair desiredFaces=" + desired + " yOffset=" + yOffset);
                    }
                    pasteSingleRoomPair(origin, pair, desired, yOffset, dungeon, x, y, verbose, log, ctx + " base");
                    return;
                }
            }

            if (baseRoom1x1Templates != null && !baseRoom1x1Templates.isEmpty()) {
                int supportedCount = 0;
                List<StructureTemplate> validTemplates = new ArrayList<>();
                List<StructureTemplate> supportedTemplates = new ArrayList<>();
                for (StructureTemplate t : baseRoom1x1Templates) {
                    if (!supportsAllFaces(t, desired)) continue;
                    supportedCount++;
                    supportedTemplates.add(t);
                    if (usedBaseRoomTemplateKeys != null && usedBaseRoomTemplateKeys.contains(keyForTemplate(t))) continue;
                    validTemplates.add(t);
                }
                if (validTemplates.isEmpty() && supportedCount > 0) {
                    if (verbose) log.warning(ctx + " No unused base_room_1x1_template left for desiredFaces=" + desired
                            + " supported=" + supportedCount + " used=" + (usedBaseRoomTemplateKeys != null ? usedBaseRoomTemplateKeys.size() : 0)
                            + " falling back to reuse");
                    validTemplates.addAll(supportedTemplates);
                }
                if (validTemplates.isEmpty()) {
                    if (verbose) {
                        log.info(ctx + " base noValidTemplates desiredFaces=" + desired + " templates=" + baseRoom1x1Templates.size());
                    }
                } else {
                    StructureTemplate template = validTemplates.get(random.nextInt(validTemplates.size()));
                    if (usedBaseRoomTemplateKeys != null) {
                        usedBaseRoomTemplateKeys.add(keyForTemplate(template));
                    }
                    int yOffset = 0;
                    Integer doorY = doorAnchorYFromTemplate(template);
                    if (anchorDoorY >= 0 && doorY != null) {
                        yOffset = anchorDoorY - doorY;
                    } else {
                        int top = template.maxFilledY();
                        yOffset = (startTop - top) + 1;
                    }
                    StructureTemplate.Rotation rotation = pickBestRotation(template, desired, verbose, log, ctx + " base");
                    if (verbose) {
                        log.info(ctx + " base using=base_room_1x1_template desiredFaces=" + desired + " yOffset=" + yOffset + " rotation=" + rotation.name());
                    }
                    template.rotate(rotation).paste(origin.getWorld(), origin.clone().add(0, yOffset, 0));
                    processRoomBlocks(origin.clone().add(0, yOffset, 0), rotation, template.sizeX(), template.sizeY(), template.sizeZ(), templateName(template));
                    return;
                }
            }
        }

        if (bigRoomMode) {
            origin = origin.clone().add(13, 0, 13);
        }

        Material floorMaterial = getMaterialForType(room.getRoomType());
        if (room.getStructureType() != null) {
            floorMaterial = getMaterialForStructure(room.getStructureType());
        }
        Material wallMaterial = Material.BLACK_CONCRETE;

        // Draw 5x5 box
        for (int dx = 0; dx < 5; dx++) {
            for (int dz = 0; dz < 5; dz++) {
                Location blockLoc = origin.clone().add(dx, 0, dz);
                
                boolean isEdge = dx == 0 || dx == 4 || dz == 0 || dz == 4;
                
                if (isEdge) {
                    // Check for connections
                    boolean isConnected = false;
                    boolean isSameStructure = false;

                    int neighborX = x;
                    int neighborY = y;

                    if (dx == 0 && dz >= 1 && dz <= 3) { // West
                        neighborX = x - 1;
                    } else if (dx == 4 && dz >= 1 && dz <= 3) { // East
                        neighborX = x + 1;
                    } else if (dz == 0 && dx >= 1 && dx <= 3) { // North
                        neighborY = y - 1;
                    } else if (dz == 4 && dx >= 1 && dx <= 3) { // South
                        neighborY = y + 1;
                    } else {
                        // Corners, treat as wall usually, unless diagonal structure? No, just wall.
                        // But wait, if we are in a 2x2, the center corner should be open?
                        // For simplicity, let's keep corners as walls for now unless strictly needed.
                    }

                    if (neighborX != x || neighborY != y) {
                        isConnected = dungeon.isConnected(x, y, neighborX, neighborY);
                        SkyBlockDungeon.DungeonRoom neighbor = dungeon.getRoom(neighborX, neighborY);
                        if (neighbor != null && room.getStructureId() != null && room.getStructureId().equals(neighbor.getStructureId())) {
                            isSameStructure = true;
                        }
                    }

                    if (isSameStructure) {
                        blockLoc.getBlock().setType(floorMaterial); // Fully merged
                    } else if (isConnected && (dz == 2 || dx == 2)) { // Only center of edge is door
                        blockLoc.getBlock().setType(floorMaterial); // Open passage
                    } else {
                        blockLoc.getBlock().setType(wallMaterial); // Wall
                    }
                } else {
                    // Inner floor
                    blockLoc.getBlock().setType(floorMaterial);
                    
                    // Mark critical path with a beacon or something if needed, 
                    // or maybe just a different block in the center?
                    if (dx == 2 && dz == 2) {
                        if (room.isCritical()) {
                            blockLoc.add(0, 1, 0).getBlock().setType(Material.RED_CARPET);
                            blockLoc.add(0, -1, 0); // Reset
                        }
                    }
                }
            }
        }
    }

    /** 門日誌：不依賴 debug，始終單獨顯示 */
    private void doorLog(String message) {
        Logger l = Test_dungeons.getInstance() != null ? Test_dungeons.getInstance().getLogger() : null;
        if (l != null && message != null) l.info("[Door] " + message);
    }

    /** 主路門口放煤炭磚：兩房皆 critical；或任一側為 BLOOD/EXIT。MINI_BOSS 不在主路不放煤炭。粉色房可到達初始房的那側門口不放煤炭 */
    private boolean shouldDoorMarkerHaveCoal(SkyBlockDungeon dungeon, int cellX, int cellY, BlockFace facing) {
        int nx = cellX + (facing == BlockFace.EAST ? 1 : facing == BlockFace.WEST ? -1 : 0);
        int ny = cellY + (facing == BlockFace.SOUTH ? 1 : facing == BlockFace.NORTH ? -1 : 0);
        SkyBlockDungeon.DungeonRoom r1 = dungeon.getRoom(cellX, cellY);
        SkyBlockDungeon.DungeonRoom r2 = dungeon.getRoom(nx, ny);
        if (r1 == null || r2 == null) {
            doorLog("marker cell=(" + cellX + "," + cellY + ") facing=" + facing + " coal=false reason=roomNull");
            return false;
        }
        if (r1.getRoomType() == DungeonRoomType.TRAP || r1.getRoomType() == DungeonRoomType.MINI_BOSS
                || r2.getRoomType() == DungeonRoomType.TRAP || r2.getRoomType() == DungeonRoomType.MINI_BOSS) {
            doorLog("marker cell=(" + cellX + "," + cellY + ") facing=" + facing + " coal=false reason=TRAP/MINI_BOSS");
            return false;
        }
        if (r1.getRoomType() == DungeonRoomType.BLOOD || r1.getRoomType() == DungeonRoomType.EXIT
                || r2.getRoomType() == DungeonRoomType.BLOOD || r2.getRoomType() == DungeonRoomType.EXIT) {
            doorLog("marker cell=(" + cellX + "," + cellY + ") facing=" + facing + " coal=true reason=BLOOD/EXIT");
            return true;
        }
        if (!r1.isCritical() || !r2.isCritical()) {
            doorLog("marker cell=(" + cellX + "," + cellY + ") facing=" + facing + " coal=false reason=notCritical");
            return false;
        }
        boolean enteringFairy = isEnteringFairyDoor(dungeon, cellX, cellY, nx, ny, -1, -1);
        if (enteringFairy) {
            doorLog("marker cell=(" + cellX + "," + cellY + ") facing=" + facing + " coal=false reason=enteringFairy");
            return false;
        }
        doorLog("marker cell=(" + cellX + "," + cellY + ") facing=" + facing + " coal=true reason=mainPath");
        return true;
    }

    /** 結構的房間門是否應放置煤炭磚（整房判定）：關鍵路徑上或含 EXIT 皆放 */
    private boolean shouldStructurePlaceCoalForDoors(SkyBlockDungeon dungeon, int minX, int minY, int maxX, int maxY) {
        boolean anyCritical = false;
        boolean hasExit = false;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cy = minY; cy <= maxY; cy++) {
                SkyBlockDungeon.DungeonRoom r = dungeon.getRoom(cx, cy);
                if (r == null) continue;
                if (r.isCritical()) anyCritical = true;
                if (r.getRoomType() == DungeonRoomType.EXIT) hasExit = true;
            }
        }
        return anyCritical || hasExit;
    }

    /** 主路門口放煤炭磚：兩房皆 critical；或任一側為 BLOOD/EXIT。MINI_BOSS 不在主路不放煤炭。粉色房可到達初始房的那側門口不放煤炭 */
    private boolean shouldDoorHaveCoalBlocks(SkyBlockDungeon dungeon, SkyBlockDungeon.DungeonDoor d, int buildWidth, int buildHeight) {
        SkyBlockDungeon.DungeonRoom r1 = dungeon.getRoom(d.x1(), d.y1());
        SkyBlockDungeon.DungeonRoom r2 = dungeon.getRoom(d.x2(), d.y2());
        if (r1 == null || r2 == null) {
            doorLog("tunnel (" + d.x1() + "," + d.y1() + ")-(" + d.x2() + "," + d.y2() + ") coal=false reason=roomNull");
            return false;
        }
        if (r1.getRoomType() == DungeonRoomType.TRAP || r1.getRoomType() == DungeonRoomType.MINI_BOSS
                || r2.getRoomType() == DungeonRoomType.TRAP || r2.getRoomType() == DungeonRoomType.MINI_BOSS) {
            doorLog("tunnel (" + d.x1() + "," + d.y1() + ")-(" + d.x2() + "," + d.y2() + ") coal=false reason=TRAP/MINI_BOSS");
            return false;
        }
        if (r1.getRoomType() == DungeonRoomType.BLOOD || r1.getRoomType() == DungeonRoomType.EXIT
                || r2.getRoomType() == DungeonRoomType.BLOOD || r2.getRoomType() == DungeonRoomType.EXIT) {
            doorLog("tunnel (" + d.x1() + "," + d.y1() + ")-(" + d.x2() + "," + d.y2() + ") coal=true reason=BLOOD/EXIT");
            return true;
        }
        if (!r1.isCritical() || !r2.isCritical()) {
            doorLog("tunnel (" + d.x1() + "," + d.y1() + ")-(" + d.x2() + "," + d.y2() + ") coal=false reason=notCritical");
            return false;
        }
        boolean enteringFairy = isEnteringFairyDoor(dungeon, d.x1(), d.y1(), d.x2(), d.y2(), buildWidth, buildHeight);
        if (enteringFairy) {
            doorLog("tunnel (" + d.x1() + "," + d.y1() + ")-(" + d.x2() + "," + d.y2() + ") coal=false reason=enteringFairy");
            return false;
        }
        doorLog("tunnel (" + d.x1() + "," + d.y1() + ")-(" + d.x2() + "," + d.y2() + ") coal=true reason=mainPath");
        return true;
    }

    /** 是否為「粉色房可到達初始房」的門：門連接 FAIRY 與「從 exit 不可經 FAIRY 到達」的格子 → 不放煤炭。
     * 使用「從 exit 出發、排除 FAIRY 的 BFS」：若 other 不可從 exit 不經 FAIRY 到達，則為進入門（通往 initial）。 */
    private boolean isEnteringFairyDoor(SkyBlockDungeon dungeon, int x1, int y1, int x2, int y2, int buildWidth, int buildHeight) {
        SkyBlockDungeon.DungeonRoom r1 = dungeon.getRoom(x1, y1);
        SkyBlockDungeon.DungeonRoom r2 = dungeon.getRoom(x2, y2);
        if (r1 == null || r2 == null) return false;
        boolean r1Fairy = r1.getRoomType() == DungeonRoomType.FAIRY;
        boolean r2Fairy = r2.getRoomType() == DungeonRoomType.FAIRY;
        if (!r1Fairy && !r2Fairy) return false;
        int otherX = r1Fairy ? x2 : x1;
        int otherY = r1Fairy ? y2 : y1;
        int fairyX = r1Fairy ? x1 : x2;
        int fairyY = r1Fairy ? y1 : y2;
        int[][] distFromExitExcludingFairy = computeDistFromExitExcluding(dungeon, fairyX, fairyY, buildWidth, buildHeight);
        if (distFromExitExcludingFairy == null) return false;
        int dOther = otherX >= 0 && otherX < distFromExitExcludingFairy.length && otherY >= 0 && otherY < distFromExitExcludingFairy[0].length
                ? distFromExitExcludingFairy[otherX][otherY] : Integer.MAX_VALUE;
        return dOther == Integer.MAX_VALUE;
    }

    /** 從 exit 出發 BFS，但不經過 excludeX,excludeY；回傳各格到 exit 的距離；無法到達為 Integer.MAX_VALUE。用於判斷 FAIRY 的「進入門」vs「離開門」。 */
    private int[][] computeDistFromExitExcluding(SkyBlockDungeon dungeon, int excludeX, int excludeY, int buildWidth, int buildHeight) {
        int width, height;
        if (buildWidth > 0 && buildHeight > 0) {
            width = buildWidth;
            height = buildHeight;
        } else {
            var snap = Test_dungeons.getInstance().getLastDungeon();
            if (snap != null) {
                width = snap.width();
                height = snap.height();
            } else {
                width = 6;
                height = 6;
            }
        }
        int[] exit = null;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                SkyBlockDungeon.DungeonRoom r = dungeon.getRoom(x, y);
                if (r != null && r.getRoomType() == DungeonRoomType.EXIT) exit = new int[]{x, y};
            }
        }
        if (exit == null) return null;
        int[][] dist = new int[width][height];
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) dist[x][y] = Integer.MAX_VALUE;
        dist[exit[0]][exit[1]] = 0;
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(exit);
        while (!q.isEmpty()) {
            int[] cur = q.poll();
            int d = dist[cur[0]][cur[1]];
            for (int[] dd : dirs) {
                int nx = cur[0] + dd[0], ny = cur[1] + dd[1];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                if (nx == excludeX && ny == excludeY) continue;
                SkyBlockDungeon.DungeonRoom nr = dungeon.getRoom(nx, ny);
                if (nr == null) continue;
                if (!dungeon.isConnectedForCoalCheck(cur[0], cur[1], nx, ny)) continue;
                if (dist[nx][ny] <= d + 1) continue;
                dist[nx][ny] = d + 1;
                q.add(new int[]{nx, ny});
            }
        }
        return dist;
    }

    /** 從 entrance 出發 BFS，回傳各格到 entrance 的距離；無法到達為 Integer.MAX_VALUE。遍歷所有已連接房間（不限 critical）以正確判斷「通往初始房」的門。 */
    private int[][] computeDistFromEntrance(SkyBlockDungeon dungeon, int buildWidth, int buildHeight) {
        int width, height;
        if (buildWidth > 0 && buildHeight > 0) {
            width = buildWidth;
            height = buildHeight;
        } else {
            var snap = Test_dungeons.getInstance().getLastDungeon();
            if (snap != null) {
                width = snap.width();
                height = snap.height();
            } else {
                width = 6;
                height = 6;
            }
        }
        int[] entrance = null;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                SkyBlockDungeon.DungeonRoom r = dungeon.getRoom(x, y);
                if (r != null && r.getRoomType() == DungeonRoomType.ENTRANCE) entrance = new int[]{x, y};
            }
        }
        if (entrance == null) return null;
        int[][] dist = new int[width][height];
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) dist[x][y] = Integer.MAX_VALUE;
        dist[entrance[0]][entrance[1]] = 0;
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(entrance);
        while (!q.isEmpty()) {
            int[] cur = q.poll();
            int d = dist[cur[0]][cur[1]];
            for (int[] dd : dirs) {
                int nx = cur[0] + dd[0], ny = cur[1] + dd[1];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                SkyBlockDungeon.DungeonRoom nr = dungeon.getRoom(nx, ny);
                if (nr == null) continue;
                if (!dungeon.isConnected(cur[0], cur[1], nx, ny)) continue;
                if (dist[nx][ny] <= d + 1) continue;
                dist[nx][ny] = d + 1;
                q.add(new int[]{nx, ny});
            }
        }
        return dist;
    }

    private List<int[]> getCriticalPath(SkyBlockDungeon dungeon, int buildWidth, int buildHeight) {
        int[] entrance = null, exit = null;
        int maxX = 0, maxY = 0;
        for (SkyBlockDungeon.DungeonDoor d : dungeon.getAllDoors()) {
            maxX = Math.max(maxX, Math.max(d.x1(), d.x2()));
            maxY = Math.max(maxY, Math.max(d.y1(), d.y2()));
        }
        int width, height;
        if (buildWidth > 0 && buildHeight > 0) {
            width = buildWidth;
            height = buildHeight;
        } else {
            var snap = Test_dungeons.getInstance().getLastDungeon();
            if (snap != null) {
                width = snap.width();
                height = snap.height();
            } else {
                width = maxX + 2;
                height = maxY + 2;
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                SkyBlockDungeon.DungeonRoom r = dungeon.getRoom(x, y);
                if (r == null || !r.isCritical()) continue;
                if (r.getRoomType() == DungeonRoomType.ENTRANCE) entrance = new int[]{x, y};
                if (r.getRoomType() == DungeonRoomType.EXIT) exit = new int[]{x, y};
            }
        }
        if (entrance == null || exit == null) return List.of();
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        int n = width * height;
        int[] parent = new int[n];
        java.util.Arrays.fill(parent, -1);
        boolean[] visited = new boolean[n];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(entrance);
        visited[entrance[0] + entrance[1] * width] = true;
        while (!q.isEmpty()) {
            int[] cur = q.poll();
            if (cur[0] == exit[0] && cur[1] == exit[1]) break;
            for (int[] d : dirs) {
                int nx = cur[0] + d[0], ny = cur[1] + d[1];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                SkyBlockDungeon.DungeonRoom nr = dungeon.getRoom(nx, ny);
                if (nr == null || !nr.isCritical()) continue;
                if (!dungeon.isConnected(cur[0], cur[1], nx, ny)) continue;
                int ni = nx + ny * width;
                if (visited[ni]) continue;
                visited[ni] = true;
                parent[ni] = cur[0] + cur[1] * width;
                q.add(new int[]{nx, ny});
            }
        }
        int ti = exit[0] + exit[1] * width;
        if (!visited[ti]) return List.of();
        List<int[]> path = new ArrayList<>();
        int cur = ti;
        while (cur != -1) {
            path.add(new int[]{cur % width, cur / width});
            cur = parent[cur];
        }
        Collections.reverse(path);
        return path;
    }

    /** 地牢完成後掃描 TRAP/MINI_BOSS 房間範圍，刪除所有煤炭磚（兜底確保無煤炭門） */
    private void removeCoalFromTrapAndMinibossRooms(World world, SkyBlockDungeon dungeon, int startX, int startY, int startZ, int width, int height, int roomScale) {
        if (world == null || dungeon == null) return;
        int yMin = -32;
        int yMax = 64;
        int removed = 0;
        for (int cy = 0; cy < height; cy++) {
            for (int cx = 0; cx < width; cx++) {
                SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(cx, cy);
                if (room == null) continue;
                DungeonRoomType type = room.getRoomType();
                if (type != DungeonRoomType.TRAP && type != DungeonRoomType.MINI_BOSS) continue;

                int baseX = startX + cx * roomScale;
                int baseZ = startZ + cy * roomScale;
                for (int dx = -1; dx <= roomScale; dx++) {
                    for (int dz = -1; dz <= roomScale; dz++) {
                        for (int dy = yMin; dy <= yMax; dy++) {
                            Block b = world.getBlockAt(baseX + dx, startY + dy, baseZ + dz);
                            if (b.getType() == Material.COAL_BLOCK) {
                                b.setType(Material.AIR, false);
                                removed++;
                            }
                        }
                    }
                }
            }
        }
        if (removed > 0 && Test_dungeons.getInstance() != null && Test_dungeons.getInstance().getLogger() != null) {
            Test_dungeons.getInstance().getLogger().info("[Door] removeCoalFromTrapAndMinibossRooms removed=" + removed);
        }
    }

    private void buildDoorTunnels(Location startLoc, SkyBlockDungeon dungeon, int roomScale, int anchorDoorY, int buildWidth, int buildHeight) {
        World world = startLoc.getWorld();
        if (world == null) return;
        int startX = startLoc.getBlockX();
        int startY = startLoc.getBlockY();
        int startZ = startLoc.getBlockZ();
        int baseY = startY + (anchorDoorY >= 0 ? anchorDoorY : 0);
        doorLog("buildDoorTunnels doors=" + dungeon.getAllDoors().size() + " buildSize=" + buildWidth + "x" + buildHeight);

        List<SkyBlockDungeon.DungeonDoor> allDoors = dungeon.getAllDoors();
        for (SkyBlockDungeon.DungeonDoor d : allDoors) {
            int x1 = d.x1();
            int y1 = d.y1();
            int x2 = d.x2();
            int y2 = d.y2();
            if (Math.abs(x1 - x2) + Math.abs(y1 - y2) != 1) continue;

            // 主路門口：石磚外框 + 煤炭磚內側；非主路（含 EXIT）：石磚外框 + 空氣內側
            boolean isMainPath = shouldDoorHaveCoalBlocks(dungeon, d, buildWidth, buildHeight);
            Material innerMaterial = isMainPath ? Material.COAL_BLOCK : Material.AIR;

            if (x1 != x2) {
                int minX = Math.min(x1, x2);
                int gapX = startX + (minX + 1) * roomScale - 1;
                int centerZ = startZ + y1 * roomScale + 15;

                for (int z = centerZ - 2; z <= centerZ + 2; z++) {
                    for (int y = baseY - 1; y <= baseY + 4; y++) {
                        world.getBlockAt(gapX, y, z).setType(Material.STONE_BRICKS);
                    }
                }
                for (int z = centerZ - 1; z <= centerZ + 1; z++) {
                    for (int y = baseY; y <= baseY + 3; y++) {
                        world.getBlockAt(gapX, y, z).setType(innerMaterial);
                    }
                }
            } else {
                int minY = Math.min(y1, y2);
                int gapZ = startZ + (minY + 1) * roomScale - 1;
                int centerX = startX + x1 * roomScale + 15;

                for (int x = centerX - 2; x <= centerX + 2; x++) {
                    for (int y = baseY - 1; y <= baseY + 4; y++) {
                        world.getBlockAt(x, y, gapZ).setType(Material.STONE_BRICKS);
                    }
                }
                for (int x = centerX - 1; x <= centerX + 1; x++) {
                    for (int y = baseY; y <= baseY + 3; y++) {
                        world.getBlockAt(x, y, gapZ).setType(innerMaterial);
                    }
                }
            }
        }
    }

    private void pasteSingleRoomPair(Location origin, TemplatePair pair, List<BlockFace> desiredFaces, int yOffset, SkyBlockDungeon dungeon, int cellX, int cellY, boolean verbose, Logger log, String ctx) {
        if (pair == null || pair.walls() == null || pair.doors() == null) return;
        pair = resolvePuzzleRoom05VariantPair(pair);
        pair = normalizePair(pair);
        List<StructureTemplate.Rotation> rotations = rankedRotations(pair.doors(), desiredFaces);
        if (verbose) {
            StringBuilder sb = new StringBuilder();
            sb.append(ctx).append(" rotationCandidates=");
            for (int i = 0; i < rotations.size(); i++) {
                StructureTemplate.Rotation r = rotations.get(i);
                if (i > 0) sb.append(",");
                sb.append(r.name()).append("(").append(rotationScore(pair.doors(), desiredFaces, r)).append(")");
            }
            log.info(sb.toString());
        }
        for (StructureTemplate.Rotation rotation : rotations) {
            if (pasteSingleRoomPairWithRotationValidated(origin, pair, desiredFaces, yOffset, rotation, dungeon, cellX, cellY, verbose, log, ctx)) return;
        }
        if (!rotations.isEmpty()) {
            pasteSingleRoomPairWithRotationValidated(origin, pair, desiredFaces, yOffset, rotations.get(0), dungeon, cellX, cellY, verbose, log, ctx);
        }
    }

    private boolean pasteSingleRoomPairWithRotationValidated(Location origin, TemplatePair pair, List<BlockFace> desiredFaces, int yOffset, StructureTemplate.Rotation rotation, SkyBlockDungeon dungeon, int cellX, int cellY, boolean verbose, Logger log, String ctx) {
        if (pair == null || pair.walls() == null || pair.doors() == null) return false;
        pair = resolvePuzzleRoom05VariantPair(pair);
        pair = normalizePair(pair);
        StructureTemplate walls = pair.walls().rotate(rotation);
        StructureTemplate doors = pair.doors().rotate(rotation);

        Location base = yOffset == 0 ? origin : origin.clone().add(0, yOffset, 0);
        walls.paste(base.getWorld(), base);

        registerPuzzleRoom06IfNeeded(base, rotation, walls.sizeX(), walls.sizeZ(), pair.baseName());
        if ("start_room".equalsIgnoreCase(pair.baseName())) {
            Test_dungeons.getInstance().registerStartRoomNpc(base.getWorld(), base, rotation, walls.sizeX(), walls.sizeZ(), desiredFaces != null ? desiredFaces : List.of());
        }
        processRoomBlocks(base, rotation, pair.walls().sizeX(), pair.walls().sizeY(), pair.walls().sizeZ(), pair.baseName());
        if (desiredFaces == null || desiredFaces.isEmpty()) return true;
        Set<Long> include = new HashSet<>();
        Set<Long> coalInclude = new HashSet<>();
        List<ExpectedDoor> expected = new ArrayList<>(desiredFaces.size());
        for (BlockFace face : desiredFaces) {
            StructureTemplate.DoorMarker marker = pickBestMarkerForFacing(doors, face);
            if (marker == null) continue;
            StructureTemplate.addDoorOverlayIncludePositions(include, marker, doors.sizeX(), doors.sizeY(), doors.sizeZ());
                if (dungeon != null && shouldDoorMarkerHaveCoal(dungeon, cellX, cellY, face)) {
                StructureTemplate.addDoorOverlayIncludePositions(coalInclude, marker, doors.sizeX(), doors.sizeY(), doors.sizeZ());
            }
            expected.add(new ExpectedDoor(marker, face));
        }
        if (dungeon != null) {
            SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(cellX, cellY);
            if (room != null && (room.getRoomType() == DungeonRoomType.TRAP || room.getRoomType() == DungeonRoomType.MINI_BOSS)) {
                coalInclude.clear();
                doorLog("trap/miniboss cell=(" + cellX + "," + cellY + ") coal=false");
            }
        }
        if (verbose) {
            log.info(ctx + " rotation=" + rotation.name()
                    + " desiredFaces=" + desiredFaces
                    + " doorMarkers=" + doors.doorMarkers().size()
                    + " expected=" + expected.size()
                    + " includeBlocks=" + include.size()
                    + " doorsSize=" + doors.sizeX() + "x" + doors.sizeY() + "x" + doors.sizeZ());
            for (ExpectedDoor e : expected) {
                StructureTemplate.DoorMarker m = e.marker();
                log.info(ctx + " rotation=" + rotation.name()
                        + " pick marker=(" + m.x() + "," + m.y() + "," + m.z() + ") expected=" + e.expectedFacing().name());
            }
        }
        if (!include.isEmpty()) {
            doors.paste(base.getWorld(), base, include, coalInclude);
        }
        boolean ok = verifyExpectedDoors(base, doors, expected, verbose, log, ctx + " rotation=" + rotation.name());
        if (verbose) {
            log.info(ctx + " rotation=" + rotation.name() + " verifyOk=" + ok);
        }
        return ok;
    }

    private TemplatePair resolvePuzzleRoom05VariantPair(TemplatePair pair) {
        if (pair == null || pair.baseName() == null) return pair;
        if (!"puzzle_room_05".equalsIgnoreCase(pair.baseName())) return pair;

        StructureTemplate doors = pair.doors();
        StructureTemplate walls = pickPuzzleRoom05WallsVariant();
        if (walls == null) return pair;
        return new TemplatePair("puzzle_room_05", walls, doors);
    }

    private StructureTemplate pickPuzzleRoom05WallsVariant() {
        File dir = new File(Test_dungeons.getInstance().getDataFolder(), "structures");
        if (!dir.exists()) return null;
        File[] files = dir.listFiles((d, n) -> {
            String s = n != null ? n.toLowerCase(Locale.ROOT) : "";
            return s.startsWith("puzzle_room_05-") && s.endsWith("_walls.yml");
        });
        if (files == null || files.length == 0) return null;
        File pick = files[ThreadLocalRandom.current().nextInt(files.length)];
        String name = pick.getName();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".yml")) return null;
        String base = name.substring(0, name.length() - 4);
        return loadTemplateOrNull(base);
    }

    private boolean verifyExpectedDoors(Location base, StructureTemplate doors, List<ExpectedDoor> expected, boolean verbose, Logger log, String ctx) {
        if (expected == null || expected.isEmpty()) return true;
        World world = base.getWorld();
        if (world == null) return false;

        int ox = base.getBlockX();
        int oy = base.getBlockY();
        int oz = base.getBlockZ();
        for (ExpectedDoor e : expected) {
            BlockFace expectedFacing = e.expectedFacing();
            StructureTemplate.DoorMarker m = e.marker();
            BlockFace resolved = StructureTemplate.resolveDoorFacing(m, doors.sizeX(), doors.sizeZ());
            if (resolved != expectedFacing) {
                if (verbose) {
                    log.info(ctx + " mismatch resolvedFacing marker=(" + m.x() + "," + m.y() + "," + m.z() + ")"
                            + " resolved=" + resolved.name() + " expected=" + expectedFacing.name());
                }
                return false;
            }
            var block = world.getBlockAt(ox + m.x(), oy + m.y(), oz + m.z());
            if (block.getType() != Material.OBSERVER) {
                if (verbose) {
                    log.info(ctx + " markerBlockNotObserver marker=(" + m.x() + "," + m.y() + "," + m.z() + ")"
                            + " type=" + block.getType().name() + " expectedFacing=" + expectedFacing.name());
                }
                continue;
            }
            if (!(block.getBlockData() instanceof Directional directional)) {
                if (verbose) {
                    log.info(ctx + " markerObserverNotDirectional marker=(" + m.x() + "," + m.y() + "," + m.z() + ")"
                            + " data=" + block.getBlockData().getAsString());
                }
                return false;
            }
            if (directional.getFacing() != expectedFacing) {
                if (verbose) {
                    log.info(ctx + " observerFacingMismatch marker=(" + m.x() + "," + m.y() + "," + m.z() + ")"
                            + " actual=" + directional.getFacing().name() + " expected=" + expectedFacing.name());
                }
                return false;
            }
            if (verbose) {
                log.info(ctx + " ok marker=(" + m.x() + "," + m.y() + "," + m.z() + ") facing=" + expectedFacing.name());
            }
        }
        return true;
    }

    private List<StructureTemplate.Rotation> rankedRotations(StructureTemplate doors, List<BlockFace> desiredFaces) {
        List<StructureTemplate.Rotation> sorted = new ArrayList<>(List.of(StructureTemplate.Rotation.values()));
        sorted.sort((a, b) -> Integer.compare(rotationScore(doors, desiredFaces, b), rotationScore(doors, desiredFaces, a)));
        return sorted;
    }

    private int rotationScore(StructureTemplate doors, List<BlockFace> desiredFaces, StructureTemplate.Rotation rotation) {
        if (doors == null || desiredFaces == null || desiredFaces.isEmpty()) return 0;
        StructureTemplate rotated = doors.rotate(rotation);
        Set<BlockFace> available = new HashSet<>();
        for (StructureTemplate.DoorMarker d : rotated.doorMarkers()) {
            BlockFace face = StructureTemplate.resolveDoorFacing(d, rotated.sizeX(), rotated.sizeZ());
            if (face != BlockFace.SELF) available.add(face);
        }
        int score = 0;
        for (BlockFace f : desiredFaces) {
            if (available.contains(f)) score += 10;
            else score -= 100;
        }
        score -= available.size();
        return score;
    }

    private boolean supportsAllFaces(TemplatePair pair, List<BlockFace> desiredFaces) {
        if (desiredFaces == null || desiredFaces.isEmpty()) return true;
        if (pair == null || pair.doors() == null) return false;
        pair = normalizePair(pair);
        return supportsAllFaces(pair.doors(), desiredFaces);
    }

    private boolean supportsAllFaces(StructureTemplate template, List<BlockFace> desiredFaces) {
        if (desiredFaces == null || desiredFaces.isEmpty()) return true;
        if (template == null) return false;

        for (StructureTemplate.Rotation r : StructureTemplate.Rotation.values()) {
            StructureTemplate rotated = template.rotate(r);
            Set<BlockFace> available = new HashSet<>();
            for (StructureTemplate.DoorMarker d : rotated.doorMarkers()) {
                BlockFace face = StructureTemplate.resolveDoorFacing(d, rotated.sizeX(), rotated.sizeZ());
                if (face != BlockFace.SELF) available.add(face);
            }
            boolean ok = true;
            for (BlockFace f : desiredFaces) {
                if (!available.contains(f)) {
                    ok = false;
                    break;
                }
            }
            if (ok) return true;
        }

        return false;
    }

    private record ExpectedDoor(StructureTemplate.DoorMarker marker, BlockFace expectedFacing) {}

    private static StructureTemplate.DoorMarker pickBestMarkerForFacing(StructureTemplate doors, BlockFace facing) {
        if (doors == null || facing == null) return null;

        int bestScore = Integer.MAX_VALUE;
        StructureTemplate.DoorMarker best = null;
        for (StructureTemplate.DoorMarker m : doors.doorMarkers()) {
            if (StructureTemplate.resolveDoorFacing(m, doors.sizeX(), doors.sizeZ()) != facing) continue;
            int score = switch (facing) {
                case WEST -> m.x();
                case EAST -> (doors.sizeX() - 1) - m.x();
                case NORTH -> m.z();
                case SOUTH -> (doors.sizeZ() - 1) - m.z();
                default -> Integer.MAX_VALUE;
            };
            if (score < bestScore) {
                bestScore = score;
                best = m;
            }
        }
        return best;
    }

    private StructureTemplate loadTemplateOrNull(String name) {
        if (name == null || name.isEmpty()) return null;
        String key = name.toLowerCase(Locale.ROOT);
        if (missingTemplateNames.contains(key)) return null;

        StructureTemplate cached = templateCache.get(key);
        if (cached != null) {
            templateNames.putIfAbsent(cached, key);
            return cached;
        }

        File dir = new File(Test_dungeons.getInstance().getDataFolder(), "structures");
        File file = new File(dir, key + ".yml");
        if (!file.exists()) {
            missingTemplateNames.add(key);
            return null;
        }
        try {
            StructureTemplate loaded = StructureTemplate.load(file);
            templateCache.put(key, loaded);
            templateNames.put(loaded, key);
            return loaded;
        } catch (Throwable e) {
            missingTemplateNames.add(key);
            return null;
        }
    }

    private String templateName(StructureTemplate template) {
        if (template == null) return null;
        return templateNames.get(template);
    }

    private void processRoomBlocks(Location base, StructureTemplate.Rotation rotation, int sizeX, int sizeY, int sizeZ) {
        processRoomBlocks(base, rotation, sizeX, sizeY, sizeZ, null);
    }

    private void processRoomBlocks(Location base, StructureTemplate.Rotation rotation, int sizeX, int sizeY, int sizeZ, String templateName) {
        if (base == null || rotation == null) return;
        World world = base.getWorld();
        if (world == null) return;
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) return;

        int scanX = (rotation == StructureTemplate.Rotation.R90 || rotation == StructureTemplate.Rotation.R270) ? sizeZ : sizeX;
        int scanZ = (rotation == StructureTemplate.Rotation.R90 || rotation == StructureTemplate.Rotation.R270) ? sizeX : sizeZ;

        int minX = base.getBlockX();
        int minY = base.getBlockY();
        int minZ = base.getBlockZ();
        int maxX = minX + scanX - 1;
        int maxY = minY + sizeY - 1;
        int maxZ = minZ + scanZ - 1;

        Set<Long> ladders = new HashSet<>();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (type == Material.DISPENSER || type == Material.DROPPER) {
                        adjustDispenserFacingAir(block, minX, maxX, minY, maxY, minZ, maxZ);
                        Test_dungeons plugin = Test_dungeons.getInstance();
                        if (plugin != null) {
                            if (templateName != null && !templateName.isBlank()) {
                                int lx = x - minX;
                                int ly = y - minY;
                                int lz = z - minZ;
                                int[] unrot = inverseRotateXZ(lx, lz, rotation, scanX, scanZ);
                                plugin.registerLavaTrapEmitter(block, minX, maxX, minY, maxY, minZ, maxZ, templateName, unrot[0], ly, unrot[1]);
                            } else {
                                plugin.registerLavaTrapEmitter(block, minX, maxX, minY, maxY, minZ, maxZ);
                            }
                        }
                        continue;
                    }
                    if (type == Material.LADDER) {
                        ladders.add(packBlock(x, y, z));
                    }
                }
            }
        }

        if (!ladders.isEmpty()) {
            adjustLadderClusters(world, ladders, minX, maxX, minY, maxY, minZ, maxZ);
        }
    }

    private static int[] inverseRotateXZ(int xRot, int zRot, StructureTemplate.Rotation rotation, int sizeXRot, int sizeZRot) {
        int sizeX0;
        int sizeZ0;
        if (rotation == StructureTemplate.Rotation.R90 || rotation == StructureTemplate.Rotation.R270) {
            sizeX0 = sizeZRot;
            sizeZ0 = sizeXRot;
        } else {
            sizeX0 = sizeXRot;
            sizeZ0 = sizeZRot;
        }
        return switch (rotation) {
            case R0 -> new int[] { xRot, zRot };
            case R90 -> new int[] { sizeX0 - 1 - zRot, xRot };
            case R180 -> new int[] { sizeX0 - 1 - xRot, sizeZ0 - 1 - zRot };
            case R270 -> new int[] { zRot, sizeZ0 - 1 - xRot };
        };
    }

    private static void adjustDispenserFacingAir(Block block, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        if (block == null) return;
        BlockData data = block.getBlockData();
        if (!(data instanceof Directional directional)) return;

        BlockFace current = directional.getFacing();
        if (current != null && current != BlockFace.SELF) {
            Block rel = block.getRelative(current);
            if (isPassableBlock(rel)) {
                int rx = rel.getX();
                int ry = rel.getY();
                int rz = rel.getZ();
                if (rx >= minX && rx <= maxX && ry >= minY && ry <= maxY && rz >= minZ && rz <= maxZ) return;
            }
        }

        BlockFace chosen = null;
        BlockFace fallback = null;
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN}) {
            Block rel = block.getRelative(f);
            if (!isPassableBlock(rel)) continue;
            if (fallback == null) fallback = f;
            int rx = rel.getX();
            int ry = rel.getY();
            int rz = rel.getZ();
            if (rx >= minX && rx <= maxX && ry >= minY && ry <= maxY && rz >= minZ && rz <= maxZ) {
                chosen = f;
                break;
            }
        }
        if (chosen == null) chosen = fallback;
        if (chosen == null || chosen == BlockFace.SELF) return;

        if (current != chosen) {
            directional.setFacing(chosen);
            block.setBlockData(directional, false);
        }
    }

    private static void adjustLadderClusters(World world, Set<Long> ladders, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        if (world == null || ladders == null || ladders.isEmpty()) return;

        ArrayDeque<Long> queue = new ArrayDeque<>();
        Set<Long> cluster = new HashSet<>();

        while (!ladders.isEmpty()) {
            long seed = ladders.iterator().next();
            ladders.remove(seed);
            queue.add(seed);
            cluster.add(seed);

            while (!queue.isEmpty()) {
                long k = queue.pollFirst();
                int x = unpackX(k);
                int y = unpackY(k);
                int z = unpackZ(k);

                for (int i = 0; i < 6; i++) {
                    int nx = x;
                    int ny = y;
                    int nz = z;
                    if (i == 0) ny++;
                    else if (i == 1) ny--;
                    else if (i == 2) nz--;
                    else if (i == 3) nz++;
                    else if (i == 4) nx--;
                    else nx++;

                    if (nx < minX || nx > maxX || ny < minY || ny > maxY || nz < minZ || nz > maxZ) continue;
                    long nk = packBlock(nx, ny, nz);
                    if (!ladders.remove(nk)) continue;
                    queue.add(nk);
                    cluster.add(nk);
                }
            }

            BlockFace facing = chooseLadderFacing(world, cluster);
            if (facing != null && facing != BlockFace.SELF) {
                for (long k : cluster) {
                    int x = unpackX(k);
                    int y = unpackY(k);
                    int z = unpackZ(k);
                    Block b = world.getBlockAt(x, y, z);
                    if (b.getType() != Material.LADDER) continue;
                    BlockData data = b.getBlockData();
                    if (data instanceof Ladder ladder) {
                        if (ladder.getFacing() == facing) continue;
                        ladder.setFacing(facing);
                        b.setBlockData(ladder, false);
                        continue;
                    }
                    if (data instanceof Directional directional) {
                        if (directional.getFacing() == facing) continue;
                        directional.setFacing(facing);
                        b.setBlockData(directional, false);
                    }
                }
            }

            cluster.clear();
        }
    }

    private static BlockFace chooseLadderFacing(World world, Set<Long> cluster) {
        if (world == null || cluster == null || cluster.isEmpty()) return null;

        int topY = Integer.MIN_VALUE;
        int bottomY = Integer.MAX_VALUE;
        for (long k : cluster) {
            int y = unpackY(k);
            if (y > topY) topY = y;
            if (y < bottomY) bottomY = y;
        }

        BlockFace[] faces = new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

        for (int y = topY; y >= bottomY; y--) {
            for (long k : cluster) {
                if (unpackY(k) != y) continue;
                int x = unpackX(k);
                int z = unpackZ(k);
                Block ladder = world.getBlockAt(x, y, z);
                for (BlockFace attach : faces) {
                    Block support = ladder.getRelative(attach);
                    if (isFullSupportBlock(support)) return attach.getOppositeFace();
                }
            }
        }

        for (int y = topY; y >= bottomY; y--) {
            for (long k : cluster) {
                if (unpackY(k) != y) continue;
                int x = unpackX(k);
                int z = unpackZ(k);
                Block ladder = world.getBlockAt(x, y, z);
                for (BlockFace attach : faces) {
                    Block support = ladder.getRelative(attach);
                    if (isSlabSupportBlock(support)) return attach.getOppositeFace();
                }
            }
        }

        return null;
    }

    private static boolean isFullSupportBlock(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        if (type.isAir() || block.isPassable()) return false;
        if (!type.isSolid()) return false;
        BlockData data = block.getBlockData();
        if (data instanceof Slab slab) {
            return slab.getType() == Slab.Type.DOUBLE;
        }
        return true;
    }

    private static boolean isSlabSupportBlock(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        if (type.isAir() || block.isPassable()) return false;
        BlockData data = block.getBlockData();
        if (!(data instanceof Slab slab)) return false;
        return slab.getType() != Slab.Type.DOUBLE;
    }

    private static boolean isPassableBlock(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        if (type.isAir()) return true;
        return block.isPassable();
    }

    private static long packBlock(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (long) (y & 0xFFF);
    }

    private static int unpackX(long packed) {
        int x = (int) (packed >> 38);
        if (x >= 0x2000000) x -= 0x4000000;
        return x;
    }

    private static int unpackY(long packed) {
        return (int) (packed & 0xFFF);
    }

    private static int unpackZ(long packed) {
        int z = (int) ((packed >> 12) & 0x3FFFFFF);
        if (z >= 0x2000000) z -= 0x4000000;
        return z;
    }

    private static List<String> filterPrefix(List<String> candidates, String rawPrefix) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        if (rawPrefix == null || rawPrefix.isEmpty()) return candidates;
        String prefix = rawPrefix.toLowerCase(Locale.ROOT);
        List<String> out = null;
        for (String c : candidates) {
            if (c == null) continue;
            if (!c.toLowerCase(Locale.ROOT).startsWith(prefix)) continue;
            if (out == null) out = new ArrayList<>();
            out.add(c);
        }
        return out != null ? out : List.of();
    }

    private void registerPuzzleRoom06IfNeeded(Location roomOrigin, StructureTemplate.Rotation rotation, int sizeX, int sizeZ, String templateName) {
        if (roomOrigin == null || rotation == null || templateName == null) return;
        Test_dungeons plugin = Test_dungeons.getInstance();
        if (plugin == null) return;

        if ("puzzle_room_01".equalsIgnoreCase(templateName)) {
            plugin.registerPuzzleRoom01(roomOrigin.getWorld(), roomOrigin, rotation, sizeX, sizeZ);
            return;
        }

        if ("puzzle_room_06".equalsIgnoreCase(templateName)) {
            plugin.registerPuzzleRoom06(roomOrigin.getWorld(), roomOrigin, rotation, sizeX, sizeZ);
            return;
        }

        if ("puzzle_room_05".equalsIgnoreCase(templateName)) {
            plugin.registerPuzzleRoom05(roomOrigin.getWorld(), roomOrigin, rotation, sizeX, sizeZ);
            return;
        }

        if ("puzzle_room_02".equalsIgnoreCase(templateName)) {
            return;
        }

        if ("puzzle_room_03".equalsIgnoreCase(templateName)) {
            plugin.registerPuzzleRoom03(roomOrigin.getWorld(), roomOrigin, rotation, sizeX, sizeZ);
            return;
        }

        if ("puzzle_room_04".equalsIgnoreCase(templateName)) {
            plugin.registerPuzzleRoom04(roomOrigin.getWorld(), roomOrigin, rotation, sizeX, sizeZ);
            return;
        }

        if ("puzzle_room_07".equalsIgnoreCase(templateName)) {
            plugin.registerPuzzleRoom07(roomOrigin.getWorld(), roomOrigin, rotation, sizeX, sizeZ);
            return;
        }

        if ("puzzle_room_08".equalsIgnoreCase(templateName)) {
            plugin.registerPuzzleRoom08(roomOrigin.getWorld(), roomOrigin, rotation, sizeX, sizeZ);
            return;
        }
    }

    private List<StructureTemplate> loadTemplatesByPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) return List.of();
        String key = prefix.toLowerCase(Locale.ROOT);

        List<StructureTemplate> cached = templatesByPrefixCache.get(key);
        if (cached != null) return cached;

        File dir = new File(Test_dungeons.getInstance().getDataFolder(), "structures");
        if (!dir.exists()) return List.of();
        File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).startsWith(key) && n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) return List.of();

        List<StructureTemplate> templates = new ArrayList<>();
        for (File f : files) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (n.endsWith("_walls.yml") || n.endsWith("_doors.yml")) continue;
            String base = f.getName().substring(0, f.getName().length() - 4);
            if ("puzzle_room_".equals(key) && "puzzle_room_02".equalsIgnoreCase(base)) continue;
            StructureTemplate t = loadTemplateOrNull(base);
            if (t != null) templates.add(t);
        }
        List<StructureTemplate> out = List.copyOf(templates);
        templatesByPrefixCache.put(key, out);
        return out;
    }

    private TemplatePair loadTemplatePairOrNull(String baseName) {
        if (baseName == null || baseName.isEmpty()) return null;
        String key = baseName.toLowerCase(Locale.ROOT);
        if (missingPairBaseNames.contains(key)) return null;

        TemplatePair cached = pairCache.get(key);
        if (cached != null) return cached;

        StructureTemplate walls = loadTemplateOrNull(key + "_walls");
        StructureTemplate doors = loadTemplateOrNull(key + "_doors");
        if (walls == null || doors == null) {
            missingPairBaseNames.add(key);
            return null;
        }
        TemplatePair pair = new TemplatePair(key, walls, doors);
        pairCache.put(key, pair);
        return pair;
    }

    private List<TemplatePair> loadTemplatePairsByPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) return List.of();
        String key = prefix.toLowerCase(Locale.ROOT);

        List<TemplatePair> cached = pairsByPrefixCache.get(key);
        if (cached != null) return cached;

        File dir = new File(Test_dungeons.getInstance().getDataFolder(), "structures");
        if (!dir.exists()) return List.of();
        File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).startsWith(key) && n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) return List.of();

        Map<String, StructureTemplate> walls = new HashMap<>();
        Map<String, StructureTemplate> doors = new HashMap<>();
        for (File f : files) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            boolean isWalls = n.endsWith("_walls.yml");
            boolean isDoors = n.endsWith("_doors.yml");
            if (!isWalls && !isDoors) continue;

            String base = f.getName().substring(0, f.getName().length() - 4);
            if (isWalls) base = base.substring(0, base.length() - "_walls".length());
            if (isDoors) base = base.substring(0, base.length() - "_doors".length());
            base = base.toLowerCase(Locale.ROOT);
            if ("puzzle_room_".equals(key) && "puzzle_room_02".equals(base)) continue;

            StructureTemplate t = loadTemplateOrNull(base + (isWalls ? "_walls" : "_doors"));
            if (t == null) continue;
            if (isWalls) walls.put(base, t);
            if (isDoors) doors.put(base, t);
        }

        if (walls.isEmpty()) return List.of();
        List<TemplatePair> pairs = new ArrayList<>();
        for (Map.Entry<String, StructureTemplate> e : walls.entrySet()) {
            StructureTemplate d = doors.get(e.getKey());
            if (d == null) continue;
            TemplatePair pair = new TemplatePair(e.getKey().toLowerCase(Locale.ROOT), e.getValue(), d);
            pairs.add(pair);
            pairCache.putIfAbsent(pair.baseName(), pair);
        }

        if ("puzzle_room_".equals(key)) {
            String base = "puzzle_room_05";
            if (!walls.containsKey(base) && doors.containsKey(base)) {
                List<String> variants = new ArrayList<>();
                for (String w : walls.keySet()) {
                    if (w != null && w.startsWith(base + "-")) variants.add(w);
                }
                if (!variants.isEmpty()) {
                    variants.sort(String::compareTo);
                    StructureTemplate w = walls.get(variants.get(0));
                    StructureTemplate d = doors.get(base);
                    if (w != null && d != null) {
                        TemplatePair p = new TemplatePair(base, w, d);
                        pairs.add(p);
                        pairCache.putIfAbsent(p.baseName(), p);
                    }
                }
            }
        }

        List<TemplatePair> out = List.copyOf(pairs);
        pairsByPrefixCache.put(key, out);
        return out;
    }

    private List<BlockFace> connectedDirections(SkyBlockDungeon dungeon, int x, int y) {
        List<BlockFace> faces = new ArrayList<>(4);
        if (x - 1 >= 0 && dungeon.getRoom(x - 1, y) != null && dungeon.isConnected(x, y, x - 1, y)) faces.add(BlockFace.WEST);
        if (dungeon.getRoom(x + 1, y) != null && dungeon.isConnected(x, y, x + 1, y)) faces.add(BlockFace.EAST);
        if (y - 1 >= 0 && dungeon.getRoom(x, y - 1) != null && dungeon.isConnected(x, y, x, y - 1)) faces.add(BlockFace.NORTH);
        if (dungeon.getRoom(x, y + 1) != null && dungeon.isConnected(x, y, x, y + 1)) faces.add(BlockFace.SOUTH);
        return faces;
    }

    private StructureTemplate.Rotation pickBestRotation(StructureTemplate template, List<BlockFace> desiredFaces, boolean verbose, Logger log, String ctx) {
        if (desiredFaces == null || desiredFaces.isEmpty()) return StructureTemplate.Rotation.R0;

        int bestScore = Integer.MIN_VALUE;
        StructureTemplate.Rotation best = StructureTemplate.Rotation.R0;

        for (StructureTemplate.Rotation r : StructureTemplate.Rotation.values()) {
            StructureTemplate rotated = template.rotate(r);
            Set<BlockFace> available = new HashSet<>();
            for (StructureTemplate.DoorMarker d : rotated.doorMarkers()) {
                BlockFace face = StructureTemplate.resolveDoorFacing(d, rotated.sizeX(), rotated.sizeZ());
                if (face != BlockFace.SELF) available.add(face);
            }
            int score = 0;
            for (BlockFace f : desiredFaces) {
                if (available.contains(f)) score++;
            }
            if (verbose) {
                log.info(ctx + " rotationCandidate=" + r.name() + " score=" + score + " desiredFaces=" + desiredFaces + " availableFaces=" + available);
            }
            if (score > bestScore) {
                bestScore = score;
                best = r;
            }
        }

        if (verbose) {
            log.info(ctx + " rotationSelected=" + best.name() + " score=" + bestScore + " desiredFaces=" + desiredFaces);
        }
        return best;
    }

    private Material getMaterialForStructure(DungeonStructureType type) {
        return switch (type) {
            case DOUBLE_1x2 -> Material.CYAN_CONCRETE;
            case DOUBLE_2x1 -> Material.CYAN_CONCRETE;
            case QUAD_2x2 -> Material.YELLOW_CONCRETE;
            case L_SHAPE -> Material.BLUE_CONCRETE;
            case TRIPLE_3x1 -> Material.LIGHT_BLUE_CONCRETE;
        };
    }

    /** 若模板名為 boss_room* 或 last_boss_room* 則向 BossRoomManager 註冊 */
    private static void registerBossRoomIfNeeded(org.bukkit.World world, Location base, StructureTemplate.Rotation rotation,
                                                 int sizeX, int sizeZ, String tName, StructureTemplate template, int cellX, int cellY) {
        if (world == null || base == null || tName == null) return;
        maple.underground_caves.dungeon.BossRoomManager mgr = Test_dungeons.getInstance().getBossRoomManager();
        if (mgr == null) return;
        if (tName.startsWith("boss_room")) {
            if (template != null) mgr.registerBossRoom(world, base, rotation, sizeX, sizeZ, tName, template, cellX, cellY);
        } else if (tName.startsWith("last_boss_room")) {
            mgr.registerLastBossRoom(world, base, tName, cellX, cellY);
        }
    }

    /** 若模板名為 exit_room_f* 則向 ExitRoomManager 註冊（頭顱緩存、9 種怪各 2 隻、每 5 秒生成） */
    private static void registerExitRoomIfNeeded(org.bukkit.World world, Location base, StructureTemplate.Rotation rotation,
                                                 int sizeX, int sizeZ, String tName, StructureTemplate template, int cellX, int cellY) {
        if (world == null || base == null || tName == null) return;
        maple.underground_caves.dungeon.ExitRoomManager mgr = Test_dungeons.getInstance().getExitRoomManager();
        if (mgr == null) return;
        if (tName.toLowerCase(java.util.Locale.ROOT).startsWith("exit_room_f") && template != null) {
            mgr.registerExitRoom(world, base, rotation, sizeX, sizeZ, tName, template, cellX, cellY);
        }
    }

    private Material getMaterialForType(DungeonRoomType type) {
        return switch (type) {
            case ENTRANCE -> Material.LIME_CONCRETE;
            case EXIT -> Material.RED_CONCRETE;
            case FAIRY -> Material.PINK_CONCRETE;
            case PUZZLE -> Material.PURPLE_CONCRETE;
            case BLOOD -> Material.YELLOW_CONCRETE;
            case TRAP -> Material.ORANGE_CONCRETE;
            case MINI_BOSS -> Material.YELLOW_CONCRETE;
            case BASE -> Material.WHITE_CONCRETE;
            default -> Material.LIGHT_GRAY_CONCRETE;
        };
    }

    private static String keyForPair(TemplatePair pair) {
        if (pair == null) return "pair:null";
        String base = pair.baseName();
        if (base == null) return "pair:nullBase";
        return "pair:" + base.toLowerCase(Locale.ROOT);
    }

    private static String keyForTemplate(StructureTemplate template) {
        if (template == null) return "tpl:null";
        return "tpl:" + templateDigest(template);
    }

    private static String templateDigest(StructureTemplate template) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        updateInt(md, template.sizeX());
        updateInt(md, template.sizeY());
        updateInt(md, template.sizeZ());

        List<StructureTemplate.TemplateBlock> blocks = new ArrayList<>(template.blocks());
        blocks.sort(Comparator
                .comparingInt(StructureTemplate.TemplateBlock::x)
                .thenComparingInt(StructureTemplate.TemplateBlock::y)
                .thenComparingInt(StructureTemplate.TemplateBlock::z)
                .thenComparing(StructureTemplate.TemplateBlock::blockData, Comparator.nullsFirst(String::compareTo)));
        for (StructureTemplate.TemplateBlock b : blocks) {
            updateInt(md, b.x());
            updateInt(md, b.y());
            updateInt(md, b.z());
            updateString(md, b.blockData());
        }

        List<StructureTemplate.DoorMarker> doors = new ArrayList<>(template.doorMarkers());
        doors.sort(Comparator
                .comparingInt(StructureTemplate.DoorMarker::x)
                .thenComparingInt(StructureTemplate.DoorMarker::y)
                .thenComparingInt(StructureTemplate.DoorMarker::z)
                .thenComparing(d -> d.facing() != null ? d.facing().name() : "", Comparator.nullsFirst(String::compareTo)));
        for (StructureTemplate.DoorMarker d : doors) {
            updateInt(md, d.x());
            updateInt(md, d.y());
            updateInt(md, d.z());
            updateString(md, d.facing() != null ? d.facing().name() : null);
        }

        return Base64.getUrlEncoder().withoutPadding().encodeToString(md.digest());
    }

    private static void updateInt(MessageDigest md, int v) {
        md.update((byte) (v >>> 24));
        md.update((byte) (v >>> 16));
        md.update((byte) (v >>> 8));
        md.update((byte) v);
    }

    private static void updateString(MessageDigest md, String s) {
        if (s == null) {
            updateInt(md, -1);
            return;
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        updateInt(md, bytes.length);
        md.update(bytes);
    }

    private record TemplatePair(String baseName, StructureTemplate walls, StructureTemplate doors) {}
}
