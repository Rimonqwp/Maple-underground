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
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("ALL")
public final class PuzzleRoom08BlazeShoot {
    private static final int TOTAL = 10;
    private final Test_dungeons plugin;
    private final Map<String, RoomState> roomsByKey = new HashMap<>();
    private static final String CONFIG_FILE_NAME = "puzzle_room_08.yml";
    private static final String TAG_ROOT = "pr08";
    private static final String TAG_ROOM_PREFIX = "pr08_room:";
    private static final String TAG_IDX_PREFIX = "pr08_idx:";

    public PuzzleRoom08BlazeShoot(Test_dungeons plugin) {
        this.plugin = plugin;
    }

    private void info(String msg) {
        if (plugin == null || plugin.getLogger() == null || msg == null) return;
        plugin.getLogger().info("puzzle_room_08 " + msg);
    }

    private void warn(String msg) {
        if (plugin == null || plugin.getLogger() == null || msg == null) return;
        plugin.getLogger().warning("puzzle_room_08 " + msg);
    }

    private void roomInfo(RoomState room, String msg) {
        if (room == null) {
            info("room=null " + msg);
            return;
        }
        info(room.roomKey + " " + msg);
    }

    public void onDisable() {
        info("onDisable rooms=" + roomsByKey.size());
        for (RoomState room : List.copyOf(roomsByKey.values())) {
            if (room == null) continue;
            cleanupRoom(room);
        }
        roomsByKey.clear();
    }

