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
    void pickupPlainVanillaItemAddsLoreAndTag() {
        Item item = drop(new ItemStack(Material.DIRT));

        listener.onEntityPickupItem(new EntityPickupItemEvent(player, item, 0));

        ItemMeta meta = item.getItemStack().getItemMeta();
        assertNotNull(meta);
        assertTrue(meta.getPersistentDataContainer().has(
                VanillaItemLoreListener.vanillaOriginKey(plugin),
                PersistentDataType.BYTE));
        assertEquals("[普通原版物品]", plainLastLore(meta));
    }

    @Test
    void pickupItemWithExistingLoreIsNotChanged() {
        ItemStack stack = new ItemStack(Material.DIRT);
        stack.editMeta(meta -> meta.lore(List.of(Component.text("已有自定义 Lore"))));
        Item item = drop(stack);

        listener.onEntityPickupItem(new EntityPickupItemEvent(player, item, 0));

        ItemMeta meta = item.getItemStack().getItemMeta();
        assertNotNull(meta);
        assertNull(meta.getPersistentDataContainer().get(
                VanillaItemLoreListener.vanillaOriginKey(plugin),
                PersistentDataType.BYTE));
        assertEquals("已有自定义 Lore", plainLastLore(meta));
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
