package maple.underground_caves.dungeon;

import maple.underground_caves.Test_dungeons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Switch;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("ALL")
public final class DungeonSecrets implements Listener {
    enum SecretType {
        CHEST,
        DROP,
        ESSENCE,
        BAT,
        CRACKED_BRICKS,
        TOMB,
        REDSTONE,
        STICKY_BLOCK
    }

    private static boolean countsAsSecret(SecretType type) {
        return type == SecretType.CHEST
                || type == SecretType.DROP
                || type == SecretType.ESSENCE
                || type == SecretType.BAT;
    }

    private static boolean blessingOnlyRoomTemplate(String templateName) {
        if (templateName == null) return false;
        String t = templateName.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return false;
        return t.startsWith("trap_room") || t.startsWith("puzzle_room");
    }

    private record SecretDef(SecretType type, int x, int y, int z, int x2, int y2, int z2) {}

    private record EditSession(String templateName, String worldName, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, List<SecretDef> extraDefs, ItemStack[] savedContents, int savedHeldSlot) {}

    private record SecretInstance(long id, SecretType type, int bx, int by, int bz, int bx2, int by2, int bz2, double cx, double cy, double cz) {}

    private record RedstoneDoorAction(int x, int y, int z) {}

    private enum ChestRewardType {
        ITEM,
        BLESSING
    }

    private record ItemChestHolder(String worldName, long secretId, int x, int y, int z) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final int PROXIMITY_RADIUS_BLOCKS = 3;
    private static final double PROXIMITY_RADIUS_SQ = PROXIMITY_RADIUS_BLOCKS * PROXIMITY_RADIUS_BLOCKS;
    private static final int BAT_SPAWN_RADIUS_BLOCKS = 6;
    private static final double BAT_SPAWN_RADIUS_SQ = BAT_SPAWN_RADIUS_BLOCKS * BAT_SPAWN_RADIUS_BLOCKS;
    private static final int BAT_MAX_RANGE_BLOCKS = 5;
    private static final double BAT_MAX_RANGE_SQ = BAT_MAX_RANGE_BLOCKS * BAT_MAX_RANGE_BLOCKS;
    private static final Set<Material> STICKY_TAG_MATERIALS = EnumSet.of(Material.SLIME_BLOCK, Material.HONEY_BLOCK);
    private static final int STICKY_MAX_CLEAR_BLOCKS = 20000;

    private static final String TEXTURE_BASE = "http://textures.minecraft.net/texture/";
    private static final String ESSENCE_TEXTURE = "8764060b818e4a6706713c851df52affb700837019cbd77126ea154e70cd671c";
    private static final String BLESSING_TEXTURE = "e93e2068617872c542ecda1d27df4ece91c699907bf327c4ddb85309412d3939";

    private static volatile boolean loggedCreateProfileMissing;
    private static volatile boolean loggedProfilePropertyMissing;
    private static volatile boolean loggedCreateProfileNull;

    private final Test_dungeons plugin;
    private final NamespacedKey markerKey;
    private final NamespacedKey typeKey;
    private final NamespacedKey tombCornerKey;
    private final NamespacedKey specialTntKey;
    private final NamespacedKey redstoneLeverKey;
    private final NamespacedKey redstoneTargetKey;
    private final NamespacedKey fallingDoorKey;
    private final NamespacedKey markerBlockXKey;
    private final NamespacedKey markerBlockYKey;
    private final NamespacedKey markerBlockZKey;
    private final Map<UUID, EditSession> editSessions = new HashMap<>();
    private final Map<UUID, int[]> pendingRedstoneLever = new HashMap<>();
    private final Map<String, RunState> runsByWorld = new HashMap<>();
    private final Map<UUID, Long> lastStickyFloorByPlayer = new HashMap<>();

