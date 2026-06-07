package maple.underground_state.skills.xp;

import maple.underground_state.skills.SkillProgressManager;
import maple.underground_state.skills.SkillRewardService;
import maple.underground_state.skills.SkillType;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class EnchantingXpService implements Listener {

    private static final NamespacedKey ENCHANTING_XP_MARK_KEY =
            new NamespacedKey("underground_state", "enchanting_xp_awarded");

    private final SkillProgressManager skillProgressManager;
    private final SkillRewardService skillRewardService;
    private final CombatXpOverlayService xpOverlayService;

    public EnchantingXpService(@NotNull SkillProgressManager skillProgressManager,
                               @NotNull SkillRewardService skillRewardService,
                               @NotNull CombatXpOverlayService xpOverlayService) {
        this.skillProgressManager = skillProgressManager;
        this.skillRewardService = skillRewardService;
        this.xpOverlayService = xpOverlayService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        if (!canGainXp(player)) {
            return;
        }
        if (event.getEnchantsToAdd().isEmpty()) {
            return;
        }

        ItemStack item = event.getItem();
        if (hasEnchantingXpMark(item)) {
            return;
        }

        int awardWholeXp = xpForCost(event.getExpLevelCost());
        if (awardWholeXp <= 0) {
            return;
        }

        markEnchantingXpAwarded(item);
        grantEnchantingXp(player, awardWholeXp);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnvilResultTake(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !canGainXp(player)) {
            return;
        }
        if (!(event.getView() instanceof AnvilView anvilView)) {
            return;
        }
        if (event.getRawSlot() != 2) {
            return;
        }

        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType() == Material.AIR) {
            return;
        }

        ItemStack left = anvilView.getTopInventory().getItem(0);
        ItemStack right = anvilView.getTopInventory().getItem(1);
        int awardWholeXp = resolveAnvilAward(left, right, result, anvilView.getRepairCost());
        if (awardWholeXp <= 0) {
            return;
        }

        grantEnchantingXp(player, awardWholeXp);
    }

    private void grantEnchantingXp(@NotNull Player player, int awardWholeXp) {
        UUID playerId = player.getUniqueId();
        skillRewardService.addSkillXp(playerId, SkillType.ENCHANTING, awardWholeXp);

        long enchantingXpWhole = skillProgressManager.getXp(playerId, SkillType.ENCHANTING);
        xpOverlayService.showSkillOverlay(player, SkillType.ENCHANTING, awardWholeXp * 10, enchantingXpWhole * 10L);
    }

    private static boolean canGainXp(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        return player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR;
    }

    private static boolean hasEnchantingXpMark(@NotNull ItemStack item) {
        if (!item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(ENCHANTING_XP_MARK_KEY, PersistentDataType.BYTE);
    }

    private static void markEnchantingXpAwarded(@NotNull ItemStack item) {
        item.editMeta(meta -> meta.getPersistentDataContainer().set(ENCHANTING_XP_MARK_KEY, PersistentDataType.BYTE, (byte) 1));
    }

    private static int resolveAnvilAward(@Nullable ItemStack left,
                                         @Nullable ItemStack right,
                                         @NotNull ItemStack result,
                                         int repairCost) {
        if (left == null || right == null || left.getType() == Material.AIR || right.getType() == Material.AIR) {
            return 0;
        }

        Map<Enchantment, Integer> leftEnchants = allEnchantLevels(left);
        Map<Enchantment, Integer> rightEnchants = allEnchantLevels(right);
        Map<Enchantment, Integer> resultEnchants = allEnchantLevels(result);
        if (resultEnchants.isEmpty()) {
            return 0;
        }
        if (!addsOrImprovesEnchantments(leftEnchants, resultEnchants)) {
            return 0;
        }

        if (isHighTierAnvilMerge(resultEnchants, repairCost)) {
            return 8;
        }
        if (isEnchantedBook(left) && isEnchantedBook(right)) {
            return 3;
        }
        if (isEnchantedBook(left) ^ isEnchantedBook(right)) {
            return 4;
        }
        if (!leftEnchants.isEmpty() && !rightEnchants.isEmpty()) {
            return 5;
        }
        return 0;
    }

    private static boolean addsOrImprovesEnchantments(@NotNull Map<Enchantment, Integer> sourceEnchants,
                                                      @NotNull Map<Enchantment, Integer> resultEnchants) {
        for (Map.Entry<Enchantment, Integer> entry : resultEnchants.entrySet()) {
            int before = sourceEnchants.getOrDefault(entry.getKey(), 0);
            if (entry.getValue() > before) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHighTierAnvilMerge(@NotNull Map<Enchantment, Integer> resultEnchants, int repairCost) {
        if (repairCost >= 20) {
            return true;
        }
        for (int level : resultEnchants.values()) {
            if (level >= 4) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEnchantedBook(@NotNull ItemStack item) {
        return item.getType() == Material.ENCHANTED_BOOK;
    }

    private static @NotNull Map<Enchantment, Integer> allEnchantLevels(@NotNull ItemStack item) {
        Map<Enchantment, Integer> enchants = new HashMap<>(item.getEnchantments());
        if (!item.hasItemMeta()) {
            return enchants;
        }
        ItemMeta meta = item.getItemMeta();
        enchants.putAll(meta.getEnchants());
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            enchants.putAll(storageMeta.getStoredEnchants());
        }
        return enchants;
    }

    private static int xpForCost(int expLevelCost) {
        if (expLevelCost >= 30) {
            return 14;
        }
        if (expLevelCost >= 20) {
            return 9;
        }
        if (expLevelCost >= 10) {
            return 5;
        }
        if (expLevelCost >= 1) {
            return 2;
        }
        return 0;
    }
}
