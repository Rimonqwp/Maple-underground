package maple.underground_state.collection;

import maple.underground_state.UndergroundState;
import maple.underground_state.gui.CollectionInfoGui;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.PistonMoveReaction;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class CollectionProgressListener implements Listener {

    private static final NamespacedKey LEGACY_PLACED_FORAGING_KEY =
            new NamespacedKey("underground_state", "placed_foraging_blocks");

    private final UndergroundState plugin;
    private final CollectionProgressManager manager;
    private final NamespacedKey placedForagingKey;

    public CollectionProgressListener(@NotNull UndergroundState plugin,
                                      @NotNull CollectionProgressManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.placedForagingKey = new NamespacedKey(plugin, "placed_collection_foraging_blocks");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(@NotNull PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> manager.loadIntoCache(uuid));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(@NotNull PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> manager.onQuit(uuid));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(@NotNull BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (CollectionInfoGui.hasCollectionMaterial(CollectionInfoGui.Category.FORAGING, block.getType())) {
            markPlaced(block, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(@NotNull BlockPistonExtendEvent event) {
        movePlacedMarkers(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(@NotNull BlockPistonRetractEvent event) {
        movePlacedMarkers(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(@NotNull EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            markPlaced(block, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(@NotNull BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            markPlaced(block, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForagingBreak(@NotNull BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        if (!CollectionInfoGui.hasCollectionMaterial(CollectionInfoGui.Category.FORAGING, type)) {
            return;
        }
        boolean wasPlaced = consumePlacedMarker(block);
        Player player = event.getPlayer();
        if (wasPlaced || !isEligiblePlayer(player) || !event.isDropItems()) {
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        int amount = 0;
        for (ItemStack drop : block.getDrops(tool, player)) {
            if (drop != null && drop.getType() == type && drop.getAmount() > 0) {
                amount += drop.getAmount();
            }
        }
        if (amount > 0) {
            manager.addMaterial(player.getUniqueId(), type, amount);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombatEntityDeath(@NotNull EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }
        Player killer = entity.getKiller();
        if (!isEligiblePlayer(killer)) {
            return;
        }

        UUID killerId = killer.getUniqueId();
        for (ItemStack drop : event.getDrops()) {
            if (drop == null || drop.getType().isAir() || drop.getAmount() <= 0) {
                continue;
            }
            if (hasCustomPdc(drop)) {
                continue;
            }
            Material type = drop.getType();
            if (CollectionInfoGui.hasCollectionMaterial(CollectionInfoGui.Category.COMBAT, type)) {
                manager.addMaterial(killerId, type, drop.getAmount());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(@NotNull PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        Player player = event.getPlayer();
        if (!isEligiblePlayer(player)) {
            return;
        }
        if (!(event.getCaught() instanceof Item itemEntity)) {
            return;
        }

        ItemStack stack = itemEntity.getItemStack();
        if (stack.getType().isAir() || stack.getAmount() <= 0 || hasCustomPdc(stack)) {
            return;
        }
        Material type = stack.getType();
        if (CollectionInfoGui.hasCollectionMaterial(CollectionInfoGui.Category.FISHING, type)) {
            manager.addMaterial(player.getUniqueId(), type, stack.getAmount());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCookedFishExtract(@NotNull FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        if (!isEligiblePlayer(player)) {
            return;
        }
        Material type = event.getItemType();
        int amount = event.getItemAmount();
        if (amount <= 0) {
            return;
        }
        if (type == Material.COOKED_COD || type == Material.COOKED_SALMON) {
            manager.addMaterial(player.getUniqueId(), type, amount);
        }
    }

    private static boolean isEligiblePlayer(@Nullable Player player) {
        return player != null
                && player.isOnline()
                && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE);
    }

    private static boolean hasCustomPdc(@NotNull ItemStack stack) {
        return stack.hasItemMeta() && !stack.getItemMeta().getPersistentDataContainer().isEmpty();
    }

    private void movePlacedMarkers(@NotNull List<Block> blocks, @NotNull BlockFace direction) {
        List<Block> tracked = blocks.stream()
                .filter(this::isMarkedPlaced)
                .filter(block -> block.getPistonMoveReaction() != PistonMoveReaction.BREAK)
                .toList();
        for (Block block : blocks) {
            markPlaced(block, false);
        }
        for (Block block : tracked) {
            markPlaced(block.getRelative(direction), true);
        }
    }

    private boolean isMarkedPlaced(@NotNull Block block) {
        String key = localKey(block);
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        return readEntries(pdc, placedForagingKey).contains(key)
                || readEntries(pdc, LEGACY_PLACED_FORAGING_KEY).contains(key);
    }

    private boolean consumePlacedMarker(@NotNull Block block) {
        String key = localKey(block);
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        Set<String> current = readEntries(pdc, placedForagingKey);
        boolean marked = current.remove(key);
        if (marked) {
            writeEntries(pdc, placedForagingKey, current);
        }
        if (!marked && readEntries(pdc, LEGACY_PLACED_FORAGING_KEY).contains(key)) {
            return true;
        }
        return marked;
    }

    private void markPlaced(@NotNull Block block, boolean placed) {
        Chunk chunk = block.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        Set<String> entries = readEntries(pdc, placedForagingKey);
        String key = localKey(block);
        boolean changed = placed ? entries.add(key) : entries.remove(key);
        if (changed) {
            writeEntries(pdc, placedForagingKey, entries);
        }
    }

    private static @NotNull Set<String> readEntries(@NotNull PersistentDataContainer pdc, @NotNull NamespacedKey key) {
        String raw = pdc.get(key, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(raw.split(";"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static void writeEntries(@NotNull PersistentDataContainer pdc,
                                     @NotNull NamespacedKey key,
                                     @NotNull Set<String> entries) {
        if (entries.isEmpty()) {
            pdc.remove(key);
            return;
        }
        pdc.set(key, PersistentDataType.STRING, String.join(";", entries));
    }

    private static @NotNull String localKey(@NotNull Block block) {
        return (block.getX() & 15) + "," + block.getY() + "," + (block.getZ() & 15);
    }
}
