package maple.underground_state.skills.xp;

import maple.underground_state.skills.SkillProgressManager;
import maple.underground_state.skills.SkillRewardService;
import maple.underground_state.skills.SkillType;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public final class FishingXpService implements Listener {

    private static final int RAIN_BONUS_TENTHS = 2;
    private static final Map<Material, Integer> XP_TENTHS_BY_ITEM = buildXpTenthsByItem();

    private final SkillProgressManager skillProgressManager;
    private final SkillRewardService skillRewardService;
    private final CombatXpOverlayService xpOverlayService;

    public FishingXpService(@NotNull SkillProgressManager skillProgressManager,
                            @NotNull SkillRewardService skillRewardService,
                            @NotNull CombatXpOverlayService xpOverlayService) {
        this.skillProgressManager = skillProgressManager;
        this.skillRewardService = skillRewardService;
        this.xpOverlayService = xpOverlayService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }

        Player player = event.getPlayer();
        if (!canGainXp(player)) {
            return;
        }
        if (!(event.getCaught() instanceof Item item)) {
            return;
        }

        ItemStack stack = item.getItemStack();
        int awardTenths = resolveAwardTenths(stack);
        if (awardTenths <= 0) {
            return;
        }

        if (isRainingAtHook(event)) {
            awardTenths += RAIN_BONUS_TENTHS;
        }

        grantFishingXp(player, awardTenths);
    }

    private void grantFishingXp(@NotNull Player player, int awardTenths) {
        UUID playerId = player.getUniqueId();
        int carriedTenths = skillProgressManager.getFishingXpRemainderTenths(playerId);
        int totalTenths = carriedTenths + awardTenths;
        long wholeXp = totalTenths / 10L;
        int remainderTenths = totalTenths % 10;

        if (wholeXp > 0L) {
            skillRewardService.addSkillXp(playerId, SkillType.FISHING, wholeXp);
        }
        skillProgressManager.setFishingXpRemainderTenths(playerId, remainderTenths);

        long fishingXpWhole = skillProgressManager.getXp(playerId, SkillType.FISHING);
        long displayTotalTenths = fishingXpWhole * 10L + remainderTenths;
        xpOverlayService.showSkillOverlay(player, SkillType.FISHING, awardTenths, displayTotalTenths);
    }

    private static int resolveAwardTenths(@Nullable ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0;
        }

        if (isWaterBottle(stack)) {
            return 2;
        }
        return XP_TENTHS_BY_ITEM.getOrDefault(stack.getType(), 0);
    }

    private static boolean isWaterBottle(@NotNull ItemStack stack) {
        if (stack.getType() != Material.POTION || !(stack.getItemMeta() instanceof PotionMeta potionMeta)) {
            return false;
        }
        return potionMeta.getBasePotionType() == PotionType.WATER;
    }

    private static boolean isRainingAtHook(@NotNull PlayerFishEvent event) {
        Location location = event.getHook().getLocation();
        World world = location.getWorld();
        if (world == null || !world.hasStorm()) {
            return false;
        }
        int highestY = world.getHighestBlockYAt(location);
        return highestY <= location.getBlockY() + 1;
    }

    private static boolean canGainXp(@Nullable Player player) {
        return player != null
                && player.isOnline()
                && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR;
    }

    private static @NotNull Map<Material, Integer> buildXpTenthsByItem() {
        Map<Material, Integer> map = new EnumMap<>(Material.class);

        map.put(Material.COD, 10);
        map.put(Material.SALMON, 11);
        map.put(Material.TROPICAL_FISH, 12);
        map.put(Material.PUFFERFISH, 12);

        map.put(Material.LILY_PAD, 2);
        map.put(Material.BOWL, 2);
        map.put(Material.STICK, 2);
        map.put(Material.LEATHER, 3);
        map.put(Material.LEATHER_BOOTS, 3);
        map.put(Material.ROTTEN_FLESH, 2);
        map.put(Material.BONE, 3);
        map.put(Material.STRING, 2);
        map.put(Material.INK_SAC, 4);
        map.put(Material.TRIPWIRE_HOOK, 2);

        map.put(Material.ENCHANTED_BOOK, 35);
        map.put(Material.BOW, 30);
        map.put(Material.FISHING_ROD, 30);
        map.put(Material.NAME_TAG, 25);
        map.put(Material.SADDLE, 25);
        map.put(Material.NAUTILUS_SHELL, 20);

        return Map.copyOf(map);
    }
}
