package maple.underground_state.gui;

import maple.underground_state.UndergroundState;
import maple.underground_state.backpack.BackpackBagItems;
import maple.underground_state.backpack.BackpackDataDB;
import maple.underground_state.backpack.BackpackDataDB.BagSlot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public final class BackpackInfoGui {

    public static final String PAGE_ID = "backpack";
    public static final String STORAGE_PAGE_ID = "backpack_storage";

    private static final int BAG_STORAGE_BASE = -1000;
    private static final String PERMISSION_PREFIX = "underground_state.backpack.";
    private static final String[] PERMISSION_ALIASES = {
            PERMISSION_PREFIX,
            "underground_state.enderchest.",
            "underground_state.ender_chest.",
            "underground_state.bag."
    };
    private static final int MAX_PERMISSION_ROWS = 270;
    private static final int MAX_STORAGE_ROWS_PER_BOX = 5;
    private static final int BOXES_PER_OVERVIEW_PAGE = 9;
    private static final int OVERVIEW_SIZE = 54;
    private static final int OVERVIEW_BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 49;
    private static final int OVERVIEW_PREVIOUS_SLOT = 48;
    private static final int OVERVIEW_NEXT_SLOT = 50;

    private static final int BACK_OFFSET = 0;
    private static final int CLOSE_OFFSET = 4;
    private static final int FIRST_OFFSET = 5;
    private static final int PREVIOUS_OFFSET = 6;
    private static final int NEXT_OFFSET = 7;
    private static final int LAST_OFFSET = 8;

    private static final int[] OVERVIEW_HEAD_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17};
    private static final int[] OVERVIEW_BLACK_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            45, 46, 47, 48, 50, 51, 52, 53
    };
    private static final int[] OVERVIEW_BAG_SLOTS = {
            27, 28, 29, 30, 31, 32, 33, 34, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44
    };

    private static final String BOX_HEAD_TEXTURE =
            "http://textures.minecraft.net/texture/b12c0a39cf46a72c18fba307b815462d19974feee7a078643569decbf5b7";
    private static final String FIRST_TEXTURE =
            "http://textures.minecraft.net/texture/49b2bee39b6ef47e182d6f1dca9dea842fcd68bda9bacc6a6d66a8dcdf3ec";
    private static final String LAST_TEXTURE =
            "http://textures.minecraft.net/texture/141ff6bc67a481232d2e669e43c4f087f9d2306665b4f829fb86892d13b70ca";
    private static final String PREVIOUS_TEXTURE =
            "http://textures.minecraft.net/texture/f5347423ee55daa7923668fca8581985ff5389a45435321efad537af23d";
    private static final String NEXT_TEXTURE =
            "http://textures.minecraft.net/texture/4ef356ad2aa7b1678aecb88290e5fa5a3427e5e456ff42fb515690c67517b8";

    private static final Component TITLE = Component.text("背包", NamedTextColor.WHITE);
    private static final TextColor NAME_RGB = TextColor.color(0x63E6A6);
    private static final TextColor ACTION_RGB = TextColor.color(0xFFE36E);

    private BackpackInfoGui() {
    }

    public static void open(@NotNull Player player) {
        open(player, 0);
    }

    public static void open(@NotNull Player player, int page) {
        int totalRows = permissionRows(player);
        int totalBoxes = totalBoxes(totalRows);
        int totalPages = Math.max(1, (totalBoxes + BOXES_PER_OVERVIEW_PAGE - 1) / BOXES_PER_OVERVIEW_PAGE);
        int safePage = Math.max(0, Math.min(page, totalPages - 1));

        InfoGuiHolder holder = new InfoGuiHolder(player.getUniqueId(), PAGE_ID, safePage);
        Inventory inventory = Bukkit.createInventory(holder, OVERVIEW_SIZE, TITLE);
        holder.setInventory(inventory);
        fillOverview(inventory);

        if (totalBoxes == 0) {
            inventory.setItem(13, InfoGuiItems.createNamedItem(
                    Material.BARRIER,
                    nameText("没有可用背包空间"),
                    List.of(
                            bodyText("需要权限 " + PERMISSION_PREFIX + "1 或更高。"),
                            bodyText("权限数字代表总可用行数，每 5 行会拆成一个末影箱。")
                    )
            ));
        } else {
            int firstBox = safePage * BOXES_PER_OVERVIEW_PAGE;
            for (int index = 0; index < OVERVIEW_HEAD_SLOTS.length; index++) {
                int boxIndex = firstBox + index;
                if (boxIndex >= totalBoxes) {
                    break;
                }
                inventory.setItem(OVERVIEW_HEAD_SLOTS[index], createBoxEntry(boxIndex, totalRows));
            }
        }
        fillPlacedBags(player, inventory);

        inventory.setItem(OVERVIEW_BACK_SLOT, InfoGuiItems.createNamedItem(
                Material.ARROW,
                nameText("上一页"),
                List.of(bodyText("返回主页。"))
        ));
        if (safePage > 0) {
            inventory.setItem(OVERVIEW_PREVIOUS_SLOT, InfoGuiItems.createNamedItem(
                    Material.ARROW,
                    nameText("上一页"),
                    List.of(bodyText("查看前面的末影箱入口。"))
            ));
        }
        if (safePage + 1 < totalPages) {
            inventory.setItem(OVERVIEW_NEXT_SLOT, InfoGuiItems.createNamedItem(
                    Material.ARROW,
                    nameText("下一页"),
                    List.of(bodyText("查看更多末影箱入口。"))
            ));
        }
        inventory.setItem(CLOSE_SLOT, closeItem());
        player.openInventory(inventory);
    }

    public static void openStorage(@NotNull Player player, int boxIndex) {
        int totalRows = permissionRows(player);
        int totalBoxes = totalBoxes(totalRows);
        if (boxIndex < 0 || boxIndex >= totalBoxes) {
            open(player);
            return;
        }

        int storageRows = storageRows(totalRows, boxIndex);
        int storageSlots = storageRows * 9;
        int inventorySize = storageSlots + 9;
        InfoGuiHolder holder = new InfoGuiHolder(player.getUniqueId(), STORAGE_PAGE_ID, boxIndex);
        Inventory inventory = Bukkit.createInventory(holder, inventorySize,
                Component.text("背包 - 末影箱 #" + (boxIndex + 1), NamedTextColor.WHITE));
        holder.setInventory(inventory);

        BackpackDataDB db = backpackDataDB();
        ItemStack[] items = db == null ? new ItemStack[storageSlots] : db.load(player.getUniqueId(), boxIndex, storageSlots);
        for (int slot = 0; slot < storageSlots; slot++) {
            inventory.setItem(slot, cloneOrNull(items[slot]));
        }
        fillActionBar(inventory, boxIndex, totalBoxes, storageRows);
        player.openInventory(inventory);
    }

    public static void openBagStorage(@NotNull Player player, int overviewSlot, int rows) {
        if (!isBagOverviewSlot(overviewSlot)) {
            open(player);
            return;
        }
        int storageRows = Math.max(BackpackBagItems.MIN_ROWS, Math.min(BackpackBagItems.MAX_ROWS, rows));
        int storageSlots = storageRows * 9;
        int storageKey = bagStorageKey(overviewSlot);
        InfoGuiHolder holder = new InfoGuiHolder(player.getUniqueId(), STORAGE_PAGE_ID, storageKey);
        Inventory inventory = Bukkit.createInventory(holder, storageSlots + 9,
                Component.text(BackpackBagItems.sizeName(storageRows) + " #" + overviewSlot, NamedTextColor.WHITE));
        holder.setInventory(inventory);

        BackpackDataDB db = backpackDataDB();
        ItemStack[] items = db == null ? new ItemStack[storageSlots] : db.load(player.getUniqueId(), storageKey, storageSlots);
        for (int slot = 0; slot < storageSlots; slot++) {
            inventory.setItem(slot, cloneOrNull(items[slot]));
        }
        fillActionBar(inventory, storageKey, 1, storageRows);
        player.openInventory(inventory);
    }

    public static void handleOverviewClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        int rawSlot = event.getRawSlot();
        int totalRows = permissionRows(player);
        int totalBoxes = totalBoxes(totalRows);
        int page = currentPage(player, PAGE_ID);
        if (rawSlot == OVERVIEW_BACK_SLOT) {
            MainInfoGui.open(player);
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (rawSlot == OVERVIEW_PREVIOUS_SLOT && page > 0) {
            open(player, page - 1);
            return;
        }
        if (rawSlot == OVERVIEW_NEXT_SLOT && (page + 1) * BOXES_PER_OVERVIEW_PAGE < totalBoxes) {
            open(player, page + 1);
            return;
        }
        if (isBagOverviewSlot(rawSlot)) {
            handleBagOverviewClick(event, player, rawSlot);
            return;
        }

        int index = headSlotIndex(rawSlot);
        if (index < 0) {
            return;
        }
        int boxIndex = page * BOXES_PER_OVERVIEW_PAGE + index;
        if (boxIndex < totalBoxes) {
            openStorage(player, boxIndex);
        }
    }

    public static void handleOverviewBottomClick(@NotNull InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (isBlockedBagQuickClick(event) || !isEmpty(event.getCursor())) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        int rows = BackpackBagItems.rows(clicked);
        if (rows <= 0) {
            return;
        }

        BackpackDataDB db = backpackDataDB();
        if (db == null) {
            player.sendMessage(bodyText("[背包] 数据库暂时不可用，无法使用小型袋子。"));
            return;
        }

        int emptySlot = firstEmptyBagOverviewSlot(db, player);
        if (emptySlot < 0) {
            player.sendMessage(bodyText("[背包] 没有空的小型袋子槽位。"));
            return;
        }
        if (!db.saveBagSlot(player.getUniqueId(), emptySlot, rows)) {
            player.sendMessage(bodyText("[背包] 小型袋子保存失败，请稍后再试。"));
            return;
        }

        consumeClickedItem(event, clicked);
        open(player, currentPage(player, PAGE_ID));
    }

    public static void handleStorageClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        Inventory top = event.getView().getTopInventory();
        int storageSlots = storageSlots(top);
        int rawSlot = event.getRawSlot();

        if (rawSlot < 0) {
            event.setCancelled(true);
            return;
        }
        if (rawSlot < storageSlots) {
            scheduleSave(player);
            return;
        }
        if (rawSlot < top.getSize()) {
            event.setCancelled(true);
            handleActionClick(player, rawSlot - storageSlots);
            return;
        }

        if (event.getClickedInventory() != null
                && event.getClickedInventory().equals(player.getInventory())
                && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            scheduleSave(player);
            return;
        }
        if (shouldCancelBottomClick(event)) {
            event.setCancelled(true);
        }
    }

    public static void handleStorageDrag(@NotNull InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        Inventory top = event.getView().getTopInventory();
        int storageSlots = storageSlots(top);
        boolean touchesTop = false;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= top.getSize()) {
                continue;
            }
            touchesTop = true;
            if (rawSlot >= storageSlots) {
                event.setCancelled(true);
                return;
            }
        }
        if (touchesTop) {
            scheduleSave(player);
        }
    }

    public static void handleStorageClose(@NotNull InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            saveOpenStorage(player, event.getInventory());
        }
    }

    private static void handleActionClick(@NotNull Player player, int actionOffset) {
        int storageKey = currentPage(player, STORAGE_PAGE_ID);
        if (isBagStorageKey(storageKey)) {
            if (actionOffset == BACK_OFFSET) {
                open(player);
                return;
            }
            if (actionOffset == CLOSE_OFFSET) {
                player.closeInventory();
            }
            return;
        }

        int totalRows = permissionRows(player);
        int totalBoxes = totalBoxes(totalRows);
        int currentBox = storageKey;
        if (actionOffset == BACK_OFFSET) {
            open(player, currentBox / BOXES_PER_OVERVIEW_PAGE);
            return;
        }
        if (actionOffset == CLOSE_OFFSET) {
            player.closeInventory();
            return;
        }
        if (actionOffset == FIRST_OFFSET && currentBox > 0) {
            openStorage(player, 0);
            return;
        }
        if (actionOffset == PREVIOUS_OFFSET && currentBox > 0) {
            openStorage(player, currentBox - 1);
            return;
        }
        if (actionOffset == NEXT_OFFSET && currentBox + 1 < totalBoxes) {
            openStorage(player, currentBox + 1);
            return;
        }
        if (actionOffset == LAST_OFFSET && currentBox + 1 < totalBoxes) {
            openStorage(player, totalBoxes - 1);
        }
    }

    private static void fillOverview(@NotNull Inventory inventory) {
        ItemStack black = InfoGuiItems.createFiller(Material.BLACK_STAINED_GLASS_PANE, NamedTextColor.BLACK);
        ItemStack purple = InfoGuiItems.createFiller(Material.PURPLE_STAINED_GLASS_PANE, NamedTextColor.LIGHT_PURPLE);
        ItemStack bagPlaceholder = InfoGuiItems.createFiller(Material.GRAY_STAINED_GLASS_PANE, NamedTextColor.DARK_GRAY);

        Arrays.stream(OVERVIEW_BLACK_SLOTS).forEach(slot -> inventory.setItem(slot, black.clone()));
        Arrays.stream(OVERVIEW_HEAD_SLOTS).forEach(slot -> inventory.setItem(slot, purple.clone()));
        Arrays.stream(OVERVIEW_BAG_SLOTS).forEach(slot -> inventory.setItem(slot, bagPlaceholder.clone()));
    }

    private static void fillActionBar(@NotNull Inventory inventory, int boxIndex, int totalBoxes, int storageRows) {
        int actionStart = storageRows * 9;
        ItemStack filler = InfoGuiItems.createFiller(Material.BLACK_STAINED_GLASS_PANE, NamedTextColor.BLACK);
        for (int slot = actionStart; slot < actionStart + 9; slot++) {
            inventory.setItem(slot, filler.clone());
        }

        inventory.setItem(actionStart + BACK_OFFSET, InfoGuiItems.createNamedItem(
                Material.ARROW,
                nameText("返回背包列表"),
                List.of(bodyText("返回上一个背包总览界面。"))
        ));
        inventory.setItem(actionStart + CLOSE_OFFSET, closeItem());

        if (boxIndex >= 0 && boxIndex > 0) {
            inventory.setItem(actionStart + FIRST_OFFSET, InfoGuiItems.createCustomHead(
                    FIRST_TEXTURE,
                    nameText("跳转到末影箱 #1"),
                    List.of(bodyText("直接跳到第一个末影箱。"))
            ));
            inventory.setItem(actionStart + PREVIOUS_OFFSET, InfoGuiItems.createCustomHead(
                    PREVIOUS_TEXTURE,
                    nameText("上一页"),
                    List.of(bodyText("打开末影箱 #" + boxIndex + "。"))
            ));
        }
        if (boxIndex >= 0 && boxIndex + 1 < totalBoxes) {
            inventory.setItem(actionStart + NEXT_OFFSET, InfoGuiItems.createCustomHead(
                    NEXT_TEXTURE,
                    nameText("下一页"),
                    List.of(bodyText("打开末影箱 #" + (boxIndex + 2) + "。"))
            ));
            inventory.setItem(actionStart + LAST_OFFSET, InfoGuiItems.createCustomHead(
                    LAST_TEXTURE,
                    nameText("跳转到末影箱 #" + totalBoxes),
                    List.of(bodyText("直接跳到最后一个可用末影箱。"))
            ));
        }
    }

    private static int firstEmptyBagOverviewSlot(@NotNull BackpackDataDB db, @NotNull Player player) {
        BagSlot[] bagSlots = db.loadBagSlots(player.getUniqueId(), OVERVIEW_BAG_SLOTS[0],
                OVERVIEW_BAG_SLOTS[OVERVIEW_BAG_SLOTS.length - 1]);
        for (int index = 0; index < OVERVIEW_BAG_SLOTS.length; index++) {
            if (index >= bagSlots.length || bagSlots[index] == null) {
                return OVERVIEW_BAG_SLOTS[index];
            }
        }
        return -1;
    }

    private static void fillPlacedBags(@NotNull Player player, @NotNull Inventory inventory) {
        BackpackDataDB db = backpackDataDB();
        if (db == null) {
            return;
        }
        BagSlot[] bagSlots = db.loadBagSlots(player.getUniqueId(), OVERVIEW_BAG_SLOTS[0],
                OVERVIEW_BAG_SLOTS[OVERVIEW_BAG_SLOTS.length - 1]);
        for (BagSlot slot : bagSlots) {
            if (slot == null || !isBagOverviewSlot(slot.overviewSlot())) {
                continue;
            }
            inventory.setItem(slot.overviewSlot(), BackpackBagItems.createPlacedBag(slot.rows(), slot.overviewSlot()));
        }
    }

    private static void handleBagOverviewClick(@NotNull InventoryClickEvent event, @NotNull Player player, int rawSlot) {
        BackpackDataDB db = backpackDataDB();
        if (db == null) {
            player.sendMessage(bodyText("[背包] 数据库暂时不可用，无法使用小型袋子。"));
            return;
        }

        BagSlot existing = db.loadBagSlot(player.getUniqueId(), rawSlot);
        if (existing != null) {
            if (isBagUnbindDrop(event)) {
                unbindBagAndDrop(player, rawSlot, existing.rows());
                return;
            }
            openBagStorage(player, rawSlot, existing.rows());
            return;
        }

        ItemStack cursor = event.getCursor();
        int rows = BackpackBagItems.rows(cursor);
        if (rows <= 0) {
            if (!isEmpty(cursor)) {
                player.sendMessage(bodyText("[背包] 这里只能放入通过指令获取的小型袋子。"));
            }
            return;
        }
        if (!db.saveBagSlot(player.getUniqueId(), rawSlot, rows)) {
            player.sendMessage(bodyText("[背包] 小型袋子保存失败，请稍后再试。"));
            return;
        }
        consumeCursorItem(player, cursor);
        open(player, currentPage(player, PAGE_ID));
    }

    private static @NotNull ItemStack createBoxEntry(int boxIndex, int totalRows) {
        int rows = storageRows(totalRows, boxIndex);
        return InfoGuiItems.createCustomHead(
                BOX_HEAD_TEXTURE,
                nameText("末影箱 #" + (boxIndex + 1)),
                List.of(
                        bodyText("你的随身储物空间。"),
                        bodyText("可以存放常用材料、装备与战利品。"),
                        Component.empty(),
                        bodyText("当前容量: " + rows + " 行 / " + (rows * 9) + " 格"),
                        Component.empty(),
                        actionText("点击打开")
                )
        );
    }

    private static int permissionRows(@NotNull Player player) {
        int max = 0;
        for (String prefix : PERMISSION_ALIASES) {
            if (player.hasPermission(prefix + "*")) {
                max = Math.max(max, MAX_PERMISSION_ROWS);
            }
            for (int rows = 1; rows <= MAX_PERMISSION_ROWS; rows++) {
                if (player.hasPermission(prefix + rows)) {
                    max = Math.max(max, rows);
                }
            }
        }
        return Math.max(0, Math.min(MAX_PERMISSION_ROWS, max));
    }

    private static int totalBoxes(int totalRows) {
        int boxes = Math.max(0, (totalRows + MAX_STORAGE_ROWS_PER_BOX - 1) / MAX_STORAGE_ROWS_PER_BOX);
        return Math.min(BOXES_PER_OVERVIEW_PAGE, boxes);
    }

    private static int storageRows(int totalRows, int boxIndex) {
        int usedBefore = Math.max(0, boxIndex) * MAX_STORAGE_ROWS_PER_BOX;
        int remaining = Math.max(0, totalRows - usedBefore);
        return Math.max(0, Math.min(MAX_STORAGE_ROWS_PER_BOX, remaining));
    }

    private static int storageSlots(@NotNull Inventory inventory) {
        if (inventory.getSize() < 18) {
            return 0;
        }
        return inventory.getSize() - 9;
    }

    private static int headSlotIndex(int slot) {
        for (int index = 0; index < OVERVIEW_HEAD_SLOTS.length; index++) {
            if (OVERVIEW_HEAD_SLOTS[index] == slot) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isBagOverviewSlot(int slot) {
        for (int bagSlot : OVERVIEW_BAG_SLOTS) {
            if (bagSlot == slot) {
                return true;
            }
        }
        return false;
    }

    private static int currentPage(@NotNull Player player, @NotNull String pageId) {
        Inventory inventory = player.getOpenInventory().getTopInventory();
        if (inventory.getHolder() instanceof InfoGuiHolder holder && pageId.equals(holder.pageId())) {
            return holder.page();
        }
        return 0;
    }

    private static void scheduleSave(@NotNull Player player) {
        UndergroundState plugin = UndergroundState.getInstance();
        if (plugin == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            Inventory inventory = player.getOpenInventory().getTopInventory();
            if (inventory.getHolder() instanceof InfoGuiHolder holder && STORAGE_PAGE_ID.equals(holder.pageId())) {
                saveOpenStorage(player, inventory);
            }
        });
    }

    private static void saveOpenStorage(@NotNull Player player, @NotNull Inventory inventory) {
        if (!(inventory.getHolder() instanceof InfoGuiHolder holder) || !STORAGE_PAGE_ID.equals(holder.pageId())) {
            return;
        }
        int storageSlots = storageSlots(inventory);
        ItemStack[] items = new ItemStack[storageSlots];
        for (int slot = 0; slot < storageSlots; slot++) {
            items[slot] = cloneOrNull(inventory.getItem(slot));
        }
        BackpackDataDB db = backpackDataDB();
        if (db != null) {
            db.save(player.getUniqueId(), holder.page(), items);
        }
    }

    private static int bagStorageKey(int overviewSlot) {
        return BAG_STORAGE_BASE - overviewSlot;
    }

    private static boolean isBagStorageKey(int storageKey) {
        return storageKey <= BAG_STORAGE_BASE;
    }

    private static void consumeCursorItem(@NotNull Player player, @Nullable ItemStack cursor) {
        if (isEmpty(cursor)) {
            player.setItemOnCursor(null);
            return;
        }
        ItemStack updated = cursor.clone();
        updated.setAmount(updated.getAmount() - 1);
        player.setItemOnCursor(updated.getAmount() <= 0 ? null : updated);
    }

    private static void consumeClickedItem(@NotNull InventoryClickEvent event, @Nullable ItemStack item) {
        Inventory clickedInventory = event.getClickedInventory();
        if (isEmpty(item)) {
            if (clickedInventory != null) {
                clickedInventory.setItem(event.getSlot(), null);
            } else {
                event.setCurrentItem(null);
            }
            return;
        }
        ItemStack updated = item.clone();
        updated.setAmount(updated.getAmount() - 1);
        ItemStack result = updated.getAmount() <= 0 ? null : updated;
        if (clickedInventory != null) {
            clickedInventory.setItem(event.getSlot(), result);
        } else {
            event.setCurrentItem(result);
        }
    }

    private static boolean shouldCancelBottomClick(@NotNull InventoryClickEvent event) {
        InventoryAction action = event.getAction();
        ClickType click = event.getClick();
        return action == InventoryAction.COLLECT_TO_CURSOR
                || action == InventoryAction.HOTBAR_MOVE_AND_READD
                || action == InventoryAction.HOTBAR_SWAP
                || click == ClickType.NUMBER_KEY
                || click == ClickType.SWAP_OFFHAND
                || click == ClickType.DOUBLE_CLICK;
    }

    private static boolean isBlockedBagQuickClick(@NotNull InventoryClickEvent event) {
        ClickType click = event.getClick();
        InventoryAction action = event.getAction();
        return click == ClickType.SHIFT_LEFT
                || click == ClickType.SHIFT_RIGHT
                || click == ClickType.NUMBER_KEY
                || click == ClickType.SWAP_OFFHAND
                || click == ClickType.DOUBLE_CLICK
                || action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || action == InventoryAction.COLLECT_TO_CURSOR
                || action == InventoryAction.HOTBAR_MOVE_AND_READD
                || action == InventoryAction.HOTBAR_SWAP;
    }

    private static boolean isBagUnbindDrop(@NotNull InventoryClickEvent event) {
        ClickType click = event.getClick();
        InventoryAction action = event.getAction();
        return click == ClickType.DROP
                || click == ClickType.CONTROL_DROP
                || action == InventoryAction.DROP_ALL_SLOT
                || action == InventoryAction.DROP_ONE_SLOT;
    }

    private static void unbindBagAndDrop(@NotNull Player player, int overviewSlot, int rows) {
        BackpackDataDB db = backpackDataDB();
        if (db == null) {
            player.sendMessage(bodyText("[背包] 数据库暂时不可用，无法解绑小型袋子。"));
            return;
        }
        if (!db.deleteBagSlot(player.getUniqueId(), overviewSlot)) {
            player.sendMessage(bodyText("[背包] 小型袋子解绑失败，请稍后再试。"));
            return;
        }

        ItemStack droppedBag = BackpackBagItems.createBag(rows);
        World world = player.getWorld();
        Item itemEntity = world.dropItem(player.getLocation(), droppedBag);
        itemEntity.setOwner(player.getUniqueId());
        itemEntity.setThrower(player.getUniqueId());
        player.sendMessage(bodyText("[背包] 已解绑该槽位的小型袋子，并将其丢到你脚下。"));
        open(player, currentPage(player, PAGE_ID));
    }

    private static @NotNull ItemStack closeItem() {
        return InfoGuiItems.createNamedItem(
                Material.BARRIER,
                nameText("关闭页面"),
                List.of(bodyText("点击关闭这个页面。"))
        );
    }

    private static @Nullable BackpackDataDB backpackDataDB() {
        UndergroundState plugin = UndergroundState.getInstance();
        return plugin == null ? null : plugin.getBackpackDataDB();
    }

    private static @Nullable ItemStack cloneOrNull(@Nullable ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0 ? null : item.clone();
    }

    private static boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private static @NotNull Component nameText(@NotNull String text) {
        return Component.text(text, NAME_RGB)
                .decoration(TextDecoration.ITALIC, false)
                .decorate(TextDecoration.BOLD);
    }

    private static @NotNull Component actionText(@NotNull String text) {
        return Component.text(text, ACTION_RGB)
                .decoration(TextDecoration.ITALIC, false)
                .decorate(TextDecoration.BOLD);
    }

    private static @NotNull Component bodyText(@NotNull String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }
}
