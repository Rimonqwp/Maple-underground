package maple.underground_caves.commands;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import maple.underground_caves.Test_dungeons;
import maple.underground_caves.gear.SublimationGearManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * 查詢主手物品：原版屬性修飾（含材質預設）、附魔、昇華 PDC 生命／防禦。
 */
@SuppressWarnings("UnstableApiUsage")
public final class ItemHandInfoCommand implements CommandExecutor {

    private final Test_dungeons plugin;

    public ItemHandInfoCommand(@NotNull Test_dungeons plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("仅玩家可使用此指令。", NamedTextColor.RED));
            return true;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage(Component.text("主手没有物品。", NamedTextColor.RED));
            return true;
        }

        player.sendMessage(Component.text("—— 主手物品 ——", NamedTextColor.GOLD));
        player.sendMessage(Component.text("类型: ", NamedTextColor.GRAY)
                .append(Component.text(hand.getType().name(), NamedTextColor.WHITE))
                .append(Component.text(" ×" + hand.getAmount(), NamedTextColor.DARK_GRAY)));

        if (hand.hasItemMeta()) {
            ItemMeta meta = hand.getItemMeta();
            if (meta.hasDisplayName()) {
                player.sendMessage(Component.text("显示名: ", NamedTextColor.GRAY).append(meta.displayName()));
            }
        }

        appendAttributeModifiers(player, hand);
        appendEnchantments(player, hand);
        appendSublimation(player, hand);

        return true;
    }

    private void appendAttributeModifiers(Player player, ItemStack stack) {
        player.sendMessage(Component.text("【原版属性修饰】", NamedTextColor.AQUA));
        Material mat = stack.getType();
        ItemAttributeModifiers fallback = mat.getDefaultData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        ItemAttributeModifiers attrs = stack.getDataOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, fallback);
        if (attrs == null || attrs.modifiers().isEmpty()) {
            player.sendMessage(Component.text("  （无；非装备或该物品无属性组件）", NamedTextColor.DARK_GRAY));
            return;
        }
        for (Object raw : attrs.modifiers()) {
            if (!(raw instanceof ItemAttributeModifiers.Entry entry)) continue;
            Attribute attr = entry.attribute();
            AttributeModifier mod = entry.modifier();
            EquipmentSlotGroup slot = entry.getGroup();
            String attrName = attr.getKey().toString();
            String op = formatOperation(mod.getOperation());
            double amt = mod.getAmount();
            String slotStr = slot != null ? slot.toString() : "任意";
            Component line = Component.text("  · ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(attrName, NamedTextColor.YELLOW))
                    .append(Component.text(" " + op + " ", NamedTextColor.GRAY))
                    .append(Component.text(String.format("%.4f", amt), NamedTextColor.WHITE))
                    .append(Component.text(" 槽位: " + slotStr, NamedTextColor.DARK_AQUA));
            if (attr.getKey().equals(Attribute.MAX_HEALTH.getKey())) {
                line = line.append(Component.text(" （血量单位：1 = 半颗心）", NamedTextColor.DARK_GRAY));
            }
            player.sendMessage(line);
        }
    }

    private static @NotNull String formatOperation(AttributeModifier.Operation op) {
        if (op == null) return "?";
        return switch (op) {
            case ADD_NUMBER -> "加算";
            case ADD_SCALAR -> "倍率加";
            case MULTIPLY_SCALAR_1 -> "倍率乘";
        };
    }

    private void appendEnchantments(Player player, ItemStack stack) {
        player.sendMessage(Component.text("【附魔】", NamedTextColor.AQUA));
        Map<Enchantment, Integer> enchants = stack.getEnchantments();
        if (enchants.isEmpty()) {
            player.sendMessage(Component.text("  （无）", NamedTextColor.DARK_GRAY));
            return;
        }
        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
            player.sendMessage(Component.text("  · ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(e.getKey().getKey().asString(), NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text(" " + e.getValue(), NamedTextColor.WHITE)));
        }
    }

    private void appendSublimation(Player player, ItemStack stack) {
        SublimationGearManager mgr = plugin.getSublimationGearManager();
        if (mgr == null) return;
        int h = mgr.getSublimationHealthOnPiece(stack);
        int d = mgr.getSublimationDefenseOnPiece(stack);
        if (h == 0 && d == 0) return;
        player.sendMessage(Component.text("【插件：升华装备（PDC）】", NamedTextColor.GREEN));
        player.sendMessage(Component.text("  生命 +" + h + "  防御 +" + d, NamedTextColor.WHITE));
    }
}
