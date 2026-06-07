package maple.underground_item.stats;

import maple.underground_item.UndergroundItem;
import maple.underground_state.stats.PlayerStatsData;
import maple.underground_state.stats.PlayerStatsRuntimeExtension;
import org.bukkit.entity.Player;

public final class ItemPlayerStatsExtension implements PlayerStatsRuntimeExtension {

    private final UndergroundItem plugin;

    public ItemPlayerStatsExtension(UndergroundItem plugin) {
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
        return plugin.applyHealthBlessing(player, value);
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
        return plugin.applyDefenseBlessing(player, value);
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
        return plugin.applyIntelligenceBlessing(player, value);
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
        return value;
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
        return value;
    }
}
