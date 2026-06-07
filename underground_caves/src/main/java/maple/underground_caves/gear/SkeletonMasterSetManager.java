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
 * 冥界套裝：每件箭矢傷害 +5%，套裝獎勵 Skeleton Master +25%；胸甲額外：弓不消耗箭矢。
 */
public final class SkeletonMasterSetManager {

    private static final String PDC_SET_ID = "skeleton_master_set_id";
    private static final String PDC_SLOT = "skeleton_master_slot";
    private static final String PDC_FLOOR = "skeleton_master_floor";
    private static final int SET_PIECE_COUNT = 4;

    private final JavaPlugin plugin;
    private final NamespacedKey keySetId;
    private final NamespacedKey keySlot;
    private final NamespacedKey keyFloor;
    private SkeletonMasterSetConfig config;

    public SkeletonMasterSetManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.keySetId = new NamespacedKey(plugin, PDC_SET_ID);
        this.keySlot = new NamespacedKey(plugin, PDC_SLOT);
        this.keyFloor = new NamespacedKey(plugin, PDC_FLOOR);
        reload();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "gear/skeleton_master_set.yml");
        if (!f.exists()) {
            plugin.saveResource("gear/skeleton_master_set.yml", false);
            f = new File(plugin.getDataFolder(), "gear/skeleton_master_set.yml");
        }
        config = new SkeletonMasterSetConfig(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f));
    }

    public SkeletonMasterSetConfig getConfig() {
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

    public boolean isSkeletonMasterPiece(ItemStack item) {
        return SkeletonMasterSetConfig.SET_ID.equals(getSetId(item));
    }

    /** 玩家是否穿著冥界套裝胸甲（用於弓不消耗箭矢）。 */
    public boolean hasSkeletonMasterChestplate(Player player) {
        if (player == null || config == null || !config.isChestplateBowNoConsumeArrow()) return false;
        ItemStack chest = player.getInventory().getChestplate();
        return chest != null && isSkeletonMasterPiece(chest) && "chest".equals(getSlot(chest));
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
        for (String f : SkeletonMasterSetConfig.FLOORS) {
            if (f.equalsIgnoreCase(floor)) return f;
        }
        return "F1";
    }

    public ItemStack createSkeletonMasterPiece(String slot, String floor) {
        String f = toValidFloor(floor);
        Material mat = slotToMaterial(slot);
        if (mat == null) return null;
        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        meta.getPersistentDataContainer().set(keySetId, PersistentDataType.STRING, SkeletonMasterSetConfig.SET_ID);
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
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        if (meta instanceof LeatherArmorMeta lam) {
            lam.setColor(config.getLeatherColor());
        }
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> buildLore(String slot, int defense, int health, int critDamage, int critChance, int arrowDmg, int wornCount) {
        List<Component> out = new ArrayList<>();
        String setProgress = Math.max(0, Math.min(wornCount, SET_PIECE_COUNT)) + "/" + SET_PIECE_COUNT;
        int arrowPercentPerPiece = config.getArrowDamagePercentPerPiece();
        int setBonusPercent = config.getSetBonusArrowDamagePercent();
        for (String line : config.getLoreLines(slot)) {
            String replaced = line
                    .replace("{defense}", String.valueOf(defense))
                    .replace("{health}", String.valueOf(health))
                    .replace("{crit_damage}", String.valueOf(critDamage))
                    .replace("{crit_chance}", String.valueOf(critChance))
                    .replace("{arrow_damage}", String.valueOf(arrowDmg))
                    .replace("{set_progress}", setProgress)
                    .replace("{arrow_damage_percent_per_piece}", String.valueOf(arrowPercentPerPiece))
                    .replace("{set_bonus_arrow_damage_percent}", String.valueOf(setBonusPercent));
            out.add(LegacyRgbParser.parse(replaced));
        }
        return out;
    }

    public int countWornPieces(Player player) {
        if (player == null) return 0;
        PlayerInventory inv = player.getInventory();
        int n = 0;
        if (isSkeletonMasterPiece(inv.getHelmet())) n++;
        if (isSkeletonMasterPiece(inv.getChestplate())) n++;
        if (isSkeletonMasterPiece(inv.getLeggings())) n++;
        if (isSkeletonMasterPiece(inv.getBoots())) n++;
        return n;
    }

    public boolean isWearingFullSet(Player player) {
        return countWornPieces(player) == SET_PIECE_COUNT;
    }

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
        if (isSkeletonMasterPiece(inv.getHelmet())) total += config.getDefense(getFloor(inv.getHelmet()), "helmet");
        if (isSkeletonMasterPiece(inv.getChestplate())) total += config.getDefense(getFloor(inv.getChestplate()), "chest");
        if (isSkeletonMasterPiece(inv.getLeggings())) total += config.getDefense(getFloor(inv.getLeggings()), "leggings");
        if (isSkeletonMasterPiece(inv.getBoots())) total += config.getDefense(getFloor(inv.getBoots()), "boots");
        return total;
    }

    public int getBonusHealth(Player player) {
        if (player == null) return 0;
        int total = 0;
        PlayerInventory inv = player.getInventory();
        if (isSkeletonMasterPiece(inv.getHelmet())) total += config.getHealth(getFloor(inv.getHelmet()), "helmet");
        if (isSkeletonMasterPiece(inv.getChestplate())) total += config.getHealth(getFloor(inv.getChestplate()), "chest");
        if (isSkeletonMasterPiece(inv.getLeggings())) total += config.getHealth(getFloor(inv.getLeggings()), "leggings");
        if (isSkeletonMasterPiece(inv.getBoots())) total += config.getHealth(getFloor(inv.getBoots()), "boots");
        return total;
    }

    public int getBonusCritDamage(Player player) {
        if (player == null) return 0;
        int total = 0;
        PlayerInventory inv = player.getInventory();
        if (isSkeletonMasterPiece(inv.getHelmet())) total += config.getCritDamage(getFloor(inv.getHelmet()), "helmet");
        if (isSkeletonMasterPiece(inv.getChestplate())) total += config.getCritDamage(getFloor(inv.getChestplate()), "chest");
        if (isSkeletonMasterPiece(inv.getLeggings())) total += config.getCritDamage(getFloor(inv.getLeggings()), "leggings");
        if (isSkeletonMasterPiece(inv.getBoots())) total += config.getCritDamage(getFloor(inv.getBoots()), "boots");
        return total;
    }

    public int getBonusCritChance(Player player) {
        if (player == null) return 0;
        int total = 0;
        PlayerInventory inv = player.getInventory();
        if (isSkeletonMasterPiece(inv.getHelmet())) total += config.getCritChance(getFloor(inv.getHelmet()), "helmet");
        if (isSkeletonMasterPiece(inv.getChestplate())) total += config.getCritChance(getFloor(inv.getChestplate()), "chest");
        if (isSkeletonMasterPiece(inv.getLeggings())) total += config.getCritChance(getFloor(inv.getLeggings()), "leggings");
        if (isSkeletonMasterPiece(inv.getBoots())) total += config.getCritChance(getFloor(inv.getBoots()), "boots");
        return total;
    }
}
