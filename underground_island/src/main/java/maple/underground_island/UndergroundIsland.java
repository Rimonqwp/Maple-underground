package maple.underground_island;

import maple.underground_state.UndergroundState;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class UndergroundIsland extends JavaPlugin {
    private IslandService islandService;
    private IslandWorldService worldService;
    private IslandVelocityBridge velocityBridge;

    @Override
    public void onEnable() {
        if (!(Bukkit.getPluginManager().getPlugin("underground_state") instanceof UndergroundState)) {
            getLogger().severe("underground_state is required but not available.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();

        PortalItem portalItem = new PortalItem(this);
        IslandRepository repository = new IslandRepository(getLogger());
        this.worldService = new IslandWorldService(this);
        this.islandService = new IslandService(this, repository, worldService);
        this.velocityBridge = new IslandVelocityBridge(this);
        velocityBridge.register();

        IslandCommand command = new IslandCommand(islandService);
        PluginCommand islandCommand = Objects.requireNonNull(getCommand("island"), "island command missing");
        islandCommand.setExecutor(command);
        islandCommand.setTabCompleter(command);

        Bukkit.getPluginManager().registerEvents(new IslandPlayerListener(this, islandService), this);
        Bukkit.getPluginManager().registerEvents(new OneBlockListener(this, islandService, worldService, portalItem), this);
        Bukkit.getPluginManager().registerEvents(new PortalListener(this, worldService, portalItem, velocityBridge), this);

        getLogger().info("underground_island enabled.");
    }

    @Override
    public void onDisable() {
        if (velocityBridge != null) {
            velocityBridge.unregister();
            velocityBridge = null;
        }
        if (islandService != null) {
            islandService.shutdown();
            islandService = null;
        }
        if (worldService != null) {
            worldService.clearCache();
            worldService = null;
        }
    }
}
