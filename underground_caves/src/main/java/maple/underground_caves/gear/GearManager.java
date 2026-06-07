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

import java.util.ArrayList;
import java.util.List;

/**
 * 擊敗坦克套裝：建立物品、讀取 PDC、計算裝備防禦與速度（含套裝獎勵）。
 */
public final class GearManager {

    private static final String PDC_SET_ID = "gear_set_id";
    private static final String PDC_SLOT = "gear_slot";
    private static final String PDC_FLOOR = "gear_floor";
    private static final int SET_PIECE_COUNT = 4;

    private final JavaPlugin plugin;
    private final NamespacedKey keySetId;
    private final NamespacedKey keySlot;
    private final NamespacedKey keyFloor;
    private DefeatTankSetConfig defeatTankConfig;

    public GearManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.keySetId = new NamespacedKey(plugin, PDC_SET_ID);
        this.keySlot = new NamespacedKey(plugin, PDC_SLOT);
        this.keyFloor = new NamespacedKey(plugin, PDC_FLOOR);
        reload();
    }

    public void reload() {
        java.io.File f = new java.io.File(plugin.getDataFolder(), "gear/defeat_tank_set.yml");
        if (!f.exists()) {
            plugin.saveResource("gear/defeat_tank_set.yml", false);
            f = new java.io.File(plugin.getDataFolder(), "gear/defeat_tank_set.yml");
        }
        defeatTankConfig = new DefeatTankSetConfig(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f));
    }

    public DefeatTankSetConfig getDefeatTankConfig() {
        return defeatTankConfig;
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
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(keyFloor, PersistentDataType.STRING);
    }

    public boolean isDefeatTankPiece(ItemStack item) {
        return DefeatTankSetConfig.SET_ID.equals(getSetId(item));
    }

    /** 建立擊敗坦克指定部位、樓層的皮甲。僅支援 F1～F3，其餘視為 F1。 */
    public ItemStack createDefeatTankPiece(String slot, String floor) {
        String f = toValidFloor(floor);
        Material mat = slotToMaterial(slot);
        if (mat == null) return null;
        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        meta.getPersistentDataContainer().set(keySetId, PersistentDataType.STRING, DefeatTankSetConfig.SET_ID);
        meta.getPersistentDataContainer().set(keySlot, PersistentDataType.STRING, slot);
        meta.getPersistentDataContainer().set(keyFloor, PersistentDataType.STRING, f);
        int def = defeatTankConfig.getDefense(f, slot);
        int spd = defeatTankConfig.getSpeed(f, slot);
        Component nameComponent = LegacyRgbParser.parse(defeatTankConfig.getPieceDisplayName(slot))
                .append(Component.text(" (" + f + ")").color(NamedTextColor.YELLOW));
        meta.displayName(nameComponent);
        List<Component> lore = buildLore(slot, def, spd, 0);
        meta.lore(lore);
        meta.setUnbreakable(defeatTankConfig.isUnbreakable());
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        if (meta instanceof LeatherArmorMeta lam) {
            lam.setColor(defeatTankConfig.getLeatherColor());
        }
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> buildLore(String slot, int defense, int speed, int wornCount) {
        List<Component> out = new ArrayList<>();
        String setProgress = Math.max(0, Math.min(wornCount, SET_PIECE_COUNT)) + "/" + SET_PIECE_COUNT;
        String defStr = defense >= 0 ? "+" + defense : String.valueOf(defense);
        String spdStr = String.valueOf(speed);
        String speedPer50Def = String.valueOf(defeatTankConfig.getSpeedPer50Defense());
        for (String line : defeatTankConfig.getLoreLines(slot)) {
            String replaced = line.replace("{defense}", defStr)
                    .replace("{speed}", spdStr)
                    .replace("{speed_per_50_defense}", speedPer50Def)
                    .replace("{set_progress}", setProgress);
            if (replaced.isEmpty()) {
                out.add(Component.empty());
            } else {
                out.add(LegacyRgbParser.parse(replaced));
            }
        }
        return out;
    }

    /** 玩家目前穿著幾件堅壁套裝（0～4）。 */
    public int countWornPieces(Player player) {
        if (player == null) return 0;
        int n = 0;
        if (isDefeatTankPiece(player.getInventory().getHelmet())) n++;
        if (isDefeatTankPiece(player.getInventory().getChestplate())) n++;
        if (isDefeatTankPiece(player.getInventory().getLeggings())) n++;
        if (isDefeatTankPiece(player.getInventory().getBoots())) n++;
        return n;
    }

    /** 更新該玩家背包與裝備欄中所有堅壁套裝的 lore，使「完整套裝獎勵：堅壁 (x/4)」顯示當前穿戴件數。 */
    public void refreshSetProgressLore(Player player) {
        if (player == null || defeatTankConfig == null) return;
        int worn = countWornPieces(player);
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.isEmpty() || !isDefeatTankPiece(stack)) continue;
            applySetProgressLore(stack, worn);
        }
        for (ItemStack stack : new ItemStack[]{inv.getHelmet(), inv.getChestplate(), inv.getLeggings(), inv.getBoots()}) {
            if (stack == null || stack.isEmpty() || !isDefeatTankPiece(stack)) continue;
            applySetProgressLore(stack, worn);
        }
    }

    private void applySetProgressLore(ItemStack stack, int worn) {
        String slot = getSlot(stack);
        String floor = getFloor(stack);
        if (slot == null || floor == null) return;
        int def = defeatTankConfig.getDefense(floor, slot);
        int spd = defeatTankConfig.getSpeed(floor, slot);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        meta.lore(buildLore(slot, def, spd, worn));
        meta.setUnbreakable(defeatTankConfig.isUnbreakable());
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
    }

    private static String toValidFloor(String floor) {
        if (floor == null) return "F1";
        for (String f : DefeatTankSetConfig.FLOORS) {
            if (f.equalsIgnoreCase(floor)) return f;
        }
        return "F1";
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

    /** 玩家穿著擊敗坦克四件時，總防禦（來自裝備）。 */
    public int getEquipmentDefense(Player player) {
        if (player == null || defeatTankConfig == null) return 0;
        int total = 0;
        ItemStack h = player.getInventory().getHelmet();
        ItemStack c = player.getInventory().getChestplate();
        ItemStack l = player.getInventory().getLeggings();
        ItemStack b = player.getInventory().getBoots();
        if (isDefeatTankPiece(h)) total += defeatTankConfig.getDefense(getFloor(h), "helmet");
        if (isDefeatTankPiece(c)) total += defeatTankConfig.getDefense(getFloor(c), "chest");
        if (isDefeatTankPiece(l)) total += defeatTankConfig.getDefense(getFloor(l), "leggings");
        if (isDefeatTankPiece(b)) total += defeatTankConfig.getDefense(getFloor(b), "boots");
        return total;
    }

    /** 玩家穿著擊敗坦克四件時，總速度（來自裝備，每件 -5）。 */
    public int getEquipmentSpeed(Player player) {
        if (player == null || defeatTankConfig == null) return 0;
        int total = 0;
        if (isDefeatTankPiece(player.getInventory().getHelmet())) total += defeatTankConfig.getSpeed(getFloor(player.getInventory().getHelmet()), "helmet");
        if (isDefeatTankPiece(player.getInventory().getChestplate())) total += defeatTankConfig.getSpeed(getFloor(player.getInventory().getChestplate()), "chest");
        if (isDefeatTankPiece(player.getInventory().getLeggings())) total += defeatTankConfig.getSpeed(getFloor(player.getInventory().getLeggings()), "leggings");
        if (isDefeatTankPiece(player.getInventory().getBoots())) total += defeatTankConfig.getSpeed(getFloor(player.getInventory().getBoots()), "boots");
        return total;
    }

    /** 是否穿齊四件擊敗坦克。 */
    public boolean isWearingFullSet(Player player) {
        if (player == null) return false;
        return isDefeatTankPiece(player.getInventory().getHelmet())
                && isDefeatTankPiece(player.getInventory().getChestplate())
                && isDefeatTankPiece(player.getInventory().getLeggings())
                && isDefeatTankPiece(player.getInventory().getBoots());
    }

    /** 套裝獎勵：每 50 防禦 +1 速度（僅當穿齊四件時）。 */
    public int getSetBonusSpeed(Player player) {
        if (!isWearingFullSet(player)) return 0;
        int def = getEquipmentDefense(player);
        return (def / 50) * defeatTankConfig.getSpeedPer50Defense();
    }

    /**
     * Hypixel 風格：總 Speed 數值（基礎 100 + 裝備速度 + 套裝獎勵）。
     * 僅在穿齊堅壁四件時計算；否則回傳 null 表示不覆寫行走速度。
     */
    public Integer getTotalSpeedStat(Player player) {
        if (player == null || !isWearingFullSet(player)) return null;
        int base = 100;
        int gear = getEquipmentSpeed(player);
        int bonus = getSetBonusSpeed(player);
        return base + gear + bonus;
    }

    /** 來自堅壁套裝的 Speed 加減（穿齊四件時 = 裝備速度 + 套裝獎勵，否則 0）。用於與玩家 Speed 屬性相加。 */
    public int getSpeedModifierFromGear(Player player) {
        if (player == null || !isWearingFullSet(player)) return 0;
        return getEquipmentSpeed(player) + getSetBonusSpeed(player);
    }
}
