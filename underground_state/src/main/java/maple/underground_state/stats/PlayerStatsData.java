package maple.underground_state.stats;

public final class PlayerStatsData {

    public static final double DEFAULT_HEALTH = 20;
    public static final double DEFAULT_DEFENSE = 0;
    public static final double DEFAULT_STRENGTH = 0;
    public static final double DEFAULT_INTELLIGENCE = 100;
    public static final double DEFAULT_CRIT_CHANCE = 30;
    public static final double DEFAULT_CRIT_DAMAGE = 50;
    public static final double DEFAULT_BONUS_ATTACK_SPEED = 0;
    public static final double DEFAULT_TRUE_DEFENSE = 0;
    public static final double DEFAULT_HEALTH_REGEN = 100;
    public static final double DEFAULT_FEROCITY = 0;
    public static final double DEFAULT_ABILITY_DAMAGE = 0;
    public static final double DEFAULT_SPEED = 100;
    public static final double DEFAULT_VITALITY = 100;
    public static final double DEFAULT_MENDING = 100;
    public static final double DEFAULT_MONSTER_DAMAGE_PERCENT = 0;
    public static final double DEFAULT_MINING_FORTUNE = 0;
    public static final double DEFAULT_FORAGING_FORTUNE = 0;
    public static final double DEFAULT_FARMING_FORTUNE = 0;
    public static final double DEFAULT_TREASURE_CHANCE = 0;
    public static final double DEFAULT_BONUS_EXP_PERCENT = 0;
    public static final double DEFAULT_POTION_DURATION_PERCENT = 0;
    public static final double DEFAULT_PET_LUCK = 0;
    public static final double DEFAULT_PET_XP_GAIN_PERCENT = 0;
    public static final double DEFAULT_EXP_SHARE_RATE_PERCENT = 0;

    private double health = DEFAULT_HEALTH;
    private double currentHealth = DEFAULT_HEALTH;
    private double defense = DEFAULT_DEFENSE;
    private double strength = DEFAULT_STRENGTH;
    private double intelligence = DEFAULT_INTELLIGENCE;
    private double currentIntelligence = DEFAULT_INTELLIGENCE;
    private double critChance = DEFAULT_CRIT_CHANCE;
    private double critDamage = DEFAULT_CRIT_DAMAGE;
    private double bonusAttackSpeed = DEFAULT_BONUS_ATTACK_SPEED;
    private double trueDefense = DEFAULT_TRUE_DEFENSE;
    private double healthRegen = DEFAULT_HEALTH_REGEN;
    private double ferocity = DEFAULT_FEROCITY;
    private double abilityDamage = DEFAULT_ABILITY_DAMAGE;
    private double speed = DEFAULT_SPEED;
    private double vitality = DEFAULT_VITALITY;
    private double mending = DEFAULT_MENDING;
    private double monsterDamagePercent = DEFAULT_MONSTER_DAMAGE_PERCENT;
    private double miningFortune = DEFAULT_MINING_FORTUNE;
    private double foragingFortune = DEFAULT_FORAGING_FORTUNE;
    private double farmingFortune = DEFAULT_FARMING_FORTUNE;
    private double treasureChance = DEFAULT_TREASURE_CHANCE;
    private double bonusExpPercent = DEFAULT_BONUS_EXP_PERCENT;
    private double potionDurationPercent = DEFAULT_POTION_DURATION_PERCENT;
    private double petLuck = DEFAULT_PET_LUCK;
    private double petXpGainPercent = DEFAULT_PET_XP_GAIN_PERCENT;
    private double expShareRatePercent = DEFAULT_EXP_SHARE_RATE_PERCENT;

    public double getHealth() {
        return health;
    }

    public void setHealth(double health) {
        this.health = Math.max(0, health);
        if (currentHealth > this.health) currentHealth = this.health;
    }

    public double getCurrentHealth() {
        return currentHealth;
    }

    public void setCurrentHealth(double currentHealth) {
        this.currentHealth = Math.max(0, Math.min(currentHealth, health));
    }

    public void setCurrentHealthWithCap(double value, double cap) {
        this.currentHealth = Math.max(0, Math.min(value, cap));
    }

    public double getDefense() {
        return defense;
    }

    public void setDefense(double defense) {
        this.defense = Math.max(0, defense);
    }

    public double getStrength() {
        return strength;
    }

    public void setStrength(double strength) {
        this.strength = Math.max(0, strength);
    }

    public double getIntelligence() {
        return intelligence;
    }

    public void setIntelligence(double intelligence) {
        this.intelligence = Math.max(0, intelligence);
        if (currentIntelligence > this.intelligence) currentIntelligence = this.intelligence;
    }

    public double getCurrentIntelligence() {
        return currentIntelligence;
    }

    public void setCurrentIntelligence(double currentIntelligence) {
        this.currentIntelligence = Math.max(0, Math.min(currentIntelligence, intelligence));
    }

