package maple.underground_state.skills;

import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;

public final class SkillProgressData {

    private final EnumMap<SkillType, Long> xpBySkill = new EnumMap<>(SkillType.class);
    private long mapleXp;
    private int combatXpRemainderTenths;
    private int miningXpRemainderTenths;
    private int foragingXpRemainderTenths;
    private int farmingXpRemainderTenths;
    private int fishingXpRemainderTenths;
    private int alchemyXpRemainderTenths;
    private int craftingXpRemainderTenths;
    private int explorationDistanceRemainderMillis;

    public SkillProgressData() {
        for (SkillType skillType : SkillType.values()) {
            xpBySkill.put(skillType, 0L);
        }
        mapleXp = 0L;
    }

    public long getXp(@NotNull SkillType skillType) {
        return xpBySkill.getOrDefault(skillType, 0L);
    }

    public void setXp(@NotNull SkillType skillType, long xp) {
        xpBySkill.put(skillType, Math.max(0L, xp));
    }

    public long addXp(@NotNull SkillType skillType, long delta) {
        long updated = Math.max(0L, getXp(skillType) + delta);
        xpBySkill.put(skillType, updated);
        return updated;
    }

    public long getMapleXp() {
        return mapleXp;
    }

    public void setMapleXp(long mapleXp) {
        this.mapleXp = Math.max(0L, mapleXp);
    }

    public long addMapleXp(long delta) {
        mapleXp = Math.max(0L, mapleXp + delta);
        return mapleXp;
    }

    public int getCombatXpRemainderTenths() {
        return combatXpRemainderTenths;
    }

    public void setCombatXpRemainderTenths(int combatXpRemainderTenths) {
        this.combatXpRemainderTenths = Math.max(0, combatXpRemainderTenths % 10);
    }

    public int getMiningXpRemainderTenths() {
        return miningXpRemainderTenths;
    }

    public void setMiningXpRemainderTenths(int miningXpRemainderTenths) {
        this.miningXpRemainderTenths = Math.max(0, miningXpRemainderTenths % 10);
    }

    public int getForagingXpRemainderTenths() {
        return foragingXpRemainderTenths;
    }

    public void setForagingXpRemainderTenths(int foragingXpRemainderTenths) {
        this.foragingXpRemainderTenths = Math.max(0, foragingXpRemainderTenths % 10);
    }

    public int getFarmingXpRemainderTenths() {
        return farmingXpRemainderTenths;
    }

    public void setFarmingXpRemainderTenths(int farmingXpRemainderTenths) {
        this.farmingXpRemainderTenths = Math.max(0, farmingXpRemainderTenths % 10);
    }

    public int getFishingXpRemainderTenths() {
        return fishingXpRemainderTenths;
    }

    public void setFishingXpRemainderTenths(int fishingXpRemainderTenths) {
        this.fishingXpRemainderTenths = Math.max(0, fishingXpRemainderTenths % 10);
    }

    public int getAlchemyXpRemainderTenths() {
        return alchemyXpRemainderTenths;
    }

    public void setAlchemyXpRemainderTenths(int alchemyXpRemainderTenths) {
        this.alchemyXpRemainderTenths = Math.max(0, alchemyXpRemainderTenths % 10);
    }

    public int getCraftingXpRemainderTenths() {
        return craftingXpRemainderTenths;
    }

    public void setCraftingXpRemainderTenths(int craftingXpRemainderTenths) {
        this.craftingXpRemainderTenths = Math.max(0, craftingXpRemainderTenths % 10);
    }

    public int getExplorationDistanceRemainderMillis() {
        return Math.max(0, explorationDistanceRemainderMillis);
    }

    public void setExplorationDistanceRemainderMillis(int explorationDistanceRemainderMillis) {
        this.explorationDistanceRemainderMillis = Math.max(0, explorationDistanceRemainderMillis);
    }

    public @NotNull Map<SkillType, Long> asMap() {
        return Map.copyOf(xpBySkill);
    }

    public @NotNull SkillProgressData copy() {
        SkillProgressData copy = new SkillProgressData();
        for (SkillType skillType : SkillType.values()) {
            copy.setXp(skillType, getXp(skillType));
        }
        copy.setMapleXp(getMapleXp());
        copy.setCombatXpRemainderTenths(getCombatXpRemainderTenths());
        copy.setMiningXpRemainderTenths(getMiningXpRemainderTenths());
        copy.setForagingXpRemainderTenths(getForagingXpRemainderTenths());
        copy.setFarmingXpRemainderTenths(getFarmingXpRemainderTenths());
        copy.setFishingXpRemainderTenths(getFishingXpRemainderTenths());
        copy.setAlchemyXpRemainderTenths(getAlchemyXpRemainderTenths());
        copy.setCraftingXpRemainderTenths(getCraftingXpRemainderTenths());
        copy.setExplorationDistanceRemainderMillis(getExplorationDistanceRemainderMillis());
        return copy;
    }
}
