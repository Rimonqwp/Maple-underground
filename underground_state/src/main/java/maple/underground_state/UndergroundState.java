package maple.underground_state;

import maple.underground_state.commands.ChestDumpCommand;
import maple.underground_state.commands.BackpackBagCommand;
import maple.underground_state.commands.InfoMenuCommand;
import maple.underground_state.commands.PlayerStatsCommand;
import maple.underground_state.commands.UndergroundStateCommand;
import maple.underground_state.collection.CollectionProgressDB;
import maple.underground_state.collection.CollectionProgressListener;
import maple.underground_state.collection.CollectionProgressManager;
import maple.underground_state.backpack.BackpackBagPlaceListener;
import maple.underground_state.backpack.BackpackDataDB;
import maple.underground_state.bank.BankDataDB;
import maple.underground_state.bank.BankManager;
import maple.underground_state.dungeon.panel.DungeonPanelDataProvider;
import maple.underground_state.dungeon.panel.DungeonPanelManager;
import maple.underground_state.guild.GuildDataDB;
import maple.underground_state.items.VanillaItemLoreListener;
import maple.underground_state.skills.SkillProgressDB;
import maple.underground_state.skills.SkillProgressListener;
import maple.underground_state.skills.SkillProgressManager;
import maple.underground_state.skills.SkillRewardService;
import maple.underground_state.skills.xp.CombatXpOverlayService;
import maple.underground_state.skills.xp.AlchemyBrewingService;
import maple.underground_state.skills.xp.EnchantingXpService;
import maple.underground_state.skills.xp.CraftingXpService;
import maple.underground_state.skills.xp.ExplorationXpService;
import maple.underground_state.skills.xp.FarmingXpService;
import maple.underground_state.skills.xp.FishingXpService;
import maple.underground_state.skills.xp.ForagingXpService;
import maple.underground_state.skills.xp.MiningXpService;
import maple.underground_state.skills.xp.SocialXpService;
import maple.underground_state.stats.HealthRegenTask;
import maple.underground_state.stats.PlayerCritListener;
import maple.underground_state.stats.PlayerStatsDB;
import maple.underground_state.stats.PlayerStatsData;
import maple.underground_state.stats.PlayerStatsDisplayTask;
import maple.underground_state.stats.PlayerStatsListener;
import maple.underground_state.stats.PlayerStatsManager;
import maple.underground_state.stats.PlayerStatsRuntime;
import maple.underground_state.stats.PlayerStatsRuntimeListener;
import maple.underground_state.wardrobe.WardrobeDataDB;
import maple.underground_state.wardrobe.WardrobeSyncListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public final class UndergroundState extends JavaPlugin {

    private static UndergroundState instance;
    private maple.underground_state.professions.ProfessionService professionService;
    public maple.underground_state.professions.ProfessionService getProfessionService() { return professionService; }
    public CombatXpOverlayService getCombatXpOverlayService() { return combatXpOverlayService; }

    private PlayerStatsDB playerStatsDB;
    private PlayerStatsManager playerStatsManager;
    private BankDataDB bankDataDB;
    private BankManager bankManager;
    private BackpackDataDB backpackDataDB;
    private WardrobeDataDB wardrobeDataDB;
    private GuildDataDB guildDataDB;
    private PlayerStatsRuntime playerStatsRuntime;
    private SkillProgressDB skillProgressDB;
    private SkillProgressManager skillProgressManager;
    private CollectionProgressDB collectionProgressDB;
    private CollectionProgressManager collectionProgressManager;
    private SkillRewardService skillRewardService;
    private CombatXpOverlayService combatXpOverlayService;
    private MiningXpService miningXpService;
    private ForagingXpService foragingXpService;
    private FarmingXpService farmingXpService;
    private FishingXpService fishingXpService;
    private EnchantingXpService enchantingXpService;
    private AlchemyBrewingService alchemyBrewingService;
    private ExplorationXpService explorationXpService;
    private SocialXpService socialXpService;
    private CraftingXpService craftingXpService;
    private PlayerStatsDisplayTask playerStatsDisplayTask;
    private HealthRegenTask healthRegenTask;
    private BukkitTask collectionSaveTask;
    private DungeonPanelManager dungeonPanelManager;
    private volatile DungeonPanelDataProvider dungeonPanelDataProvider;

    public static UndergroundState getInstance() {
        return instance;
    }

    public PlayerStatsManager getPlayerStatsManager() {
        return playerStatsManager;
    }

    public PlayerStatsRuntime getPlayerStatsRuntime() {
        return playerStatsRuntime;
    }

    public SkillProgressManager getSkillProgressManager() {
        return skillProgressManager;
    }

    public SkillRewardService getSkillRewardService() {
        return skillRewardService;
    }

    public CollectionProgressManager getCollectionProgressManager() {
        return collectionProgressManager;
    }

    public DungeonPanelManager getDungeonPanelManager() {
        return dungeonPanelManager;
    }

    public BankManager getBankManager() {
        return bankManager;
    }

    public WardrobeDataDB getWardrobeDataDB() {
        return wardrobeDataDB;
    }

    public BackpackDataDB getBackpackDataDB() {
        return backpackDataDB;
    }

    public GuildDataDB getGuildDataDB() {
        return guildDataDB;
    }

    public DungeonPanelDataProvider getDungeonPanelDataProvider() {
        return dungeonPanelDataProvider;
    }

    public void setDungeonPanelDataProvider(DungeonPanelDataProvider dungeonPanelDataProvider) {
        this.dungeonPanelDataProvider = dungeonPanelDataProvider;
    }

    @Override
    public void onEnable() {
        instance = this;
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("Failed to create plugin data folder: " + getDataFolder().getAbsolutePath());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        playerStatsDB = new PlayerStatsDB(getDataFolder(), getLogger());
        playerStatsManager = new PlayerStatsManager(playerStatsDB);
        bankDataDB = new BankDataDB(getDataFolder(), getLogger());
        bankManager = new BankManager(bankDataDB, getLogger());
        backpackDataDB = new BackpackDataDB(getDataFolder(), getLogger());
        wardrobeDataDB = new WardrobeDataDB(getDataFolder(), getLogger());
        guildDataDB = new GuildDataDB(getDataFolder(), getLogger());
        playerStatsRuntime = new PlayerStatsRuntime(this, playerStatsManager);
        skillProgressDB = new SkillProgressDB(getDataFolder(), getLogger());
        skillProgressManager = new SkillProgressManager(skillProgressDB);
        collectionProgressDB = new CollectionProgressDB(getDataFolder(), getLogger());
        collectionProgressManager = new CollectionProgressManager(collectionProgressDB);
        skillRewardService = new SkillRewardService(this, skillProgressManager, playerStatsManager);
        combatXpOverlayService = new CombatXpOverlayService(this, playerStatsRuntime, skillProgressManager, skillRewardService);
        miningXpService = new MiningXpService(skillProgressManager, skillRewardService, combatXpOverlayService);
        foragingXpService = new ForagingXpService(skillProgressManager, skillRewardService, combatXpOverlayService);
        farmingXpService = new FarmingXpService(skillProgressManager, skillRewardService, combatXpOverlayService);
        fishingXpService = new FishingXpService(skillProgressManager, skillRewardService, combatXpOverlayService);
        enchantingXpService = new EnchantingXpService(skillProgressManager, skillRewardService, combatXpOverlayService);
        alchemyBrewingService = new AlchemyBrewingService(this, skillProgressManager, skillRewardService, combatXpOverlayService);
        explorationXpService = new ExplorationXpService(skillProgressManager, skillRewardService, combatXpOverlayService);
        socialXpService = new SocialXpService(this, skillProgressManager, skillRewardService, combatXpOverlayService);
        craftingXpService = new CraftingXpService(skillProgressManager, skillRewardService, combatXpOverlayService);
        dungeonPanelManager = new DungeonPanelManager(this);
        professionService = new maple.underground_state.professions.ProfessionService(this);
        Bukkit.getPluginManager().registerEvents(professionService, this);

        Bukkit.getPluginManager().registerEvents(new PlayerStatsListener(this, playerStatsManager), this);
        Bukkit.getPluginManager().registerEvents(new PlayerStatsRuntimeListener(this, playerStatsRuntime), this);
        Bukkit.getPluginManager().registerEvents(new PlayerCritListener(playerStatsManager, playerStatsRuntime), this);
        Bukkit.getPluginManager().registerEvents(new SkillProgressListener(skillProgressManager), this);
        Bukkit.getPluginManager().registerEvents(new CollectionProgressListener(this, collectionProgressManager), this);
        Bukkit.getPluginManager().registerEvents(new VanillaItemLoreListener(this), this);
        Bukkit.getPluginManager().registerEvents(combatXpOverlayService, this);
        Bukkit.getPluginManager().registerEvents(miningXpService, this);
        Bukkit.getPluginManager().registerEvents(foragingXpService, this);
        Bukkit.getPluginManager().registerEvents(farmingXpService, this);
        Bukkit.getPluginManager().registerEvents(fishingXpService, this);
        Bukkit.getPluginManager().registerEvents(enchantingXpService, this);
        Bukkit.getPluginManager().registerEvents(alchemyBrewingService, this);
        Bukkit.getPluginManager().registerEvents(explorationXpService, this);
        Bukkit.getPluginManager().registerEvents(socialXpService, this);
        Bukkit.getPluginManager().registerEvents(craftingXpService, this);
        Bukkit.getPluginManager().registerEvents(dungeonPanelManager, this);
        Bukkit.getPluginManager().registerEvents(new WardrobeSyncListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BackpackBagPlaceListener(), this);
        playerStatsRuntime.registerExtension(this, "combat-xp-overlay", combatXpOverlayService);
        var infoMenuCommand = new InfoMenuCommand();
        var chestDumpCommand = new ChestDumpCommand(this);
        Bukkit.getPluginManager().registerEvents(infoMenuCommand, this);
        Bukkit.getPluginManager().registerEvents(chestDumpCommand, this);
        playerStatsDisplayTask = new PlayerStatsDisplayTask(this, playerStatsRuntime);
        playerStatsDisplayTask.runTaskTimer(this, 40L, 20L);
        healthRegenTask = new HealthRegenTask(this, playerStatsRuntime);
        healthRegenTask.runTaskTimer(this, 40L, 40L);
        collectionSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (collectionProgressManager != null) {
                collectionProgressManager.saveDirty();
            }
        }, 1200L, 1200L);
        for (var player : Bukkit.getOnlinePlayers()) {
            queuePlayerStatsInitialLoad(player.getUniqueId());
            skillProgressManager.onJoin(player.getUniqueId());
            UUID onlineUuid = player.getUniqueId();
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> collectionProgressManager.loadIntoCache(onlineUuid));
        }

        var playerStatsCmd = getCommand("playerstats");
        if (playerStatsCmd != null) {
            var executor = new PlayerStatsCommand(playerStatsManager);
            playerStatsCmd.setExecutor(executor);
            playerStatsCmd.setTabCompleter(executor);
        }

        var infoCmd = getCommand("info-new");
        if (infoCmd != null) {
            infoCmd.setExecutor(infoMenuCommand);
        }

        var dungeonPanelCmd = getCommand("dungeon_panel");
        if (dungeonPanelCmd != null) {
            dungeonPanelCmd.setExecutor((sender, command, label, args) -> {
                if (!(sender instanceof org.bukkit.entity.Player player)) {
                    sender.sendMessage(org.bukkit.ChatColor.RED + "Only players can use this command.");
                    return true;
                }
                dungeonPanelManager.openMainPanel(player);
                return true;
            });
        }

        var chestDumpCmd = getCommand("chestdump");
        if (chestDumpCmd != null) {
            chestDumpCmd.setExecutor(chestDumpCommand);
        }

        var backpackBagCmd = getCommand("backpackbag");
        if (backpackBagCmd != null) {
            var executor = new BackpackBagCommand();
            backpackBagCmd.setExecutor(executor);
            backpackBagCmd.setTabCompleter(executor);
        }

        var undergroundStateCmd = getCommand("underground_state");
        if (undergroundStateCmd != null) {
            var executor = new UndergroundStateCommand(skillProgressManager, skillRewardService);
            undergroundStateCmd.setExecutor(executor);
            undergroundStateCmd.setTabCompleter(executor);
        }
    }

    @Override
    public void onDisable() {
        if (professionService != null) { professionService.shutdown(); professionService = null; }
        if (playerStatsRuntime != null) {
            playerStatsRuntime.unregisterExtension(this, "combat-xp-overlay");
        }
        if (playerStatsDisplayTask != null) {
            playerStatsDisplayTask.cancel();
            playerStatsDisplayTask = null;
        }
        if (healthRegenTask != null) {
            healthRegenTask.cancel();
            healthRegenTask = null;
        }
        if (collectionSaveTask != null) {
            collectionSaveTask.cancel();
            collectionSaveTask = null;
        }
        if (playerStatsManager != null) {
            for (var player : Bukkit.getOnlinePlayers()) {
                playerStatsManager.save(player.getUniqueId());
            }
        }
        if (skillProgressManager != null) {
            for (var player : Bukkit.getOnlinePlayers()) {
                skillProgressManager.save(player.getUniqueId());
            }
        }
        if (collectionProgressManager != null) {
            for (var player : Bukkit.getOnlinePlayers()) {
                collectionProgressManager.save(player.getUniqueId());
            }
        }
        if (socialXpService != null) {
            socialXpService.shutdown();
        }
        combatXpOverlayService = null;
        miningXpService = null;
        foragingXpService = null;
        farmingXpService = null;
        fishingXpService = null;
        enchantingXpService = null;
        alchemyBrewingService = null;
        explorationXpService = null;
        socialXpService = null;
        craftingXpService = null;
        collectionProgressManager = null;
        collectionProgressDB = null;
        dungeonPanelManager = null;
        dungeonPanelDataProvider = null;
        wardrobeDataDB = null;
        backpackDataDB = null;
        guildDataDB = null;
        bankManager = null;
        bankDataDB = null;
        instance = null;
    }

    private void queuePlayerStatsInitialLoad(UUID uuid) {
        if (uuid == null || playerStatsManager == null) {
            return;
        }
        playerStatsManager.onJoin(uuid);
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            PlayerStatsData loaded = playerStatsManager.loadOrCreateSnapshot(uuid);
            Bukkit.getScheduler().runTask(this, () -> playerStatsManager.completeInitialLoad(uuid, loaded));
        });
    }
}
