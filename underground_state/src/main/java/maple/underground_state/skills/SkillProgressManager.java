package maple.underground_state.skills;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SkillProgressManager {

    private final SkillProgressDB db;
    private final Map<UUID, SkillProgressData> cache = new ConcurrentHashMap<>();

    public SkillProgressManager(@NotNull SkillProgressDB db) {
        this.db = db;
    }

    public @NotNull SkillProgressData getOrLoad(UUID uuid) {
        if (uuid == null) {
            return new SkillProgressData();
        }
        return cache.computeIfAbsent(uuid, db::load);
    }

    public @NotNull SkillProgressData get(Player player) {
        return player == null ? new SkillProgressData() : getOrLoad(player.getUniqueId());
    }

    public @NotNull SkillProgressData get(UUID uuid) {
        return uuid == null ? new SkillProgressData() : getOrLoad(uuid);
    }

    public long getXp(UUID uuid, @NotNull SkillType skillType) {
        return get(uuid).getXp(skillType);
    }

    public void setXp(UUID uuid, @NotNull SkillType skillType, long xp) {
        if (uuid == null) {
            return;
        }
        SkillProgressData data = getOrLoad(uuid).copy();
        data.setXp(skillType, xp);
        cache.put(uuid, data);
    }

    public long addXp(UUID uuid, @NotNull SkillType skillType, long delta) {
        if (uuid == null) {
            return 0L;
        }
        SkillProgressData data = getOrLoad(uuid).copy();
        long updated = data.addXp(skillType, delta);
        cache.put(uuid, data);
        return updated;
    }

    public long getMapleXp(UUID uuid) {
        return get(uuid).getMapleXp();
    }

    public void setMapleXp(UUID uuid, long mapleXp) {
        if (uuid == null) {
            return;
        }
        SkillProgressData data = getOrLoad(uuid).copy();
        data.setMapleXp(mapleXp);
        cache.put(uuid, data);
    }

    public long addMapleXp(UUID uuid, long delta) {
        if (uuid == null) {
            return 0L;
        }
        SkillProgressData data = getOrLoad(uuid).copy();
        long updated = data.addMapleXp(delta);
        cache.put(uuid, data);
        return updated;
    }

    public int getCombatXpRemainderTenths(UUID uuid) {
        return get(uuid).getCombatXpRemainderTenths();
    }

    public void setCombatXpRemainderTenths(UUID uuid, int remainderTenths) {
        if (uuid == null) {
            return;
        }
        SkillProgressData data = getOrLoad(uuid).copy();
        data.setCombatXpRemainderTenths(remainderTenths);
        cache.put(uuid, data);
    }

    public int getMiningXpRemainderTenths(UUID uuid) {
        return get(uuid).getMiningXpRemainderTenths();
    }

    public void setMiningXpRemainderTenths(UUID uuid, int remainderTenths) {
        if (uuid == null) {
            return;
        }
        SkillProgressData data = getOrLoad(uuid).copy();
        data.setMiningXpRemainderTenths(remainderTenths);
        cache.put(uuid, data);
    }

    public int getForagingXpRemainderTenths(UUID uuid) {
        return get(uuid).getForagingXpRemainderTenths();
    }

    public void setForagingXpRemainderTenths(UUID uuid, int remainderTenths) {
        if (uuid == null) {
            return;
        }
        SkillProgressData data = getOrLoad(uuid).copy();
        data.setForagingXpRemainderTenths(remainderTenths);
        cache.put(uuid, data);
    }

    public int getFarmingXpRemainderTenths(UUID uuid) {
        return get(uuid).getFarmingXpRemainderTenths();
    }

    public void setFarmingXpRemainderTenths(UUID uuid, int remainderTenths) {
        if (uuid == null) {
            return;
        }
        SkillProgressData data = getOrLoad(uuid).copy();
        data.setFarmingXpRemainderTenths(remainderTenths);
        cache.put(uuid, data);
    }

    public int getFishingXpRemainderTenths(UUID uuid) {
        return get(uuid).getFishingXpRemainderTenths();
    }

    public void setFishingXpRemainderTenths(UUID uuid, int remainderTenths) {
        if (uuid == null) {
            return;
        }
        SkillProgressData data = getOrLoad(uuid).copy();
        data.setFishingXpRemainderTenths(remainderTenths);
        cache.put(uuid, data);
    }

    public int getAlchemyXpRemainderTenths(UUID uuid) {
        return get(uuid).getAlchemyXpRemainderTenths();
    }

    public void setAlchemyXpRemainderTenths(UUID uuid, int remainderTenths) {
        if (uuid == null) {
            return;
        }
        SkillProgressData data = getOrLoad(uuid).copy();
        data.setAlchemyXpRemainderTenths(remainderTenths);
        cache.put(uuid, data);
    }

    public int getCraftingXpRemainderTenths(UUID uuid) {
        return get(uuid).getCraftingXpRemainderTenths();
    }

    public void setCraftingXpRemainderTenths(UUID uuid, int remainderTenths) {
        if (uuid == null) {
            return;
        }
        SkillProgressData data = getOrLoad(uuid).copy();
        data.setCraftingXpRemainderTenths(remainderTenths);
        cache.put(uuid, data);
    }

    public int getExplorationDistanceRemainderMillis(UUID uuid) {
        return get(uuid).getExplorationDistanceRemainderMillis();
    }

    public void setExplorationDistanceRemainderMillis(UUID uuid, int remainderMillis) {
        if (uuid == null) {
            return;
        }
        SkillProgressData data = getOrLoad(uuid).copy();
        data.setExplorationDistanceRemainderMillis(remainderMillis);
        cache.put(uuid, data);
    }

    public void onJoin(UUID uuid) {
        if (uuid == null) {
            return;
        }
        cache.put(uuid, db.load(uuid));
    }

    public void onQuit(UUID uuid) {
        if (uuid == null) {
            return;
        }
        SkillProgressData data = cache.remove(uuid);
        if (data != null) {
            db.save(uuid, data);
        }
    }

    public void save(UUID uuid) {
        if (uuid == null) {
            return;
        }
        SkillProgressData data = cache.get(uuid);
        if (data != null) {
            db.save(uuid, data);
        }
    }
}
