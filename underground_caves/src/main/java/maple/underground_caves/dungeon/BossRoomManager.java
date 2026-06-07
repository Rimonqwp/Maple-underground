package maple.underground_caves.dungeon;

import maple.underground_caves.Test_dungeons;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Boss 房流程：玩家打開 boss_room 門後，依模板內頭顱位置列表隨機選點，
 * 移除頭顱、生成旋轉盔甲架並朝房間中心移動，約每 5 秒在該點生成一隻怪物；
 * 全部怪物擊殺後在中心半徑 2 格內生成兩個祝福盔甲架、在設定的傳送門位置生成終界傳送門，
 * 並將玩家傳送到 last_boss_room。
 */
public final class BossRoomManager implements Listener {

    private static final int SPAWN_INTERVAL_TICKS = 100; // 約 5 秒
    private static final int ARMOR_STAND_MOVE_TICKS = 40;
    private static final double CENTER_RADIUS = 2.0;

    private final Test_dungeons plugin;
    private final DungeonSecrets dungeonSecrets;

    /** key: worldName + ":" + cellX + ":" + cellY */
    private final Map<String, BossRoomState> bossRooms = new ConcurrentHashMap<>();
    /** key: worldName + ":" + floor (F1/F2/F3) -> 傳送目標（房間內一點） */
    private final Map<String, Location> lastBossDestinations = new ConcurrentHashMap<>();
    /** key: worldName + ":" + floor -> 房間 origin（用於計算 local 29,17,10 傳送點與 BOSS 生成） */
    private final Map<String, Location> lastBossRoomOrigins = new ConcurrentHashMap<>();
    /** 正在進行的生成任務，key 同 bossRooms */
    private final Map<String, SpawnTask> activeSpawns = new ConcurrentHashMap<>();
    /** 該 boss 房已生成且未死的怪物 */
    private final Map<String, Set<UUID>> roomMonsters = new ConcurrentHashMap<>();

    public BossRoomManager(Test_dungeons plugin, DungeonSecrets dungeonSecrets) {
        this.plugin = plugin;
        this.dungeonSecrets = dungeonSecrets;
    }

    /**
     * 從模板名解析樓層：boss_room_F1、boss_room_F2_north、last_boss_room_F3 等。
     * 支援 F1 / F2 / F3，未匹配則為 F1。
     */
    private static String parseFloorFromTemplateName(String templateName) {
        if (templateName == null) return "F1";
        String n = templateName.toUpperCase(java.util.Locale.ROOT);
        if (n.contains("_F3")) return "F3";
        if (n.contains("_F2")) return "F2";
        if (n.contains("_F1")) return "F1";
        return "F1";
    }

    /**
     * 註冊一個 boss_room：模板名需以 "boss_room" 開頭；頭顱＝出生點、portalMarkers＝傳送門位置，貼上時會隨房間旋轉。
     * 模板名可含樓層，例如 boss_room_F1、boss_room_F2_north，用於對應 last_boss_room_F1/F2/F3。
     */
    public void registerBossRoom(World world, Location origin, StructureTemplate.Rotation rotation,
                                 int sizeX, int sizeZ, String templateName, StructureTemplate template, int cellX, int cellY) {
        if (world == null || origin == null || template == null || !templateName.startsWith("boss_room")) return;
        String floor = parseFloorFromTemplateName(templateName);
        StructureTemplate rotated = rotation == StructureTemplate.Rotation.R0 ? template : template.rotate(rotation);
        int szX = rotated.sizeX();
        int szZ = rotated.sizeZ();
        List<Location> spawnWorldPositions = new ArrayList<>();
        for (StructureTemplate.BlockPos p : rotated.skulls().keySet()) {
            spawnWorldPositions.add(origin.clone().add(p.x(), p.y(), p.z()));
        }
        Location portalWorld = null;
        if (rotated.portalMarkers() != null && !rotated.portalMarkers().isEmpty()) {
            StructureTemplate.BlockPos first = rotated.portalMarkers().get(0);
            portalWorld = origin.clone().add(first.x(), first.y(), first.z());
        }
        double centerX = origin.getX() + szX / 2.0;
        double centerZ = origin.getZ() + szZ / 2.0;
        Location center = new Location(world, centerX, origin.getY(), centerZ);
        String key = key(world.getName(), cellX, cellY);
        bossRooms.put(key, new BossRoomState(origin, szX, szZ, spawnWorldPositions, portalWorld, center, floor));
    }

