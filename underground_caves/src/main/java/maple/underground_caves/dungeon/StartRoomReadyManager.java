package maple.underground_caves.dungeon;

import maple.underground_caves.Test_dungeons;
import maple.underground_caves.party.PartyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.swofty.dungeons.DungeonRoomType;
import net.swofty.dungeons.SkyBlockDungeon;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 準備階段管理：玩家傳送至地牢後，2 秒緩衝取得自動準備狀態，五位全準備後 5 秒倒數開門，門開後計時器啟動。
 * 開門動畫與秘密紅石系統相同：煤炭磚變為下墜方塊落下後消失。
 */
public final class StartRoomReadyManager implements Listener {
    private static final int BUFFER_SECONDS = 2;
    private static final int COUNTDOWN_SECONDS = 5;

    private final Test_dungeons plugin;
    private final PartyManager partyManager;
    private final DungeonScoreboard scoreboard;

    /** worldName -> 該世界是否已開門（避免重複開門） */
    private final java.util.Map<String, Boolean> openedByWorld = new java.util.concurrent.ConcurrentHashMap<>();

    private final NamespacedKey fallingDoorKey;

    public StartRoomReadyManager(Test_dungeons plugin, PartyManager partyManager, DungeonScoreboard scoreboard) {
        this.plugin = plugin;
        this.partyManager = partyManager;
        this.scoreboard = scoreboard;
        this.fallingDoorKey = new NamespacedKey(plugin, "start_room_falling_door");
    }

    /**
     * 玩家傳送完成後呼叫，開始準備流程。
     * @param worldName 地牢世界名稱
     * @param partyMemberIds 隊伍成員 UUID
     * @param dungeonOrigin 地牢起點 (用於清除入口門煤炭)
     * @param dungeon 地牢資料
     * @param width 寬
     * @param height 高
     * @param roomScale 房間尺度
     * @param anchorDoorY 門 Y 錨點
     */
    public void startReadyPhase(String worldName, List<UUID> partyMemberIds, Location dungeonOrigin,
                                SkyBlockDungeon dungeon, int width, int height, int roomScale, int anchorDoorY) {
        if (worldName == null || partyMemberIds == null || partyMemberIds.isEmpty()) return;
        if (openedByWorld.getOrDefault(worldName, false)) return;

        // 新一局：清本局「手動準備/手動取消」，保留 MySQL 自動準備開關（不在開門時清除 DB）
        if (partyManager != null) {
            partyManager.resetReadyStateForNewDungeonRun(partyMemberIds);
        }

        // 2 秒緩衝：讓自動準備玩家的狀態從 DB 載入
        new BukkitRunnable() {
            @Override
            public void run() {
                if (openedByWorld.getOrDefault(worldName, false)) {
                    cancel();
                    return;
                }
                broadcastToParty(partyMemberIds, Component.text("準備狀態已更新。請在職業 NPC 處點擊準備，五位全準備後將倒數 5 秒開門。", NamedTextColor.GRAY));
                // 進入準備檢查階段
                runReadyCheckLoop(worldName, partyMemberIds, dungeonOrigin, dungeon, width, height, roomScale, anchorDoorY);
            }
        }.runTaskLater(plugin, BUFFER_SECONDS * 20L);
    }

