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

/** 深窟探險者套裝 F2：穿齊四件且在洞窟內時 +5 速度、5% 傷害減免 */
public final class CavernExplorerSetManager {

    private static final String PDC_SET_ID = "cavern_explorer_set_id";
    private static final String PDC_SLOT = "cavern_explorer_slot";
    private static final int SET_PIECE_COUNT = 4;
    private static final String[] SLOTS = { "helmet", "chest", "leggings", "boots" };

    private final JavaPlugin plugin;
    private final NamespacedKey keySetId;
    private final NamespacedKey keySlot;
    private CavernExplorerSetConfig config;

    public CavernExplorerSetManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.keySetId = new NamespacedKey(plugin, PDC_SET_ID);
        this.keySlot = new NamespacedKey(plugin, PDC_SLOT);
        reload();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "gear/cavern_explorer_set.yml");
        if (!f.exists()) {
            plugin.saveResource("gear/cavern_explorer_set.yml", false);
            f = new File(plugin.getDataFolder(), "gear/cavern_explorer_set.yml");
        }
        config = new CavernExplorerSetConfig(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f));
    }

    public NamespacedKey getKeySetId() { return keySetId; }

    private static Material slotToMaterial(String slot) {
        return switch (slot == null ? "" : slot) {
            case "helmet" -> Material.LEATHER_HELMET;
            case "chest" -> Material.LEATHER_CHESTPLATE;
            case "leggings" -> Material.LEATHER_LEGGINGS;
            case "boots" -> Material.LEATHER_BOOTS;
            default -> null;
        };
    }

    public boolean isCavernExplorerPiece(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return CavernExplorerSetConfig.SET_ID.equals(item.getItemMeta().getPersistentDataContainer().get(keySetId, PersistentDataType.STRING));
    }

    public int countWornPieces(Player player) {
        if (player == null) return 0;
        PlayerInventory inv = player.getInventory();
        int n = 0;
        if (isCavernExplorerPiece(inv.getHelmet())) n++;
        if (isCavernExplorerPiece(inv.getChestplate())) n++;
        if (isCavernExplorerPiece(inv.getLeggings())) n++;
        if (isCavernExplorerPiece(inv.getBoots())) n++;
        return n;
    }

    public boolean isWearingFullSet(Player player) { return countWornPieces(player) == SET_PIECE_COUNT; }

    private static boolean isInDungeon(Player player) {
        return player != null && player.getWorld() != null && player.getWorld().getName() != null
                && player.getWorld().getName().startsWith("dungeon_");
    }

    /** 洞窟內穿齊四件時額外速度 */
    public int getBonusSpeed(Player player) {
        return (config != null && isWearingFullSet(player) && isInDungeon(player)) ? config.getWalkSpeedBonus() : 0;
    }

    /** 洞窟內穿齊四件時傷害減免百分比（0~100） */
    public int getDamageReductionPercent(Player player) {
        return (config != null && isWearingFullSet(player) && isInDungeon(player)) ? config.getDamageReductionPercent() : 0;
    }

    public int getBonusHealth(Player player) {
        if (player == null || config == null) return 0;
        int total = 0;
        PlayerInventory inv = player.getInventory();
        if (isCavernExplorerPiece(inv.getHelmet())) total += config.getHealth("helmet");
        if (isCavernExplorerPiece(inv.getChestplate())) total += config.getHealth("chest");
        if (isCavernExplorerPiece(inv.getLeggings())) total += config.getHealth("leggings");
        if (isCavernExplorerPiece(inv.getBoots())) total += config.getHealth("boots");
        return total;
    }

    public int getBonusDefense(Player player) {
        if (player == null || config == null) return 0;
        int total = 0;
        PlayerInventory inv = player.getInventory();
        if (isCavernExplorerPiece(inv.getHelmet())) total += config.getDefense("helmet");
        if (isCavernExplorerPiece(inv.getChestplate())) total += config.getDefense("chest");
        if (isCavernExplorerPiece(inv.getLeggings())) total += config.getDefense("leggings");
        if (isCavernExplorerPiece(inv.getBoots())) total += config.getDefense("boots");
        return total;
    }

    public int getBonusStrength(Player player) {
        if (player == null || config == null) return 0;
        int total = 0;
        PlayerInventory inv = player.getInventory();
        if (isCavernExplorerPiece(inv.getHelmet())) total += config.getStrength("helmet");
        if (isCavernExplorerPiece(inv.getChestplate())) total += config.getStrength("chest");
        if (isCavernExplorerPiece(inv.getLeggings())) total += config.getStrength("leggings");
        if (isCavernExplorerPiece(inv.getBoots())) total += config.getStrength("boots");
        return total;
    }

    public int getBonusIntelligence(Player player) {
        if (player == null || config == null) return 0;
        int total = 0;
        PlayerInventory inv = player.getInventory();
        if (isCavernExplorerPiece(inv.getHelmet())) total += config.getIntelligence("helmet");
        if (isCavernExplorerPiece(inv.getChestplate())) total += config.getIntelligence("chest");
        if (isCavernExplorerPiece(inv.getLeggings())) total += config.getIntelligence("leggings");
        if (isCavernExplorerPiece(inv.getBoots())) total += config.getIntelligence("boots");
        return total;
    }

    public ItemStack createPiece(String slot) {
        if (config == null) return null;
        Material mat = slotToMaterial(slot);
        if (mat == null) return null;
        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        meta.getPersistentDataContainer().set(keySetId, PersistentDataType.STRING, CavernExplorerSetConfig.SET_ID);
        meta.getPersistentDataContainer().set(keySlot, PersistentDataType.STRING, slot);
        int health = config.getHealth(slot);
        int defense = config.getDefense(slot);
        int strength = config.getStrength(slot);
        int intelligence = config.getIntelligence(slot);
        meta.displayName(LegacyRgbParser.parse(config.getPieceDisplayName(slot))
                .append(Component.text(" (F2)", NamedTextColor.YELLOW)));
        List<Component> lore = new ArrayList<>();
        String setProgress = "0/" + SET_PIECE_COUNT;
        for (String line : config.getLoreLines(slot)) {
            lore.add(LegacyRgbParser.parse(line
                    .replace("{health}", String.valueOf(health))
                    .replace("{defense}", String.valueOf(defense))
                    .replace("{strength}", String.valueOf(strength))
                    .replace("{intelligence}", String.valueOf(intelligence))
                    .replace("{set_progress}", setProgress)
                    .replace("{walk_speed_bonus}", String.valueOf(config.getWalkSpeedBonus()))
                    .replace("{damage_reduction_percent}", String.valueOf(config.getDamageReductionPercent()))));
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
