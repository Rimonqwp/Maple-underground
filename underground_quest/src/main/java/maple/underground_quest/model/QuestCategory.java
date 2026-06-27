package maple.underground_quest.model;

import java.util.Optional;

/**
 * 任务分类。DAILY 每日重置、WEEKLY 每周重置、MAIN 主线（永久）、SIDE 支线、ONCE 一次性。
 * 目前 basic 版仅作型别记录用，重置逻辑留待后续扩充。
 */
public enum QuestCategory {
    DAILY,
    WEEKLY,
    MAIN,
    SIDE,
    ONCE;

    public static Optional<QuestCategory> fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(QuestCategory.valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}