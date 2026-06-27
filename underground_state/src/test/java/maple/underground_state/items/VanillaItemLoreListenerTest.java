package maple.underground_state.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaItemLoreListenerTest {

    private static final String VANILLA_TAG_TEXT = "\u005b\u666e\u901a\u539f\u7248\u7269\u54c1\u005d";

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private PluginMock plugin;
    private VanillaItemLoreListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        player = server.addPlayer("MapleTester");
        plugin = MockBukkit.createMockPlugin("underground_state");
        listener = new VanillaItemLoreListener(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void pickupSamplePlainVanillaItemsAddsLoreAndTag() {
        List<Material> samples = List.of(
                Material.DIRT,
                Material.COBBLESTONE,
                Material.RAW_IRON,
                Material.OAK_LOG,
                Material.WHEAT_SEEDS,
                Material.BONE,
                Material.STRING,
                Material.DIAMOND,
                Material.APPLE,
                Material.SAND
        );

        for (Material sample : samples) {
            Item item = drop(new ItemStack(sample));

            listener.onEntityPickupItem(new EntityPickupItemEvent(player, item, 0));

            assertVanillaTagged(item);
        }
    }

    @Test
    void pickupItemWithExistingLoreIsNotChanged() {
        ItemStack stack = new ItemStack(Material.DIRT);
        stack.editMeta(meta -> meta.lore(List.of(Component.text("Existing custom lore"))));
        Item item = drop(stack);

        listener.onEntityPickupItem(new EntityPickupItemEvent(player, item, 0));

        ItemMeta meta = item.getItemStack().getItemMeta();
        assertNotNull(meta);
        assertNull(meta.getPersistentDataContainer().get(
                VanillaItemLoreListener.vanillaOriginKey(plugin),
                PersistentDataType.BYTE));
        assertEquals("Existing custom lore", plainLastLore(meta));
    }

    private void assertVanillaTagged(Item item) {
        ItemMeta meta = item.getItemStack().getItemMeta();
        assertNotNull(meta);
        assertTrue(meta.getPersistentDataContainer().has(
                VanillaItemLoreListener.vanillaOriginKey(plugin),
                PersistentDataType.BYTE));
        assertEquals(VANILLA_TAG_TEXT, plainLastLore(meta));
    }

    private Item drop(ItemStack stack) {
        return world.dropItem(new Location(world, 0, 64, 0), stack);
    }

    private String plainLastLore(ItemMeta meta) {
        List<Component> lore = meta.lore();
        assertNotNull(lore);
        return PlainTextComponentSerializer.plainText().serialize(lore.get(lore.size() - 1));
    }
}
