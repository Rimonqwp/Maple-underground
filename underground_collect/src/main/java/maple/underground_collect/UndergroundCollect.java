package maple.underground_collect;

import maple.underground_state.UndergroundState;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class UndergroundCollect extends JavaPlugin {

    private UndergroundState state;
    private CollectionDataRepository dataRepository;
    private CollectionRewardService rewardService;
    private CollectionMenus collectionMenus;

    @Override
    public void onEnable() {
        if (!(Bukkit.getPluginManager().getPlugin("underground_state") instanceof UndergroundState foundState)) {
            getLogger().severe("找不到 underground_state，收集中心无法启动。");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.state = foundState;
        this.dataRepository = new CollectionDataRepository(getLogger());
        this.rewardService = new CollectionRewardService(state, dataRepository);
        this.collectionMenus = new CollectionMenus(state, dataRepository, rewardService);

        CollectCommand command = new CollectCommand(collectionMenus, rewardService);
        PluginCommand collectCommand = Objects.requireNonNull(getCommand("collect"), "collect command missing");
        collectCommand.setExecutor(command);
        collectCommand.setTabCompleter(command);

        Bukkit.getPluginManager().registerEvents(new CollectionMenuListener(collectionMenus), this);

        getLogger().info("收集中心已启动，玩家可使用 /collect 进入。");
    }

    @Override
    public void onDisable() {
        collectionMenus = null;
        rewardService = null;
        dataRepository = null;
        state = null;
    }
}
