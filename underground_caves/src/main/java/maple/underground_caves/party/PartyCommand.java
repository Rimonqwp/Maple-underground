package maple.underground_caves.party;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
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
import java.util.UUID;
import java.util.stream.Collectors;

public final class PartyCommand implements CommandExecutor, TabCompleter {
    private final PartyManager partyManager;

    public PartyCommand(PartyManager partyManager) {
        this.partyManager = partyManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("只有玩家可以使用此指令。", NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            sendUsage(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "invite", "i" -> handleInvite(player, args);
            case "accept", "a" -> handleAccept(player, args);
            case "decline", "d" -> handleDecline(player, args);
            case "leave", "l" -> handleLeave(player);
            case "disband" -> handleDisband(player);
            default -> sendUsage(player);
        }
        return true;
    }

    private void handleInvite(Player inviter, String[] args) {
        if (args.length < 2) {
            inviter.sendMessage(Component.text("用法: /p invite <玩家ID>", NamedTextColor.RED));
            return;
        }
        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            inviter.sendMessage(Component.text("找不到玩家: " + targetName, NamedTextColor.RED));
            return;
        }

        String err = partyManager.invite(inviter, target);
        if (err != null) {
            inviter.sendMessage(Component.text(err, NamedTextColor.RED));
            return;
        }

        inviter.sendMessage(Component.text("已邀請 ", NamedTextColor.GREEN)
                .append(Component.text(target.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" 加入隊伍。", NamedTextColor.GREEN)));

        // 被邀請者：彈出可點擊的 [接受] [拒絕]
        Component inviteMsg = Component.text(inviter.getName(), NamedTextColor.YELLOW)
                .append(Component.text(" 邀請你加入隊伍 ", NamedTextColor.GRAY))
                .append(Component.text("[接受]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/p accept " + inviter.getName())))
                .append(Component.text(" ", NamedTextColor.GRAY))
                .append(Component.text("[拒絕]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/p decline " + inviter.getName())));
        target.sendMessage(inviteMsg);
    }

    private void handleAccept(Player accepter, String[] args) {
        Player onlineLeader = null;
        UUID leaderId;
        String leaderName;
        if (args.length >= 2) {
            onlineLeader = Bukkit.getPlayer(args[1]);
            UUID pendingInviter = partyManager.getPendingInviter(accepter);
            String pendingName = partyManager.getPendingInviterName(accepter);
            if (onlineLeader != null) {
                leaderId = onlineLeader.getUniqueId();
                leaderName = onlineLeader.getName();
            } else if (pendingInviter != null && pendingName != null && pendingName.equalsIgnoreCase(args[1])) {
                leaderId = pendingInviter;
                leaderName = pendingName;
            } else {
                accepter.sendMessage(Component.text("沒有來自該玩家的待接受邀請。", NamedTextColor.RED));
                return;
            }
        } else {
            leaderId = partyManager.getPendingInviter(accepter);
            leaderName = partyManager.getPendingInviterName(accepter);
        }
        if (leaderId == null) {
            accepter.sendMessage(Component.text("沒有待接受的邀請，或邀請已過期。", NamedTextColor.RED));
            return;
        }
        if (leaderName == null || leaderName.isBlank()) leaderName = leaderId.toString();

        String err = partyManager.accept(accepter, leaderId, leaderName);
        if (err != null) {
            accepter.sendMessage(Component.text(err, NamedTextColor.RED));
            return;
        }

        accepter.sendMessage(Component.text("你已加入 ", NamedTextColor.GREEN)
                .append(Component.text(leaderName, NamedTextColor.YELLOW))
                .append(Component.text(" 的隊伍。", NamedTextColor.GREEN)));
        if (onlineLeader != null && onlineLeader.isOnline()) {
            onlineLeader.sendMessage(Component.text(accepter.getName(), NamedTextColor.YELLOW)
                    .append(Component.text(" 已加入隊伍。", NamedTextColor.GREEN)));
        }
    }

    private void handleDecline(Player decliner, String[] args) {
        UUID leaderId;
        if (args.length >= 2) {
            Player leader = Bukkit.getPlayer(args[1]);
            UUID pendingInviter = partyManager.getPendingInviter(decliner);
            String pendingName = partyManager.getPendingInviterName(decliner);
            if (leader != null) {
                leaderId = leader.getUniqueId();
            } else if (pendingInviter != null && pendingName != null && pendingName.equalsIgnoreCase(args[1])) {
                leaderId = pendingInviter;
            } else {
                decliner.sendMessage(Component.text("沒有來自該玩家的待拒絕邀請。", NamedTextColor.RED));
                return;
            }
        } else {
            leaderId = partyManager.getPendingInviter(decliner);
        }
        if (leaderId == null) {
            decliner.sendMessage(Component.text("沒有待拒絕的邀請，或邀請已過期。", NamedTextColor.RED));
            return;
        }
        if (partyManager.decline(decliner, leaderId)) {
            decliner.sendMessage(Component.text("已拒絕邀請。", NamedTextColor.GRAY));
        } else {
            decliner.sendMessage(Component.text("沒有符合的待拒絕邀請。", NamedTextColor.RED));
        }
    }

    private void handleLeave(Player player) {
        PartyManager.LeaveResult result = partyManager.leave(player);
        if (result.disbanded()) {
            player.sendMessage(Component.text("隊伍已解散。", NamedTextColor.GRAY));
            notifyOldMembers(player, result.oldMembers(), player.getName() + " 已解散隊伍。");
        } else {
            player.sendMessage(Component.text("你已離開隊伍。", NamedTextColor.GRAY));
            notifyOldMembers(player, result.oldMembers(), player.getName() + " 已離開隊伍。");
        }
    }

    private void handleDisband(Player player) {
        PartyManager.LeaveResult result = partyManager.disband(player);
        if (!result.hadParty()) {
            player.sendMessage(Component.text("只有隊長可以解散隊伍。", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("隊伍已解散。", NamedTextColor.GRAY));
        notifyOldMembers(player, result.oldMembers(), player.getName() + " 已解散隊伍。");
    }

    private void sendUsage(Player player) {
        player.sendMessage(Component.text("用法: /p invite <ID> | accept [ID] | decline [ID] | leave | disband", NamedTextColor.GRAY));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) return List.of();
        if (args.length == 1) {
            return filterPrefix(List.of("invite", "accept", "decline", "leave", "disband"), args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("invite".equals(sub) || "accept".equals(sub) || "decline".equals(sub)) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> !n.equalsIgnoreCase(sender.getName()))
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .limit(20)
                        .collect(Collectors.toList());
            }
        }
        return List.of();
    }

    private static List<String> filterPrefix(List<String> list, String prefix) {
        if (prefix == null || prefix.isEmpty()) return list;
        String low = prefix.toLowerCase(Locale.ROOT);
        return list.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(low))
                .collect(Collectors.toList());
    }

    private static void notifyOldMembers(Player actor, List<UUID> oldMembers, String message) {
        for (UUID memberId : oldMembers) {
            if (memberId.equals(actor.getUniqueId())) continue;
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(Component.text(message, NamedTextColor.GRAY));
            }
        }
    }
}