    private void runReadyCheckLoop(String worldName, List<UUID> partyMemberIds, Location dungeonOrigin,
                                   SkyBlockDungeon dungeon, int width, int height, int roomScale, int anchorDoorY) {
        if (!allReady(partyMemberIds)) {
            // 未全準備，每秒再檢查
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (openedByWorld.getOrDefault(worldName, false)) {
                        cancel();
                        return;
                    }
                    runReadyCheckLoop(worldName, partyMemberIds, dungeonOrigin, dungeon, width, height, roomScale, anchorDoorY);
                }
            }.runTaskLater(plugin, 20L);
            return;
        }

        // 五位全準備，開始 5 秒倒數
        runCountdown(worldName, partyMemberIds, dungeonOrigin, dungeon, width, height, roomScale, anchorDoorY, COUNTDOWN_SECONDS);
    }

    private void runCountdown(String worldName, List<UUID> partyMemberIds, Location dungeonOrigin,
                             SkyBlockDungeon dungeon, int width, int height, int roomScale, int anchorDoorY, int secondsLeft) {
        if (secondsLeft <= 0) {
            // 倒數結束，開門並啟動計時器
            openEntranceDoorAndStartTimer(worldName, partyMemberIds, dungeonOrigin, dungeon, width, height, roomScale, anchorDoorY);
            return;
        }

        if (!allReady(partyMemberIds)) {
            // 有人取消準備，取消倒數
            broadcastToParty(partyMemberIds, Component.text("有人取消了準備，倒數已取消。", NamedTextColor.YELLOW));
            runReadyCheckLoop(worldName, partyMemberIds, dungeonOrigin, dungeon, width, height, roomScale, anchorDoorY);
            return;
        }

        broadcastToParty(partyMemberIds, Component.text("門將在 " + secondsLeft + " 秒後開啟...", NamedTextColor.GREEN));

        new BukkitRunnable() {
            @Override
            public void run() {
                if (openedByWorld.getOrDefault(worldName, false)) {
                    cancel();
                    return;
                }
                runCountdown(worldName, partyMemberIds, dungeonOrigin, dungeon, width, height, roomScale, anchorDoorY, secondsLeft - 1);
            }
        }.runTaskLater(plugin, 20L);
    }

    private boolean allReady(List<UUID> memberIds) {
        if (partyManager == null || memberIds == null) return false;
        int required = Math.min(5, Math.max(1, memberIds.size()));
        int ready = 0;
        for (UUID id : memberIds) {
            if (partyManager.isEffectiveReady(id)) ready++;
        }
        return ready >= required;
    }

    private void broadcastToParty(List<UUID> memberIds, Component message) {
        if (memberIds == null) return;
        for (UUID id : memberIds) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) p.sendMessage(message);
        }
    }

    private void openEntranceDoorAndStartTimer(String worldName, List<UUID> partyMemberIds, Location startLoc,
                                               SkyBlockDungeon dungeon, int width, int height, int roomScale, int anchorDoorY) {
        if (openedByWorld.putIfAbsent(worldName, true) != null) return;

        // 自動準備為玩家偏好，存於 MySQL long_term_ready；開門後保留，下一场仍從 DB 讀取

        World world = startLoc != null ? startLoc.getWorld() : (worldName != null ? Bukkit.getWorld(worldName) : null);
        if (world != null) {
            animateEntranceDoorOpen(world, dungeon, startLoc, roomScale, anchorDoorY, width, height, partyMemberIds);
        }

        if (scoreboard != null) {
            scoreboard.beginTimer(worldName);
        }
        if (plugin != null) {
            plugin.markDungeonRunStarted(worldName);
        }
    }

    /** 開門動畫：與秘密紅石系統相同，煤炭磚變為下墜方塊落下後消失 */
    private void animateEntranceDoorOpen(World world, SkyBlockDungeon dungeon, Location startLoc,
                                         int roomScale, int anchorDoorY, int buildWidth, int buildHeight,
                                         List<UUID> partyMemberIds) {
        if (world == null || dungeon == null || startLoc == null) return;
        int startX = startLoc.getBlockX();
        int startY = startLoc.getBlockY();
        int startZ = startLoc.getBlockZ();
        int baseY = startY + (anchorDoorY >= 0 ? anchorDoorY : 0);

        List<Location> coalLocations = new ArrayList<>();

        // 收集入口房間內的煤炭磚
        for (int cy = 0; cy < buildHeight; cy++) {
            for (int cx = 0; cx < buildWidth; cx++) {
                var room = dungeon.getRoom(cx, cy);
                if (room == null || room.getRoomType() != DungeonRoomType.ENTRANCE) continue;

                int baseX = startX + cx * roomScale;
                int baseZ = startZ + cy * roomScale;
                for (int dx = -1; dx <= roomScale; dx++) {
                    for (int dz = -1; dz <= roomScale; dz++) {
                        for (int dy = -4; dy <= 8; dy++) {
                            Block b = world.getBlockAt(baseX + dx, baseY + dy, baseZ + dz);
                            if (b.getType() == Material.COAL_BLOCK) {
                                coalLocations.add(b.getLocation());
                            }
                        }
                    }
                }
            }
        }

        // 收集與入口相連的隧道門煤炭
        for (var d : dungeon.getAllDoors()) {
            int x1 = d.x1(), y1 = d.y1(), x2 = d.x2(), y2 = d.y2();
            if (Math.abs(x1 - x2) + Math.abs(y1 - y2) != 1) continue;
            var r1 = dungeon.getRoom(x1, y1);
            var r2 = dungeon.getRoom(x2, y2);
            if (r1 == null || r2 == null) continue;
            boolean entranceInvolved = r1.getRoomType() == DungeonRoomType.ENTRANCE || r2.getRoomType() == DungeonRoomType.ENTRANCE;
            if (!entranceInvolved) continue;

            if (x1 != x2) {
                int minX = Math.min(x1, x2);
                int gapX = startX + (minX + 1) * roomScale - 1;
                int centerZ = startZ + y1 * roomScale + 15;
                // 門寬 3x3，掃描 gapX±1（3 格厚度）與 center±2（5 格寬）確保涵蓋完整 3x3 門
                for (int px = gapX - 1; px <= gapX + 1; px++) {
                    for (int z = centerZ - 2; z <= centerZ + 2; z++) {
                        for (int y = baseY - 1; y <= baseY + 4; y++) {
                            Block b = world.getBlockAt(px, y, z);
                            if (b.getType() == Material.COAL_BLOCK) {
                                coalLocations.add(b.getLocation());
                            }
                        }
                    }
                }
            } else {
                int minY = Math.min(y1, y2);
                int gapZ = startZ + (minY + 1) * roomScale - 1;
                int centerX = startX + x1 * roomScale + 15;
                // 門寬 3x3，掃描 gapZ±1（3 格厚度）與 center±2（5 格寬）確保涵蓋完整 3x3 門
                for (int pz = gapZ - 1; pz <= gapZ + 1; pz++) {
                    for (int x = centerX - 2; x <= centerX + 2; x++) {
                        for (int y = baseY - 1; y <= baseY + 4; y++) {
                            Block b = world.getBlockAt(x, y, pz);
                            if (b.getType() == Material.COAL_BLOCK) {
                                coalLocations.add(b.getLocation());
                            }
                        }
                    }
                }
            }
        }

        if (coalLocations.isEmpty()) {
            if (scoreboard != null) scoreboard.beginTimer(world.getName());
            return;
        }

        int spawned = 0;
        for (Location loc : coalLocations) {
            triggerBlockFall(world, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            spawned++;
        }

        if (spawned > 0) {
            // 所有方塊下墜動畫約 100 tick (5 秒)，結束後啟動計時器
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (scoreboard != null) scoreboard.beginTimer(world.getName());
                }
            }.runTaskLater(plugin, 105L);
        } else {
            if (scoreboard != null) scoreboard.beginTimer(world.getName());
        }
    }

    /** 與 DungeonSecrets.triggerDoorFall 相同：方塊變為 FallingBlock 下墜後消失 */
    private void triggerBlockFall(World world, int x, int y, int z) {
        if (world == null) return;
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() != Material.COAL_BLOCK) return;

        BlockData data = block.getBlockData();
        Location spawnAt = new Location(world, x + 0.5, y, z + 0.5);
        block.setType(Material.AIR, false);

        FallingBlock falling;
        try {
            falling = world.spawnFallingBlock(spawnAt, data);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        falling.getPersistentDataContainer().set(fallingDoorKey, PersistentDataType.BYTE, (byte) 1);
        falling.setDropItem(false);
        falling.setHurtEntities(false);
        falling.setGravity(false);

        double minY = y - 10.0;
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!falling.isValid()) {
                    cancel();
                    return;
                }
                ticks++;
                falling.setVelocity(new Vector(0.0, -0.06, 0.0));
                if (ticks >= 100 || falling.getLocation().getY() <= minY) {
                    falling.remove();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFallingDoorPlace(EntityChangeBlockEvent event) {
        if (event == null) return;
        Entity e = event.getEntity();
        if (!(e instanceof FallingBlock falling)) return;
        Byte b = falling.getPersistentDataContainer().get(fallingDoorKey, PersistentDataType.BYTE);
        if (b == null || b != (byte) 1) return;
        event.setCancelled(true);
        falling.remove();
    }

    /** 世界卸載時清除狀態 */
    public void onWorldUnload(String worldName) {
        if (worldName != null) openedByWorld.remove(worldName);
    }
}
