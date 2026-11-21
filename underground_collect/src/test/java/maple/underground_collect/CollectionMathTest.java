package maple.underground_collect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CollectionMathTest {
    @Test
    void professionalCollectionsHaveTwelveTiersAndNoSkillXp() {
        var farm = maple.underground_state.gui.CollectionInfoGui.Category.FARMING;
        var mine = maple.underground_state.gui.CollectionInfoGui.Category.MINING;
        var combat = maple.underground_state.gui.CollectionInfoGui.Category.COMBAT;
        assertEquals(12, CollectionMath.maxTier(farm));
        assertEquals(12, CollectionMath.maxTier(mine));
        assertEquals(9, CollectionMath.maxTier(combat));
        assertEquals(10, CollectionMath.currentTier(100000, farm));
        assertEquals(11, CollectionMath.currentTier(250000, mine));
        assertEquals(12, CollectionMath.currentTier(500000, mine));
        assertEquals(0, CollectionMath.tierXpReward(9, farm));
        assertEquals(0, CollectionMath.tierXpReward(9, combat));
        assertEquals(0, CollectionMath.tierXpReward(10, farm));
        assertEquals(0, CollectionMath.tierXpReward(11, mine));
        assertEquals(0, CollectionMath.tierXpReward(12, mine));
        assertEquals("XII", CollectionMath.roman(12));
    }

    @Test
    void currentTierMatchesThresholds() {
        assertEquals(0, CollectionMath.currentTier(0));
        assertEquals(1, CollectionMath.currentTier(50));
        assertEquals(2, CollectionMath.currentTier(100));
        assertEquals(9, CollectionMath.currentTier(50_000));
    }

    @Test
    void rewardsGrantOnlyMapleXp() {
        assertEquals(0, CollectionMath.tierXpReward(1));
        assertEquals(0, CollectionMath.tierXpReward(4));
        assertEquals(0, CollectionMath.tierXpReward(9));
        assertEquals(1, CollectionMath.mapleXpReward(1));
        assertEquals(4, CollectionMath.mapleXpReward(9));
        assertEquals(6, CollectionMath.mapleXpReward(12));
    }
}
