package maple.underground_caves.gear;

import maple.underground_caves.weapons.WeaponManager;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * /gear 與 /immortalskull 的 Tab 補全（裝備、武器、永恆的面具）。
 */
public final class GearTabCompleter implements TabCompleter {

    private static final String[] SLOTS = { "helmet", "chest", "leggings", "boots" };
    private static final String[] FLOORS = { "F1", "F2", "F3" };

    private final WeaponManager weaponManager;

    public GearTabCompleter() {
        this.weaponManager = null;
    }

    public GearTabCompleter(WeaponManager weaponManager) {
        this.weaponManager = weaponManager;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            if ("give".startsWith(args[0].toLowerCase())) out.add("give");
            if ("reload".startsWith(args[0].toLowerCase())) out.add("reload");
            return out.isEmpty() ? null : out;
        }
        if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            if ("immortalskull".equalsIgnoreCase(alias)) {
                String pre = args[1].toLowerCase();
                for (Player p : sender.getServer().getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(pre)) out.add(p.getName());
                }
                return out.isEmpty() ? null : out;
            }
            String pre = args[1].toLowerCase();
            if ("skull".startsWith(pre)) out.add("skull");
            if ("quality_upgrade".startsWith(pre)) out.add("quality_upgrade");
            if ("cave_helmet".startsWith(pre)) out.add("cave_helmet");
            for (String id : StatsDoubleHelmetManager.HELMET_IDS) {
                if (id.toLowerCase().startsWith(pre)) out.add(id);
            }
            for (String id : CaveSpecialHelmetManager.HELMET_IDS) {
                if (id.toLowerCase().startsWith(pre)) out.add(id);
            }
            if ("second_wind_helmet".startsWith(pre)) out.add("second_wind_helmet");
            if ("arrow_distance_helmet".startsWith(pre)) out.add("arrow_distance_helmet");
            if ("adaptive_set".startsWith(pre)) out.add("adaptive_set");
            if ("cavern_explorer_set".startsWith(pre)) out.add("cavern_explorer_set");
            for (String id : BloodSetManager.getSetIds()) {
                if (id.toLowerCase().startsWith(pre)) out.add(id);
            }
            if ("sieve".startsWith(pre)) out.add("sieve");
            if ("archer".startsWith(pre)) out.add("archer");
            if ("skeleton_soldier".startsWith(pre)) out.add("skeleton_soldier");
            if ("skeleton_master".startsWith(pre)) out.add("skeleton_master");
            if ("weapon".startsWith(pre)) out.add("weapon");
            for (String slot : SLOTS) {
                if (slot.toLowerCase().startsWith(pre)) out.add(slot);
            }
            for (Player p : sender.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(pre)) out.add(p.getName());
            }
            return out.isEmpty() ? null : out;
        }
        if (args.length == 3 && "give".equalsIgnoreCase(args[0]) && "skull".equalsIgnoreCase(args[1])) {
            String pre = args[2].toLowerCase();
            for (Player p : sender.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(pre)) out.add(p.getName());
            }
            return out.isEmpty() ? null : out;
        }
        if (args.length == 3 && "give".equalsIgnoreCase(args[0]) && "cave_helmet".equalsIgnoreCase(args[1])) {
            String pre = args[2].toLowerCase();
            if ("f1".startsWith(pre)) out.add("F1");
            if ("f2".startsWith(pre)) out.add("F2");
            if ("f3".startsWith(pre)) out.add("F3");
            return out.isEmpty() ? null : out;
        }
        if (args.length == 4 && "give".equalsIgnoreCase(args[0]) && "cave_helmet".equalsIgnoreCase(args[1])) {
            String pre = args[3].toLowerCase();
            for (Player p : sender.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(pre)) out.add(p.getName());
            }
            return out.isEmpty() ? null : out;
        }
        if (args.length >= 3 && "give".equalsIgnoreCase(args[0]) && args[1] != null) {
            String second = args[1].toLowerCase();
            for (String id : StatsDoubleHelmetManager.HELMET_IDS) {
                if (id.equals(second)) {
                    if (args.length == 3) {
                        String pre = args[2].toLowerCase();
                        for (Player p : sender.getServer().getOnlinePlayers()) {
                            if (p.getName().toLowerCase().startsWith(pre)) out.add(p.getName());
                        }
                    }
                    return out.isEmpty() ? null : out;
                }
            }
            for (String id : CaveSpecialHelmetManager.HELMET_IDS) {
                if (id.equals(second)) {
                    if (args.length == 3) {
                        String pre = args[2].toLowerCase();
                        for (Player p : sender.getServer().getOnlinePlayers()) {
                            if (p.getName().toLowerCase().startsWith(pre)) out.add(p.getName());
                        }
                    }
                    return out.isEmpty() ? null : out;
                }
            }
        }
        if (args.length == 3 && "give".equalsIgnoreCase(args[0]) && "weapon".equalsIgnoreCase(args[1]) && weaponManager != null) {
            String pre = args[2].toLowerCase();
            for (String id : weaponManager.getWeaponIds()) {
                if (id.toLowerCase().startsWith(pre)) out.add(id);
            }
            return out.isEmpty() ? null : out;
        }
        if (args.length == 4 && "give".equalsIgnoreCase(args[0]) && "weapon".equalsIgnoreCase(args[1])) {
            String pre = args[3].toLowerCase();
            for (var p : sender.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(pre)) out.add(p.getName());
            }
            return out.isEmpty() ? null : out;
        }
        if (args.length == 3 && "give".equalsIgnoreCase(args[0]) && "sieve".equalsIgnoreCase(args[1])) {
            String pre = args[2].toLowerCase();
            for (String slot : SLOTS) {
                if (slot.toLowerCase().startsWith(pre)) out.add(slot);
            }
            for (String f : FLOORS) {
                if (f.toLowerCase().startsWith(pre)) out.add(f);
            }
            for (var p : sender.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(pre)) out.add(p.getName());
            }
            return out.isEmpty() ? null : out;
        }
        if (args.length == 4 && "give".equalsIgnoreCase(args[0]) && "sieve".equalsIgnoreCase(args[1]) && isSlot(args[2])) {
            String pre = args[3].toLowerCase();
            for (String f : FLOORS) {
                if (f.toLowerCase().startsWith(pre)) out.add(f);
            }
            for (var p : sender.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(pre)) out.add(p.getName());
            }
            return out.isEmpty() ? null : out;
        }
        if (args.length == 5 && "give".equalsIgnoreCase(args[0]) && "sieve".equalsIgnoreCase(args[1]) && isSlot(args[2]) && isFloor(args[3])) {
            String pre = args[4].toLowerCase();
            for (var p : sender.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(pre)) out.add(p.getName());
            }
            return out.isEmpty() ? null : out;
        }
        if (args.length == 3 && "give".equalsIgnoreCase(args[0]) && "archer".equalsIgnoreCase(args[1])) {
            String pre = args[2].toLowerCase();
            for (String slot : SLOTS) {
                if (slot.toLowerCase().startsWith(pre)) out.add(slot);
            }
            for (String f : FLOORS) {
                if (f.toLowerCase().startsWith(pre)) out.add(f);
            }
            for (var p : sender.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(pre)) out.add(p.getName());
            }
            return out.isEmpty() ? null : out;
        }
        if (args.length == 4 && "give".equalsIgnoreCase(args[0]) && "archer".equalsIgnoreCase(args[1]) && isSlot(args[2])) {
            String pre = args[3].toLowerCase();
            for (String f : FLOORS) {
                if (f.toLowerCase().startsWith(pre)) out.add(f);
            }
            for (var p : sender.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(pre)) out.add(p.getName());
            }
            return out.isEmpty() ? null : out;
        }
        if (args.length == 5 && "give".equalsIgnoreCase(args[0]) && "archer".equalsIgnoreCase(args[1]) && isSlot(args[2]) && isFloor(args[3])) {
            String pre = args[4].toLowerCase();
            for (var p : sender.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(pre)) out.add(p.getName());
            }
            return out.isEmpty() ? null : out;
        }
        if (args.length == 3 && "give".equalsIgnoreCase(args[0]) && "skeleton_soldier".equalsIgnoreCase(args[1])) {
            String pre = args[2].toLowerCase();
            for (String slot : SLOTS) {
                if (slot.toLowerCase().startsWith(pre)) out.add(slot);
            }
            for (String f : FLOORS) {
                if (f.toLowerCase().startsWith(pre)) out.add(f);
            }
            for (var p : sender.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(pre)) out.add(p.getName());
            }
            return out.isEmpty() ? null : out;
        }
        if (args.length == 4 && "give".equalsIgnoreCase(args[0]) && "skeleton_soldier".equalsIgnoreCase(args[1]) && isSlot(args[2])) {
            String pre = args[3].toLowerCase();
            for (String f : FLOORS) {
                if (f.toLowerCase().startsWith(pre)) out.add(f);
            }
            for (var p : sender.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(pre)) out.add(p.getName());
            }
            return out.isEmpty() ? null : out;
        }
        if (args.length == 5 && "give".equalsIgnoreCase(args[0]) && "skeleton_soldier".equalsIgnoreCase(args[1]) && isSlot(args[2]) && isFloor(args[3])) {
            String pre = args[4].toLowerCase();
            for (var p : sender.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(pre)) out.add(p.getName());
            }
            return out.isEmpty() ? null : out;
        }
        if (args.length == 3 && "give".equalsIgnoreCase(args[0]) && "skeleton_master".equalsIgnoreCase(args[1])) {
            String pre = args[2].toLowerCase();
            for (String slot : SLOTS) {
                if (slot.toLowerCase().startsWith(pre)) out.add(slot);
            }
            for (String f : FLOORS) {
                if (f.toLowerCase().startsWith(pre)) out.add(f);
            }
            for (var p : sender.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(pre)) out.add(p.getName());
            }
            return out.isEmpty() ? null : out;
        }
        if (args.length == 4 && "give".equalsIgnoreCase(args[0]) && "skeleton_master".equalsIgnoreCase(args[1]) && isSlot(args[2])) {
            String pre = args[3].toLowerCase();
            for (String f : FLOORS) {
                if (f.toLowerCase().startsWith(pre)) out.add(f);
            }
            for (var p : sender.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(pre)) out.add(p.getName());
            }
            return out.isEmpty() ? null : out;
        }
        if (args.length == 5 && "give".equalsIgnoreCase(args[0]) && "skeleton_master".equalsIgnoreCase(args[1]) && isSlot(args[2]) && isFloor(args[3])) {
            String pre = args[4].toLowerCase();
            for (var p : sender.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(pre)) out.add(p.getName());
            }
            return out.isEmpty() ? null : out;
        }
        if (args.length == 3 && "give".equalsIgnoreCase(args[0]) && "skull".equalsIgnoreCase(args[1])) {
            String pre = args[2].toLowerCase();
            for (var p : sender.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(pre)) out.add(p.getName());
            }
            return out.isEmpty() ? null : out;
        }
        if (args.length == 3 && "give".equalsIgnoreCase(args[0]) && isSlot(args[1])) {
            for (String f : FLOORS) {
                if (f.toLowerCase().startsWith(args[2].toLowerCase())) out.add(f);
            }
            for (var p : sender.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[2].toLowerCase())) out.add(p.getName());
            }
            return out.isEmpty() ? null : out;
        }
        if (args.length == 4 && "give".equalsIgnoreCase(args[0]) && isSlot(args[1]) && isFloor(args[2])) {
            String prefix = args[3].toLowerCase();
            for (var p : sender.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) out.add(p.getName());
            }
            return out.isEmpty() ? null : out;
        }
        return null;
    }

    private static boolean isSlot(String s) {
        if (s == null) return false;
        switch (s.toLowerCase()) {
            case "helmet": case "chest": case "leggings": case "boots": return true;
            default: return false;
        }
    }

    private static boolean isFloor(String s) {
        return "F1".equalsIgnoreCase(s) || "F2".equalsIgnoreCase(s) || "F3".equalsIgnoreCase(s);
    }
}
