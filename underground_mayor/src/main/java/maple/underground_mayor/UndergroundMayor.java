package maple.underground_mayor;

import maple.underground_mayor.commands.MayorCommand;
import maple.underground_mayor.compat.CitizensCompat;
import maple.underground_mayor.discord.DiscordBotManager;
import maple.underground_mayor.mayor.MayorCollectionManager;
import maple.underground_mayor.mayor.MayorNpcManager;
import maple.underground_mayor.reward.MayorBadgeManager;
import maple.underground_mayor.reward.MayorBadgeDeliveryService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class UndergroundMayor extends JavaPlugin {
    private CitizensCompat citizensCompat;
    private DiscordBotManager discordBotManager;
    private MayorCollectionManager mayorCollectionManager;
    private MayorNpcManager mayorNpcManager;
    private MayorBadgeManager mayorBadgeManager;
    private MayorBadgeDeliveryService mayorBadgeDeliveryService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        citizensCompat = new CitizensCompat();
        getLogger().info("Hooked into Citizens for seated mayor NPCs.");

        mayorCollectionManager = new MayorCollectionManager(this);
        mayorBadgeManager = new MayorBadgeManager(this);
        mayorBadgeManager.load();
        mayorBadgeDeliveryService = new MayorBadgeDeliveryService(this, mayorCollectionManager, mayorBadgeManager);
        mayorNpcManager = new MayorNpcManager(this, citizensCompat, mayorCollectionManager, mayorBadgeManager, mayorBadgeDeliveryService);
        getServer().getPluginManager().registerEvents(mayorNpcManager, this);
        getServer().getPluginManager().registerEvents(mayorBadgeDeliveryService, this);
        mayorNpcManager.restoreSavedNpcs();
        mayorBadgeDeliveryService.deliverPendingBadgesToOnlinePlayers();

        PluginCommand mayorCommand = getCommand("mayor");
        if (mayorCommand != null) {
            MayorCommand executor = new MayorCommand(mayorNpcManager, mayorBadgeManager);
            mayorCommand.setExecutor(executor);
            mayorCommand.setTabCompleter(executor);
        }

        discordBotManager = new DiscordBotManager(this);
        discordBotManager.start();
    }

    @Override
    public void onDisable() {
        if (mayorNpcManager != null) {
            mayorNpcManager.shutdown();
        }
        if (mayorCollectionManager != null) {
            mayorCollectionManager.saveNow();
        }
        if (discordBotManager != null) {
            discordBotManager.shutdown();
        }
        if (citizensCompat != null) {
            citizensCompat.shutdown();
        }
    }

    public CitizensCompat getCitizensCompat() {
        return citizensCompat;
    }

    public DiscordBotManager getDiscordBotManager() {
        return discordBotManager;
    }

    public MayorCollectionManager getMayorCollectionManager() {
        return mayorCollectionManager;
    }

    public MayorNpcManager getMayorNpcManager() {
        return mayorNpcManager;
    }

    public MayorBadgeManager getMayorBadgeManager() {
        return mayorBadgeManager;
    }

    public MayorBadgeDeliveryService getMayorBadgeDeliveryService() {
        return mayorBadgeDeliveryService;
    }
}
