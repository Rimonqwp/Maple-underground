package maple.underground_collect;

import maple.underground_state.UndergroundState;
import maple.underground_state.gui.CollectionInfoGui;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

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
        this.dataRepository = new CollectionDataRepository(this, getLogger());
        this.rewardService = new CollectionRewardService(this, state, dataRepository);
        this.collectionMenus = new CollectionMenus(state, dataRepository, rewardService);

        for (Player player : Bukkit.getOnlinePlayers()) {
            dataRepository.preloadClaimsAsync(player.getUniqueId(), null);
        }

        CollectionInfoGui.registerBridge(new CollectionInfoGui.CollectionCenterBridge() {
            @Override
            public void openHub(@NotNull Player player) {
                collectionMenus.openHub(player);
            }

            @Override
            public int getClaimedTier(@NotNull UUID uuid, @NotNull String collectionKey) {
                return dataRepository.getClaimedTier(uuid, collectionKey);
            }

            @Override
            public boolean claimItemTier(@NotNull Player player,
                                         @NotNull CollectionInfoGui.Category category,
                                         @NotNull CollectionInfoGui.CollectionItem item,
                                         int targetTier) {
                CollectionRewardService.ClaimSummary summary = rewardService.claimItem(player, category, item, targetTier);
                rewardService.sendClaimFeedback(player, summary);
                return summary.hasAnything();
            }

            @Override
            public boolean openTierRecipe(@NotNull Player player,
                                          @NotNull CollectionInfoGui.Category category,
                                          int itemIndex,
                                          int tier) {
                collectionMenus.openTierRecipe(player, category, itemIndex, tier);
                return true;
            }
        });

        CollectCommand command = new CollectCommand(this, state, dataRepository, collectionMenus, rewardService);
        PluginCommand collectCommand = Objects.requireNonNull(getCommand("collect"), "collect command missing");
        collectCommand.setExecutor(command);
        collectCommand.setTabCompleter(command);

        Bukkit.getPluginManager().registerEvents(new CollectionMenuListener(collectionMenus, dataRepository, rewardService), this);

        getLogger().info("收集中心 v" + getDescription().getVersion() + " 已启动，玩家可使用 /collect 进入。");
    }

    @Override
    public void onDisable() {
        CollectionInfoGui.registerBridge(null);
        if (dataRepository != null) {
            dataRepository.shutdown();
        }
        collectionMenus = null;
        rewardService = null;
        dataRepository = null;
        state = null;
    }
}
