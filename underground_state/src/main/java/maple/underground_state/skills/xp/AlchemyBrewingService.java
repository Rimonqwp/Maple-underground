package maple.underground_state.skills.xp;

import maple.underground_state.UndergroundState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BrewingStartEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import maple.underground_state.skills.SkillProgressManager;
import maple.underground_state.skills.SkillRewardService;
import maple.underground_state.skills.SkillType;

public final class AlchemyBrewingService implements Listener {

    private static final Component TITLE = Component.text("酿造台", NamedTextColor.WHITE);
    private static final NamespacedKey BREWING_STAND_ID_KEY =
            new NamespacedKey("underground_state", "alchemy_brewing_stand_id");
    private static final NamespacedKey[] PENDING_XP_KEYS = {
            new NamespacedKey("underground_state", "alchemy_pending_xp_slot_0_tenths"),
            new NamespacedKey("underground_state", "alchemy_pending_xp_slot_1_tenths"),
            new NamespacedKey("underground_state", "alchemy_pending_xp_slot_2_tenths")
    };
    private static final NamespacedKey LEGACY_PENDING_XP_KEY =
            new NamespacedKey("underground_state", "alchemy_pending_xp_tenths");

    private static final int MENU_SIZE = 54;
    private static final int INGREDIENT_SLOT = 13;
    private static final int[] BOTTLE_SLOTS = {38, 40, 42};
    private static final int[] STATUS_SLOTS = {20, 21, 22, 23, 24, 29, 31, 33};
    private static final int[] ORANGE_STATUS_SLOTS = {20, 21, 22, 23, 24};
    private static final int[] BLUE_STATUS_SLOTS = {29, 31, 33};
    private static final int CLOSE_SLOT = 49;
    private static final int[] ANIMATION_SEQUENCE = {22, 21, 20, 29, 22, 31, 22, 23, 24, 33};

    private static final int BREW_TIME_TICKS = 300;
    private static final int HIDDEN_FUEL_LEVEL = 20;
    private static final long SUCCESS_FLASH_MS = 2_000L;

    private static final Set<Material> PRIMARY_EFFECT_INGREDIENTS = EnumSet.of(
            Material.SUGAR,
            Material.RABBIT_FOOT,
            Material.GLISTERING_MELON_SLICE,
            Material.SPIDER_EYE,
            Material.GHAST_TEAR,
            Material.BLAZE_POWDER,
            Material.MAGMA_CREAM,
            Material.GOLDEN_CARROT,
            Material.PUFFERFISH,
            Material.TURTLE_HELMET,
            Material.PHANTOM_MEMBRANE,
            Material.STONE,
            Material.SLIME_BLOCK,
            Material.COBWEB,
            Material.BREEZE_ROD
    );

    private static final Set<Material> VALID_INGREDIENTS = buildValidIngredients();
    private static final Set<Material> VALID_BOTTLES = EnumSet.of(
            Material.GLASS_BOTTLE,
            Material.POTION,
            Material.SPLASH_POTION,
            Material.LINGERING_POTION
    );

    private final UndergroundState plugin;
    private final SkillProgressManager skillProgressManager;
    private final SkillRewardService skillRewardService;
    private final CombatXpOverlayService xpOverlayService;
    private final Map<UUID, OpenBrewingView> openViews = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveBrew> activeBrews = new ConcurrentHashMap<>();
    private final Map<UUID, Long> successFlashUntil = new ConcurrentHashMap<>();
    private final BukkitTask refreshTask;

