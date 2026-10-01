package maple.underground_collect;

import maple.underground_state.UndergroundState;
import maple.underground_state.collection.CollectionProgressManager;
import maple.underground_state.gui.CollectionInfoGui;
import maple.underground_state.skills.SkillProgressManager;
import maple.underground_state.skills.SkillRewardService;
import maple.underground_state.skills.SkillType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class CollectionRewardService {

    private final UndergroundState state;
    private final CollectionDataRepository repository;

    CollectionRewardService(@NotNull UndergroundState state, @NotNull CollectionDataRepository repository) {
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
    ClaimSummary claim(@NotNull UUID uuid, @Nullable CollectionInfoGui.Category category) {
        if(state.getProfessionService()!=null && (category==null || CollectionMath.extended(category))) {
            var store=state.getProfessionService().store();
            if(!store.ready(uuid))store.load(uuid);
            if(!store.ready(uuid))throw new IllegalStateException("职业存档加载失败，未领取任何收藏奖励");
        }
        CollectionProgressManager progressManager = state.getCollectionProgressManager();
        SkillRewardService rewardService = state.getSkillRewardService();
        SkillProgressManager skillProgressManager = state.getSkillProgressManager();

        Map<String, Long> progress = progressManager != null ? progressManager.snapshot(uuid) : Map.of();
        Map<String, Integer> claimed = repository.loadClaims(uuid);
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

        for (Map.Entry<String, Integer> entry : newClaimStates.entrySet()) {
            repository.saveClaim(uuid, entry.getKey(), entry.getValue());
        }

        for (Map.Entry<SkillType, Long> entry : skillXpByType.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0L) {
                rewardService.addSkillXp(uuid, entry.getKey(), entry.getValue());
            }
        }

        if (mapleXp > 0L) {
            skillProgressManager.addMapleXp(uuid, mapleXp);
            skillProgressManager.save(uuid);
        }

        return new ClaimSummary(collectionsClaimed, tiersClaimed, mapleXp, skillXpByType);
    }

    @NotNull
    Map<String, Long> missingSummary(@NotNull UUID uuid, @Nullable CollectionInfoGui.Category category) {
        CollectionProgressManager progressManager = state.getCollectionProgressManager();
        Map<String, Long> progress = progressManager != null ? progressManager.snapshot(uuid) : Map.of();
        Map<String, Integer> claimed = repository.loadClaims(uuid);
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

    record ClaimSummary(int collectionsClaimed,
                        int tiersClaimed,
                        long mapleXp,
                        @NotNull Map<SkillType, Long> skillXpByType) {
        boolean hasAnything() {
            return collectionsClaimed > 0 || tiersClaimed > 0 || mapleXp > 0L;
        }
    }
}
