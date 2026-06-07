package net.swofty.dungeons;

import java.util.HashMap;
import java.util.Map;

public class DungeonsData {
    private final Map<DungeonRoomType, RoomData> roomData;
    private final Map<DungeonStructureType, Integer> structureCounts;
    private final Integer width;
    private final Integer height;

    public DungeonsData(Integer width, Integer height) {
        this.width = width;
        this.height = height;
        this.roomData = new HashMap<>();
        this.structureCounts = new HashMap<>();
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    public DungeonsData with(DungeonRoomType room, RoomData data) {
        roomData.put(room, data);
        return this;
    }

    public DungeonsData withStructure(DungeonStructureType type, int count) {
        structureCounts.put(type, count);
        return this;
    }

    public int getStructureCount(DungeonStructureType type) {
        return structureCounts.getOrDefault(type, 0);
    }

    public RoomData getDataForOrNull(DungeonRoomType room) {
        return roomData.get(room);
    }

    public record RoomData(int minimumAmount, int maximumAmount) {}
}
