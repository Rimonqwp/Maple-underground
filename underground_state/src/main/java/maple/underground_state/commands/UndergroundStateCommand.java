package maple.underground_state.commands;

import maple.underground_state.skills.CombatLevelTable;
import maple.underground_state.skills.SkillLevelTables;
import maple.underground_state.skills.SkillProgressManager;
import maple.underground_state.skills.SkillRewardService;
import maple.underground_state.skills.SkillType;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class UndergroundStateCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT_SUBCOMMANDS = List.of("player");
    private static final List<String> PLAYER_SUBCOMMANDS = List.of("state");
    private static final List<String> ACTIONS = List.of("add", "set", "delete");
    private static final List<String> SKILLS = Arrays.stream(SkillType.values())
            .map(type -> type.name().toLowerCase(Locale.ROOT))
            .toList();
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("^(\\d+)(xp|lvl)$", Pattern.CASE_INSENSITIVE);

    private final SkillProgressManager skillProgressManager;
    private final SkillRewardService skillRewardService;

    public UndergroundStateCommand(@NotNull SkillProgressManager skillProgressManager,
                                   @NotNull SkillRewardService skillRewardService) {
        this.skillProgressManager = skillProgressManager;
        this.skillRewardService = skillRewardService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (args.length < 4) {
            sendUsage(sender);
            return true;
        }

        if (!"player".equalsIgnoreCase(args[0]) || !"state".equalsIgnoreCase(args[1])) {
            sendUsage(sender);
            return true;
        }

        ParsedCommand parsed = parseArguments(sender, args);
        if (parsed == null) {
            return true;
        }

        long currentXp = skillProgressManager.getXp(parsed.target(), parsed.skillType());
        long updatedXp = applyChange(parsed.skillType(), currentXp, parsed.action(), parsed.amount());

        skillRewardService.setSkillXp(parsed.target(), parsed.skillType(), updatedXp);
        skillProgressManager.save(parsed.target());

        int currentLevel = SkillLevelTables.levelForXp(parsed.skillType(), updatedXp);
        String targetName = playerName(parsed.target());
        sender.sendMessage(Component.text(
                "已更新 " + targetName + " 的 " + parsed.skillType().displayName()
                        + "：XP=" + updatedXp + "，等級=" + currentLevel,
                NamedTextColor.GREEN
        ));
        return true;
    }

    @Nullable
    private ParsedCommand parseArguments(@NotNull CommandSender sender, @NotNull String[] args) {
        int offset = 2;

        UUID target;
        SkillType skillType;
        String actionArg;
        String amountArg;

        if (args.length == 5) {
            target = resolveSelf(sender);
            if (target == null) {
                sender.sendMessage(Component.text("控制台必須指定玩家名稱。", NamedTextColor.RED));
                sendUsage(sender);
                return null;
            }
            skillType = parseSkill(args[offset]);
            actionArg = args[offset + 1];
            amountArg = args[offset + 2];
        } else if (args.length == 6) {
            target = resolveTarget(args[offset]);
            if (target == null) {
                sender.sendMessage(Component.text("找不到目標玩家。", NamedTextColor.RED));
                return null;
            }
            skillType = parseSkill(args[offset + 1]);
            actionArg = args[offset + 2];
            amountArg = args[offset + 3];
        } else {
            sendUsage(sender);
            return null;
        }

        if (skillType == null) {
            sender.sendMessage(Component.text("未知技能。可用技能: " + String.join(", ", SKILLS), NamedTextColor.RED));
            return null;
        }

        Action action = parseAction(actionArg);
        if (action == null) {
            sender.sendMessage(Component.text("未知操作，只能使用 add / set / delete。", NamedTextColor.RED));
            return null;
        }

        Amount amount = parseAmount(amountArg);
        if (amount == null) {
            sender.sendMessage(Component.text("數值格式錯誤，請使用像 10xp 或 10lvl。", NamedTextColor.RED));
            return null;
        }

        return new ParsedCommand(target, skillType, action, amount);
    }

    private long applyChange(@NotNull SkillType skillType, long currentXp, @NotNull Action action, @NotNull Amount amount) {
        if (amount.unit() == Unit.XP) {
            return switch (action) {
                case ADD -> Math.max(0L, currentXp + amount.value());
                case SET -> Math.max(0L, amount.value());
                case DELETE -> Math.max(0L, currentXp - amount.value());
            };
        }

        int currentLevel = SkillLevelTables.levelForXp(skillType, currentXp);
        int targetLevel = switch (action) {
            case ADD -> currentLevel + (int) amount.value();
            case SET -> (int) amount.value();
            case DELETE -> currentLevel - (int) amount.value();
        };
        return xpForLevel(skillType, targetLevel);
    }

    private long xpForLevel(@NotNull SkillType skillType, int level) {
        if (level <= 0) {
            return 0L;
        }
        if (level >= SkillLevelTables.maxLevel(skillType)) {
            return SkillLevelTables.totalXpRequiredForLevel(skillType, SkillLevelTables.maxLevel(skillType));
        }
        return SkillLevelTables.totalXpRequiredForLevel(skillType, level);
    }

    @Nullable
    private UUID resolveSelf(@NotNull CommandSender sender) {
        if (sender instanceof Player player) {
            return player.getUniqueId();
        }
        return null;
    }

    @Nullable
    private UUID resolveTarget(@NotNull String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (name.equalsIgnoreCase(offline.getName())) {
                return offline.getUniqueId();
            }
        }
        return null;
    }

    @Nullable
    private SkillType parseSkill(@NotNull String raw) {
        try {
            return SkillType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Nullable
    private Action parseAction(@NotNull String raw) {
        try {
            return Action.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Nullable
    private Amount parseAmount(@NotNull String raw) {
        Matcher matcher = AMOUNT_PATTERN.matcher(raw.trim());
        if (!matcher.matches()) {
            return null;
        }
        long value = Long.parseLong(matcher.group(1));
        String unitText = matcher.group(2).toLowerCase(Locale.ROOT);
        Unit unit = "lvl".equals(unitText) ? Unit.LEVEL : Unit.XP;
        return new Amount(Math.max(0L, value), unit);
    }

    private @NotNull String playerName(@NotNull UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name == null || name.isBlank() ? uuid.toString() : name;
    }

    private void sendUsage(@NotNull CommandSender sender) {
        sender.sendMessage(Component.text("用法：", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/underground_state player state <skill> <add|set|delete> <10xp|10lvl>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/underground_state player state <player> <skill> <add|set|delete> <10xp|10lvl>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("技能: " + String.join(", ", SKILLS), NamedTextColor.DARK_GRAY));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        if (args.length == 1) {
            return filter(ROOT_SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && "player".equalsIgnoreCase(args[0])) {
            return filter(PLAYER_SUBCOMMANDS, args[1]);
        }
        if (args.length == 3 && "player".equalsIgnoreCase(args[0]) && "state".equalsIgnoreCase(args[1])) {
            List<String> suggestions = new ArrayList<>(SKILLS);
            suggestions.addAll(playerNames(args[2]));
            return filter(suggestions, args[2]);
        }
        if (args.length == 4 && "player".equalsIgnoreCase(args[0]) && "state".equalsIgnoreCase(args[1])) {
            if (parseSkill(args[2]) != null) {
                return filter(ACTIONS, args[3]);
            }
            return filter(SKILLS, args[3]);
        }
        if (args.length == 5 && "player".equalsIgnoreCase(args[0]) && "state".equalsIgnoreCase(args[1])) {
            if (parseSkill(args[2]) != null) {
                return filter(sampleAmounts(), args[4]);
            }
            return filter(ACTIONS, args[4]);
        }
        if (args.length == 6 && "player".equalsIgnoreCase(args[0]) && "state".equalsIgnoreCase(args[1])) {
            return filter(sampleAmounts(), args[5]);
        }
        return Collections.emptyList();
    }

    private static List<String> sampleAmounts() {
        return List.of("10xp", "100xp", "1lvl", "10lvl");
    }

    private static List<String> filter(@NotNull List<String> values, @NotNull String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return new ArrayList<>(values);
        }
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .distinct()
                .collect(Collectors.toList());
    }

    private static List<String> playerNames(@NotNull String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(normalized)) {
                result.add(player.getName());
            }
        }
        return result;
    }

    private enum Action {
        ADD,
        SET,
        DELETE
    }

    private enum Unit {
        XP,
        LEVEL
    }

    private record Amount(long value, @NotNull Unit unit) {
    }

    private record ParsedCommand(@NotNull UUID target,
                                 @NotNull SkillType skillType,
                                 @NotNull Action action,
                                 @NotNull Amount amount) {
    }
}
