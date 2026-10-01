package maple.underground_collect;

import maple.underground_state.UndergroundState;
import maple.underground_state.collection.CollectionProgressManager;
import maple.underground_state.gui.BookshelfInfoGui;
import maple.underground_state.gui.CollectionInfoGui;
import maple.underground_state.gui.InfoGuiItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class CollectionMenus {

    private static final int MENU_SIZE = 54;
    private static final int TITLE_SLOT = 4;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 49;

    private static final int HUB_CLAIM_SLOT = 30;
    private static final int HUB_LEADERBOARD_SLOT = 32;
    private static final int HUB_GUIDE_SLOT = 34;
    private static final int[] CATEGORY_SLOTS = {20, 21, 22, 23, 24};

    private static final int LEADERBOARD_TITLE_SLOT = 4;
    private static final int[] LEADERBOARD_SCOPE_SLOTS = {19, 20, 21, 22, 23, 24};
    private static final int[] LEADERBOARD_ENTRY_SLOTS = {28, 29, 30, 31, 32, 33, 34, 35, 36, 37};

    private static final int GUIDE_TITLE_SLOT = 4;
    private static final int[] GUIDE_CATEGORY_SLOTS = {19, 20, 21, 22, 23};
    private static final int GUIDE_OPEN_SLOT = 30;

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
        CollectionSnapshot snapshot = snapshot(player.getUniqueId());
        CollectionMenuHolder holder = new CollectionMenuHolder(player.getUniqueId(), CollectionMenuType.HUB, "");
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, title("收藏中心"));
        holder.setInventory(inventory);
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE, NamedTextColor.BLACK);
        inventory.setItem(TITLE_SLOT, createHubTitle(snapshot));

        CollectionInfoGui.Category[] categories = CollectionInfoGui.Category.values();
        for (int index = 0; index < CATEGORY_SLOTS.length && index < categories.length; index++) {
            inventory.setItem(CATEGORY_SLOTS[index], createCategoryItem(snapshot, categories[index]));
        }

        inventory.setItem(HUB_CLAIM_SLOT, createClaimItem(snapshot));
        inventory.setItem(HUB_LEADERBOARD_SLOT, createLeaderboardItem());
        inventory.setItem(HUB_GUIDE_SLOT, createGuideItem());
        inventory.setItem(BACK_SLOT, InfoGuiItems.createNamedItem(
                Material.ARROW,
                title("返回书架"),
                List.of(text("回到主信息里的收藏入口。", NamedTextColor.GRAY))
        ));
        inventory.setItem(CLOSE_SLOT, InfoGuiItems.createCloseItem());
        player.openInventory(inventory);
    }

    void openLeaderboard(@NotNull Player player, @Nullable CollectionInfoGui.Category category) {
        CollectionSnapshot snapshot = snapshot(player.getUniqueId());
        String scope = category == null ? "overall" : category.pageId();
        CollectionMenuHolder holder = new CollectionMenuHolder(player.getUniqueId(), CollectionMenuType.LEADERBOARD, scope);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, title(scopeName(category) + "排行榜"));
        holder.setInventory(inventory);
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE, NamedTextColor.DARK_GRAY);
        inventory.setItem(LEADERBOARD_TITLE_SLOT, createLeaderboardTitle(snapshot, category));

        CollectionInfoGui.Category[] categories = CollectionInfoGui.Category.values();
        for (int index = 0; index < LEADERBOARD_SCOPE_SLOTS.length && index < categories.length; index++) {
            inventory.setItem(LEADERBOARD_SCOPE_SLOTS[index], createScopeButton(categories[index], category));
        }

        List<CollectionDataRepository.LeaderboardEntry> entries = repository.loadLeaderboard(category, LEADERBOARD_ENTRY_SLOTS.length);
        for (int index = 0; index < LEADERBOARD_ENTRY_SLOTS.length && index < entries.size(); index++) {
            inventory.setItem(LEADERBOARD_ENTRY_SLOTS[index], createLeaderboardEntryItem(index + 1, entries.get(index), category));
        }

        inventory.setItem(BACK_SLOT, InfoGuiItems.createNamedItem(
                Material.ARROW,
                title("返回收集中心"),
                List.of(text("回到收藏中心首页。", NamedTextColor.GRAY))
        ));
        inventory.setItem(CLOSE_SLOT, InfoGuiItems.createCloseItem());
        player.openInventory(inventory);
    }

    void openGuide(@NotNull Player player) {
        CollectionSnapshot snapshot = snapshot(player.getUniqueId());
        CollectionMenuHolder holder = new CollectionMenuHolder(player.getUniqueId(), CollectionMenuType.GUIDE, "");
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, title("收藏指南"));
        holder.setInventory(inventory);
        fill(inventory, Material.BLUE_STAINED_GLASS_PANE, NamedTextColor.BLUE);
        inventory.setItem(GUIDE_TITLE_SLOT, createGuideTitle(snapshot));

        CollectionInfoGui.Category[] categories = CollectionInfoGui.Category.values();
        for (int index = 0; index < GUIDE_CATEGORY_SLOTS.length && index < categories.length; index++) {
            inventory.setItem(GUIDE_CATEGORY_SLOTS[index], createGuideCategoryItem(snapshot, categories[index]));
        }
        inventory.setItem(GUIDE_OPEN_SLOT, InfoGuiItems.createNamedItem(
                Material.BOOK,
                title("直接打开收藏页"),
                List.of(
                        text("这会直接打开原有收藏分类页。", NamedTextColor.GRAY),
                        text("你可以继续点进单个物品查看详情。", NamedTextColor.GRAY)
                )
        ));
        inventory.setItem(BACK_SLOT, InfoGuiItems.createNamedItem(
                Material.ARROW,
                title("返回收集中心"),
                List.of(text("回到收藏中心首页。", NamedTextColor.GRAY))
        ));
        inventory.setItem(CLOSE_SLOT, InfoGuiItems.createCloseItem());
        player.openInventory(inventory);
    }

    void handleClick(@NotNull Player player, @NotNull CollectionMenuHolder holder, int rawSlot) {
        switch (holder.type()) {
            case HUB -> handleHubClick(player, rawSlot);
            case LEADERBOARD -> handleLeaderboardClick(player, rawSlot);
            case GUIDE -> handleGuideClick(player, rawSlot);
        }
    }

    @NotNull
    Map<String, Long> missingSummary(@NotNull Player player, @Nullable CollectionInfoGui.Category category) {
        return rewardService.missingSummary(player.getUniqueId(), category);
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
            if (!summary.hasAnything()) {
                player.sendMessage(Component.text("当前没有可领取的收藏奖励。", NamedTextColor.YELLOW));
            } else {
                sendClaimSummary(player, summary);
            }
            openHub(player);
            return;
        }
        if (rawSlot == HUB_LEADERBOARD_SLOT) {
            openLeaderboard(player, null);
            return;
        }
        if (rawSlot == HUB_GUIDE_SLOT) {
            openGuide(player);
            return;
        }
        if (rawSlot == BACK_SLOT) {
            BookshelfInfoGui.open(player);
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
        }
    }

    private void handleLeaderboardClick(@NotNull Player player, int rawSlot) {
        CollectionInfoGui.Category[] categories = CollectionInfoGui.Category.values();
        if (rawSlot == LEADERBOARD_TITLE_SLOT) {
            openLeaderboard(player, null);
            return;
        }
        for (int index = 0; index < LEADERBOARD_SCOPE_SLOTS.length && index < categories.length; index++) {
            if (rawSlot == LEADERBOARD_SCOPE_SLOTS[index]) {
                openLeaderboard(player, categories[index]);
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

    private void handleGuideClick(@NotNull Player player, int rawSlot) {
        CollectionInfoGui.Category[] categories = CollectionInfoGui.Category.values();
        if (rawSlot == GUIDE_TITLE_SLOT) {
            openGuide(player);
            return;
        }
        for (int index = 0; index < GUIDE_CATEGORY_SLOTS.length && index < categories.length; index++) {
            if (rawSlot == GUIDE_CATEGORY_SLOTS[index]) {
                CollectionInfoGui.open(player, categories[index]);
                return;
            }
        }
        if (rawSlot == GUIDE_OPEN_SLOT) {
            BookshelfInfoGui.open(player);
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

    @NotNull
    private CollectionSnapshot snapshot(@NotNull UUID uuid) {
        CollectionProgressManager progressManager = state.getCollectionProgressManager();
        Map<String, Long> progress = progressManager != null ? progressManager.snapshot(uuid) : Map.of();
        Map<String, Integer> claims = repository.loadClaims(uuid);
        return new CollectionSnapshot(progress, claims);
    }

    @NotNull
    private ItemStack createHubTitle(@NotNull CollectionSnapshot snapshot) {
        List<Component> lore = new ArrayList<>();
        lore.add(text("这里是收藏玩法的独立入口。", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("当前总收藏等级: ", NamedTextColor.GRAY)
                .append(Component.text(snapshot.currentTiers() + "/" + snapshot.maxTiers(), NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("已完成收藏: ", NamedTextColor.GRAY)
                .append(Component.text(snapshot.completedItems() + "/" + snapshot.totalItems(), NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("可领取奖励层数: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(snapshot.claimableTiers()), NamedTextColor.GOLD))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("累计收藏进度: ", NamedTextColor.GRAY)
                .append(Component.text(formatNumber(snapshot.totalCollected()), NamedTextColor.AQUA))
                .decoration(TextDecoration.ITALIC, false));
        return InfoGuiItems.createNamedItem(Material.BOOKSHELF, title("收藏中心"), lore);
    }

    @NotNull
    private ItemStack createCategoryItem(@NotNull CollectionSnapshot snapshot,
                                         @NotNull CollectionInfoGui.Category category) {
        CategoryView view = snapshot.view(category);
        List<Component> lore = new ArrayList<>();
        lore.add(text(category.description(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("已完成收藏: ", NamedTextColor.GRAY)
                .append(Component.text(view.completedItems() + "/" + category.items().size(), NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("当前收藏等级: ", NamedTextColor.GRAY)
                .append(Component.text(view.currentTiers() + "/" + view.maxTiers(), NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("可领取奖励层数: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(view.claimableTiers()), NamedTextColor.GOLD))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("首个未满级物品: ", NamedTextColor.GRAY)
                .append(Component.text(view.firstMissingItem(), NamedTextColor.AQUA))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(text("点击打开详细收藏页。", NamedTextColor.YELLOW));
        return InfoGuiItems.createNamedItem(category.icon(), title(category.title()), lore);
    }

    @NotNull
    private ItemStack createClaimItem(@NotNull CollectionSnapshot snapshot) {
        List<Component> lore = List.of(
                text("领取已经解锁但尚未领取的收藏奖励。", NamedTextColor.GRAY),
                Component.empty(),
                text("可领取层数: " + snapshot.claimableTiers(), NamedTextColor.GOLD),
                text("会自动补发收藏经验与楓叶经验。", NamedTextColor.GRAY)
        );
        return InfoGuiItems.createNamedItem(Material.AMETHYST_SHARD, title("领取奖励"), lore);
    }

    @NotNull
    private ItemStack createLeaderboardItem() {
        List<Component> lore = List.of(
                text("查看全服收藏总量排行榜。", NamedTextColor.GRAY),
                text("也可以在榜单页切换到单个分类。", NamedTextColor.GRAY),
                Component.empty(),
                text("点击打开排行榜。", NamedTextColor.YELLOW)
        );
        return InfoGuiItems.createNamedItem(Material.GOLD_INGOT, title("收藏榜单"), lore);
    }

    @NotNull
    private ItemStack createGuideItem() {
        List<Component> lore = List.of(
                text("查看收藏系统说明、分类入口和玩法提示。", NamedTextColor.GRAY),
                Component.empty(),
                text("点击打开指南。", NamedTextColor.YELLOW)
        );
        return InfoGuiItems.createNamedItem(Material.WRITABLE_BOOK, title("收藏指南"), lore);
    }

    @NotNull
    private ItemStack createLeaderboardTitle(@NotNull CollectionSnapshot snapshot,
                                             @Nullable CollectionInfoGui.Category category) {
        List<Component> lore = new ArrayList<>();
        lore.add(text(category == null ? "统计所有收藏记录总和。" : "统计该分类下所有收藏记录总和。", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(text("你的当前进度: " + formatNumber(snapshot.totalFor(category)), NamedTextColor.AQUA));
        lore.add(text("点击下方分类按钮切换榜单。", NamedTextColor.YELLOW));
        return InfoGuiItems.createNamedItem(Material.COMPASS, title(scopeName(category) + "榜单"), lore);
    }

    @NotNull
    private ItemStack createScopeButton(@NotNull CollectionInfoGui.Category category,
                                        @Nullable CollectionInfoGui.Category current) {
        List<Component> lore = List.of(
                text("切换到这个分类的排行榜。", NamedTextColor.GRAY),
                text(current == category ? "当前已选中" : "点击切换", current == category ? NamedTextColor.GREEN : NamedTextColor.YELLOW)
        );
        return InfoGuiItems.createNamedItem(category.icon(), title(category.title()), lore);
    }

    @NotNull
    private ItemStack createLeaderboardEntryItem(int rank,
                                                 @NotNull CollectionDataRepository.LeaderboardEntry entry,
                                                 @Nullable CollectionInfoGui.Category category) {
        List<Component> lore = new ArrayList<>();
        lore.add(text(category == null ? "全服收藏总量" : category.title() + " 收藏总量", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(text("总量: " + formatNumber(entry.totalAmount()), NamedTextColor.YELLOW));
        lore.add(text("UUID: " + entry.uuid(), NamedTextColor.DARK_GRAY));
        return InfoGuiItems.createNamedItem(Material.PAPER, title("#" + rank + " " + playerName(entry.uuid())), lore);
    }

    @NotNull
    private ItemStack createGuideTitle(@NotNull CollectionSnapshot snapshot) {
        List<Component> lore = new ArrayList<>();
        lore.add(text("收藏进度记录和奖励领取是分开的。", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(text("当前收藏等级总和: " + snapshot.currentTiers() + "/" + snapshot.maxTiers(), NamedTextColor.YELLOW));
        lore.add(text("当前可领取层数: " + snapshot.claimableTiers(), NamedTextColor.GOLD));
        return InfoGuiItems.createNamedItem(Material.WRITABLE_BOOK, title("收藏指南"), lore);
    }

    @NotNull
    private ItemStack createGuideCategoryItem(@NotNull CollectionSnapshot snapshot,
                                              @NotNull CollectionInfoGui.Category category) {
        CategoryView view = snapshot.view(category);
        List<Component> lore = new ArrayList<>();
        lore.add(text("点击直接进入这个分类。", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(text("已完成: " + view.completedItems() + "/" + category.items().size(), NamedTextColor.YELLOW));
        lore.add(text("可领取: " + view.claimableTiers(), NamedTextColor.GOLD));
        lore.add(text("首个未满级: " + view.firstMissingItem(), NamedTextColor.AQUA));
        return InfoGuiItems.createNamedItem(category.icon(), title(category.title()), lore);
    }

    private void sendClaimSummary(@NotNull Player player, @NotNull CollectionRewardService.ClaimSummary summary) {
        player.sendMessage(Component.text("收藏奖励领取完成。", NamedTextColor.GREEN));
        player.sendMessage(Component.text("领取收藏层数: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(summary.tiersClaimed()), NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("领取收藏项数: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(summary.collectionsClaimed()), NamedTextColor.YELLOW)));
        if (summary.mapleXp() > 0L) {
            player.sendMessage(Component.text("楓叶经验: ", NamedTextColor.GRAY)
                    .append(Component.text(formatNumber(summary.mapleXp()), NamedTextColor.AQUA)));
        }
        for (Map.Entry<maple.underground_state.skills.SkillType, Long> entry : summary.skillXpByType().entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0L) {
                continue;
            }
            player.sendMessage(Component.text(entry.getKey().displayName() + " 经验: ", NamedTextColor.GRAY)
                    .append(Component.text(formatNumber(entry.getValue()), NamedTextColor.AQUA)));
        }
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
    private String playerName(@NotNull UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name == null || name.isBlank() ? uuid.toString().substring(0, 8) : name;
    }

    @NotNull
    private static String formatNumber(long value) {
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
            int completedItems = 0;
            String firstMissing = "全部满级";
            for (CollectionInfoGui.CollectionItem item : category.items()) {
                String key = CollectionInfoGui.collectionKey(category, item.material());
                int currentTier = CollectionMath.currentTier(progress.getOrDefault(key, 0L), category);
                int claimedTier = claims.getOrDefault(key, 0);
                currentTiers += currentTier;
                claimableTiers += Math.max(0, currentTier - claimedTier);
                if (currentTier >= CollectionMath.maxTier(category)) {
                    completedItems++;
                } else if ("全部满级".equals(firstMissing)) {
                    firstMissing = item.name() + " " + CollectionMath.roman(currentTier + 1);
                }
            }
            return new CategoryView(category.items().size(), completedItems, currentTiers, (long) category.items().size() * CollectionMath.maxTier(category), claimableTiers, firstMissing);
        }
    }

    private record CategoryView(int totalItems,
                                int completedItems,
                                long currentTiers,
                                long maxTiers,
                                long claimableTiers,
                                @NotNull String firstMissingItem) {
    }
}
