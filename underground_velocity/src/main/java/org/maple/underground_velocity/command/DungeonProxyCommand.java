package org.maple.underground_velocity.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import org.maple.underground_velocity.config.BridgeConfig;
import org.maple.underground_velocity.dungeon.DungeonInstanceManager;
import org.maple.underground_velocity.dungeon.DungeonStartCoordinator;

import java.util.List;
import java.util.Locale;

public final class DungeonProxyCommand implements SimpleCommand {
    private final DungeonStartCoordinator coordinator;
    private final DungeonInstanceManager instanceManager;
    private final BridgeConfig config;

    public DungeonProxyCommand(DungeonStartCoordinator coordinator, DungeonInstanceManager instanceManager, BridgeConfig config) {
        this.coordinator = coordinator;
        this.instanceManager = instanceManager;
        this.config = config;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length >= 1 && "admin".equalsIgnoreCase(args[0])) {
            handleAdmin(invocation, args);
            return;
        }

        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendPlainMessage("只有玩家可以使用这个命令。");
            return;
        }
        if (args.length >= 2 && "start".equalsIgnoreCase(args[0])) {
            coordinator.startDungeon(player, args[1]);
            return;
        }
        if (args.length >= 1 && "leave".equalsIgnoreCase(args[0])) {
            coordinator.leaveDungeon(player);
            return;
        }
        if (args.length >= 1 && "queue".equalsIgnoreCase(args[0])) {
            coordinator.sendQueueStatus(player);
            return;
        }

        player.sendPlainMessage("用法：/dungeon start <F1|F2|F3>、/dungeon leave、/dungeon queue 或 /dungeon admin <status|add|restart|delete>");
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            return List.of("start", "leave", "queue", "admin");
        }
        if (args.length == 1) {
            return filter(List.of("start", "leave", "queue", "admin"), args[0]);
        }
        if (args.length == 2 && "start".equalsIgnoreCase(args[0])) {
            return filter(List.of("F1", "F2", "F3"), args[1]);
        }
        if ("admin".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                return filter(List.of("status", "add", "restart", "delete"), args[1]);
            }
            String sub = args[1].toLowerCase(Locale.ROOT);
            if (args.length == 3 && ("restart".equals(sub) || "delete".equals(sub))) {
                return filter(instanceManager.serverNames(), args[2]);
            }
            if (args.length == 4 && ("restart".equals(sub) || "delete".equals(sub))) {
                return filter(List.of("force"), args[3]);
            }
        }
        return List.of();
    }

    private void handleAdmin(Invocation invocation, String[] args) {
        if (!config.adminPermission().isBlank() && !invocation.source().hasPermission(config.adminPermission())) {
            invocation.source().sendPlainMessage("你没有权限使用这个管理命令。");
            return;
        }
        if (args.length < 2 || "status".equalsIgnoreCase(args[1])) {
            invocation.source().sendPlainMessage(instanceManager.describeStatus() + "\n" + coordinator.describeQueue());
            return;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        if ("add".equals(sub)) {
            int count = 1;
            if (args.length >= 3) {
                try {
                    count = Math.max(1, Integer.parseInt(args[2]));
                } catch (NumberFormatException ignored) {
                    invocation.source().sendPlainMessage("数量必须是数字。");
                    return;
                }
            }
            int finalCount = count;
            invocation.source().sendPlainMessage("正在新增动态副本服务器...");
            instanceManager.addServers(finalCount).thenAccept(created ->
                    invocation.source().sendPlainMessage("已新增 " + created + " 台动态副本服务器。")
            ).exceptionally(error -> {
                invocation.source().sendPlainMessage("新增失败：" + DungeonInstanceManager.userMessage(error));
                return null;
            });
            return;
        }
        if ("restart".equals(sub)) {
            if (args.length < 3) {
                invocation.source().sendPlainMessage("用法：/dungeon admin restart <server> [force]");
                return;
            }
            boolean force = args.length >= 4 && "force".equalsIgnoreCase(args[3]);
            instanceManager.restartServer(args[2], force).thenAccept(result ->
                    invocation.source().sendPlainMessage(result.message())
            );
            return;
        }
        if ("delete".equals(sub)) {
            if (args.length < 3) {
                invocation.source().sendPlainMessage("用法：/dungeon admin delete <server> [force]");
                return;
            }
            boolean force = args.length >= 4 && "force".equalsIgnoreCase(args[3]);
            invocation.source().sendPlainMessage(instanceManager.deleteServer(args[2], force).message());
            return;
        }
        invocation.source().sendPlainMessage("用法：/dungeon admin <status|add|restart|delete>");
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
