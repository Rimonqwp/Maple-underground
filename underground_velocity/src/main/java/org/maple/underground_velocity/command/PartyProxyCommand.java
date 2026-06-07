package org.maple.underground_velocity.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.maple.underground_velocity.party.PartyService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class PartyProxyCommand implements SimpleCommand {
    private final ProxyServer server;
    private final PartyService partyService;

    public PartyProxyCommand(ProxyServer server, PartyService partyService) {
        this.server = server;
        this.partyService = partyService;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendPlainMessage("只有玩家可以使用这个命令。");
            return;
        }

        String[] args = invocation.arguments();
        if (args.length == 0) {
            sendUsage(player);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "invite", "i" -> handleInvite(player, args);
            case "accept", "a" -> handleAccept(player, args);
            case "decline", "d" -> handleDecline(player, args);
            case "leave", "l" -> handleLeave(player);
            case "list", "info" -> player.sendPlainMessage(partyService.describeParty(player.getUniqueId()));
            default -> sendUsage(player);
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            return List.of();
        }
        String[] args = invocation.arguments();
        if (args.length == 0) {
            return List.of("invite", "accept", "decline", "leave", "list");
        }
        if (args.length == 1) {
            return filter(List.of("invite", "accept", "decline", "leave", "list"), args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && ("invite".equals(sub) || "i".equals(sub))) {
            return filter(partyService.onlinePlayerNamesExcept(player.getUniqueId()), args[1]);
        }
        if (args.length == 2 && ("accept".equals(sub) || "a".equals(sub) || "decline".equals(sub) || "d".equals(sub))) {
            Optional<UUID> inviter = partyService.getPendingInviter(player.getUniqueId());
            if (inviter.isPresent()) {
                return server.getPlayer(inviter.get())
                        .map(Player::getUsername)
                        .map(List::of)
                        .map(list -> filter(list, args[1]))
                        .orElseGet(List::of);
            }
        }
        return List.of();
    }

    private void handleInvite(Player inviter, String[] args) {
        if (args.length < 2) {
            inviter.sendPlainMessage("用法：/p invite <玩家>");
            return;
        }
        Optional<Player> target = server.getPlayer(args[1]);
        if (target.isEmpty()) {
            inviter.sendPlainMessage("找不到在线玩家：" + args[1]);
            return;
        }

        PartyService.Result result = partyService.invite(inviter, target.get());
        inviter.sendPlainMessage(result.message());
        if (!result.success()) {
            return;
        }

        Component inviteMessage = Component.text(inviter.getUsername(), NamedTextColor.YELLOW)
                .append(Component.text(" 邀请你加入队伍 ", NamedTextColor.GRAY))
                .append(Component.text("[接受]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/p accept " + inviter.getUsername())))
                .append(Component.text(" "))
                .append(Component.text("[拒绝]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/p decline " + inviter.getUsername())));
        target.get().sendMessage(inviteMessage);
    }

    private void handleAccept(Player accepter, String[] args) {
        Optional<Player> requestedLeader = Optional.empty();
        if (args.length >= 2) {
            requestedLeader = server.getPlayer(args[1]);
            if (requestedLeader.isEmpty()) {
                accepter.sendPlainMessage("找不到在线玩家：" + args[1]);
                return;
            }
        }

        PartyService.Result result = partyService.accept(accepter, requestedLeader);
        accepter.sendPlainMessage(result.message());
        if (!result.success()) {
            return;
        }
        List<UUID> members = partyService.getPartyMemberIds(accepter.getUniqueId());
        partyService.broadcast(members, accepter.getUsername() + " 已加入队伍。");
    }

    private void handleDecline(Player decliner, String[] args) {
        Optional<Player> requestedLeader = Optional.empty();
        if (args.length >= 2) {
            requestedLeader = server.getPlayer(args[1]);
            if (requestedLeader.isEmpty()) {
                decliner.sendPlainMessage("找不到在线玩家：" + args[1]);
                return;
            }
        }
        PartyService.Result result = partyService.decline(decliner, requestedLeader);
        decliner.sendPlainMessage(result.message());
    }

    private void handleLeave(Player player) {
        PartyService.LeaveResult result = partyService.leave(player.getUniqueId());
        player.sendPlainMessage("你已离开队伍。");
        if (!result.hadParty()) {
            return;
        }
        List<UUID> notify = new ArrayList<>(result.oldMembers());
        notify.remove(player.getUniqueId());
        if (!notify.isEmpty()) {
            String suffix = result.newLeaderId() != null
                    ? " 新队长是 " + server.getPlayer(result.newLeaderId()).map(Player::getUsername).orElse(result.newLeaderId().toString()) + "。"
                    : "";
            partyService.broadcast(notify, player.getUsername() + " 已离开队伍。" + suffix);
        }
    }

    private void sendUsage(Player player) {
        player.sendPlainMessage("用法：/p invite <玩家> | accept [玩家] | decline [玩家] | leave | list");
    }

    private static List<String> filter(List<String> values, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return values;
        }
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }
}
