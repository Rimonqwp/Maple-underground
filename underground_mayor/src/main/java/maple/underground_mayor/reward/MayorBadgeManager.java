package maple.underground_mayor.reward;

import maple.underground_mayor.util.LegacyRgbParser;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MayorBadgeManager {
    private static final String RESOURCE_PATH = "rewards/mayor_badge.yml";

    private final maple.underground_mayor.UndergroundMayor plugin;
    private final NamespacedKey badgeKey;
    private YamlConfiguration config;

    public MayorBadgeManager(maple.underground_mayor.UndergroundMayor plugin) {
        this.plugin = plugin;
        this.badgeKey = new NamespacedKey(plugin, "mayor_badge");
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), RESOURCE_PATH);
        ensureResourceFileExists(file);
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void giveBadges(Player player, int badgeCount, RewardContext context) {
        if (player == null || badgeCount <= 0) {
            return;
        }

        int amountPerBadge = Math.max(1, config.getInt("item.amount_per_badge", 1));
        int totalItems = badgeCount * amountPerBadge;
        Material material = parseMaterial(config.getString("item.material", "PAPER"));
        int maxStack = Math.max(1, material.getMaxStackSize());

        while (totalItems > 0) {
            int stackAmount = Math.min(maxStack, totalItems);
            ItemStack item = createConfiguredBadge(material, stackAmount, context);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            totalItems -= stackAmount;
        }
    }

    public void givePreviewBadges(Player player, int badgeCount) {
        giveBadges(
                player,
                badgeCount,
                new RewardContext(
                        player != null ? player.getName() : "Preview",
                        0,
                        0,
                        0,
                        0,
                        0,
                        maple.underground_mayor.mayor.MayorCollectionManager.TOTAL_BADGES,
                        12000
                )
        );
    }

    public String getDisplayNamePreview() {
        return config.getString("item.display_name", "&6Mayor Badge");
    }

    private ItemStack createConfiguredBadge(Material material, int amount, RewardContext context) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        Map<String, String> placeholders = context.placeholders();
        meta.displayName(LegacyRgbParser.parse(applyPlaceholders(config.getString("item.display_name", "&6Mayor Badge"), placeholders)));

        List<String> loreLines = config.getStringList("item.lore");
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(LegacyRgbParser.parse(applyPlaceholders(line, placeholders)));
        }
        meta.lore(lore);

        int customModelData = config.getInt("item.custom_model_data", 0);
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }

        if (config.getBoolean("item.unbreakable", false)) {
            meta.setUnbreakable(true);
        }

        if (config.getBoolean("item.glow", false)) {
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
        }

        for (String rawFlag : config.getStringList("item.item_flags")) {
            try {
                meta.addItemFlags(ItemFlag.valueOf(rawFlag.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }

        meta.getPersistentDataContainer().set(badgeKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private String applyPlaceholders(String input, Map<String, String> placeholders) {
        String result = input == null ? "" : input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private Material parseMaterial(String raw) {
        try {
            return Material.valueOf(raw == null ? "PAPER" : raw.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return Material.PAPER;
        }
    }

    private void ensureResourceFileExists(File file) {
        if (file.exists()) {
            return;
        }

        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Failed to create reward config directory: " + parent.getAbsolutePath());
            return;
        }

        try (InputStream in = plugin.getResource(RESOURCE_PATH)) {
            if (in != null) {
                Files.copy(in, file.toPath());
                return;
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to copy embedded reward config: " + ex.getMessage());
            return;
        }

        // Fallback for reload/remap edge cases: create a minimal default file instead of failing plugin startup.
        YamlConfiguration fallback = new YamlConfiguration();
        fallback.set("item.material", "PAPER");
        fallback.set("item.amount_per_badge", 1);
        fallback.set("item.custom_model_data", 0);
        fallback.set("item.unbreakable", false);
        fallback.set("item.glow", true);
        fallback.set("item.display_name", "&#F9D976[&#F6E58D市&#F4EFA1長&#F2F7B4建&#F6FBC0設&#FCFFCC徽&#FCF8B2章&#F9D976]");
        fallback.set("item.lore", List.of(
                "&8Mayor Construction Reward",
                "&7感謝你參與地下城市建設。",
                "&7本次提交: &e{contributed_this_turn}",
                "&7累計提交: &e{player_contribution}",
                "&7全服總徽章上限: &6100",
                "&7依照最終貢獻比例精確分配",
                "",
                "&#7FAFFF[&#8AB2FF稀&#96B6FF有&#A1B9FF建&#ADBDFF設&#B8C0FF紀&#C4C4FF念&#CFC7FF徽&#D8CCFF章&#E1D2FF]"
        ));
        fallback.set("item.item_flags", List.of("HIDE_ENCHANTS", "HIDE_ATTRIBUTES", "HIDE_UNBREAKABLE"));

        try {
            fallback.save(file);
            plugin.getLogger().warning("Embedded reward config was unavailable during load; wrote a fallback mayor_badge.yml instead.");
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to write fallback reward config: " + ex.getMessage());
        }
    }

    public record RewardContext(
            String playerName,
            int playerContribution,
            int playerBadges,
            int playerEntitledBadges,
            int playerPendingBadges,
            int contributedThisTurn,
            int totalBadges,
            int totalRequired
    ) {
        public Map<String, String> placeholders() {
            return Map.of(
                    "player_name", playerName,
                    "player_contribution", String.valueOf(playerContribution),
                    "player_badges", String.valueOf(playerBadges),
                    "player_entitled_badges", String.valueOf(playerEntitledBadges),
                    "player_pending_badges", String.valueOf(playerPendingBadges),
                    "contributed_this_turn", String.valueOf(contributedThisTurn),
                    "total_badges", String.valueOf(totalBadges),
                    "total_required", String.valueOf(totalRequired)
            );
        }
    }
}
