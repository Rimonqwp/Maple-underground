package maple.underground_caves.puzzle;

import maple.underground_caves.Test_dungeons;
import maple.underground_caves.dungeon.DungeonSecrets;
import maple.underground_caves.dungeon.StructureTemplate;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Silverfish;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PuzzleRoom06Silverfish {
    private static final boolean DEBUG = false;
    private final Test_dungeons plugin;
    private final Map<UUID, PuzzleSilverfishState> puzzleSilverfish = new HashMap<>();

    public PuzzleRoom06Silverfish(Test_dungeons plugin) {
        this.plugin = plugin;
    }

    private void debug(String message) {
    }

    public void registerPuzzleRoom06(World world, Location roomOrigin, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
        if (world == null || roomOrigin == null || rotation == null) return;
        Location base = roomOrigin.getBlock().getLocation();
        BlockPos spawnPos = toWorldBlock(base, 22, 5, 8, rotation, sizeX, sizeZ);
        Location spawn = new Location(world, spawnPos.x() + 0.5, spawnPos.y(), spawnPos.z() + 0.5);
        for (UUID id : puzzleSilverfish.keySet()) {
            Entity entity = world.getEntity(id);
            if (entity instanceof Silverfish existing && existing.getLocation().distanceSquared(spawn) <= 0.25) {
                return;
            }
        }

        Silverfish silverfish = world.spawn(spawn, Silverfish.class, s -> {
            s.setAI(false);
            s.setGravity(false);
            s.setSilent(true);
            s.setCollidable(false);
            s.setRemoveWhenFarAway(false);
        });

        ItemDisplay tntDisplay = world.spawn(spawn.clone().add(0, 0.55, 0), ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.TNT));
            d.setBillboard(Display.Billboard.FIXED);
            d.setShadowRadius(0);
            d.setShadowStrength(0);
            Transformation baseTransform = d.getTransformation();
            d.setTransformation(new Transformation(
                    baseTransform.getTranslation(),
                    baseTransform.getLeftRotation(),
                    new Vector3f(0.30f, 0.30f, 0.30f),
                    baseTransform.getRightRotation()
            ));
        });
        silverfish.addPassenger(tntDisplay);

        Set<BlockPos> triggerBlocks = new HashSet<>();
        triggerBlocks.add(toWorldBlock(base, 5, 5, 14, rotation, sizeX, sizeZ));
        triggerBlocks.add(toWorldBlock(base, 5, 5, 15, rotation, sizeX, sizeZ));
        triggerBlocks.add(toWorldBlock(base, 5, 5, 16, rotation, sizeX, sizeZ));

        Set<BlockPos> removalBlocks = new HashSet<>();
        for (int y = 6; y <= 7; y++) {
            for (int z = 14; z <= 16; z++) {
                removalBlocks.add(toWorldBlock(base, 5, y, z, rotation, sizeX, sizeZ));
            }
        }

        puzzleSilverfish.put(silverfish.getUniqueId(), new PuzzleSilverfishState(world.getName(), tntDisplay.getUniqueId(), triggerBlocks, removalBlocks));
    }

    public boolean handleDamage(EntityDamageByEntityEvent event) {
        if (event == null) return false;
        Entity damaged = event.getEntity();
        if (plugin != null && (damaged instanceof Silverfish || damaged instanceof ItemDisplay)) {
            debug("puzzle_room_06 hit raw entity=" + damaged.getType().name()
                    + " world=" + damaged.getWorld().getName()
                    + " cancelled=" + event.isCancelled()
                    + " damager=" + (event.getDamager() != null ? event.getDamager().getType().name() : "null"));
        }

        Silverfish fish = null;
        if (damaged instanceof Silverfish direct) {
            fish = direct;
        } else if (damaged instanceof ItemDisplay display && display.getVehicle() instanceof Silverfish mounted) {
            fish = mounted;
        } else {
            return false;
        }

        World world = fish.getWorld();
        PuzzleSilverfishState state = puzzleSilverfish.get(fish.getUniqueId());
        if (state == null) {
            return false;
        }
        if (state.worldName == null || !state.worldName.equals(world.getName())) {
            return false;
        }

        event.setCancelled(true);
        if (state.moving) {
            return true;
        }

        Vector direction;
        if (event.getDamager() instanceof Player player) {
            direction = player.getLocation().getDirection();
            if (direction.lengthSquared() < 1.0E-6) {
                direction = fish.getLocation().toVector().subtract(player.getLocation().toVector());
            }
        } else if (event.getDamager() instanceof Projectile projectile) {
            direction = projectile.getVelocity();
        } else {
            direction = fish.getLocation().toVector().subtract(event.getDamager().getLocation().toVector());
        }
        if (direction.lengthSquared() < 1.0E-6) {
            debug("puzzle_room_06 hit ignored: zero direction fish=" + fish.getUniqueId()
                    + " damager=" + event.getDamager().getType().name());
            return true;
        }

        debug("puzzle_room_06 hit ok fish=" + fish.getUniqueId()
                + " damager=" + event.getDamager().getType().name()
                + " dir=" + direction.getX() + "," + direction.getY() + "," + direction.getZ());
        startPuzzleMove(fish, state, direction);
        return true;
    }

    private static BlockPos toWorldBlock(Location base, int localX, int localY, int localZ, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
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

    private void startPuzzleMove(Silverfish fish, PuzzleSilverfishState state, Vector direction) {
        if (fish == null || state == null) return;
        if (state.moving) return;
        debug("puzzle_room_06 move start fish=" + fish.getUniqueId()
                + " pos=" + fish.getLocation().getBlockX() + "," + fish.getLocation().getBlockY() + "," + fish.getLocation().getBlockZ()
                + " dir=" + direction.getX() + "," + direction.getY() + "," + direction.getZ());
        state.moving = true;

        Vector step = direction.clone().setY(0);
        if (step.lengthSquared() < 1.0E-6) {
            debug("puzzle_room_06 move cancelled: zero horizontal direction");
            state.moving = false;
            return;
        }
        if (Math.abs(step.getX()) >= Math.abs(step.getZ())) {
            step.setZ(0);
        } else {
            step.setX(0);
        }
        step.normalize();
        int moveDx = step.getX() > 0 ? 1 : (step.getX() < 0 ? -1 : 0);
        int moveDz = step.getZ() > 0 ? 1 : (step.getZ() < 0 ? -1 : 0);
        if (moveDx == 0 && moveDz == 0) {
            debug("puzzle_room_06 move cancelled: no axis direction");
            state.moving = false;
            return;
        }
        Location initial = fish.getLocation();
        double fixedY = initial.getY();
        int fixedBlockY = initial.getBlockY();
        World world = fish.getWorld();
        double height = fish.getHeight();
        double speedPerTick = 0.25;

        int startBlockX = initial.getBlockX();
        int startBlockZ = initial.getBlockZ();
        Location snappedStart = initial.clone();
        snappedStart.setX(startBlockX + 0.5);
        snappedStart.setY(fixedY);
        snappedStart.setZ(startBlockZ + 0.5);
        teleportWithPassengers(fish, snappedStart);

        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;
            int curBlockX = startBlockX;
            int curBlockZ = startBlockZ;
            int targetBlockX = curBlockX + moveDx;
            int targetBlockZ = curBlockZ + moveDz;
            double targetX = targetBlockX + 0.5;
            double targetZ = targetBlockZ + 0.5;
            boolean targetChecked = false;

            @Override
            public void run() {
                if (!fish.isValid()) {
                    cleanupPuzzleEntity(world, fish.getUniqueId(), state);
                    cancel();
                    return;
                }
                ticks++;
                if (!targetChecked) {
                    Location targetCenter = fish.getLocation();
                    targetCenter.setX(targetX);
                    targetCenter.setY(fixedY);
                    targetCenter.setZ(targetZ);
                    if (isBlocked(world, targetCenter, height)) {
                        Location safe = fish.getLocation();
                        safe.setX(curBlockX + 0.5);
                        safe.setY(fixedY);
                        safe.setZ(curBlockZ + 0.5);
                        teleportWithPassengers(fish, safe);
                        state.moving = false;
                        cancel();
                        return;
                    }
                    targetChecked = true;
                }
                Location cur = fish.getLocation();
                double curX = cur.getX();
                double curZ = cur.getZ();
                double dx = targetX - curX;
                double dz = targetZ - curZ;
                double remaining = Math.abs(dx) + Math.abs(dz);

                if (remaining <= 1.0E-6) {
                    curBlockX = targetBlockX;
                    curBlockZ = targetBlockZ;
                    if (state.triggerBlocks.contains(new BlockPos(curBlockX, fixedBlockY, curBlockZ))) {
                        triggerPuzzleExplosion(world, fish, state);
                        cancel();
                        return;
                    }
                    targetBlockX = curBlockX + moveDx;
                    targetBlockZ = curBlockZ + moveDz;
                    targetX = targetBlockX + 0.5;
                    targetZ = targetBlockZ + 0.5;
                    targetChecked = false;
                } else {
                    double move = Math.min(speedPerTick, remaining);
                    double nextX = curX + Math.signum(dx) * move;
                    double nextZ = curZ + Math.signum(dz) * move;
                    cur.setX(nextX);
                    cur.setY(fixedY);
                    cur.setZ(nextZ);
                    if (!teleportWithPassengers(fish, cur)) {
                        state.moving = false;
                        cancel();
                        return;
                    }
                }

                if (ticks >= 200) {
                    debug("puzzle_room_06 move timeout");
                    state.moving = false;
                    cancel();
                }
            }
        };
        state.moveTask = task;
        task.runTaskTimer(plugin, 1L, 1L);
    }

    private static boolean teleportWithPassengers(Entity vehicle, Location destination) {
        if (vehicle == null || destination == null) return false;
        boolean moved = vehicle.teleport(destination);
        if (moved) return true;
        List<Entity> passengers = List.copyOf(vehicle.getPassengers());
        if (!passengers.isEmpty()) vehicle.eject();
        moved = vehicle.teleport(destination);
        for (Entity p : passengers) {
            vehicle.addPassenger(p);
        }
        return moved;
    }

    private static boolean isBlocked(World world, Location next, double height) {
        int bx = next.getBlockX();
        int by = next.getBlockY();
        int bz = next.getBlockZ();
        if (world.getBlockAt(bx, by, bz).getType().isSolid()) return true;
        if (height > 1.0) {
            return world.getBlockAt(bx, by + 1, bz).getType().isSolid();
        }
        return false;
    }

    private static boolean isOnTriggerBlock(Silverfish fish, PuzzleSilverfishState state) {
        Location loc = fish.getLocation();
        BlockPos pos = new BlockPos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        return state.triggerBlocks.contains(pos);
    }

    private void triggerPuzzleExplosion(World world, Silverfish fish, PuzzleSilverfishState state) {
        Location loc = fish.getLocation();
        world.createExplosion(loc.getX(), loc.getY(), loc.getZ(), 2.0f, false, false);
        for (BlockPos p : state.removalBlocks) {
            world.getBlockAt(p.x(), p.y(), p.z()).setType(Material.AIR);
        }
        DungeonSecrets secrets = plugin != null ? plugin.getDungeonSecrets() : null;
        if (secrets != null) {
            secrets.markPuzzleCompleted(world, loc);
        }
        cleanupPuzzleEntity(world, fish.getUniqueId(), state);
    }

    private void cleanupPuzzleEntity(World world, UUID silverfishId, PuzzleSilverfishState state) {
        if (state.moveTask != null) {
            state.moveTask.cancel();
            state.moveTask = null;
        }
        if (world != null && state.tntStandId != null) {
            Entity stand = world.getEntity(state.tntStandId);
            if (stand != null) {
                stand.remove();
            }
        }
        Entity fish = world != null ? world.getEntity(silverfishId) : null;
        if (fish != null) {
            fish.remove();
        }
        puzzleSilverfish.remove(silverfishId);
    }

    private record BlockPos(int x, int y, int z) {}

    private static final class PuzzleSilverfishState {
        private final String worldName;
        private final UUID tntStandId;
        private final Set<BlockPos> triggerBlocks;
        private final Set<BlockPos> removalBlocks;
        private BukkitRunnable moveTask;
        private boolean moving;

        private PuzzleSilverfishState(String worldName, UUID tntStandId, Set<BlockPos> triggerBlocks, Set<BlockPos> removalBlocks) {
            this.worldName = worldName;
            this.tntStandId = tntStandId;
            this.triggerBlocks = triggerBlocks;
            this.removalBlocks = removalBlocks;
        }
    }
}
