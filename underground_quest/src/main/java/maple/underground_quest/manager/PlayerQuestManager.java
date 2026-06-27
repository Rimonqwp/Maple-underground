package maple.underground_quest.manager;

import maple.underground_quest.UndergroundQuest;
import maple.underground_quest.data.QuestPlayerRepository;
import maple.underground_quest.model.Quest;
import maple.underground_quest.model.QuestObjective;
import maple.underground_quest.model.QuestType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 玩家任務狀態管理器（記憶體層）。
 *
 * <p>職責：接受任務、更新目標進度、判定完成、發放獎勵、追蹤任務。
 * 持久化全部委派給 {@link QuestPlayerRepository}，這裡只快取線上玩家的進度。</p>
 */
public final class PlayerQuestManager {

    private final UndergroundQuest plugin;
    private final QuestPlayerRepository repository;

    /** 線上玩家快取：uuid -> 該玩家所有進行中任務。 */
    private final Map<UUID, Map<String, ActiveQuest>> activeByPlayer = new ConcurrentHashMap<>();

    /** 線上玩家已完成的任務 id 集合。 */
    private final Map<UUID, java.util.Set<String>> completedByPlayer = new ConcurrentHashMap<>();

    /** 線上玩家已領獎的任務 id 集合。 */
    private final Map<UUID, java.util.Set<String>> claimedByPlayer = new ConcurrentHashMap<>();

    /** 線上玩家正在追蹤的任務 id（可為 null）。 */
    private final Map<UUID, String> trackedByPlayer = new ConcurrentHashMap<>();

