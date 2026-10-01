package maple.underground_item.professions;

import maple.underground_state.professions.ProfessionBonuses;
import maple.underground_state.skills.SkillType;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProfessionCatalogTest {
    ProfessionCatalog catalog;PlayerMock player;
    @BeforeEach void setup() {
        var server=MockBukkit.mock();server.addSimpleWorld("world");player=server.addPlayer();
        catalog=new ProfessionCatalog(YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/professions.yml")),StandardCharsets.UTF_8)));
    }
    @AfterEach void cleanup() { MockBukkit.unmock(); }
    @Test void completeCatalogAndIdentities() {
        assertEquals(32,catalog.sets().size());assertEquals(128,catalog.items().stream().filter(d -> d.kind().equals("armor")).count());
        assertEquals(12,catalog.items().stream().filter(d -> d.kind().equals("tool")).count());assertEquals(6,catalog.items().stream().filter(d -> d.kind().equals("weapon")).count());
        Set<String> identities=new HashSet<>();for(var d:catalog.items()) {
            var item=catalog.create(d.id());assertEquals(d,catalog.identify(item));assertTrue(identities.add(d.id()));
            if(!d.kind().equals("material"))assertTrue(item.getItemMeta().isUnbreakable());
        }
    }
    private void wear(String set) {
        player.getInventory().setHelmet(catalog.create(set+"_helmet"));player.getInventory().setChestplate(catalog.create(set+"_chestplate"));
        player.getInventory().setLeggings(catalog.create(set+"_leggings"));player.getInventory().setBoots(catalog.create(set+"_boots"));
    }
    @Test void fullSetMixingLevelGatesAndCaps() {
        int[] level={60};var equipment=new ProfessionEquipment(catalog,p -> true,(p,s) -> level[0]);
        wear("farming_experience_2");player.getInventory().setItemInMainHand(catalog.create("farming_tool_6"));
        var bonus=equipment.modifyProfessionBonuses(player,SkillType.FARMING,ProfessionBonuses.ZERO).bounded();
        assertEquals(190,bonus.fortune(),.00001);assertEquals(40,bonus.xpPercent(),.00001);assertEquals(2,bonus.eventTier());
        player.getInventory().setHelmet(catalog.create("farming_yield_2_helmet"));
        bonus=equipment.modifyProfessionBonuses(player,SkillType.FARMING,ProfessionBonuses.ZERO).bounded();
        assertEquals(230,bonus.fortune(),.00001);assertEquals(30,bonus.xpPercent(),.00001);assertEquals(2,bonus.eventTier());
        player.getInventory().setBoots(catalog.create("mining_yield_2_boots"));
        assertEquals(0,equipment.modifyProfessionBonuses(player,SkillType.FARMING,ProfessionBonuses.ZERO).eventTier());
        level[0]=10;
        assertEquals(ProfessionBonuses.ZERO,equipment.modifyProfessionBonuses(player,SkillType.FARMING,ProfessionBonuses.ZERO));
        assertFalse(equipment.canUseProfessionItem(player,catalog.create("farming_tool_6")));
        assertFalse(equipment.canUseProfessionItem(player,catalog.create("mining_weapon_3")));
        assertEquals(0,equipment.modifyMaxHealth(player,null,0));
    }
    @Test void allSetsHaveCorrectFullValuesAndLowestEligibility() {
        var equipment=new ProfessionEquipment(catalog,p -> true,(p,s) -> 60);
        for(var entry:catalog.sets().entrySet()) {
            wear(entry.getKey());var s=entry.getValue();SkillType skill=s.getString("job").equals("farming") ? SkillType.FARMING : SkillType.MINING;
            var bonus=equipment.modifyProfessionBonuses(player,skill,ProfessionBonuses.ZERO);
            assertEquals(s.getDouble("fortune"),bonus.fortune(),.00001);assertEquals(s.getDouble("xp"),bonus.xpPercent(),.00001);
            assertEquals(s.getDouble("rare"),bonus.rarePercent(),.00001);assertEquals(s.getDouble("event"),bonus.eventPercent(),.00001);
            assertEquals(s.getInt("event_tier"),bonus.eventTier());assertEquals(s.getDouble("health"),equipment.modifyMaxHealth(player,null,0));
            assertEquals(s.getDouble("defense"),equipment.modifyDefense(player,null,0));assertEquals(s.getDouble("reduction")/100,equipment.reduction(player,skill));
        }
        wear("farming_main_6");player.getInventory().setHelmet(catalog.create("farming_main_1_helmet"));
        assertEquals(0,equipment.modifyProfessionBonuses(player,SkillType.FARMING,ProfessionBonuses.ZERO).eventTier());
        player.getInventory().setHelmet(catalog.create("farming_main_3_helmet"));assertEquals(1,equipment.modifyProfessionBonuses(player,SkillType.FARMING,ProfessionBonuses.ZERO).eventTier());
    }
    @Test void personalCraftGatesAreIndependentFromClaimingAndMainline() {
        var t6=catalog.get("farming_main_6_helmet");
        assertFalse(catalog.canCraft(t6,55,m -> 250000,0,29));assertTrue(catalog.canCraft(t6,55,m -> 250000,0,30));
        assertFalse(catalog.canCraft(t6,54,m -> 250000,0,30));
        var alt=catalog.get("farming_experience_2_helmet");assertTrue(catalog.canCraft(alt,45,m -> 100000,0,0));
        var event=catalog.get("mining_event_2_helmet");assertFalse(catalog.canCraft(event,48,m -> 250000,99,10));
        assertFalse(catalog.canCraft(event,48,m -> 250000,100,9));assertTrue(catalog.canCraft(event,48,m -> 250000,100,10));
        assertEquals("farming_experience_1_helmet",alt.config().getString("recipe.p"));
        assertFalse(catalog.canCraft(catalog.get("farming_main_4_helmet"),30,m -> m==Material.WHEAT || m==Material.CARROT ? 10000 : 0,0,0));
    }
    @Test void renameCannotForgeIdentityAndUpgradesPreserveLegalEnchantments() {
        var fake=new ItemStack(Material.LEATHER_HELMET);var meta=fake.getItemMeta();meta.displayName(catalog.create("farming_main_1_helmet").getItemMeta().displayName());fake.setItemMeta(meta);
        assertNull(catalog.identify(fake));assertFalse(catalog.matches(fake,"farming_main_1_helmet"));
        var old=catalog.create("mining_tool_2");meta=old.getItemMeta();meta.displayName(net.kyori.adventure.text.Component.text("我的矿镐"));old.setItemMeta(meta);
        var fortune=Objects.requireNonNull(Registry.ENCHANTMENT.get(NamespacedKey.minecraft("fortune")));old.addEnchantment(fortune,3);
        var next=catalog.upgrade(catalog.get("mining_tool_3"),old);assertEquals(old.getItemMeta().displayName(),next.getItemMeta().displayName());assertEquals(3,next.getEnchantmentLevel(fortune));
        assertEquals(3,next.getEnchantmentLevel(Registry.ENCHANTMENT.get(NamespacedKey.minecraft("efficiency"))));assertEquals("mining_tool_3",catalog.identify(next).id());
        assertFalse(catalog.matches(old,"vanilla:IRON_PICKAXE"));
    }
    @Test void configurationRecipesRespectAllNineIdentities() {
        var plugin=MockBukkit.createMockPlugin("underground_item");var crafting=new ProfessionCrafting(plugin,catalog,null);
        for(var d:catalog.items()) if(d.config().contains("recipe")) {
            String[] layout={"a","n","b","a","p","b","a","n","b"};ItemStack[] matrix=new ItemStack[9];
            for(int i=0;i<9;i++) { String token=d.config().getString("recipe."+layout[i]);matrix[i]=token.startsWith("vanilla:") ? new ItemStack(catalog.materialOf(token)) : catalog.create(token); }
            assertTrue(crafting.matches(d,matrix),d.id());matrix[4]=new ItemStack(matrix[4].getType());
            if(!d.config().getString("recipe.p").startsWith("vanilla:"))assertFalse(crafting.matches(d,matrix),d.id());
        }
        crafting.close();
    }
    @Test void craftingConsumesOnceRespectsOutputCapacityAndRejectsBatchEquipment() {
        var plugin=MockBukkit.createMockPlugin("underground_item");
        boolean[] allowed={true};var crafting=new ProfessionCrafting(plugin,catalog,null,(p,d) -> allowed[0]);
        // MockBukkit's workbench incorrectly exposes its result as a tenth matrix slot.
        var inv=new org.mockbukkit.mockbukkit.inventory.WorkbenchInventoryMock(player) {
            @Override public ItemStack[] getMatrix() { return Arrays.copyOf(super.getMatrix(),9); }
            @Override public org.bukkit.inventory.Recipe getRecipe() { return null; }
        };
        player.openInventory(inv);
        var definition=catalog.get("farming_main_1_helmet");crafting.select(player,definition.id());
        String[] layout={"a","n","b","a","p","b","a","n","b"};ItemStack[] matrix=new ItemStack[9];
        for(int i=0;i<9;i++)matrix[i]=new ItemStack(catalog.materialOf(definition.config().getString("recipe."+layout[i])),2);
        inv.setMatrix(matrix);
        assertSame(inv,player.getOpenInventory().getTopInventory());
        assertTrue(crafting.matches(definition,inv.getMatrix()));
        var recipe=new org.bukkit.inventory.ShapedRecipe(new NamespacedKey(plugin,"test"),catalog.create(definition.id()));
        var shift=new org.bukkit.event.inventory.CraftItemEvent(recipe,player.getOpenInventory(),org.bukkit.event.inventory.InventoryType.SlotType.RESULT,0,
                org.bukkit.event.inventory.ClickType.SHIFT_LEFT,org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY);
        crafting.craft(shift);assertTrue(shift.isCancelled());assertEquals(2,inv.getMatrix()[4].getAmount());
        allowed[0]=false;
        var left=new org.bukkit.event.inventory.CraftItemEvent(recipe,player.getOpenInventory(),org.bukkit.event.inventory.InventoryType.SlotType.RESULT,0,
                org.bukkit.event.inventory.ClickType.LEFT,org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        crafting.craft(left);assertEquals(2,inv.getMatrix()[4].getAmount());
        allowed[0]=true;player.setItemOnCursor(new ItemStack(Material.DIRT));crafting.craft(left);assertEquals(2,inv.getMatrix()[4].getAmount());
        player.setItemOnCursor(null);crafting.craft(left);
        assertEquals(1,inv.getMatrix()[4].getAmount());assertEquals(definition.id(),catalog.identify(player.getItemOnCursor()).id());
        for(ItemStack remaining:inv.getMatrix())assertEquals(1,remaining.getAmount());
        player.setItemOnCursor(null);
        matrix=new ItemStack[9];for(int i=0;i<9;i++)matrix[i]=new ItemStack(Material.WHEAT,64);inv.setMatrix(matrix);
        crafting.select(player,"wheat_compressed_1");
        var batch=new org.bukkit.event.inventory.CraftItemEvent(recipe,player.getOpenInventory(),org.bukkit.event.inventory.InventoryType.SlotType.RESULT,0,
                org.bukkit.event.inventory.ClickType.SHIFT_LEFT,org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY);
        crafting.craft(batch);assertEquals(64,Arrays.stream(player.getInventory().getStorageContents()).filter(i -> catalog.identify(i)!=null).mapToInt(ItemStack::getAmount).sum());
        for(ItemStack remaining:inv.getMatrix())assertTrue(remaining==null || remaining.getType().isAir());
        crafting.craft(batch);assertEquals(64,Arrays.stream(player.getInventory().getStorageContents()).filter(i -> catalog.identify(i)!=null).mapToInt(ItemStack::getAmount).sum());
        crafting.close();
    }
    @Test void automaticCraftingValidatesIdentityAndCannotBypassPersonalProgress() {
        var plugin=MockBukkit.createMockPlugin("underground_item");var crafting=new ProfessionCrafting(plugin,catalog,null);
        var block=player.getWorld().getBlockAt(0,64,0);block.setType(Material.CRAFTER);var state=(org.bukkit.block.Crafter)block.getState();
        var recipe=new org.bukkit.inventory.ShapelessRecipe(new NamespacedKey(plugin,"test_auto"),catalog.create("wheat_compressed_2"));
        ItemStack[] matrix=new ItemStack[9];for(int i=0;i<9;i++)matrix[i]=catalog.create("wheat_compressed_1");state.getSnapshotInventory().setContents(matrix);state.update();
        assertTrue(crafting.matches(catalog.get("wheat_compressed_2"),state.getInventory().getContents()));
        assertTrue(crafting.matches(catalog.get("wheat_compressed_2"),((org.bukkit.block.Crafter)block.getState()).getInventory().getContents()));
        var event=new org.bukkit.event.block.CrafterCraftEvent(block,recipe,catalog.create("raw_iron_compressed_2"));crafting.automatic(event);
        assertFalse(event.isCancelled());assertEquals("wheat_compressed_2",catalog.identify(event.getResult()).id());
        matrix[4]=catalog.create("farming_main_2_helmet");state.getSnapshotInventory().setContents(matrix);state.update();
        event=new org.bukkit.event.block.CrafterCraftEvent(block,recipe,catalog.create("farming_main_3_helmet"));crafting.automatic(event);assertTrue(event.isCancelled());
        for(int i=0;i<9;i++)matrix[i]=new ItemStack(Material.WHEAT);state.getSnapshotInventory().setContents(matrix);state.update();
        event=new org.bukkit.event.block.CrafterCraftEvent(block,recipe,new ItemStack(Material.HAY_BLOCK));crafting.automatic(event);assertFalse(event.isCancelled());assertEquals(Material.HAY_BLOCK,event.getResult().getType());
        crafting.close();
    }
}
