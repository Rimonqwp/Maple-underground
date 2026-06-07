package maple.underground_caves.dungeon;

import maple.underground_caves.Test_dungeons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 管理員：調整當前副本隊伍（該地牢世界）在側邊欄顯示的總分覆蓋，不修改擊殺/探索等底層統計。
 */
public final class DungeonTeamScoreCommand implements CommandExecutor, TabCompleter {

    private static final String PERM = "test_dungeons.admin";

    private final Test_dungeons plugin;

    public DungeonTeamScoreCommand(Test_dungeons plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERM)) {
            sender.sendMessage(Component.text("你没有权限。", NamedTextColor.RED));
            return true;
        }
        DungeonScoreboard board = plugin.getDungeonScoreboard();
        if (board == null) {
            sender.sendMessage(Component.text("记分板未就绪。", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            return sendStatus(sender, board, resolveWorldForStatus(sender, null));
        }

        if (args.length == 1
                && !"set".equalsIgnoreCase(args[0])
                && !"clear".equalsIgnoreCase(args[0])) {
            return sendStatus(sender, board, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("clear".equals(sub)) {
            String worldName = resolveWorld(sender, args, 1);
            if (worldName == null) return true;
            if (!plugin.isDungeonWorld(worldName)) {
                sender.sendMessage(Component.text("不是地牢世界：" + worldName, NamedTextColor.RED));
                return true;
            }
            board.setAdminTotalScoreOverride(worldName, null);
            sender.sendMessage(Component.text("已清除该副本的总分覆盖，恢复公式计分：", NamedTextColor.GREEN)
                    .append(Component.text(worldName, NamedTextColor.WHITE)));
            return true;
        }

        if ("set".equals(sub)) {
            if (args.length < 2) {
                sender.sendMessage(Component.text("用法：/" + label + " set <0-" + DungeonScoreboard.MAX_SCORE_TOTAL + "> [地牢世界名]",
                        NamedTextColor.GRAY));
                sender.sendMessage(Component.text("控制台必须写世界名：/" + label + " set <世界名> <分数>", NamedTextColor.GRAY));
                return true;
            }

            String worldName;
            int scoreIndex;
            if (sender instanceof Player) {
                if (args.length >= 3) {
                    worldName = args[1];
                    scoreIndex = 2;
                } else {
                    Player p = (Player) sender;
                    worldName = p.getWorld().getName();
                    scoreIndex = 1;
                }
            } else {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("控制台用法：/" + label + " set <地牢世界名> <0-" + DungeonScoreboard.MAX_SCORE_TOTAL + ">",
                            NamedTextColor.RED));
                    return true;
                }
                worldName = args[1];
                scoreIndex = 2;
            }

            if (!plugin.isDungeonWorld(worldName)) {
                sender.sendMessage(Component.text("不是地牢世界：" + worldName, NamedTextColor.RED));
                return true;
            }

            int score;
            try {
                score = Integer.parseInt(args[scoreIndex]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("分数必须是整数。", NamedTextColor.RED));
                return true;
            }

            if (score < 0 || score > DungeonScoreboard.MAX_SCORE_TOTAL) {
                sender.sendMessage(Component.text("分数须在 0～" + DungeonScoreboard.MAX_SCORE_TOTAL + " 之间。", NamedTextColor.RED));
                return true;
            }

            board.setAdminTotalScoreOverride(worldName, score);
            int natural = board.computeNaturalTotalScore(worldName);
            sender.sendMessage(Component.text("已设定副本 ", NamedTextColor.GREEN)
                    .append(Component.text(worldName, NamedTextColor.WHITE))
                    .append(Component.text(" 显示总分为 ", NamedTextColor.GREEN))
                    .append(Component.text(String.valueOf(score), NamedTextColor.YELLOW))
                    .append(Component.text("（公式计算为 ", NamedTextColor.GREEN))
                    .append(Component.text(String.valueOf(natural), NamedTextColor.GRAY))
                    .append(Component.text("）", NamedTextColor.GREEN)));
            return true;
        }

        sender.sendMessage(Component.text("用法：/" + label + " | set <0-" + DungeonScoreboard.MAX_SCORE_TOTAL + "> | clear [世界名]",
                NamedTextColor.GRAY));
        return true;
    }

    /** 无参数查状态时：玩家用脚下世界；控制台需带世界名 */
    private String resolveWorldForStatus(CommandSender sender, String hint) {
        if (hint != null && !hint.isBlank()) return hint;
        if (sender instanceof Player p) {
            return p.getWorld().getName();
        }
        return null;
    }

    private String resolveWorld(CommandSender sender, String[] args, int worldArgIndex) {
        if (args.length > worldArgIndex) {
            String w = args[worldArgIndex];
            World bw = Bukkit.getWorld(w);
            if (bw == null) {
                sender.sendMessage(Component.text("找不到世界：" + w, NamedTextColor.RED));
                return null;
            }
            return bw.getName();
        }
        if (sender instanceof Player p) {
            return p.getWorld().getName();
        }
        sender.sendMessage(Component.text("请指定地牢世界名。", NamedTextColor.RED));
        return null;
    }

    private boolean sendStatus(CommandSender sender, DungeonScoreboard board, String worldName) {
        if (worldName == null) {
            sender.sendMessage(Component.text("控制台请写：/dungeon_team_score <世界名> 查看状态，或使用 set/clear。", NamedTextColor.RED));
            return true;
        }
        if (!plugin.isDungeonWorld(worldName)) {
            sender.sendMessage(Component.text("当前不在地牢世界（或世界未登记为副本）：" + worldName, NamedTextColor.RED));
            return true;
        }
        int natural = board.computeNaturalTotalScore(worldName);
        Integer ov = board.getAdminTotalScoreOverride(worldName);
        if (ov == null) {
            sender.sendMessage(Component.text("副本 ", NamedTextColor.GRAY)
                    .append(Component.text(worldName, NamedTextColor.WHITE))
                    .append(Component.text(" 当前总分（公式）：", NamedTextColor.GRAY))
                    .append(Component.text(natural, NamedTextColor.YELLOW))
                    .append(Component.text("/" + DungeonScoreboard.MAX_SCORE_TOTAL, NamedTextColor.DARK_GRAY)));
            sender.sendMessage(Component.text("未设置管理覆盖。使用 /dungeon_team_score set <分数> 可锁定显示总分。", NamedTextColor.DARK_GRAY));
        } else {
            sender.sendMessage(Component.text("副本 ", NamedTextColor.GRAY)
                    .append(Component.text(worldName, NamedTextColor.WHITE))
                    .append(Component.text("  公式：", NamedTextColor.GRAY))
                    .append(Component.text(natural, NamedTextColor.YELLOW))
                    .append(Component.text("  显示覆盖：", NamedTextColor.GRAY))
                    .append(Component.text(ov, NamedTextColor.GREEN))
                    .append(Component.text("/" + DungeonScoreboard.MAX_SCORE_TOTAL, NamedTextColor.DARK_GRAY)));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERM)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterPrefix(List.of("set", "clear"), args[0]);
        }
        if (args.length == 2 && "set".equalsIgnoreCase(args[0]) && sender instanceof Player) {
            List<String> worlds = new ArrayList<>();
            for (World w : Bukkit.getWorlds()) {
                if (w != null && plugin.isDungeonWorld(w.getName())) {
                    worlds.add(w.getName());
                }
            }
            return filterPrefix(worlds, args[1]);
        }
        return Collections.emptyList();
    }

    private static List<String> filterPrefix(List<String> opts, String prefix) {
        if (prefix == null || prefix.isEmpty()) return new ArrayList<>(opts);
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : opts) {
            if (o != null && o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }
}
