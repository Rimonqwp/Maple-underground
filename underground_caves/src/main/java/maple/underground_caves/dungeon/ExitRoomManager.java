package maple.underground_caves.dungeon;

import maple.underground_caves.Test_dungeons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.logging.Level;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exit 房（exit_room_f1/f2/f3）：玩家開門進房後等 2 秒，每 5 秒選一個頭顱→清除→隱身盔甲架戴頭顱向左旋轉並移向中心→
 * 在中心半徑 2 格空中生成怪物（綠血條）；18 隻全滅後在中心半徑 2 生成 2 祝福、傳送門位置放下界傳送門、地圖該格綠✓。
 */
public final class ExitRoomManager implements Listener {

    private static final int DELAY_AFTER_DOOR_TICKS = 40; // 開門後 2 秒再開始
    private static final int SPAWN_INTERVAL_TICKS = 100; // 5 秒
    /** 盔甲架每 tick 移動距離；抵達中心（距離 <= REACH_THRESHOLD）才生怪，避免未到位就生成 */
    private static final double ARMOR_STAND_SPEED = 0.15;
    private static final double REACH_THRESHOLD = 0.8;
    private static final int ARMOR_STAND_MAX_TICKS = 120;
    private static final double CENTER_RADIUS = 2.0;
    private static final double AIR_SPAWN_OFFSET_Y = 2.5; // 空中生成讓怪落地
    private static final int EXIT_ROOM_SKULL_COUNT = 18;
    private static final int EXIT_MOB_TYPES = 9;

    /** 打在 Exit 房生成的怪物上，value=roomKey，死亡時依此判斷是否該房 18 隻全滅 */
    private final NamespacedKey exitRoomMarkerKey;

    private final Test_dungeons plugin;
    private volatile Map<String, ExitMonsterDef> exitMonsterDefs = new HashMap<>();
    private volatile long configLoadedAt = 0L;

    /** key: worldName + ":" + cellX + ":" + cellY */
    private final Map<String, ExitRoomState> exitRooms = new ConcurrentHashMap<>();
    private final Map<String, ExitSpawnTask> activeSpawns = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> roomMonsters = new ConcurrentHashMap<>();
    /** Exit 房血條：roomKey -> (怪物 UUID -> 血條 ArmorStand UUID)，與 DungeonMonsters 一樣由單一任務統一更新 */
    private final Map<String, Map<UUID, UUID>> exitRoomNametags = new ConcurrentHashMap<>();
    /** 怪物 UUID -> 顯示名，供血條任務組文字 */
    private final Map<UUID, String> exitRoomMobDisplayNames = new ConcurrentHashMap<>();
    /** 血條 ArmorStand UUID -> 對其 hideEntity 的玩家 UUID 集合，每個玩家依自己視線單獨計算是否可見 */
    private final Map<UUID, Set<UUID>> exitRoomNametagHiddenFrom = new ConcurrentHashMap<>();
    /** 每房一個血條更新任務，每 tick 對該房所有怪物做 teleport + 更新文字 + 依視線 per-player 顯示/隱藏 */
    private final Map<String, BukkitRunnable> exitRoomNameTasks = new ConcurrentHashMap<>();
    /** exit 房傳送門方塊位置 key = worldName:x:y:z，用於判定玩家進入時傳送整隊到 last_boss_room */
    private final Set<String> exitRoomPortalBlocks = ConcurrentHashMap.newKeySet();
    /** 玩家上次因進入 exit 傳送門被傳送的時間，用於立即傳送時防重複觸發（毫秒） */
    private final Map<UUID, Long> exitPortalTeleportCooldown = new ConcurrentHashMap<>();
    private static final long EXIT_PORTAL_COOLDOWN_MS = 1500L;

    public ExitRoomManager(Test_dungeons plugin) {
        this.plugin = plugin;
        this.exitRoomMarkerKey = new NamespacedKey(plugin, "exit_room_mob");
    }

