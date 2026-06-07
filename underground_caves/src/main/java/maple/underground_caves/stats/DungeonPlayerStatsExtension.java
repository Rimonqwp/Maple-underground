package maple.underground_caves.stats;

import maple.underground_caves.Test_dungeons;
import maple.underground_caves.dungeon.DungeonBlessingManager;
import maple.underground_state.stats.PlayerStatsData;
import maple.underground_state.stats.PlayerStatsRuntime;
import maple.underground_state.stats.PlayerStatsRuntimeExtension;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.List;
import java.util.UUID;

public final class DungeonPlayerStatsExtension implements PlayerStatsRuntimeExtension {

    private final Test_dungeons plugin;

    public DungeonPlayerStatsExtension(Test_dungeons plugin) {
        this.plugin = plugin;
    }

    @Override
    public double modifyMaxHealth(Player player, PlayerStatsData data, double base) {
        double value = base;
        value += plugin.getImmortalSkullManager() != null ? plugin.getImmortalSkullManager().getBonusHealth(player) : 0D;
        value += plugin.getSieveSetManager() != null ? plugin.getSieveSetManager().getBonusHealth(player) : 0D;
        value += plugin.getArcherSetManager() != null ? plugin.getArcherSetManager().getBonusHealth(player) : 0D;
        value += plugin.getSkeletonSoldierSetManager() != null ? plugin.getSkeletonSoldierSetManager().getBonusHealth(player) : 0D;
        value += plugin.getSkeletonMasterSetManager() != null ? plugin.getSkeletonMasterSetManager().getBonusHealth(player) : 0D;
        value += plugin.getUndergroundCaveHelmetManager() != null ? plugin.getUndergroundCaveHelmetManager().getBonusHealth(player) : 0D;
        value += plugin.getStatsDoubleHelmetManager() != null ? plugin.getStatsDoubleHelmetManager().getBonusHealth(player) : 0D;
        value += plugin.getCaveSpecialHelmetManager() != null ? plugin.getCaveSpecialHelmetManager().getBonusHealth(player) : 0D;
        value += plugin.getSecondWindHelmetManager() != null ? plugin.getSecondWindHelmetManager().getBonusHealth(player) : 0D;
        value += plugin.getArrowDistanceHelmetManager() != null ? plugin.getArrowDistanceHelmetManager().getBonusHealth(player) : 0D;
        value += plugin.getCavernExplorerSetManager() != null ? plugin.getCavernExplorerSetManager().getBonusHealth(player) : 0D;
        value += plugin.getAdaptiveSetManager() != null ? plugin.getAdaptiveSetManager().getBonusHealth(player) : 0D;
        value += plugin.getBloodSetManager() != null ? plugin.getBloodSetManager().getBonusHealth(player) : 0D;
        value += plugin.getSublimationGearManager() != null ? plugin.getSublimationGearManager().getBonusHealth(player) : 0D;
        return plugin.applyDungeonBlessing(player, DungeonBlessingManager.BlessingStat.HEALTH, value);
    }

    @Override
    public double modifyDefense(Player player, PlayerStatsData data, double base) {
        double value = base;
        value += plugin.getGearManager() != null ? plugin.getGearManager().getEquipmentDefense(player) : 0D;
        value += plugin.getImmortalSkullManager() != null ? plugin.getImmortalSkullManager().getBonusDefense(player) : 0D;
        value += plugin.getArcherSetManager() != null ? plugin.getArcherSetManager().getBonusDefense(player) : 0D;
        value += plugin.getSkeletonSoldierSetManager() != null ? plugin.getSkeletonSoldierSetManager().getBonusDefense(player) : 0D;
        value += plugin.getSkeletonMasterSetManager() != null ? plugin.getSkeletonMasterSetManager().getBonusDefense(player) : 0D;
        value += plugin.getUndergroundCaveHelmetManager() != null ? plugin.getUndergroundCaveHelmetManager().getBonusDefense(player) : 0D;
        value += plugin.getStatsDoubleHelmetManager() != null ? plugin.getStatsDoubleHelmetManager().getBonusDefense(player) : 0D;
        value += plugin.getSecondWindHelmetManager() != null ? plugin.getSecondWindHelmetManager().getBonusDefense(player) : 0D;
        value += plugin.getArrowDistanceHelmetManager() != null ? plugin.getArrowDistanceHelmetManager().getBonusDefense(player) : 0D;
        value += plugin.getCavernExplorerSetManager() != null ? plugin.getCavernExplorerSetManager().getBonusDefense(player) : 0D;
        value += plugin.getAdaptiveSetManager() != null ? plugin.getAdaptiveSetManager().getBonusDefense(player) : 0D;
        value += plugin.getBloodSetManager() != null ? plugin.getBloodSetManager().getBonusDefense(player) : 0D;
        value += plugin.getSublimationGearManager() != null ? plugin.getSublimationGearManager().getBonusDefense(player) : 0D;
        return plugin.applyDungeonBlessing(player, DungeonBlessingManager.BlessingStat.DEFENSE, value);
    }

