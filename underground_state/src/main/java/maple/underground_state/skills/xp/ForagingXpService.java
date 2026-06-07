package maple.underground_state.skills.xp;

import maple.underground_state.skills.SkillProgressManager;
import maple.underground_state.skills.SkillRewardService;
import maple.underground_state.skills.SkillType;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ForagingXpService implements Listener {

    private static final NamespacedKey PLACED_FORAGING_BLOCKS_KEY =
            new NamespacedKey("underground_state", "placed_foraging_blocks");
    private static final Map<Material, Integer> XP_TENTHS_BY_BLOCK = buildXpTenthsByBlock();

    private final SkillProgressManager skillProgressManager;
    private final SkillRewardService skillRewardService;
    private final CombatXpOverlayService xpOverlayService;

    public ForagingXpService(@NotNull SkillProgressManager skillProgressManager,
                             @NotNull SkillRewardService skillRewardService,
                             @NotNull CombatXpOverlayService xpOverlayService) {
        this.skillProgressManager = skillProgressManager;
        this.skillRewardService = skillRewardService;
        this.xpOverlayService = xpOverlayService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!XP_TENTHS_BY_BLOCK.containsKey(block.getType())) {
            return;
        }
        addPlacedMarker(block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || !player.isOnline()) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        Block block = event.getBlock();
        Material type = block.getType();
        Integer awardTenths = XP_TENTHS_BY_BLOCK.get(type);
        if (awardTenths == null || awardTenths <= 0) {
            return;
        }

        if (removePlacedMarkerIfPresent(block)) {
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (block.getDrops(tool, player).isEmpty()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        int carriedTenths = skillProgressManager.getForagingXpRemainderTenths(playerId);
        int totalTenths = carriedTenths + awardTenths;
        long wholeXp = totalTenths / 10L;
        int remainderTenths = totalTenths % 10;

        if (wholeXp > 0L) {
            skillRewardService.addSkillXp(playerId, SkillType.FORAGING, wholeXp);
        }
        skillProgressManager.setForagingXpRemainderTenths(playerId, remainderTenths);

        long foragingXpWhole = skillProgressManager.getXp(playerId, SkillType.FORAGING);
        long displayTotalTenths = foragingXpWhole * 10L + remainderTenths;
        xpOverlayService.showSkillOverlay(player, SkillType.FORAGING, awardTenths, displayTotalTenths);
    }

    private static void addPlacedMarker(@NotNull Block block) {
        Chunk chunk = block.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        Set<String> entries = readPlacedEntries(pdc);
        entries.add(localKey(block));
        writePlacedEntries(pdc, entries);
    }

    private static boolean removePlacedMarkerIfPresent(@NotNull Block block) {
        Chunk chunk = block.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        Set<String> entries = readPlacedEntries(pdc);
        if (!entries.remove(localKey(block))) {
            return false;
        }
        writePlacedEntries(pdc, entries);
        return true;
    }

    private static @NotNull Set<String> readPlacedEntries(@NotNull PersistentDataContainer pdc) {
        String raw = pdc.get(PLACED_FORAGING_BLOCKS_KEY, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(raw.split(";"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static void writePlacedEntries(@NotNull PersistentDataContainer pdc, @NotNull Set<String> entries) {
        if (entries.isEmpty()) {
            pdc.remove(PLACED_FORAGING_BLOCKS_KEY);
            return;
        }
        pdc.set(PLACED_FORAGING_BLOCKS_KEY, PersistentDataType.STRING, String.join(";", entries));
    }

    private static @NotNull String localKey(@NotNull Block block) {
        int localX = block.getX() & 15;
        int localZ = block.getZ() & 15;
        return localX + "," + block.getY() + "," + localZ;
    }

    private static @NotNull Map<Material, Integer> buildXpTenthsByBlock() {
        Map<Material, Integer> map = new EnumMap<>(Material.class);
        map.put(Material.OAK_LOG, 10);
        map.put(Material.BIRCH_LOG, 10);
        map.put(Material.SPRUCE_LOG, 10);
        map.put(Material.JUNGLE_LOG, 11);
        map.put(Material.ACACIA_LOG, 10);
        map.put(Material.DARK_OAK_LOG, 11);
        map.put(Material.CHERRY_LOG, 10);
        map.put(Material.MANGROVE_LOG, 12);
        map.put(Material.MANGROVE_ROOTS, 5);
        map.put(Material.MUDDY_MANGROVE_ROOTS, 5);
        map.put(Material.CRIMSON_STEM, 13);
        map.put(Material.WARPED_STEM, 13);
        map.put(Material.CRIMSON_HYPHAE, 11);
        map.put(Material.WARPED_HYPHAE, 11);
        return Map.copyOf(map);
    }
}