    /**
     * 註冊 last_boss_room 傳送目標：模板名需以 "last_boss_room" 開頭，並含樓層，例如 last_boss_room_F1、last_boss_room_F2。
     * 會存 origin 供計算 local (29,17,10) 傳送點與 BOSS 生成位置 (29,17,29)。
     */
    public void registerLastBossRoom(World world, Location origin, String templateName, int cellX, int cellY) {
        if (world == null || origin == null) return;
        String floor = parseFloorFromTemplateName(templateName);
        String key = world.getName() + ":" + floor;
        lastBossRoomOrigins.put(key, origin.clone());
        lastBossDestinations.put(key, origin.clone().add(0.5, 0, 0.5));
    }

    /** 取得 exit 房傳送門進入後，整隊應傳送到的位置：last_boss_room 內 local (29, 17, 10)。 */
    public Location getLastBossTeleportTarget(String worldName, String floor) {
        if (worldName == null || floor == null) return null;
        Location origin = lastBossRoomOrigins.get(worldName + ":" + floor);
        if (origin == null) return null;
        return origin.clone().add(29, 17, 10);
    }

    /** 取得 last_boss_room 的房間 origin（用於第二隻 BOSS 死後生成箱子等 local 座標計算）。 */
    public Location getLastBossRoomOrigin(String worldName, String floor) {
        if (worldName == null || floor == null) return null;
        Location o = lastBossRoomOrigins.get(worldName + ":" + floor);
        return o != null ? o.clone() : null;
    }

    /**
     * 玩家打開門進入房間時呼叫；若該房間為已註冊的 boss_room 則開始生成流程。
     */
    public void onDoorOpened(World world, int roomCellX, int roomCellY) {
        if (world == null) return;
        String key = key(world.getName(), roomCellX, roomCellY);
        BossRoomState state = bossRooms.get(key);
        if (state == null || state.spawnPositions.isEmpty()) return;
        if (activeSpawns.containsKey(key)) return; // 已開始過
        List<Location> copy = new ArrayList<>(state.spawnPositions);
        Collections.shuffle(copy);
        roomMonsters.put(key, ConcurrentHashMap.newKeySet());
        SpawnTask task = new SpawnTask(plugin, key, state, copy, () -> onBossRoomCleared(key));
        activeSpawns.put(key, task);
        task.start();
    }

    private void onBossRoomCleared(String roomKey) {
        if (roomKey == null) return;
        activeSpawns.remove(roomKey);
        BossRoomState state = bossRooms.get(roomKey);
        if (state == null) return;
        World world = state.origin.getWorld();
        if (world == null) return;
        // 中心半徑 2 格內隨機兩點生成祝福
        Random r = new Random();
        for (int i = 0; i < 2; i++) {
            double dx = (r.nextDouble() * 2 - 1) * CENTER_RADIUS;
            double dz = (r.nextDouble() * 2 - 1) * CENTER_RADIUS;
            int bx = (int) Math.floor(state.center.getX() + dx);
            int by = state.center.getBlockY();
            int bz = (int) Math.floor(state.center.getZ() + dz);
            if (dungeonSecrets != null) {
                try {
                    var m = DungeonSecrets.class.getDeclaredMethod("spawnBlessingEffect", World.class, int.class, int.class, int.class);
                    m.setAccessible(true);
                    m.invoke(dungeonSecrets, world, Integer.valueOf(bx), Integer.valueOf(by), Integer.valueOf(bz));
                } catch (Throwable ignored) {}
            }
        }
        // 傳送門位置放置終界傳送門（簡化：用終界傳送門方塊）
        if (state.portalWorld != null) {
            Block portalBlock = state.portalWorld.getBlock();
            portalBlock.setType(Material.END_PORTAL_FRAME, false);
            // 可依需求改為 2x3 傳送門
        }
        // 依樓層傳送該房內玩家到對應 last_boss_room（F1/F2/F3）
        String destKey = world.getName() + ":" + state.floor;
        Location dest = lastBossDestinations.get(destKey);
        if (dest != null) {
            BoundingBox roomBox = BoundingBox.of(state.origin.getBlock(), state.origin.getBlock().getRelative(state.sizeX, 0, state.sizeZ));
            for (Player p : world.getPlayers()) {
                if (p.getWorld().equals(world) && roomBox.contains(p.getBoundingBox())) {
                    p.teleport(dest);
                }
            }
        }
        roomMonsters.remove(roomKey);
    }

