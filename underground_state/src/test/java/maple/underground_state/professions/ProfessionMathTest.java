package maple.underground_state.professions;

import maple.underground_state.skills.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProfessionMathTest {
    @Test void unchangedSixtyLevelCurve() {
        int[] levels={8,18,30,42,55,60};long[] expected={4425,222425,8022425,30222425,79672425,111672425};
        for(int i=0;i<levels.length;i++) for(SkillType skill:new SkillType[]{SkillType.FARMING,SkillType.MINING})
            assertEquals(expected[i],SkillLevelTables.totalXpRequiredForLevel(skill,levels[i]));
    }
    @Test void fixedPointKeepsSmallAwardsAndCapsProfessionalXp() {
        assertEquals(1060,ProfessionMath.xpUnits(1,SkillType.FARMING,1,0));
        assertEquals(1050,ProfessionMath.xpUnits(1,SkillType.MINING,1,0));
        assertEquals(ProfessionMath.xpUnits(10,SkillType.FARMING,55,40),ProfessionMath.xpUnits(10,SkillType.FARMING,55,100));
        long total=0,remainder=0;
        for(int i=0;i<10;i++) { long award=remainder+ProfessionMath.xpUnits(1,SkillType.FARMING,1,0);total+=award/10000;remainder=award%10000; }
        assertEquals(1,total);assertEquals(600,remainder);
    }
    @Test void independentRelativeProbabilitiesAndPity() {
        assertTrue(ProfessionMath.roll(.02,75,0,150,.0349));assertFalse(ProfessionMath.roll(.02,75,0,150,.0351));
        assertTrue(ProfessionMath.roll(.001,100,0,3000,.0019));assertFalse(ProfessionMath.roll(.001,100,0,3000,.0021));
        assertTrue(ProfessionMath.roll(.02,0,149,150,.9999));
        assertFalse(ProfessionMath.roll(.02,0,148,150,.9999));
        assertTrue(ProfessionMath.roll(.002,0,1499,1500,.9999));
        assertTrue(ProfessionMath.roll(.1,0,19,20,.9999));
        var capped=new ProfessionBonuses(450,90,180,500,5).bounded();
        assertEquals(40,capped.xpPercent());assertEquals(75,capped.rarePercent());assertEquals(100,capped.eventPercent());assertEquals(2,capped.eventTier());
    }
    @Test void yieldIsWholeBasePortionsWithFractionalRoll() {
        assertEquals(6,ProfessionMath.extraAmount(2,320,.21));assertEquals(8,ProfessionMath.extraAmount(2,320,.01));
        assertEquals(0,ProfessionMath.extraAmount(1,20,.21));assertEquals(1,ProfessionMath.extraAmount(1,20,.19));
    }
    @Test void collectionAndCoinsAreProfessionSpecific() {
        assertEquals(9,ProfessionMath.collectionTier(500000,false));assertEquals(12,ProfessionMath.collectionTier(500000,true));
        assertEquals(10,ProfessionMath.collectionTier(100000,true));assertEquals(11,ProfessionMath.collectionTier(250000,true));
        assertEquals(2,ProfessionMath.collectionXp(1,true));assertEquals(500,ProfessionMath.collectionXp(9,true));
        assertEquals(750,ProfessionMath.collectionXp(10,true));assertEquals(1000,ProfessionMath.collectionXp(11,true));assertEquals(1500,ProfessionMath.collectionXp(12,true));
        assertEquals(100,FarmingLevelTable.definitionForLevel(10).coinReward());assertEquals(250,FarmingLevelTable.definitionForLevel(11).coinReward());
        assertEquals(500,MiningLevelTable.definitionForLevel(40).coinReward());assertEquals(1000,MiningLevelTable.definitionForLevel(41).coinReward());
        assertEquals(2,MiningLevelTable.definitionForLevel(60).miningFortuneReward());assertEquals(2,FarmingLevelTable.definitionForLevel(60).farmingFortuneReward());
    }
}