    private void log(String msg) {
        if (plugin != null && plugin.getLogger() != null) plugin.getLogger().log(Level.INFO, "[ExitRoom] " + msg);
    }

    private void ensureExitMonstersLoaded() {
        long now = System.currentTimeMillis();
        if (!exitMonsterDefs.isEmpty() && now - configLoadedAt < 3000L) return;
        configLoadedAt = now;
        Map<String, ExitMonsterDef> defs = new HashMap<>();
        File file = new File(plugin.getDataFolder(), "monsters.yml");
        if (!file.exists()) {
            try {
                plugin.saveResource("monsters.yml", false);
            } catch (Throwable ignored) {}
        }
        if (file.exists()) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            List<?> list = cfg.getList("exit_room_monsters");
            if (list != null) {
                for (Object o : list) {
                    if (!(o instanceof Map)) continue;
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) o;
                    String tag = str(m.get("tag"));
                    if (tag == null || tag.isBlank()) continue;
                    String entityStr = str(m.get("entity"));
                    EntityType type = parseEntityType(entityStr, EntityType.ZOMBIE);
                    String displayName = str(m.get("displayName"));
                    double hp = num(m.get("hp"), 100);
                    double damage = num(m.get("damage"), 8);
                    double defense = num(m.get("defense"), 5);
                    defs.put(tag.toUpperCase(Locale.ROOT), new ExitMonsterDef(tag, type, displayName != null ? displayName : tag, hp, damage, defense));
                }
            }
        }
        if (defs.size() < EXIT_MOB_TYPES) {
            for (int i = 1; i <= EXIT_MOB_TYPES; i++) {
                String t = "EXIT_MOB_" + i;
                if (!defs.containsKey(t)) {
                    defs.put(t, new ExitMonsterDef(t, EntityType.ZOMBIE, "出口怪 " + i, 100, 8, 5));
                }
            }
        }
        exitMonsterDefs = defs;
    }

    private static String str(Object o) {
        if (o == null) return null;
        return o instanceof String ? (String) o : String.valueOf(o);
    }

    private static double num(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) {
            try { return Double.parseDouble(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private static EntityType parseEntityType(String s, EntityType def) {
        if (s == null || s.isBlank()) return def;
        try {
            return EntityType.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return def;
        }
    }

    /**
     * 註冊 exit_room_f1/f2/f3：模板內頭顱位置、房間中心、傳送門位置會緩存。
     */
    public void registerExitRoom(World world, Location origin, StructureTemplate.Rotation rotation,
                                  int sizeX, int sizeZ, String templateName, StructureTemplate template, int cellX, int cellY) {
        if (world == null || origin == null || template == null) return;
        if (templateName == null || !templateName.toLowerCase(Locale.ROOT).startsWith("exit_room_f")) return;
        StructureTemplate rotated = rotation == StructureTemplate.Rotation.R0 ? template : template.rotate(rotation);
        List<StructureTemplate.BlockPos> skullPositions = new ArrayList<>(rotated.skulls().keySet());
        skullPositions.sort(Comparator.comparingInt(StructureTemplate.BlockPos::y).thenComparingInt(StructureTemplate.BlockPos::x).thenComparingInt(StructureTemplate.BlockPos::z));
        if (skullPositions.size() < EXIT_ROOM_SKULL_COUNT) return;
        List<ExitSlot> slots = new ArrayList<>(EXIT_ROOM_SKULL_COUNT);
        for (int i = 0; i < EXIT_ROOM_SKULL_COUNT; i++) {
            StructureTemplate.BlockPos p = skullPositions.get(i);
            Location worldLoc = origin.clone().add(p.x(), p.y(), p.z());
            int typeIndex = (i % EXIT_MOB_TYPES) + 1;
            String tag = "EXIT_MOB_" + typeIndex;
            slots.add(new ExitSlot(worldLoc, tag));
        }
        // 中心固定為模板內 (15, 8, 15)，盔甲架朝此中心移動、生怪在此半徑 2 格內
        Location center = origin.clone().add(15, 8, 15);
        // 完成房間後在 (15, 3, 15) 半徑 2 格內生成 2 個祝福盔甲架（不再掉落那三樣獎勵）
        Location blessingCenter = origin.clone().add(15, 3, 15);
        List<Location> portalMarkers = new ArrayList<>();
        if (rotated.portalMarkers() != null) {
            for (StructureTemplate.BlockPos p : rotated.portalMarkers()) {
                portalMarkers.add(origin.clone().add(p.x(), p.y(), p.z()));
            }
        }
        String key = roomKey(world.getName(), cellX, cellY);
        exitRooms.put(key, new ExitRoomState(slots, center, blessingCenter, portalMarkers, cellX, cellY, rotation));
    }

    /**
     * 玩家打開門進入 exit 房時呼叫；等 2 秒後開始每 5 秒頭顱→盔甲架→移向中心→空中生怪流程。
     */
    public void onDoorOpened(World world, int roomCellX, int roomCellY) {
        if (world == null) return;
        String key = roomKey(world.getName(), roomCellX, roomCellY);
        ExitRoomState state = exitRooms.get(key);
        if (state == null || state.slots.isEmpty()) return;
        if (activeSpawns.containsKey(key)) return;
        ensureExitMonstersLoaded();
        List<ExitSlot> copy = new ArrayList<>(state.slots);
        roomMonsters.put(key, ConcurrentHashMap.newKeySet());
        ExitSpawnTask task = new ExitSpawnTask(plugin, key, state, copy);
        activeSpawns.put(key, task);
        log("onDoorOpened roomKey=" + key + " slots=18, delay 2s then start");
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!activeSpawns.containsKey(key) || activeSpawns.get(key) != task) return;
                log("start spawn task roomKey=" + key);
                task.start();
            }
        }.runTaskLater(plugin, DELAY_AFTER_DOOR_TICKS);
    }

    void addSpawnedMob(String roomKey, UUID entityUuid) {
        Set<UUID> set = roomMonsters.get(roomKey);
        if (set != null) set.add(entityUuid);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event == null || event.getEntity() == null) return;
        Entity ent = event.getEntity();
        String roomKey = null;
        try {
            if (ent.getPersistentDataContainer().has(exitRoomMarkerKey, PersistentDataType.STRING))
                roomKey = ent.getPersistentDataContainer().get(exitRoomMarkerKey, PersistentDataType.STRING);
        } catch (Throwable t) {
            log("death PDC read error uuid=" + ent.getUniqueId() + " " + t.getMessage());
        }
        if (roomKey == null || roomKey.isBlank()) return;
        UUID id = ent.getUniqueId();
        Set<UUID> set = roomMonsters.get(roomKey);
        int sizeBefore = set != null ? set.size() : -1;
        if (set == null) {
            log("death roomKey=" + roomKey + " uuid=" + id + " set=null skip");
            return;
        }
        boolean removed = set.remove(id);
        int sizeAfter = set.size();
        log("death roomKey=" + roomKey + " uuid=" + id + " removed=" + removed + " sizeBefore=" + sizeBefore + " sizeAfter=" + sizeAfter);
        if (!removed) return;
        removeExitRoomNametag(roomKey, id);
        ExitSpawnTask task = activeSpawns.get(roomKey);
        boolean allSpawned = task != null && task.isAllSpawned();
        boolean empty = set.isEmpty();
        log("death roomKey=" + roomKey + " task=" + (task != null) + " isAllSpawned=" + allSpawned + " isEmpty=" + empty + " -> onAllKilled=" + (allSpawned && empty));
        if (task != null && allSpawned && empty)
            task.onAllKilled();
    }

    /** Exit 房生成的末影人不瞬移：帶 exit_room PDC 的實體觸發瞬移時直接取消 */
    @EventHandler(priority = org.bukkit.event.EventPriority.LOW, ignoreCancelled = false)
    public void onEntityTeleport(EntityTeleportEvent event) {
        if (event == null || event.getEntity() == null) return;
        Entity ent = event.getEntity();
        try {
            if (ent.getPersistentDataContainer().has(exitRoomMarkerKey, PersistentDataType.STRING))
                event.setCancelled(true);
        } catch (Throwable ignored) {}
    }

    private void removeExitRoomNametag(String roomKey, UUID mobUuid) {
        Map<UUID, UUID> map = exitRoomNametags.get(roomKey);
        if (map == null) return;
        UUID nametagId = map.remove(mobUuid);
        exitRoomMobDisplayNames.remove(mobUuid);
        if (nametagId != null) exitRoomNametagHiddenFrom.remove(nametagId);
        if (nametagId == null) return;
        for (World w : plugin.getServer().getWorlds()) {
            Entity nt = w.getEntity(nametagId);
            if (nt != null) { nt.remove(); return; }
        }
    }

    /** 與 DungeonMonsters 一樣：每房一個任務，每 tick 對該房所有怪物做 teleport + 更新血條文字（每 tick 更新才不會像在追著怪跑） */
    private void ensureExitRoomNameTask(String roomKey) {
        if (exitRoomNameTasks.containsKey(roomKey)) return;
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                Set<UUID> mobs = roomMonsters.get(roomKey);
                Map<UUID, UUID> nametags = exitRoomNametags.get(roomKey);
                if (mobs == null || nametags == null) return;
                World world = null;
                for (UUID mobId : new ArrayList<>(mobs)) {
                    if (world == null) {
                        for (World w : plugin.getServer().getWorlds()) {
                            if (w.getEntity(mobId) != null) { world = w; break; }
                        }
                        if (world == null) continue;
                    }
                    Entity me = world.getEntity(mobId);
                    if (!(me instanceof LivingEntity le) || !le.isValid() || le.isDead()) {
                        removeExitRoomNametag(roomKey, mobId);
                        continue;
                    }
                    UUID ntId = nametags.get(mobId);
                    if (ntId == null) continue;
                    Entity ne = world.getEntity(ntId);
                    if (!(ne instanceof ArmorStand nametag)) continue;
                    nametag.teleport(le.getLocation().add(0, 1, 0));
                    String dispName = exitRoomMobDisplayNames.getOrDefault(mobId, "");
                    double hp = le.getHealth();
                    double max = le.getAttribute(Attribute.MAX_HEALTH) != null ? le.getAttribute(Attribute.MAX_HEALTH).getValue() : le.getMaxHealth();
                    nametag.customName(Component.text(dispName + " ❤ " + (int) hp + "/" + (int) max, NamedTextColor.GREEN));
                    Set<UUID> hiddenFrom = exitRoomNametagHiddenFrom.computeIfAbsent(ntId, k -> ConcurrentHashMap.newKeySet());
                    for (Player p : world.getPlayers()) {
                        if (p == null || !p.isOnline() || p.getWorld() != world) continue;
                        UUID pid = p.getUniqueId();
                        boolean shouldSee;
                        try {
                            shouldSee = p.hasLineOfSight(le);
                        } catch (Throwable ignored) {
                            shouldSee = false;
                        }
                        boolean currentlyHidden = hiddenFrom.contains(pid);
                        if (shouldSee && currentlyHidden) {
                            try {
                                p.showEntity(plugin, nametag);
                                hiddenFrom.remove(pid);
                            } catch (Throwable ignored) {}
                        } else if (!shouldSee && !currentlyHidden) {
                            try {
                                p.hideEntity(plugin, nametag);
                                hiddenFrom.add(pid);
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
        };
        task.runTaskTimer(plugin, 1L, 1L);
        exitRoomNameTasks.put(roomKey, task);
    }

    private static String roomKey(String worldName, int cellX, int cellY) {
        return worldName + ":" + cellX + ":" + cellY;
    }

    /** 依大門方向在 portalMarkers 區域直接生成地獄傳送門方塊（不生成黑曜石框架），並記錄位置供進入時傳送整隊。 */
    private void placeNetherPortalBlocks(World world, List<Location> portalMarkers, StructureTemplate.Rotation rotation) {
        if (world == null || portalMarkers == null || portalMarkers.isEmpty()) return;
        org.bukkit.Axis axis = (rotation == StructureTemplate.Rotation.R90 || rotation == StructureTemplate.Rotation.R270) ? org.bukkit.Axis.X : org.bukkit.Axis.Z;
        for (Location loc : portalMarkers) {
            int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
            Block block = world.getBlockAt(bx, by, bz);
            block.setType(Material.NETHER_PORTAL, false);
            BlockData data = block.getBlockData();
            if (data instanceof Orientable o) {
                o.setAxis(axis);
                block.setBlockData(o, false);
            }
            exitRoomPortalBlocks.add(world.getName() + ":" + bx + ":" + by + ":" + bz);
        }
    }

    /** 將觸發者所在隊伍所有人傳送到 last_boss_room local (29,17,10)。傳送成功回傳 true。 */
    private boolean teleportPartyToLastBossRoom(Player trigger, World world) {
        if (trigger == null || world == null) return false;
        Test_dungeons.DungeonSnapshot snapshot = plugin.getDungeonSnapshot(world.getName());
        if (snapshot == null || !world.getName().equals(snapshot.worldName())) return false;
        maple.underground_caves.dungeon.BossRoomManager bossMgr = plugin.getBossRoomManager();
        if (bossMgr == null) return false;
        Location dest = bossMgr.getLastBossTeleportTarget(snapshot.worldName(), snapshot.floor());
        if (dest == null) return false;
        java.util.List<UUID> partyMemberIds = plugin.getPartyManager() != null
                ? plugin.getPartyManager().getPartyMembers(trigger)
                : java.util.List.of(trigger.getUniqueId());
        for (UUID memberId : partyMemberIds) {
            Player p = plugin.getServer().getPlayer(memberId);
            if (p != null && p.isOnline() && p.getWorld().getName().equals(snapshot.worldName()))
                p.teleport(dest);
        }
        maple.underground_caves.dungeon.DungeonMonsters dm = plugin.getDungeonMonsters();
        if (dm != null) dm.scheduleFirstBossSpawnAfterTeleport(world, snapshot.floor());
        return true;
    }

    /** 玩家移動時若踏入 exit 房傳送門方塊，立即傳送整隊（不需等原版地獄門 4 秒）。 */
    @EventHandler(priority = org.bukkit.event.EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerMoveInExitPortal(PlayerMoveEvent event) {
        if (event == null || event.getPlayer() == null) return;
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) return;
        if (exitRoomPortalBlocks.isEmpty()) return;
        int bx = to.getBlockX(), by = to.getBlockY(), bz = to.getBlockZ();
        String keyFeet = to.getWorld().getName() + ":" + bx + ":" + by + ":" + bz;
        String keyHead = to.getWorld().getName() + ":" + bx + ":" + (by + 1) + ":" + bz;
        if (!exitRoomPortalBlocks.contains(keyFeet) && !exitRoomPortalBlocks.contains(keyHead)) return;
        long now = System.currentTimeMillis();
        if (now - exitPortalTeleportCooldown.getOrDefault(event.getPlayer().getUniqueId(), 0L) < EXIT_PORTAL_COOLDOWN_MS) return;
        if (teleportPartyToLastBossRoom(event.getPlayer(), to.getWorld())) {
            exitPortalTeleportCooldown.put(event.getPlayer().getUniqueId(), now);
        }
    }

    /** 有一名玩家進入 exit 房傳送門時（原版地獄門會延遲約 4 秒才觸發），取消原版傳送並將整隊傳到 last_boss_room。 */
    @EventHandler(priority = org.bukkit.event.EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event == null || event.getPlayer() == null) return;
        org.bukkit.Location from = event.getFrom();
        if (from == null || from.getWorld() == null) return;
        if (from.getWorld().getBlockAt(from.getBlockX(), from.getBlockY(), from.getBlockZ()).getType() != Material.NETHER_PORTAL) return;
        String key = from.getWorld().getName() + ":" + from.getBlockX() + ":" + from.getBlockY() + ":" + from.getBlockZ();
        if (!exitRoomPortalBlocks.contains(key)) return;
        if (teleportPartyToLastBossRoom(event.getPlayer(), from.getWorld())) {
            event.setCancelled(true);
        }
    }

    /** 執行 exit 房通關效果：2 祝福（停在那直到玩家撿起或 10 秒後自動撿起）、傳送門、地圖標綠、通關訊息（不給掉落獎勵）。 */
    private void doExitRoomCompletionEffects(World world, ExitRoomState state) {
        if (world == null || state == null) return;
        Random r = new Random();
        maple.underground_caves.dungeon.RoomRewardManager rewardMgr = plugin.getRoomRewardManager();
        Location blessCenter = state.blessingCenter != null ? state.blessingCenter : state.center;
        for (int i = 0; i < 2; i++) {
            double dx = (r.nextDouble() * 2 - 1) * CENTER_RADIUS;
            double dz = (r.nextDouble() * 2 - 1) * CENTER_RADIUS;
            double x = blessCenter.getX() + dx;
            double z = blessCenter.getZ() + dz;
            Location spawnLoc = new Location(world, x, blessCenter.getY(), z);
            if (rewardMgr != null) rewardMgr.spawnSecretBlessing(world, spawnLoc);
        }
        if (state.portalMarkers() != null && !state.portalMarkers().isEmpty()) placeNetherPortalBlocks(world, state.portalMarkers(), state.rotation());
        DungeonMonsters monsters = plugin.getDungeonMonsters();
        if (monsters != null) {
            monsters.markExitRoomCleared(world.getName(), state.cellX(), state.cellY());
            monsters.notifyExitRoomCleared(world, state.center, state.cellX(), state.cellY(), false);
        }
    }

    /** 清理該房生成任務與血條（用於通關時或測試強制通關）。 */
    private void cleanupExitRoomForCompletion(String roomKey) {
        ExitSpawnTask task = activeSpawns.remove(roomKey);
        if (task != null) task.cancel();
        BukkitRunnable nameTask = exitRoomNameTasks.remove(roomKey);
        if (nameTask != null) nameTask.cancel();
        Map<UUID, UUID> nametags = exitRoomNametags.remove(roomKey);
        if (nametags != null) {
            for (UUID mobId : nametags.keySet()) exitRoomMobDisplayNames.remove(mobId);
            for (UUID ntId : nametags.values()) exitRoomNametagHiddenFrom.remove(ntId);
            for (World w : plugin.getServer().getWorlds()) {
                for (UUID ntId : nametags.values()) {
                    Entity e = w.getEntity(ntId);
                    if (e != null) e.remove();
                }
            }
        }
    }

    /**
     * 測試用：強制將指定 exit 房設為已通關，觸發祝福、傳送門區域點火、地圖標綠、通關訊息。
     * @return 若該房已註冊為 exit 房則 true，否則 false
     */
    public boolean forceCompleteExitRoom(World world, int cellX, int cellY) {
        if (world == null) return false;
        String key = roomKey(world.getName(), cellX, cellY);
        ExitRoomState state = exitRooms.get(key);
        if (state == null) return false;
        cleanupExitRoomForCompletion(key);
        doExitRoomCompletionEffects(world, state);
        roomMonsters.remove(key);
        log("forceCompleteExitRoom roomKey=" + key);
        return true;
    }

    private record ExitMonsterDef(String tag, EntityType entityType, String displayName, double hp, double damage, double defense) {}
    private record ExitSlot(Location location, String tag) {}
    private record ExitRoomState(List<ExitSlot> slots, Location center, Location blessingCenter, List<Location> portalMarkers, int cellX, int cellY, StructureTemplate.Rotation rotation) {}

    private final class ExitSpawnTask {
        private final Test_dungeons plugin;
        private final String roomKey;
        private final ExitRoomState state;
        private final List<ExitSlot> remainingSlots;
        private BukkitRunnable runnable;
        private volatile int spawnedCount;

        ExitSpawnTask(Test_dungeons plugin, String roomKey, ExitRoomState state, List<ExitSlot> remainingSlots) {
            this.plugin = plugin;
            this.roomKey = roomKey;
            this.state = state;
            this.remainingSlots = remainingSlots;
        }

        void start() {
            runnable = new BukkitRunnable() {
                int tick = 0;

                @Override
                public void run() {
                    tick++;
                    if (remainingSlots.isEmpty()) {
                        cancel();
                        return;
                    }
                    if (tick % SPAWN_INTERVAL_TICKS != 0) return;
                    ExitSlot slot = remainingSlots.remove(0);
                    spawnOne(slot);
                }
            };
            runnable.runTaskTimer(plugin, 1L, 1L);
        }

        boolean isAllSpawned() {
            return remainingSlots.isEmpty() && spawnedCount >= EXIT_ROOM_SKULL_COUNT;
        }

        void cancel() {
            if (runnable != null) {
                runnable.cancel();
                runnable = null;
            }
        }

        void onAllKilled() {
            log("onAllKilled roomKey=" + roomKey + " cell=" + state.cellX + "," + state.cellY);
            ExitRoomManager.this.cleanupExitRoomForCompletion(roomKey);
            World world = state.center != null ? state.center.getWorld() : null;
            if (world != null) ExitRoomManager.this.doExitRoomCompletionEffects(world, state);
            roomMonsters.remove(roomKey);
        }

        private void spawnOne(ExitSlot slot) {
            Location loc = slot.location();
            World world = loc.getWorld();
            if (world == null) return;
            Block skullBlock = loc.getBlock();
            final ItemStack helmetForStand;
            if (skullBlock.getState() instanceof Skull skull) {
                ItemStack skullItem = new ItemStack(Material.PLAYER_HEAD, 1);
                SkullMeta meta = (SkullMeta) skullItem.getItemMeta();
                if (meta != null) {
                    try {
                        if (skull.getPlayerProfile() != null) meta.setPlayerProfile(skull.getPlayerProfile());
                        else if (skull.getOwningPlayer() != null) meta.setOwningPlayer(skull.getOwningPlayer());
                    } catch (Throwable ignored) {}
                    skullItem.setItemMeta(meta);
                }
                helmetForStand = skullItem;
            } else {
                helmetForStand = null;
            }
            skullBlock.setType(Material.AIR, false);

            Location standLoc = loc.clone().add(0.5, 0, 0.5);
            ArmorStand as = world.spawn(standLoc, ArmorStand.class, stand -> {
                stand.setVisible(false);
                stand.setGravity(false);
                stand.setMarker(true);
                stand.setSmall(true);
                if (helmetForStand != null && stand.getEquipment() != null) stand.getEquipment().setHelmet(helmetForStand);
            });

            Location centerPoint = state.center.clone().add(0.5, 0, 0.5);
            Vector toCenter = centerPoint.toVector().subtract(standLoc.toVector());
            double dist = toCenter.length();
            if (dist > 1e-3) toCenter.normalize().multiply(ARMOR_STAND_SPEED);
            float yawPerTick = -8f; // 向左旋轉
            new BukkitRunnable() {
                int t = 0;

                @Override
                public void run() {
                    if (!as.isValid() || as.isDead()) {
                        cancel();
                        return;
                    }
                    as.teleport(as.getLocation().add(toCenter));
                    as.setHeadPose(as.getHeadPose().add(0, Math.toRadians(yawPerTick), 0));
                    t++;
                    double d = as.getLocation().distance(centerPoint);
                    if (d <= REACH_THRESHOLD || t >= ARMOR_STAND_MAX_TICKS) {
                        as.remove();
                        spawnMonsterAtCenter(slot);
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
        }

        private void spawnMonsterAtCenter(ExitSlot slot) {
            World world = state.center.getWorld();
            if (world == null) return;
            ExitMonsterDef def = exitMonsterDefs.get(slot.tag().toUpperCase(Locale.ROOT));
            if (def == null) def = exitMonsterDefs.get("EXIT_MOB_1");
            if (def == null) return;
            Random rand = new Random();
            Location mobLoc = state.center.clone().add(
                    (rand.nextDouble() * 2 - 1) * CENTER_RADIUS,
                    AIR_SPAWN_OFFSET_Y,
                    (rand.nextDouble() * 2 - 1) * CENTER_RADIUS
            );
            EntityType type = def.entityType();
            if (!type.isAlive()) type = EntityType.ZOMBIE;
            LivingEntity mob;
            try {
                Entity e = world.spawnEntity(mobLoc, type);
                if (!(e instanceof LivingEntity le)) return;
                mob = le;
            } catch (Throwable t) {
                return;
            }
            if (mob instanceof Zombie z) z.setAdult();
            mob.setRemoveWhenFarAway(false);
            applyStats(mob, def.hp(), def.damage(), def.defense());
            String name = def.displayName() != null ? def.displayName() : def.tag();
            // 與一般房一致：怪物本體不顯示名稱，只由血條 ArmorStand 顯示（單一任務統一更新）
            mob.customName(Component.empty());
            mob.setCustomNameVisible(false);
            Component greenBar = Component.text(name + " ❤ " + (int) def.hp() + "/" + (int) def.hp(), NamedTextColor.GREEN);
            ArmorStand bar = world.spawn(mob.getLocation().add(0, 1, 0), ArmorStand.class, stand -> {
                stand.setVisible(false);
                stand.setSmall(true);
                stand.setGravity(false);
                stand.setBasePlate(false);
                stand.setArms(false);
                stand.setMarker(false);
                stand.setInvulnerable(true);
                stand.setCollidable(false);
                stand.setSilent(true);
                stand.setRemoveWhenFarAway(false);
                stand.customName(greenBar);
                stand.setCustomNameVisible(true);
            });
            mob.setGravity(true);
            mob.getPersistentDataContainer().set(exitRoomMarkerKey, PersistentDataType.STRING, roomKey);
            exitRoomNametags.computeIfAbsent(roomKey, k -> new ConcurrentHashMap<>()).put(mob.getUniqueId(), bar.getUniqueId());
            exitRoomMobDisplayNames.put(mob.getUniqueId(), name);
            ensureExitRoomNameTask(roomKey);
            addSpawnedMob(roomKey, mob.getUniqueId());
            spawnedCount++;
            Set<UUID> set = roomMonsters.get(roomKey);
            log("spawn roomKey=" + roomKey + " uuid=" + mob.getUniqueId() + " spawnedCount=" + spawnedCount + " setSize=" + (set != null ? set.size() : 0));
        }

        private void applyStats(LivingEntity ent, double hp, double dmg, double armor) {
            if (ent == null) return;
            if (hp > 0) {
                if (ent.getAttribute(Attribute.MAX_HEALTH) != null) {
                    try { ent.getAttribute(Attribute.MAX_HEALTH).setBaseValue(hp); } catch (Throwable ignored) {}
                }
                try {
                    double max = ent.getAttribute(Attribute.MAX_HEALTH) != null ? ent.getAttribute(Attribute.MAX_HEALTH).getValue() : ent.getMaxHealth();
                    if (max <= 0) max = ent.getMaxHealth();
                    ent.setHealth(Math.min(hp, max));
                } catch (Throwable ignored) {}
            }
            if (ent.getAttribute(Attribute.ATTACK_DAMAGE) != null && dmg > 0) {
                ent.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(dmg);
            }
            if (ent.getAttribute(Attribute.ARMOR) != null && armor >= 0) {
                ent.getAttribute(Attribute.ARMOR).setBaseValue(armor);
            }
        }
    }
}
