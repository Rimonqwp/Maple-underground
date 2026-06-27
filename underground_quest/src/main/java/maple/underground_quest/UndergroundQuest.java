package maple.underground_quest;

import maple.underground_caves.Test_dungeons;
import maple.underground_state.UndergroundState;
import maple.underground_quest.command.QuestAdminCommand;
import maple.underground_quest.command.QuestCommand;
import maple.underground_quest.data.QuestPlayerRepository;
import maple.underground_quest.data.QuestRegistry;
import maple.underground_quest.listener.QuestProgressListener;
import maple.underground_quest.manager.PlayerQuestManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * 任務系統子插件主類別。
 *
 * <p>依賴 underground_state（主插件，擁有資料庫憑證）與 underground_caves（副本／隊伍系統）。
 * 任務進度、獎勵領取紀錄一律走 underground_state 的 MySqlConnectionFactory，不自行持有憑證。
 */
public final class UndergroundQuest extends JavaPlugin {

    private static UndergroundQuest instance;

    private UndergroundState state;
    private Test_dungeons caves;
    private QuestRegistry questRegistry;
    private QuestPlayerRepository playerRepository;
    private PlayerQuestManager playerQuestManager;

    public static UndergroundQuest getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        // 前置檢查：必須先有 underground_state（DB 憑證）與 underground_caves（副本前置）
        if (!(Bukkit.getPluginManager().getPlugin("underground_state") instanceof UndergroundState foundState)) {
            getLogger().severe("找不到 underground_state，任務系統無法啟動。");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        if (!(Bukkit.getPluginManager().getPlugin("underground_caves") instanceof Test_dungeons foundCaves)) {
            getLogger().severe("找不到 underground_caves，任務系統無法啟動。");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        this.state = foundState;
        this.caves = foundCaves;

        // 載入任務定義（resources/quests.yml）
        saveDefaultConfig();
        this.questRegistry = new QuestRegistry(this);
        this.questRegistry.load();

        // 資料層：走 underground_state 的 MySqlConnectionFactory（caves 資料源）
        this.playerRepository = new QuestPlayerRepository(getLogger());
        this.playerQuestManager = new PlayerQuestManager(this, playerRepository);

        // 玩家上線時從 DB 載入進度
        Bukkit.getPluginManager().registerEvents(new QuestProgressListener(this, playerQuestManager), this);

        // 指令
        QuestCommand questCommand = new QuestCommand(this, playerQuestManager, questRegistry);
        PluginCommand quest = Objects.requireNonNull(getCommand("quest"), "quest command missing");
        quest.setExecutor(questCommand);
        quest.setTabCompleter(questCommand);

        QuestAdminCommand adminCommand = new QuestAdminCommand(this, playerQuestManager, questRegistry);
        PluginCommand questAdmin = Objects.requireNonNull(getCommand("questadmin"), "questadmin command missing");
        questAdmin.setExecutor(adminCommand);
        questAdmin.setTabCompleter(adminCommand);

        getLogger().info("任務系統已啟動，使用 /quest 開啟任務面板。");
    }

    @Override
    public void onDisable() {
        if (playerQuestManager != null) {
            playerQuestManager.saveAllOnline();
        }
        playerQuestManager = null;
        playerRepository = null;
        questRegistry = null;
        caves = null;
        state = null;
        instance = null;
    }

    public UndergroundState getState() {
        return state;
    }

    public Test_dungeons getCaves() {
        return caves;
    }

    public QuestRegistry getQuestRegistry() {
        return questRegistry;
    }

    public QuestPlayerRepository getPlayerRepository() {
        return playerRepository;
    }

    public PlayerQuestManager getPlayerQuestManager() {
        return playerQuestManager;
    }
}