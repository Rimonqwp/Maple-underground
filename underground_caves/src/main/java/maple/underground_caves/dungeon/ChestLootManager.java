package maple.underground_caves.dungeon;

import maple.underground_caves.Test_dungeons;
import maple.underground_caves.gear.BloodSetManager;
import maple.underground_caves.gear.CavernExplorerSetManager;
import maple.underground_caves.gear.AdaptiveSetManager;
import maple.underground_caves.gear.QualityUpgradeItemManager;
import maple.underground_state.bank.CmiEconomyBridge;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Last boss 房六個寶箱戰利品：依材質名稱（bronze, silver, gold, platinum, red, orange）與樓層 F1/F2/F3 設定。
 * 開箱計費 = base_price + 隨機選中格的 price 總和；選中的格會發放對應物品。
 */
public final class ChestLootManager {

    private static final String CONFIG_NAME = "chest_loot.yml";
    private static final String CONFIG_SPLIT_DIR = "chest_loot";
    /** 新制箱子 ID（直接等於材質名稱） */
    private static final java.util.Set<String> TREASURE_NAMES = java.util.Set.of(
            "bronze", "silver", "gold", "platinum", "red", "orange"
    );
    /** 舊資料別名 -> 新制 ID（相容舊盔甲架 PDC） */
    private static final Map<String, String> LEGACY_CHEST_ID_ALIASES = Map.of(
            "wood", "bronze",
            "gold", "silver",
            "diamond_left", "gold",
            "diamond_right", "platinum",
            "emerald_left", "red",
            "emerald_right", "orange"
    );

    private final Test_dungeons plugin;
    private final Random random = new Random();
    private final CmiEconomyBridge economy;
    private volatile QualityUpgradeItemManager qualityUpgradeItemManager;
    private volatile Map<String, Map<String, FloorLootConfig>> configByChest = new LinkedHashMap<>();
    private volatile Map<String, String> displayNameByChest = new LinkedHashMap<>();