    public void registerPuzzleRoom08(World world, Location roomOrigin, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
        if (world == null || roomOrigin == null || rotation == null) return;
        Location base = roomOrigin.getBlock().getLocation();
        String roomKey = world.getName() + ":" + base.getBlockX() + ":" + base.getBlockY() + ":" + base.getBlockZ();

        RoomState old = roomsByKey.remove(roomKey);
        if (old != null) {
            roomInfo(old, "re-register cleanup old room");
            cleanupRoom(old);
        }

        RoomState room = new RoomState(roomKey, world.getName(), rotation, base.getBlockX(), base.getBlockY(), base.getBlockZ(), sizeX, sizeZ);
        int[] bounds = computeXZBounds(base, rotation, sizeX, sizeZ);
        room.minX = bounds[0];
        room.maxX = bounds[1];
        room.minZ = bounds[2];
        room.maxZ = bounds[3];
        roomInfo(room, "register origin=" + base.getBlockX() + "," + base.getBlockY() + "," + base.getBlockZ()
                + " rotation=" + rotation.name()
                + " size=" + sizeX + "x" + sizeZ
                + " boundsX=" + room.minX + ".." + room.maxX
                + " boundsZ=" + room.minZ + ".." + room.maxZ);

        room.config = loadConfigForWorld(world.getName());
        if (room.config == null) {
            roomInfo(room, "register skipped: config missing for world=" + world.getName());
            roomsByKey.put(roomKey, room);
            return;
        }

        room.resolved = resolveConfig(room, room.config);
        if (room.resolved == null) {
            roomInfo(room, "register skipped: config resolve failed");
            roomsByKey.put(roomKey, room);
            return;
        }

        if (room.resolved.centerX < room.minX || room.resolved.centerX > room.maxX || room.resolved.centerZ < room.minZ || room.resolved.centerZ > room.maxZ) {
            roomInfo(room, "register skipped: circle center out of room"
                    + " center=" + room.resolved.centerX + "," + room.resolved.centerZ
                    + " boundsX=" + room.minX + ".." + room.maxX
                    + " boundsZ=" + room.minZ + ".." + room.maxZ);
            roomsByKey.put(roomKey, room);
            return;
        }

        roomsByKey.put(roomKey, room);
        roomInfo(room, "register ok: waiting for players inside");
        startPresenceScan(room);
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
                if (room.spawned || room.completed || room.failed) {
                    cancel();
                    room.presenceTask = null;
                    return;
                }
                World world = Bukkit.getWorld(room.worldName);
                if (world == null) return;
                for (Player p : world.getPlayers()) {
                    if (p == null) continue;
                    if (!insideRoom(room, p.getLocation())) continue;
                    spawnForRoom(room);
                    cancel();
                    room.presenceTask = null;
                    break;
                }
            }
        };
        room.presenceTask.runTaskTimer(plugin, 5L, 10L);
    }

    private boolean insideRoom(RoomState room, Location loc) {
        if (room == null || loc == null) return false;
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(room.worldName)) return false;
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        if (x < room.minX || x > room.maxX) return false;
        if (z < room.minZ || z > room.maxZ) return false;
        int minY = room.baseY - 5;
        int maxY = room.baseY + 80;
        return y >= minY && y <= maxY;
    }

    public boolean saveConfig(Location roomOrigin, StructureTemplate.Rotation rotation, int sizeXRot, int sizeZRot,
                       Location circlePos1, Location circlePos2, Location progressPos1, Location progressPos2) {
        if (plugin == null) return false;
        if (roomOrigin == null || roomOrigin.getWorld() == null) return false;
        if (rotation == null) return false;
        if (sizeXRot <= 0 || sizeZRot <= 0) return false;
        if (circlePos1 == null || circlePos2 == null || progressPos1 == null || progressPos2 == null) return false;
        if (circlePos1.getWorld() == null || circlePos2.getWorld() == null) return false;
        if (!circlePos1.getWorld().equals(circlePos2.getWorld())) return false;
        if (!circlePos1.getWorld().equals(roomOrigin.getWorld())) return false;
        if (progressPos1.getWorld() == null || !progressPos1.getWorld().equals(circlePos1.getWorld())) return false;
        if (progressPos2.getWorld() == null || !progressPos2.getWorld().equals(circlePos1.getWorld())) return false;

        Location base = roomOrigin.getBlock().getLocation();
        int ox = base.getBlockX();
        int oy = base.getBlockY();
        int oz = base.getBlockZ();

        int cx1r = circlePos1.getBlockX() - ox;
        int cz1r = circlePos1.getBlockZ() - oz;
        int cy1 = circlePos1.getBlockY() - oy;
        int cx2r = circlePos2.getBlockX() - ox;
        int cz2r = circlePos2.getBlockZ() - oz;
        int cy2 = circlePos2.getBlockY() - oy;

        int[] c1 = inverseRotateXZ(cx1r, cz1r, rotation, sizeXRot, sizeZRot);
        int[] c2 = inverseRotateXZ(cx2r, cz2r, rotation, sizeXRot, sizeZRot);
        int minLocalX = Math.min(c1[0], c2[0]);
        int maxLocalX = Math.max(c1[0], c2[0]);
        int minLocalZ = Math.min(c1[1], c2[1]);
        int maxLocalZ = Math.max(c1[1], c2[1]);

        int centerLocalX = (minLocalX + maxLocalX) / 2;
        int centerLocalZ = (minLocalZ + maxLocalZ) / 2;
        int radius = Math.max(1, Math.max(maxLocalX - minLocalX, maxLocalZ - minLocalZ) / 2);
        int spawnMinLocalY = Math.min(cy1, cy2);
        int spawnMaxLocalY = Math.max(cy1, cy2);

        int pMinXr = Math.min(progressPos1.getBlockX(), progressPos2.getBlockX()) - ox;
        int pMaxXr = Math.max(progressPos1.getBlockX(), progressPos2.getBlockX()) - ox;
        int pMinZr = Math.min(progressPos1.getBlockZ(), progressPos2.getBlockZ()) - oz;
        int pMaxZr = Math.max(progressPos1.getBlockZ(), progressPos2.getBlockZ()) - oz;
        int[] pMin = inverseRotateXZ(pMinXr, pMinZr, rotation, sizeXRot, sizeZRot);
        int[] pMax = inverseRotateXZ(pMaxXr, pMaxZr, rotation, sizeXRot, sizeZRot);
        int pxLocal = (Math.min(pMin[0], pMax[0]) + Math.max(pMin[0], pMax[0])) / 2;
        int pzLocal = (Math.min(pMin[1], pMax[1]) + Math.max(pMin[1], pMax[1])) / 2;

        int topLocalY = Math.max(progressPos1.getBlockY(), progressPos2.getBlockY()) - oy;
        int bottomLocalY = Math.min(progressPos1.getBlockY(), progressPos2.getBlockY()) - oy;
        int totalHeight = Math.abs(topLocalY - bottomLocalY) + 1;
        int seg = Math.max(1, totalHeight / TOTAL);

        File dir = plugin.getDataFolder();
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, CONFIG_FILE_NAME);
        YamlConfiguration cfg = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        cfg.set("circle.centerLocalX", centerLocalX);
        cfg.set("circle.centerLocalZ", centerLocalZ);
        cfg.set("circle.radius", radius);
        cfg.set("circle.spawnMinLocalY", spawnMinLocalY);
        cfg.set("circle.spawnMaxLocalY", spawnMaxLocalY);
        cfg.set("progress.xLocal", pxLocal);
        cfg.set("progress.zLocal", pzLocal);
        cfg.set("progress.topLocalY", topLocalY);
        cfg.set("progress.bottomLocalY", bottomLocalY);
        cfg.set("progress.segmentHeight", seg);
        try {
            cfg.save(file);
            info("config saved origin=" + ox + "," + oy + "," + oz
                    + " rotation=" + rotation.name()
                    + " sizeRot=" + sizeXRot + "x" + sizeZRot
                    + " circle.localCenter=" + centerLocalX + "," + centerLocalZ
                    + " radius=" + radius
                    + " localY=" + spawnMinLocalY + ".." + spawnMaxLocalY
                    + " progress.localXZ=" + pxLocal + "," + pzLocal
                    + " progress.localTopY=" + topLocalY
                    + " progress.localBottomY=" + bottomLocalY
                    + " progress.seg=" + seg);
            return true;
        } catch (IOException e) {
            warn("config save failed err=" + e.getMessage());
            return false;
        }
    }

    public boolean handleInteract(PlayerInteractEvent event) {
        if (event == null) return false;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return false;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return false;
        if (clicked.getType() != Material.CHEST && clicked.getType() != Material.TRAPPED_CHEST) return false;
        World world = clicked.getWorld();
        if (world == null) return false;

        for (RoomState room : List.copyOf(roomsByKey.values())) {
            if (room == null) continue;
            if (!world.getName().equals(room.worldName)) continue;
            if (room.chestPos == null) continue;
            if (clicked.getX() != room.chestPos.x || clicked.getY() != room.chestPos.y || clicked.getZ() != room.chestPos.z) continue;

            if (!room.completed) {
                event.setCancelled(true);
                Player p = event.getPlayer();
                if (p != null) {
                    if (room.failed) {
                        p.sendMessage(Component.text("该谜题已失败", NamedTextColor.RED));
                    } else {
                        p.sendMessage(Component.text("该谜题尚未完成", NamedTextColor.RED));
                    }
                }
                roomInfo(room, "chest_interact blocked player=" + (p != null ? p.getName() : "null")
                        + " chest=" + clicked.getX() + "," + clicked.getY() + "," + clicked.getZ()
                        + " kills=" + room.kills + "/" + TOTAL
                        + " activeIndex=" + room.activeIndex);
            } else {
                event.setCancelled(true);
                Player p = event.getPlayer();
                if (p != null) {
                    UUID pid = p.getUniqueId();
                    if (!markOnce(room, pid, "pr08_blessing_chest")) {
                        p.sendMessage(Component.text("该箱子已被打开", NamedTextColor.RED));
                    } else {
                        openChestVisual(world, clicked.getX(), clicked.getY(), clicked.getZ());
                        spawnBlessing(world, clicked.getX(), clicked.getY(), clicked.getZ());
                    }
                }
                roomInfo(room, "chest_interact blessing player=" + (p != null ? p.getName() : "null")
                        + " chest=" + clicked.getX() + "," + clicked.getY() + "," + clicked.getZ());
            }
            return true;
        }

        return false;
    }

    public void handleAnyDamage(EntityDamageEvent event) {
        if (event == null) return;
        if (!(event.getEntity() instanceof Blaze blaze)) return;
        World world = blaze.getWorld();
        if (world == null) return;

        RoomState room = findRoomByBlazeEntity(blaze);
        if (room == null) return;
        if (room.failed || room.resetting || room.completed) {
            event.setCancelled(true);
            blaze.setInvulnerable(true);
            return;
        }

        int idx = blazeIndex(room, blaze);
        if (idx < 0 || idx >= TOTAL) {
            event.setCancelled(true);
            blaze.setInvulnerable(true);
            roomInfo(room, "damageAny cancelled: idx invalid blaze=" + blaze.getUniqueId()
                    + " cause=" + (event.getCause() != null ? event.getCause().name() : "null"));
            return;
        }

        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            event.setCancelled(true);
            blaze.setInvulnerable(true);
            return;
        }
        if (!(byEntity.getDamager() instanceof Projectile proj) || !(proj.getShooter() instanceof Player)) {
            event.setCancelled(true);
            blaze.setInvulnerable(true);
            return;
        }

        blaze.setInvulnerable(false);
    }

    public void handleDamage(EntityDamageByEntityEvent event) {
        if (event == null) return;
        if (!(event.getEntity() instanceof Blaze blaze)) return;
        World world = blaze.getWorld();
        if (world == null) return;

        RoomState room = findRoomByBlazeEntity(blaze);
        if (room == null) return;
        if (room.failed || room.resetting) {
            event.setCancelled(true);
            blaze.setInvulnerable(true);
            return;
        }

        if (room.completed) {
            event.setCancelled(true);
            blaze.setInvulnerable(true);
            roomInfo(room, "damage cancelled: room completed blaze=" + blaze.getUniqueId());
            return;
        }

        if (!(event.getDamager() instanceof Projectile proj) || !(proj.getShooter() instanceof Player player)) {
            event.setCancelled(true);
            blaze.setInvulnerable(true);
            roomInfo(room, "damage cancelled: not player projectile blaze=" + blaze.getUniqueId()
                    + " damager=" + (event.getDamager() != null ? event.getDamager().getType().name() : "null"));
            return;
        }

        int idx = blazeIndex(room, blaze);
        if (idx < 0 || idx >= TOTAL) {
            event.setCancelled(true);
            blaze.setInvulnerable(true);
            roomInfo(room, "damage cancelled: idx invalid player=" + player.getName()
                    + " blaze=" + blaze.getUniqueId()
                    + " dmg=" + event.getFinalDamage());
            return;
        }
        if (idx != room.activeIndex) {
            blaze.setInvulnerable(false);
            roomInfo(room, "damage allowed: wrong target player=" + player.getName()
                    + " blazeIdx=" + idx
                    + " activeIdx=" + room.activeIndex
                    + " blaze=" + blaze.getUniqueId()
                    + " dmg=" + event.getFinalDamage());
            Bukkit.getScheduler().runTask(plugin, () -> updateBlazeName(blaze, room.blazeMaxHealth[idx], idx, false));
            return;
        }

        blaze.setInvulnerable(false);
        roomInfo(room, "damage ok player=" + player.getName()
                + " blazeIdx=" + idx
                + " blaze=" + blaze.getUniqueId()
                + " hp=" + (int) Math.ceil(blaze.getHealth()) + "/" + (int) Math.ceil(room.blazeMaxHealth[idx])
                + " dmg=" + event.getFinalDamage());
        Bukkit.getScheduler().runTask(plugin, () -> updateBlazeName(blaze, room.blazeMaxHealth[idx], idx, true));
    }

    public void handleDeath(EntityDeathEvent event) {
        if (event == null) return;
        if (!(event.getEntity() instanceof Blaze blaze)) return;
        World world = blaze.getWorld();
        if (world == null) return;

        RoomState room = findRoomByBlazeEntity(blaze);
        if (room == null) return;
        if (room.completed) return;
        if (room.resetting) return;

        int idx = blazeIndex(room, blaze);
        if (idx < 0 || idx >= TOTAL) {
            roomInfo(room, "death treated as fail: idx invalid blaze=" + blaze.getUniqueId());
            failAndReset(room, Component.text("烈焰人状态异常：已重置", NamedTextColor.RED));
            return;
        }
        if (idx != room.activeIndex) {
            roomInfo(room, "death treated as fail: wrong blazeIdx=" + idx + " activeIdx=" + room.activeIndex + " blaze=" + blaze.getUniqueId());
            failNoReset(room, Component.text("击杀顺序错误：失败", NamedTextColor.RED));
            return;
        }

        room.kills++;
        room.activeIndex++;
        roomInfo(room, "death active blazeIdx=" + idx + " blaze=" + blaze.getUniqueId()
                + " kills=" + room.kills + "/" + TOTAL
                + " nextActiveIdx=" + room.activeIndex);
        advanceProgress(room);

        if (room.activeIndex >= TOTAL) {
            room.completed = true;
            roomInfo(room, "completed: all blazes killed");
            markPuzzleCompleted(world, room);
            return;
        }

        enableActive(room);
    }

    private void spawnForRoom(RoomState room) {
        if (room == null || room.resolved == null) return;
        World world = Bukkit.getWorld(room.worldName);
        if (world == null) return;

        cleanupEntities(world, room);
        room.messageOnce.clear();
        room.resetting = false;
        room.failed = false;

        double[] maxHp = generateDistinctDescendingMaxHealth(room.roomKey);
        for (int i = 0; i < TOTAL; i++) room.blazeMaxHealth[i] = maxHp[i];
        roomInfo(room, "spawn start config circle center=" + room.resolved.centerX + "," + room.resolved.centerZ
                + " radius=" + room.resolved.radius
                + " y=" + room.resolved.spawnMinY + ".." + room.resolved.spawnMaxY
                + " progress xz=" + room.resolved.progressX + "," + room.resolved.progressZ
                + " topY=" + room.resolved.progressTopY
                + " bottomY=" + room.resolved.progressBottomY
                + " seg=" + room.resolved.segmentHeight);

        int centerX = room.resolved.centerX;
        int centerZ = room.resolved.centerZ;
        int radius = Math.max(1, room.resolved.radius);
        int minY = Math.min(room.resolved.spawnMinY, room.resolved.spawnMaxY);
        int maxY = Math.max(room.resolved.spawnMinY, room.resolved.spawnMaxY);

        for (int i = 0; i < TOTAL; i++) {
            double angle = (Math.PI * 2.0) * (i / (double) TOTAL);
            int x = centerX + (int) Math.round(radius * Math.cos(angle));
            int z = centerZ + (int) Math.round(radius * Math.sin(angle));
            int y = pickSpawnY(world, x, minY, maxY, z, Objects.hash(room.roomKey, i));
            Location spawn = new Location(world, x + 0.5, y, z + 0.5);

            double max = room.blazeMaxHealth[i];
            int index = i;
            Blaze b = world.spawn(spawn, Blaze.class, e -> {
                e.setAI(false);
                e.setSilent(true);
                e.setRemoveWhenFarAway(false);
                e.setInvulnerable(true);
                e.setGlowing(false);
                if (e.getAttribute(Attribute.MAX_HEALTH) != null) {
                    Objects.requireNonNull(e.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(max);
                }
                e.setHealth(Math.min(max, e.getMaxHealth()));
                e.setCustomNameVisible(true);
            });

            room.blazeIds[i] = b.getUniqueId();
            b.addScoreboardTag(TAG_ROOT);
            b.addScoreboardTag(TAG_ROOM_PREFIX + room.roomKey);
            b.addScoreboardTag(TAG_IDX_PREFIX + i);
            updateBlazeName(b, max, index, false);
            roomInfo(room, "spawn blaze idx=" + i
                    + " id=" + b.getUniqueId()
                    + " at=" + x + "," + y + "," + z
                    + " maxHp=" + (int) Math.ceil(max));
        }

        room.activeIndex = 0;
        room.kills = 0;
        room.completed = false;
        room.spawned = true;

        resetProgressColumn(room);
        enableActive(room);
        roomInfo(room, "spawn done activeIdx=0");
    }

    private void enableActive(RoomState room) {
        if (room == null) return;
        World world = Bukkit.getWorld(room.worldName);
        if (world == null) return;

        for (int i = 0; i < room.blazeIds.length; i++) {
            UUID id = room.blazeIds[i];
            if (id == null) continue;
            Entity e = world.getEntity(id);
            if (!(e instanceof Blaze b) || !b.isValid() || b.isDead()) continue;
            boolean active = i == room.activeIndex;
            b.setInvulnerable(!active);
            updateBlazeName(b, room.blazeMaxHealth[i], i, active);
        }

        UUID id = room.activeIndex >= 0 && room.activeIndex < room.blazeIds.length ? room.blazeIds[room.activeIndex] : null;
        if (id == null) {
            roomInfo(room, "enableActive failed: activeIndex out of range activeIdx=" + room.activeIndex);
            return;
        }
        Entity e = world.getEntity(id);
        if (!(e instanceof Blaze b) || !b.isValid() || b.isDead()) {
            roomInfo(room, "enableActive failed: active blaze missing activeIdx=" + room.activeIndex + " id=" + id);
            return;
        }
        roomInfo(room, "enableActive activeIdx=" + room.activeIndex + " id=" + id
                + " maxHp=" + (int) Math.ceil(room.blazeMaxHealth[room.activeIndex]));
    }

    static Component buildHpTextComponent(int currentHp, int maxHp) {
        int c = Math.max(0, currentHp);
        int m = Math.max(1, maxHp);
        return Component.text("❤ " + c + "/" + m, NamedTextColor.RED);
    }

    static String buildHpText(int currentHp, int maxHp) {
        int c = Math.max(0, currentHp);
        int m = Math.max(1, maxHp);
        return "❤ " + c + "/" + m;
    }

    private void updateBlazeName(Blaze blaze, double maxHp, int index, boolean active) {
        if (blaze == null) return;
        double cur = Math.max(0.0, blaze.getHealth());
        int c = (int) Math.ceil(cur);
        int m = (int) Math.ceil(maxHp);
        Component name = Component.text("[烈焰人]", NamedTextColor.GRAY)
                .append(Component.text(" ", NamedTextColor.GRAY))
                .append(buildHpTextComponent(c, m));
        blaze.customName(name);
        blaze.setCustomName("[烈焰人] " + buildHpText(c, m));
        blaze.setCustomNameVisible(true);
    }

    private void resetProgressColumn(RoomState room) {
        if (room == null || room.resolved == null) return;
        World world = Bukkit.getWorld(room.worldName);
        if (world == null) return;

        if (room.progressTask != null) {
            room.progressTask.cancel();
            room.progressTask = null;
        }

        int x = room.resolved.progressX;
        int z = room.resolved.progressZ;
        int top = room.resolved.progressTopY;
        int bottom = room.resolved.progressBottomY;
        if (x == Integer.MIN_VALUE || z == Integer.MIN_VALUE) return;
        if (top == Integer.MIN_VALUE || bottom == Integer.MIN_VALUE) return;

        int endY = adjustProgressEndY(world, x, z, top, bottom);
        int minY = Math.min(top, endY);
        int maxY = Math.max(top, endY);
        for (int y = minY; y <= maxY; y++) {
            Block b = world.getBlockAt(x, y, z);
            if (b.getType() == Material.IRON_BARS || b.getType() == Material.CHEST || b.getType() == Material.TRAPPED_CHEST) {
                b.setType(Material.AIR, false);
            }
        }

        room.filledBlocks = 0;
        placeChest(room, x, top, z);
        roomInfo(room, "progress reset xz=" + x + "," + z + " top=" + top + " bottom=" + bottom + " endY=" + endY);
    }

    private int adjustProgressEndY(World world, int x, int z, int top, int bottom) {
        if (world == null) return bottom;
        int step = top <= bottom ? 1 : -1;
        int y = bottom;
        int minH = world.getMinHeight();
        int maxH = world.getMaxHeight() - 1;
        for (int i = 0; i < 2; i++) {
            int next = y + step;
            if (next < minH || next > maxH) break;
            if (!world.getBlockAt(x, next, z).getType().isAir()) break;
            y = next;
        }
        return y;
    }

    private void advanceProgress(RoomState room) {
        if (room == null || room.resolved == null) return;
        World world = Bukkit.getWorld(room.worldName);
        if (world == null) return;

        int x = room.resolved.progressX;
        int z = room.resolved.progressZ;
        int top = room.resolved.progressTopY;
        int bottom = room.resolved.progressBottomY;

        if (x == Integer.MIN_VALUE || z == Integer.MIN_VALUE) return;
        if (top == Integer.MIN_VALUE || bottom == Integer.MIN_VALUE) return;

        int endY = adjustProgressEndY(world, x, z, top, bottom);
        int step = top <= endY ? 1 : -1;
        int maxBars = Math.abs(top - endY);
        final int targetBars = Math.min(maxBars, (room.kills * maxBars + (TOTAL - 1)) / TOTAL);
        int start = room.filledBlocks;
        if (targetBars <= start) {
            roomInfo(room, "progress no-op kills=" + room.kills + " filled=" + start + " target=" + targetBars + " maxBars=" + maxBars);
            return;
        }
        roomInfo(room, "progress advance start=" + start + " target=" + targetBars + " maxBars=" + maxBars + " xz=" + x + "," + z + " top=" + top + " bottom=" + bottom + " endY=" + endY);

        if (room.progressTask != null) {
            room.progressTask.cancel();
            room.progressTask = null;
        }

        room.progressTask = new BukkitRunnable() {
            int placed = start;

            @Override
            public void run() {
                if (placed >= targetBars) {
                    room.filledBlocks = targetBars;
                    placeChest(room, x, top + targetBars * step, z);
                    cancel();
                    roomInfo(room, "progress done filled=" + room.filledBlocks + " chestY=" + (top + targetBars * step));
                    return;
                }

                int y = top + placed * step;
                if (y < Math.min(top, endY) || y > Math.max(top, endY)) {
                    placed = targetBars;
                    return;
                }
                world.getBlockAt(x, y, z).setType(Material.IRON_BARS, false);
                placed++;
                placeChest(room, x, top + placed * step, z);
            }
        };
        room.progressTask.runTaskTimer(plugin, 0L, 2L);
    }

    private void placeChest(RoomState room, int x, int y, int z) {
        if (room == null) return;
        World world = Bukkit.getWorld(room.worldName);
        if (world == null) return;

        if (room.chestPos != null) {
            Block old = world.getBlockAt(room.chestPos.x, room.chestPos.y, room.chestPos.z);
            if (old.getType() == Material.CHEST || old.getType() == Material.TRAPPED_CHEST) old.setType(Material.AIR, false);
        }

        Block b = world.getBlockAt(x, y, z);
        b.setType(Material.CHEST, false);
        room.chestPos = new BlockPos(x, y, z);
        roomInfo(room, "chest placed at " + x + "," + y + "," + z);
    }

    private RoomState findRoomByBlaze(String worldName, UUID blazeId) {
        if (worldName == null || blazeId == null) return null;
        for (RoomState room : List.copyOf(roomsByKey.values())) {
            if (room == null) continue;
            if (!worldName.equals(room.worldName)) continue;
            for (UUID id : room.blazeIds) {
                if (blazeId.equals(id)) return room;
            }
        }
        return null;
    }

    private void cleanupRoom(RoomState room) {
        if (room == null) return;
        World world = Bukkit.getWorld(room.worldName);
        roomInfo(room, "cleanup start");
        if (world != null) cleanupEntities(world, room);
        if (room.presenceTask != null) {
            room.presenceTask.cancel();
            room.presenceTask = null;
        }
        if (room.progressTask != null) {
            room.progressTask.cancel();
            room.progressTask = null;
        }
        roomsByKey.remove(room.roomKey);
        roomInfo(room, "cleanup done");
    }

    private void cleanupEntities(World world, RoomState room) {
        if (world == null || room == null) return;
        int removed = 0;
        for (UUID id : room.blazeIds) {
            if (id == null) continue;
            Entity e = world.getEntity(id);
            if (e != null) {
                e.remove();
                removed++;
            }
        }
        roomInfo(room, "cleanupEntities removedBlazes=" + removed);
        if (room.resolved != null) {
            int x = room.resolved.progressX;
            int z = room.resolved.progressZ;
            int top = room.resolved.progressTopY;
            int bottom = room.resolved.progressBottomY;
            if (x != Integer.MIN_VALUE && z != Integer.MIN_VALUE && top != Integer.MIN_VALUE && bottom != Integer.MIN_VALUE) {
                int minY = Math.min(top, bottom);
                int maxY = Math.max(top, bottom);
                int cleared = 0;
                for (int y = minY; y <= maxY; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (b.getType() == Material.IRON_BARS || b.getType() == Material.CHEST || b.getType() == Material.TRAPPED_CHEST) {
                        b.setType(Material.AIR, false);
                        cleared++;
                    }
                }
                roomInfo(room, "cleanupEntities clearedProgress blocks=" + cleared + " xz=" + x + "," + z + " y=" + minY + ".." + maxY);
            }
        } else if (room.chestPos != null) {
            Block b = world.getBlockAt(room.chestPos.x, room.chestPos.y, room.chestPos.z);
            if (b.getType() == Material.CHEST || b.getType() == Material.TRAPPED_CHEST) b.setType(Material.AIR, false);
            roomInfo(room, "cleanupEntities clearedChestOnly at " + room.chestPos.x + "," + room.chestPos.y + "," + room.chestPos.z);
        }
    }

    private boolean markOnce(RoomState room, UUID playerId, String key) {
        if (room == null || playerId == null || key == null) return false;
        room.messageOnce.computeIfAbsent(playerId, k -> new HashSet<>());
        Set<String> s = room.messageOnce.get(playerId);
        if (s == null) return false;
        return s.add(key);
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

    private static void openChestVisual(World world, int x, int y, int z) {
        if (world == null) return;
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST) return;
        Object state = block.getState();
        if (state == null) return;
        try {
            Class<?> lidded = Class.forName("org.bukkit.block.Lidded");
            if (!lidded.isInstance(state)) return;
            Method open = lidded.getMethod("open");
            open.invoke(state);
        } catch (Throwable ignored) {
        }
    }

    private void cleanupBlazesOnly(World world, RoomState room) {
        if (world == null || room == null) return;
        int removed = 0;
        for (UUID id : room.blazeIds) {
            if (id == null) continue;
            Entity e = world.getEntity(id);
            if (e != null) {
                e.remove();
                removed++;
            }
        }
        roomInfo(room, "cleanupBlazesOnly removedBlazes=" + removed);
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

    private void sendOnce(RoomState room, UUID playerId, String key, Component msg) {
        if (room == null || playerId == null || key == null || msg == null) return;
        room.messageOnce.computeIfAbsent(playerId, k -> new HashSet<>());
        Set<String> s = room.messageOnce.get(playerId);
        if (s == null) return;
        if (!s.add(key)) return;
        Player p = Bukkit.getPlayer(playerId);
        if (p == null) return;
        if (p.getWorld() == null || !p.getWorld().getName().equals(room.worldName)) return;
        p.sendMessage(msg);
    }

    private RoomState findRoomByBlazeEntity(Blaze blaze) {
        if (blaze == null) return null;
        World w = blaze.getWorld();
        if (w == null) return null;
        String worldName = w.getName();
        UUID blazeId = blaze.getUniqueId();
        if (worldName == null || blazeId == null) return null;

        RoomState byArray = findRoomByBlaze(worldName, blazeId);
        if (byArray != null) return byArray;

        for (String tag : blaze.getScoreboardTags()) {
            if (tag == null) continue;
            if (!tag.startsWith(TAG_ROOM_PREFIX)) continue;
            String roomKey = tag.substring(TAG_ROOM_PREFIX.length());
            RoomState room = roomsByKey.get(roomKey);
            if (room != null && worldName.equals(room.worldName)) return room;
        }
        return null;
    }

    private int blazeIndex(RoomState room, Blaze blaze) {
        if (room == null || blaze == null) return -1;
        UUID id = blaze.getUniqueId();
        int idx = indexOf(room.blazeIds, id);
        if (idx >= 0) return idx;

        for (String tag : blaze.getScoreboardTags()) {
            if (tag == null) continue;
            if (!tag.startsWith(TAG_IDX_PREFIX)) continue;
            try {
                int parsed = Integer.parseInt(tag.substring(TAG_IDX_PREFIX.length()));
                if (parsed < 0 || parsed >= TOTAL) return -1;
                if (room.blazeIds[parsed] == null || !id.equals(room.blazeIds[parsed])) {
                    room.blazeIds[parsed] = id;
                }
                return parsed;
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private void failAndReset(RoomState room, Component msg) {
        if (room == null || plugin == null) return;
        if (room.resetting) return;
        room.resetting = true;
        if (msg != null) {
            broadcastToRoom(room, msg);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            World w = Bukkit.getWorld(room.worldName);
            if (w == null || room.resolved == null) {
                room.resetting = false;
                return;
            }
            spawnForRoom(room);
        }, 10L);
    }

    private void failNoReset(RoomState room, Component msg) {
        if (room == null) return;
        if (room.failed) return;
        room.failed = true;
        room.resetting = true;
        if (msg != null) {
            broadcastToRoom(room, msg);
        }
        World world = Bukkit.getWorld(room.worldName);
        if (world != null) {
            markPuzzleFailed(world, room);
            cleanupBlazesOnly(world, room);
        }
        if (room.progressTask != null) {
            room.progressTask.cancel();
            room.progressTask = null;
        }
    }

    private void broadcastToRoom(RoomState room, Component msg) {
        if (room == null || msg == null) return;
        World world = Bukkit.getWorld(room.worldName);
        if (world == null) return;
        for (Player p : world.getPlayers()) {
            if (p == null) continue;
            Location l = p.getLocation();
            if (l == null) continue;
            int x = l.getBlockX();
            int z = l.getBlockZ();
            if (x < room.minX - 2 || x > room.maxX + 2 || z < room.minZ - 2 || z > room.maxZ + 2) continue;
            p.sendMessage(msg);
        }
    }

    private static int indexOf(UUID[] arr, UUID id) {
        if (arr == null || id == null) return -1;
        for (int i = 0; i < arr.length; i++) {
            if (id.equals(arr[i])) return i;
        }
        return -1;
    }

    private static int pickSpawnY(World world, int x, int minY, int maxY, int z, int seed) {
        if (world == null) return minY;
        int lo = Math.max(world.getMinHeight() + 1, Math.min(minY, maxY));
        int hi = Math.min(world.getMaxHeight() - 2, Math.max(minY, maxY));
        if (hi < lo) return lo;
        Random rng = new Random(seed);
        int start = lo + rng.nextInt((hi - lo) + 1);

        int maxSearch = Math.min(32, hi - lo);
        for (int d = 0; d <= maxSearch; d++) {
            int y1 = start + d;
            if (y1 >= lo && y1 <= hi) {
                if (world.getBlockAt(x, y1, z).getType().isAir() && world.getBlockAt(x, y1 + 1, z).getType().isAir()) {
                    return y1;
                }
            }
            int y2 = start - d;
            if (d != 0 && y2 >= lo && y2 <= hi) {
                if (world.getBlockAt(x, y2, z).getType().isAir() && world.getBlockAt(x, y2 + 1, z).getType().isAir()) {
                    return y2;
                }
            }
        }

        return start;
    }

    private static double[] generateDistinctDescendingMaxHealth(String roomKey) {
        Random rng = new Random(Objects.hash(roomKey, 81273421));
        int min = 30;
        int max = 220;
        Set<Integer> pick = new HashSet<>();
        while (pick.size() < TOTAL) {
            pick.add(min + rng.nextInt((max - min) + 1));
        }
        List<Integer> sorted = new ArrayList<>(pick);
        sorted.sort((a, b) -> Integer.compare(b, a));
        double[] out = new double[TOTAL];
        for (int i = 0; i < TOTAL; i++) {
            out[i] = sorted.get(i);
        }
        return out;
    }

    private Config loadConfigForWorld(String worldName) {
        if (plugin == null || worldName == null) return null;
        File file = new File(plugin.getDataFolder(), CONFIG_FILE_NAME);
        if (!file.exists()) {
            info("config missing file=" + file.getAbsolutePath());
            return null;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        int centerLocalX = cfg.getInt("circle.centerLocalX", Integer.MIN_VALUE);
        int centerLocalZ = cfg.getInt("circle.centerLocalZ", Integer.MIN_VALUE);
        int radius = cfg.getInt("circle.radius", Integer.MIN_VALUE);
        int spawnMinLocalY = cfg.getInt("circle.spawnMinLocalY", Integer.MIN_VALUE);
        int spawnMaxLocalY = cfg.getInt("circle.spawnMaxLocalY", Integer.MIN_VALUE);

        int progressLocalX = cfg.getInt("progress.xLocal", Integer.MIN_VALUE);
        int progressLocalZ = cfg.getInt("progress.zLocal", Integer.MIN_VALUE);
        int progressTopLocalY = cfg.getInt("progress.topLocalY", Integer.MIN_VALUE);
        int progressBottomLocalY = cfg.getInt("progress.bottomLocalY", Integer.MIN_VALUE);
        int seg = cfg.getInt("progress.segmentHeight", Integer.MIN_VALUE);

        if (centerLocalX != Integer.MIN_VALUE && centerLocalZ != Integer.MIN_VALUE && radius != Integer.MIN_VALUE
                && spawnMinLocalY != Integer.MIN_VALUE && spawnMaxLocalY != Integer.MIN_VALUE) {
            Config c = new Config();
            c.centerLocalX = centerLocalX;
            c.centerLocalZ = centerLocalZ;
            c.radius = radius;
            c.spawnMinLocalY = spawnMinLocalY;
            c.spawnMaxLocalY = spawnMaxLocalY;
            c.progressLocalX = progressLocalX;
            c.progressLocalZ = progressLocalZ;
            c.progressTopLocalY = progressTopLocalY;
            c.progressBottomLocalY = progressBottomLocalY;
            c.segmentHeight = seg == Integer.MIN_VALUE ? 1 : seg;
            c.legacyAbsolute = false;
            info("config loaded (relative) world=" + worldName
                    + " circle.localCenter=" + c.centerLocalX + "," + c.centerLocalZ
                    + " radius=" + c.radius
                    + " circle.localY=" + c.spawnMinLocalY + ".." + c.spawnMaxLocalY
                    + " progress.localXZ=" + c.progressLocalX + "," + c.progressLocalZ
                    + " progress.localTopY=" + c.progressTopLocalY
                    + " progress.localBottomY=" + c.progressBottomLocalY
                    + " seg=" + c.segmentHeight);
            return c;
        }

        String base = "worlds." + worldName;
        int absCenterX = cfg.getInt(base + ".circle.centerX", Integer.MIN_VALUE);
        int absCenterZ = cfg.getInt(base + ".circle.centerZ", Integer.MIN_VALUE);
        int absRadius = cfg.getInt(base + ".circle.radius", Integer.MIN_VALUE);
        int absMinY = cfg.getInt(base + ".circle.minY", Integer.MIN_VALUE);
        int absMaxY = cfg.getInt(base + ".circle.maxY", Integer.MIN_VALUE);
        int absProgressX = cfg.getInt(base + ".progress.x", Integer.MIN_VALUE);
        int absProgressZ = cfg.getInt(base + ".progress.z", Integer.MIN_VALUE);
        int absTopY = cfg.getInt(base + ".progress.topY", Integer.MIN_VALUE);
        int absBottomY = cfg.getInt(base + ".progress.bottomY", Integer.MIN_VALUE);
        int absSeg = cfg.getInt(base + ".progress.segmentHeight", Integer.MIN_VALUE);
        if (absCenterX == Integer.MIN_VALUE || absCenterZ == Integer.MIN_VALUE || absRadius == Integer.MIN_VALUE || absMinY == Integer.MIN_VALUE || absMaxY == Integer.MIN_VALUE) {
            info("config invalid world=" + worldName + " missing required fields (relative or legacy absolute)");
            return null;
        }
        Config legacy = new Config();
        legacy.centerLocalX = absCenterX;
        legacy.centerLocalZ = absCenterZ;
        legacy.radius = absRadius;
        legacy.spawnMinLocalY = absMinY;
        legacy.spawnMaxLocalY = absMaxY;
        legacy.progressLocalX = absProgressX;
        legacy.progressLocalZ = absProgressZ;
        legacy.progressTopLocalY = absTopY;
        legacy.progressBottomLocalY = absBottomY;
        legacy.segmentHeight = absSeg == Integer.MIN_VALUE ? 1 : absSeg;
        legacy.legacyAbsolute = true;
        warn("config loaded legacy absolute (will break if room origin changes) world=" + worldName);
        return legacy;
    }

    private static int[] computeXZBounds(Location base, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
        int bx = base.getBlockX();
        int bz = base.getBlockZ();
        int maxLocalX = sizeX - 1;
        int maxLocalZ = sizeZ - 1;
        int[][] corners = new int[][]{
                {0, 0},
                {maxLocalX, 0},
                {0, maxLocalZ},
                {maxLocalX, maxLocalZ}
        };
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int[] c : corners) {
            int[] rz = rotateLocalXZ(c[0], c[1], rotation, sizeX, sizeZ);
            int wx = bx + rz[0];
            int wz = bz + rz[1];
            minX = Math.min(minX, wx);
            maxX = Math.max(maxX, wx);
            minZ = Math.min(minZ, wz);
            maxZ = Math.max(maxZ, wz);
        }
        return new int[]{minX, maxX, minZ, maxZ};
    }

    private static int[] rotateLocalXZ(int x, int z, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
        return switch (rotation) {
            case R0 -> new int[]{x, z};
            case R90 -> new int[]{z, sizeX - 1 - x};
            case R180 -> new int[]{sizeX - 1 - x, sizeZ - 1 - z};
            case R270 -> new int[]{sizeZ - 1 - z, x};
        };
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
            case R0 -> new int[]{xRot, zRot};
            case R90 -> new int[]{sizeX0 - 1 - zRot, xRot};
            case R180 -> new int[]{sizeX0 - 1 - xRot, sizeZ0 - 1 - zRot};
            case R270 -> new int[]{zRot, sizeZ0 - 1 - xRot};
        };
    }

    private static ResolvedConfig resolveConfig(RoomState room, Config config) {
        if (room == null || config == null) return null;
        ResolvedConfig out = new ResolvedConfig();
        out.radius = Math.max(1, config.radius);
        out.segmentHeight = Math.max(1, config.segmentHeight);
        if (config.legacyAbsolute) {
            out.centerX = config.centerLocalX;
            out.centerZ = config.centerLocalZ;
            out.spawnMinY = config.spawnMinLocalY;
            out.spawnMaxY = config.spawnMaxLocalY;
            out.progressX = config.progressLocalX;
            out.progressZ = config.progressLocalZ;
            out.progressTopY = config.progressTopLocalY;
            out.progressBottomY = config.progressBottomLocalY;
            return out;
        }
        int[] rz = rotateLocalXZ(config.centerLocalX, config.centerLocalZ, room.rotation, room.sizeX, room.sizeZ);
        out.centerX = room.baseX + rz[0];
        out.centerZ = room.baseZ + rz[1];
        out.spawnMinY = room.baseY + config.spawnMinLocalY;
        out.spawnMaxY = room.baseY + config.spawnMaxLocalY;
        if (config.progressLocalX != Integer.MIN_VALUE && config.progressLocalZ != Integer.MIN_VALUE) {
            int[] p = rotateLocalXZ(config.progressLocalX, config.progressLocalZ, room.rotation, room.sizeX, room.sizeZ);
            out.progressX = room.baseX + p[0];
            out.progressZ = room.baseZ + p[1];
            if (config.progressTopLocalY != Integer.MIN_VALUE) out.progressTopY = room.baseY + config.progressTopLocalY;
            if (config.progressBottomLocalY != Integer.MIN_VALUE) out.progressBottomY = room.baseY + config.progressBottomLocalY;
        }
        return out;
    }

    private static final class Config {
        private int centerLocalX;
        private int centerLocalZ;
        private int radius;
        private int spawnMinLocalY;
        private int spawnMaxLocalY;
        private int progressLocalX = Integer.MIN_VALUE;
        private int progressLocalZ = Integer.MIN_VALUE;
        private int progressTopLocalY = Integer.MIN_VALUE;
        private int progressBottomLocalY = Integer.MIN_VALUE;
        private int segmentHeight = 1;
        private boolean legacyAbsolute;
    }

    private static final class ResolvedConfig {
        private int centerX;
        private int centerZ;
        private int radius;
        private int spawnMinY;
        private int spawnMaxY;
        private int progressX = Integer.MIN_VALUE;
        private int progressZ = Integer.MIN_VALUE;
        private int progressTopY = Integer.MIN_VALUE;
        private int progressBottomY = Integer.MIN_VALUE;
        private int segmentHeight = 1;
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
        private final UUID[] blazeIds = new UUID[TOTAL];
        private final double[] blazeMaxHealth = new double[TOTAL];
        private final Map<UUID, Set<String>> messageOnce = new HashMap<>();
        private boolean spawned;
        private boolean completed;
        private boolean failed;
        private boolean resetting;
        private int activeIndex;
        private int kills;
        private int minX;
        private int maxX;
        private int minZ;
        private int maxZ;
        private Config config;
        private ResolvedConfig resolved;
        private BlockPos chestPos;
        private BukkitRunnable presenceTask;
        private BukkitRunnable progressTask;
        private int filledBlocks;

        private RoomState(String roomKey, String worldName, StructureTemplate.Rotation rotation, int baseX, int baseY, int baseZ, int sizeX, int sizeZ) {
            this.roomKey = roomKey;
            this.worldName = worldName;
            this.rotation = rotation;
            this.baseX = baseX;
            this.baseY = baseY;
            this.baseZ = baseZ;
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
            this.failed = false;
        }
    }

    private static final class BlockPos {
        private final int x;
        private final int y;
        private final int z;

        private BlockPos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