    public PlayerQuestManager(UndergroundQuest plugin, QuestPlayerRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    /** 玩家上線時（異步）從 DB 載入進度，再回主執行緒套用。 */
    public void loadPlayer(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Map<String, Map<String, Integer>> progress = repository.loadProgress(uuid);
                java.util.Set<String> claimed = repository.loadClaimed(uuid);
                String tracked = repository.loadTracked(uuid);
                Bukkit.getScheduler().runTask(plugin, () -> applyLoaded(uuid, progress, claimed, tracked));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "載入玩家任務失敗: " + uuid, e);
            }
        });
    }

    /** 供 listener 回主執行緒套用載入結果。 */
    public void applyLoaded(UUID uuid, Map<String, Map<String, Integer>> progress,
                            java.util.Set<String> claimed, String tracked) {
        Map<String, ActiveQuest> active = new HashMap<>();
        for (Map.Entry<String, Map<String, Integer>> entry : progress.entrySet()) {
            String questId = entry.getKey();
            Quest quest = plugin.getQuestRegistry().getQuest(questId);
            if (quest == null) {
                continue; // 定義已被移除，跳過
            }
            boolean allDone = true;
            for (QuestObjective obj : quest.getObjectives()) {
                Integer amount = entry.getValue().get(obj.getId());
                if (amount == null || amount < obj.getRequiredAmount()) {
                    allDone = false;
                    break;
                }
            }
            if (!allDone) {
                active.put(questId, new ActiveQuest(questId, new HashMap<>(entry.getValue())));
            }
        }
        activeByPlayer.put(uuid, active);
        claimedByPlayer.put(uuid, new java.util.HashSet<>(claimed));
        // completed = 所有目標達標但尚未領獎
        java.util.Set<String> completed = new java.util.HashSet<>();
        for (Map.Entry<String, Map<String, Integer>> entry : progress.entrySet()) {
            String questId = entry.getKey();
            Quest quest = plugin.getQuestRegistry().getQuest(questId);
            if (quest == null) continue;
            boolean allDone = true;
            for (QuestObjective obj : quest.getObjectives()) {
                Integer amount = entry.getValue().get(obj.getId());
                if (amount == null || amount < obj.getRequiredAmount()) {
                    allDone = false;
                    break;
                }
            }
            if (allDone && !claimed.contains(questId)) {
                completed.add(questId);
            }
        }
        completedByPlayer.put(uuid, completed);
        trackedByPlayer.put(uuid, tracked);
    }

    /** 玩家下線時保存進度。 */
    public void savePlayer(UUID uuid) {
        Map<String, ActiveQuest> active = activeByPlayer.get(uuid);
        if (active == null) return;
        // 進度已在每次更新時即時寫 DB，這裡只需清理快取
    }

    /** 保存所有線上玩家（關服時呼叫）。 */
    public void saveAllOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            savePlayer(player.getUniqueId());
        }
    }

    /** 接受任務。回傳結果訊息供呼叫端顯示。 */
    public String acceptQuest(UUID uuid, String questId) {
        Quest quest = plugin.getQuestRegistry().getQuest(questId);
        if (quest == null) {
            return "找不到任務: " + questId;
        }
        Map<String, ActiveQuest> active = activeByPlayer.computeIfAbsent(uuid, k -> new HashMap<>());
        if (active.containsKey(questId)) {
            return "你已經接取了此任務。";
        }
        java.util.Set<String> claimed = claimedByPlayer.get(uuid);
        if (claimed != null && claimed.contains(questId)) {
            return "你已經完成並領取過此任務。";
        }
        // 初始化所有目標進度為 0
        Map<String, Integer> progress = new HashMap<>();
        for (QuestObjective obj : quest.getObjectives()) {
            progress.put(obj.getId(), 0);
            repository.saveObjective(uuid, questId, obj.getId(), 0);
        }
        active.put(questId, new ActiveQuest(questId, progress));
        return "已接取任務: " + quest.getDisplayName();
    }

    /** 放棄任務。 */
    public String abandon(UUID uuid, String questId) {
        Map<String, ActiveQuest> active = activeByPlayer.get(uuid);
        if (active == null || !active.containsKey(questId)) {
            return "你沒有進行中的此任務。";
        }
        active.remove(questId);
        repository.resetQuest(uuid, questId);
        return "已放棄任務: " + questId;
    }

    /**
     * 推進符合指定類型與目標的任務進度。由監聽器在對應事件觸發時呼叫。
     *
     * @param uuid   玩家
     * @param type   目標類型
     * @param amount 本次增量
     * @param target 額外比對條件（例如怪物 ID、方塊 ID），{@code null} 表示不限
     */
    public void incrementProgress(UUID uuid, QuestType type, int amount, String target) {
        Map<String, ActiveQuest> active = activeByPlayer.get(uuid);
        if (active == null || active.isEmpty()) {
            return;
        }
        for (ActiveQuest record : new ArrayList<>(active.values())) {
            Quest quest = plugin.getQuestRegistry().getQuest(record.questId);
            if (quest == null) continue;
            for (QuestObjective obj : quest.getObjectives()) {
                if (!obj.matches(type, target)) {
                    continue;
                }
                int before = record.progress.getOrDefault(obj.getId(), 0);
                if (before >= obj.getRequiredAmount()) {
                    continue; // 這個目標已完成
                }
                int after = Math.min(obj.getRequiredAmount(), before + amount);
                record.progress.put(obj.getId(), after);
                repository.saveObjective(uuid, quest.getId(), obj.getId(), after);

                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.sendMessage("\u00a7a任務進度 \u00a77" + quest.getDisplayName()
                            + " \u00a77(" + obj.getTarget() + "): " + after + "/" + obj.getRequiredAmount());
                }
                if (isQuestComplete(uuid, quest)) {
                    onQuestComplete(uuid, quest);
                }
            }
        }
    }

    private boolean isQuestComplete(UUID uuid, Quest quest) {
        ActiveQuest record = activeByPlayer.getOrDefault(uuid, Map.of()).get(quest.getId());
        if (record == null) return false;
        for (QuestObjective obj : quest.getObjectives()) {
            if (record.progress.getOrDefault(obj.getId(), 0) < obj.getRequiredAmount()) {
                return false;
            }
        }
        return true;
    }

    private void onQuestComplete(UUID uuid, Quest quest) {
        // 從進行中移除，標記為已完成（等待領獎）
        Map<String, ActiveQuest> active = activeByPlayer.get(uuid);
        if (active != null) {
            active.remove(quest.getId());
        }
        completedByPlayer.computeIfAbsent(uuid, k -> new java.util.HashSet<>()).add(quest.getId());
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.sendMessage("\u00a76任務完成: \u00a7f" + quest.getDisplayName()
                    + " \u00a77(使用 /quest claim 領取獎勵)");
        }
    }

    /** 領取可領取的獎勵（若任務採用手動領獎流程）。回傳結果訊息。 */
    public String claimReward(UUID uuid, String questId) {
        Quest quest = plugin.getQuestRegistry().getQuest(questId);
        if (quest == null) {
            return "找不到任務: " + questId;
        }
        java.util.Set<String> completed = completedByPlayer.get(uuid);
        if (completed == null || !completed.contains(questId)) {
            return "此任務尚未完成。";
        }
        java.util.Set<String> claimed = claimedByPlayer.computeIfAbsent(uuid, k -> new java.util.HashSet<>());
        if (claimed.contains(questId)) {
            return "你已經領取過此任務獎勵。";
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return "玩家不在線上。";
        }
        quest.grantRewards(plugin, player);
        repository.markClaimed(uuid, questId);
        claimed.add(questId);
        completed.remove(questId);
        return "已領取獎勵: " + quest.getDisplayName();
    }

    public String getTracked(UUID uuid) {
        return trackedByPlayer.get(uuid);
    }

    public void setTracked(UUID uuid, String questId) {
        trackedByPlayer.put(uuid, questId);
        repository.saveTracked(uuid, questId);
    }

    public void resetQuest(UUID uuid, String questId) {
        Map<String, ActiveQuest> active = activeByPlayer.get(uuid);
        if (active != null) active.remove(questId);
        java.util.Set<String> completed = completedByPlayer.get(uuid);
        if (completed != null) completed.remove(questId);
        java.util.Set<String> claimed = claimedByPlayer.get(uuid);
        if (claimed != null) claimed.remove(questId);
        repository.resetQuest(uuid, questId);
    }

    public void resetAll(UUID uuid) {
        activeByPlayer.remove(uuid);
        completedByPlayer.remove(uuid);
        claimedByPlayer.remove(uuid);
        trackedByPlayer.remove(uuid);
        repository.resetAll(uuid);
    }

    /** 取得玩家進行中的任務（id -> 進度 map）。 */
    public Map<String, Map<String, Integer>> getActive(UUID uuid) {
        Map<String, ActiveQuest> active = activeByPlayer.get(uuid);
        if (active == null) return Collections.emptyMap();
        Map<String, Map<String, Integer>> result = new HashMap<>();
        for (Map.Entry<String, ActiveQuest> e : active.entrySet()) {
            result.put(e.getKey(), Collections.unmodifiableMap(e.getValue().progress));
        }
        return result;
    }

    public boolean hasActiveQuest(UUID uuid, String questId) {
        Map<String, ActiveQuest> active = activeByPlayer.get(uuid);
        return active != null && active.containsKey(questId);
    }

    public boolean isCompleted(UUID uuid, String questId) {
        java.util.Set<String> completed = completedByPlayer.get(uuid);
        return completed != null && completed.contains(questId);
    }

    /** 記憶體中的玩家任務進度快取。 */
    private static final class ActiveQuest {
        final String questId;
        final Map<String, Integer> progress; // objectiveId -> amount

        ActiveQuest(String questId, Map<String, Integer> progress) {
            this.questId = questId;
            this.progress = progress;
        }
    }
}