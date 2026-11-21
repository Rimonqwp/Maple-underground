package maple.underground_collect;

import maple.underground_state.UndergroundState;
import maple.underground_state.collection.CollectionProgressManager;
import maple.underground_state.gui.CollectionInfoGui;
import maple.underground_state.skills.SkillProgressManager;
import maple.underground_state.skills.SkillRewardService;
import maple.underground_state.skills.SkillType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class CollectionRewardService {

    private final @Nullable Plugin plugin;
    private final UndergroundState state;
    private final CollectionDataRepository repository;
    private final Map<UUID, Object> playerLocks = new ConcurrentHashMap<>();

    CollectionRewardService(@NotNull UndergroundState state, @NotNull CollectionDataRepository repository) {
        this(null, state, repository);
    }

    CollectionRewardService(@Nullable Plugin plugin,
                            @NotNull UndergroundState state,
                            @NotNull CollectionDataRepository repository) {
        this.plugin = plugin;
        this.state = state;
        this.repository = repository;
    }

    @NotNull
    ClaimSummary claimAll(@NotNull Player player) {
        return claim(player.getUniqueId(), null);
    }

    @NotNull
    ClaimSummary claimCategory(@NotNull Player player, @NotNull CollectionInfoGui.Category category) {
        return claim(player.getUniqueId(), category);
    }

    @NotNull
    ClaimSummary claimItem(@NotNull Player player,
                           @NotNull CollectionInfoGui.Category category,
                           @NotNull CollectionInfoGui.CollectionItem item,
                           int targetTier) {
        UUID uuid = player.getUniqueId();
        ClaimSummary guard = checkReady(uuid, category);
        if (guard != null) {
            return guard;
        }
        SkillType skillType = skillTypeForCategory(category);
        if (skillType == null) {
            return ClaimSummary.nothing();
        }

        Object lock = playerLocks.computeIfAbsent(uuid, ignored -> new Object());
        synchronized (lock) {
            CollectionProgressManager progressManager = state.getCollectionProgressManager();
            SkillRewardService rewardService = state.getSkillRewardService();
            SkillProgressManager skillProgressManager = state.getSkillProgressManager();

            String key = CollectionInfoGui.collectionKey(category, item.material());
            long collected = progressManager != null ? progressManager.getAmount(uuid, key) : 0L;
            int currentTier = CollectionMath.currentTier(collected, category);
            int previousClaimedTier = repository.getClaimedTier(uuid, key);
            int effectiveTarget = Math.min(currentTier, targetTier);

            if (effectiveTarget <= previousClaimedTier) {
                return ClaimSummary.nothing();
            }

            Map<SkillType, Long> skillXpByType = new EnumMap<>(SkillType.class);
            long mapleXp = 0L;
            int tiersClaimed = 0;
            for (int tier = previousClaimedTier + 1; tier <= effectiveTarget; tier++) {
                tiersClaimed++;
                skillXpByType.merge(skillType, (long) CollectionMath.tierXpReward(tier, category), Long::sum);
                mapleXp += CollectionMath.mapleXpReward(tier);
            }

            Map<String, Integer> updates = Map.of(key, effectiveTarget);
            repository.updateCachedClaims(uuid, updates);
            repository.saveClaimsBatchAsync(uuid, updates);

            grantRewards(uuid, rewardService, skillProgressManager, skillXpByType, mapleXp);
            return new ClaimSummary(ClaimStatus.CLAIMED, 1, tiersClaimed, mapleXp, skillXpByType);
        }
    }

    @NotNull
    ClaimSummary claim(@NotNull UUID uuid, @Nullable CollectionInfoGui.Category category) {
        ClaimSummary guard = checkReady(uuid, category);
        if (guard != null) {
            return guard;
        }

        Object lock = playerLocks.computeIfAbsent(uuid, ignored -> new Object());
        synchronized (lock) {
            CollectionProgressManager progressManager = state.getCollectionProgressManager();
            SkillRewardService rewardService = state.getSkillRewardService();
            SkillProgressManager skillProgressManager = state.getSkillProgressManager();

            Map<String, Long> progress = progressManager != null ? progressManager.snapshot(uuid) : Map.of();
            Map<String, Integer> claimed = repository.getCachedClaims(uuid);
            Map<SkillType, Long> skillXpByType = new EnumMap<>(SkillType.class);
            Map<String, Integer> newClaimStates = new LinkedHashMap<>();
            long mapleXp = 0L;
            int collectionsClaimed = 0;
            int tiersClaimed = 0;

            for (CollectionInfoGui.Category currentCategory : targetCategories(category)) {
                SkillType skillType = skillTypeForCategory(currentCategory);
                if (skillType == null) {
                    continue;
                }
                for (CollectionInfoGui.CollectionItem item : currentCategory.items()) {
                    String key = CollectionInfoGui.collectionKey(currentCategory, item.material());
                    long collected = progress.getOrDefault(key, 0L);
                    int currentTier = CollectionMath.currentTier(collected, currentCategory);
                    int previousClaimedTier = claimed.getOrDefault(key, 0);
                    if (currentTier <= previousClaimedTier) {
                        continue;
                    }
                    collectionsClaimed++;
                    newClaimStates.put(key, currentTier);
                    for (int tier = previousClaimedTier + 1; tier <= currentTier; tier++) {
                        tiersClaimed++;
                        skillXpByType.merge(skillType, (long) CollectionMath.tierXpReward(tier, currentCategory), Long::sum);
                        mapleXp += CollectionMath.mapleXpReward(tier);
                    }
                }
            }

            if (newClaimStates.isEmpty()) {
                return ClaimSummary.nothing();
            }

            repository.updateCachedClaims(uuid, newClaimStates);
            repository.saveClaimsBatchAsync(uuid, newClaimStates);
            grantRewards(uuid, rewardService, skillProgressManager, skillXpByType, mapleXp);
            return new ClaimSummary(ClaimStatus.CLAIMED, collectionsClaimed, tiersClaimed, mapleXp, skillXpByType);
        }
    }

    void evictPlayer(@NotNull UUID uuid) {
        playerLocks.remove(uuid);
    }

    @NotNull
    Map<String, Long> missingSummary(@NotNull UUID uuid, @Nullable CollectionInfoGui.Category category) {
        CollectionProgressManager progressManager = state.getCollectionProgressManager();
        Map<String, Long> progress = progressManager != null ? progressManager.snapshot(uuid) : Map.of();
        Map<String, Integer> claimed = repository.getCachedClaims(uuid);
        Map<String, Long> result = new LinkedHashMap<>();

        for (CollectionInfoGui.Category currentCategory : targetCategories(category)) {
            for (CollectionInfoGui.CollectionItem item : currentCategory.items()) {
                String key = CollectionInfoGui.collectionKey(currentCategory, item.material());
                long collected = progress.getOrDefault(key, 0L);
                int currentTier = CollectionMath.currentTier(collected, currentCategory);
                int previousClaimedTier = claimed.getOrDefault(key, 0);
                if (currentTier > previousClaimedTier) {
                    result.put(currentCategory.title() + " - " + item.name(), collected);
                }
            }
        }
        return result;
    }

    @NotNull
    List<MissingEntry> missingEntries(@NotNull UUID uuid, @Nullable CollectionInfoGui.Category category) {
        CollectionProgressManager progressManager = state.getCollectionProgressManager();
        Map<String, Long> progress = progressManager != null ? progressManager.snapshot(uuid) : Map.of();
        Map<String, Integer> claimed = repository.getCachedClaims(uuid);
        List<MissingEntry> entries = new ArrayList<>();

        for (CollectionInfoGui.Category currentCategory : targetCategories(category)) {
            int maxTier = CollectionMath.maxTier(currentCategory);
            for (CollectionInfoGui.CollectionItem item : currentCategory.items()) {
                String key = CollectionInfoGui.collectionKey(currentCategory, item.material());
                long collected = progress.getOrDefault(key, 0L);
                int currentTier = CollectionMath.currentTier(collected, currentCategory);
                int claimedTier = claimed.getOrDefault(key, 0);
                int claimableTiers = Math.max(0, currentTier - claimedTier);
                if (claimableTiers <= 0 && currentTier >= maxTier) {
                    continue;
                }
                int nextTier = Math.min(maxTier, currentTier + 1);
                long nextRequirement = CollectionInfoGui.requirementForTier(currentCategory, nextTier);
                double nextRatio = nextRequirement <= 0L ? 1.0D : Math.min(1.0D, (double) collected / (double) nextRequirement);
                entries.add(new MissingEntry(
                        currentCategory,
                        item,
                        collected,
                        currentTier,
                        maxTier,
                        claimedTier,
                        claimableTiers,
                        nextRequirement,
                        nextRatio
                ));
            }
        }

        entries.sort(Comparator
                .comparing((MissingEntry e) -> e.claimableTiers() > 0).reversed()
                .thenComparing(Comparator.comparingInt(MissingEntry::claimableTiers).reversed())
                .thenComparing(Comparator.comparingDouble(MissingEntry::nextRatio).reversed())
                .thenComparing(e -> e.category().ordinal())
                .thenComparing(e -> e.item().name()));
        return entries;
    }

    void sendClaimFeedback(@NotNull Player player, @NotNull ClaimSummary summary) {
        switch (summary.status()) {
            case LOADING_PROFESSION -> player.sendMessage(Component.text("职业存档正在加载中，请稍候片刻再领取收藏奖励。", NamedTextColor.YELLOW));
            case LOADING_CLAIMS -> player.sendMessage(Component.text("收藏领取记录正在加载中，请稍候片刻再试。", NamedTextColor.YELLOW));
            case NOTHING -> player.sendMessage(Component.text("当前没有可领取的收藏奖励。", NamedTextColor.YELLOW));
            case CLAIMED -> {
                player.sendMessage(Component.text("收藏奖励领取完成！", NamedTextColor.GREEN));
                player.sendMessage(Component.text("领取收藏层数: ", NamedTextColor.GRAY)
                        .append(Component.text(String.valueOf(summary.tiersClaimed()), NamedTextColor.YELLOW)));
                player.sendMessage(Component.text("领取收藏项数: ", NamedTextColor.GRAY)
                        .append(Component.text(String.valueOf(summary.collectionsClaimed()), NamedTextColor.YELLOW)));
                if (summary.mapleXp() > 0L) {
                    player.sendMessage(Component.text("枫叶经验: ", NamedTextColor.GRAY)
                            .append(Component.text("+" + CollectionMath.formatNumber(summary.mapleXp()), NamedTextColor.AQUA)));
                }
                for (Map.Entry<SkillType, Long> entry : summary.skillXpByType().entrySet()) {
                    if (entry.getValue() == null || entry.getValue() <= 0L) {
                        continue;
                    }
                    player.sendMessage(Component.text(entry.getKey().displayName() + " 经验: ", NamedTextColor.GRAY)
                            .append(Component.text("+" + CollectionMath.formatNumber(entry.getValue()), NamedTextColor.AQUA)));
                }
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
            }
        }
    }

    @Nullable
    private ClaimSummary checkReady(@NotNull UUID uuid, @Nullable CollectionInfoGui.Category category) {
        if (state.getProfessionService() != null && (category == null || CollectionMath.extended(category))) {
            var store = state.getProfessionService().store();
            if (!store.ready(uuid)) {
                runAsync(() -> store.load(uuid));
                return ClaimSummary.loadingProfession();
            }
        }
        if (!repository.isClaimsLoaded(uuid)) {
            repository.preloadClaimsAsync(uuid, null);
            return ClaimSummary.loadingClaims();
        }
        return null;
    }

    private void grantRewards(@NotNull UUID uuid,
                              @Nullable SkillRewardService rewardService,
                              @Nullable SkillProgressManager skillProgressManager,
                              @NotNull Map<SkillType, Long> skillXpByType,
                              long mapleXp) {
        if (rewardService != null) {
            for (Map.Entry<SkillType, Long> entry : skillXpByType.entrySet()) {
                if (entry.getValue() != null && entry.getValue() > 0L) {
                    rewardService.addSkillXp(uuid, entry.getKey(), entry.getValue());
                }
            }
        }
        if (mapleXp > 0L && skillProgressManager != null) {
            skillProgressManager.addMapleXp(uuid, mapleXp);
            runAsync(() -> skillProgressManager.save(uuid));
        }
    }

    private void runAsync(@NotNull Runnable task) {
        if (plugin != null && plugin.isEnabled() && Bukkit.getServer() != null) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        } else {
            task.run();
        }
    }

    @NotNull
    private static CollectionInfoGui.Category[] targetCategories(@Nullable CollectionInfoGui.Category category) {
        return category == null ? CollectionInfoGui.Category.values() : new CollectionInfoGui.Category[]{category};
    }

    @Nullable
    private static SkillType skillTypeForCategory(@NotNull CollectionInfoGui.Category category) {
        return switch (category) {
            case FARMING -> SkillType.FARMING;
            case MINING -> SkillType.MINING;
            case COMBAT -> SkillType.COMBAT;
            case FORAGING -> SkillType.FORAGING;
            case FISHING -> SkillType.FISHING;
        };
    }

    enum ClaimStatus {
        CLAIMED,
        NOTHING,
        LOADING_PROFESSION,
        LOADING_CLAIMS
    }

    record ClaimSummary(@NotNull ClaimStatus status,
                        int collectionsClaimed,
                        int tiersClaimed,
                        long mapleXp,
                        @NotNull Map<SkillType, Long> skillXpByType) {

        static ClaimSummary nothing() {
            return new ClaimSummary(ClaimStatus.NOTHING, 0, 0, 0L, Map.of());
        }

        static ClaimSummary loadingProfession() {
            return new ClaimSummary(ClaimStatus.LOADING_PROFESSION, 0, 0, 0L, Map.of());
        }

        static ClaimSummary loadingClaims() {
            return new ClaimSummary(ClaimStatus.LOADING_CLAIMS, 0, 0, 0L, Map.of());
        }

        boolean hasAnything() {
            return status == ClaimStatus.CLAIMED && (collectionsClaimed > 0 || tiersClaimed > 0 || mapleXp > 0L);
        }
    }

    record MissingEntry(@NotNull CollectionInfoGui.Category category,
                        @NotNull CollectionInfoGui.CollectionItem item,
                        long collected,
                        int currentTier,
                        int maxTier,
                        int claimedTier,
                        int claimableTiers,
                        long nextRequirement,
                        double nextRatio) {
    }
}
