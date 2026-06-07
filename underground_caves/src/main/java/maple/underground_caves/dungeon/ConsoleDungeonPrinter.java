package maple.underground_caves.dungeon;

import net.swofty.dungeons.*;

public class ConsoleDungeonPrinter {

    public static void main(String[] args) {
        int width = 4;
        int height = 4;

        // Initialize Data with some defaults (same as plugin)
        DungeonsData data = new DungeonsData(width, height)
                .with(DungeonRoomType.FAIRY, new DungeonsData.RoomData(1, 1))
                .with(DungeonRoomType.PUZZLE, new DungeonsData.RoomData(1, 3))
                .with(DungeonRoomType.BLOOD, new DungeonsData.RoomData(0, 0))  // 不使用 BLOOD
                .with(DungeonRoomType.TRAP, new DungeonsData.RoomData(1, 1))
                .with(DungeonRoomType.MINI_BOSS, new DungeonsData.RoomData(1, 1))
                .withStructure(DungeonStructureType.QUAD_2x2, 1) // Force 1 2x2
                .withStructure(DungeonStructureType.L_SHAPE, 2); // Force 2 Ls

        System.out.println("Generating " + width + "x" + height + " dungeon...");
        GeneratorService service = DungeonsAPI.getGeneratorService(data);
        
        try {
            SkyBlockDungeon dungeon = service.generate().join();
            printDungeon(dungeon, width, height);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Force exit because generator uses thread pool
        System.exit(0);
    }

    private static void printDungeon(SkyBlockDungeon dungeon, int width, int height) {
        // Each cell is 3x3 chars
        // ###
        // # #
        // ###
        
        // We render row by row. Each dungeon row (y) has 3 console rows.
        
        for (int y = 0; y < height; y++) {
            // Line 1: Top walls/doors
            for (int x = 0; x < width; x++) {
                System.out.print("X"); // Corner
                if (y > 0 && dungeon.isConnected(x, y, x, y - 1)) {
                    System.out.print("   "); // Vertical connection up
                } else {
                    System.out.print("---"); // Horizontal Wall
                }
            }
            System.out.println("X");

            // Line 2: Middle (Room content + Left/Right walls)
            for (int x = 0; x < width; x++) {
                // Left Wall/Door
                if (x > 0 && dungeon.isConnected(x, y, x - 1, y)) {
                    System.out.print(" "); // Open
                } else {
                    System.out.print("|"); // Wall
                }
                
                // Center
                SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(x, y);
                if (room == null) {
                    System.out.print("   ");
                } else {
                    String symbol = "   ";
                    if (room.getRoomType() == DungeonRoomType.ENTRANCE) symbol = " S "; // Start
                    else if (room.getRoomType() == DungeonRoomType.EXIT) symbol = " E "; // End
                    else if (room.getRoomType() == DungeonRoomType.FAIRY) symbol = " F ";
                    else if (room.getRoomType() == DungeonRoomType.PUZZLE) symbol = " P ";
                    else if (room.getRoomType() == DungeonRoomType.BLOOD) symbol = " B ";
                    else if (room.getRoomType() == DungeonRoomType.TRAP) symbol = " T ";
                    else if (room.getRoomType() == DungeonRoomType.MINI_BOSS) symbol = " M ";
                    else if (room.getStructureType() != null) {
                         if (room.getStructureType() == DungeonStructureType.QUAD_2x2) symbol = "QQQ";
                         else if (room.getStructureType() == DungeonStructureType.L_SHAPE) symbol = "LLL";
                         else symbol = "DDD";
                    }
                    else if (room.isCritical()) symbol = " . ";
                    
                    System.out.print(symbol);
                }
            }
            System.out.println("|"); // Rightmost wall
        }
        
        // Bottom border of map
        for (int x = 0; x < width; x++) {
            System.out.print("X---");
        }
        System.out.println("X");
    }
}
