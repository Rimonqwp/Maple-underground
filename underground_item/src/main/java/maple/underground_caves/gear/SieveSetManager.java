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
import java.util.List;

/**
 * 篩子身體套裝：每件 15% 箭矢擊退抗性，套裝獎勵 +20%；擊殺地牢女巫 10% 掉落。屬性為生命 + 力量，F1～F3 硬編碼。
 */
public final class SieveSetManager {

    private static final String PDC_SET_ID = "sieve_set_id";
    private static final String PDC_SLOT = "sieve_slot";
    private static final String PDC_FLOOR = "sieve_floor";
    private static final int SET_PIECE_COUNT = 4;

    private final JavaPlugin plugin;
    private final NamespacedKey keySetId;
    private final NamespacedKey keySlot;
    private final NamespacedKey keyFloor;
    private SieveSetConfig config;

    public SieveSetManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.keySetId = new NamespacedKey(plugin, PDC_SET_ID);
        this.keySlot = new NamespacedKey(plugin, PDC_SLOT);
        this.keyFloor = new NamespacedKey(plugin, PDC_FLOOR);
        reload();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "gear/sieve_set.yml");
        if (!f.exists()) {
            plugin.saveResource("gear/sieve_set.yml", false);
            f = new File(plugin.getDataFolder(), "gear/sieve_set.yml");
        }
        config = new SieveSetConfig(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f));
    }

    public SieveSetConfig getConfig() {
        return config;
    }

    public String getSetId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(keySetId, PersistentDataType.STRING);
    }

    public String getSlot(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(keySlot, PersistentDataType.STRING);
    }

    /** 取得裝備樓層（F1/F2/F3），無則視為 F1）。 */
    public String getFloor(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return "F1";
        String f = item.getItemMeta().getPersistentDataContainer().get(keyFloor, PersistentDataType.STRING);
        return f != null && !f.isEmpty() ? f : "F1";
    }

    public boolean isSievePiece(ItemStack item) {
        return SieveSetConfig.SET_ID.equals(getSetId(item));
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

    /** 建立篩子身體指定部位、樓層；樓層僅支援 F1～F3，否則視為 F1。 */
    public ItemStack createSievePiece(String slot, String floor) {
        String f = toValidFloor(floor);
        Material mat = slotToMaterial(slot);
        if (mat == null) return null;
        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        meta.getPersistentDataContainer().set(keySetId, PersistentDataType.STRING, SieveSetConfig.SET_ID);
        meta.getPersistentDataContainer().set(keySlot, PersistentDataType.STRING, slot);
        meta.getPersistentDataContainer().set(keyFloor, PersistentDataType.STRING, f);
        int health = config.getHealth(f, slot);
        int strength = config.getStrength(f, slot);
        int arrowResist = config.getArrowResistPerPiece();
        meta.displayName(LegacyRgbParser.parse(config.getPieceDisplayName(slot))
                .append(net.kyori.adventure.text.Component.text(" (" + f + ")").color(net.kyori.adventure.text.format.NamedTextColor.YELLOW)));
        meta.lore(buildLore(slot, health, strength, arrowResist, 0));
        meta.setUnbreakable(config.isUnbreakable());
        maple.underground_item.util.ItemFlagUtil.applyStandardItemFlags(meta);
        if (meta instanceof LeatherArmorMeta lam) {
            lam.setColor(config.getLeatherColor());
        }
        item.setItemMeta(meta);
        return item;
    }

    private static String toValidFloor(String floor) {
        if (floor == null) return "F1";
        for (String f : SieveSetConfig.FLOORS) {
            if (f.equalsIgnoreCase(floor)) return f;
        }
        return "F1";
    }

    private List<Component> buildLore(String slot, int health, int strength, int arrowResist, int wornCount) {
        List<Component> out = new ArrayList<>();
        String setProgress = Math.max(0, Math.min(wornCount, SET_PIECE_COUNT)) + "/" + SET_PIECE_COUNT;
        int setBonusResist = config.getSetBonusArrowResist();
        for (String line : config.getLoreLines(slot)) {
            String replaced = line
                    .replace("{health}", String.valueOf(health))
                    .replace("{strength}", String.valueOf(strength))
                    .replace("{arrow_resist}", String.valueOf(arrowResist))
                    .replace("{arrow_knockback_resistance_per_piece}", String.valueOf(arrowResist))
                    .replace("{set_bonus_arrow_resistance}", String.valueOf(setBonusResist))
                    .replace("{set_progress}", setProgress);
            out.add(LegacyRgbParser.parse(replaced));
        }
        return out;
    }

    public int countWornPieces(Player player) {
        if (player == null) return 0;
        PlayerInventory inv = player.getInventory();
        int n = 0;
        if (isSievePiece(inv.getHelmet())) n++;
        if (isSievePiece(inv.getChestplate())) n++;
        if (isSievePiece(inv.getLeggings())) n++;
        if (isSievePiece(inv.getBoots())) n++;
        return n;
    }

    public boolean isWearingFullSet(Player player) {
        return countWornPieces(player) == SET_PIECE_COUNT;
    }

    /** 玩家當前箭矢擊退抗性百分比（每件 15% + 套裝滿 4 件時 +20%）。 */
    public double getArrowKnockbackResistancePercent(Player player) {
        if (player == null || config == null) return 0;
        int worn = countWornPieces(player);
        int fromPieces = worn * config.getArrowResistPerPiece();
        int setBonus = isWearingFullSet(player) ? config.getSetBonusArrowResist() : 0;
        return fromPieces + setBonus;
    }

    /** 裝備篩子身體時額外生命（依每件裝備的樓層讀取）。 */
    public int getBonusHealth(Player player) {
        if (player == null) return 0;
        int total = 0;
        PlayerInventory inv = player.getInventory();
        if (isSievePiece(inv.getHelmet())) total += config.getHealth(getFloor(inv.getHelmet()), "helmet");
        if (isSievePiece(inv.getChestplate())) total += config.getHealth(getFloor(inv.getChestplate()), "chest");
        if (isSievePiece(inv.getLeggings())) total += config.getHealth(getFloor(inv.getLeggings()), "leggings");
        if (isSievePiece(inv.getBoots())) total += config.getHealth(getFloor(inv.getBoots()), "boots");
        return total;
    }

    /** 裝備篩子身體時額外力量（依每件裝備的樓層讀取）。 */
    public int getBonusStrength(Player player) {
        if (player == null) return 0;
        int total = 0;
        PlayerInventory inv = player.getInventory();
        if (isSievePiece(inv.getHelmet())) total += config.getStrength(getFloor(inv.getHelmet()), "helmet");
        if (isSievePiece(inv.getChestplate())) total += config.getStrength(getFloor(inv.getChestplate()), "chest");
        if (isSievePiece(inv.getLeggings())) total += config.getStrength(getFloor(inv.getLeggings()), "leggings");
        if (isSievePiece(inv.getBoots())) total += config.getStrength(getFloor(inv.getBoots()), "boots");
        return total;
    }
}
