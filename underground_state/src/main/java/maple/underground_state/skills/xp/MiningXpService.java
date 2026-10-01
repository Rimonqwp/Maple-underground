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

public final class MiningXpService implements Listener {

    private static final NamespacedKey PLACED_MINING_BLOCKS_KEY =
            new NamespacedKey("underground_state", "placed_mining_blocks");
    private static final Map<Material, Integer> XP_TENTHS_BY_BLOCK = buildXpTenthsByBlock();

    public static int baseXpTenths(Material material) { return XP_TENTHS_BY_BLOCK.getOrDefault(material, 0); }

    private final SkillProgressManager skillProgressManager;
    private final SkillRewardService skillRewardService;
    private final CombatXpOverlayService xpOverlayService;

    public MiningXpService(@NotNull SkillProgressManager skillProgressManager,
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
        var active = maple.underground_state.UndergroundState.getInstance();
        if (active != null && active.getProfessionService() != null && active.getProfessionService().enabled(event.getPlayer().getWorld())) return;
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
        int carriedTenths = skillProgressManager.getMiningXpRemainderTenths(playerId);
        int totalTenths = carriedTenths + awardTenths;
        long wholeXp = totalTenths / 10L;
        int remainderTenths = totalTenths % 10;

        if (wholeXp > 0L) {
            skillRewardService.addSkillXp(playerId, SkillType.MINING, wholeXp);
        }
        skillProgressManager.setMiningXpRemainderTenths(playerId, remainderTenths);

        long miningXpWhole = skillProgressManager.getXp(playerId, SkillType.MINING);
        long displayTotalTenths = miningXpWhole * 10L + remainderTenths;
        xpOverlayService.showSkillOverlay(player, SkillType.MINING, awardTenths, displayTotalTenths);
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
        String raw = pdc.get(PLACED_MINING_BLOCKS_KEY, PersistentDataType.STRING);
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
            pdc.remove(PLACED_MINING_BLOCKS_KEY);
            return;
        }
        pdc.set(PLACED_MINING_BLOCKS_KEY, PersistentDataType.STRING, String.join(";", entries));
    }

    private static @NotNull String localKey(@NotNull Block block) {
        int localX = block.getX() & 15;
        int localZ = block.getZ() & 15;
        return localX + "," + block.getY() + "," + localZ;
    }

    private static @NotNull Map<Material, Integer> buildXpTenthsByBlock() {
        Map<Material, Integer> map = new EnumMap<>(Material.class);

        map.put(Material.STONE, 1);
        map.put(Material.DEEPSLATE, 1);
        map.put(Material.GRANITE, 1);
        map.put(Material.DIORITE, 1);
        map.put(Material.ANDESITE, 1);
        map.put(Material.TUFF, 1);
        map.put(Material.CALCITE, 1);
        map.put(Material.BLACKSTONE, 1);
        map.put(Material.BASALT, 1);
        map.put(Material.SMOOTH_BASALT, 1);

        map.put(Material.COAL_ORE, 10);
        map.put(Material.DEEPSLATE_COAL_ORE, 12);
        map.put(Material.COPPER_ORE, 12);
        map.put(Material.DEEPSLATE_COPPER_ORE, 14);
        map.put(Material.IRON_ORE, 16);
        map.put(Material.DEEPSLATE_IRON_ORE, 18);
        map.put(Material.REDSTONE_ORE, 18);
        map.put(Material.DEEPSLATE_REDSTONE_ORE, 20);
        map.put(Material.LAPIS_ORE, 20);
        map.put(Material.DEEPSLATE_LAPIS_ORE, 22);
        map.put(Material.GOLD_ORE, 22);
        map.put(Material.DEEPSLATE_GOLD_ORE, 24);
        map.put(Material.NETHER_GOLD_ORE, 8);
        map.put(Material.NETHER_QUARTZ_ORE, 9);
        map.put(Material.EMERALD_ORE, 35);
        map.put(Material.DEEPSLATE_EMERALD_ORE, 38);
        map.put(Material.DIAMOND_ORE, 45);
        map.put(Material.DEEPSLATE_DIAMOND_ORE, 50);
        map.put(Material.ANCIENT_DEBRIS, 120);

        map.put(Material.AMETHYST_CLUSTER, 25);
        map.put(Material.LARGE_AMETHYST_BUD, 12);
        map.put(Material.MEDIUM_AMETHYST_BUD, 6);
        map.put(Material.SMALL_AMETHYST_BUD, 2);
        map.put(Material.GLOWSTONE, 8);
        map.put(Material.OBSIDIAN, 5);

        return Map.copyOf(map);
    }
}