    public void setCurrentIntelligenceWithCap(double value, double cap) {
        this.currentIntelligence = Math.max(0, Math.min(value, cap));
    }

    public double getCritChance() {
        return critChance;
    }

    public void setCritChance(double critChance) {
        this.critChance = Math.max(0, Math.min(100, critChance));
    }

    public double getCritDamage() {
        return critDamage;
    }

    public void setCritDamage(double critDamage) {
        this.critDamage = critDamage;
    }

    public double getBonusAttackSpeed() {
        return bonusAttackSpeed;
    }

    public void setBonusAttackSpeed(double bonusAttackSpeed) {
        this.bonusAttackSpeed = Math.max(0, bonusAttackSpeed);
    }

    public double getTrueDefense() {
        return trueDefense;
    }

    public void setTrueDefense(double trueDefense) {
        this.trueDefense = Math.max(0, trueDefense);
    }

    public double getHealthRegen() {
        return healthRegen;
    }

    public void setHealthRegen(double healthRegen) {
        this.healthRegen = Math.max(0, healthRegen);
    }

    public double getFerocity() {
        return ferocity;
    }

    public void setFerocity(double ferocity) {
        this.ferocity = Math.max(0, ferocity);
    }

    public double getAbilityDamage() {
        return abilityDamage;
    }

    public void setAbilityDamage(double abilityDamage) {
        this.abilityDamage = Math.max(0, abilityDamage);
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = Math.max(0, speed);
    }

    public double getVitality() {
        return vitality;
    }

    public void setVitality(double vitality) {
        this.vitality = Math.max(0, vitality);
    }

    public double getMending() {
        return mending;
    }

    public void setMending(double mending) {
        this.mending = Math.max(0, mending);
    }

    public double getMonsterDamagePercent() {
        return monsterDamagePercent;
    }

    public void setMonsterDamagePercent(double monsterDamagePercent) {
        this.monsterDamagePercent = Math.max(0, monsterDamagePercent);
    }

    public double getMiningFortune() {
        return miningFortune;
    }

    public void setMiningFortune(double miningFortune) {
        this.miningFortune = Math.max(0, miningFortune);
    }

    public double getForagingFortune() {
        return foragingFortune;
    }

    public void setForagingFortune(double foragingFortune) {
        this.foragingFortune = Math.max(0, foragingFortune);
    }

    public double getFarmingFortune() {
        return farmingFortune;
    }

    public void setFarmingFortune(double farmingFortune) {
        this.farmingFortune = Math.max(0, farmingFortune);
    }

    public double getTreasureChance() {
        return treasureChance;
    }

    public void setTreasureChance(double treasureChance) {
        this.treasureChance = Math.max(0, treasureChance);
    }

    public double getBonusExpPercent() {
        return bonusExpPercent;
    }

    public void setBonusExpPercent(double bonusExpPercent) {
        this.bonusExpPercent = Math.max(0, bonusExpPercent);
    }

    public double getPotionDurationPercent() {
        return potionDurationPercent;
    }

    public void setPotionDurationPercent(double potionDurationPercent) {
        this.potionDurationPercent = Math.max(0, potionDurationPercent);
    }

    public double getPetLuck() {
        return petLuck;
    }

    public void setPetLuck(double petLuck) {
        this.petLuck = Math.max(0, petLuck);
    }

    public double getPetXpGainPercent() {
        return petXpGainPercent;
    }

    public void setPetXpGainPercent(double petXpGainPercent) {
        this.petXpGainPercent = Math.max(0, petXpGainPercent);
    }

    public double getExpShareRatePercent() {
        return expShareRatePercent;
    }

    public void setExpShareRatePercent(double expShareRatePercent) {
        this.expShareRatePercent = Math.max(0, expShareRatePercent);
    }

    public PlayerStatsData copy() {
        PlayerStatsData copy = new PlayerStatsData();
        copy.health = health;
        copy.currentHealth = currentHealth;
        copy.defense = defense;
        copy.strength = strength;
        copy.intelligence = intelligence;
        copy.currentIntelligence = currentIntelligence;
        copy.critChance = critChance;
        copy.critDamage = critDamage;
        copy.bonusAttackSpeed = bonusAttackSpeed;
        copy.trueDefense = trueDefense;
        copy.healthRegen = healthRegen;
        copy.ferocity = ferocity;
        copy.abilityDamage = abilityDamage;
        copy.speed = speed;
        copy.vitality = vitality;
        copy.mending = mending;
        copy.monsterDamagePercent = monsterDamagePercent;
        copy.miningFortune = miningFortune;
        copy.foragingFortune = foragingFortune;
        copy.farmingFortune = farmingFortune;
        copy.treasureChance = treasureChance;
        copy.bonusExpPercent = bonusExpPercent;
        copy.potionDurationPercent = potionDurationPercent;
        copy.petLuck = petLuck;
        copy.petXpGainPercent = petXpGainPercent;
        copy.expShareRatePercent = expShareRatePercent;
        return copy;
    }
}
