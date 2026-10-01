package maple.underground_state.stats;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerStatsRuntime {

    private final JavaPlugin plugin;
    private final PlayerStatsManager manager;
    private final CopyOnWriteArrayList<RegisteredExtension> extensions = new CopyOnWriteArrayList<>();
    private final Set<java.util.UUID> pendingVanillaDeaths = ConcurrentHashMap.newKeySet();
    private final Set<java.util.UUID> respawnRecoveryPending = ConcurrentHashMap.newKeySet();

    public PlayerStatsRuntime(@NotNull JavaPlugin plugin, @NotNull PlayerStatsManager manager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    public @NotNull JavaPlugin getPlugin() {
        return plugin;
    }

    public @NotNull PlayerStatsManager getManager() {
        return manager;
    }

    public void registerExtension(@NotNull Plugin owner,
                                  @NotNull String key,
                                  @NotNull PlayerStatsRuntimeExtension extension) {
        unregisterExtension(owner, key);
        extensions.add(new RegisteredExtension(owner, buildExtensionKey(owner, key), extension));
    }

    public void unregisterExtension(@NotNull Plugin owner, @NotNull String key) {
        String extensionKey = buildExtensionKey(owner, key);
        extensions.removeIf(registered -> registered.key.equals(extensionKey));
    }

    public void unregisterExtensions(@NotNull Plugin owner) {
        extensions.removeIf(registered -> registered.owner.equals(owner));
    }

    public maple.underground_state.professions.ProfessionBonuses getProfessionBonuses(Player player, maple.underground_state.skills.SkillType skill) {
        if(!maple.underground_state.professions.ProfessionMath.isProfession(skill))return maple.underground_state.professions.ProfessionBonuses.ZERO;
        var data = manager.get(player);
        double fortune = skill == maple.underground_state.skills.SkillType.FARMING ? data.getFarmingFortune() : data.getMiningFortune();
        var core = maple.underground_state.UndergroundState.getInstance();
        if (core != null && core.getProfessionService() != null) fortune += core.getProfessionService().collectionFortune(player, skill);
        var bonuses = new maple.underground_state.professions.ProfessionBonuses(fortune, 0, 0, 0, 0);
        for (PlayerStatsRuntimeExtension extension : extensions()) bonuses = extension.modifyProfessionBonuses(player, skill, bonuses);
        return bonuses.bounded();
    }

    public double getEffectiveMiningFortune(Player player) { return getProfessionBonuses(player, maple.underground_state.skills.SkillType.MINING).fortune(); }
    public double getEffectiveFarmingFortune(Player player) { return getProfessionBonuses(player, maple.underground_state.skills.SkillType.FARMING).fortune(); }
    public boolean canUseProfessionItem(Player player, org.bukkit.inventory.ItemStack item) {
        for (PlayerStatsRuntimeExtension extension : extensions()) if (!extension.canUseProfessionItem(player, item)) return false;
        return true;
    }

    public double getEffectiveMaxHealth(Player player) {
        if (player == null) return PlayerStatsData.DEFAULT_HEALTH;
        PlayerStatsData data = manager.get(player);
        double value = data.getHealth();
        for (PlayerStatsRuntimeExtension extension : extensions()) {
            value = extension.modifyMaxHealth(player, data, value);
        }
        return Math.max(1D, value);
    }

    public double getEffectiveDefense(Player player) {
        if (player == null) return PlayerStatsData.DEFAULT_DEFENSE;
        PlayerStatsData data = manager.get(player);
        double value = data.getDefense();
        for (PlayerStatsRuntimeExtension extension : extensions()) {
            value = extension.modifyDefense(player, data, value);
        }
        return Math.max(0D, value);
    }

    public double getEffectiveMaxIntelligence(Player player) {
        if (player == null) return PlayerStatsData.DEFAULT_INTELLIGENCE;
        PlayerStatsData data = manager.get(player);
        double value = data.getIntelligence();
        for (PlayerStatsRuntimeExtension extension : extensions()) {
            value = extension.modifyMaxIntelligence(player, data, value);
        }
        return Math.max(0D, value);
    }

    public double getEffectiveStrength(Player player) {
        if (player == null) return PlayerStatsData.DEFAULT_STRENGTH;
        PlayerStatsData data = manager.get(player);
        double value = manager.getEffectiveStrength(player);
        for (PlayerStatsRuntimeExtension extension : extensions()) {
            value = extension.modifyStrength(player, data, value);
        }
        return Math.max(0D, value);
    }

    public double getEffectiveBonusAttackSpeed(Player player) {
        if (player == null) return PlayerStatsData.DEFAULT_BONUS_ATTACK_SPEED;
        PlayerStatsData data = manager.get(player);
        double value = data.getBonusAttackSpeed();
        for (PlayerStatsRuntimeExtension extension : extensions()) {
            value = extension.modifyBonusAttackSpeed(player, data, value);
        }
        return Math.max(0D, Math.min(100D, value));
    }

    public double getEffectiveTrueDefense(Player player) {
        if (player == null) return PlayerStatsData.DEFAULT_TRUE_DEFENSE;
        PlayerStatsData data = manager.get(player);
        double value = data.getTrueDefense();
        for (PlayerStatsRuntimeExtension extension : extensions()) {
            value = extension.modifyTrueDefense(player, data, value);
        }
        return Math.max(0D, value);
    }

    public double getEffectiveFerocity(Player player) {
        if (player == null) return PlayerStatsData.DEFAULT_FEROCITY;
        PlayerStatsData data = manager.get(player);
        double value = data.getFerocity();
        for (PlayerStatsRuntimeExtension extension : extensions()) {
            value = extension.modifyFerocity(player, data, value);
        }
        return Math.max(0D, value);
    }

    public double getEffectiveHealthRegen(Player player) {
        if (player == null) return PlayerStatsData.DEFAULT_HEALTH_REGEN;
        PlayerStatsData data = manager.get(player);
        double value = data.getHealthRegen();
        for (PlayerStatsRuntimeExtension extension : extensions()) {
            value = extension.modifyHealthRegen(player, data, value);
        }
        return Math.max(0D, value);
    }

    public double getEffectiveVitalityForSelfHeal(Player player) {
        if (player == null) return PlayerStatsData.DEFAULT_VITALITY;
        PlayerStatsData data = manager.get(player);
        double value = data.getVitality();
        for (PlayerStatsRuntimeExtension extension : extensions()) {
            value = extension.modifyVitality(player, data, value);
        }
        return Math.max(0D, value);
    }

    public double getEffectiveOutgoingMending(Player player) {
        if (player == null) return PlayerStatsData.DEFAULT_MENDING;
        PlayerStatsData data = manager.get(player);
        double value = data.getMending();
        for (PlayerStatsRuntimeExtension extension : extensions()) {
            value = extension.modifyMending(player, data, value);
        }
        return Math.max(0D, value);
    }

    public double getEffectiveCritChance(Player player) {
        if (player == null) return PlayerStatsData.DEFAULT_CRIT_CHANCE;
        PlayerStatsData data = manager.get(player);
        double value = data.getCritChance();
        for (PlayerStatsRuntimeExtension extension : extensions()) {
            value = extension.modifyCritChance(player, data, value);
        }
        return Math.max(0D, value);
    }

    public double getEffectiveCritDamage(Player player) {
        if (player == null) return PlayerStatsData.DEFAULT_CRIT_DAMAGE;
        PlayerStatsData data = manager.get(player);
        double value = data.getCritDamage();
        for (PlayerStatsRuntimeExtension extension : extensions()) {
            value = extension.modifyCritDamage(player, data, value);
        }
        return Math.max(0D, value);
    }

    public double applyOutgoingDamageModifiers(@NotNull Player player,
                                               @NotNull EntityDamageByEntityEvent event,
                                               double baseDamage) {
        PlayerStatsData data = manager.get(player);
        double value = baseDamage;
        for (PlayerStatsRuntimeExtension extension : extensions()) {
            value = extension.modifyOutgoingDamage(player, data, value, event, this);
        }
        return value;
    }

    public double getExtraHealthCap(Player player) {
        if (player == null) return 0D;
        double effectiveMaxHealth = getEffectiveMaxHealth(player);
        return getExtraHealthCap(player, manager.get(player), effectiveMaxHealth);
    }

    public double getEffectiveHealthCap(Player player) {
        if (player == null) return PlayerStatsData.DEFAULT_HEALTH;
        double effectiveMaxHealth = getEffectiveMaxHealth(player);
        return getEffectiveHealthCap(player, effectiveMaxHealth);
    }

    public double getEffectiveHealthCap(Player player, double effectiveMaxHealth) {
        if (player == null) return Math.max(1D, effectiveMaxHealth);
        return Math.max(1D, effectiveMaxHealth + getExtraHealthCap(player, manager.get(player), effectiveMaxHealth));
    }

    public double getBaseEffectiveStrength(Player player) {
        return player == null ? 0D : manager.getEffectiveStrength(player);
    }

    public double getCurrentMana(Player player) {
        if (player == null) return 0D;
        PlayerStatsData data = manager.get(player);
        double maxInt = getEffectiveMaxIntelligence(player);
        if (data.getCurrentIntelligence() > maxInt) {
            setCurrentIntelligence(player, maxInt, maxInt);
        }
        return Math.max(0D, Math.min(data.getCurrentIntelligence(), maxInt));
    }

    public boolean hasEnoughMana(Player player, double amount) {
        return getCurrentMana(player) + 1.0E-9D >= Math.max(0D, amount);
    }

    public boolean trySpendMana(Player player, double amount) {
        if (player == null) return false;
        double cost = Math.max(0D, amount);
        double maxInt = getEffectiveMaxIntelligence(player);
        PlayerStatsData data = manager.get(player);
        double current = Math.max(0D, Math.min(data.getCurrentIntelligence(), maxInt));
        if (current + 1.0E-9D < cost) {
            return false;
        }
        setCurrentIntelligence(player, current - cost, maxInt);
        return true;
    }

    public double restoreMana(Player player, double amount) {
        if (player == null) return 0D;
        double gain = Math.max(0D, amount);
        if (gain <= 0D) return 0D;
        double maxInt = getEffectiveMaxIntelligence(player);
        PlayerStatsData data = manager.get(player);
        double current = Math.max(0D, Math.min(data.getCurrentIntelligence(), maxInt));
        double newValue = Math.min(maxInt, current + gain);
        setCurrentIntelligence(player, newValue, maxInt);
        return newValue - current;
    }

    public double setCurrentHealth(Player player, double requestedValue) {
        if (player == null) return 0D;
        double effectiveMaxHealth = getEffectiveMaxHealth(player);
        double effectiveCap = getEffectiveHealthCap(player, effectiveMaxHealth);
        return setCurrentHealth(player, requestedValue, effectiveMaxHealth, effectiveCap, true);
    }

    public double heal(Player player, double amount) {
        if (player == null) return 0D;
        double healAmount = Math.max(0D, amount);
        if (healAmount <= 0D) return 0D;
        PlayerStatsData data = manager.get(player);
        return setCurrentHealth(player, data.getCurrentHealth() + healAmount);
    }

    public double damage(Player player, double amount) {
        if (player == null) return 0D;
        double damageAmount = Math.max(0D, amount);
        if (damageAmount <= 0D) {
            return manager.get(player).getCurrentHealth();
        }
        PlayerStatsData data = manager.get(player);
        double previousHealth = data.getCurrentHealth();
        double effectiveMaxHealth = getEffectiveMaxHealth(player);
        double effectiveCap = getEffectiveHealthCap(player, effectiveMaxHealth);
        double newHealth = Math.max(0D, previousHealth - damageAmount);
        setCurrentHealth(player, newHealth, effectiveMaxHealth, effectiveCap, false);
        if (newHealth <= 0D && handleLethalDamage(player, null, previousHealth, damageAmount, effectiveMaxHealth, effectiveCap)) {
            syncVirtualHealthBar(player);
            return 0D;
        }
        syncVirtualHealthBar(player);
        return newHealth;
    }

    public double restoreFullHealth(Player player) {
        if (player == null) return 0D;
        double effectiveMaxHealth = getEffectiveMaxHealth(player);
        double effectiveCap = getEffectiveHealthCap(player, effectiveMaxHealth);
        return setCurrentHealth(player, effectiveCap, effectiveMaxHealth, effectiveCap, true);
    }

    public void applySatiatedRegenToVirtualHealth(Player player, double vanillaHealAmount) {
        if (player == null || vanillaHealAmount <= 0D) return;
        double effectiveMaxHealth = getEffectiveMaxHealth(player);
        double extraHealthCap = getExtraHealthCap(player);
        double displayMax = Math.max(1D, effectiveMaxHealth + extraHealthCap);
        double barMax = computeVirtualHealthBarMax(displayMax);
        if (barMax <= 0D) return;
        double deltaVirtual = vanillaHealAmount * (displayMax / barMax);
        PlayerStatsData data = manager.get(player);
        setCurrentHealth(player,
                Math.min(displayMax, data.getCurrentHealth() + deltaVirtual),
                effectiveMaxHealth,
                displayMax,
                true);
    }

    public void syncVirtualHealthBar(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        PlayerStatsData data = manager.get(player);
        double effectiveMaxHealth = getEffectiveMaxHealth(player);
        double extraHealthCap = getExtraHealthCap(player, data, effectiveMaxHealth);
        double displayMax = Math.max(1D, effectiveMaxHealth + extraHealthCap);
        double barMax = computeVirtualHealthBarMax(displayMax);

        for (PlayerStatsRuntimeExtension extension : extensions()) {
            if (extension.applyCustomVirtualHealthBar(player, this, data, effectiveMaxHealth, extraHealthCap, displayMax, barMax)) {
                return;
            }
        }

        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(barMax);
        }

        double current = data.getCurrentHealth();
        if (current <= 0D) {
            java.util.UUID playerId = player.getUniqueId();
            if (respawnRecoveryPending.contains(playerId)) {
                return;
            }
            if (!pendingVanillaDeaths.add(playerId)) {
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    pendingVanillaDeaths.remove(playerId);
                    return;
                }
                PlayerStatsData latest = manager.get(player);
                double latestCurrent = latest.getCurrentHealth();
                if (latestCurrent > 0D) {
                    pendingVanillaDeaths.remove(playerId);
                    return;
                }
                if (player.isDead()) {
                    pendingVanillaDeaths.remove(playerId);
                    return;
                }
                player.setHealth(0D);
            });
            return;
        }

        double ratio = displayMax > 0D ? current / displayMax : 0D;
        double display = Math.max(0D, Math.min(barMax, barMax * ratio));
        player.setHealth(Math.max(0.0001D, Math.min(barMax, display)));
    }

    public boolean shouldCancelIncomingDamage(@NotNull Player player, @NotNull EntityDamageEvent event) {
        for (PlayerStatsRuntimeExtension extension : extensions()) {
            if (extension.shouldCancelIncomingDamage(player, event, this)) {
                return true;
            }
        }
        return false;
    }

    public boolean handleLethalDamage(@NotNull Player player,
                                      EntityDamageEvent event,
                                      double previousHealth,
                                      double dealtDamage,
                                      double effectiveMaxHealth,
                                      double effectiveCap) {
        for (PlayerStatsRuntimeExtension extension : extensions()) {
            if (extension.handleLethalDamage(player, event, this, previousHealth, dealtDamage, effectiveMaxHealth, effectiveCap)) {
                return true;
            }
        }
        return false;
    }

    public boolean renderCustomActionBar(@NotNull Player player,
                                         @NotNull PlayerStatsData data,
                                         double effectiveMaxHealth,
                                         double effectiveDefense,
                                         double effectiveMaxIntelligence) {
        for (PlayerStatsRuntimeExtension extension : extensions()) {
            if (extension.renderCustomActionBar(player, this, data, effectiveMaxHealth, effectiveDefense, effectiveMaxIntelligence)) {
                return true;
            }
        }
        return false;
    }

    public boolean handleRespawn(@NotNull Player player) {
        for (PlayerStatsRuntimeExtension extension : extensions()) {
            if (extension.handleRespawn(player, this)) {
                return true;
            }
        }
        return false;
    }

    public boolean shouldSkipHealthRegen(@NotNull Player player, @NotNull PlayerStatsData data) {
        for (PlayerStatsRuntimeExtension extension : extensions()) {
            if (extension.shouldSkipHealthRegen(player, this, data)) {
                return true;
            }
        }
        return false;
    }

    double setCurrentHealth(Player player,
                            double requestedValue,
                            double effectiveMaxHealth,
                            double effectiveCap,
                            boolean sync) {
        if (player == null) return 0D;
        PlayerStatsData data = manager.get(player);
        double previousHealth = data.getCurrentHealth();
        double clamped = Math.max(0D, Math.min(requestedValue, effectiveCap));
        if (clamped > 0D) {
            pendingVanillaDeaths.remove(player.getUniqueId());
        }
        data.setCurrentHealthWithCap(clamped, effectiveCap);
        if (Math.abs(previousHealth - clamped) > 1.0E-9D) {
            for (PlayerStatsRuntimeExtension extension : extensions()) {
                extension.onHealthChanged(player, this, previousHealth, clamped, effectiveMaxHealth, effectiveCap);
            }
        }
        if (sync) {
            syncVirtualHealthBar(player);
        }
        return clamped;
    }

    public void clearPendingVanillaDeath(Player player) {
        if (player == null) return;
        pendingVanillaDeaths.remove(player.getUniqueId());
    }

    public void markRespawnRecovery(Player player) {
        if (player == null) return;
        respawnRecoveryPending.add(player.getUniqueId());
    }

    public void clearRespawnRecovery(Player player) {
        if (player == null) return;
        respawnRecoveryPending.remove(player.getUniqueId());
    }

    double setCurrentIntelligence(Player player, double requestedValue, double maxIntelligence) {
        if (player == null) return 0D;
        PlayerStatsData data = manager.get(player);
        double clamped = Math.max(0D, Math.min(requestedValue, maxIntelligence));
        data.setCurrentIntelligenceWithCap(clamped, maxIntelligence);
        return clamped;
    }

    public static double computeVirtualHealthBarMax(double maxHealth) {
        if (maxHealth <= 1000D) {
            return 20D;
        }
        double t = Math.min(1D, (maxHealth - 1000D) / 1000D);
        return 20D + 20D * t;
    }

    private double getExtraHealthCap(@NotNull Player player,
                                     @NotNull PlayerStatsData data,
                                     double effectiveMaxHealth) {
        double extra = 0D;
        for (PlayerStatsRuntimeExtension extension : extensions()) {
            extra += Math.max(0D, extension.getExtraHealthCap(player, data, effectiveMaxHealth, this));
        }
        return extra;
    }

    private List<PlayerStatsRuntimeExtension> extensions() {
        List<PlayerStatsRuntimeExtension> out = new ArrayList<>(extensions.size());
        for (RegisteredExtension registered : extensions) {
            out.add(registered.extension);
        }
        return out;
    }

    private static @NotNull String buildExtensionKey(@NotNull Plugin owner, @NotNull String key) {
        String normalized = key.isBlank() ? "default" : key.trim().toLowerCase(Locale.ROOT);
        return owner.getName().toLowerCase(Locale.ROOT) + ":" + normalized;
    }

    private record RegisteredExtension(@NotNull Plugin owner,
                                       @NotNull String key,
                                       @NotNull PlayerStatsRuntimeExtension extension) {
    }
}