    public ChestLootManager(Test_dungeons plugin) {
        this.plugin = plugin;
        this.economy = new CmiEconomyBridge(plugin.getLogger());
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), CONFIG_NAME);
        if (!file.exists()) {
            try {
                plugin.saveResource(CONFIG_NAME, false);
            } catch (Throwable ignored) {}
        }
        Map<String, Map<String, FloorLootConfig>> next = new LinkedHashMap<>();
        Map<String, String> displayNames = new LinkedHashMap<>();

        // 先讀舊版單檔（作為基底）
        if (file.exists()) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            loadFromConfig(cfg, next, displayNames, null);
        }

        // 再讀分檔（按樓層覆蓋）
        File splitDir = new File(plugin.getDataFolder(), CONFIG_SPLIT_DIR);
        if (splitDir.exists() && splitDir.isDirectory()) {
            File f1 = new File(splitDir, "F1.yml");
            File f2 = new File(splitDir, "F2.yml");
            File f3 = new File(splitDir, "F3.yml");
            if (f1.exists()) loadFromConfig(YamlConfiguration.loadConfiguration(f1), next, displayNames, "F1");
            if (f2.exists()) loadFromConfig(YamlConfiguration.loadConfiguration(f2), next, displayNames, "F2");
            if (f3.exists()) loadFromConfig(YamlConfiguration.loadConfiguration(f3), next, displayNames, "F3");
        }
        configByChest = next;
        displayNameByChest = displayNames;
    }

    /** 依 chest_id 取得材質名稱（新制直接回傳；舊制別名自動轉新制）。 */
    public static String getTreasureNameByChestId(String chestId) {
        if (chestId == null) return null;
        String normalized = normalizeChestId(chestId);
        return TREASURE_NAMES.contains(normalized) ? normalized : null;
    }

    /** 將舊制箱子 id 正規化為新制（bronze/silver/gold/platinum/red/orange）。 */
    public static String normalizeChestId(String chestId) {
        if (chestId == null) return null;
        String id = chestId.toLowerCase(Locale.ROOT);
        if (TREASURE_NAMES.contains(id)) return id;
        return LEGACY_CHEST_ID_ALIASES.getOrDefault(id, id);
    }

    /** 依材質名稱取得該箱的顯示名（配置中的 display_name）。 */
    public String getDisplayName(String treasureName) {
        if (treasureName == null) return null;
        String normalized = normalizeChestId(treasureName);
        String name = displayNameByChest.get(normalized);
        if (name != null && !name.isBlank()) return name;
        return treasureName;
    }

    /** 取得某箱某樓層的戰利品配置，無則回傳 null。 */
    public FloorLootConfig getFloorConfig(String treasureName, String floor) {
        if (treasureName == null || floor == null) return null;
        Map<String, FloorLootConfig> byFloor = configByChest.get(treasureName.toLowerCase(Locale.ROOT));
        if (byFloor == null) return null;
        return byFloor.get(floor.toUpperCase(Locale.ROOT));
    }

    /** 依每格 chance 機率隨機選中若干格；若全未中則強制中一格。 */
    public List<String> rollSelectedSlots(FloorLootConfig floorConfig) {
        if (floorConfig == null || floorConfig.slots.isEmpty()) return List.of();
        List<String> ids = new ArrayList<>(floorConfig.slots.keySet());
        if (ids.isEmpty()) return List.of();
        List<String> selected = new ArrayList<>();
        for (String id : ids) {
            SlotEntry slot = floorConfig.slots.get(id);
            if (slot != null && slot.chance() > 0 && (slot.chance() >= 1.0 || random.nextDouble() < slot.chance())) {
                selected.add(id);
            }
        }
        if (selected.isEmpty()) {
            selected.add(ids.get(random.nextInt(ids.size())));
        }
        return selected;
    }

    /** 解析 chance：未填或無效為 1.0；>1 視為百分比（如 50→0.5），<=1 視為小數。 */
    private static double parseChance(Object val) {
        if (val == null) return 1.0;
        if (val instanceof Number n) {
            double v = n.doubleValue();
            if (v <= 0) return 0;
            // yml 约定：
            // - 0~1 为小数概率（0.22=22%）
            // - 1~100 为百分比（22=22%）
            // 因此：v < 1 视为小数；v >= 1 视为百分比整数。
            return v < 1 ? v : Math.min(1.0, v / 100.0);
        }
        if (val instanceof String s) {
            try {
                double v = Double.parseDouble(s.trim());
                if (v <= 0) return 0;
                return v < 1 ? v : Math.min(1.0, v / 100.0);
            } catch (NumberFormatException ignored) {}
        }
        return 1.0;
    }

    /** 計算總價：base_price + 選中格的 price 總和。 */
    public long computeTotalCost(FloorLootConfig floorConfig, List<String> selectedSlotIds) {
        if (floorConfig == null) return 0;
        long sum = floorConfig.basePrice;
        for (String id : selectedSlotIds) {
            SlotEntry slot = floorConfig.slots.get(id);
            if (slot != null) sum += slot.price;
        }
        return sum;
    }

    /** 依選中的 slot id 產生獎勵物品列表。 */
    public List<ItemStack> getRewardsForSelectedSlots(FloorLootConfig floorConfig, List<String> selectedSlotIds) {
        if (floorConfig == null || selectedSlotIds == null) return List.of();
        List<ItemStack> out = new ArrayList<>();
        for (String id : selectedSlotIds) {
            SlotEntry slot = floorConfig.slots.get(id);
            if (slot == null || slot.itemStr.isEmpty()) continue;
            ItemStack item = resolveItem(slot.itemStr);
            if (item != null && !item.getType().isAir()) out.add(item);
        }
        return out;
    }

    /** Bronze GUI：固定槽位 + 概率池槽位一次抽取。 */
    public Map<Integer, ItemStack> rollGuiItems(FloorLootConfig floorConfig) {
        Map<Integer, ItemStack> out = new LinkedHashMap<>();
        Map<Integer, SlotEntry> entries = rollGuiEntries(floorConfig);
        Map<String, Set<String>> usedSetSlotsBySetId = new LinkedHashMap<>();
        for (Map.Entry<Integer, SlotEntry> e : entries.entrySet()) {
            ItemStack item = resolveItem(e.getValue().itemStr, usedSetSlotsBySetId);
            if (item != null && !item.getType().isAir()) out.put(e.getKey(), item);
        }
        return out;
    }

    /** GUI 抽選後的原始條目（可用於計費/稀有判定）。 */
    public Map<Integer, SlotEntry> rollGuiEntries(FloorLootConfig floorConfig) {
        Map<Integer, SlotEntry> out = new LinkedHashMap<>();
        if (floorConfig == null) return out;
        if (floorConfig.guiFixed != null) {
            for (Map.Entry<Integer, SlotEntry> e : floorConfig.guiFixed.entrySet()) {
                out.put(e.getKey(), e.getValue());
            }
        }
        if (floorConfig.guiPool != null) {
            // 同一轮 GUI pool 去重：
            // - 非装备类（非 *_set）同 item 只允许出现一次
            // - 装备类（*_set）允许重复，部位去重在 resolveItem(...) 阶段处理
            Set<String> pickedNonEquipIds = new HashSet<>();
            List<Integer> sortedSlots = new ArrayList<>(floorConfig.guiPool.keySet());
            sortedSlots.sort(Comparator.naturalOrder());
            for (Integer guiSlot : sortedSlots) {
                List<SlotEntry> options = floorConfig.guiPool.get(guiSlot);
                List<SlotEntry> filtered = new ArrayList<>();
                if (options != null) {
                    for (SlotEntry option : options) {
                        String itemId = firstTokenLower(option.itemStr());
                        if (!isSetToken(itemId) && pickedNonEquipIds.contains(itemId)) {
                            continue;
                        }
                        filtered.add(option);
                    }
                }
                SlotEntry picked = weightedPick(filtered.isEmpty() ? options : filtered);
                if (picked != null) out.put(guiSlot, picked);
                if (picked != null) {
                    String itemId = firstTokenLower(picked.itemStr());
                    if (!isSetToken(itemId)) pickedNonEquipIds.add(itemId);
                }
            }
        }
        return out;
    }

    private SlotEntry weightedPick(List<SlotEntry> list) {
        if (list == null || list.isEmpty()) return null;
        double total = 0;
        for (SlotEntry e : list) total += Math.max(0, e.chance());
        if (total <= 0) return list.get(random.nextInt(list.size()));
        double r = random.nextDouble() * total;
        double acc = 0;
        for (SlotEntry e : list) {
            acc += Math.max(0, e.chance());
            if (r <= acc) return e;
        }
        return list.get(list.size() - 1);
    }

    private static ItemStack parseItem(String str) {
        if (str == null || str.isBlank()) return null;
        String[] parts = str.trim().split("\\s+");
        if (parts.length < 1) return null;
        String id = parts[0];
        // custom gear id
        if (id.startsWith("cave_echo_")) {
            // resolved in non-static wrapper
            return null;
        }
        Material mat;
        try {
            mat = Material.valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        int amount = 1;
        if (parts.length >= 2) {
            amount = parseAmount(parts[1], ThreadLocalRandom.current());
        }
        return new ItemStack(mat, amount);
    }

    private static int parseAmount(String raw, Random rand) {
        if (raw == null || raw.isBlank()) return 1;
        String v = raw.trim();
        if (v.contains("-")) {
            String[] p = v.split("-", 2);
            try {
                int min = Integer.parseInt(p[0].trim());
                int max = Integer.parseInt(p[1].trim());
                if (max < min) { int t = min; min = max; max = t; }
                min = Math.max(1, min);
                max = Math.max(min, max);
                return min + rand.nextInt(max - min + 1);
            } catch (NumberFormatException ignored) {
                return 1;
            }
        }
        try { return Math.max(1, Integer.parseInt(v)); } catch (NumberFormatException ignored) { return 1; }
    }

    /** 支援 MATERIAL、weaponId、cave_echo_*。 */
    public ItemStack resolveItem(String tokenWithAmount) {
        return resolveItem(tokenWithAmount, new LinkedHashMap<>());
    }

    /** 支援 MATERIAL、weaponId、cave_echo_*，並可在同一輪抽獎中追蹤套裝部位避免重複。 */
    public ItemStack resolveItem(String tokenWithAmount, Map<String, Set<String>> usedSetSlotsBySetId) {
        if (tokenWithAmount == null || tokenWithAmount.isBlank()) return null;
        String[] parts = tokenWithAmount.trim().split("\\s+");
        if (parts.length < 1) return null;
        String id = parts[0];
        int amount = parts.length >= 2 ? parseAmount(parts[1], random) : 1;

        // enchant id -> custom enchant book (always level 1)
        var enchReg = plugin.getEnchantmentRegistry();
        if (enchReg != null && enchReg.getById(id) != null) {
            ItemStack book = enchReg.createEnchantBook(id, 1);
            if (book != null && !book.getType().isAir()) {
                book.setAmount(Math.max(1, amount));
                return book;
            }
        }

        // 洞窟特殊头盔（mending/ability/cave_echo_* 等）
        var cave = plugin.getCaveSpecialHelmetManager();
        if (cave != null) {
            ItemStack it = cave.createHelmet(id);
            if (it != null) {
                it.setAmount(Math.max(1, amount));
                return it;
            }
        }

        if ("immortal_skull".equalsIgnoreCase(id) && plugin.getImmortalSkullManager() != null) {
            ItemStack skull = plugin.getImmortalSkullManager().createImmortalSkull();
            if (skull != null) skull.setAmount(Math.max(1, amount));
            return skull;
        }

        // 品质升级道具
        if ("quality_upgrade".equalsIgnoreCase(id)) {
            var mgr = qualityUpgradeItemManager;
            if (mgr == null) {
                synchronized (this) {
                    if (qualityUpgradeItemManager == null) {
                        qualityUpgradeItemManager = new QualityUpgradeItemManager(plugin);
                    }
                    mgr = qualityUpgradeItemManager;
                }
            }
            if (mgr != null) {
                ItemStack it = mgr.createItem();
                if (it != null) {
                    it.setAmount(Math.max(1, amount));
                    return it;
                }
            }
        }

        // 套裝（默认掉落某一部位）
        String idLower = id.toLowerCase(Locale.ROOT);
        if ("cavern_explorer_set".equals(idLower) && plugin.getCavernExplorerSetManager() != null) {
            CavernExplorerSetManager m = plugin.getCavernExplorerSetManager();
            String[] slots = CavernExplorerSetManager.getSlots();
            if (slots != null && slots.length > 0) {
                String slot = pickSetSlot(idLower, slots, usedSetSlotsBySetId);
                ItemStack it = m.createPiece(slot);
                if (it != null) {
                    it.setAmount(Math.max(1, amount));
                    return it;
                }
            }
        }
        if ("adaptive_set".equals(idLower) && plugin.getAdaptiveSetManager() != null) {
            AdaptiveSetManager m = plugin.getAdaptiveSetManager();
            String[] slots = AdaptiveSetManager.getSlots();
            if (slots != null && slots.length > 0) {
                String slot = pickSetSlot(idLower, slots, usedSetSlotsBySetId);
                ItemStack it = m.createPiece(slot);
                if (it != null) {
                    it.setAmount(Math.max(1, amount));
                    return it;
                }
            }
        }
        if (idLower.startsWith("blood_") && idLower.endsWith("_set") && plugin.getBloodSetManager() != null) {
            BloodSetManager m = plugin.getBloodSetManager();
            // 仅匹配配置里已存在的 setId
            if (java.util.Arrays.asList(BloodSetManager.getSetIds()).contains(idLower)) {
                String[] slots = BloodSetManager.getSlots();
                if (slots != null && slots.length > 0) {
                    String slot = pickSetSlot(idLower, slots, usedSetSlotsBySetId);
                    ItemStack it = m.createPiece(idLower, slot);
                    if (it != null) {
                        it.setAmount(Math.max(1, amount));
                        return it;
                    }
                }
            }
        }

        var weapons = plugin.getWeaponManager();
        if (weapons != null) {
            ItemStack w = weapons.createWeaponItem(id);
            if (w != null) {
                w.setAmount(Math.max(1, amount));
                return w;
            }
        }

        Material mat;
        try { mat = Material.valueOf(id.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
        return new ItemStack(mat, Math.max(1, amount));
    }

    /** 嘗試從玩家錢包扣款。回傳 true 表示扣款成功，可發獎。 */
    public boolean tryCharge(Player player, long amount) {
        if (player == null || amount <= 0) return true;
        OptionalDouble balance = economy.balance(player);
        if (balance.isEmpty()) {
            plugin.getLogger().warning("[ChestLoot] Economy unavailable, cannot charge " + player.getName() + " " + amount + " for chest open.");
            return false;
        }
        long walletBalance = Math.max(0L, (long) Math.floor(balance.getAsDouble()));
        if (walletBalance < amount) {
            plugin.getLogger().info("[ChestLoot] Insufficient balance for " + player.getName()
                    + ": need=" + amount + " balance=" + walletBalance);
            return false;
        }
        boolean withdrawn = economy.withdraw(player, amount);
        if (!withdrawn) {
            plugin.getLogger().warning("[ChestLoot] Failed to withdraw " + amount + " from " + player.getName() + " for chest open.");
            return false;
        }
        plugin.getLogger().info("[ChestLoot] Charged " + player.getName() + " " + amount + " for chest open.");
        return true;
    }

    /** 發放獎勵到玩家背包，滿則掉落腳下。 */
    public void giveRewards(Player player, List<ItemStack> items) {
        if (player == null || items == null) return;
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
            for (ItemStack drop : overflow.values()) {
                if (drop != null && !drop.getType().isAir()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
        }
    }

    public record FloorLootConfig(long basePrice,
                                  Map<String, SlotEntry> slots,
                                  Map<Integer, SlotEntry> guiFixed,
                                  Map<Integer, List<SlotEntry>> guiPool) {}
    public record SlotEntry(long price, long extraPrice, String itemStr, double chance) {
        public SlotEntry(long price, String itemStr) { this(price, 0L, itemStr, 1.0); }
    }

    private static String firstTokenLower(String tokenWithAmount) {
        if (tokenWithAmount == null || tokenWithAmount.isBlank()) return "";
        String[] p = tokenWithAmount.trim().split("\\s+");
        if (p.length == 0) return "";
        return p[0].toLowerCase(Locale.ROOT);
    }

    private static boolean isSetToken(String itemIdLower) {
        return itemIdLower != null && itemIdLower.endsWith("_set");
    }

    private String pickSetSlot(String setIdLower, String[] slots, Map<String, Set<String>> usedSetSlotsBySetId) {
        if (slots == null || slots.length == 0) return "helmet";
        if (usedSetSlotsBySetId == null) {
            return slots[random.nextInt(slots.length)];
        }
        Set<String> used = usedSetSlotsBySetId.computeIfAbsent(setIdLower, k -> new HashSet<>());
        List<String> available = new ArrayList<>();
        for (String s : slots) {
            if (!used.contains(s)) available.add(s);
        }
        String picked = available.isEmpty()
                ? slots[random.nextInt(slots.length)]
                : available.get(random.nextInt(available.size()));
        used.add(picked);
        return picked;
    }

    private void loadFromConfig(YamlConfiguration cfg,
                                Map<String, Map<String, FloorLootConfig>> out,
                                Map<String, String> displayNamesOut,
                                String onlyFloorUpper) {
        if (cfg == null) return;
        ConfigurationSection chests = cfg.getConfigurationSection("chests");
        if (chests == null) return;

        for (String treasureName : chests.getKeys(false)) {
            ConfigurationSection chest = chests.getConfigurationSection(treasureName);
            if (chest == null) continue;
            String chestKey = treasureName.toLowerCase(Locale.ROOT);
            Map<String, FloorLootConfig> byFloor = out.computeIfAbsent(chestKey, k -> new LinkedHashMap<>());
            String displayName = chest.getString("display_name");
            if (displayName != null && !displayName.isBlank()) {
                displayNamesOut.put(chestKey, displayName);
            }
            long basePrice = chest.getLong("base_price", 1000);
            for (String floor : new String[] { "F1", "F2", "F3" }) {
                if (onlyFloorUpper != null && !onlyFloorUpper.equalsIgnoreCase(floor)) continue;
                ConfigurationSection floorSec = chest.getConfigurationSection(floor);
                if (floorSec == null) continue;
                long floorBasePrice = floorSec.getLong("base_price", basePrice);
                Map<String, SlotEntry> slots = new LinkedHashMap<>();
                ConfigurationSection slotsSec = floorSec.getConfigurationSection("slots");
                if (slotsSec != null) {
                    for (String slotId : slotsSec.getKeys(false)) {
                        ConfigurationSection slot = slotsSec.getConfigurationSection(slotId);
                        if (slot == null) continue;
                        long price = slot.getLong("price", 0);
                        String item = slot.getString("item", "");
                        double chance = parseChance(slot.get("chance"));
                        slots.put(slotId.toUpperCase(Locale.ROOT), new SlotEntry(price, 0L, item != null ? item : "", chance));
                    }
                }
                Map<Integer, SlotEntry> guiFixed = new LinkedHashMap<>();
                Map<Integer, List<SlotEntry>> guiPool = new LinkedHashMap<>();
                ConfigurationSection guiSec = floorSec.getConfigurationSection("gui");
                if (guiSec != null) {
                    ConfigurationSection fixedSec = guiSec.getConfigurationSection("fixed");
                    if (fixedSec != null) {
                        for (String slotStr : fixedSec.getKeys(false)) {
                            ConfigurationSection slot = fixedSec.getConfigurationSection(slotStr);
                            if (slot == null) continue;
                            try {
                                int guiSlot = Integer.parseInt(slotStr);
                                String item = slot.getString("item", "");
                                String amount = slot.getString("amount", "1");
                                long price = slot.getLong("price", 0);
                                long extraPrice = slot.getLong("extra_price", 0L);
                                guiFixed.put(guiSlot, new SlotEntry(price, extraPrice, item + " " + amount, 1.0));
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                    ConfigurationSection poolSec = guiSec.getConfigurationSection("pool");
                    if (poolSec != null) {
                        for (String slotStr : poolSec.getKeys(false)) {
                            try {
                                int guiSlot = Integer.parseInt(slotStr);
                                List<?> list = poolSec.getList(slotStr, Collections.emptyList());
                                List<SlotEntry> options = new ArrayList<>();
                                for (Object o : list) {
                                    if (!(o instanceof Map<?, ?> m)) continue;
                                    Object idObj = m.get("item");
                                    if (idObj == null) continue;
                                    String item = String.valueOf(idObj);
                                    Object amountObj = m.get("amount");
                                    String amount = amountObj == null ? "1" : String.valueOf(amountObj);
                                    Object chanceObj = m.get("chance");
                                    double chance = parseChance(chanceObj);
                                    long price = 0L;
                                    Object priceObj = m.get("price");
                                    if (priceObj instanceof Number n) price = n.longValue();
                                    long extraPrice = 0L;
                                    Object extraObj = m.get("extra_price");
                                    if (extraObj instanceof Number n) extraPrice = n.longValue();
                                    options.add(new SlotEntry(price, extraPrice, item + " " + amount, chance));
                                }
                                if (!options.isEmpty()) guiPool.put(guiSlot, options);
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
                byFloor.put(floor.toUpperCase(Locale.ROOT), new FloorLootConfig(floorBasePrice, slots, guiFixed, guiPool));
            }
        }
    }
}
