package maple.underground_caves.dungeon.classskill;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DungeonClassSkillSharedState {
    public final Map<UUID, Long> warriorShoutDamageBuffUntil = new ConcurrentHashMap<>();
    public final Map<UUID, Long> warriorShoutReduceBuffUntil = new ConcurrentHashMap<>();
}

