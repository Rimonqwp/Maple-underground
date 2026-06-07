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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("ALL")
public final class PuzzleRoom04LiarNPC {
    private static final int NPC_TOTAL = 3;
    private static final LocalPos[] NPC_POS = new LocalPos[]{
            new LocalPos(7, 4, 13),
            new LocalPos(5, 4, 14),
            new LocalPos(7, 4, 17),
    };
    private static final LocalPos[] CHEST_POS = new LocalPos[]{
            new LocalPos(6, 4, 17),
            new LocalPos(5, 4, 15),
            new LocalPos(6, 4, 13),
    };

    private final Test_dungeons plugin;
    private final Map<String, RoomState> roomsByKey = new HashMap<>();
    private final List<StatementGroup> groups = new ArrayList<>();
    private long configLoadedAt;

    public PuzzleRoom04LiarNPC(Test_dungeons plugin) {
        this.plugin = plugin;
    }

    public boolean isPuzzleRoom04Npc(Entity entity) {
        if (!(entity instanceof Villager villager)) return false;
        World world = villager.getWorld();
        if (world == null) return false;
        String wn = world.getName();
        UUID id = villager.getUniqueId();
        for (RoomState room : List.copyOf(roomsByKey.values())) {
            if (room == null) continue;
            if (!wn.equals(room.worldName)) continue;
            if (!room.npcIds.contains(id)) continue;
            return true;
        }
        return false;
    }

    public void onDisable() {
        for (RoomState room : List.copyOf(roomsByKey.values())) {
            if (room == null) continue;
            cleanupRoom(room);
        }
        roomsByKey.clear();
    }

    public void registerPuzzleRoom04(World world, Location roomOrigin, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
        if (world == null || roomOrigin == null || rotation == null) return;
        Location base = roomOrigin.getBlock().getLocation();
        String roomKey = world.getName() + ":" + base.getBlockX() + ":" + base.getBlockY() + ":" + base.getBlockZ();

        RoomState old = roomsByKey.remove(roomKey);
        if (old != null) cleanupRoom(old);

        maybeLoadConfig();
        RoomState room = new RoomState(roomKey, world.getName(), rotation, base.getBlockX(), base.getBlockY(), base.getBlockZ(), sizeX, sizeZ);
        for (int i = 0; i < NPC_TOTAL; i++) {
            room.npcSpawns[i] = toWorldLocation(base, NPC_POS[i], rotation, sizeX, sizeZ).add(0.5, 0.0, 0.5);
        }
        for (int i = 0; i < NPC_TOTAL; i++) {
            room.chestBlocks[i] = toWorldBlock(base, CHEST_POS[i], rotation, sizeX, sizeZ);
        }
        int[] bounds = computeXZBounds(base, rotation, sizeX, sizeZ);
        room.minX = bounds[0];
        room.maxX = bounds[1];
        room.minZ = bounds[2];
        room.maxZ = bounds[3];

        pickGroup(room);
        roomsByKey.put(roomKey, room);
        startPresenceScan(room);

        if (plugin != null && plugin.getLogger() != null) {
            plugin.getLogger().info("puzzle_room_04 registered world=" + world.getName()
                    + " origin=" + base.getBlockX() + "," + base.getBlockY() + "," + base.getBlockZ()
                    + " rotation=" + rotation.name() + " size=" + sizeX + "x" + sizeZ
                    + " group=" + (room.group != null ? room.group.id : "null"));
        }
    }

    public boolean handleInteract(PlayerInteractEvent event) {
        if (event == null) return false;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return false;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return false;
        World world = clicked.getWorld();
        if (world == null) return false;

        RoomState room = findRoomByBlock(world, clicked.getX(), clicked.getY(), clicked.getZ());
        if (room == null) return false;

        int chestIndex = resolveChestIndex(room, clicked);
        if (chestIndex < 0) return false;

        if (!room.spawned) {
            spawnForRoom(room);
        }

        if (room.completed) {
            event.setCancelled(true);
            return true;
        }
        if (room.failed) {
            event.setCancelled(true);
            return true;
        }

        boolean correct = room.correctChestIndex == chestIndex;
        if (correct) {
            room.completed = true;
            sendToNearby(room, Component.text("回答正确", NamedTextColor.GOLD));
            setNpcNameVisible(room, false);
            markPuzzleCompleted(world, room);
            log(room, "chest_correct idx=" + chestIndex + " pos=" + clicked.getX() + "," + clicked.getY() + "," + clicked.getZ());
            return true;
        }

        event.setCancelled(true);
        room.failed = true;
        openChestVisual(world, clicked.getX(), clicked.getY(), clicked.getZ());
        sendToNearby(room, Component.text("回答错误", NamedTextColor.RED));
        revealCorrectNpc(room, 40L);
        markPuzzleFailed(world, room);
        log(room, "chest_wrong idx=" + chestIndex + " correct=" + room.correctChestIndex + " pos=" + clicked.getX() + "," + clicked.getY() + "," + clicked.getZ());
        return true;
    }

