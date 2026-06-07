package maple.underground_state.commands;

import maple.underground_state.backpack.BackpackBagItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BackpackBagCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (args.length < 1 || args.length > 3) {
            sendUsage(sender, label);
            return true;
        }

        int rows = parseInt(args[0], -1);
        if (rows < BackpackBagItems.MIN_ROWS || rows > BackpackBagItems.MAX_ROWS) {
            sender.sendMessage(Component.text("行数只能是 1 到 5。", NamedTextColor.RED));
            return true;
        }

        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("找不到在线玩家: " + args[1], NamedTextColor.RED));
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sendUsage(sender, label);
            return true;
        }

        int amount = args.length >= 3 ? parseInt(args[2], -1) : 1;
        if (amount < 1 || amount > 64) {
            sender.sendMessage(Component.text("数量只能是 1 到 64。", NamedTextColor.RED));
            return true;
        }

        ItemStack item = BackpackBagItems.createBag(rows);
        item.setAmount(amount);
        Map<Integer, ItemStack> leftovers = target.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }

        sender.sendMessage(Component.text("已给予 " + target.getName() + " " + amount + " 个 "
                + BackpackBagItems.sizeName(rows) + "。", NamedTextColor.GREEN));
        if (!sender.equals(target)) {
            target.sendMessage(Component.text("你获得了 " + amount + " 个 "
                    + BackpackBagItems.sizeName(rows) + "。", NamedTextColor.GREEN));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        if (args.length == 1) {
            return filterPrefix(List.of("1", "2", "3", "4", "5"), args[0]);
        }
        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return filterPrefix(names, args[1]);
        }
        if (args.length == 3) {
            return filterPrefix(List.of("1", "8", "16", "32", "64"), args[2]);
        }
        return List.of();
    }

    private static void sendUsage(@NotNull CommandSender sender, @NotNull String label) {
        sender.sendMessage(Component.text("用法: /" + label + " <1-5> [玩家] [数量]", NamedTextColor.YELLOW));
    }

    private static int parseInt(@NotNull String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static @NotNull List<String> filterPrefix(@NotNull List<String> values, @NotNull String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                result.add(value);
            }
        }
        return result;
    }
}
