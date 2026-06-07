package maple.underground_mayor.commands;

import maple.underground_mayor.mayor.MayorNpcManager;
import maple.underground_mayor.mayor.MayorNpcRole;
import maple.underground_mayor.reward.MayorBadgeManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

public final class MayorCommand implements CommandExecutor, TabCompleter {
    private static final String ADMIN_PERMISSION = "underground_mayor.admin";

    private final MayorNpcManager mayorNpcManager;
    private final MayorBadgeManager mayorBadgeManager;

    public MayorCommand(MayorNpcManager mayorNpcManager, MayorBadgeManager mayorBadgeManager) {
        this.mayorNpcManager = mayorNpcManager;
        this.mayorBadgeManager = mayorBadgeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("你沒有權限使用這個指令。");
            return true;
        }

        String subcommand = args.length == 0 ? "progress" : args[0].toLowerCase();

        if (subcommand.equals("progress")) {
            mayorNpcManager.sendProgress(sender);
            return true;
        }

        if (subcommand.equals("spawn")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("這個指令只能由玩家執行。");
                return true;
            }
            mayorNpcManager.spawnNpcs(player);
            return true;
        }

        if (subcommand.equals("badgepreview")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("這個指令只能由玩家執行。");
                return true;
            }

            int amount = 1;
            if (args.length >= 2) {
                try {
                    amount = Math.max(1, Integer.parseInt(args[1]));
                } catch (NumberFormatException ignored) {
                    sender.sendMessage("數量必須是數字。");
                    return true;
                }
            }

            mayorBadgeManager.givePreviewBadges(player, amount);
            sender.sendMessage("已給你預覽徽章 x" + amount + "。");
            return true;
        }

        if (subcommand.equals("hide")) {
            if (args.length < 2) {
                sender.sendMessage("用法: /" + label + " hide <莉迪|巴克|艾里斯|all>");
                return true;
            }

            String target = args[1];
            if (target.equalsIgnoreCase("all")) {
                int hidden = mayorNpcManager.hideAllNpcs();
                sender.sendMessage("已隱藏 " + hidden + " 個市長 NPC。");
                return true;
            }

            MayorNpcRole role = MayorNpcRole.fromInput(target);
            if (role == null) {
                sender.sendMessage("找不到指定的 NPC。可用名稱：莉迪、巴克、艾里斯、all");
                return true;
            }

            boolean hidden = mayorNpcManager.hideNpc(role);
            if (hidden) {
                sender.sendMessage("已隱藏 NPC：" + role.displayName() + "。");
            } else {
                sender.sendMessage("目前找不到可隱藏的 NPC：" + role.displayName() + "。");
            }
            return true;
        }

        sender.sendMessage("用法: /" + label + " <spawn|progress|badgepreview [數量]|hide <npc|all>>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            return List.of();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], List.of("spawn", "progress", "badgepreview", "hide"), completions);
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("badgepreview")) {
            return List.of("1", "5", "10", "64");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("hide")) {
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[1], List.of("莉迪", "巴克", "艾里斯", "all"), completions);
            return completions;
        }

        return List.of();
    }
}