    public boolean handleInteractEntity(PlayerInteractEntityEvent event) {
        if (event == null) return false;
        Entity clicked = event.getRightClicked();
        if (clicked == null) return false;
        if (!(clicked instanceof Villager)) return false;
        World world = clicked.getWorld();
        if (world == null) return false;

        String wn = world.getName();
        UUID id = clicked.getUniqueId();
        for (RoomState room : List.copyOf(roomsByKey.values())) {
            if (room == null) continue;
            if (!wn.equals(room.worldName)) continue;
            if (!room.npcIds.contains(id)) continue;
            event.setCancelled(true);
            return true;
        }
        return false;
    }

    private void startPresenceScan(RoomState room) {
        if (plugin == null || !plugin.isEnabled()) return;
        if (room == null) return;
        if (room.presenceTask != null) {
            room.presenceTask.cancel();
            room.presenceTask = null;
        }
        room.presenceTask = new BukkitRunnable() {
            @Override
            public void run() {
                World world = Bukkit.getWorld(room.worldName);
                if (world == null) return;
                List<Player> inside = new ArrayList<>();
                for (Player p : world.getPlayers()) {
                    if (p == null) continue;
                    if (!insideRoomXZ(room, p.getLocation())) continue;
                    inside.add(p);
                }

                boolean anyInside = !inside.isEmpty();
                if (anyInside != room.hasPlayersInside) {
                    room.hasPlayersInside = anyInside;
                    if (!anyInside) {
                        if (!room.completed) room.notifiedPlayers.clear();
                        setNpcNameVisible(room, false);
                    } else {
                        if (!room.spawned) spawnForRoom(room);
                        setNpcNameVisible(room, !room.completed);
                    }
                }

                if (!anyInside) return;
                if (!room.spawned) spawnForRoom(room);
                if (room.completed) {
                    setNpcNameVisible(room, false);
                    return;
                }

                setNpcNameVisible(room, true);
                for (Player p : inside) {
                    notifyPlayerIfNeeded(room, p);
                }
            }
        };
        room.presenceTask.runTaskTimer(plugin, 5L, 10L);
    }

    private void notifyPlayerIfNeeded(RoomState room, Player p) {
        if (room == null || p == null) return;
        if (room.group == null) return;
        UUID id = p.getUniqueId();
        if (room.notifiedPlayers.contains(id)) return;
        room.notifiedPlayers.add(id);
        p.sendMessage(Component.text("三位 NPC 会说一句话，其中可能有人在说谎", NamedTextColor.AQUA));
        for (int i = 0; i < NPC_TOTAL; i++) {
            String line = room.group.npcStatements[i] != null ? room.group.npcStatements[i] : "";
            p.sendMessage(Component.text("NPC" + (i + 1) + ": ", NamedTextColor.GOLD).append(Component.text(line, NamedTextColor.YELLOW)));
        }
        p.sendMessage(Component.text("请打开你认为正确的秘密箱子", NamedTextColor.AQUA));
    }

