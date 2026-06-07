package maple.underground_caves.weapons;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class WeaponTabCompleter implements TabCompleter {

    private final WeaponManager weaponManager;

    public WeaponTabCompleter(WeaponManager weaponManager) {
        this.weaponManager = weaponManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            if ("reload".startsWith(args[0].toLowerCase())) out.add("reload");
            if ("give".startsWith(args[0].toLowerCase())) out.add("give");
            return out.isEmpty() ? Collections.emptyList() : out;
        }
        if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            String prefix = args[1].toLowerCase();
            return weaponManager.getWeaponIds().stream()
                    .filter(id -> id.toLowerCase().startsWith(prefix))
                    .sorted()
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && "give".equalsIgnoreCase(args[0])) {
            String prefix = args[2].toLowerCase();
            return sender.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .sorted()
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
