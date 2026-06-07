package maple.underground_caves.enchantments;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * 自定義附魔基底：支援普通附魔（一般顏色）與終極附魔（粉色）。
 */
public abstract class CustomEnchantment {

    public enum EnchantType { NORMAL, ULTIMATE }

    private final String id;
    private final String displayName;
    private final int maxLevel;
    private final EnchantType enchantType;
    private final Set<Material> applicableMaterials;

    protected CustomEnchantment(String id, String displayName, int maxLevel, EnchantType enchantType, Set<Material> applicableMaterials) {
        this.id = id;
        this.displayName = displayName;
        this.maxLevel = maxLevel;
        this.enchantType = enchantType;
        this.applicableMaterials = applicableMaterials == null ? Set.of() : applicableMaterials;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public int getMaxLevel() { return maxLevel; }
    public EnchantType getEnchantType() { return enchantType; }
    public Set<Material> getApplicableMaterials() { return applicableMaterials; }

    /** 普通附魔用一般顏色，終極附魔用粉色 */
    public TextColor getLoreColor() {
        return enchantType == EnchantType.ULTIMATE
            ? TextColor.fromHexString("#FF55FF")
            : TextColor.fromHexString("#AAAAAA");
    }

    /** 該等級的效果描述（用於 lore） */
    public abstract String getDescription(int level);

    /** 是否適用於此物品 */
    public boolean appliesTo(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return applicableMaterials.isEmpty() || applicableMaterials.contains(item.getType());
    }

    public static final Set<Material> SWORDS = Set.of(
        Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD
    );
    public static final Set<Material> BOWS = Set.of(Material.BOW, Material.CROSSBOW);
    public static final Set<Material> BOOTS = Set.of(
        Material.LEATHER_BOOTS, Material.CHAINMAIL_BOOTS, Material.IRON_BOOTS, Material.GOLDEN_BOOTS, Material.DIAMOND_BOOTS, Material.NETHERITE_BOOTS
    );
    public static final Set<Material> ARMOR = Set.of(
        Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
        Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS,
        Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
        Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS,
        Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
        Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS
    );
    public static final Set<Material> WEAPONS = Set.of(
        Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
        Material.BOW, Material.CROSSBOW
    );
    public static final Set<Material> SWORD_AND_BOW = Set.of(
        Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
        Material.BOW, Material.CROSSBOW
    );
    public static final Set<Material> PICKAXES = Set.of(
        Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE, Material.GOLDEN_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE
    );
    public static final Set<Material> SWORD_AND_PICKAXE = Set.of(
        Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
        Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE, Material.GOLDEN_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE
    );
}
