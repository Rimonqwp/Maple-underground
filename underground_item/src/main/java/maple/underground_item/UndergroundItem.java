package maple.underground_item;

import maple.underground_caves.commands.ItemHandInfoCommand;
import maple.underground_caves.commands.ItemCatalogCommand;
import maple.underground_caves.gear.*;
import maple.underground_caves.weapons.WeaponListener;
import maple.underground_caves.weapons.WeaponManager;
import maple.underground_caves.weapons.WeaponReloadCommand;
import maple.underground_caves.weapons.WeaponSpecialEffectsListener;
import maple.underground_caves.weapons.WeaponTabCompleter;
import maple.underground_caves.weapons.skill.FreezeSkillRunner;
import maple.underground_caves.weapons.skill.ShowtimeFireworkListener;
import maple.underground_caves.weapons.skill.ShowtimeSkillRunner;
import maple.underground_item.api.CustomEnchantService;
import maple.underground_item.api.DungeonItemBridge;
import maple.underground_item.stats.ItemPlayerStatsExtension;
import maple.underground_state.UndergroundState;
import maple.underground_state.stats.PlayerStatsData;
import maple.underground_state.stats.PlayerStatsManager;
import maple.underground_state.stats.PlayerStatsRuntime;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public final class UndergroundItem extends JavaPlugin {

    private static final String[] DEFAULT_WEAPON_FILES = {
            "cyclone_stick.yml",
            "dragon_rage_diamond.yml",
            "dreadlord_iron.yml",
            "emerald_blade.yml",
            "love_tap_stone.yml",
            "scarf_adaptive_sword.yml",
            "showtime_hook.yml",
            "teleport_diamond.yml",
            "tremor_blade_1.yml",
            "tremor_blade_2.yml",
            "tremor_blade_3.yml",
            "zombie_knight_iron.yml"
    };

    private maple.underground_item.professions.ProfessionModule professions;
    private static UndergroundItem instance;

    private PlayerStatsManager playerStatsManager;
    private PlayerStatsRuntime playerStatsRuntime;
    private DungeonItemBridge dungeonBridge;
    private CustomEnchantService customEnchantService;
    private boolean statsExtensionSuppressed;

    private WeaponManager weaponManager;
    private FreezeSkillRunner freezeSkillRunner;
    private ShowtimeSkillRunner showtimeSkillRunner;

    private GearManager gearManager;
    private ImmortalSkullManager immortalSkullManager;
    private SieveSetManager sieveSetManager;
    private ArcherSetManager archerSetManager;
    private SkeletonSoldierSetManager skeletonSoldierSetManager;
    private SkeletonMasterSetManager skeletonMasterSetManager;
    private UndergroundCaveHelmetManager undergroundCaveHelmetManager;
    private StatsDoubleHelmetManager statsDoubleHelmetManager;
    private CaveSpecialHelmetManager caveSpecialHelmetManager;
    private SecondWindHelmetManager secondWindHelmetManager;
    private ArrowDistanceHelmetManager arrowDistanceHelmetManager;
    private CavernExplorerSetManager cavernExplorerSetManager;
    private AdaptiveSetManager adaptiveSetManager;
    private BloodSetManager bloodSetManager;
    private SublimationGearManager sublimationGearManager;
    private QualityUpgradeItemManager qualityUpgradeItemManager;

    private GearStatsApplyTask gearStatsApplyTask;
    private SublimationWorkbenchManager sublimationWorkbenchManager;
    private SublimationAnvilPrepareListener sublimationAnvilPrepareListener;

    public static UndergroundItem getInstance() {
        return instance;
    }

    public PlayerStatsManager getPlayerStatsManager() {
        return playerStatsManager;
    }

    public PlayerStatsRuntime getPlayerStatsRuntime() {
        return playerStatsRuntime;
    }

    public WeaponManager getWeaponManager() {
        return weaponManager;
    }

    public GearManager getGearManager() {
        return gearManager;
    }

    public ImmortalSkullManager getImmortalSkullManager() {
        return immortalSkullManager;
    }

    public SieveSetManager getSieveSetManager() {
        return sieveSetManager;
    }

    public ArcherSetManager getArcherSetManager() {
        return archerSetManager;
    }

    public SkeletonSoldierSetManager getSkeletonSoldierSetManager() {
        return skeletonSoldierSetManager;
    }

    public SkeletonMasterSetManager getSkeletonMasterSetManager() {
        return skeletonMasterSetManager;
    }

    public UndergroundCaveHelmetManager getUndergroundCaveHelmetManager() {
        return undergroundCaveHelmetManager;
    }

    public StatsDoubleHelmetManager getStatsDoubleHelmetManager() {
        return statsDoubleHelmetManager;
    }

    public CaveSpecialHelmetManager getCaveSpecialHelmetManager() {
        return caveSpecialHelmetManager;
    }

    public SecondWindHelmetManager getSecondWindHelmetManager() {
        return secondWindHelmetManager;
    }

    public ArrowDistanceHelmetManager getArrowDistanceHelmetManager() {
        return arrowDistanceHelmetManager;
    }

    public CavernExplorerSetManager getCavernExplorerSetManager() {
        return cavernExplorerSetManager;
    }

    public AdaptiveSetManager getAdaptiveSetManager() {
        return adaptiveSetManager;
    }

    public BloodSetManager getBloodSetManager() {
        return bloodSetManager;
    }

    public SublimationGearManager getSublimationGearManager() {
        return sublimationGearManager;
    }

    public QualityUpgradeItemManager getQualityUpgradeItemManager() {
        return qualityUpgradeItemManager;
    }

    public void attachDungeonBridge(DungeonItemBridge dungeonBridge) {
        this.dungeonBridge = dungeonBridge;
    }

    public void attachCustomEnchantService(CustomEnchantService customEnchantService) {
        this.customEnchantService = customEnchantService;
        rebuildSublimationWorkbench();
    }

    public void setStatsExtensionSuppressed(boolean suppressed) {
        this.statsExtensionSuppressed = suppressed;
        if (playerStatsRuntime == null) {
            return;
        }
        if (suppressed) {
            playerStatsRuntime.unregisterExtension(this, "item-equipment");
        } else {
            registerStatsExtension();
        }
    }

    public boolean isDungeonWorld(String worldName) {
        return dungeonBridge != null && dungeonBridge.isDungeonWorld(worldName);
    }

    public String getFloorForDungeonWorld(String worldName) {
        if (dungeonBridge == null) {
            return "F1";
        }
        String floor = dungeonBridge.getFloorForDungeonWorld(worldName);
        return floor == null || floor.isBlank() ? "F1" : floor;
    }

    public int getDungeonClassIndex(Player player) {
        return dungeonBridge != null ? dungeonBridge.getDungeonClassIndex(player) : -1;
    }

    public boolean isPlayerDungeonGhost(Player player) {
        return player != null && dungeonBridge != null && dungeonBridge.isDungeonGhost(player.getUniqueId());
    }

    public double getCooldownMultiplier(Player player) {
        return dungeonBridge != null ? dungeonBridge.getCooldownMultiplier(player) : 1.0;
    }

    public double applyHealthBlessing(Player player, double base) {
        return dungeonBridge != null ? dungeonBridge.applyHealthBlessing(player, base) : base;
    }

    public double applyDefenseBlessing(Player player, double base) {
        return dungeonBridge != null ? dungeonBridge.applyDefenseBlessing(player, base) : base;
    }

    public double applyIntelligenceBlessing(Player player, double base) {
        return dungeonBridge != null ? dungeonBridge.applyIntelligenceBlessing(player, base) : base;
    }

    public double applyDamageBlessing(Player player, double base) {
        return dungeonBridge != null ? dungeonBridge.applyDamageBlessing(player, base) : base;
    }

    public void syncVirtualHealthBar(Player player) {
        if (dungeonBridge != null) {
            dungeonBridge.syncVirtualHealthBar(player);
        }
    }

    public double getEffectiveMaxHealth(Player player) {
        if (player == null || playerStatsManager == null) {
            return PlayerStatsData.DEFAULT_HEALTH;
        }
        double base = playerStatsManager.get(player).getHealth();
        base += immortalSkullManager != null ? immortalSkullManager.getBonusHealth(player) : 0;
        base += sieveSetManager != null ? sieveSetManager.getBonusHealth(player) : 0;
        base += archerSetManager != null ? archerSetManager.getBonusHealth(player) : 0;
        base += skeletonSoldierSetManager != null ? skeletonSoldierSetManager.getBonusHealth(player) : 0;
        base += skeletonMasterSetManager != null ? skeletonMasterSetManager.getBonusHealth(player) : 0;
        base += undergroundCaveHelmetManager != null ? undergroundCaveHelmetManager.getBonusHealth(player) : 0;
        base += statsDoubleHelmetManager != null ? statsDoubleHelmetManager.getBonusHealth(player) : 0;
        base += caveSpecialHelmetManager != null ? caveSpecialHelmetManager.getBonusHealth(player) : 0;
        base += secondWindHelmetManager != null ? secondWindHelmetManager.getBonusHealth(player) : 0;
        base += arrowDistanceHelmetManager != null ? arrowDistanceHelmetManager.getBonusHealth(player) : 0;
        base += cavernExplorerSetManager != null ? cavernExplorerSetManager.getBonusHealth(player) : 0;
        base += adaptiveSetManager != null ? adaptiveSetManager.getBonusHealth(player) : 0;
        base += bloodSetManager != null ? bloodSetManager.getBonusHealth(player) : 0;
        base += sublimationGearManager != null ? sublimationGearManager.getBonusHealth(player) : 0;
        return Math.max(1.0, applyHealthBlessing(player, base));
    }

    public double getEffectiveDefense(Player player) {
        if (player == null || playerStatsManager == null) {
            return 0;
        }
        double defense = playerStatsManager.get(player).getDefense();
        defense += gearManager != null ? gearManager.getEquipmentDefense(player) : 0;
        defense += immortalSkullManager != null ? immortalSkullManager.getBonusDefense(player) : 0;
        defense += archerSetManager != null ? archerSetManager.getBonusDefense(player) : 0;
        defense += skeletonSoldierSetManager != null ? skeletonSoldierSetManager.getBonusDefense(player) : 0;
        defense += skeletonMasterSetManager != null ? skeletonMasterSetManager.getBonusDefense(player) : 0;
        defense += undergroundCaveHelmetManager != null ? undergroundCaveHelmetManager.getBonusDefense(player) : 0;
        defense += statsDoubleHelmetManager != null ? statsDoubleHelmetManager.getBonusDefense(player) : 0;
        defense += secondWindHelmetManager != null ? secondWindHelmetManager.getBonusDefense(player) : 0;
        defense += arrowDistanceHelmetManager != null ? arrowDistanceHelmetManager.getBonusDefense(player) : 0;
        defense += cavernExplorerSetManager != null ? cavernExplorerSetManager.getBonusDefense(player) : 0;
        defense += adaptiveSetManager != null ? adaptiveSetManager.getBonusDefense(player) : 0;
        defense += bloodSetManager != null ? bloodSetManager.getBonusDefense(player) : 0;
        defense += sublimationGearManager != null ? sublimationGearManager.getBonusDefense(player) : 0;
        return applyDefenseBlessing(player, defense);
    }

    public double getEffectiveVitalityForSelfHeal(Player player) {
        if (player == null || playerStatsManager == null) {
            return PlayerStatsData.DEFAULT_VITALITY;
        }
        double vitality = playerStatsManager.get(player).getVitality();
        if (caveSpecialHelmetManager != null) {
            vitality += caveSpecialHelmetManager.getVitalityInDungeon(player);
        }
        return vitality;
    }

    public double getEffectiveOutgoingMending(Player player) {
        if (player == null || playerStatsManager == null) {
            return PlayerStatsData.DEFAULT_MENDING;
        }
        double mending = playerStatsManager.get(player).getMending();
        if (caveSpecialHelmetManager != null) {
            mending += caveSpecialHelmetManager.getMendingInDungeon(player);
        }
        if (adaptiveSetManager != null) {
            mending += adaptiveSetManager.getBonusMending(player);
        }
        return mending;
    }

    public int getAdditionalSpeedBonus(Player player) {
        int total = 0;
        total += secondWindHelmetManager != null ? secondWindHelmetManager.getBonusSpeed(player) : 0;
        total += cavernExplorerSetManager != null ? cavernExplorerSetManager.getBonusSpeed(player) : 0;
        total += bloodSetManager != null ? bloodSetManager.getBonusSpeed(player) : 0;
        return total;
    }

    @Override
    public void onEnable() {
        instance = this;

        UndergroundState undergroundState = UndergroundState.getInstance();
        if (undergroundState == null || undergroundState.getPlayerStatsManager() == null || undergroundState.getPlayerStatsRuntime() == null) {
            getLogger().severe("underground_state is required but player stats manager is unavailable.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        playerStatsManager = undergroundState.getPlayerStatsManager();
        playerStatsRuntime = undergroundState.getPlayerStatsRuntime();

        ensureDefaultWeaponFiles();
        initializeManagers();
        registerStatsExtension();
        registerEvents();
        registerCommands();
        professions = new maple.underground_item.professions.ProfessionModule(this, undergroundState);
        getLogger().info("underground_item enabled.");
    }

    @Override
    public void onDisable() {
        if (professions != null) { professions.close(); professions = null; }
        if (gearStatsApplyTask != null) {
            gearStatsApplyTask.cancel();
            gearStatsApplyTask = null;
        }
        if (sublimationWorkbenchManager != null) {
            HandlerList.unregisterAll(sublimationWorkbenchManager);
            sublimationWorkbenchManager = null;
        }
        if (sublimationAnvilPrepareListener != null) {
            HandlerList.unregisterAll(sublimationAnvilPrepareListener);
            sublimationAnvilPrepareListener = null;
        }
        if (playerStatsRuntime != null) {
            playerStatsRuntime.unregisterExtension(this, "item-equipment");
            playerStatsRuntime = null;
        }
        dungeonBridge = null;
        customEnchantService = null;
        instance = null;
    }

    private void ensureDefaultWeaponFiles() {
        for (String file : DEFAULT_WEAPON_FILES) {
            saveResourceIfMissing("weapons/" + file);
        }
    }

    private void saveResourceIfMissing(String path) {
        var target = new java.io.File(getDataFolder(), path);
        if (!target.exists()) {
            saveResource(path, false);
        }
    }

    private void initializeManagers() {
        gearManager = new GearManager(this);
        immortalSkullManager = new ImmortalSkullManager(this);
        sieveSetManager = new SieveSetManager(this);
        archerSetManager = new ArcherSetManager(this);
        skeletonSoldierSetManager = new SkeletonSoldierSetManager(this);
        skeletonMasterSetManager = new SkeletonMasterSetManager(this);
        undergroundCaveHelmetManager = new UndergroundCaveHelmetManager(this);
        statsDoubleHelmetManager = new StatsDoubleHelmetManager(this);
        caveSpecialHelmetManager = new CaveSpecialHelmetManager(this);
        secondWindHelmetManager = new SecondWindHelmetManager(this);
        arrowDistanceHelmetManager = new ArrowDistanceHelmetManager(this);
        cavernExplorerSetManager = new CavernExplorerSetManager(this);
        adaptiveSetManager = new AdaptiveSetManager(this);
        bloodSetManager = new BloodSetManager(this);
        sublimationGearManager = new SublimationGearManager(this);
        qualityUpgradeItemManager = new QualityUpgradeItemManager(this);

        weaponManager = new WeaponManager(this);
        weaponManager.reload();
        freezeSkillRunner = new FreezeSkillRunner(this, playerStatsManager, immortalSkullManager);
        showtimeSkillRunner = new ShowtimeSkillRunner(this, playerStatsManager, immortalSkullManager);

        gearStatsApplyTask = new GearStatsApplyTask(this, gearManager, playerStatsManager);
        gearStatsApplyTask.runTaskTimer(this, 40L, 20L);

        rebuildSublimationWorkbench();
    }

    private void registerStatsExtension() {
        if (playerStatsRuntime != null && !statsExtensionSuppressed) {
            playerStatsRuntime.registerExtension(this, "item-equipment", new ItemPlayerStatsExtension(this));
        }
    }

    private void registerEvents() {
        Bukkit.getPluginManager().registerEvents(new DefeatTankDropListener(this, gearManager), this);
        Bukkit.getPluginManager().registerEvents(new ImmortalSkullListener(this, immortalSkullManager), this);
        Bukkit.getPluginManager().registerEvents(new SieveSetDropListener(this, sieveSetManager), this);
        Bukkit.getPluginManager().registerEvents(new ArrowKnockbackResistanceListener(this, sieveSetManager), this);
        Bukkit.getPluginManager().registerEvents(new ArcherSetDropListener(this, archerSetManager), this);
        Bukkit.getPluginManager().registerEvents(new SkeletonSoldierSetDropListener(this, skeletonSoldierSetManager), this);
        Bukkit.getPluginManager().registerEvents(new SkeletonMasterSetDropListener(this, skeletonMasterSetManager), this);
        Bukkit.getPluginManager().registerEvents(new UnifiedSkeletonGearDropListener(this, archerSetManager, skeletonSoldierSetManager, skeletonMasterSetManager), this);
        Bukkit.getPluginManager().registerEvents(new BowNoConsumeArrowListener(skeletonMasterSetManager, this), this);
        Bukkit.getPluginManager().registerEvents(new ArrowDamageBonusListener(archerSetManager, skeletonSoldierSetManager, skeletonMasterSetManager), this);
        Bukkit.getPluginManager().registerEvents(new SecondWindListener(secondWindHelmetManager), this);
        Bukkit.getPluginManager().registerEvents(new ArrowDistanceListener(arrowDistanceHelmetManager), this);
        Bukkit.getPluginManager().registerEvents(new CavernExplorerDamageListener(cavernExplorerSetManager), this);

        Bukkit.getPluginManager().registerEvents(new WeaponListener(this, weaponManager, freezeSkillRunner, showtimeSkillRunner), this);
        Bukkit.getPluginManager().registerEvents(new WeaponSpecialEffectsListener(weaponManager, this), this);
        Bukkit.getPluginManager().registerEvents(new ShowtimeFireworkListener(showtimeSkillRunner), this);
    }

    private void registerCommands() {
        var weaponCmd = getCommand("weapon");
        if (weaponCmd != null) {
            var executor = new WeaponReloadCommand(weaponManager);
            weaponCmd.setExecutor(executor);
            weaponCmd.setTabCompleter(new WeaponTabCompleter(weaponManager));
        }

        var gearExecutor = new GearGiveCommand(
                gearManager,
                immortalSkullManager,
                weaponManager,
                sieveSetManager,
                archerSetManager,
                skeletonSoldierSetManager,
                skeletonMasterSetManager,
                undergroundCaveHelmetManager,
                statsDoubleHelmetManager,
                caveSpecialHelmetManager,
                secondWindHelmetManager,
                arrowDistanceHelmetManager,
                cavernExplorerSetManager,
                adaptiveSetManager,
                bloodSetManager,
                qualityUpgradeItemManager
        );
        var gearTab = new GearTabCompleter(weaponManager);

        var gearCmd = getCommand("gear");
        if (gearCmd != null) {
            gearCmd.setExecutor(gearExecutor);
            gearCmd.setTabCompleter(gearTab);
        }

        var immortalCmd = getCommand("immortalskull");
        if (immortalCmd != null) {
            immortalCmd.setExecutor(gearExecutor);
            immortalCmd.setTabCompleter(gearTab);
        }

        var itemInfoCmd = getCommand("iteminfo");
        if (itemInfoCmd != null) {
            itemInfoCmd.setExecutor(new ItemHandInfoCommand(this));
        }

        var itemMenuCmd = getCommand("itemmenu");
        if (itemMenuCmd != null) {
            var itemCatalogCommand = new ItemCatalogCommand(this);
            itemMenuCmd.setExecutor(itemCatalogCommand);
            Bukkit.getPluginManager().registerEvents(itemCatalogCommand, this);
        }

        var workbenchCmd = getCommand("sublimation_workbench");
        if (workbenchCmd != null) {
            workbenchCmd.setExecutor((sender, command, label, args) -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
                    return true;
                }
                if (!sender.hasPermission("test_dungeons.admin")) {
                    sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                    return true;
                }
                if (sublimationWorkbenchManager == null) {
                    sender.sendMessage(Component.text("Sublimation workbench is not ready yet.", NamedTextColor.RED));
                    return true;
                }
                sublimationWorkbenchManager.open(player);
                return true;
            });
        }
    }

    private void rebuildSublimationWorkbench() {
        if (sublimationWorkbenchManager != null) {
            HandlerList.unregisterAll(sublimationWorkbenchManager);
        }
        if (sublimationAnvilPrepareListener != null) {
            HandlerList.unregisterAll(sublimationAnvilPrepareListener);
        }
        sublimationWorkbenchManager = new SublimationWorkbenchManager(sublimationGearManager, customEnchantService);
        sublimationAnvilPrepareListener = new SublimationAnvilPrepareListener(sublimationGearManager, sublimationWorkbenchManager);
        if (isEnabled()) {
            Bukkit.getPluginManager().registerEvents(sublimationWorkbenchManager, this);
            Bukkit.getPluginManager().registerEvents(sublimationAnvilPrepareListener, this);
        }
    }
}
