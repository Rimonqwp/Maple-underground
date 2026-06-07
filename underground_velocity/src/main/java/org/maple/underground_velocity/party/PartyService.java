package org.maple.underground_velocity.party;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PartyService {
    private final ProxyServer server;
    private final int maxPartySize;
    private final long inviteTimeoutMillis;

    private final Map<UUID, LinkedHashSet<UUID>> partyByLeader = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> leaderByMember = new ConcurrentHashMap<>();
    private final Map<UUID, Invite> pendingInvites = new ConcurrentHashMap<>();

    public PartyService(ProxyServer server, int maxPartySize, int inviteTimeoutSeconds) {
        this.server = server;
        this.maxPartySize = Math.max(1, Math.min(5, maxPartySize));
        this.inviteTimeoutMillis = Math.max(5L, inviteTimeoutSeconds) * 1000L;
    }

    public synchronized Result invite(Player inviter, Player target) {
        cleanupExpiredInvites();
        if (inviter.getUniqueId().equals(target.getUniqueId())) {
            return Result.fail("不能邀请自己。");
        }
        UUID inviterId = inviter.getUniqueId();
        UUID targetId = target.getUniqueId();
        UUID inviterLeader = leaderByMember.get(inviterId);
        if (inviterLeader != null && !inviterLeader.equals(inviterId)) {
            return Result.fail("只有队长可以邀请队员。");
        }
        if (leaderByMember.containsKey(targetId)) {
            return Result.fail(target.getUsername() + " 已经在队伍中。");
        }

        LinkedHashSet<UUID> party = partyByLeader.computeIfAbsent(inviterId, ignored -> {
            LinkedHashSet<UUID> created = new LinkedHashSet<>();
            created.add(inviterId);
            leaderByMember.put(inviterId, inviterId);
            return created;
        });
        if (party.size() >= maxPartySize) {
            return Result.fail("队伍已满，最多 " + maxPartySize + " 人。");
        }

        pendingInvites.put(targetId, new Invite(inviterId, targetId, System.currentTimeMillis() + inviteTimeoutMillis));
        return Result.ok("已邀请 " + target.getUsername() + " 加入队伍。");
    }

    public synchronized Result accept(Player accepter, Optional<Player> requestedLeader) {
        cleanupExpiredInvites();
        UUID accepterId = accepter.getUniqueId();
        Invite invite = pendingInvites.get(accepterId);
        if (invite == null) {
            return Result.fail("你没有待处理的组队邀请。");
        }
        if (requestedLeader.isPresent() && !requestedLeader.get().getUniqueId().equals(invite.inviterId())) {
            return Result.fail("你没有来自该玩家的邀请。");
        }
        if (leaderByMember.containsKey(accepterId)) {
            pendingInvites.remove(accepterId);
            return Result.fail("你已经在队伍中。");
        }

        LinkedHashSet<UUID> party = partyByLeader.get(invite.inviterId());
        if (party == null || party.size() >= maxPartySize) {
            pendingInvites.remove(accepterId);
            return Result.fail("该队伍不存在或已经满员。");
        }

        pendingInvites.remove(accepterId);
        party.add(accepterId);
        leaderByMember.put(accepterId, invite.inviterId());
        return Result.ok("你已加入 " + nameOf(invite.inviterId()) + " 的队伍。");
    }

    public synchronized Result decline(Player decliner, Optional<Player> requestedLeader) {
        cleanupExpiredInvites();
        UUID declinerId = decliner.getUniqueId();
        Invite invite = pendingInvites.get(declinerId);
        if (invite == null) {
            return Result.fail("你没有待处理的组队邀请。");
        }
        if (requestedLeader.isPresent() && !requestedLeader.get().getUniqueId().equals(invite.inviterId())) {
            return Result.fail("你没有来自该玩家的邀请。");
        }
        pendingInvites.remove(declinerId);
        return Result.ok("已拒绝组队邀请。");
    }

    public synchronized LeaveResult leave(UUID playerId) {
        UUID leaderId = leaderByMember.get(playerId);
        if (leaderId == null) {
            pendingInvites.remove(playerId);
            return new LeaveResult(false, null, List.of(), List.of(playerId));
        }

        LinkedHashSet<UUID> party = partyByLeader.get(leaderId);
        if (party == null) {
            leaderByMember.remove(playerId);
            return new LeaveResult(false, null, List.of(), List.of(playerId));
        }

        List<UUID> oldMembers = new ArrayList<>(party);
        party.remove(playerId);
        leaderByMember.remove(playerId);

        if (party.isEmpty()) {
            partyByLeader.remove(leaderId);
            return new LeaveResult(true, null, oldMembers, List.of(playerId));
        }

        if (leaderId.equals(playerId)) {
            UUID newLeader = party.iterator().next();
            partyByLeader.remove(leaderId);
            partyByLeader.put(newLeader, party);
            for (UUID member : party) {
                leaderByMember.put(member, newLeader);
            }
            return new LeaveResult(true, newLeader, oldMembers, List.of(playerId));
        }

        if (party.size() <= 1) {
            for (UUID member : party) {
                leaderByMember.remove(member);
            }
            partyByLeader.remove(leaderId);
        }
        return new LeaveResult(true, leaderId, oldMembers, List.of(playerId));
    }

    public synchronized void removePlayer(UUID playerId) {
        pendingInvites.remove(playerId);
        pendingInvites.entrySet().removeIf(entry -> entry.getValue().inviterId().equals(playerId));
        leave(playerId);
    }

    public synchronized UUID getLeaderId(UUID playerId) {
        return leaderByMember.get(playerId);
    }

    public synchronized List<UUID> getPartyMemberIds(UUID playerId) {
        UUID leaderId = leaderByMember.get(playerId);
        if (leaderId == null) {
            return List.of(playerId);
        }
        LinkedHashSet<UUID> party = partyByLeader.get(leaderId);
        if (party == null || party.isEmpty()) {
            return List.of(playerId);
        }
        return List.copyOf(party);
    }

    public List<PartyMember> getOnlinePartyMembers(UUID playerId) {
        List<PartyMember> members = new ArrayList<>();
        for (UUID id : getPartyMemberIds(playerId)) {
            Optional<Player> player = server.getPlayer(id);
            player.ifPresent(value -> members.add(new PartyMember(id, value.getUsername())));
        }
        return members;
    }

    public synchronized Optional<UUID> getPendingInviter(UUID playerId) {
        cleanupExpiredInvites();
        Invite invite = pendingInvites.get(playerId);
        return invite == null ? Optional.empty() : Optional.of(invite.inviterId());
    }

    public synchronized boolean isLeaderOrSolo(UUID playerId) {
        UUID leaderId = leaderByMember.get(playerId);
        return leaderId == null || leaderId.equals(playerId);
    }

    public String describeParty(UUID playerId) {
        List<UUID> members = getPartyMemberIds(playerId);
        UUID leaderId = getLeaderId(playerId);
        if (leaderId == null) {
            return "当前没有队伍。";
        }
        List<String> names = new ArrayList<>();
        for (UUID member : members) {
            String suffix = member.equals(leaderId) ? "（队长）" : "";
            names.add(nameOf(member) + suffix);
        }
        return "当前队伍：" + String.join("、", names);
    }

    public List<String> onlinePlayerNamesExcept(UUID playerId) {
        Collection<Player> players = server.getAllPlayers();
        List<String> out = new ArrayList<>();
        for (Player player : players) {
            if (!player.getUniqueId().equals(playerId)) {
                out.add(player.getUsername());
            }
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public List<UUID> onlineMissingMembers(UUID playerId) {
        List<UUID> missing = new ArrayList<>();
        for (UUID member : getPartyMemberIds(playerId)) {
            if (server.getPlayer(member).isEmpty()) {
                missing.add(member);
            }
        }
        return missing;
    }

    public void broadcast(List<UUID> memberIds, String message) {
        Set<UUID> seen = new HashSet<>(memberIds);
        for (UUID member : seen) {
            server.getPlayer(member).ifPresent(player -> player.sendPlainMessage(message));
        }
    }

    private synchronized void cleanupExpiredInvites() {
        long now = System.currentTimeMillis();
        pendingInvites.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= now);
    }

    private String nameOf(UUID playerId) {
        return server.getPlayer(playerId).map(Player::getUsername).orElse(playerId.toString());
    }

    public record Result(boolean success, String message) {
        public static Result ok(String message) {
            return new Result(true, message);
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }

    public record LeaveResult(boolean hadParty, UUID newLeaderId, List<UUID> oldMembers, List<UUID> removedMembers) {
    }

    private record Invite(UUID inviterId, UUID targetId, long expiresAtMillis) {
    }
}
