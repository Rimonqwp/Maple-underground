package maple.underground_state.skills.xp;

import maple.underground_state.skills.SkillProgressData;
import maple.underground_state.skills.SkillProgressDB;
import maple.underground_state.skills.SkillProgressManager;
import maple.underground_state.skills.SkillRewardService;
import maple.underground_state.skills.SkillType;
import maple.underground_state.stats.PlayerStatsData;
import maple.underground_state.stats.PlayerStatsManager;
import maple.underground_state.stats.PlayerStatsRuntime;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MiningXpServiceTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private UUID playerId;
    private SkillProgressManager skillProgressManager;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        player = server.addPlayer("MapleMiner");
        playerId = player.getUniqueId();
        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_PICKAXE));

        PluginMock plugin = MockBukkit.createMockPlugin("underground_state");
        skillProgressManager = new SkillProgressManager(uninitializedSkillProgressDb());
        seedSkillProgress(playerId);

        PlayerStatsManager playerStatsManager = new PlayerStatsManager(null);
        playerStatsManager.set(playerId, new PlayerStatsData());

        SkillRewardService skillRewardService = new SkillRewardService(null, skillProgressManager, playerStatsManager);
        PlayerStatsRuntime runtime = new PlayerStatsRuntime(plugin, playerStatsManager);
        CombatXpOverlayService overlayService = new CombatXpOverlayService(null, runtime, skillProgressManager, skillRewardService);
        MiningXpService miningXpService = new MiningXpService(skillProgressManager, skillRewardService, overlayService);

        Bukkit.getPluginManager().registerEvents(miningXpService, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void breakingCoalOreAddsMiningXp() {
        Block block = world.getBlockAt(0, 64, 0);
        block.setType(Material.COAL_ORE);
        ((BlockMock) block).setDrops(List.of(new ItemStack(Material.COAL)));

        Bukkit.getPluginManager().callEvent(new BlockBreakEvent(block, player));

        assertEquals(1L, skillProgressManager.getXp(playerId, SkillType.MINING));
        assertEquals(0, skillProgressManager.getMiningXpRemainderTenths(playerId));
    }

    @Test
    void breakingPlayerPlacedOreDoesNotAddMiningXp() {
        Block block = world.getBlockAt(1, 64, 0);
        block.setType(Material.COAL_ORE);
        ((BlockMock) block).setDrops(List.of(new ItemStack(Material.COAL)));

        Bukkit.getPluginManager().callEvent(new BlockPlaceEvent(
                block,
                block.getState(),
                block.getRelative(0, -1, 0),
                new ItemStack(Material.COAL_ORE),
                player,
                true,
                EquipmentSlot.HAND));
        Bukkit.getPluginManager().callEvent(new BlockBreakEvent(block, player));

        assertEquals(0L, skillProgressManager.getXp(playerId, SkillType.MINING));
        assertEquals(0, skillProgressManager.getMiningXpRemainderTenths(playerId));
    }

    @SuppressWarnings("unchecked")
    private void seedSkillProgress(UUID playerId) throws Exception {
        Field cacheField = SkillProgressManager.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        Map<UUID, SkillProgressData> cache = (Map<UUID, SkillProgressData>) cacheField.get(skillProgressManager);
        cache.put(playerId, new SkillProgressData());
    }

    private static SkillProgressDB uninitializedSkillProgressDb() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        return (SkillProgressDB) unsafe.allocateInstance(SkillProgressDB.class);
    }
}
