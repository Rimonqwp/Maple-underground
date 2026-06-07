package maple.underground_state.gui;

import maple.underground_state.UndergroundState;
import maple.underground_state.wardrobe.WardrobeDataDB;
import maple.underground_state.wardrobe.WardrobeDataDB.WardrobeSnapshot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WardrobeInfoGui {

    public static final String PAGE_ID = "wardrobe";

    private static final int MENU_SIZE = 54;
    private static final int SET_COUNT = 9;
    private static final int PIECE_COUNT = 4;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 49;

    private static final int HELMET_INDEX = 0;
    private static final int CHESTPLATE_INDEX = 1;
    private static final int LEGGINGS_INDEX = 2;
    private static final int BOOTS_INDEX = 3;

    private static final Component TITLE = Component.text("衣柜", NamedTextColor.WHITE);
    private static final TextColor NAME_RGB = TextColor.color(0x63E6A6);
    private static final TextColor ACTION_RGB = TextColor.color(0xFFE36E);
    private static final Map<UUID, WardrobeData> CACHE = new ConcurrentHashMap<>();

    private WardrobeInfoGui() {
    }

    public static void open(@NotNull Player player) {
        syncActiveSet(player);
        WardrobeData data = loadData(player.getUniqueId());
        InfoGuiHolder holder = new InfoGuiHolder(player.getUniqueId(), PAGE_ID);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, TITLE);
        holder.setInventory(inventory);

        ItemStack bottomFiller = InfoGuiItems.createFiller(Material.BLACK_STAINED_GLASS_PANE, NamedTextColor.BLACK);
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, bottomFiller.clone());
        }
        inventory.setItem(BACK_SLOT, InfoGuiItems.createNamedItem(
                Material.ARROW,
                nameText("返回主页"),
                List.of(bodyText("点击返回主菜单。"))
        ));
        inventory.setItem(CLOSE_SLOT, InfoGuiItems.createNamedItem(
                Material.BARRIER,
                nameText("关闭页面"),
                List.of(bodyText("点击关闭这个页面。"))
        ));

        refreshInventoryContents(player, inventory, data);
        player.openInventory(inventory);
    }

    public static void handleInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getRawSlot();
        Inventory top = event.getView().getTopInventory();
        if (rawSlot < 0) {
            event.setCancelled(true);
            return;
        }
        if (rawSlot >= top.getSize()) {
            event.setCancelled(shouldCancelBottomClick(event));
            return;
        }

        event.setCancelled(true);
        if (rawSlot == BACK_SLOT) {
            MainInfoGui.open(player);
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (isArmorDisplaySlot(rawSlot)) {
            handleArmorDisplayClick(player, rawSlot, event.getCursor());
            return;
        }

        int column = actionColumn(rawSlot);
        if (column < 0) {
            return;
        }

        syncActiveSet(player);
        WardrobeData data = loadData(player.getUniqueId());
        ArmorSet set = ArmorSet.fromArray(data.sets[column]);
        if (!set.hasAny()) {
            saveCurrentArmorToEmptyColumn(player, data, column);
            return;
        }
        if (data.activeColumn == column) {
            unequipActiveSet(player, data, column, set);
            return;
        }
        applyArmorSet(player, data, column, set);
    }

    public static void syncActiveSet(@NotNull Player player) {
        WardrobeData data = loadData(player.getUniqueId());
        int activeColumn = data.activeColumn;
        if (activeColumn < 0 || activeColumn >= SET_COUNT) {
            return;
        }

        ItemStack[] current = currentArmor(player).toArray();
        boolean changed = false;
        for (int piece = 0; piece < PIECE_COUNT; piece++) {
            ItemStack stored = data.sets[activeColumn][piece];
            if (!sameItem(stored, current[piece])) {
                data.sets[activeColumn][piece] = cloneOrNull(current[piece]);
                changed = true;
            }
        }
        if (!changed) {
            return;
        }

        if (!ArmorSet.fromArray(data.sets[activeColumn]).hasAny()) {
            data.activeColumn = -1;
        }
        saveData(player.getUniqueId(), data);
        refreshOpenInventory(player, data);
    }

    public static boolean isArmorItem(@Nullable ItemStack item) {
        return !isEmpty(item) && armorPiece(item.getType()) >= 0;
    }

    private static void saveCurrentArmorToEmptyColumn(@NotNull Player player,
                                                      @NotNull WardrobeData data,
                                                      int column) {
        if (data.activeColumn >= 0 && data.activeColumn < SET_COUNT) {
            player.sendMessage(text("[衣柜] 这个栏位是空的。你正在使用其他栏位，请先取消使用后再保存当前装备。", NamedTextColor.YELLOW));
            refreshOpenInventory(player, data);
            return;
        }

        ArmorSet current = currentArmor(player);
        if (!current.hasAny()) {
            player.sendMessage(text("[衣柜] 这个栏位是空的，请先穿上装备，或把背包里的装备放到上方对应格子。", NamedTextColor.YELLOW));
            refreshOpenInventory(player, data);
            return;
        }

        ItemStack[] currentItems = current.toArray();
        for (int piece = 0; piece < PIECE_COUNT; piece++) {
            data.sets[column][piece] = cloneOrNull(currentItems[piece]);
        }
        data.activeColumn = column;
        saveData(player.getUniqueId(), data);
        player.sendMessage(text("[衣柜] 已把当前身上的装备保存到栏位 " + (column + 1) + "，并设为正在使用。", NamedTextColor.GREEN));
        refreshOpenInventory(player, data);
    }

    private static void handleArmorDisplayClick(@NotNull Player player,
                                                int rawSlot,
                                                @Nullable ItemStack cursorItem) {
        syncActiveSet(player);
        WardrobeData data = loadData(player.getUniqueId());
        int column = rawSlot % 9;
        int piece = rawSlot / 9;
        boolean activeColumn = data.activeColumn == column;
        ItemStack stored = cloneOrNull(data.sets[column][piece]);
        ItemStack cursor = cloneOrNull(cursorItem);

        if (isEmpty(cursor)) {
            takeStoredPiece(player, data, column, piece, stored, activeColumn);
            return;
        }
        if (!isExpectedArmorPiece(cursor, piece)) {
            player.sendMessage(text("[衣柜] 这件装备不能放在这个位置。", NamedTextColor.RED));
            refreshOpenInventory(player, data);
            return;
        }
        if (activeColumn) {
            placeIntoActiveColumn(player, data, column, piece, stored, cursor);
            return;
        }

        data.sets[column][piece] = cursor.clone();
        player.setItemOnCursor(cloneOrNull(stored));
        saveData(player.getUniqueId(), data);
        player.sendMessage(text("[衣柜] 已更新栏位 " + (column + 1) + " 的" + pieceDisplayName(piece) + "。", NamedTextColor.GREEN));
        refreshOpenInventory(player, data);
    }

    private static void takeStoredPiece(@NotNull Player player,
                                        @NotNull WardrobeData data,
                                        int column,
                                        int piece,
                                        @Nullable ItemStack stored,
                                        boolean activeColumn) {
        if (isEmpty(stored)) {
            player.sendMessage(text("[衣柜] 这里是空的，把背包里的装备放到对应位置即可保存。", NamedTextColor.YELLOW));
            refreshOpenInventory(player, data);
            return;
        }
        if (activeColumn) {
            player.sendMessage(text("[衣柜] 这一栏正在使用，不能从衣柜界面拿下。请从身上装备栏取下，它会自动从衣柜记录中删除。", NamedTextColor.RED));
            refreshOpenInventory(player, data);
            return;
        }

        data.sets[column][piece] = null;
        player.setItemOnCursor(stored.clone());
        saveData(player.getUniqueId(), data);
        player.sendMessage(text("[衣柜] 已取下栏位 " + (column + 1) + " 的" + pieceDisplayName(piece) + "。", NamedTextColor.GREEN));
        refreshOpenInventory(player, data);
    }

    private static void placeIntoActiveColumn(@NotNull Player player,
                                              @NotNull WardrobeData data,
                                              int column,
                                              int piece,
                                              @Nullable ItemStack stored,
                                              @NotNull ItemStack cursor) {
        if (!isEmpty(stored)) {
            player.sendMessage(text("[衣柜] 这一栏正在使用，不能直接替换已有装备。请先从身上取下对应装备。", NamedTextColor.RED));
            refreshOpenInventory(player, data);
            return;
        }
        if (!isEmpty(currentArmorPiece(player, piece))) {
            player.sendMessage(text("[衣柜] 你身上这个位置已经有装备，请先从身上取下。", NamedTextColor.RED));
            refreshOpenInventory(player, data);
            return;
        }

        setCurrentArmorPiece(player, piece, cursor.clone());
        data.sets[column][piece] = cursor.clone();
        player.setItemOnCursor(null);
        saveData(player.getUniqueId(), data);
        player.sendMessage(text("[衣柜] 已穿上并记录栏位 " + (column + 1) + " 的" + pieceDisplayName(piece) + "。", NamedTextColor.GREEN));
        refreshOpenInventory(player, data);
    }

    private static void applyArmorSet(@NotNull Player player,
                                      @NotNull WardrobeData data,
                                      int column,
                                      @NotNull ArmorSet set) {
        ArmorSet current = currentArmor(player);
        if (data.activeColumn == column && sameArmorSet(current, set)) {
            saveData(player.getUniqueId(), data);
            player.sendMessage(text("[衣柜] 你已经在使用这一栏。", NamedTextColor.YELLOW));
            refreshOpenInventory(player, data);
            return;
        }
        List<ItemStack> oldArmorToReturn = armorToReturnOnSwitch(data, column, current);
        if (!hasRoomFor(player.getInventory(), oldArmorToReturn)) {
            player.sendMessage(text("[衣柜] 背包空间不足，无法安全换装。", NamedTextColor.RED));
            refreshOpenInventory(player, data);
            return;
        }

        PlayerInventory inventory = player.getInventory();
        inventory.setHelmet(cloneOrNull(set.helmet()));
        inventory.setChestplate(cloneOrNull(set.chestplate()));
        inventory.setLeggings(cloneOrNull(set.leggings()));
        inventory.setBoots(cloneOrNull(set.boots()));
        for (ItemStack item : oldArmorToReturn) {
            inventory.addItem(item);
        }

        data.activeColumn = column;
        saveData(player.getUniqueId(), data);
        player.sendMessage(text("[衣柜] 已换上栏位 " + (column + 1) + " 的装备。", NamedTextColor.GREEN));
        refreshOpenInventory(player, data);
    }

    private static @NotNull List<ItemStack> armorToReturnOnSwitch(@NotNull WardrobeData data,
                                                                  int targetColumn,
                                                                  @NotNull ArmorSet current) {
        List<ItemStack> items = new ArrayList<>();
        ItemStack[] currentItems = current.toArray();
        int activeColumn = data.activeColumn;
        for (int piece = 0; piece < PIECE_COUNT; piece++) {
            ItemStack item = currentItems[piece];
            if (isEmpty(item)) {
                continue;
            }
            boolean belongsToActiveWardrobe = activeColumn >= 0
                    && activeColumn < SET_COUNT
                    && activeColumn != targetColumn
                    && sameItem(item, data.sets[activeColumn][piece]);
            if (!belongsToActiveWardrobe) {
                items.add(item.clone());
            }
        }
        return items;
    }

    private static void refreshOpenInventory(@NotNull Player player, @NotNull WardrobeData data) {
        Inventory top = player.getOpenInventory().getTopInventory();
        if (!(top.getHolder() instanceof InfoGuiHolder holder) || !PAGE_ID.equals(holder.pageId())) {
            return;
        }
        refreshInventoryContents(player, top, data);
    }

    private static void refreshInventoryContents(@NotNull Player player,
                                                 @NotNull Inventory inventory,
                                                 @NotNull WardrobeData data) {
        ItemStack emptySlot = InfoGuiItems.createFiller(Material.BROWN_STAINED_GLASS_PANE, NamedTextColor.DARK_GRAY);
        for (int column = 0; column < SET_COUNT; column++) {
            ArmorSet set = ArmorSet.fromArray(data.sets[column]);
            setArmorDisplay(inventory, column, set, emptySlot);
            inventory.setItem(actionSlot(column), createActionItem(player, data, column, set));
        }
    }

    private static void setArmorDisplay(@NotNull Inventory inventory,
                                        int column,
                                        @NotNull ArmorSet set,
                                        @NotNull ItemStack emptySlot) {
        inventory.setItem(column, displayOrEmpty(set.helmet(), emptySlot));
        inventory.setItem(column + 9, displayOrEmpty(set.chestplate(), emptySlot));
        inventory.setItem(column + 18, displayOrEmpty(set.leggings(), emptySlot));
        inventory.setItem(column + 27, displayOrEmpty(set.boots(), emptySlot));
    }

    private static @NotNull ItemStack displayOrEmpty(@Nullable ItemStack item, @NotNull ItemStack emptySlot) {
        return isEmpty(item) ? emptySlot.clone() : item.clone();
    }

    private static @NotNull ItemStack createActionItem(@NotNull Player player,
                                                       @NotNull WardrobeData data,
                                                       int column,
                                                       @NotNull ArmorSet set) {
        boolean active = data.activeColumn == column && set.hasAny();
        boolean canSaveCurrentToEmpty = !set.hasAny() && data.activeColumn < 0 && currentArmor(player).hasAny();
        Material material = active ? Material.LIME_DYE : Material.RED_DYE;

        List<Component> lore = new ArrayList<>();
        if (!set.hasAny()) {
            if (canSaveCurrentToEmpty) {
                lore.add(bodyText("点击把当前身上的装备保存到这一栏。"));
                lore.add(bodyText("保存后这一栏会设为正在使用。"));
            } else {
                lore.add(bodyText("这个栏位目前是空的。"));
                lore.add(bodyText("从背包拿起装备，放到上方对应格子。"));
            }
        } else if (active) {
            lore.add(bodyText("你当前正在使用这一栏。"));
            lore.add(bodyText("再次点击下方染料可脱下装备并保存回衣柜。"));
            lore.add(bodyText("上方装备不能从衣柜界面拿下。"));
            lore.add(bodyText("从身上取下装备后，这里会自动删除对应记录。"));
        } else {
            lore.add(bodyText("点击穿上上方这一套装备。"));
            lore.add(bodyText("你身上的原装备会放回背包。"));
        }
        lore.add(Component.empty());
        lore.add(actionText("栏位 " + (column + 1)));

        String name = active ? "正在使用" : set.hasAny() ? "穿上这一套" : canSaveCurrentToEmpty ? "保存当前装备" : "空衣柜";
        return InfoGuiItems.createNamedItem(material, nameText(name), lore);
    }

    private static void unequipActiveSet(@NotNull Player player,
                                         @NotNull WardrobeData data,
                                         int column,
                                         @NotNull ArmorSet set) {
        ItemStack[] current = currentArmor(player).toArray();
        ItemStack[] stored = set.toArray();
        for (int piece = 0; piece < PIECE_COUNT; piece++) {
            if (!isEmpty(stored[piece]) && sameItem(stored[piece], current[piece])) {
                setCurrentArmorPiece(player, piece, null);
            }
        }

        data.activeColumn = -1;
        saveData(player.getUniqueId(), data);
        player.sendMessage(text("[衣柜] 已取消使用栏位 " + (column + 1) + "，装备已保存在衣柜中。", NamedTextColor.GREEN));
        refreshOpenInventory(player, data);
    }

    private static boolean hasRoomFor(@NotNull PlayerInventory inventory, @NotNull List<ItemStack> items) {
        int requiredSlots = 0;
        for (ItemStack item : items) {
            if (!isEmpty(item)) {
                requiredSlots++;
            }
        }
        if (requiredSlots == 0) {
            return true;
        }

        int emptySlots = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (isEmpty(item)) {
                emptySlots++;
            }
        }
        return emptySlots >= requiredSlots;
    }

    private static @NotNull ArmorSet currentArmor(@NotNull Player player) {
        PlayerInventory inventory = player.getInventory();
        return new ArmorSet(
                cloneOrNull(inventory.getHelmet()),
                cloneOrNull(inventory.getChestplate()),
                cloneOrNull(inventory.getLeggings()),
                cloneOrNull(inventory.getBoots())
        );
    }

    private static @Nullable ItemStack currentArmorPiece(@NotNull Player player, int piece) {
        PlayerInventory inventory = player.getInventory();
        return switch (piece) {
            case HELMET_INDEX -> cloneOrNull(inventory.getHelmet());
            case CHESTPLATE_INDEX -> cloneOrNull(inventory.getChestplate());
            case LEGGINGS_INDEX -> cloneOrNull(inventory.getLeggings());
            case BOOTS_INDEX -> cloneOrNull(inventory.getBoots());
            default -> null;
        };
    }

    private static void setCurrentArmorPiece(@NotNull Player player, int piece, @Nullable ItemStack item) {
        PlayerInventory inventory = player.getInventory();
        switch (piece) {
            case HELMET_INDEX -> inventory.setHelmet(cloneOrNull(item));
            case CHESTPLATE_INDEX -> inventory.setChestplate(cloneOrNull(item));
            case LEGGINGS_INDEX -> inventory.setLeggings(cloneOrNull(item));
            case BOOTS_INDEX -> inventory.setBoots(cloneOrNull(item));
            default -> {
            }
        }
    }

    private static int actionSlot(int column) {
        return 36 + column;
    }

    private static int actionColumn(int slot) {
        return slot >= 36 && slot <= 44 ? slot - 36 : -1;
    }

    private static boolean isArmorDisplaySlot(int slot) {
        return slot >= 0 && slot < 36;
    }

    private static boolean shouldCancelBottomClick(@NotNull InventoryClickEvent event) {
        InventoryAction action = event.getAction();
        return action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || action == InventoryAction.COLLECT_TO_CURSOR
                || action == InventoryAction.UNKNOWN;
    }

    private static boolean isExpectedArmorPiece(@NotNull ItemStack item, int piece) {
        return armorPiece(item.getType()) == piece;
    }

    private static int armorPiece(@NotNull Material material) {
        String name = material.name();
        if (name.endsWith("_HELMET") || material == Material.TURTLE_HELMET) {
            return HELMET_INDEX;
        }
        if (name.endsWith("_CHESTPLATE") || material == Material.ELYTRA) {
            return CHESTPLATE_INDEX;
        }
        if (name.endsWith("_LEGGINGS")) {
            return LEGGINGS_INDEX;
        }
        if (name.endsWith("_BOOTS")) {
            return BOOTS_INDEX;
        }
        return -1;
    }

    private static @NotNull String pieceDisplayName(int piece) {
        return switch (piece) {
            case HELMET_INDEX -> "头盔";
            case CHESTPLATE_INDEX -> "胸甲";
            case LEGGINGS_INDEX -> "护腿";
            case BOOTS_INDEX -> "靴子";
            default -> "装备";
        };
    }

    private static boolean sameArmorSet(@NotNull ArmorSet left, @NotNull ArmorSet right) {
        return sameItem(left.helmet(), right.helmet())
                && sameItem(left.chestplate(), right.chestplate())
                && sameItem(left.leggings(), right.leggings())
                && sameItem(left.boots(), right.boots());
    }

    private static boolean sameItem(@Nullable ItemStack left, @Nullable ItemStack right) {
        if (isEmpty(left) && isEmpty(right)) {
            return true;
        }
        if (isEmpty(left) || isEmpty(right)) {
            return false;
        }
        return left.getAmount() == right.getAmount() && left.isSimilar(right);
    }

    private static @Nullable ItemStack cloneOrNull(@Nullable ItemStack item) {
        return isEmpty(item) ? null : item.clone();
    }

    private static boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private static @NotNull WardrobeData loadData(@NotNull UUID uuid) {
        WardrobeData data = readData(uuid);
        CACHE.put(uuid, data);
        return data;
    }

    private static @NotNull WardrobeData readData(@NotNull UUID uuid) {
        WardrobeData data = new WardrobeData();
        WardrobeDataDB db = wardrobeDataDB();
        if (db == null) {
            return data;
        }
        WardrobeSnapshot snapshot = db.load(uuid);
        data.activeColumn = snapshot.activeSlot();
        for (int column = 0; column < SET_COUNT; column++) {
            for (int piece = 0; piece < PIECE_COUNT; piece++) {
                data.sets[column][piece] = cloneOrNull(snapshot.sets()[column][piece]);
            }
        }
        return data;
    }

    private static void saveData(@NotNull UUID uuid, @NotNull WardrobeData data) {
        CACHE.put(uuid, data);
        WardrobeDataDB db = wardrobeDataDB();
        if (db == null) {
            return;
        }
        WardrobeSnapshot snapshot = new WardrobeSnapshot();
        snapshot.activeSlot(data.activeColumn);
        for (int column = 0; column < SET_COUNT; column++) {
            for (int piece = 0; piece < PIECE_COUNT; piece++) {
                snapshot.sets()[column][piece] = cloneOrNull(data.sets[column][piece]);
            }
        }
        db.save(uuid, snapshot);
    }

    private static @Nullable WardrobeDataDB wardrobeDataDB() {
        UndergroundState plugin = UndergroundState.getInstance();
        return plugin == null ? null : plugin.getWardrobeDataDB();
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
        return text(text, NamedTextColor.GRAY);
    }

    private static @NotNull Component text(@NotNull String text, @NotNull NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private record ArmorSet(@Nullable ItemStack helmet,
                            @Nullable ItemStack chestplate,
                            @Nullable ItemStack leggings,
                            @Nullable ItemStack boots) {

        private static @NotNull ArmorSet fromArray(@NotNull ItemStack[] items) {
            return new ArmorSet(
                    clonePiece(items, HELMET_INDEX),
                    clonePiece(items, CHESTPLATE_INDEX),
                    clonePiece(items, LEGGINGS_INDEX),
                    clonePiece(items, BOOTS_INDEX)
            );
        }

        private boolean hasAny() {
            return !isEmpty(helmet) || !isEmpty(chestplate) || !isEmpty(leggings) || !isEmpty(boots);
        }

        private @NotNull ItemStack[] toArray() {
            return new ItemStack[]{
                    cloneOrNull(helmet),
                    cloneOrNull(chestplate),
                    cloneOrNull(leggings),
                    cloneOrNull(boots)
            };
        }

        private @NotNull List<ItemStack> nonEmptyItems() {
            List<ItemStack> items = new ArrayList<>();
            addIfPresent(items, helmet);
            addIfPresent(items, chestplate);
            addIfPresent(items, leggings);
            addIfPresent(items, boots);
            return items;
        }

        private static void addIfPresent(@NotNull List<ItemStack> items, @Nullable ItemStack item) {
            if (!isEmpty(item)) {
                items.add(item.clone());
            }
        }

        private static @Nullable ItemStack clonePiece(@NotNull ItemStack[] items, int index) {
            if (index < 0 || index >= items.length) {
                return null;
            }
            return cloneOrNull(items[index]);
        }
    }

    private static final class WardrobeData {
        private final ItemStack[][] sets = new ItemStack[SET_COUNT][PIECE_COUNT];
        private int activeColumn = -1;
    }
}
