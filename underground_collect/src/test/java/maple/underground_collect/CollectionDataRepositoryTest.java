package maple.underground_collect;

import maple.underground_state.gui.CollectionInfoGui;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionDataRepositoryTest {

    private static final class StubRepository extends CollectionDataRepository {
        private final Map<UUID, Map<String, Integer>> dbClaims = new HashMap<>();
        private final List<LeaderboardEntry> dbLeaderboard;

        StubRepository(List<LeaderboardEntry> dbLeaderboard) {
            super(null, Logger.getAnonymousLogger(), false);
            this.dbLeaderboard = dbLeaderboard;
        }

        void seedDbClaim(UUID uuid, String key, int tier) {
            dbClaims.computeIfAbsent(uuid, ignored -> new HashMap<>()).put(key, tier);
        }

        @Override
        protected Map<String, Integer> loadClaimsFromDb(UUID uuid) {
            return new HashMap<>(dbClaims.getOrDefault(uuid, Map.of()));
        }

        @Override
        protected void saveClaimsBatchToDb(UUID uuid, Map<String, Integer> claimsToSave) {
            dbClaims.computeIfAbsent(uuid, ignored -> new HashMap<>()).putAll(claimsToSave);
        }

        @Override
        protected void deleteClaimsFromDb(UUID uuid, String keyPrefix) {
            Map<String, Integer> existing = dbClaims.get(uuid);
            if (existing == null) {
                return;
            }
            if (keyPrefix == null || keyPrefix.isBlank()) {
                existing.clear();
            } else {
                existing.keySet().removeIf(k -> k.startsWith(keyPrefix + ":"));
            }
        }

        @Override
        protected List<LeaderboardEntry> loadLeaderboardFromDb(CollectionInfoGui.Category category, int limit) {
            return dbLeaderboard;
        }
    }

    @Test
    void claimsCacheSupportsPreloadUpdateResetAndEvict() {
        UUID playerId = UUID.randomUUID();
        String oakKey = CollectionInfoGui.collectionKey(CollectionInfoGui.Category.FORAGING, Material.OAK_LOG);
        String coalKey = CollectionInfoGui.collectionKey(CollectionInfoGui.Category.MINING, Material.COAL);

        StubRepository repo = new StubRepository(List.of());
        repo.seedDbClaim(playerId, oakKey, 2);

        assertFalse(repo.isClaimsLoaded(playerId));
        repo.preloadClaimsAsync(playerId, null);
        assertTrue(repo.isClaimsLoaded(playerId));
        assertEquals(2, repo.getClaimedTier(playerId, oakKey));

        repo.updateCachedClaims(playerId, Map.of(oakKey, 5, coalKey, 3));
        assertEquals(5, repo.getClaimedTier(playerId, oakKey));
        assertEquals(3, repo.getClaimedTier(playerId, coalKey));

        // 不会回退到更低层
        repo.updateCachedClaims(playerId, Map.of(oakKey, 1));
        assertEquals(5, repo.getClaimedTier(playerId, oakKey));

        // 按分类重置仅清除对应分类
        repo.resetClaimsAsync(playerId, CollectionInfoGui.Category.FORAGING);
        assertEquals(0, repo.getClaimedTier(playerId, oakKey));
        assertEquals(3, repo.getClaimedTier(playerId, coalKey));

        repo.evictPlayer(playerId);
        assertFalse(repo.isClaimsLoaded(playerId));
    }

    @Test
    void realtimeLeaderboardMergesOnlineProgress() {
        UUID offlineTop = UUID.randomUUID();
        UUID onlineClimber = UUID.randomUUID();
        UUID onlineNew = UUID.randomUUID();

        StubRepository repo = new StubRepository(List.of(
                new CollectionDataRepository.LeaderboardEntry(offlineTop, 1000L),
                new CollectionDataRepository.LeaderboardEntry(onlineClimber, 500L)
        ));

        String oakKey = CollectionInfoGui.collectionKey(CollectionInfoGui.Category.FORAGING, Material.OAK_LOG);
        String codKey = CollectionInfoGui.collectionKey(CollectionInfoGui.Category.FISHING, Material.COD);
        Map<UUID, Map<String, Long>> onlineSnapshots = Map.of(
                onlineClimber, Map.of(oakKey, 2500L),
                onlineNew, Map.of(oakKey, 1200L, codKey, 300L)
        );

        List<CollectionDataRepository.LeaderboardEntry> overall = repo.buildRealtimeLeaderboard(null, onlineSnapshots);
        assertEquals(3, overall.size());
        assertEquals(onlineClimber, overall.get(0).uuid());
        assertEquals(2500L, overall.get(0).totalAmount());
        assertEquals(onlineNew, overall.get(1).uuid());
        assertEquals(1500L, overall.get(1).totalAmount());
        assertEquals(offlineTop, overall.get(2).uuid());

        List<CollectionDataRepository.LeaderboardEntry> foraging = repo.buildRealtimeLeaderboard(
                CollectionInfoGui.Category.FORAGING, onlineSnapshots);
        assertEquals(onlineClimber, foraging.get(0).uuid());
        assertEquals(2500L, foraging.get(0).totalAmount());
        assertEquals(onlineNew, foraging.get(1).uuid());
        assertEquals(1200L, foraging.get(1).totalAmount());
    }

    @Test
    void commandCategoryParserAcceptsAliasesAndAllTokens() {
        assertEquals(CollectionInfoGui.Category.FARMING, CollectCommand.parseCategory(new String[]{"open", "farming"}, 1));
        assertEquals(CollectionInfoGui.Category.FORAGING, CollectCommand.parseCategory(new String[]{"open", "伐木"}, 1));
        assertEquals(CollectionInfoGui.Category.FISHING, CollectCommand.parseCategory(new String[]{"open", "collection_fishing_rod"}, 1));
        assertNull(CollectCommand.parseCategory(new String[]{"claim", "all"}, 1));
        assertNull(CollectCommand.parseCategory(new String[]{"claim"}, 1));
    }
}
