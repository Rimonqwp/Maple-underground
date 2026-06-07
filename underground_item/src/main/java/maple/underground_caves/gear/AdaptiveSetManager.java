package maple.underground_caves.gear;

import maple.underground_item.UndergroundItem;
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

/** F3 自適應套裝：依地牢職業獲得額外屬性（治療師+生命/治療、法師+智力、戰士+力量、弓手+暴擊、坦克+防禦） */
public final class AdaptiveSetManager {

    private static final String PDC_SET_ID = "adaptive_set_id";
    private static final String PDC_SLOT = "adaptive_slot";
    private static final int SET_PIECE_COUNT = 4;
    private static final String[] SLOTS = { "helmet", "chest", "leggings", "boots" };

    private final JavaPlugin plugin;
    private final NamespacedKey keySetId;
    private final NamespacedKey keySlot;
    private AdaptiveSetConfig config;

    public AdaptiveSetManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.keySetId = new NamespacedKey(plugin, PDC_SET_ID);
        this.keySlot = new NamespacedKey(plugin, PDC_SLOT);
        reload();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "gear/adaptive_set.yml");
        if (!f.exists()) {
            plugin.saveResource("gear/adaptive_set.yml", false);
            f = new File(plugin.getDataFolder(), "gear/adaptive_set.yml");
        }
        config = new AdaptiveSetConfig(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f));
    }

    private int getClassIndex(Player player) {
        return (plugin instanceof UndergroundItem ui) ? ui.getDungeonClassIndex(player) : -1;
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

    public boolean isAdaptivePiece(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return AdaptiveSetConfig.SET_ID.equals(item.getItemMeta().getPersistentDataContainer().get(keySetId, PersistentDataType.STRING));
    }

    public int countWornPieces(Player player) {
        if (player == null) return 0;
        PlayerInventory inv = player.getInventory();
        int n = 0;
        if (isAdaptivePiece(inv.getHelmet())) n++;
        if (isAdaptivePiece(inv.getChestplate())) n++;
        if (isAdaptivePiece(inv.getLeggings())) n++;
        if (isAdaptivePiece(inv.getBoots())) n++;
        return n;
    }

    public int getBonusHealth(Player player) {
        if (player == null || config == null) return 0;
        int total = 0;
        PlayerInventory inv = player.getInventory();
        if (isAdaptivePiece(inv.getHelmet())) total += config.getHealth("helmet");
        if (isAdaptivePiece(inv.getChestplate())) total += config.getHealth("chest");
        if (isAdaptivePiece(inv.getLeggings())) total += config.getHealth("leggings");
        if (isAdaptivePiece(inv.getBoots())) total += config.getHealth("boots");
        int classIdx = getClassIndex(player);
        if (classIdx >= 0) total += config.getClassBonusHealth(classIdx);
        return total;
    }

    public int getBonusDefense(Player player) {
        if (player == null || config == null) return 0;
        int total = 0;
        PlayerInventory inv = player.getInventory();
        if (isAdaptivePiece(inv.getHelmet())) total += config.getDefense("helmet");
        if (isAdaptivePiece(inv.getChestplate())) total += config.getDefense("chest");
        if (isAdaptivePiece(inv.getLeggings())) total += config.getDefense("leggings");
        if (isAdaptivePiece(inv.getBoots())) total += config.getDefense("boots");
        int classIdx = getClassIndex(player);
        if (classIdx >= 0) total += config.getClassBonusDefense(classIdx);
        return total;
    }

    public int getBonusStrength(Player player) {
        if (player == null || config == null) return 0;
        int total = 0;
        PlayerInventory inv = player.getInventory();
        if (isAdaptivePiece(inv.getHelmet())) total += config.getStrength("helmet");
        if (isAdaptivePiece(inv.getChestplate())) total += config.getStrength("chest");
        if (isAdaptivePiece(inv.getLeggings())) total += config.getStrength("leggings");
        if (isAdaptivePiece(inv.getBoots())) total += config.getStrength("boots");
        int classIdx = getClassIndex(player);
        if (classIdx >= 0) total += config.getClassBonusStrength(classIdx);
        return total;
    }

    public int getBonusIntelligence(Player player) {
        if (player == null || config == null) return 0;
        int total = 0;
        PlayerInventory inv = player.getInventory();
        if (isAdaptivePiece(inv.getHelmet())) total += config.getIntelligence("helmet");
        if (isAdaptivePiece(inv.getChestplate())) total += config.getIntelligence("chest");
        if (isAdaptivePiece(inv.getLeggings())) total += config.getIntelligence("leggings");
        if (isAdaptivePiece(inv.getBoots())) total += config.getIntelligence("boots");
        int classIdx = getClassIndex(player);
        if (classIdx >= 0) total += config.getClassBonusIntelligence(classIdx);
        return total;
    }

    /** 治療師職業加成：治療量（有效活力加算） */
    public int getBonusMending(Player player) {
        if (player == null || config == null) return 0;
        int classIdx = getClassIndex(player);
        return classIdx >= 0 ? config.getClassBonusMending(classIdx) : 0;
    }

    public int getBonusCritChance(Player player) {
        if (player == null || config == null) return 0;
        int classIdx = getClassIndex(player);
        return classIdx >= 0 ? config.getClassBonusCritChance(classIdx) : 0;
    }

    public int getBonusCritDamage(Player player) {
        if (player == null || config == null) return 0;
        int classIdx = getClassIndex(player);
        return classIdx >= 0 ? config.getClassBonusCritDamage(classIdx) : 0;
    }

    public ItemStack createPiece(String slot) {
        if (config == null) return null;
        Material mat = slotToMaterial(slot);
        if (mat == null) return null;
        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        meta.getPersistentDataContainer().set(keySetId, PersistentDataType.STRING, AdaptiveSetConfig.SET_ID);
        meta.getPersistentDataContainer().set(keySlot, PersistentDataType.STRING, slot);
        int health = config.getHealth(slot);
        int defense = config.getDefense(slot);
        int strength = config.getStrength(slot);
        int intelligence = config.getIntelligence(slot);
        meta.displayName(LegacyRgbParser.parse(config.getPieceDisplayName(slot))
                .append(Component.text(" (F3)", NamedTextColor.YELLOW)));
        List<Component> lore = new ArrayList<>();
        for (String line : config.getLoreLines(slot)) {
            lore.add(LegacyRgbParser.parse(line
                    .replace("{health}", String.valueOf(health))
                    .replace("{defense}", String.valueOf(defense))
                    .replace("{strength}", String.valueOf(strength))
                    .replace("{intelligence}", String.valueOf(intelligence))
                    .replace("{set_progress}", "0/" + SET_PIECE_COUNT)));
        }
        meta.lore(lore);
        meta.setUnbreakable(config.isUnbreakable());
        maple.underground_item.util.ItemFlagUtil.applyStandardItemFlags(meta);
        if (meta instanceof LeatherArmorMeta lam) lam.setColor(config.getLeatherColor());
        item.setItemMeta(meta);
        return item;
    }

    public static String[] getSlots() { return SLOTS; }
}
