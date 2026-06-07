package net.swofty.dungeons;

import lombok.Getter;
import lombok.Setter;

import java.util.*;

public class SkyBlockDungeon {
    private int width;
    private int height;
    private DungeonRoom[][] rooms; // 2D array for faster access than HashMap
    private List<DungeonDoor> doors = new ArrayList<>();
    // HashSet for O(1) door lookup - significant performance improvement
    private Set<Long> doorKeys = new HashSet<>();
    /** 修剪前的完整門列表，用於 isEnteringFairyDoor 的 BFS（需用完整連通性判斷「往 exit 的門」） */
    private Set<Long> fullDoorKeysBeforePrune;

    public SkyBlockDungeon() {
        // Default constructor for backward compatibility
    }

    public SkyBlockDungeon(int width, int height) {
        this.width = width;
        this.height = height;
        this.rooms = new DungeonRoom[width][height];
    }

    // Initialize with dimensions if not set in constructor
    private void ensureInitialized(int x, int y) {
        if (rooms == null) {
            // Auto-detect dimensions based on first access
            width = Math.max(width, x + 1);
            height = Math.max(height, y + 1);
            rooms = new DungeonRoom[Math.max(10, width)][Math.max(10, height)];
        } else if (x >= rooms.length || y >= rooms[0].length) {
            // Expand array if needed
            int newWidth = Math.max(rooms.length, x + 1);
            int newHeight = Math.max(rooms[0].length, y + 1);
            DungeonRoom[][] newRooms = new DungeonRoom[newWidth][newHeight];
            for (int i = 0; i < rooms.length; i++) {
                System.arraycopy(rooms[i], 0, newRooms[i], 0, rooms[i].length);
            }
            rooms = newRooms;
            width = newWidth;
            height = newHeight;
        }
    }

    // Encode door coordinates into a long key for fast lookup
    private long encodeDoorKey(int x1, int y1, int x2, int y2) {
        // Normalize so smaller coordinates come first
        if (x1 > x2 || (x1 == x2 && y1 > y2)) {
            int tx = x1, ty = y1;
            x1 = x2;
            y1 = y2;
            x2 = tx;
            y2 = ty;
        }
        // Pack into long: (x1 << 48) | (y1 << 32) | (x2 << 16) | y2
        return ((long) x1 << 48) | ((long) y1 << 32) | ((long) x2 << 16) | (long) y2;
    }

    public SkyBlockDungeon setRoom(int x, int y, DungeonRoom room) {
        ensureInitialized(x, y);
        rooms[x][y] = room;
        return this;
    }

    public DungeonRoom getRoom(int x, int y) {
        if (rooms == null || x < 0 || y < 0 || x >= rooms.length || y >= rooms[0].length) {
            return null;
        }
        return rooms[x][y];
    }

    public boolean isConnected(int x1, int y1, int x2, int y2) {
        // O(1) lookup using HashSet instead of O(n) stream iteration
        return doorKeys.contains(encodeDoorKey(x1, y1, x2, y2));
    }

    public List<int[]> getDoors(int x, int y) {
        final List<int[]> doors = new ArrayList<>();

        // Check all four directions sequentially - much faster than creating threads
        if (isConnected(x, y, x - 1, y)) {
            doors.add(new int[] { x - 1, y });
        }
        if (isConnected(x, y, x + 1, y)) {
            doors.add(new int[] { x + 1, y });
        }
        if (isConnected(x, y, x, y - 1)) {
            doors.add(new int[] { x, y - 1 });
        }
        if (isConnected(x, y, x, y + 1)) {
            doors.add(new int[] { x, y + 1 });
        }

        return doors;
    }

    public void connectRooms(int x1, int y1, int x2, int y2) {
        DungeonRoom room1 = getRoom(x1, y1);
        DungeonRoom room2 = getRoom(x2, y2);
        if (room1 == null || room2 == null) {
            throw new IllegalArgumentException(
                    "Room at " + x1 + ", " + y1 + " or " + x2 + ", " + y2 + " does not exist");
        }

        DungeonDoor door = new DungeonDoor(x1, y1, x2, y2);
        doors.add(door);
        // Update the index for fast lookup
        doorKeys.add(encodeDoorKey(x1, y1, x2, y2));
    }

    public List<DungeonDoor> getAllDoors() {
        return new ArrayList<>(doors);
    }

