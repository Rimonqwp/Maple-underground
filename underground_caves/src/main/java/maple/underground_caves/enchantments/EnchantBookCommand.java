package maple.underground_caves.enchantments;

import maple.underground_caves.Test_dungeons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 管理員指令：領取自定義附魔書，可在鐵砧上與適用裝備合成。
 * 用法：/enchant_book <附魔ID> [等級] [玩家]
 */
public final class EnchantBookCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "test_dungeons.admin";

    private final Test_dungeons plugin;
    private final EnchantmentRegistry registry;

    public EnchantBookCommand(Test_dungeons plugin, EnchantmentRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("你沒有權限使用此指令。", NamedTextColor.RED));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(Component.text("用法: /enchant_book <附魔ID> [等級] [玩家]", NamedTextColor.GRAY));
            return true;
        }
        String enchantId = args[0].toLowerCase(Locale.ROOT);
        CustomEnchantment enchant = registry.getById(enchantId);
        if (enchant == null) {
            sender.sendMessage(Component.text("未知附魔 ID: " + enchantId, NamedTextColor.RED));
            return true;
        }
        int level = enchant.getMaxLevel();
        if (args.length >= 2) {
            try {
                level = Integer.parseInt(args[1]);
                level = Math.max(1, Math.min(level, enchant.getMaxLevel()));
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("等級必須為 1~" + enchant.getMaxLevel() + " 的數字。", NamedTextColor.RED));
                return true;
            }
        }
        Player target;
        if (args.length >= 3) {
            target = plugin.getServer().getPlayer(args[2]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(Component.text("找不到線上玩家: " + args[2], NamedTextColor.RED));
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(Component.text("請指定玩家，或由玩家本人執行此指令。", NamedTextColor.RED));
                return true;
            }
            target = (Player) sender;
        }
        ItemStack book = registry.createEnchantBook(enchantId, level);
        if (book.getType().isAir()) {
            sender.sendMessage(Component.text("無法建立附魔書。", NamedTextColor.RED));
            return true;
        }
        if (target.getInventory().addItem(book).isEmpty()) {
            if (target.equals(sender)) {
                sender.sendMessage(Component.text("已獲得附魔書: ", NamedTextColor.GREEN)
                    .append(Component.text(enchant.getDisplayName() + " " + EnchantmentLore.levelToRoman(level), enchant.getLoreColor())));
            } else {
                sender.sendMessage(Component.text("已為 " + target.getName() + " 發放附魔書: ", NamedTextColor.GREEN)
                    .append(Component.text(enchant.getDisplayName() + " " + EnchantmentLore.levelToRoman(level), enchant.getLoreColor())));
                target.sendMessage(Component.text("你獲得了附魔書: ", NamedTextColor.GREEN)
                    .append(Component.text(enchant.getDisplayName() + " " + EnchantmentLore.levelToRoman(level), enchant.getLoreColor())));
            }
        } else {
            sender.sendMessage(Component.text("目標背包已滿，無法發放附魔書。", NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) return List.of();
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (CustomEnchantment e : registry.getAll()) {
                if (e.getId().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(e.getId());
                }
            }
            return out.stream().sorted().collect(Collectors.toList());
        }
        if (args.length == 2) {
            CustomEnchantment enchant = registry.getById(args[0].toLowerCase(Locale.ROOT));
            if (enchant != null) {
                String prefix = args[1];
                for (int i = 1; i <= enchant.getMaxLevel(); i++) {
                    String s = String.valueOf(i);
                    if (s.startsWith(prefix)) out.add(s);
                }
            }
            return out.stream().sorted().collect(Collectors.toList());
        }
        if (args.length == 3) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted()
                .collect(Collectors.toList());
        }
        return List.of();
    }
}
