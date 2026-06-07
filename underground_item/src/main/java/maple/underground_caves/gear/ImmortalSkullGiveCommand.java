package maple.underground_caves.gear;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理員指令：發放永恆的面具（僅能透過指令取得，不會掉落）。
 * /immortalskull give [玩家]
 */
public final class ImmortalSkullGiveCommand implements CommandExecutor, TabCompleter {

    private final ImmortalSkullManager skullManager;

    public ImmortalSkullGiveCommand(ImmortalSkullManager skullManager) {
        this.skullManager = skullManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || !"give".equalsIgnoreCase(args[0])) {
            sender.sendMessage("§e用法: §f/immortalskull give [玩家]");
            return true;
        }
        Player target;
        if (args.length >= 2) {
            target = sender.getServer().getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§c找不到玩家: " + args[1]);
                return true;
            }
        } else {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("§c請指定玩家: §f/immortalskull give <玩家>");
                return true;
            }
            target = p;
        }
        if (target.getInventory().firstEmpty() == -1) {
            sender.sendMessage("§c目標背包已滿。");
            return true;
        }
        target.getInventory().addItem(skullManager.createImmortalSkull());
        if (target.equals(sender)) {
            sender.sendMessage("§a已獲得永恆的面具。");
        } else {
            sender.sendMessage("§a已發放永恆的面具給 §f" + target.getName() + "§a。");
            target.sendMessage("§a你獲得了永恆的面具。");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Collections.singletonList("give");
        }
        if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            String prefix = args[1].toLowerCase();
            return sender.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