    void addSpawnedMob(String roomKey, UUID entityUuid) {
        Set<UUID> set = roomMonsters.get(roomKey);
        if (set != null) set.add(entityUuid);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event == null || event.getEntity() == null) return;
        UUID id = event.getEntity().getUniqueId();
        for (Map.Entry<String, Set<UUID>> e : roomMonsters.entrySet()) {
            if (e.getValue().remove(id)) {
                String roomKey = e.getKey();
                SpawnTask task = activeSpawns.get(roomKey);
                if (task != null && task.isAllSpawned() && e.getValue().isEmpty()) {
                    task.onAllKilled();
                }
                break;
            }
        }
    }

    private static String key(String worldName, int cellX, int cellY) {
        return worldName + ":" + cellX + ":" + cellY;
    }

    private record BossRoomState(Location origin, int sizeX, int sizeZ, List<Location> spawnPositions,
                                 Location portalWorld, Location center, String floor) {}

    private static final class SpawnTask {
        private final Test_dungeons plugin;
        private final String roomKey;
        private final BossRoomState state;
        private final List<Location> remainingSpawns;
        private final Runnable onCleared;
        private BukkitRunnable runnable;
        private int spawnedCount;

        SpawnTask(Test_dungeons plugin, String roomKey, BossRoomState state, List<Location> remainingSpawns, Runnable onCleared) {
            this.plugin = plugin;
            this.roomKey = roomKey;
            this.state = state;
            this.remainingSpawns = remainingSpawns;
            this.onCleared = onCleared;
        }

        void start() {
            runnable = new BukkitRunnable() {
                int tick = 0;

                @Override
                public void run() {
                    tick++;
                    if (remainingSpawns.isEmpty()) {
                        cancel();
                        return;
                    }
                    if (tick % SPAWN_INTERVAL_TICKS != 0) return;
                    Location spawnAt = remainingSpawns.remove(0);
                    spawnOne(spawnAt);
                    spawnedCount++;
                }
            };
            runnable.runTaskTimer(plugin, SPAWN_INTERVAL_TICKS, 1L);
        }

        private void spawnOne(Location skullWorldPos) {
            World world = skullWorldPos.getWorld();
            if (world == null) return;
            Block skullBlock = skullWorldPos.getBlock();
            skullBlock.setType(Material.AIR, false);

            Location spawnLoc = skullWorldPos.clone().add(0.5, 0, 0.5);
            ArmorStand as = world.spawn(spawnLoc, ArmorStand.class, stand -> {
                stand.setVisible(false);
                stand.setGravity(false);
                stand.setMarker(true);
                stand.setSmall(true);
            });

            Vector toCenter = state.center.clone().add(0.5, 0, 0.5).toVector().subtract(spawnLoc.toVector()).normalize().multiply(0.15);
            float yawPerTick = 8f;
            new BukkitRunnable() {
                int t = 0;

                @Override
                public void run() {
                    if (!as.isValid() || as.isDead()) {
                        cancel();
                        return;
                    }
                    if (t >= ARMOR_STAND_MOVE_TICKS) {
                        as.remove();
                        Random rand = new Random();
                        Location mobLoc = state.center.clone().add(
                                (rand.nextDouble() * 2 - 1) * CENTER_RADIUS,
                                0,
                                (rand.nextDouble() * 2 - 1) * CENTER_RADIUS
                        );
                        mobLoc.setY(state.center.getY());
                        LivingEntity mob = world.spawn(mobLoc, Zombie.class, z -> {
                            z.setAdult();
                            z.setCustomName(org.bukkit.ChatColor.RED + "Boss 房怪物");
                        });
                        BossRoomManager mgr = plugin.getBossRoomManager();
                        if (mgr != null) mgr.addSpawnedMob(roomKey, mob.getUniqueId());
                        cancel();
                        return;
                    }
                    as.teleport(as.getLocation().add(toCenter));
                    as.setHeadPose(as.getHeadPose().add(0, Math.toRadians(yawPerTick), 0));
                    t++;
                }
            }.runTaskTimer(plugin, 1L, 1L);
        }

        boolean isAllSpawned() {
            return remainingSpawns.isEmpty() && spawnedCount > 0;
        }

        void onAllKilled() {
            if (runnable != null) runnable.cancel();
            onCleared.run();
        }
    }
}
