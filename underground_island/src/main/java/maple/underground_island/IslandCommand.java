package maple.underground_island;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

final class IslandCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("home", "invite", "accept", "info");

    private final IslandService islandService;

    IslandCommand(IslandService islandService) {
        this.islandService = islandService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("只有玩家可以使用岛屿命令。", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("underground_island.use")) {
            player.sendMessage(Component.text("你没有使用岛屿命令的权限。", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            islandService.home(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "home", "h" -> islandService.home(player);
            case "invite", "inv", "i" -> handleInvite(player, args);
            case "accept", "a" -> islandService.accept(player, args.length >= 2 ? args[1] : null);
            case "info" -> islandService.info(player);
            default -> sendUsage(player);
        }
        return true;
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /island invite <玩家>", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(Component.text("找不到在线玩家: " + args[1], NamedTextColor.RED));
            return;
        }
        islandService.invite(player, target);
    }

    private void sendUsage(Player player) {
        player.sendMessage(Component.text("用法: /island home | invite <玩家> | accept [岛主] | info", NamedTextColor.GRAY));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("invite".equals(sub) || "accept".equals(sub)) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> !name.equalsIgnoreCase(sender.getName()))
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .limit(20)
                        .collect(Collectors.toList());
            }
        }
        return List.of();
    }

    private static List<String> filter(List<String> values, String prefix) {
        String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return values;
        }
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .collect(Collectors.toList());
    }
}
