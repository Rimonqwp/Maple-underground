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
 * 白骨先鋒套裝：擊殺地牢骷髏 2.5% 掉落；每件箭矢傷害 +5%，套裝獎勵 Skeleton Soldier 額外 +25%（穿齊共 45%）。
 */
public final class SkeletonSoldierSetManager {

    private static final String PDC_SET_ID = "skeleton_soldier_set_id";
    private static final String PDC_SLOT = "skeleton_soldier_slot";
    private static final String PDC_FLOOR = "skeleton_soldier_floor";
    private static final int SET_PIECE_COUNT = 4;

    private final JavaPlugin plugin;
    private final NamespacedKey keySetId;
    private final NamespacedKey keySlot;
    private final NamespacedKey keyFloor;
    private SkeletonSoldierSetConfig config;

    public SkeletonSoldierSetManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.keySetId = new NamespacedKey(plugin, PDC_SET_ID);
        this.keySlot = new NamespacedKey(plugin, PDC_SLOT);
        this.keyFloor = new NamespacedKey(plugin, PDC_FLOOR);
        reload();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "gear/skeleton_soldier_set.yml");
        if (!f.exists()) {
            plugin.saveResource("gear/skeleton_soldier_set.yml", false);
            f = new File(plugin.getDataFolder(), "gear/skeleton_soldier_set.yml");
        }
        config = new SkeletonSoldierSetConfig(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f));
    }

    public SkeletonSoldierSetConfig getConfig() {
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

    public boolean isSkeletonSoldierPiece(ItemStack item) {
        return SkeletonSoldierSetConfig.SET_ID.equals(getSetId(item));
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
        for (String f : SkeletonSoldierSetConfig.FLOORS) {
            if (f.equalsIgnoreCase(floor)) return f;
        }
        return "F1";
    }

    public ItemStack createSkeletonSoldierPiece(String slot, String floor) {
        String f = toValidFloor(floor);
        Material mat = slotToMaterial(slot);
        if (mat == null) return null;
        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        meta.getPersistentDataContainer().set(keySetId, PersistentDataType.STRING, SkeletonSoldierSetConfig.SET_ID);
        meta.getPersistentDataContainer().set(keySlot, PersistentDataType.STRING, slot);
        meta.getPersistentDataContainer().set(keyFloor, PersistentDataType.STRING, f);
        int defense = config.getDefense(f, slot);
        int health = config.getHealth(f, slot);
        int critDamage = config.getCritDamage(f, slot);
        int critChance = config.getCritChance(f, slot);
        int arrowDmg = config.getArrowDamagePercentPerPiece();
        meta.displayName(LegacyRgbParser.parse(config.getPieceDisplayName(slot))
                .append(Component.text(" (" + f + ")").color(NamedTextColor.YELLOW)));
        meta.lore(buildLore(slot, defense, health, critDamage, critChance, arrowDmg, 0));
        meta.setUnbreakable(config.isUnbreakable());
        maple.underground_item.util.ItemFlagUtil.applyStandardItemFlags(meta);
        if (meta instanceof LeatherArmorMeta lam) {
            lam.setColor(config.getLeatherColor());
        }
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> buildLore(String slot, int defense, int health, int critDamage, int critChance, int arrowDmg, int wornCount) {
        List<Component> out = new ArrayList<>();
        String setProgress = Math.max(0, Math.min(wornCount, SET_PIECE_COUNT)) + "/" + SET_PIECE_COUNT;
        int setBonusArrowDmg = config.getSetBonusArrowDamagePercent();
        for (String line : config.getLoreLines(slot)) {
            String replaced = line
                    .replace("{defense}", String.valueOf(defense))
                    .replace("{health}", String.valueOf(health))
                    .replace("{crit_damage}", String.valueOf(critDamage))
                    .replace("{crit_chance}", String.valueOf(critChance))
                    .replace("{arrow_damage}", String.valueOf(arrowDmg))
                    .replace("{arrow_damage_percent_per_piece}", String.valueOf(arrowDmg))
                    .replace("{set_bonus_arrow_damage}", String.valueOf(setBonusArrowDmg))
                    .replace("{set_bonus_arrow_damage_percent}", String.valueOf(setBonusArrowDmg))
                    .replace("{set_progress}", setProgress);
            out.add(LegacyRgbParser.parse(replaced));
        }
        return out;
    }

    public int countWornPieces(Player player) {
        if (player == null) return 0;
        PlayerInventory inv = player.getInventory();
        int n = 0;
        if (isSkeletonSoldierPiece(inv.getHelmet())) n++;
        if (isSkeletonSoldierPiece(inv.getChestplate())) n++;
        if (isSkeletonSoldierPiece(inv.getLeggings())) n++;
        if (isSkeletonSoldierPiece(inv.getBoots())) n++;
        return n;
    }

    public boolean isWearingFullSet(Player player) {
        return countWornPieces(player) == SET_PIECE_COUNT;
    }

    /** 玩家當前箭矢傷害加成（每件 +5%，穿齊 4 件額外 +25%，共 45%）。 */
    public double getArrowDamagePercent(Player player) {
        if (player == null || config == null) return 0;
        int fromPieces = countWornPieces(player) * config.getArrowDamagePercentPerPiece();
        int setBonus = isWearingFullSet(player) ? config.getSetBonusArrowDamagePercent() : 0;
        return fromPieces + setBonus;
    }

    public int getBonusDefense(Player player) {
        if (player == null) return 0;
        int total = 0;
        PlayerInventory inv = player.getInventory();
        if (isSkeletonSoldierPiece(inv.getHelmet())) total += config.getDefense(getFloor(inv.getHelmet()), "helmet");
        if (isSkeletonSoldierPiece(inv.getChestplate())) total += config.getDefense(getFloor(inv.getChestplate()), "chest");
        if (isSkeletonSoldierPiece(inv.getLeggings())) total += config.getDefense(getFloor(inv.getLeggings()), "leggings");
        if (isSkeletonSoldierPiece(inv.getBoots())) total += config.getDefense(getFloor(inv.getBoots()), "boots");
        return total;
    }

    public int getBonusHealth(Player player) {
        if (player == null) return 0;
        int total = 0;
        PlayerInventory inv = player.getInventory();
        if (isSkeletonSoldierPiece(inv.getHelmet())) total += config.getHealth(getFloor(inv.getHelmet()), "helmet");
        if (isSkeletonSoldierPiece(inv.getChestplate())) total += config.getHealth(getFloor(inv.getChestplate()), "chest");
        if (isSkeletonSoldierPiece(inv.getLeggings())) total += config.getHealth(getFloor(inv.getLeggings()), "leggings");
        if (isSkeletonSoldierPiece(inv.getBoots())) total += config.getHealth(getFloor(inv.getBoots()), "boots");
        return total;
    }

    public int getBonusCritDamage(Player player) {
        if (player == null) return 0;
        int total = 0;
        PlayerInventory inv = player.getInventory();
        if (isSkeletonSoldierPiece(inv.getHelmet())) total += config.getCritDamage(getFloor(inv.getHelmet()), "helmet");
        if (isSkeletonSoldierPiece(inv.getChestplate())) total += config.getCritDamage(getFloor(inv.getChestplate()), "chest");
        if (isSkeletonSoldierPiece(inv.getLeggings())) total += config.getCritDamage(getFloor(inv.getLeggings()), "leggings");
        if (isSkeletonSoldierPiece(inv.getBoots())) total += config.getCritDamage(getFloor(inv.getBoots()), "boots");
        return total;
    }

    public int getBonusCritChance(Player player) {
        if (player == null) return 0;
        int total = 0;
        PlayerInventory inv = player.getInventory();
        if (isSkeletonSoldierPiece(inv.getHelmet())) total += config.getCritChance(getFloor(inv.getHelmet()), "helmet");
        if (isSkeletonSoldierPiece(inv.getChestplate())) total += config.getCritChance(getFloor(inv.getChestplate()), "chest");
        if (isSkeletonSoldierPiece(inv.getLeggings())) total += config.getCritChance(getFloor(inv.getLeggings()), "leggings");
        if (isSkeletonSoldierPiece(inv.getBoots())) total += config.getCritChance(getFloor(inv.getBoots()), "boots");
        return total;
    }
}
