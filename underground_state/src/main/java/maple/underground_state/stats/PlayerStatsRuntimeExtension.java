package maple.underground_state.stats;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PlayerStatsRuntimeExtension {

    default double modifyMaxHealth(@NotNull Player player, @NotNull PlayerStatsData data, double base) {
        return base;
    }

    default double modifyDefense(@NotNull Player player, @NotNull PlayerStatsData data, double base) {
        return base;
    }

    default double modifyMaxIntelligence(@NotNull Player player, @NotNull PlayerStatsData data, double base) {
        return base;
    }

    default double modifyStrength(@NotNull Player player, @NotNull PlayerStatsData data, double base) {
        return base;
    }

    default double modifyBonusAttackSpeed(@NotNull Player player, @NotNull PlayerStatsData data, double base) {
        return base;
    }

    default double modifyTrueDefense(@NotNull Player player, @NotNull PlayerStatsData data, double base) {
        return base;
    }

    default double modifyFerocity(@NotNull Player player, @NotNull PlayerStatsData data, double base) {
        return base;
    }

    default double modifyHealthRegen(@NotNull Player player, @NotNull PlayerStatsData data, double base) {
        return base;
    }

    default double modifyVitality(@NotNull Player player, @NotNull PlayerStatsData data, double base) {
        return base;
    }

    default double modifyMending(@NotNull Player player, @NotNull PlayerStatsData data, double base) {
        return base;
    }

    default double modifyCritChance(@NotNull Player player, @NotNull PlayerStatsData data, double base) {
        return base;
    }

    default double modifyCritDamage(@NotNull Player player, @NotNull PlayerStatsData data, double base) {
        return base;
    }

    default double modifyOutgoingDamage(@NotNull Player player,
                                        @NotNull PlayerStatsData data,
                                        double base,
                                        @NotNull EntityDamageByEntityEvent event,
                                        @NotNull PlayerStatsRuntime runtime) {
        return base;
    }

    default double getExtraHealthCap(@NotNull Player player,
                                     @NotNull PlayerStatsData data,
                                     double effectiveMaxHealth,
                                     @NotNull PlayerStatsRuntime runtime) {
        return 0D;
    }

    default boolean shouldCancelIncomingDamage(@NotNull Player player,
                                               @NotNull EntityDamageEvent event,
                                               @NotNull PlayerStatsRuntime runtime) {
        return false;
    }

    default boolean handleLethalDamage(@NotNull Player player,
                                       @Nullable EntityDamageEvent event,
                                       @NotNull PlayerStatsRuntime runtime,
                                       double previousHealth,
                                       double dealtDamage,
                                       double effectiveMaxHealth,
                                       double effectiveCap) {
        return false;
    }

    default boolean renderCustomActionBar(@NotNull Player player,
                                          @NotNull PlayerStatsRuntime runtime,
                                          @NotNull PlayerStatsData data,
                                          double effectiveMaxHealth,
                                          double effectiveDefense,
                                          double effectiveMaxIntelligence) {
        return false;
    }

    default boolean applyCustomVirtualHealthBar(@NotNull Player player,
                                                @NotNull PlayerStatsRuntime runtime,
                                                @NotNull PlayerStatsData data,
                                                double effectiveMaxHealth,
                                                double extraHealthCap,
                                                double displayMax,
                                                double barMax) {
        return false;
    }

    default void onHealthChanged(@NotNull Player player,
                                 @NotNull PlayerStatsRuntime runtime,
                                 double previousHealth,
                                 double newHealth,
                                 double effectiveMaxHealth,
                                 double effectiveCap) {
    }

    default boolean handleRespawn(@NotNull Player player, @NotNull PlayerStatsRuntime runtime) {
        return false;
    }

    default boolean shouldSkipHealthRegen(@NotNull Player player,
                                          @NotNull PlayerStatsRuntime runtime,
                                          @NotNull PlayerStatsData data) {
        return false;
    }
}
