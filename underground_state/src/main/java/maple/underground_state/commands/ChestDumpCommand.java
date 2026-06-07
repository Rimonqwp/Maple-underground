package maple.underground_state.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.jetbrains.annotations.NotNull;

import com.destroystokyo.paper.profile.ProfileProperty;

import java.net.URL;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChestDumpCommand implements CommandExecutor, Listener {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<InventoryType> SUPPORTED_TYPES = EnumSet.of(
            InventoryType.CHEST,
            InventoryType.BARREL,
            InventoryType.SHULKER_BOX,
            InventoryType.ENDER_CHEST
    );

    private final JavaPlugin plugin;
    private final Set<UUID> enabledPlayers = ConcurrentHashMap.newKeySet();
    private final Path dumpFile;

    public ChestDumpCommand(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
        this.dumpFile = plugin.getDataFolder().toPath().resolve("chest_dumps.log");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        UUID playerId = player.getUniqueId();
        boolean enabled;
        if (enabledPlayers.contains(playerId)) {
            enabledPlayers.remove(playerId);
            enabled = false;
        } else {
            enabledPlayers.add(playerId);
            enabled = true;
        }

        if (enabled) {
            player.sendMessage(Component.text("Chest dump enabled. Opening a chest will log every slot.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Chest dump disabled.", NamedTextColor.YELLOW));
        }
        return true;
    }

    @EventHandler
    public void onInventoryOpen(@NotNull InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!enabledPlayers.contains(player.getUniqueId())) {
            return;
        }

        Inventory inventory = event.getInventory();
        if (!SUPPORTED_TYPES.contains(inventory.getType())) {
            return;
        }

        String dump = buildDump(player, event);
        writeDump(dump);
        player.sendMessage(Component.text("Chest content dumped to console and chest_dumps.log", NamedTextColor.GREEN));
    }

    private @NotNull String buildDump(@NotNull Player player, @NotNull InventoryOpenEvent event) {
        Inventory inventory = event.getInventory();
        StringBuilder sb = new StringBuilder();
        sb.append("===== CHEST DUMP =====").append(System.lineSeparator());
        sb.append("Time: ").append(LocalDateTime.now().format(TIME_FORMAT)).append(System.lineSeparator());
        sb.append("Player: ").append(player.getName()).append(" (").append(player.getUniqueId()).append(")").append(System.lineSeparator());
        sb.append("Type: ").append(inventory.getType()).append(System.lineSeparator());
        sb.append("Title: ").append(PlainTextComponentSerializer.plainText().serialize(event.getView().title())).append(System.lineSeparator());
        sb.append("Location: ").append(formatLocation(inventory.getLocation())).append(System.lineSeparator());
        sb.append("Size: ").append(inventory.getSize()).append(System.lineSeparator());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            sb.append("Slot ").append(slot).append(": ").append(describeItem(inventory.getItem(slot))).append(System.lineSeparator());
        }
        sb.append("======================").append(System.lineSeparator());
        return sb.toString();
    }

    private @NotNull String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "unknown";
        }
        return location.getWorld().getName()
                + " x=" + location.getBlockX()
                + " y=" + location.getBlockY()
                + " z=" + location.getBlockZ();
    }

    private @NotNull String describeItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "EMPTY";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(item.getType()).append(" x").append(item.getAmount());
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            appendQuoted(sb, "name", PlainTextComponentSerializer.plainText().serialize(meta.displayName()));
        }
        if (meta instanceof SkullMeta skullMeta) {
            appendSkullInfo(sb, skullMeta);
        }
        return sb.toString();
    }

    private void appendSkullInfo(@NotNull StringBuilder sb, @NotNull SkullMeta meta) {
        if (meta.hasOwner()) {
            appendQuoted(sb, "owner", meta.getOwner());
        }

        PlayerProfile ownerProfile = meta.getOwnerProfile();
        if (ownerProfile != null) {
            if (ownerProfile.getUniqueId() != null) {
                appendQuoted(sb, "profileUuid", ownerProfile.getUniqueId().toString());
            }
            if (ownerProfile.getName() != null) {
                appendQuoted(sb, "profileName", ownerProfile.getName());
            }
            appendTexturesInfo(sb, ownerProfile.getTextures());
        }

        com.destroystokyo.paper.profile.PlayerProfile paperProfile = meta.getPlayerProfile();
        if (paperProfile != null) {
            for (ProfileProperty property : paperProfile.getProperties()) {
                if (!"textures".equals(property.getName())) {
                    continue;
                }
                appendQuoted(sb, "textures.value", property.getValue());
                if (property.getSignature() != null && !property.getSignature().isBlank()) {
                    appendQuoted(sb, "textures.signature", property.getSignature());
                }
            }
        }
    }

    private void appendTexturesInfo(@NotNull StringBuilder sb, @NotNull PlayerTextures textures) {
        URL skin = textures.getSkin();
        if (skin != null) {
            appendQuoted(sb, "skin", skin.toString());
        }
        if (textures.getSkinModel() != null) {
            appendQuoted(sb, "skinModel", textures.getSkinModel().name());
        }
        URL cape = textures.getCape();
        if (cape != null) {
            appendQuoted(sb, "cape", cape.toString());
        }
        if (textures.getTimestamp() > 0L) {
            sb.append(" texturesTimestamp=").append(textures.getTimestamp());
        }
        sb.append(" texturesSigned=").append(textures.isSigned());
    }

    private void appendQuoted(@NotNull StringBuilder sb, @NotNull String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sb.append(' ')
                .append(key)
                .append("=\"")
                .append(value.replace("\\", "\\\\").replace("\"", "\\\""))
                .append('"');
    }

    private void writeDump(@NotNull String dump) {
        for (String line : dump.split("\\R")) {
            if (!line.isEmpty()) {
                plugin.getLogger().info(line);
            }
        }

        try {
            Files.createDirectories(dumpFile.getParent());
            Files.writeString(
                    dumpFile,
                    dump + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write chest dump file: " + e.getMessage());
        }
    }
}
