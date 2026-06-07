package net.swofty.dungeons;

public class DungeonsTest {
    public static void main(String[] args) {
        DungeonsData data = new DungeonsData(5, 5)
                .with(DungeonRoomType.FAIRY, new DungeonsData.RoomData(1, 1))
                .with(DungeonRoomType.PUZZLE, new DungeonsData.RoomData(2, 4))
                .with(DungeonRoomType.BLOOD, new DungeonsData.RoomData(0, 0))  // 不使用 BLOOD
                .with(DungeonRoomType.TRAP, new DungeonsData.RoomData(1, 1))
                .with(DungeonRoomType.MINI_BOSS, new DungeonsData.RoomData(1, 1));

        GeneratorService generatorService = DungeonsAPI.getGeneratorService(data);
        SkyBlockDungeon dungeon = generatorService.generate().join();

        System.out.println("Generated dungeon: \n" + dungeon);
        System.out.println(System.currentTimeMillis() - generatorService.getGenerationStartTime());
    }
}
