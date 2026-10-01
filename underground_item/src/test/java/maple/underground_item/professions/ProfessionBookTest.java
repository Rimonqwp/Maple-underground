package maple.underground_item.professions;

import maple.underground_state.UndergroundState;
import maple.underground_state.professions.*;
import maple.underground_state.skills.SkillType;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProfessionBookTest {
    private static final class MemoryStore extends ProfessionStore {
        MemoryStore(UUID uuid) { cacheLoaded(uuid,Map.of()); }
    }
    private static Object allocate(Class<?> type) throws Exception {
        Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);return ((sun.misc.Unsafe)f.get(null)).allocateInstance(type);
    }
    private static void set(Object o,String name,Object value) throws Exception { Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }
    @Test void registrationDoesNotConsumeEquipmentOrGrantGrowthAndIsIdempotent() throws Exception {
        var server=MockBukkit.mock();server.addSimpleWorld("world");PlayerMock player=server.addPlayer();
        var catalog=new ProfessionCatalog(YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/professions.yml")),StandardCharsets.UTF_8)));
        var core=(UndergroundState)allocate(UndergroundState.class);var service=(ProfessionService)allocate(ProfessionService.class);var store=new MemoryStore(player.getUniqueId());
        set(service,"store",store);set(core,"professionService",service);var book=new ProfessionBook(core,catalog,null);
        for(String branch:List.of("yield","experience","balanced","rare","event"))for(String part:List.of("helmet","chestplate","leggings","boots"))player.getInventory().addItem(catalog.create("farming_"+branch+"_1_"+part));
        player.getInventory().setHelmet(catalog.create("mining_main_1_helmet"));
        var fake=new ItemStack(Material.LEATHER_BOOTS);var meta=fake.getItemMeta();meta.displayName(catalog.create("mining_main_1_boots").getItemMeta().displayName());fake.setItemMeta(meta);player.getInventory().addItem(fake);
        var before=Arrays.stream(player.getInventory().getContents()).map(i -> i==null ? null : i.clone()).toArray(ItemStack[]::new);
        assertEquals(21,book.register(player));assertEquals(0,book.register(player));
        assertArrayEquals(before,player.getInventory().getContents());assertEquals(21,store.discoveries(player.getUniqueId()).size());
        assertNull(core.getSkillProgressManager());assertNull(core.getPlayerStatsManager());
        book.open(player,SkillType.FARMING);
        String badge=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(player.getOpenInventory().getTopInventory().getItem(50).getItemMeta().lore().get(1));
        assertTrue(badge.contains("五套徽记：已获得"));
        assertEquals(21,store.discoveries(player.getUniqueId()).size());assertArrayEquals(before,player.getInventory().getContents());
    }
}
