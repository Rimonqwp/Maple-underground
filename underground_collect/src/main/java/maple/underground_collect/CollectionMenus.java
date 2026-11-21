package maple.underground_collect;

import maple.underground_state.UndergroundState;
import maple.underground_state.collection.CollectionProgressManager;
import maple.underground_state.gui.CollectionInfoGui;
import maple.underground_state.gui.InfoGuiItems;
import maple.underground_state.gui.MainInfoGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class CollectionMenus {

    private static final int MENU_SIZE = 54;
    private static final int TITLE_SLOT = 4;
    private static final int BACK_SLOT = 45;
    private static final int PREV_PAGE_SLOT = 48;
    private static final int CLOSE_SLOT = 49;
    private static final int NEXT_PAGE_SLOT = 50;

    private static final int HUB_CLAIM_SLOT = 28;
    private static final int HUB_MISSING_SLOT = 30;
    private static final int HUB_LEADERBOARD_SLOT = 32;
    private static final int HUB_GUIDE_SLOT = 34;
    private static final int[] CATEGORY_SLOTS = {20, 21, 22, 23, 24};

    private static final int LEADERBOARD_TITLE_SLOT = 4;
    private static final int LEADERBOARD_OVERALL_SLOT = 19;
    private static final int[] LEADERBOARD_CATEGORY_SLOTS = {20, 21, 22, 23, 24};
    private static final int[] LEADERBOARD_ENTRY_SLOTS = {
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private static final int MISSING_CLAIM_ALL_SLOT = 6;
    private static final int[] MISSING_ENTRY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private static final int GUIDE_TITLE_SLOT = 4;
    private static final int GUIDE_RULES_SLOT = 19;
    private static final int GUIDE_REWARD_SLOT = 21;
    private static final int GUIDE_FORTUNE_SLOT = 23;
    private static final int GUIDE_COMMANDS_SLOT = 25;
    private static final int[] GUIDE_CATEGORY_SLOTS = {29, 30, 31, 32, 33};

    private final UndergroundState state;
    private final CollectionDataRepository repository;
    private final CollectionRewardService rewardService;

    CollectionMenus(@NotNull UndergroundState state,
                    @NotNull CollectionDataRepository repository,
                    @NotNull CollectionRewardService rewardService) {
        this.state = state;
        this.repository = repository;
        this.rewardService = rewardService;
    }

    void openHub(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        if (!repository.isClaimsLoaded(uuid)) {
            player.sendMessage(Component.text("正在加载你的收藏数据...", NamedTextColor.GRAY));
            repository.preloadClaimsAsync(uuid, () -> {
                if (player.isOnline()) {
                    renderHub(player);
                }
            });
            return;
        }
        renderHub(player);
    }

    private void renderHub(@NotNull Player player) {
        CollectionSnapshot snapshot = snapshot(player.getUniqueId());
        CollectionMenuHolder holder = new CollectionMenuHolder(player.getUniqueId(), CollectionMenuType.HUB, "");
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, title("收集中心"));
        holder.setInventory(inventory);
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE, NamedTextColor.BLACK);
        inventory.setItem(TITLE_SLOT, createHubTitle(snapshot));

        CollectionInfoGui.Category[] categories = CollectionInfoGui.Category.values();
        for (int index = 0; index < CATEGORY_SLOTS.length && index < categories.length; index++) {
            inventory.setItem(CATEGORY_SLOTS[index], createCategoryItem(snapshot, categories[index]));
        }

        inventory.setItem(HUB_CLAIM_SLOT, createClaimItem(snapshot));
        inventory.setItem(HUB_MISSING_SLOT, createMissingItem(snapshot));
        inventory.setItem(HUB_LEADERBOARD_SLOT, createLeaderboardItem());
        inventory.setItem(HUB_GUIDE_SLOT, createGuideItem());
        inventory.setItem(BACK_SLOT, InfoGuiItems.createNamedItem(
                Material.ARROW,
                title("返回主页"),
                List.of(text("回到个人主信息菜单。", NamedTextColor.GRAY))
        ));
        inventory.setItem(CLOSE_SLOT, InfoGuiItems.createCloseItem());
        player.openInventory(inventory);
    }

    void openLeaderboard(@NotNull Player player, @Nullable CollectionInfoGui.Category category) {
        openLeaderboard(player, category, 0);
    }

    void openLeaderboard(@NotNull Player player, @Nullable CollectionInfoGui.Category category, int page) {
        CollectionProgressManager progressManager = state.getCollectionProgressManager();
        Map<UUID, Map<String, Long>> onlineSnapshots = progressManager != null
                ? progressManager.onlineSnapshots()
                : Map.of();
        repository.loadLeaderboardAsync(category, onlineSnapshots, entries -> {
            if (player.isOnline()) {
                renderLeaderboard(player, category, page, entries);
            }
        });
    }

    private void renderLeaderboard(@NotNull Player player,
                                   @Nullable CollectionInfoGui.Category category,
                                   int requestedPage,
                                   @NotNull List<CollectionDataRepository.LeaderboardEntry> entries) {
        CollectionSnapshot snapshot = snapshot(player.getUniqueId());
        int pageSize = LEADERBOARD_ENTRY_SLOTS.length;
        int maxPage = entries.isEmpty() ? 0 : (entries.size() - 1) / pageSize;
        int safePage = Math.max(0, Math.min(requestedPage, maxPage));
        String scope = category == null ? "overall" : category.pageId();

        CollectionMenuHolder holder = new CollectionMenuHolder(player.getUniqueId(), CollectionMenuType.LEADERBOARD, scope, safePage);
        String pageSuffix = maxPage > 0 ? " (" + (safePage + 1) + "/" + (maxPage + 1) + ")" : "";
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, title(scopeName(category) + "排行榜" + pageSuffix));
        holder.setInventory(inventory);
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE, NamedTextColor.DARK_GRAY);

        int selfRank = findRank(entries, player.getUniqueId());
        inventory.setItem(LEADERBOARD_TITLE_SLOT, createLeaderboardTitle(snapshot, category, selfRank, entries.size()));
        inventory.setItem(LEADERBOARD_OVERALL_SLOT, createOverallScopeButton(category == null));

        CollectionInfoGui.Category[] categories = CollectionInfoGui.Category.values();
        for (int index = 0; index < LEADERBOARD_CATEGORY_SLOTS.length && index < categories.length; index++) {
            inventory.setItem(LEADERBOARD_CATEGORY_SLOTS[index], createScopeButton(categories[index], category));
        }

        int startIndex = safePage * pageSize;
        for (int slotIndex = 0; slotIndex < pageSize; slotIndex++) {
            int entryIndex = startIndex + slotIndex;
            if (entryIndex >= entries.size()) {
                break;
            }
            inventory.setItem(
                    LEADERBOARD_ENTRY_SLOTS[slotIndex],
                    createLeaderboardEntryItem(entryIndex + 1, entries.get(entryIndex), category, player.getUniqueId())
            );
        }

        inventory.setItem(BACK_SLOT, InfoGuiItems.createNamedItem(
                Material.ARROW,
                title("返回收集中心"),
                List.of(text("回到收集中心首页。", NamedTextColor.GRAY))
        ));
        if (safePage > 0) {
            inventory.setItem(PREV_PAGE_SLOT, InfoGuiItems.createNamedItem(
                    Material.ARROW,
                    title("上一页"),
                    List.of(text("切换到第 " + safePage + " 页。", NamedTextColor.GRAY))
            ));
        }
        inventory.setItem(CLOSE_SLOT, InfoGuiItems.createCloseItem());
        if (safePage < maxPage) {
            inventory.setItem(NEXT_PAGE_SLOT, InfoGuiItems.createNamedItem(
                    Material.ARROW,
                    title("下一页"),
                    List.of(text("切换到第 " + (safePage + 2) + " 页。", NamedTextColor.GRAY))
            ));
        }
        player.openInventory(inventory);
    }

    void openMissing(@NotNull Player player, @Nullable CollectionInfoGui.Category category) {
        openMissing(player, category, 0);
    }

    void openMissing(@NotNull Player player, @Nullable CollectionInfoGui.Category category, int page) {
        UUID uuid = player.getUniqueId();
        if (!repository.isClaimsLoaded(uuid)) {
            player.sendMessage(Component.text("正在加载你的收藏数据...", NamedTextColor.GRAY));
            repository.preloadClaimsAsync(uuid, () -> {
                if (player.isOnline()) {
                    renderMissing(player, category, page);
                }
            });
            return;
        }
        renderMissing(player, category, page);
    }

    private void renderMissing(@NotNull Player player,
                               @Nullable CollectionInfoGui.Category category,
                               int requestedPage) {
        List<CollectionRewardService.MissingEntry> entries = rewardService.missingEntries(player.getUniqueId(), category);
        int pageSize = MISSING_ENTRY_SLOTS.length;
        int maxPage = entries.isEmpty() ? 0 : (entries.size() - 1) / pageSize;
        int safePage = Math.max(0, Math.min(requestedPage, maxPage));
        String scope = category == null ? "overall" : category.pageId();

        int claimableItems = 0;
        int claimableTiers = 0;
        for (CollectionRewardService.MissingEntry entry : entries) {
            if (entry.claimableTiers() > 0) {
                claimableItems++;
                claimableTiers += entry.claimableTiers();
            }
        }

        CollectionMenuHolder holder = new CollectionMenuHolder(player.getUniqueId(), CollectionMenuType.MISSING, scope, safePage);
        String headerName = (category == null ? "待领取与推进目标" : category.title() + " · 待领取与目标")
                + (maxPage > 0 ? " (" + (safePage + 1) + "/" + (maxPage + 1) + ")" : "");
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, title(headerName));
        holder.setInventory(inventory);
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE, NamedTextColor.BLACK);

        inventory.setItem(TITLE_SLOT, InfoGuiItems.createNamedItem(
                Material.HOPPER,
                title(category == null ? "待领取与推进目标" : category.title() + " 目标清单"),
                List.of(
                        text("发光物品代表有未领取奖励，排在最前。", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("待领取收藏项: ", NamedTextColor.GRAY)
                                .append(Component.text(String.valueOf(claimableItems), NamedTextColor.GOLD))
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("待领取总层数: ", NamedTextColor.GRAY)
                                .append(Component.text(String.valueOf(claimableTiers), NamedTextColor.YELLOW))
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("未满级项目数: ", NamedTextColor.GRAY)
                                .append(Component.text(String.valueOf(entries.size()), NamedTextColor.AQUA))
                                .decoration(TextDecoration.ITALIC, false)
                )
        ));

        if (claimableTiers > 0) {
            ItemStack claimBtn = InfoGuiItems.createNamedItem(
                    Material.AMETHYST_SHARD,
                    title("一键领取当前范围奖励"),
                    List.of(
                            text("立即领取 " + claimableTiers + " 层待领取奖励。", NamedTextColor.GOLD),
                            text("点击后自动发放枫叶经验。", NamedTextColor.GRAY)
                    )
            );
            applyGlow(claimBtn);
            inventory.setItem(MISSING_CLAIM_ALL_SLOT, claimBtn);
        }

        int startIndex = safePage * pageSize;
        for (int slotIndex = 0; slotIndex < pageSize; slotIndex++) {
            int entryIndex = startIndex + slotIndex;
            if (entryIndex >= entries.size()) {
                break;
            }
            inventory.setItem(MISSING_ENTRY_SLOTS[slotIndex], createMissingEntryItem(entries.get(entryIndex)));
        }

        inventory.setItem(BACK_SLOT, InfoGuiItems.createNamedItem(
                Material.ARROW,
                title("返回收集中心"),
                List.of(text("回到收集中心首页。", NamedTextColor.GRAY))
        ));
        if (safePage > 0) {
            inventory.setItem(PREV_PAGE_SLOT, InfoGuiItems.createNamedItem(
                    Material.ARROW,
                    title("上一页"),
                    List.of(text("切换到第 " + safePage + " 页。", NamedTextColor.GRAY))
            ));
        }
        inventory.setItem(CLOSE_SLOT, InfoGuiItems.createCloseItem());
        if (safePage < maxPage) {
            inventory.setItem(NEXT_PAGE_SLOT, InfoGuiItems.createNamedItem(
                    Material.ARROW,
                    title("下一页"),
                    List.of(text("切换到第 " + (safePage + 2) + " 页。", NamedTextColor.GRAY))
            ));
        }
        player.openInventory(inventory);
    }

    void openGuide(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        if (!repository.isClaimsLoaded(uuid)) {
            player.sendMessage(Component.text("正在加载你的收藏数据...", NamedTextColor.GRAY));
            repository.preloadClaimsAsync(uuid, () -> {
                if (player.isOnline()) {
                    renderGuide(player);
                }
            });
            return;
        }
        renderGuide(player);
    }

    private void renderGuide(@NotNull Player player) {
        CollectionSnapshot snapshot = snapshot(player.getUniqueId());
        CollectionMenuHolder holder = new CollectionMenuHolder(player.getUniqueId(), CollectionMenuType.GUIDE, "");
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, title("收藏指南"));
        holder.setInventory(inventory);
        fill(inventory, Material.BLUE_STAINED_GLASS_PANE, NamedTextColor.BLUE);
        inventory.setItem(GUIDE_TITLE_SLOT, createGuideTitle(snapshot));

        inventory.setItem(GUIDE_RULES_SLOT, InfoGuiItems.createNamedItem(
                Material.IRON_PICKAXE,
                title("1. 获取方式与防刷规则"),
                List.of(
                        text("• 农耕/挖矿：在职业启用世界采集作物、矿石或击杀畜牧。", NamedTextColor.GRAY),
                        text("• 战斗：亲手击杀怪物掉落的战利品自动计入。", NamedTextColor.GRAY),
                        text("• 伐木：砍伐自然生成的原木与菌柄（放置方块不计入）。", NamedTextColor.GRAY),
                        text("• 钓鱼：通过钓鱼收获鱼类，或熔炉烤制熟鳕鱼/熟鲑鱼。", NamedTextColor.GRAY),
                        Component.empty(),
                        text("提示：丢弃在地上再拾取的物品不会增加收藏进度。", NamedTextColor.YELLOW)
                )
        ));

        inventory.setItem(GUIDE_REWARD_SLOT, InfoGuiItems.createNamedItem(
                Material.EXPERIENCE_BOTTLE,
                title("2. 层级与奖励领取"),
                List.of(
                        text("• 战斗 / 伐木 / 钓鱼：每项最高 9 级（上限 50,000）。", NamedTextColor.GRAY),
                        text("• 农耕 / 挖矿：每项最高 12 级（上限 500,000）。", NamedTextColor.GRAY),
                        text("• 收藏层级奖励仅发放枫叶经验，不再发放技能经验。", NamedTextColor.AQUA),
                        text("• 可在首页一键领取，或点进物品详情页逐层领取。", NamedTextColor.GREEN)
                )
        ));

        inventory.setItem(GUIDE_FORTUNE_SLOT, InfoGuiItems.createNamedItem(
                Material.EMERALD,
                title("3. 职业采集幸运加成"),
                List.of(
                        text("农耕与挖矿收藏会自动提供职业采集幸运：", NamedTextColor.GRAY),
                        text("• 单项达到 5,000   → +1 采集幸运", NamedTextColor.AQUA),
                        text("• 单项达到 50,000  → 再 +1 采集幸运", NamedTextColor.AQUA),
                        text("• 单项达到 250,000 → 再 +1 采集幸运", NamedTextColor.AQUA),
                        Component.empty(),
                        text("每个职业通过收藏最高可叠加 +30 幸运。", NamedTextColor.GOLD)
                )
        ));

        inventory.setItem(GUIDE_COMMANDS_SLOT, InfoGuiItems.createNamedItem(
                Material.BOOK,
                title("4. 常用命令速查"),
                List.of(
                        text("• /collect             打开收集中心首页", NamedTextColor.YELLOW),
                        text("• /collect claim [分类] 一键领取奖励", NamedTextColor.YELLOW),
                        text("• /collect missing [分类] 查看待领与目标面板", NamedTextColor.YELLOW),
                        text("• /collect leaderboard  查看全服与分类榜单", NamedTextColor.YELLOW),
                        text("• /collect open <分类>  直达指定收藏分类", NamedTextColor.YELLOW)
                )
        ));

        CollectionInfoGui.Category[] categories = CollectionInfoGui.Category.values();
        for (int index = 0; index < GUIDE_CATEGORY_SLOTS.length && index < categories.length; index++) {
            inventory.setItem(GUIDE_CATEGORY_SLOTS[index], createGuideCategoryItem(snapshot, categories[index]));
        }

        inventory.setItem(BACK_SLOT, InfoGuiItems.createNamedItem(
                Material.ARROW,
                title("返回收集中心"),
                List.of(text("回到收集中心首页。", NamedTextColor.GRAY))
        ));
        inventory.setItem(CLOSE_SLOT, InfoGuiItems.createCloseItem());
        player.openInventory(inventory);
    }

    void handleClick(@NotNull Player player, @NotNull CollectionMenuHolder holder, int rawSlot) {
        switch (holder.type()) {
            case HUB -> handleHubClick(player, rawSlot);
            case LEADERBOARD -> handleLeaderboardClick(player, holder, rawSlot);
            case GUIDE -> handleGuideClick(player, rawSlot);
            case MISSING -> handleMissingClick(player, holder, rawSlot);
        }
    }

    private void handleHubClick(@NotNull Player player, int rawSlot) {
        CollectionInfoGui.Category[] categories = CollectionInfoGui.Category.values();
        for (int index = 0; index < CATEGORY_SLOTS.length && index < categories.length; index++) {
            if (rawSlot == CATEGORY_SLOTS[index]) {
                CollectionInfoGui.open(player, categories[index]);
                return;
            }
        }
        if (rawSlot == HUB_CLAIM_SLOT) {
            CollectionRewardService.ClaimSummary summary = rewardService.claimAll(player);
            rewardService.sendClaimFeedback(player, summary);
            renderHub(player);
            return;
        }
        if (rawSlot == HUB_MISSING_SLOT) {
            openMissing(player, null, 0);
            return;
        }
        if (rawSlot == HUB_LEADERBOARD_SLOT) {
            openLeaderboard(player, null, 0);
            return;
        }
        if (rawSlot == HUB_GUIDE_SLOT) {
            openGuide(player);
            return;
        }
        if (rawSlot == BACK_SLOT) {
            MainInfoGui.open(player);
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
        }
    }

    private void handleLeaderboardClick(@NotNull Player player, @NotNull CollectionMenuHolder holder, int rawSlot) {
        CollectionInfoGui.Category currentCategory = parseScope(holder.scope());
        if (rawSlot == LEADERBOARD_TITLE_SLOT || rawSlot == LEADERBOARD_OVERALL_SLOT) {
            openLeaderboard(player, null, 0);
            return;
        }
        CollectionInfoGui.Category[] categories = CollectionInfoGui.Category.values();
        for (int index = 0; index < LEADERBOARD_CATEGORY_SLOTS.length && index < categories.length; index++) {
            if (rawSlot == LEADERBOARD_CATEGORY_SLOTS[index]) {
                openLeaderboard(player, categories[index], 0);
                return;
            }
        }
        if (rawSlot == PREV_PAGE_SLOT && holder.page() > 0) {
            openLeaderboard(player, currentCategory, holder.page() - 1);
            return;
        }
        if (rawSlot == NEXT_PAGE_SLOT) {
            openLeaderboard(player, currentCategory, holder.page() + 1);
            return;
        }
        if (rawSlot == BACK_SLOT) {
            openHub(player);
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
        }
    }

    private void handleMissingClick(@NotNull Player player, @NotNull CollectionMenuHolder holder, int rawSlot) {
        CollectionInfoGui.Category currentCategory = parseScope(holder.scope());
        if (rawSlot == MISSING_CLAIM_ALL_SLOT) {
            CollectionRewardService.ClaimSummary summary = currentCategory == null
                    ? rewardService.claimAll(player)
                    : rewardService.claimCategory(player, currentCategory);
            rewardService.sendClaimFeedback(player, summary);
            renderMissing(player, currentCategory, holder.page());
            return;
        }
        if (rawSlot == BACK_SLOT) {
            openHub(player);
            return;
        }
        if (rawSlot == PREV_PAGE_SLOT && holder.page() > 0) {
            renderMissing(player, currentCategory, holder.page() - 1);
            return;
        }
        if (rawSlot == NEXT_PAGE_SLOT) {
            renderMissing(player, currentCategory, holder.page() + 1);
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        int slotIndex = indexOf(MISSING_ENTRY_SLOTS, rawSlot);
        if (slotIndex < 0) {
            return;
        }
        List<CollectionRewardService.MissingEntry> entries = rewardService.missingEntries(player.getUniqueId(), currentCategory);
        int entryIndex = holder.page() * MISSING_ENTRY_SLOTS.length + slotIndex;
        if (entryIndex >= 0 && entryIndex < entries.size()) {
            CollectionRewardService.MissingEntry entry = entries.get(entryIndex);
            CollectionInfoGui.openDetailByMaterial(player, entry.category(), entry.item().material());
        }
    }

    private void handleGuideClick(@NotNull Player player, int rawSlot) {
        if (rawSlot == GUIDE_TITLE_SLOT) {
            renderGuide(player);
            return;
        }
        CollectionInfoGui.Category[] categories = CollectionInfoGui.Category.values();
        for (int index = 0; index < GUIDE_CATEGORY_SLOTS.length && index < categories.length; index++) {
            if (rawSlot == GUIDE_CATEGORY_SLOTS[index]) {
                CollectionInfoGui.open(player, categories[index]);
                return;
            }
        }
        if (rawSlot == BACK_SLOT) {
            openHub(player);
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
        }
    }

    @NotNull
    private CollectionSnapshot snapshot(@NotNull UUID uuid) {
        CollectionProgressManager progressManager = state.getCollectionProgressManager();
        Map<String, Long> progress = progressManager != null ? progressManager.snapshot(uuid) : Map.of();
        Map<String, Integer> claims = repository.getCachedClaims(uuid);
        return new CollectionSnapshot(progress, claims);
    }

    @NotNull
    private ItemStack createHubTitle(@NotNull CollectionSnapshot snapshot) {
        List<Component> lore = new ArrayList<>();
        lore.add(text("这里是收藏玩法的统一中心。", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("当前总收藏等级: ", NamedTextColor.GRAY)
                .append(Component.text(snapshot.currentTiers() + "/" + snapshot.maxTiers(), NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("已解锁收藏项: ", NamedTextColor.GRAY)
                .append(Component.text(snapshot.unlockedItems() + "/" + snapshot.totalItems(), NamedTextColor.AQUA))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("已满级收藏项: ", NamedTextColor.GRAY)
                .append(Component.text(snapshot.completedItems() + "/" + snapshot.totalItems(), NamedTextColor.GREEN))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("可领取奖励层数: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(snapshot.claimableTiers()), snapshot.claimableTiers() > 0 ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("累计收藏数量: ", NamedTextColor.GRAY)
                .append(Component.text(CollectionMath.formatNumber(snapshot.totalCollected()), NamedTextColor.AQUA))
                .decoration(TextDecoration.ITALIC, false));
        return InfoGuiItems.createNamedItem(Material.BOOKSHELF, title("收集中心"), lore);
    }

    @NotNull
    private ItemStack createCategoryItem(@NotNull CollectionSnapshot snapshot,
                                         @NotNull CollectionInfoGui.Category category) {
        CategoryView view = snapshot.view(category);
        List<Component> lore = new ArrayList<>();
        lore.add(text(category.description(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("已解锁物品: ", NamedTextColor.GRAY)
                .append(Component.text(view.unlockedItems() + "/" + view.totalItems(), NamedTextColor.AQUA))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("已满级物品: ", NamedTextColor.GRAY)
                .append(Component.text(view.completedItems() + "/" + view.totalItems(), NamedTextColor.GREEN))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("当前收藏等级: ", NamedTextColor.GRAY)
                .append(Component.text(view.currentTiers() + "/" + view.maxTiers(), NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("可领取奖励层数: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(view.claimableTiers()), view.claimableTiers() > 0 ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("首个未满级物品: ", NamedTextColor.GRAY)
                .append(Component.text(view.firstMissingItem(), NamedTextColor.AQUA))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(text("点击打开详细收藏页。", NamedTextColor.YELLOW));
        ItemStack stack = InfoGuiItems.createNamedItem(category.icon(), title(category.title()), lore);
        if (view.claimableTiers() > 0) {
            applyGlow(stack);
        }
        return stack;
    }

    @NotNull
    private ItemStack createClaimItem(@NotNull CollectionSnapshot snapshot) {
        long claimable = snapshot.claimableTiers();
        List<Component> lore = List.of(
                text("一键领取所有已解锁且未领取的收藏奖励。", NamedTextColor.GRAY),
                Component.empty(),
                text("可领取层数: " + claimable, claimable > 0 ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY),
                text("自动发放枫叶经验。", NamedTextColor.GRAY),
                Component.empty(),
                text(claimable > 0 ? "点击立即领取！" : "当前暂无可领取奖励。", claimable > 0 ? NamedTextColor.GREEN : NamedTextColor.YELLOW)
        );
        ItemStack stack = InfoGuiItems.createNamedItem(Material.AMETHYST_SHARD, title("一键领取奖励"), lore);
        if (claimable > 0) {
            applyGlow(stack);
        }
        return stack;
    }

    @NotNull
    private ItemStack createMissingItem(@NotNull CollectionSnapshot snapshot) {
        long claimable = snapshot.claimableTiers();
        List<Component> lore = List.of(
                text("查看待领取的奖励项与最接近升级的收藏目标。", NamedTextColor.GRAY),
                Component.empty(),
                text("当前可领取层数: " + claimable, claimable > 0 ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY),
                text("点击项目可直达该物品详情页领奖。", NamedTextColor.GRAY),
                Component.empty(),
                text("点击打开清单。", NamedTextColor.YELLOW)
        );
        return InfoGuiItems.createNamedItem(Material.HOPPER, title("待领与目标清单"), lore);
    }

    @NotNull
    private ItemStack createLeaderboardItem() {
        List<Component> lore = List.of(
                text("查看全服收藏总量实时排行榜（含在线最新进度）。", NamedTextColor.GRAY),
                text("支持按分类筛选、翻页浏览并显示你的个人排名。", NamedTextColor.GRAY),
                Component.empty(),
                text("点击打开排行榜。", NamedTextColor.YELLOW)
        );
        return InfoGuiItems.createNamedItem(Material.GOLD_INGOT, title("收藏排行榜"), lore);
    }

    @NotNull
    private ItemStack createGuideItem() {
        List<Component> lore = List.of(
                text("查看各类收藏获取途径、防刷规则、", NamedTextColor.GRAY),
                text("职业采集幸运加成与常用指令说明。", NamedTextColor.GRAY),
                Component.empty(),
                text("点击打开指南。", NamedTextColor.YELLOW)
        );
        return InfoGuiItems.createNamedItem(Material.WRITABLE_BOOK, title("收藏指南"), lore);
    }

    @NotNull
    private ItemStack createLeaderboardTitle(@NotNull CollectionSnapshot snapshot,
                                             @Nullable CollectionInfoGui.Category category,
                                             int selfRank,
                                             int totalRankedPlayers) {
        List<Component> lore = new ArrayList<>();
        lore.add(text(category == null ? "统计全服所有收藏总量（实时合并在线进度）。" : "统计该分类下所有收藏总量（实时合并在线进度）。", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("你的当前排名: ", NamedTextColor.GRAY)
                .append(Component.text(selfRank > 0 ? "第 #" + selfRank + " 名" : "暂未上榜", selfRank > 0 ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY))
                .append(Component.text(" / 共 " + totalRankedPlayers + " 人", NamedTextColor.DARK_GRAY))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("你的收藏总量: ", NamedTextColor.GRAY)
                .append(Component.text(CollectionMath.formatNumber(snapshot.totalFor(category)), NamedTextColor.AQUA))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(text("点击下方按钮切换全服总榜或各分类榜。", NamedTextColor.YELLOW));
        return InfoGuiItems.createNamedItem(Material.COMPASS, title(scopeName(category) + "榜单"), lore);
    }

    @NotNull
    private ItemStack createOverallScopeButton(boolean selected) {
        List<Component> lore = List.of(
                text("查看所有五大分类合计的收藏总量排名。", NamedTextColor.GRAY),
                text(selected ? "✔ 当前已选中" : "点击切换", selected ? NamedTextColor.GREEN : NamedTextColor.YELLOW)
        );
        ItemStack stack = InfoGuiItems.createNamedItem(Material.NETHER_STAR, title("全服总榜"), lore);
        if (selected) {
            applyGlow(stack);
        }
        return stack;
    }

    @NotNull
    private ItemStack createScopeButton(@NotNull CollectionInfoGui.Category category,
                                        @Nullable CollectionInfoGui.Category current) {
        boolean selected = current == category;
        List<Component> lore = List.of(
                text("切换到 " + category.title() + " 排行榜。", NamedTextColor.GRAY),
                text(selected ? "✔ 当前已选中" : "点击切换", selected ? NamedTextColor.GREEN : NamedTextColor.YELLOW)
        );
        ItemStack stack = InfoGuiItems.createNamedItem(category.icon(), title(category.title()), lore);
        if (selected) {
            applyGlow(stack);
        }
        return stack;
    }

    @NotNull
    private ItemStack createLeaderboardEntryItem(int rank,
                                                 @NotNull CollectionDataRepository.LeaderboardEntry entry,
                                                 @Nullable CollectionInfoGui.Category category,
                                                 @NotNull UUID viewerId) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.uuid());
        String name = playerName(offlinePlayer, entry.uuid());
        boolean isSelf = entry.uuid().equals(viewerId);
        NamedTextColor rankColor = switch (rank) {
            case 1 -> NamedTextColor.GOLD;
            case 2 -> NamedTextColor.AQUA;
            case 3 -> NamedTextColor.GREEN;
            default -> NamedTextColor.YELLOW;
        };

        List<Component> lore = new ArrayList<>();
        lore.add(text(category == null ? "全服收藏总量" : category.title() + " 收藏总量", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("累计数量: ", NamedTextColor.GRAY)
                .append(Component.text(CollectionMath.formatNumber(entry.totalAmount()), NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        if (isSelf) {
            lore.add(Component.empty());
            lore.add(text("★ 这就是你！", NamedTextColor.GREEN));
        }

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(offlinePlayer);
            meta.displayName(Component.text("#" + rank + " " + name, rankColor)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
            head.setItemMeta(meta);
        }
        return head;
    }

    @NotNull
    private ItemStack createMissingEntryItem(@NotNull CollectionRewardService.MissingEntry entry) {
        List<Component> lore = new ArrayList<>();
        lore.add(text("所属分类: " + entry.category().title(), NamedTextColor.GRAY));
        lore.add(Component.text("当前等级: ", NamedTextColor.GRAY)
                .append(Component.text(entry.currentTier() > 0 ? CollectionMath.roman(entry.currentTier()) : "未解锁", NamedTextColor.YELLOW))
                .append(Component.text(" (" + entry.currentTier() + "/" + entry.maxTier() + ")", NamedTextColor.DARK_GRAY))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("累计收集: ", NamedTextColor.GRAY)
                .append(Component.text(CollectionMath.formatNumber(entry.collected()), NamedTextColor.AQUA))
                .decoration(TextDecoration.ITALIC, false));
        if (entry.claimableTiers() > 0) {
            lore.add(Component.empty());
            lore.add(Component.text("✦ 有 " + entry.claimableTiers() + " 层奖励待领取！", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(text("已领至 " + (entry.claimedTier() > 0 ? CollectionMath.roman(entry.claimedTier()) : "无")
                    + "，可领至 " + CollectionMath.roman(entry.currentTier()), NamedTextColor.GRAY));
        } else if (entry.currentTier() < entry.maxTier()) {
            long remaining = Math.max(0L, entry.nextRequirement() - entry.collected());
            int percent = (int) Math.min(100L, Math.round(entry.nextRatio() * 100D));
            lore.add(Component.empty());
            lore.add(Component.text("距离 " + CollectionMath.roman(entry.currentTier() + 1) + ": ", NamedTextColor.GRAY)
                    .append(Component.text(percent + "%", NamedTextColor.YELLOW))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(text("还差 " + CollectionMath.formatNumber(remaining) + " 个", NamedTextColor.AQUA));
        }
        lore.add(Component.empty());
        lore.add(text("点击打开该物品详情页。", NamedTextColor.YELLOW));

        ItemStack stack = InfoGuiItems.createNamedItem(entry.item().material(), title(entry.item().name()), lore);
        if (entry.claimableTiers() > 0) {
            applyGlow(stack);
        }
        return stack;
    }

    @NotNull
    private ItemStack createGuideTitle(@NotNull CollectionSnapshot snapshot) {
        List<Component> lore = new ArrayList<>();
        lore.add(text("收藏进度自动累计，奖励支持一键领或逐层领。", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(text("当前收藏等级总和: " + snapshot.currentTiers() + "/" + snapshot.maxTiers(), NamedTextColor.YELLOW));
        lore.add(text("当前可领取层数: " + snapshot.claimableTiers(), NamedTextColor.GOLD));
        lore.add(text("下方图标可直接进入对应收藏分类。", NamedTextColor.AQUA));
        return InfoGuiItems.createNamedItem(Material.WRITABLE_BOOK, title("收藏指南"), lore);
    }

    @NotNull
    private ItemStack createGuideCategoryItem(@NotNull CollectionSnapshot snapshot,
                                              @NotNull CollectionInfoGui.Category category) {
        CategoryView view = snapshot.view(category);
        List<Component> lore = new ArrayList<>();
        lore.add(text("点击直接进入 " + category.title() + "。", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(text("已解锁: " + view.unlockedItems() + "/" + view.totalItems(), NamedTextColor.AQUA));
        lore.add(text("已满级: " + view.completedItems() + "/" + view.totalItems(), NamedTextColor.GREEN));
        lore.add(text("可领取: " + view.claimableTiers(), NamedTextColor.GOLD));
        lore.add(text("首个未满级: " + view.firstMissingItem(), NamedTextColor.YELLOW));
        return InfoGuiItems.createNamedItem(category.icon(), title(category.title()), lore);
    }

    private static void applyGlow(@NotNull ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
    }

    private static int findRank(@NotNull List<CollectionDataRepository.LeaderboardEntry> entries, @NotNull UUID uuid) {
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).uuid().equals(uuid)) {
                return index + 1;
            }
        }
        return -1;
    }

    @Nullable
    private static CollectionInfoGui.Category parseScope(@Nullable String scope) {
        if (scope == null || scope.isBlank() || "overall".equalsIgnoreCase(scope)) {
            return null;
        }
        for (CollectionInfoGui.Category category : CollectionInfoGui.Category.values()) {
            if (category.pageId().equalsIgnoreCase(scope)) {
                return category;
            }
        }
        return null;
    }

    private static int indexOf(int[] values, int target) {
        for (int index = 0; index < values.length; index++) {
            if (values[index] == target) {
                return index;
            }
        }
        return -1;
    }

    @NotNull
    private static String scopeName(@Nullable CollectionInfoGui.Category category) {
        return category == null ? "全服收藏" : category.title();
    }

    @NotNull
    private static Component title(@NotNull String text) {
        return Component.text(text, NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);
    }

    @NotNull
    private static Component text(@NotNull String text, @NotNull NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static void fill(@NotNull Inventory inventory, @NotNull Material material, @NotNull NamedTextColor color) {
        ItemStack filler = InfoGuiItems.createFiller(material, color);
        for (int slot = 0; slot < MENU_SIZE; slot++) {
            inventory.setItem(slot, filler.clone());
        }
    }

    @NotNull
    private static String playerName(@NotNull OfflinePlayer offlinePlayer, @NotNull UUID uuid) {
        String name = offlinePlayer.getName();
        return name == null || name.isBlank() ? uuid.toString().substring(0, 8) : name;
    }

    private record CollectionSnapshot(@NotNull Map<String, Long> progress,
                                      @NotNull Map<String, Integer> claims) {

        long totalCollected() {
            return progress.values().stream().filter(value -> value != null && value > 0L).mapToLong(Long::longValue).sum();
        }

        long totalFor(@Nullable CollectionInfoGui.Category category) {
            if (category == null) {
                return totalCollected();
            }
            long total = 0L;
            for (CollectionInfoGui.CollectionItem item : category.items()) {
                total += progress.getOrDefault(CollectionInfoGui.collectionKey(category, item.material()), 0L);
            }
            return total;
        }

        long currentTiers() {
            long total = 0L;
            for (CollectionInfoGui.Category category : CollectionInfoGui.Category.values()) {
                for (CollectionInfoGui.CollectionItem item : category.items()) {
                    total += CollectionMath.currentTier(progress.getOrDefault(CollectionInfoGui.collectionKey(category, item.material()), 0L), category);
                }
            }
            return total;
        }

        long maxTiers() {
            long total = 0L;
            for (CollectionInfoGui.Category category : CollectionInfoGui.Category.values()) {
                total += (long) category.items().size() * CollectionMath.maxTier(category);
            }
            return total;
        }

        long claimableTiers() {
            long total = 0L;
            for (CollectionInfoGui.Category category : CollectionInfoGui.Category.values()) {
                for (CollectionInfoGui.CollectionItem item : category.items()) {
                    String key = CollectionInfoGui.collectionKey(category, item.material());
                    int currentTier = CollectionMath.currentTier(progress.getOrDefault(key, 0L), category);
                    int claimedTier = claims.getOrDefault(key, 0);
                    total += Math.max(0, currentTier - claimedTier);
                }
            }
            return total;
        }

        int unlockedItems() {
            int unlocked = 0;
            for (CollectionInfoGui.Category category : CollectionInfoGui.Category.values()) {
                for (CollectionInfoGui.CollectionItem item : category.items()) {
                    String key = CollectionInfoGui.collectionKey(category, item.material());
                    if (progress.getOrDefault(key, 0L) > 0L) {
                        unlocked++;
                    }
                }
            }
            return unlocked;
        }

        int completedItems() {
            int completed = 0;
            for (CollectionInfoGui.Category category : CollectionInfoGui.Category.values()) {
                for (CollectionInfoGui.CollectionItem item : category.items()) {
                    String key = CollectionInfoGui.collectionKey(category, item.material());
                    if (CollectionMath.currentTier(progress.getOrDefault(key, 0L), category) >= CollectionMath.maxTier(category)) {
                        completed++;
                    }
                }
            }
            return completed;
        }

        int totalItems() {
            int total = 0;
            for (CollectionInfoGui.Category category : CollectionInfoGui.Category.values()) {
                total += category.items().size();
            }
            return total;
        }

        @NotNull
        CategoryView view(@NotNull CollectionInfoGui.Category category) {
            long currentTiers = 0L;
            long claimableTiers = 0L;
            int unlockedItems = 0;
            int completedItems = 0;
            String firstMissing = "全部满级";
            for (CollectionInfoGui.CollectionItem item : category.items()) {
                String key = CollectionInfoGui.collectionKey(category, item.material());
                long amount = progress.getOrDefault(key, 0L);
                if (amount > 0L) {
                    unlockedItems++;
                }
                int currentTier = CollectionMath.currentTier(amount, category);
                int claimedTier = claims.getOrDefault(key, 0);
                currentTiers += currentTier;
                claimableTiers += Math.max(0, currentTier - claimedTier);
                if (currentTier >= CollectionMath.maxTier(category)) {
                    completedItems++;
                } else if ("全部满级".equals(firstMissing)) {
                    firstMissing = item.name() + " " + CollectionMath.roman(currentTier + 1);
                }
            }
            return new CategoryView(
                    category.items().size(),
                    unlockedItems,
                    completedItems,
                    currentTiers,
                    (long) category.items().size() * CollectionMath.maxTier(category),
                    claimableTiers,
                    firstMissing
            );
        }
    }

    private record CategoryView(int totalItems,
                                int unlockedItems,
                                int completedItems,
                                long currentTiers,
                                long maxTiers,
                                long claimableTiers,
                                @NotNull String firstMissingItem) {
    }
}
