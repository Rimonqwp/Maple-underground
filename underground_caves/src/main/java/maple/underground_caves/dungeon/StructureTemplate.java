package maple.underground_caves.dungeon;

import org.bukkit.Bukkit;
import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Hopper;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.Switch;
import org.bukkit.block.data.type.TripwireHook;
import org.bukkit.block.data.type.Wall;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.InventoryHolder;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record StructureTemplate(
        int sizeX,
        int sizeY,
        int sizeZ,
        List<TemplateBlock> blocks,
        List<DoorMarker> doorMarkers,
        Map<BlockPos, SkullData> skulls,
        List<BlockPos> portalMarkers
) {
    public record TemplateBlock(int x, int y, int z, String blockData) {}

    public record DoorMarker(int x, int y, int z, BlockFace facing) {}

    /** boss_room 傳送門位置標記：終界傳送門框（有方向）、紫珀柱。儲存時記為空氣。 */
    private static boolean isPortalMarker(String materialName) {
        if (materialName == null) return false;
        String n = materialName.toUpperCase(Locale.ROOT);
        return "END_PORTAL_FRAME".equals(n) || "PURPUR_PILLAR".equals(n);
    }

    public record BlockPos(int x, int y, int z) {}

    public record SkullData(String uuid, String name, String skinUrl, String capeUrl) {}

    public enum Rotation {
        R0,
        R90,
        R180,
        R270
    }

    public int maxFilledY() {
        int max = 0;
        for (TemplateBlock b : blocks) {
            if (isDoorMarkerBlockData(b.blockData())) continue;
            if (b.y() > max) max = b.y();
        }
        return max;
    }

    public static StructureTemplate capture(World world, Location p1, Location p2) {
        return capture(world, p1, p2, null);
    }

    /**
     * @param templateName 若為 exit_room 開頭，選區內的偵測器（Observer）視為傳送門位置而非門，不會當成連接其他房間的通道
     */
    public static StructureTemplate capture(World world, Location p1, Location p2, String templateName) {
        int minX = Math.min(p1.getBlockX(), p2.getBlockX());
        int minY = Math.min(p1.getBlockY(), p2.getBlockY());
        int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
        int maxX = Math.max(p1.getBlockX(), p2.getBlockX());
        int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
        int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());

        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;

        List<TemplateBlock> blocks = new ArrayList<>();
        List<DoorMarker> doors = new ArrayList<>();
        Map<BlockPos, SkullData> skulls = new HashMap<>();
        List<BlockPos> portalMarkers = new ArrayList<>();
        boolean exitRoomTemplate = templateName != null && templateName.toLowerCase(java.util.Locale.ROOT).startsWith("exit_room");

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (b.getType().isAir()) continue;
                    BlockData data = b.getBlockData();
                    int rx = x - minX;
                    int ry = y - minY;
                    int rz = z - minZ;

                    if (isPortalMarker(b.getType().name())) {
                        portalMarkers.add(new BlockPos(rx, ry, rz));
                        blocks.add(new TemplateBlock(rx, ry, rz, "minecraft:air"));
                        continue;
                    }
                    if (exitRoomTemplate && "OBSERVER".equalsIgnoreCase(b.getType().name())) {
                        portalMarkers.add(new BlockPos(rx, ry, rz));
                        blocks.add(new TemplateBlock(rx, ry, rz, "minecraft:air"));
                        continue;
                    }

                    BlockFace markerFacing = null;
                    if (isDoorMarker(b.getType().name())) {
                        BlockFace inferred = inferFacingFromBoundsLoose(rx, rz, sizeX, sizeZ);
                        BlockFace facing = inferred;
                        if (!isWaxedCopperBulb(b.getType().name()) && data instanceof Directional directional) {
                            BlockFace f = directional.getFacing();
                            if (HORIZONTAL.contains(f)) {
                                facing = f;
                            }
                        }
                        doors.add(new DoorMarker(rx, ry, rz, facing));
                        markerFacing = facing;
                    }

                    String dataString = data.getAsString();
                    if ("OBSERVER".equalsIgnoreCase(b.getType().name()) && markerFacing != null) {
                        dataString = observerBlockData(markerFacing);
                    }
                    blocks.add(new TemplateBlock(rx, ry, rz, dataString));

                    SkullData skullData = captureSkullData(b);
                    if (skullData != null) {
                        skulls.put(new BlockPos(rx, ry, rz), skullData);
                    }
                }
            }
        }

        return trimBottomEmpty(new StructureTemplate(sizeX, sizeY, sizeZ, blocks, doors, skulls, portalMarkers));
    }

    public static StructureTemplate captureDoorVariant(World world, Location p1, Location p2) {
        int minX = Math.min(p1.getBlockX(), p2.getBlockX());
        int minY = Math.min(p1.getBlockY(), p2.getBlockY());
        int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
        int maxX = Math.max(p1.getBlockX(), p2.getBlockX());
        int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
        int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());

        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;

        List<TemplateBlock> blocks = new ArrayList<>();
        List<DoorMarker> doors = new ArrayList<>();
        Map<BlockPos, SkullData> skulls = new HashMap<>();
        List<BlockPos> portalMarkers = new ArrayList<>();
        Set<Long> existing = new HashSet<>();

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block b = world.getBlockAt(x, y, z);
                    BlockData data = b.getBlockData();
                    int rx = x - minX;
                    int ry = y - minY;
                    int rz = z - minZ;

                    if (isPortalMarker(b.getType().name())) {
                        portalMarkers.add(new BlockPos(rx, ry, rz));
                        long key = pack(rx, ry, rz);
                        if (!existing.contains(key)) {
                            existing.add(key);
                            blocks.add(new TemplateBlock(rx, ry, rz, "minecraft:air"));
                        }
                        continue;
                    }

                    BlockFace markerFacing = null;
                    if (isDoorMarker(b.getType().name())) {
                        BlockFace inferred = inferFacingFromBoundsLoose(rx, rz, sizeX, sizeZ);
                        BlockFace facing = inferred;
                        if (!isWaxedCopperBulb(b.getType().name()) && data instanceof Directional directional) {
                            BlockFace f = directional.getFacing();
                            if (HORIZONTAL.contains(f)) {
                                facing = f;
                            }
                        }
                        doors.add(new DoorMarker(rx, ry, rz, facing));
                        markerFacing = facing;
                    }

                    if (b.getType().isAir()) continue;
                    long key = pack(rx, ry, rz);
                    if (existing.contains(key)) continue;
                    existing.add(key);
                    String dataString = data.getAsString();
                    if ("OBSERVER".equalsIgnoreCase(b.getType().name()) && markerFacing != null) {
                        dataString = observerBlockData(markerFacing);
                    }
                    blocks.add(new TemplateBlock(rx, ry, rz, dataString));

                    SkullData skullData = captureSkullData(b);
                    if (skullData != null) {
                        skulls.put(new BlockPos(rx, ry, rz), skullData);
                    }
                }
            }
        }

        for (DoorMarker d : doors) {
            addDoorOverlayBlocks(world, minX, minY, minZ, sizeX, sizeY, sizeZ, d, existing, blocks);
        }

        return trimBottomEmpty(new StructureTemplate(sizeX, sizeY, sizeZ, blocks, doors, skulls, portalMarkers));
    }

    public static List<int[]> doorVolumePositions(DoorMarker d) {
        int half = 1;
        int x0 = d.x();
        int y0 = d.y();
        int z0 = d.z();

        List<int[]> positions = new ArrayList<>(12);
        for (int dy = 0; dy < 4; dy++) {
            for (int w = -half; w <= half; w++) {
                if (d.facing() == BlockFace.EAST || d.facing() == BlockFace.WEST) {
                    positions.add(new int[]{x0, y0 + dy, z0 + w});
                } else {
                    positions.add(new int[]{x0 + w, y0 + dy, z0});
                }
            }
        }
        return positions;
    }

    public static void addDoorOverlayIncludePositions(Set<Long> include, DoorMarker marker, int sizeX, int sizeY, int sizeZ) {
        int x0 = marker.x();
        int y0 = marker.y();
        int z0 = marker.z();

        for (int dy = -DOOR_OVERLAY_Y_BELOW; dy < 4 + DOOR_OVERLAY_Y_ABOVE; dy++) {
            int y = y0 + dy;
            if (y < 0 || y >= sizeY) continue;
            for (int dx = -DOOR_OVERLAY_RADIUS; dx <= DOOR_OVERLAY_RADIUS; dx++) {
                int x = x0 + dx;
                if (x < 0 || x >= sizeX) continue;
                for (int dz = -DOOR_OVERLAY_RADIUS; dz <= DOOR_OVERLAY_RADIUS; dz++) {
                    int z = z0 + dz;
                    if (z < 0 || z >= sizeZ) continue;
                    include.add(packPos(x, y, z));
                }
            }
        }
    }

    private static void addDoorOverlayBlocks(World world, int originX, int originY, int originZ, int sizeX, int sizeY, int sizeZ, DoorMarker marker, Set<Long> existing, List<TemplateBlock> blocks) {
        int x0 = marker.x();
        int y0 = marker.y();
        int z0 = marker.z();

        for (int dy = -DOOR_OVERLAY_Y_BELOW; dy < 4 + DOOR_OVERLAY_Y_ABOVE; dy++) {
            int ry = y0 + dy;
            if (ry < 0 || ry >= sizeY) continue;
            for (int dx = -DOOR_OVERLAY_RADIUS; dx <= DOOR_OVERLAY_RADIUS; dx++) {
                int rx = x0 + dx;
                if (rx < 0 || rx >= sizeX) continue;
                for (int dz = -DOOR_OVERLAY_RADIUS; dz <= DOOR_OVERLAY_RADIUS; dz++) {
                    int rz = z0 + dz;
                    if (rz < 0 || rz >= sizeZ) continue;
                    long key = pack(rx, ry, rz);
                    if (existing.contains(key)) continue;
                    existing.add(key);
                    Block b = world.getBlockAt(originX + rx, originY + ry, originZ + rz);
                    blocks.add(new TemplateBlock(rx, ry, rz, b.getBlockData().getAsString()));
                }
            }
        }
    }

    private static boolean isWaxedCopperBulb(String materialName) {
        String n = materialName.toUpperCase(Locale.ROOT);
        return n.contains("WAXED") && n.contains("COPPER_BULB");
    }

    private static boolean isDoorMarker(String materialName) {
        if (isWaxedCopperBulb(materialName)) return true;
        return "OBSERVER".equalsIgnoreCase(materialName);
    }

    public static StructureTemplate load(File file) throws IOException {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int sizeX = yaml.getInt("sizeX");
        int sizeY = yaml.getInt("sizeY");
        int sizeZ = yaml.getInt("sizeZ");

        List<String> rawBlocks = yaml.getStringList("blocks");
        List<TemplateBlock> blocks = new ArrayList<>(rawBlocks.size());
        for (String s : rawBlocks) {
            String[] parts = s.split("\\|", 4);
            if (parts.length < 4) continue;
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            blocks.add(new TemplateBlock(x, y, z, parts[3]));
        }

        List<String> rawDoors = yaml.getStringList("doors");
        List<DoorMarker> doors = new ArrayList<>(rawDoors.size());
        for (String s : rawDoors) {
            String[] parts = s.split("\\|", 4);
            if (parts.length < 4) continue;
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            BlockFace facing = BlockFace.valueOf(parts[3]);
            BlockFace inferred = inferFacingFromBoundsLoose(x, z, sizeX, sizeZ);
            if (facing == BlockFace.SELF || !HORIZONTAL.contains(facing)) {
                facing = inferred;
            }
            doors.add(new DoorMarker(x, y, z, facing));
        }

        Map<Long, Integer> indexByPos = new HashMap<>();
        for (int i = 0; i < blocks.size(); i++) {
            TemplateBlock b = blocks.get(i);
            indexByPos.put(pack(b.x(), b.y(), b.z()), i);
        }

        for (DoorMarker d : doors) {
            Integer idx = indexByPos.get(pack(d.x(), d.y(), d.z()));
            if (idx == null) continue;
            TemplateBlock b = blocks.get(idx);
            if (!isObserverBlockData(b.blockData())) continue;
            blocks.set(idx, new TemplateBlock(b.x(), b.y(), b.z(), observerBlockData(d.facing())));
        }

        Set<Long> existing = new HashSet<>();
        for (TemplateBlock b : blocks) {
            existing.add(pack(b.x(), b.y(), b.z()));
        }
        for (DoorMarker d : doors) {
            if (d.x() < 0 || d.x() >= sizeX || d.y() < 0 || d.y() >= sizeY || d.z() < 0 || d.z() >= sizeZ) {
                continue;
            }
            if (existing.contains(pack(d.x(), d.y(), d.z()))) continue;
            blocks.add(new TemplateBlock(d.x(), d.y(), d.z(), observerBlockData(d.facing())));
        }

        Map<BlockPos, SkullData> skulls = new HashMap<>();
        List<String> rawSkulls = yaml.getStringList("skulls");
        for (String s : rawSkulls) {
            String[] parts = s.split("\\|", 7);
            if (parts.length < 3) continue;
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            String uuid = parts.length > 3 ? emptyToNull(parts[3]) : null;
            String name = parts.length > 4 ? emptyToNull(parts[4]) : null;
            String skin = parts.length > 5 ? emptyToNull(parts[5]) : null;
            String cape = parts.length > 6 ? emptyToNull(parts[6]) : null;
            skulls.put(new BlockPos(x, y, z), new SkullData(uuid, name, skin, cape));
        }

        List<BlockPos> portalMarkers = new ArrayList<>();
        List<String> rawPortal = yaml.getStringList("portalMarkers");
        if (rawPortal != null) {
            for (String s : rawPortal) {
                String[] parts = s.split("\\|", 3);
                if (parts.length < 3) continue;
                portalMarkers.add(new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
            }
        }

        return trimBottomEmpty(new StructureTemplate(sizeX, sizeY, sizeZ, blocks, doors, skulls, portalMarkers));
    }

    private static StructureTemplate trimBottomEmpty(StructureTemplate template) {
        if (template == null || template.blocks() == null || template.blocks().isEmpty()) return template;

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (TemplateBlock b : template.blocks()) {
            if (b.y() < minY) minY = b.y();
            if (b.y() > maxY) maxY = b.y();
        }
        if (minY <= 0) return template;

        int newSizeY = (maxY - minY) + 1;
        List<TemplateBlock> shiftedBlocks = new ArrayList<>(template.blocks().size());
        for (TemplateBlock b : template.blocks()) {
            shiftedBlocks.add(new TemplateBlock(b.x(), b.y() - minY, b.z(), b.blockData()));
        }

        List<DoorMarker> shiftedDoors = new ArrayList<>(template.doorMarkers().size());
        for (DoorMarker d : template.doorMarkers()) {
            shiftedDoors.add(new DoorMarker(d.x(), d.y() - minY, d.z(), d.facing()));
        }

        Map<BlockPos, SkullData> shiftedSkulls = new HashMap<>();
        if (template.skulls() != null && !template.skulls().isEmpty()) {
            for (Map.Entry<BlockPos, SkullData> e : template.skulls().entrySet()) {
                BlockPos p = e.getKey();
                shiftedSkulls.put(new BlockPos(p.x(), p.y() - minY, p.z()), e.getValue());
            }
        }

        List<BlockPos> shiftedPortals = new ArrayList<>();
        if (template.portalMarkers() != null && !template.portalMarkers().isEmpty()) {
            for (BlockPos p : template.portalMarkers()) {
                shiftedPortals.add(new BlockPos(p.x(), p.y() - minY, p.z()));
            }
        }

        return new StructureTemplate(template.sizeX(), newSizeY, template.sizeZ(), shiftedBlocks, shiftedDoors, shiftedSkulls, shiftedPortals);
    }

    public static BlockFace inferDoorFacingFromBounds(int x, int z, int sizeX, int sizeZ) {
        return inferFacingFromBoundsLoose(x, z, sizeX, sizeZ);
    }

    public static BlockFace resolveDoorFacing(DoorMarker marker, int sizeX, int sizeZ) {
        if (marker == null) return BlockFace.SELF;
        int cellStride = 32;
        boolean gridX = sizeX >= (cellStride - 1) && ((sizeX + 1) % cellStride == 0);
        boolean gridZ = sizeZ >= (cellStride - 1) && ((sizeZ + 1) % cellStride == 0);
        if (gridX && gridZ) {
            int localX = Math.floorMod(marker.x(), cellStride);
            int localZ = Math.floorMod(marker.z(), cellStride);
            return inferDoorFacingFromBounds(localX, localZ, cellStride, cellStride);
        }
        return inferDoorFacingFromBounds(marker.x(), marker.z(), sizeX, sizeZ);
    }

    private static BlockFace inferFacingFromBoundsLoose(int x, int z, int sizeX, int sizeZ) {
        int distWest = Math.max(0, x);
        int distEast = Math.max(0, (sizeX - 1) - x);
        int distNorth = Math.max(0, z);
        int distSouth = Math.max(0, (sizeZ - 1) - z);

        int best = distWest;
        BlockFace face = BlockFace.WEST;

        if (distEast < best) {
            best = distEast;
            face = BlockFace.EAST;
        }
        if (distNorth < best) {
            best = distNorth;
            face = BlockFace.NORTH;
        }
        if (distSouth < best) {
            face = BlockFace.SOUTH;
        }

        return face;
    }

    private static boolean isDoorMarkerBlockData(String blockData) {
        String s = blockData.toLowerCase(Locale.ROOT);
        if (s.contains("minecraft:observer")) return true;
        return s.contains("copper_bulb") && s.contains("waxed");
    }

    private static boolean isObserverBlockData(String blockData) {
        return blockData.toLowerCase(Locale.ROOT).contains("minecraft:observer");
    }

    private static boolean isAirBlockData(String blockData) {
        String s = blockData.toLowerCase(Locale.ROOT);
        return s.startsWith("minecraft:air") || s.startsWith("minecraft:cave_air") || s.startsWith("minecraft:void_air");
    }

    private static String observerBlockData(BlockFace facing) {
        BlockFace f = facing == null ? BlockFace.NORTH : facing;
        if (!HORIZONTAL.contains(f)) f = BlockFace.NORTH;
        BlockData data = Bukkit.createBlockData("minecraft:observer");
        if (data instanceof Directional directional) {
            directional.setFacing(f);
            return directional.getAsString();
        }
        return data.getAsString();
    }

    private static long pack(int x, int y, int z) {
        return (((long) x) << 42) ^ (((long) y) << 21) ^ (z & 0x1FFFFF);
    }

    private static int unpackX(long p) { return (int) ((p >> 42) & 0x1FFFFF); }
    private static int unpackY(long p) { return (int) ((p >> 21) & 0x1FFFFF); }
    private static int unpackZ(long p) { return (int) (p & 0x1FFFFF); }

    public static long packPos(int x, int y, int z) {
        return pack(x, y, z);
    }

    public void save(File file) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("sizeX", sizeX);
        yaml.set("sizeY", sizeY);
        yaml.set("sizeZ", sizeZ);

        List<String> rawBlocks = new ArrayList<>(blocks.size());
        for (TemplateBlock b : blocks) {
            rawBlocks.add(b.x() + "|" + b.y() + "|" + b.z() + "|" + b.blockData());
        }
        yaml.set("blocks", rawBlocks);

        List<String> rawDoors = new ArrayList<>(doorMarkers.size());
        for (DoorMarker d : doorMarkers) {
            rawDoors.add(d.x() + "|" + d.y() + "|" + d.z() + "|" + d.facing().name());
        }
        yaml.set("doors", rawDoors);

        if (skulls != null && !skulls.isEmpty()) {
            List<String> rawSkulls = new ArrayList<>(skulls.size());
            for (Map.Entry<BlockPos, SkullData> e : skulls.entrySet()) {
                BlockPos p = e.getKey();
                SkullData s = e.getValue();
                rawSkulls.add(p.x() + "|" + p.y() + "|" + p.z() + "|"
                        + nullToEmpty(s.uuid()) + "|" + nullToEmpty(s.name()) + "|"
                        + nullToEmpty(s.skinUrl()) + "|" + nullToEmpty(s.capeUrl()));
            }
            yaml.set("skulls", rawSkulls);
        }

        if (portalMarkers != null && !portalMarkers.isEmpty()) {
            List<String> rawPortal = new ArrayList<>(portalMarkers.size());
            for (BlockPos p : portalMarkers) {
                rawPortal.add(p.x() + "|" + p.y() + "|" + p.z());
            }
            yaml.set("portalMarkers", rawPortal);
        }

        yaml.save(file);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String emptyToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static SkullData captureSkullData(Block block) {
        if (block == null) return null;
        if (!(block.getState() instanceof Skull skull)) return null;

        Object profile = invokeNoArg(skull, "getPlayerProfile");
        if (profile == null) profile = invokeNoArg(skull, "getOwnerProfile");
        if (profile == null) return null;

        UUID id = null;
        String name = null;
        try {
            Object idObj = invokeNoArg(profile, "getId");
            if (idObj instanceof UUID u) id = u;
        } catch (Throwable ignored) {
        }
        try {
            Object nameObj = invokeNoArg(profile, "getName");
            if (nameObj instanceof String s) name = s;
        } catch (Throwable ignored) {
        }

        String skinUrl = null;
        String capeUrl = null;
        Object textures = invokeNoArg(profile, "getTextures");
        if (textures != null) {
            Object skinObj = invokeNoArg(textures, "getSkin");
            if (skinObj instanceof URL u) skinUrl = u.toString();
            Object capeObj = invokeNoArg(textures, "getCape");
            if (capeObj instanceof URL u) capeUrl = u.toString();
        }

        if (id == null && (name == null || name.isBlank()) && (skinUrl == null || skinUrl.isBlank())) return null;
        return new SkullData(id != null ? id.toString() : null, emptyToNull(name), emptyToNull(skinUrl), emptyToNull(capeUrl));
    }

    private static void applySkullData(Block block, SkullData data) {
        if (block == null || data == null) return;
        if (!(block.getState() instanceof Skull skull)) return;

        UUID id = null;
        if (data.uuid() != null) {
            try {
                id = UUID.fromString(data.uuid());
            } catch (IllegalArgumentException ignored) {
            }
        }

        Object profile = createProfile(id, data.name());
        if (profile == null) return;

        Object textures = invokeNoArg(profile, "getTextures");
        if (textures != null) {
            if (data.skinUrl() != null) {
                try {
                    invokeOneArg(textures, "setSkin", new URL(data.skinUrl()));
                } catch (Throwable ignored) {
                }
            }
            if (data.capeUrl() != null) {
                try {
                    invokeOneArg(textures, "setCape", new URL(data.capeUrl()));
                } catch (Throwable ignored) {
                }
            }
            invokeOneArg(profile, "setTextures", textures);
        }

        if (!invokeOneArg(skull, "setPlayerProfile", profile)) {
            invokeOneArg(skull, "setOwnerProfile", profile);
        }
        skull.update(true, false);
    }

    private static void adjustSkullFacing(Block block) {
        if (block == null) return;
        BlockFace doorDir = findNearestDoorDirection(block, 5);
        BlockFace facing = doorDir != null ? doorDir.getOppositeFace() : null;
        if (facing == null) {
            BlockFace attached = firstSolidHorizontal(block);
            if (attached == null) return;
            facing = attached.getOppositeFace();
        }
        BlockData data = block.getBlockData();
        if (data instanceof Directional directional) {
            directional.setFacing(facing);
            block.setBlockData(directional, false);
            return;
        }
        if (data instanceof Rotatable rotatable) {
            rotatable.setRotation(facing);
            block.setBlockData(rotatable, false);
        }
    }

    private static BlockFace findNearestDoorDirection(Block block, int radius) {
        if (block == null || radius <= 0) return null;
        BlockFace best = null;
        int bestDist = Integer.MAX_VALUE;
        for (BlockFace face : HORIZONTAL) {
            for (int i = 1; i <= radius; i++) {
                Block scan = block.getRelative(face, i);
                if (scan.getType() == Material.COAL_BLOCK) {
                    if (i < bestDist) {
                        bestDist = i;
                        best = face;
                    }
                    break;
                }
                if (scan.getType().isSolid()) break;
            }
        }
        return best;
    }

    private static Object createProfile(UUID id, String name) {
        try {
            for (Method m : Bukkit.class.getMethods()) {
                if (!m.getName().equals("createProfile")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 2 && UUID.class.isAssignableFrom(p[0]) && String.class.isAssignableFrom(p[1])) {
                    if (id != null || (name != null && !name.isBlank())) return m.invoke(null, id, name);
                }
                if (p.length == 1 && UUID.class.isAssignableFrom(p[0])) {
                    if (id != null) return m.invoke(null, id);
                }
                if (p.length == 1 && String.class.isAssignableFrom(p[0])) {
                    if (name != null && !name.isBlank()) return m.invoke(null, name);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String method) {
        if (target == null) return null;
        try {
            Method m = findMethod(target.getClass(), method, 0);
            if (m == null) return null;
            try {
                if (!m.canAccess(target)) m.setAccessible(true);
            } catch (Throwable ignored) {
            }
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean invokeOneArg(Object target, String method, Object arg) {
        if (target == null) return false;
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(method)) continue;
            if (m.getParameterCount() != 1) continue;
            Class<?> pt = m.getParameterTypes()[0];
            if (arg != null && !pt.isInstance(arg)) continue;
            try {
                try {
                    if (!m.canAccess(target)) m.setAccessible(true);
                } catch (Throwable ignored) {
                }
                m.invoke(target, arg);
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static Method findMethod(Class<?> cls, String name, int paramCount) {
        for (Method m : cls.getMethods()) {
            if (!m.getName().equals(name)) continue;
            if (m.getParameterCount() != paramCount) continue;
            return m;
        }
        return null;
    }

    public StructureTemplate rotate(Rotation rotation) {
        if (rotation == Rotation.R0) return this;

        int newSizeX = switch (rotation) {
            case R90, R270 -> sizeZ;
            case R180, R0 -> sizeX;
        };
        int newSizeZ = switch (rotation) {
            case R90, R270 -> sizeX;
            case R180, R0 -> sizeZ;
        };

        List<TemplateBlock> rotatedBlocks = new ArrayList<>(blocks.size());
        for (TemplateBlock b : blocks) {
            int[] p = rotateXZ(b.x(), b.z(), rotation, sizeX, sizeZ);
            String rotatedData = rotateBlockDataString(b.blockData(), rotation);
            rotatedBlocks.add(new TemplateBlock(p[0], b.y(), p[1], rotatedData));
        }

        List<DoorMarker> rotatedDoors = new ArrayList<>(doorMarkers.size());
        for (DoorMarker d : doorMarkers) {
            int[] p = rotateXZ(d.x(), d.z(), rotation, sizeX, sizeZ);
            rotatedDoors.add(new DoorMarker(p[0], d.y(), p[1], rotateFace(d.facing(), rotation)));
        }

        Map<BlockPos, SkullData> rotatedSkulls = new HashMap<>();
        if (skulls != null && !skulls.isEmpty()) {
            for (Map.Entry<BlockPos, SkullData> e : skulls.entrySet()) {
                BlockPos p0 = e.getKey();
                int[] p = rotateXZ(p0.x(), p0.z(), rotation, sizeX, sizeZ);
                rotatedSkulls.put(new BlockPos(p[0], p0.y(), p[1]), e.getValue());
            }
        }

        List<BlockPos> rotatedPortals = new ArrayList<>();
        if (portalMarkers != null && !portalMarkers.isEmpty()) {
            for (BlockPos p0 : portalMarkers) {
                int[] p = rotateXZ(p0.x(), p0.z(), rotation, sizeX, sizeZ);
                rotatedPortals.add(new BlockPos(p[0], p0.y(), p[1]));
            }
        }

        return new StructureTemplate(newSizeX, sizeY, newSizeZ, rotatedBlocks, rotatedDoors, rotatedSkulls, rotatedPortals);
    }

    public void paste(World world, Location origin) {
        paste(world, origin, null);
    }

    public void paste(World world, Location origin, Set<Long> includePositions) {
        paste(world, origin, includePositions, true);
    }

    public void paste(World world, Location origin, Set<Long> includePositions, boolean placeCoalForDoors) {
        PasteJob job = pasteJob(world, origin, includePositions, null, placeCoalForDoors, null);
        while (!job.isDone()) {
            job.tick(Integer.MAX_VALUE);
        }
    }

    public void paste(World world, Location origin, Set<Long> includePositions, Set<Long> coalDoorPositions) {
        PasteJob job = pasteJob(world, origin, includePositions, null, true, coalDoorPositions);
        while (!job.isDone()) {
            job.tick(Integer.MAX_VALUE);
        }
    }

    public PasteJob pasteJob(World world, Location origin) {
        return pasteJob(world, origin, null);
    }

    public PasteJob pasteJob(World world, Location origin, Set<Long> includePositions) {
        return pasteJob(world, origin, includePositions, null);
    }

    public PasteJob pasteJob(World world, Location origin, Set<Long> includePositions, String templateName) {
        return new PasteJob(this, world, origin, includePositions, templateName, true, null);
    }

    public PasteJob pasteJob(World world, Location origin, Set<Long> includePositions, String templateName, boolean placeCoalForDoors) {
        return new PasteJob(this, world, origin, includePositions, templateName, placeCoalForDoors, null);
    }

    /** coalDoorPositions：若為非 null，則僅此集合內的位置放置煤炭磚，其餘門位置為空氣；null 時依 placeCoalForDoors */
    public PasteJob pasteJob(World world, Location origin, Set<Long> includePositions, String templateName, boolean placeCoalForDoors, Set<Long> coalDoorPositions) {
        return new PasteJob(this, world, origin, includePositions, templateName, placeCoalForDoors, coalDoorPositions);
    }

    public static final class PasteJob {
        private final StructureTemplate template;
        private final World world;
        private final int ox;
        private final int oy;
        private final int oz;
        private final Set<Long> includePositions;
        private final String templateName;
        private final boolean placeCoalForDoors;
        private final Set<Long> coalDoorPositions;
        private final Map<Long, BlockFace> resolvedDoorFacingByPos;
        private final List<BlockPos> doorCenterPositions;
        private final List<Long> overlayPositionsToSet;
        private final List<Map.Entry<BlockPos, SkullData>> skullEntries;
        private final Map<String, BlockData> blockDataCache;

        private int stage;
        private int index;

        public PasteJob(StructureTemplate template, World world, Location origin, Set<Long> includePositions, String templateName, boolean placeCoalForDoors, Set<Long> coalDoorPositions) {
            this.template = template;
            this.world = world;
            this.ox = origin.getBlockX();
            this.oy = origin.getBlockY();
            this.oz = origin.getBlockZ();
            this.includePositions = includePositions;
            this.templateName = normalizeTemplateName(templateName);
            this.placeCoalForDoors = placeCoalForDoors;
            this.coalDoorPositions = coalDoorPositions;

            Map<Long, BlockFace> resolved = new HashMap<>();
            for (DoorMarker d : template.doorMarkers) {
                resolved.put(pack(d.x(), d.y(), d.z()), resolveDoorFacing(d, template.sizeX, template.sizeZ));
            }
            this.resolvedDoorFacingByPos = resolved;

            if (includePositions != null && !template.doorMarkers.isEmpty()) {
                Set<Long> markerPositions = new HashSet<>(template.doorMarkers.size());
                for (DoorMarker d : template.doorMarkers) {
                    markerPositions.add(pack(d.x(), d.y(), d.z()));
                }
                Set<Long> centersPacked = new HashSet<>();
                List<BlockPos> centers = new ArrayList<>();
                for (DoorMarker d : template.doorMarkers) {
                    int x = d.x();
                    int y = d.y();
                    int z = d.z();
                    if (markerPositions.contains(pack(x + 2, y, z))) {
                        long center = pack(x + 1, y, z);
                        if (includePositions.contains(center) && centersPacked.add(center)) {
                            centers.add(new BlockPos(x + 1, y, z));
                        }
                    }
                    if (markerPositions.contains(pack(x - 2, y, z))) {
                        long center = pack(x - 1, y, z);
                        if (includePositions.contains(center) && centersPacked.add(center)) {
                            centers.add(new BlockPos(x - 1, y, z));
                        }
                    }
                    if (markerPositions.contains(pack(x, y, z + 2))) {
                        long center = pack(x, y, z + 1);
                        if (includePositions.contains(center) && centersPacked.add(center)) {
                            centers.add(new BlockPos(x, y, z + 1));
                        }
                    }
                    if (markerPositions.contains(pack(x, y, z - 2))) {
                        long center = pack(x, y, z - 1);
                        if (includePositions.contains(center) && centersPacked.add(center)) {
                            centers.add(new BlockPos(x, y, z - 1));
                        }
                    }
                }
                this.doorCenterPositions = centers;
                List<Long> overlay = new ArrayList<>();
                for (long packed : includePositions) {
                    if (!markerPositions.contains(packed)) overlay.add(packed);
                }
                this.overlayPositionsToSet = overlay;
            } else {
                this.doorCenterPositions = List.of();
                this.overlayPositionsToSet = List.of();
            }

            if (template.skulls != null && !template.skulls.isEmpty()) {
                this.skullEntries = new ArrayList<>(template.skulls.entrySet());
            } else {
                this.skullEntries = List.of();
            }
            this.blockDataCache = new HashMap<>();
            this.stage = (overlayPositionsToSet != null && !overlayPositionsToSet.isEmpty()) ? -1 : 0;
        }

        public boolean isDone() {
            return stage >= 4;
        }

        public int tick(int budget) {
            if (budget <= 0 || isDone()) return 0;
            int used = 0;
            while (used < budget && stage < 4) {
                if (stage == -1) {
                    while (index < overlayPositionsToSet.size() && used < budget) {
                        long packed = overlayPositionsToSet.get(index++);
                        boolean useCoal = coalDoorPositions != null
                                ? coalDoorPositions.contains(packed)
                                : placeCoalForDoors;
                        Block block = world.getBlockAt(ox + unpackX(packed), oy + unpackY(packed), oz + unpackZ(packed));
                        block.setType(useCoal ? Material.COAL_BLOCK : Material.AIR, false);
                        used++;
                    }
                    if (index >= overlayPositionsToSet.size()) {
                        stage = 0;
                        index = 0;
                    }
                    continue;
                }
                if (stage == 0) {
                    List<TemplateBlock> bs = template.blocks;
                    while (index < bs.size() && used < budget) {
                        TemplateBlock b = bs.get(index++);
                        if (includePositions != null && !includePositions.contains(pack(b.x(), b.y(), b.z()))) continue;
                        Block block = world.getBlockAt(ox + b.x(), oy + b.y(), oz + b.z());
                        String blockData = b.blockData();
                        if (shouldCoalForDoorOverlay(b.x(), b.y(), b.z(), blockData)) {
                            boolean useCoal = coalDoorPositions != null
                                    ? coalDoorPositions.contains(pack(b.x(), b.y(), b.z()))
                                    : placeCoalForDoors;
                            block.setType(useCoal ? Material.COAL_BLOCK : Material.AIR, false);
                            used++;
                            continue;
                        }
                        if (isObserverBlockData(blockData)) {
                            BlockFace f = resolvedDoorFacingByPos.get(pack(b.x(), b.y(), b.z()));
                            if (f != null && f != BlockFace.SELF) {
                                blockData = observerBlockData(f);
                            }
                        }
                        BlockData cached = blockDataCache.get(blockData);
                        if (cached == null) {
                            cached = Bukkit.createBlockData(blockData);
                            blockDataCache.put(blockData, cached);
                        }
                        block.setBlockData(cached.clone(), false);
                        used++;
                    }
                    if (index >= bs.size()) {
                        stage = 1;
                        index = 0;
                    }
                    continue;
                }

                if (stage == 1) {
                    while (index < doorCenterPositions.size() && used < budget) {
                        BlockPos p = doorCenterPositions.get(index++);
                        Block block = world.getBlockAt(ox + p.x(), oy + p.y(), oz + p.z());
                        long packed = pack(p.x(), p.y(), p.z());
                        boolean useCoal = coalDoorPositions != null
                                ? coalDoorPositions.contains(packed)
                                : (placeCoalForDoors && block.getType().isAir());
                        if (useCoal && block.getType().isAir()) {
                            block.setType(Material.COAL_BLOCK, false);
                        } else if (!useCoal) {
                            block.setType(Material.AIR, false);
                        }
                        used++;
                    }
                    if (index >= doorCenterPositions.size()) {
                        stage = 2;
                        index = 0;
                    }
                    continue;
                }

                if (stage == 2) {
                    List<TemplateBlock> bs = template.blocks;
                    while (index < bs.size() && used < budget) {
                        TemplateBlock b = bs.get(index++);
                        if (includePositions != null && !includePositions.contains(pack(b.x(), b.y(), b.z()))) continue;
                        Block block = world.getBlockAt(ox + b.x(), oy + b.y(), oz + b.z());
                        BlockData data = block.getBlockData();

                        if (data instanceof Stairs stairs) {
                            BlockFace facing = stairs.getFacing();
                            if (HORIZONTAL.contains(facing)) {
                                BlockFace slopeDir = facing.getOppositeFace();
                                if (!block.getRelative(slopeDir).getType().isAir()) {
                                    stairs.setFacing(slopeDir);
                                    block.setBlockData(stairs, false);
                                }
                            }
                            used++;
                            continue;
                        }

                        if (data instanceof Wall wall) {
                            boolean any = false;
                            for (BlockFace face : HORIZONTAL) {
                                boolean connect = shouldConnect(block, face);
                                wall.setHeight(face, connect ? Wall.Height.LOW : Wall.Height.NONE);
                                if (connect) any = true;
                            }
                            wall.setUp(any || !block.getRelative(BlockFace.UP).getType().isAir());
                            block.setBlockData(wall, false);
                            used++;
                            continue;
                        }

                        if (data instanceof MultipleFacing multipleFacing) {
                            for (BlockFace face : multipleFacing.getAllowedFaces()) {
                                if (!HORIZONTAL.contains(face)) continue;
                                multipleFacing.setFace(face, shouldConnect(block, face));
                            }
                            block.setBlockData(multipleFacing, false);
                            used++;
                            continue;
                        }

                        if (data instanceof Switch s) {
                            Switch adjusted = adjustedSwitchData(block, s);
                            if (adjusted != null) {
                                block.setBlockData(adjusted, false);
                            }
                            used++;
                            continue;
                        }

                        if (block.getType() == Material.TRIPWIRE_HOOK && data instanceof TripwireHook hook) {
                            TripwireHook adjusted = adjustedTripwireHookData(block, hook, templateName);
                            if (adjusted != null) {
                                block.setBlockData(adjusted, false);
                            }
                            used++;
                            continue;
                        }

                        BlockData torchAdjusted = adjustedTorchData(block, data);
                        if (torchAdjusted != null) {
                            block.setBlockData(torchAdjusted, false);
                            used++;
                            continue;
                        }

                        if (block.getType() == Material.END_ROD && data instanceof Directional directional) {
                            Directional adjusted = adjustedEndRodData(block, directional);
                            if (adjusted != null) {
                                block.setBlockData(adjusted, false);
                            }
                            used++;
                            continue;
                        }

                        if (block.getType() == Material.HOPPER && data instanceof Hopper hopper) {
                            Hopper adjusted = adjustedHopperData(block, hopper);
                            if (adjusted != null) {
                                block.setBlockData(adjusted, false);
                            }
                            used++;
                            continue;
                        }

                        used++;
                    }
                    if (index >= bs.size()) {
                        stage = 3;
                        index = 0;
                    }
                    continue;
                }

                if (stage == 3) {
                    while (index < skullEntries.size() && used < budget) {
                        Map.Entry<BlockPos, SkullData> e = skullEntries.get(index++);
                        BlockPos p = e.getKey();
                        if (includePositions != null && !includePositions.contains(pack(p.x(), p.y(), p.z()))) continue;
                        Block block = world.getBlockAt(ox + p.x(), oy + p.y(), oz + p.z());
                        applySkullData(block, e.getValue());
                        adjustSkullFacing(block);
                        used++;
                    }
                    if (index >= skullEntries.size()) {
                        stage = 4;
                        index = 0;
                    }
                }
            }
            return used;
        }

        private boolean shouldCoalForDoorOverlay(int x, int y, int z, String blockData) {
            if (isDoorMarkerBlockData(blockData) || isObserverBlockData(blockData)) {
                return resolvedDoorFacingByPos.containsKey(pack(x, y, z));
            }
            if (includePositions == null || template.doorMarkers.isEmpty()) return false;
            if (!isAirBlockData(blockData)) return false;
            return includePositions.contains(pack(x, y, z)) && isBetweenDoorMarkers(x, y, z);
        }

        private boolean isBetweenDoorMarkers(int x, int y, int z) {
            long left = pack(x - 1, y, z);
            long right = pack(x + 1, y, z);
            if (resolvedDoorFacingByPos.containsKey(left) && resolvedDoorFacingByPos.containsKey(right)) return true;
            long north = pack(x, y, z - 1);
            long south = pack(x, y, z + 1);
            return resolvedDoorFacingByPos.containsKey(north) && resolvedDoorFacingByPos.containsKey(south);
        }
    }

    private static String normalizeTemplateName(String templateName) {
        if (templateName == null) return null;
        String t = templateName.trim().toLowerCase(Locale.ROOT);
        return t.isEmpty() ? null : t;
    }

    private static boolean shouldConnect(Block block, BlockFace face) {
        if (block == null || face == null) return false;
        if (!HORIZONTAL.contains(face)) return false;
        Block other = block.getRelative(face);
        if (other.getType().isSolid()) return true;
        BlockData od = other.getBlockData();
        return od instanceof MultipleFacing || od instanceof Wall;
    }

    private static Switch adjustedSwitchData(Block block, Switch s) {
        if (block == null || s == null) return null;

        BlockFace attachedDir = switch (s.getFace()) {
            case FLOOR -> BlockFace.DOWN;
            case CEILING -> BlockFace.UP;
            case WALL -> s.getFacing().getOppositeFace();
        };
        if (block.getRelative(attachedDir).getType().isSolid()) return s;

        BlockFace chosen = null;
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN, BlockFace.UP}) {
            if (block.getRelative(f).getType().isSolid()) {
                chosen = f;
                break;
            }
        }
        if (chosen == null) return null;

        if (chosen == BlockFace.DOWN) {
            s.setFace(Switch.Face.FLOOR);
            if (!HORIZONTAL.contains(s.getFacing())) s.setFacing(BlockFace.NORTH);
            return s;
        }
        if (chosen == BlockFace.UP) {
            s.setFace(Switch.Face.CEILING);
            if (!HORIZONTAL.contains(s.getFacing())) s.setFacing(BlockFace.NORTH);
            return s;
        }

        s.setFace(Switch.Face.WALL);
        s.setFacing(chosen.getOppositeFace());
        return s;
    }

    private static TripwireHook adjustedTripwireHookData(Block block, TripwireHook hook, String templateName) {
        if (block == null || hook == null) return null;

        if (templateName != null && templateName.startsWith("trap_room")) {
            BlockFace facing = hook.getFacing();
            if (facing == null || facing == BlockFace.SELF || !HORIZONTAL.contains(facing)) {
                facing = BlockFace.NORTH;
                hook.setFacing(facing);
            }
            BlockFace attachedDir = facing.getOppositeFace();
            Block attached = block.getRelative(attachedDir);
            if (attached.getType().isAir()) {
                attached.setType(Material.OAK_WOOD, false);
            }
            return hook;
        }

        BlockFace facing = hook.getFacing();
        if (facing != null && facing != BlockFace.SELF) {
            BlockFace attachedDir = facing.getOppositeFace();
            if (block.getRelative(attachedDir).getType().isSolid()) return hook;
        }

        BlockFace chosenAttached = null;
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN, BlockFace.UP}) {
            if (block.getRelative(f).getType().isSolid()) {
                chosenAttached = f;
                break;
            }
        }
        if (chosenAttached == null) return null;
        if (!HORIZONTAL.contains(chosenAttached)) return null;

        hook.setFacing(chosenAttached.getOppositeFace());
        return hook;
    }

    private static Directional adjustedEndRodData(Block block, Directional directional) {
        if (block == null || directional == null) return null;
        BlockFace facing = directional.getFacing();
        BlockFace attachedDir = facing != null ? facing.getOppositeFace() : null;
        if (attachedDir != null && block.getRelative(attachedDir).getType().isSolid()) return directional;

        BlockFace chosen = null;
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN, BlockFace.UP}) {
            if (block.getRelative(f).getType().isSolid()) {
                chosen = f;
                break;
            }
        }
        if (chosen == null) return null;

        directional.setFacing(chosen.getOppositeFace());
        return directional;
    }

    private static BlockData adjustedTorchData(Block block, BlockData data) {
        if (block == null) return null;
        Material type = block.getType();
        if (type == Material.TORCH || type == Material.SOUL_TORCH || type == Material.REDSTONE_TORCH) {
            if (block.getRelative(BlockFace.DOWN).getType().isSolid()) return null;
            BlockFace attach = firstSolidHorizontal(block);
            if (attach == null) return null;
            Material wall = switch (type) {
                case TORCH -> Material.WALL_TORCH;
                case SOUL_TORCH -> Material.SOUL_WALL_TORCH;
                case REDSTONE_TORCH -> Material.REDSTONE_WALL_TORCH;
                default -> null;
            };
            if (wall == null) return null;
            BlockData bd = Bukkit.createBlockData(wall);
            if (bd instanceof Directional directional) {
                directional.setFacing(attach.getOppositeFace());
                return bd;
            }
            return null;
        }

        if (type == Material.WALL_TORCH || type == Material.SOUL_WALL_TORCH || type == Material.REDSTONE_WALL_TORCH) {
            if (!(data instanceof Directional directional)) return null;
            BlockFace facing = directional.getFacing();
            if (facing != null) {
                BlockFace attachedDir = facing.getOppositeFace();
                if (block.getRelative(attachedDir).getType().isSolid()) return null;
            }

            BlockFace attach = firstSolidHorizontal(block);
            if (attach != null) {
                directional.setFacing(attach.getOppositeFace());
                return directional;
            }
            if (block.getRelative(BlockFace.DOWN).getType().isSolid()) {
                Material standing = switch (type) {
                    case WALL_TORCH -> Material.TORCH;
                    case SOUL_WALL_TORCH -> Material.SOUL_TORCH;
                    case REDSTONE_WALL_TORCH -> Material.REDSTONE_TORCH;
                    default -> null;
                };
                if (standing != null) {
                    return Bukkit.createBlockData(standing);
                }
            }
        }

        return null;
    }

    private static BlockFace firstSolidHorizontal(Block block) {
        for (BlockFace f : HORIZONTAL) {
            if (block.getRelative(f).getType().isSolid()) return f;
        }
        return null;
    }

    private static Hopper adjustedHopperData(Block block, Hopper hopper) {
        if (block == null || hopper == null) return null;

        BlockFace facing = hopper.getFacing();
        if (facing != null && facing != BlockFace.SELF) {
            Block out = block.getRelative(facing);
            if (isValidHopperTarget(out)) return hopper;
        }

        BlockFace chosen = null;
        for (BlockFace f : new BlockFace[]{BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            if (isValidHopperTarget(block.getRelative(f))) {
                chosen = f;
                break;
            }
        }
        if (chosen == null) return hopper;

        hopper.setFacing(chosen);
        return hopper;
    }

    private static boolean isValidHopperTarget(Block block) {
        if (block == null) return false;
        if (block.getState() instanceof InventoryHolder) return true;
        if (!block.getType().isAir()) return true;
        return false;
    }

    private static int[] rotateXZ(int x, int z, Rotation rotation, int sizeX, int sizeZ) {
        return switch (rotation) {
            case R0 -> new int[]{x, z};
            case R90 -> new int[]{z, sizeX - 1 - x};
            case R180 -> new int[]{sizeX - 1 - x, sizeZ - 1 - z};
            case R270 -> new int[]{sizeZ - 1 - z, x};
        };
    }

    private static BlockFace rotateFace(BlockFace face, Rotation rotation) {
        if (face == BlockFace.SELF) return face;
        if (!HORIZONTAL.contains(face)) return face;

        return switch (rotation) {
            case R0 -> face;
            case R90 -> switch (face) {
                case NORTH -> BlockFace.EAST;
                case EAST -> BlockFace.SOUTH;
                case SOUTH -> BlockFace.WEST;
                case WEST -> BlockFace.NORTH;
                default -> face;
            };
            case R180 -> switch (face) {
                case NORTH -> BlockFace.SOUTH;
                case EAST -> BlockFace.WEST;
                case SOUTH -> BlockFace.NORTH;
                case WEST -> BlockFace.EAST;
                default -> face;
            };
            case R270 -> switch (face) {
                case NORTH -> BlockFace.WEST;
                case EAST -> BlockFace.NORTH;
                case SOUTH -> BlockFace.EAST;
                case WEST -> BlockFace.SOUTH;
                default -> face;
            };
        };
    }

    private static final EnumSet<BlockFace> HORIZONTAL = EnumSet.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);
    private static final int DOOR_OVERLAY_RADIUS = 3;
    private static final int DOOR_OVERLAY_Y_BELOW = 1;
    private static final int DOOR_OVERLAY_Y_ABOVE = 2;

    private static String rotateBlockDataString(String blockDataString, Rotation rotation) {
        if (rotation == Rotation.R0) return blockDataString;

        BlockData data;
        try {
            data = Bukkit.createBlockData(blockDataString);
        } catch (IllegalArgumentException ignored) {
            return blockDataString;
        }

        if (data instanceof Directional directional) {
            directional.setFacing(rotateFace(directional.getFacing(), rotation));
            return directional.getAsString();
        }

        if (data instanceof Switch s) {
            s.setFacing(rotateFace(s.getFacing(), rotation));
            return s.getAsString();
        }

        if (data instanceof Rotatable rotatable) {
            BlockFace rot = rotatable.getRotation();
            rotatable.setRotation(rotateFace(rot, rotation));
            return rotatable.getAsString();
        }

        if (data instanceof Orientable orientable) {
            Axis axis = orientable.getAxis();
            if (axis == Axis.X || axis == Axis.Z) {
                if (rotation == Rotation.R90 || rotation == Rotation.R270) {
                    orientable.setAxis(axis == Axis.X ? Axis.Z : Axis.X);
                }
            }
            return orientable.getAsString();
        }

        if (data instanceof Wall wall) {
            var north = wall.getHeight(BlockFace.NORTH);
            var east = wall.getHeight(BlockFace.EAST);
            var south = wall.getHeight(BlockFace.SOUTH);
            var west = wall.getHeight(BlockFace.WEST);

            wall.setHeight(BlockFace.NORTH, Wall.Height.NONE);
            wall.setHeight(BlockFace.EAST, Wall.Height.NONE);
            wall.setHeight(BlockFace.SOUTH, Wall.Height.NONE);
            wall.setHeight(BlockFace.WEST, Wall.Height.NONE);

            wall.setHeight(rotateFace(BlockFace.NORTH, rotation), north);
            wall.setHeight(rotateFace(BlockFace.EAST, rotation), east);
            wall.setHeight(rotateFace(BlockFace.SOUTH, rotation), south);
            wall.setHeight(rotateFace(BlockFace.WEST, rotation), west);
            return wall.getAsString();
        }

        if (data instanceof MultipleFacing multipleFacing) {
            var allowed = multipleFacing.getAllowedFaces();
            boolean north = allowed.contains(BlockFace.NORTH) && multipleFacing.hasFace(BlockFace.NORTH);
            boolean east = allowed.contains(BlockFace.EAST) && multipleFacing.hasFace(BlockFace.EAST);
            boolean south = allowed.contains(BlockFace.SOUTH) && multipleFacing.hasFace(BlockFace.SOUTH);
            boolean west = allowed.contains(BlockFace.WEST) && multipleFacing.hasFace(BlockFace.WEST);

            for (BlockFace f : allowed) {
                if (HORIZONTAL.contains(f)) multipleFacing.setFace(f, false);
            }

            if (north) multipleFacing.setFace(rotateFace(BlockFace.NORTH, rotation), true);
            if (east) multipleFacing.setFace(rotateFace(BlockFace.EAST, rotation), true);
            if (south) multipleFacing.setFace(rotateFace(BlockFace.SOUTH, rotation), true);
            if (west) multipleFacing.setFace(rotateFace(BlockFace.WEST, rotation), true);
            return multipleFacing.getAsString();
        }

        return data.getAsString();
    }
}
