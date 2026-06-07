package maple.underground_caves.gear;

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemArmorTrim;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import maple.underground_item.api.CustomEnchantService;
import maple.underground_item.api.CustomEnchantView;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.ItemFlag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 昇華工作台：中間放裝備，下方鐵砧點擊後依保護附魔與 lore 判定，重寫名稱與 lore。
 * 昇華結果會清空原版屬性修飾（資料組件），僅保留 PDC 虛擬生命／防禦與插件 lore。
 * 原版 {@link DataComponentTypes#TRIM} 的「已有升級」提示改為寫入 lore、置於分組附魔下方，並以 {@link DataComponentTypes#TOOLTIP_DISPLAY} 隱藏頂部預設紋飾提示，避免重複。
 */
@SuppressWarnings("UnstableApiUsage")
public final class SublimationWorkbenchManager implements Listener {

    private static final int SIZE = 27;
    /** 中間欄位（第二列中央） */
    private static final int SLOT_ITEM = 13;
    /** 最底列中央：鐵砧按鈕 */
    private static final int SLOT_ANVIL = 22;

    /**
     * 比對時會剥除整段 lore 的色碼後再 contains（見 {@link #normalizedLoreCombined}）。
     * 第一句「遣」「遺」兩種常見寫法皆視為通過。
     */
    private static final String MARKER_LINE_0_QIAN = "似乎為二次終界之城所遣留下來的殘骸";
    private static final String MARKER_LINE_0_YI = "似乎為二次終界之城所遺留下來的殘骸";
    /** 第三段標記：四件套任一（頭／胸／腿／鞋）皆可通過昇華條件，見 {@link #markerLineSatisfied(int, String)}。 */
    private static final String MARKER_LINE_2_VILLAGER_HELMET = "遠遠不如1000年前的村民頭盔";
    private static final String MARKER_LINE_2_VILLAGER_CHEST = "遠遠不如1000年前的村民胸甲";
    private static final String MARKER_LINE_2_VILLAGER_LEGGINGS = "遠遠不如1000年前的村民護腿";
    private static final String MARKER_LINE_2_VILLAGER_BOOTS = "遠遠不如1000年前的村民靴子";
    private static final List<String> MARKER_LORE = List.of(
            MARKER_LINE_0_QIAN,
            "洗鍊多次所昇華出來的精華",
            MARKER_LINE_2_VILLAGER_HELMET
    );

    /**
     * 原版盔甲鍛造紋飾在簡／繁客戶端 lore 中的常見質料字樣（用於從舊 lore 抽出並固定放在分組附魔下方）。
     */
    private static final String[] ARMOR_TRIM_MATERIAL_MARKERS = {
            "红石质", "紅石質",
            "铜质", "銅質",
            "钻石质", "鑽石質",
            "金质", "金質",
            "铁质", "鐵質",
            "青金石质", "青金石質",
            "下界石英质", "下界石英質",
            "绿宝石质", "綠寶石質",
            "下界合金质", "下界合金質",
            "紫水晶质", "紫水晶質",
            "石英质", "石英質",
            "深板岩质", "深板岩質",
            "黑石质", "黑石質",
            "海晶石质", "海晶石質",
            "树脂质", "樹脂質"
    };

    private static final int[] RARE_PALETTE = {
            0x7FAFFF, 0x8AB2FF, 0x96B6FF, 0xA1B9FF, 0xADBDFF, 0xB8C0FF, 0xC4C4FF, 0xCFC7FF
    };

    private static final String LINE_UNBREAKABLE = "&#D6FFB4不&#A7FF95可&#79FF76破&#4AFF57壞";
    private static final String LINE_RARE_TAG = "&#7FAFFF[&#8AB2FF稀&#96B6FF有&#A1B9FF洞&#ADBDFF窟&#B8C0FF裝&#C4C4FF備&#CFC7FF]";

    private final @Nullable SublimationGearManager sublimationGear;
    private final @Nullable CustomEnchantService enchantRegistry;

    public SublimationWorkbenchManager() {
        this(null, null);
    }

    public SublimationWorkbenchManager(@Nullable SublimationGearManager sublimationGear) {
        this(sublimationGear, null);
    }

    public SublimationWorkbenchManager(@Nullable SublimationGearManager sublimationGear, @Nullable CustomEnchantService enchantRegistry) {
        this.sublimationGear = sublimationGear;
        this.enchantRegistry = enchantRegistry;
    }

    public void open(@NotNull Player player) {
        WorkbenchHolder holder = new WorkbenchHolder(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, SIZE, LegacyRgbParser.parse("&8昇華工作台"));
        holder.setInventory(inv);
        ItemStack filler = createFillerGlassPane();
        for (int i = 0; i < SIZE; i++) {
            if (i == SLOT_ITEM || i == SLOT_ANVIL) continue;
            inv.setItem(i, filler.clone());
        }
        ItemStack button = new ItemStack(Material.ANVIL);
        ItemMeta bm = button.getItemMeta();
        if (bm != null) {
            bm.displayName(LegacyRgbParser.parse("&e點擊昇華"));
            List<Component> bl = new ArrayList<>();
            bl.add(Component.text("將符合條件的裝備放在上方中央欄位後點此。", NamedTextColor.GRAY));
            bm.lore(bl);
            button.setItemMeta(bm);
        }
        inv.setItem(SLOT_ANVIL, button);
        player.openInventory(inv);
    }

    private static ItemStack createFillerGlassPane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE, 1);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" ", NamedTextColor.DARK_GRAY));
            maple.underground_item.util.ItemFlagUtil.applyStandardItemFlags(meta);
            pane.setItemMeta(meta);
        }
        return pane;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof WorkbenchHolder h)) return;
        if (!player.getUniqueId().equals(h.owner())) return;

        int raw = event.getRawSlot();
        boolean topInv = event.getClickedInventory() != null && event.getClickedInventory().equals(top);

        if (raw == SLOT_ANVIL && topInv) {
            event.setCancelled(true);
            if (event.getClick().isKeyboardClick() || event.getClick().isCreativeAction()) return;
            runSublimation(player, top);
            return;
        }

        if (topInv && raw != SLOT_ITEM) {
            event.setCancelled(true);
            return;
        }

        if (topInv && raw == SLOT_ITEM) {
            return;
        }

        // 玩家背包點擊：禁止 shift 塞到錯誤格
        if (!topInv && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            ItemStack current = top.getItem(SLOT_ITEM);
            if (current != null && !current.getType().isAir()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof WorkbenchHolder h)) return;
        if (!player.getUniqueId().equals(h.owner())) return;
        for (int slot : event.getRawSlots()) {
            if (slot < SIZE && slot != SLOT_ITEM) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof WorkbenchHolder h)) return;
        if (!player.getUniqueId().equals(h.owner())) return;
        ItemStack left = top.getItem(SLOT_ITEM);
        if (left != null && !left.getType().isAir()) {
            giveOrDrop(player, left);
            top.setItem(SLOT_ITEM, null);
        }
    }

    private static void giveOrDrop(Player player, ItemStack left) {
        var leftover = player.getInventory().addItem(left);
        for (ItemStack stack : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), stack);
        }
    }

    private void runSublimation(Player player, Inventory top) {
        ItemStack input = top.getItem(SLOT_ITEM);
        if (input == null || input.getType().isAir()) {
            player.sendMessage(Component.text("請先在中央欄位放入裝備。", NamedTextColor.RED));
            return;
        }
        ItemStack result = evaluateSublimation(input);
        if (result == null) {
            player.sendMessage(Component.text("條件不符：須為盔甲，且 lore 需同時包含三段終界殘骸敘述（第三段可為村民頭盔／胸甲／護腿／靴子任一）；保護附魔每級視為 +5 防禦。", NamedTextColor.RED));
            return;
        }
        top.setItem(SLOT_ITEM, result);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.1f);
        player.sendMessage(Component.text("昇華完成。", NamedTextColor.GREEN));
    }

    /**
     * @return 新物品，失敗則 null（邏輯與 {@link #trySublimate} 相同）
     */
    @Nullable
    private ItemStack evaluateSublimation(@NotNull ItemStack input) {
        ItemStack out = input.clone();
        ItemMeta meta = out.getItemMeta();
        if (meta == null) return null;

        ArmorKind kind = armorKind(out.getType());
        if (kind == ArmorKind.NONE) return null;

        List<Component> oldLore = meta.lore();
        if (oldLore == null || oldLore.isEmpty()) return null;
        if (!missingMarkerLines(oldLore).isEmpty()) return null;

        String plainName = meta.hasDisplayName()
                ? PlainTextComponentSerializer.plainText().serialize(meta.displayName())
                : "";
        if (plainName.contains("昇華 · 蒼界") || plainName.contains("昇華‧蒼界")) return null;

        int protLevel = protectionLevel(out);
        int baseHealth = kind.baseHealth;
        int baseDefense = kind.baseDefense;
        int defenseTotal = baseDefense + protLevel * 5;

        List<Component> oldWithoutTrim = new ArrayList<>();
        List<Component> trimMigrate = new ArrayList<>();
        splitArmorTrimLore(oldLore, trimMigrate, oldWithoutTrim);

        List<Component> bodyLore = stripSlotBonusLinesFromBody(kind,
                filterEnchantLoreFromBody(filterUnbreakableLines(oldWithoutTrim)));

        /*
         * 客戶端會把「原版附魔」畫在 lore 之前；若要洞窟評分顯示在最頂，需 HIDE_ENCHANTS
         * 並把附魔行手動插入到評分／屬性列之後。
         */
        List<Component> newLore = new ArrayList<>();
        newLore.add(LegacyRgbParser.parse("&8可放在鐵砧/附魔台"));
        newLore.add(LegacyRgbParser.parse("&7洞窟評分: &d-"));
        newLore.add(LegacyRgbParser.parse("&7生命: &a+" + baseHealth));
        newLore.add(LegacyRgbParser.parse("&7防禦: &a+" + defenseTotal));
        newLore.add(Component.empty());
        List<Component> groupedEnchants = buildGroupedEnchantLines(out, enchantRegistry);
        if (!groupedEnchants.isEmpty()) {
            newLore.addAll(groupedEnchants);
        }
        newLore.add(Component.empty());
        List<Component> trimSection = mergedArmorTrimSection(out, trimMigrate);
        if (!trimSection.isEmpty()) {
            newLore.addAll(trimSection);
            newLore.add(Component.empty());
        }
        newLore.addAll(bodyLore);
        newLore.add(Component.empty());
        newLore.add(LegacyRgbParser.parse(LINE_UNBREAKABLE));
        newLore.add(LegacyRgbParser.parse(LINE_RARE_TAG));

        meta.displayName(buildGradientName(kind.displayName));
        meta.lore(newLore);
        meta.setUnbreakable(true);
        maple.underground_item.util.ItemFlagUtil.applyStandardItemFlags(meta);
        if (sublimationGear != null) {
            sublimationGear.writeGearStats(meta, baseHealth, defenseTotal);
        }
        meta.setAttributeModifiers(null);
        // 保護等級已併入「防禦:+」與上方分組附魔 lore；從物品上移除原版 Protection，避免與虛擬防禦重疊
        removeVanillaProtectionEnchant(meta);
        out.setItemMeta(meta);
        // 空組件覆寫材質預設（護甲、韌性、穿上加血等），避免與虛擬屬性重疊
        out.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.itemAttributes().build());
        hideVanillaArmorTrimTooltip(out);
        return out;
    }

    /**
     * 鐵砧合成預覽／結果：左欄為昇華裝備且輸出為同類盔甲時，依輸出堆疊上當前 Protection（含與保護附魔書合併後等級）
     * 重算防禦（部位基礎 + 每級 +5）、更新頂部「防禦」行、分組附魔區與 PDC，並剝除原版 Protection（與昇華一致）。
     *
     * @param result     鐵砧輸出槽物品（通常為 event.getResult().clone()）
     * @param leftFallback 左欄物品，若 result 尚未帶昇華 PDC 時用於讀取生命等欄位
     * @return 是否已套用刷新
     */
    public boolean refreshSublimationAnvilOutput(@NotNull ItemStack result, @Nullable ItemStack leftFallback) {
        if (sublimationGear == null) return false;
        boolean outTagged = sublimationGear.isSublimationArmor(result);
        boolean leftTagged = leftFallback != null && sublimationGear.isSublimationArmor(leftFallback);
        if (!outTagged && !leftTagged) return false;
        if (leftFallback != null && leftFallback.getType() != result.getType()) return false;

        ArmorKind kind = armorKind(result.getType());
        if (kind == ArmorKind.NONE) return false;

        int baseHealth = sublimationGear.getSublimationHealthOnPiece(result);
        if (baseHealth <= 0 && leftFallback != null) {
            baseHealth = sublimationGear.getSublimationHealthOnPiece(leftFallback);
        }
        if (baseHealth <= 0) {
            baseHealth = kind.baseHealth;
        }

        int protLevel = protectionLevel(result);
        int defenseTotal = kind.baseDefense + protLevel * 5;

        ItemMeta meta = result.getItemMeta();
        if (meta == null) return false;
        List<Component> lore = meta.lore();
        if (lore == null || lore.size() < 8) return false;

        var plainSer = PlainTextComponentSerializer.plainText();
        int unbreakIdx = -1;
        for (int idx = lore.size() - 1; idx >= 0; idx--) {
            if (plainContainsUnbreakable(plainSer.serialize(lore.get(idx)))) {
                unbreakIdx = idx;
                break;
            }
        }
        if (unbreakIdx < 3) return false;

        final int afterStatsEmptyIdx = 4;
        if (lore.size() <= afterStatsEmptyIdx + 1) return false;

        int i = afterStatsEmptyIdx + 1;
        while (i < lore.size() && !loreLineIsBlank(lore.get(i), plainSer)) {
            i++;
        }
        if (i >= lore.size()) return false;
        int groupedEndExclusive = i;
        if (unbreakIdx - 1 <= groupedEndExclusive) return false;

        int j = groupedEndExclusive + 1;
        while (j < lore.size() && j < unbreakIdx - 1 && loreLineIsBlank(lore.get(j), plainSer)) {
            j++;
        }
        List<Component> trimBlock = new ArrayList<>();
        while (j < lore.size() && j < unbreakIdx - 1 && isArmorTrimLoreLine(lore.get(j), plainSer)) {
            trimBlock.add(lore.get(j));
            j++;
        }
        while (j < lore.size() && j < unbreakIdx - 1 && loreLineIsBlank(lore.get(j), plainSer)) {
            j++;
        }
        List<Component> bodyMid = new ArrayList<>(lore.subList(j, unbreakIdx - 1));

        List<Component> newLore = new ArrayList<>();
        newLore.add(LegacyRgbParser.parse("&8可放在鐵砧/附魔台"));
        newLore.add(LegacyRgbParser.parse("&7洞窟評分: &d-"));
        newLore.add(LegacyRgbParser.parse("&7生命: &a+" + baseHealth));
        newLore.add(LegacyRgbParser.parse("&7防禦: &a+" + defenseTotal));
        newLore.add(Component.empty());
        newLore.addAll(buildGroupedEnchantLines(result, enchantRegistry));
        newLore.add(Component.empty());
        List<Component> trimOut = !trimBlock.isEmpty() ? trimBlock : buildArmorTrimLinesFromDataComponent(result);
        if (!trimOut.isEmpty()) {
            newLore.addAll(trimOut);
            newLore.add(Component.empty());
        }
        newLore.addAll(bodyMid);
        newLore.add(Component.empty());
        newLore.add(lore.get(unbreakIdx));
        if (unbreakIdx + 1 < lore.size()) {
            newLore.add(lore.get(unbreakIdx + 1));
        } else {
            newLore.add(LegacyRgbParser.parse(LINE_RARE_TAG));
        }

        meta.lore(newLore);
        sublimationGear.writeGearStats(meta, baseHealth, defenseTotal);
        removeVanillaProtectionEnchant(meta);
        maple.underground_item.util.ItemFlagUtil.applyStandardItemFlags(meta);
        result.setItemMeta(meta);
        hideVanillaArmorTrimTooltip(result);
        return true;
    }

    /**
     * 昇華工作台：優先使用原版 {@link DataComponentTypes#TRIM} 生成「已有升級」區塊（與客戶端譯名一致），否則沿用從舊 lore 拆出的紋飾行。
     */
    private static List<Component> mergedArmorTrimSection(ItemStack stack, List<Component> trimFromLore) {
        List<Component> fromData = buildArmorTrimLinesFromDataComponent(stack);
        if (!fromData.isEmpty()) return fromData;
        return new ArrayList<>(trimFromLore);
    }

    /**
     * 由物品上的 trim 資料組件生成 lore（紅字紋飾名 + 質料名），對應原版「已有升級」區塊。
     * 譯鍵格式：{@code trim_pattern.<ns>.<id>}、{@code trim_material.<ns>.<id>}（與 vanilla lang 一致）。
     */
    private static List<Component> buildArmorTrimLinesFromDataComponent(ItemStack stack) {
        ItemArmorTrim wrap = stack.getData(DataComponentTypes.TRIM);
        if (wrap == null) return List.of();
        ArmorTrim at = wrap.armorTrim();
        var pk = at.getPattern().getKey();
        var mk = at.getMaterial().getKey();
        String patternKey = "trim_pattern." + pk.getNamespace() + "." + pk.getKey();
        String materialKey = "trim_material." + mk.getNamespace() + "." + mk.getKey();
        List<Component> lines = new ArrayList<>(4);
        lines.add(LegacyRgbParser.parse("&7已有升級:"));
        lines.add(Component.translatable(patternKey)
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        lines.add(Component.translatable(materialKey)
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        return lines;
    }

    /** 隱藏原版頂部 trim 提示，改由 lore 區塊顯示（保留 trim 組件以維持盔甲外觀）。 */
    private static void hideVanillaArmorTrimTooltip(ItemStack stack) {
        if (stack.getData(DataComponentTypes.TRIM) == null) return;
        TooltipDisplay existing = stack.getData(DataComponentTypes.TOOLTIP_DISPLAY);
        TooltipDisplay.Builder b = TooltipDisplay.tooltipDisplay();
        if (existing != null) {
            b.hideTooltip(existing.hideTooltip());
            for (DataComponentType t : existing.hiddenComponents()) {
                b.addHiddenComponents(t);
            }
        }
        b.addHiddenComponents(DataComponentTypes.TRIM);
        stack.setData(DataComponentTypes.TOOLTIP_DISPLAY, b.build());
    }

    private static boolean loreLineIsBlank(Component line, PlainTextComponentSerializer plainSer) {
        return plainSer.serialize(line).isBlank();
    }

    /** 將偵測到的紋飾／質料行移入 outTrim，其餘依序寫入 outRest（昇華時再插入分組附魔下方）。 */
    private static void splitArmorTrimLore(List<Component> source, List<Component> outTrim, List<Component> outRest) {
        outTrim.clear();
        outRest.clear();
        var plainSer = PlainTextComponentSerializer.plainText();
        for (Component c : source) {
            if (isArmorTrimLoreLine(c, plainSer)) {
                outTrim.add(c);
            } else {
                outRest.add(c);
            }
        }
    }

    private static boolean isArmorTrimLoreLine(Component line, PlainTextComponentSerializer plainSer) {
        String n = normalizeLoreLineForMatch(plainSer.serialize(line));
        return isArmorTrimPlainNormalized(n);
    }

    private static boolean isArmorTrimPlainNormalized(String n) {
        if (n.isEmpty()) return false;
        if (n.contains("盔甲纹饰") || n.contains("盔甲紋飾")) return true;
        if (n.contains("盔甲") && (n.contains("纹饰") || n.contains("紋飾"))) return true;
        for (String m : ARMOR_TRIM_MATERIAL_MARKERS) {
            if (n.contains(m)) return true;
        }
        return false;
    }

    @Nullable
    private static Enchantment vanillaProtectionEnchantment() {
        return Registry.ENCHANTMENT.get(NamespacedKey.minecraft("protection"));
    }

    /** 從昇華結果的 meta 移除 {@code minecraft:protection}（等級已換算進防禦與分組 lore）。 */
    private static void removeVanillaProtectionEnchant(ItemMeta meta) {
        Enchantment prot = vanillaProtectionEnchantment();
        if (prot == null) return;
        meta.removeEnchant(prot);
    }

    /**
     * 依 PDC 總防禦與部位基礎防禦反推已併入的 Protection 等級（每 5 點防禦視為 1 級保護）。
     * 鐵砧側用於判定是否已達原版保護上限，禁止再與保護附魔書合成。
     */
    public int impliedProtectionLevelFromPdc(@NotNull ItemStack stack) {
        if (sublimationGear == null || !sublimationGear.isSublimationArmor(stack)) return 0;
        ArmorKind k = armorKind(stack.getType());
        if (k == ArmorKind.NONE) return 0;
        int total = sublimationGear.getSublimationDefenseOnPiece(stack);
        int extra = total - k.baseDefense;
        if (extra <= 0) return 0;
        return extra / 5;
    }

    private static final int ENCHANTS_PER_LORE_LINE = 3;

    /** 從舊 lore 去掉插件自定義附魔標題／描述行（改由分組區顯示）。 */
    private List<Component> filterEnchantLoreFromBody(List<Component> body) {
        if (body.isEmpty()) return body;
        List<Component> out = new ArrayList<>();
        for (Component c : body) {
            String plain = PlainTextComponentSerializer.plainText().serialize(c);
            if (enchantRegistry != null && isCustomEnchantLoreLine(plain)) continue;
            out.add(c);
        }
        return out;
    }

    /**
     * 從舊 lore 刪除與「部位加成說明」相同的行（先前若誤加在模板裡則昇華時剥掉），不再寫入新物品。
     */
    private static List<Component> stripSlotBonusLinesFromBody(ArmorKind kind, List<Component> body) {
        if (body.isEmpty() || kind == ArmorKind.NONE) return body;
        List<Component> out = new ArrayList<>();
        for (Component c : body) {
            String norm = normalizeLoreLineForMatch(PlainTextComponentSerializer.plainText().serialize(c));
            if (norm.isEmpty()) {
                out.add(c);
                continue;
            }
            if (matchesSlotBonusLineToStrip(norm, kind)) continue;
            out.add(c);
        }
        return out;
    }

    private static String normalizeLoreLineForMatch(String plain) {
        if (plain == null || plain.isEmpty()) return "";
        String t = stripColorAndFormatCodesForMatch(plain.trim());
        return t.replaceAll("\\s+", " ").trim();
    }

    private static boolean matchesSlotBonusLineToStrip(String normalizedLine, ArmorKind kind) {
        return switch (kind) {
            case HELMET -> lineMatchesAny(normalizedLine,
                    "靈魂加持I",
                    "久氧",
                    "久氧-於水底永不斷氣");
            case CHEST -> lineMatchesAny(normalizedLine,
                    "靈魂加持II",
                    "爆炸抗性III",
                    "爆炸抗性-受到爆炸傷害時受到的傷害减少,爆炸不被擊飛",
                    "爆炸抗性-受到爆炸傷害時受到的傷害減少,爆炸不被擊飛");
            case LEGGINGS -> lineMatchesAny(normalizedLine,
                    "靈魂加持II",
                    "擊退保護II",
                    "擊退保護-擊退距離减少",
                    "擊退保護-擊退距離減少");
            case BOOTS -> lineMatchesAny(normalizedLine,
                    "靈魂加持II",
                    "靈魂加持I",
                    "緩衝",
                    "緩衝-從高空落下時不會受到摔落傷害");
            default -> false;
        };
    }

    private static boolean lineMatchesAny(String normalizedLine, String... exactPhrases) {
        for (String p : exactPhrases) {
            if (normalizedLine.equals(p)) return true;
        }
        return false;
    }

    private boolean isCustomEnchantLoreLine(String text) {
        if (text == null || text.isEmpty()) return false;
        for (CustomEnchantView e : enchantRegistry.getAll()) {
            if (text.startsWith(e.displayName() + " ")) return true;
            if (text.startsWith("  ") && text.contains(e.displayName())) return true;
        }
        return false;
    }

    /**
     * 原版／自定義附魔合併，依等級由高到低；每行最多三個，以灰色逗號分隔。
     * 原版：等級 ≤ 內建上限 &7（灰），超過 &6（金）。自定義：不超過上限用原普通／終極色，超過則 &6。
     */
    private static List<Component> buildGroupedEnchantLines(ItemStack item, @Nullable CustomEnchantService registry) {
        List<EnchantSortable> rows = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> e : item.getEnchantments().entrySet()) {
            rows.add(new EnchantSortable(e.getValue(), "v:" + e.getKey().getKey(), () -> vanillaEnchantChip(e.getKey(), e.getValue())));
        }
        if (registry != null) {
            for (Map.Entry<CustomEnchantView, Integer> e : registry.getEnchants(item).entrySet()) {
                rows.add(new EnchantSortable(e.getValue(), "c:" + e.getKey().id(), () -> customEnchantChip(e.getKey(), e.getValue())));
            }
        }
        if (rows.isEmpty()) return List.of();
        rows.sort(Comparator.comparingInt(EnchantSortable::level).reversed().thenComparing(EnchantSortable::sortKey, String.CASE_INSENSITIVE_ORDER));
        List<Component> lines = new ArrayList<>();
        for (int i = 0; i < rows.size(); i += ENCHANTS_PER_LORE_LINE) {
            Component line = Component.empty();
            for (int j = 0; j < ENCHANTS_PER_LORE_LINE && i + j < rows.size(); j++) {
                if (j > 0) {
                    line = line.append(Component.text(", ", NamedTextColor.GRAY));
                }
                line = line.append(rows.get(i + j).build());
            }
            lines.add(line);
        }
        return lines;
    }

    private record EnchantSortable(int level, String sortKey, Supplier<Component> builder) {
        Component build() {
            return builder.get();
        }
    }

    private static Component vanillaEnchantChip(Enchantment enchant, int level) {
        int cap = Math.max(1, enchant.getMaxLevel());
        NamedTextColor color = level > cap ? NamedTextColor.GOLD : NamedTextColor.GRAY;
        NamespacedKey k = enchant.getKey();
        String trKey = "enchantment." + k.getNamespace() + "." + k.getKey();
        return Component.translatable(trKey)
                .color(color)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(" " + toRomanNumeral(level), color).decoration(TextDecoration.ITALIC, false));
    }

    private static Component customEnchantChip(CustomEnchantView enchant, int level) {
        int cap = Math.max(1, enchant.maxLevel());
        TextColor color = level > cap
                ? TextColor.color(0xFFAA00)
                : TextColor.color(enchant.loreColorRgb());
        return Component.text(enchant.displayName() + " " + toRomanNumeral(level), color)
                .decoration(TextDecoration.ITALIC, false);
    }

    /** 任意正整數羅馬數字（例：125 → CXXV）。 */
    private static String toRomanNumeral(int n) {
        if (n < 1) return "?";
        if (n > 3999) return String.valueOf(n);
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] syms = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        int rest = n;
        for (int i = 0; i < values.length; i++) {
            while (rest >= values[i]) {
                sb.append(syms[i]);
                rest -= values[i];
            }
        }
        return sb.toString();
    }

    private static boolean markerLineSatisfied(int index, String normalized) {
        if (index == 0) {
            return normalized.contains(MARKER_LINE_0_QIAN) || normalized.contains(MARKER_LINE_0_YI);
        }
        if (index == 2) {
            return normalized.contains(MARKER_LINE_2_VILLAGER_HELMET)
                    || normalized.contains(MARKER_LINE_2_VILLAGER_CHEST)
                    || normalized.contains(MARKER_LINE_2_VILLAGER_LEGGINGS)
                    || normalized.contains(MARKER_LINE_2_VILLAGER_BOOTS);
        }
        return normalized.contains(MARKER_LORE.get(index));
    }

    private static List<String> missingMarkerLines(List<Component> lore) {
        String n = normalizedLoreCombined(lore);
        List<String> miss = new ArrayList<>();
        for (int i = 0; i < MARKER_LORE.size(); i++) {
            if (!markerLineSatisfied(i, n)) {
                if (i == 0) {
                    miss.add(MARKER_LINE_0_QIAN + " 或 " + MARKER_LINE_0_YI);
                } else if (i == 2) {
                    miss.add(MARKER_LINE_2_VILLAGER_HELMET + "／村民胸甲／村民護腿／村民靴子 句式擇一");
                } else {
                    miss.add(MARKER_LORE.get(i));
                }
            }
        }
        return miss;
    }

    /** 與標記比對用：剥色碼、合併、壓縮空白。 */
    private static String normalizedLoreCombined(List<Component> lore) {
        if (lore == null || lore.isEmpty()) return "";
        String combined = lore.stream()
                .map(c -> PlainTextComponentSerializer.plainText().serialize(c))
                .map(SublimationWorkbenchManager::stripColorAndFormatCodesForMatch)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        return combined.replaceAll("\\s+", " ").trim();
    }

    /**
     * @return 新物品，或 null 表示失敗
     */
    @Nullable
    public ItemStack trySublimate(@NotNull ItemStack input) {
        return evaluateSublimation(input);
    }

    private static String stripColorAndFormatCodesForMatch(String s) {
        if (s == null || s.isEmpty()) return "";
        String t = stripAmpersandColorCodes(s);
        t = stripSectionSignColorCodes(t);
        t = t.replaceAll("(?i)#[0-9A-Fa-f]{6}", "");
        return t;
    }

    /** 移除 &#[0-9a-f]{6} 與 &0-9a-fk-or 等 */
    private static String stripAmpersandColorCodes(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); ) {
            char c = s.charAt(i);
            if (c == '&' && i + 1 < s.length()) {
                if (s.charAt(i + 1) == '#' && i + 8 <= s.length()) {
                    String hex = s.substring(i + 2, i + 8);
                    if (hex.matches("[0-9A-Fa-f]{6}")) {
                        i += 8;
                        continue;
                    }
                }
                char n = s.charAt(i + 1);
                if ("0123456789abcdefABCDEFkKlLmMnNoOrR".indexOf(n) >= 0) {
                    i += 2;
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** 移除 §x§R§R§G§G§B§B 與一般 § 代碼 */
    private static String stripSectionSignColorCodes(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); ) {
            if (s.charAt(i) == '§' && i + 1 < s.length()) {
                if (s.charAt(i + 1) == 'x' && i + 14 <= s.length()) {
                    int pos = i + 2;
                    boolean ok = true;
                    for (int k = 0; k < 6; k++) {
                        if (pos + 1 >= s.length() || s.charAt(pos) != '§') {
                            ok = false;
                            break;
                        }
                        if (Character.digit(s.charAt(pos + 1), 16) < 0) {
                            ok = false;
                            break;
                        }
                        pos += 2;
                    }
                    if (ok) {
                        i = pos;
                        continue;
                    }
                }
                i += 2;
                continue;
            }
            out.append(s.charAt(i));
            i++;
        }
        return out.toString();
    }

    private static List<Component> filterUnbreakableLines(List<Component> lore) {
        List<Component> out = new ArrayList<>();
        for (Component line : lore) {
            String plain = PlainTextComponentSerializer.plainText().serialize(line);
            if (plainContainsUnbreakable(plain)) continue;
            out.add(line);
        }
        return out;
    }

    private static boolean plainContainsUnbreakable(String plain) {
        if (plain == null) return false;
        return plain.contains("不可破壞") || plain.contains("无法破坏") || plain.contains("無法破壞");
    }

    private static int protectionLevel(ItemStack item) {
        Enchantment prot = vanillaProtectionEnchantment();
        if (prot == null) return 0;
        return item.getEnchantmentLevel(prot);
    }

    private static Component buildGradientName(String text) {
        int[] cps = text.codePoints().toArray();
        if (cps.length == 0) return Component.empty();
        StringBuilder sb = new StringBuilder("&l");
        for (int i = 0; i < cps.length; i++) {
            int idx = cps.length <= 1 ? 0 : (i * (RARE_PALETTE.length - 1)) / (cps.length - 1);
            sb.append("&#").append(String.format("%06X", RARE_PALETTE[idx]));
            sb.appendCodePoint(cps[i]);
        }
        return LegacyRgbParser.parse(sb.toString());
    }

    private enum ArmorKind {
        NONE(0, 0, ""),
        HELMET(43, 29, "昇華 · 蒼界頭盔"),
        CHEST(72, 47, "昇華 · 蒼界胸甲"),
        LEGGINGS(66, 31, "昇華 · 蒼界護腿"),
        BOOTS(21, 20, "昇華 · 蒼界靴子");

        final int baseHealth;
        final int baseDefense;
        final String displayName;

        ArmorKind(int baseHealth, int baseDefense, String displayName) {
            this.baseHealth = baseHealth;
            this.baseDefense = baseDefense;
            this.displayName = displayName;
        }
    }

    private static ArmorKind armorKind(Material m) {
        if (m == Material.TURTLE_HELMET) return ArmorKind.HELMET;
        String n = m.name();
        if (n.endsWith("_HELMET")) return ArmorKind.HELMET;
        if (n.endsWith("_CHESTPLATE")) return ArmorKind.CHEST;
        if (n.endsWith("_LEGGINGS")) return ArmorKind.LEGGINGS;
        if (n.endsWith("_BOOTS")) return ArmorKind.BOOTS;
        return ArmorKind.NONE;
    }

    private static final class WorkbenchHolder implements InventoryHolder {
        private final UUID owner;
        private Inventory inventory;

        WorkbenchHolder(UUID owner) {
            this.owner = owner;
        }

        UUID owner() {
            return owner;
        }

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            if (inventory == null) throw new IllegalStateException("inventory not set");
            return inventory;
        }
    }
}