    @Override
    public double modifyMaxIntelligence(Player player, PlayerStatsData data, double base) {
        double value = base;
        value += plugin.getImmortalSkullManager() != null ? plugin.getImmortalSkullManager().getBonusIntelligence(player) : 0D;
        value += plugin.getCaveSpecialHelmetManager() != null ? plugin.getCaveSpecialHelmetManager().getBonusIntelligence(player) : 0D;
        value += plugin.getSecondWindHelmetManager() != null ? plugin.getSecondWindHelmetManager().getBonusIntelligence(player) : 0D;
        value += plugin.getCavernExplorerSetManager() != null ? plugin.getCavernExplorerSetManager().getBonusIntelligence(player) : 0D;
        value += plugin.getAdaptiveSetManager() != null ? plugin.getAdaptiveSetManager().getBonusIntelligence(player) : 0D;
        value += plugin.getBloodSetManager() != null ? plugin.getBloodSetManager().getBonusIntelligence(player) : 0D;

        if (plugin.isDungeonWorld(player.getWorld().getName()) && plugin.isDungeonRunStarted(player.getWorld().getName())) {
            if (plugin.getDungeonClassIndex(player) == 1) {
                int mageLevel = Math.max(0, plugin.getDungeonClassLevel(player));
                value += mageLevel * 5D;
            }
            try {
                List<UUID> members = plugin.getPartyManager() != null ? plugin.getPartyManager().getPartyMembers(player) : null;
                if (members == null || members.isEmpty()) {
                    members = List.of(player.getUniqueId());
                }
                int mageCount = 0;
                for (UUID id : members) {
                    Player member = org.bukkit.Bukkit.getPlayer(id);
                    if (member == null || !member.isOnline()) continue;
                    if (!member.getWorld().equals(player.getWorld())) continue;
                    if (plugin.getDungeonClassIndex(member) == 1) {
                        mageCount++;
                    }
                }
                if (mageCount > 0 && plugin.getDungeonClassIndex(player) == 1) {
                    value += mageCount == 1 ? 500D : 250D;
                }
            } catch (Throwable ignored) {
            }
        }

        return plugin.applyDungeonBlessing(player, DungeonBlessingManager.BlessingStat.INTELLIGENCE, value);
    }

    @Override
    public double modifyStrength(Player player, PlayerStatsData data, double base) {
        double value = base;
        value += plugin.getSieveSetManager() != null ? plugin.getSieveSetManager().getBonusStrength(player) : 0D;
        value += plugin.getUndergroundCaveHelmetManager() != null ? plugin.getUndergroundCaveHelmetManager().getBonusStrength(player) : 0D;
        value += plugin.getStatsDoubleHelmetManager() != null ? plugin.getStatsDoubleHelmetManager().getBonusStrength(player) : 0D;
        value += plugin.getCavernExplorerSetManager() != null ? plugin.getCavernExplorerSetManager().getBonusStrength(player) : 0D;
        value += plugin.getAdaptiveSetManager() != null ? plugin.getAdaptiveSetManager().getBonusStrength(player) : 0D;
        value += plugin.getBloodSetManager() != null ? plugin.getBloodSetManager().getBonusStrength(player) : 0D;
        return plugin.applyDungeonBlessing(player, DungeonBlessingManager.BlessingStat.STRENGTH, value);
    }

    @Override
    public double modifyHealthRegen(Player player, PlayerStatsData data, double base) {
        return plugin.applyDungeonBlessing(player, DungeonBlessingManager.BlessingStat.HEALTH_REGEN, base);
    }

    @Override
    public double modifyVitality(Player player, PlayerStatsData data, double base) {
        double value = base;
        value += plugin.getCaveSpecialHelmetManager() != null ? plugin.getCaveSpecialHelmetManager().getVitalityInDungeon(player) : 0D;
        return value;
    }

    @Override
    public double modifyMending(Player player, PlayerStatsData data, double base) {
        double value = base;
        value += plugin.getCaveSpecialHelmetManager() != null ? plugin.getCaveSpecialHelmetManager().getMendingInDungeon(player) : 0D;
        value += plugin.getAdaptiveSetManager() != null ? plugin.getAdaptiveSetManager().getBonusMending(player) : 0D;
        return value;
    }

    @Override
    public double modifyCritChance(Player player, PlayerStatsData data, double base) {
        double value = base;
        value += plugin.getSkeletonSoldierSetManager() != null ? plugin.getSkeletonSoldierSetManager().getBonusCritChance(player) : 0D;
        value += plugin.getSkeletonMasterSetManager() != null ? plugin.getSkeletonMasterSetManager().getBonusCritChance(player) : 0D;
        value += plugin.getAdaptiveSetManager() != null ? plugin.getAdaptiveSetManager().getBonusCritChance(player) : 0D;
        value += plugin.getBloodSetManager() != null ? plugin.getBloodSetManager().getBonusCritChance(player) : 0D;
        return value;
    }

