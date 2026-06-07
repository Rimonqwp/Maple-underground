package maple.underground_caves.gear;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
 * 骨弓套裝：擊殺地牢骷髏 10% 掉落；每件裝備使箭矢傷害 +5%。屬性為防禦、生命、暴擊傷害，F1～F3 硬編碼。
 */
public final class ArcherSetManager {

    private static final String PDC_SET_ID = "archer_set_id";
    private static final String PDC_SLOT = "archer_slot";
    private static final String PDC_FLOOR = "archer_floor";
    private static final int SET_PIECE_COUNT = 4;

    private final JavaPlugin plugin;
    private final NamespacedKey keySetId;
    private final NamespacedKey keySlot;
    private final NamespacedKey keyFloor;
    private ArcherSetConfig config;

    public ArcherSetManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.keySetId = new NamespacedKey(plugin, PDC_SET_ID);
        this.keySlot = new NamespacedKey(plugin, PDC_SLOT);
        this.keyFloor = new NamespacedKey(plugin, PDC_FLOOR);
        reload();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "gear/archer_set.yml");
        if (!f.exists()) {
            plugin.saveResource("gear/archer_set.yml", false);
            f = new File(plugin.getDataFolder(), "gear/archer_set.yml");
        }
        config = new ArcherSetConfig(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f));
    }

    public ArcherSetConfig getConfig() {
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

    public String getFloor(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return "F1";
        String f = item.getItemMeta().getPersistentDataContainer().get(keyFloor, PersistentDataType.STRING);
        return f != null && !f.isEmpty() ? f : "F1";
    }

    public boolean isArcherPiece(ItemStack item) {
        return ArcherSetConfig.SET_ID.equals(getSetId(item));
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

    private static String toValidFloor(String floor) {
        if (floor == null) return "F1";
        for (String f : ArcherSetConfig.FLOORS) {
            if (f.equalsIgnoreCase(floor)) return f;
        }
        return "F1";
    }

    public ItemStack createArcherPiece(String slot, String floor) {
        String f = toValidFloor(floor);
        Material mat = slotToMaterial(slot);
        if (mat == null) return null;
        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        meta.getPersistentDataContainer().set(keySetId, PersistentDataType.STRING, ArcherSetConfig.SET_ID);
        meta.getPersistentDataContainer().set(keySlot, PersistentDataType.STRING, slot);
        meta.getPersistentDataContainer().set(keyFloor, PersistentDataType.STRING, f);
        int defense = config.getDefense(f, slot);
        int health = config.getHealth(f, slot);
        int critDamage = config.getCritDamage(f, slot);
        int arrowDmg = config.getArrowDamagePercentPerPiece();
        meta.displayName(LegacyRgbParser.parse(config.getPieceDisplayName(slot))
                .append(Component.text(" (" + f + ")").color(NamedTextColor.YELLOW)));
        meta.lore(buildLore(slot, defense, health, critDamage, arrowDmg, 0));
        meta.setUnbreakable(config.isUnbreakable());
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        if (meta instanceof LeatherArmorMeta lam) {
            lam.setColor(config.getLeatherColor());
        }
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> buildLore(String slot, int defense, int health, int critDamage, int arrowDmg, int wornCount) {
        List<Component> out = new ArrayList<>();
        String setProgress = Math.max(0, Math.min(wornCount, SET_PIECE_COUNT)) + "/" + SET_PIECE_COUNT;
        for (String line : config.getLoreLines(slot)) {
            String replaced = line
                    .replace("{defense}", String.valueOf(defense))
                    .replace("{health}", String.valueOf(health))
                    .replace("{crit_damage}", String.valueOf(critDamage))
                    .replace("{arrow_damage}", String.valueOf(arrowDmg))
                    .replace("{set_progress}", setProgress);
            out.add(LegacyRgbParser.parse(replaced));
        }
        return out;
    }

    public int countWornPieces(Player player) {
        if (player == null) return 0;
        PlayerInventory inv = player.getInventory();
        int n = 0;
        if (isArcherPiece(inv.getHelmet())) n++;
        if (isArcherPiece(inv.getChestplate())) n++;
        if (isArcherPiece(inv.getLeggings())) n++;
        if (isArcherPiece(inv.getBoots())) n++;
        return n;
    }

    /** 玩家當前箭矢傷害加成百分比（每件 +5%，4 件 = 20%）。 */
    public double getArrowDamagePercent(Player player) {
        if (player == null || config == null) return 0;
        return countWornPieces(player) * config.getArrowDamagePercentPerPiece();
    }

    public int getBonusDefense(Player player) {
        if (player == null) return 0;
        int total = 0;
        PlayerInventory inv = player.getInventory();
        if (isArcherPiece(inv.getHelmet())) total += config.getDefense(getFloor(inv.getHelmet()), "helmet");
        if (isArcherPiece(inv.getChestplate())) total += config.getDefense(getFloor(inv.getChestplate()), "chest");
        if (isArcherPiece(inv.getLeggings())) total += config.getDefense(getFloor(inv.getLeggings()), "leggings");
        if (isArcherPiece(inv.getBoots())) total += config.getDefense(getFloor(inv.getBoots()), "boots");
        return total;
    }

    public int getBonusHealth(Player player) {
        if (player == null) return 0;
        int total = 0;
        PlayerInventory inv = player.getInventory();
        if (isArcherPiece(inv.getHelmet())) total += config.getHealth(getFloor(inv.getHelmet()), "helmet");
        if (isArcherPiece(inv.getChestplate())) total += config.getHealth(getFloor(inv.getChestplate()), "chest");
        if (isArcherPiece(inv.getLeggings())) total += config.getHealth(getFloor(inv.getLeggings()), "leggings");
        if (isArcherPiece(inv.getBoots())) total += config.getHealth(getFloor(inv.getBoots()), "boots");
        return total;
    }

    public int getBonusCritDamage(Player player) {
        if (player == null) return 0;
        int total = 0;
        PlayerInventory inv = player.getInventory();
        if (isArcherPiece(inv.getHelmet())) total += config.getCritDamage(getFloor(inv.getHelmet()), "helmet");
        if (isArcherPiece(inv.getChestplate())) total += config.getCritDamage(getFloor(inv.getChestplate()), "chest");
        if (isArcherPiece(inv.getLeggings())) total += config.getCritDamage(getFloor(inv.getLeggings()), "leggings");
        if (isArcherPiece(inv.getBoots())) total += config.getCritDamage(getFloor(inv.getBoots()), "boots");
        return total;
    }
}
