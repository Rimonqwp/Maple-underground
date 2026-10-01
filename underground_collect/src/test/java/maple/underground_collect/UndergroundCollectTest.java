package maple.underground_collect;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class UndergroundCollectTest {

    private ServerMock server;
    private UndergroundCollect plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(UndergroundCollect.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void missingMainPluginDisablesCollectionPlugin() {
        Plugin registeredPlugin = server.getPluginManager().getPlugin("underground_collect");

        assertFalse(plugin.isEnabled());
        assertSame(plugin, registeredPlugin);
        assertEquals("underground_collect", plugin.getDescription().getName());
    }

    @Test
    void serverCanCreateMockPlayer() {
        PlayerMock player = server.addPlayer("MapleTester");

        assertTrue(player.isOnline());
        assertEquals("MapleTester", player.getName());
        assertEquals(1, server.getOnlinePlayers().size());
    }
}