    @Override
    public double modifyCritDamage(Player player, PlayerStatsData data, double base) {
        double value = base;
        value += plugin.getArcherSetManager() != null ? plugin.getArcherSetManager().getBonusCritDamage(player) : 0D;
        value += plugin.getSkeletonSoldierSetManager() != null ? plugin.getSkeletonSoldierSetManager().getBonusCritDamage(player) : 0D;
        value += plugin.getSkeletonMasterSetManager() != null ? plugin.getSkeletonMasterSetManager().getBonusCritDamage(player) : 0D;
        value += plugin.getAdaptiveSetManager() != null ? plugin.getAdaptiveSetManager().getBonusCritDamage(player) : 0D;
        value += plugin.getBloodSetManager() != null ? plugin.getBloodSetManager().getBonusCritDamage(player) : 0D;
        return plugin.applyDungeonBlessing(player, DungeonBlessingManager.BlessingStat.CRIT_DAMAGE, value);
    }

    @Override
    public double modifyOutgoingDamage(Player player,
                                       PlayerStatsData data,
                                       double base,
                                       EntityDamageByEntityEvent event,
                                       PlayerStatsRuntime runtime) {
        return plugin.applyDungeonBlessing(player, DungeonBlessingManager.BlessingStat.DAMAGE, base);
    }

    @Override
    public double getExtraHealthCap(Player player, PlayerStatsData data, double effectiveMaxHealth, PlayerStatsRuntime runtime) {
        return plugin.getWishShieldExtra(player.getUniqueId());
    }

    @Override
    public boolean shouldCancelIncomingDamage(Player player, EntityDamageEvent event, PlayerStatsRuntime runtime) {
        var death = plugin.getDungeonDeathListener();
        if (death != null && (death.isDungeonGhost(player.getUniqueId())
                || death.isResurrectionInProgress(player.getUniqueId()))) {
            return true;
        }
        return plugin.isDungeonWorld(player.getWorld().getName()) && !plugin.isDungeonRunStarted(player.getWorld().getName());
    }

    @Override
    public boolean handleLethalDamage(Player player,
                                      EntityDamageEvent event,
                                      PlayerStatsRuntime runtime,
                                      double previousHealth,
                                      double dealtDamage,
                                      double effectiveMaxHealth,
                                      double effectiveCap) {
        if (!plugin.isDungeonWorld(player.getWorld().getName())) {
            return false;
        }
        plugin.getLogger().info(String.format(
                "[DungeonLethal] player=%s world=%s prev=%.2f dealt=%.2f max=%.2f cap=%.2f cause=%s",
                player.getName(),
                player.getWorld().getName(),
                previousHealth,
                dealtDamage,
                effectiveMaxHealth,
                effectiveCap,
                event != null ? event.getCause().name() : "DIRECT"));
        plugin.handleDungeonDeath(player);
        return true;
    }

    @Override
    public boolean renderCustomActionBar(Player player,
                                         PlayerStatsRuntime runtime,
                                         PlayerStatsData data,
                                         double effectiveMaxHealth,
                                         double effectiveDefense,
                                         double effectiveMaxIntelligence) {
        var death = plugin.getDungeonDeathListener();
        if (death == null) return false;
        if (!plugin.isDungeonWorld(player.getWorld().getName())) return false;
        if (!death.isDungeonGhost(player.getUniqueId())) return false;
        if (death.isDungeonFailCountdownActive(player.getWorld().getName())) return false;
        death.refreshGhostHotbar(player);
        return true;
    }

    @Override
    public boolean applyCustomVirtualHealthBar(Player player,
                                               PlayerStatsRuntime runtime,
                                               PlayerStatsData data,
                                               double effectiveMaxHealth,
                                               double extraHealthCap,
                                               double displayMax,
                                               double barMax) {
        var death = plugin.getDungeonDeathListener();
        if (death != null && death.isDungeonGhost(player.getUniqueId())) {
            AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) {
                attr.setBaseValue(20D);
            }
            player.setHealth(1D);
            player.setInvulnerable(true);
            return true;
        }
        if (plugin.isDungeonWorld(player.getWorld().getName())) {
            player.setInvulnerable(false);
        }
        return false;
    }

    @Override
    public void onHealthChanged(Player player,
                                PlayerStatsRuntime runtime,
                                double previousHealth,
                                double newHealth,
                                double effectiveMaxHealth,
                                double effectiveCap) {
        plugin.consumeWishShieldIfNeeded(player, newHealth);
    }

    @Override
    public boolean handleRespawn(Player player, PlayerStatsRuntime runtime) {
        if (!plugin.isDungeonWorld(player.getWorld().getName())) {
            return false;
        }
        var death = plugin.getDungeonDeathListener();
        if (death != null && death.isDungeonGhost(player.getUniqueId())) {
            runtime.syncVirtualHealthBar(player);
            return true;
        }
        runtime.restoreFullHealth(player);
        return true;
    }

    @Override
    public boolean shouldSkipHealthRegen(Player player, PlayerStatsRuntime runtime, PlayerStatsData data) {
        return plugin.isPlayerDungeonGhost(player);
    }
}
