package maple.underground_collect;

import maple.underground_state.gui.CollectionInfoGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import java.util.Map;
import java.util.stream.Collectors;

final class CollectCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT_SUBCOMMANDS = List.of("open", "claim", "leaderboard", "missing", "guide");

    private final CollectionMenus menus;
    private final CollectionRewardService rewardService;

    CollectCommand(@NotNull CollectionMenus menus, @NotNull CollectionRewardService rewardService) {
        this.menus = menus;
        this.rewardService = rewardService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("只有玩家才能使用收藏中心命令。", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            menus.openHub(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "open" -> {
                CollectionInfoGui.Category category = parseCategory(args, 1);
                if (category == null) {
                    sendCategoryUsage(player);
                    return true;
                }
                CollectionInfoGui.open(player, category);
            }
            case "claim" -> {
                CollectionInfoGui.Category category = parseCategory(args, 1);
                CollectionRewardService.ClaimSummary summary = category == null
                        ? rewardService.claimAll(player)
                        : rewardService.claimCategory(player, category);
                if (!summary.hasAnything()) {
                    player.sendMessage(Component.text("当前没有可领取的收藏奖励。", NamedTextColor.YELLOW));
                } else {
                    sendClaimSummary(player, summary);
                }
                menus.openHub(player);
            }
            case "leaderboard" -> {
                CollectionInfoGui.Category category = parseCategory(args, 1);
                menus.openLeaderboard(player, category);
            }
            case "missing" -> {
                CollectionInfoGui.Category category = parseCategory(args, 1);
                Map<String, Long> missing = menus.missingSummary(player, category);
                if (missing.isEmpty()) {
                    player.sendMessage(Component.text("你当前没有未领取的收藏奖励。", NamedTextColor.GREEN));
                    return true;
                }
                player.sendMessage(Component.text("未领取的收藏奖励提示：", NamedTextColor.GOLD));
                int shown = 0;
                for (Map.Entry<String, Long> entry : missing.entrySet()) {
                    player.sendMessage(Component.text(" - " + entry.getKey() + "，当前数量 " + formatNumber(entry.getValue()), NamedTextColor.GRAY));
                    if (++shown >= 8) {
                        break;
                    }
                }
            }
            case "guide" -> menus.openGuide(player);
            default -> menus.openHub(player);
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        if (args.length == 1) {
            return filter(ROOT_SUBCOMMANDS, args[0]);
        }
        if (args.length == 2) {
            return filter(categorySuggestions(), args[1]);
        }
        return Collections.emptyList();
    }

    @Nullable
    private CollectionInfoGui.Category parseCategory(@NotNull String[] args, int index) {
        if (args.length <= index) {
            return null;
        }
        String raw = args[index].trim().toLowerCase(Locale.ROOT);
        if (raw.isBlank() || "all".equals(raw) || "overall".equals(raw) || "全部".equals(raw)) {
            return null;
        }
        return switch (raw) {
            case "farming", "farm", "hoe", "锄头", "农耕" -> CollectionInfoGui.Category.FARMING;
            case "mining", "mine", "pickaxe", "镐子", "挖矿" -> CollectionInfoGui.Category.MINING;
            case "combat", "sword", "战斗", "剑" -> CollectionInfoGui.Category.COMBAT;
            case "foraging", "axe", "伐木", "斧头" -> CollectionInfoGui.Category.FORAGING;
            case "fishing", "rod", "钓鱼" -> CollectionInfoGui.Category.FISHING;
            default -> null;
        };
    }

    private void sendCategoryUsage(@NotNull Player player) {
        player.sendMessage(Component.text("用法：/collect open <farming|mining|combat|foraging|fishing>", NamedTextColor.RED));
    }

    private static @NotNull List<String> categorySuggestions() {
        return Arrays.stream(CollectionInfoGui.Category.values())
                .flatMap(category -> List.of(category.pageId(), category.name().toLowerCase(Locale.ROOT)).stream())
                .distinct()
                .collect(Collectors.toList());
    }

    private static @NotNull List<String> filter(@NotNull List<String> values, @NotNull String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return new ArrayList<>(values);
        }
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .distinct()
                .collect(Collectors.toList());
    }

    private static void sendClaimSummary(@NotNull Player player, @NotNull CollectionRewardService.ClaimSummary summary) {
        player.sendMessage(Component.text("收藏奖励领取完成。", NamedTextColor.GREEN));
        player.sendMessage(Component.text("领取收藏层数: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(summary.tiersClaimed()), NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("领取收藏项数: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(summary.collectionsClaimed()), NamedTextColor.YELLOW)));
        if (summary.mapleXp() > 0L) {
            player.sendMessage(Component.text("楓叶经验: ", NamedTextColor.GRAY)
                    .append(Component.text(formatNumber(summary.mapleXp()), NamedTextColor.AQUA)));
        }
        for (Map.Entry<maple.underground_state.skills.SkillType, Long> entry : summary.skillXpByType().entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0L) {
                continue;
            }
            player.sendMessage(Component.text(entry.getKey().displayName() + " 经验: ", NamedTextColor.GRAY)
                    .append(Component.text(formatNumber(entry.getValue()), NamedTextColor.AQUA)));
        }
    }

    private static @NotNull String formatNumber(long value) {
        long abs = Math.abs(value);
        if (abs >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000D);
        }
        if (abs >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000D);
        }
        if (abs >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fk", value / 1_000D);
        }
        return String.valueOf(value);
    }
}
