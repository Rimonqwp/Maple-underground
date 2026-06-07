package maple.underground_state.gui;

import maple.underground_state.UndergroundState;
import maple.underground_state.skills.AlchemyLevelDefinition;
import maple.underground_state.skills.AlchemyLevelTable;
import maple.underground_state.skills.CombatLevelDefinition;
import maple.underground_state.skills.CombatLevelTable;
import maple.underground_state.skills.EnchantingLevelDefinition;
import maple.underground_state.skills.EnchantingLevelTable;
import maple.underground_state.skills.FarmingLevelDefinition;
import maple.underground_state.skills.FarmingLevelTable;
import maple.underground_state.skills.FishingLevelDefinition;
import maple.underground_state.skills.FishingLevelTable;
import maple.underground_state.skills.ForagingLevelDefinition;
import maple.underground_state.skills.ForagingLevelTable;
import maple.underground_state.skills.MiningLevelDefinition;
import maple.underground_state.skills.MiningLevelTable;
import maple.underground_state.skills.SkillProgressData;
import maple.underground_state.skills.SkillProgressManager;
import maple.underground_state.skills.SkillType;
import maple.underground_state.skills.TamingLevelDefinition;
import maple.underground_state.skills.TamingLevelTable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CopperSwordMenuGui {

    private record SkillCardState(int level, long currentLevelBaseXp, SkillLevelPreview nextLevel) {
    }

    private record SkillLevelPreview(int level, long xpRequired, List<Component> rewards) {
    }

    public static final String PAGE_ID = "copper_sword_menu";
    private static final int MENU_SIZE = 54;

    private static final int ICON_SLOT = 4;
    private static final int SWORD_SLOT = 19;
    private static final int PICKAXE_SLOT = 20;
    private static final int AXE_SLOT = 21;
    private static final int HOE_SLOT = 22;
    private static final int FISHING_ROD_SLOT = 23;
    private static final int ENCHANTING_TABLE_SLOT = 24;
    private static final int BREWING_STAND_SLOT = 25;
    private static final int ELYTRA_SLOT = 29;
    private static final int BUNDLE_SLOT = 30;
    private static final int DUNGEON_PANEL_SLOT = 31;
    private static final int CRAFTING_TABLE_SLOT = 32;
    private static final int COPPER_GOLEM_STATUE_SLOT = 33;
    private static final int BACK_SLOT = 45;
    private static final int EXTRA_FILLER_SLOT = 48;
    private static final int CLOSE_SLOT = 49;
    private static final boolean SHOW_TAMING_SKILL = false;
    private static final boolean SHOW_CRAFTING_SKILL = false;

    private static final int[] GLASS_SLOTS = {
            0, 1, 2, 3, 5, 6, 7, 8, 9,
            10, 11, 12, 13, 14, 15, 16, 17, 18,
            26, 27, 28, 34, 35,
            BUNDLE_SLOT, CRAFTING_TABLE_SLOT,
            36, 37, 38, 39, 40, 41, 42, 43,
            44, 45, 46, 47, 50, 51, 52, 53
    };

    private static final Component TITLE = Component.text("你的技能等級", NamedTextColor.WHITE);
    private static final TextColor NAME_RGB = TextColor.color(0x63E6A6);
    private static final TextColor ACTION_RGB = TextColor.color(0xFFE36E);
    private static final TextColor SYMBOL_RGB = TextColor.color(0xE0A83A);
    private static final ItemStack[] STATIC_CONTENTS = createStaticContents();

    private CopperSwordMenuGui() {
    }

    public static void open(@NotNull Player player) {
        InfoGuiHolder holder = new InfoGuiHolder(player.getUniqueId(), PAGE_ID);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, TITLE);
        holder.setInventory(inventory);

        applyStaticContents(inventory);
        populateDynamicSkillCards(inventory, player);

        player.openInventory(inventory);
    }

    public static void handleClick(@NotNull Player player, int rawSlot) {
        if (rawSlot == SWORD_SLOT) {
            SwordInfoGui.open(player);
            return;
        }
        if (rawSlot == PICKAXE_SLOT) {
            PickaxeInfoGui.open(player);
            return;
        }
        if (rawSlot == AXE_SLOT) {
            AxeInfoGui.open(player);
            return;
        }
        if (rawSlot == HOE_SLOT) {
            HoeInfoGui.open(player);
            return;
        }
        if (rawSlot == FISHING_ROD_SLOT) {
            FishingRodInfoGui.open(player);
            return;
        }
        if (rawSlot == ENCHANTING_TABLE_SLOT) {
            EnchantingInfoGui.open(player);
            return;
        }
        if (rawSlot == BREWING_STAND_SLOT) {
            AlchemyInfoGui.open(player);
            return;
        }
        if (rawSlot == ELYTRA_SLOT) {
            ExplorationInfoGui.open(player);
            return;
        }
        if (SHOW_TAMING_SKILL && rawSlot == BUNDLE_SLOT) {
            TamingInfoGui.open(player);
            return;
        }
        if (rawSlot == DUNGEON_PANEL_SLOT) {
            UndergroundState plugin = UndergroundState.getInstance();
            if (plugin != null && plugin.getDungeonPanelManager() != null) {
                plugin.getDungeonPanelManager().openMainPanel(player);
            }
            return;
        }
        if (SHOW_CRAFTING_SKILL && rawSlot == CRAFTING_TABLE_SLOT) {
            CraftingInfoGui.open(player);
            return;
        }
        if (rawSlot == COPPER_GOLEM_STATUE_SLOT) {
            SocialInfoGui.open(player);
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

    private static void applyStaticContents(@NotNull Inventory inventory) {
        for (int slot = 0; slot < STATIC_CONTENTS.length; slot++) {
            ItemStack item = STATIC_CONTENTS[slot];
            if (item != null) {
                inventory.setItem(slot, item.clone());
            }
        }
    }

    private static void populateDynamicSkillCards(@NotNull Inventory inventory, @NotNull Player player) {
        inventory.setItem(SWORD_SLOT, skillCard(player, SkillType.COMBAT, "擊殺怪物和特殊 Boss 即可獲得戰鬥經驗。"));
        inventory.setItem(PICKAXE_SLOT, skillCard(player, SkillType.MINING, "挖掘礦石和稀有礦物即可獲得挖礦經驗。"));
        inventory.setItem(AXE_SLOT, skillCard(player, SkillType.FORAGING, "砍伐原木和收集木材即可獲得伐木經驗。"));
        inventory.setItem(HOE_SLOT, skillCard(player, SkillType.FARMING, "收成作物和畜牧資源即可獲得農業經驗。"));
        inventory.setItem(FISHING_ROD_SLOT, skillCard(player, SkillType.FISHING, "釣起魚獲和海洋資源即可獲得釣魚經驗。"));
        inventory.setItem(ENCHANTING_TABLE_SLOT, skillCard(player, SkillType.ENCHANTING, "進行附魔和研究魔法即可獲得附魔經驗。"));
        inventory.setItem(BREWING_STAND_SLOT, skillCard(player, SkillType.ALCHEMY, "製作藥水和研究配方即可獲得釀造經驗。"));
        inventory.setItem(ELYTRA_SLOT, skillCard(player, SkillType.EXPLORATION, "探索未知地區和稀有地標即可獲得探索經驗。"));
        if (SHOW_TAMING_SKILL) {
            inventory.setItem(BUNDLE_SLOT, skillCard(player, SkillType.TAMING, "培養寵物和提升默契即可獲得寵物經驗。"));
        }
        UndergroundState plugin = UndergroundState.getInstance();
        if (plugin != null && plugin.getDungeonPanelManager() != null) {
            inventory.setItem(DUNGEON_PANEL_SLOT, plugin.getDungeonPanelManager().createCaveOverviewSkullForSkills(player));
        }
        if (SHOW_CRAFTING_SKILL) {
            inventory.setItem(CRAFTING_TABLE_SLOT, skillCard(player, SkillType.CRAFTING, "製作物品和解鎖配方即可獲得工藝經驗。"));
        }
        inventory.setItem(COPPER_GOLEM_STATUE_SLOT, skillCard(player, SkillType.SOCIAL, "參與伺服器互動和活動即可獲得社交經驗。"));
    }

    private static @NotNull ItemStack[] createStaticContents() {
        ItemStack[] contents = new ItemStack[MENU_SIZE];
        ItemStack filler = InfoGuiItems.createFiller(Material.BLACK_STAINED_GLASS_PANE, NamedTextColor.BLACK);
        for (int slot : GLASS_SLOTS) {
            contents[slot] = filler.clone();
        }
        contents[EXTRA_FILLER_SLOT] = filler.clone();
        contents[ICON_SLOT] = InfoGuiItems.createNamedItem(
                Material.COPPER_SWORD,
                nameText("你的技能等級"),
                List.of(
                        Component.text("查看您的技能進度和獎勵。", NamedTextColor.GRAY),
                        Component.empty(),
                        actionText("點擊查看！")
                )
        );
        contents[BACK_SLOT] = InfoGuiItems.createNamedItem(
                Material.ARROW,
                "返回",
                List.of(Component.text("點擊返回主頁", NamedTextColor.GRAY))
        );
        contents[CLOSE_SLOT] = InfoGuiItems.createCloseItem();
        return contents;
    }

    private static @NotNull ItemStack skillCard(@NotNull Player player,
                                                @NotNull SkillType skillType,
                                                @NotNull String description) {
        long totalXp = skillXp(player, skillType);
        SkillCardState state = skillCardState(skillType, totalXp);
        SkillLevelPreview nextLevel = state.nextLevel();

        List<Component> lore = new ArrayList<>();
        lore.add(bodyText(description));

        if (nextLevel == null) {
            lore.add(Component.empty());
            lore.add(bodyText("已達最高等級", NamedTextColor.GREEN));
        } else {
            long currentInLevel = Math.max(0L, totalXp - state.currentLevelBaseXp());
            long neededXp = nextLevel.xpRequired();
            int percent = neededXp <= 0L ? 100 : (int) Math.min(100L, (currentInLevel * 100L) / neededXp);

            lore.add(Component.empty());
            lore.add(Component.text("升級到 ", NamedTextColor.GRAY)
                    .append(Component.text(nextLevel.level(), ACTION_RGB))
                    .append(Component.text(" 級 ", NamedTextColor.GRAY))
                    .append(Component.text(percent, ACTION_RGB))
                    .append(Component.text("%", SYMBOL_RGB))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(progressLine(percent, formatCompactNumber(currentInLevel), formatCompactNumber(neededXp)));
            lore.add(Component.empty());
            lore.add(sectionTitle(nextLevel.level() + " 級獎勵："));
            lore.addAll(nextLevel.rewards());
        }

        lore.add(Component.empty());
        lore.add(actionText("點擊查看！"));

        return InfoGuiItems.createNamedItem(
                skillType.icon(),
                nameText(skillType.displayName() + "等級 " + state.level()),
                lore
        );
    }

    private static @NotNull SkillCardState skillCardState(@NotNull SkillType skillType, long totalXp) {
        return switch (skillType) {
            case COMBAT -> {
                int level = CombatLevelTable.levelForXp(totalXp);
                yield new SkillCardState(
                        level,
                        level <= 0 ? 0L : CombatLevelTable.currentLevelBaseXp(level),
                        buildCombatPreview(CombatLevelTable.nextDefinitionForXp(totalXp))
                );
            }
            case MINING -> {
                int level = MiningLevelTable.levelForXp(totalXp);
                yield new SkillCardState(
                        level,
                        level <= 0 ? 0L : MiningLevelTable.currentLevelBaseXp(level),
                        buildMiningPreview(MiningLevelTable.nextDefinitionForXp(totalXp))
                );
            }
            case FORAGING -> {
                int level = ForagingLevelTable.levelForXp(totalXp);
                yield new SkillCardState(
                        level,
                        level <= 0 ? 0L : ForagingLevelTable.currentLevelBaseXp(level),
                        buildForagingPreview(ForagingLevelTable.nextDefinitionForXp(totalXp))
                );
            }
            case FARMING -> {
                int level = FarmingLevelTable.levelForXp(totalXp);
                yield new SkillCardState(
                        level,
                        level <= 0 ? 0L : FarmingLevelTable.currentLevelBaseXp(level),
                        buildFarmingPreview(FarmingLevelTable.nextDefinitionForXp(totalXp))
                );
            }
            case FISHING -> {
                int level = FishingLevelTable.levelForXp(totalXp);
                yield new SkillCardState(
                        level,
                        level <= 0 ? 0L : FishingLevelTable.currentLevelBaseXp(level),
                        buildFishingPreview(FishingLevelTable.nextDefinitionForXp(totalXp))
                );
            }
            case ENCHANTING -> {
                int level = EnchantingLevelTable.levelForXp(totalXp);
                yield new SkillCardState(
                        level,
                        level <= 0 ? 0L : EnchantingLevelTable.currentLevelBaseXp(level),
                        buildEnchantingPreview(EnchantingLevelTable.nextDefinitionForXp(totalXp))
                );
            }
            case ALCHEMY -> {
                int level = AlchemyLevelTable.levelForXp(totalXp);
                yield new SkillCardState(
                        level,
                        level <= 0 ? 0L : AlchemyLevelTable.currentLevelBaseXp(level),
                        buildAlchemyPreview(AlchemyLevelTable.nextDefinitionForXp(totalXp))
                );
            }
            case TAMING -> {
                int level = TamingLevelTable.levelForXp(totalXp);
                yield new SkillCardState(
                        level,
                        level <= 0 ? 0L : TamingLevelTable.currentLevelBaseXp(level),
                        buildTamingPreview(TamingLevelTable.nextDefinitionForXp(totalXp))
                );
            }
            case EXPLORATION, CRAFTING, SOCIAL -> {
                int level = CombatLevelTable.levelForXp(totalXp);
                yield new SkillCardState(
                        level,
                        level <= 0 ? 0L : CombatLevelTable.currentLevelBaseXp(level),
                        buildUtilityPreview(CombatLevelTable.nextDefinitionForXp(totalXp))
                );
            }
        };
    }

    private static SkillLevelPreview buildCombatPreview(CombatLevelDefinition definition) {
        if (definition == null) {
            return null;
        }
        return new SkillLevelPreview(
                definition.level(),
                definition.xpRequired(),
                List.of(
                        rewardLine(" + 硬幣 " + formatCompactNumber(definition.coinReward())),
                        rewardLine(" + 暴擊機率 " + formatDecimal(definition.critChanceReward()) + " CC"),
                        rewardLine(" + 對怪物造成額外 " + definition.extraDamageRewardPercent() + "% 的傷害。"),
                        rewardLine(" + 楓葉經驗 " + definition.mapleXpReward())
                )
        );
    }

    private static SkillLevelPreview buildUtilityPreview(CombatLevelDefinition definition) {
        if (definition == null) {
            return null;
        }
        return new SkillLevelPreview(
                definition.level(),
                definition.xpRequired(),
                List.of(
                        rewardLine(" + 硬幣 " + formatCompactNumber(definition.coinReward())),
                        rewardLine(" + 楓葉經驗 " + definition.mapleXpReward())
                )
        );
    }

    private static SkillLevelPreview buildMiningPreview(MiningLevelDefinition definition) {
        if (definition == null) {
            return null;
        }
        return new SkillLevelPreview(
                definition.level(),
                definition.xpRequired(),
                List.of(
                        rewardLine(" + 硬幣 " + formatCompactNumber(definition.coinReward())),
                        rewardLine(" + 防禦 " + definition.defenseReward() + " DEF"),
                        rewardLine(" + 挖礦幸運 " + definition.miningFortuneReward() + " MI"),
                        rewardLine(" + 楓葉經驗 " + definition.mapleXpReward())
                )
        );
    }

    private static SkillLevelPreview buildForagingPreview(ForagingLevelDefinition definition) {
        if (definition == null) {
            return null;
        }
        return new SkillLevelPreview(
                definition.level(),
                definition.xpRequired(),
                List.of(
                        rewardLine(" + 硬幣 " + formatCompactNumber(definition.coinReward())),
                        rewardLine(" + 力量 " + definition.strengthReward() + " STR"),
                        rewardLine(" + 伐木幸運 " + definition.foragingFortuneReward() + " FO"),
                        rewardLine(" + 楓葉經驗 " + definition.mapleXpReward())
                )
        );
    }

    private static SkillLevelPreview buildFarmingPreview(FarmingLevelDefinition definition) {
        if (definition == null) {
            return null;
        }
        return new SkillLevelPreview(
                definition.level(),
                definition.xpRequired(),
                List.of(
                        rewardLine(" + 硬幣 " + formatCompactNumber(definition.coinReward())),
                        rewardLine(" + 生命 " + definition.healthReward() + " HP"),
                        rewardLine(" + 農業幸運 " + definition.farmingFortuneReward() + " FA"),
                        rewardLine(" + 楓葉經驗 " + definition.mapleXpReward())
                )
        );
    }

    private static SkillLevelPreview buildFishingPreview(FishingLevelDefinition definition) {
        if (definition == null) {
            return null;
        }
        return new SkillLevelPreview(
                definition.level(),
                definition.xpRequired(),
                List.of(
                        rewardLine(" + 硬幣 " + formatCompactNumber(definition.coinReward())),
                        rewardLine(" + 生命 " + definition.healthReward() + " HP"),
                        rewardLine(" + 寶藏機率 " + formatDecimal(definition.treasureChanceReward()) + " TC"),
                        rewardLine(" + 楓葉經驗 " + definition.mapleXpReward())
                )
        );
    }

    private static SkillLevelPreview buildEnchantingPreview(EnchantingLevelDefinition definition) {
        if (definition == null) {
            return null;
        }
        return new SkillLevelPreview(
                definition.level(),
                definition.xpRequired(),
                List.of(
                        rewardLine(" + 硬幣 " + formatCompactNumber(definition.coinReward())),
                        rewardLine(" + 智力 " + definition.intelligenceReward() + " INT"),
                        rewardLine(" + 技能傷害 " + formatDecimal(definition.abilityDamageReward()) + " AD"),
                        rewardLine(" + 額外經驗 " + definition.bonusExpReward() + "%"),
                        rewardLine(" + 楓葉經驗 " + definition.mapleXpReward())
                )
        );
    }

    private static SkillLevelPreview buildAlchemyPreview(AlchemyLevelDefinition definition) {
        if (definition == null) {
            return null;
        }
        return new SkillLevelPreview(
                definition.level(),
                definition.xpRequired(),
                List.of(
                        rewardLine(" + 硬幣 " + formatCompactNumber(definition.coinReward())),
                        rewardLine(" + 智力 " + definition.intelligenceReward() + " INT"),
                        rewardLine(" + 藥水持續時間 " + definition.potionDurationReward() + "%"),
                        rewardLine(" + 楓葉經驗 " + definition.mapleXpReward())
                )
        );
    }

    private static SkillLevelPreview buildTamingPreview(TamingLevelDefinition definition) {
        if (definition == null) {
            return null;
        }
        return new SkillLevelPreview(
                definition.level(),
                definition.xpRequired(),
                List.of(
                        rewardLine(" + 硬幣 " + formatCompactNumber(definition.coinReward())),
                        rewardLine(" + 寵物幸運 " + definition.petLuckReward() + " PL"),
                        rewardLine(" + 寵物經驗加成 " + definition.petXpGainReward() + "%"),
                        rewardLine(" + 經驗共享率 " + formatDecimal(definition.expShareRateReward()) + "%"),
                        rewardLine(" + 楓葉經驗 " + definition.mapleXpReward())
                )
        );
    }

    private static long skillXp(@NotNull Player player, @NotNull SkillType skillType) {
        UndergroundState plugin = UndergroundState.getInstance();
        if (plugin == null) {
            return 0L;
        }
        SkillProgressManager manager = plugin.getSkillProgressManager();
        if (manager == null) {
            return 0L;
        }
        SkillProgressData data = manager.get(player.getUniqueId());
        return data.getXp(skillType);
    }

    private static Component nameText(String text) {
        return Component.text(text, NAME_RGB)
                .decoration(TextDecoration.ITALIC, false)
                .decorate(TextDecoration.BOLD);
    }

    private static Component actionText(String text) {
        return Component.text(text, ACTION_RGB)
                .decoration(TextDecoration.ITALIC, false)
                .decorate(TextDecoration.BOLD);
    }

    private static Component sectionTitle(String text) {
        return Component.text(text, ACTION_RGB)
                .decoration(TextDecoration.ITALIC, false)
                .decorate(TextDecoration.BOLD);
    }

    private static Component rewardLine(String text) {
        return bodyText(text);
    }

    private static Component bodyText(String text) {
        return bodyText(text, NamedTextColor.GRAY);
    }

    private static Component bodyText(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static Component progressLine(int percent, String current, String required) {
        return InfoGuiItems.createColoredProgressBar(percent)
                .append(Component.text(" ").decoration(TextDecoration.STRIKETHROUGH, false))
                .append(Component.text(current, ACTION_RGB).decoration(TextDecoration.STRIKETHROUGH, false))
                .append(Component.text("/", SYMBOL_RGB).decoration(TextDecoration.STRIKETHROUGH, false))
                .append(Component.text(required, ACTION_RGB).decoration(TextDecoration.STRIKETHROUGH, false))
                .decoration(TextDecoration.ITALIC, false);
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

    private static @NotNull String formatDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
