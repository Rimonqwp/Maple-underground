package maple.underground_state.collection;

import maple.underground_state.UndergroundState;
import maple.underground_state.gui.CollectionInfoGui;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class CollectionProgressListener implements Listener {

    private final CollectionProgressManager manager;
    private final NamespacedKey sourceBlockKey;

    public CollectionProgressListener(@NotNull UndergroundState plugin,
                                      @NotNull CollectionProgressManager manager) {
        this.manager = manager;
        this.sourceBlockKey = new NamespacedKey(plugin, "collection_source_block");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(@NotNull PlayerJoinEvent event) {
        manager.onJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(@NotNull PlayerQuitEvent event) {
        manager.onQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDropItem(@NotNull BlockDropItemEvent event) {
        Material source = event.getBlockState().getType();
        if (!shouldTrackSourceBlock(source)) {
            return;
        }
        for (Item item : event.getItems()) {
            item.getPersistentDataContainer().set(sourceBlockKey, PersistentDataType.STRING, source.name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPickupItem(@NotNull EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        Item itemEntity = event.getItem();
        ItemStack stack = itemEntity.getItemStack();
        int pickedUp = Math.max(0, stack.getAmount() - event.getRemaining());
        if (pickedUp <= 0) {
            return;
        }

        UUID playerId = player.getUniqueId();
        Material stackType = stack.getType();
        @Nullable Material sourceType = sourceMaterial(itemEntity.getPersistentDataContainer());
        @Nullable Material sourceCollectionType = sourceType == null ? null : collectionMaterialForSource(sourceType);

        Material countedMaterial = null;
        if (sourceCollectionType != null) {
            countedMaterial = sourceCollectionType;
            manager.addMaterial(playerId, sourceCollectionType, pickedUp);
        } else if (sourceType != null) {
            countedMaterial = normalizeCollectionMaterial(sourceType);
            manager.addMaterial(playerId, sourceType, pickedUp);
        }
        if (countedMaterial == null || countedMaterial != normalizeCollectionMaterial(stackType)) {
            manager.addMaterial(playerId, stackType, pickedUp);
        }
    }

    private boolean shouldTrackSourceBlock(@NotNull Material material) {
        return collectionMaterialForSource(material) != null
                || CollectionInfoGui.hasCollectionMaterial(CollectionInfoGui.Category.MINING, material)
                || CollectionInfoGui.hasCollectionMaterial(CollectionInfoGui.Category.FORAGING, material);
    }

    private @Nullable Material collectionMaterialForSource(@NotNull Material source) {
        return switch (source) {
            case RED_SAND -> Material.SAND;
            case COAL_ORE, DEEPSLATE_COAL_ORE -> Material.COAL;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> Material.RAW_COPPER;
            case IRON_ORE, DEEPSLATE_IRON_ORE -> Material.RAW_IRON;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE -> Material.RAW_GOLD;
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> Material.REDSTONE;
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> Material.LAPIS_LAZULI;
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> Material.EMERALD;
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> Material.DIAMOND;
            case NETHER_QUARTZ_ORE -> Material.QUARTZ;
            case ANCIENT_DEBRIS -> Material.NETHERITE_SCRAP;
            default -> null;
        };
    }

    private @Nullable Material sourceMaterial(@NotNull PersistentDataContainer pdc) {
        String raw = pdc.get(sourceBlockKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Material material = Material.matchMaterial(raw);
        return material == null || material.isAir() ? null : material;
    }

    private @NotNull Material normalizeCollectionMaterial(@NotNull Material material) {
        return switch (material) {
            case RED_SAND -> Material.SAND;
            default -> material;
        };
    }
}
