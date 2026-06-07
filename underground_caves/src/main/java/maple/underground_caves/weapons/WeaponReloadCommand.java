package maple.underground_caves.weapons;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 武器指令：reload 重載配置並更新背包內武器；give 發放武器。
 */
public final class WeaponReloadCommand implements CommandExecutor {

    private final WeaponManager weaponManager;

    public WeaponReloadCommand(WeaponManager weaponManager) {
        this.weaponManager = weaponManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§e用法: §f/weapon reload §7| §f/weapon give <武器ID> [玩家]");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                weaponManager.reload();
                int updated = 0;
                for (Player p : sender.getServer().getOnlinePlayers()) {
                    updated += updatePlayerWeapons(p);
                }
                sender.sendMessage("§a武器配置已重載。已更新 " + updated + " 件背包內的武器。");
                return true;
            }
            case "give" -> {
                if (args.length < 2) {
                    sender.sendMessage("§c用法: /weapon give <武器ID> [玩家]");
                    return true;
                }
                String weaponId = args[1];
                Player target = args.length >= 3 ? sender.getServer().getPlayer(args[2]) : (sender instanceof Player p ? p : null);
                if (target == null) {
                    sender.sendMessage("§c請指定在線玩家或自己執行。");
                    return true;
                }
                ItemStack item = weaponManager.createWeaponItem(weaponId);
                if (item == null) {
                    sender.sendMessage("§c未知武器 ID: " + weaponId);
                    return true;
                }
                target.getInventory().addItem(item);
                sender.sendMessage("§a已發放 " + weaponId + " 給 " + target.getName());
                return true;
            }
            default -> {
                sender.sendMessage("§e用法: §f/weapon reload §7| §f/weapon give <武器ID> [玩家]");
                return true;
            }
        }
    }

    /** 更新該玩家背包（含主手、副手、盔甲欄）內所有插件武器，回傳更新件數 */
    private int updatePlayerWeapons(Player player) {
        int count = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (weaponManager.updateItemFromConfig(inv.getItem(i))) count++;
        }
        if (weaponManager.updateItemFromConfig(inv.getItemInOffHand())) count++;
        for (ItemStack armor : inv.getArmorContents()) {
            if (armor != null && !armor.getType().isAir() && weaponManager.updateItemFromConfig(armor)) count++;
        }
        return count;
    }
}
