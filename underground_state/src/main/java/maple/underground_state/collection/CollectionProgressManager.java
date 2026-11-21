package maple.underground_state.collection;

import maple.underground_state.gui.CollectionInfoGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CollectionProgressManager {

    private final CollectionProgressDB db;
    private final Map<UUID, Map<String, Long>> cache = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

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
            add(uuid, category, collectionMaterial, CollectionInfoGui.collectionKey(category, collectionMaterial), amount, true);
        }
    }

    public long addMaterialInCategory(@NotNull UUID uuid,
                                      @NotNull CollectionInfoGui.Category category,
                                      @NotNull Material material,
                                      long delta,
                                      boolean notify) {
        Material normalized = normalizeCollectionMaterial(material);
        String key = CollectionInfoGui.collectionKey(category, normalized);
        if (delta == 0L) {
            return getAmount(uuid, key);
        }
        if (delta > 0L) {
            return add(uuid, category, normalized, key, delta, notify);
        }
        Map<String, Long> data = getOrLoad(uuid);
        long oldAmount = data.getOrDefault(key, 0L);
        long nextAmount = Math.max(0L, oldAmount + delta);
        if (nextAmount <= 0L) {
            data.remove(key);
            db.deleteKey(uuid, key);
        } else {
            data.put(key, nextAmount);
            dirty.add(uuid);
        }
        return nextAmount;
    }

    public void setAmount(@NotNull UUID uuid,
                          @NotNull CollectionInfoGui.Category category,
                          @NotNull Material material,
                          long amount,
                          boolean notify) {
        Material normalized = normalizeCollectionMaterial(material);
        String key = CollectionInfoGui.collectionKey(category, normalized);
        long safeAmount = Math.max(0L, amount);
        Map<String, Long> data = getOrLoad(uuid);
        long oldAmount = data.getOrDefault(key, 0L);
        if (safeAmount <= 0L) {
            data.remove(key);
            db.deleteKey(uuid, key);
            return;
        }
        data.put(key, safeAmount);
        dirty.add(uuid);
        if (notify) {
            checkAndNotifyTierUp(uuid, category, normalized, oldAmount, safeAmount);
        }
    }

    public void reset(@NotNull UUID uuid, @Nullable CollectionInfoGui.Category category) {
        Map<String, Long> data = cache.get(uuid);
        if (category == null) {
            if (data != null) {
                data.clear();
            }
            dirty.remove(uuid);
            db.delete(uuid, null);
            return;
        }
        String prefix = category.pageId() + ":";
        if (data != null) {
            data.keySet().removeIf(k -> k.startsWith(prefix));
        }
        db.delete(uuid, category.pageId());
    }

    public void onJoin(@NotNull Player player) {
        loadIntoCache(player.getUniqueId());
    }

    public void loadIntoCache(@NotNull UUID uuid) {
        Map<String, Long> loaded = db.load(uuid);
        Map<String, Long> target = cache.computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>());
        for (Map.Entry<String, Long> entry : loaded.entrySet()) {
            Long value = entry.getValue();
            if (value != null && value > 0L) {
                target.merge(entry.getKey(), value, Math::max);
            }
        }
    }

    public void onQuit(@NotNull UUID uuid) {
        dirty.remove(uuid);
        Map<String, Long> data = cache.remove(uuid);
        if (data != null && !data.isEmpty()) {
            db.save(uuid, new HashMap<>(data));
        }
    }

    public void save(@NotNull UUID uuid) {
        dirty.remove(uuid);
        Map<String, Long> data = cache.get(uuid);
        if (data != null && !data.isEmpty()) {
            db.save(uuid, new HashMap<>(data));
        }
    }

    public void saveDirty() {
        for (UUID uuid : dirty.toArray(UUID[]::new)) {
            if (!dirty.remove(uuid)) {
                continue;
            }
            Map<String, Long> data = cache.get(uuid);
            if (data != null && !data.isEmpty()) {
                db.save(uuid, new HashMap<>(data));
            }
        }
    }

    public @NotNull Map<UUID, Map<String, Long>> onlineSnapshots() {
        Map<UUID, Map<String, Long>> result = new HashMap<>();
        if (Bukkit.getServer() == null) {
            for (Map.Entry<UUID, Map<String, Long>> entry : cache.entrySet()) {
                result.put(entry.getKey(), new HashMap<>(entry.getValue()));
            }
            return result;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Map<String, Long> data = cache.get(player.getUniqueId());
            if (data != null) {
                result.put(player.getUniqueId(), new HashMap<>(data));
            }
        }
        return result;
    }

    private @NotNull Map<String, Long> getOrLoad(@NotNull UUID uuid) {
        return cache.computeIfAbsent(uuid, id -> new ConcurrentHashMap<>(db.load(id)));
    }

    private long add(@NotNull UUID uuid,
                     @NotNull CollectionInfoGui.Category category,
                     @NotNull Material material,
                     @NotNull String collectionKey,
                     long amount,
                     boolean notify) {
        Map<String, Long> data = getOrLoad(uuid);
        long oldAmount = data.getOrDefault(collectionKey, 0L);
        long newAmount = data.merge(collectionKey, amount, Long::sum);
        dirty.add(uuid);
        if (notify) {
            checkAndNotifyTierUp(uuid, category, material, oldAmount, newAmount);
        }
        return newAmount;
    }

    private void checkAndNotifyTierUp(@NotNull UUID uuid,
                                      @NotNull CollectionInfoGui.Category category,
                                      @NotNull Material material,
                                      long oldAmount,
                                      long newAmount) {
        int oldTier = CollectionInfoGui.tierFor(oldAmount, category);
        int newTier = CollectionInfoGui.tierFor(newAmount, category);
        if (newTier <= oldTier) {
            return;
        }
        if (Bukkit.getServer() == null) {
            return;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            return;
        }
        CollectionInfoGui.CollectionItem item = CollectionInfoGui.findItem(category, material);
        String itemName = item != null ? item.name() : material.name();
        String tierLabel = CollectionInfoGui.roman(newTier);
        String rangeLabel = newTier > oldTier + 1
                ? CollectionInfoGui.roman(oldTier + 1) + " ~ " + tierLabel
                : tierLabel;

        Component chatMessage = Component.text("[收藏升级] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(category.title() + " · ", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                .append(Component.text(itemName + " " + rangeLabel, NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" 已解锁！", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, false))
                .append(Component.text("[点击领取奖励]", NamedTextColor.AQUA, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/collect"))
                        .hoverEvent(HoverEvent.showText(Component.text("点击打开收集中心领取奖励", NamedTextColor.YELLOW))));
        player.sendMessage(chatMessage);
        player.sendActionBar(Component.text("★ 收藏升级：", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(itemName + " " + tierLabel, NamedTextColor.YELLOW))
                .append(Component.text("（输入 /collect 领奖）", NamedTextColor.GRAY)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.25f);
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
