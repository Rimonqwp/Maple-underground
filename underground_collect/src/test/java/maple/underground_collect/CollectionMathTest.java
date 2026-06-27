package maple.underground_collect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CollectionMathTest {

    @Test
    void currentTierMatchesThresholds() {
        assertEquals(0, CollectionMath.currentTier(0));
        assertEquals(1, CollectionMath.currentTier(50));
        assertEquals(2, CollectionMath.currentTier(100));
        assertEquals(9, CollectionMath.currentTier(50_000));
    }

    @Test
    void rewardsMatchExpectedCurve() {
        assertEquals(20, CollectionMath.tierXpReward(1));
        assertEquals(250, CollectionMath.tierXpReward(4));
        assertEquals(5_000, CollectionMath.tierXpReward(9));
        assertEquals(1, CollectionMath.mapleXpReward(1));
        assertEquals(4, CollectionMath.mapleXpReward(9));
    }
}