    public DungeonSecrets(Test_dungeons plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "dungeon_secret_marker");
        this.typeKey = new NamespacedKey(plugin, "dungeon_secret_type");
        this.tombCornerKey = new NamespacedKey(plugin, "dungeon_secret_tomb_corner");
        this.specialTntKey = new NamespacedKey(plugin, "dungeon_special_tnt");
        this.redstoneLeverKey = new NamespacedKey(plugin, "dungeon_secret_redstone_lever");
        this.redstoneTargetKey = new NamespacedKey(plugin, "dungeon_secret_redstone_target");
        this.fallingDoorKey = new NamespacedKey(plugin, "dungeon_falling_door");
        this.markerBlockXKey = new NamespacedKey(plugin, "dungeon_secret_marker_x");
        this.markerBlockYKey = new NamespacedKey(plugin, "dungeon_secret_marker_y");
        this.markerBlockZKey = new NamespacedKey(plugin, "dungeon_secret_marker_z");
        new BukkitRunnable() {
            @Override
            public void run() {
                tickHud();
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    private void redstoneLog(String message) {
        // redstone_debug removed
    }

    private static BlockFace rotateFacing(BlockFace facing, StructureTemplate.Rotation rotation) {
        if (facing == null || rotation == null) return facing;
        return switch (rotation) {
            case R0 -> facing;
            case R90 -> switch (facing) {
                case NORTH -> BlockFace.EAST;
                case EAST -> BlockFace.SOUTH;
                case SOUTH -> BlockFace.WEST;
                case WEST -> BlockFace.NORTH;
                default -> facing;
            };
            case R180 -> switch (facing) {
                case NORTH -> BlockFace.SOUTH;
                case EAST -> BlockFace.WEST;
                case SOUTH -> BlockFace.NORTH;
                case WEST -> BlockFace.EAST;
                default -> facing;
            };
            case R270 -> switch (facing) {
                case NORTH -> BlockFace.WEST;
                case EAST -> BlockFace.NORTH;
                case SOUTH -> BlockFace.EAST;
                case WEST -> BlockFace.SOUTH;
                default -> facing;
            };
        };
    }

    /**
     * 在 (x,y,z) 放置拉杆：优先检测该格六邻是否有实体方块，有则贴附到对应面，不额外生成垫块。
     * 若六面皆无可用支撑，则回退为旧逻辑（下方空则垫 OAK_WOOD，再按地板拉杆放置）。
     */
    private void placeLeverOnBelowFace(World world, int x, int y, int z, StructureTemplate.Rotation rotation) {
        if (world == null) return;
        Block b = world.getBlockAt(x, y, z);
        if (b == null) return;
        if (!b.getType().isAir()) return;

        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        // 优先顺序：下、上、北南东西（与常见墙体/地面贴合习惯一致）
        BlockFace[] neighborOrder = {
                BlockFace.DOWN, BlockFace.UP,
                BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
        };
        for (BlockFace towardSolid : neighborOrder) {
            Block neighbor = b.getRelative(towardSolid);
            if (neighbor == null) continue;
            int ny = neighbor.getY();
            if (ny < minY || ny > maxY) continue;
            if (!neighbor.getType().isSolid()) continue;
            placeLeverOnNeighborFace(b, towardSolid, rotation, world.getName(), x, y, z, neighbor.getType().name());
            return;
        }

        // 六面无支撑：沿用旧逻辑（垫下方橡木 + 地板拉杆）
        if (y - 1 < minY) return;
        Block below = world.getBlockAt(x, y - 1, z);
        if (below != null && below.getType().isAir()) {
            below.setType(Material.OAK_WOOD, false);
        }
        below = world.getBlockAt(x, y - 1, z);
        if (below == null || !below.getType().isSolid()) {
            redstoneLog("placeLever fail world=" + world.getName()
                    + " lever=" + x + "," + y + "," + z
                    + " reason=no_solid_neighbor_or_below below=" + (below != null ? below.getType().name() : "null"));
            return;
        }
        Switch data = (Switch) Bukkit.createBlockData(Material.LEVER);
        data.setFace(Switch.Face.FLOOR);
        data.setFacing(rotateFacing(BlockFace.NORTH, rotation));
        b.setBlockData(data, false);
        redstoneLog("placeLever ok world=" + world.getName()
                + " lever=" + x + "," + y + "," + z
                + " face=FLOOR (fallback_oak)"
                + " facing=" + data.getFacing().name()
                + " below=" + below.getType().name());
    }

    /** towardSolid：从拉杆格指向实体方块的方向（拉杆贴在该侧的墙上/顶/底）。 */
    private void placeLeverOnNeighborFace(Block leverBlock, BlockFace towardSolid, StructureTemplate.Rotation rotation,
                                         String worldName, int x, int y, int z, String supportType) {
        Switch data = (Switch) Bukkit.createBlockData(Material.LEVER);
        if (towardSolid == BlockFace.DOWN) {
            data.setFace(Switch.Face.FLOOR);
            data.setFacing(rotateFacing(BlockFace.NORTH, rotation));
        } else if (towardSolid == BlockFace.UP) {
            data.setFace(Switch.Face.CEILING);
            data.setFacing(rotateFacing(BlockFace.NORTH, rotation));
        } else {
            data.setFace(Switch.Face.WALL);
            data.setFacing(towardSolid);
        }
        leverBlock.setBlockData(data, false);
        redstoneLog("placeLever ok world=" + worldName
                + " lever=" + x + "," + y + "," + z
                + " attach=" + towardSolid.name()
                + " face=" + data.getFace().name()
                + " facing=" + data.getFacing().name()
                + " support=" + supportType);
    }

    void beginRun(Test_dungeons.DungeonSnapshot snapshot) {
        if (snapshot == null) return;
        if (snapshot.worldName() == null) return;
        runsByWorld.put(snapshot.worldName(), new RunState(snapshot.worldName(), snapshot.width(), snapshot.height()));
        if (plugin != null && plugin.getDungeonBlessingManager() != null) {
            plugin.getDungeonBlessingManager().resetWorld(snapshot.worldName());
        }
    }

    /** 評分用：已發現的可計分秘密數（與 HUD 統計一致） */
    public int getScoringSecretsFound(String worldName) {
        if (worldName == null) return 0;
        RunState run = runsByWorld.get(worldName);
        return run == null ? 0 : run.aggregateFoundScoringSecrets();
    }

    /** 評分用：可計分秘密總數 */
    public int getScoringSecretsTotal(String worldName) {
        if (worldName == null) return 0;
        RunState run = runsByWorld.get(worldName);
        return run == null ? 0 : run.aggregateTotalScoringSecrets();
    }

    public boolean isRoomSecretsCompleted(String worldName, int cellX, int cellY) {
        if (worldName == null) return false;
        RunState run = runsByWorld.get(worldName);
        if (run == null) return false;
        return run.isRoomSecretsCompleted(cellX, cellY);
    }

    void registerRoomGroup(String worldName, String groupKey, String templateName, int cellX, int cellY) {
        if (worldName == null || groupKey == null || templateName == null) return;
        RunState run = runsByWorld.get(worldName);
        if (run == null) return;
        run.cellToGroup.put(packCell(cellX, cellY), groupKey);
        run.groups.computeIfAbsent(groupKey, k -> new GroupState(groupKey, templateName)).cells.add(packCell(cellX, cellY));
    }

    void beginTemplateTestRun(World world, Location origin, int sizeX, int sizeZ, String groupKey, String templateName) {
        if (world == null || origin == null) return;
        if (groupKey == null || groupKey.isBlank()) return;
        if (templateName == null || templateName.isBlank()) return;

        RunState run = runsByWorld.computeIfAbsent(world.getName(), wn -> new RunState(wn, 1, 1));
        int scale = Math.max(1, Math.max(sizeX, sizeZ));
        run.enableTestMode(origin.getBlockX(), origin.getBlockZ(), scale);
        run.cellToGroup.put(packCell(0, 0), groupKey);
        run.groups.computeIfAbsent(groupKey, k -> new GroupState(groupKey, templateName)).cells.add(packCell(0, 0));
    }

    void spawnGroupSecrets(World world, Location baseOrigin, StructureTemplate.Rotation rotation, int sizeX, int sizeZ, String groupKey, String templateName) {
        if (world == null || baseOrigin == null || rotation == null) return;
        if (groupKey == null || templateName == null) return;
        RunState run = runsByWorld.get(world.getName());
        if (run == null) return;
        GroupState group = run.groups.get(groupKey);
        if (group == null) return;
        group.setPlacement(baseOrigin.getBlockX(), baseOrigin.getBlockY(), baseOrigin.getBlockZ(), rotation, sizeX, sizeZ);

        List<SecretDef> defs = loadSecrets(templateName);
        List<SecretDef> redstoneLinks = loadRedstoneLinks(templateName);
        if ((defs == null || defs.isEmpty()) && (redstoneLinks == null || redstoneLinks.isEmpty())) return;
        Set<Long> placedLevers = new HashSet<>();
        redstoneLog("spawnGroupSecrets begin"
                + " world=" + world.getName()
                + " template=" + templateName
                + " groupKey=" + groupKey
                + " origin=" + baseOrigin.getBlockX() + "," + baseOrigin.getBlockY() + "," + baseOrigin.getBlockZ()
                + " rotation=" + rotation.name()
                + " size=" + sizeX + "x" + sizeZ
                + " defs=" + (defs != null ? defs.size() : 0)
                + " redstoneLinks=" + (redstoneLinks != null ? redstoneLinks.size() : 0));

        if (defs != null) for (SecretDef def : defs) {
            if (def.type() == SecretType.TOMB) {
                int x1 = def.x();
                int y1 = def.y();
                int z1 = def.z();
                int x2 = def.x2();
                int y2 = def.y2();
                int z2 = def.z2();
                if (x2 < 0 || y2 < 0 || z2 < 0) continue;

                int[] box = rotateBoxLocalXZ(x1, z1, x2, z2, rotation, sizeX, sizeZ);
                int minWX = baseOrigin.getBlockX() + box[0];
                int minWZ = baseOrigin.getBlockZ() + box[1];
                int maxWX = baseOrigin.getBlockX() + box[2];
                int maxWZ = baseOrigin.getBlockZ() + box[3];
                int minWY = baseOrigin.getBlockY() + Math.min(y1, y2);
                int maxWY = baseOrigin.getBlockY() + Math.max(y1, y2);
                long id = packSecretId(minWX, minWY, minWZ, def.type());
                if (group.secrets.containsKey(id)) continue;

                double cx = (minWX + maxWX + 1) * 0.5;
                double cy = minWY + 0.5;
                double cz = (minWZ + maxWZ + 1) * 0.5;
                SecretInstance instance = new SecretInstance(id, def.type(), minWX, minWY, minWZ, maxWX, maxWY, maxWZ, cx, cy, cz);
                group.secrets.put(id, instance);
                run.secretToGroup.put(id, groupKey);
                run.tombBounds.put(id, new int[]{minWX, minWY, minWZ, maxWX, maxWY, maxWZ});
                continue;
            }

            if (def.type() == SecretType.STICKY_BLOCK) {
                int x2 = def.x2();
                int y2 = def.y2();
                int z2 = def.z2();
                if (x2 < 0 || y2 < 0 || z2 < 0) {
                    int[] rot = rotateLocalXZ(def.x(), def.z(), rotation, sizeX, sizeZ);
                    int wx = baseOrigin.getBlockX() + rot[0];
                    int wy = baseOrigin.getBlockY() + def.y();
                    int wz = baseOrigin.getBlockZ() + rot[1];
                    int triggerY = wy;
                    if (world.getBlockAt(wx, triggerY, wz).getType().isAir()) {
                        int yDown = triggerY - 1;
                        int yUp = triggerY + 1;
                        if (yDown >= world.getMinHeight() && !world.getBlockAt(wx, yDown, wz).getType().isAir()) {
                            triggerY = yDown;
                        } else if (yUp < world.getMaxHeight() && !world.getBlockAt(wx, yUp, wz).getType().isAir()) {
                            triggerY = yUp;
                        }
                    }

                    long id = packSecretId(wx, triggerY, wz, def.type());
                    if (group.secrets.containsKey(id)) continue;

                    double cx = wx + 0.5;
                    double cy = triggerY + 0.1;
                    double cz = wz + 0.5;
                    SecretInstance instance = new SecretInstance(id, def.type(), wx, triggerY, wz, wx, triggerY, wz, cx, cy, cz);
                    group.secrets.put(id, instance);
                    run.secretToGroup.put(id, groupKey);
                    run.stickyTriggerBlocks.put(packBlock(wx, triggerY, wz), id);
                    continue;
                }

                int x1 = def.x();
                int y1 = def.y();
                int z1 = def.z();
                int[] box = rotateBoxLocalXZ(x1, z1, x2, z2, rotation, sizeX, sizeZ);
                int minWX = baseOrigin.getBlockX() + box[0];
                int minWZ = baseOrigin.getBlockZ() + box[1];
                int maxWX = baseOrigin.getBlockX() + box[2];
                int maxWZ = baseOrigin.getBlockZ() + box[3];
                int minWY = baseOrigin.getBlockY() + Math.min(y1, y2);
                int maxWY = baseOrigin.getBlockY() + Math.max(y1, y2);
                long id = packSecretId(minWX, minWY, minWZ, def.type());
                if (group.secrets.containsKey(id)) continue;

                double cx = (minWX + maxWX + 1) * 0.5;
                double cy = minWY + 0.5;
                double cz = (minWZ + maxWZ + 1) * 0.5;
                SecretInstance instance = new SecretInstance(id, def.type(), minWX, minWY, minWZ, maxWX, maxWY, maxWZ, cx, cy, cz);
                group.secrets.put(id, instance);
                run.secretToGroup.put(id, groupKey);
                run.stickyBounds.put(id, new int[]{minWX, minWY, minWZ, maxWX, maxWY, maxWZ});
                continue;
            }

            if (def.type() == SecretType.REDSTONE) {
                int x2 = def.x2();
                int y2 = def.y2();
                int z2 = def.z2();
                if (x2 < 0 || y2 < 0 || z2 < 0) continue;

                int[] leverRot = rotateLocalXZ(def.x(), def.z(), rotation, sizeX, sizeZ);
                int leverWX = baseOrigin.getBlockX() + leverRot[0];
                int leverWY = baseOrigin.getBlockY() + def.y();
                int leverWZ = baseOrigin.getBlockZ() + leverRot[1];

                int[] targetRot = rotateLocalXZ(x2, z2, rotation, sizeX, sizeZ);
                int targetWX = baseOrigin.getBlockX() + targetRot[0];
                int targetWY = baseOrigin.getBlockY() + y2;
                int targetWZ = baseOrigin.getBlockZ() + targetRot[1];

                long leverKey = packBlock(leverWX, leverWY, leverWZ);
                if (placedLevers.add(leverKey)) {
                Block b = world.getBlockAt(leverWX, leverWY, leverWZ);
                Material before = b.getType();
                if (before.isAir()) {
                    placeLeverOnBelowFace(world, leverWX, leverWY, leverWZ, rotation);
                } else {
                    redstoneLog("spawnGroupSecrets leverNotPlaced world=" + world.getName()
                            + " lever=" + leverWX + "," + leverWY + "," + leverWZ
                            + " occupiedBy=" + before.name());
                }
                }
                Block targetBlock = world.getBlockAt(targetWX, targetWY, targetWZ);
                if (targetBlock != null && targetBlock.getType().isAir()) {
                    targetBlock.setType(Material.OAK_WOOD, false);
                }
                run.redstoneDoorsByLever.computeIfAbsent(leverKey, k -> new ArrayList<>())
                        .add(new RedstoneDoorAction(targetWX, targetWY, targetWZ));
                redstoneLog("spawnGroupSecrets link(defs) lever=" + leverWX + "," + leverWY + "," + leverWZ
                        + " -> target=" + targetWX + "," + targetWY + "," + targetWZ);
                continue;
            }

            int[] rot = rotateLocalXZ(def.x(), def.z(), rotation, sizeX, sizeZ);
            int wx = baseOrigin.getBlockX() + rot[0];
            int baseWy = baseOrigin.getBlockY() + def.y();
            int wz = baseOrigin.getBlockZ() + rot[1];
            boolean isTrapRoom01 = "trap_room_01".equalsIgnoreCase(templateName.trim());
            boolean isTrapChest = isTrapRoom01 && def.type() == SecretType.CHEST && def.x() == 27 && def.z() == 23;
            int wy = (def.type() == SecretType.CHEST || def.type() == SecretType.ESSENCE)
                    ? pickPlaceY(world, wx, baseWy, wz)
                    : baseWy;
            long id = packSecretId(wx, wy, wz, def.type());
            if (group.secrets.containsKey(id)) continue;

            double cx = wx + 0.5;
            double cy = wy + 0.1;
            double cz = wz + 0.5;
            SecretInstance instance = new SecretInstance(id, def.type(), wx, wy, wz, wx, wy, wz, cx, cy, cz);
            group.secrets.put(id, instance);
            run.secretToGroup.put(id, groupKey);

            if (def.type() == SecretType.CHEST) {
                if (wy != baseWy) {
                    clearVerticalDuplicates(world, wx, baseWy, wy, wz, true);
                }
                world.getBlockAt(wx, wy, wz).setType(isTrapChest ? Material.TRAPPED_CHEST : Material.CHEST, false);
                run.chestBlocks.put(packBlock(wx, wy, wz), id);
            } else if (def.type() == SecretType.ESSENCE) {
                if (wy != baseWy) {
                    clearVerticalDuplicates(world, wx, baseWy, wy, wz, false);
                }
                world.getBlockAt(wx, wy, wz).setType(Material.PLAYER_HEAD, false);
                applySkullTexture(world.getBlockAt(wx, wy, wz), ESSENCE_TEXTURE);
                run.essenceBlocks.put(packBlock(wx, wy, wz), id);
            } else if (def.type() == SecretType.CRACKED_BRICKS) {
                world.getBlockAt(wx, wy, wz).setType(Material.CRACKED_STONE_BRICKS, false);
                run.breakableBlocks.put(packBlock(wx, wy, wz), id);
            } else {
                spawnInvisibleMarker(world, cx, wy, cz, def.type());
            }
        }

        if (redstoneLinks != null) for (SecretDef def : redstoneLinks) {
            if (def == null || def.type() != SecretType.REDSTONE) continue;
            int x2 = def.x2();
            int y2 = def.y2();
            int z2 = def.z2();
            if (x2 < 0 || y2 < 0 || z2 < 0) continue;

            int[] leverRot = rotateLocalXZ(def.x(), def.z(), rotation, sizeX, sizeZ);
            int leverWX = baseOrigin.getBlockX() + leverRot[0];
            int leverWY = baseOrigin.getBlockY() + def.y();
            int leverWZ = baseOrigin.getBlockZ() + leverRot[1];

            int[] targetRot = rotateLocalXZ(x2, z2, rotation, sizeX, sizeZ);
            int targetWX = baseOrigin.getBlockX() + targetRot[0];
            int targetWY = baseOrigin.getBlockY() + y2;
            int targetWZ = baseOrigin.getBlockZ() + targetRot[1];

            long leverKey = packBlock(leverWX, leverWY, leverWZ);
            if (placedLevers.add(leverKey)) {
                Block b = world.getBlockAt(leverWX, leverWY, leverWZ);
                Material before = b.getType();
                if (before.isAir()) {
                    placeLeverOnBelowFace(world, leverWX, leverWY, leverWZ, rotation);
                } else {
                    redstoneLog("spawnGroupSecrets leverNotPlaced world=" + world.getName()
                            + " lever=" + leverWX + "," + leverWY + "," + leverWZ
                            + " occupiedBy=" + before.name());
                }
            }
            Block targetBlock = world.getBlockAt(targetWX, targetWY, targetWZ);
            if (targetBlock != null && targetBlock.getType().isAir()) {
                targetBlock.setType(Material.OAK_WOOD, false);
            }
            run.redstoneDoorsByLever.computeIfAbsent(leverKey, k -> new ArrayList<>())
                    .add(new RedstoneDoorAction(targetWX, targetWY, targetWZ));
            redstoneLog("spawnGroupSecrets link(redstoneLinks) lever=" + leverWX + "," + leverWY + "," + leverWZ
                    + " -> target=" + targetWX + "," + targetWY + "," + targetWZ);
        }
        redstoneLog("spawnGroupSecrets end world=" + world.getName()
                + " template=" + templateName
                + " levers=" + placedLevers.size());
    }

    boolean startEdit(Player player, String templateName, Location pos1, Location pos2) {
        if (player == null || templateName == null || templateName.isBlank()) return false;
        if (pos1 == null || pos2 == null || pos1.getWorld() == null || !Objects.equals(pos1.getWorld(), pos2.getWorld())) return false;
        World world = pos1.getWorld();
        if (world == null) return false;

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        exitEdit(player);
        pendingRedstoneLever.remove(player.getUniqueId());

        PlayerInventory inv = player.getInventory();
        ItemStack[] saved = inv.getContents();
        int held = inv.getHeldItemSlot();
        inv.clear();
        inv.setItem(0, toolItem(Material.CHEST, "生成箱子"));
        inv.setItem(1, toolItem(Material.DIAMOND, "生成掉落物"));
        inv.setItem(2, toolItem(Material.AMETHYST_SHARD, "生成精粹"));
        inv.setItem(3, toolItem(Material.REDSTONE, "红石系统"));
        inv.setItem(4, toolItem(Material.BAT_SPAWN_EGG, "蝙蝠"));
        inv.setItem(5, toolItem(Material.CRACKED_STONE_BRICKS, "裂纹石砖"));
        inv.setItem(6, toolItem(Material.SKELETON_SKULL, "坟墓(用指令选区)"));
        inv.setItem(7, toolItem(Material.SLIME_BLOCK, "粘连方块"));
        inv.setHeldItemSlot(0);

        String normalized = templateName.toLowerCase(Locale.ROOT);
        List<SecretDef> extra = new ArrayList<>();
        editSessions.put(player.getUniqueId(), new EditSession(normalized, world.getName(), minX, minY, minZ, maxX, maxY, maxZ, extra, saved, held));

        clearMarkersInBox(world, minX, minY, minZ, maxX, maxY, maxZ);
        List<SecretDef> existing = loadSecrets(normalized);
        for (SecretDef def : existing) {
            if (def.type() == SecretType.TOMB) {
                extra.add(def);
                int wx1 = minX + def.x();
                int wy1 = minY + def.y();
                int wz1 = minZ + def.z();
                int wx2 = minX + def.x2();
                int wy2 = minY + def.y2();
                int wz2 = minZ + def.z2();
                spawnTombCornerMarker(world, wx1 + 0.5, wy1, wz1 + 0.5, "坟墓A");
                spawnTombCornerMarker(world, wx2 + 0.5, wy2, wz2 + 0.5, "坟墓B");
                continue;
            }
            if (def.type() == SecretType.STICKY_BLOCK) {
                if (def.x2() >= 0 && def.y2() >= 0 && def.z2() >= 0) {
                    extra.add(def);
                    int wx1 = minX + def.x();
                    int wy1 = minY + def.y();
                    int wz1 = minZ + def.z();
                    int wx2 = minX + def.x2();
                    int wy2 = minY + def.y2();
                    int wz2 = minZ + def.z2();
                    spawnStickyCornerMarker(world, wx1 + 0.5, wy1, wz1 + 0.5, "粘连A");
                    spawnStickyCornerMarker(world, wx2 + 0.5, wy2, wz2 + 0.5, "粘连B");
                } else {
                    int wx = minX + def.x();
                    int wy = minY + def.y();
                    int wz = minZ + def.z();
                    spawnEditMarker(world, wx + 0.5, wy, wz + 0.5, def.type(), def.type().name());
                }
                continue;
            }
            if (def.type() == SecretType.REDSTONE) {
                int x2 = def.x2();
                int y2 = def.y2();
                int z2 = def.z2();
                if (x2 < 0 || y2 < 0 || z2 < 0) continue;
                int leverX = minX + def.x();
                int leverY = minY + def.y();
                int leverZ = minZ + def.z();
                int targetX = minX + x2;
                int targetY = minY + y2;
                int targetZ = minZ + z2;
                spawnEditRedstoneTargetMarker(world, targetX, targetY, targetZ, leverX, leverY, leverZ);
                continue;
            }
            int wx = minX + def.x();
            int wy = minY + def.y();
            int wz = minZ + def.z();
            spawnEditMarker(world, wx + 0.5, wy, wz + 0.5, def.type(), def.type().name());
        }

        List<SecretDef> existingRedstone = loadRedstoneLinks(normalized);
        if (existingRedstone != null) for (SecretDef def : existingRedstone) {
            if (def == null || def.type() != SecretType.REDSTONE) continue;
            int x2 = def.x2();
            int y2 = def.y2();
            int z2 = def.z2();
            if (x2 < 0 || y2 < 0 || z2 < 0) continue;
            int leverX = minX + def.x();
            int leverY = minY + def.y();
            int leverZ = minZ + def.z();
            int targetX = minX + x2;
            int targetY = minY + y2;
            int targetZ = minZ + z2;
            spawnEditRedstoneTargetMarker(world, targetX, targetY, targetZ, leverX, leverY, leverZ);
        }
        return true;
    }

    boolean addTombToEdit(Player player, Location p1, Location p2) {
        if (player == null || p1 == null || p2 == null) return false;
        EditSession session = editSessions.get(player.getUniqueId());
        if (session == null) return false;
        if (!Objects.equals(p1.getWorld(), p2.getWorld())) return false;
        if (!Objects.equals(session.worldName(), p1.getWorld() != null ? p1.getWorld().getName() : null)) return false;

        int minX = Math.min(p1.getBlockX(), p2.getBlockX());
        int minY = Math.min(p1.getBlockY(), p2.getBlockY());
        int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
        int maxX = Math.max(p1.getBlockX(), p2.getBlockX());
        int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
        int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());

        if (minX < session.minX() || maxX > session.maxX()) return false;
        if (minY < session.minY() || maxY > session.maxY()) return false;
        if (minZ < session.minZ() || maxZ > session.maxZ()) return false;

        SecretDef def = new SecretDef(SecretType.TOMB,
                minX - session.minX(), minY - session.minY(), minZ - session.minZ(),
                maxX - session.minX(), maxY - session.minY(), maxZ - session.minZ()
        );
        List<SecretDef> next = new ArrayList<>(session.extraDefs());
        next.add(def);
        editSessions.put(player.getUniqueId(), new EditSession(session.templateName(), session.worldName(),
                session.minX(), session.minY(), session.minZ(),
                session.maxX(), session.maxY(), session.maxZ(),
                next,
                session.savedContents(), session.savedHeldSlot()
        ));

        World world = p1.getWorld();
        if (world != null) {
            spawnTombCornerMarker(world, minX + 0.5, minY, minZ + 0.5, "坟墓A");
            spawnTombCornerMarker(world, maxX + 0.5, maxY, maxZ + 0.5, "坟墓B");
        }
        return true;
    }

    boolean addStickyToEdit(Player player, Location p1, Location p2) {
        if (player == null || p1 == null || p2 == null) return false;
        EditSession session = editSessions.get(player.getUniqueId());
        if (session == null) return false;
        if (!Objects.equals(p1.getWorld(), p2.getWorld())) return false;
        if (!Objects.equals(session.worldName(), p1.getWorld() != null ? p1.getWorld().getName() : null)) return false;

        int minX = Math.min(p1.getBlockX(), p2.getBlockX());
        int minY = Math.min(p1.getBlockY(), p2.getBlockY());
        int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
        int maxX = Math.max(p1.getBlockX(), p2.getBlockX());
        int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
        int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());

        if (minX < session.minX() || maxX > session.maxX()) return false;
        if (minY < session.minY() || maxY > session.maxY()) return false;
        if (minZ < session.minZ() || maxZ > session.maxZ()) return false;

        SecretDef def = new SecretDef(SecretType.STICKY_BLOCK,
                minX - session.minX(), minY - session.minY(), minZ - session.minZ(),
                maxX - session.minX(), maxY - session.minY(), maxZ - session.minZ()
        );
        List<SecretDef> next = new ArrayList<>(session.extraDefs());
        next.add(def);
        editSessions.put(player.getUniqueId(), new EditSession(session.templateName(), session.worldName(),
                session.minX(), session.minY(), session.minZ(),
                session.maxX(), session.maxY(), session.maxZ(),
                next,
                session.savedContents(), session.savedHeldSlot()
        ));

        World world = p1.getWorld();
        if (world != null) {
            spawnStickyCornerMarker(world, minX + 0.5, minY, minZ + 0.5, "粘连A");
            spawnStickyCornerMarker(world, maxX + 0.5, maxY, maxZ + 0.5, "粘连B");
        }
        return true;
    }

    boolean saveEdit(Player player) {
        if (player == null) return false;
        EditSession session = editSessions.get(player.getUniqueId());
        if (session == null) return false;
        World world = Bukkit.getWorld(session.worldName());
        if (world == null) return false;

        List<SecretDef> defs = new ArrayList<>();
        for (Entity e : world.getEntitiesByClass(ArmorStand.class)) {
            if (!(e instanceof ArmorStand as)) continue;
            if (!isMarker(as)) continue;
            if (as.getPersistentDataContainer().has(tombCornerKey, PersistentDataType.BYTE)) continue;
            Location l = as.getLocation();
            Integer mx = as.getPersistentDataContainer().get(markerBlockXKey, PersistentDataType.INTEGER);
            Integer my = as.getPersistentDataContainer().get(markerBlockYKey, PersistentDataType.INTEGER);
            Integer mz = as.getPersistentDataContainer().get(markerBlockZKey, PersistentDataType.INTEGER);
            int bx = mx != null ? mx.intValue() : l.getBlockX();
            int by = my != null ? my.intValue() : l.getBlockY();
            int bz = mz != null ? mz.intValue() : l.getBlockZ();
            if (bx < session.minX() || bx > session.maxX()) continue;
            if (by < session.minY() || by > session.maxY()) continue;
            if (bz < session.minZ() || bz > session.maxZ()) continue;
            SecretType type = getMarkerType(as);
            if (type == null) continue;
            if (type == SecretType.TOMB) continue;
            if (type == SecretType.REDSTONE) continue;
            defs.add(new SecretDef(type, bx - session.minX(), by - session.minY(), bz - session.minZ(), -1, -1, -1));
        }
        defs.addAll(session.extraDefs());

        saveSecrets(session.templateName(), defs);
        exitEdit(player);
        return true;
    }

