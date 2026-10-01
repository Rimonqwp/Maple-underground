package maple.underground_state.professions;

import maple.underground_state.UndergroundState;
import maple.underground_state.collection.*;
import maple.underground_state.gui.CollectionInfoGui;
import maple.underground_state.skills.*;
import maple.underground_state.skills.xp.CombatXpOverlayService;
import maple.underground_state.stats.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Item;
import org.bukkit.event.block.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProfessionServiceTest {
    ServerMock server;WorldMock world;PlayerMock player;ProfessionService service;
    SkillProgressManager skills;CollectionProgressManager collections;PlayerStatsManager stats;
    private static final class MemoryStore extends ProfessionStore {
        MemoryStore(UUID id) { cacheLoaded(id,Map.of()); }
        @Override public void save(UUID id) {}
        @Override public void saveAll() {}
    }
    private static Object allocate(Class<?> type) throws Exception {
        Field field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);
        return ((sun.misc.Unsafe)field.get(null)).allocateInstance(type);
    }
    private static void field(Object object,String name,Object value) throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);
    }
    @SuppressWarnings("unchecked") private static void seed(Object manager,UUID id,Object data) throws Exception {
        Field field=manager.getClass().getDeclaredField("cache");field.setAccessible(true);((Map<UUID,Object>)field.get(manager)).put(id,data);
    }
    @BeforeEach void setup() throws Exception {
        server=MockBukkit.mock();world=server.addSimpleWorld("world");player=server.addPlayer();player.setGameMode(GameMode.SURVIVAL);
        var plugin=MockBukkit.createMockPlugin("underground_state");var core=(UndergroundState)allocate(UndergroundState.class);
        skills=new SkillProgressManager((SkillProgressDB)allocate(SkillProgressDB.class));seed(skills,player.getUniqueId(),new SkillProgressData());
        collections=new CollectionProgressManager((CollectionProgressDB)allocate(CollectionProgressDB.class));seed(collections,player.getUniqueId(),new java.util.concurrent.ConcurrentHashMap<String,Long>());
        stats=new PlayerStatsManager(null);var data=new PlayerStatsData();data.setMiningFortune(140);data.setFarmingFortune(200);stats.set(player.getUniqueId(),data);
        var runtime=new PlayerStatsRuntime(plugin,stats);var rewards=new SkillRewardService(null,skills,stats);
        field(core,"skillProgressManager",skills);field(core,"collectionProgressManager",collections);field(core,"playerStatsRuntime",runtime);
        field(core,"skillRewardService",rewards);field(core,"combatXpOverlayService",new CombatXpOverlayService(null,runtime,skills,rewards));
        service=new ProfessionService(core,plugin,new MemoryStore(player.getUniqueId()),Set.of("world"));field(core,"professionService",service);
        Bukkit.getPluginManager().registerEvents(service,plugin);player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_PICKAXE));
    }
    @AfterEach void cleanup() { MockBukkit.unmock(); }
    private BlockMock block(int x,Material type,ItemStack... drops) {
        var block=(BlockMock)world.getBlockAt(x,64,0);block.setType(type);block.setDrops(List.of(drops));return block;
    }
    private BlockDropItemEvent breakAndDrop(BlockMock block,boolean cancelled,ItemStack... actual) {
        var state=block.getState();var breaking=new BlockBreakEvent(block,player);Bukkit.getPluginManager().callEvent(breaking);
        if(breaking.isCancelled())return null;block.setType(Material.AIR);
        List<Item> entities=new ArrayList<>();for(var stack:actual)entities.add(world.dropItemNaturally(block.getLocation(),stack));
        var event=new BlockDropItemEvent(block,state,player,entities);event.setCancelled(cancelled);Bukkit.getPluginManager().callEvent(event);
        server.getScheduler().performTicks(2);return event;
    }
    private long amount(CollectionInfoGui.Category category,Material material) { return collections.getAmount(player.getUniqueId(),CollectionInfoGui.collectionKey(category,material)); }
    private Block movable(Block block) {
        // MockBukkit leaves this API unimplemented; coal ore's real reaction is MOVE.
        return (Block)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Block.class},(proxy,method,args) -> {
            if(method.getName().equals("getPistonMoveReaction"))return PistonMoveReaction.MOVE;
            try { return method.invoke(block,args); } catch(java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
        });
    }
    @Test void fortuneReplacesVanillaFortuneWithoutMultiplyingXpOrRePickupCollection() {
        var tool=player.getInventory().getItemInMainHand();tool.addEnchantment(Registry.ENCHANTMENT.get(NamespacedKey.minecraft("fortune")),3);
        var ore=block(0,Material.COAL_ORE,new ItemStack(Material.COAL));
        var event=breakAndDrop(ore,false,new ItemStack(Material.COAL,6));
        assertEquals(3,event.getItems().getFirst().getItemStack().getAmount());assertEquals(3,amount(CollectionInfoGui.Category.MINING,Material.COAL));
        assertEquals(1,skills.getXp(player.getUniqueId(),SkillType.MINING));
        // Re-delivering a drop event has no matching pending manual action.
        Bukkit.getPluginManager().callEvent(event);server.getScheduler().performOneTick();assertEquals(3,amount(CollectionInfoGui.Category.MINING,Material.COAL));
    }
    @Test void cancelledDropsAndPlacedOreDoNotEarnAnything() {
        breakAndDrop(block(0,Material.COAL_ORE,new ItemStack(Material.COAL)),true,new ItemStack(Material.COAL));
        var ore=block(1,Material.COAL_ORE,new ItemStack(Material.COAL));
        Bukkit.getPluginManager().callEvent(new BlockPlaceEvent(ore,ore.getState(),ore.getRelative(0,-1,0),new ItemStack(Material.COAL_ORE),player,true,EquipmentSlot.HAND));
        breakAndDrop(ore,false,new ItemStack(Material.COAL));
        assertEquals(0,skills.getXp(player.getUniqueId(),SkillType.MINING));assertEquals(0,amount(CollectionInfoGui.Category.MINING,Material.COAL));
    }
    @Test void silkTouchRetainsBaseExperienceAndSkipsMiningRewards() {
        player.getInventory().getItemInMainHand().addEnchantment(Registry.ENCHANTMENT.get(NamespacedKey.minecraft("silk_touch")),1);
        var event=breakAndDrop(block(0,Material.COAL_ORE,new ItemStack(Material.COAL_ORE)),false,new ItemStack(Material.COAL_ORE));
        assertEquals(1,event.getItems().getFirst().getItemStack().getAmount());assertEquals(1,skills.getXp(player.getUniqueId(),SkillType.MINING));
        assertEquals(0,amount(CollectionInfoGui.Category.MINING,Material.COAL));
    }
    @Test void onlyMatureCropsEarnFortuneAndSeedsAreUnchanged() {
        var immature=block(0,Material.WHEAT,new ItemStack(Material.WHEAT_SEEDS));
        breakAndDrop(immature,false,new ItemStack(Material.WHEAT_SEEDS));assertEquals(0,amount(CollectionInfoGui.Category.FARMING,Material.WHEAT_SEEDS));
        var mature=block(1,Material.WHEAT,new ItemStack(Material.WHEAT),new ItemStack(Material.WHEAT_SEEDS,2));
        var age=(Ageable)mature.getBlockData();age.setAge(age.getMaximumAge());mature.setBlockData(age);
        breakAndDrop(mature,false,new ItemStack(Material.WHEAT),new ItemStack(Material.WHEAT_SEEDS,2));
        assertEquals(3,amount(CollectionInfoGui.Category.FARMING,Material.WHEAT));assertEquals(2,amount(CollectionInfoGui.Category.FARMING,Material.WHEAT_SEEDS));
        assertEquals(8000,service.store().get(player.getUniqueId(),"FARMING:xp_remainder"));
    }
    @Test void manualChainCountsNaturalSegmentsOnceAndLeavesPlacedYieldUnboosted() {
        var lower=block(0,Material.SUGAR_CANE,new ItemStack(Material.SUGAR_CANE));
        var upper=(BlockMock)lower.getRelative(0,1,0);upper.setType(Material.SUGAR_CANE);upper.setDrops(List.of(new ItemStack(Material.SUGAR_CANE)));
        Bukkit.getPluginManager().callEvent(new BlockPlaceEvent(lower,lower.getState(),lower.getRelative(0,-1,0),new ItemStack(Material.SUGAR_CANE),player,true,EquipmentSlot.HAND));
        breakAndDrop(lower,false,new ItemStack(Material.SUGAR_CANE));
        assertEquals(Material.AIR,upper.getType());assertEquals(3,amount(CollectionInfoGui.Category.FARMING,Material.SUGAR_CANE));
        assertEquals(4000,service.store().get(player.getUniqueId(),"FARMING:xp_remainder"));
    }
    @Test void pistonMovesPlacementMarkerWithTheOre() {
        var ore=block(0,Material.COAL_ORE,new ItemStack(Material.COAL));
        Bukkit.getPluginManager().callEvent(new BlockPlaceEvent(ore,ore.getState(),ore.getRelative(0,-1,0),new ItemStack(Material.COAL_ORE),player,true,EquipmentSlot.HAND));
        var piston=world.getBlockAt(-1,64,0);Bukkit.getPluginManager().callEvent(new BlockPistonExtendEvent(piston,List.of(movable(ore)),BlockFace.EAST));
        ore.setType(Material.AIR);var moved=block(1,Material.COAL_ORE,new ItemStack(Material.COAL));breakAndDrop(moved,false,new ItemStack(Material.COAL));
        assertEquals(0,amount(CollectionInfoGui.Category.MINING,Material.COAL));assertEquals(0,skills.getXp(player.getUniqueId(),SkillType.MINING));
    }
    @Test void rightClickHarvestAndCancellationSettleOnlyConfirmedMatureYield() {
        var berry=block(0,Material.SWEET_BERRY_BUSH);var data=(Ageable)berry.getBlockData();data.setAge(data.getMaximumAge());berry.setBlockData(data);
        var cancelled=new org.bukkit.event.player.PlayerHarvestBlockEvent(player,berry,EquipmentSlot.HAND,new ArrayList<>(List.of(new ItemStack(Material.SWEET_BERRIES,2))));
        cancelled.setCancelled(true);Bukkit.getPluginManager().callEvent(cancelled);assertEquals(0,amount(CollectionInfoGui.Category.FARMING,Material.SWEET_BERRIES));
        var event=new org.bukkit.event.player.PlayerHarvestBlockEvent(player,berry,EquipmentSlot.HAND,new ArrayList<>(List.of(new ItemStack(Material.SWEET_BERRIES,2))));
        Bukkit.getPluginManager().callEvent(event);assertEquals(6,event.getItemsHarvested().getFirst().getAmount());assertEquals(6,amount(CollectionInfoGui.Category.FARMING,Material.SWEET_BERRIES));
        assertEquals(6000,service.store().get(player.getUniqueId(),"FARMING:xp_remainder"));
    }
    @Test void retractUsesTheMovementDirectionAlreadyProvidedByPaper() {
        var ore=block(2,Material.COAL_ORE,new ItemStack(Material.COAL));
        Bukkit.getPluginManager().callEvent(new BlockPlaceEvent(ore,ore.getState(),ore.getRelative(0,-1,0),new ItemStack(Material.COAL_ORE),player,true,EquipmentSlot.HAND));
        Bukkit.getPluginManager().callEvent(new BlockPistonRetractEvent(world.getBlockAt(0,64,0),List.of(movable(ore)),BlockFace.WEST));
        ore.setType(Material.AIR);breakAndDrop(block(1,Material.COAL_ORE,new ItemStack(Material.COAL)),false,new ItemStack(Material.COAL));
        assertEquals(0,amount(CollectionInfoGui.Category.MINING,Material.COAL));assertEquals(0,skills.getXp(player.getUniqueId(),SkillType.MINING));
    }
    @Test void protectedUpperSegmentCancelsWholeChain() {
        var lower=block(0,Material.SUGAR_CANE,new ItemStack(Material.SUGAR_CANE));var upper=lower.getRelative(0,1,0);upper.setType(Material.SUGAR_CANE);
        var plugin=MockBukkit.createMockPlugin("region_test");Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler public void protectedBlock(BlockBreakEvent e) { if(e.getBlock().equals(upper))e.setCancelled(true); }
        },plugin);
        assertNull(breakAndDrop(lower,false,new ItemStack(Material.SUGAR_CANE)));assertEquals(Material.SUGAR_CANE,lower.getType());
        assertEquals(0,amount(CollectionInfoGui.Category.FARMING,Material.SUGAR_CANE));
    }
    @Test void gatheringKeepsToolAndBonusesFromBeforeChangingEquipment() {
        List<ProfessionGatherEvent> snapshots=new ArrayList<>();var plugin=MockBukkit.createMockPlugin("snapshot_test");
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler public void complete(ProfessionGatherEvent e) { snapshots.add(e); }
        },plugin);
        var ore=block(0,Material.COAL_ORE,new ItemStack(Material.COAL));var state=ore.getState();
        player.getInventory().getItemInMainHand().addEnchantment(Registry.ENCHANTMENT.get(NamespacedKey.minecraft("fortune")),3);
        Bukkit.getPluginManager().callEvent(new BlockBreakEvent(ore,player));
        stats.set(player.getUniqueId(),new PlayerStatsData());player.getInventory().setItemInMainHand(new ItemStack(Material.STONE_PICKAXE));ore.setType(Material.AIR);
        Bukkit.getPluginManager().callEvent(new BlockDropItemEvent(ore,state,player,new ArrayList<>(List.of(world.dropItemNaturally(ore.getLocation(),new ItemStack(Material.COAL,6))))));
        server.getScheduler().performTicks(2);assertEquals(1,snapshots.size());var snapshot=snapshots.getFirst();
        assertEquals(200,snapshot.bonuses().fortune());assertEquals(Material.IRON_PICKAXE,snapshot.tool().getType());assertEquals(3,snapshot.products().getFirst().getAmount());
        snapshot.products().getFirst().setAmount(64);snapshot.tool().setType(Material.DIRT);
        assertEquals(3,snapshot.products().getFirst().getAmount());assertEquals(Material.IRON_PICKAXE,snapshot.tool().getType());
    }
}
