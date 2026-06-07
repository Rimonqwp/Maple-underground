package maple.underground_state.skills.xp;

import maple.underground_state.UndergroundState;
import maple.underground_state.skills.AlchemyLevelDefinition;
import maple.underground_state.skills.AlchemyLevelTable;
import maple.underground_state.skills.CombatLevelDefinition;
import maple.underground_state.skills.CombatLevelTable;
import maple.underground_state.skills.EnchantingLevelDefinition;
import maple.underground_state.skills.EnchantingLevelTable;
import maple.underground_state.skills.FarmingLevelDefinition;
import maple.underground_state.skills.FarmingLevelTable;
import maple.underground_state.skills.FishingLevelDefinition;
import maple.underground_state.skills.FishingLevelTable;
import maple.underground_state.skills.ForagingLevelDefinition;
import maple.underground_state.skills.ForagingLevelTable;
import maple.underground_state.skills.MiningLevelDefinition;
import maple.underground_state.skills.MiningLevelTable;
import maple.underground_state.skills.SkillProgressManager;
import maple.underground_state.skills.SkillRewardService;
import maple.underground_state.skills.SkillType;
import maple.underground_state.stats.PlayerStatsData;
import maple.underground_state.stats.PlayerStatsRuntime;
import maple.underground_state.stats.PlayerStatsRuntimeExtension;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.GameMode;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatXpOverlayService implements Listener, PlayerStatsRuntimeExtension {

    private static final long DISPLAY_DURATION_MS = 2_000L;
    private static final NamespacedKey DUNGEON_MONSTER_KEY =
            new NamespacedKey("underground_caves", "dungeon_monster_entity_type");
    private static final NamespacedKey EXIT_ROOM_MOB_KEY =
            new NamespacedKey("underground_caves", "exit_room_mob");
    private static final int GRADIENT_START = 0xCAFFF3;
    private static final int GRADIENT_END = 0x51FFD9;
    private static final Map<EntityType, Integer> XP_TENTHS_BY_TYPE = buildXpTenthsByType();

    private final PlayerStatsRuntime runtime;
    private final SkillProgressManager skillProgressManager;
    private final SkillRewardService skillRewardService;
    private final Map<UUID, OverlayState> overlays = new ConcurrentHashMap<>();

    public CombatXpOverlayService(@NotNull UndergroundState plugin,
                                  @NotNull PlayerStatsRuntime runtime,
                                  @NotNull SkillProgressManager skillProgressManager,
                                  @NotNull SkillRewardService skillRewardService) {
        this.runtime = runtime;
        this.skillProgressManager = skillProgressManager;
        this.skillRewardService = skillRewardService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (!canGainXp(killer)) {
            return;
        }
        if (isDungeonTagged(entity)) {
            return;
        }

        Integer awardTenths = XP_TENTHS_BY_TYPE.get(entity.getType());
        if (awardTenths == null || awardTenths <= 0) {
            return;
        }

        UUID playerId = killer.getUniqueId();
        int carriedTenths = skillProgressManager.getCombatXpRemainderTenths(playerId);
        int totalTenths = carriedTenths + awardTenths;
        long wholeXp = totalTenths / 10L;
        int remainderTenths = totalTenths % 10;

        if (wholeXp > 0L) {
            skillRewardService.addSkillXp(playerId, SkillType.COMBAT, wholeXp);
        }
        skillProgressManager.setCombatXpRemainderTenths(playerId, remainderTenths);

        long combatXpWhole = skillProgressManager.getXp(playerId, SkillType.COMBAT);
        long displayTotalTenths = combatXpWhole * 10L + remainderTenths;
        showSkillOverlay(killer, SkillType.COMBAT, awardTenths, displayTotalTenths);
    }

    private static boolean canGainXp(@Nullable Player player) {
        return player != null
                && player.isOnline()
                && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        overlays.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public boolean renderCustomActionBar(@NotNull Player player,
                                         @NotNull PlayerStatsRuntime runtime,
                                         @NotNull PlayerStatsData data,
                                         double effectiveMaxHealth,
                                         double effectiveDefense,
                                         double effectiveMaxIntelligence) {
        OverlayState state = overlays.get(player.getUniqueId());
        if (state == null) {
            return false;
        }
        if (System.currentTimeMillis() > state.expireAtMs()) {
            overlays.remove(player.getUniqueId(), state);
            return false;
        }

        player.sendActionBar(buildOverlayLine(player, data, effectiveMaxHealth, effectiveMaxIntelligence, state));
        return true;
    }

    public void showSkillOverlay(@NotNull Player player,
                                 @NotNull SkillType skillType,
                                 int awardTenths,
                                 long totalXpTenths) {
        OverlayState state = new OverlayState(skillType, awardTenths, totalXpTenths, System.currentTimeMillis() + DISPLAY_DURATION_MS);
        overlays.put(player.getUniqueId(), state);
        sendOverlay(player, state);
    }

    private void sendOverlay(@NotNull Player player, @NotNull OverlayState state) {
        PlayerStatsData data = runtime.getManager().get(player);
        double maxHealth = runtime.getEffectiveMaxHealth(player);
        double maxIntelligence = runtime.getEffectiveMaxIntelligence(player);
        player.sendActionBar(buildOverlayLine(player, data, maxHealth, maxIntelligence, state));
    }

    private static boolean isDungeonTagged(@NotNull LivingEntity entity) {
        var pdc = entity.getPersistentDataContainer();
        return pdc.has(DUNGEON_MONSTER_KEY, PersistentDataType.STRING)
                || pdc.has(EXIT_ROOM_MOB_KEY, PersistentDataType.STRING);
    }

    private static @NotNull Component buildOverlayLine(@NotNull Player player,
                                                       @NotNull PlayerStatsData data,
                                                       double effectiveMaxHealth,
                                                       double effectiveMaxIntelligence,
                                                       @NotNull OverlayState state) {
        Component middle = buildXpSegment(player, state);
        if (shouldUseDarkAqua(player)) {
            return Component.text("HP ", NamedTextColor.RED)
                    .append(Component.text(formatCurrentMax(data.getCurrentHealth(), effectiveMaxHealth), NamedTextColor.WHITE))
                    .append(Component.text("   ", NamedTextColor.DARK_GRAY))
                    .append(middle)
                    .append(Component.text("   ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("INT ", NamedTextColor.AQUA))
                    .append(Component.text(formatCurrentMax(data.getCurrentIntelligence(), effectiveMaxIntelligence), NamedTextColor.WHITE))
                    .decoration(TextDecoration.BOLD, false);
        }

        return Component.text("❤ ", NamedTextColor.RED)
                .append(Component.text(formatCurrentMax(data.getCurrentHealth(), effectiveMaxHealth), NamedTextColor.WHITE))
                .append(Component.text("   ", NamedTextColor.DARK_GRAY))
                .append(middle)
                .append(Component.text("   ", NamedTextColor.DARK_GRAY))
                .append(Component.text("✎ ", NamedTextColor.AQUA))
                .append(Component.text(formatCurrentMax(data.getCurrentIntelligence(), effectiveMaxIntelligence), NamedTextColor.WHITE))
                .decoration(TextDecoration.BOLD, false);
    }

    private static @NotNull Component buildXpSegment(@NotNull Player player, @NotNull OverlayState state) {
        long totalWholeXp = state.totalXpTenths() / 10L;
        long requiredXp;

        if (state.skillType() == SkillType.MINING) {
            @Nullable MiningLevelDefinition nextDefinition = MiningLevelTable.nextDefinitionForXp(totalWholeXp);
            requiredXp = nextDefinition == null ? Math.max(0L, totalWholeXp) : nextDefinition.totalXpRequired();
        } else if (state.skillType() == SkillType.FORAGING) {
            @Nullable ForagingLevelDefinition nextDefinition = ForagingLevelTable.nextDefinitionForXp(totalWholeXp);
            requiredXp = nextDefinition == null ? Math.max(0L, totalWholeXp) : nextDefinition.totalXpRequired();
        } else if (state.skillType() == SkillType.FARMING) {
            @Nullable FarmingLevelDefinition nextDefinition = FarmingLevelTable.nextDefinitionForXp(totalWholeXp);
            requiredXp = nextDefinition == null ? Math.max(0L, totalWholeXp) : nextDefinition.totalXpRequired();
        } else if (state.skillType() == SkillType.FISHING) {
            @Nullable FishingLevelDefinition nextDefinition = FishingLevelTable.nextDefinitionForXp(totalWholeXp);
            requiredXp = nextDefinition == null ? Math.max(0L, totalWholeXp) : nextDefinition.totalXpRequired();
        } else if (state.skillType() == SkillType.ENCHANTING) {
            @Nullable EnchantingLevelDefinition nextDefinition = EnchantingLevelTable.nextDefinitionForXp(totalWholeXp);
            requiredXp = nextDefinition == null ? Math.max(0L, totalWholeXp) : nextDefinition.totalXpRequired();
        } else if (state.skillType() == SkillType.ALCHEMY) {
            @Nullable AlchemyLevelDefinition nextDefinition = AlchemyLevelTable.nextDefinitionForXp(totalWholeXp);
            requiredXp = nextDefinition == null ? Math.max(0L, totalWholeXp) : nextDefinition.totalXpRequired();
        } else {
            @Nullable CombatLevelDefinition nextDefinition = CombatLevelTable.nextDefinitionForXp(totalWholeXp);
            requiredXp = nextDefinition == null ? Math.max(0L, totalWholeXp) : nextDefinition.totalXpRequired();
        }

        String text = "+" + formatTenths(state.awardTenths()) + " " + skillXpLabel(state.skillType())
                + " (" + formatWhole(totalWholeXp) + "/" + formatWhole(requiredXp) + ")";
        if (shouldUseDarkAqua(player)) {
            return Component.text(text, NamedTextColor.DARK_AQUA);
        }
        return gradientText(text, GRADIENT_START, GRADIENT_END);
    }

    private static @NotNull String skillXpLabel(@NotNull SkillType skillType) {
        if (skillType == SkillType.FISHING) {
            return "\u9493\u9c7c\u7ecf\u9a8c";
        }
        if (skillType == SkillType.ENCHANTING) {
            return "\u9644\u9b54\u7ecf\u9a8c";
        }
        if (skillType == SkillType.ALCHEMY) {
            return "\u917f\u9020\u7ecf\u9a8c";
        }
        if (skillType == SkillType.EXPLORATION) {
            return "\u63a2\u7d22\u7ecf\u9a8c";
        }
        if (skillType == SkillType.CRAFTING) {
            return "\u5de5\u827a\u7ecf\u9a8c";
        }
        if (skillType == SkillType.SOCIAL) {
            return "\u793e\u4ea4\u7ecf\u9a8c";
        }
        return switch (skillType) {
            case MINING -> "挖矿经验";
            case FORAGING -> "伐木经验";
            case FARMING -> "耕作经验";
            case COMBAT -> "战斗经验";
            default -> skillType.name().toLowerCase(Locale.ROOT) + " xp";
        };
    }

    private static boolean shouldUseDarkAqua(@NotNull Player player) {
        String name = player.getName();
        return name != null && name.startsWith(".");
    }

    private static @NotNull Component gradientText(@NotNull String text, int startHex, int endHex) {
        if (text.isEmpty()) {
            return Component.empty();
        }
        if (text.length() == 1) {
            return Component.text(text, TextColor.color(startHex));
        }

        Component out = Component.empty();
        for (int index = 0; index < text.length(); index++) {
            double ratio = (double) index / (double) (text.length() - 1);
            int color = interpolateColor(startHex, endHex, ratio);
            out = out.append(Component.text(String.valueOf(text.charAt(index)), TextColor.color(color)));
        }
        return out;
    }

    private static int interpolateColor(int startHex, int endHex, double ratio) {
        int startR = (startHex >> 16) & 0xFF;
        int startG = (startHex >> 8) & 0xFF;
        int startB = startHex & 0xFF;
        int endR = (endHex >> 16) & 0xFF;
        int endG = (endHex >> 8) & 0xFF;
        int endB = endHex & 0xFF;

        int red = (int) Math.round(startR + (endR - startR) * ratio);
        int green = (int) Math.round(startG + (endG - startG) * ratio);
        int blue = (int) Math.round(startB + (endB - startB) * ratio);
        return (red << 16) | (green << 8) | blue;
    }

    private static @NotNull String formatTenths(int tenths) {
        return String.format(Locale.US, "%.1f", tenths / 10.0D);
    }

    private static @NotNull String formatWhole(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    private static @NotNull String formatNum(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-9D) {
            return String.format(Locale.US, "%.0f", value);
        }
        return String.format(Locale.US, "%.1f", value);
    }

    private static @NotNull String formatCurrentMax(double current, double max) {
        return formatNum(current) + "/" + formatNum(max);
    }

    private static @NotNull Map<EntityType, Integer> buildXpTenthsByType() {
        Map<EntityType, Integer> map = new EnumMap<>(EntityType.class);
        map.put(EntityType.ZOMBIE, 11);
        map.put(EntityType.SKELETON, 9);
        map.put(EntityType.SPIDER, 9);
        map.put(EntityType.CREEPER, 12);
        map.put(EntityType.CAVE_SPIDER, 10);
        map.put(EntityType.HUSK, 11);
        map.put(EntityType.DROWNED, 9);
        map.put(EntityType.STRAY, 10);
        map.put(EntityType.BOGGED, 10);
        map.put(EntityType.SLIME, 4);
        map.put(EntityType.WITCH, 6);
        map.put(EntityType.ENDERMAN, 3);
        map.put(EntityType.PHANTOM, 8);
        map.put(EntityType.SILVERFISH, 1);
        map.put(EntityType.ENDERMITE, 1);
        map.put(EntityType.PILLAGER, 6);
        map.put(EntityType.VINDICATOR, 8);
        map.put(EntityType.EVOKER, 10);
        map.put(EntityType.RAVAGER, 20);
        map.put(EntityType.ZOMBIFIED_PIGLIN, 3);
        map.put(EntityType.PIGLIN_BRUTE, 13);
        map.put(EntityType.BLAZE, 10);
        map.put(EntityType.MAGMA_CUBE, 5);
        map.put(EntityType.WITHER_SKELETON, 10);
        map.put(EntityType.GHAST, 10);
        map.put(EntityType.HOGLIN, 9);
        map.put(EntityType.ZOGLIN, 10);
        map.put(EntityType.GUARDIAN, 6);
        map.put(EntityType.ELDER_GUARDIAN, 40);
        map.put(EntityType.SHULKER, 10);
        map.put(EntityType.WARDEN, 100);
        map.put(EntityType.WITHER, 200);
        map.put(EntityType.ENDER_DRAGON, 400);
        return Map.copyOf(map);
    }

    private record OverlayState(@NotNull SkillType skillType, int awardTenths, long totalXpTenths, long expireAtMs) {
    }
}
