package maple.underground_caves.gear;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 傳說套裝（8 套）：載入與創建物品，穿齊四件時提供屬性與套裝效果 */
public final class BloodSetManager {

    private static final String PDC_BLOOD_SET_ID = "blood_set_id";
    private static final String PDC_BLOOD_SLOT = "blood_slot";
    private static final String[] SET_IDS = {
            "blood_holy_set", "blood_protective_set", "blood_wise_set", "blood_unstable_set",
            "blood_young_set", "blood_old_set", "blood_strong_set", "blood_superior_set"
    };
    private static final String[] SLOTS = { "helmet", "chest", "leggings", "boots" };

    private final JavaPlugin plugin;
    private final NamespacedKey keySetId;
    private final NamespacedKey keySlot;
    private final Map<String, BloodSetConfig> configs = new HashMap<>();

    public BloodSetManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.keySetId = new NamespacedKey(plugin, PDC_BLOOD_SET_ID);
        this.keySlot = new NamespacedKey(plugin, PDC_BLOOD_SLOT);
        reload();
    }

    public void reload() {
        configs.clear();
        for (String setId : SET_IDS) {
            File f = new File(plugin.getDataFolder(), "gear/" + setId + ".yml");
            if (!f.exists()) {
                plugin.saveResource("gear/" + setId + ".yml", false);
                f = new File(plugin.getDataFolder(), "gear/" + setId + ".yml");
            }
            configs.put(setId, new BloodSetConfig(setId, org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f)));
        }
    }

    public BloodSetConfig getConfig(String setId) { return configs.get(setId); }

    public boolean isBloodPiece(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        String id = item.getItemMeta().getPersistentDataContainer().get(keySetId, PersistentDataType.STRING);
        return id != null && configs.containsKey(id);
    }

    public String getSlot(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(keySlot, PersistentDataType.STRING);
    }

    public String getSetId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(keySetId, PersistentDataType.STRING);
    }

    /** 若穿齊同一套傳說四件，回傳 set_id，否則 null */
    public String getWornFullSetId(Player player) {
        if (player == null) return null;
        PlayerInventory inv = player.getInventory();
        String id = getSetId(inv.getHelmet());
        if (id == null || !configs.containsKey(id)) return null;
        if (!id.equals(getSetId(inv.getChestplate()))) return null;
        if (!id.equals(getSetId(inv.getLeggings()))) return null;
        if (!id.equals(getSetId(inv.getBoots()))) return null;
        return id;
    }

    public int getBonusHealth(Player player) {
        String id = getWornFullSetId(player);
        if (id == null) return 0;
        BloodSetConfig c = configs.get(id);
        if (c == null) return 0;
        return c.getHealth("helmet") + c.getHealth("chest") + c.getHealth("leggings") + c.getHealth("boots");
    }

    public int getBonusDefense(Player player) {
        String id = getWornFullSetId(player);
        if (id == null) return 0;
        BloodSetConfig c = configs.get(id);
        if (c == null) return 0;
        int base = c.getDefense("helmet") + c.getDefense("chest") + c.getDefense("leggings") + c.getDefense("boots");
        if ("blood_protective_set".equals(id) && c.getSetBonusDefensePerMissingHpPercent() > 0) {
            double maxH = (plugin instanceof maple.underground_caves.Test_dungeons td) ? td.getEffectiveMaxHealth(player) : 20;
            double cur = (plugin instanceof maple.underground_caves.Test_dungeons td && td.getPlayerStatsManager() != null)
                    ? td.getPlayerStatsManager().get(player).getCurrentHealth() : player.getHealth();
            if (maxH > 0) {
                double missingPercent = 100 - (cur / maxH * 100);
                base += (int) (base * (missingPercent / 100.0) * (c.getSetBonusDefensePerMissingHpPercent() / 100.0));
            }
        }
        return base;
    }

    public int getBonusStrength(Player player) {
        String id = getWornFullSetId(player);
        if (id == null) return 0;
        BloodSetConfig c = configs.get(id);
        if (c == null) return 0;
        return c.getStrength("helmet") + c.getStrength("chest") + c.getStrength("leggings") + c.getStrength("boots");
    }

    public int getBonusIntelligence(Player player) {
        String id = getWornFullSetId(player);
        if (id == null) return 0;
        BloodSetConfig c = configs.get(id);
        if (c == null) return 0;
        return c.getIntelligence("helmet") + c.getIntelligence("chest") + c.getIntelligence("leggings") + c.getIntelligence("boots");
    }

    public int getBonusSpeed(Player player) {
        String id = getWornFullSetId(player);
        if (id == null) return 0;
        BloodSetConfig c = configs.get(id);
        if (c == null) return 0;
        int fromPieces = c.getSpeed("helmet") + c.getSpeed("chest") + c.getSpeed("leggings") + c.getSpeed("boots");
        if ("blood_young_set".equals(id) && c.getSetBonusSpeedBonus() > 0) {
            double maxH = (plugin instanceof maple.underground_caves.Test_dungeons td) ? td.getEffectiveMaxHealth(player) : 20;
            double cur = (plugin instanceof maple.underground_caves.Test_dungeons td && td.getPlayerStatsManager() != null)
                    ? td.getPlayerStatsManager().get(player).getCurrentHealth() : player.getHealth();
            if (maxH > 0 && cur / maxH * 100 >= c.getSetBonusHealthThresholdPercent())
                fromPieces += c.getSetBonusSpeedBonus();
        }
        return fromPieces;
    }

    public int getBonusCritChance(Player player) {
        String id = getWornFullSetId(player);
        if (id == null) return 0;
        BloodSetConfig c = configs.get(id);
        if (c == null) return 0;
        return c.getCritChance("helmet") + c.getCritChance("chest") + c.getCritChance("leggings") + c.getCritChance("boots");
    }

    public int getBonusCritDamage(Player player) {
        String id = getWornFullSetId(player);
        if (id == null) return 0;
        BloodSetConfig c = configs.get(id);
        if (c == null) return 0;
        return c.getCritDamage("helmet") + c.getCritDamage("chest") + c.getCritDamage("leggings") + c.getCritDamage("boots");
    }

    /** 祝福套裝：範圍內自然恢復倍率（穿齊 blood_holy_set 時回傳 regen_multiplier，否則 1） */
    public double getHolyRegenMultiplier(Player player) {
        if (!"blood_holy_set".equals(getWornFullSetId(player))) return 1.0;
        BloodSetConfig c = configs.get("blood_holy_set");
        return c != null ? c.getSetBonusRegenMultiplier() : 1.0;
    }

    /** 祝福套裝：半徑格數 */
    public int getHolyRadiusBlocks(Player player) {
        if (!"blood_holy_set".equals(getWornFullSetId(player))) return 0;
        BloodSetConfig c = configs.get("blood_holy_set");
        return c != null ? c.getSetBonusRadiusBlocks() : 0;
    }

    /** 睿智套裝：魔力消耗比例（2/3 → 約 0.667） */
    public double getWiseManaCostRatio(Player player) {
        if (!"blood_wise_set".equals(getWornFullSetId(player))) return 1.0;
        BloodSetConfig c = configs.get("blood_wise_set");
        return c != null ? c.getSetBonusManaCostRatio() : 1.0;
    }

    /** 至尊套裝：戰鬥屬性加成百分比（如 5 表示 1.05 倍） */
    public int getSuperiorCombatStatsPercent(Player player) {
        if (!"blood_superior_set".equals(getWornFullSetId(player))) return 0;
        BloodSetConfig c = configs.get("blood_superior_set");
        return c != null ? c.getSetBonusCombatStatsPercent() : 0;
    }

    private static Material slotToMaterial(String slot) {
        return switch (slot == null ? "" : slot) {
            case "helmet" -> Material.LEATHER_HELMET;
            case "chest" -> Material.LEATHER_CHESTPLATE;
            case "leggings" -> Material.LEATHER_LEGGINGS;
            case "boots" -> Material.LEATHER_BOOTS;
            default -> null;
        };
    }

    public ItemStack createPiece(String setId, String slot) {
        BloodSetConfig c = configs.get(setId);
        if (c == null) return null;
        Material mat = slotToMaterial(slot);
        if (mat == null) return null;
        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        meta.getPersistentDataContainer().set(keySetId, PersistentDataType.STRING, setId);
        meta.getPersistentDataContainer().set(keySlot, PersistentDataType.STRING, slot);
        int health = c.getHealth(slot);
        int defense = c.getDefense(slot);
        int strength = c.getStrength(slot);
        int intelligence = c.getIntelligence(slot);
        int speed = c.getSpeed(slot);
        int cc = c.getCritChance(slot);
        int cd = c.getCritDamage(slot);
        meta.displayName(LegacyRgbParser.parse(c.getPieceDisplayName(slot)));
        List<Component> lore = new ArrayList<>();
        for (String line : c.getLoreLines(slot)) {
            String r = line.replace("{set_progress}", "4/4")
                    .replace("{radius_blocks}", String.valueOf(c.getSetBonusRadiusBlocks()))
                    .replace("{regen_multiplier}", String.valueOf((int) c.getSetBonusRegenMultiplier()))
                    .replace("{health}", String.valueOf(health)).replace("{defense}", String.valueOf(defense));
            lore.add(LegacyRgbParser.parse(r));
        }
        meta.lore(lore);
        meta.setUnbreakable(c.isUnbreakable());
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        if (meta instanceof LeatherArmorMeta lam) lam.setColor(c.getLeatherColor());
        item.setItemMeta(meta);
        return item;
    }

    public static String[] getSetIds() { return SET_IDS; }
    public static String[] getSlots() { return SLOTS; }
}
