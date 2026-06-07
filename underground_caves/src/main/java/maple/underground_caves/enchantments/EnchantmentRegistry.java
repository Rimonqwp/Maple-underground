package maple.underground_caves.enchantments;

import maple.underground_caves.Test_dungeons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 附魔註冊表：儲存所有自定義附魔、從物品 PDC 讀寫等級、生成 lore。
 */
public final class EnchantmentRegistry {

    private final Test_dungeons plugin;
    private final Map<String, CustomEnchantment> byId = new HashMap<>();
    private final List<CustomEnchantment> all = new ArrayList<>();

    public EnchantmentRegistry(Test_dungeons plugin) {
        this.plugin = plugin;
        registerAll();
    }

    private void registerAll() {
        register(new EnchantCombo());
        register(new EnchantDeadwoodRevival());
        register(new EnchantFortuneCoins());
        register(new EnchantInfiniteQuiver());
        register(new EnchantFeatherWing());
        register(new EnchantCaveBlessing());
        register(new EnchantNoPainNoGain());
        register(new EnchantWisdom());
        register(new EnchantThrift());
        register(new EnchantAllIn());
        register(new EnchantLastStand());
        register(new EnchantArmorBreak());
        register(new EnchantKillLegacy());
        register(new EnchantCompanionResonance());
        register(new EnchantComboStorm());
        register(new EnchantLoneFighter());
        register(new EnchantEmberChase());
        register(new EnchantWarAura());
        register(new EnchantDeathPennies());
        register(new EnchantCollectionBonus());
        register(new EnchantSpiderKiller());
        register(new EnchantMercenary());
        register(new EnchantCriticalStrike());
        register(new EnchantLucky());
        register(new EnchantDragonSlayer());
        register(new EnchantEnderSlayer());
        register(new EnchantTwin());
        register(new EnchantSurpriseStrike());
        register(new EnchantChallenger());
        register(new EnchantOverwhelming());
        register(new EnchantLifeSteal());
        register(new EnchantArmorShred());
        register(new EnchantManaSteal());
        register(new EnchantExecute());
        register(new EnchantBountyHunter());
        register(new EnchantBloodCrit());
        register(new EnchantArmorBreakCombo());
        register(new EnchantLoneWolf());
        register(new EnchantThunderChain());
        register(new EnchantThunderGodChain());
        register(new EnchantWildFury());
        register(new EnchantKillHeal());
        register(new EnchantFirstStrike());
    }

    private void register(CustomEnchantment enchant) {
        byId.put(enchant.getId(), enchant);
        all.add(enchant);
    }

    public CustomEnchantment getById(String id) { return byId.get(id); }
    public List<CustomEnchantment> getAll() { return Collections.unmodifiableList(all); }

    public NamespacedKey keyFor(String enchantId) {
        return new NamespacedKey(plugin, "ench_" + enchantId);
    }

    private static final String ENCHANT_BOOK_ID_KEY = "enchant_book_id";
    private static final String ENCHANT_BOOK_LEVEL_KEY = "enchant_book_level";

    public NamespacedKey getEnchantBookIdKey() {
        return new NamespacedKey(plugin, ENCHANT_BOOK_ID_KEY);
    }

    public NamespacedKey getEnchantBookLevelKey() {
        return new NamespacedKey(plugin, ENCHANT_BOOK_LEVEL_KEY);
    }

    private static final String[] TITLE_GRADIENT = { "#FFCEEC", "#FFD5F5", "#FFDBFE" };
    private static final String[] ULTIMATE_GRADIENT = { "#F7A2FF", "#EA9FFF", "#DC9CFF", "#CF99FF", "#C196FF", "#B493FF" };
    private static final String[] NORMAL_GRADIENT = { "#BAFFA2", "#AFFF9E", "#A4FF99", "#99FF95", "#8EFF90", "#83FF8C" };
    private static final TextColor LORE_GRAY = TextColor.fromHexString("#989898");
    private static final TextColor LORE_DESC = TextColor.fromHexString("#D9D9D9");

