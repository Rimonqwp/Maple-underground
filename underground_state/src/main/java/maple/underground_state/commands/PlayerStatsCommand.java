package maple.underground_state.commands;

import maple.underground_state.stats.PlayerStatsData;
import maple.underground_state.stats.PlayerStatsFormulas;
import maple.underground_state.stats.PlayerStatsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public final class PlayerStatsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("get", "set");
    private static final List<String> STAT_KEYS = Arrays.asList(
            "health", "defense", "strength", "intelligence", "crit_chance", "crit_damage",
            "bonus_attack_speed", "true_defense", "health_regen", "mending", "ferocity",
            "ability_damage", "speed", "vitality", "monster_damage_percent",
            "mining_fortune", "foraging_fortune", "farming_fortune", "treasure_chance",
            "bonus_exp_percent", "potion_duration_percent", "pet_luck", "pet_xp_gain_percent",
            "exp_share_rate_percent"
    );

    private final PlayerStatsManager manager;

    public PlayerStatsCommand(PlayerStatsManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("get".equals(sub)) return runGet(sender, args);
        if ("set".equals(sub)) return runSet(sender, args);

        sendUsage(sender);
        return true;
    }

    private boolean runGet(CommandSender sender, String[] args) {
        UUID target = resolveTarget(sender, args.length > 1 ? args[1] : null);
        if (target == null) {
            sender.sendMessage(Component.text("找不到目标玩家。", NamedTextColor.RED));
            return true;
        }

        PlayerStatsData data = manager.get(target);
        String name = Bukkit.getOfflinePlayer(target).getName();
        if (name == null || name.isBlank()) name = target.toString();

        sender.sendMessage(Component.text("玩家 " + name + " 的基础状态：", NamedTextColor.GOLD));
        sender.sendMessage(line("Health", format(data.getCurrentHealth()) + "/" + format(data.getHealth())));
        sender.sendMessage(line("Defense", format(data.getDefense())));
        sender.sendMessage(line("Strength", format(data.getStrength()) + tempStrengthSuffix(target)));
        sender.sendMessage(line("Intelligence", format(data.getCurrentIntelligence()) + "/" + format(data.getIntelligence())));
        sender.sendMessage(line("Crit Chance", format(data.getCritChance()) + "%"));
        sender.sendMessage(line("Crit Damage", format(data.getCritDamage()) + "%"));
        sender.sendMessage(line("Bonus Attack Speed", format(data.getBonusAttackSpeed()) + "%"));
        sender.sendMessage(line("True Defense", format(data.getTrueDefense())));
        sender.sendMessage(line("Health Regen", format(data.getHealthRegen())));
        sender.sendMessage(line("Ferocity", format(data.getFerocity())));
        sender.sendMessage(line("Ability Damage", format(data.getAbilityDamage()) + "%"));
        sender.sendMessage(line("Speed", format(data.getSpeed())));
        sender.sendMessage(line("Vitality", format(data.getVitality())));
        sender.sendMessage(line("Mending", format(data.getMending())));
        sender.sendMessage(line("Monster Damage", format(data.getMonsterDamagePercent()) + "%"));
        sender.sendMessage(line("Mining Fortune", format(data.getMiningFortune())));
        sender.sendMessage(line("Foraging Fortune", format(data.getForagingFortune())));
        sender.sendMessage(line("Farming Fortune", format(data.getFarmingFortune())));
        sender.sendMessage(line("Treasure Chance", format(data.getTreasureChance()) + "%"));
        sender.sendMessage(line("Bonus Exp", format(data.getBonusExpPercent()) + "%"));
        sender.sendMessage(line("Potion Duration", format(data.getPotionDurationPercent()) + "%"));
        sender.sendMessage(line("Pet Luck", format(data.getPetLuck())));
        sender.sendMessage(line("Pet XP Gain", format(data.getPetXpGainPercent()) + "%"));
        sender.sendMessage(line("Exp Share Rate", format(data.getExpShareRatePercent()) + "%"));
        sender.sendMessage(Component.text("EHP: " + format(PlayerStatsFormulas.effectiveHealth(data.getHealth(), data.getDefense())), NamedTextColor.YELLOW));
        return true;
    }

    private boolean runSet(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sendUsage(sender);
            return true;
        }

        UUID target = resolveTarget(sender, args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("找不到目标玩家。", NamedTextColor.RED));
            return true;
        }

        PlayerStatsData data = manager.get(target).copy();
        int changed = 0;
        for (int i = 2; i + 1 < args.length; i += 2) {
            String stat = args[i].toLowerCase(Locale.ROOT);
            double value;
            try {
                value = Double.parseDouble(args[i + 1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("无效数值: " + args[i + 1], NamedTextColor.RED));
                continue;
            }
            if (applyStat(data, stat, value)) {
                changed++;
            } else {
                sender.sendMessage(Component.text("未知属性: " + stat, NamedTextColor.RED));
            }
        }

        if (changed <= 0) {
            sender.sendMessage(Component.text("没有任何属性被修改。", NamedTextColor.GRAY));
            return true;
        }

        manager.set(target, data);
        manager.save(target);
        String name = Bukkit.getOfflinePlayer(target).getName();
        if (name == null || name.isBlank()) name = target.toString();
        sender.sendMessage(Component.text("已更新 " + name + " 的 " + changed + " 项基础状态。", NamedTextColor.GREEN));
        return true;
    }

    private static Component line(String key, String value) {
        return Component.text(key + ": " + value, NamedTextColor.GRAY);
    }

    private String tempStrengthSuffix(UUID target) {
        double temp = manager.getTempStrengthBonus(target);
        return temp == 0 ? "" : " (基础 + 临时 " + format(temp) + ")";
    }

    private boolean applyStat(PlayerStatsData data, String stat, double value) {
        switch (stat) {
            case "health" -> data.setHealth(value);
            case "defense" -> data.setDefense(value);
            case "strength" -> data.setStrength(value);
            case "intelligence" -> data.setIntelligence(value);
            case "crit_chance" -> data.setCritChance(value);
            case "crit_damage" -> data.setCritDamage(value);
            case "bonus_attack_speed" -> data.setBonusAttackSpeed(value);
            case "true_defense" -> data.setTrueDefense(value);
            case "health_regen" -> data.setHealthRegen(value);
            case "mending" -> data.setMending(value);
            case "ferocity" -> data.setFerocity(value);
            case "ability_damage" -> data.setAbilityDamage(value);
            case "speed" -> data.setSpeed(value);
            case "vitality" -> data.setVitality(value);
            case "monster_damage_percent" -> data.setMonsterDamagePercent(value);
            case "mining_fortune" -> data.setMiningFortune(value);
            case "foraging_fortune" -> data.setForagingFortune(value);
            case "farming_fortune" -> data.setFarmingFortune(value);
            case "treasure_chance" -> data.setTreasureChance(value);
            case "bonus_exp_percent" -> data.setBonusExpPercent(value);
            case "potion_duration_percent" -> data.setPotionDurationPercent(value);
            case "pet_luck" -> data.setPetLuck(value);
            case "pet_xp_gain_percent" -> data.setPetXpGainPercent(value);
            case "exp_share_rate_percent" -> data.setExpShareRatePercent(value);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private UUID resolveTarget(CommandSender sender, @Nullable String name) {
        if (name == null || name.isBlank()) {
            if (sender instanceof Player player) return player.getUniqueId();
            return null;
        }

        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();

        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (name.equalsIgnoreCase(offline.getName())) return offline.getUniqueId();
        }
        return null;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("用法:", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/playerstats get [玩家]", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/playerstats set <玩家> <属性> <数值> [属性 数值] ...", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("属性: " + String.join(", ", STAT_KEYS), NamedTextColor.DARK_GRAY));
    }

    private static String format(double value) {
        if (value == (long) value) return String.valueOf((long) value);
        return String.format(Locale.US, "%.2f", value);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        if (args.length == 1) return filter(SUBCOMMANDS, args[0]);
        if (args.length == 2) return playerNames(args[1]);
        if (args.length >= 3 && "set".equalsIgnoreCase(args[0])) {
            int index = args.length - 1;
            if (index % 2 == 0) return filter(STAT_KEYS, args[index]);
        }
        return Collections.emptyList();
    }

    private static List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return new ArrayList<>(values);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .collect(Collectors.toList());
    }

    private static List<String> playerNames(String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(normalized)) {
                result.add(player.getName());
            }
        }
        return result;
    }
}
