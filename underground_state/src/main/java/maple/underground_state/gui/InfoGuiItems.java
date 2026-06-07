package maple.underground_state.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.jetbrains.annotations.NotNull;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class InfoGuiItems {

    private static final int PROGRESS_BAR_SEGMENTS = 25;
    private static final int[] DETAIL_GLASS_SLOTS = {
            0, 1, 2, 3, 5, 6, 7, 8, 9,
            17, 18, 26, 27, 35, 36,
            44, 46, 47, 48, 50, 51, 52, 53
    };

    private InfoGuiItems() {
    }

    public static void fillDetailFrame(@NotNull Inventory inventory) {
        ItemStack filler = createFiller(Material.BLACK_STAINED_GLASS_PANE, NamedTextColor.BLACK);
        Arrays.stream(DETAIL_GLASS_SLOTS).forEach(slot -> inventory.setItem(slot, filler.clone()));
    }

    public static @NotNull ItemStack createCloseItem() {
        return createNamedItem(
                Material.BARRIER,
                "关闭界面",
                List.of(Component.text("点击关闭这个界面。", NamedTextColor.GRAY))
        );
    }

    public static @NotNull ItemStack createPlayerHead(@NotNull Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(player);
            meta.displayName(Component.text(player.getName() + " 的頭像", NamedTextColor.YELLOW));
            meta.lore(List.of(Component.text("這是打開這個介面的玩家頭像", NamedTextColor.GRAY)));
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    public static @NotNull ItemStack createCustomHead(@NotNull String textureUrl,
                                                      @NotNull Component name,
                                                      @NotNull List<Component> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (!(item.getItemMeta() instanceof SkullMeta meta)) {
            return item;
        }

        PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
        PlayerTextures textures = profile.getTextures();
        try {
            URL skinUrl = URI.create(textureUrl).toURL();
            textures.setSkin(skinUrl);
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
        } catch (IllegalArgumentException | MalformedURLException ignored) {
        }

        meta.displayName(name);
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    public static @NotNull ItemStack createColoredLeatherChestplate(@NotNull Component name,
                                                                    @NotNull List<Component> lore,
                                                                    @NotNull Color color) {
        ItemStack item = new ItemStack(Material.LEATHER_CHESTPLATE);
        if (!(item.getItemMeta() instanceof LeatherArmorMeta meta)) {
            return item;
        }

        meta.displayName(name);
        meta.setColor(color);
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    public static @NotNull ItemStack createFiller(@NotNull Material material, @NotNull NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" ", color));
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    public static @NotNull ItemStack createNamedItem(@NotNull Material material, @NotNull String name) {
        return createNamedItem(material, name, List.of());
    }

    public static @NotNull ItemStack createNamedItem(@NotNull Material material,
                                                     @NotNull Component name,
                                                     @NotNull List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    public static @NotNull ItemStack createNamedItem(@NotNull Material material,
                                                     @NotNull Component name) {
        return createNamedItem(material, name, List.of());
    }

    public static @NotNull ItemStack createNamedItem(@NotNull Material material,
                                                     @NotNull String name,
                                                     @NotNull List<Component> lore) {
        return createNamedItem(material, Component.text(name, NamedTextColor.GOLD), lore);
    }

    public static @NotNull Component createColoredProgressBar(int percent) {
        int safePercent = Math.max(0, Math.min(100, percent));
        int filled = (int) Math.round(PROGRESS_BAR_SEGMENTS * safePercent / 100.0);

        Component bar = Component.empty();
        for (int index = 0; index < PROGRESS_BAR_SEGMENTS; index++) {
            NamedTextColor color = index < filled ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY;
            bar = bar.append(Component.text(" ", color, TextDecoration.STRIKETHROUGH));
        }
        return bar;
    }
}
