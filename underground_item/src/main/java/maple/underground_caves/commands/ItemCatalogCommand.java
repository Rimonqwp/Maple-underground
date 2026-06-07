package maple.underground_caves.commands;

import maple.underground_caves.gear.AdaptiveSetManager;
import maple.underground_caves.gear.ArcherSetManager;
import maple.underground_caves.gear.ArrowDistanceHelmetManager;
import maple.underground_caves.gear.BloodSetManager;
import maple.underground_caves.gear.CaveSpecialHelmetManager;
import maple.underground_caves.gear.CavernExplorerSetManager;
import maple.underground_caves.gear.GearManager;
import maple.underground_caves.gear.ImmortalSkullManager;
import maple.underground_caves.gear.LegacyRgbParser;
import maple.underground_caves.gear.QualityUpgradeItemManager;
import maple.underground_caves.gear.SecondWindHelmetManager;
import maple.underground_caves.gear.SieveSetManager;
import maple.underground_caves.gear.SkeletonMasterSetManager;
import maple.underground_caves.gear.SkeletonSoldierSetManager;
import maple.underground_caves.gear.StatsDoubleHelmetManager;
import maple.underground_caves.gear.UndergroundCaveHelmetManager;
import maple.underground_caves.weapons.WeaponManager;
import maple.underground_item.UndergroundItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ItemCatalogCommand implements CommandExecutor, Listener {

    private static final int INVENTORY_SIZE = 54;
    private static final int ITEMS_PER_PAGE = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int CLOSE_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final String[] FLOORS = {"F1", "F2", "F3"};
    private static final String[] ARMOR_SLOTS = {"helmet", "chest", "leggings", "boots"};

    private final UndergroundItem plugin;

    public ItemCatalogCommand(@NotNull UndergroundItem plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("test_dungeons.admin")) {
            player.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        openPage(player, 0);
        return true;
    }

    private void openPage(@NotNull Player player, int requestedPage) {
        List<ItemStack> items = buildCatalogItems();
        int totalPages = Math.max(1, (items.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));

        CatalogHolder holder = new CatalogHolder(player.getUniqueId(), page);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE,
                LegacyRgbParser.parse("&8物品总览 &7(" + (page + 1) + "/" + totalPages + ")"));
        holder.setInventory(inventory);

        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, items.size());
        for (int slot = 0; slot < ITEMS_PER_PAGE && startIndex + slot < endIndex; slot++) {
            inventory.setItem(slot, items.get(startIndex + slot));
        }

        ItemStack filler = createFillerItem();
        for (int slot = ITEMS_PER_PAGE; slot < INVENTORY_SIZE; slot++) {
            inventory.setItem(slot, filler.clone());
        }
        inventory.setItem(CLOSE_SLOT, createControlItem(Material.BARRIER, "&c关闭"));
        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, createControlItem(Material.ARROW, "&e上一页"));
        }
        if (page + 1 < totalPages) {
            inventory.setItem(NEXT_SLOT, createControlItem(Material.ARROW, "&e下一页"));
        }

        player.openInventory(inventory);
    }

    private @NotNull List<ItemStack> buildCatalogItems() {
        List<ItemStack> items = new ArrayList<>();

        WeaponManager weaponManager = plugin.getWeaponManager();
        if (weaponManager != null) {
            List<String> weaponIds = new ArrayList<>(weaponManager.getWeaponIds());
            weaponIds.sort(String.CASE_INSENSITIVE_ORDER);
            for (String weaponId : weaponIds) {
                addIfPresent(items, weaponManager.createWeaponItem(weaponId));
            }
        }

        GearManager gearManager = plugin.getGearManager();
        if (gearManager != null) {
            for (String floor : FLOORS) {
                for (String slot : ARMOR_SLOTS) {
                    addIfPresent(items, gearManager.createDefeatTankPiece(slot, floor));
                }
            }
        }

        ImmortalSkullManager immortalSkullManager = plugin.getImmortalSkullManager();
        if (immortalSkullManager != null) {
            addIfPresent(items, immortalSkullManager.createImmortalSkull());
        }

        QualityUpgradeItemManager qualityUpgradeItemManager = plugin.getQualityUpgradeItemManager();
        if (qualityUpgradeItemManager != null) {
            addIfPresent(items, qualityUpgradeItemManager.createItem());
        }

        UndergroundCaveHelmetManager undergroundCaveHelmetManager = plugin.getUndergroundCaveHelmetManager();
        if (undergroundCaveHelmetManager != null) {
            for (String floor : FLOORS) {
                addIfPresent(items, undergroundCaveHelmetManager.createCaveHelmet(floor));
            }
        }

        StatsDoubleHelmetManager statsDoubleHelmetManager = plugin.getStatsDoubleHelmetManager();
        if (statsDoubleHelmetManager != null) {
            for (String helmetId : statsDoubleHelmetManager.getHelmetIds()) {
                addIfPresent(items, statsDoubleHelmetManager.createHelmet(helmetId));
            }
        }

        CaveSpecialHelmetManager caveSpecialHelmetManager = plugin.getCaveSpecialHelmetManager();
        if (caveSpecialHelmetManager != null) {
            for (String helmetId : caveSpecialHelmetManager.getHelmetIds()) {
                addIfPresent(items, caveSpecialHelmetManager.createHelmet(helmetId));
            }
        }

        SecondWindHelmetManager secondWindHelmetManager = plugin.getSecondWindHelmetManager();
        if (secondWindHelmetManager != null) {
            addIfPresent(items, secondWindHelmetManager.createHelmet());
        }

        ArrowDistanceHelmetManager arrowDistanceHelmetManager = plugin.getArrowDistanceHelmetManager();
        if (arrowDistanceHelmetManager != null) {
            addIfPresent(items, arrowDistanceHelmetManager.createHelmet());
        }

        AdaptiveSetManager adaptiveSetManager = plugin.getAdaptiveSetManager();
        if (adaptiveSetManager != null) {
            for (String slot : AdaptiveSetManager.getSlots()) {
                addIfPresent(items, adaptiveSetManager.createPiece(slot));
            }
        }

        CavernExplorerSetManager cavernExplorerSetManager = plugin.getCavernExplorerSetManager();
        if (cavernExplorerSetManager != null) {
            for (String slot : CavernExplorerSetManager.getSlots()) {
                addIfPresent(items, cavernExplorerSetManager.createPiece(slot));
            }
        }

        BloodSetManager bloodSetManager = plugin.getBloodSetManager();
        if (bloodSetManager != null) {
            for (String setId : BloodSetManager.getSetIds()) {
                for (String slot : BloodSetManager.getSlots()) {
                    addIfPresent(items, bloodSetManager.createPiece(setId, slot));
                }
            }
        }

        SieveSetManager sieveSetManager = plugin.getSieveSetManager();
        if (sieveSetManager != null) {
            for (String floor : FLOORS) {
                for (String slot : ARMOR_SLOTS) {
                    addIfPresent(items, sieveSetManager.createSievePiece(slot, floor));
                }
            }
        }

        ArcherSetManager archerSetManager = plugin.getArcherSetManager();
        if (archerSetManager != null) {
            for (String floor : FLOORS) {
                for (String slot : ARMOR_SLOTS) {
                    addIfPresent(items, archerSetManager.createArcherPiece(slot, floor));
                }
            }
        }

        SkeletonSoldierSetManager skeletonSoldierSetManager = plugin.getSkeletonSoldierSetManager();
        if (skeletonSoldierSetManager != null) {
            for (String floor : FLOORS) {
                for (String slot : ARMOR_SLOTS) {
                    addIfPresent(items, skeletonSoldierSetManager.createSkeletonSoldierPiece(slot, floor));
                }
            }
        }

        SkeletonMasterSetManager skeletonMasterSetManager = plugin.getSkeletonMasterSetManager();
        if (skeletonMasterSetManager != null) {
            for (String floor : FLOORS) {
                for (String slot : ARMOR_SLOTS) {
                    addIfPresent(items, skeletonMasterSetManager.createSkeletonMasterPiece(slot, floor));
                }
            }
        }

        return items;
    }

    private static void addIfPresent(@NotNull List<ItemStack> items, ItemStack item) {
        if (item != null && !item.getType().isAir()) {
            items.add(item);
        }
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof CatalogHolder holder)) {
            return;
        }
        if (!holder.owner().equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0) {
            return;
        }
        if (rawSlot < top.getSize()) {
            event.setCancelled(true);
            if (rawSlot == PREVIOUS_SLOT) {
                openPage(player, holder.page() - 1);
                return;
            }
            if (rawSlot == NEXT_SLOT) {
                openPage(player, holder.page() + 1);
                return;
            }
            if (rawSlot == CLOSE_SLOT) {
                player.closeInventory();
                return;
            }
            if (rawSlot >= ITEMS_PER_PAGE) {
                return;
            }

            ItemStack clicked = top.getItem(rawSlot);
            if (clicked == null || clicked.getType().isAir()) {
                return;
            }
            giveOrDrop(player, clicked.clone());
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.75f, 1.15f);
            player.sendActionBar(Component.text("物品已放入你的背包。", NamedTextColor.GREEN));
            return;
        }

        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY || event.getClick().isShiftClick()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof CatalogHolder)) {
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private static void giveOrDrop(@NotNull Player player, @NotNull ItemStack item) {
        var leftover = player.getInventory().addItem(item);
        for (ItemStack stack : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), stack);
        }
    }

    private static @NotNull ItemStack createFillerItem() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" ", NamedTextColor.DARK_GRAY));
            maple.underground_item.util.ItemFlagUtil.applyStandardItemFlags(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static @NotNull ItemStack createControlItem(@NotNull Material material, @NotNull String title) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyRgbParser.parse(title));
            maple.underground_item.util.ItemFlagUtil.applyStandardItemFlags(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static final class CatalogHolder implements InventoryHolder {
        private final UUID owner;
        private final int page;
        private Inventory inventory;

        private CatalogHolder(@NotNull UUID owner, int page) {
            this.owner = owner;
            this.page = page;
        }

        private @NotNull UUID owner() {
            return owner;
        }

        private int page() {
            return page;
        }

        private void setInventory(@NotNull Inventory inventory) {
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