    public AlchemyBrewingService(@NotNull UndergroundState plugin,
                                 @NotNull SkillProgressManager skillProgressManager,
                                 @NotNull SkillRewardService skillRewardService,
                                 @NotNull CombatXpOverlayService xpOverlayService) {
        this.plugin = plugin;
        this.skillProgressManager = skillProgressManager;
        this.skillRewardService = skillRewardService;
        this.xpOverlayService = xpOverlayService;
        this.refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshOpenViews, 1L, 5L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() == null || !event.getHand().name().equals("HAND")) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.BREWING_STAND) {
            return;
        }

        event.setCancelled(true);
        openBrewingUi(event.getPlayer(), block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrewingStart(BrewingStartEvent event) {
        Block block = event.getBlock();
        BrewingStand stand = getBrewingStand(block);
        if (stand == null) {
            return;
        }

        UUID standId = getOrCreateStandId(stand);
        Material ingredientType = normalizeType(event.getSource());
        if (ingredientType != null) {
            activeBrews.put(standId, new ActiveBrew(ingredientType));
        }

        logAlchemy("BREW_START stand=" + standId
                + " location=" + formatLocation(block.getLocation())
                + " ingredient=" + formatItem(event.getSource())
                + " bottles=" + formatBottleSlots(stand)
                + " trackedIngredient=" + (ingredientType == null ? "null" : ingredientType.name()));

        event.setBrewingTime(BREW_TIME_TICKS);
        event.setRecipeBrewTime(BREW_TIME_TICKS);
        stand.setRecipeBrewTime(BREW_TIME_TICKS);
        stand.setFuelLevel(HIDDEN_FUEL_LEVEL);
        stand.update();
        successFlashUntil.remove(standId);
        refreshStandViews(standId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        BrewingStand stand = getBrewingStand(event.getBlock());
        if (stand == null) {
            return;
        }

        UUID standId = getOrCreateStandId(stand);
        ActiveBrew activeBrew = activeBrews.remove(standId);
        successFlashUntil.put(standId, System.currentTimeMillis() + SUCCESS_FLASH_MS);
        int awardTenths = 0;

        if (activeBrew != null) {
            awardTenths = resolveAlchemyAwardTenths(activeBrew.ingredient(), event.getResults());
        }

        logAlchemy("BREW_FINISH stand=" + standId
                + " location=" + formatLocation(event.getBlock().getLocation())
                + " ingredient=" + (activeBrew == null ? "null" : activeBrew.ingredient().name())
                + " results=" + formatResults(event.getResults())
                + " resolvedTenths=" + awardTenths);

        int finalAwardTenths = awardTenths;
        Bukkit.getScheduler().runTask(plugin, () -> {
            BrewingStand refreshedStand = getBrewingStand(event.getBlock());
            if (refreshedStand == null) {
                logAlchemy("BREW_FINISH_APPLY stand=" + standId + " result=missing_stand");
                return;
            }
            if (finalAwardTenths > 0) {
                addPendingXp(refreshedStand, event.getResults(), finalAwardTenths);
            } else {
                logAlchemy("BREW_FINISH_APPLY stand=" + standId
                        + " result=no_pending_xp_added"
                        + " bottles=" + formatBottleSlots(refreshedStand));
            }
            refreshStandViews(standId);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (event.getSource().getHolder() instanceof BrewingStand
                || event.getDestination().getHolder() instanceof BrewingStand) {
            event.setCancelled(true);
            logAlchemy("HOPPER_BLOCKED sourceHolder="
                    + formatHolder(event.getSource().getHolder())
                    + " destinationHolder=" + formatHolder(event.getDestination().getHolder())
                    + " item=" + formatItem(event.getItem()));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof BrewingStandGuiHolder holder)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }

        BrewingStand stand = getBrewingStand(holder.standLocation());
        if (stand == null) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0) {
            event.setCancelled(true);
            return;
        }

        if (rawSlot < top.getSize()) {
            event.setCancelled(true);
            handleTopInventoryClick(player, rawSlot, event.isRightClick(), event.getCursor(), stand, holder.standId());
            return;
        }

        if (event.isShiftClick()) {
            event.setCancelled(true);
            handleShiftClickFromPlayerInventory(player, event, stand, holder.standId());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof BrewingStandGuiHolder)) {
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof BrewingStandGuiHolder)) {
            return;
        }
        openViews.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.BREWING_STAND) {
            return;
        }

        BrewingStand stand = getBrewingStand(event.getBlock());
        if (stand == null) {
            return;
        }
        UUID standId = getOrCreateStandId(stand);
        closeStandViews(standId);
        activeBrews.remove(standId);
        successFlashUntil.remove(standId);
        clearPendingXp(stand);
    }

    private void openBrewingUi(@NotNull Player player, @NotNull Block block) {
        BrewingStand stand = getBrewingStand(block);
        if (stand == null) {
            return;
        }

        ensureFuelAndIdentity(stand);
        UUID standId = getOrCreateStandId(stand);
        BrewingStandGuiHolder holder = new BrewingStandGuiHolder(player.getUniqueId(), standId, block.getLocation());
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, TITLE);
        holder.setInventory(inventory);
        openViews.put(player.getUniqueId(), new OpenBrewingView(holder.standId()));
        renderInventory(inventory, stand, holder.standId());
        player.openInventory(inventory);
    }

    private void handleTopInventoryClick(@NotNull Player player,
                                         int rawSlot,
                                         boolean rightClick,
                                         @Nullable ItemStack cursor,
                                         @NotNull BrewingStand stand,
                                         @NotNull UUID standId) {
        BrewerInventory standInventory = standInventory(stand);
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (rawSlot == INGREDIENT_SLOT) {
            SlotInteractionResult result = processSlotInteraction(
                    standInventory.getIngredient(),
                    cursor,
                    rightClick,
                    AlchemyBrewingService::isValidIngredientItem,
                    64
            );
            if (!result.changed()) {
                return;
            }
            standInventory.setIngredient(result.slotItem());
            player.setItemOnCursor(result.cursorItem());
            ensureFuelAndIdentity(stand);
            logAlchemy("UI_INGREDIENT_CLICK player=" + player.getName()
                    + " stand=" + standId
                    + " rightClick=" + rightClick
                    + " ingredientNow=" + formatItem(result.slotItem())
                    + " cursorNow=" + formatItem(result.cursorItem()));
            refreshStandViews(standId);
            player.updateInventory();
            return;
        }

        int bottleIndex = toBottleIndex(rawSlot);
        if (bottleIndex >= 0) {
            SlotInteractionResult result = processSlotInteraction(
                    standInventory.getItem(bottleIndex),
                    cursor,
                    rightClick,
                    AlchemyBrewingService::isValidBottleItem,
                    1
            );
            if (!result.changed()) {
                return;
            }
            ItemStack extractedItem = extractedItem(standInventory.getItem(bottleIndex), result.slotItem());
            standInventory.setItem(bottleIndex, result.slotItem());
            player.setItemOnCursor(result.cursorItem());
            ensureFuelAndIdentity(stand);
            logAlchemy("UI_BOTTLE_CLICK player=" + player.getName()
                    + " stand=" + standId
                    + " bottleIndex=" + bottleIndex
                    + " rightClick=" + rightClick
                    + " extracted=" + formatItem(extractedItem)
                    + " slotNow=" + formatItem(result.slotItem())
                    + " cursorNow=" + formatItem(result.cursorItem()));
            maybeGrantPendingXpOnBottleTake(player, stand, bottleIndex, extractedItem);
            refreshStandViews(standId);
            player.updateInventory();
        }
    }

    private void handleShiftClickFromPlayerInventory(@NotNull Player player,
                                                     @NotNull InventoryClickEvent event,
                                                     @NotNull BrewingStand stand,
                                                     @NotNull UUID standId) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        ItemStack remaining = clicked.clone();
        String action;
        if (isValidBottleItem(remaining)) {
            action = "bottle";
            remaining = moveBottleStackIntoStand(stand, remaining);
        } else if (isValidIngredientItem(remaining)) {
            action = "ingredient";
            remaining = moveIngredientStackIntoStand(stand, remaining);
        } else {
            logAlchemy("UI_SHIFT_CLICK_IGNORED player=" + player.getName()
                    + " stand=" + standId
                    + " item=" + formatItem(clicked));
            return;
        }

        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) {
            return;
        }

        if (remaining.getAmount() <= 0 || remaining.getType().isAir()) {
            clickedInventory.setItem(event.getSlot(), null);
        } else {
            clickedInventory.setItem(event.getSlot(), remaining);
        }

        ensureFuelAndIdentity(stand);
        logAlchemy("UI_SHIFT_CLICK player=" + player.getName()
                + " stand=" + standId
                + " action=" + action
                + " clicked=" + formatItem(clicked)
                + " remaining=" + formatItem(remaining)
                + " bottles=" + formatBottleSlots(stand)
                + " ingredient=" + formatItem(standInventory(stand).getIngredient()));
        refreshStandViews(standId);
        player.updateInventory();
    }

    private @NotNull ItemStack moveIngredientStackIntoStand(@NotNull BrewingStand stand, @NotNull ItemStack stack) {
        BrewerInventory standInventory = standInventory(stand);
        ItemStack ingredient = standInventory.getIngredient();
        if (ingredient == null || ingredient.getType().isAir()) {
            standInventory.setIngredient(stack.clone());
            stack.setAmount(0);
            return stack;
        }
        if (!ingredient.isSimilar(stack) || ingredient.getAmount() >= ingredient.getMaxStackSize()) {
            return stack;
        }
        int moved = Math.min(stack.getAmount(), ingredient.getMaxStackSize() - ingredient.getAmount());
        ingredient = ingredient.clone();
        ingredient.setAmount(ingredient.getAmount() + moved);
        standInventory.setIngredient(ingredient);
        stack.setAmount(stack.getAmount() - moved);
        return stack;
    }

    private @NotNull ItemStack moveBottleStackIntoStand(@NotNull BrewingStand stand, @NotNull ItemStack stack) {
        BrewerInventory standInventory = standInventory(stand);
        for (int slot = 0; slot < 3 && stack.getAmount() > 0; slot++) {
            ItemStack current = standInventory.getItem(slot);
            if (current != null && !current.getType().isAir()) {
                continue;
            }
            ItemStack placed = stack.clone();
            placed.setAmount(1);
            standInventory.setItem(slot, placed);
            stack.setAmount(stack.getAmount() - 1);
        }
        return stack;
    }

    private void refreshOpenViews() {
        long now = System.currentTimeMillis();
        successFlashUntil.entrySet().removeIf(entry -> entry.getValue() < now);

        for (Map.Entry<UUID, OpenBrewingView> entry : openViews.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                openViews.remove(entry.getKey());
                continue;
            }

            Inventory top = player.getOpenInventory().getTopInventory();
            if (!(top.getHolder() instanceof BrewingStandGuiHolder holder)) {
                openViews.remove(entry.getKey());
                continue;
            }
            if (!holder.owner().equals(player.getUniqueId())) {
                openViews.remove(entry.getKey());
                continue;
            }

            BrewingStand stand = getBrewingStand(holder.standLocation());
            if (stand == null) {
                player.closeInventory();
                openViews.remove(entry.getKey());
                continue;
            }

            renderInventory(top, stand, holder.standId());
        }
    }

    private void refreshStandViews(@NotNull UUID standId) {
        for (Map.Entry<UUID, OpenBrewingView> entry : openViews.entrySet()) {
            if (!entry.getValue().standId().equals(standId)) {
                continue;
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            Inventory top = player.getOpenInventory().getTopInventory();
            if (!(top.getHolder() instanceof BrewingStandGuiHolder holder)) {
                continue;
            }
            BrewingStand stand = getBrewingStand(holder.standLocation());
            if (stand != null) {
                renderInventory(top, stand, standId);
            }
        }
    }

    private void closeStandViews(@NotNull UUID standId) {
        for (Map.Entry<UUID, OpenBrewingView> entry : openViews.entrySet()) {
            if (!entry.getValue().standId().equals(standId)) {
                continue;
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        }
    }

    private void renderInventory(@NotNull Inventory inventory, @NotNull BrewingStand stand, @NotNull UUID standId) {
        BrewerInventory standInventory = standInventory(stand);
        ItemStack filler = createPane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < MENU_SIZE; slot++) {
            inventory.setItem(slot, filler.clone());
        }

        inventory.setItem(INGREDIENT_SLOT, cloneOrNull(standInventory.getIngredient()));
        for (int index = 0; index < BOTTLE_SLOTS.length; index++) {
            inventory.setItem(BOTTLE_SLOTS[index], cloneOrNull(standInventory.getItem(index)));
        }
        inventory.setItem(CLOSE_SLOT, createBarrier());

        renderStatusPanes(inventory, stand, standId);
    }

    private void renderStatusPanes(@NotNull Inventory inventory, @NotNull BrewingStand stand, @NotNull UUID standId) {
        long now = System.currentTimeMillis();
        boolean success = successFlashUntil.getOrDefault(standId, 0L) >= now;
        int brewingTime = Math.max(0, stand.getBrewingTime());
        int recipeTime = Math.max(1, stand.getRecipeBrewTime());
        boolean brewing = brewingTime > 0;

        if (success) {
            ItemStack successPane = createStatusPane(
                    Material.LIME_STAINED_GLASS_PANE,
                    "酿造完成",
                    Component.text("当前药水已经酿造完成。", NamedTextColor.GRAY)
            );
            for (int slot : STATUS_SLOTS) {
                inventory.setItem(slot, successPane.clone());
            }
            return;
        }

        if (brewing) {
            double remainingSeconds = brewingTime / 20.0D;
            int frame = (int) ((System.currentTimeMillis() / 200L) % ANIMATION_SEQUENCE.length);
            int activeSlot = ANIMATION_SEQUENCE[frame];
            for (int slot : ORANGE_STATUS_SLOTS) {
                inventory.setItem(slot, createStatusPane(
                        slot == activeSlot ? Material.YELLOW_STAINED_GLASS_PANE : Material.ORANGE_STAINED_GLASS_PANE,
                        "正在酿造",
                        Component.text("剩余 " + formatSeconds(remainingSeconds) + " 秒完成。", NamedTextColor.GRAY),
                        Component.text("进度 " + formatPercent(recipeTime, brewingTime), NamedTextColor.YELLOW)
                ));
            }
            for (int slot : BLUE_STATUS_SLOTS) {
                inventory.setItem(slot, createStatusPane(
                        slot == activeSlot ? Material.YELLOW_STAINED_GLASS_PANE : Material.ORANGE_STAINED_GLASS_PANE,
                        "正在酿造",
                        Component.text("剩余 " + formatSeconds(remainingSeconds) + " 秒完成。", NamedTextColor.GRAY),
                        Component.text("进度 " + formatPercent(recipeTime, brewingTime), NamedTextColor.YELLOW)
                ));
            }
            return;
        }

        for (int slot : ORANGE_STATUS_SLOTS) {
            inventory.setItem(slot, createStatusPane(
                    Material.ORANGE_STAINED_GLASS_PANE,
                    "等待酿造",
                    Component.text("放入材料后会开始酿造。", NamedTextColor.GRAY)
            ));
        }
        for (int slot : BLUE_STATUS_SLOTS) {
            inventory.setItem(slot, createStatusPane(
                    Material.ORANGE_STAINED_GLASS_PANE,
                    "等待酿造",
                    Component.text("放入材料后会开始酿造。", NamedTextColor.GRAY)
            ));
        }
    }

    private void ensureFuelAndIdentity(@NotNull BrewingStand stand) {
        PersistentDataContainer pdc = ((TileState) stand).getPersistentDataContainer();
        if (!pdc.has(BREWING_STAND_ID_KEY, PersistentDataType.STRING)) {
            pdc.set(BREWING_STAND_ID_KEY, PersistentDataType.STRING, UUID.randomUUID().toString());
        }
        if (stand.getFuelLevel() < HIDDEN_FUEL_LEVEL) {
            stand.setFuelLevel(HIDDEN_FUEL_LEVEL);
        }
        stand.update();
    }

    private @NotNull UUID getOrCreateStandId(@NotNull BrewingStand stand) {
        PersistentDataContainer pdc = ((TileState) stand).getPersistentDataContainer();
        String raw = pdc.get(BREWING_STAND_ID_KEY, PersistentDataType.STRING);
        if (raw != null && !raw.isBlank()) {
            try {
                return UUID.fromString(raw);
            } catch (IllegalArgumentException ignored) {
            }
        }

        UUID standId = UUID.randomUUID();
        pdc.set(BREWING_STAND_ID_KEY, PersistentDataType.STRING, standId.toString());
        stand.update();
        return standId;
    }

    private @Nullable BrewingStand getBrewingStand(@NotNull Location location) {
        if (location.getWorld() == null) {
            return null;
        }
        return getBrewingStand(location.getBlock());
    }

    private @Nullable BrewingStand getBrewingStand(@NotNull Block block) {
        if (block.getType() != Material.BREWING_STAND) {
            return null;
        }
        if (!(block.getState() instanceof BrewingStand stand)) {
            return null;
        }
        return stand;
    }

    private void grantAlchemyXp(@NotNull Player player, int awardTenths) {
        UUID playerId = player.getUniqueId();
        int carriedTenths = skillProgressManager.getAlchemyXpRemainderTenths(playerId);
        int totalTenths = carriedTenths + awardTenths;
        long wholeXp = totalTenths / 10L;
        int remainderTenths = totalTenths % 10;

        if (wholeXp > 0L) {
            skillRewardService.addSkillXp(playerId, SkillType.ALCHEMY, wholeXp);
        }
        skillProgressManager.setAlchemyXpRemainderTenths(playerId, remainderTenths);

        long alchemyXpWhole = skillProgressManager.getXp(playerId, SkillType.ALCHEMY);
        long displayTotalTenths = alchemyXpWhole * 10L + remainderTenths;
        logAlchemy("XP_GRANTED player=" + player.getName()
                + " awardTenths=" + awardTenths
                + " carriedTenths=" + carriedTenths
                + " wholeXpAdded=" + wholeXp
                + " remainderTenths=" + remainderTenths
                + " totalWholeXp=" + alchemyXpWhole
                + " displayTotalTenths=" + displayTotalTenths);
        xpOverlayService.showSkillOverlay(player, SkillType.ALCHEMY, awardTenths, displayTotalTenths);
    }

    private void maybeGrantPendingXpOnBottleTake(@NotNull Player player,
                                                 @NotNull BrewingStand stand,
                                                 int bottleIndex,
                                                 @Nullable ItemStack extractedItem) {
        UUID standId = getOrCreateStandId(stand);
        if (extractedItem == null || extractedItem.getType().isAir()) {
            logAlchemy("XP_TAKE_SKIPPED player=" + player.getName()
                    + " stand=" + standId
                    + " slot=" + bottleIndex
                    + " reason=no_extracted_item");
            return;
        }
        if (!VALID_BOTTLES.contains(extractedItem.getType())) {
            logAlchemy("XP_TAKE_SKIPPED player=" + player.getName()
                    + " stand=" + standId
                    + " slot=" + bottleIndex
                    + " reason=not_bottle"
                    + " extracted=" + formatItem(extractedItem));
            return;
        }
        if (!canGainXp(player)) {
            logAlchemy("XP_TAKE_SKIPPED player=" + player.getName()
                    + " stand=" + standId
                    + " slot=" + bottleIndex
                    + " reason=player_cannot_gain_xp"
                    + " online=" + player.isOnline()
                    + " gameMode=" + player.getGameMode()
                    + " extracted=" + formatItem(extractedItem));
            return;
        }

        int awardTenths = takePendingXp(stand, bottleIndex);
        if (awardTenths <= 0) {
            logAlchemy("XP_TAKE_SKIPPED player=" + player.getName()
                    + " stand=" + standId
                    + " slot=" + bottleIndex
                    + " reason=no_pending_xp"
                    + " extracted=" + formatItem(extractedItem));
            return;
        }
        logAlchemy("XP_TAKE_READY player=" + player.getName()
                + " stand=" + standId
                + " slot=" + bottleIndex
                + " extracted=" + formatItem(extractedItem)
                + " awardTenths=" + awardTenths);
        grantAlchemyXp(player, awardTenths);
    }

    private static @Nullable ItemStack extractedItem(@Nullable ItemStack previousSlotItem,
                                                     @Nullable ItemStack nextSlotItem) {
        if (previousSlotItem == null || previousSlotItem.getType().isAir()) {
            return null;
        }
        if (nextSlotItem == null || nextSlotItem.getType().isAir()) {
            return previousSlotItem.clone();
        }
        if (!previousSlotItem.isSimilar(nextSlotItem) || nextSlotItem.getAmount() < previousSlotItem.getAmount()) {
            return previousSlotItem.clone();
        }
        return null;
    }

    private static boolean canGainXp(@Nullable Player player) {
        return player != null
                && player.isOnline()
                && player.getGameMode() != org.bukkit.GameMode.CREATIVE
                && player.getGameMode() != org.bukkit.GameMode.SPECTATOR;
    }

    private void addPendingXp(@NotNull BrewingStand stand,
                              @NotNull java.util.List<ItemStack> results,
                              int awardTenthsPerBottle) {
        if (awardTenthsPerBottle <= 0) {
            logAlchemy("PENDING_ADD_SKIPPED stand=" + getOrCreateStandId(stand)
                    + " reason=non_positive_award"
                    + " awardTenthsPerBottle=" + awardTenthsPerBottle);
            return;
        }
        TileState state = (TileState) stand;
        PersistentDataContainer pdc = state.getPersistentDataContainer();
        migrateLegacyPendingXp(pdc);
        StringBuilder slots = new StringBuilder();
        for (int slot = 0; slot < Math.min(3, results.size()); slot++) {
            ItemStack result = results.get(slot);
            if (!isValidBottleItem(result)) {
                continue;
            }
            int current = pdc.getOrDefault(PENDING_XP_KEYS[slot], PersistentDataType.INTEGER, 0);
            int updated = current + awardTenthsPerBottle;
            pdc.set(PENDING_XP_KEYS[slot], PersistentDataType.INTEGER, updated);
            if (slots.length() > 0) {
                slots.append(", ");
            }
            slots.append(slot).append(":").append(current).append("->").append(updated);
        }
        stand.update();
        logAlchemy("PENDING_ADD stand=" + getOrCreateStandId(stand)
                + " addedTenthsPerBottle=" + awardTenthsPerBottle
                + " slotUpdates={" + slots + "}"
                + " bottles=" + formatBottleSlots(stand));
    }

    private int takePendingXp(@NotNull BrewingStand stand, int bottleIndex) {
        TileState state = (TileState) stand;
        PersistentDataContainer pdc = state.getPersistentDataContainer();
        migrateLegacyPendingXp(pdc);
        if (bottleIndex < 0 || bottleIndex >= PENDING_XP_KEYS.length) {
            logAlchemy("PENDING_TAKE stand=" + getOrCreateStandId(stand)
                    + " slot=" + bottleIndex
                    + " pendingTenths=0"
                    + " reason=invalid_slot");
            return 0;
        }
        int pending = pdc.getOrDefault(PENDING_XP_KEYS[bottleIndex], PersistentDataType.INTEGER, 0);
        if (pending <= 0) {
            logAlchemy("PENDING_TAKE stand=" + getOrCreateStandId(stand)
                    + " slot=" + bottleIndex
                    + " pendingTenths=0");
            return 0;
        }
        pdc.remove(PENDING_XP_KEYS[bottleIndex]);
        stand.update();
        logAlchemy("PENDING_TAKE stand=" + getOrCreateStandId(stand)
                + " slot=" + bottleIndex
                + " takenTenths=" + pending
                + " slotPendingAfterClear=" + pdc.getOrDefault(PENDING_XP_KEYS[bottleIndex], PersistentDataType.INTEGER, 0)
                + " bottles=" + formatBottleSlots(stand));
        return pending;
    }

    private void clearPendingXp(@NotNull BrewingStand stand) {
        TileState state = (TileState) stand;
        PersistentDataContainer pdc = state.getPersistentDataContainer();
        boolean changed = false;
        if (pdc.has(LEGACY_PENDING_XP_KEY, PersistentDataType.INTEGER)) {
            pdc.remove(LEGACY_PENDING_XP_KEY);
            changed = true;
        }
        for (NamespacedKey key : PENDING_XP_KEYS) {
            if (pdc.has(key, PersistentDataType.INTEGER)) {
                pdc.remove(key);
                changed = true;
            }
        }
        if (!changed) {
            return;
        }
        stand.update();
        logAlchemy("PENDING_CLEAR stand=" + getOrCreateStandId(stand));
    }

    private static int resolveAlchemyAwardTenths(@NotNull Material ingredient, @NotNull java.util.List<ItemStack> results) {
        int awardTenths = switch (ingredient) {
            case NETHER_WART -> 10;
            case REDSTONE -> 8;
            case GLOWSTONE_DUST -> 10;
            case FERMENTED_SPIDER_EYE -> 10;
            case GUNPOWDER -> 10;
            case DRAGON_BREATH -> 15;
            default -> PRIMARY_EFFECT_INGREDIENTS.contains(ingredient) ? 20 : 0;
        };

        int bonusTenths = 0;
        for (ItemStack result : results) {
            PotionType potionType = potionTypeOf(result);
            if (potionType == null) {
                continue;
            }
            bonusTenths = Math.max(bonusTenths, bonusForPotion(potionType));
        }
        return awardTenths + bonusTenths;
    }

    private static int bonusForPotion(@NotNull PotionType potionType) {
        return switch (potionType) {
            case HEALING, STRONG_HEALING,
                    REGENERATION, LONG_REGENERATION, STRONG_REGENERATION,
                    STRENGTH, LONG_STRENGTH, STRONG_STRENGTH,
                    SWIFTNESS, LONG_SWIFTNESS, STRONG_SWIFTNESS -> 5;
            case SLOW_FALLING, LONG_SLOW_FALLING,
                    WATER_BREATHING, LONG_WATER_BREATHING,
                    NIGHT_VISION, LONG_NIGHT_VISION -> 3;
            case POISON, LONG_POISON, STRONG_POISON,
                    HARMING, STRONG_HARMING,
                    WEAKNESS, LONG_WEAKNESS -> 4;
            default -> 0;
        };
    }

    private static @Nullable PotionType potionTypeOf(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir() || !(item.getItemMeta() instanceof PotionMeta potionMeta)) {
            return null;
        }
        return potionMeta.getBasePotionType();
    }

    private static boolean isValidIngredientItem(@Nullable ItemStack item) {
        Material type = normalizeType(item);
        return type != null && VALID_INGREDIENTS.contains(type);
    }

    private static boolean isValidBottleItem(@Nullable ItemStack item) {
        Material type = normalizeType(item);
        return type != null && VALID_BOTTLES.contains(type);
    }

    private static @Nullable Material normalizeType(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        return item.getType();
    }

    private static void migrateLegacyPendingXp(@NotNull PersistentDataContainer pdc) {
        if (!pdc.has(LEGACY_PENDING_XP_KEY, PersistentDataType.INTEGER)) {
            return;
        }
        int legacy = pdc.getOrDefault(LEGACY_PENDING_XP_KEY, PersistentDataType.INTEGER, 0);
        pdc.remove(LEGACY_PENDING_XP_KEY);
        if (legacy <= 0) {
            return;
        }
        int occupiedSlots = 0;
        for (NamespacedKey key : PENDING_XP_KEYS) {
            if (pdc.getOrDefault(key, PersistentDataType.INTEGER, 0) > 0) {
                occupiedSlots++;
            }
        }
        if (occupiedSlots <= 0) {
            pdc.set(PENDING_XP_KEYS[0], PersistentDataType.INTEGER, legacy);
            return;
        }
        int slotShare = legacy / occupiedSlots;
        int remainder = legacy % occupiedSlots;
        for (NamespacedKey key : PENDING_XP_KEYS) {
            int current = pdc.getOrDefault(key, PersistentDataType.INTEGER, 0);
            if (current <= 0) {
                continue;
            }
            int extra = slotShare + (remainder > 0 ? 1 : 0);
            if (remainder > 0) {
                remainder--;
            }
            pdc.set(key, PersistentDataType.INTEGER, current + extra);
        }
    }

    private void logAlchemy(@NotNull String message) {
        plugin.getLogger().info("[AlchemyXP] " + message);
    }

    private static @NotNull String formatLocation(@NotNull Location location) {
        String world = location.getWorld() == null ? "null" : location.getWorld().getName();
        return world + ":" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private static @NotNull String formatItem(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "AIR";
        }
        PotionType potionType = potionTypeOf(item);
        if (potionType != null) {
            return item.getType().name() + "x" + item.getAmount() + "[" + potionType.name() + "]";
        }
        return item.getType().name() + "x" + item.getAmount();
    }

    private static @NotNull String formatResults(@NotNull java.util.List<ItemStack> results) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < results.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(i).append("=").append(formatItem(results.get(i)));
        }
        builder.append("]");
        return builder.toString();
    }

    private static @NotNull String formatBottleSlots(@NotNull BrewingStand stand) {
        BrewerInventory inventory = standInventory(stand);
        return "[0=" + formatItem(inventory.getItem(0))
                + ", 1=" + formatItem(inventory.getItem(1))
                + ", 2=" + formatItem(inventory.getItem(2)) + "]";
    }

    private static @NotNull String formatHolder(@Nullable InventoryHolder holder) {
        if (holder == null) {
            return "null";
        }
        return holder.getClass().getSimpleName();
    }

    private static @NotNull BrewerInventory standInventory(@NotNull BrewingStand stand) {
        return stand.getSnapshotInventory();
    }

    private static int toBottleIndex(int rawSlot) {
        for (int index = 0; index < BOTTLE_SLOTS.length; index++) {
            if (BOTTLE_SLOTS[index] == rawSlot) {
                return index;
            }
        }
        return -1;
    }

    private static @NotNull SlotInteractionResult processSlotInteraction(@Nullable ItemStack slotItem,
                                                                         @Nullable ItemStack cursorItem,
                                                                         boolean rightClick,
                                                                         @NotNull java.util.function.Predicate<ItemStack> validator,
                                                                         int maxStackSize) {
        ItemStack slot = cloneOrNull(slotItem);
        ItemStack cursor = cloneOrNull(cursorItem);

        if (cursor == null) {
            if (slot == null) {
                return SlotInteractionResult.unchanged(slotItem, cursorItem);
            }
            if (rightClick && slot.getAmount() > 1) {
                int pickup = (slot.getAmount() + 1) / 2;
                ItemStack newCursor = slot.clone();
                newCursor.setAmount(pickup);
                slot.setAmount(slot.getAmount() - pickup);
                return SlotInteractionResult.changed(slot, newCursor);
            }
            return SlotInteractionResult.changed(null, slot);
        }

        if (slot == null) {
            if (!validator.test(cursor)) {
                return SlotInteractionResult.unchanged(slotItem, cursorItem);
            }
            int moved = rightClick ? 1 : Math.min(maxStackSize, cursor.getAmount());
            ItemStack newSlot = cursor.clone();
            newSlot.setAmount(moved);
            cursor.setAmount(cursor.getAmount() - moved);
            if (cursor.getAmount() <= 0) {
                cursor = null;
            }
            return SlotInteractionResult.changed(newSlot, cursor);
        }

        if (slot.isSimilar(cursor) && validator.test(cursor) && slot.getAmount() < maxStackSize) {
            int moved = rightClick ? 1 : Math.min(maxStackSize - slot.getAmount(), cursor.getAmount());
            if (moved <= 0) {
                return SlotInteractionResult.unchanged(slotItem, cursorItem);
            }
            slot.setAmount(slot.getAmount() + moved);
            cursor.setAmount(cursor.getAmount() - moved);
            if (cursor.getAmount() <= 0) {
                cursor = null;
            }
            return SlotInteractionResult.changed(slot, cursor);
        }

        if (!validator.test(cursor)) {
            return SlotInteractionResult.unchanged(slotItem, cursorItem);
        }
        if (maxStackSize == 1 && cursor.getAmount() != 1) {
            return SlotInteractionResult.unchanged(slotItem, cursorItem);
        }
        return SlotInteractionResult.changed(cursor, slot);
    }

    private static @Nullable ItemStack cloneOrNull(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        return item.clone();
    }

    private static @NotNull ItemStack createBarrier() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("关闭", NamedTextColor.RED));
            meta.lore(java.util.List.of(Component.text("点击关闭酿造台界面。", NamedTextColor.GRAY)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static @NotNull ItemStack createPane(@NotNull Material material, @NotNull String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.WHITE));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static @NotNull ItemStack createStatusPane(@NotNull Material material,
                                                       @NotNull String name,
                                                       @NotNull Component... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.GOLD));
            meta.lore(Arrays.asList(loreLines));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static @NotNull String formatSeconds(double seconds) {
        return String.format(Locale.US, "%.1f", seconds);
    }

    private static @NotNull String formatPercent(int recipeTime, int brewingTime) {
        double percent = recipeTime <= 0 ? 100.0D : ((recipeTime - brewingTime) * 100.0D / recipeTime);
        return String.format(Locale.US, "%.0f%%", Math.max(0.0D, Math.min(100.0D, percent)));
    }

    private static @NotNull Set<Material> buildValidIngredients() {
        Set<Material> materials = EnumSet.of(
                Material.NETHER_WART,
                Material.REDSTONE,
                Material.GLOWSTONE_DUST,
                Material.FERMENTED_SPIDER_EYE,
                Material.GUNPOWDER,
                Material.DRAGON_BREATH
        );
        materials.addAll(PRIMARY_EFFECT_INGREDIENTS);
        return Set.copyOf(materials);
    }

    private record ActiveBrew(@NotNull Material ingredient) {
    }

    private record OpenBrewingView(@NotNull UUID standId) {
    }

    private record SlotInteractionResult(@Nullable ItemStack slotItem,
                                         @Nullable ItemStack cursorItem,
                                         boolean changed) {

        private static @NotNull SlotInteractionResult changed(@Nullable ItemStack slotItem, @Nullable ItemStack cursorItem) {
            return new SlotInteractionResult(slotItem, cursorItem, true);
        }

        private static @NotNull SlotInteractionResult unchanged(@Nullable ItemStack slotItem, @Nullable ItemStack cursorItem) {
            return new SlotInteractionResult(cloneOrNull(slotItem), cloneOrNull(cursorItem), false);
        }
    }

    private static final class BrewingStandGuiHolder implements InventoryHolder {

        private final UUID owner;
        private final UUID standId;
        private final Location standLocation;
        private Inventory inventory;

        private BrewingStandGuiHolder(@NotNull UUID owner, @NotNull UUID standId, @NotNull Location standLocation) {
            this.owner = owner;
            this.standId = standId;
            this.standLocation = standLocation.clone();
        }

        public @NotNull UUID owner() {
            return owner;
        }

        public @NotNull UUID standId() {
            return standId;
        }

        public @NotNull Location standLocation() {
            return standLocation.clone();
        }

        public void setInventory(@NotNull Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            if (inventory == null) {
                throw new IllegalStateException("inventory not set");
            }
            return inventory;
        }
    }
}
