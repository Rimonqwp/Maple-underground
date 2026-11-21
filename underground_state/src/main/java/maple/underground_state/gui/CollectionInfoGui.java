package maple.underground_state.gui;

import maple.underground_state.UndergroundState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class CollectionInfoGui {

    public static final String HOE_PAGE_ID = "collection_hoe";
    public static final String PICKAXE_PAGE_ID = "collection_pickaxe";
    public static final String SWORD_PAGE_ID = "collection_sword";
    public static final String AXE_PAGE_ID = "collection_axe";
    public static final String FISHING_ROD_PAGE_ID = "collection_fishing_rod";

    private static final int MENU_SIZE = 54;
    private static final int TITLE_SLOT = 4;
    private static final int BACK_SLOT = 45;
    private static final int PREVIOUS_PAGE_SLOT = 48;
    private static final int CLOSE_SLOT = 49;
    private static final int NEXT_PAGE_SLOT = 50;
    private static final int DETAIL_BACK_SLOT = 48;
    private static final int DETAIL_CLOSE_SLOT = 50;
    private static final int DETAIL_ITEM_SLOT = 4;
    private static final int DETAIL_PAGE_OFFSET = 1000;

    private static final int[] ITEM_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private static final int[] DETAIL_TIER_SLOTS = {
            18, 19, 20, 21, 22, 23, 24, 25, 26, 28, 29, 30
    };

    private static final int[] DETAIL_STATUS_BAR_SLOTS = {
            45, 46, 47, 48, 49, 50, 51, 52, 53
    };

    private static final int[] TIER_REQUIREMENTS = {
            50, 100, 250, 500, 1_000, 2_500, 5_000, 10_000, 50_000
    };

    private static volatile @Nullable CollectionCenterBridge bridge;

    public interface CollectionCenterBridge {
        void openHub(@NotNull Player player);

        int getClaimedTier(@NotNull UUID uuid, @NotNull String collectionKey);

        boolean claimItemTier(@NotNull Player player,
                              @NotNull Category category,
                              @NotNull CollectionItem item,
                              int targetTier);
    }

    private CollectionInfoGui() {
    }

    public static void registerBridge(@Nullable CollectionCenterBridge newBridge) {
        bridge = newBridge;
    }

    public static boolean hasBridge() {
        return bridge != null;
    }

    public static void openHubOrBookshelf(@NotNull Player player) {
        CollectionCenterBridge current = bridge;
        if (current != null) {
            current.openHub(player);
        } else {
            BookshelfInfoGui.open(player);
        }
    }

    public static void open(@NotNull Player player, @NotNull Category category) {
        open(player, category, 0);
    }

    public static void open(@NotNull Player player, @NotNull Category category, int page) {
        int safePage = Math.max(0, Math.min(page, maxPageIndex(category)));
        InfoGuiHolder holder = new InfoGuiHolder(player.getUniqueId(), category.pageId(), safePage);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, title(category.title()));
        holder.setInventory(inventory);

        fillInventory(inventory, Material.BLACK_STAINED_GLASS_PANE, NamedTextColor.BLACK);
        inventory.setItem(TITLE_SLOT, createCategoryOverviewItem(player, category));

        int startIndex = safePage * ITEM_SLOTS.length;
        for (int slotIndex = 0; slotIndex < ITEM_SLOTS.length; slotIndex++) {
            int itemIndex = startIndex + slotIndex;
            if (itemIndex >= category.items().size()) {
                break;
            }
            CollectionItem item = category.items().get(itemIndex);
            inventory.setItem(ITEM_SLOTS[slotIndex], createCollectionListItem(player, category, item));
        }

        String backLabel = hasBridge() ? "返回收集中心" : "返回收藏主页";
        inventory.setItem(BACK_SLOT, InfoGuiItems.createNamedItem(
                Material.ARROW,
                title(backLabel),
                List.of(text("点击" + backLabel + "。", NamedTextColor.GRAY))
        ));
        if (safePage > 0) {
            inventory.setItem(PREVIOUS_PAGE_SLOT, InfoGuiItems.createNamedItem(
                    Material.ARROW,
                    title("上一页"),
                    List.of(text("点击查看第 " + safePage + " 页。", NamedTextColor.GRAY))
            ));
        }
        inventory.setItem(CLOSE_SLOT, InfoGuiItems.createCloseItem());
        if (safePage < maxPageIndex(category)) {
            inventory.setItem(NEXT_PAGE_SLOT, InfoGuiItems.createNamedItem(
                    Material.ARROW,
                    title("下一页"),
                    List.of(text("点击查看第 " + (safePage + 2) + " 页。", NamedTextColor.GRAY))
            ));
        }

        player.openInventory(inventory);
    }

    public static void openDetail(@NotNull Player player, @NotNull Category category, int itemIndex) {
        if (itemIndex < 0 || itemIndex >= category.items().size()) {
            open(player, category);
            return;
        }

        CollectionItem item = category.items().get(itemIndex);
        InfoGuiHolder holder = new InfoGuiHolder(player.getUniqueId(), category.pageId(), DETAIL_PAGE_OFFSET + itemIndex);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, title(item.name() + " 收藏详情"));
        holder.setInventory(inventory);

        fillInventory(inventory, Material.GRAY_STAINED_GLASS_PANE, NamedTextColor.DARK_GRAY);
        inventory.setItem(DETAIL_ITEM_SLOT, createCollectionListItem(player, category, item));

        long collected = collectedAmount(player, category, item);
        CollectionCenterBridge currentBridge = bridge;
        int claimedTier = currentBridge != null
                ? currentBridge.getClaimedTier(player.getUniqueId(), collectionKey(category, item.material()))
                : 0;
        int[] reqs = requirements(category);
        for (int index = 0; index < reqs.length && index < DETAIL_TIER_SLOTS.length; index++) {
            int tier = index + 1;
            inventory.setItem(
                    DETAIL_TIER_SLOTS[index],
                    createTierPane(category, item, tier, collected, claimedTier, currentBridge != null)
            );
        }

        applyDetailStatusBar(inventory, category);

        player.openInventory(inventory);
    }

    public static void openDetailByMaterial(@NotNull Player player, @NotNull Category category, @NotNull Material material) {
        List<CollectionItem> items = category.items();
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).material() == material) {
                openDetail(player, category, index);
                return;
            }
        }
        open(player, category);
    }

    public static void handleClick(@NotNull Player player, @NotNull String pageId, int rawSlot) {
        Category category = categoryForPageId(pageId);
        if (category == null) {
            return;
        }
        handleClick(player, category, rawSlot);
    }

    public static void handleClick(@NotNull Player player, @NotNull Category category, int rawSlot) {
        int encodedPage = currentPage(player, category);
        if (encodedPage >= DETAIL_PAGE_OFFSET) {
            int itemIndex = encodedPage - DETAIL_PAGE_OFFSET;
            if (rawSlot == DETAIL_BACK_SLOT) {
                open(player, category, itemIndex / ITEM_SLOTS.length);
                return;
            }
            if (rawSlot == DETAIL_CLOSE_SLOT) {
                player.closeInventory();
                return;
            }
            int tierIndex = indexOf(DETAIL_TIER_SLOTS, rawSlot);
            if (tierIndex >= 0 && tierIndex < requirements(category).length
                    && itemIndex >= 0 && itemIndex < category.items().size()) {
                CollectionCenterBridge currentBridge = bridge;
                if (currentBridge != null) {
                    CollectionItem item = category.items().get(itemIndex);
                    currentBridge.claimItemTier(player, category, item, tierIndex + 1);
                    openDetail(player, category, itemIndex);
                }
            }
            return;
        }

        if (rawSlot == BACK_SLOT) {
            openHubOrBookshelf(player);
            return;
        }
        if (rawSlot == PREVIOUS_PAGE_SLOT && encodedPage > 0) {
            open(player, category, encodedPage - 1);
            return;
        }
        if (rawSlot == NEXT_PAGE_SLOT && encodedPage < maxPageIndex(category)) {
            open(player, category, encodedPage + 1);
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        int slotIndex = indexOf(ITEM_SLOTS, rawSlot);
        if (slotIndex < 0) {
            return;
        }
        int itemIndex = encodedPage * ITEM_SLOTS.length + slotIndex;
        if (itemIndex < category.items().size()) {
            openDetail(player, category, itemIndex);
        }
    }

    public static @NotNull ItemStack createCategoryOverviewItem(@NotNull Category category) {
        List<Component> lore = new ArrayList<>();
        lore.add(text(category.description(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("收藏物品数: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(category.items().size()), NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(progressLine(0, category.items().size()));
        lore.add(Component.empty());
        lore.add(actionText("点击查看！"));
        return InfoGuiItems.createNamedItem(category.icon(), title(category.title()), lore);
    }

    public static @NotNull ItemStack createCategoryOverviewItem(@NotNull Player player, @NotNull Category category) {
        int totalItems = category.items().size();
        int unlockedItems = 0;
        int completedItems = 0;
        long currentTiers = 0L;
        long maxTiers = (long) totalItems * maxTier(category);
        long claimableTiers = 0L;
        CollectionCenterBridge currentBridge = bridge;

        for (CollectionItem item : category.items()) {
            long amount = collectedAmount(player, category, item);
            if (amount > 0L) {
                unlockedItems++;
            }
            int tier = tierFor(amount, category);
            currentTiers += tier;
            if (tier >= maxTier(category)) {
                completedItems++;
            }
            if (currentBridge != null) {
                int claimed = currentBridge.getClaimedTier(player.getUniqueId(), collectionKey(category, item.material()));
                claimableTiers += Math.max(0, tier - claimed);
            }
        }

        List<Component> lore = new ArrayList<>();
        lore.add(text(category.description(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("已解锁物品: ", NamedTextColor.GRAY)
                .append(Component.text(unlockedItems + "/" + totalItems, NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("已满级物品: ", NamedTextColor.GRAY)
                .append(Component.text(completedItems + "/" + totalItems, NamedTextColor.GREEN))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("分类层级进度: ", NamedTextColor.GRAY)
                .append(Component.text(percentText(currentTiers, maxTiers), NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(progressLine(currentTiers, maxTiers));
        if (currentBridge != null) {
            lore.add(Component.text("可领取奖励层数: ", NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(claimableTiers), claimableTiers > 0 ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY))
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(actionText("点击查看！"));
        return InfoGuiItems.createNamedItem(category.icon(), title(category.title()), lore);
    }

    private static @NotNull ItemStack createCollectionListItem(@NotNull Player player,
                                                               @NotNull Category category,
                                                               @NotNull CollectionItem item) {
        long collected = collectedAmount(player, category, item);
        int maxTier = maxTier(category);
        int currentTier = tierFor(collected, category);
        boolean maxed = currentTier >= maxTier;
        int nextTier = Math.min(maxTier, currentTier + 1);
        int required = requirements(category)[nextTier - 1];
        CollectionCenterBridge currentBridge = bridge;
        int claimedTier = currentBridge != null
                ? currentBridge.getClaimedTier(player.getUniqueId(), collectionKey(category, item.material()))
                : 0;
        int claimable = currentBridge != null ? Math.max(0, currentTier - claimedTier) : 0;

        List<Component> lore = new ArrayList<>();
        lore.add(text(item.description(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("当前等级: ", NamedTextColor.GRAY)
                .append(Component.text(currentTier > 0 ? roman(currentTier) : "未解锁", currentTier > 0 ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY))
                .append(Component.text(" (" + currentTier + "/" + maxTier + ")", NamedTextColor.DARK_GRAY))
                .decoration(TextDecoration.ITALIC, false));

        if (maxed) {
            lore.add(Component.text("进度状态: ", NamedTextColor.GRAY)
                    .append(Component.text("已满级 (100%)", NamedTextColor.GREEN))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(progressLine(collected, required));
        } else {
            lore.add(Component.text("距离 " + item.name() + " " + roman(nextTier) + ": ", NamedTextColor.GRAY)
                    .append(Component.text(percentText(collected, required), NamedTextColor.YELLOW))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(progressLine(collected, required));
            lore.add(Component.empty());
            lore.add(text("下一级解锁奖励:", NamedTextColor.GRAY));
            lore.add(Component.text(" +", NamedTextColor.DARK_GRAY)
                    .append(Component.text(formatNumber(xpReward(category, nextTier)) + " " + category.experienceName(), NamedTextColor.AQUA))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(" +", NamedTextColor.DARK_GRAY)
                    .append(Component.text(mapleXpReward(nextTier) + " 枫叶经验", NamedTextColor.AQUA))
                    .decoration(TextDecoration.ITALIC, false));
            if (isFortuneMilestone(category, required)) {
                lore.add(Component.text(" +", NamedTextColor.DARK_GRAY)
                        .append(Component.text("1 职业采集幸运（全局上限 +30）", NamedTextColor.GREEN))
                        .decoration(TextDecoration.ITALIC, false));
            }
        }

        if (claimable > 0) {
            lore.add(Component.empty());
            lore.add(Component.text("✦ 有 " + claimable + " 层奖励可领取！", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(actionText("点击查看详情与领奖！"));

        ItemStack stack = InfoGuiItems.createNamedItem(item.material(), title(item.name()), lore);
        if (claimable > 0) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.values());
                stack.setItemMeta(meta);
            }
        }
        return stack;
    }

    private static @NotNull ItemStack createTierPane(@NotNull Category category,
                                                     @NotNull CollectionItem item,
                                                     int tier,
                                                     long collected,
                                                     int claimedTier,
                                                     boolean hasBridge) {
        int[] reqs = requirements(category);
        int required = reqs[tier - 1];
        int previousRequired = tier <= 1 ? 0 : reqs[tier - 2];

        Material material;
        NamedTextColor nameColor;
        Component statusLine;
        boolean glow = false;

        if (collected >= required) {
            if (hasBridge && tier > claimedTier) {
                material = Material.LIGHT_BLUE_STAINED_GLASS_PANE;
                nameColor = NamedTextColor.AQUA;
                statusLine = Component.text("✦ 已解锁 — 点击领取本层奖励！", NamedTextColor.GOLD, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false);
                glow = true;
            } else if (hasBridge) {
                material = Material.LIME_STAINED_GLASS_PANE;
                nameColor = NamedTextColor.GREEN;
                statusLine = text("✔ 奖励已领取", NamedTextColor.GREEN);
            } else {
                material = Material.LIME_STAINED_GLASS_PANE;
                nameColor = NamedTextColor.GREEN;
                statusLine = text("✔ 已解锁", NamedTextColor.GREEN);
            }
        } else if (collected >= previousRequired) {
            material = Material.YELLOW_STAINED_GLASS_PANE;
            nameColor = NamedTextColor.YELLOW;
            statusLine = text("进行中", NamedTextColor.YELLOW);
        } else {
            material = Material.RED_STAINED_GLASS_PANE;
            nameColor = NamedTextColor.RED;
            statusLine = text("未解锁", NamedTextColor.RED);
        }

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("状态: ", NamedTextColor.GRAY)
                .append(statusLine)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("当前进度: ", NamedTextColor.GRAY)
                .append(Component.text(percentText(collected, required), NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(progressLine(collected, required));
        lore.add(Component.empty());
        lore.add(text("层级奖励:", NamedTextColor.GRAY));
        lore.add(Component.text(" +", NamedTextColor.DARK_GRAY)
                .append(Component.text(formatNumber(xpReward(category, tier)) + " " + category.experienceName(), NamedTextColor.AQUA))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(" +", NamedTextColor.DARK_GRAY)
                .append(Component.text(mapleXpReward(tier) + " 枫叶经验", NamedTextColor.AQUA))
                .decoration(TextDecoration.ITALIC, false));
        if (isFortuneMilestone(category, required)) {
            lore.add(Component.text(" +", NamedTextColor.DARK_GRAY)
                    .append(Component.text("1 职业采集幸运（自动生效，全局上限 +30）", NamedTextColor.GREEN))
                    .decoration(TextDecoration.ITALIC, false));
        }

        ItemStack pane = new ItemStack(material, Math.max(1, Math.min(64, tier)));
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(item.name() + " " + roman(tier), nameColor)
                    .decoration(TextDecoration.ITALIC, false)
                    .decorate(TextDecoration.BOLD));
            meta.lore(lore);
            if (glow) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            }
            meta.addItemFlags(ItemFlag.values());
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private static boolean isFortuneMilestone(@NotNull Category category, int required) {
        return extended(category) && (required == 5_000 || required == 50_000 || required == 250_000);
    }

    private static void fillInventory(@NotNull Inventory inventory,
                                      @NotNull Material material,
                                      @NotNull NamedTextColor color) {
        ItemStack filler = InfoGuiItems.createFiller(material, color);
        for (int slot = 0; slot < MENU_SIZE; slot++) {
            inventory.setItem(slot, filler.clone());
        }
    }

    private static void applyDetailStatusBar(@NotNull Inventory inventory, @NotNull Category category) {
        ItemStack filler = InfoGuiItems.createFiller(Material.GRAY_STAINED_GLASS_PANE, NamedTextColor.DARK_GRAY);
        for (int slot : DETAIL_STATUS_BAR_SLOTS) {
            inventory.setItem(slot, filler.clone());
        }
        inventory.setItem(DETAIL_BACK_SLOT, InfoGuiItems.createNamedItem(
                Material.ARROW,
                title("返回"),
                List.of(text("点击返回 " + category.title() + "。", NamedTextColor.GRAY))
        ));
        inventory.setItem(DETAIL_CLOSE_SLOT, InfoGuiItems.createCloseItem());
    }

    private static int currentPage(@NotNull Player player, @NotNull Category category) {
        Inventory inventory = player.getOpenInventory().getTopInventory();
        if (inventory.getHolder() instanceof InfoGuiHolder holder && category.pageId().equals(holder.pageId())) {
            return holder.page();
        }
        return 0;
    }

    private static int maxPageIndex(@NotNull Category category) {
        return Math.max(0, (category.items().size() - 1) / ITEM_SLOTS.length);
    }

    public static @NotNull String collectionKey(@NotNull Category category, @NotNull Material material) {
        return category.pageId() + ":" + material.name();
    }

    public static boolean hasCollectionMaterial(@NotNull Category category, @NotNull Material material) {
        return category.items().stream().anyMatch(item -> item.material() == material);
    }

    public static @Nullable CollectionItem findItem(@NotNull Category category, @NotNull Material material) {
        for (CollectionItem item : category.items()) {
            if (item.material() == material) {
                return item;
            }
        }
        return null;
    }

    public static boolean extended(@NotNull Category category) {
        return category == Category.FARMING || category == Category.MINING;
    }

    public static int maxTier(@NotNull Category category) {
        return requirements(category).length;
    }

    public static int requirementForTier(@NotNull Category category, int tier) {
        int[] reqs = requirements(category);
        if (tier <= 0) {
            return 0;
        }
        return reqs[Math.min(tier, reqs.length) - 1];
    }

    private static int[] requirements(@NotNull Category category) {
        return extended(category)
                ? maple.underground_state.professions.ProfessionMath.COLLECTION_REQUIREMENTS
                : TIER_REQUIREMENTS;
    }

    public static int xpReward(@NotNull Category category, int tier) {
        return maple.underground_state.professions.ProfessionMath.collectionXp(tier, extended(category));
    }

    public static int tierFor(long collected, @NotNull Category category) {
        int tier = 0;
        for (int requirement : requirements(category)) {
            if (collected < requirement) {
                break;
            }
            tier++;
        }
        return tier;
    }

    private static int percent(long current, long required) {
        if (required <= 0L) {
            return 100;
        }
        return (int) Math.max(0L, Math.min(100L, (current * 100L) / required));
    }

    private static @NotNull String percentText(long current, long required) {
        if (required <= 0L) {
            return "100%";
        }
        double value = Math.max(0D, Math.min(100D, current * 100D / required));
        if (value >= 10D || value == Math.rint(value)) {
            return String.format(Locale.ROOT, "%.0f%%", value);
        }
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    private static @NotNull Component progressLine(long current, long required) {
        return InfoGuiItems.createColoredProgressBar(percent(current, required))
                .append(Component.text(" ").decoration(TextDecoration.STRIKETHROUGH, false))
                .append(Component.text(formatNumber(current), NamedTextColor.YELLOW).decoration(TextDecoration.STRIKETHROUGH, false))
                .append(Component.text("/", NamedTextColor.GOLD).decoration(TextDecoration.STRIKETHROUGH, false))
                .append(Component.text(formatNumber(required), NamedTextColor.YELLOW).decoration(TextDecoration.STRIKETHROUGH, false))
                .decoration(TextDecoration.ITALIC, false);
    }

    private static @Nullable Category categoryForPageId(@NotNull String pageId) {
        return Arrays.stream(Category.values())
                .filter(category -> category.pageId().equals(pageId))
                .findFirst()
                .orElse(null);
    }

    private static int indexOf(int[] values, int value) {
        for (int index = 0; index < values.length; index++) {
            if (values[index] == value) {
                return index;
            }
        }
        return -1;
    }

    public static int mapleXpReward(int tier) {
        return Math.max(1, tier / 2);
    }

    public static @NotNull String roman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            case 11 -> "XI";
            case 12 -> "XII";
            default -> String.valueOf(number);
        };
    }

    public static @NotNull String formatNumber(long value) {
        long abs = Math.abs(value);
        if (abs >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000D);
        }
        if (abs >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000D);
        }
        if (abs >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fk", value / 1_000D);
        }
        return String.valueOf(value);
    }

    private static @NotNull Component title(@NotNull String text) {
        return Component.text(text, NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
                .decorate(TextDecoration.BOLD);
    }

    private static @NotNull Component text(@NotNull String text, @NotNull NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static @NotNull Component actionText(@NotNull String text) {
        return Component.text(text, NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)
                .decorate(TextDecoration.BOLD);
    }

    public enum Category {
        FARMING(
                HOE_PAGE_ID,
                "锄头收藏",
                Material.COPPER_HOE,
                "农作物、种子和畜牧材料收藏。",
                "农耕经验",
                List.of(
                        item(Material.TORCHFLOWER_SEEDS, "火把花种子", "收集稀有的远古花种。"),
                        item(Material.PITCHER_POD, "瓶子草荚果", "收集可以培育瓶子草的荚果。"),
                        item(Material.WHEAT_SEEDS, "小麦种子", "收集耕地里最基础的作物种子。"),
                        item(Material.WHEAT, "小麦", "收集成熟小麦并推进农耕收藏。"),
                        item(Material.CARROT, "胡萝卜", "收集可食用也可种植的胡萝卜。"),
                        item(Material.POTATO, "土豆", "收集常见的田地作物。"),
                        item(Material.BEETROOT, "甜菜根", "收集成熟甜菜根作物。"),
                        item(Material.MELON, "西瓜", "收集完整西瓜方块。"),
                        item(Material.PUMPKIN, "南瓜", "收集田地里的完整南瓜。"),
                        item(Material.SUGAR_CANE, "甘蔗", "收集河岸边生长的甘蔗。"),
                        item(Material.BAMBOO, "竹子", "收集快速生长的竹类作物。"),
                        item(Material.CACTUS, "仙人掌", "收集沙漠里生长的多刺作物。"),
                        item(Material.COCOA_BEANS, "可可豆", "收集丛林木上的可可豆。"),
                        item(Material.SWEET_BERRIES, "甜浆果", "收集浆果丛结出的果实。"),
                        item(Material.BROWN_MUSHROOM, "棕色蘑菇", "收集可种植和烹饪的蘑菇。"),
                        item(Material.RED_MUSHROOM, "红色蘑菇", "收集鲜红的蘑菇资源。"),
                        item(Material.NETHER_WART, "地狱疣", "收集灵魂沙上生长的炼药作物。"),
                        item(Material.KELP, "海带", "收集水下生长的农作物。"),
                        item(Material.LEATHER, "皮革", "收集畜牧动物产出的皮革。"),
                        item(Material.RABBIT_HIDE, "兔子皮", "收集兔子掉落的皮革材料。"),
                        item(Material.RABBIT_FOOT, "兔子脚", "收集兔子的稀有畜牧掉落。"),
                        item(Material.BEEF, "生牛肉", "收集牛产出的肉类资源。"),
                        item(Material.PORKCHOP, "生猪排", "收集猪产出的肉类资源。"),
                        item(Material.MUTTON, "生羊肉", "收集羊产出的肉类资源。"),
                        item(Material.CHICKEN, "生鸡肉", "收集鸡产出的肉类资源。"),
                        item(Material.RABBIT, "生兔肉", "收集兔子产出的肉类资源。"),
                        item(Material.FEATHER, "羽毛", "收集鸡掉落的畜牧材料。")
                )
        ),
        MINING(
                PICKAXE_PAGE_ID,
                "镐子收藏",
                Material.COPPER_PICKAXE,
                "沙子、泥土、石头和矿物收藏。",
                "挖矿经验",
                List.of(
                        item(Material.SAND, "沙子", "收集最常见的松散方块。"),
                        item(Material.GRAVEL, "砂砾", "收集会掉落燧石的松散方块。"),
                        item(Material.DIRT, "泥土", "收集地表最基础的方块。"),
                        item(Material.CLAY, "黏土", "收集水边生成的黏土块。"),
                        item(Material.STONE, "石头", "收集地下最基础的岩石。"),
                        item(Material.OBSIDIAN, "黑曜石", "收集高硬度火山玻璃。"),
                        item(Material.GLOWSTONE, "荧石", "收集下界会发光的矿物。"),
                        item(Material.COAL, "煤炭", "收集基础燃料矿物。"),
                        item(Material.RAW_COPPER, "粗铜", "收集铜矿石产出的原矿。"),
                        item(Material.COPPER_INGOT, "铜锭", "收集熔炼后的铜。"),
                        item(Material.RAW_IRON, "粗铁", "收集铁矿石产出的原矿。"),
                        item(Material.IRON_INGOT, "铁锭", "收集熔炼后的铁。"),
                        item(Material.RAW_GOLD, "粗金", "收集金矿石产出的原矿。"),
                        item(Material.GOLD_INGOT, "金锭", "收集熔炼后的金。"),
                        item(Material.REDSTONE, "红石粉", "收集红石矿产出的粉末。"),
                        item(Material.LAPIS_LAZULI, "青金石", "收集附魔常用的矿物。"),
                        item(Material.QUARTZ, "下界石英", "收集下界石英矿产物。"),
                        item(Material.AMETHYST_SHARD, "紫水晶碎片", "收集紫晶簇产出的碎片。"),
                        item(Material.EMERALD, "绿宝石", "收集稀有交易矿物。"),
                        item(Material.DIAMOND, "钻石", "收集稀有而坚硬的矿物。"),
                        item(Material.NETHERITE_SCRAP, "下界合金碎片", "收集远古残骸加工后的材料。")
                )
        ),
        COMBAT(
                SWORD_PAGE_ID,
                "剑收藏",
                Material.COPPER_SWORD,
                "怪物战利品和战斗掉落收藏。",
                "战斗经验",
                List.of(
                        item(Material.ROTTEN_FLESH, "腐肉", "收集僵尸类怪物掉落的战利品。"),
                        item(Material.BONE, "骨头", "收集骷髅类怪物掉落的骨头。"),
                        item(Material.ARROW, "箭", "收集远程怪物留下的箭矢。"),
                        item(Material.STRING, "线", "收集蜘蛛类怪物掉落的丝线。"),
                        item(Material.SPIDER_EYE, "蜘蛛眼", "收集蜘蛛掉落的炼药材料。"),
                        item(Material.GUNPOWDER, "火药", "收集苦力怕和女巫相关掉落。"),
                        item(Material.SLIME_BALL, "黏液球", "收集史莱姆掉落的材料。"),
                        item(Material.ENDER_PEARL, "末影珍珠", "收集末影人掉落的珍珠。"),
                        item(Material.PHANTOM_MEMBRANE, "幻翼膜", "收集幻翼掉落的材料。"),
                        item(Material.INK_SAC, "墨囊", "收集鱿鱼掉落的墨囊。"),
                        item(Material.BLAZE_ROD, "烈焰棒", "收集烈焰人掉落的核心材料。"),
                        item(Material.MAGMA_CREAM, "岩浆膏", "收集岩浆怪相关战利品。"),
                        item(Material.GHAST_TEAR, "恶魂之泪", "收集恶魂掉落的稀有材料。"),
                        item(Material.SHULKER_SHELL, "潜影壳", "收集潜影贝掉落的外壳。"),
                        item(Material.BREEZE_ROD, "旋风棒", "收集试炼密室里的稀有掉落。"),
                        item(Material.NETHER_STAR, "下界之星", "收集强大 Boss 掉落的核心。")
                )
        ),
        FORAGING(
                AXE_PAGE_ID,
                "斧头收藏",
                Material.COPPER_AXE,
                "原木、菌柄和木材相关收藏。",
                "伐木经验",
                List.of(
                        item(Material.OAK_LOG, "橡木原木", "收集最常见的橡木原木。"),
                        item(Material.SPRUCE_LOG, "云杉原木", "收集寒冷群系常见的云杉原木。"),
                        item(Material.BIRCH_LOG, "白桦原木", "收集浅色的白桦原木。"),
                        item(Material.JUNGLE_LOG, "丛林原木", "收集丛林树木的原木。"),
                        item(Material.ACACIA_LOG, "金合欢原木", "收集热带草原的原木。"),
                        item(Material.DARK_OAK_LOG, "深色橡木原木", "收集深色橡木森林的原木。"),
                        item(Material.MANGROVE_LOG, "红树原木", "收集红树林里的原木。"),
                        item(Material.CHERRY_LOG, "樱花原木", "收集樱花树的粉色原木。"),
                        item(Material.CRIMSON_STEM, "绯红菌柄", "收集下界绯红森林的菌柄。"),
                        item(Material.WARPED_STEM, "诡异菌柄", "收集下界诡异森林的菌柄。")
                )
        ),
        FISHING(
                FISHING_ROD_PAGE_ID,
                "钓鱼收藏",
                Material.FISHING_ROD,
                "通过钓鱼获得的鱼类收藏。",
                "钓鱼经验",
                List.of(
                        item(Material.COD, "鳕鱼", "收集最常见的钓鱼收获。"),
                        item(Material.SALMON, "鲑鱼", "收集河流与海洋里的鲑鱼。"),
                        item(Material.TROPICAL_FISH, "热带鱼", "收集颜色丰富的热带鱼。"),
                        item(Material.PUFFERFISH, "河豚", "收集危险但稀有的鱼类。"),
                        item(Material.COOKED_COD, "熟鳕鱼", "收集加工后的鳕鱼。"),
                        item(Material.COOKED_SALMON, "熟鲑鱼", "收集加工后的鲑鱼。")
                )
        );

        private final String pageId;
        private final String title;
        private final Material icon;
        private final String description;
        private final String experienceName;
        private final List<CollectionItem> items;

        Category(@NotNull String pageId,
                 @NotNull String title,
                 @NotNull Material icon,
                 @NotNull String description,
                 @NotNull String experienceName,
                 @NotNull List<CollectionItem> items) {
            this.pageId = pageId;
            this.title = title;
            this.icon = icon;
            this.description = description;
            this.experienceName = experienceName;
            this.items = items;
        }

        public @NotNull String pageId() {
            return pageId;
        }

        public @NotNull String title() {
            return title;
        }

        public @NotNull Material icon() {
            return icon;
        }

        public @NotNull String description() {
            return description;
        }

        public @NotNull String experienceName() {
            return experienceName;
        }

        public @NotNull List<CollectionItem> items() {
            return items;
        }
    }

    private static long collectedAmount(@NotNull Player player,
                                        @NotNull Category category,
                                        @NotNull CollectionItem item) {
        UndergroundState plugin = UndergroundState.getInstance();
        if (plugin != null && plugin.getCollectionProgressManager() != null) {
            return plugin.getCollectionProgressManager().getAmount(
                    player.getUniqueId(),
                    collectionKey(category, item.material())
            );
        }
        return 0L;
    }

    public record CollectionItem(@NotNull Material material,
                                 @NotNull String name,
                                 @NotNull String description) {
    }

    private static @NotNull CollectionItem item(@NotNull Material material,
                                                @NotNull String name,
                                                @NotNull String description) {
        return new CollectionItem(material, name, description);
    }
}
