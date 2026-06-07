package maple.underground_caves.party;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 組隊系統：邀請、接受、成員管理。
 */
public final class PartyManager {
    /** 隊長 -> 隊伍成員 (含隊長) */
    private final Map<UUID, Set<UUID>> partyByLeader = new ConcurrentHashMap<>();
    /** 玩家 -> 所屬隊伍的隊長 */
    private final Map<UUID, UUID> leaderByMember = new ConcurrentHashMap<>();
    /** 被邀請者 -> 邀請者 (pending) */
    private final Map<UUID, UUID> pendingInvites = new ConcurrentHashMap<>();
    /** 玩家 -> 職業 (HEALER, MAGE, WARRIOR, ARCHER, TANK) */
    private final Map<UUID, String> playerRole = new ConcurrentHashMap<>();
    /** 玩家 -> 是否準備 (用於職業選擇 GUI) */
    private final Map<UUID, Boolean> readyByMember = new ConcurrentHashMap<>();
    /** 開啟自動準備時，玩家手動點擊取消準備的狀態 (不關閉自動準備) */
    private final Map<UUID, Boolean> explicitlyCancelled = new ConcurrentHashMap<>();
    private volatile PlayerAutoReadyDB autoReadyDB;
    /** 若設定則優先使用（MySQL underground_caves long_term_ready＝自動準備偏好，開門後仍保留） */
    private volatile maple.underground_caves.dungeon.PlayerDungeonSettingsRemote dungeonSettingsRemote;

    public static final String ROLE_HEALER = "HEALER";
    public static final String ROLE_MAGE = "MAGE";
    public static final String ROLE_WARRIOR = "WARRIOR";
    public static final String ROLE_ARCHER = "ARCHER";
    public static final String ROLE_TANK = "TANK";

    /** 邀請玩家加入隊伍。返回錯誤訊息或 null 表示成功 */
    public String invite(Player inviter, Player target) {
        if (inviter == null || target == null) return "無效的目標";
        if (inviter.getUniqueId().equals(target.getUniqueId())) return "不能邀請自己";

        UUID inviterId = inviter.getUniqueId();
        UUID targetId = target.getUniqueId();

        // 若邀請者已在別隊且非隊長，不能邀請
        UUID inviterLeader = leaderByMember.get(inviterId);
        if (inviterLeader != null && !inviterLeader.equals(inviterId)) {
            return "你必須是隊長才能邀請隊友";
        }

        // 若目標已在隊伍中
        if (leaderByMember.containsKey(targetId)) {
            return target.getName() + " 已在其他隊伍中";
        }

        // 若目標已有來自他人的邀請，先移除
        pendingInvites.remove(targetId);

        // 邀請者創建/取得隊伍
        Set<UUID> party = partyByLeader.get(inviterId);
        if (party == null) {
            party = new HashSet<>();
            party.add(inviterId);
            partyByLeader.put(inviterId, party);
            leaderByMember.put(inviterId, inviterId);
        }

        if (party.size() >= 5) return "隊伍已滿 (最多 5 人)";

        pendingInvites.put(targetId, inviterId);
        return null;
    }

    /** 接受邀請。返回錯誤訊息或 null 表示成功 */
    public String accept(Player accepter, Player leader) {
        if (accepter == null || leader == null) return "無效的目標";
        UUID accepterId = accepter.getUniqueId();
        UUID leaderId = leader.getUniqueId();

        if (!leaderId.equals(pendingInvites.get(accepterId))) {
            return "你沒有來自 " + leader.getName() + " 的邀請";
        }

        pendingInvites.remove(accepterId);

        if (leaderByMember.containsKey(accepterId)) {
            return "你已在其他隊伍中";
        }

        Set<UUID> party = partyByLeader.get(leaderId);
        if (party == null || party.size() >= 5) {
            return "該隊伍已滿或不存在";
        }

        party.add(accepterId);
        leaderByMember.put(accepterId, leaderId);
        return null;
    }

    /** 拒絕邀請 */
    public void decline(Player decliner, UUID leaderId) {
        if (decliner == null) return;
        if (leaderId.equals(pendingInvites.get(decliner.getUniqueId()))) {
            pendingInvites.remove(decliner.getUniqueId());
        }
    }

    /** 取得待接受的邀請者 UUID */
    public UUID getPendingInviter(Player player) {
        return player == null ? null : pendingInvites.get(player.getUniqueId());
    }

    /** 取得玩家的隊伍成員（含自己），若無則只含自己。隊長為第一個。 */
    public synchronized void setPartySnapshot(UUID leaderId, List<UUID> memberIds) {
        if (leaderId == null || memberIds == null || memberIds.isEmpty()) return;
        LinkedHashSet<UUID> normalized = new LinkedHashSet<>();
        normalized.add(leaderId);
        for (UUID memberId : memberIds) {
            if (memberId != null) normalized.add(memberId);
            if (normalized.size() >= 5) break;
        }

        for (UUID memberId : normalized) {
            UUID oldLeader = leaderByMember.remove(memberId);
            if (oldLeader != null) {
                Set<UUID> oldParty = partyByLeader.get(oldLeader);
                if (oldParty != null) {
                    oldParty.remove(memberId);
                    if (oldParty.size() <= 1) {
                        partyByLeader.remove(oldLeader);
                        for (UUID oldMember : oldParty) {
                            leaderByMember.remove(oldMember);
                        }
                    }
                }
            }
            pendingInvites.remove(memberId);
        }

        Set<UUID> party = ConcurrentHashMap.newKeySet();
        party.addAll(normalized);
        partyByLeader.put(leaderId, party);
        for (UUID memberId : normalized) {
            leaderByMember.put(memberId, leaderId);
        }
    }

