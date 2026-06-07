package maple.underground_caves.puzzle;

import maple.underground_caves.Test_dungeons;
import maple.underground_caves.dungeon.DungeonSecrets;
import maple.underground_caves.dungeon.StructureTemplate;

import org.bukkit.Location;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PuzzleRoom01Couplet {
    private static final boolean DEBUG = false; // 启用调试模式
    private static final double CREEPER_LINE_HIT_RADIUS = 1.0;
    private static final double CREEPER_LINE_HIT_HEIGHT_UP = 2.5;
    private static final double CREEPER_LINE_HIT_HEIGHT_DOWN = 1.5;
    private static final float CREEPER_TOMB_TNT_YIELD = 16.0f;
    private static final int ORBIT_UPDATE_PERIOD_TICKS = 1;
    private static final int ORBIT_INTERPOLATION_DURATION_TICKS = 1;
    private static final int RANGE_DISPLAY_PERIOD_TICKS = 2;
    private final Test_dungeons plugin;
    private final Map<UUID, PuzzleCoupletState> puzzleRooms = new HashMap<>();

    public PuzzleRoom01Couplet(Test_dungeons plugin) {
        this.plugin = plugin;
    }

    private void debug(String message) {
    }

    private void sendMessageOnce(PuzzleCoupletState state, Player player, String key, String message) {
        if (state == null || player == null || key == null || message == null) return;
        UUID pid = player.getUniqueId();
        Set<String> shown = state.shownMessageKeysByPlayer.computeIfAbsent(pid, k -> new HashSet<>());
        if (!shown.add(key)) return;
        player.sendMessage(message);
    }

    public void registerPuzzleRoom01(World world, Location roomOrigin, StructureTemplate.Rotation rotation, int sizeX,
            int sizeZ) {
        if (world == null || roomOrigin == null || rotation == null)
            return;
        Location base = roomOrigin.getBlock().getLocation();

        // 苦力怕位置 local(15, 19, 15)
        BlockPos creeperPos = toWorldBlock(base, 15, 19, 15, rotation, sizeX, sizeZ);
        Location creeperLoc = new Location(world, creeperPos.x() + 0.5, creeperPos.y(), creeperPos.z() + 0.5);

        // 检查是否已存在
        for (UUID id : puzzleRooms.keySet()) {
            Entity entity = world.getEntity(id);
            if (entity instanceof Creeper existing && existing.getLocation().distanceSquared(creeperLoc) <= 0.25) {
                return;
            }
        }

        // 生成苦力怕,朝向门口,Y+1格,高度3格
        final Location spawnLoc = creeperLoc.clone().add(0, 1.0, 0);
        final Location lookAt = spawnLoc.clone().add(0, 0, -10);
        final org.bukkit.util.Vector facing = lookAt.toVector().subtract(spawnLoc.toVector());
        Creeper creeper = world.spawn(spawnLoc, Creeper.class, c -> {
            c.setAI(false);
            c.setGravity(false);
            c.setSilent(true);
            c.setCollidable(true);
            c.setInvulnerable(false);
            c.setRemoveWhenFarAway(false);
            c.setPowered(false);
            c.setMaxFuseTicks(Integer.MAX_VALUE);
            // 朝向门口 (假设门在Z负方向)
            Location l = spawnLoc.clone();
            l.setDirection(facing);
            c.teleport(l);
        });

        // 生成粒子效果
        world.spawnParticle(Particle.EXPLOSION_EMITTER, spawnLoc, 5, 0.5, 0.5, 0.5, 0);
        world.playSound(spawnLoc, Sound.ENTITY_CREEPER_PRIMED, 1.0f, 0.8f);

        // 不再预定义海晶石灯位置,改为动态检测
        Set<BlockPos> seaLanterns = new HashSet<>(); // 空集合,动态添加

        // 定义坟墓区域 (12-18, 5-7, 12-18)
        Set<BlockPos> tombstones = new HashSet<>();
        for (int x = 12; x <= 18; x++) {
            for (int y = 5; y <= 7; y++) {
                for (int z = 12; z <= 18; z++) {
                    tombstones.add(toWorldBlock(base, x, y, z, rotation, sizeX, sizeZ));
                }
            }
        }

        PuzzleCoupletState state = new PuzzleCoupletState(world.getName(), creeper.getUniqueId(), spawnLoc,
                seaLanterns, tombstones);
        puzzleRooms.put(creeper.getUniqueId(), state);

        debug("Registered puzzle room - will detect sea lanterns dynamically");
        debug("Creeper location: " + spawnLoc.getBlockX() + "," + spawnLoc.getBlockY() + "," + spawnLoc.getBlockZ());
        debug("Creeper UUID: " + creeper.getUniqueId());
        debug("Total active puzzle rooms: " + puzzleRooms.size());
    }

    public boolean isPuzzleRoom01Creeper(Entity entity) {
        if (!(entity instanceof Creeper creeper)) return false;
        PuzzleCoupletState state = puzzleRooms.get(creeper.getUniqueId());
        if (state == null) return false;
        if (state.completed) return false;
        World w = creeper.getWorld();
        return w != null && state.worldName != null && state.worldName.equals(w.getName());
    }

    public boolean handleProjectileHit(ProjectileHitEvent event) {
        debug("ProjectileHit event received");

        if (event == null || event.getHitBlock() == null) {
            debug("Event or hitBlock is null");
            return false;
        }

        Material hitMaterial = event.getHitBlock().getType();
        debug("Hit block type: " + hitMaterial + " at " + event.getHitBlock().getX() + "," + event.getHitBlock().getY()
                + "," + event.getHitBlock().getZ());

        // 只接受海晶石灯
        if (hitMaterial != Material.SEA_LANTERN) {
            debug("Not a sea lantern, ignoring");
            return false;
        }

        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player player)) {
            debug("Shooter is not a player");
            return false;
        }

        World world = projectile.getWorld();
        BlockPos hitPos = new BlockPos(event.getHitBlock().getX(), event.getHitBlock().getY(),
                event.getHitBlock().getZ());

        debug("Player " + player.getName() + " hit sea lantern at " + hitPos.x() + "," + hitPos.y() + "," + hitPos.z());
        debug("Total puzzle rooms: " + puzzleRooms.size());

        // 查找对应的谜题房间 - 只要在同一个世界就接受
        PuzzleCoupletState state = null;
        for (PuzzleCoupletState s : puzzleRooms.values()) {
            if (!s.worldName.equals(world.getName())) {
                continue;
            }
            if (s.completed) {
                continue;
            }
            state = s;
            debug("Found matching puzzle room in world: " + s.worldName);
            break;
        }

        if (state == null) {
            debug("No active puzzle room found in this world");
            return false;
        }

        // 检查是否击中同一个灯
        if (state.lastHitLantern != null && state.lastHitLantern.equals(hitPos)) {
            sendMessageOnce(state, player, "same_lantern_twice", "§c不能连续射击同一个海晶石灯!");
            debug("Same lantern hit twice");
            return true;
        }

        if (state.firstHitLantern == null) {
            // 第一次射击
            state.firstHitLantern = hitPos;
            state.lastHitLantern = hitPos;
            world.playSound(event.getHitBlock().getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            sendMessageOnce(state, player, "first_hit_ok", "§a第一次射击成功! 请射击另一个海晶石灯!");
            debug("First hit registered");
        } else {
            // 第二次射击
            // 检查是否是重复的连线
            String lineKey = getLineKey(state.firstHitLantern, hitPos);
            if (state.usedLines.contains(lineKey)) {
                sendMessageOnce(state, player, "line_already_used", "§c这两个海晶石灯已经连接过了!");
                debug("Line already used: " + lineKey);
                state.firstHitLantern = null;
                state.lastHitLantern = hitPos;
                return true;
            }

            Location start = new Location(world, state.firstHitLantern.x() + 0.5, state.firstHitLantern.y() + 0.5,
                    state.firstHitLantern.z() + 0.5);
            Location end = new Location(world, hitPos.x() + 0.5, hitPos.y() + 0.5, hitPos.z() + 0.5);

            boolean intersects = checkLineIntersectsCreeper(start, end, state.creeperLocation);

            if (intersects) {
                // 穿过苦力怕 - 替换为红石块
                world.getBlockAt(state.firstHitLantern.x(), state.firstHitLantern.y(), state.firstHitLantern.z())
                        .setType(Material.REDSTONE_BLOCK);
                world.getBlockAt(hitPos.x(), hitPos.y(), hitPos.z()).setType(Material.REDSTONE_BLOCK);
                world.playSound(end, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 2.0f);
                sendMessageOnce(state, player, "intersect_success", "§a成功穿过苦力怕! 海晶石灯变成红石块!");

                // 创建粒子线并生成TNT
                createParticleLine(start, end, state, true);
                spawnOrbitingTNT(state);

                // 标记这条线已使用
                state.usedLines.add(lineKey);
            } else {
                // 没穿过苦力怕 - 保持海晶石灯
                world.playSound(end, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
                sendMessageOnce(state, player, "intersect_fail", "§c没有穿过苦力怕,请重新尝试!");

                // 创建粒子线但不生成TNT
                createParticleLine(start, end, state, false);
            }

            state.firstHitLantern = null;
            state.lastHitLantern = hitPos;
            debug("Second hit registered, intersects=" + intersects);
        }

        return true;
    }

    private void createParticleLine(Location start, Location end, PuzzleCoupletState state, boolean intersects) {
        World world = start.getWorld();
        if (world == null)
            return;

        int sx = start.getBlockX();
        int sy = start.getBlockY();
        int sz = start.getBlockZ();
        int ex = end.getBlockX();
        int ey = end.getBlockY();
        int ez = end.getBlockZ();

        state.activeLines.removeIf(existing -> {
            if (existing == null) return true;
            Location a = existing.start;
            Location b = existing.end;
            if (a == null || b == null) return true;
            boolean touches =
                    (a.getBlockX() == sx && a.getBlockY() == sy && a.getBlockZ() == sz)
                            || (a.getBlockX() == ex && a.getBlockY() == ey && a.getBlockZ() == ez)
                            || (b.getBlockX() == sx && b.getBlockY() == sy && b.getBlockZ() == sz)
                            || (b.getBlockX() == ex && b.getBlockY() == ey && b.getBlockZ() == ez);
            if (!touches) return false;
            if (existing.particleTask != null) existing.particleTask.cancel();
            return true;
        });

        ParticleLine line = new ParticleLine(start.clone(), end.clone(), intersects);
        state.activeLines.add(line);

        // 创建粒子任务
        BukkitRunnable particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state.completed) {
                    cancel();
                    return;
                }
                drawParticleLine(start, end, world, intersects);
            }
        };
        line.particleTask = particleTask;
        particleTask.runTaskTimer(plugin, 0L, 2L);
    }

    private void drawParticleLine(Location start, Location end, World world, boolean intersects) {
        Vector direction = end.toVector().subtract(start.toVector());
        double distance = direction.length();
        direction.normalize();

        Particle particle = intersects ? Particle.ELECTRIC_SPARK : Particle.SMOKE;
        for (double d = 0; d <= distance; d += 0.3) {
            Location point = start.clone().add(direction.clone().multiply(d));
            world.spawnParticle(particle, point, 1, 0, 0, 0, 0);
        }
    }

    private boolean checkLineIntersectsCreeper(Location start, Location end, Location creeper) {
        Vector lineVec = end.toVector().subtract(start.toVector());
        Vector creeperVec = creeper.toVector().subtract(start.toVector());

        double lineLengthSq = lineVec.lengthSquared();
        if (lineLengthSq < 0.0001)
            return false;

        double t = creeperVec.dot(lineVec) / lineLengthSq;
        t = Math.max(0, Math.min(1, t));

        Vector closest = start.toVector().add(lineVec.clone().multiply(t));

        // 检查水平距离和垂直距离
        double dx = closest.getX() - creeper.getX();
        double dy = closest.getY() - creeper.getY();
        double dz = closest.getZ() - creeper.getZ();

        double horizontalDistSq = dx * dx + dz * dz;
        double verticalSigned = dy;

        return horizontalDistSq <= CREEPER_LINE_HIT_RADIUS * CREEPER_LINE_HIT_RADIUS
                && verticalSigned >= -CREEPER_LINE_HIT_HEIGHT_DOWN
                && verticalSigned <= CREEPER_LINE_HIT_HEIGHT_UP;
    }

    private void startCreeperRangeDisplay(PuzzleCoupletState state) {
        if (state == null) return;
        World world = state.creeperLocation != null ? state.creeperLocation.getWorld() : null;
        if (world == null) return;
        if (state.rangeTask != null) {
            state.rangeTask.cancel();
            state.rangeTask = null;
        }

        Particle.DustOptions ring = new Particle.DustOptions(Color.fromRGB(0, 255, 0), 1.0f);
        state.rangeTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state.completed) {
                    cancel();
                    return;
                }
                World w = state.creeperLocation != null ? state.creeperLocation.getWorld() : null;
                if (w == null) {
                    cancel();
                    return;
                }

                Entity e = w.getEntity(state.creeperUUID);
                if (!(e instanceof Creeper creeper) || !creeper.isValid() || creeper.isDead()) {
                    cancel();
                    return;
                }

                Location c = creeper.getLocation().clone();
                c.setX(state.creeperLocation.getX());
                c.setZ(state.creeperLocation.getZ());
                double baseY = state.creeperLocation.getY();
                drawRangeRing(w, c.getX(), baseY - CREEPER_LINE_HIT_HEIGHT_DOWN, c.getZ(), CREEPER_LINE_HIT_RADIUS, ring);
                drawRangeRing(w, c.getX(), baseY, c.getZ(), CREEPER_LINE_HIT_RADIUS, ring);
                drawRangeRing(w, c.getX(), baseY + CREEPER_LINE_HIT_HEIGHT_UP, c.getZ(), CREEPER_LINE_HIT_RADIUS, ring);
                drawRangePillars(w, c.getX(), baseY, c.getZ(), CREEPER_LINE_HIT_RADIUS, CREEPER_LINE_HIT_HEIGHT_DOWN, CREEPER_LINE_HIT_HEIGHT_UP, ring);
            }
        };
        state.rangeTask.runTaskTimer(plugin, 0L, RANGE_DISPLAY_PERIOD_TICKS);
    }

    private static void drawRangeRing(World world, double cx, double y, double cz, double radius, Particle.DustOptions dust) {
        int points = 32;
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2.0) * (i / (double) points);
            double x = cx + radius * Math.cos(a);
            double z = cz + radius * Math.sin(a);
            world.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, dust);
        }
    }

    private static void drawRangePillars(World world, double cx, double cy, double cz, double radius, double down, double up, Particle.DustOptions dust) {
        int pillars = 8;
        double stepY = 0.6;
        for (int i = 0; i < pillars; i++) {
            double a = (Math.PI * 2.0) * (i / (double) pillars);
            double x = cx + radius * Math.cos(a);
            double z = cz + radius * Math.sin(a);
            for (double dy = -down; dy <= up; dy += stepY) {
                world.spawnParticle(Particle.DUST, x, cy + dy, z, 1, 0, 0, 0, 0, dust);
            }
        }
    }

    private void spawnOrbitingTNT(PuzzleCoupletState state) {
        if (state.orbitingTNTs.size() >= 5)
            return;

        World world = state.creeperLocation.getWorld();
        if (world == null)
            return;

        int tntIndex = state.orbitingTNTs.size();
        double angle = tntIndex * Math.PI * 2 / 5;
        double radius = 1.5; // 增加半径
        double x = state.creeperLocation.getX() + radius * Math.cos(angle);
        double z = state.creeperLocation.getZ() + radius * Math.sin(angle);
        double y = state.creeperLocation.getY(); // 苦力怕中间高度

        Location tntLoc = new Location(world, x, y, z);
        ItemDisplay tntDisplay = world.spawn(tntLoc, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.TNT));
            d.setBillboard(Display.Billboard.FIXED);
            d.setShadowRadius(0);
            d.setShadowStrength(0);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(ORBIT_INTERPOLATION_DURATION_TICKS);
            Transformation baseTransform = d.getTransformation();
            d.setTransformation(new Transformation(
                    baseTransform.getTranslation(),
                    baseTransform.getLeftRotation(),
                    new Vector3f(0.5f, 0.5f, 0.5f), // 稍微大一点
                    baseTransform.getRightRotation()));
        });

        state.orbitingTNTs.add(tntDisplay.getUniqueId());
        world.playSound(state.creeperLocation, Sound.ENTITY_TNT_PRIMED, 0.5f, 1.5f);

        // 如果是第一个TNT,启动旋转任务
        if (state.orbitTask == null) {
            startOrbitAnimation(state);
        }

        // 如果是第5个TNT,开始汇聚
        if (state.orbitingTNTs.size() == 5) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    startTNTConvergence(state);
                }
            }.runTaskLater(plugin, 40L); // 2秒后开始汇聚
        }
    }

    private void startOrbitAnimation(PuzzleCoupletState state) {
        state.orbitTask = new BukkitRunnable() {
            double angle = 0;
            final double radius = 1.5; // 与生成时一致
            final double speed = Math.PI / 20; // 更慢更流畅 (9度/tick)

            @Override
            public void run() {
                if (state.completed || state.convergenceStarted) {
                    cancel();
                    return;
                }

                World world = state.creeperLocation.getWorld();
                if (world == null) {
                    cancel();
                    return;
                }

                angle += speed * ORBIT_UPDATE_PERIOD_TICKS;
                for (int i = 0; i < state.orbitingTNTs.size(); i++) {
                    UUID tntId = state.orbitingTNTs.get(i);
                    Entity entity = world.getEntity(tntId);
                    if (!(entity instanceof ItemDisplay tnt))
                        continue;

                    double tntAngle = angle + i * Math.PI * 2 / 5;
                    double x = state.creeperLocation.getX() + radius * Math.cos(tntAngle);
                    double z = state.creeperLocation.getZ() + radius * Math.sin(tntAngle);
                    double y = state.creeperLocation.getY();

                    tnt.teleport(new Location(world, x, y, z, 0f, 0f));
                }
            }
        };
        state.orbitTask.runTaskTimer(plugin, 0L, ORBIT_UPDATE_PERIOD_TICKS);
    }

    private void startTNTConvergence(PuzzleCoupletState state) {
        if (state.convergenceStarted)
            return;
        state.convergenceStarted = true;

        // 停止旋转
        if (state.orbitTask != null) {
            state.orbitTask.cancel();
            state.orbitTask = null;
        }

        World world = state.creeperLocation.getWorld();
        if (world == null)
            return;

        world.playSound(state.creeperLocation, Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.5f);

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 30; // 1.5秒汇聚时间

            @Override
            public void run() {
                if (state.completed) {
                    cancel();
                    return;
                }

                World world = state.creeperLocation.getWorld();
                if (world == null) {
                    cancel();
                    return;
                }

                ticks++;
                boolean allArrived = true;

                for (UUID tntId : state.orbitingTNTs) {
                    Entity entity = world.getEntity(tntId);
                    if (!(entity instanceof ItemDisplay tnt))
                        continue;

                    Location tntLoc = tnt.getLocation();
                    double dist = tntLoc.distance(state.creeperLocation);

                    if (dist > 0.3) {
                        allArrived = false;
                        Vector direction = state.creeperLocation.toVector().subtract(tntLoc.toVector());
                        direction.normalize().multiply(0.1);
                        tnt.teleport(tntLoc.add(direction));
                    }
                }

                // 粒子效果
                world.spawnParticle(Particle.FLAME, state.creeperLocation, 10, 0.5, 0.5, 0.5, 0.02);

                if (allArrived || ticks >= maxTicks) {
                    triggerTombExplosion(world, state);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void triggerTombExplosion(World world, PuzzleCoupletState state) {
        // 清理TNT展示实体
        for (UUID tntId : state.orbitingTNTs) {
            Entity entity = world.getEntity(tntId);
            if (entity != null)
                entity.remove();
        }

        if (state.rangeTask != null) {
            state.rangeTask.cancel();
            state.rangeTask = null;
        }

        // 生成即爆TNT
        DungeonSecrets secrets = plugin != null ? plugin.getDungeonSecrets() : null;
        Location explosionLoc = state.creeperLocation.clone();
        if (secrets != null) {
            secrets.spawnSpecialTnt(world, explosionLoc, 1, CREEPER_TOMB_TNT_YIELD);
        } else {
            world.spawn(explosionLoc, TNTPrimed.class, tnt -> {
                tnt.setFuseTicks(1);
                tnt.setYield(CREEPER_TOMB_TNT_YIELD);
            });
        }

        // 标记谜题完成
        if (secrets != null) {
            secrets.markPuzzleCompleted(world, explosionLoc);
        }

        state.completed = true;

        // 清理粒子线
        for (ParticleLine line : state.activeLines) {
            if (line.particleTask != null) {
                line.particleTask.cancel();
            }
        }

        // 清理苦力怕
        Entity creeper = world.getEntity(state.creeperUUID);
        if (creeper != null)
            creeper.remove();

        puzzleRooms.remove(state.creeperUUID);
    }

    // 生成连线的唯一key
    private static String getLineKey(BlockPos pos1, BlockPos pos2) {
        // 确保顺序一致,不管A-B还是B-A都是同一条线
        int x1 = Math.min(pos1.x(), pos2.x());
        int y1 = Math.min(pos1.y(), pos2.y());
        int z1 = Math.min(pos1.z(), pos2.z());
        int x2 = Math.max(pos1.x(), pos2.x());
        int y2 = Math.max(pos1.y(), pos2.y());
        int z2 = Math.max(pos1.z(), pos2.z());
        return x1 + "," + y1 + "," + z1 + "-" + x2 + "," + y2 + "," + z2;
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
            case R0 -> new int[] { x, z };
            case R90 -> new int[] { z, sizeX - 1 - x };
            case R180 -> new int[] { sizeX - 1 - x, sizeZ - 1 - z };
            case R270 -> new int[] { sizeZ - 1 - z, x };
        };
    }

    private record BlockPos(int x, int y, int z) {
    }

    private static final class PuzzleCoupletState {
        private final String worldName;
        private final UUID creeperUUID;
        private final Location creeperLocation;
        private final Set<BlockPos> seaLanterns;
        private final Set<BlockPos> tombstones;
        private final List<ParticleLine> activeLines = new ArrayList<>();
        private final List<UUID> orbitingTNTs = new ArrayList<>();
        private final Set<String> usedLines = new HashSet<>(); // 已使用的连线
        private final Map<UUID, Set<String>> shownMessageKeysByPlayer = new HashMap<>();

        private BlockPos firstHitLantern;
        private BlockPos lastHitLantern;
        private BukkitRunnable orbitTask;
        private BukkitRunnable rangeTask;
        private boolean convergenceStarted;
        private boolean completed;

        private PuzzleCoupletState(String worldName, UUID creeperUUID, Location creeperLocation,
                Set<BlockPos> seaLanterns, Set<BlockPos> tombstones) {
            this.worldName = worldName;
            this.creeperUUID = creeperUUID;
            this.creeperLocation = creeperLocation;
            this.seaLanterns = seaLanterns;
            this.tombstones = tombstones;
        }
    }

    private static final class ParticleLine {
        private final Location start;
        private final Location end;
        private final boolean intersectsCreeper;
        private BukkitRunnable particleTask;

        private ParticleLine(Location start, Location end, boolean intersectsCreeper) {
            this.start = start;
            this.end = end;
            this.intersectsCreeper = intersectsCreeper;
        }
    }
}