    private void spawnForRoom(RoomState room) {
        if (room == null) return;
        if (room.spawned) return;
        room.spawned = true;

        World world = Bukkit.getWorld(room.worldName);
        if (world == null) return;

        for (int i = 0; i < NPC_TOTAL; i++) {
            Location at = room.npcSpawns[i];
            if (at == null) continue;
            final int idx = i + 1;
            final String line = room.group != null ? room.group.npcStatements[i] : null;
            final Component name = Component.text("NPC" + idx + ": ", NamedTextColor.GOLD)
                    .append(Component.text(line != null ? line : "NPC", NamedTextColor.YELLOW));
            Villager v = world.spawn(at, Villager.class, villager -> {
                villager.setAI(false);
                villager.setGravity(false);
                villager.setSilent(true);
                villager.setCollidable(true);
                villager.setInvulnerable(false);
                villager.setRemoveWhenFarAway(false);
                villager.customName(name);
                villager.setCustomNameVisible(false);
            });
            room.npcIds.add(v.getUniqueId());
            room.npcIdsByIndex[i] = v.getUniqueId();
        }

        for (int i = 0; i < NPC_TOTAL; i++) {
            BlockPos b = room.chestBlocks[i];
            if (b == null) continue;
            Block block = world.getBlockAt(b.x, b.y, b.z);
            if (block.getType().isAir()) {
                block.setType(Material.CHEST, false);
            }
        }

        log(room, "spawned correct=" + room.correctChestIndex);
    }

    private void cleanupRoom(RoomState room) {
        if (room == null) return;
        if (room.presenceTask != null) {
            room.presenceTask.cancel();
            room.presenceTask = null;
        }
        World world = Bukkit.getWorld(room.worldName);
        if (world != null) {
            for (UUID id : List.copyOf(room.npcIds)) {
                if (id == null) continue;
                Entity e = world.getEntity(id);
                if (e != null) e.remove();
            }
        }
        room.npcIds.clear();
    }

    private void pickGroup(RoomState room) {
        if (room == null) return;
        if (groups.isEmpty()) {
            room.group = null;
            room.correctChestIndex = 0;
            room.failed = true;
            return;
        }
        Random rng = new Random(System.nanoTime() ^ room.roomKey.hashCode());
        room.group = groups.get(rng.nextInt(groups.size()));
        room.correctChestIndex = clampChestIndex(room.group.correctChestIndex);
    }

    private int clampChestIndex(int idx) {
        if (idx < 0) return 0;
        if (idx >= NPC_TOTAL) return NPC_TOTAL - 1;
        return idx;
    }

    private void maybeLoadConfig() {
        if (plugin == null) return;
        long now = System.currentTimeMillis();
        if (now - configLoadedAt < 2000L && !groups.isEmpty()) return;
        configLoadedAt = now;

        File file = configFile();
        if (!file.exists()) {
            try {
                plugin.saveResource("puzzle_room_04.yml", false);
            } catch (Throwable t) {
                if (plugin.getLogger() != null) plugin.getLogger().warning("puzzle_room_04 saveResource failed: " + t.getMessage());
            }
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("groups");
        List<StatementGroup> loaded = new ArrayList<>();
        if (root != null) {
            for (String key : root.getKeys(false)) {
                if (key == null) continue;
                ConfigurationSection g = root.getConfigurationSection(key);
                if (g == null) continue;
                List<String> npc = g.getStringList("npc");
                if (npc == null || npc.size() < 3) continue;
                String c = g.getString("correct");
                int correct = parseCorrect(c, g.getInt("correct_index", -1));
                String[] s = new String[NPC_TOTAL];
                for (int i = 0; i < NPC_TOTAL; i++) {
                    s[i] = npc.get(i);
                }
                loaded.add(new StatementGroup(key, s, correct));
            }
        }

        groups.clear();
        groups.addAll(loaded);
        if (plugin.getLogger() != null) {
            plugin.getLogger().info("puzzle_room_04 loaded groups=" + groups.size() + " file=" + file.getName());
        }
    }

    private int parseCorrect(String raw, int fallback) {
        if (raw != null) {
            String t = raw.trim().toUpperCase(Locale.ROOT);
            if ("A".equals(t)) return 0;
            if ("B".equals(t)) return 1;
            if ("C".equals(t)) return 2;
            try {
                int n = Integer.parseInt(t);
                if (n >= 0 && n < NPC_TOTAL) return n;
                if (n >= 1 && n <= NPC_TOTAL) return n - 1;
            } catch (Throwable ignored) {
            }
        }
        return fallback;
    }

    private File configFile() {
        File dir = plugin.getDataFolder();
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "puzzle_room_04.yml");
    }

    private RoomState findRoomByBlock(World world, int x, int y, int z) {
        if (world == null) return null;
        String wn = world.getName();
        for (RoomState room : List.copyOf(roomsByKey.values())) {
            if (room == null) continue;
            if (!wn.equals(room.worldName)) continue;
            if (!insideRoom(room, x, y, z)) continue;
            return room;
        }
        return null;
    }

