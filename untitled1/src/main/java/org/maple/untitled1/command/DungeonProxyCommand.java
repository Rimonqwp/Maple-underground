package org.maple.untitled1.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import org.maple.untitled1.dungeon.DungeonStartCoordinator;

import java.util.List;
import java.util.Locale;

public final class DungeonProxyCommand implements SimpleCommand {
    private final DungeonStartCoordinator coordinator;

    public DungeonProxyCommand(DungeonStartCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendPlainMessage("只有玩家可以使用这个命令。");
            return;
        }

        String[] args = invocation.arguments();
        if (args.length >= 2 && "start".equalsIgnoreCase(args[0])) {
            coordinator.startDungeon(player, args[1]);
            return;
        }

        player.sendPlainMessage("用法：/dungeon start <F1|F2|F3>");
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            return List.of("start");
        }
        if (args.length == 1) {
            return filter(List.of("start"), args[0]);
        }
        if (args.length == 2 && "start".equalsIgnoreCase(args[0])) {
            return filter(List.of("F1", "F2", "F3"), args[1]);
        }
        return List.of();
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