    boolean saveRedstoneEdit(Player player) {
        if (player == null) return false;
        EditSession session = editSessions.get(player.getUniqueId());
        if (session == null) {
            redstoneLog("saveRedstoneEdit fail player=" + player.getName() + " reason=no_session");
            return false;
        }
        World world = Bukkit.getWorld(session.worldName());
        if (world == null) {
            redstoneLog("saveRedstoneEdit fail player=" + player.getName()
                    + " template=" + session.templateName()
                    + " reason=world_missing worldName=" + session.worldName());
            return false;
        }

        List<SecretDef> links = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();
        Set<String> levers = new HashSet<>();
        int scannedEntities = 0;
        int markerEntities = 0;
        int redstoneMarkerEntities = 0;
        int skippedMissingLever = 0;
        int skippedOutOfBounds = 0;
        int skippedDeduped = 0;

        for (Entity e : world.getEntitiesByClass(ArmorStand.class)) {
            scannedEntities++;
            if (!(e instanceof ArmorStand as)) continue;
            if (!isMarker(as)) continue;
            markerEntities++;
            if (as.getPersistentDataContainer().has(tombCornerKey, PersistentDataType.BYTE)) continue;
            if (getMarkerType(as) != SecretType.REDSTONE) continue;
            redstoneMarkerEntities++;

            Location l = as.getLocation();
            Integer mx = as.getPersistentDataContainer().get(markerBlockXKey, PersistentDataType.INTEGER);
            Integer my = as.getPersistentDataContainer().get(markerBlockYKey, PersistentDataType.INTEGER);
            Integer mz = as.getPersistentDataContainer().get(markerBlockZKey, PersistentDataType.INTEGER);
            int targetX = mx != null ? mx.intValue() : l.getBlockX();
            int targetY = my != null ? my.intValue() : l.getBlockY();
            int targetZ = mz != null ? mz.intValue() : l.getBlockZ();

            int leverX;
            int leverY;
            int leverZ;
            int[] lever = getRedstoneLever(as);
            if (lever != null) {
                leverX = lever[0];
                leverY = lever[1];
                leverZ = lever[2];
            } else {
                int[] oldTarget = getRedstoneTarget(as);
                if (oldTarget == null) {
                    skippedMissingLever++;
                    continue;
                }
                leverX = targetX;
                leverY = targetY;
                leverZ = targetZ;
                targetX = oldTarget[0];
                targetY = oldTarget[1];
                targetZ = oldTarget[2];
            }

            if (leverX < session.minX() || leverX > session.maxX()
                    || leverY < session.minY() || leverY > session.maxY()
                    || leverZ < session.minZ() || leverZ > session.maxZ()
                    || targetX < session.minX() || targetX > session.maxX()
                    || targetY < session.minY() || targetY > session.maxY()
                    || targetZ < session.minZ() || targetZ > session.maxZ()) {
                skippedOutOfBounds++;
                continue;
            }

            String key = leverX + "," + leverY + "," + leverZ + "->" + targetX + "," + targetY + "," + targetZ;
            if (!dedupe.add(key)) {
                skippedDeduped++;
                continue;
            }
            levers.add(leverX + "," + leverY + "," + leverZ);
            int leverRelX = leverX - session.minX();
            int leverRelY = leverY - session.minY();
            int leverRelZ = leverZ - session.minZ();
            int targetRelX = targetX - session.minX();
            int targetRelY = targetY - session.minY();
            int targetRelZ = targetZ - session.minZ();
            links.add(new SecretDef(SecretType.REDSTONE,
                    leverRelX, leverRelY, leverRelZ,
                    targetRelX, targetRelY, targetRelZ
            ));
            redstoneLog("saveRedstoneEdit link"
                    + " leverAbs=" + leverX + "," + leverY + "," + leverZ
                    + " targetAbs=" + targetX + "," + targetY + "," + targetZ
                    + " leverRel=" + leverRelX + "," + leverRelY + "," + leverRelZ
                    + " targetRel=" + targetRelX + "," + targetRelY + "," + targetRelZ);
        }

        redstoneLog("saveRedstoneEdit ok player=" + player.getName()
                + " template=" + session.templateName()
                + " world=" + world.getName()
                + " selectionMin=" + session.minX() + "," + session.minY() + "," + session.minZ()
                + " selectionMax=" + session.maxX() + "," + session.maxY() + "," + session.maxZ()
                + " links=" + links.size()
                + " uniqueLevers=" + levers.size()
                + " scannedEntities=" + scannedEntities
                + " markerEntities=" + markerEntities
                + " redstoneMarkerEntities=" + redstoneMarkerEntities
                + " skippedMissingLever=" + skippedMissingLever
                + " skippedOutOfBounds=" + skippedOutOfBounds
                + " skippedDeduped=" + skippedDeduped);
        saveRedstoneLinks(session.templateName(), links);
        return true;
    }

    boolean stopRedstoneEdit(Player player) {
        if (player == null) return false;
        boolean ok = pendingRedstoneLever.remove(player.getUniqueId()) != null;
        if (ok && plugin.getLogger() != null) {
            plugin.getLogger().info("redstone_stop player=" + player.getName());
        }
        return ok;
    }

    void exitEdit(Player player) {
        if (player == null) return;
        EditSession session = editSessions.remove(player.getUniqueId());
        if (session == null) return;
        pendingRedstoneLever.remove(player.getUniqueId());
        PlayerInventory inv = player.getInventory();
        inv.setContents(session.savedContents());
        inv.setHeldItemSlot(Math.max(0, Math.min(8, session.savedHeldSlot())));
    }

    private void tickHud() {
        Test_dungeons.DungeonSnapshot snapshot = plugin.getLastDungeon();
        String worldName = snapshot != null ? snapshot.worldName() : null;
        RunState run = worldName != null ? runsByWorld.get(worldName) : null;
        boolean useSnapshot = snapshot != null && worldName != null && run != null;

        Set<String> clampedBatWorlds = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p == null || !p.isOnline()) continue;
            if (plugin.getDungeonDeathListener() != null && plugin.getDungeonDeathListener().isDungeonGhost(p.getUniqueId())) {
                continue;
            }
            World w = p.getWorld();
            if (w == null) continue;

            RunState activeRun;
            if (useSnapshot && worldName.equals(w.getName())) {
                activeRun = run;
            } else {
                activeRun = runsByWorld.get(w.getName());
                if (activeRun == null || !activeRun.testMode) continue;
            }

