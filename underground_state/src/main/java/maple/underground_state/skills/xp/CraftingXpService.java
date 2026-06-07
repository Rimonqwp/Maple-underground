package maple.underground_state.skills.xp;

import maple.underground_state.skills.SkillProgressManager;
import maple.underground_state.skills.SkillRewardService;
import maple.underground_state.skills.SkillType;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.StonecutterInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class CraftingXpService implements Listener {

    private static final int SHIFT_BATCH_MAX_UNITS = 32;

    private static final NamespacedKey FIRST_WORKSTATION_KEY =
            new NamespacedKey("underground_state", "crafting_first_workstation");
    private static final NamespacedKey FIRST_IRON_GEAR_KEY =
            new NamespacedKey("underground_state", "crafting_first_iron_gear");
    private static final NamespacedKey FIRST_DIAMOND_GEAR_KEY =
            new NamespacedKey("underground_state", "crafting_first_diamond_gear");
    private static final NamespacedKey FIRST_NETHERITE_GEAR_KEY =
            new NamespacedKey("underground_state", "crafting_first_netherite_gear");
    private static final NamespacedKey FIRST_ADVANCED_BLOCK_KEY =
            new NamespacedKey("underground_state", "crafting_first_advanced_block");

    private static final Set<Material> BASIC_COMPRESSION = EnumSet.of(
            Material.PAPER,
            Material.STICK,
            Material.BREAD,
            Material.BOWL
    );
    private static final Set<Material> BASIC_TOOLS_AND_ITEMS = EnumSet.of(
            Material.TORCH,
            Material.CHEST,
            Material.BARREL,
            Material.CAMPFIRE
    );
    private static final Set<Material> WORKSTATIONS = EnumSet.of(
            Material.FURNACE,
            Material.BLAST_FURNACE,
            Material.SMOKER,
            Material.CRAFTING_TABLE,
            Material.STONECUTTER,
            Material.LOOM,
            Material.GRINDSTONE,
            Material.CARTOGRAPHY_TABLE,
            Material.SMITHING_TABLE,
            Material.FLETCHING_TABLE
    );
    private static final Set<Material> REDSTONE_DEVICES = EnumSet.of(
            Material.PISTON,
            Material.STICKY_PISTON,
            Material.DISPENSER,
            Material.DROPPER,
            Material.HOPPER,
            Material.OBSERVER,
            Material.REDSTONE_LAMP,
            Material.REPEATER,
            Material.COMPARATOR,
            Material.DAYLIGHT_DETECTOR,
            Material.NOTE_BLOCK,
            Material.TARGET
    );
    private static final Set<Material> ADVANCED_FUNCTIONAL_BLOCKS = EnumSet.of(
            Material.BEACON,
            Material.RESPAWN_ANCHOR,
            Material.SHULKER_BOX
    );

    private final SkillProgressManager skillProgressManager;
    private final SkillRewardService skillRewardService;
    private final CombatXpOverlayService xpOverlayService;

    public CraftingXpService(@NotNull SkillProgressManager skillProgressManager,
                             @NotNull SkillRewardService skillRewardService,
                             @NotNull CombatXpOverlayService xpOverlayService) {
        this.skillProgressManager = skillProgressManager;
        this.skillRewardService = skillRewardService;
        this.xpOverlayService = xpOverlayService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !canGainXp(player)) {
            return;
        }

        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) {
            return;
        }

        int perUnitTenths = craftingXpTenthsForMaterial(result.getType());
        if (perUnitTenths <= 0) {
            return;
        }

        int units = craftedUnits(player, event.getInventory(), result, event.isShiftClick());
        if (units <= 0) {
            return;
        }

        int awardTenths = perUnitTenths * units + firstCraftBonusTenths(player, result.getType());
        if (awardTenths > 0) {
            grantCraftingXp(player, awardTenths);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmithItem(SmithItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !canGainXp(player)) {
            return;
        }

        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) {
            return;
        }

        int awardTenths = smithingAwardTenths(result.getType()) + firstCraftBonusTenths(player, result.getType());
        if (awardTenths > 0) {
            grantCraftingXp(player, awardTenths);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStonecutterResultTake(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !canGainXp(player)) {
            return;
        }
        if (!(event.getView().getTopInventory() instanceof StonecutterInventory inventory)) {
            return;
        }
        if (event.getRawSlot() != 1) {
            return;
        }

        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) {
            return;
        }

        ItemStack input = inventory.getInputItem();
        if (input == null || input.getType().isAir()) {
            return;
        }

        int units = stonecutterUnits(player, input, result, event.isShiftClick());
        int awardTenths = units * 2;
        if (awardTenths > 0) {
            grantCraftingXp(player, awardTenths);
        }
    }

    private void grantCraftingXp(@NotNull Player player, int awardTenths) {
        UUID playerId = player.getUniqueId();
        int carriedTenths = skillProgressManager.getCraftingXpRemainderTenths(playerId);
        int totalTenths = carriedTenths + awardTenths;
        long wholeXp = totalTenths / 10L;
        int remainderTenths = totalTenths % 10;

        if (wholeXp > 0L) {
            skillRewardService.addSkillXp(playerId, SkillType.CRAFTING, wholeXp);
        }
        skillProgressManager.setCraftingXpRemainderTenths(playerId, remainderTenths);

        long craftingXpWhole = skillProgressManager.getXp(playerId, SkillType.CRAFTING);
        long displayTotalTenths = craftingXpWhole * 10L + remainderTenths;
        xpOverlayService.showSkillOverlay(player, SkillType.CRAFTING, awardTenths, displayTotalTenths);
    }

    private static int craftedUnits(@NotNull Player player,
                                    @NotNull CraftingInventory inventory,
                                    @NotNull ItemStack result,
                                    boolean shiftClick) {
        int baseUnits = Math.max(1, result.getAmount());
        if (!shiftClick) {
            return baseUnits;
        }

        int crafts = Integer.MAX_VALUE;
        for (ItemStack item : inventory.getMatrix()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            crafts = Math.min(crafts, item.getAmount());
        }
        if (crafts == Integer.MAX_VALUE) {
            crafts = 1;
        }
        int theoreticalUnits = Math.min(SHIFT_BATCH_MAX_UNITS, baseUnits * crafts);
        return Math.min(theoreticalUnits, maxReceivableUnits(player.getInventory(), result));
    }

    private static int stonecutterUnits(@NotNull Player player,
                                        @NotNull ItemStack input,
                                        @NotNull ItemStack result,
                                        boolean shiftClick) {
        int baseUnits = Math.max(1, result.getAmount());
        if (!shiftClick) {
            return baseUnits;
        }

        int theoreticalUnits = Math.min(SHIFT_BATCH_MAX_UNITS, input.getAmount() * baseUnits);
        return Math.min(theoreticalUnits, maxReceivableUnits(player.getInventory(), result));
    }

    private static int maxReceivableUnits(@NotNull PlayerInventory inventory, @NotNull ItemStack result) {
        int capacity = 0;
        int maxStack = Math.max(1, result.getMaxStackSize());
        for (ItemStack content : inventory.getStorageContents()) {
            if (content == null || content.getType().isAir()) {
                capacity += maxStack;
                continue;
            }
            if (content.isSimilar(result)) {
                capacity += Math.max(0, maxStack - content.getAmount());
            }
        }
        return capacity;
    }

    private static int smithingAwardTenths(@NotNull Material resultType) {
        return isNetheriteGear(resultType) ? 60 : 20;
    }

    private static int craftingXpTenthsForMaterial(@NotNull Material material) {
        if (isNetheriteGear(material)) {
            return 60;
        }
        if (isDiamondGear(material)) {
            return 35;
        }
        if (isIronGear(material)) {
            return 15;
        }
        if (isGoldenGear(material)) {
            return 12;
        }
        if (REDSTONE_DEVICES.contains(material)) {
            return 12;
        }
        if (WORKSTATIONS.contains(material)) {
            return 8;
        }
        if (isTransportOrFunctional(material)) {
            return 8;
        }
        if (isBasicToolOrStarterItem(material)) {
            return 4;
        }
        if (isDecorative(material) || isBasicCompressionMaterial(material)) {
            return 1;
        }
        return 0;
    }

    private static boolean isBasicCompressionMaterial(@NotNull Material material) {
        return BASIC_COMPRESSION.contains(material) || material.name().endsWith("_PLANKS");
    }

    private static boolean isBasicToolOrStarterItem(@NotNull Material material) {
        return BASIC_TOOLS_AND_ITEMS.contains(material)
                || isWoodenGear(material)
                || isStoneGear(material);
    }

    private static boolean isTransportOrFunctional(@NotNull Material material) {
        String name = material.name();
        return name.endsWith("_BOAT")
                || name.endsWith("_CHEST_BOAT")
                || name.contains("MINECART")
                || material == Material.RAIL
                || material == Material.POWERED_RAIL
                || material == Material.DETECTOR_RAIL
                || material == Material.ACTIVATOR_RAIL;
    }

    private static boolean isDecorative(@NotNull Material material) {
        String name = material.name();
        return name.endsWith("_STAIRS")
                || name.endsWith("_SLAB")
                || name.endsWith("_WALL")
                || name.endsWith("_FENCE")
                || name.endsWith("_FENCE_GATE")
                || name.endsWith("_DOOR")
                || name.endsWith("_TRAPDOOR")
                || name.endsWith("_PRESSURE_PLATE")
                || name.endsWith("_BUTTON");
    }

    private static boolean isWoodenGear(@NotNull Material material) {
        return isGearWithPrefix(material, "WOODEN_");
    }

    private static boolean isStoneGear(@NotNull Material material) {
        return isGearWithPrefix(material, "STONE_");
    }

    private static boolean isIronGear(@NotNull Material material) {
        return isGearWithPrefix(material, "IRON_");
    }

    private static boolean isGoldenGear(@NotNull Material material) {
        return isGearWithPrefix(material, "GOLDEN_");
    }

    private static boolean isDiamondGear(@NotNull Material material) {
        return isGearWithPrefix(material, "DIAMOND_");
    }

    private static boolean isNetheriteGear(@NotNull Material material) {
        return isGearWithPrefix(material, "NETHERITE_");
    }

    private static boolean isGearWithPrefix(@NotNull Material material, @NotNull String prefix) {
        String name = material.name();
        return name.startsWith(prefix)
                && (name.endsWith("_SWORD")
                || name.endsWith("_PICKAXE")
                || name.endsWith("_AXE")
                || name.endsWith("_SHOVEL")
                || name.endsWith("_HOE")
                || name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS"));
    }

    private static int firstCraftBonusTenths(@NotNull Player player, @NotNull Material resultType) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (WORKSTATIONS.contains(resultType)) {
            return consumeFirstBonus(pdc, FIRST_WORKSTATION_KEY, 10);
        }
        if (isIronGear(resultType)) {
            return consumeFirstBonus(pdc, FIRST_IRON_GEAR_KEY, 20);
        }
        if (isDiamondGear(resultType)) {
            return consumeFirstBonus(pdc, FIRST_DIAMOND_GEAR_KEY, 50);
        }
        if (isNetheriteGear(resultType)) {
            return consumeFirstBonus(pdc, FIRST_NETHERITE_GEAR_KEY, 80);
        }
        if (ADVANCED_FUNCTIONAL_BLOCKS.contains(resultType)) {
            return consumeFirstBonus(pdc, FIRST_ADVANCED_BLOCK_KEY, 60);
        }
        return 0;
    }

    private static int consumeFirstBonus(@NotNull PersistentDataContainer pdc,
                                         @NotNull NamespacedKey key,
                                         int awardTenths) {
        if (pdc.has(key, PersistentDataType.BYTE)) {
            return 0;
        }
        pdc.set(key, PersistentDataType.BYTE, (byte) 1);
        return awardTenths;
    }

    private static boolean canGainXp(@Nullable Player player) {
        return player != null
                && player.isOnline()
                && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR;
    }
}
