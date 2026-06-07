package maple.underground_state.dungeon.panel;

import maple.underground_state.UndergroundState;
import maple.underground_state.gui.CopperSwordMenuGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 副本面板 GUI：主面板（職業、地底洞窟等級）+ 等級進度子面板。
 */
@SuppressWarnings("ALL")
public final class DungeonPanelManager implements Listener {
    private static final String PANEL_TITLE = "副本面板";
    private static final String DUNGEON_PANEL_ID = "dungeon_panel";
    private static final String CLASS_LEVEL_PANEL_ID = "class_level_panel";
    private static final String COLLECTION_PANEL_ID = "dungeon_collection";
    private static final String GUARANTEE_PANEL_ID = "dungeon_guarantee";

    /** Slot 13: 玩家頭顱皮膚 */
    private static final String SKULL_TEXTURE = "8db4c861843b6c078781267ef335305f44bcd701df352ec4b1b1a556fc69a5d8";

    /** 主面板：13 地底洞窟等級、29-33 職業箭（副本保底／收藏改在「地底洞窟 等級」子面板顯示） */
    private static final int MAIN_CAVE_LEVEL_SLOT = 13;
    private static final int[] MAIN_CLASS_SLOTS = {29, 30, 31, 32, 33};
    private static final int MAIN_BACK_SLOT = 48;
    private static final int MAIN_CLOSE_SLOT = 49;
    /** 地底洞窟等級面板（category=-1）內：40 副本保底、45 副本收藏 */
    private static final int LEVEL_CAVE_GUARANTEE_SLOT = 40;
    private static final int LEVEL_CAVE_COLLECTION_SLOT = 45;

    /** 副本收藏／保底子介面：4 標題、19-25,28-34 紅玻璃／頭顱區、48 返回、49 關閉 */
    private static final int SUB_TITLE_SLOT = 4;
    private static final int[] SUB_HEAD_SLOTS = {19, 20, 21, 22};
    private static final int[] SUB_RED_GLASS_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int SUB_BACK_SLOT = 48;
    private static final int SUB_CLOSE_SLOT = 49;

