package maple.underground_quest.command;

import maple.underground_quest.UndergroundQuest;
import maple.underground_quest.data.QuestRegistry;
import maple.underground_quest.manager.PlayerQuestManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 管理員指令 /questadmin。
 * 子指令：reload（重載任務定義）、give（給予任務）、reset（重置進度）、list（列出所有任務）。
 */
public final class QuestAdminCommand implements CommandExecutor, TabCompleter {

    private final UndergroundQuest plugin;
    private final PlayerQuestManager playerQuestManager;
    private final QuestRegistry questRegistry;

    public QuestAdminCommand(@NotNull UndergroundQuest plugin,
                             @NotNull PlayerQuestManager playerQuestManager,
                             @NotNull QuestRegistry questRegistry) {
        this.plugin = plugin;
        this.playerQuestManager = playerQuestManager;
        this.questRegistry = questRegistry;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "give" -> handleGive(sender, args);
            case "reset" -> handleReset(sender, args);
            case "list" -> handleList(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        questRegistry.load();
        sender.sendMessage(ChatColor.GREEN + "任務定義已重新載入，共載入 "
                + questRegistry.getQuestCount() + " 項任務。");
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "用法: /questadmin give <player> <questId>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "找不到玩家: " + args[1]);
            return;
        }
        String questId = args[2];
        if (questRegistry.getQuest(questId) == null) {
            sender.sendMessage(ChatColor.RED + "找不到任務: " + questId);
            return;
        }
        String result = playerQuestManager.acceptQuest(target.getUniqueId(), questId);
        sender.sendMessage(ChatColor.GRAY + result);
        if (result.startsWith("已接受")) {
            target.sendMessage(ChatColor.YELLOW + "你接到一個新任務！使用 /quest 查看。");
        }
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /questadmin reset <player> [questId]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "找不到玩家: " + args[1]);
            return;
        }
        if (args.length >= 3) {
            String questId = args[2];
            playerQuestManager.resetQuest(target.getUniqueId(), questId);
            sender.sendMessage(ChatColor.GREEN + "已重置 " + target.getName() + " 的任務 " + questId);
        } else {
            playerQuestManager.resetAll(target.getUniqueId());
            sender.sendMessage(ChatColor.GREEN + "已重置 " + target.getName() + " 的全部任務。");
        }
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "已註冊任務 (" + questRegistry.getQuestCount() + "):");
        for (String id : questRegistry.getAllQuestIds()) {
            sender.sendMessage(ChatColor.GRAY + " - " + id);
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== 任務管理 ===");
        sender.sendMessage(ChatColor.GRAY + "/questadmin reload " + ChatColor.WHITE + "- 重載任務定義");
        sender.sendMessage(ChatColor.GRAY + "/questadmin give <player> <questId> " + ChatColor.WHITE + "- 給予任務");
        sender.sendMessage(ChatColor.GRAY + "/questadmin reset <player> [questId] " + ChatColor.WHITE + "- 重置任務");
        sender.sendMessage(ChatColor.GRAY + "/questadmin list " + ChatColor.WHITE + "- 列出所有任務");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("reload", "give", "reset", "list"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("reset"))) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
            return filter(names, args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("reset"))) {
            return filter(questRegistry.getAllQuestIds(), args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(lower))
                .toList();
    }
}