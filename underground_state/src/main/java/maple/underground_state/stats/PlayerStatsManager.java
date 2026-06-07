package maple.underground_state.stats;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerStatsManager {

    private final PlayerStatsDB db;
    private final Map<UUID, PlayerStatsData> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Double> tempStrengthAmount = new ConcurrentHashMap<>();
    private final Map<UUID, Long> tempStrengthExpireAt = new ConcurrentHashMap<>();
    private final Set<UUID> pendingInitialLoads = ConcurrentHashMap.newKeySet();

    public PlayerStatsManager(PlayerStatsDB db) {
        this.db = db;
    }

    public PlayerStatsData getOrLoad(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::loadIntoSession);
    }

    public PlayerStatsData get(Player player) {
        return player == null ? new PlayerStatsData() : getOrLoad(player.getUniqueId());
    }

    public PlayerStatsData get(UUID uuid) {
        return uuid == null ? new PlayerStatsData() : getOrLoad(uuid);
    }

    public void addTempStrengthBonus(UUID uuid, double amount, long durationMs) {
        if (uuid == null) return;
        long now = System.currentTimeMillis();
        tempStrengthAmount.put(uuid, amount);
        tempStrengthExpireAt.put(uuid, now + durationMs);
    }

    public double getTempStrengthBonus(UUID uuid) {
        if (uuid == null) return 0;
        Long expireAt = tempStrengthExpireAt.get(uuid);
        if (expireAt == null) return 0;
        if (System.currentTimeMillis() >= expireAt) {
            tempStrengthAmount.remove(uuid);
            tempStrengthExpireAt.remove(uuid);
            return 0;
        }
        return tempStrengthAmount.getOrDefault(uuid, 0D);
    }

    public double getEffectiveStrength(Player player) {
        return player == null ? 0 : getEffectiveStrength(player.getUniqueId());
    }

    public double getEffectiveStrength(UUID uuid) {
        if (uuid == null) return 0;
        return get(uuid).getStrength() + getTempStrengthBonus(uuid);
    }

    public void set(UUID uuid, PlayerStatsData data) {
        if (uuid == null || data == null) return;
        pendingInitialLoads.remove(uuid);
        cache.put(uuid, data.copy());
    }

    public void onJoin(UUID uuid) {
        if (uuid == null) return;
        pendingInitialLoads.add(uuid);
        cache.putIfAbsent(uuid, new PlayerStatsData());
    }

    public void onQuit(UUID uuid) {
        if (uuid == null) return;
        pendingInitialLoads.remove(uuid);
        PlayerStatsData data = cache.remove(uuid);
        tempStrengthAmount.remove(uuid);
        tempStrengthExpireAt.remove(uuid);
        if (data != null) db.save(uuid, data);
    }

    public void save(UUID uuid) {
        if (uuid == null) return;
        PlayerStatsData data = cache.get(uuid);
        if (data != null) {
            db.save(uuid, data);
        }
    }

    public @NotNull List<UUID> cachedPlayerIds() {
        return List.copyOf(cache.keySet());
    }

    public @NotNull PlayerStatsData loadSnapshot(UUID uuid) {
        return uuid == null ? new PlayerStatsData() : db.load(uuid);
    }

    public @NotNull PlayerStatsData loadOrCreateSnapshot(UUID uuid) {
        if (uuid == null) {
            return new PlayerStatsData();
        }
        return db.loadOrCreate(uuid);
    }

    public void completeInitialLoad(UUID uuid, @NotNull PlayerStatsData loaded) {
        if (uuid == null) return;
        if (!pendingInitialLoads.remove(uuid)) return;
        if (!cache.containsKey(uuid)) return;
        cache.put(uuid, loaded.copy());
    }

    public void applyBaseStats(UUID uuid, @NotNull PlayerStatsData latest) {
        if (uuid == null) return;
        PlayerStatsData data = cache.get(uuid);
        if (data == null) return;

        data.setHealth(latest.getHealth());
        data.setDefense(latest.getDefense());
        data.setStrength(latest.getStrength());
        data.setIntelligence(latest.getIntelligence());
        data.setCritChance(latest.getCritChance());
        data.setCritDamage(latest.getCritDamage());
        data.setBonusAttackSpeed(latest.getBonusAttackSpeed());
        data.setTrueDefense(latest.getTrueDefense());
        data.setHealthRegen(latest.getHealthRegen());
        data.setFerocity(latest.getFerocity());
        data.setAbilityDamage(latest.getAbilityDamage());
        data.setSpeed(latest.getSpeed());
        data.setVitality(latest.getVitality());
        data.setMending(latest.getMending());
        data.setMonsterDamagePercent(latest.getMonsterDamagePercent());
        data.setMiningFortune(latest.getMiningFortune());
        data.setForagingFortune(latest.getForagingFortune());
        data.setFarmingFortune(latest.getFarmingFortune());
        data.setTreasureChance(latest.getTreasureChance());
        data.setBonusExpPercent(latest.getBonusExpPercent());
        data.setPotionDurationPercent(latest.getPotionDurationPercent());
        data.setPetLuck(latest.getPetLuck());
        data.setPetXpGainPercent(latest.getPetXpGainPercent());
        data.setExpShareRatePercent(latest.getExpShareRatePercent());
    }

    private PlayerStatsData loadIntoSession(UUID uuid) {
        return db.load(uuid);
    }
}
