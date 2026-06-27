package maple.underground_quest.model;

import maple.underground_quest.UndergroundQuest;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Function;

/**
 * 一個完整的任務定義。
 */
public final class Quest {

    private final String id;
    private final String displayName;
    private final List<String> description;
    private final List<QuestObjective> objectives;
    private final List<QuestReward> rewards;
    private final List<String> prerequisiteQuestIds;

    public Quest(String id, String displayName,
                 List<String> description, List<QuestObjective> objectives,
                 List<QuestReward> rewards, List<String> prerequisiteQuestIds) {
        this.id = id;
        this.displayName = displayName;
        this.description = description == null ? List.of() : List.copyOf(description);
        this.objectives = objectives == null ? List.of() : List.copyOf(objectives);
        this.rewards = rewards == null ? List.of() : List.copyOf(rewards);
        this.prerequisiteQuestIds = prerequisiteQuestIds == null ? List.of() : List.copyOf(prerequisiteQuestIds);
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public List<String> getDescription() { return description; }
    public List<QuestObjective> getObjectives() { return objectives; }
    public List<QuestReward> getRewards() { return rewards; }
    public List<String> getPrerequisiteQuestIds() { return prerequisiteQuestIds; }

    /** 是否所有目標都達成。 */
    public boolean isCompleted(java.util.Map<String, Integer> progress) {
        for (QuestObjective obj : objectives) {
            int current = progress == null ? 0 : progress.getOrDefault(obj.getId(), 0);
            if (current < obj.getRequiredAmount()) {
                return false;
            }
        }
        return true;
    }

    public boolean arePrerequisitesMet(Function<String, Boolean> isCompleted) {
        for (String reqId : prerequisiteQuestIds) {
            if (!Boolean.TRUE.equals(isCompleted.apply(reqId))) {
                return false;
            }
        }
        return true;
    }

    /** 發放所有獎勵給玩家。 */
    public void grantRewards(UndergroundQuest plugin, Player player) {
        for (QuestReward reward : rewards) {
            reward.grant(plugin, player);
        }
    }
}