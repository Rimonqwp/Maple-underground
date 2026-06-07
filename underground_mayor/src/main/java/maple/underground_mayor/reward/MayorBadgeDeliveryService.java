package maple.underground_mayor.reward;

import maple.underground_mayor.mayor.MayorCollectionManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class MayorBadgeDeliveryService implements Listener {
    private final maple.underground_mayor.UndergroundMayor plugin;
    private final MayorCollectionManager collectionManager;
    private final MayorBadgeManager badgeManager;

    public MayorBadgeDeliveryService(
            maple.underground_mayor.UndergroundMayor plugin,
            MayorCollectionManager collectionManager,
            MayorBadgeManager badgeManager
    ) {
        this.plugin = plugin;
        this.collectionManager = collectionManager;
        this.badgeManager = badgeManager;
    }

    public void deliverPendingBadgesToOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            deliverPendingBadges(player, true);
        }
    }

    public boolean deliverPendingBadges(Player player, boolean notifyWhenEmpty) {
        if (player == null || !collectionManager.isRewardsFinalized()) {
            return false;
        }

        MayorCollectionManager.PlayerRewardStatus before = collectionManager.getPlayerRewardStatus(player.getUniqueId());
        int pending = before.pendingBadges();
        if (pending <= 0) {
            if (notifyWhenEmpty) {
                player.sendMessage("莉迪说: 你的徽章已经结算完成，目前没有待领取的建设徽章。");
            }
            return false;
        }

        int claimed = collectionManager.claimPendingBadges(player.getUniqueId());
        MayorCollectionManager.PlayerRewardStatus after = collectionManager.getPlayerRewardStatus(player.getUniqueId());
        badgeManager.giveBadges(
                player,
                claimed,
                new MayorBadgeManager.RewardContext(
                        player.getName(),
                        after.playerContribution(),
                        after.awardedBadges(),
                        after.entitledBadges(),
                        after.pendingBadges(),
                        0,
                        MayorCollectionManager.TOTAL_BADGES,
                        collectionManager.getTotalRequiredAmount()
                )
        );
        player.sendMessage("系統提示：你有市長徽章待領取，已自動發放到你的背包。");
        player.sendMessage("莉迪說：你已領取 " + claimed + " 枚建設徽章。");
        return true;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> deliverPendingBadges(player, false));
    }
}
