package maple.underground_quest.model;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 任务目标：例如「击杀 10 只僵尸」「通关 F1 副本 3 次」。
 * 一个任务可含多个目标，全部完成才算完成。
 */
public final class QuestObjective {

    private final String id;
    private final QuestType type;
    private final int requiredAmount;
    private final String target;
    private final String displayText;

    public QuestObjective(String id, QuestType type, int requiredAmount, String target, String displayText) {
        this.id = id;
        this.type = type;
        this.requiredAmount = Math.max(1, requiredAmount);
        this.target = target == null ? "" : target;
        this.displayText = displayText == null ? "" : displayText;
    }

    public static QuestObjective fromConfig(String id, ConfigurationSection sec) {
        if (sec == null) {
            throw new IllegalArgumentException("objective section is null");
        }
        QuestType type = QuestType.fromId(sec.getString("type", "KILL")).orElse(QuestType.CUSTOM);
        int amount = sec.getInt("amount", 1);
        String target = sec.getString("target", "");
        String display = sec.getString("display", target);
        return new QuestObjective(id, type, amount, target, display);
    }

    public String getId() { return id; }
    public QuestType getType() { return type; }
    public int getRequiredAmount() { return requiredAmount; }
    public String getTarget() { return target; }
    public String getDisplayText() { return displayText; }

    public boolean isCompleted(int currentAmount) {
        return currentAmount >= requiredAmount;
    }

    /**
     * 判断此目标是否匹配指定类型与目标。
     * target 为空表示不限目标；否则需大小写不敏感匹配。
     */
    public boolean matches(QuestType type, String target) {
        if (this.type != type) {
            return false;
        }
        if (this.target == null || this.target.isEmpty()) {
            return true;
        }
        if (target == null) {
            return false;
        }
        return this.target.equalsIgnoreCase(target);
    }
}