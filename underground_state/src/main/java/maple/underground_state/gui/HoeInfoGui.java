package maple.underground_state.gui;

import maple.underground_state.UndergroundState;
import maple.underground_state.skills.FarmingLevelDefinition;
import maple.underground_state.skills.FarmingLevelTable;
import maple.underground_state.skills.SkillProgressData;
import maple.underground_state.skills.SkillProgressManager;
import maple.underground_state.skills.SkillType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HoeInfoGui {

    public static final String PAGE_ID = "hoe";
    private static final int MENU_SIZE = 54;

    private static final int SUMMARY_SLOT = 0;
    private static final int PREVIOUS_PAGE_SLOT = 48;
    private static final int NEXT_PAGE_SLOT = 50;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 49;

    private static final int[] LEVEL_SLOTS = {
            9, 18, 27, 28, 29, 20, 11, 2, 3, 4, 13, 22, 31,
            32, 33, 24, 15, 6, 7, 8, 17, 26, 35, 44, 53
    };

    private static final Component TITLE = text("農業詳情頁", NamedTextColor.WHITE);

    private HoeInfoGui() {
    }

    public static void open(@NotNull Player player) {
        open(player, 0);
    }

    public static void open(@NotNull Player player, int page) {
        int safePage = Math.max(0, Math.min(page, maxPageIndex()));
        InfoGuiHolder holder = new InfoGuiHolder(player.getUniqueId(), PAGE_ID, safePage);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, TITLE);
        holder.setInventory(inventory);

        fillInventory(inventory);

        long totalXp = farmingXp(player);
        int currentLevel = FarmingLevelTable.levelForXp(totalXp);
        FarmingLevelDefinition currentDefinition = currentLevel > 0
                ? FarmingLevelTable.definitionForLevel(currentLevel)
                : null;
        FarmingLevelDefinition nextDefinition = FarmingLevelTable.nextDefinitionForXp(totalXp);

        inventory.setItem(SUMMARY_SLOT, createSummaryItem(totalXp, currentLevel, currentDefinition, nextDefinition));
        if (safePage > 0) {
            inventory.setItem(PREVIOUS_PAGE_SLOT, createPageControl("上一頁", safePage, maxPageIndex() + 1));
        }
        if (safePage < maxPageIndex()) {
            inventory.setItem(NEXT_PAGE_SLOT, createPageControl("下一頁", safePage + 2, maxPageIndex() + 1));
        }

        int startLevel = safePage * LEVEL_SLOTS.length + 1;
        for (int index = 0; index < LEVEL_SLOTS.length; index++) {
            int level = startLevel + index;
            if (level > FarmingLevelTable.maxLevel()) {
                break;
            }
            FarmingLevelDefinition definition = FarmingLevelTable.definitionForLevel(level);
            inventory.setItem(LEVEL_SLOTS[index], createLevelItem(definition, totalXp));
        }

        inventory.setItem(BACK_SLOT, InfoGuiItems.createNamedItem(
                Material.ARROW,
                "返回",
                List.of(text("點擊返回技能總覽", NamedTextColor.GRAY))
        ));
        inventory.setItem(CLOSE_SLOT, InfoGuiItems.createCloseItem());

        player.openInventory(inventory);
    }

    public static void handleClick(@NotNull Player player, int rawSlot) {
        int currentPage = currentPage(player);
        if (rawSlot == PREVIOUS_PAGE_SLOT && currentPage > 0) {
            open(player, currentPage - 1);
            return;
        }
        if (rawSlot == NEXT_PAGE_SLOT && currentPage < maxPageIndex()) {
            open(player, currentPage + 1);
            return;
        }
        if (rawSlot == BACK_SLOT) {
            CopperSwordMenuGui.open(player);
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
        }
    }

    private static void fillInventory(@NotNull Inventory inventory) {
        ItemStack filler = InfoGuiItems.createFiller(Material.BLACK_STAINED_GLASS_PANE, NamedTextColor.BLACK);
        for (int slot = 0; slot < MENU_SIZE; slot++) {
            inventory.setItem(slot, filler.clone());
        }
    }

    private static @NotNull ItemStack createSummaryItem(long totalXp,
                                                        int currentLevel,
                                                        @Nullable FarmingLevelDefinition currentDefinition,
                                                        @Nullable FarmingLevelDefinition nextDefinition) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(text("提升你的農業屬性", NamedTextColor.GRAY));
        lore.add(text("每次升級都會獲得固定獎勵", NamedTextColor.GRAY));
        lore.add(Component.empty());

        int nextLevel = nextDefinition == null ? FarmingLevelTable.maxLevel() : nextDefinition.level();
        long currentXpThreshold = currentLevel <= 0 ? 0L : FarmingLevelTable.currentLevelBaseXp(currentLevel);
        long nextXpThreshold = nextDefinition == null ? currentXpThreshold : nextDefinition.totalXpRequired();
        lore.add(Component.text("農業等級: ", NamedTextColor.DARK_GRAY)
                .append(Component.text(String.valueOf(currentLevel), NamedTextColor.WHITE))
                .append(Component.text(" -> ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(nextLevel), NamedTextColor.GREEN))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("農業經驗: ", NamedTextColor.DARK_GRAY)
                .append(Component.text(formatCompactNumber(currentXpThreshold), NamedTextColor.WHITE))
                .append(Component.text(" -> ", NamedTextColor.GRAY))
                .append(Component.text(formatCompactNumber(nextXpThreshold), NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        if (nextDefinition == null) {
            lore.add(text("已達最高等級", NamedTextColor.GREEN));
        } else {
            lore.add(buildProgressBar(
                    summaryCurrentXp(totalXp, currentLevel, currentDefinition, nextDefinition),
                    summaryRequiredXp(currentDefinition, nextDefinition),
                    progressPercent(totalXp, currentLevel, nextDefinition)
            ));
        }
        lore.add(Component.empty());

        lore.add(text("累計獎勵:", NamedTextColor.GOLD));
        lore.add(text(" + 硬幣 " + formatCompactNumber(currentDefinition == null ? 0 : currentDefinition.totalCoinReward()), NamedTextColor.GRAY));
        lore.add(text(" + 生命 " + (currentDefinition == null ? 0 : currentDefinition.totalHealthReward()) + " HP", NamedTextColor.GRAY));
        lore.add(text(" + 農業幸運 " + (currentDefinition == null ? 0 : currentDefinition.totalFarmingFortuneReward()) + " FA", NamedTextColor.GRAY));
        lore.add(text(" + 楓葉經驗 " + (currentDefinition == null ? 0 : currentDefinition.totalMapleXpReward()), NamedTextColor.GRAY));

        return InfoGuiItems.createNamedItem(Material.COPPER_HOE, title("農業詳情頁"), lore);
    }

    private static @NotNull ItemStack createPageControl(@NotNull String label, int targetPage, int totalPages) {
        return InfoGuiItems.createNamedItem(
                Material.ARROW,
                title(label),
                List.of(text("點擊切換到第 " + targetPage + " 頁 / 共 " + totalPages + " 頁", NamedTextColor.GRAY))
        );
    }

    private static @NotNull ItemStack createLevelItem(@NotNull FarmingLevelDefinition definition, long totalXp) {
        long threshold = definition.totalXpRequired();
        long previousThreshold = definition.level() <= 1
                ? 0L
                : FarmingLevelTable.definitionForLevel(definition.level() - 1).totalXpRequired();
        long xpNeededForLevel = threshold - previousThreshold;
        long xpInLevel = totalXp >= threshold
                ? xpNeededForLevel
                : totalXp >= previousThreshold ? totalXp - previousThreshold : 0L;
        int percent = xpNeededForLevel <= 0 ? 100 : (int) Math.min(100, (xpInLevel * 100) / xpNeededForLevel);

        int currentLevel = FarmingLevelTable.levelForXp(totalXp);
        boolean reached = totalXp >= threshold;
        boolean inProgress = !reached && currentLevel + 1 == definition.level();

        Material material;
        NamedTextColor nameColor;
        String statusText;
        if (reached) {
            material = Material.LIME_STAINED_GLASS_PANE;
            nameColor = NamedTextColor.GREEN;
            statusText = "已達成";
        } else if (inProgress) {
            material = Material.YELLOW_STAINED_GLASS_PANE;
            nameColor = NamedTextColor.YELLOW;
            statusText = "正在解鎖";
        } else {
            material = Material.RED_STAINED_GLASS_PANE;
            nameColor = NamedTextColor.RED;
            statusText = "尚未解鎖";
        }

        List<Component> lore = new ArrayList<>();
        lore.add(text("累積需求 " + formatCompactNumber(threshold) + " XP", NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        lore.add(text("獎勵:", NamedTextColor.GOLD));
        lore.add(text(" + 硬幣 " + formatCompactNumber(definition.coinReward()), NamedTextColor.GRAY));
        lore.add(text(" + 生命 " + definition.healthReward() + " HP", NamedTextColor.GRAY));
        lore.add(text(" + 農業幸運 " + definition.farmingFortuneReward() + " FA", NamedTextColor.GRAY));
        lore.add(text(" + 楓葉經驗 " + definition.mapleXpReward(), NamedTextColor.GRAY));
        if (inProgress) {
            lore.add(Component.empty());
            lore.add(Component.text("當前進度: ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(percent + "%", NamedTextColor.YELLOW))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(buildProgressBar(xpInLevel, xpNeededForLevel, percent));
        }

        if (!inProgress) {
            lore.add(Component.empty());
        }
        lore.add(text(statusText, nameColor));

        return InfoGuiItems.createNamedItem(material, title("農業 Lv." + definition.level()), lore);
    }

    private static @NotNull Component buildProgressBar(long currentXpInLevel, long requiredXp, int percent) {
        return InfoGuiItems.createColoredProgressBar(percent)
                .append(Component.text(" ").decoration(TextDecoration.STRIKETHROUGH, false))
                .append(Component.text(formatCompactNumber(currentXpInLevel), NamedTextColor.YELLOW).decoration(TextDecoration.STRIKETHROUGH, false))
                .append(Component.text("/", NamedTextColor.GOLD).decoration(TextDecoration.STRIKETHROUGH, false))
                .append(Component.text(formatCompactNumber(requiredXp), NamedTextColor.YELLOW).decoration(TextDecoration.STRIKETHROUGH, false))
                .decoration(TextDecoration.ITALIC, false);
    }

    private static int progressPercent(long totalXp, int currentLevel, @Nullable FarmingLevelDefinition nextDefinition) {
        if (nextDefinition == null) {
            return 100;
        }
        long currentThreshold = currentLevel <= 0 ? 0L : FarmingLevelTable.currentLevelBaseXp(currentLevel);
        long needed = nextDefinition.xpRequired();
        long inLevel = Math.max(0L, totalXp - currentThreshold);
        return needed <= 0L ? 100 : (int) Math.min(100, (inLevel * 100) / needed);
    }

    private static long summaryCurrentXp(long totalXp,
                                         int currentLevel,
                                         @Nullable FarmingLevelDefinition currentDefinition,
                                         @Nullable FarmingLevelDefinition nextDefinition) {
        if (nextDefinition == null) {
            return currentDefinition == null ? 0L : currentDefinition.xpRequired();
        }
        long currentThreshold = currentLevel <= 0 ? 0L : FarmingLevelTable.currentLevelBaseXp(currentLevel);
        return Math.max(0L, totalXp - currentThreshold);
    }

    private static long summaryRequiredXp(@Nullable FarmingLevelDefinition currentDefinition,
                                          @Nullable FarmingLevelDefinition nextDefinition) {
        if (nextDefinition == null) {
            return currentDefinition == null ? 0L : currentDefinition.xpRequired();
        }
        return nextDefinition.xpRequired();
    }

    private static int currentPage(@NotNull Player player) {
        Inventory inventory = player.getOpenInventory().getTopInventory();
        if (inventory.getHolder() instanceof InfoGuiHolder holder && PAGE_ID.equals(holder.pageId())) {
            return holder.page();
        }
        return 0;
    }

    private static int maxPageIndex() {
        return Math.max(0, (FarmingLevelTable.maxLevel() - 1) / LEVEL_SLOTS.length);
    }

    private static long farmingXp(@NotNull Player player) {
        UndergroundState plugin = UndergroundState.getInstance();
        if (plugin == null) {
            return 0L;
        }
        SkillProgressManager manager = plugin.getSkillProgressManager();
        if (manager == null) {
            return 0L;
        }
        SkillProgressData data = manager.get(player.getUniqueId());
        return data.getXp(SkillType.FARMING);
    }

    private static @NotNull Component title(@NotNull String text) {
        return Component.text(text, NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
                .decorate(TextDecoration.BOLD);
    }

    private static @NotNull Component text(@NotNull String text, @NotNull NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static @NotNull String formatCompactNumber(long value) {
        if (value >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.1fB", value / 1e9);
        }
        if (value >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", value / 1e6);
        }
        if (value >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fK", value / 1e3);
        }
        return String.valueOf(value);
    }
}