    /** 建立自定義附魔書，可在鐵砧上與適用裝備合成（繁體、新排版） */
    public ItemStack createEnchantBook(String enchantId, int level) {
        CustomEnchantment e = getById(enchantId);
        if (e == null || level < 1) return new ItemStack(Material.AIR);
        level = Math.min(level, e.getMaxLevel());
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta == null) return book;
        meta.displayName(gradientComponent(TITLE_GRADIENT, "附魔書"));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("此物品可以在鐵砧上使用", LORE_GRAY));
        lore.add(Component.empty());
        String nameWithLevel = e.getDisplayName() + " " + EnchantmentLore.levelToRoman(level);
        lore.add(gradientComponent(e.getEnchantType() == CustomEnchantment.EnchantType.ULTIMATE ? ULTIMATE_GRADIENT : NORMAL_GRADIENT, nameWithLevel));
        lore.add(Component.text(e.getDescription(level), LORE_DESC));
        lore.add(Component.empty());
        lore.add(Component.text("適用裝備: " + getApplicableEquipmentLabel(e), LORE_GRAY));
        lore.add(Component.text("所需經驗: 1 等級", LORE_GRAY));
        lore.add(Component.empty());
        lore.add(e.getEnchantType() == CustomEnchantment.EnchantType.ULTIMATE
            ? gradientComponent(ULTIMATE_GRADIENT, "[終極附魔]")
            : gradientComponent(NORMAL_GRADIENT, "[普通附魔]"));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(getEnchantBookIdKey(), PersistentDataType.STRING, enchantId);
        meta.getPersistentDataContainer().set(getEnchantBookLevelKey(), PersistentDataType.INTEGER, level);
        book.setItemMeta(meta);
        return book;
    }

    private static Component gradientComponent(String[] hexColors, String text) {
        if (text.isEmpty()) return Component.empty();
        Component out = Component.empty();
        for (int i = 0; i < text.length(); i++) {
            TextColor c = TextColor.fromHexString(hexColors[i % hexColors.length]);
            out = out.append(Component.text(String.valueOf(text.charAt(i)), c));
        }
        return out;
    }

    private static String getApplicableEquipmentLabel(CustomEnchantment e) {
        if ("collection_bonus".equals(e.getId())) return "飾品";
        var set = e.getApplicableMaterials();
        if (set.equals(CustomEnchantment.SWORDS)) return "劍";
        if (set.equals(CustomEnchantment.BOWS)) return "弓";
        if (set.equals(CustomEnchantment.BOOTS)) return "鞋子";
        if (set.equals(CustomEnchantment.SWORD_AND_BOW)) return "劍/弓";
        if (set.equals(CustomEnchantment.SWORD_AND_PICKAXE)) return "劍/鎬";
        if (set.equals(CustomEnchantment.WEAPONS)) return "武器";
        if (set.equals(CustomEnchantment.ARMOR)) return "裝備";
        if (set.size() == 1) return set.iterator().next().name();
        return "裝備";
    }

    public boolean isCustomEnchantBook(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(getEnchantBookIdKey(), PersistentDataType.STRING);
    }

    public String getEnchantBookId(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(getEnchantBookIdKey(), PersistentDataType.STRING);
    }

    public int getEnchantBookLevel(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        Integer v = meta.getPersistentDataContainer().get(getEnchantBookLevelKey(), PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    public int getLevel(ItemStack item, String enchantId) {
        if (item == null || item.getType() == Material.AIR) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        Integer v = meta.getPersistentDataContainer().get(keyFor(enchantId), PersistentDataType.INTEGER);
        return v == null ? 0 : Math.max(0, v);
    }

    public void setLevel(ItemStack item, String enchantId, int level) {
        if (item == null || item.getType() == Material.AIR) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (level <= 0) {
            pdc.remove(keyFor(enchantId));
        } else {
            CustomEnchantment e = getById(enchantId);
            int capped = e == null ? level : Math.min(level, e.getMaxLevel());
            pdc.set(keyFor(enchantId), PersistentDataType.INTEGER, capped);
        }
        item.setItemMeta(meta);
    }

    /** 取得物品上所有自定義附魔與等級 */
    public Map<CustomEnchantment, Integer> getEnchants(ItemStack item) {
        Map<CustomEnchantment, Integer> out = new HashMap<>();
        if (item == null || item.getType() == Material.AIR) return out;
        for (CustomEnchantment e : all) {
            int lvl = getLevel(item, e.getId());
            if (lvl > 0) out.put(e, lvl);
        }
        return out;
    }

    /** 為物品加上附魔並更新 lore（普通附魔灰色、終極附魔粉色） */
    public void addEnchant(ItemStack item, String enchantId, int level) {
        CustomEnchantment e = getById(enchantId);
        if (e == null || level <= 0) return;
        if (!e.appliesTo(item)) return;
        int capped = Math.min(level, e.getMaxLevel());
        setLevel(item, enchantId, capped);
        updateEnchantLore(item);
    }

    /** 依目前 PDC 附魔重新生成並寫入物品 lore（附魔名稱與描述，終極附魔粉色、普通附魔灰色） */
    public void updateEnchantLore(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        List<Component> existing = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        List<Component> keep = new ArrayList<>();
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        for (Component c : existing) {
            String text = plain.serialize(c);
            boolean isOurEnchant = false;
            for (CustomEnchantment e : all) {
                if (text.startsWith(e.getDisplayName() + " ") || (text.startsWith("  ") && text.contains(e.getDisplayName()))) {
                    isOurEnchant = true;
                    break;
                }
            }
            if (!isOurEnchant) keep.add(c);
        }
        List<Component> enchantLore = EnchantmentLore.buildLore(getEnchants(item));
        keep.addAll(enchantLore);
        meta.lore(keep);
        item.setItemMeta(meta);
    }
}
