package maple.underground_state.skills.xp;

import maple.underground_state.skills.SkillProgressManager;
import maple.underground_state.skills.SkillRewardService;
import maple.underground_state.skills.SkillType;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.CaveVines;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
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

public final class FarmingXpService implements Listener {

    private static final NamespacedKey PLACED_FARMING_BLOCKS_KEY =
            new NamespacedKey("underground_state", "placed_farming_blocks");
    private static final Map<Material, Integer> HARVEST_XP_TENTHS_BY_BLOCK = buildHarvestXpTenthsByBlock();
    private static final Map<EntityType, Integer> KILL_XP_TENTHS_BY_ENTITY = buildKillXpTenthsByEntity();

    public static int baseXpTenths(Material material) { return HARVEST_XP_TENTHS_BY_BLOCK.getOrDefault(material, 0); }

    private final SkillProgressManager skillProgressManager;
    private final SkillRewardService skillRewardService;
    private final CombatXpOverlayService xpOverlayService;

    public FarmingXpService(@NotNull SkillProgressManager skillProgressManager,
                            @NotNull SkillRewardService skillRewardService,
                            @NotNull CombatXpOverlayService xpOverlayService) {
        this.skillProgressManager = skillProgressManager;
        this.skillRewardService = skillRewardService;
        this.xpOverlayService = xpOverlayService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (isTrackedPlacedBlock(block.getType())) {
            addPlacedMarker(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        var active = maple.underground_state.UndergroundState.getInstance();
        if (active != null && active.getProfessionService() != null && active.getProfessionService().enabled(event.getPlayer().getWorld())) return;
        Player player = event.getPlayer();
        if (!canGainXp(player)) {
            return;
        }

        Block block = event.getBlock();
        Material type = block.getType();
        Integer unitTenths = HARVEST_XP_TENTHS_BY_BLOCK.get(type);
        if (unitTenths == null || unitTenths <= 0) {
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (block.getDrops(tool, player).isEmpty()) {
            return;
        }

        int awardTenths = resolveHarvestAwardTenths(block, type, unitTenths);
        if (awardTenths <= 0) {
            return;
        }

        grantFarmingXp(player, awardTenths);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockHarvest(PlayerHarvestBlockEvent event) {
        var active = maple.underground_state.UndergroundState.getInstance();
        if (active != null && active.getProfessionService() != null && active.getProfessionService().enabled(event.getPlayer().getWorld())) return;
        Player player = event.getPlayer();
        if (!canGainXp(player)) {
            return;
        }

        Block block = event.getHarvestedBlock();
        Material type = block.getType();
        Integer unitTenths = HARVEST_XP_TENTHS_BY_BLOCK.get(type);
        if (unitTenths == null || unitTenths <= 0 || !supportsNonBreakHarvest(type)) {
            return;
        }
        if (event.getItemsHarvested().isEmpty()) {
            return;
        }

        grantFarmingXp(player, unitTenths);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (!canGainXp(killer)) {
            return;
        }

        Integer awardTenths = KILL_XP_TENTHS_BY_ENTITY.get(entity.getType());
        if (awardTenths == null || awardTenths <= 0) {
            return;
        }

        var active = maple.underground_state.UndergroundState.getInstance();
        if (entity.getPersistentDataContainer().has(maple.underground_state.professions.ProfessionService.MOB_KEY)) return;
        if (active != null && active.getProfessionService() != null) {
            var service = active.getProfessionService();
            if (service.enabled(killer.getWorld()) && service.store().ready(killer.getUniqueId())) {
                service.grantXp(killer, SkillType.FARMING, awardTenths, service.level(killer, SkillType.FARMING), service.bonuses(killer, SkillType.FARMING).xpPercent());
                for (var drop : event.getDrops()) active.getCollectionProgressManager().addMaterial(killer.getUniqueId(), drop.getType(), drop.getAmount());
            } else if(!service.enabled(killer.getWorld())) grantFarmingXp(killer,awardTenths);
        } else grantFarmingXp(killer, awardTenths);
    }

    private void grantFarmingXp(@NotNull Player player, int awardTenths) {
        UUID playerId = player.getUniqueId();
        int carriedTenths = skillProgressManager.getFarmingXpRemainderTenths(playerId);
        int totalTenths = carriedTenths + awardTenths;
        long wholeXp = totalTenths / 10L;
        int remainderTenths = totalTenths % 10;

        if (wholeXp > 0L) {
            skillRewardService.addSkillXp(playerId, SkillType.FARMING, wholeXp);
        }
        skillProgressManager.setFarmingXpRemainderTenths(playerId, remainderTenths);

        long farmingXpWhole = skillProgressManager.getXp(playerId, SkillType.FARMING);
        long displayTotalTenths = farmingXpWhole * 10L + remainderTenths;
        xpOverlayService.showSkillOverlay(player, SkillType.FARMING, awardTenths, displayTotalTenths);
    }

    private static boolean canGainXp(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        return player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR;
    }

    private int resolveHarvestAwardTenths(@NotNull Block block, @NotNull Material type, int unitTenths) {
        if (isStackCrop(type)) {
            return unitTenths * countHarvestedStackSegments(block, type);
        }
        if (removePlacedMarkerIfPresent(block)) {
            return 0;
        }
        if (isAgeableCrop(type)) {
            return isFullyMature(block.getBlockData()) ? unitTenths : 0;
        }
        if (type == Material.CAVE_VINES || type == Material.CAVE_VINES_PLANT) {
            BlockData data = block.getBlockData();
            return (data instanceof CaveVines caveVines && caveVines.isBerries()) ? unitTenths : 0;
        }
        return unitTenths;
    }

    private int countHarvestedStackSegments(@NotNull Block start, @NotNull Material type) {
        int count = 0;
        Block cursor = start;
        while (cursor.getType() == type) {
            if (!removePlacedMarkerIfPresent(cursor)) {
                count++;
            }
            cursor = cursor.getRelative(0, 1, 0);
        }
        return count;
    }

    private static boolean isFullyMature(@NotNull BlockData data) {
        return data instanceof Ageable ageable && ageable.getAge() >= ageable.getMaximumAge();
    }

    private static boolean isAgeableCrop(@NotNull Material type) {
        return type == Material.WHEAT
                || type == Material.CARROTS
                || type == Material.POTATOES
                || type == Material.BEETROOTS
                || type == Material.NETHER_WART
                || type == Material.COCOA
                || type == Material.SWEET_BERRY_BUSH;
    }

    private static boolean isStackCrop(@NotNull Material type) {
        return type == Material.SUGAR_CANE
                || type == Material.CACTUS
                || type == Material.BAMBOO;
    }

    private static boolean isTrackedPlacedBlock(@NotNull Material type) {
        return isStackCrop(type)
                || type == Material.PUMPKIN
                || type == Material.MELON
                || type == Material.BROWN_MUSHROOM_BLOCK
                || type == Material.RED_MUSHROOM_BLOCK
                || type == Material.MUSHROOM_STEM;
    }

    private static boolean supportsNonBreakHarvest(@NotNull Material type) {
        return type == Material.SWEET_BERRY_BUSH
                || type == Material.CAVE_VINES
                || type == Material.CAVE_VINES_PLANT;
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
        String raw = pdc.get(PLACED_FARMING_BLOCKS_KEY, PersistentDataType.STRING);
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
            pdc.remove(PLACED_FARMING_BLOCKS_KEY);
            return;
        }
        pdc.set(PLACED_FARMING_BLOCKS_KEY, PersistentDataType.STRING, String.join(";", entries));
    }

    private static @NotNull String localKey(@NotNull Block block) {
        int localX = block.getX() & 15;
        int localZ = block.getZ() & 15;
        return localX + "," + block.getY() + "," + localZ;
    }

    private static @NotNull Map<Material, Integer> buildHarvestXpTenthsByBlock() {
        Map<Material, Integer> map = new EnumMap<>(Material.class);
        map.put(Material.WHEAT, 8);
        map.put(Material.CARROTS, 9);
        map.put(Material.POTATOES, 9);
        map.put(Material.BEETROOTS, 9);
        map.put(Material.NETHER_WART, 12);
        map.put(Material.COCOA, 10);
        map.put(Material.PUMPKIN, 10);
        map.put(Material.MELON, 8);
        map.put(Material.SUGAR_CANE, 4);
        map.put(Material.CACTUS, 3);
        map.put(Material.BAMBOO, 2);
        map.put(Material.SWEET_BERRY_BUSH, 6);
        map.put(Material.CAVE_VINES, 6);
        map.put(Material.CAVE_VINES_PLANT, 6);
        map.put(Material.BROWN_MUSHROOM_BLOCK, 6);
        map.put(Material.RED_MUSHROOM_BLOCK, 6);
        map.put(Material.MUSHROOM_STEM, 6);
        return Map.copyOf(map);
    }

    private static @NotNull Map<EntityType, Integer> buildKillXpTenthsByEntity() {
        Map<EntityType, Integer> map = new EnumMap<>(EntityType.class);
        map.put(EntityType.CHICKEN, 5);
        map.put(EntityType.COW, 8);
        map.put(EntityType.SHEEP, 7);
        map.put(EntityType.PIG, 7);
        map.put(EntityType.RABBIT, 5);
        map.put(EntityType.MOOSHROOM, 10);
        map.put(EntityType.GOAT, 8);
        map.put(EntityType.COD, 3);
        map.put(EntityType.SALMON, 3);
        map.put(EntityType.TROPICAL_FISH, 3);
        map.put(EntityType.PUFFERFISH, 3);
        return Map.copyOf(map);
    }
}
