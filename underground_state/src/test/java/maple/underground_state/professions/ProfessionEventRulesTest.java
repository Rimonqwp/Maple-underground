package maple.underground_state.professions;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProfessionEventRulesTest {
    @Test void cooldownAndActiveEventSuppressDrawsWhileEliteAllowsBothTypes() {
        assertFalse(ProfessionEventRules.mayRoll(false,0,false,100,0));
        assertTrue(ProfessionEventRules.mayRoll(false,1,false,100,100));
        assertFalse(ProfessionEventRules.mayRoll(true,1,false,100,0));
        assertTrue(ProfessionEventRules.mayRoll(true,2,false,300,300));
        assertTrue(ProfessionEventRules.mayRoll(false,2,false,300,90));
        assertFalse(ProfessionEventRules.mayRoll(true,2,true,300,0));
        assertFalse(ProfessionEventRules.mayRoll(false,2,true,300,0));
        assertFalse(ProfessionEventRules.mayRoll(true,2,false,299,300));
    }
    @Test void rewardsRequireBothPersonalContributionAndRange() {
        assertTrue(ProfessionEventRules.mayReward(20,100,true));
        assertFalse(ProfessionEventRules.mayReward(19.999,100,true));
        assertFalse(ProfessionEventRules.mayReward(100,100,false));
        assertFalse(ProfessionEventRules.mayReward(0,0,true));
        assertFalse(ProfessionEventRules.mayReward(Double.NaN,100,true));
    }
    @Test void expiryBoundariesRespectGracePeriodAndTier() {
        assertFalse(ProfessionEventRules.expired(true,true,true,false,89999,9));
        assertTrue(ProfessionEventRules.expired(true,true,true,false,90000,0));
        assertFalse(ProfessionEventRules.expired(true,true,true,true,179999,9));
        assertTrue(ProfessionEventRules.expired(true,true,true,true,180000,0));
        assertTrue(ProfessionEventRules.expired(true,true,true,true,100,10));
        assertTrue(ProfessionEventRules.expired(false,true,true,true,100,0));
        assertTrue(ProfessionEventRules.expired(true,false,true,true,100,0));
    }
}
