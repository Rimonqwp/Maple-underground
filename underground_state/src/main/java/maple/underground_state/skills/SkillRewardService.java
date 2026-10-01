package maple.underground_state.skills;

import maple.underground_state.UndergroundState;
import maple.underground_state.stats.PlayerStatsData;
import maple.underground_state.stats.PlayerStatsManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.UUID;

public final class SkillRewardService {

    private final UndergroundState plugin;
    private final SkillProgressManager skillProgressManager;
    private final PlayerStatsManager playerStatsManager;

    public SkillRewardService(@NotNull UndergroundState plugin,
                              @NotNull SkillProgressManager skillProgressManager,
                              @NotNull PlayerStatsManager playerStatsManager) {
        this.plugin = plugin;
        this.skillProgressManager = skillProgressManager;
        this.playerStatsManager = playerStatsManager;
    }

    public long setSkillXp(UUID uuid, @NotNull SkillType skillType, long xp) {
        if (uuid == null) {
            return 0L;
        }
        ensureProfessionLoaded(uuid, skillType);
        long previousXp = skillProgressManager.getXp(uuid, skillType);
        long updatedXp = Math.max(0L, xp);
        skillProgressManager.setXp(uuid, skillType, updatedXp);
        applyRewardsForNewLevels(uuid, skillType, previousXp, updatedXp);
        return updatedXp;
    }

    public long addSkillXp(UUID uuid, @NotNull SkillType skillType, long delta) {
        if (uuid == null) {
            return 0L;
        }
        ensureProfessionLoaded(uuid, skillType);
        long previousXp = skillProgressManager.getXp(uuid, skillType);
        long updatedXp = Math.max(0L, previousXp + delta);
        skillProgressManager.setXp(uuid, skillType, updatedXp);
        applyRewardsForNewLevels(uuid, skillType, previousXp, updatedXp);
        return updatedXp;
    }

    private void ensureProfessionLoaded(UUID uuid, SkillType skill) {
        if (maple.underground_state.professions.ProfessionMath.isProfession(skill) && plugin != null && plugin.getProfessionService() != null) {
            var store = plugin.getProfessionService().store();
            if (!store.ready(uuid)) store.load(uuid);
            if (!store.ready(uuid)) throw new IllegalStateException("职业存档加载失败，未修改经验");
        }
    }
    private void applyRewardsForNewLevels(UUID uuid,
                                          @NotNull SkillType skillType,
                                          long previousXp,
                                          long updatedXp) {
        int previousLevel = SkillLevelTables.levelForXp(skillType, previousXp);
        int updatedLevel = SkillLevelTables.levelForXp(skillType, updatedXp);
        boolean ledgerChanged = false;
        if (maple.underground_state.professions.ProfessionMath.isProfession(skillType) && plugin != null && plugin.getProfessionService() != null) {
            var store = plugin.getProfessionService().store();
            if (!store.ready(uuid)) throw new IllegalStateException("职业存档尚未加载");
            String key = skillType.name() + ":highest_rewarded";
            previousLevel = Math.max(previousLevel, (int) store.get(uuid, key));
            ledgerChanged = store.get(uuid,key) != Math.max(previousLevel,updatedLevel);
            store.set(uuid, key, Math.max(previousLevel, updatedLevel));
        }
        if (updatedLevel <= previousLevel) {
            if(ledgerChanged)plugin.getProfessionService().store().save(uuid);
            return;
        }

        RewardBundle bundle = new RewardBundle();
        for (int level = previousLevel + 1; level <= updatedLevel; level++) {
            accumulateRewards(skillType, level, bundle);
        }
        applyBundle(uuid, bundle);
        if (maple.underground_state.professions.ProfessionMath.isProfession(skillType) && plugin != null && plugin.getProfessionService() != null) plugin.getProfessionService().store().save(uuid);
    }

    private void accumulateRewards(@NotNull SkillType skillType, int level, @NotNull RewardBundle bundle) {
        switch (skillType) {
            case COMBAT -> {
                CombatLevelDefinition definition = CombatLevelTable.definitionForLevel(level);
                bundle.coins += definition.coinReward();
                bundle.critChance += definition.critChanceReward();
                bundle.monsterDamagePercent += definition.extraDamageRewardPercent();
                bundle.mapleXp += definition.mapleXpReward();
            }
            case MINING -> {
                MiningLevelDefinition definition = MiningLevelTable.definitionForLevel(level);
                bundle.coins += definition.coinReward();
                bundle.defense += definition.defenseReward();
                bundle.miningFortune += definition.miningFortuneReward();
                bundle.mapleXp += definition.mapleXpReward();
            }
            case FORAGING -> {
                ForagingLevelDefinition definition = ForagingLevelTable.definitionForLevel(level);
                bundle.coins += definition.coinReward();
                bundle.strength += definition.strengthReward();
                bundle.foragingFortune += definition.foragingFortuneReward();
                bundle.mapleXp += definition.mapleXpReward();
            }
            case FARMING -> {
                FarmingLevelDefinition definition = FarmingLevelTable.definitionForLevel(level);
                bundle.coins += definition.coinReward();
                bundle.health += definition.healthReward();
                bundle.farmingFortune += definition.farmingFortuneReward();
                bundle.mapleXp += definition.mapleXpReward();
            }
            case FISHING -> {
                FishingLevelDefinition definition = FishingLevelTable.definitionForLevel(level);
                bundle.coins += definition.coinReward();
                bundle.health += definition.healthReward();
                bundle.treasureChance += definition.treasureChanceReward();
                bundle.mapleXp += definition.mapleXpReward();
            }
            case ENCHANTING -> {
                EnchantingLevelDefinition definition = EnchantingLevelTable.definitionForLevel(level);
                bundle.coins += definition.coinReward();
                bundle.intelligence += definition.intelligenceReward();
                bundle.abilityDamage += definition.abilityDamageReward();
                bundle.bonusExpPercent += definition.bonusExpReward();
                bundle.mapleXp += definition.mapleXpReward();
            }
            case ALCHEMY -> {
                AlchemyLevelDefinition definition = AlchemyLevelTable.definitionForLevel(level);
                bundle.coins += definition.coinReward();
                bundle.intelligence += definition.intelligenceReward();
                bundle.potionDurationPercent += definition.potionDurationReward();
                bundle.mapleXp += definition.mapleXpReward();
            }
            case TAMING -> {
                TamingLevelDefinition definition = TamingLevelTable.definitionForLevel(level);
                bundle.coins += definition.coinReward();
                bundle.petLuck += definition.petLuckReward();
                bundle.petXpGainPercent += definition.petXpGainReward();
                bundle.expShareRatePercent += definition.expShareRateReward();
                bundle.mapleXp += definition.mapleXpReward();
            }
            case EXPLORATION, CRAFTING, SOCIAL -> {
                CombatLevelDefinition definition = CombatLevelTable.definitionForLevel(level);
                bundle.coins += definition.coinReward();
                bundle.mapleXp += definition.mapleXpReward();
            }
        }
    }

