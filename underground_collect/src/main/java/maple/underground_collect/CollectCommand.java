package maple.underground_collect;

import maple.underground_state.UndergroundState;
import maple.underground_state.collection.CollectionProgressManager;
import maple.underground_state.gui.CollectionInfoGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

final class CollectCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERMISSION = "underground_collect.admin";
    private static final List<String> ROOT_SUBCOMMANDS = List.of("open", "claim", "leaderboard", "missing", "guide");
    private static final List<String> ADMIN_SUBCOMMANDS = List.of("view", "add", "set", "reset", "resetclaims");

    private final @Nullable Plugin plugin;
    private final UndergroundState state;
    private final CollectionDataRepository repository;
    private final CollectionMenus menus;
    private final CollectionRewardService rewardService;

    CollectCommand(@Nullable Plugin plugin,
                   @NotNull UndergroundState state,
                   @NotNull CollectionDataRepository repository,
                   @NotNull CollectionMenus menus,
                   @NotNull CollectionRewardService rewardService) {
        this.plugin = plugin;
        this.state = state;
        this.repository = repository;
        this.menus = menus;
        this.rewardService = rewardService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (args.length > 0 && "admin".equalsIgnoreCase(args[0])) {
            handleAdminCommand(sender, args);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("只有玩家才能打开收集中心界面；控制台请使用 /collect admin ...", NamedTextColor.RED));
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
                rewardService.sendClaimFeedback(player, summary);
                menus.openHub(player);
            }
            case "leaderboard", "top" -> {
                CollectionInfoGui.Category category = parseCategory(args, 1);
                menus.openLeaderboard(player, category, 0);
            }
            case "missing" -> {
                CollectionInfoGui.Category category = parseCategory(args, 1);
                menus.openMissing(player, category, 0);
            }
            case "guide", "help" -> menus.openGuide(player);
            default -> menus.openHub(player);
        }
        return true;
    }

    private void handleAdminCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(Component.text("你没有权限使用收集中心管理指令。", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sendAdminUsage(sender);
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "view" -> handleAdminView(sender, args);
            case "add" -> handleAdminModify(sender, args, false);
            case "set" -> handleAdminModify(sender, args, true);
            case "reset" -> handleAdminReset(sender, args, true);
            case "resetclaims" -> handleAdminReset(sender, args, false);
            default -> sendAdminUsage(sender);
        }
    }

    private void handleAdminView(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("用法：/collect admin view <玩家> [分类]", NamedTextColor.RED));
            return;
        }
        TargetPlayer target = resolveTarget(args[2]);
        if (target == null) {
            sender.sendMessage(Component.text("找不到玩家：" + args[2], NamedTextColor.RED));
            return;
        }
        CollectionInfoGui.Category filterCategory = args.length >= 4 ? parseCategory(args, 3) : null;
        if (args.length >= 4 && filterCategory == null && !isAllToken(args[3])) {
            sender.sendMessage(Component.text("未知分类：" + args[3], NamedTextColor.RED));
            return;
        }

        runAsync(() -> {
            CollectionProgressManager progressManager = state.getCollectionProgressManager();
            Map<String, Long> progress = progressManager != null ? progressManager.snapshot(target.uuid()) : Map.of();
            Map<String, Integer> claims = repository.loadClaims(target.uuid());

            List<Component> lines = new ArrayList<>();
            lines.add(Component.text("=== 收藏数据：" + target.displayName() + " ===", NamedTextColor.GOLD));
            CollectionInfoGui.Category[] categories = filterCategory == null
                    ? CollectionInfoGui.Category.values()
                    : new CollectionInfoGui.Category[]{filterCategory};

            long grandTotal = 0L;
            long grandTiers = 0L;
            long grandClaimable = 0L;
            for (CollectionInfoGui.Category category : categories) {
                long catTotal = 0L;
                long catTiers = 0L;
                long catMaxTiers = (long) category.items().size() * CollectionMath.maxTier(category);
                long catClaimable = 0L;
                List<Component> itemLines = new ArrayList<>();
                for (CollectionInfoGui.CollectionItem item : category.items()) {
                    String key = CollectionInfoGui.collectionKey(category, item.material());
                    long amount = progress.getOrDefault(key, 0L);
                    int tier = CollectionMath.currentTier(amount, category);
                    int claimed = claims.getOrDefault(key, 0);
                    int claimable = Math.max(0, tier - claimed);
                    catTotal += amount;
                    catTiers += tier;
                    catClaimable += claimable;
                    if (filterCategory != null && amount > 0L) {
                        itemLines.add(Component.text("  - " + item.name() + " (" + item.material().name() + "): ", NamedTextColor.GRAY)
                                .append(Component.text(CollectionMath.formatNumber(amount), NamedTextColor.AQUA))
                                .append(Component.text(" | 等级 " + tier + "/" + CollectionMath.maxTier(category), NamedTextColor.YELLOW))
                                .append(Component.text(" | 已领 " + claimed, NamedTextColor.GREEN)));
                    }
                }
                grandTotal += catTotal;
                grandTiers += catTiers;
                grandClaimable += catClaimable;
                lines.add(Component.text("[" + category.title() + "] ", NamedTextColor.YELLOW)
                        .append(Component.text("总量 " + CollectionMath.formatNumber(catTotal), NamedTextColor.AQUA))
                        .append(Component.text(" | 等级 " + catTiers + "/" + catMaxTiers, NamedTextColor.GRAY))
                        .append(Component.text(" | 可领 " + catClaimable, catClaimable > 0 ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY)));
                lines.addAll(itemLines);
            }
            if (filterCategory == null) {
                lines.add(Component.text("合计：总量 " + CollectionMath.formatNumber(grandTotal)
                        + " | 总等级 " + grandTiers
                        + " | 待领层数 " + grandClaimable, NamedTextColor.GREEN));
            }
            runSync(() -> lines.forEach(sender::sendMessage));
        });
    }

    private void handleAdminModify(@NotNull CommandSender sender, @NotNull String[] args, boolean absoluteSet) {
        String verb = absoluteSet ? "set" : "add";
        if (args.length < 6) {
            sender.sendMessage(Component.text("用法：/collect admin " + verb + " <玩家> <分类> <物品> <数量>", NamedTextColor.RED));
            return;
        }
        TargetPlayer target = resolveTarget(args[2]);
        if (target == null) {
            sender.sendMessage(Component.text("找不到玩家：" + args[2], NamedTextColor.RED));
            return;
        }
        CollectionInfoGui.Category category = parseCategory(args, 3);
        if (category == null) {
            sender.sendMessage(Component.text("未知分类：" + args[3] + "（可选：farming/mining/combat/foraging/fishing）", NamedTextColor.RED));
            return;
        }
        CollectionInfoGui.CollectionItem item = findCollectionItem(category, args[4]);
        if (item == null) {
            sender.sendMessage(Component.text("分类 " + category.title() + " 中不存在物品：" + args[4], NamedTextColor.RED));
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[5]);
        } catch (NumberFormatException ignored) {
            sender.sendMessage(Component.text("数量必须是整数：" + args[5], NamedTextColor.RED));
            return;
        }
        if (absoluteSet && amount < 0L) {
            sender.sendMessage(Component.text("设置的数量不能小于 0。", NamedTextColor.RED));
            return;
        }

        CollectionProgressManager progressManager = state.getCollectionProgressManager();
        if (progressManager == null) {
            sender.sendMessage(Component.text("收藏进度管理器未就绪。", NamedTextColor.RED));
            return;
        }

        long finalAmount;
        if (absoluteSet) {
            progressManager.setAmount(target.uuid(), category, item.material(), amount, true);
            finalAmount = amount;
        } else {
            finalAmount = progressManager.addMaterialInCategory(target.uuid(), category, item.material(), amount, true);
        }
        repository.invalidateLeaderboardCache();
        runAsync(() -> progressManager.save(target.uuid()));

        int newTier = CollectionMath.currentTier(finalAmount, category);
        sender.sendMessage(Component.text("已更新 ", NamedTextColor.GREEN)
                .append(Component.text(target.displayName(), NamedTextColor.YELLOW))
                .append(Component.text(" 的 [" + category.title() + " · " + item.name() + "] 进度为 ", NamedTextColor.GREEN))
                .append(Component.text(CollectionMath.formatNumber(finalAmount), NamedTextColor.AQUA))
                .append(Component.text("（当前等级 " + newTier + "/" + CollectionMath.maxTier(category) + "）。", NamedTextColor.GRAY)));
    }

    private void handleAdminReset(@NotNull CommandSender sender, @NotNull String[] args, boolean resetProgress) {
        String verb = resetProgress ? "reset" : "resetclaims";
        if (args.length < 3) {
            sender.sendMessage(Component.text("用法：/collect admin " + verb + " <玩家> [分类|all]", NamedTextColor.RED));
            return;
        }
        TargetPlayer target = resolveTarget(args[2]);
        if (target == null) {
            sender.sendMessage(Component.text("找不到玩家：" + args[2], NamedTextColor.RED));
            return;
        }
        CollectionInfoGui.Category category = null;
        if (args.length >= 4 && !isAllToken(args[3])) {
            category = parseCategory(args, 3);
            if (category == null) {
                sender.sendMessage(Component.text("未知分类：" + args[3], NamedTextColor.RED));
                return;
            }
        }

        CollectionInfoGui.Category targetCategory = category;
        if (resetProgress) {
            CollectionProgressManager progressManager = state.getCollectionProgressManager();
            if (progressManager != null) {
                runAsync(() -> progressManager.reset(target.uuid(), targetCategory));
            }
            repository.invalidateLeaderboardCache();
        }
        repository.resetClaimsAsync(target.uuid(), targetCategory);

        String scopeLabel = targetCategory == null ? "全部收藏" : targetCategory.title();
        String what = resetProgress ? "收藏进度与已领奖励记录" : "已领奖励记录";
        sender.sendMessage(Component.text("已重置 ", NamedTextColor.GREEN)
                .append(Component.text(target.displayName(), NamedTextColor.YELLOW))
                .append(Component.text(" 的 [" + scopeLabel + "] " + what + "。", NamedTextColor.GREEN)));
    }

    private void sendAdminUsage(@NotNull CommandSender sender) {
        sender.sendMessage(Component.text("收集中心管理指令用法：", NamedTextColor.GOLD));
        sender.sendMessage(Component.text(" /collect admin view <玩家> [分类]", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(" /collect admin add <玩家> <分类> <物品> <数量>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(" /collect admin set <玩家> <分类> <物品> <数量>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(" /collect admin reset <玩家> [分类|all]", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(" /collect admin resetclaims <玩家> [分类|all]", NamedTextColor.YELLOW));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(ROOT_SUBCOMMANDS);
            if (sender.hasPermission(ADMIN_PERMISSION)) {
                options.add("admin");
            }
            return filter(options, args[0]);
        }

        if ("admin".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission(ADMIN_PERMISSION)) {
                return Collections.emptyList();
            }
            if (args.length == 2) {
                return filter(ADMIN_SUBCOMMANDS, args[1]);
            }
            if (args.length == 3) {
                List<String> players = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList());
                return filter(players, args[2]);
            }
            String action = args[1].toLowerCase(Locale.ROOT);
            if (args.length == 4) {
                List<String> categories = new ArrayList<>(categorySuggestions());
                if ("view".equals(action) || "reset".equals(action) || "resetclaims".equals(action)) {
                    categories.add("all");
                }
                return filter(categories, args[3]);
            }
            if (args.length == 5 && ("add".equals(action) || "set".equals(action))) {
                CollectionInfoGui.Category category = parseCategory(args, 3);
                if (category == null) {
                    return Collections.emptyList();
                }
                List<String> items = category.items().stream()
                        .map(item -> item.material().name().toLowerCase(Locale.ROOT))
                        .collect(Collectors.toList());
                return filter(items, args[4]);
            }
            if (args.length == 6 && ("add".equals(action) || "set".equals(action))) {
                return filter(List.of("50", "500", "5000", "50000"), args[5]);
            }
            return Collections.emptyList();
        }

        if (args.length == 2) {
            return filter(categorySuggestions(), args[1]);
        }
        return Collections.emptyList();
    }

    @Nullable
    private static TargetPlayer resolveTarget(@NotNull String token) {
        Player online = Bukkit.getPlayerExact(token);
        if (online != null) {
            return new TargetPlayer(online.getUniqueId(), online.getName());
        }
        try {
            UUID parsed = UUID.fromString(token);
            OfflinePlayer offline = Bukkit.getOfflinePlayer(parsed);
            String name = offline.getName();
            return new TargetPlayer(parsed, name == null || name.isBlank() ? parsed.toString().substring(0, 8) : name);
        } catch (IllegalArgumentException ignored) {
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(token)) {
                return new TargetPlayer(player.getUniqueId(), player.getName());
            }
        }
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            String name = offline.getName();
            if (name != null && name.equalsIgnoreCase(token)) {
                return new TargetPlayer(offline.getUniqueId(), name);
            }
        }
        return null;
    }

    @Nullable
    private static CollectionInfoGui.CollectionItem findCollectionItem(@NotNull CollectionInfoGui.Category category,
                                                                       @NotNull String raw) {
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        for (CollectionInfoGui.CollectionItem item : category.items()) {
            if (item.material().name().equals(normalized) || item.name().equalsIgnoreCase(raw.trim())) {
                return item;
            }
        }
        return null;
    }

    private static boolean isAllToken(@NotNull String raw) {
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        return "all".equals(lower) || "overall".equals(lower) || "全部".equals(lower);
    }

    @Nullable
    static CollectionInfoGui.Category parseCategory(@NotNull String[] args, int index) {
        if (args.length <= index) {
            return null;
        }
        String raw = args[index].trim().toLowerCase(Locale.ROOT);
        if (raw.isBlank() || isAllToken(raw)) {
            return null;
        }
        return switch (raw) {
            case "farming", "farm", "hoe", "collection_hoe", "锄头", "农耕" -> CollectionInfoGui.Category.FARMING;
            case "mining", "mine", "pickaxe", "collection_pickaxe", "镐子", "挖矿" -> CollectionInfoGui.Category.MINING;
            case "combat", "sword", "collection_sword", "战斗", "剑" -> CollectionInfoGui.Category.COMBAT;
            case "foraging", "axe", "collection_axe", "伐木", "斧头" -> CollectionInfoGui.Category.FORAGING;
            case "fishing", "rod", "collection_fishing_rod", "钓鱼" -> CollectionInfoGui.Category.FISHING;
            default -> null;
        };
    }

    private void sendCategoryUsage(@NotNull Player player) {
        player.sendMessage(Component.text("用法：/collect open <farming|mining|combat|foraging|fishing>", NamedTextColor.RED));
    }

    private static @NotNull List<String> categorySuggestions() {
        return Arrays.stream(CollectionInfoGui.Category.values())
                .map(category -> category.name().toLowerCase(Locale.ROOT))
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

    private void runAsync(@NotNull Runnable task) {
        if (plugin != null && plugin.isEnabled() && Bukkit.getServer() != null) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        } else {
            task.run();
        }
    }

    private void runSync(@NotNull Runnable task) {
        if (plugin != null && plugin.isEnabled() && Bukkit.getServer() != null) {
            if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, task);
            }
        } else {
            task.run();
        }
    }

    private record TargetPlayer(@NotNull UUID uuid, @NotNull String displayName) {
    }
}
