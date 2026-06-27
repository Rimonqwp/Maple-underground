package maple.underground_quest.model;

import java.util.Locale;
import java.util.Optional;

/**
 * 任务目标类型。决定监听器如何匹配与推进进度。
 */
public enum QuestType {
    KILL,
    BREAK,
    COLLECT,
    FISH,
    TALK,
    DUNGEON_CLEAR,
    CUSTOM;

    public static QuestType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return CUSTOM;
        }
        try {
            return QuestType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return CUSTOM;
        }
    }

    public static Optional<QuestType> fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(QuestType.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}