            if (clampedBatWorlds.add(w.getName())) {
                clampSecretBats(w, activeRun);
            }
        }
    }

    private void clampSecretBats(World world, RunState run) {
        if (world == null || run == null) return;

        List<UUID> toRemove = null;
        for (Map.Entry<UUID, Long> e : run.batById.entrySet()) {
            if (e == null) continue;
            UUID uuid = e.getKey();
            if (uuid == null) continue;
            Entity ent = world.getEntity(uuid);
            if (!(ent instanceof Bat bat) || !bat.isValid() || bat.isDead()) {
                if (toRemove == null) toRemove = new ArrayList<>();
                toRemove.add(uuid);
                continue;
            }

            double[] origin = run.batOriginById.get(uuid);
            if (origin == null || origin.length < 3) {
                Location l = bat.getLocation();
                run.batOriginById.put(uuid, new double[]{l.getX(), l.getY(), l.getZ()});
                continue;
            }

            Location l = bat.getLocation();
            double dx = l.getX() - origin[0];
            double dy = l.getY() - origin[1];
            double dz = l.getZ() - origin[2];
            if (dx * dx + dy * dy + dz * dz <= BAT_MAX_RANGE_SQ) continue;

            Location back = findSafeBatSpawn(world, origin[0], origin[1], origin[2]);
            if (back != null) {
                bat.teleport(back);
                bat.setVelocity(new Vector(0.0, 0.0, 0.0));
            }
        }

        if (toRemove == null || toRemove.isEmpty()) return;
        for (UUID uuid : toRemove) {
            Long secretId = run.batById.remove(uuid);
            run.batOriginById.remove(uuid);
            if (secretId != null) {
                UUID current = run.batEntityBySecret.get(secretId);
                if (uuid.equals(current)) run.batEntityBySecret.remove(secretId);
            }
        }
    }

    private static Location findSafeBatSpawn(World world, double x, double y, double z) {
        if (world == null) return null;
        double sy = y;
        for (int i = 0; i < 6; i++) {
            int bx = (int) Math.floor(x);
            int by = (int) Math.floor(sy);
            int bz = (int) Math.floor(z);
            if (by < world.getMinHeight()) return null;
            if (by + 1 >= world.getMaxHeight()) return null;
            if (world.getBlockAt(bx, by, bz).getType().isAir() && world.getBlockAt(bx, by + 1, bz).getType().isAir()) {
                return new Location(world, x, sy, z);
            }
            sy += 1.0;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEditPlace(PlayerInteractEvent event) {
        if (event == null) return;
        Player player = event.getPlayer();
        if (player == null) return;
        EditSession session = editSessions.get(player.getUniqueId());
        if (session == null) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        SecretType type = toolType(event.getItem());
        if (type == null) return;
        if (type == SecretType.TOMB) {
            event.setCancelled(true);
            player.sendMessage(Component.text("坟墓请用指令设置选区后再添加", NamedTextColor.RED));
            return;
        }

        Location loc = event.getClickedBlock().getLocation();
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        if (!session.worldName().equals(loc.getWorld() != null ? loc.getWorld().getName() : null)) return;
        if (bx < session.minX() || bx > session.maxX()) {
            event.setCancelled(true);
            player.sendMessage(Component.text("不在选区内", NamedTextColor.RED));
            return;
        }
        if (by < session.minY() || by > session.maxY()) {
            event.setCancelled(true);
            player.sendMessage(Component.text("不在选区内", NamedTextColor.RED));
            return;
        }
        if (bz < session.minZ() || bz > session.maxZ()) {
            event.setCancelled(true);
            player.sendMessage(Component.text("不在选区内", NamedTextColor.RED));
            return;
        }

        if (type == SecretType.REDSTONE) {
            event.setCancelled(true);
            handleEditRedstone(session, player, event.getClickedBlock());
            return;
        }

        spawnEditMarker(loc.getWorld(), bx + 0.5, by, bz + 0.5, type, type.name());
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRedstoneLeverInteract(PlayerInteractEvent event) {
        if (event == null) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block lever = event.getClickedBlock();
        if (lever == null || lever.getType() != Material.LEVER) return;
        Player player = event.getPlayer();
        if (player == null) return;
        if (editSessions.containsKey(player.getUniqueId())) return;
        World world = lever.getWorld();
        if (world == null) return;
        RunState run = runsByWorld.get(world.getName());
        if (run == null) return;

        long leverKey = packBlock(lever.getX(), lever.getY(), lever.getZ());
        List<RedstoneDoorAction> actions = run.redstoneDoorsByLever.get(leverKey);
        redstoneLog("leverInteract player=" + player.getName()
                + " world=" + world.getName()
                + " lever=" + lever.getX() + "," + lever.getY() + "," + lever.getZ()
                + " actions=" + (actions != null ? actions.size() : 0));
        if (actions == null || actions.isEmpty()) return;

        for (RedstoneDoorAction a : actions) {
            redstoneLog("leverInteract trigger target=" + a.x() + "," + a.y() + "," + a.z()
                    + " type=" + world.getBlockAt(a.x(), a.y(), a.z()).getType().name());
            triggerDoorFall(world, a.x(), a.y(), a.z());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChestInteract(PlayerInteractEvent event) {
        if (event == null) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        if (player == null) return;
        World world = player.getWorld();
        if (world == null) return;
        RunState run = runsByWorld.get(world.getName());
        if (run == null) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        int bx = event.getClickedBlock().getX();
        int by = event.getClickedBlock().getY();
        int bz = event.getClickedBlock().getZ();
        long blockKey = packBlock(bx, by, bz);

        Long essenceId = run.essenceBlocks.get(blockKey);
        if (essenceId != null) {
            if (run.isFound(essenceId)) {
                event.setCancelled(true);
                return;
            }
            event.setCancelled(true);
            markFoundAndGrantBlessing(world, run, essenceId);
            event.getClickedBlock().setType(Material.AIR, false);
            run.essenceBlocks.remove(blockKey);
            return;
        }

        Long chestId = run.chestBlocks.get(blockKey);
        if (chestId == null) return;

        if (run.isFound(chestId)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("该箱子已被打开", NamedTextColor.RED));
            return;
        }

        String groupKey = run.secretToGroup.get(chestId);
        boolean forceBlessing = false;
        boolean isPuzzleRoom = false;
        if (groupKey != null) {
            GroupState group = run.groups.get(groupKey);
            if (group != null && group.templateName != null) {
                String t = group.templateName.trim().toLowerCase(Locale.ROOT);
                if ("trap_room_01".equalsIgnoreCase(t) && group.hasPlacement()) {
                    int dx = bx - group.originX;
                    int dz = bz - group.originZ;
                    int dy = by - group.originY;
                    int[] local = inverseRotateLocalXZ(dx, dz, group.rotation, group.sizeX, group.sizeZ);
                    if (local != null && local[0] == 27 && local[1] == 23 && (dy == 21 || dy == 22 || dy == 23)) {
                        event.setCancelled(true);
                        if (!run.triggeredTrapChests.add(chestId)) return;
                        setChestOpenedVisual(world, bx, by, bz);
                        spawnTrapRoom01Tnt(world, group, 60);
                        return;
                    }
                }
                isPuzzleRoom = t.startsWith("puzzle_room");
                forceBlessing = blessingOnlyRoomTemplate(t);
                if (isPuzzleRoom && !run.solvedPuzzleGroups.contains(groupKey) && !isUnrestrictedPuzzleChest(world, run, groupKey, group, bx, by, bz)) {
                    event.setCancelled(true);
                    player.sendMessage(Component.text("该谜题房间未完成", NamedTextColor.RED));
                    return;
                }
            }
        }

        if (forceBlessing) {
            event.setCancelled(true);
            markFoundAndGrantBlessing(world, run, chestId);
            setChestOpenedVisual(world, bx, by, bz);
            spawnBlessingEffect(world, bx, by, bz);
            if (isPuzzleRoom && groupKey != null && run.solvedPuzzleGroups.contains(groupKey)) {
                GroupState group = run.groups.get(groupKey);
                updatePuzzleLootedIfCompleted(run, groupKey, group);
            }
            return;
        }

        run.ensureChestAssignments();
        ChestRewardType reward = run.chestRewardById.getOrDefault(chestId, ChestRewardType.ITEM);
        if (reward == ChestRewardType.BLESSING) {
            event.setCancelled(true);
            markFoundAndGrantBlessing(world, run, chestId);
            setChestOpenedVisual(world, bx, by, bz);
            spawnBlessingEffect(world, bx, by, bz);
            if (groupKey != null) {
                GroupState group = run.groups.get(groupKey);
                if (group != null && group.templateName != null) {
                    String t = group.templateName.trim().toLowerCase(Locale.ROOT);
                    if (t.startsWith("puzzle_room") && run.solvedPuzzleGroups.contains(groupKey)) {
                        updatePuzzleLootedIfCompleted(run, groupKey, group);
                    }
                }
            }
            return;
        }

        event.setCancelled(true);
        Inventory inv = Bukkit.createInventory(new ItemChestHolder(world.getName(), chestId, bx, by, bz), 27, Component.text("秘密箱子", NamedTextColor.GOLD));
        inv.setItem(13, new ItemStack(Material.DIAMOND, 1));
        player.openInventory(inv);
    }

    public void markPuzzleCompleted(World world, Location loc) {
        if (world == null || loc == null) return;
        RunState run = runsByWorld.get(world.getName());
        if (run == null) return;

        Test_dungeons.DungeonSnapshot snapshot = plugin.getLastDungeon();
        int originX;
        int originZ;
        int width;
        int height;
        int roomScaleWorld;
        if (snapshot != null && snapshot.worldName() != null && snapshot.worldName().equals(world.getName())) {
            originX = snapshot.originX();
            originZ = snapshot.originZ();
            width = snapshot.width();
            height = snapshot.height();
            roomScaleWorld = Math.max(1, snapshot.roomScale());
        } else {
            if (!run.testMode) return;
            originX = run.testOriginX;
            originZ = run.testOriginZ;
            width = run.width;
            height = run.height;
            roomScaleWorld = Math.max(1, run.testRoomScale);
        }

        int worldDx = loc.getBlockX() - originX;
        int worldDz = loc.getBlockZ() - originZ;
        if (worldDx < 0 || worldDz < 0) return;
        if (worldDx >= width * roomScaleWorld || worldDz >= height * roomScaleWorld) return;
        int cellX = worldDx / roomScaleWorld;
        int cellY = worldDz / roomScaleWorld;
        String groupKey = run.cellToGroup.get(packCell(cellX, cellY));
        if (groupKey == null) return;
        GroupState group = run.groups.get(groupKey);
        if (group == null || group.templateName == null) return;
        String t = group.templateName.trim().toLowerCase(Locale.ROOT);
        if (!t.startsWith("puzzle_room")) return;
        run.solvedPuzzleGroups.add(groupKey);
        updatePuzzleLootedIfCompleted(run, groupKey, group);
    }

    public void markPuzzleFailed(World world, Location loc) {
        if (world == null || loc == null) return;
        RunState run = runsByWorld.get(world.getName());
        if (run == null) return;

        Test_dungeons.DungeonSnapshot snapshot = plugin.getLastDungeon();
        int originX;
        int originZ;
        int width;
        int height;
        int roomScaleWorld;
        if (snapshot != null && snapshot.worldName() != null && snapshot.worldName().equals(world.getName())) {
            originX = snapshot.originX();
            originZ = snapshot.originZ();
            width = snapshot.width();
            height = snapshot.height();
            roomScaleWorld = Math.max(1, snapshot.roomScale());
        } else {
            if (!run.testMode) return;
            originX = run.testOriginX;
            originZ = run.testOriginZ;
            width = run.width;
            height = run.height;
            roomScaleWorld = Math.max(1, run.testRoomScale);
        }

        int worldDx = loc.getBlockX() - originX;
        int worldDz = loc.getBlockZ() - originZ;
        if (worldDx < 0 || worldDz < 0) return;
        if (worldDx >= width * roomScaleWorld || worldDz >= height * roomScaleWorld) return;
        int cellX = worldDx / roomScaleWorld;
        int cellY = worldDz / roomScaleWorld;
        String groupKey = run.cellToGroup.get(packCell(cellX, cellY));
        if (groupKey == null) return;
        GroupState group = run.groups.get(groupKey);
        if (group == null || group.templateName == null) return;
        String t = group.templateName.trim().toLowerCase(Locale.ROOT);
        if (!t.startsWith("puzzle_room")) return;
        run.failedPuzzleGroups.add(groupKey);
    }

    public boolean isPuzzleSolved(String worldName, int cellX, int cellY) {
        if (worldName == null) return false;
        RunState run = runsByWorld.get(worldName);
        if (run == null) return false;
        String groupKey = run.cellToGroup.get(packCell(cellX, cellY));
        if (groupKey == null) return false;
        return run.solvedPuzzleGroups.contains(groupKey);
    }

    public boolean isPuzzleFailed(String worldName, int cellX, int cellY) {
        if (worldName == null) return false;
        RunState run = runsByWorld.get(worldName);
        if (run == null) return false;
        String groupKey = run.cellToGroup.get(packCell(cellX, cellY));
        if (groupKey == null) return false;
        return run.failedPuzzleGroups.contains(groupKey);
    }

    public boolean isPuzzleLooted(String worldName, int cellX, int cellY) {
        if (worldName == null) return false;
        RunState run = runsByWorld.get(worldName);
        if (run == null) return false;
        String groupKey = run.cellToGroup.get(packCell(cellX, cellY));
        if (groupKey == null) return false;
        return run.lootedPuzzleGroups.contains(groupKey);
    }

    /** 檢查該房間所有秘密是否已被發現。 */
    public boolean isRoomSecretsAllFound(String worldName, int cellX, int cellY) {
        if (worldName == null) return false;
        RunState run = runsByWorld.get(worldName);
        if (run == null) return false;
        String groupKey = run.cellToGroup.get(packCell(cellX, cellY));
        if (groupKey == null) return false;
        GroupState group = run.groups.get(groupKey);
        if (group == null) return false;
        int total = group.totalSecrets();
        return total > 0 && group.foundSecretsCount() >= total;
    }

    private boolean isUnrestrictedPuzzleChest(World world, RunState run, String groupKey, GroupState group, int bx, int by, int bz) {
        if (world == null || run == null || groupKey == null || group == null || group.templateName == null) return false;
        String template = group.templateName.trim();
        boolean isRoom03 = "puzzle_room_03".equalsIgnoreCase(template);
        boolean isRoom08 = "puzzle_room_08".equalsIgnoreCase(template);
        if (!isRoom03 && !isRoom08) return false;
        if (!group.hasPlacement()) return false;

        int dx = bx - group.originX;
        int dz = bz - group.originZ;
        int dy = by - group.originY;
        int[] local = inverseRotateLocalXZ(dx, dz, group.rotation, group.sizeX, group.sizeZ);
        if (local == null) return false;
        if (isRoom03) return local[0] == 21 && dy == 3 && local[1] == 4;
        return local[0] == 27 && dy == 53 && local[1] == 28;
    }

    private void updatePuzzleLootedIfCompleted(RunState run, String groupKey, GroupState group) {
        if (run == null || groupKey == null || group == null) return;
        if (!run.solvedPuzzleGroups.contains(groupKey)) return;
        boolean isPuzzleRoom04 = group.templateName != null && "puzzle_room_04".equalsIgnoreCase(group.templateName.trim());
        boolean anyChest = false;
        boolean anyChestFound = false;
        for (SecretInstance s : group.secrets.values()) {
            if (s == null || s.type() != SecretType.CHEST) continue;
            anyChest = true;
            if (isPuzzleRoom04) {
                if (group.found.contains(s.id())) {
                    anyChestFound = true;
                    break;
                }
            } else {
                if (!group.found.contains(s.id())) return;
            }
        }
        if (anyChest) {
            if (!isPuzzleRoom04 || anyChestFound) run.lootedPuzzleGroups.add(groupKey);
        } else {
            run.lootedPuzzleGroups.add(groupKey);
        }
    }

    private GroupState groupAtLocation(World world, RunState run, Location loc) {
        if (world == null || run == null || loc == null) return null;

        Test_dungeons.DungeonSnapshot snapshot = plugin.getLastDungeon();
        int originX;
        int originZ;
        int width;
        int height;
        int roomScaleWorld;
        if (snapshot != null && snapshot.worldName() != null && snapshot.worldName().equals(world.getName())) {
            originX = snapshot.originX();
            originZ = snapshot.originZ();
            width = snapshot.width();
            height = snapshot.height();
            roomScaleWorld = Math.max(1, snapshot.roomScale());
        } else {
            if (!run.testMode) return null;
            originX = run.testOriginX;
            originZ = run.testOriginZ;
            width = run.width;
            height = run.height;
            roomScaleWorld = Math.max(1, run.testRoomScale);
        }

        int worldDx = loc.getBlockX() - originX;
        int worldDz = loc.getBlockZ() - originZ;
        if (worldDx < 0 || worldDz < 0) return null;
        if (worldDx >= width * roomScaleWorld || worldDz >= height * roomScaleWorld) return null;
        int cellX = worldDx / roomScaleWorld;
        int cellY = worldDz / roomScaleWorld;
        String groupKey = run.cellToGroup.get(packCell(cellX, cellY));
        if (groupKey == null) return null;
        return run.groups.get(groupKey);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProximity(PlayerMoveEvent event) {
        if (event == null) return;
        Player player = event.getPlayer();
        if (player == null) return;
        World world = player.getWorld();
        if (world == null) return;
        RunState run = runsByWorld.get(world.getName());
        if (run == null) return;
        Test_dungeons.DungeonSnapshot snapshot = plugin.getLastDungeon();
        boolean useSnapshot = snapshot != null && snapshot.worldName() != null && snapshot.worldName().equals(world.getName());
        int originX;
        int originZ;
        int width;
        int height;
        int roomScaleWorld;
        if (useSnapshot) {
            originX = snapshot.originX();
            originZ = snapshot.originZ();
            width = snapshot.width();
            height = snapshot.height();
            roomScaleWorld = Math.max(1, snapshot.roomScale());
        } else {
            if (!run.testMode) return;
            originX = run.testOriginX;
            originZ = run.testOriginZ;
            width = run.width;
            height = run.height;
            roomScaleWorld = Math.max(1, run.testRoomScale);
        }
        Location to = event.getTo();
        if (to == null) return;
        int worldDx = to.getBlockX() - originX;
        int worldDz = to.getBlockZ() - originZ;
        if (worldDx < 0 || worldDz < 0) return;
        if (worldDx >= width * roomScaleWorld || worldDz >= height * roomScaleWorld) return;
        int cellX = worldDx / roomScaleWorld;
        int cellY = worldDz / roomScaleWorld;
        String groupKey = run.cellToGroup.get(packCell(cellX, cellY));
        if (groupKey == null) return;
        GroupState group = run.groups.get(groupKey);
        if (group == null) return;

        double px = to.getX();
        double py = to.getY();
        double pz = to.getZ();
        for (SecretInstance s : group.secrets.values()) {
            if (group.found.contains(s.id())) continue;
            if (s.type() == SecretType.DROP) {
                double dx = s.cx() - px;
                double dy = (s.cy() + 0.9) - py;
                double dz = s.cz() - pz;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq <= PROXIMITY_RADIUS_SQ) {
                    markFoundAndGrantBlessing(world, run, s.id());
                }
                continue;
            }

            if (s.type() == SecretType.BAT) {
                UUID existing = run.batEntityBySecret.get(s.id());
                if (existing != null) {
                    Entity ent = world.getEntity(existing);
                    if (ent instanceof Bat bat && bat.isValid() && !bat.isDead()) continue;
                    run.batEntityBySecret.remove(s.id());
                    run.batById.remove(existing);
                    run.batOriginById.remove(existing);
                }

                double dx = s.cx() - px;
                double dy = (s.by() + 1.8) - py;
                double dz = s.cz() - pz;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq <= BAT_SPAWN_RADIUS_SQ) {
                    Location spawnAt = findSafeBatSpawn(world, s.cx(), s.by() + 1.8, s.cz());
                    if (spawnAt == null) continue;
                    world.spawn(spawnAt, Bat.class, bat -> {
                        bat.setSilent(true);
                        bat.setRemoveWhenFarAway(false);
                        run.batById.put(bat.getUniqueId(), s.id());
                        run.batEntityBySecret.put(s.id(), bat.getUniqueId());
                        run.batOriginById.put(bat.getUniqueId(), new double[]{spawnAt.getX(), spawnAt.getY(), spawnAt.getZ()});
                    });
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStickyStep(PlayerMoveEvent event) {
        if (event == null) return;
        Player player = event.getPlayer();
        if (player == null) return;
        if (editSessions.containsKey(player.getUniqueId())) return;
        Location to = event.getTo();
        if (to == null) return;
        Location from = event.getFrom();
        if (from == null) return;

        World world = player.getWorld();
        if (world == null) return;
        RunState run = runsByWorld.get(world.getName());
        if (run == null) return;
        if (run.stickyTriggerBlocks.isEmpty() && run.stickyBounds.isEmpty()) return;

        int toX = to.getBlockX();
        int toY = (int) Math.floor(to.getY() - 0.001);
        int toZ = to.getBlockZ();

        int fromX = from.getBlockX();
        int fromY = (int) Math.floor(from.getY() - 0.001);
        int fromZ = from.getBlockZ();
        if (toX == fromX && toY == fromY && toZ == fromZ) return;
        if (toY < world.getMinHeight() || toY >= world.getMaxHeight()) return;

        long floorKey = packBlock(toX, toY, toZ);
        Long last = lastStickyFloorByPlayer.get(player.getUniqueId());
        if (last != null && last.longValue() == floorKey) return;
        lastStickyFloorByPlayer.put(player.getUniqueId(), floorKey);

        Long stickyId = run.stickyTriggerBlocks.get(floorKey);
        int[] bounds = null;
        GroupState group = null;
        String groupKey = null;
        boolean regionMode = false;
        if (stickyId != null) {
            groupKey = run.secretToGroup.get(stickyId);
            group = groupKey != null ? run.groups.get(groupKey) : null;
        } else {
            stickyId = stickyIdAt(run, toX, toY, toZ);
            if (stickyId == null) return;
            bounds = run.stickyBounds.get(stickyId);
            if (bounds == null || bounds.length < 6) return;
            regionMode = true;
        }

        int yMin = Math.max(world.getMinHeight(), toY - 64);
        int yMax = Math.min(world.getMaxHeight() - 1, toY + 64);

        if (regionMode) {
            if (!isStickyNode(world, toX, toY, toZ, bounds)) return;
        } else {
            Block start = world.getBlockAt(toX, toY, toZ);
            Material startType = start.getType();
            if (startType.isAir()) return;
            boolean useTagMode = STICKY_TAG_MATERIALS.contains(startType) || hasAdjacentStickyTag(world, toX, toY, toZ);
            if (useTagMode) {
                if (!isStickyNode(world, toX, toY, toZ)) return;
            }

            ArrayDeque<Long> q = new ArrayDeque<>();
            Set<Long> visited = new HashSet<>();
            List<Long> toClear = new ArrayList<>();
            q.add(floorKey);
            visited.add(floorKey);

            while (!q.isEmpty() && toClear.size() < STICKY_MAX_CLEAR_BLOCKS) {
                long pos = q.poll();
                int x = unpackBlockX(pos);
                int y = unpackBlockY(pos);
                int z = unpackBlockZ(pos);

                if (y < yMin || y > yMax) continue;
                if (group != null && group.hasPlacement()) {
                    if (!isWithinGroupFootprintXZ(group, x, z)) continue;
                } else {
                    if (Math.abs(x - toX) > 96) continue;
                    if (Math.abs(z - toZ) > 96) continue;
                }

                Block b = world.getBlockAt(x, y, z);
                Material t = b.getType();
                if (t.isAir()) continue;
                if (useTagMode) {
                    if (!isStickyNode(world, x, y, z)) continue;
                } else {
                    if (t != startType) continue;
                }

                toClear.add(pos);
                tryEnqueueStickyNeighbor(q, visited, x + 1, y, z, yMin, yMax, group, toX, toZ);
                tryEnqueueStickyNeighbor(q, visited, x - 1, y, z, yMin, yMax, group, toX, toZ);
                tryEnqueueStickyNeighbor(q, visited, x, y + 1, z, yMin, yMax, group, toX, toZ);
                tryEnqueueStickyNeighbor(q, visited, x, y - 1, z, yMin, yMax, group, toX, toZ);
                tryEnqueueStickyNeighbor(q, visited, x, y, z + 1, yMin, yMax, group, toX, toZ);
                tryEnqueueStickyNeighbor(q, visited, x, y, z - 1, yMin, yMax, group, toX, toZ);
            }

            if (toClear.isEmpty()) return;
            for (long pos : toClear) {
                int x = unpackBlockX(pos);
                int y = unpackBlockY(pos);
                int z = unpackBlockZ(pos);
                Block b = world.getBlockAt(x, y, z);
                if (b.getType().isAir()) continue;
                b.setType(Material.AIR, false);
            }
            run.stickyTriggerBlocks.remove(floorKey);
            return;
        }

        ArrayDeque<Long> q = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        List<Long> toClear = new ArrayList<>();
        q.add(floorKey);
        visited.add(floorKey);

        while (!q.isEmpty() && toClear.size() < STICKY_MAX_CLEAR_BLOCKS) {
            long pos = q.poll();
            int x = unpackBlockX(pos);
            int y = unpackBlockY(pos);
            int z = unpackBlockZ(pos);

            if (regionMode) {
                if (x < bounds[0] || x > bounds[3]) continue;
                if (y < bounds[1] || y > bounds[4]) continue;
                if (z < bounds[2] || z > bounds[5]) continue;
                if (!isStickyNode(world, x, y, z, bounds)) continue;
            } else {
                if (y < yMin || y > yMax) continue;
                if (group != null && group.hasPlacement()) {
                    if (!isWithinGroupFootprintXZ(group, x, z)) continue;
                } else {
                    if (Math.abs(x - toX) > 96) continue;
                    if (Math.abs(z - toZ) > 96) continue;
                }
                if (!isStickyNode(world, x, y, z)) continue;
            }
            Block b = world.getBlockAt(x, y, z);
            if (b.getType().isAir()) continue;
            toClear.add(pos);

            if (regionMode) {
                tryEnqueueStickyNeighbor(q, visited, x + 1, y, z, bounds);
                tryEnqueueStickyNeighbor(q, visited, x - 1, y, z, bounds);
                tryEnqueueStickyNeighbor(q, visited, x, y + 1, z, bounds);
                tryEnqueueStickyNeighbor(q, visited, x, y - 1, z, bounds);
                tryEnqueueStickyNeighbor(q, visited, x, y, z + 1, bounds);
                tryEnqueueStickyNeighbor(q, visited, x, y, z - 1, bounds);
            } else {
                tryEnqueueStickyNeighbor(q, visited, x + 1, y, z, yMin, yMax, group, toX, toZ);
                tryEnqueueStickyNeighbor(q, visited, x - 1, y, z, yMin, yMax, group, toX, toZ);
                tryEnqueueStickyNeighbor(q, visited, x, y + 1, z, yMin, yMax, group, toX, toZ);
                tryEnqueueStickyNeighbor(q, visited, x, y - 1, z, yMin, yMax, group, toX, toZ);
                tryEnqueueStickyNeighbor(q, visited, x, y, z + 1, yMin, yMax, group, toX, toZ);
                tryEnqueueStickyNeighbor(q, visited, x, y, z - 1, yMin, yMax, group, toX, toZ);
            }
        }

        if (toClear.isEmpty()) return;
        for (long pos : toClear) {
            int x = unpackBlockX(pos);
            int y = unpackBlockY(pos);
            int z = unpackBlockZ(pos);
            Block b = world.getBlockAt(x, y, z);
            if (b.getType().isAir()) continue;
            b.setType(Material.AIR, false);
        }
        if (!regionMode) {
            run.stickyTriggerBlocks.remove(floorKey);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event == null) return;
        if (event.getPlayer() != null) {
            lastStickyFloorByPlayer.remove(event.getPlayer().getUniqueId());
        }
        exitEdit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemChestClick(InventoryClickEvent event) {
        if (event == null) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        Inventory top = event.getView().getTopInventory();
        if (top == null) return;
        if (!(top.getHolder() instanceof ItemChestHolder)) return;

        if (event.getClickedInventory() == null) {
            event.setCancelled(true);
            return;
        }

        if (event.getClickedInventory().equals(top)) {
            if (event.getSlot() != 13) {
                event.setCancelled(true);
                return;
            }
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                event.setCancelled(true);
            }
            return;
        }

        if (event.isShiftClick()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemChestDrag(InventoryDragEvent event) {
        if (event == null) return;
        Inventory top = event.getView().getTopInventory();
        if (top == null) return;
        if (!(top.getHolder() instanceof ItemChestHolder)) return;
        for (int raw : event.getRawSlots()) {
            if (raw < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemChestClose(InventoryCloseEvent event) {
        if (event == null) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory inv = event.getInventory();
        if (inv == null) return;
        if (!(inv.getHolder() instanceof ItemChestHolder holder)) return;

        RunState run = runsByWorld.get(holder.worldName());
        if (run == null) return;

        if (run.isFound(holder.secretId())) return;

        for (ItemStack it : inv.getContents()) {
            if (it == null || it.getType().isAir()) continue;
            Map<Integer, ItemStack> left = player.getInventory().addItem(it);
            if (!left.isEmpty()) {
                Location dropAt = new Location(player.getWorld(), holder.x() + 0.5, holder.y() + 1.0, holder.z() + 0.5);
                for (ItemStack rem : left.values()) {
                    if (rem == null || rem.getType().isAir()) continue;
                    player.getWorld().dropItemNaturally(dropAt, rem);
                }
            }
        }

        markFoundAndGrantBlessing(player.getWorld(), run, holder.secretId());
        setChestOpenedVisual(player.getWorld(), holder.x(), holder.y(), holder.z());
        String groupKey = run.secretToGroup.get(holder.secretId());
        if (groupKey != null) {
            GroupState group = run.groups.get(groupKey);
            if (group != null && group.templateName != null) {
                String t = group.templateName.trim().toLowerCase(Locale.ROOT);
                if (t.startsWith("puzzle_room") && run.solvedPuzzleGroups.contains(groupKey)) {
                    updatePuzzleLootedIfCompleted(run, groupKey, group);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpecialTntUse(PlayerInteractEvent event) {
        if (event == null) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        Player player = event.getPlayer();
        if (player == null) return;
        ItemStack item = event.getItem();
        if (!isSpecialTntItem(item)) return;

        Location base = null;
        if (event.getClickedBlock() != null) {
            base = event.getClickedBlock().getLocation().add(0.5, 1.0, 0.5);
        } else {
            base = player.getLocation().add(0, 0.5, 0);
        }
        World world = base.getWorld();
        if (world == null) return;

        RunState run = runsByWorld.get(world.getName());
        if (run != null) {
            GroupState group = groupAtLocation(world, run, base);
            if (group != null && group.templateName != null) {
                String t = group.templateName.trim().toLowerCase(Locale.ROOT);
                if (t.startsWith("puzzle_room")) {
                    event.setCancelled(true);
                    player.sendMessage(Component.text("谜题房间内无法放置特殊TNT", NamedTextColor.RED));
                    return;
                }
            }
        }

        spawnSpecialTnt(world, base, 40);

        int amt = item.getAmount();
        if (amt <= 1) {
            player.getInventory().removeItem(item);
        } else {
            item.setAmount(amt - 1);
        }
        event.setCancelled(true);
    }

    public TNTPrimed spawnSpecialTnt(World world, Location base, int fuseTicks) {
        return spawnSpecialTnt(world, base, fuseTicks, null);
    }

    public TNTPrimed spawnSpecialTnt(World world, Location base, int fuseTicks, float yield) {
        return spawnSpecialTnt(world, base, fuseTicks, Float.valueOf(yield));
    }

    private TNTPrimed spawnSpecialTnt(World world, Location base, int fuseTicks, Float yield) {
        if (world == null || base == null) return null;
        int fuse = Math.max(1, fuseTicks);
        return world.spawn(base, TNTPrimed.class, tnt -> {
            tnt.setFuseTicks(fuse);
            tnt.setIsIncendiary(false);
            if (yield != null) {
                tnt.setYield(yield.floatValue());
            }
            tnt.getPersistentDataContainer().set(specialTntKey, PersistentDataType.BYTE, (byte) 1);
        });
    }

    private void spawnTrapRoom01Tnt(World world, GroupState group, int fuseTicks) {
        if (world == null || group == null) return;
        if (!group.hasPlacement()) return;
        int y = 31;
        for (int lx = 26; lx <= 28; lx++) {
            for (int lz = 11; lz <= 14; lz++) {
                int[] rot = rotateLocalXZ(lx, lz, group.rotation, group.sizeX, group.sizeZ);
                int wx = group.originX + rot[0];
                int wz = group.originZ + rot[1];
                int wy = group.originY + y;
                spawnSpecialTnt(world, new Location(world, wx + 0.5, wy + 0.5, wz + 0.5), fuseTicks);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExplode(EntityExplodeEvent event) {
        if (event == null) return;
        Entity e = event.getEntity();
        if (e == null) return;
        World world = e.getWorld();
        if (world == null) return;
        RunState run = runsByWorld.get(world.getName());
        if (run == null) return;

        boolean special = isSpecialTntEntity(e);
        if (special && e instanceof TNTPrimed) {
            GroupState group = groupAtLocation(world, run, e.getLocation());
            if (group != null && group.templateName != null && "trap_room_01".equalsIgnoreCase(group.templateName.trim())) {
                Location loc = e.getLocation();
                double r2 = 49.0;
                for (Player p : world.getPlayers()) {
                    if (p == null || !p.isOnline()) continue;
                    if (p.getLocation().distanceSquared(loc) > r2) continue;
                    p.damage(Math.max(1.0, p.getHealth() + 1000.0), e);
                }
            }
        }
        handleExplosion(world, run, e.getLocation(), event.blockList(), special);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExplode(BlockExplodeEvent event) {
        if (event == null) return;
        Block b = event.getBlock();
        if (b == null) return;
        World world = b.getWorld();
        if (world == null) return;
        RunState run = runsByWorld.get(world.getName());
        if (run == null) return;
        handleExplosion(world, run, b.getLocation().add(0.5, 0.5, 0.5), event.blockList(), false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpecialTntDamage(EntityDamageByEntityEvent event) {
        if (event == null) return;
        if (!(event.getDamager() instanceof TNTPrimed tnt)) return;
        if (!isSpecialTntEntity(tnt)) return;
        World world = tnt.getWorld();
        if (world == null) return;
        RunState run = runsByWorld.get(world.getName());
        if (run == null) return;
        GroupState group = groupAtLocation(world, run, tnt.getLocation());
        if (group == null || group.templateName == null) return;
        String t = group.templateName.trim().toLowerCase(Locale.ROOT);
        if (!t.startsWith("puzzle_room")) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedBreak(BlockBreakEvent event) {
        if (event == null) return;
        Player player = event.getPlayer();
        if (player != null && editSessions.containsKey(player.getUniqueId())) return;
        Block block = event.getBlock();
        if (block == null) return;
        World world = block.getWorld();
        if (world == null) return;
        RunState run = runsByWorld.get(world.getName());
        if (run == null) return;

        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        if (run.breakableBlocks.containsKey(packBlock(x, y, z)) || isInAnyTomb(run, x, y, z)) {
            event.setCancelled(true);
            if (player != null) {
                player.sendMessage(Component.text("需要特殊TNT才能炸开", NamedTextColor.RED));
            }
        }
    }

    /** 公開方法：觸發煤炭磚下墜動畫（與紅石開門、初始房開門相同），供其他模組呼叫。僅處理煤炭磚。 */
    public void triggerCoalBlockFall(World world, int x, int y, int z) {
        if (world == null) return;
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() != Material.COAL_BLOCK) return;
        triggerDoorFall(world, x, y, z);
    }

    private void triggerDoorFall(World world, int x, int y, int z) {
        if (world == null) return;
        Block block = world.getBlockAt(x, y, z);
        if (block == null) return;
        Material type = block.getType();
        if (type.isAir()) {
            redstoneLog("triggerDoorFall skip world=" + world.getName()
                    + " pos=" + x + "," + y + "," + z
                    + " reason=already_air");
            return;
        }

        BlockData data = block.getBlockData();
        Location spawnAt = new Location(world, x + 0.5, y, z + 0.5);
        block.setType(Material.AIR, false);
        FallingBlock falling;
        try {
            falling = world.spawnFallingBlock(spawnAt, data);
        } catch (IllegalArgumentException ex) {
            redstoneLog("triggerDoorFall spawnFail world=" + world.getName()
                    + " pos=" + x + "," + y + "," + z
                    + " originalType=" + type.name()
                    + " error=" + ex.getMessage());
            return;
        }
        falling.getPersistentDataContainer().set(fallingDoorKey, PersistentDataType.BYTE, (byte) 1);
        redstoneLog("triggerDoorFall spawned world=" + world.getName()
                + " pos=" + x + "," + y + "," + z
                + " originalType=" + type.name()
                + " fallingId=" + falling.getUniqueId());
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
        redstoneLog("fallingDoor placeCancelled world=" + event.getBlock().getWorld().getName()
                + " at=" + event.getBlock().getX() + "," + event.getBlock().getY() + "," + event.getBlock().getZ()
                + " to=" + event.getTo().name()
                + " fallingId=" + falling.getUniqueId());
        falling.remove();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBatHit(EntityDamageByEntityEvent event) {
        if (event == null) return;
        if (event.getEntityType() != EntityType.BAT) return;
        if (!(event.getEntity() instanceof Bat)) return;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBatDeath(EntityDeathEvent event) {
        if (event == null) return;
        if (event.getEntityType() != EntityType.BAT) return;
        if (!(event.getEntity() instanceof Bat bat)) return;
        World world = bat.getWorld();
        if (world == null) return;
        RunState run = runsByWorld.get(world.getName());
        if (run == null) return;

        UUID uuid = bat.getUniqueId();
        Long id = run.batById.remove(uuid);
        if (id == null) return;
        run.batEntityBySecret.remove(id);
        run.batOriginById.remove(uuid);

        if (bat.getKiller() == null) return;
        markFoundAndGrantBlessing(world, run, id);
    }

    private void spawnInvisibleMarker(World world, double x, double y, double z, SecretType type) {
        if (world == null) return;
        world.spawn(new Location(world, x, y, z), ArmorStand.class, as -> {
            as.setVisible(false);
            as.setMarker(true);
            as.setGravity(false);
            as.setSilent(true);
            as.setCollidable(false);
            as.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
            as.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.name());
        });
    }

    private static int pickPlaceY(World world, int x, int y, int z) {
        if (world == null) return y;
        Material at = world.getBlockAt(x, y, z).getType();
        if (at.isAir()) return y;

        int above = y + 1;
        if (above >= world.getMaxHeight()) return y;
        return above;
    }

    private static void clearVerticalDuplicates(World world, int x, int fromY, int toY, int z, boolean chest) {
        if (world == null) return;
        int min = Math.min(fromY, toY);
        int max = Math.max(fromY, toY);
        for (int y = min; y < max; y++) {
            Material m = world.getBlockAt(x, y, z).getType();
            if (chest) {
                if (m == Material.CHEST || m == Material.TRAPPED_CHEST) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            } else {
                if (m == Material.PLAYER_HEAD) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    private static Object profileWithTexture(String textureId) {
        String name = "d" + UUID.randomUUID().toString().replace("-", "").substring(0, 15);
        Object profile = createProfile(UUID.randomUUID(), name);
        if (profile == null) {
            if (!loggedCreateProfileNull) {
                loggedCreateProfileNull = true;
                skullLog(Level.WARNING, "profileWithTexture: createProfile returned null", null);
            }
            return null;
        }
        if (textureId == null || textureId.isBlank()) return profile;

        URL skinUrl = null;
        try {
            skinUrl = new URL(TEXTURE_BASE + textureId);
        } catch (Throwable ignored) {
        }

        Object textures = invokeNoArg(profile, "getTextures");
        if (textures != null && skinUrl != null) {
            boolean ok = invokeOneArg(textures, "setSkin", skinUrl);
            if (!ok) ok = invokeOneArg(textures, "setSkin", skinUrl.toString());
            if (ok) {
                invokeOneArg(profile, "setTextures", textures);
                return profile;
            }
        }

        Object props = invokeNoArg(profile, "getProperties");
        if (props instanceof Iterable<?> it) {
            try {
                java.util.Iterator<?> iter = it.iterator();
                while (iter.hasNext()) {
                    Object p = iter.next();
                    Object n = invokeNoArg(p, "getName");
                    if (n instanceof String s && "textures".equalsIgnoreCase(s)) {
                        iter.remove();
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + TEXTURE_BASE + textureId + "\"}}}";
        String b64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        Object property = newProfileProperty("textures", b64);
        if (property == null) {
            skullLog(Level.WARNING, "profileWithTexture: newProfileProperty returned null", null);
        }
        if (property != null && props instanceof java.util.Collection<?> c) {
            try {
                ((java.util.Collection<Object>) c).add(property);
            } catch (Throwable ignored) {
            }
        }
        return profile;
    }

    private static void applySkullTexture(Block block, String textureId) {
        if (block == null || textureId == null || textureId.isBlank()) return;
        if (!(block.getState() instanceof Skull skull)) {
            skullLog(Level.WARNING, "applySkullTexture: block state is not Skull at " + fmtBlock(block), null);
            return;
        }
        Object profile = profileWithTexture(textureId);
        boolean applied = false;
        if (profile != null) {
            applied = invokeOneArg(skull, "setPlayerProfile", profile, "applySkullTexture " + fmtBlock(block));
            if (!applied) {
                applied = invokeOneArg(skull, "setOwnerProfile", profile, "applySkullTexture " + fmtBlock(block));
            }
        }
        if (!applied) {
            Object legacy = legacyGameProfileWithTexture(textureId);
            if (legacy == null) {
                skullLog(Level.WARNING, "applySkullTexture: no usable profile at " + fmtBlock(block) + " skullClass=" + skull.getClass().getName(), null);
            } else if (!applyLegacyProfileToSkullState(skull, legacy)) {
                skullLog(Level.SEVERE, "applySkullTexture: legacy apply failed at " + fmtBlock(block)
                        + " skullClass=" + skull.getClass().getName()
                        + " legacyProfileClass=" + legacy.getClass().getName(), null);
            } else {
                applied = true;
            }
        }
        try {
            skull.update(true, false);
        } catch (Throwable t) {
            skullLog(Level.SEVERE, "applySkullTexture: skull.update failed at " + fmtBlock(block), t);
        }
    }

    private ItemStack skullItem(String textureId) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, 1);
        if (!(item.getItemMeta() instanceof SkullMeta meta)) return item;
        applySkullTextureToMeta(meta, textureId, "skullItem");
        item.setItemMeta(meta);
        return item;
    }

    public static void applySkullTextureToMeta(SkullMeta meta, String textureId, String ctx) {
        if (meta == null || textureId == null || textureId.isBlank()) return;
        boolean applied = false;
        Object profile = profileWithTexture(textureId);
        if (profile != null) {
            applied = invokeOneArg(meta, "setPlayerProfile", profile, ctx + " setPlayerProfile metaClass=" + meta.getClass().getName());
            if (!applied) {
                applied = invokeOneArg(meta, "setOwnerProfile", profile, ctx + " setOwnerProfile metaClass=" + meta.getClass().getName());
            }
        }
        if (!applied) {
            Object legacy = legacyGameProfileWithTexture(textureId);
            if (legacy == null) {
                skullLog(Level.WARNING, ctx + ": no usable profile for SkullMeta metaClass=" + meta.getClass().getName(), null);
                return;
            }
            if (!applyLegacyProfileToSkullMeta(meta, legacy)) {
                skullLog(Level.SEVERE, ctx + ": legacy apply failed metaClass=" + meta.getClass().getName()
                        + " legacyProfileClass=" + legacy.getClass().getName(), null);
            }
        }
    }

    private static Object newProfileProperty(String name, String value) {
        if (name == null || value == null) return null;
        try {
            Class<?> cls = Class.forName("org.bukkit.profile.ProfileProperty");
            return cls.getConstructor(String.class, String.class).newInstance(name, value);
        } catch (Throwable ignored) {
        }
        try {
            Class<?> cls = Class.forName("com.destroystokyo.paper.profile.ProfileProperty");
            return cls.getConstructor(String.class, String.class).newInstance(name, value);
        } catch (Throwable ignored) {
        }
        if (!loggedProfilePropertyMissing) {
            loggedProfilePropertyMissing = true;
            skullLog(Level.WARNING, "ProfileProperty class not found; skull textures may not apply on this server jar", null);
        }
        return null;
    }

    private static Object createProfile(UUID id, String name) {
        try {
            Object profile = null;
            for (Method m : Bukkit.class.getMethods()) {
                String mn = m.getName();
                if (!mn.equals("createProfile") && !mn.equals("createPlayerProfile")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 2 && UUID.class.isAssignableFrom(p[0]) && String.class.isAssignableFrom(p[1])) {
                    profile = m.invoke(null, id, name);
                    if (profile != null) return profile;
                }
                if (p.length == 1 && UUID.class.isAssignableFrom(p[0])) {
                    profile = m.invoke(null, id);
                    if (profile != null) return profile;
                }
                if (p.length == 1 && String.class.isAssignableFrom(p[0])) {
                    profile = m.invoke(null, name);
                    if (profile != null) return profile;
                }
            }
        } catch (Throwable ignored) {
        }
        if (!loggedCreateProfileMissing) {
            loggedCreateProfileMissing = true;
            skullLog(Level.WARNING, "Bukkit.createProfile/createPlayerProfile not available; skull textures may not apply on this server jar", null);
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String name) {
        if (target == null || name == null) return null;
        try {
            Method m = target.getClass().getMethod(name);
            try {
                if (!m.canAccess(target)) m.setAccessible(true);
            } catch (Throwable ignored) {
            }
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean invokeOneArg(Object target, String name, Object arg) {
        return invokeOneArg(target, name, arg, null);
    }

    private static boolean invokeOneArg(Object target, String name, Object arg, String ctx) {
        if (target == null || name == null) return false;
        Throwable lastError = null;
        boolean hadMatch = false;
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(name)) continue;
            if (m.getParameterCount() != 1) continue;
            Class<?> pt = m.getParameterTypes()[0];
            if (arg != null && !pt.isInstance(arg)) continue;
            hadMatch = true;
            try {
                try {
                    if (!m.canAccess(target)) m.setAccessible(true);
                } catch (Throwable ignored) {
                }
                m.invoke(target, arg);
                return true;
            } catch (Throwable t) {
                lastError = t;
            }
        }
        if (hadMatch && lastError != null) {
            skullLog(Level.WARNING, "invokeOneArg failed method=" + name
                    + " targetClass=" + target.getClass().getName()
                    + " argClass=" + (arg != null ? arg.getClass().getName() : "null")
                    + (ctx != null ? (" ctx=" + ctx) : ""), lastError);
            return false;
        }
        if (ctx != null) {
            skullLog(Level.WARNING, "invokeOneArg no-match method=" + name
                    + " targetClass=" + target.getClass().getName()
                    + " argClass=" + (arg != null ? arg.getClass().getName() : "null")
                    + " ctx=" + ctx, null);
        }
        return false;
    }

    private static void skullLog(Level level, String message, Throwable t) {
        Logger log;
        try {
            Test_dungeons inst = Test_dungeons.getInstance();
            log = inst != null ? inst.getLogger() : Bukkit.getLogger();
        } catch (Throwable e) {
            log = Logger.getLogger("test_dungeons");
        }
        String msg = "[Dungeons][Skull] " + message;
        if (t != null) {
            log.log(level != null ? level : Level.INFO, msg, t);
        } else {
            log.log(level != null ? level : Level.INFO, msg);
        }
    }

    private static String fmtBlock(Block block) {
        if (block == null) return "null";
        World w = block.getWorld();
        String wn = w != null ? w.getName() : "null";
        return wn + " (" + block.getX() + "," + block.getY() + "," + block.getZ() + ")";
    }

    private static Object legacyGameProfileWithTexture(String textureId) {
        if (textureId == null || textureId.isBlank()) return null;
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + TEXTURE_BASE + textureId + "\"}}}";
        String b64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        try {
            Class<?> gpCls = Class.forName("com.mojang.authlib.GameProfile");
            Object gp = gpCls.getConstructor(UUID.class, String.class)
                    .newInstance(UUID.randomUUID(), "d" + UUID.randomUUID().toString().replace("-", "").substring(0, 15));

            Object props = gpCls.getMethod("getProperties").invoke(gp);
            if (props == null) return gp;

            Class<?> propCls = Class.forName("com.mojang.authlib.properties.Property");
            Object prop = propCls.getConstructor(String.class, String.class).newInstance("textures", b64);
            if (!invokePutProperty(props, "textures", prop)) {
                skullLog(Level.WARNING, "legacyGameProfileWithTexture: cannot put textures property propsClass=" + props.getClass().getName(), null);
            }
            return gp;
        } catch (Throwable t) {
            skullLog(Level.WARNING, "legacyGameProfileWithTexture: failed to build GameProfile", t);
            return null;
        }
    }

    private static boolean invokePutProperty(Object props, String key, Object prop) {
        if (props == null || key == null || prop == null) return false;
        try {
            for (Method m : props.getClass().getMethods()) {
                if (!m.getName().equals("put")) continue;
                if (m.getParameterCount() != 2) continue;
                Class<?>[] pt = m.getParameterTypes();
                if (!pt[0].isInstance(key)) continue;
                if (!pt[1].isInstance(prop)) continue;
                m.invoke(props, key, prop);
                return true;
            }
        } catch (Throwable t) {
            skullLog(Level.WARNING, "invokePutProperty failed propsClass=" + props.getClass().getName()
                    + " propClass=" + prop.getClass().getName(), t);
        }
        return false;
    }

    private static boolean applyLegacyProfileToSkullState(Skull skull, Object legacyProfile) {
        if (skull == null || legacyProfile == null) return false;
        if (setFieldInHierarchyCoerced(skull, "profile", legacyProfile)) return true;
        if (setFieldInHierarchyCoerced(skull, "gameProfile", legacyProfile)) return true;
        return setFieldByValueTypeInHierarchyCoerced(skull, legacyProfile);
    }

    private static boolean applyLegacyProfileToSkullMeta(SkullMeta meta, Object legacyProfile) {
        if (meta == null || legacyProfile == null) return false;
        if (setFieldInHierarchyCoerced(meta, "profile", legacyProfile)) return true;
        if (setFieldInHierarchyCoerced(meta, "gameProfile", legacyProfile)) return true;
        return setFieldByValueTypeInHierarchyCoerced(meta, legacyProfile);
    }

    private static boolean setFieldByValueTypeInHierarchyCoerced(Object target, Object value) {
        if (target == null || value == null) return false;
        Class<?> valueClass = value.getClass();
        Class<?> c = target.getClass();
        while (c != null && c != Object.class) {
            try {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    Object coerced = coerceProfileForField(value, f);
                    if (coerced == null) continue;
                    f.setAccessible(true);
                    f.set(target, coerced);
                    return true;
                }
            } catch (Throwable t) {
                skullLog(Level.WARNING, "setFieldByValueTypeInHierarchy failed targetClass=" + target.getClass().getName()
                        + " valueClass=" + valueClass.getName(), t);
                return false;
            }
            c = c.getSuperclass();
        }
        return false;
    }

    private static boolean setFieldInHierarchy(Object target, String fieldName, Object value) {
        if (target == null || fieldName == null) return false;
        Class<?> c = target.getClass();
        while (c != null && c != Object.class) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(target, value);
                return true;
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable t) {
                skullLog(Level.WARNING, "setFieldInHierarchy failed field=" + fieldName + " targetClass=" + target.getClass().getName(), t);
                return false;
            }
            c = c.getSuperclass();
        }
        return false;
    }

    private static boolean setFieldInHierarchyCoerced(Object target, String fieldName, Object value) {
        if (target == null || fieldName == null || value == null) return false;
        Class<?> c = target.getClass();
        while (c != null && c != Object.class) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                Object coerced = coerceProfileForField(value, f);
                if (coerced == null) return false;
                f.set(target, coerced);
                return true;
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable t) {
                skullLog(Level.WARNING, "setFieldInHierarchyCoerced failed field=" + fieldName + " targetClass=" + target.getClass().getName()
                        + " valueClass=" + value.getClass().getName(), t);
                return false;
            }
            c = c.getSuperclass();
        }
        return false;
    }

    private static Object coerceProfileForField(Object legacyProfile, java.lang.reflect.Field field) {
        if (legacyProfile == null || field == null) return null;
        Class<?> fieldType = field.getType();
        if (fieldType.isInstance(legacyProfile)) return legacyProfile;
        if (fieldType == java.util.Optional.class) {
            Object inner = legacyProfile;
            try {
                java.lang.reflect.Type gt = field.getGenericType();
                if (gt instanceof java.lang.reflect.ParameterizedType pt) {
                    java.lang.reflect.Type[] args = pt.getActualTypeArguments();
                    if (args.length == 1) {
                        String tn = args[0].getTypeName();
                        if ("net.minecraft.world.item.component.ResolvableProfile".equals(tn) || tn.endsWith("ResolvableProfile")) {
                            Object rp = createResolvableProfile(legacyProfile);
                            if (rp != null) inner = rp;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            return java.util.Optional.of(inner);
        }
        return coerceProfileForFieldType(legacyProfile, fieldType);
    }

    private static Object coerceProfileForFieldType(Object legacyProfile, Class<?> fieldType) {
        if (legacyProfile == null || fieldType == null) return null;
        if (fieldType.isInstance(legacyProfile)) return legacyProfile;
        String n = fieldType.getName();
        if ("net.minecraft.world.item.component.ResolvableProfile".equals(n)) {
            return createResolvableProfile(legacyProfile);
        }
        if (n.endsWith("ResolvableProfile")) {
            Object rp = createResolvableProfile(legacyProfile);
            if (rp != null && fieldType.isInstance(rp)) return rp;
        }
        return null;
    }

    private static Object createResolvableProfile(Object legacyProfile) {
        if (legacyProfile == null) return null;
        try {
            Class<?> rp = Class.forName("net.minecraft.world.item.component.ResolvableProfile");
            Class<?> gp = legacyProfile.getClass();

            for (String methodName : new String[]{"of", "fromGameProfile", "create", "a"}) {
                try {
                    Method m = rp.getMethod(methodName, gp);
                    Object v = m.invoke(null, legacyProfile);
                    if (v != null) return v;
                } catch (Throwable ignored) {
                }
            }

            for (Method m : rp.getMethods()) {
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                if (m.getParameterCount() != 1) continue;
                if (!m.getParameterTypes()[0].isInstance(legacyProfile)) continue;
                if (!rp.isAssignableFrom(m.getReturnType())) continue;
                try {
                    Object v = m.invoke(null, legacyProfile);
                    if (v != null) return v;
                } catch (Throwable ignored) {
                }
            }

            for (java.lang.reflect.Constructor<?> ctor : rp.getDeclaredConstructors()) {
                if (ctor.getParameterCount() != 1) continue;
                if (!ctor.getParameterTypes()[0].isInstance(legacyProfile)) continue;
                try {
                    ctor.setAccessible(true);
                    Object v = ctor.newInstance(legacyProfile);
                    if (v != null) return v;
                } catch (Throwable ignored) {
                }
            }

            for (java.lang.reflect.Constructor<?> ctor : rp.getDeclaredConstructors()) {
                Class<?>[] p = ctor.getParameterTypes();
                if (p.length != 2) continue;
                if (p[0] != java.util.Optional.class) continue;
                if (p[1] != java.util.Optional.class) continue;
                try {
                    ctor.setAccessible(true);
                    Object v = ctor.newInstance(java.util.Optional.of(legacyProfile), java.util.Optional.empty());
                    if (v != null) return v;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            skullLog(Level.WARNING, "createResolvableProfile failed legacyProfileClass=" + legacyProfile.getClass().getName(), t);
        }
        return null;
    }

    private void spawnBlessingEffect(World world, int bx, int by, int bz) {
        if (world == null) return;
        Location start = new Location(world, bx + 0.5, by + 0.3, bz + 0.5);
        ArmorStand as = world.spawn(start, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setSmall(true);
            stand.setGravity(false);
            stand.setSilent(true);
            stand.setCollidable(false);
            if (stand.getEquipment() != null) stand.getEquipment().setHelmet(skullItem(BLESSING_TEXTURE));
        });

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!as.isValid() || as.isDead()) {
                    cancel();
                    return;
                }
                if (ticks >= 60) {
                    as.remove();
                    cancel();
                    return;
                }
                Location l = as.getLocation();
                as.teleport(l.add(0, 1.0 / 60.0, 0));
                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static void setChestOpenedVisual(World world, int x, int y, int z) {
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

    private void spawnEditMarker(World world, double x, double y, double z, SecretType type, String name) {
        if (world == null || type == null) return;
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        world.spawn(new Location(world, x, y, z), ArmorStand.class, as -> {
            as.setVisible(true);
            as.setSmall(true);
            as.setGravity(false);
            as.setSilent(true);
            as.setCollidable(false);
            as.customName(Component.text(name != null ? name : type.name(), NamedTextColor.YELLOW));
            as.setCustomNameVisible(true);
            as.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
            as.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.name());
            as.getPersistentDataContainer().set(markerBlockXKey, PersistentDataType.INTEGER, bx);
            as.getPersistentDataContainer().set(markerBlockYKey, PersistentDataType.INTEGER, by);
            as.getPersistentDataContainer().set(markerBlockZKey, PersistentDataType.INTEGER, bz);
        });
    }

    private void spawnEditRedstoneTargetMarker(World world, int targetX, int targetY, int targetZ, int leverX, int leverY, int leverZ) {
        if (world == null) return;
        removeRedstoneTargetMarker(world, targetX, targetY, targetZ, leverX, leverY, leverZ);
        String name = "REDSTONE <= " + leverX + "," + leverY + "," + leverZ;
        world.spawn(new Location(world, targetX + 0.5, targetY, targetZ + 0.5), ArmorStand.class, as -> {
            as.setVisible(true);
            as.setSmall(true);
            as.setGravity(false);
            as.setSilent(true);
            as.setCollidable(false);
            as.customName(Component.text(name, NamedTextColor.YELLOW));
            as.setCustomNameVisible(true);
            as.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
            as.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, SecretType.REDSTONE.name());
            as.getPersistentDataContainer().set(redstoneLeverKey, PersistentDataType.STRING, leverX + "," + leverY + "," + leverZ);
            as.getPersistentDataContainer().set(markerBlockXKey, PersistentDataType.INTEGER, targetX);
            as.getPersistentDataContainer().set(markerBlockYKey, PersistentDataType.INTEGER, targetY);
            as.getPersistentDataContainer().set(markerBlockZKey, PersistentDataType.INTEGER, targetZ);
        });
    }

    private void spawnTombCornerMarker(World world, double x, double y, double z, String name) {
        if (world == null) return;
        world.spawn(new Location(world, x, y, z), ArmorStand.class, as -> {
            as.setVisible(true);
            as.setSmall(true);
            as.setGravity(false);
            as.setSilent(true);
            as.setCollidable(false);
            as.customName(Component.text(name, NamedTextColor.YELLOW));
            as.setCustomNameVisible(true);
            as.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
            as.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, SecretType.TOMB.name());
            as.getPersistentDataContainer().set(tombCornerKey, PersistentDataType.BYTE, (byte) 1);
        });
    }

    private void spawnStickyCornerMarker(World world, double x, double y, double z, String name) {
        if (world == null) return;
        world.spawn(new Location(world, x, y, z), ArmorStand.class, as -> {
            as.setVisible(true);
            as.setSmall(true);
            as.setGravity(false);
            as.setSilent(true);
            as.setCollidable(false);
            as.customName(Component.text(name, NamedTextColor.YELLOW));
            as.setCustomNameVisible(true);
            as.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
            as.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, SecretType.STICKY_BLOCK.name());
            as.getPersistentDataContainer().set(tombCornerKey, PersistentDataType.BYTE, (byte) 1);
        });
    }

    private void clearMarkersInBox(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (world == null) return;
        for (Entity e : List.copyOf(world.getEntities())) {
            if (e.getType() != EntityType.ARMOR_STAND) continue;
            if (!(e instanceof ArmorStand as)) continue;
            if (!isMarker(as)) continue;
            Location l = as.getLocation();
            int bx = l.getBlockX();
            int by = l.getBlockY();
            int bz = l.getBlockZ();
            if (bx < minX || bx > maxX) continue;
            if (by < minY || by > maxY) continue;
            if (bz < minZ || bz > maxZ) continue;
            as.remove();
        }
    }

    private boolean isMarker(ArmorStand as) {
        if (as == null) return false;
        Byte b = as.getPersistentDataContainer().get(markerKey, PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }

    private SecretType getMarkerType(ArmorStand as) {
        if (as == null) return null;
        String raw = as.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return SecretType.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private int[] getRedstoneTarget(ArmorStand as) {
        if (as == null) return null;
        String raw = as.getPersistentDataContainer().get(redstoneTargetKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split(",");
        if (parts.length != 3) return null;
        try {
            return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim())};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int[] getRedstoneLever(ArmorStand as) {
        if (as == null) return null;
        String raw = as.getPersistentDataContainer().get(redstoneLeverKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split(",");
        if (parts.length != 3) return null;
        try {
            return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim())};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void handleEditRedstone(EditSession session, Player player, Block clicked) {
        if (session == null || player == null || clicked == null) return;
        if (clicked.getWorld() == null) return;
        UUID id = player.getUniqueId();

        int bx = clicked.getX();
        int by = clicked.getY();
        int bz = clicked.getZ();

        if (clicked.getType() == Material.LEVER) {
            pendingRedstoneLever.put(id, new int[]{bx, by, bz});
            player.sendMessage(Component.text("已选择拉杆，继续右键记录门方块位置", NamedTextColor.GRAY));
            if (plugin.getLogger() != null) {
                plugin.getLogger().info("redstone_edit selectLever player=" + player.getName()
                        + " template=" + session.templateName()
                        + " lever=" + bx + "," + by + "," + bz);
            }
            return;
        }

        int[] pending = pendingRedstoneLever.get(id);
        if (pending == null) {
            player.sendMessage(Component.text("请先右键拉杆位置", NamedTextColor.RED));
            return;
        }

        int leverX = pending[0];
        int leverY = pending[1];
        int leverZ = pending[2];
        if (leverX < session.minX() || leverX > session.maxX()
                || leverY < session.minY() || leverY > session.maxY()
                || leverZ < session.minZ() || leverZ > session.maxZ()) {
            player.sendMessage(Component.text("拉杆不在选区内", NamedTextColor.RED));
            return;
        }

        if (bx < session.minX() || bx > session.maxX()
                || by < session.minY() || by > session.maxY()
                || bz < session.minZ() || bz > session.maxZ()) {
            player.sendMessage(Component.text("门方块不在选区内", NamedTextColor.RED));
            redstoneLog("redstone_edit targetOutOfSelection player=" + player.getName()
                    + " template=" + session.templateName()
                    + " lever=" + leverX + "," + leverY + "," + leverZ
                    + " target=" + bx + "," + by + "," + bz
                    + " selectionMin=" + session.minX() + "," + session.minY() + "," + session.minZ()
                    + " selectionMax=" + session.maxX() + "," + session.maxY() + "," + session.maxZ());
            return;
        }

        boolean removed = removeRedstoneTargetMarker(clicked.getWorld(), bx, by, bz, leverX, leverY, leverZ);
        if (removed) {
            player.sendMessage(Component.text("已移除门方块: " + bx + "," + by + "," + bz, NamedTextColor.GRAY));
            if (plugin.getLogger() != null) {
                plugin.getLogger().info("redstone_edit removeTarget player=" + player.getName()
                        + " template=" + session.templateName()
                        + " lever=" + leverX + "," + leverY + "," + leverZ
                        + " target=" + bx + "," + by + "," + bz);
            }
            return;
        }

        spawnEditRedstoneTargetMarker(clicked.getWorld(), bx, by, bz, leverX, leverY, leverZ);
        player.sendMessage(Component.text("已记录门方块: " + bx + "," + by + "," + bz, NamedTextColor.GREEN));
        if (plugin.getLogger() != null) {
            plugin.getLogger().info("redstone_edit addTarget player=" + player.getName()
                    + " template=" + session.templateName()
                    + " lever=" + leverX + "," + leverY + "," + leverZ
                    + " target=" + bx + "," + by + "," + bz);
        }
    }

    private boolean removeRedstoneTargetMarker(World world, int targetX, int targetY, int targetZ, int leverX, int leverY, int leverZ) {
        if (world == null) return false;
        boolean removed = false;
        Location center = new Location(world, targetX + 0.5, targetY, targetZ + 0.5);
        for (Entity e : world.getNearbyEntities(center, 0.6, 0.6, 0.6)) {
            if (!(e instanceof ArmorStand as)) continue;
            if (!isMarker(as)) continue;
            if (getMarkerType(as) != SecretType.REDSTONE) continue;
            int[] lever = getRedstoneLever(as);
            if (lever == null) continue;
            if (lever[0] != leverX || lever[1] != leverY || lever[2] != leverZ) continue;
            as.remove();
            removed = true;
        }
        return removed;
    }

    private void removeMarkersAtBlock(World world, int x, int y, int z, SecretType type) {
        if (world == null || type == null) return;
        for (Entity e : world.getNearbyEntities(new Location(world, x + 0.5, y, z + 0.5), 0.6, 0.6, 0.6)) {
            if (!(e instanceof ArmorStand as)) continue;
            if (!isMarker(as)) continue;
            if (getMarkerType(as) != type) continue;
            as.remove();
        }
    }

    private SecretType toolType(ItemStack stack) {
        if (stack == null) return null;
        Material m = stack.getType();
        if (m == Material.CHEST) return SecretType.CHEST;
        if (m == Material.DIAMOND) return SecretType.DROP;
        if (m == Material.AMETHYST_SHARD) return SecretType.ESSENCE;
        if (m == Material.BAT_SPAWN_EGG) return SecretType.BAT;
        if (m == Material.CRACKED_STONE_BRICKS) return SecretType.CRACKED_BRICKS;
        if (m == Material.SKELETON_SKULL) return SecretType.TOMB;
        if (m == Material.REDSTONE) return SecretType.REDSTONE;
        if (m == Material.SLIME_BLOCK) return SecretType.STICKY_BLOCK;
        return null;
    }

    private ItemStack toolItem(Material material, String name) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.YELLOW));
            item.setItemMeta(meta);
        }
        return item;
    }

    private File secretsFile(String templateName) {
        File dir = new File(plugin.getDataFolder(), "secrets");
        if (!dir.exists()) dir.mkdirs();
        String safe = templateName.toLowerCase(Locale.ROOT);
        return new File(dir, safe + ".yml");
    }

    private File redstoneFile(String templateName) {
        File dir = new File(plugin.getDataFolder(), "redstone");
        if (!dir.exists()) dir.mkdirs();
        String safe = templateName.toLowerCase(Locale.ROOT);
        return new File(dir, safe + ".yml");
    }

    private List<SecretDef> loadRedstoneLinks(String templateName) {
        if (templateName == null || templateName.isBlank()) return List.of();
        File file = redstoneFile(templateName);
        if (file.exists()) {
            redstoneLog("loadRedstoneLinks source=dataFile template=" + templateName + " file=" + file.getAbsolutePath());
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            List<SecretDef> parsed = parseRedstoneLinks(cfg);
            redstoneLog("loadRedstoneLinks parsed template=" + templateName + " links=" + parsed.size());
            return parsed;
        }

        String safe = templateName.toLowerCase(Locale.ROOT);
        String[] candidates = new String[]{"redstone/" + safe + ".yml", safe + ".yml"};
        for (String res : candidates) {
            try (InputStream in = plugin.getResource(res)) {
                if (in == null) continue;
                redstoneLog("loadRedstoneLinks source=resource template=" + templateName + " resource=" + res);
                YamlConfiguration cfg = new YamlConfiguration();
                cfg.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                List<SecretDef> parsed = parseRedstoneLinks(cfg);
                redstoneLog("loadRedstoneLinks parsed template=" + templateName + " links=" + parsed.size());
                if (!parsed.isEmpty()) return parsed;
            } catch (Exception ignored) {
            }
        }
        redstoneLog("loadRedstoneLinks none template=" + templateName
                + " expectedFile=" + file.getAbsolutePath());
        return List.of();
    }

    private List<SecretDef> parseRedstoneLinks(YamlConfiguration cfg) {
        if (cfg == null) return List.of();
        List<?> list = cfg.getList("links");
        if (list == null || list.isEmpty()) return List.of();
        List<SecretDef> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            Object x = m.get("x");
            Object y = m.get("y");
            Object z = m.get("z");
            Object x2 = m.get("x2");
            Object y2 = m.get("y2");
            Object z2 = m.get("z2");
            if (!(x instanceof Number xn) || !(y instanceof Number yn) || !(z instanceof Number zn)) continue;
            if (!(x2 instanceof Number x2n) || !(y2 instanceof Number y2n) || !(z2 instanceof Number z2n)) continue;
            out.add(new SecretDef(SecretType.REDSTONE,
                    xn.intValue(), yn.intValue(), zn.intValue(),
                    x2n.intValue(), y2n.intValue(), z2n.intValue()
            ));
        }
        return List.copyOf(out);
    }

    private void saveRedstoneLinks(String templateName, List<SecretDef> defs) {
        if (templateName == null || templateName.isBlank()) return;
        File file = redstoneFile(templateName);
        redstoneLog("saveRedstoneLinks begin template=" + templateName + " file=" + file.getAbsolutePath()
                + " links=" + (defs != null ? defs.size() : 0));
        YamlConfiguration cfg = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        if (defs != null) {
            for (SecretDef d : defs) {
                if (d == null || d.type() != SecretType.REDSTONE) continue;
                Map<String, Object> m = new HashMap<>();
                m.put("x", d.x());
                m.put("y", d.y());
                m.put("z", d.z());
                m.put("x2", d.x2());
                m.put("y2", d.y2());
                m.put("z2", d.z2());
                list.add(m);
            }
        }
        cfg.set("version", 1);
        cfg.set("links", list);
        try {
            cfg.save(file);
            redstoneLog("saveRedstoneLinks ok template=" + templateName + " file=" + file.getAbsolutePath()
                    + " links=" + list.size());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save redstone for " + templateName + ": " + e.getMessage());
            redstoneLog("saveRedstoneLinks fail template=" + templateName + " file=" + file.getAbsolutePath()
                    + " error=" + e.getMessage());
        }
    }

    private List<SecretDef> loadSecrets(String templateName) {
        if (templateName == null || templateName.isBlank()) return List.of();
        File file = secretsFile(templateName);
        if (!file.exists()) return List.of();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        List<?> list = cfg.getList("secrets");
        if (list == null || list.isEmpty()) return List.of();
        List<SecretDef> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            Object t = m.get("type");
            Object x = m.get("x");
            Object y = m.get("y");
            Object z = m.get("z");
            Object x2 = m.get("x2");
            Object y2 = m.get("y2");
            Object z2 = m.get("z2");
            if (!(t instanceof String ts)) continue;
            SecretType type;
            try {
                type = SecretType.valueOf(ts.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            if (!(x instanceof Number xn) || !(y instanceof Number yn) || !(z instanceof Number zn)) continue;
            int ix2 = x2 instanceof Number n ? n.intValue() : -1;
            int iy2 = y2 instanceof Number n ? n.intValue() : -1;
            int iz2 = z2 instanceof Number n ? n.intValue() : -1;
            out.add(new SecretDef(type, xn.intValue(), yn.intValue(), zn.intValue(), ix2, iy2, iz2));
        }
        return List.copyOf(out);
    }

    private void saveSecrets(String templateName, List<SecretDef> defs) {
        if (templateName == null || templateName.isBlank()) return;
        File file = secretsFile(templateName);
        YamlConfiguration cfg = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        if (defs != null) {
            for (SecretDef d : defs) {
                Map<String, Object> m = new HashMap<>();
                m.put("type", d.type().name());
                m.put("x", d.x());
                m.put("y", d.y());
                m.put("z", d.z());
                if (d.x2() >= 0 && d.y2() >= 0 && d.z2() >= 0) {
                    m.put("x2", d.x2());
                    m.put("y2", d.y2());
                    m.put("z2", d.z2());
                }
                list.add(m);
            }
        }
        cfg.set("version", 2);
        cfg.set("secrets", list);
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save secrets for " + templateName + ": " + e.getMessage());
        }
    }

    private boolean isSpecialTntItem(ItemStack stack) {
        if (stack == null) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        Byte b = meta.getPersistentDataContainer().get(specialTntKey, PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }

    ItemStack specialTntItem(int amount) {
        int amt = Math.max(1, Math.min(64, amount));
        ItemStack item = new ItemStack(Material.TNT, amt);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("特殊TNT", NamedTextColor.GOLD));
            meta.getPersistentDataContainer().set(specialTntKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isSpecialTntEntity(Entity entity) {
        if (!(entity instanceof TNTPrimed tnt)) return false;
        Byte b = tnt.getPersistentDataContainer().get(specialTntKey, PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }

    private void handleExplosion(World world, RunState run, Location origin, List<Block> blockList, boolean special) {
        if (world == null || run == null || origin == null || blockList == null) return;

        List<Block> baseOriginal = List.copyOf(blockList);
        List<Block> original;
        if (special) {
            original = new ArrayList<>(baseOriginal);
            Set<Long> present = new HashSet<>();
            for (Block b : baseOriginal) {
                if (b == null) continue;
                present.add(packBlock(b.getX(), b.getY(), b.getZ()));
            }

            final int r = 5;
            final double ox = origin.getX();
            final double oy = origin.getY();
            final double oz = origin.getZ();
            final double r2 = 25.0;
            final int bx = origin.getBlockX();
            final int by = origin.getBlockY();
            final int bz = origin.getBlockZ();
            final int minY = Math.max(world.getMinHeight(), by - r);
            final int maxY = Math.min(world.getMaxHeight() - 1, by + r);

            for (int y = minY; y <= maxY; y++) {
                double dy = (y + 0.5) - oy;
                for (int x = bx - r; x <= bx + r; x++) {
                    double dx = (x + 0.5) - ox;
                    for (int z = bz - r; z <= bz + r; z++) {
                        double dz = (z + 0.5) - oz;
                        if (dx * dx + dy * dy + dz * dz > r2) continue;
                        long key = packBlock(x, y, z);
                        if (present.contains(key)) continue;
                        if (!run.breakableBlocks.containsKey(key)) continue;
                        present.add(key);
                        original.add(world.getBlockAt(x, y, z));
                    }
                }
            }
        } else {
            original = baseOriginal;
        }
        blockList.clear();

        Set<Long> crackedHit = new HashSet<>();
        Set<Long> tombHit = new HashSet<>();

        for (Block b : original) {
            if (b == null) continue;
            int x = b.getX();
            int y = b.getY();
            int z = b.getZ();
            long key = packBlock(x, y, z);

            Long crackedId = run.breakableBlocks.get(key);
            if (crackedId != null) {
                if (special) {
                    blockList.add(b);
                    crackedHit.add(crackedId);
                }
                continue;
            }

            Long tombId = tombIdAt(run, x, y, z);
            if (tombId != null) {
                if (special) {
                    tombHit.add(tombId);
                }
                continue;
            }

            if (special) continue;
            blockList.add(b);
        }

        if (!special) return;

        for (Long id : crackedHit) {
            if (id == null) continue;
            markFoundAndGrantBlessing(world, run, id);
            run.breakableBlocks.values().removeIf(v -> Objects.equals(v, id));
        }

        for (Long id : tombHit) {
            if (id == null) continue;
            int[] bounds = run.tombBounds.get(id);
            if (bounds == null || bounds.length < 6) continue;
            clearBox(world, run, bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]);
            markFoundAndGrantBlessing(world, run, id);
            run.tombBounds.remove(id);
            // 僅坟墓計分（2.5/座，最多4）；炸可破牆走 crackedHit，不經此分支
            if (plugin != null && plugin.getDungeonScoreboard() != null) {
                plugin.getDungeonScoreboard().recordTombDestroyedForScore(world.getName());
            }
        }
    }

    private static void clearBox(World world, RunState run, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (world == null) return;
        int lowY = Math.max(world.getMinHeight(), Math.min(minY, maxY));
        int highY = Math.min(world.getMaxHeight() - 1, Math.max(minY, maxY));
        int x1 = Math.min(minX, maxX);
        int x2 = Math.max(minX, maxX);
        int z1 = Math.min(minZ, maxZ);
        int z2 = Math.max(minZ, maxZ);

        for (int y = lowY; y <= highY; y++) {
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    long key = packBlock(x, y, z);
                    if (run != null && (run.chestBlocks.containsKey(key) || run.essenceBlocks.containsKey(key))) {
                        continue;
                    }
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    private static void clearBox(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (world == null) return;
        int lowY = Math.max(world.getMinHeight(), Math.min(minY, maxY));
        int highY = Math.min(world.getMaxHeight() - 1, Math.max(minY, maxY));
        int x1 = Math.min(minX, maxX);
        int x2 = Math.max(minX, maxX);
        int z1 = Math.min(minZ, maxZ);
        int z2 = Math.max(minZ, maxZ);

        for (int y = lowY; y <= highY; y++) {
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    private static boolean isInAnyTomb(RunState run, int x, int y, int z) {
        if (run == null) return false;
        return tombIdAt(run, x, y, z) != null;
    }

    private static Long tombIdAt(RunState run, int x, int y, int z) {
        if (run == null) return null;
        for (Map.Entry<Long, int[]> e : run.tombBounds.entrySet()) {
            int[] b = e.getValue();
            if (b == null || b.length < 6) continue;
            if (x < b[0] || x > b[3]) continue;
            if (y < b[1] || y > b[4]) continue;
            if (z < b[2] || z > b[5]) continue;
            return e.getKey();
        }
        return null;
    }

    private static Long stickyIdAt(RunState run, int x, int y, int z) {
        if (run == null) return null;
        for (Map.Entry<Long, int[]> e : run.stickyBounds.entrySet()) {
            int[] b = e.getValue();
            if (b == null || b.length < 6) continue;
            if (x < b[0] || x > b[3]) continue;
            if (y < b[1] || y > b[4]) continue;
            if (z < b[2] || z > b[5]) continue;
            return e.getKey();
        }
        return null;
    }

    private static boolean isStickyNode(World world, int x, int y, int z, int[] bounds) {
        if (world == null || bounds == null || bounds.length < 6) return false;
        if (x < bounds[0] || x > bounds[3]) return false;
        if (y < bounds[1] || y > bounds[4]) return false;
        if (z < bounds[2] || z > bounds[5]) return false;
        Block b = world.getBlockAt(x, y, z);
        Material type = b.getType();
        if (type.isAir()) return false;
        if (STICKY_TAG_MATERIALS.contains(type)) return true;
        return hasAdjacentStickyTag(world, x, y, z, bounds);
    }

    private static boolean isStickyNode(World world, int x, int y, int z) {
        if (world == null) return false;
        Block b = world.getBlockAt(x, y, z);
        Material type = b.getType();
        if (type.isAir()) return false;
        if (STICKY_TAG_MATERIALS.contains(type)) return true;
        return hasAdjacentStickyTag(world, x, y, z);
    }

    private static boolean hasAdjacentStickyTag(World world, int x, int y, int z) {
        if (world == null) return false;
        if (STICKY_TAG_MATERIALS.contains(world.getBlockAt(x + 1, y, z).getType())) return true;
        if (STICKY_TAG_MATERIALS.contains(world.getBlockAt(x - 1, y, z).getType())) return true;
        if (STICKY_TAG_MATERIALS.contains(world.getBlockAt(x, y + 1, z).getType())) return true;
        if (STICKY_TAG_MATERIALS.contains(world.getBlockAt(x, y - 1, z).getType())) return true;
        if (STICKY_TAG_MATERIALS.contains(world.getBlockAt(x, y, z + 1).getType())) return true;
        return STICKY_TAG_MATERIALS.contains(world.getBlockAt(x, y, z - 1).getType());
    }

    private static boolean hasAdjacentStickyTag(World world, int x, int y, int z, int[] bounds) {
        if (world == null || bounds == null || bounds.length < 6) return false;
        if (hasStickyTagAt(world, x + 1, y, z, bounds)) return true;
        if (hasStickyTagAt(world, x - 1, y, z, bounds)) return true;
        if (hasStickyTagAt(world, x, y + 1, z, bounds)) return true;
        if (hasStickyTagAt(world, x, y - 1, z, bounds)) return true;
        if (hasStickyTagAt(world, x, y, z + 1, bounds)) return true;
        return hasStickyTagAt(world, x, y, z - 1, bounds);
    }

    private static boolean hasStickyTagAt(World world, int x, int y, int z, int[] bounds) {
        if (world == null || bounds == null || bounds.length < 6) return false;
        if (x < bounds[0] || x > bounds[3]) return false;
        if (y < bounds[1] || y > bounds[4]) return false;
        if (z < bounds[2] || z > bounds[5]) return false;
        Material type = world.getBlockAt(x, y, z).getType();
        return STICKY_TAG_MATERIALS.contains(type);
    }

    private static void tryEnqueueStickyNeighbor(ArrayDeque<Long> q, Set<Long> visited, int x, int y, int z, int[] bounds) {
        if (q == null || visited == null || bounds == null || bounds.length < 6) return;
        if (x < bounds[0] || x > bounds[3]) return;
        if (y < bounds[1] || y > bounds[4]) return;
        if (z < bounds[2] || z > bounds[5]) return;
        long p = packBlock(x, y, z);
        if (!visited.add(p)) return;
        q.add(p);
    }

    private static void tryEnqueueStickyNeighbor(ArrayDeque<Long> q, Set<Long> visited, int x, int y, int z, int yMin, int yMax, GroupState group, int originX, int originZ) {
        if (q == null || visited == null) return;
        if (y < yMin || y > yMax) return;
        if (group != null && group.hasPlacement()) {
            if (!isWithinGroupFootprintXZ(group, x, z)) return;
        } else {
            if (Math.abs(x - originX) > 96) return;
            if (Math.abs(z - originZ) > 96) return;
        }
        long p = packBlock(x, y, z);
        if (!visited.add(p)) return;
        q.add(p);
    }

    private static boolean isWithinGroupFootprintXZ(GroupState group, int x, int z) {
        if (group == null || !group.hasPlacement()) return false;
        int dx = x - group.originX;
        int dz = z - group.originZ;
        return inverseRotateLocalXZ(dx, dz, group.rotation, group.sizeX, group.sizeZ) != null;
    }

    private static int unpackBlockX(long key) {
        int x = (int) (key >> 38);
        if (x >= 0x2000000) x -= 0x4000000;
        return x;
    }

    private static int unpackBlockZ(long key) {
        int z = (int) ((key >> 12) & 0x3FFFFFF);
        if (z >= 0x2000000) z -= 0x4000000;
        return z;
    }

    private static int unpackBlockY(long key) {
        int y = (int) (key & 0xFFF);
        if (y >= 0x800) y -= 0x1000;
        return y;
    }

    private static int[] rotateLocalXZ(int x, int z, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
        return switch (rotation) {
            case R0 -> new int[]{x, z};
            case R90 -> new int[]{z, sizeX - 1 - x};
            case R180 -> new int[]{sizeX - 1 - x, sizeZ - 1 - z};
            case R270 -> new int[]{sizeZ - 1 - z, x};
        };
    }

    private static int[] inverseRotateLocalXZ(int rotatedX, int rotatedZ, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
        if (rotation == null) return null;
        int x;
        int z;
        switch (rotation) {
            case R0 -> {
                x = rotatedX;
                z = rotatedZ;
            }
            case R90 -> {
                x = sizeX - 1 - rotatedZ;
                z = rotatedX;
            }
            case R180 -> {
                x = sizeX - 1 - rotatedX;
                z = sizeZ - 1 - rotatedZ;
            }
            case R270 -> {
                x = rotatedZ;
                z = sizeZ - 1 - rotatedX;
            }
            default -> {
                return null;
            }
        }
        if (x < 0 || x >= sizeX) return null;
        if (z < 0 || z >= sizeZ) return null;
        return new int[]{x, z};
    }

    private static int[] rotateBoxLocalXZ(int x1, int z1, int x2, int z2, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        int[] p1 = rotateLocalXZ(minX, minZ, rotation, sizeX, sizeZ);
        int[] p2 = rotateLocalXZ(minX, maxZ, rotation, sizeX, sizeZ);
        int[] p3 = rotateLocalXZ(maxX, minZ, rotation, sizeX, sizeZ);
        int[] p4 = rotateLocalXZ(maxX, maxZ, rotation, sizeX, sizeZ);

        int outMinX = Math.min(Math.min(p1[0], p2[0]), Math.min(p3[0], p4[0]));
        int outMaxX = Math.max(Math.max(p1[0], p2[0]), Math.max(p3[0], p4[0]));
        int outMinZ = Math.min(Math.min(p1[1], p2[1]), Math.min(p3[1], p4[1]));
        int outMaxZ = Math.max(Math.max(p1[1], p2[1]), Math.max(p3[1], p4[1]));

        return new int[]{outMinX, outMinZ, outMaxX, outMaxZ};
    }

    private static long packCell(int x, int y) {
        return ((long) (x & 0xFFFF) << 16) | (long) (y & 0xFFFF);
    }

    private static long packBlock(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (long) (y & 0xFFF);
    }

    private static long packSecretId(int x, int y, int z, SecretType type) {
        return (packBlock(x, y, z) << 3) | (long) (type != null ? type.ordinal() : 0);
    }

    private void markFoundAndGrantBlessing(World world, RunState run, long secretId) {
        if (world == null || run == null) return;
        boolean newlyFound = run.markFound(secretId);
        if (!newlyFound) return;
        if (plugin == null || plugin.getDungeonBlessingManager() == null) return;
        boolean puzzle = run.isPuzzleSecret(secretId);
        DungeonBlessingManager.BlessingType type = plugin.getDungeonBlessingManager().onSecretFound(world.getName(), puzzle);
        int levelNow = plugin.getDungeonBlessingManager().getLevel(world.getName(), type);
        plugin.getDungeonBlessingManager().broadcastGain(world, type, levelNow);
    }

    private static final class RunState {
        private final String worldName;
        private final int width;
        private final int height;
        private final Map<Long, String> cellToGroup = new HashMap<>();
        private final Map<String, GroupState> groups = new HashMap<>();
        private final Map<Long, String> secretToGroup = new HashMap<>();
        private final Map<Long, Long> chestBlocks = new HashMap<>();
        private final Map<Long, Long> essenceBlocks = new HashMap<>();
        private final Map<Long, Long> breakableBlocks = new HashMap<>();
        private final Map<Long, List<RedstoneDoorAction>> redstoneDoorsByLever = new HashMap<>();
        private final Map<UUID, Long> batById = new HashMap<>();
        private final Map<Long, UUID> batEntityBySecret = new HashMap<>();
        private final Map<UUID, double[]> batOriginById = new HashMap<>();
        private final Map<Long, int[]> tombBounds = new HashMap<>();
        private final Map<Long, int[]> stickyBounds = new HashMap<>();
        private final Map<Long, Long> stickyTriggerBlocks = new HashMap<>();
        private final Map<Long, ChestRewardType> chestRewardById = new HashMap<>();
        private final Set<String> solvedPuzzleGroups = new HashSet<>();
        private final Set<String> failedPuzzleGroups = new HashSet<>();
        private final Set<String> lootedPuzzleGroups = new HashSet<>();
        private final Set<Long> triggeredTrapChests = new HashSet<>();
        private boolean chestRewardsReady;
        private boolean testMode;
        private int testOriginX;
        private int testOriginZ;
        private int testRoomScale;

        private RunState(String worldName, int width, int height) {
            this.worldName = worldName;
            this.width = width;
            this.height = height;
        }

        private void enableTestMode(int originX, int originZ, int roomScale) {
            this.testMode = true;
            this.testOriginX = originX;
            this.testOriginZ = originZ;
            this.testRoomScale = Math.max(1, roomScale);
        }

        private boolean isFound(long secretId) {
            for (GroupState g : groups.values()) {
                if (g.found.contains(secretId)) return true;
            }
            return false;
        }

        private void ensureChestAssignments() {
            if (chestRewardsReady) return;

            Set<Long> forcedBlessing = new HashSet<>();
            List<Long> normalIds = new ArrayList<>();
            for (GroupState g : groups.values()) {
                boolean force = blessingOnlyRoomTemplate(g.templateName);
                for (SecretInstance s : g.secrets.values()) {
                    if (s.type() != SecretType.CHEST) continue;
                    if (g.isTrapRoom01TrapChest(s)) continue;
                    if (force) forcedBlessing.add(s.id());
                    else normalIds.add(s.id());
                }
            }

            if (forcedBlessing.isEmpty() && normalIds.isEmpty()) {
                chestRewardsReady = true;
                return;
            }

            for (Long id : forcedBlessing) {
                if (id == null) continue;
                chestRewardById.put(id, ChestRewardType.BLESSING);
            }

            if (!normalIds.isEmpty()) {
                normalIds.sort(Long::compare);
                Random random = new Random(Objects.hash(worldName, width, height, forcedBlessing.size(), normalIds.size()));
                for (int i = normalIds.size() - 1; i > 0; i--) {
                    int j = random.nextInt(i + 1);
                    Long a = normalIds.get(i);
                    normalIds.set(i, normalIds.get(j));
                    normalIds.set(j, a);
                }

                int itemCount = normalIds.size() / 2;
                for (int i = 0; i < normalIds.size(); i++) {
                    long id = normalIds.get(i);
                    if (forcedBlessing.contains(id)) continue;
                    chestRewardById.put(id, i < itemCount ? ChestRewardType.ITEM : ChestRewardType.BLESSING);
                }
            }

            chestRewardsReady = true;
        }

        private boolean markFound(long secretId) {
            for (GroupState g : groups.values()) {
                SecretInstance s = g.secrets.get(secretId);
                if (s == null) continue;
                if (!countsAsSecret(s.type())) return false;
                return g.found.add(secretId);
            }
            return false;
        }

        private boolean isPuzzleSecret(long secretId) {
            String groupKey = secretToGroup.get(secretId);
            if (groupKey == null) return false;
            GroupState g = groups.get(groupKey);
            if (g == null || g.templateName == null) return false;
            return g.templateName.trim().toLowerCase(Locale.ROOT).startsWith("puzzle_room");
        }

        /** 與地圖統計一致的「可計分秘密」已發現總數（跨所有房間組） */
        private int aggregateFoundScoringSecrets() {
            int n = 0;
            for (GroupState g : groups.values()) {
                if (g != null) n += g.foundSecretsCount();
            }
            return n;
        }

        /** 與地圖統計一致的「可計分秘密」總數 */
        private int aggregateTotalScoringSecrets() {
            int n = 0;
            for (GroupState g : groups.values()) {
                if (g != null) n += g.totalSecrets();
            }
            return n;
        }

        private int totalSecrets() {
            int total = 0;
            for (GroupState g : groups.values()) total += g.totalSecrets();
            return total;
        }

        private boolean isRoomSecretsCompleted(int cellX, int cellY) {
            String groupKey = cellToGroup.get(packCell(cellX, cellY));
            if (groupKey == null) return false;
            GroupState group = groups.get(groupKey);
            if (group == null) return false;
            int total = group.totalSecrets();
            if (total <= 0) return false;
            return group.foundSecretsCount() >= total;
        }
    }

    private static final class GroupState {
        private final String groupKey;
        private final String templateName;
        private final Set<Long> cells = new HashSet<>();
        private final Map<Long, SecretInstance> secrets = new HashMap<>();
        private final Set<Long> found = new HashSet<>();
        private boolean hasPlacement;
        private int originX;
        private int originY;
        private int originZ;
        private StructureTemplate.Rotation rotation;
        private int sizeX;
        private int sizeZ;

        private GroupState(String groupKey, String templateName) {
            this.groupKey = groupKey;
            this.templateName = templateName;
        }

        private void setPlacement(int x, int y, int z, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
            if (hasPlacement) return;
            this.hasPlacement = true;
            this.originX = x;
            this.originY = y;
            this.originZ = z;
            this.rotation = rotation;
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
        }

        private boolean hasPlacement() {
            return hasPlacement && rotation != null && sizeX > 0 && sizeZ > 0;
        }

        private int totalSecrets() {
            boolean isPuzzleRoom04 = templateName != null && "puzzle_room_04".equalsIgnoreCase(templateName.trim());
            boolean anyChest = false;
            int total = 0;
            for (SecretInstance s : secrets.values()) {
                if (s == null) continue;
                if (!countsAsSecret(s.type())) continue;
                if (isTrapRoom01TrapChest(s)) continue;
                if (isPuzzleRoom04 && s.type() == SecretType.CHEST) {
                    anyChest = true;
                    continue;
                }
                total++;
            }
            if (isPuzzleRoom04 && anyChest) total++;
            return total;
        }

        private int foundSecretsCount() {
            boolean isPuzzleRoom04 = templateName != null && "puzzle_room_04".equalsIgnoreCase(templateName.trim());
            boolean anyChestFound = false;
            int total = 0;
            for (Long id : found) {
                if (id == null) continue;
                SecretInstance s = secrets.get(id);
                if (s == null) continue;
                if (!countsAsSecret(s.type())) continue;
                if (isTrapRoom01TrapChest(s)) continue;
                if (isPuzzleRoom04 && s.type() == SecretType.CHEST) {
                    anyChestFound = true;
                    continue;
                }
                total++;
            }
            if (isPuzzleRoom04 && anyChestFound) total++;
            return total;
        }

        private boolean isTrapRoom01TrapChest(SecretInstance s) {
            if (s == null) return false;
            if (templateName == null || !"trap_room_01".equalsIgnoreCase(templateName.trim())) return false;
            if (s.type() != SecretType.CHEST) return false;
            if (!hasPlacement()) return false;
            int dx = s.bx() - originX;
            int dz = s.bz() - originZ;
            int dy = s.by() - originY;
            int[] local = inverseRotateLocalXZ(dx, dz, rotation, sizeX, sizeZ);
            return local != null && local[0] == 27 && local[1] == 23 && (dy == 21 || dy == 22 || dy == 23);
        }
    }
}
