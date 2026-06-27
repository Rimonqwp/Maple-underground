package maple.underground_quest.listener;

import maple.underground_quest.UndergroundQuest;
import maple.underground_quest.manager.PlayerQuestManager;
import maple.underground_quest.model.QuestType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * 監聽遊戲事件並推進任務進度。
 *
 * <p>這是一個示範性的監聽器：實際專案中可依任務目標類型拆成多個監聽器，
 * 或改用系列的事件總線。目前涵蓋最基礎的擊殺、破壞方塊，以及玩家上下線。
 */
public final class QuestProgressListener implements Listener {

    private final UndergroundQuest plugin;
    private final PlayerQuestManager playerQuestManager;

    public QuestProgressListener(UndergroundQuest plugin, PlayerQuestManager playerQuestManager) {
        this.plugin = plugin;
        this.playerQuestManager = playerQuestManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // 玩家上線：異步從 DB 載入任務進度，完成後回主執行緒套用。
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                playerQuestManager.loadPlayer(uuid));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // 玩家下線：保存當前進度。
        playerQuestManager.savePlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        // 擊殺目標：以怪物類型名稱做為 target 比對。
        playerQuestManager.incrementProgress(
                killer.getUniqueId(), QuestType.KILL, 1, event.getEntityType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String target = event.getBlock().getType().name();
        playerQuestManager.incrementProgress(
                player.getUniqueId(), QuestType.COLLECT, 1, target);
    }
}