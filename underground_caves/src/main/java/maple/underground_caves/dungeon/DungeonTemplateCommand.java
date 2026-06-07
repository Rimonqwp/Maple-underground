package maple.underground_caves.dungeon;

import maple.underground_caves.Test_dungeons;
import maple.underground_caves.puzzle.PuzzleRoom05IcePath;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Ladder;
import org.bukkit.block.data.type.Slab;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@SuppressWarnings("ALL")
public class DungeonTemplateCommand implements CommandExecutor, TabCompleter {
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();
    private static final String PUSHER_CONFIG_FILE = "pushers.yml";
    private static final Map<String, List<PusherConfig>> PUSHER_CONFIGS = new HashMap<>();
    private static long PUSHER_CONFIG_LOADED_AT_MS = 0L;
    private static final Map<String, RoomPusherRuntime> ACTIVE_PUSHER_ROOMS = new HashMap<>();
    private static BukkitRunnable PUSHER_TICK_TASK;
    private static final long PUSHER_TICK_PERIOD_TICKS = 10L;
    private final DungeonSecrets dungeonSecrets;
    private final DungeonMonsters dungeonMonsters;

    public DungeonTemplateCommand(DungeonSecrets dungeonSecrets) {
        this(dungeonSecrets, null);
    }

    public DungeonTemplateCommand(DungeonSecrets dungeonSecrets, DungeonMonsters dungeonMonsters) {
        this.dungeonSecrets = dungeonSecrets;
        this.dungeonMonsters = dungeonMonsters;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 0) return List.of();

        if (args.length == 1) {
            return filterPrefix(List.of(
                    "pos1", "pos2",
                    "check", "paste",
                    "save", "save_variant", "save_walls", "save_doors",
                    "secret_edit", "secret_save", "secret_exit",
                    "monster_edit", "monster_save", "monster_exit", "monster_reload",
                    "redstone_stop", "redstone_save",
                    "tomb_pos1", "tomb_pos2", "tomb_add",
                    "give_tnt",
                    "ice05_level", "ice05_board_pos1", "ice05_board_pos2", "ice05_start", "ice05_end", "ice05_save",
                    "blaze08_bow",
                    "blaze08_circle_pos1", "blaze08_circle_pos2",
                    "blaze08_progress_pos1", "blaze08_progress_pos2",
                    "blaze08_save",
                    "pusher_region_pos1", "pusher_region_pos2",
                    "pusher_start", "pusher_end",
                    "pusher_run", "pusher_stop",
                    "pusher_link", "pusher_unlink",
                    "pusher_save", "pusher_clear", "pusher_reload"
            ), args[0]);
        }

        String sub = args[0] != null ? args[0].trim().toLowerCase(Locale.ROOT) : "";

        if (args.length == 2) {
            if ("give_tnt".equals(sub)) {
                return filterPrefix(List.of("1", "4", "8", "16", "32", "64"), args[1]);
            }
            if ("ice05_level".equals(sub)) {
                return filterPrefix(List.of("small", "medium", "large"), args[1]);
            }
            if ("check".equals(sub)) {
                return filterPrefix(rotationCandidates(), args[1]);
            }
            if (needsTemplateName(sub)) {
                return filterPrefix(listTemplateBaseNames(), args[1]);
            }
            if ("pusher_save".equals(sub) || "pusher_clear".equals(sub)) {
                return filterPrefix(listTemplateBaseNames(), args[1]);
            }
            if ("pusher_link".equals(sub) || "pusher_unlink".equals(sub)) {
                return filterPrefix(listTemplateBaseNames(), args[1]);
            }
            return List.of();
        }

        if (args.length >= 3) {
            if ("paste".equals(sub)) {
                return filterPrefix(List.of(
                        "R0", "R90", "R180", "R270",
                        "walls", "open", "all",
                        "secrets", "monsters"
                ), args[args.length - 1]);
            }
            if ("check".equals(sub)) {
                return filterPrefix(rotationCandidates(), args[args.length - 1]);
            }
            if ("pusher_link".equals(sub) && args.length == 3) {
                return filterPrefix(listPusherIds(args[1]), args[2]);
            }
            if ("pusher_link".equals(sub) && args.length == 4) {
                return filterPrefix(listPusherIds(args[1]), args[3]);
            }
            if ("pusher_unlink".equals(sub) && args.length == 3) {
                return filterPrefix(listPusherIds(args[1]), args[2]);
            }
            return List.of();
        }