    /** 保底樓層詳情頁：鋪黑玻璃，10-16/19-25/28-34/37-43 紅玻璃，中央 1 個頭顱、48 返回、49 關閉 */
    private static final int[] FLOOR_DETAIL_HEAD_SLOTS = {4};
    private static final int[] FLOOR_DETAIL_RED_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };
    private static final int FLOOR_DETAIL_BACK_SLOT = 48;
    private static final int FLOOR_DETAIL_CLOSE_SLOT = 49;

    /** 副本收藏頭顱紋理（19-22 顯示，23-25 僅儲存待後續顯示） */
    private static final String[] COLLECTION_HEAD_TEXTURES = {
        "9f742eb7fd661d382885b22bdc9a29e22665b1d191a30d1f67d0dc958cfe93d8",
        "cf35c124092a77bc028716c33367720d2be2f929faeb7514d96f39dfdeb7bc8a",
        "bbc24dcff213b1ff1bbcfe01f83f0e7e13aab6d6c01b3a1e8558a15eebce9c96",
        "b7024bd6cd7d3396a31ac30f0a34ab61247db2837e32fa6830f296f6461a6ad1",
        "89f0a71cdaad939883b18c71ae83696943c506d84fd3faf6cbf69d4ea434e1ec",
        "d895719ba0fa76c841486337ffbb9382300d6a43646a1ad6ce14ecfb3d474c9d",
        "250c82c52c727b49cd8f16eb61a6e7cb3ba37523db28d2ee8a88057161f2e506"
    };
    private static final String GUARANTEE_HEAD_TEXTURE = "49dace0378cfc066c6c3a6c96a95aa8f5181dd2e00263838286f743d984c29c1";

    /** 等級進度條 slot 順序（對應 Lv 1–25） */
    private static final int[] LEVEL_SLOTS_PAGE1 = {
        9, 18, 27, 28, 29, 20, 11, 2, 3, 4, 13, 22, 31, 32, 33, 24, 15, 6, 7, 8, 17, 26, 35, 44, 53
    };
    private static final int LEVEL_PAGE_ARROW_SLOT = 50;
    private static final int LEVEL_BACK_SLOT = 48;
    private static final int LEVEL_CLOSE_SLOT = 49;
    private static final int LEVEL_CLASS_DETAIL_SLOT = 45;

    private static final String[] CLASS_NAMES = {"治療師", "法師", "戰士", "弓箭手", "坦克"};
    /** 職業詳情頭顱皮膚（slot 45） */
    private static final String[] CLASS_SKULL_TEXTURES = {
        "b41e69c7bcf1af0c55042089cbc94fbca0ca7d8f4a4004e8ccf57b2e2817d146",
        "a24a23e558d7f9fe5dd88dffbf56dfdd225a1de14e2613f1834ca3e6f7f02ecc",
        "a0af67d29d3ce8d388dacf8a01e23f099e6804d5c9912f3f0538b71c069d7849",
        "c6f7e37ee3f65f9fb1c83baef250230e091d1b462f1c49ff2af940cda1a1760a",
        "113692b325ec926b0fbf067a2584229d2fa5e977c92ed9d433a4ae388574556f"
    };
    private static final PotionType[] CLASS_ARROW_TYPES = {
        PotionType.REGENERATION, PotionType.SWIFTNESS, PotionType.HARMING,
        PotionType.STRENGTH, PotionType.POISON
    };
    private static final TextColor SKILL_MENU_NAME_RGB = TextColor.color(0x63E6A6);
    private static final TextColor SKILL_MENU_ACTION_RGB = TextColor.color(0xFFE36E);

    private final UndergroundState plugin;

    /** 地底洞窟經驗（category -1）每人累積 XP，重啟後重置 */
    private final Map<UUID, Long> caveXPStorage = new ConcurrentHashMap<>();

    public DungeonPanelManager(UndergroundState plugin) {
        this.plugin = plugin;
    }

    public void openMainPanel(Player player) {
        if (player == null) return;
        DungeonPanelDataProvider dataProvider = plugin != null ? plugin.getDungeonPanelDataProvider() : null;
        if (plugin != null && dataProvider != null) {
            UUID playerId = player.getUniqueId();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                dataProvider.refreshExperience(playerId);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player online = Bukkit.getPlayer(playerId);
                    if (online != null && online.isOnline()) {
                        openMainPanelLoaded(online);
                    }
                });
            });
            return;
        }
        openMainPanelLoaded(player);
    }

    private void openMainPanelLoaded(Player player) {
        DungeonPanelHolder holder = new DungeonPanelHolder(DUNGEON_PANEL_ID, -1, 0);
        Inventory inv = Bukkit.createInventory(holder, 54,
            Component.text(PANEL_TITLE, NamedTextColor.GOLD));
        holder.setInventory(inv);
        fillMainPanel(inv, player);
        player.openInventory(inv);
    }

    private void fillMainPanel(Inventory inv, Player player) {
        ItemStack glass = glassPane(" ");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, glass);
        }
        inv.setItem(MAIN_CAVE_LEVEL_SLOT, createCaveLevelSkull(player));
        for (int i = 0; i < 5; i++) {
            inv.setItem(MAIN_CLASS_SLOTS[i], createClassArrow(i, player));
        }
        inv.setItem(MAIN_BACK_SLOT, createBackToSkillsItem());
        inv.setItem(MAIN_CLOSE_SLOT, createClosePageItem());
    }

    /** 「點擊進入查看」漸變色：&#FDFF71→#FF9E4F */
    private static final String[] CLICK_ENTER_GRADIENT = {
        "#FDFF71", "#FDEF6B", "#FEDF66", "#FECF60", "#FEBE5A", "#FFAE55", "#FF9E4F"
    };

    private static Component buildClickToEnterGradient(boolean withExclamation) {
        String text = withExclamation ? "點擊進入查看！" : "點擊進入查看";
        Component out = Component.empty();
        for (int i = 0; i < text.length(); i++) {
            int idx = Math.min(i, CLICK_ENTER_GRADIENT.length - 1);
            out = out.append(Component.text(String.valueOf(text.charAt(i)), TextColor.fromHexString(CLICK_ENTER_GRADIENT[idx])));
        }
        return out.decoration(TextDecoration.ITALIC, false);
    }

    /** 副本保底說明 lore（繁體，入口／標題用） */
    private static List<Component> buildGuaranteeLore() {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("每次完成評分為 S 或 S+ 的地底洞窟樓層時，", NamedTextColor.GRAY));
        lore.add(Component.text("該局的分數會填入你的洞窟保底計量器。", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("當某樓層的保底計量器滿額時，你選擇的保底掉落物", NamedTextColor.GRAY));
        lore.add(Component.text("必定會出現在該層的其中一個箱子裡！", NamedTextColor.YELLOW));
        lore.add(Component.empty());
        lore.add(buildClickToEnterGradient(false));
        return lore;
    }

    /** 保底樓層標籤：0=F1, 1=F2, 2=F3 */
    private static final String[] GUARANTEE_FLOOR_LABELS = {"F1", "F2", "F3"};

    /**
     * 單一樓層保底 lore（繁體），F1／F2／F3 結構相同。
     * @param floorLabel 樓層標籤（如 "F1"、"F2"、"F3"）
     * @param progressXp 當前計量器 XP
     * @param maxXp 計量器滿額 XP
     * @param selectedDrop 已選擇的掉落物名稱（可為 "尚未選擇"）
     */
    private static List<Component> buildGuaranteeFloorLore(String floorLabel, int progressXp, int maxXp, String selectedDrop) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("每次你在地底洞窟（" + floorLabel + "）中達成 S+ 或以上評分時，", NamedTextColor.GRAY));
        lore.add(Component.text("系統會依該局洞窟得分填充此樓層的保底計量器。", NamedTextColor.GRAY));
        lore.add(Component.text("若達成 S 評分，計量器將填充至該局得分的 70%。", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("當計量條滿額時，你選擇的保底掉落物將在下次", NamedTextColor.GRAY));
        lore.add(Component.text("符合條件的 Boss 戰後，保證出現在寶箱中！", NamedTextColor.YELLOW));
        lore.add(Component.empty());
        lore.add(Component.text("已選擇保底掉落物：", NamedTextColor.DARK_GRAY));
        lore.add(Component.text(selectedDrop != null ? selectedDrop : "尚未選擇", NamedTextColor.WHITE));
        lore.add(Component.empty());
        int pct = maxXp <= 0 ? 0 : Math.min(100, (progressXp * 100) / maxXp);
        lore.add(Component.text("當前進度：", NamedTextColor.DARK_GRAY)
            .append(Component.text(pct + "%", NamedTextColor.YELLOW)));
        lore.add(Component.text("計量器 ", NamedTextColor.DARK_GRAY)
            .append(Component.text(progressXp + "/" + maxXp + " XP", NamedTextColor.GRAY)));
        lore.add(Component.empty());
        lore.add(buildClickToEnterGradient(true));
        return lore;
    }

    /** 地底洞窟等級面板：副本保底入口頭顱（地底洞窟 RGB 漸變） */
    private ItemStack createGuaranteeEntryHead(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            applySkullTextureToMeta(meta, GUARANTEE_HEAD_TEXTURE);
            meta.displayName(buildCaveStyleTitle(player, "地底洞窟副本保底"));
            meta.lore(buildGuaranteeLore());
            head.setItemMeta(meta);
        }
        return head;
    }

    /** 副本收藏總數（進度顯示用） */
    private static final int TOTAL_COLLECTIONS = 4;

    /** 副本收藏說明 lore（繁體，含進度 已完成/總數 與進度條） */
    private static List<Component> buildCollectionLore(int completed, int total) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("在此查看你的收藏進度，並領取對應總回合數的獎勵。", NamedTextColor.GRAY));
        lore.add(Component.empty());
        int pct = total <= 0 ? 0 : Math.min(100, (completed * 100) / total);
        lore.add(Component.text("當前進度：", NamedTextColor.DARK_GRAY)
            .append(Component.text(pct + "%", NamedTextColor.YELLOW)));
        int barLen = 10;
        int filled = total <= 0 ? 0 : (int) Math.round(barLen * (double) completed / total);
        lore.add(DungeonClassSkills.buildStrikethroughProgressBar(barLen, filled, NamedTextColor.YELLOW, NamedTextColor.DARK_GRAY)
            .append(Component.text(" " + completed + "/" + total, NamedTextColor.GRAY)));
        lore.add(Component.empty());
        lore.add(buildClickToEnterGradient(true));
        return lore;
    }

    /** 地底洞窟等級面板：副本收藏入口告示牌（地底洞窟 RGB 漸變） */
    private ItemStack createCollectionEntrySign(Player player) {
        ItemStack sign = new ItemStack(Material.OAK_SIGN, 1);
        ItemMeta meta = sign.getItemMeta();
        if (meta != null) {
            meta.displayName(buildCaveStyleTitle(player, "地底洞窟副本收藏"));
            meta.lore(buildCollectionLore(0, TOTAL_COLLECTIONS));
            sign.setItemMeta(meta);
        }
        return sign;
    }

    public void openCollectionPanel(Player player) {
        if (player == null) return;
        CollectionPanelHolder holder = new CollectionPanelHolder();
        Inventory inv = Bukkit.createInventory(holder, 54,
            buildCaveStyleTitle(player, "地底洞窟副本收藏"));
        holder.setInventory(inv);
        fillCollectionPanel(inv, player);
        player.openInventory(inv);
    }

    private void fillCollectionPanel(Inventory inv, Player player) {
        ItemStack blackGlass = blackGlassPane(" ");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, blackGlass);
        }
        inv.setItem(SUB_TITLE_SLOT, createCollectionTitleSign(player));
        ItemStack redGlass = redGlassPane(" ");
        for (int slot : SUB_RED_GLASS_SLOTS) {
            inv.setItem(slot, redGlass);
        }
        for (int i = 0; i < SUB_HEAD_SLOTS.length; i++) {
            inv.setItem(SUB_HEAD_SLOTS[i], createCollectionHead(i, false));
        }
        inv.setItem(SUB_BACK_SLOT, createBackToPanelItem());
        inv.setItem(SUB_CLOSE_SLOT, createClosePageItem());
    }

    private ItemStack createCollectionTitleSign(Player player) {
        ItemStack sign = new ItemStack(Material.OAK_SIGN, 1);
        ItemMeta meta = sign.getItemMeta();
        if (meta != null) {
            meta.displayName(buildCaveStyleTitle(player, "地底洞窟副本收藏"));
            meta.lore(buildCollectionLore(0, TOTAL_COLLECTIONS));
            sign.setItemMeta(meta);
        }
        return sign;
    }

    /**
     * 副本收藏／保底子介面內 slot 19–22 的頭顱。
     * 保底時：0=F1、1=F2、2=F3，各有樓層專用 lore。
     */
    private ItemStack createCollectionHead(int index, boolean inGuaranteePanel) {
        if (index < 0 || index >= COLLECTION_HEAD_TEXTURES.length) return blackGlassPane(" ");
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            applySkullTextureToMeta(meta, COLLECTION_HEAD_TEXTURES[index]);
            if (inGuaranteePanel) {
                String floor = index < GUARANTEE_FLOOR_LABELS.length ? GUARANTEE_FLOOR_LABELS[index] : "F1";
                meta.displayName(Component.text("地底洞窟保底（" + floor + "）", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
                meta.lore(buildGuaranteeFloorLore(floor, 0, 100, "尚未選擇"));
            } else {
                meta.displayName(Component.text("收藏 #" + (index + 1), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                meta.lore(buildCollectionLore(0, TOTAL_COLLECTIONS));
            }
            head.setItemMeta(meta);
        }
        return head;
    }

    public void openGuaranteePanel(Player player) {
        if (player == null) return;
        GuaranteePanelHolder holder = new GuaranteePanelHolder();
        Inventory inv = Bukkit.createInventory(holder, 54,
            buildCaveStyleTitle(player, "地底洞窟副本保底"));
        holder.setInventory(inv);
        fillGuaranteePanel(inv, player);
        player.openInventory(inv);
    }

    private void fillGuaranteePanel(Inventory inv, Player player) {
        ItemStack blackGlass = blackGlassPane(" ");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, blackGlass);
        }
        inv.setItem(SUB_TITLE_SLOT, createGuaranteeTitleHead(player));
        ItemStack redGlass = redGlassPane(" ");
        for (int slot : SUB_RED_GLASS_SLOTS) {
            inv.setItem(slot, redGlass);
        }
        for (int i = 0; i < Math.min(SUB_HEAD_SLOTS.length, GUARANTEE_FLOOR_LABELS.length); i++) {
            inv.setItem(SUB_HEAD_SLOTS[i], createCollectionHead(i, true));
        }
        inv.setItem(SUB_BACK_SLOT, createBackToPanelItem());
        inv.setItem(SUB_CLOSE_SLOT, createClosePageItem());
    }

    /** 開啟保底樓層詳情頁（點擊 F1／F2／F3 後）：黑底、指定 slot 紅玻璃、中央 1 個頭顱。 */
    public void openGuaranteeFloorDetailPanel(Player player, String floor) {
        if (player == null || floor == null) return;
        GuaranteeFloorDetailHolder holder = new GuaranteeFloorDetailHolder(floor);
        Inventory inv = Bukkit.createInventory(holder, 54,
            buildCaveStyleTitle(player, "地底洞窟保底（" + floor + "）"));
        holder.setInventory(inv);
        fillGuaranteeFloorDetailPanel(inv, player, floor);
        player.openInventory(inv);
    }

    private void fillGuaranteeFloorDetailPanel(Inventory inv, Player player, String floor) {
        ItemStack blackGlass = blackGlassPane(" ");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, blackGlass);
        }
        ItemStack redGlass = redGlassPane(" ");
        for (int slot : FLOOR_DETAIL_RED_SLOTS) {
            inv.setItem(slot, redGlass);
        }
        ItemStack headItem = createGuaranteeFloorDetailHead(player, floor);
        for (int slot : FLOOR_DETAIL_HEAD_SLOTS) {
            inv.setItem(slot, headItem.clone());
        }
        inv.setItem(FLOOR_DETAIL_BACK_SLOT, createBackToPanelItem());
        inv.setItem(FLOOR_DETAIL_CLOSE_SLOT, createClosePageItem());
    }

    /** 樓層詳情頁中央頭顱（lore 與該樓層一致） */
    private ItemStack createGuaranteeFloorDetailHead(Player player, String floor) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            applySkullTextureToMeta(meta, GUARANTEE_HEAD_TEXTURE);
            meta.displayName(Component.text("地底洞窟保底（" + floor + "）", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(buildGuaranteeFloorLore(floor, 0, 100, "尚未選擇"));
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack createGuaranteeTitleHead(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            applySkullTextureToMeta(meta, GUARANTEE_HEAD_TEXTURE);
            meta.displayName(buildCaveStyleTitle(player, "地底洞窟副本保底"));
            meta.lore(buildGuaranteeLore());
            head.setItemMeta(meta);
        }
        return head;
    }

    private static ItemStack blackGlassPane(String name) {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) meta.displayName(Component.text(name, NamedTextColor.DARK_GRAY));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack redGlassPane(String name) {
        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) meta.displayName(Component.text(name, NamedTextColor.DARK_GRAY));
        item.setItemMeta(meta);
        return item;
    }

    private static void applySkullTextureToMeta(SkullMeta meta, String textureId) {
        if (meta == null || textureId == null || textureId.isBlank()) {
            return;
        }
        PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
        PlayerTextures textures = profile.getTextures();
        try {
            URL skinUrl = URI.create("http://textures.minecraft.net/texture/" + textureId).toURL();
            textures.setSkin(skinUrl);
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
        } catch (IllegalArgumentException | MalformedURLException ignored) {
        }
    }

    /** 建構職業箭顯示名稱：基岩用戶（名稱以.開頭）用 &b，其餘用漸變色 */
    private static Component buildClassDisplayName(int classIndex, Player player) {
        String name = CLASS_NAMES[classIndex];
        if (player != null && player.getName().startsWith(".")) {
            return Component.text(name, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false);
        }
        Component comp = Component.text(name, NamedTextColor.WHITE);
        switch (classIndex) {
            case 0 -> comp = Component.text("治", TextColor.fromHexString("#E0F7FA"))
                .append(Component.text("療", TextColor.fromHexString("#B0EBF2")))
                .append(Component.text("師", TextColor.fromHexString("#80DEEA")));
            case 1 -> comp = Component.text("法", TextColor.fromHexString("#F3E5F5"))
                .append(Component.text("師", TextColor.fromHexString("#CE93D8")));
            case 2 -> comp = Component.text("戰", TextColor.fromHexString("#FFEBEE"))
                .append(Component.text("士", TextColor.fromHexString("#EF9A9A")));
            case 3 -> comp = Component.text("弓", TextColor.fromHexString("#E8F5E9"))
                .append(Component.text("箭", TextColor.fromHexString("#C7E6C8")))
                .append(Component.text("手", TextColor.fromHexString("#A5D6A7")));
            case 4 -> comp = Component.text("坦", TextColor.fromHexString("#FAFAFA"))
                .append(Component.text("克", TextColor.fromHexString("#EEEEEE")));
            default -> { }
        }
        return comp.decoration(TextDecoration.ITALIC, false);
    }

    /** 建構地底洞窟顯示名稱：基岩用戶（名稱以.開頭）用 &6 金色，其餘用漸變色 */
    private static Component buildCaveDisplayName(Player player) {
        if (player != null && player.getName().startsWith(".")) {
            return Component.text("地底洞窟", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false);
        }
        return Component.text("地", TextColor.fromHexString("#FFAEAE"))
            .append(Component.text("底", TextColor.fromHexString("#FFC894")))
            .append(Component.text("洞", TextColor.fromHexString("#FFE17A")))
            .append(Component.text("窟", TextColor.fromHexString("#FFFB60")))
            .decoration(TextDecoration.ITALIC, false);
    }

    /** 地底洞窟風格 RGB 漸變（延伸至「地底洞窟副本保底」「地底洞窟副本收藏」等 8 字） */
    private static final String[] CAVE_GRADIENT_8 = {
        "#FFAEAE", "#FFC894", "#FFE17A", "#FFFB60", "#E8FF80", "#C8FFA0", "#A8FFC0", "#88FFE0"
    };

    private static Component buildCaveStyleTitle(Player player, String fullText) {
        if (player != null && player.getName().startsWith(".")) {
            return Component.text(fullText, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false);
        }
        Component out = Component.empty();
        for (int i = 0; i < fullText.length(); i++) {
            int idx = Math.min(i, CAVE_GRADIENT_8.length - 1);
            out = out.append(Component.text(String.valueOf(fullText.charAt(i)), TextColor.fromHexString(CAVE_GRADIENT_8[idx])));
        }
        return out.decoration(TextDecoration.ITALIC, false);
    }

    /** Slot 13：地底洞窟等級（頭顱皮膚）。經驗從 SQLite 讀取（進地牢後由 MySQL 拉取並存於 dungeon_rewards.db）。 */
    private ItemStack createCaveLevelSkull(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            applySkullTextureToMeta(meta, SKULL_TEXTURE);
            meta.displayName(buildCaveDisplayName(player));
            long xp = player != null ? getClassXP(player, -1) : 0;
            meta.lore(DungeonClassSkills.buildCaveLevelLore(xp));
            head.setItemMeta(meta);
        }
        return head;
    }

    /** 建立與 /dungeon_panel 相同的職業箭頭（名稱、lore、藥水類型），供職業選擇 UI 等處複用。 */
    public ItemStack createClassArrowForDisplay(int classIndex, Player player) {
        return createClassArrow(classIndex, player);
    }

    public ItemStack createCaveLevelSkullForDisplay(Player player) {
        return createCaveLevelSkull(player);
    }

    public ItemStack createCaveOverviewSkullForSkills(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            applySkullTextureToMeta(meta, SKULL_TEXTURE);
            meta.displayName(Component.text("地底洞窟等級", SKILL_MENU_NAME_RGB)
                .decoration(TextDecoration.ITALIC, false)
                .decorate(TextDecoration.BOLD));
            meta.lore(List.of(
                Component.text("查看地底洞窟副本資訊、職業面板、", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("副本保底與收藏入口。", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("地底洞窟是一個以隊伍職業、樓層推進、", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("怪物戰鬥與寶箱獎勵為核心的副本。", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("點擊查看！", SKILL_MENU_ACTION_RGB)
                    .decoration(TextDecoration.ITALIC, false)
                    .decorate(TextDecoration.BOLD)
            ));
            meta.addItemFlags(ItemFlag.values());
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack createClassArrow(int classIndex, Player player) {
        ItemStack arrow = new ItemStack(Material.TIPPED_ARROW, 1);
        if (arrow.getItemMeta() instanceof PotionMeta meta) {
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            meta.setBasePotionData(new PotionData(CLASS_ARROW_TYPES[classIndex]));
            meta.displayName(buildClassDisplayName(classIndex, player));
            long xp = player != null ? getClassXP(player, classIndex) : 0;
            boolean solo = isSoloClassSelection(player);
            meta.lore(DungeonClassSkills.buildClassLore(classIndex, xp, solo));
            arrow.setItemMeta(meta);
        }
        return arrow;
    }

    private boolean isSoloClassSelection(Player player) {
        DungeonPanelDataProvider dataProvider = plugin != null ? plugin.getDungeonPanelDataProvider() : null;
        return player != null && dataProvider != null && dataProvider.isSoloClassSelection(player);
    }

    public void openClassLevelPanel(Player player, int category, int page) {
        if (player == null || category < -1 || category > 4) return;
        if (page < 0 || page > 1) page = 0;
        String title = category < 0 ? "地底洞窟 等級" : (CLASS_NAMES[category] + " 等級");
        ClassLevelPanelHolder holder = new ClassLevelPanelHolder(category, page);
        Inventory inv = Bukkit.createInventory(holder, 54,
            Component.text(title, NamedTextColor.GOLD));
        holder.setInventory(inv);
        fillClassLevelPanel(inv, player, category, page);
        player.openInventory(inv);
    }

    private void fillClassLevelPanel(Inventory inv, Player player, int category, int page) {
        ItemStack glass = glassPane(" ");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, glass);
        }
        if (category >= 0) {
            inv.setItem(0, createClassArrow(category, player));
        } else {
            inv.setItem(0, createCaveLevelSkull(player));
        }
        int baseLevel = page * 25;
        long totalXP = getClassXP(player, category);
        for (int i = 0; i < 25 && (baseLevel + i) <= DungeonLevelXP.MAX_LEVEL; i++) {
            int lvl = baseLevel + i + 1;
            int slot = LEVEL_SLOTS_PAGE1[i];
            inv.setItem(slot, createLevelItem(lvl, totalXP, category));
        }
        if (page == 0) {
            inv.setItem(LEVEL_PAGE_ARROW_SLOT, navArrow("下一頁", true));
        } else {
            inv.setItem(LEVEL_PAGE_ARROW_SLOT, navArrow("上一頁", false));
        }
        inv.setItem(LEVEL_BACK_SLOT, createBackToPanelItem());
        inv.setItem(LEVEL_CLOSE_SLOT, createClosePageItem());
        if (category >= 0) {
            inv.setItem(LEVEL_CLASS_DETAIL_SLOT, createClassDetailSkull(category, player));
        } else {
            inv.setItem(LEVEL_CAVE_GUARANTEE_SLOT, createGuaranteeEntryHead(player));
            inv.setItem(LEVEL_CAVE_COLLECTION_SLOT, createCollectionEntrySign(player));
        }
    }

    /** Slot 45：職業詳情頭顱（寶珠） */
    private ItemStack createClassDetailSkull(int category, Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        if (head.getItemMeta() instanceof SkullMeta meta && category >= 0 && category < CLASS_SKULL_TEXTURES.length) {
            applySkullTextureToMeta(meta, CLASS_SKULL_TEXTURES[category]);
            meta.displayName(Component.text(CLASS_NAMES[category] + "寶珠", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(DungeonClassSkills.buildClassDetailLore(category));
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack createBackToPanelItem() {
        ItemStack item = new ItemStack(Material.ARROW, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) meta.displayName(Component.text("返回副本面板", NamedTextColor.YELLOW));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBackToSkillsItem() {
        ItemStack item = new ItemStack(Material.ARROW, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("返回技能等級", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("點擊返回你的技能等級", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createClosePageItem() {
        ItemStack item = new ItemStack(Material.BARRIER, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) meta.displayName(Component.text("關閉頁面", NamedTextColor.RED));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createLevelItem(int level, long totalXP, int category) {
        long threshold = DungeonLevelXP.getCumulativeXPForLevel(level);
        long prevThreshold = level <= 1 ? 0 : DungeonLevelXP.getCumulativeXPForLevel(level - 1);
        long xpNeededForLevel = threshold - prevThreshold;
        long xpInLevel = totalXP >= threshold ? xpNeededForLevel
            : totalXP >= prevThreshold ? totalXP - prevThreshold : 0;
        int pct = (xpNeededForLevel <= 0) ? 100 : (int) Math.min(100, (xpInLevel * 100 / xpNeededForLevel));

        int currentLevel = DungeonLevelXP.getLevelFromCumulativeXP(totalXP);
        boolean reached = totalXP >= threshold;
        boolean inProgress = !reached && (currentLevel + 1 == level);
        Material mat;
        NamedTextColor nameColor;
        String statusLore;
        if (reached) {
            mat = Material.LIME_STAINED_GLASS_PANE;
            nameColor = NamedTextColor.GREEN;
            statusLore = "已達成";
        } else if (inProgress) {
            mat = Material.YELLOW_STAINED_GLASS_PANE;
            nameColor = NamedTextColor.YELLOW;
            statusLore = "正在解鎖";
        } else {
            mat = Material.RED_STAINED_GLASS_PANE;
            nameColor = NamedTextColor.RED;
            statusLore = "還沒解鎖";
        }
        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String displayPrefix = category < 0 ? "地底洞窟" : (category >= 0 && category < 5 ? CLASS_NAMES[category] : "");
            meta.displayName(Component.text((displayPrefix.isEmpty() ? "" : displayPrefix + " ") + "Lv." + level, nameColor).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("累積需求: " + formatNumber(threshold) + " XP", NamedTextColor.DARK_GRAY));
            if (category == 0) {
                lore.add(Component.empty());
                lore.addAll(DungeonClassSkills.buildHealerLevelRewardsLore(level));
                if (reached || inProgress) {
                    lore.add(Component.empty());
                    lore.add(Component.text("當前進度: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(pct + "%", NamedTextColor.YELLOW)));
                    int barLen = 10;
                    int filled = (int) Math.round(barLen * pct / 100.0);
                    lore.add(DungeonClassSkills.buildStrikethroughProgressBar(barLen, filled, NamedTextColor.YELLOW, NamedTextColor.DARK_GRAY)
                        .append(Component.text(" " + formatNumber(totalXP), NamedTextColor.GRAY)));
                }
            } else if (category == 1) {
                lore.add(Component.empty());
                lore.addAll(DungeonClassSkills.buildMageLevelRewardsLore(level));
                if (reached || inProgress) {
                    lore.add(Component.empty());
                    lore.add(Component.text("當前進度: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(pct + "%", NamedTextColor.YELLOW)));
                    int barLen = 10;
                    int filled = (int) Math.round(barLen * pct / 100.0);
                    lore.add(DungeonClassSkills.buildStrikethroughProgressBar(barLen, filled, NamedTextColor.YELLOW, NamedTextColor.DARK_GRAY)
                        .append(Component.text(" " + formatNumber(totalXP), NamedTextColor.GRAY)));
                }
            } else if (category == 2) {
                lore.add(Component.empty());
                lore.addAll(DungeonClassSkills.buildWarriorLevelRewardsLore(level));
                if (reached || inProgress) {
                    lore.add(Component.empty());
                    lore.add(Component.text("當前進度: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(pct + "%", NamedTextColor.YELLOW)));
                    int barLen = 10;
                    int filled = (int) Math.round(barLen * pct / 100.0);
                    lore.add(DungeonClassSkills.buildStrikethroughProgressBar(barLen, filled, NamedTextColor.YELLOW, NamedTextColor.DARK_GRAY)
                        .append(Component.text(" " + formatNumber(totalXP), NamedTextColor.GRAY)));
                }
            } else if (category == 3) {
                lore.add(Component.empty());
                lore.addAll(DungeonClassSkills.buildArcherLevelRewardsLore(level));
                if (reached || inProgress) {
                    lore.add(Component.empty());
                    lore.add(Component.text("當前進度: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(pct + "%", NamedTextColor.YELLOW)));
                    int barLen = 10;
                    int filled = (int) Math.round(barLen * pct / 100.0);
                    lore.add(DungeonClassSkills.buildStrikethroughProgressBar(barLen, filled, NamedTextColor.YELLOW, NamedTextColor.DARK_GRAY)
                        .append(Component.text(" " + formatNumber(totalXP), NamedTextColor.GRAY)));
                }
            } else if (category == 4) {
                lore.add(Component.empty());
                lore.addAll(DungeonClassSkills.buildTankLevelRewardsLore(level));
                if (reached || inProgress) {
                    lore.add(Component.empty());
                    lore.add(Component.text("當前進度: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(pct + "%", NamedTextColor.YELLOW)));
                    int barLen = 10;
                    int filled = (int) Math.round(barLen * pct / 100.0);
                    lore.add(DungeonClassSkills.buildStrikethroughProgressBar(barLen, filled, NamedTextColor.YELLOW, NamedTextColor.DARK_GRAY)
                        .append(Component.text(" " + formatNumber(totalXP), NamedTextColor.GRAY)));
                }
            } else if (category < 0) {
                lore.add(Component.empty());
                lore.add(Component.text("獎勵:", NamedTextColor.GOLD));
                int mapleCoin = 1000;
                lore.add(Component.text(" + " + formatNumber(mapleCoin) + " 楓葉幣", NamedTextColor.GRAY));
                int prevPercent = level <= 1 ? 10 : DungeonClassSkills.getCaveBonusPercent(level - 1);
                int curPercent = DungeonClassSkills.getCaveBonusPercent(level);
                lore.add(Component.text(" + 來自洞窟的物品提升", NamedTextColor.GRAY)
                    .append(Component.text(prevPercent + "%", NamedTextColor.WHITE))
                    .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(curPercent + "%", NamedTextColor.YELLOW)));
                if (reached || inProgress) {
                    lore.add(Component.empty());
                    lore.add(Component.text("當前進度: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(pct + "%", NamedTextColor.YELLOW)));
                    int barLen = 10;
                    int filled = (int) Math.round(barLen * pct / 100.0);
                    lore.add(DungeonClassSkills.buildStrikethroughProgressBar(barLen, filled, NamedTextColor.YELLOW, NamedTextColor.DARK_GRAY)
                        .append(Component.text(" " + formatNumber(totalXP), NamedTextColor.GRAY)));
                }
            }
            if (!"正在解鎖".equals(statusLore)) {
                lore.add(Component.empty());
            }
            lore.add(Component.text(statusLore, nameColor));
            meta.lore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack navArrow(String text, boolean next) {
        ItemStack item = new ItemStack(Material.ARROW, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) meta.displayName(Component.text(text, NamedTextColor.YELLOW));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack glassPane(String name) {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) meta.displayName(Component.text(name, NamedTextColor.GRAY));
        item.setItemMeta(meta);
        return item;
    }

    private static String formatNumber(long n) {
        if (n >= 1_000_000_000) return String.format(Locale.ROOT, "%.1fB", n / 1e9);
        if (n >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", n / 1e6);
        if (n >= 1_000) return String.format(Locale.ROOT, "%.1fK", n / 1e3);
        return String.valueOf(n);
    }

    /**
     * 取得該玩家在當前世界的某項經驗：category -1 = 地底洞窟，0～4 = 五職業（治療師/法師/戰士/弓箭手/坦克）。
     * 在副本世界：從本地 SQLite（dungeon_rewards.db）讀取；在主世界：直接連 MySQL 讀取。
     * 若兩者皆不可用則地底洞窟用記憶體緩存，職業為 0。
     */
    public long getClassXP(Player player, int category) {
        if (player == null) return category == -1 ? 0L : 0;
        DungeonPanelDataProvider dataProvider = plugin != null ? plugin.getDungeonPanelDataProvider() : null;
        if (dataProvider != null) {
            long[] exp = dataProvider.fetchExperience(player.getUniqueId());
            if (category == -1) return exp[0];
            if (category >= 0 && category <= 4) return exp[category + 1];
            return 0;
        }
        if (category == -1) return caveXPStorage.getOrDefault(player.getUniqueId(), 0L);
        return 0;
    }

    /** 取得玩家地底洞窟累積經驗（用於面板顯示與等級計算）。進地牢後從 SQLite 讀取。 */
    public long getCaveXP(Player player) {
        return getClassXP(player, -1);
    }

    /** 取得玩家地底洞窟經驗（僅 UUID 時用於離線；在線時請用 getCaveXP(Player) 以從 SQLite 讀取）。 */
    public long getCaveXP(UUID playerId) {
        if (playerId == null) return 0;
        Player p = Bukkit.getPlayer(playerId);
        return p != null ? getCaveXP(p) : caveXPStorage.getOrDefault(playerId, 0L);
    }

    /**
     * 增加執行者地底洞窟經驗（用於 /dungeon give xp dungeon &lt;數值&gt;）。
     * 若玩家在地牢世界，會一併寫回 SQLite。
     * @return 增加後的累積地底洞窟 XP
     */
    public long addCaveXP(Player player, long amount) {
        if (amount <= 0) return player != null ? getClassXP(player, -1) : 0;
        if (player == null) return 0;
        UUID playerId = player.getUniqueId();
        long cur = getClassXP(player, -1);
        long next = cur + amount;
        caveXPStorage.put(playerId, next);
        DungeonPanelDataProvider dataProvider = plugin != null ? plugin.getDungeonPanelDataProvider() : null;
        if (dataProvider != null) {
            dataProvider.addExperience(playerId, 0, amount);
        }
        return next;
    }

    /** @deprecated 請使用 {@link #addCaveXP(org.bukkit.entity.Player, long)} 以正確寫回 MySQL */
    public long addCaveXP(UUID playerId, long amount) {
        if (playerId == null || amount <= 0) return getCaveXP(playerId);
        Player p = Bukkit.getPlayer(playerId);
        if (p != null) return addCaveXP(p, amount);
        long cur = caveXPStorage.getOrDefault(playerId, 0L);
        long next = cur + amount;
        caveXPStorage.put(playerId, next);
        DungeonPanelDataProvider dataProvider = plugin != null ? plugin.getDungeonPanelDataProvider() : null;
        if (dataProvider != null) {
            dataProvider.addExperience(playerId, 0, amount);
        }
        return next;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event == null) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getInventory();
        if (top == null || top.getHolder() == null) return;
        InventoryHolder holder = top.getHolder();

        // 僅取消「本插件地牢面板」的點擊；不可在判斷前一律 cancel，否則箱子/熔爐等所有帶 Holder 的容器都無法正常拿放（並可能造成客戶端與服務端不同步、重影/複製感）
        if (holder instanceof GuaranteeFloorDetailHolder) {
            event.setCancelled(true);
            handleGuaranteeFloorDetailClick(player, event.getRawSlot());
            return;
        }
        if (holder instanceof CollectionPanelHolder || holder instanceof GuaranteePanelHolder) {
            event.setCancelled(true);
            handleSubPanelClick(player, event.getRawSlot(), holder instanceof GuaranteePanelHolder);
            return;
        }
        if (!(holder instanceof DungeonPanelHolder dpHolder)) return;

        event.setCancelled(true);

        if (holder instanceof ClassLevelPanelHolder clHolder) {
            handleClassLevelClick(player, event.getRawSlot(), clHolder);
            return;
        }

        if (!DUNGEON_PANEL_ID.equals(dpHolder.panelId)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        for (int i = 0; i < 5; i++) {
            if (slot == MAIN_CLASS_SLOTS[i]) {
                openClassLevelPanel(player, i, 0);
                return;
            }
        }
        if (slot == MAIN_CAVE_LEVEL_SLOT) {
            openClassLevelPanel(player, -1, 0);
            return;
        }
        if (slot == MAIN_BACK_SLOT) {
            CopperSwordMenuGui.open(player);
            return;
        }
        if (slot == MAIN_CLOSE_SLOT) {
            player.closeInventory();
        }
    }

    private void handleSubPanelClick(Player player, int slot, boolean isGuaranteePanel) {
        if (slot == SUB_BACK_SLOT) {
            player.closeInventory();
            openClassLevelPanel(player, -1, 0);
        } else if (slot == SUB_CLOSE_SLOT) {
            player.closeInventory();
        } else if (isGuaranteePanel && (slot == 19 || slot == 20 || slot == 21 || slot == 22)) {
            int idx = slot - 19;
            if (idx >= 0 && idx < GUARANTEE_FLOOR_LABELS.length) {
                player.closeInventory();
                openGuaranteeFloorDetailPanel(player, GUARANTEE_FLOOR_LABELS[idx]);
            }
        }
    }

    private void handleGuaranteeFloorDetailClick(Player player, int slot) {
        if (slot == FLOOR_DETAIL_BACK_SLOT) {
            player.closeInventory();
            openGuaranteePanel(player);
        } else if (slot == FLOOR_DETAIL_CLOSE_SLOT) {
            player.closeInventory();
        }
    }

    private void handleClassLevelClick(Player player, int slot, ClassLevelPanelHolder holder) {
        if (holder.category == -1 && slot == LEVEL_CAVE_GUARANTEE_SLOT) {
            openGuaranteePanel(player);
            return;
        }
        if (holder.category == -1 && slot == LEVEL_CAVE_COLLECTION_SLOT) {
            openCollectionPanel(player);
            return;
        }
        if (slot == LEVEL_PAGE_ARROW_SLOT) {
            if (holder.page == 0) {
                openClassLevelPanel(player, holder.category, 1);
            } else {
                openClassLevelPanel(player, holder.category, 0);
            }
        } else if (slot == LEVEL_BACK_SLOT) {
            player.closeInventory();
            openMainPanel(player);
        } else if (slot == LEVEL_CLOSE_SLOT) {
            player.closeInventory();
        }
    }

    private class DungeonPanelHolder implements InventoryHolder {
        final String panelId;
        final int category;
        final int page;
        private Inventory inventory;

        DungeonPanelHolder(String panelId, int category, int page) {
            this.panelId = panelId;
            this.category = category;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        void setInventory(Inventory inv) {
            this.inventory = inv;
        }
    }

    private final class ClassLevelPanelHolder extends DungeonPanelHolder {
        ClassLevelPanelHolder(int category, int page) {
            super(CLASS_LEVEL_PANEL_ID, category, page);
        }
    }

    private static final class CollectionPanelHolder implements InventoryHolder {
        private Inventory inventory;
        @Override
        public Inventory getInventory() { return inventory; }
        void setInventory(Inventory inv) { this.inventory = inv; }
    }

    private static final class GuaranteePanelHolder implements InventoryHolder {
        private Inventory inventory;
        @Override
        public Inventory getInventory() { return inventory; }
        void setInventory(Inventory inv) { this.inventory = inv; }
    }

    private static final class GuaranteeFloorDetailHolder implements InventoryHolder {
        final String floor;
        private Inventory inventory;

        GuaranteeFloorDetailHolder(String floor) {
            this.floor = floor;
        }

        @Override
        public Inventory getInventory() { return inventory; }
        void setInventory(Inventory inv) { this.inventory = inv; }
    }
}
