package maple.underground_caves.gear;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 昇華裝備在鐵砧與保護附魔書等合成時，更新預覽／結果的防禦數值、分組附魔 lore 與 PDC（每級保護 +5 防禦）。
 */
public final class SublimationAnvilPrepareListener implements Listener {

    private final SublimationGearManager gear;
    private final SublimationWorkbenchManager workbench;

    public SublimationAnvilPrepareListener(@NotNull SublimationGearManager gear, @NotNull SublimationWorkbenchManager workbench) {
        this.gear = gear;
        this.workbench = workbench;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory inv)) return;
        ItemStack left = inv.getFirstItem();
        if (left == null || left.getType().isAir()) return;
        if (!gear.isSublimationArmor(left)) return;

        Enchantment prot = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("protection"));
        if (prot != null) {
            ItemStack right = inv.getSecondItem();
            if (right != null && !right.getType().isAir()
                    && right.getType() == Material.ENCHANTED_BOOK
                    && right.getEnchantmentLevel(prot) > 0) {
                int implied = workbench.impliedProtectionLevelFromPdc(left);
                if (implied >= prot.getMaxLevel()) {
                    event.setResult(null);
                    return;
                }
            }
        }

        ItemStack result = event.getResult();
        if (result == null || result.getType().isAir()) return;
        if (left.getType() != result.getType()) return;

        ItemStack patch = result.clone();
        if (!workbench.refreshSublimationAnvilOutput(patch, left)) return;
        event.setResult(patch);
    }
}