        return List.of();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(Component.text(
                    "Usage: /dungeon_template <pos1|pos2|check|paste|save|save_variant|save_walls|save_doors|secret_edit|secret_save|secret_exit|monster_edit|monster_save|monster_exit|monster_reload|redstone_stop|redstone_save|tomb_pos1|tomb_pos2|tomb_add|give_tnt|ice05_level|ice05_board_pos1|ice05_board_pos2|ice05_start|ice05_end|ice05_save|blaze08_circle_pos1|blaze08_circle_pos2|blaze08_progress_pos1|blaze08_progress_pos2|blaze08_save|pusher_region_pos1|pusher_region_pos2|pusher_start|pusher_end|pusher_run|pusher_stop|pusher_save|pusher_clear|pusher_reload|pusher_link|pusher_unlink> [name] [rotation]",
                    NamedTextColor.RED));
            return true;
        }

        String sub = args[0].toLowerCase();
        Selection sel = SELECTIONS.computeIfAbsent(player.getUniqueId(), k -> new Selection());

        return switch (sub) {
            case "pos1" -> {
                Location loc = pickTargetBlockLocation(player);
                sel.pos1 = loc;
                player.sendMessage(Component.text("Pos1 set to " + format(loc), NamedTextColor.GRAY));
                yield true;
            }
            case "pos2" -> {
                Location loc = pickTargetBlockLocation(player);
                sel.pos2 = loc;
                player.sendMessage(Component.text("Pos2 set to " + format(loc), NamedTextColor.GRAY));
                yield true;
            }
            case "tomb_pos1" -> {
                Location loc = pickTargetBlockLocation(player);
                sel.tombPos1 = loc;
                player.sendMessage(Component.text("Tomb Pos1 set to " + format(loc), NamedTextColor.GRAY));
                yield true;
            }
            case "tomb_pos2" -> {
                Location loc = pickTargetBlockLocation(player);
                sel.tombPos2 = loc;
                player.sendMessage(Component.text("Tomb Pos2 set to " + format(loc), NamedTextColor.GRAY));
                yield true;
            }
            case "tomb_add" -> {
                if (sel.tombPos1 == null || sel.tombPos2 == null) {
                    player.sendMessage(
                            Component.text("You must set tomb_pos1 and tomb_pos2 first.", NamedTextColor.RED));
                    yield true;
                }
                boolean ok = dungeonSecrets != null && dungeonSecrets.addTombToEdit(player, sel.tombPos1, sel.tombPos2);
                player.sendMessage(Component.text(
                        ok ? "Tomb added." : "Failed to add tomb (not in secret edit mode or out of bounds).",
                        ok ? NamedTextColor.GREEN : NamedTextColor.RED));
                yield true;
            }
            case "give_tnt" -> {
                int amount = 16;
                if (args.length >= 2) {
                    try {
                        amount = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ignored) {
                        amount = 16;
                    }
                }
                if (dungeonSecrets != null) {
                    player.getInventory().addItem(dungeonSecrets.specialTntItem(amount));
                }
                player.sendMessage(Component.text("Special TNT given x" + amount, NamedTextColor.GREEN));
                yield true;
            }
            case "blaze08_bow" -> {
                Test_dungeons plugin = Test_dungeons.getInstance();
                if (plugin == null) {
                    player.sendMessage(Component.text("Plugin not ready.", NamedTextColor.RED));
                    yield true;
                }
                NamespacedKey key = new NamespacedKey(plugin, "blaze08_oneshot_bow");
                ItemStack bow = new ItemStack(Material.BOW, 1);
                ItemMeta meta = bow.getItemMeta();
                if (meta != null) {
                    meta.displayName(Component.text("烈焰人秒杀弓", NamedTextColor.LIGHT_PURPLE));
                    meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
                    meta.addEnchant(Enchantment.INFINITY, 1, true);
                    bow.setItemMeta(meta);
                }
                player.getInventory().addItem(bow);
                player.getInventory().addItem(new ItemStack(Material.ARROW, 1));
                player.sendMessage(Component.text("已给予：烈焰人秒杀弓", NamedTextColor.GREEN));
                yield true;
            }
            case "ice05_level" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /dungeon_template ice05_level <small|medium|large>", NamedTextColor.RED));
                    yield true;
                }
                String key = args[1] != null ? args[1].trim().toLowerCase(Locale.ROOT) : "";
                if (!key.equals("small") && !key.equals("medium") && !key.equals("large")) {
                    player.sendMessage(Component.text("Level must be small, medium, or large.", NamedTextColor.RED));
                    yield true;
                }
                sel.ice05LevelKey = key;
                player.sendMessage(Component.text("Ice05 level set to " + key, NamedTextColor.GRAY));
                yield true;
            }
            case "ice05_board_pos1" -> {
                Location loc = pickTargetBlockLocation(player);
                sel.ice05BoardPos1 = loc;
                player.sendMessage(Component.text("Ice05 board pos1 set to " + format(loc), NamedTextColor.GRAY));
                yield true;
            }
            case "ice05_board_pos2" -> {
                Location loc = pickTargetBlockLocation(player);
                sel.ice05BoardPos2 = loc;
                player.sendMessage(Component.text("Ice05 board pos2 set to " + format(loc), NamedTextColor.GRAY));
                yield true;
            }
            case "ice05_start" -> {
                Location loc = pickTargetBlockLocation(player);
                sel.ice05Start = loc;
                player.sendMessage(Component.text("Ice05 start set to " + format(loc), NamedTextColor.GRAY));
                yield true;
            }
            case "ice05_end" -> {
                Location loc = pickTargetBlockLocation(player);
                sel.ice05End = loc;
                player.sendMessage(Component.text("Ice05 end set to " + format(loc), NamedTextColor.GRAY));
                yield true;
            }
            case "ice05_save" -> {
                if (sel.pos1 == null) {
                    player.sendMessage(Component.text("You must set pos1 (room origin) first.", NamedTextColor.RED));
                    yield true;
                }
                if (sel.ice05LevelKey == null || sel.ice05LevelKey.isBlank()) {
                    player.sendMessage(Component.text("You must set ice05_level first.", NamedTextColor.RED));
                    yield true;
                }
                if (sel.ice05BoardPos1 == null || sel.ice05BoardPos2 == null) {
                    player.sendMessage(Component.text("You must set ice05_board_pos1 and ice05_board_pos2 first.", NamedTextColor.RED));
                    yield true;
                }
                if (sel.ice05Start == null || sel.ice05End == null) {
                    player.sendMessage(Component.text("You must set ice05_start and ice05_end first.", NamedTextColor.RED));
                    yield true;
                }
                Test_dungeons plugin = Test_dungeons.getInstance();
                boolean ok = PuzzleRoom05IcePath.saveLevelConfig(plugin, sel.ice05LevelKey, sel.pos1,
                        sel.ice05BoardPos1, sel.ice05BoardPos2, sel.ice05Start, sel.ice05End);
                if (ok && plugin != null) {
                    plugin.reloadPuzzleRoom05Config();
                }
                player.sendMessage(Component.text(ok ? "Ice05 config saved." : "Failed to save Ice05 config.", ok ? NamedTextColor.GREEN : NamedTextColor.RED));
                yield true;
            }
            case "blaze08_circle_pos1" -> {
                Location loc = pickTargetBlockLocation(player);
                sel.blaze08CirclePos1 = loc;
                player.sendMessage(Component.text("Blaze08 circle pos1 set to " + format(loc), NamedTextColor.GRAY));
                yield true;
            }
            case "blaze08_circle_pos2" -> {
                Location loc = pickTargetBlockLocation(player);
                sel.blaze08CirclePos2 = loc;
                player.sendMessage(Component.text("Blaze08 circle pos2 set to " + format(loc), NamedTextColor.GRAY));
                yield true;
            }
            case "blaze08_progress_pos1" -> {
                Location loc = pickTargetBlockLocation(player);
                sel.blaze08ProgressPos1 = loc;
                player.sendMessage(Component.text("Blaze08 progress pos1 set to " + format(loc), NamedTextColor.GRAY));
                yield true;
            }
            case "blaze08_progress_pos2" -> {
                Location loc = pickTargetBlockLocation(player);
                sel.blaze08ProgressPos2 = loc;
                player.sendMessage(Component.text("Blaze08 progress pos2 set to " + format(loc), NamedTextColor.GRAY));
                yield true;
            }
            case "blaze08_save" -> {
                if (sel.pos1 == null) {
                    player.sendMessage(Component.text("You must set pos1 (room origin) first.", NamedTextColor.RED));
                    yield true;
                }
                if (sel.blaze08CirclePos1 == null || sel.blaze08CirclePos2 == null || sel.blaze08ProgressPos1 == null || sel.blaze08ProgressPos2 == null) {
                    player.sendMessage(Component.text("You must set all blaze08 positions first.", NamedTextColor.RED));
                    yield true;
                }
                Test_dungeons plugin = Test_dungeons.getInstance();
                if (plugin == null || plugin.getPuzzleRoom08() == null) {
                    player.sendMessage(Component.text("PuzzleRoom08 not initialized.", NamedTextColor.RED));
                    yield true;
                }
                StructureTemplate.Rotation r = sel.lastPasteRotation != null ? sel.lastPasteRotation : StructureTemplate.Rotation.R0;
                int sx = Math.max(1, sel.lastPasteSizeX);
                int sz = Math.max(1, sel.lastPasteSizeZ);
                boolean ok = plugin.getPuzzleRoom08().saveConfig(sel.pos1, r, sx, sz, sel.blaze08CirclePos1, sel.blaze08CirclePos2, sel.blaze08ProgressPos1, sel.blaze08ProgressPos2);
                player.sendMessage(Component.text(ok ? "Blaze08 config saved." : "Failed to save Blaze08 config.", ok ? NamedTextColor.GREEN : NamedTextColor.RED));
                yield true;
            }
            case "pusher_region_pos1" -> {
                Location loc = pickTargetBlockLocation(player);
                sel.pusherRegionPos1 = loc;
                player.sendMessage(Component.text("Pusher region pos1 set to " + format(loc), NamedTextColor.GRAY));
                yield true;
            }
            case "pusher_region_pos2" -> {
                Location loc = pickTargetBlockLocation(player);
                sel.pusherRegionPos2 = loc;
                player.sendMessage(Component.text("Pusher region pos2 set to " + format(loc), NamedTextColor.GRAY));
                yield true;
            }
            case "pusher_start" -> {
                Location loc = pickTargetBlockLocation(player);
                sel.pusherStart = loc;
                player.sendMessage(Component.text("Pusher start set to " + format(loc), NamedTextColor.GRAY));
                yield true;
            }
            case "pusher_end" -> {
                Location loc = pickTargetBlockLocation(player);
                sel.pusherEnd = loc;
                player.sendMessage(Component.text("Pusher end set to " + format(loc), NamedTextColor.GRAY));
                yield true;
            }
            case "pusher_reload" -> {
                Test_dungeons plugin = Test_dungeons.getInstance();
                if (plugin == null) {
                    player.sendMessage(Component.text("Plugin not ready.", NamedTextColor.RED));
                    yield true;
                }
                reloadPusherConfigs(plugin);
                player.sendMessage(Component.text("Pusher configs reloaded.", NamedTextColor.GREEN));
                yield true;
            }
            case "pusher_clear" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /dungeon_template pusher_clear <template>", NamedTextColor.RED));
                    yield true;
                }
                Test_dungeons plugin = Test_dungeons.getInstance();
                if (plugin == null) {
                    player.sendMessage(Component.text("Plugin not ready.", NamedTextColor.RED));
                    yield true;
                }
                String templateName = sanitizeName(args[1]);
                if (templateName.isEmpty()) {
                    player.sendMessage(Component.text("Invalid template name.", NamedTextColor.RED));
                    yield true;
                }
                ensurePusherConfigsLoaded(plugin);
                PUSHER_CONFIGS.remove(templateName);
                savePusherConfigs(plugin);
                player.sendMessage(Component.text("Pushers cleared for template '" + templateName + "'.", NamedTextColor.GREEN));
                yield true;
            }
            case "pusher_save" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /dungeon_template pusher_save <template> [id]", NamedTextColor.RED));
                    yield true;
                }
                if (sel.pos1 == null) {
                    player.sendMessage(Component.text("You must set pos1 (room origin) first.", NamedTextColor.RED));
                    yield true;
                }
                if (sel.pusherRegionPos1 == null || sel.pusherRegionPos2 == null || sel.pusherStart == null || sel.pusherEnd == null) {
                    player.sendMessage(Component.text("You must set pusher_region_pos1, pusher_region_pos2, pusher_start, pusher_end first.", NamedTextColor.RED));
                    yield true;
                }
                Test_dungeons plugin = Test_dungeons.getInstance();
                if (plugin == null) {
                    player.sendMessage(Component.text("Plugin not ready.", NamedTextColor.RED));
                    yield true;
                }
                World world = sel.pos1.getWorld();
                if (world == null
                        || sel.pusherRegionPos1.getWorld() == null || !world.equals(sel.pusherRegionPos1.getWorld())
                        || sel.pusherRegionPos2.getWorld() == null || !world.equals(sel.pusherRegionPos2.getWorld())
                        || sel.pusherStart.getWorld() == null || !world.equals(sel.pusherStart.getWorld())
                        || sel.pusherEnd.getWorld() == null || !world.equals(sel.pusherEnd.getWorld())) {
                    player.sendMessage(Component.text("Room origin and all pusher points must be in the same world.", NamedTextColor.RED));
                    yield true;
                }

                String templateName = sanitizeName(args[1]);
                if (templateName.isEmpty()) {
                    player.sendMessage(Component.text("Invalid template name.", NamedTextColor.RED));
                    yield true;
                }
                String idRaw = args.length >= 3 ? sanitizeName(args[2]) : "";
                final String id = idRaw.isEmpty() ? ("p" + System.currentTimeMillis()) : idRaw;

                StructureTemplate.Rotation rotation = sel.lastPasteRotation != null ? sel.lastPasteRotation : StructureTemplate.Rotation.R0;
                int[] sizeXZ0 = loadTemplateSizeXZ0(plugin, templateName);
                if (sizeXZ0 == null) {
                    player.sendMessage(Component.text("Template not found for size: " + templateName, NamedTextColor.RED));
                    yield true;
                }
                int sizeX0 = sizeXZ0[0];
                int sizeZ0 = sizeXZ0[1];
                int sizeXRot = (rotation == StructureTemplate.Rotation.R90 || rotation == StructureTemplate.Rotation.R270) ? sizeZ0 : sizeX0;
                int sizeZRot = (rotation == StructureTemplate.Rotation.R90 || rotation == StructureTemplate.Rotation.R270) ? sizeX0 : sizeZ0;

                int originX = sel.pos1.getBlockX();
                int originY = sel.pos1.getBlockY();
                int originZ = sel.pos1.getBlockZ();

                int rMinX = Math.min(sel.pusherRegionPos1.getBlockX(), sel.pusherRegionPos2.getBlockX()) - originX;
                int rMinY = Math.min(sel.pusherRegionPos1.getBlockY(), sel.pusherRegionPos2.getBlockY()) - originY;
                int rMinZ = Math.min(sel.pusherRegionPos1.getBlockZ(), sel.pusherRegionPos2.getBlockZ()) - originZ;
                int rMaxX = Math.max(sel.pusherRegionPos1.getBlockX(), sel.pusherRegionPos2.getBlockX()) - originX;
                int rMaxY = Math.max(sel.pusherRegionPos1.getBlockY(), sel.pusherRegionPos2.getBlockY()) - originY;
                int rMaxZ = Math.max(sel.pusherRegionPos1.getBlockZ(), sel.pusherRegionPos2.getBlockZ()) - originZ;

                int sXR = sel.pusherStart.getBlockX() - originX;
                int sYR = sel.pusherStart.getBlockY() - originY;
                int sZR = sel.pusherStart.getBlockZ() - originZ;

                int eXR = sel.pusherEnd.getBlockX() - originX;
                int eYR = sel.pusherEnd.getBlockY() - originY;
                int eZR = sel.pusherEnd.getBlockZ() - originZ;

                int[] r0a = inverseRotateXZ(rMinX, rMinZ, rotation, sizeXRot, sizeZRot);
                int[] r0b = inverseRotateXZ(rMaxX, rMaxZ, rotation, sizeXRot, sizeZRot);
                int[] s0 = inverseRotateXZ(sXR, sZR, rotation, sizeXRot, sizeZRot);
                int[] e0 = inverseRotateXZ(eXR, eZR, rotation, sizeXRot, sizeZRot);

                PusherConfig cfg = new PusherConfig(
                        id,
                        Math.min(r0a[0], r0b[0]), rMinY, Math.min(r0a[1], r0b[1]),
                        Math.max(r0a[0], r0b[0]), rMaxY, Math.max(r0a[1], r0b[1]),
                        s0[0], sYR, s0[1],
                        e0[0], eYR, e0[1],
                        null, 0
                );

                ensurePusherConfigsLoaded(plugin);
                List<PusherConfig> list = PUSHER_CONFIGS.computeIfAbsent(templateName, k -> new ArrayList<>());
                list.removeIf(p -> p != null && id.equalsIgnoreCase(p.id));
                list.add(cfg);
                savePusherConfigs(plugin);
                player.sendMessage(Component.text("Pusher saved template='" + templateName + "' id='" + id + "' rotationSaved=R0.", NamedTextColor.GREEN));
                yield true;
            }
            case "pusher_link" -> {
                if (args.length < 4) {
                    player.sendMessage(Component.text("Usage: /dungeon_template pusher_link <template> <idA> <idB>", NamedTextColor.RED));
                    yield true;
                }
                Test_dungeons plugin = Test_dungeons.getInstance();
                if (plugin == null) {
                    player.sendMessage(Component.text("Plugin not ready.", NamedTextColor.RED));
                    yield true;
                }
                String templateName = sanitizeName(args[1]);
                String idA = sanitizeName(args[2]);
                String idB = sanitizeName(args[3]);
                if (templateName.isEmpty() || idA.isEmpty() || idB.isEmpty()) {
                    player.sendMessage(Component.text("Invalid arguments.", NamedTextColor.RED));
                    yield true;
                }
                ensurePusherConfigsLoaded(plugin);
                List<PusherConfig> list = PUSHER_CONFIGS.get(templateName);
                if (list == null || list.isEmpty()) {
                    player.sendMessage(Component.text("No pushers found for template '" + templateName + "'.", NamedTextColor.RED));
                    yield true;
                }
                PusherConfig a = findPusherById(list, idA);
                PusherConfig b = findPusherById(list, idB);
                if (a == null || b == null) {
                    player.sendMessage(Component.text("Pusher id not found.", NamedTextColor.RED));
                    yield true;
                }
                String linkKey = UUID.randomUUID().toString();
                list.removeIf(p -> p != null && (idA.equalsIgnoreCase(p.id) || idB.equalsIgnoreCase(p.id)));
                list.add(a.withLink(linkKey, 0));
                list.add(b.withLink(linkKey, 1));
                savePusherConfigs(plugin);
                player.sendMessage(Component.text("Linked pushers template='" + templateName + "' A='" + idA + "' B='" + idB + "'.", NamedTextColor.GREEN));
                yield true;
            }
            case "pusher_unlink" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Usage: /dungeon_template pusher_unlink <template> <id>", NamedTextColor.RED));
                    yield true;
                }
                Test_dungeons plugin = Test_dungeons.getInstance();
                if (plugin == null) {
                    player.sendMessage(Component.text("Plugin not ready.", NamedTextColor.RED));
                    yield true;
                }
                String templateName = sanitizeName(args[1]);
                String id = sanitizeName(args[2]);
                if (templateName.isEmpty() || id.isEmpty()) {
                    player.sendMessage(Component.text("Invalid arguments.", NamedTextColor.RED));
                    yield true;
                }
                ensurePusherConfigsLoaded(plugin);
                List<PusherConfig> list = PUSHER_CONFIGS.get(templateName);
                if (list == null || list.isEmpty()) {
                    player.sendMessage(Component.text("No pushers found for template '" + templateName + "'.", NamedTextColor.RED));
                    yield true;
                }
                PusherConfig target = findPusherById(list, id);
                if (target == null) {
                    player.sendMessage(Component.text("Pusher id not found.", NamedTextColor.RED));
                    yield true;
                }
                String lk = target.linkKey;
                for (int i = 0; i < list.size(); i++) {
                    PusherConfig p = list.get(i);
                    if (p == null) continue;
                    if (id.equalsIgnoreCase(p.id) || (lk != null && !lk.isBlank() && lk.equals(p.linkKey))) {
                        list.set(i, p.withLink(null, 0));
                    }
                }
                savePusherConfigs(plugin);
                player.sendMessage(Component.text("Unlinked pusher template='" + templateName + "' id='" + id + "'.", NamedTextColor.GREEN));
                yield true;
            }
            case "pusher_stop" -> {
                stopPusher(sel);
                player.sendMessage(Component.text("Pusher stopped.", NamedTextColor.GREEN));
                yield true;
            }
            case "pusher_run" -> {
                Test_dungeons plugin = Test_dungeons.getInstance();
                if (plugin == null || !plugin.isEnabled()) {
                    player.sendMessage(Component.text("Plugin not ready.", NamedTextColor.RED));
                    yield true;
                }
                if (sel.pusherRegionPos1 == null || sel.pusherRegionPos2 == null) {
                    player.sendMessage(Component.text("You must set pusher_region_pos1 and pusher_region_pos2 first.", NamedTextColor.RED));
                    yield true;
                }
                if (sel.pusherStart == null || sel.pusherEnd == null) {
                    player.sendMessage(Component.text("You must set pusher_start and pusher_end first.", NamedTextColor.RED));
                    yield true;
                }
                World w = sel.pusherRegionPos1.getWorld();
                if (w == null
                        || sel.pusherRegionPos2.getWorld() == null
                        || sel.pusherStart.getWorld() == null
                        || sel.pusherEnd.getWorld() == null
                        || !w.equals(sel.pusherRegionPos2.getWorld())
                        || !w.equals(sel.pusherStart.getWorld())
                        || !w.equals(sel.pusherEnd.getWorld())) {
                    player.sendMessage(Component.text("All pusher positions must be in the same world.", NamedTextColor.RED));
                    yield true;
                }

                int minX = Math.min(sel.pusherRegionPos1.getBlockX(), sel.pusherRegionPos2.getBlockX());
                int minY = Math.min(sel.pusherRegionPos1.getBlockY(), sel.pusherRegionPos2.getBlockY());
                int minZ = Math.min(sel.pusherRegionPos1.getBlockZ(), sel.pusherRegionPos2.getBlockZ());
                int maxX = Math.max(sel.pusherRegionPos1.getBlockX(), sel.pusherRegionPos2.getBlockX());
                int maxY = Math.max(sel.pusherRegionPos1.getBlockY(), sel.pusherRegionPos2.getBlockY());
                int maxZ = Math.max(sel.pusherRegionPos1.getBlockZ(), sel.pusherRegionPos2.getBlockZ());

                int sx = sel.pusherStart.getBlockX();
                int sy = sel.pusherStart.getBlockY();
                int sz = sel.pusherStart.getBlockZ();
                if (sx < minX || sx > maxX || sy < minY || sy > maxY || sz < minZ || sz > maxZ) {
                    player.sendMessage(Component.text("pusher_start must be inside the selected region.", NamedTextColor.RED));
                    yield true;
                }

                int dx = sel.pusherEnd.getBlockX() - sx;
                int dy = sel.pusherEnd.getBlockY() - sy;
                int dz = sel.pusherEnd.getBlockZ() - sz;
                if (dx == 0 && dy == 0 && dz == 0) {
                    player.sendMessage(Component.text("pusher_end must be different from pusher_start.", NamedTextColor.RED));
                    yield true;
                }

                int adx = Math.abs(dx);
                int ady = Math.abs(dy);
                int adz = Math.abs(dz);
                int stepX = 0, stepY = 0, stepZ = 0, length = 0;
                if (adx >= ady && adx >= adz) {
                    stepX = dx > 0 ? 1 : -1;
                    length = adx;
                } else if (adz >= ady) {
                    stepZ = dz > 0 ? 1 : -1;
                    length = adz;
                } else {
                    stepY = dy > 0 ? 1 : -1;
                    length = ady;
                }

                if ((stepX != 0 && (dy != 0 || dz != 0)) || (stepY != 0 && (dx != 0 || dz != 0)) || (stepZ != 0 && (dx != 0 || dy != 0))) {
                    player.sendMessage(Component.text("Note: pusher moves only on one axis (picked the dominant axis).", NamedTextColor.YELLOW));
                }

                stopPusher(sel);

                PusherRuntime runtime = buildPusherRuntime(w, minX, minY, minZ, maxX, maxY, maxZ, stepX, stepY, stepZ, length);
                if (runtime == null) {
                    player.sendMessage(Component.text("Failed to start pusher (region must be filled with one same block type).", NamedTextColor.RED));
                    yield true;
                }
                sel.pusherRuntime = runtime;
                sel.pusherTask = new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!player.isOnline()) {
                            stopPusher(sel);
                            cancel();
                            return;
                        }
                        World ww = Bukkit.getWorld(runtime.worldName);
                        if (ww == null) {
                            stopPusher(sel);
                            cancel();
                            return;
                        }
                        boolean ok = tickPusher(ww, runtime);
                        if (!ok) {
                            player.sendMessage(Component.text("Pusher stopped (blocked or invalid).", NamedTextColor.RED));
                            stopPusher(sel);
                            cancel();
                        }
                    }
                };
                sel.pusherTask.runTaskTimer(plugin, 1L, PUSHER_TICK_PERIOD_TICKS);
                player.sendMessage(Component.text("Pusher started. Use /dungeon_template pusher_stop to stop.", NamedTextColor.GREEN));
                yield true;
            }
            case "check" -> {
                if (sel.pos1 == null || sel.pos2 == null) {
                    player.sendMessage(Component.text("You must set pos1 and pos2 first.", NamedTextColor.RED));
                    yield true;
                }
                if (!sel.pos1.getWorld().equals(sel.pos2.getWorld())) {
                    player.sendMessage(Component.text("pos1 and pos2 must be in the same world.", NamedTextColor.RED));
                    yield true;
                }

                Location target = pickTargetBlockLocation(player);
                if (target.getWorld() == null || !target.getWorld().equals(sel.pos1.getWorld())) {
                    player.sendMessage(Component.text("Target block must be in the same world as the selection.",
                            NamedTextColor.RED));
                    yield true;
                }

                int minX = Math.min(sel.pos1.getBlockX(), sel.pos2.getBlockX());
                int minY = Math.min(sel.pos1.getBlockY(), sel.pos2.getBlockY());
                int minZ = Math.min(sel.pos1.getBlockZ(), sel.pos2.getBlockZ());
                int maxX = Math.max(sel.pos1.getBlockX(), sel.pos2.getBlockX());
                int maxY = Math.max(sel.pos1.getBlockY(), sel.pos2.getBlockY());
                int maxZ = Math.max(sel.pos1.getBlockZ(), sel.pos2.getBlockZ());

                int tx = target.getBlockX();
                int ty = target.getBlockY();
                int tz = target.getBlockZ();

                boolean inside = tx >= minX && tx <= maxX && ty >= minY && ty <= maxY && tz >= minZ && tz <= maxZ;
                int lx = tx - minX;
                int ly = ty - minY;
                int lz = tz - minZ;
                int sizeX = (maxX - minX) + 1;
                int sizeY = (maxY - minY) + 1;
                int sizeZ = (maxZ - minZ) + 1;

                StructureTemplate.Rotation rotation = args.length >= 2 ? parseRotation(args[1]) : null;
                if (rotation != null) {
                    int[] unrot = inverseRotateXZ(lx, lz, rotation, sizeX, sizeZ);
                    player.sendMessage(Component.text(
                            "Target=" + format(target)
                                    + " local=(" + lx + "," + ly + "," + lz + ")"
                                    + " localR0=(" + unrot[0] + "," + ly + "," + unrot[1] + ")"
                                    + " rotation=" + rotation.name()
                                    + " inside=" + inside
                                    + " selectionMin=(" + minX + "," + minY + "," + minZ + ")"
                                    + " size=" + sizeX + "x" + sizeY + "x" + sizeZ,
                            inside ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
                    yield true;
                }

                int[] unrot0 = inverseRotateXZ(lx, lz, StructureTemplate.Rotation.R0, sizeX, sizeZ);
                int[] unrot90 = inverseRotateXZ(lx, lz, StructureTemplate.Rotation.R90, sizeX, sizeZ);
                int[] unrot180 = inverseRotateXZ(lx, lz, StructureTemplate.Rotation.R180, sizeX, sizeZ);
                int[] unrot270 = inverseRotateXZ(lx, lz, StructureTemplate.Rotation.R270, sizeX, sizeZ);
                player.sendMessage(Component.text(
                        "Target=" + format(target)
                                + " local=(" + lx + "," + ly + "," + lz + ")"
                                + " localR0[" + StructureTemplate.Rotation.R0.name() + "]=(" + unrot0[0] + "," + ly
                                + "," + unrot0[1] + ")"
                                + " localR0[" + StructureTemplate.Rotation.R90.name() + "]=(" + unrot90[0] + "," + ly
                                + "," + unrot90[1] + ")"
                                + " localR0[" + StructureTemplate.Rotation.R180.name() + "]=(" + unrot180[0] + "," + ly
                                + "," + unrot180[1] + ")"
                                + " localR0[" + StructureTemplate.Rotation.R270.name() + "]=(" + unrot270[0] + "," + ly
                                + "," + unrot270[1] + ")"
                                + " inside=" + inside
                                + " selectionMin=(" + minX + "," + minY + "," + minZ + ")"
                                + " size=" + sizeX + "x" + sizeY + "x" + sizeZ,
                        inside ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
                yield true;
            }
            case "paste" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text(
                            "Usage: /dungeon_template paste <name> [rotation] [walls|open|all] [secrets] [monsters]",
                            NamedTextColor.RED));
                    yield true;
                }

                String name = sanitizeName(args[1]);
                if (name.isEmpty()) {
                    player.sendMessage(Component.text("Invalid name.", NamedTextColor.RED));
                    yield true;
                }

                StructureTemplate.Rotation rotation = StructureTemplate.Rotation.R0;
                String mode = "walls";
                boolean withSecrets = false;
                boolean withMonsters = false;
                for (int i = 2; i < args.length; i++) {
                    String token = args[i] != null ? args[i].trim().toLowerCase(Locale.ROOT) : "";
                    if (token.isEmpty())
                        continue;
                    StructureTemplate.Rotation r = parseRotation(token);
                    if (r != null) {
                        rotation = r;
                        continue;
                    }
                    if (token.equals("walls") || token.equals("open") || token.equals("all")) {
                        mode = token;
                        continue;
                    }
                    if (token.equals("secrets") || token.equals("secret") || token.equals("run")) {
                        withSecrets = true;
                        continue;
                    }
                    if (token.equals("monsters") || token.equals("monster")) {
                        withMonsters = true;
                    }
                }
                boolean autoMonsters = false;
                if ("trap_room_01".equalsIgnoreCase(name) && withSecrets && !withMonsters) {
                    withMonsters = true;
                    autoMonsters = true;
                }

                File dir = new File(Test_dungeons.getInstance().getDataFolder(), "structures");
                File single = new File(dir, name + ".yml");
                File walls = new File(dir, name + "_walls.yml");
                File doors = new File(dir, name + "_doors.yml");

                StructureTemplate raw0 = null;
                StructureTemplate raw1 = null;
                StructureTemplate t0 = null;
                StructureTemplate t1 = null;
                boolean isPair = false;
                try {
                    if (walls.exists() && doors.exists()) {
                        raw0 = StructureTemplate.load(walls);
                        raw1 = StructureTemplate.load(doors);
                        t0 = raw0.rotate(rotation);
                        t1 = raw1.rotate(rotation);
                        isPair = true;
                    } else if ("puzzle_room_05".equalsIgnoreCase(name) && !walls.exists() && doors.exists()) {
                        File[] variants = dir.listFiles((d, n) -> {
                            String s = n != null ? n.toLowerCase(Locale.ROOT) : "";
                            return s.startsWith("puzzle_room_05-") && s.endsWith("_walls.yml");
                        });
                        if (variants != null && variants.length > 0) {
                            File pick = variants[ThreadLocalRandom.current().nextInt(variants.length)];
                            raw0 = StructureTemplate.load(pick);
                            raw1 = StructureTemplate.load(doors);
                            t0 = raw0.rotate(rotation);
                            t1 = raw1.rotate(rotation);
                            isPair = true;
                        } else {
                            player.sendMessage(Component.text("Template not found: " + name, NamedTextColor.RED));
                            yield true;
                        }
                    } else if (single.exists()) {
                        raw0 = StructureTemplate.load(single);
                        t0 = raw0.rotate(rotation);
                    } else {
                        player.sendMessage(Component.text("Template not found: " + name, NamedTextColor.RED));
                        yield true;
                    }
                } catch (IOException e) {
                    player.sendMessage(Component.text("Failed to load: " + e.getMessage(), NamedTextColor.RED));
                    yield true;
                }

                Location origin = pickTargetBlockLocation(player);
                World world = origin.getWorld();
                if (world == null) {
                    player.sendMessage(Component.text("World is null.", NamedTextColor.RED));
                    yield true;
                }
                if (autoMonsters) {
                    player.sendMessage(Component.text("trap_room_01: 已自动启用 monsters（否则只用 secrets 不会刷蠹虫）", NamedTextColor.GRAY));
                }
                if (Test_dungeons.getInstance() != null && Test_dungeons.getInstance().getLogger() != null) {
                    Test_dungeons.getInstance().getLogger().info("template_paste name=" + name
                            + " rot=" + rotation.name()
                            + " mode=" + mode
                            + " secrets=" + withSecrets
                            + " monsters=" + withMonsters
                            + " origin=" + origin.getBlockX() + "," + origin.getBlockY() + "," + origin.getBlockZ()
                            + " pair=" + isPair);
                }

                if (!mode.equals("walls") && !mode.equals("open") && !mode.equals("all")) {
                    player.sendMessage(Component.text("Mode must be walls, open, or all.", NamedTextColor.RED));
                    yield true;
                }

                if (t0 != null) {
                    StructureTemplate.PasteJob job = t0.pasteJob(world, origin, null, name);
                    while (!job.isDone()) {
                        job.tick(Integer.MAX_VALUE);
                    }
                }
                if (t1 != null) {
                    if ("all".equals(mode)) {
                        StructureTemplate.PasteJob job = t1.pasteJob(world, origin, null, name);
                        while (!job.isDone()) {
                            job.tick(Integer.MAX_VALUE);
                        }
                    } else if ("open".equals(mode)) {
                        Set<Long> include = new HashSet<>();
                        for (StructureTemplate.DoorMarker m : t1.doorMarkers()) {
                            StructureTemplate.addDoorOverlayIncludePositions(include, m, t1.sizeX(), t1.sizeY(),
                                    t1.sizeZ());
                        }
                        if (!include.isEmpty()) {
                            StructureTemplate.PasteJob job = t1.pasteJob(world, origin, include, name);
                            while (!job.isDone()) {
                                job.tick(Integer.MAX_VALUE);
                            }
                        }
                    }
                }

                int sizeX = 0;
                int sizeY = 0;
                int sizeZ = 0;
                if (t0 != null) {
                    sizeX = Math.max(sizeX, t0.sizeX());
                    sizeY = Math.max(sizeY, t0.sizeY());
                    sizeZ = Math.max(sizeZ, t0.sizeZ());
                }
                if (t1 != null) {
                    sizeX = Math.max(sizeX, t1.sizeX());
                    sizeY = Math.max(sizeY, t1.sizeY());
                    sizeZ = Math.max(sizeZ, t1.sizeZ());
                }

                Location p1 = origin.getBlock().getLocation();
                processRoomBlocks(p1, rotation, sizeX, sizeY, sizeZ, name);
                Location p2 = p1.clone().add(sizeX - 1, sizeY - 1, sizeZ - 1);
                sel.pos1 = p1;
                sel.pos2 = p2;
                sel.lastPasteRotation = rotation;
                sel.lastPasteSizeX = sizeX;
                sel.lastPasteSizeZ = sizeZ;

                int secretSizeX = raw0 != null ? raw0.sizeX() : sizeX;
                int secretSizeZ = raw0 != null ? raw0.sizeZ() : sizeZ;
                if (withSecrets && dungeonSecrets != null) {
                    String groupKey = "tpl:" + name + ":" + p1.getBlockX() + ":" + p1.getBlockY() + ":"
                            + p1.getBlockZ();
                    dungeonSecrets.beginTemplateTestRun(world, p1, secretSizeX, secretSizeZ, groupKey, name);
                    dungeonSecrets.spawnGroupSecrets(world, p1, rotation, secretSizeX, secretSizeZ, groupKey, name);
                }
                if (withMonsters && dungeonMonsters != null) {
                    String groupKey = "tpl:" + name + ":" + p1.getBlockX() + ":" + p1.getBlockY() + ":"
                            + p1.getBlockZ();
                    dungeonMonsters.beginTemplateTestRun(world, groupKey, name);
                    dungeonMonsters.spawnGroupMonsters(world, p1, rotation, secretSizeX, secretSizeZ, groupKey, name);
                }
                {
                    String groupKey = "tpl:" + name + ":" + p1.getBlockX() + ":" + p1.getBlockY() + ":"
                            + p1.getBlockZ();
                    registerRoomPushers(Test_dungeons.getInstance(), world, p1, rotation, secretSizeX, secretSizeZ, groupKey, name);
                }

                if ("puzzle_room_06".equalsIgnoreCase(name)) {
                    Test_dungeons plugin = Test_dungeons.getInstance();
                    if (plugin != null) {
                        plugin.registerPuzzleRoom06(world, p1, rotation, sizeX, sizeZ);
                    }
                }

                if ("puzzle_room_01".equalsIgnoreCase(name)) {
                    Test_dungeons plugin = Test_dungeons.getInstance();
                    if (plugin != null) {
                        plugin.registerPuzzleRoom01(world, p1, rotation, sizeX, sizeZ);
                    }
                }

                if ("puzzle_room_05".equalsIgnoreCase(name)) {
                    Test_dungeons plugin = Test_dungeons.getInstance();
                    if (plugin != null) {
                        plugin.registerPuzzleRoom05(world, p1, rotation, sizeX, sizeZ);
                    }
                }

                if ("puzzle_room_03".equalsIgnoreCase(name)) {
                    Test_dungeons plugin = Test_dungeons.getInstance();
                    if (plugin != null) {
                        plugin.registerPuzzleRoom03(world, p1, rotation, sizeX, sizeZ);
                    }
                }

                if ("puzzle_room_04".equalsIgnoreCase(name)) {
                    Test_dungeons plugin = Test_dungeons.getInstance();
                    if (plugin != null) {
                        plugin.registerPuzzleRoom04(world, p1, rotation, sizeX, sizeZ);
                    }
                }

                if ("puzzle_room_07".equalsIgnoreCase(name)) {
                    Test_dungeons plugin = Test_dungeons.getInstance();
                    if (plugin != null) {
                        plugin.registerPuzzleRoom07(world, p1, rotation, sizeX, sizeZ);
                    }
                }
                if ("puzzle_room_08".equalsIgnoreCase(name)) {
                    Test_dungeons plugin = Test_dungeons.getInstance();
                    if (plugin != null) {
                        plugin.registerPuzzleRoom08(world, p1, rotation, sizeX, sizeZ);
                    }
                }
                if ("start_room".equalsIgnoreCase(name)) {
                    Test_dungeons plugin = Test_dungeons.getInstance();
                    if (plugin != null) {
                        plugin.registerStartRoomNpc(world, p1, rotation, sizeX, sizeZ, List.of(BlockFace.SOUTH));
                    }
                }

                int markerY = p2.getBlockY() + 2;
                int maxY = world.getMaxHeight() - 1;
                if (markerY > maxY)
                    markerY = maxY;
                if (markerY < world.getMinHeight())
                    markerY = world.getMinHeight();
                world.getBlockAt(p1.getBlockX(), markerY, p1.getBlockZ()).setType(Material.LIME_CONCRETE, false);
                world.getBlockAt(p2.getBlockX(), markerY, p2.getBlockZ()).setType(Material.RED_CONCRETE, false);

                player.sendMessage(Component.text(
                        "Pasted " + (isPair ? "pair" : "template") + " '" + name + "' rotation=" + rotation.name()
                                + (isPair ? " mode=" + mode : "")
                                + (withSecrets ? " secrets=on" : "")
                                + (withMonsters ? " monsters=on" : "")
                                + " origin=" + format(p1)
                                + " pos1=" + format(p1)
                                + " pos2=" + format(p2)
                                + " markerY=" + markerY
                                + " size=" + sizeX + "x" + sizeY + "x" + sizeZ,
                        NamedTextColor.GREEN));
                yield true;
            }
            case "save" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /dungeon_template save <name>", NamedTextColor.RED));
                    yield true;
                }
                if (sel.pos1 == null || sel.pos2 == null) {
                    player.sendMessage(Component.text("You must set pos1 and pos2 first.", NamedTextColor.RED));
                    yield true;
                }
                if (!sel.pos1.getWorld().equals(sel.pos2.getWorld())) {
                    player.sendMessage(Component.text("pos1 and pos2 must be in the same world.", NamedTextColor.RED));
                    yield true;
                }

                String name = sanitizeName(args[1]);
                if (name.isEmpty()) {
                    player.sendMessage(Component.text("Invalid name.", NamedTextColor.RED));
                    yield true;
                }

                File dir = new File(Test_dungeons.getInstance().getDataFolder(), "structures");
                if (!dir.exists() && !dir.mkdirs()) {
                    player.sendMessage(Component.text("Failed to create structures folder.", NamedTextColor.RED));
                    yield true;
                }

                File out = new File(dir, name + ".yml");
                StructureTemplate template = StructureTemplate.capture(sel.pos1.getWorld(), sel.pos1, sel.pos2, name);
                try {
                    template.save(out);
                } catch (IOException e) {
                    player.sendMessage(Component.text("Failed to save: " + e.getMessage(), NamedTextColor.RED));
                    yield true;
                }

                int skullCount = template.skulls() != null ? template.skulls().size() : 0;
                int portalCount = template.portalMarkers() != null ? template.portalMarkers().size() : 0;
                player.sendMessage(Component.text("Saved template '" + name + "' size=" + template.sizeX() + "x"
                        + template.sizeY() + "x" + template.sizeZ()
                        + " blocks=" + template.blocks().size()
                        + " doors=" + template.doorMarkers().size()
                        + (skullCount > 0 ? " skulls=" + skullCount : "")
                        + (portalCount > 0 ? " portalMarkers=" + portalCount : ""), NamedTextColor.GREEN));
                yield true;
            }
            case "save_variant", "save_walls", "save_doors" -> {
                String subName = sub;
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /dungeon_template " + subName + " <name> [walls|doors]",
                            NamedTextColor.RED));
                    yield true;
                }
                if (sel.pos1 == null || sel.pos2 == null) {
                    player.sendMessage(Component.text("You must set pos1 and pos2 first.", NamedTextColor.RED));
                    yield true;
                }
                if (!sel.pos1.getWorld().equals(sel.pos2.getWorld())) {
                    player.sendMessage(Component.text("pos1 and pos2 must be in the same world.", NamedTextColor.RED));
                    yield true;
                }

                String name = sanitizeName(args[1]);
                if (name.isEmpty()) {
                    player.sendMessage(Component.text("Invalid name.", NamedTextColor.RED));
                    yield true;
                }

                String variant;
                if ("save_walls".equals(subName)) {
                    variant = "walls";
                } else if ("save_doors".equals(subName)) {
                    variant = "doors";
                } else {
                    if (args.length < 3) {
                        player.sendMessage(Component.text("Usage: /dungeon_template save_variant <name> <walls|doors>",
                                NamedTextColor.RED));
                        yield true;
                    }
                    variant = args[2].toLowerCase();
                }

                StructureTemplate template;
                String outName;
                if ("walls".equals(variant)) {
                    template = StructureTemplate.capture(sel.pos1.getWorld(), sel.pos1, sel.pos2, name);
                    outName = name + "_walls";
                } else if ("doors".equals(variant)) {
                    template = StructureTemplate.captureDoorVariant(sel.pos1.getWorld(), sel.pos1, sel.pos2);
                    outName = name + "_doors";
                } else {
                    player.sendMessage(Component.text("Variant must be walls or doors.", NamedTextColor.RED));
                    yield true;
                }

                File dir = new File(Test_dungeons.getInstance().getDataFolder(), "structures");
                if (!dir.exists() && !dir.mkdirs()) {
                    player.sendMessage(Component.text("Failed to create structures folder.", NamedTextColor.RED));
                    yield true;
                }

                File out = new File(dir, outName + ".yml");
                try {
                    template.save(out);
                } catch (IOException e) {
                    player.sendMessage(Component.text("Failed to save: " + e.getMessage(), NamedTextColor.RED));
                    yield true;
                }

                player.sendMessage(Component.text("Saved template '" + outName + "' size=" + template.sizeX() + "x"
                        + template.sizeY() + "x" + template.sizeZ()
                        + " blocks=" + template.blocks().size()
                        + " doors=" + template.doorMarkers().size(), NamedTextColor.GREEN));
                yield true;
            }
            case "secret_edit" -> {
                if (args.length < 2) {
                    player.sendMessage(
                            Component.text("Usage: /dungeon_template secret_edit <templateName>", NamedTextColor.RED));
                    yield true;
                }
                if (sel.pos1 == null || sel.pos2 == null) {
                    player.sendMessage(Component.text("You must set pos1 and pos2 first.", NamedTextColor.RED));
                    yield true;
                }
                if (!sel.pos1.getWorld().equals(sel.pos2.getWorld())) {
                    player.sendMessage(Component.text("pos1 and pos2 must be in the same world.", NamedTextColor.RED));
                    yield true;
                }
                String name = sanitizeName(args[1]);
                if (name.isEmpty()) {
                    player.sendMessage(Component.text("Invalid name.", NamedTextColor.RED));
                    yield true;
                }
                boolean ok = dungeonSecrets != null && dungeonSecrets.startEdit(player, name, sel.pos1, sel.pos2);
                player.sendMessage(Component.text(
                        ok ? "Secret edit mode enabled for '" + name + "'" : "Failed to start secret edit mode.",
                        ok ? NamedTextColor.GREEN : NamedTextColor.RED));
                yield true;
            }
            case "secret_save" -> {
                boolean ok = dungeonSecrets != null && dungeonSecrets.saveEdit(player);
                player.sendMessage(Component.text(ok ? "Secrets saved." : "Failed to save secrets (not in edit mode?).",
                        ok ? NamedTextColor.GREEN : NamedTextColor.RED));
                yield true;
            }
            case "redstone_stop" -> {
                boolean ok = dungeonSecrets != null && dungeonSecrets.stopRedstoneEdit(player);
                player.sendMessage(Component.text(ok ? "红石记录已停止。" : "当前没有正在记录的拉杆。", ok ? NamedTextColor.GRAY : NamedTextColor.RED));
                yield true;
            }
            case "redstone_save" -> {
                boolean ok = dungeonSecrets != null && dungeonSecrets.saveRedstoneEdit(player);
                player.sendMessage(Component.text(ok ? "红石系统已保存。" : "保存失败（不在编辑模式？）。", ok ? NamedTextColor.GREEN : NamedTextColor.RED));
                yield true;
            }
            case "puzzle08_circle" -> {
                if (sel.pos1 == null || sel.pos2 == null) {
                    player.sendMessage(Component.text("You must set pos1 and pos2 first.", NamedTextColor.RED));
                    yield true;
                }
                if (sel.pos1.getWorld() == null || !sel.pos1.getWorld().equals(sel.pos2.getWorld())) {
                    player.sendMessage(Component.text("pos1 and pos2 must be in the same world.", NamedTextColor.RED));
                    yield true;
                }
                World world = sel.pos1.getWorld();
                int minX = Math.min(sel.pos1.getBlockX(), sel.pos2.getBlockX());
                int maxX = Math.max(sel.pos1.getBlockX(), sel.pos2.getBlockX());
                int minY = Math.min(sel.pos1.getBlockY(), sel.pos2.getBlockY());
                int maxY = Math.max(sel.pos1.getBlockY(), sel.pos2.getBlockY());
                int minZ = Math.min(sel.pos1.getBlockZ(), sel.pos2.getBlockZ());
                int maxZ = Math.max(sel.pos1.getBlockZ(), sel.pos2.getBlockZ());

                int centerX = (minX + maxX) / 2;
                int centerZ = (minZ + maxZ) / 2;
                int radius = Math.max(1, Math.min(maxX - minX, maxZ - minZ) / 2);

                File dataDir = Test_dungeons.getInstance().getDataFolder();
                if (!dataDir.exists() && !dataDir.mkdirs()) {
                    player.sendMessage(Component.text("Failed to create plugin data folder.", NamedTextColor.RED));
                    yield true;
                }
                File file = new File(dataDir, "puzzle_room_08.yml");
                YamlConfiguration cfg = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
                String base = "worlds." + world.getName() + ".circle";
                cfg.set(base + ".centerX", centerX);
                cfg.set(base + ".centerZ", centerZ);
                cfg.set(base + ".radius", radius);
                cfg.set(base + ".minY", minY);
                cfg.set(base + ".maxY", maxY);
                try {
                    cfg.save(file);
                } catch (IOException e) {
                    player.sendMessage(Component.text("Failed to save: " + e.getMessage(), NamedTextColor.RED));
                    yield true;
                }
                player.sendMessage(Component.text("Puzzle08 circle saved: center=(" + centerX + "," + centerZ + ") radius=" + radius + " y=" + minY + "~" + maxY, NamedTextColor.GREEN));
                yield true;
            }
            case "puzzle08_progress" -> {
                if (sel.pos1 == null || sel.pos2 == null) {
                    player.sendMessage(Component.text("You must set pos1 and pos2 first.", NamedTextColor.RED));
                    yield true;
                }
                if (sel.pos1.getWorld() == null || !sel.pos1.getWorld().equals(sel.pos2.getWorld())) {
                    player.sendMessage(Component.text("pos1 and pos2 must be in the same world.", NamedTextColor.RED));
                    yield true;
                }
                World world = sel.pos1.getWorld();
                int minX = Math.min(sel.pos1.getBlockX(), sel.pos2.getBlockX());
                int maxX = Math.max(sel.pos1.getBlockX(), sel.pos2.getBlockX());
                int minY = Math.min(sel.pos1.getBlockY(), sel.pos2.getBlockY());
                int maxY = Math.max(sel.pos1.getBlockY(), sel.pos2.getBlockY());
                int minZ = Math.min(sel.pos1.getBlockZ(), sel.pos2.getBlockZ());
                int maxZ = Math.max(sel.pos1.getBlockZ(), sel.pos2.getBlockZ());

                int x = (minX + maxX) / 2;
                int z = (minZ + maxZ) / 2;
                int height = (maxY - minY) + 1;
                int segmentHeight = Math.max(1, height / 10);

                File dataDir = Test_dungeons.getInstance().getDataFolder();
                if (!dataDir.exists() && !dataDir.mkdirs()) {
                    player.sendMessage(Component.text("Failed to create plugin data folder.", NamedTextColor.RED));
                    yield true;
                }
                File file = new File(dataDir, "puzzle_room_08.yml");
                YamlConfiguration cfg = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
                String base = "worlds." + world.getName() + ".progress";
                cfg.set(base + ".x", x);
                cfg.set(base + ".z", z);
                cfg.set(base + ".topY", maxY);
                cfg.set(base + ".bottomY", minY);
                cfg.set(base + ".segmentHeight", segmentHeight);
                try {
                    cfg.save(file);
                } catch (IOException e) {
                    player.sendMessage(Component.text("Failed to save: " + e.getMessage(), NamedTextColor.RED));
                    yield true;
                }
                player.sendMessage(Component.text("Puzzle08 progress saved: xz=(" + x + "," + z + ") yTop=" + maxY + " yBottom=" + minY + " segment=" + segmentHeight, NamedTextColor.GREEN));
                yield true;
            }
            case "secret_exit" -> {
                if (dungeonSecrets != null)
                    dungeonSecrets.exitEdit(player);
                player.sendMessage(Component.text("Secret edit mode disabled.", NamedTextColor.GRAY));
                yield true;
            }
            case "monster_edit" -> {
                if (args.length < 2) {
                    player.sendMessage(
                            Component.text("Usage: /dungeon_template monster_edit <templateName>", NamedTextColor.RED));
                    yield true;
                }
                if (sel.pos1 == null || sel.pos2 == null) {
                    player.sendMessage(Component.text("You must set pos1 and pos2 first.", NamedTextColor.RED));
                    yield true;
                }
                if (!sel.pos1.getWorld().equals(sel.pos2.getWorld())) {
                    player.sendMessage(Component.text("pos1 and pos2 must be in the same world.", NamedTextColor.RED));
                    yield true;
                }
                String name = sanitizeName(args[1]);
                if (name.isEmpty()) {
                    player.sendMessage(Component.text("Invalid name.", NamedTextColor.RED));
                    yield true;
                }
                boolean ok = dungeonMonsters != null && dungeonMonsters.startEdit(player, name, sel.pos1, sel.pos2);
                player.sendMessage(Component.text(
                        ok ? "Monster edit mode enabled for '" + name + "'" : "Failed to start monster edit mode.",
                        ok ? NamedTextColor.GREEN : NamedTextColor.RED));
                yield true;
            }
            case "monster_save" -> {
                boolean ok = dungeonMonsters != null && dungeonMonsters.saveEdit(player);
                player.sendMessage(Component.text(ok ? "Monsters saved." : "Failed to save monsters (not in edit mode?).",
                        ok ? NamedTextColor.GREEN : NamedTextColor.RED));
                yield true;
            }
            case "monster_exit" -> {
                if (dungeonMonsters != null)
                    dungeonMonsters.exitEdit(player);
                player.sendMessage(Component.text("Monster edit mode disabled.", NamedTextColor.GRAY));
                yield true;
            }
            case "monster_reload" -> {
                if (dungeonMonsters != null) {
                    dungeonMonsters.reloadGlobalConfig();
                }
                player.sendMessage(Component.text("Monsters config reloaded.", NamedTextColor.GRAY));
                yield true;
            }
            default -> {
                player.sendMessage(Component.text(
                        "Usage: /dungeon_template <pos1|pos2|check|paste|save|save_variant|save_walls|save_doors|secret_edit|secret_save|secret_exit|monster_edit|monster_save|monster_exit|monster_reload|redstone_stop|redstone_save|tomb_pos1|tomb_pos2|tomb_add|give_tnt|ice05_level|ice05_board_pos1|ice05_board_pos2|ice05_start|ice05_end|ice05_save|blaze08_bow|blaze08_circle_pos1|blaze08_circle_pos2|blaze08_progress_pos1|blaze08_progress_pos2|blaze08_save|pusher_region_pos1|pusher_region_pos2|pusher_start|pusher_end|pusher_run|pusher_stop|pusher_save|pusher_clear|pusher_reload|pusher_link|pusher_unlink> [name] [rotation]",
                        NamedTextColor.RED));
                yield true;
            }
        };
    }

    private static boolean needsTemplateName(String sub) {
        if (sub == null || sub.isEmpty()) return false;
        return switch (sub) {
            case "paste",
                 "save", "save_variant", "save_walls", "save_doors",
                 "secret_edit", "secret_save", "secret_exit",
                 "monster_edit", "monster_save", "monster_exit", "monster_reload",
                 "redstone_stop", "redstone_save",
                 "pusher_save", "pusher_clear", "pusher_link", "pusher_unlink" -> true;
            default -> false;
        };
    }

    private static List<String> rotationCandidates() {
        return List.of("R0", "R90", "R180", "R270", "0", "90", "180", "270", "north", "east", "south", "west", "n", "e", "s", "w");
    }

    private static List<String> listTemplateBaseNames() {
        Test_dungeons plugin = Test_dungeons.getInstance();
        if (plugin == null) return List.of();
        File dir = new File(plugin.getDataFolder(), "structures");
        File[] files = dir.listFiles((d, n) -> n != null && n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) return List.of();

        Set<String> out = new HashSet<>();
        for (File f : files) {
            String n = f.getName();
            if (n == null) continue;
            String low = n.toLowerCase(Locale.ROOT);
            if (!low.endsWith(".yml")) continue;
            String base = n.substring(0, n.length() - 4);
            String baseLow = base.toLowerCase(Locale.ROOT);
            if (baseLow.endsWith("_walls")) base = base.substring(0, base.length() - "_walls".length());
            if (baseLow.endsWith("_doors")) base = base.substring(0, base.length() - "_doors".length());
            if (!base.isEmpty()) out.add(base);
        }
        return List.copyOf(out);
    }

    static void registerRoomPushers(Test_dungeons plugin, World world, Location origin, StructureTemplate.Rotation rotation,
            int sizeX0, int sizeZ0, String groupKey, String templateName) {
        if (plugin == null || world == null || origin == null || rotation == null) return;
        if (groupKey == null || groupKey.isBlank()) return;
        if (templateName == null || templateName.isBlank()) return;
        ensurePusherConfigsLoaded(plugin);
        String templateKey = templateName.toLowerCase(Locale.ROOT);
        List<PusherConfig> list = PUSHER_CONFIGS.get(templateKey);
        boolean log = shouldLogPusher(groupKey, templateName);
        if (list == null || list.isEmpty()) {
            if (log) plugin.getLogger().warning("pusher_register template=" + templateKey + " group=" + groupKey + " result=no_configs");
            return;
        }

        boolean isTemplatePasteGroup = groupKey.startsWith("tpl:");
        long now = System.currentTimeMillis();
        long activeUntilMs = isTemplatePasteGroup ? (now + 300_000L) : 0L;
        String mapKey = world.getName() + "|" + groupKey;

        int scanX = (rotation == StructureTemplate.Rotation.R90 || rotation == StructureTemplate.Rotation.R270) ? sizeZ0 : sizeX0;
        int scanZ = (rotation == StructureTemplate.Rotation.R90 || rotation == StructureTemplate.Rotation.R270) ? sizeX0 : sizeZ0;
        int minX = origin.getBlockX();
        int minY = origin.getBlockY();
        int minZ = origin.getBlockZ();
        int maxX = minX + scanX - 1;
        int maxZ = minZ + scanZ - 1;

        if (log) {
            plugin.getLogger().info("pusher_register template=" + templateKey
                    + " group=" + groupKey
                    + " world=" + world.getName()
                    + " origin=" + minX + "," + minY + "," + minZ
                    + " rot=" + rotation.name()
                    + " size0=" + sizeX0 + "x" + sizeZ0
                    + " configs=" + list.size()
                    + " activeMs=" + (activeUntilMs > 0L ? (activeUntilMs - now) : 0L));
        }

        RoomPusherRuntime rt = new RoomPusherRuntime(world.getName(), mapKey, templateKey,
                minX, minY, minZ, maxX, maxZ, rotation, sizeX0, sizeZ0, new ArrayList<>(), activeUntilMs);

        for (PusherConfig pc : list) {
            if (pc == null) continue;
            int[] region = rotateRegionR0ToWorld(pc, rotation, sizeX0, sizeZ0, origin);
            if (region == null) {
                if (log) plugin.getLogger().warning("pusher_skip template=" + templateKey + " group=" + groupKey + " id=" + pc.id + " reason=region_null");
                continue;
            }
            Location start = rotatePointR0ToWorld(pc.startX, pc.startY, pc.startZ, rotation, sizeX0, sizeZ0, origin);
            Location end = rotatePointR0ToWorld(pc.endX, pc.endY, pc.endZ, rotation, sizeX0, sizeZ0, origin);
            if (start == null || end == null) {
                if (log) plugin.getLogger().warning("pusher_skip template=" + templateKey + " group=" + groupKey + " id=" + pc.id + " reason=start_or_end_null");
                continue;
            }

            int sx = start.getBlockX();
            int sy = start.getBlockY();
            int sz = start.getBlockZ();

            int dx = end.getBlockX() - sx;
            int dy = end.getBlockY() - sy;
            int dz = end.getBlockZ() - sz;
            if (dx == 0 && dy == 0 && dz == 0) {
                if (log) plugin.getLogger().warning("pusher_skip template=" + templateKey + " group=" + groupKey + " id=" + pc.id + " reason=zero_vector");
                continue;
            }

            int adx = Math.abs(dx);
            int ady = Math.abs(dy);
            int adz = Math.abs(dz);
            int stepX = 0, stepY = 0, stepZ = 0, length = 0;
            if (adx >= ady && adx >= adz) {
                stepX = dx > 0 ? 1 : -1;
                length = adx;
            } else if (adz >= ady) {
                stepZ = dz > 0 ? 1 : -1;
                length = adz;
            } else {
                stepY = dy > 0 ? 1 : -1;
                length = ady;
            }

            int rMinX = region[0];
            int rMinY = region[1];
            int rMinZ = region[2];
            int rMaxX = region[3];
            int rMaxY = region[4];
            int rMaxZ = region[5];

            if (sx < rMinX || sx > rMaxX || sy < rMinY || sy > rMaxY || sz < rMinZ || sz > rMaxZ) {
                if (log) {
                    plugin.getLogger().warning("pusher_start_outside template=" + templateKey
                            + " group=" + groupKey
                            + " id=" + pc.id
                            + " start=" + sx + "," + sy + "," + sz
                            + " region=[" + rMinX + "," + rMinY + "," + rMinZ + " -> " + rMaxX + "," + rMaxY + "," + rMaxZ + "]");
                }
                continue;
            }

            String[] fail = log ? new String[1] : null;
            PusherRuntime prt = buildPusherRuntime(world, rMinX, rMinY, rMinZ, rMaxX, rMaxY, rMaxZ, stepX, stepY, stepZ, length, fail);
            if (prt == null && log && fail != null && fail[0] != null
                    && (fail[0].startsWith("region_not_solid") || fail[0].startsWith("empty_region"))) {
                int total = (rMaxX - rMinX + 1) * (rMaxY - rMinY + 1) * (rMaxZ - rMinZ + 1);
                int baseY = origin.getBlockY();
                int preferMinY = baseY - 8;
                int preferMaxY = baseY + 80;
                int bestDy = bestRegionYShiftForSolids(world, rMinX, rMinY, rMinZ, rMaxX, rMaxY, rMaxZ, total, 64, preferMinY, preferMaxY);
                if (bestDy != 0) {
                    int shiftedMinY = rMinY + bestDy;
                    int shiftedMaxY = rMaxY + bestDy;
                    if (sy + bestDy >= shiftedMinY && sy + bestDy <= shiftedMaxY) {
                        String[] fail2 = new String[1];
                        PusherRuntime shifted = buildPusherRuntime(world, rMinX, shiftedMinY, rMinZ, rMaxX, shiftedMaxY, rMaxZ, stepX, stepY, stepZ, length, fail2);
                        if (shifted != null) {
                            plugin.getLogger().warning("pusher_autofix_y template=" + templateKey
                                    + " group=" + groupKey
                                    + " id=" + pc.id
                                    + " dy=" + bestDy
                                    + " regionY=[" + rMinY + ".." + rMaxY + " -> " + shiftedMinY + ".." + shiftedMaxY + "]"
                                    + " preferY=[" + preferMinY + ".." + preferMaxY + "]");
                            prt = shifted;
                        } else if (fail2[0] != null) {
                            plugin.getLogger().warning("pusher_autofix_y_failed template=" + templateKey
                                    + " group=" + groupKey
                                    + " id=" + pc.id
                                    + " dy=" + bestDy
                                    + " detail=" + fail2[0]);
                        }
                    }
                }
            }
            if (prt == null) {
                if (log) {
                    plugin.getLogger().warning("pusher_skip template=" + templateKey
                            + " group=" + groupKey
                            + " id=" + pc.id
                            + " reason=runtime_null"
                            + (fail != null && fail[0] != null ? (" detail=" + fail[0]) : "")
                            + " region=[" + rMinX + "," + rMinY + "," + rMinZ + " -> " + rMaxX + "," + rMaxY + "," + rMaxZ + "]"
                            + " step=" + stepX + "," + stepY + "," + stepZ
                            + " length=" + length);
                }
                continue;
            }

            rt.pushers.add(new RoomPusher(pc, prt));
        }

        if (rt.pushers.isEmpty()) {
            if (log) plugin.getLogger().warning("pusher_register template=" + templateKey + " group=" + groupKey + " result=no_valid_pushers");
            return;
        }

        RoomPusherRuntime prev = ACTIVE_PUSHER_ROOMS.get(mapKey);
        if (prev != null) {
            World prevWorld = Bukkit.getWorld(prev.worldName);
            if (prevWorld != null) {
                stopRoomPushers(prevWorld, prev);
                prev.running = false;
            }
        }

        ACTIVE_PUSHER_ROOMS.put(mapKey, rt);
        if (log) plugin.getLogger().info("pusher_register template=" + templateKey + " group=" + groupKey + " result=ok pushers=" + rt.pushers.size());
        ensurePusherTickTask(plugin);
    }

    private static void ensurePusherTickTask(Test_dungeons plugin) {
        if (plugin == null) return;
        if (PUSHER_TICK_TASK != null) return;
        PUSHER_TICK_TASK = new BukkitRunnable() {
            @Override
            public void run() {
                if (ACTIVE_PUSHER_ROOMS.isEmpty()) return;
                List<String> toRemove = null;
                for (RoomPusherRuntime rr : ACTIVE_PUSHER_ROOMS.values()) {
                    if (rr == null) continue;
                    World w = Bukkit.getWorld(rr.worldName);
                    if (w == null) {
                        if (toRemove == null) toRemove = new ArrayList<>();
                        toRemove.add(rr.groupKey);
                        continue;
                    }
                    boolean shouldRun = false;
                    for (Player p : w.getPlayers()) {
                        if (p == null || !p.isOnline()) continue;
                        Location loc = p.getLocation();
                        if (loc == null) continue;
                        if (insideRoom(rr, loc)) {
                            shouldRun = true;
                            break;
                        }
                    }
                    if (!shouldRun && rr.activeUntilMs > 0L && System.currentTimeMillis() < rr.activeUntilMs) {
                        shouldRun = true;
                    }

                    if (!shouldRun) {
                        if (rr.running) {
                            rr.running = false;
                        }
                        continue;
                    }

                    if (!rr.running) {
                        if (!rr.startedOnce) {
                            startRoomPushers(w, rr);
                            rr.startedOnce = true;
                        }
                        rr.running = true;
                        if (plugin != null && shouldLogPusher(rr.groupKey, rr.templateName)) {
                            plugin.getLogger().info("pusher_room_start template=" + rr.templateName + " group=" + rr.groupKey + " world=" + rr.worldName + " pushers=" + (rr.pushers != null ? rr.pushers.size() : 0));
                        }
                    }

                    boolean ok = true;
                    for (RoomPusher rp : rr.pushers) {
                        if (rp == null || rp.runtime == null) continue;
                        if (!tickPusher(w, rp.runtime)) {
                            if (plugin != null && shouldLogPusher(rr.groupKey, rr.templateName)) {
                                plugin.getLogger().warning("pusher_tick_stop template=" + rr.templateName
                                        + " group=" + rr.groupKey
                                        + " id=" + (rp.config != null ? rp.config.id : "null")
                                        + " fail=" + (rp.runtime.lastFail != null ? rp.runtime.lastFail : "unknown"));
                            }
                            ok = false;
                            break;
                        }
                        if (plugin != null && shouldLogPusher(rr.groupKey, rr.templateName) && !rp.runtime.firstMoveLogged && rp.runtime.offset != 0) {
                            rp.runtime.firstMoveLogged = true;
                            plugin.getLogger().info("pusher_first_move template=" + rr.templateName
                                    + " group=" + rr.groupKey
                                    + " id=" + (rp.config != null ? rp.config.id : "null")
                                    + " offset=" + rp.runtime.offset + "/" + rp.runtime.length
                                    + " step=" + rp.runtime.stepX + "," + rp.runtime.stepY + "," + rp.runtime.stepZ);
                        }
                    }
                    if (!ok) {
                        stopRoomPushers(w, rr);
                        rr.running = false;
                        if (plugin != null && shouldLogPusher(rr.groupKey, rr.templateName)) {
                            plugin.getLogger().warning("pusher_room_stop template=" + rr.templateName + " group=" + rr.groupKey + " world=" + rr.worldName);
                        }
                    }
                }
                if (toRemove != null) {
                    for (String k : toRemove) ACTIVE_PUSHER_ROOMS.remove(k);
                }
            }
        };
        PUSHER_TICK_TASK.runTaskTimer(plugin, 10L, PUSHER_TICK_PERIOD_TICKS);
    }

    private static boolean insideRoom(RoomPusherRuntime rr, Location loc) {
        if (rr == null || loc == null) return false;
        World w = loc.getWorld();
        if (w == null || rr.worldName == null || !rr.worldName.equals(w.getName())) return false;
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= rr.minX && x <= rr.maxX && z >= rr.minZ && z <= rr.maxZ && y >= rr.baseY - 5 && y <= rr.baseY + 80;
    }

    private static void startRoomPushers(World world, RoomPusherRuntime rr) {
        if (world == null || rr == null || rr.pushers == null) return;
        for (RoomPusher rp : rr.pushers) {
            if (rp == null || rp.runtime == null) continue;
            resetPusherToStart(world, rp.runtime);
        }
        applyLinkPhases(world, rr);
    }

    private static void applyLinkPhases(World world, RoomPusherRuntime rr) {
        if (world == null || rr == null || rr.pushers == null) return;
        Map<String, List<RoomPusher>> byLink = new HashMap<>();
        for (RoomPusher rp : rr.pushers) {
            if (rp == null || rp.config == null) continue;
            String lk = rp.config.linkKey;
            if (lk == null || lk.isBlank()) continue;
            byLink.computeIfAbsent(lk, k -> new ArrayList<>()).add(rp);
        }
        for (List<RoomPusher> g : byLink.values()) {
            if (g == null || g.size() != 2) continue;
            RoomPusher a = g.get(0);
            RoomPusher b = g.get(1);
            if (a == null || b == null || a.runtime == null || b.runtime == null) continue;
            if (a.config != null && a.config.linkPhase == 1) {
                RoomPusher t = a;
                a = b;
                b = t;
            }
            if (b.config == null || b.config.linkPhase != 1) continue;
            movePusherToOffset(world, b.runtime, b.runtime.length);
            b.runtime.forward = false;
        }
    }

    private static void stopRoomPushers(World world, RoomPusherRuntime rr) {
        if (world == null || rr == null || rr.pushers == null) return;
        for (RoomPusher rp : rr.pushers) {
            if (rp == null || rp.runtime == null) continue;
            resetPusherToStart(world, rp.runtime);
        }
    }

    private static void resetPusherToStart(World world, PusherRuntime rt) {
        if (world == null || rt == null) return;
        if (rt.offset != 0) movePusherToOffset(world, rt, 0);
        restoreOverwrittenBlocks(world, rt);
        rt.forward = true;
        rt.offset = 0;
        rt.lastFail = null;
        rt.firstMoveLogged = false;
    }

    private static void movePusherToOffset(World world, PusherRuntime rt, int targetOffset) {
        if (world == null || rt == null) return;
        if (targetOffset < 0) targetOffset = 0;
        if (targetOffset > rt.length) targetOffset = rt.length;
        if (targetOffset == rt.offset) return;
        if (targetOffset > rt.offset) {
            for (int o = rt.offset + 1; o <= targetOffset; o++) {
                placeLayer(world, rt, o);
            }
        } else {
            for (int o = rt.offset; o > targetOffset; o--) {
                removeLayer(world, rt, o);
            }
        }
        rt.offset = targetOffset;
    }

    private static void ensurePusherConfigsLoaded(Test_dungeons plugin) {
        if (plugin == null) return;
        long now = System.currentTimeMillis();
        if (!PUSHER_CONFIGS.isEmpty() && (now - PUSHER_CONFIG_LOADED_AT_MS) < 30_000L) return;
        reloadPusherConfigs(plugin);
    }

    private static void reloadPusherConfigs(Test_dungeons plugin) {
        if (plugin == null) return;
        PUSHER_CONFIGS.clear();
        PUSHER_CONFIG_LOADED_AT_MS = System.currentTimeMillis();

        File dataDir = plugin.getDataFolder();
        if (!dataDir.exists() && !dataDir.mkdirs()) return;
        File file = new File(dataDir, PUSHER_CONFIG_FILE);
        if (!file.exists()) {
            plugin.saveResource(PUSHER_CONFIG_FILE, false);
        }
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection templates = cfg.getConfigurationSection("templates");
        if (templates == null) return;

        for (String templateKey : templates.getKeys(false)) {
            if (templateKey == null) continue;
            ConfigurationSection tSec = templates.getConfigurationSection(templateKey);
            if (tSec == null) continue;
            ConfigurationSection pushers = tSec.getConfigurationSection("pushers");
            if (pushers == null) continue;

            List<PusherConfig> list = new ArrayList<>();
            for (String id : pushers.getKeys(false)) {
                if (id == null) continue;
                ConfigurationSection pSec = pushers.getConfigurationSection(id);
                if (pSec == null) continue;

                int rMinX = pSec.getInt("region.min.x");
                int rMinY = pSec.getInt("region.min.y");
                int rMinZ = pSec.getInt("region.min.z");
                int rMaxX = pSec.getInt("region.max.x");
                int rMaxY = pSec.getInt("region.max.y");
                int rMaxZ = pSec.getInt("region.max.z");

                int sX = pSec.getInt("start.x");
                int sY = pSec.getInt("start.y");
                int sZ = pSec.getInt("start.z");

                int eX = pSec.getInt("end.x");
                int eY = pSec.getInt("end.y");
                int eZ = pSec.getInt("end.z");

                String linkKey = pSec.getString("link.key");
                int linkPhase = pSec.getInt("link.phase", 0);

                list.add(new PusherConfig(id, rMinX, rMinY, rMinZ, rMaxX, rMaxY, rMaxZ, sX, sY, sZ, eX, eY, eZ, linkKey, linkPhase));
            }

            if (!list.isEmpty()) {
                PUSHER_CONFIGS.put(templateKey.toLowerCase(Locale.ROOT), list);
            }
        }
    }

    private static void savePusherConfigs(Test_dungeons plugin) {
        if (plugin == null) return;
        File dataDir = plugin.getDataFolder();
        if (!dataDir.exists() && !dataDir.mkdirs()) return;
        File file = new File(dataDir, PUSHER_CONFIG_FILE);
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("version", 1);
        for (Map.Entry<String, List<PusherConfig>> e : PUSHER_CONFIGS.entrySet()) {
            String template = e.getKey();
            if (template == null) continue;
            List<PusherConfig> list = e.getValue();
            if (list == null || list.isEmpty()) continue;
            for (PusherConfig pc : list) {
                if (pc == null || pc.id == null || pc.id.isBlank()) continue;
                String base = "templates." + template + ".pushers." + pc.id + ".";
                cfg.set(base + "region.min.x", pc.regionMinX);
                cfg.set(base + "region.min.y", pc.regionMinY);
                cfg.set(base + "region.min.z", pc.regionMinZ);
                cfg.set(base + "region.max.x", pc.regionMaxX);
                cfg.set(base + "region.max.y", pc.regionMaxY);
                cfg.set(base + "region.max.z", pc.regionMaxZ);
                cfg.set(base + "start.x", pc.startX);
                cfg.set(base + "start.y", pc.startY);
                cfg.set(base + "start.z", pc.startZ);
                cfg.set(base + "end.x", pc.endX);
                cfg.set(base + "end.y", pc.endY);
                cfg.set(base + "end.z", pc.endZ);
                if (pc.linkKey != null && !pc.linkKey.isBlank()) {
                    cfg.set(base + "link.key", pc.linkKey);
                    cfg.set(base + "link.phase", pc.linkPhase);
                }
            }
        }
        try {
            cfg.save(file);
        } catch (IOException ignored) {
        }
        PUSHER_CONFIG_LOADED_AT_MS = System.currentTimeMillis();
    }

    private static List<String> listPusherIds(String templateNameRaw) {
        Test_dungeons plugin = Test_dungeons.getInstance();
        if (plugin == null) return List.of();
        ensurePusherConfigsLoaded(plugin);
        String template = sanitizeName(templateNameRaw);
        if (template.isEmpty()) return List.of();
        List<PusherConfig> list = PUSHER_CONFIGS.get(template);
        if (list == null || list.isEmpty()) return List.of();
        List<String> ids = new ArrayList<>();
        for (PusherConfig pc : list) {
            if (pc == null || pc.id == null || pc.id.isBlank()) continue;
            ids.add(pc.id);
        }
        return ids;
    }

    private static PusherConfig findPusherById(List<PusherConfig> list, String id) {
        if (list == null || list.isEmpty() || id == null || id.isBlank()) return null;
        for (PusherConfig p : list) {
            if (p == null || p.id == null) continue;
            if (id.equalsIgnoreCase(p.id)) return p;
        }
        return null;
    }

    private static int[] loadTemplateSizeXZ0(Test_dungeons plugin, String templateName) {
        if (plugin == null || templateName == null || templateName.isBlank()) return null;
        File dir = new File(plugin.getDataFolder(), "structures");
        File walls = new File(dir, templateName + "_walls.yml");
        File single = new File(dir, templateName + ".yml");
        File doors = new File(dir, templateName + "_doors.yml");
        File file = walls.exists() ? walls : (single.exists() ? single : (doors.exists() ? doors : null));
        if (file == null) return null;
        try {
            StructureTemplate t = StructureTemplate.load(file);
            return new int[] { t.sizeX(), t.sizeZ() };
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int[] rotateRegionR0ToWorld(PusherConfig pc, StructureTemplate.Rotation rotation, int sizeX0, int sizeZ0, Location origin) {
        if (pc == null || origin == null || rotation == null) return null;
        int minX0 = Math.min(pc.regionMinX, pc.regionMaxX);
        int maxX0 = Math.max(pc.regionMinX, pc.regionMaxX);
        int minZ0 = Math.min(pc.regionMinZ, pc.regionMaxZ);
        int maxZ0 = Math.max(pc.regionMinZ, pc.regionMaxZ);
        int yMin = Math.min(pc.regionMinY, pc.regionMaxY);
        int yMax = Math.max(pc.regionMinY, pc.regionMaxY);

        int[] c1 = rotateFromR0XZ(minX0, minZ0, rotation, sizeX0, sizeZ0);
        int[] c2 = rotateFromR0XZ(minX0, maxZ0, rotation, sizeX0, sizeZ0);
        int[] c3 = rotateFromR0XZ(maxX0, minZ0, rotation, sizeX0, sizeZ0);
        int[] c4 = rotateFromR0XZ(maxX0, maxZ0, rotation, sizeX0, sizeZ0);

        int minXR = Math.min(Math.min(c1[0], c2[0]), Math.min(c3[0], c4[0]));
        int maxXR = Math.max(Math.max(c1[0], c2[0]), Math.max(c3[0], c4[0]));
        int minZR = Math.min(Math.min(c1[1], c2[1]), Math.min(c3[1], c4[1]));
        int maxZR = Math.max(Math.max(c1[1], c2[1]), Math.max(c3[1], c4[1]));

        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();
        return new int[] { ox + minXR, oy + yMin, oz + minZR, ox + maxXR, oy + yMax, oz + maxZR };
    }

    private static Location rotatePointR0ToWorld(int x0, int y0, int z0, StructureTemplate.Rotation rotation, int sizeX0, int sizeZ0, Location origin) {
        if (origin == null) return null;
        int[] xz = rotateFromR0XZ(x0, z0, rotation, sizeX0, sizeZ0);
        World w = origin.getWorld();
        if (w == null) return null;
        return new Location(w, origin.getBlockX() + xz[0], origin.getBlockY() + y0, origin.getBlockZ() + xz[1]);
    }

    private static int[] rotateFromR0XZ(int x0, int z0, StructureTemplate.Rotation rotation, int sizeX0, int sizeZ0) {
        return switch (rotation) {
            case R0 -> new int[] { x0, z0 };
            case R90 -> new int[] { z0, (sizeX0 - 1) - x0 };
            case R180 -> new int[] { (sizeX0 - 1) - x0, (sizeZ0 - 1) - z0 };
            case R270 -> new int[] { (sizeZ0 - 1) - z0, x0 };
        };
    }

    private static List<String> filterPrefix(List<String> candidates, String rawPrefix) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        if (rawPrefix == null || rawPrefix.isEmpty()) return candidates;
        String prefix = rawPrefix.toLowerCase(Locale.ROOT);
        List<String> out = null;
        for (String c : candidates) {
            if (c == null) continue;
            if (!c.toLowerCase(Locale.ROOT).startsWith(prefix)) continue;
            if (out == null) out = new ArrayList<>();
            out.add(c);
        }
        return out != null ? out : List.of();
    }

    private static void processRoomBlocks(Location base, StructureTemplate.Rotation rotation, int sizeX, int sizeY, int sizeZ, String templateName) {
        if (base == null) return;
        World world = base.getWorld();
        if (world == null) return;
        if (rotation == null) rotation = StructureTemplate.Rotation.R0;
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) return;

        int minX = base.getBlockX();
        int minY = base.getBlockY();
        int minZ = base.getBlockZ();
        int maxX = minX + sizeX - 1;
        int maxY = minY + sizeY - 1;
        int maxZ = minZ + sizeZ - 1;

        Set<Long> ladders = new HashSet<>();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (type == Material.DISPENSER || type == Material.DROPPER) {
                        adjustDispenserFacingAir(block, minX, maxX, minY, maxY, minZ, maxZ);
                        Test_dungeons plugin = Test_dungeons.getInstance();
                        if (plugin != null) {
                            int lx = x - minX;
                            int ly = y - minY;
                            int lz = z - minZ;
                            int[] unrot = inverseRotateXZ(lx, lz, rotation, sizeX, sizeZ);
                            plugin.registerLavaTrapEmitter(block, minX, maxX, minY, maxY, minZ, maxZ, templateName, unrot[0], ly, unrot[1]);
                        }
                        continue;
                    }
                    if (type == Material.LADDER) {
                        ladders.add(packBlock(x, y, z));
                    }
                }
            }
        }

        if (!ladders.isEmpty()) {
            adjustLadderClusters(world, ladders, minX, maxX, minY, maxY, minZ, maxZ);
        }
    }

    private static void adjustDispenserFacingAir(Block block, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        if (block == null) return;
        BlockData data = block.getBlockData();
        if (!(data instanceof Directional directional)) return;

        BlockFace current = directional.getFacing();
        if (current != null && current != BlockFace.SELF) {
            Block rel = block.getRelative(current);
            if (isPassableBlock(rel)) {
                int rx = rel.getX();
                int ry = rel.getY();
                int rz = rel.getZ();
                if (rx >= minX && rx <= maxX && ry >= minY && ry <= maxY && rz >= minZ && rz <= maxZ) return;
            }
        }

        BlockFace chosen = null;
        BlockFace fallback = null;
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN}) {
            Block rel = block.getRelative(f);
            if (!isPassableBlock(rel)) continue;
            if (fallback == null) fallback = f;
            int rx = rel.getX();
            int ry = rel.getY();
            int rz = rel.getZ();
            if (rx >= minX && rx <= maxX && ry >= minY && ry <= maxY && rz >= minZ && rz <= maxZ) {
                chosen = f;
                break;
            }
        }
        if (chosen == null) chosen = fallback;
        if (chosen == null || chosen == BlockFace.SELF) return;

        if (current != chosen) {
            directional.setFacing(chosen);
            block.setBlockData(directional, false);
        }
    }

    private static void adjustLadderClusters(World world, Set<Long> ladders, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        if (world == null || ladders == null || ladders.isEmpty()) return;

        ArrayDeque<Long> queue = new ArrayDeque<>();
        Set<Long> cluster = new HashSet<>();

        while (!ladders.isEmpty()) {
            long seed = ladders.iterator().next();
            ladders.remove(seed);
            queue.add(seed);
            cluster.add(seed);

            while (!queue.isEmpty()) {
                long k = queue.pollFirst();
                int x = unpackX(k);
                int y = unpackY(k);
                int z = unpackZ(k);

                for (int i = 0; i < 6; i++) {
                    int nx = x;
                    int ny = y;
                    int nz = z;
                    if (i == 0) ny++;
                    else if (i == 1) ny--;
                    else if (i == 2) nz--;
                    else if (i == 3) nz++;
                    else if (i == 4) nx--;
                    else nx++;

                    if (nx < minX || nx > maxX || ny < minY || ny > maxY || nz < minZ || nz > maxZ) continue;
                    long nk = packBlock(nx, ny, nz);
                    if (!ladders.remove(nk)) continue;
                    queue.add(nk);
                    cluster.add(nk);
                }
            }

            BlockFace facing = chooseLadderFacing(world, cluster);
            if (facing != null && facing != BlockFace.SELF) {
                for (long k : cluster) {
                    int x = unpackX(k);
                    int y = unpackY(k);
                    int z = unpackZ(k);
                    Block b = world.getBlockAt(x, y, z);
                    if (b.getType() != Material.LADDER) continue;
                    BlockData data = b.getBlockData();
                    if (data instanceof Ladder ladder) {
                        if (ladder.getFacing() == facing) continue;
                        ladder.setFacing(facing);
                        b.setBlockData(ladder, false);
                        continue;
                    }
                    if (data instanceof Directional directional) {
                        if (directional.getFacing() == facing) continue;
                        directional.setFacing(facing);
                        b.setBlockData(directional, false);
                    }
                }
            }

            cluster.clear();
        }
    }

    private static BlockFace chooseLadderFacing(World world, Set<Long> cluster) {
        if (world == null || cluster == null || cluster.isEmpty()) return null;

        int topY = Integer.MIN_VALUE;
        int bottomY = Integer.MAX_VALUE;
        for (long k : cluster) {
            int y = unpackY(k);
            if (y > topY) topY = y;
            if (y < bottomY) bottomY = y;
        }

        BlockFace[] faces = new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

        for (int y = topY; y >= bottomY; y--) {
            for (long k : cluster) {
                if (unpackY(k) != y) continue;
                int x = unpackX(k);
                int z = unpackZ(k);
                Block ladder = world.getBlockAt(x, y, z);
                for (BlockFace attach : faces) {
                    Block support = ladder.getRelative(attach);
                    if (isFullSupportBlock(support)) return attach.getOppositeFace();
                }
            }
        }

        for (int y = topY; y >= bottomY; y--) {
            for (long k : cluster) {
                if (unpackY(k) != y) continue;
                int x = unpackX(k);
                int z = unpackZ(k);
                Block ladder = world.getBlockAt(x, y, z);
                for (BlockFace attach : faces) {
                    Block support = ladder.getRelative(attach);
                    if (isSlabSupportBlock(support)) return attach.getOppositeFace();
                }
            }
        }

        return null;
    }

    private static boolean isFullSupportBlock(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        if (type.isAir() || block.isPassable()) return false;
        if (!type.isSolid()) return false;
        BlockData data = block.getBlockData();
        if (data instanceof Slab slab) {
            return slab.getType() == Slab.Type.DOUBLE;
        }
        return true;
    }

    private static boolean isSlabSupportBlock(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        if (type.isAir() || block.isPassable()) return false;
        BlockData data = block.getBlockData();
        if (!(data instanceof Slab slab)) return false;
        return slab.getType() != Slab.Type.DOUBLE;
    }

    private static boolean isPassableBlock(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        if (type.isAir()) return true;
        return block.isPassable();
    }

    private static long packBlock(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (long) (y & 0xFFF);
    }

    private static int unpackX(long packed) {
        int x = (int) (packed >> 38);
        if (x >= 0x2000000) x -= 0x4000000;
        return x;
    }

    private static int unpackY(long packed) {
        return (int) (packed & 0xFFF);
    }

    private static int unpackZ(long packed) {
        int z = (int) ((packed >> 12) & 0x3FFFFFF);
        if (z >= 0x2000000) z -= 0x4000000;
        return z;
    }

    private static void stopPusher(Selection sel) {
        if (sel == null) return;
        if (sel.pusherTask != null) {
            sel.pusherTask.cancel();
            sel.pusherTask = null;
        }
        if (sel.pusherRuntime != null) {
            World w = Bukkit.getWorld(sel.pusherRuntime.worldName);
            if (w != null) {
                movePusherToOffset(w, sel.pusherRuntime, 0);
                restoreOverwrittenBlocks(w, sel.pusherRuntime);
            }
        }
        sel.pusherRuntime = null;
    }

    private static PusherRuntime buildPusherRuntime(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
            int stepX, int stepY, int stepZ, int length) {
        return buildPusherRuntime(world, minX, minY, minZ, maxX, maxY, maxZ, stepX, stepY, stepZ, length, null);
    }

    private static PusherRuntime buildPusherRuntime(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
            int stepX, int stepY, int stepZ, int length, String[] failOut) {
        if (world == null) {
            if (failOut != null) failOut[0] = "world_null";
            return null;
        }
        if (length <= 0) {
            if (failOut != null) failOut[0] = "length_invalid " + length;
            return null;
        }
        if ((stepX == 0 ? 0 : 1) + (stepY == 0 ? 0 : 1) + (stepZ == 0 ? 0 : 1) != 1) {
            if (failOut != null) failOut[0] = "step_invalid " + stepX + "," + stepY + "," + stepZ;
            return null;
        }

        int sizeX = (maxX - minX) + 1;
        int sizeY = (maxY - minY) + 1;
        int sizeZ = (maxZ - minZ) + 1;

        int total = sizeX * sizeY * sizeZ;
        Material uniformMat = null;
        boolean uniform = true;
        Map<Long, BlockData> baseBlocks = new HashMap<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block b = world.getBlockAt(x, y, z);
                    Material t = b.getType();
                    if (t.isAir() || b.isPassable()) {
                        if (failOut != null) {
                            failOut[0] = "region_not_solid at=" + x + "," + y + "," + z
                                    + " type=" + t.name()
                                    + " passable=" + b.isPassable()
                                    + " region=[" + minX + "," + minY + "," + minZ + " -> " + maxX + "," + maxY + "," + maxZ + "]";
                        }
                        return null;
                    }
                    if (uniformMat == null) uniformMat = t;
                    else if (t != uniformMat) uniform = false;
                    baseBlocks.put(packBlock(x, y, z), b.getBlockData().clone());
                }
            }
        }
        if (!uniform) uniformMat = null;
        if (baseBlocks.size() != total || uniformMat == null) {
            if (baseBlocks.size() != total) {
                if (failOut != null) failOut[0] = "region_invalid total=" + total + " captured=" + baseBlocks.size();
                return null;
            }
        }

        int oldMinX = minX;
        int oldMinY = minY;
        int oldMinZ = minZ;
        int oldMaxX = maxX;
        int oldMaxY = maxY;
        int oldMaxZ = maxZ;

        int backX = -stepX;
        int backY = -stepY;
        int backZ = -stepZ;
        if (uniformMat != null) {
            if (sizeX == 1 && stepX != 0) {
                int expanded = tryExpandFull(world, uniformMat, minX, minY, minZ, maxX, maxY, maxZ, backX, 0, 0);
                if (expanded > 0) {
                    if (backX < 0) minX -= expanded;
                    else maxX += expanded;
                }
            } else if (sizeZ == 1 && stepZ != 0) {
                int expanded = tryExpandFull(world, uniformMat, minX, minY, minZ, maxX, maxY, maxZ, 0, 0, backZ);
                if (expanded > 0) {
                    if (backZ < 0) minZ -= expanded;
                    else maxZ += expanded;
                }
            } else if (sizeY == 1 && stepY != 0) {
                int expanded = tryExpandFull(world, uniformMat, minX, minY, minZ, maxX, maxY, maxZ, 0, backY, 0);
                if (expanded > 0) {
                    if (backY < 0) minY -= expanded;
                    else maxY += expanded;
                }
            }
            if (minX != oldMinX || minY != oldMinY || minZ != oldMinZ || maxX != oldMaxX || maxY != oldMaxY || maxZ != oldMaxZ) {
                baseBlocks.clear();
                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            Block b = world.getBlockAt(x, y, z);
                            if (b.getType() != uniformMat || b.isPassable()) {
                                if (failOut != null) {
                                    failOut[0] = "expand_mismatch expected=" + uniformMat.name()
                                            + " got=" + b.getType().name()
                                            + " passable=" + b.isPassable()
                                            + " at=" + x + "," + y + "," + z;
                                }
                                return null;
                            }
                            baseBlocks.put(packBlock(x, y, z), b.getBlockData().clone());
                        }
                    }
                }
                if (baseBlocks.isEmpty()) return null;
            }
        }

        String worldName = world.getName();
        Map<Long, BlockData> blocks = new HashMap<>(baseBlocks);
        Map<Long, Integer> coverage = new HashMap<>(baseBlocks.size() * 2);
        for (long k : baseBlocks.keySet()) {
            coverage.put(k, 1);
        }
        return new PusherRuntime(worldName, minX, minY, minZ, maxX, maxY, maxZ, stepX, stepY, stepZ, length, baseBlocks, blocks, coverage, new HashMap<>());
    }

    private static boolean tickPusher(World world, PusherRuntime rt) {
        if (world == null || rt == null) return false;
        if (!world.getName().equals(rt.worldName)) return false;
        rt.lastFail = null;

        int nextOffset = rt.forward ? rt.offset + 1 : rt.offset - 1;
        if (nextOffset > rt.length) {
            rt.forward = false;
            nextOffset = rt.offset - 1;
        } else if (nextOffset < 0) {
            rt.forward = true;
            nextOffset = rt.offset + 1;
        }
        if (nextOffset < 0 || nextOffset > rt.length) return false;

        if (nextOffset == rt.offset) return true;
        if (nextOffset > rt.offset) {
            if (!placeLayer(world, rt, nextOffset)) {
                if (rt.lastFail == null) rt.lastFail = "placeLayer_failed offset=" + nextOffset;
                return false;
            }
        } else {
            if (!removeLayer(world, rt, rt.offset)) {
                if (rt.lastFail == null) rt.lastFail = "removeLayer_failed offset=" + rt.offset;
                return false;
            }
        }
        rt.offset = nextOffset;

        int minX = rt.minX;
        int maxX = rt.maxX;
        int minY = rt.minY;
        int maxY = rt.maxY;
        int minZ = rt.minZ;
        int maxZ = rt.maxZ;
        if (rt.stepX > 0) maxX += rt.offset * rt.stepX;
        else if (rt.stepX < 0) minX += rt.offset * rt.stepX;
        if (rt.stepY > 0) maxY += rt.offset * rt.stepY;
        else if (rt.stepY < 0) minY += rt.offset * rt.stepY;
        if (rt.stepZ > 0) maxZ += rt.offset * rt.stepZ;
        else if (rt.stepZ < 0) minZ += rt.offset * rt.stepZ;

        pushPlayers(world, minX, minY, minZ, maxX, maxY, maxZ, rt.stepX * (rt.forward ? 1 : -1), rt.stepY * (rt.forward ? 1 : -1), rt.stepZ * (rt.forward ? 1 : -1));
        return true;
    }

    private static void restoreOverwrittenBlocks(World world, PusherRuntime rt) {
        if (world == null || rt == null) return;
        if (rt.overwritten == null || rt.overwritten.isEmpty()) return;
        for (Map.Entry<Long, BlockData> e : rt.overwritten.entrySet()) {
            long k = e.getKey();
            if (rt.blocks != null && rt.blocks.containsKey(k)) continue;
            int x = unpackX(k);
            int y = unpackY(k);
            int z = unpackZ(k);
            world.getBlockAt(x, y, z).setBlockData(e.getValue(), false);
        }
        rt.overwritten.clear();
    }

    private static boolean placeLayer(World world, PusherRuntime rt, int layerOffset) {
        if (world == null || rt == null || rt.baseBlocks == null || rt.baseBlocks.isEmpty()) return false;
        if (layerOffset <= 0 || layerOffset > rt.length) return false;
        int dx = rt.stepX * layerOffset;
        int dy = rt.stepY * layerOffset;
        int dz = rt.stepZ * layerOffset;
        for (Map.Entry<Long, BlockData> e : rt.baseBlocks.entrySet()) {
            long k0 = e.getKey();
            int x = unpackX(k0) + dx;
            int y = unpackY(k0) + dy;
            int z = unpackZ(k0) + dz;
            long k = packBlock(x, y, z);
            Integer count = rt.coverage.get(k);
            if (count != null) {
                rt.coverage.put(k, count + 1);
                continue;
            }
            Block b = world.getBlockAt(x, y, z);
            rt.overwritten.putIfAbsent(k, b.getBlockData().clone());
            BlockData data = e.getValue();
            BlockData placed = data != null ? data.clone() : null;
            if (placed == null) {
                rt.lastFail = "blockdata_null place x=" + x + " y=" + y + " z=" + z;
                return false;
            }
            b.setBlockData(placed, false);
            rt.blocks.put(k, placed);
            rt.coverage.put(k, 1);
        }
        return true;
    }

    private static boolean removeLayer(World world, PusherRuntime rt, int layerOffset) {
        if (world == null || rt == null || rt.baseBlocks == null || rt.baseBlocks.isEmpty()) return false;
        if (layerOffset <= 0 || layerOffset > rt.length) return false;
        int dx = rt.stepX * layerOffset;
        int dy = rt.stepY * layerOffset;
        int dz = rt.stepZ * layerOffset;
        for (long k0 : rt.baseBlocks.keySet()) {
            int x = unpackX(k0) + dx;
            int y = unpackY(k0) + dy;
            int z = unpackZ(k0) + dz;
            long k = packBlock(x, y, z);
            Integer count = rt.coverage.get(k);
            if (count == null) continue;
            if (count > 1) {
                rt.coverage.put(k, count - 1);
                continue;
            }
            rt.coverage.remove(k);
            rt.blocks.remove(k);
            Block b = world.getBlockAt(x, y, z);
            BlockData restore = rt.overwritten.remove(k);
            if (restore != null) b.setBlockData(restore, false);
            else b.setType(Material.AIR, false);
        }
        return true;
    }

    private static boolean shouldLogPusher(String groupKey, String templateName) {
        if (groupKey != null && groupKey.startsWith("tpl:")) return true;
        return templateName != null && "trap_room_02".equalsIgnoreCase(templateName);
    }

    private static int bestRegionYShiftForSolids(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int total, int radius,
            int preferMinY, int preferMaxY) {
        if (world == null) return 0;
        if (radius <= 0) return 0;
        if (total <= 0) return 0;
        int minH = world.getMinHeight();
        int maxH = world.getMaxHeight() - 1;

        for (int d = 0; d <= radius; d++) {
            int[] candidates = d == 0 ? new int[] { 0 } : new int[] { d, -d };
            for (int dy : candidates) {
                int y0 = minY + dy;
                int y1 = maxY + dy;
                if (y1 < preferMinY || y0 > preferMaxY) continue;
                if (y0 < minH || y1 > maxH) continue;

                boolean ok = true;
                outer:
                for (int x = minX; x <= maxX; x++) {
                    for (int y = y0; y <= y1; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            Block b = world.getBlockAt(x, y, z);
                            Material t = b.getType();
                            if (t.isAir() || b.isPassable()) {
                                ok = false;
                                break outer;
                            }
                        }
                    }
                }
                if (!ok) continue;
                return dy;
            }
        }
        return 0;
    }

    private static int tryExpandFull(World world, Material mat,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
            int backX, int backY, int backZ) {
        if (world == null || mat == null) return 0;
        if ((backX == 0 ? 0 : 1) + (backY == 0 ? 0 : 1) + (backZ == 0 ? 0 : 1) != 1) return 0;

        int expanded = 0;
        for (int i = 0; i < 64; i++) {
            int layerXMin = minX + backX * (expanded + 1);
            int layerXMax = maxX + backX * (expanded + 1);
            int layerYMin = minY + backY * (expanded + 1);
            int layerYMax = maxY + backY * (expanded + 1);
            int layerZMin = minZ + backZ * (expanded + 1);
            int layerZMax = maxZ + backZ * (expanded + 1);

            boolean ok = true;
            if (backX != 0) {
                int x = backX < 0 ? layerXMin : layerXMax;
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        Block b = world.getBlockAt(x, y, z);
                        if (b.getType() != mat || b.isPassable()) {
                            ok = false;
                            break;
                        }
                    }
                    if (!ok) break;
                }
            } else if (backZ != 0) {
                int z = backZ < 0 ? layerZMin : layerZMax;
                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        Block b = world.getBlockAt(x, y, z);
                        if (b.getType() != mat || b.isPassable()) {
                            ok = false;
                            break;
                        }
                    }
                    if (!ok) break;
                }
            } else {
                int y = backY < 0 ? layerYMin : layerYMax;
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        Block b = world.getBlockAt(x, y, z);
                        if (b.getType() != mat || b.isPassable()) {
                            ok = false;
                            break;
                        }
                    }
                    if (!ok) break;
                }
            }

            if (!ok) break;
            expanded++;
        }
        return expanded;
    }

    private static void pushPlayers(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int dirX, int dirY, int dirZ) {
        if (world == null) return;
        for (Player p : world.getPlayers()) {
            if (p == null || !p.isOnline()) continue;
            Location loc = p.getLocation();
            if (loc == null) continue;
            int px = loc.getBlockX();
            int py = loc.getBlockY();
            int pz = loc.getBlockZ();

            boolean inXZ = px >= minX && px <= maxX && pz >= minZ && pz <= maxZ;
            boolean inY = py >= minY && py <= maxY + 1;
            boolean inside = inXZ && inY;

            boolean onTop = inXZ && py == maxY + 1 && !world.getBlockAt(px, py - 1, pz).isPassable();

            boolean inFront = false;
            if (dirX > 0) inFront = px == maxX + 1 && pz >= minZ && pz <= maxZ && py >= minY && py <= maxY + 1;
            else if (dirX < 0) inFront = px == minX - 1 && pz >= minZ && pz <= maxZ && py >= minY && py <= maxY + 1;
            else if (dirZ > 0) inFront = pz == maxZ + 1 && px >= minX && px <= maxX && py >= minY && py <= maxY + 1;
            else if (dirZ < 0) inFront = pz == minZ - 1 && px >= minX && px <= maxX && py >= minY && py <= maxY + 1;
            else if (dirY > 0) inFront = py == maxY + 1 && px >= minX && px <= maxX && pz >= minZ && pz <= maxZ;
            else if (dirY < 0) inFront = py == minY - 1 && px >= minX && px <= maxX && pz >= minZ && pz <= maxZ;

            if (!(inside || onTop || inFront)) continue;

            boolean teleported = tryPushTeleport(p, dirX, dirY, dirZ);
            if (!teleported) {
                if (p.getGameMode() == GameMode.SURVIVAL && wouldCrushPlayer(p, dirX, dirY, dirZ)) {
                    p.setHealth(0.0);
                    continue;
                }
                Vector v = new Vector(dirX * 0.25, Math.max(0.06, dirY * 0.12), dirZ * 0.25);
                p.setVelocity(v);
            }
        }
    }

    private static boolean tryPushTeleport(Player p, int dx, int dy, int dz) {
        if (p == null) return false;
        World w = p.getWorld();
        if (w == null) return false;
        Location from = p.getLocation();
        if (from == null) return false;
        Location base = from.clone().add(dx, dy, dz);
        for (int up = 0; up <= 2; up++) {
            Location at = base.clone().add(0, up, 0);
            int x = at.getBlockX();
            int y = at.getBlockY();
            int z = at.getBlockZ();
            if (!isPassableBlock(w.getBlockAt(x, y, z))) continue;
            if (!isPassableBlock(w.getBlockAt(x, y + 1, z))) continue;
            at.setYaw(from.getYaw());
            at.setPitch(from.getPitch());
            return p.teleport(at);
        }
        return false;
    }

    private static boolean wouldCrushPlayer(Player p, int dx, int dy, int dz) {
        if (p == null) return false;
        World w = p.getWorld();
        if (w == null) return false;
        Location from = p.getLocation();
        if (from == null) return false;
        Location base = from.clone().add(dx, dy, dz);
        int bx = base.getBlockX();
        int by = base.getBlockY();
        int bz = base.getBlockZ();
        for (int up = 0; up <= 2; up++) {
            int y = by + up;
            if (!isPassableBlock(w.getBlockAt(bx, y, bz))) continue;
            if (!isPassableBlock(w.getBlockAt(bx, y + 1, bz))) continue;
            return false;
        }
        return true;
    }

    private static Location pickTargetBlockLocation(Player player) {
        Block target = player.getTargetBlockExact(32);
        if (target != null)
            return target.getLocation();
        return player.getLocation().getBlock().getLocation();
    }

    private static String format(Location loc) {
        return loc.getWorld().getName() + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ();
    }

    private static String sanitizeName(String raw) {
        return raw.toLowerCase().replaceAll("[^a-z0-9_\\-]", "");
    }

    private static StructureTemplate.Rotation parseRotation(String raw) {
        if (raw == null)
            return null;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "r0", "0", "n", "north" -> StructureTemplate.Rotation.R0;
            case "r90", "90", "e", "east" -> StructureTemplate.Rotation.R90;
            case "r180", "180", "s", "south" -> StructureTemplate.Rotation.R180;
            case "r270", "270", "w", "west" -> StructureTemplate.Rotation.R270;
            default -> null;
        };
    }

    private static int[] inverseRotateXZ(int xRot, int zRot, StructureTemplate.Rotation rotation, int sizeXRot,
            int sizeZRot) {
        int sizeX0;
        int sizeZ0;
        if (rotation == StructureTemplate.Rotation.R90 || rotation == StructureTemplate.Rotation.R270) {
            sizeX0 = sizeZRot;
            sizeZ0 = sizeXRot;
        } else {
            sizeX0 = sizeXRot;
            sizeZ0 = sizeZRot;
        }

        return switch (rotation) {
            case R0 -> new int[] { xRot, zRot };
            case R90 -> new int[] { sizeX0 - 1 - zRot, xRot };
            case R180 -> new int[] { sizeX0 - 1 - xRot, sizeZ0 - 1 - zRot };
            case R270 -> new int[] { zRot, sizeZ0 - 1 - xRot };
        };
    }

    private static final class PusherConfig {
        private final String id;
        private final int regionMinX;
        private final int regionMinY;
        private final int regionMinZ;
        private final int regionMaxX;
        private final int regionMaxY;
        private final int regionMaxZ;
        private final int startX;
        private final int startY;
        private final int startZ;
        private final int endX;
        private final int endY;
        private final int endZ;
        private final String linkKey;
        private final int linkPhase;

        private PusherConfig(String id,
                int regionMinX, int regionMinY, int regionMinZ,
                int regionMaxX, int regionMaxY, int regionMaxZ,
                int startX, int startY, int startZ,
                int endX, int endY, int endZ,
                String linkKey, int linkPhase) {
            this.id = id;
            this.regionMinX = regionMinX;
            this.regionMinY = regionMinY;
            this.regionMinZ = regionMinZ;
            this.regionMaxX = regionMaxX;
            this.regionMaxY = regionMaxY;
            this.regionMaxZ = regionMaxZ;
            this.startX = startX;
            this.startY = startY;
            this.startZ = startZ;
            this.endX = endX;
            this.endY = endY;
            this.endZ = endZ;
            this.linkKey = linkKey;
            this.linkPhase = linkPhase;
        }

        private PusherConfig withLink(String linkKey, int linkPhase) {
            return new PusherConfig(id,
                    regionMinX, regionMinY, regionMinZ,
                    regionMaxX, regionMaxY, regionMaxZ,
                    startX, startY, startZ,
                    endX, endY, endZ,
                    linkKey, linkPhase);
        }
    }

    private static final class RoomPusher {
        private final PusherConfig config;
        private final PusherRuntime runtime;

        private RoomPusher(PusherConfig config, PusherRuntime runtime) {
            this.config = config;
            this.runtime = runtime;
        }
    }

    private static final class RoomPusherRuntime {
        private final String worldName;
        private final String groupKey;
        private final String templateName;
        private final int minX;
        private final int baseY;
        private final int minZ;
        private final int maxX;
        private final int maxZ;
        private final StructureTemplate.Rotation rotation;
        private final int sizeX0;
        private final int sizeZ0;
        private final List<RoomPusher> pushers;
        private final long activeUntilMs;
        private boolean running;
        private boolean startedOnce;

        private RoomPusherRuntime(String worldName, String groupKey, String templateName,
                int minX, int baseY, int minZ, int maxX, int maxZ,
                StructureTemplate.Rotation rotation, int sizeX0, int sizeZ0, List<RoomPusher> pushers, long activeUntilMs) {
            this.worldName = worldName;
            this.groupKey = groupKey;
            this.templateName = templateName;
            this.minX = minX;
            this.baseY = baseY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxZ = maxZ;
            this.rotation = rotation;
            this.sizeX0 = sizeX0;
            this.sizeZ0 = sizeZ0;
            this.pushers = pushers;
            this.activeUntilMs = activeUntilMs;
        }
    }

    private static final class Selection {
        private Location pos1;
        private Location pos2;
        private Location tombPos1;
        private Location tombPos2;
        private String ice05LevelKey;
        private Location ice05BoardPos1;
        private Location ice05BoardPos2;
        private Location ice05Start;
        private Location ice05End;
        private Location blaze08CirclePos1;
        private Location blaze08CirclePos2;
        private Location blaze08ProgressPos1;
        private Location blaze08ProgressPos2;
        private StructureTemplate.Rotation lastPasteRotation;
        private int lastPasteSizeX;
        private int lastPasteSizeZ;

        private Location pusherRegionPos1;
        private Location pusherRegionPos2;
        private Location pusherStart;
        private Location pusherEnd;
        private BukkitRunnable pusherTask;
        private PusherRuntime pusherRuntime;
    }

    private static final class PusherRuntime {
        private final String worldName;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;
        private final int stepX;
        private final int stepY;
        private final int stepZ;
        private final int length;
        private int offset;
        private boolean forward = true;
        private String lastFail;
        private boolean firstMoveLogged;
        private final Map<Long, BlockData> baseBlocks;
        private final Map<Long, BlockData> blocks;
        private final Map<Long, Integer> coverage;
        private final Map<Long, BlockData> overwritten;

        private PusherRuntime(String worldName, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                int stepX, int stepY, int stepZ, int length, Map<Long, BlockData> baseBlocks, Map<Long, BlockData> blocks, Map<Long, Integer> coverage, Map<Long, BlockData> overwritten) {
            this.worldName = worldName;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.stepX = stepX;
            this.stepY = stepY;
            this.stepZ = stepZ;
            this.length = length;
            this.baseBlocks = baseBlocks;
            this.blocks = blocks;
            this.coverage = coverage;
            this.overwritten = overwritten;
        }
    }
}
