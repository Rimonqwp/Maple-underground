package maple.underground_caves.gear;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.ToIntFunction;

/**
 * 昇華 · 蒼界裝備：在物品 PDC 寫入生命／防禦數值，穿戴時併入有效血量與 ActionBar 防禦。
 */
public final class SublimationGearManager {

    private static final String PDC_TAG = "sublimation_azur";
    private static final String PDC_HEALTH = "sublimation_azur_health";
    private static final String PDC_DEFENSE = "sublimation_azur_defense";

    private final NamespacedKey keyTag;
    private final NamespacedKey keyHealth;
    private final NamespacedKey keyDefense;

    public SublimationGearManager(@NotNull JavaPlugin plugin) {
        this.keyTag = new NamespacedKey(plugin, PDC_TAG);
        this.keyHealth = new NamespacedKey(plugin, PDC_HEALTH);
        this.keyDefense = new NamespacedKey(plugin, PDC_DEFENSE);
    }

    /** 昇華成功時寫入，與 lore 上「生命」「防禦」一致。 */
    public void writeGearStats(@NotNull ItemMeta meta, int health, int defense) {
        var pdc = meta.getPersistentDataContainer();
        pdc.set(keyTag, PersistentDataType.BYTE, (byte) 1);
        pdc.set(keyHealth, PersistentDataType.INTEGER, health);
        pdc.set(keyDefense, PersistentDataType.INTEGER, defense);
    }

    public boolean isSublimationArmor(@Nullable ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return false;
        Byte b = stack.getItemMeta().getPersistentDataContainer().get(keyTag, PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }

    /** 單件昇華裝備 PDC 生命（非昇華為 0）。 */
    public int getSublimationHealthOnPiece(@Nullable ItemStack stack) {
        return isSublimationArmor(stack) ? readInt(stack, keyHealth) : 0;
    }

    /** 單件昇華裝備 PDC 防禦（非昇華為 0）。 */
    public int getSublimationDefenseOnPiece(@Nullable ItemStack stack) {
        return isSublimationArmor(stack) ? readInt(stack, keyDefense) : 0;
    }

    public int getBonusHealth(@Nullable Player player) {
        return sumArmorSlots(player, this::healthOnPiece);
    }

    public int getBonusDefense(@Nullable Player player) {
        return sumArmorSlots(player, this::defenseOnPiece);
    }

    private int healthOnPiece(ItemStack s) {
        return isSublimationArmor(s) ? readInt(s, keyHealth) : 0;
    }

    private int defenseOnPiece(ItemStack s) {
        return isSublimationArmor(s) ? readInt(s, keyDefense) : 0;
    }

    private static int readInt(ItemStack stack, NamespacedKey key) {
        if (!stack.hasItemMeta()) return 0;
        Integer v = stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return v != null ? Math.max(0, v) : 0;
    }

    private static int sumArmorSlots(@Nullable Player player, ToIntFunction<ItemStack> perPiece) {
        if (player == null) return 0;
        var inv = player.getInventory();
        int t = 0;
        t += perPiece.applyAsInt(nullToAir(inv.getHelmet()));
        t += perPiece.applyAsInt(nullToAir(inv.getChestplate()));
        t += perPiece.applyAsInt(nullToAir(inv.getLeggings()));
        t += perPiece.applyAsInt(nullToAir(inv.getBoots()));
        return t;
    }

    private static ItemStack nullToAir(@Nullable ItemStack s) {
        return s != null ? s : new ItemStack(Material.AIR);
    }
}