    public List<UUID> getPartyMembers(Player player) {
        if (player == null) return List.of();
        UUID id = player.getUniqueId();
        UUID leaderId = leaderByMember.get(id);
        if (leaderId == null) {
            return List.of(id);
        }
        Set<UUID> party = partyByLeader.get(leaderId);
        if (party == null) return List.of(id);
        List<UUID> list = new ArrayList<>(party);
        list.sort((a, b) -> a.equals(leaderId) ? -1 : (b.equals(leaderId) ? 1 : a.compareTo(b)));
        return list;
    }

    /** 取得隊長，若無則回傳自己 */
    public Player getLeader(Player player) {
        if (player == null) return null;
        UUID leaderId = leaderByMember.get(player.getUniqueId());
        if (leaderId == null) return player;
        Player p = Bukkit.getPlayer(leaderId);
        return p != null ? p : player;
    }

    /** 離開隊伍 */
    public void leave(Player player) {
        if (player == null) return;
        UUID id = player.getUniqueId();
        UUID leaderId = leaderByMember.remove(id);
        if (leaderId == null) return;

        Set<UUID> party = partyByLeader.get(leaderId);
        if (party != null) {
            party.remove(id);
            if (party.size() <= 1) {
                partyByLeader.remove(leaderId);
                if (!party.isEmpty()) leaderByMember.remove(party.iterator().next());
            }
        }
        playerRole.remove(id);
        readyByMember.remove(id);
        explicitlyCancelled.remove(id);
    }

    public void setPlayerAutoReadyDB(PlayerAutoReadyDB db) {
        this.autoReadyDB = db;
    }

    /** 設定長期準備遠端（MySQL underground_caves）；設定後 isAutoReady/setAutoReady 會讀寫此處 */
    public void setDungeonSettingsRemote(maple.underground_caves.dungeon.PlayerDungeonSettingsRemote remote) {
        this.dungeonSettingsRemote = remote;
    }

    /**
     * 每局地牢準備階段開始時呼叫：清除本局手動準備與「自動準備下暫時取消」，不影響 MySQL 自動準備開關。
     */
    public void resetReadyStateForNewDungeonRun(List<UUID> memberIds) {
        if (memberIds == null) return;
        for (UUID id : memberIds) {
            if (id == null) continue;
            explicitlyCancelled.remove(id);
            readyByMember.remove(id);
        }
    }

    /** 取得玩家是否開啟自動準備（優先讀 MySQL 長期準備，否則讀原 autoReadyDB） */
    public boolean isAutoReady(UUID playerId) {
        if (playerId == null) return false;
        if (dungeonSettingsRemote != null) return dungeonSettingsRemote.isLongTermReady(playerId);
        if (autoReadyDB != null) return autoReadyDB.isAutoReady(playerId);
        return false;
    }

    /** 設定玩家自動準備（寫入 MySQL 長期準備或原 autoReadyDB） */
    public void setAutoReady(UUID playerId, boolean autoReady) {
        if (playerId == null) return;
        if (dungeonSettingsRemote != null) dungeonSettingsRemote.setLongTermReady(playerId, autoReady);
        else if (autoReadyDB != null) autoReadyDB.setAutoReady(playerId, autoReady);
        if (!autoReady) explicitlyCancelled.remove(playerId);
    }

    /** 取得顯示用的準備狀態：自動準備開啟且未手動取消 → 綠；否則看手動準備 */
    public boolean isEffectiveReady(UUID playerId) {
        if (playerId == null) return false;
        if (isAutoReady(playerId)) return !Boolean.TRUE.equals(explicitlyCancelled.get(playerId));
        return Boolean.TRUE.equals(readyByMember.get(playerId));
    }

    /** 玩家手動取消準備 (當自動準備開啟時點擊綠色) */
    public void setExplicitlyCancelled(UUID playerId, boolean cancelled) {
        if (playerId != null) {
            if (cancelled) explicitlyCancelled.put(playerId, true);
            else explicitlyCancelled.remove(playerId);
        }
    }

    /** 設定玩家手動準備狀態 */
    public void setReady(Player player, boolean ready) {
        if (player != null) readyByMember.put(player.getUniqueId(), ready);
    }

    /** 取得玩家手動準備狀態 (不含自動準備) */
    public boolean isReady(UUID playerId) {
        return playerId != null && Boolean.TRUE.equals(readyByMember.get(playerId));
    }

    /** 設定玩家職業 */
    public void setRole(Player player, String role) {
        if (player != null && role != null) playerRole.put(player.getUniqueId(), role);
    }

    /** 取得玩家職業 */
    public String getRole(Player player) {
        return player == null ? null : playerRole.get(player.getUniqueId());
    }

    /** 取得該職業在隊伍中是否有人選擇（含自己） */
    public boolean isRoleTakenByParty(Player player, String role) {
        if (player == null || role == null) return false;
        for (UUID id : getPartyMembers(player)) {
            if (role.equals(playerRole.get(id))) return true;
        }
        return false;
    }
}
