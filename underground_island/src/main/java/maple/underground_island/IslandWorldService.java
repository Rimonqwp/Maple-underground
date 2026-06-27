package maple.underground_island;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class IslandWorldService {
    private static final String WORLD_PREFIX = "island_";

    private final JavaPlugin plugin;
    private final Map<String, IslandRecord> islandsByWorld = new ConcurrentHashMap<>();

    IslandWorldService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    static String worldNameFor(UUID ownerId) {
        return WORLD_PREFIX + ownerId;
    }

    static boolean isIslandWorldName(String worldName) {
        if (worldName == null || !worldName.startsWith(WORLD_PREFIX)) {
            return false;
        }
        try {
            UUID.fromString(worldName.substring(WORLD_PREFIX.length()));
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    World ensureWorld(IslandRecord island) {
        if (island == null) {
            throw new IllegalArgumentException("island is null");
        }
        if (!isIslandWorldName(island.worldName())) {
            throw new IllegalArgumentException("Unsafe island world name: " + island.worldName());
        }
        World world = Bukkit.getWorld(island.worldName());
        if (world == null) {
            WorldCreator creator = new WorldCreator(island.worldName());
            creator.environment(World.Environment.NORMAL);
            creator.generateStructures(false);
            creator.generator(new VoidChunkGenerator());
            world = Bukkit.createWorld(creator);
        }
        if (world == null) {
            throw new IllegalStateException("Failed to create island world: " + island.worldName());
        }
        configureWorld(world);
        ensureStarterBlocks(world, island);
        islandsByWorld.put(island.worldName(), island);
        return world;
    }

    Optional<IslandRecord> islandForCenter(Block block) {
        if (block == null || block.getWorld() == null) {
            return Optional.empty();
        }
        IslandRecord island = islandsByWorld.get(block.getWorld().getName());
        if (island == null) {
            return Optional.empty();
        }
        if (block.getX() == island.centerX()
                && block.getY() == island.centerY()
                && block.getZ() == island.centerZ()) {
            return Optional.of(island);
        }
        return Optional.empty();
    }

    boolean isManagedIslandWorld(World world) {
        return world != null && isIslandWorldName(world.getName());
    }

    void updateMineState(IslandRecord island, IslandRepository.MineResult result) {
        if (island == null || result == null) {
            return;
        }
        islandsByWorld.compute(island.worldName(), (ignored, current) -> {
            IslandRecord base = current != null ? current : island;
            return base.withMineState(result.blocksMined(), result.portalRewarded());
        });
    }

    Location homeLocation(IslandRecord island, World world) {
        return island.home(world);
    }

    @Nullable
    Location fallbackLocation() {
        for (World world : Bukkit.getWorlds()) {
            if (world != null && !isIslandWorldName(world.getName())) {
                return world.getSpawnLocation();
            }
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    void deleteIslandWorld(String worldName) {
        if (!isIslandWorldName(worldName)) {
            plugin.getLogger().warning("[Island] Refusing to delete unsafe world name: " + worldName);
            return;
        }
        islandsByWorld.remove(worldName);
        World world = Bukkit.getWorld(worldName);
        Location fallback = fallbackLocation();
        if (world != null) {
            for (Player player : new ArrayList<>(world.getPlayers())) {
                if (player == null || !player.isOnline()) {
                    continue;
                }
                if (fallback != null) {
                    player.teleport(fallback);
                    player.sendMessage(Component.text("你的旧岛屿已迁移，已先把你送回安全位置。", NamedTextColor.GRAY));
                } else {
                    player.kick(Component.text("岛屿正在迁移，请稍后重新进入。", NamedTextColor.YELLOW));
                }
            }
            Bukkit.unloadWorld(world, false);
        }

        Path worldContainer = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        Path worldDir = worldContainer.resolve(worldName).normalize();
        if (!worldDir.startsWith(worldContainer)
                || worldDir.equals(worldContainer)
                || worldDir.getFileName() == null
                || !worldDir.getFileName().toString().equals(worldName)) {
            plugin.getLogger().warning("[Island] Refusing to delete unsafe island world path: " + worldDir);
            return;
        }
        if (Files.notExists(worldDir)) {
            return;
        }
        try (var stream = Files.walk(worldDir)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
            plugin.getLogger().info("[Island] Deleted island world " + worldName);
        } catch (IOException e) {
            plugin.getLogger().warning("[Island] Failed to delete island world " + worldName + ": " + e.getMessage());
        }
    }

    void clearCache() {
        islandsByWorld.clear();
    }

    private void configureWorld(World world) {
        world.setDifficulty(Difficulty.NORMAL);
        world.setSpawnFlags(false, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        world.setTime(6000L);
        world.setStorm(false);
        world.setThundering(false);
    }

    private void ensureStarterBlocks(World world, IslandRecord island) {
        int cx = island.centerX();
        int cy = island.centerY();
        int cz = island.centerZ();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block floor = world.getBlockAt(cx + dx, cy - 1, cz + dz);
                if (floor.getType().isAir()) {
                    floor.setType(Material.OAK_PLANKS, false);
                }
            }
        }
        Block support = world.getBlockAt(cx, cy - 1, cz);
        support.setType(Material.BEDROCK, false);

        Block center = world.getBlockAt(cx, cy, cz);
        if (center.getType().isAir()) {
            center.setType(Material.DIRT, false);
        }
        world.setSpawnLocation(island.home(world));
    }

    private static final class VoidChunkGenerator extends ChunkGenerator {
        @Override
        public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
            return createChunkData(world);
        }
    }
}
