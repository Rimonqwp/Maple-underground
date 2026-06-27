package maple.underground_state.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class VanillaItemLoreListener implements Listener {

    private static final String TAG_TEXT = "[普通原版物品]";
    private static final int TAG_GRADIENT_START = 0x7FAFFF;
    private static final int TAG_GRADIENT_END = 0xCFC7FF;

    private final NamespacedKey vanillaOriginKey;

    public VanillaItemLoreListener(@NotNull Plugin plugin) {
        this.vanillaOriginKey = vanillaOriginKey(plugin);
    }

    public static @NotNull NamespacedKey vanillaOriginKey(@NotNull Plugin plugin) {
        return new NamespacedKey(plugin, "vanilla_origin_item");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPickupItem(@NotNull EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        applyVanillaOriginTag(stack);
    }

    public boolean applyVanillaOriginTag(@NotNull ItemStack stack) {
        if (!shouldTag(stack)) {
            return false;
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }

        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(buildVanillaOriginLore());
        meta.lore(lore);
        meta.getPersistentDataContainer().set(vanillaOriginKey, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return true;
    }

    private boolean shouldTag(@NotNull ItemStack stack) {
        Material type = stack.getType();
        if (type.isAir() || stack.getAmount() <= 0) {
            return false;
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(vanillaOriginKey, PersistentDataType.BYTE)) {
            return false;
        }

        return pdc.getKeys().isEmpty()
                && !meta.hasDisplayName()
                && !meta.hasLore()
                && !meta.hasCustomModelData()
                && !meta.hasEnchants();
    }

    private static @NotNull Component buildVanillaOriginLore() {
        Component component = Component.empty();
        for (int index = 0; index < TAG_TEXT.length(); index++) {
            double ratio = TAG_TEXT.length() == 1 ? 0D : (double) index / (double) (TAG_TEXT.length() - 1);
            int color = interpolateColor(TAG_GRADIENT_START, TAG_GRADIENT_END, ratio);
            component = component.append(Component.text(String.valueOf(TAG_TEXT.charAt(index)), TextColor.color(color)));
        }
        return component.decoration(TextDecoration.ITALIC, false);
    }

    private static int interpolateColor(int startHex, int endHex, double ratio) {
        int startR = (startHex >> 16) & 0xFF;
        int startG = (startHex >> 8) & 0xFF;
        int startB = startHex & 0xFF;
        int endR = (endHex >> 16) & 0xFF;
        int endG = (endHex >> 8) & 0xFF;
        int endB = endHex & 0xFF;

        int red = (int) Math.round(startR + (endR - startR) * ratio);
        int green = (int) Math.round(startG + (endG - startG) * ratio);
        int blue = (int) Math.round(startB + (endB - startB) * ratio);
        return (red << 16) | (green << 8) | blue;
    }
}