    public void setAllDoors(List<DungeonDoor> newDoors) {
        this.doors = new ArrayList<>(newDoors);
        // Rebuild the index
        this.doorKeys.clear();
        for (DungeonDoor door : newDoors) {
            doorKeys.add(encodeDoorKey(door.x1(), door.y1(), door.x2(), door.y2()));
        }
    }

    /** 保存修剪前的完整門列表，供 isEnteringFairyDoor 使用（prune 會破壞連通性導致誤判） */
    public void setFullDoorsBeforePrune(List<DungeonDoor> doors) {
        if (doors == null) {
            this.fullDoorKeysBeforePrune = null;
            return;
        }
        this.fullDoorKeysBeforePrune = new HashSet<>();
        for (DungeonDoor d : doors) {
            fullDoorKeysBeforePrune.add(encodeDoorKey(d.x1(), d.y1(), d.x2(), d.y2()));
        }
    }

    /** 檢查兩格是否相連；若有 fullDoorKeysBeforePrune 則用完整圖，否則用當前門 */
    public boolean isConnectedForCoalCheck(int x1, int y1, int x2, int y2) {
        long k = encodeDoorKey(x1, y1, x2, y2);
        if (fullDoorKeysBeforePrune != null) {
            return fullDoorKeysBeforePrune.contains(k);
        }
        return doorKeys.contains(k);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        // Loop over the dungeon rooms, center them and print them

        if (rooms == null)
            return "Empty dungeon";
        int maxWidth = width;
        int maxHeight = height;

        for (int y = 0; y < maxHeight; y++) {
            for (int x = 0; x < maxWidth; x++) {
                DungeonRoom room = getRoom(x, y);
                if (room == null) {
                    builder.append(DungeonUtilities.center(" ", 30, ' '));
                } else {
                    builder.append(DungeonUtilities.center(
                            (isConnected(x, y, x - 1, y) ? "|" : " ") +
                                    room + (isConnected(x, y, x + 1, y) ? "|" : " "),
                            30, ' '));
                }
            }
            builder.append("\n");

            if (y < maxHeight - 1) {
                for (int x = 0; x < maxWidth; x++) {
                    DungeonRoom room = getRoom(x, y);
                    if (room == null) {
                        builder.append(DungeonUtilities.center(" ", 30, ' '));
                    } else {
                        builder.append(DungeonUtilities.center(
                                (isConnected(x, y, x, y + 1) ? "--" : "   "),
                                30, ' '));
                    }
                }
                builder.append("\n");
            }
        }

        return builder.toString();
    }

    @Getter
    @Setter
    public static class DungeonRoom {
        private DungeonRoomType roomType;
        private boolean isCritical;
        private int stage = 0;
        private int corridorNumber = 0;
        private DungeonStructureType structureType;
        private UUID structureId;

        public DungeonRoom(DungeonRoomType roomType) {
            this.roomType = roomType;
        }

        public DungeonRoomType getRoomType() {
            return roomType;
        }

        public void setRoomType(DungeonRoomType roomType) {
            this.roomType = roomType;
        }

        public boolean isCritical() {
            return isCritical;
        }

        public void setCritical(boolean critical) {
            isCritical = critical;
        }

        public int getStage() {
            return stage;
        }

        public void setStage(int stage) {
            this.stage = stage;
        }

        public int getCorridorNumber() {
            return corridorNumber;
        }

        public void setCorridorNumber(int corridorNumber) {
            this.corridorNumber = corridorNumber;
        }

        public DungeonStructureType getStructureType() {
            return structureType;
        }

        public void setStructureType(DungeonStructureType structureType) {
            this.structureType = structureType;
        }

        public UUID getStructureId() {
            return structureId;
        }

        public void setStructureId(UUID structureId) {
            this.structureId = structureId;
        }

        @Override
        public String toString() {
            return roomType.name() + (isCritical ? " (C)" : "") + (stage > 0 ? " (S-" + stage + ")" : "")
                    + (corridorNumber > 0 ? " (C-" + corridorNumber + ")" : "")
                    + (structureType != null ? " (" + structureType.name() + ")" : "");
        }

        public static DungeonRoom ofBase() {
            return new DungeonRoom(DungeonRoomType.BASE);
        }
    }

    public record DungeonDoor(int x1, int y1, int x2, int y2) {
    }
}
