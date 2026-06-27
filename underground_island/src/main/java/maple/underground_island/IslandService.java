package maple.underground_island;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

final class IslandService {
    private final JavaPlugin plugin;
    private final IslandRepository repository;
    private final IslandWorldService worldService;
    private final ExecutorService databaseExecutor;
    private final CompletableFuture<Void> databaseReady;
    private final Map<UUID, Map<UUID, IslandInvite>> invitesByTarget = new LinkedHashMap<>();
    private final long inviteTimeoutMillis;

    IslandService(JavaPlugin plugin, IslandRepository repository, IslandWorldService worldService) {
        this.plugin = plugin;
        this.repository = repository;
        this.worldService = worldService;
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "underground-island-db");
            thread.setDaemon(true);
            return thread;
        };
        this.databaseExecutor = Executors.newFixedThreadPool(2, threadFactory);
        this.databaseReady = CompletableFuture.runAsync(repository::init, databaseExecutor);
        long seconds = Math.max(15L, plugin.getConfig().getLong("invite-timeout-seconds", 180L));
        this.inviteTimeoutMillis = seconds * 1000L;
    }

    void shutdown() {
        databaseExecutor.shutdownNow();
        synchronized (invitesByTarget) {
            invitesByTarget.clear();
        }
    }

    void prepareOnJoin(Player player) {
        if (player == null) {
            return;
        }
        ensureIsland(player.getUniqueId()).whenComplete((result, error) -> runSync(() -> {
            if (!isPlayerAvailable(player)) {
                return;
            }
            if (error != null) {
                sendDatabaseError(player, error);
                return;
            }
            teleportToIsland(player, result.island(), result.created());
        }));
    }

    void home(Player player) {
        if (player == null) {
            return;
        }
        ensureIsland(player.getUniqueId()).whenComplete((result, error) -> runSync(() -> {
            if (!isPlayerAvailable(player)) {
                return;
            }
            if (error != null) {
                sendDatabaseError(player, error);
                return;
            }
            teleportToIsland(player, result.island(), result.created());
        }));
    }

    void invite(Player owner, Player target) {
        if (owner == null || target == null) {
            return;
        }
        if (!owner.hasPermission("underground_island.invite")) {
            owner.sendMessage(Component.text("你没有邀请岛屿成员的权限。", NamedTextColor.RED));
            return;
        }
        if (owner.getUniqueId().equals(target.getUniqueId())) {
            owner.sendMessage(Component.text("不能邀请自己加入自己的岛屿。", NamedTextColor.RED));
            return;
        }
        ensureIsland(owner.getUniqueId()).whenComplete((result, error) -> runSync(() -> {
            if (!isPlayerAvailable(owner)) {
                return;
            }
            if (error != null) {
                sendDatabaseError(owner, error);
                return;
            }
            IslandRecord island = result.island();
            if (!island.isOwner(owner.getUniqueId())) {
                owner.sendMessage(Component.text("只有岛主可以邀请 coop 成员。", NamedTextColor.RED));
                return;
            }
            if (!isPlayerAvailable(target)) {
                owner.sendMessage(Component.text("对方已经离线。", NamedTextColor.RED));
                return;
            }
            IslandInvite invite = new IslandInvite(owner.getUniqueId(), owner.getName(), System.currentTimeMillis());
            synchronized (invitesByTarget) {
                invitesByTarget.computeIfAbsent(target.getUniqueId(), ignored -> new LinkedHashMap<>())
                        .put(owner.getUniqueId(), invite);
            }
            owner.sendMessage(Component.text("已邀请 ", NamedTextColor.GREEN)
                    .append(Component.text(target.getName(), NamedTextColor.YELLOW))
                    .append(Component.text(" 加入你的岛屿。", NamedTextColor.GREEN)));
            target.sendMessage(Component.text(owner.getName(), NamedTextColor.YELLOW)
                    .append(Component.text(" 邀请你加入岛屿 coop ", NamedTextColor.GRAY))
                    .append(Component.text("[接受]", NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.runCommand("/island accept " + owner.getName()))));
        }));
    }

    void accept(Player player, String ownerNameOrId) {
        if (player == null) {
            return;
        }
        IslandInvite invite = findInvite(player.getUniqueId(), ownerNameOrId);
        if (invite == null) {
            player.sendMessage(Component.text("没有找到对应的岛屿邀请，或邀请已过期。", NamedTextColor.RED));
            return;
        }

        databaseReady.thenApplyAsync(ignored ->
                repository.joinCoop(player.getUniqueId(), invite.ownerId()), databaseExecutor
        ).whenComplete((result, error) -> runSync(() -> {
            if (!isPlayerAvailable(player)) {
                return;
            }
            if (error != null) {
                sendDatabaseError(player, error);
                return;
            }
            removeInvite(player.getUniqueId(), invite.ownerId());
            if (result.alreadyMember()) {
                player.sendMessage(Component.text("你已经在这个岛屿 coop 里了。", NamedTextColor.YELLOW));
            } else {
                player.sendMessage(Component.text("你已加入 ", NamedTextColor.GREEN)
                        .append(Component.text(invite.ownerName(), NamedTextColor.YELLOW))
                        .append(Component.text(" 的岛屿。", NamedTextColor.GREEN)));
            }

            if (result.deletedWorldName() != null && !result.deletedWorldName().equals(result.island().worldName())) {
                worldService.deleteIslandWorld(result.deletedWorldName());
                notifyEvictedMembers(player.getUniqueId(), result.evictedMembers());
            }
            teleportToIsland(player, result.island(), false);
            Player owner = Bukkit.getPlayer(invite.ownerId());
            if (owner != null && owner.isOnline()) {
                owner.sendMessage(Component.text(player.getName(), NamedTextColor.YELLOW)
                        .append(Component.text(" 已加入你的岛屿 coop。", NamedTextColor.GREEN)));
            }
        }));
    }

    void info(Player player) {
        if (player == null) {
            return;
        }
        databaseReady.thenApplyAsync(ignored ->
                repository.getActiveIsland(player.getUniqueId()), databaseExecutor
        ).whenComplete((island, error) -> runSync(() -> {
            if (!isPlayerAvailable(player)) {
                return;
            }
            if (error != null) {
                sendDatabaseError(player, error);
                return;
            }
            if (island == null) {
                player.sendMessage(Component.text("你还没有岛屿，正在为你创建。", NamedTextColor.YELLOW));
                home(player);
                return;
            }
            player.sendMessage(Component.text("岛屿世界: ", NamedTextColor.GRAY)
                    .append(Component.text(island.worldName(), NamedTextColor.YELLOW)));
            player.sendMessage(Component.text("角色: ", NamedTextColor.GRAY)
                    .append(Component.text(island.memberRole(), NamedTextColor.YELLOW)));
            player.sendMessage(Component.text("OneBlock 已挖掘: ", NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(island.blocksMined()), NamedTextColor.AQUA)));
        }));
    }

    void recordOneBlockMine(IslandRecord island) {
        if (island == null) {
            return;
        }
        databaseReady.thenApplyAsync(ignored ->
                repository.recordOneBlockMined(island.islandId()), databaseExecutor
        ).whenComplete((result, error) -> runSync(() -> {
            if (error != null) {
                plugin.getLogger().warning("[Island] Failed to record OneBlock mine: " + error.getMessage());
                return;
            }
            worldService.updateMineState(island, result);
        }));
    }

    void recordOneBlockMineAndReward(IslandRecord island, Runnable rewardAction) {
        if (island == null) {
            return;
        }
        databaseReady.thenApplyAsync(ignored ->
                repository.recordOneBlockMined(island.islandId()), databaseExecutor
        ).whenComplete((result, error) -> runSync(() -> {
            if (error != null) {
                plugin.getLogger().warning("[Island] Failed to record OneBlock mine: " + error.getMessage());
                return;
            }
            worldService.updateMineState(island, result);
            if (result.spawnPortalReward() && rewardAction != null) {
                rewardAction.run();
            }
        }));
    }

    private CompletableFuture<IslandRepository.EnsureIslandResult> ensureIsland(UUID playerId) {
        return databaseReady.thenApplyAsync(ignored -> repository.ensureIsland(playerId), databaseExecutor);
    }

    private void teleportToIsland(Player player, IslandRecord island, boolean created) {
        World world;
        try {
            world = worldService.ensureWorld(island);
        } catch (RuntimeException e) {
            player.sendMessage(Component.text("岛屿世界创建失败，请联系管理员。", NamedTextColor.RED));
            plugin.getLogger().warning("[Island] Failed to ensure world " + island.worldName() + ": " + e.getMessage());
            return;
        }
        player.teleport(worldService.homeLocation(island, world));
        if (created) {
            player.sendMessage(Component.text("你的 OneBlock 岛屿已创建。", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("已传送到岛屿。", NamedTextColor.GREEN));
        }
    }

    private IslandInvite findInvite(UUID targetId, String ownerNameOrId) {
        long now = System.currentTimeMillis();
        synchronized (invitesByTarget) {
            Map<UUID, IslandInvite> invites = invitesByTarget.get(targetId);
            if (invites == null || invites.isEmpty()) {
                return null;
            }
            invites.entrySet().removeIf(entry -> now - entry.getValue().createdAtMillis() > inviteTimeoutMillis);
            if (invites.isEmpty()) {
                invitesByTarget.remove(targetId);
                return null;
            }
            if (ownerNameOrId == null || ownerNameOrId.isBlank()) {
                return invites.size() == 1 ? invites.values().iterator().next() : null;
            }
            String normalized = ownerNameOrId.trim().toLowerCase(Locale.ROOT);
            for (IslandInvite invite : invites.values()) {
                if (invite.ownerName().toLowerCase(Locale.ROOT).equals(normalized)
                        || invite.ownerId().toString().equalsIgnoreCase(normalized)) {
                    return invite;
                }
            }
        }
        return null;
    }

    private void removeInvite(UUID targetId, UUID ownerId) {
        synchronized (invitesByTarget) {
            Map<UUID, IslandInvite> invites = invitesByTarget.get(targetId);
            if (invites == null) {
                return;
            }
            invites.remove(ownerId);
            if (invites.isEmpty()) {
                invitesByTarget.remove(targetId);
            }
        }
    }

    private void notifyEvictedMembers(UUID accepterId, List<UUID> evictedMembers) {
        if (evictedMembers == null || evictedMembers.isEmpty()) {
            return;
        }
        for (UUID memberId : evictedMembers) {
            if (memberId == null || memberId.equals(accepterId)) {
                continue;
            }
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(Component.text("原岛主加入了其他 coop，旧岛屿已删除。使用 /island home 可创建或进入你的新岛屿。", NamedTextColor.YELLOW));
            }
        }
    }

    private void sendDatabaseError(Player player, Throwable error) {
        player.sendMessage(Component.text("岛屿数据库暂时不可用，请稍后再试。", NamedTextColor.RED));
        plugin.getLogger().warning("[Island] Database operation failed: " + error.getMessage());
    }

    private boolean isPlayerAvailable(Player player) {
        return player != null && player.isOnline();
    }

    private void runSync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private record IslandInvite(UUID ownerId, String ownerName, long createdAtMillis) {
    }
}
