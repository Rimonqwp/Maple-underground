package maple.underground_state.collection;

import maple.underground_state.gui.CollectionInfoGui;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CollectionProgressManager {

    private final CollectionProgressDB db;
    private final Map<UUID, Map<String, Long>> cache = new ConcurrentHashMap<>();

    public CollectionProgressManager(@NotNull CollectionProgressDB db) {
        this.db = db;
    }

    public long getAmount(@NotNull UUID uuid, @NotNull String collectionKey) {
        return getOrLoad(uuid).getOrDefault(collectionKey, 0L);
    }

    public void addMaterial(@NotNull UUID uuid, @NotNull Material material, long amount) {
        if (amount <= 0L) {
            return;
        }
        Material collectionMaterial = normalizeCollectionMaterial(material);
        for (CollectionInfoGui.Category category : CollectionInfoGui.Category.values()) {
            if (!CollectionInfoGui.hasCollectionMaterial(category, collectionMaterial)) {
                continue;
            }
            add(uuid, CollectionInfoGui.collectionKey(category, collectionMaterial), amount);
        }
    }

    public void onJoin(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, Long> data = db.load(uuid);
        cache.put(uuid, new ConcurrentHashMap<>(data));
    }

    public void onQuit(@NotNull UUID uuid) {
        Map<String, Long> data = cache.remove(uuid);
        if (data != null) {
            db.save(uuid, data);
        }
    }

    public void save(@NotNull UUID uuid) {
        Map<String, Long> data = cache.get(uuid);
        if (data != null) {
            db.save(uuid, data);
        }
    }

    private @NotNull Map<String, Long> getOrLoad(@NotNull UUID uuid) {
        return cache.computeIfAbsent(uuid, id -> new ConcurrentHashMap<>(db.load(id)));
    }

    private void add(@NotNull UUID uuid, @NotNull String collectionKey, long amount) {
        Map<String, Long> data = getOrLoad(uuid);
        data.merge(collectionKey, amount, Long::sum);
    }

    private static @NotNull Material normalizeCollectionMaterial(@NotNull Material material) {
        return switch (material) {
            case RED_SAND -> Material.SAND;
            default -> material;
        };
    }

    public @NotNull Map<String, Long> snapshot(@NotNull UUID uuid) {
        return new HashMap<>(getOrLoad(uuid));
    }
}
