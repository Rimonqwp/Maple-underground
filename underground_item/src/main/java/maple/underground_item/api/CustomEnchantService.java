package maple.underground_item.api;

import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Map;

public interface CustomEnchantService {

    Collection<CustomEnchantView> getAll();

    Map<CustomEnchantView, Integer> getEnchants(ItemStack item);
}
