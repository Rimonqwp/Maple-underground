package maple.underground_state.stats;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerStatsDisplayTask extends BukkitRunnable {

    private static final double MANA_REGEN_PERCENT_PER_SECOND = 0.02D;
    private static final double HEALTH_EPSILON = 1.0E-6D;

    private final JavaPlugin plugin;
    private final PlayerStatsRuntime runtime;
    private final Map<UUID, Double> lastHealthCapByPlayer = new ConcurrentHashMap<>();

    public PlayerStatsDisplayTask(JavaPlugin plugin, PlayerStatsRuntime runtime) {
        this.plugin = plugin;
        this.runtime = runtime;
    }

    @Override
    public void run() {
        if (!plugin.isEnabled()) return;

        Set<UUID> onlinePlayers = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline() || player.isDead()) continue;
            onlinePlayers.add(player.getUniqueId());

            PlayerStatsData data = runtime.getManager().get(player);
            double maxHealth = runtime.getEffectiveMaxHealth(player);
            double healthCap = runtime.getEffectiveHealthCap(player, maxHealth);
            Double previousHealthCap = lastHealthCapByPlayer.put(player.getUniqueId(), healthCap);
            double currentHealth = data.getCurrentHealth();
            if (previousHealthCap != null && healthCap > previousHealthCap + HEALTH_EPSILON
                    && currentHealth >= previousHealthCap - HEALTH_EPSILON) {
                // If the player was full before max health increased, keep them full at the new cap.
                runtime.setCurrentHealth(player, healthCap, maxHealth, healthCap, false);
                currentHealth = healthCap;
            } else if (currentHealth > healthCap) {
                runtime.setCurrentHealth(player, currentHealth, maxHealth, healthCap, false);
            }

            double maxIntelligence = runtime.getEffectiveMaxIntelligence(player);
            if (data.getCurrentIntelligence() > maxIntelligence) {
                runtime.setCurrentIntelligence(player, data.getCurrentIntelligence(), maxIntelligence);
            }

            double currentMana = data.getCurrentIntelligence();
            if (currentMana < maxIntelligence && maxIntelligence > 0D) {
                double regen = Math.min(maxIntelligence * MANA_REGEN_PERCENT_PER_SECOND, maxIntelligence - currentMana);
                runtime.setCurrentIntelligence(player, currentMana + regen, maxIntelligence);
            }

            double defense = runtime.getEffectiveDefense(player);
            if (!runtime.renderCustomActionBar(player, data, maxHealth, defense, maxIntelligence)) {
                Component line = buildActionBarLine(player, data, maxHealth, defense, maxIntelligence);
                player.sendActionBar(line);
            }

            runtime.syncVirtualHealthBar(player);
        }
        lastHealthCapByPlayer.keySet().retainAll(onlinePlayers);
    }

    private static String formatNum(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.US, "%.1f", value);
    }

    private static String formatCurrentMax(double current, double max) {
        return formatNum(current) + "/" + formatNum(max);
    }

    private static Component buildActionBarLine(Player player,
                                                PlayerStatsData data,
                                                double maxHealth,
                                                double defense,
                                                double maxIntelligence) {
        if (shouldUsePlainTextHud(player)) {
            return Component.text("HP ", NamedTextColor.RED)
                    .append(Component.text(formatCurrentMax(data.getCurrentHealth(), maxHealth), NamedTextColor.WHITE))
                    .append(Component.text("   DEF ", NamedTextColor.GREEN))
                    .append(Component.text(formatNum(defense), NamedTextColor.WHITE))
                    .append(Component.text("   INT ", NamedTextColor.AQUA))
                    .append(Component.text(formatCurrentMax(data.getCurrentIntelligence(), maxIntelligence), NamedTextColor.WHITE))
                    .decoration(TextDecoration.BOLD, false);
        }

        return Component.text("❤ ", NamedTextColor.RED)
                .append(Component.text(formatCurrentMax(data.getCurrentHealth(), maxHealth), NamedTextColor.WHITE))
                .append(Component.text("   ", NamedTextColor.DARK_GRAY))
                .append(Component.text("❈ ", NamedTextColor.GREEN))
                .append(Component.text(formatNum(defense), NamedTextColor.WHITE))
                .append(Component.text("   ", NamedTextColor.DARK_GRAY))
                .append(Component.text("✎ ", NamedTextColor.AQUA))
                .append(Component.text(formatCurrentMax(data.getCurrentIntelligence(), maxIntelligence), NamedTextColor.WHITE))
                .decoration(TextDecoration.BOLD, false);
    }

    private static boolean shouldUsePlainTextHud(Player player) {
        return player != null && player.getName() != null && player.getName().startsWith(".");
    }
}