    private boolean insideRoom(RoomState room, int x, int y, int z) {
        if (room == null) return false;
        if (x < room.minX || x > room.maxX) return false;
        if (z < room.minZ || z > room.maxZ) return false;
        int minY = room.baseY - 5;
        int maxY = room.baseY + 80;
        return y >= minY && y <= maxY;
    }

    private boolean insideRoomXZ(RoomState room, Location loc) {
        if (room == null || loc == null) return false;
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(room.worldName)) return false;
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        return x >= room.minX && x <= room.maxX && z >= room.minZ && z <= room.maxZ;
    }

    private int resolveChestIndex(RoomState room, Block clicked) {
        if (room == null || clicked == null) return -1;
        int cx = clicked.getX();
        int cy = clicked.getY();
        int cz = clicked.getZ();
        for (int i = 0; i < NPC_TOTAL; i++) {
            BlockPos b = room.chestBlocks[i];
            if (b == null) continue;
            if (b.x == cx && b.y == cy && b.z == cz) return i;
        }
        return -1;
    }

    private void sendToNearby(RoomState room, Component msg) {
        if (room == null || msg == null) return;
        World world = Bukkit.getWorld(room.worldName);
        if (world == null) return;
        int cx = (room.minX + room.maxX) / 2;
        int cz = (room.minZ + room.maxZ) / 2;
        double maxDistSq = 45.0 * 45.0;
        for (Player p : world.getPlayers()) {
            if (p == null) continue;
            Location l = p.getLocation();
            double dx = l.getX() - (cx + 0.5);
            double dz = l.getZ() - (cz + 0.5);
            if (dx * dx + dz * dz > maxDistSq) continue;
            p.sendMessage(msg);
        }
    }

    private void markPuzzleCompleted(World world, RoomState room) {
        if (plugin == null || world == null || room == null) return;
        DungeonSecrets secrets = plugin.getDungeonSecrets();
        if (secrets == null) return;
        secrets.markPuzzleCompleted(world, new Location(world, room.baseX, room.baseY, room.baseZ));
    }

    private void markPuzzleFailed(World world, RoomState room) {
        if (plugin == null || world == null || room == null) return;
        DungeonSecrets secrets = plugin.getDungeonSecrets();
        if (secrets == null) return;
        secrets.markPuzzleFailed(world, new Location(world, room.baseX, room.baseY, room.baseZ));
    }

    private void openChestVisual(World world, int x, int y, int z) {
        if (plugin == null || world == null) return;
        DungeonSecrets secrets = plugin.getDungeonSecrets();
        if (secrets == null) return;
        try {
            Method m = DungeonSecrets.class.getDeclaredMethod("setChestOpenedVisual", World.class, int.class, int.class, int.class);
            m.setAccessible(true);
            m.invoke(null, world, x, y, z);
        } catch (Throwable ignored) {
        }
    }

    private void spawnBlessing(World world, int x, int y, int z) {
        if (plugin == null || world == null) return;
        DungeonSecrets secrets = plugin.getDungeonSecrets();
        if (secrets == null) return;
        try {
            Method m = DungeonSecrets.class.getDeclaredMethod("spawnBlessingEffect", World.class, int.class, int.class, int.class);
            m.setAccessible(true);
            m.invoke(secrets, world, Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(z));
        } catch (Throwable ignored) {
        }
    }

    private void log(RoomState room, String message) {
        if (plugin == null || plugin.getLogger() == null || room == null || message == null) return;
        plugin.getLogger().info("puzzle_room_04 " + room.roomKey + " " + message);
    }

    private void revealCorrectNpc(RoomState room, long durationTicks) {
        if (plugin == null || !plugin.isEnabled()) return;
        if (room == null) return;
        if (room.revealedCorrectNpc) return;
        room.revealedCorrectNpc = true;

        int idx = clampChestIndex(room.correctChestIndex);
        UUID id = room.npcIdsByIndex[idx];
        if (id == null) return;
        World world = Bukkit.getWorld(room.worldName);
        if (world == null) return;
        Entity e = world.getEntity(id);
        if (e == null) return;

        try {
            e.setGlowing(true);
        } catch (Throwable ignored) {
            return;
        }

        long ticks = Math.max(1L, durationTicks);
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Entity cur = world.getEntity(id);
                    if (cur != null) cur.setGlowing(false);
                } catch (Throwable ignored) {
                }
            }
        }.runTaskLater(plugin, ticks);
    }

    private void setNpcNameVisible(RoomState room, boolean visible) {
        if (room == null) return;
        World world = Bukkit.getWorld(room.worldName);
        if (world == null) return;
        for (UUID id : List.copyOf(room.npcIds)) {
            if (id == null) continue;
            Entity e = world.getEntity(id);
            if (!(e instanceof Villager v)) continue;
            try {
                v.setCustomNameVisible(visible);
            } catch (Throwable ignored) {
            }
        }
    }

    private static int[] computeXZBounds(Location base, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
        int[] a = rotateLocalXZ(0, 0, rotation, sizeX, sizeZ);
        int[] b = rotateLocalXZ(sizeX - 1, 0, rotation, sizeX, sizeZ);
        int[] c = rotateLocalXZ(0, sizeZ - 1, rotation, sizeX, sizeZ);
        int[] d = rotateLocalXZ(sizeX - 1, sizeZ - 1, rotation, sizeX, sizeZ);
        int bx = base.getBlockX();
        int bz = base.getBlockZ();
        int minX = bx + Math.min(Math.min(a[0], b[0]), Math.min(c[0], d[0]));
        int maxX = bx + Math.max(Math.max(a[0], b[0]), Math.max(c[0], d[0]));
        int minZ = bz + Math.min(Math.min(a[1], b[1]), Math.min(c[1], d[1]));
        int maxZ = bz + Math.max(Math.max(a[1], b[1]), Math.max(c[1], d[1]));
        return new int[]{minX, maxX, minZ, maxZ};
    }

    private static BlockPos toWorldBlock(Location base, LocalPos local, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
        int[] rotated = rotateLocalXZ(local.x, local.z, rotation, sizeX, sizeZ);
        int wx = base.getBlockX() + rotated[0];
        int wy = base.getBlockY() + local.y;
        int wz = base.getBlockZ() + rotated[1];
        return new BlockPos(wx, wy, wz);
    }

    private static Location toWorldLocation(Location base, LocalPos local, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
        int[] rotated = rotateLocalXZ(local.x, local.z, rotation, sizeX, sizeZ);
        return new Location(base.getWorld(), base.getBlockX() + rotated[0], base.getBlockY() + local.y, base.getBlockZ() + rotated[1]);
    }

    private static int[] rotateLocalXZ(int x, int z, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
        return switch (rotation) {
            case R0 -> new int[]{x, z};
            case R90 -> new int[]{z, sizeX - 1 - x};
            case R180 -> new int[]{sizeX - 1 - x, sizeZ - 1 - z};
            case R270 -> new int[]{sizeZ - 1 - z, x};
        };
    }

    private record LocalPos(int x, int y, int z) {
    }

    private record BlockPos(int x, int y, int z) {
    }

    private record StatementGroup(String id, String[] npcStatements, int correctChestIndex) {
    }

    private static final class RoomState {
        private final String roomKey;
        private final String worldName;
        private final StructureTemplate.Rotation rotation;
        private final int baseX;
        private final int baseY;
        private final int baseZ;
        private final int sizeX;
        private final int sizeZ;

        private int minX;
        private int maxX;
        private int minZ;
        private int maxZ;

        private final Location[] npcSpawns = new Location[NPC_TOTAL];
        private final BlockPos[] chestBlocks = new BlockPos[NPC_TOTAL];
        private final Set<UUID> npcIds = new HashSet<>();
        private final UUID[] npcIdsByIndex = new UUID[NPC_TOTAL];
        private final Set<UUID> notifiedPlayers = new HashSet<>();

        private StatementGroup group;
        private int correctChestIndex;
        private boolean spawned;
        private boolean failed;
        private boolean completed;
        private boolean revealedCorrectNpc;
        private boolean hasPlayersInside;

        private BukkitRunnable presenceTask;

        private RoomState(String roomKey, String worldName, StructureTemplate.Rotation rotation, int baseX, int baseY, int baseZ, int sizeX, int sizeZ) {
            this.roomKey = roomKey;
            this.worldName = worldName;
            this.rotation = rotation;
            this.baseX = baseX;
            this.baseY = baseY;
            this.baseZ = baseZ;
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
        }
    }
}
