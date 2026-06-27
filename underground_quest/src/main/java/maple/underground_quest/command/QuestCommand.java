package maple.underground_quest.command;

import maple.underground_quest.UndergroundQuest;
import maple.underground_quest.data.QuestRegistry;
import maple.underground_quest.manager.PlayerQuestManager;
import maple.underground_quest.model.Quest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /quest 指令：查看任務清單、追追蹤、領取獎勵、放棄。
 * basic 版先用聊天訊息呈現，GUI 面板留待後續。
 */
public final class QuestCommand implements CommandExecutor, TabCompleter {

    private final UndergroundQuest plugin;
    private final PlayerQuestManager manager;
    private final QuestRegistry registry;

    public QuestCommand(UndergroundQuest plugin, PlayerQuestManager manager, QuestRegistry registry) {
        this.plugin = plugin;
        this.manager = manager;
        this.registry = registry;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("只有玩家可以使用此指令。", NamedTextColor.RED));
            return true;
        }
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "open", "list" -> listQuests(player);
            case "track" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("用法: /quest track <id>", NamedTextColor.YELLOW));
                    return true;
                }
                manager.setTracked(player.getUniqueId(), args[1]);
                player.sendMessage(Component.text("已追蹤任務: " + args[1], NamedTextColor.GREEN));
            }
            case "claim" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("用法: /quest claim <id>", NamedTextColor.YELLOW));
                    return true;
                }
                String result = manager.claimReward(player.getUniqueId(), args[1]);
                boolean ok = result.startsWith("已");
                player.sendMessage(Component.text(result, ok ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
            case "abandon" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("用法: /quest abandon <id>", NamedTextColor.YELLOW));
                    return true;
                }
                String result = manager.abandon(player.getUniqueId(), args[1]);
                player.sendMessage(Component.text(result, NamedTextColor.GRAY));
            }
            default -> player.sendMessage(Component.text(
                    "用法: /quest [list|track <id>|claim <id>|abandon <id>]", NamedTextColor.YELLOW));
        }
        return true;
    }

    private void listQuests(Player player) {
        Map<String, Map<String, Integer>> active = manager.getActive(player.getUniqueId());
        player.sendMessage(Component.text("===== 你的任務 =====", NamedTextColor.GOLD));
        if (active.isEmpty()) {
            player.sendMessage(Component.text("目前沒有進行中的任務。", NamedTextColor.GRAY));
            return;
        }
        for (Map.Entry<String, Map<String, Integer>> entry : active.entrySet()) {
            Quest quest = registry.getQuest(entry.getKey());
            if (quest == null) {
                continue;
            }
            player.sendMessage(Component.text("- " + quest.getDisplayName() + " (" + quest.getId() + ")", NamedTextColor.AQUA));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("list", "track", "claim", "abandon"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("track")
                || args[0].equalsIgnoreCase("claim")
                || args[0].equalsIgnoreCase("abandon"))) {
            List<String> ids = new ArrayList<>();
            for (Quest q : registry.all()) {
                ids.add(q.getId());
            }
            return filter(ids, args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                result.add(o);
            }
        }
        return result;
    }
}