    private void applyBundle(UUID uuid, @NotNull RewardBundle bundle) {
        if (bundle.mapleXp > 0L) {
            skillProgressManager.addMapleXp(uuid, bundle.mapleXp);
            skillProgressManager.save(uuid);
        }

        if (bundle.hasStatChanges()) {
            PlayerStatsData data = playerStatsManager.get(uuid).copy();
            double previousHealth = data.getHealth();
            double previousCurrentHealth = data.getCurrentHealth();
            double previousIntelligence = data.getIntelligence();
            double previousCurrentIntelligence = data.getCurrentIntelligence();

            data.setDefense(data.getDefense() + bundle.defense);
            data.setStrength(data.getStrength() + bundle.strength);
            data.setCritChance(data.getCritChance() + bundle.critChance);
            data.setAbilityDamage(data.getAbilityDamage() + bundle.abilityDamage);
            data.setMonsterDamagePercent(data.getMonsterDamagePercent() + bundle.monsterDamagePercent);
            data.setMiningFortune(data.getMiningFortune() + bundle.miningFortune);
            data.setForagingFortune(data.getForagingFortune() + bundle.foragingFortune);
            data.setFarmingFortune(data.getFarmingFortune() + bundle.farmingFortune);
            data.setTreasureChance(data.getTreasureChance() + bundle.treasureChance);
            data.setBonusExpPercent(data.getBonusExpPercent() + bundle.bonusExpPercent);
            data.setPotionDurationPercent(data.getPotionDurationPercent() + bundle.potionDurationPercent);
            data.setPetLuck(data.getPetLuck() + bundle.petLuck);
            data.setPetXpGainPercent(data.getPetXpGainPercent() + bundle.petXpGainPercent);
            data.setExpShareRatePercent(data.getExpShareRatePercent() + bundle.expShareRatePercent);

            if (bundle.health > 0D) {
                data.setHealth(previousHealth + bundle.health);
                if (Math.abs(previousCurrentHealth - previousHealth) < 1.0E-6) {
                    data.setCurrentHealth(data.getHealth());
                }
            }
            if (bundle.intelligence > 0D) {
                data.setIntelligence(previousIntelligence + bundle.intelligence);
                if (Math.abs(previousCurrentIntelligence - previousIntelligence) < 1.0E-6) {
                    data.setCurrentIntelligence(data.getIntelligence());
                }
            }

            playerStatsManager.set(uuid, data);
            playerStatsManager.save(uuid);
        }

        if (bundle.coins > 0L) {
            giveCoins(uuid, bundle.coins);
        }
    }

    private void giveCoins(UUID uuid, long amount) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        String playerName = offlinePlayer.getName();
        if (playerName == null || playerName.isBlank()) {
            plugin.getLogger().warning("Failed to give coins because player name is unavailable: " + uuid);
            return;
        }

        Runnable task = () -> {
            CommandSender console = Bukkit.getConsoleSender();
            String command = String.format(Locale.ROOT, "eco give %s %d", playerName, amount);
            Bukkit.dispatchCommand(console, command);
        };

        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private static final class RewardBundle {
        private long coins;
        private long mapleXp;
        private double health;
        private double defense;
        private double strength;
        private double intelligence;
        private double critChance;
        private double abilityDamage;
        private double monsterDamagePercent;
        private double miningFortune;
        private double foragingFortune;
        private double farmingFortune;
        private double treasureChance;
        private double bonusExpPercent;
        private double potionDurationPercent;
        private double petLuck;
        private double petXpGainPercent;
        private double expShareRatePercent;

        private boolean hasStatChanges() {
            return health > 0D
                    || defense > 0D
                    || strength > 0D
                    || intelligence > 0D
                    || critChance > 0D
                    || abilityDamage > 0D
                    || monsterDamagePercent > 0D
                    || miningFortune > 0D
                    || foragingFortune > 0D
                    || farmingFortune > 0D
                    || treasureChance > 0D
                    || bonusExpPercent > 0D
                    || potionDurationPercent > 0D
                    || petLuck > 0D
                    || petXpGainPercent > 0D
                    || expShareRatePercent > 0D;
        }
    }
}
