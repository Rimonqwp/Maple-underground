package maple.underground_caves.dungeon;

import maple.underground_caves.Test_dungeons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTeleportEvent;

import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.InputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DungeonMonsters implements Listener {
    public record ManualSummonResult(boolean success, String message) {}

    enum MonsterType {
        MINIBOSS,
        ZOMBIE_TANK,
        ZOMBIE_WARRIOR,
        SKELETON,
        WITCH,
        ENDERMAN
    }

    private record MonsterStats(double hp, double damage, double defense, int level) {}

    private record MonsterVariant(String id, String displayName, MonsterStats stats, String skinTexture, boolean baby) {}

    private record MonsterTypeConfig(String displayName, EntityType entityType, List<MonsterVariant> variants, boolean baby) {}

    private record MonsterDef(MonsterType type, int x, int y, int z, boolean target, Boolean baby, Double hp, Double damage, Double defense, String variantId, String skinTexture) {}
    private record SummonAddStats(double hp, double damage, double defense) {}

    private record EditSession(String templateName, String worldName, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                               boolean placeAsTarget, ItemStack[] savedContents, int savedHeldSlot) {}

    private record MonsterEditHolder(UUID armorStandId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record LastBossChestHolder(String treasureName, String floor, long cost, List<ItemStack> rewards) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final String GLOBAL_CONFIG_NAME = "monsters.yml";
    private static final String TEXTURE_BASE = "http://textures.minecraft.net/texture/";
    private static final String GUI_TITLE = "怪物点编辑";
    private static final String MHF_ENDERMAN_TEXTURE_ID = "97438bd156261957ca944389dd79f0f02ca94583f0f420c1ff9a9e2c594a14";
    private static final Pattern BASE_ROOM_1X1_TEMPLATE_PATTERN = Pattern.compile("^base_room_1x1_\\d+$", Pattern.CASE_INSENSITIVE);
    private static volatile String mhfEndermanTextureId;
    private static volatile long mhfEndermanTextureCheckedAtMs;

    private final Test_dungeons plugin;
    private final NamespacedKey markerKey;
    private final NamespacedKey markerTypeKey;
    private final NamespacedKey markerTargetKey;
    private final NamespacedKey markerHpKey;
    private final NamespacedKey markerDamageKey;
    private final NamespacedKey markerDefenseKey;
    private final NamespacedKey markerVariantKey;
    private final NamespacedKey markerSkinKey;
    private final NamespacedKey markerBabyKey;

    private final NamespacedKey toolKey;
    private final NamespacedKey toolTypeKey;
    private final NamespacedKey toolToggleKey;

    private final NamespacedKey entityKey;
    private final NamespacedKey entityGroupKey;
    private final NamespacedKey entityTargetKey;
    private final NamespacedKey entityDamageKey;
    private final NamespacedKey entityTypeKey;
    private final NamespacedKey entityVariantKey;
    private final NamespacedKey entityLevelKey;
    private final NamespacedKey entityOriginalLocationKey;

    private final NamespacedKey entityNametagKey;
    private final NamespacedKey endermanSkullKey;
    private final NamespacedKey endermanSkullGroupKey;
    private final NamespacedKey endermanSkullTargetKey;
    private final NamespacedKey endermanSkullHpKey;
    private final NamespacedKey endermanSkullDamageKey;
    private final NamespacedKey endermanSkullDefenseKey;
    private final NamespacedKey endermanSkullVariantKey;
    private final NamespacedKey endermanSkullLevelKey;
    /** last_boss 第一階段 BOSS 標記，value="1" 表示死亡時在原地生成第二隻；"2" 表示第二隻，死亡時生成五組箱子 */
    private final NamespacedKey lastBossPhaseKey;
    private final NamespacedKey lastBossFearChanceKey;
    private final NamespacedKey lastBossFearCooldownMsKey;
    private final NamespacedKey lastBossFearDurationTicksKey;
    private final NamespacedKey lastBossFearDamagePerSecKey;
    private final NamespacedKey lastBossSlamChanceKey;
    private final NamespacedKey lastBossSlamCooldownMsKey;
    private final NamespacedKey lastBossSlamWindupTicksKey;
    private final NamespacedKey lastBossSlamRadiusKey;
    private final NamespacedKey lastBossSlamDamageKey;
    private final NamespacedKey lastBossSummonChanceKey;
    private final NamespacedKey lastBossSummonCooldownMsKey;
    private final NamespacedKey lastBossSummonCountKey;
    private final NamespacedKey lastBossSummonHpKey;
    private final NamespacedKey lastBossSummonDamageKey;
    private final NamespacedKey lastBossSummonDefenseKey;
    private final NamespacedKey lastBossSummonTypesKey;
    private final NamespacedKey lastBossSummonTypeStatsKey;
    private final NamespacedKey lastBossSummonedAddKey;
    /** last_boss 房間內箱子盔甲架標記，value=chestId（wood/gold/diamond_left/...）供細調與 dump */
    private final NamespacedKey lastBossChestKey;
    private final Map<UUID, LastBossChestHolder> openLastBossChestGui = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastBossFearBossCooldownUntilMs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastBossSlamCooldownUntilMs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastBossSummonCooldownUntilMs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> minibossLeechCooldownUntilMs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> minibossHexCooldownUntilMs = new ConcurrentHashMap<>();
    private final Map<UUID, FearParalysisState> fearParalysisStates = new ConcurrentHashMap<>();
    /** 已排程或已生成第一隻 last_boss 的 world:floor，避免傳送後重複生成 */
    private final Set<String> lastBossFirstBossScheduledOrSpawned = ConcurrentHashMap.newKeySet();
    private static final long LAST_BOSS_FIRST_SPAWN_DELAY_TICKS = 100L; // 5 秒
    private static final double DEFAULT_LAST_BOSS_FEAR_CHANCE = 0.25;
    private static final long DEFAULT_LAST_BOSS_FEAR_COOLDOWN_MS = 12000L;
    private static final int DEFAULT_LAST_BOSS_FEAR_DURATION_TICKS = 100;
    private static final double DEFAULT_LAST_BOSS_FEAR_DAMAGE_PER_SEC = 1.0;
    private static final double DEFAULT_LAST_BOSS_SLAM_CHANCE = 0.25;
    private static final long DEFAULT_LAST_BOSS_SLAM_COOLDOWN_MS = 14000L;
    private static final int DEFAULT_LAST_BOSS_SLAM_WINDUP_TICKS = 32;
    private static final double DEFAULT_LAST_BOSS_SLAM_RADIUS = 4.5;
    private static final double DEFAULT_LAST_BOSS_SLAM_DAMAGE = 22.0;
    private static final double DEFAULT_LAST_BOSS_SUMMON_CHANCE = 0.30;
    private static final long DEFAULT_LAST_BOSS_SUMMON_COOLDOWN_MS = 15000L;
    private static final int DEFAULT_LAST_BOSS_SUMMON_COUNT = 2;
    private static final double DEFAULT_LAST_BOSS_SUMMON_HP = 220.0;
    private static final double DEFAULT_LAST_BOSS_SUMMON_DAMAGE = 16.0;
    private static final double DEFAULT_LAST_BOSS_SUMMON_DEFENSE = 10.0;
    private static final String DEFAULT_LAST_BOSS_SUMMON_TYPES_CSV = "ZOMBIE,SKELETON,SPIDER,HUSK,SILVERFISH";
    private static final double MINIBOSS_LEECH_CHANCE = 0.25;
    private static final long MINIBOSS_LEECH_COOLDOWN_MS = 8000L;
    private static final double MINIBOSS_LEECH_HEAL_RATIO = 0.08;
    private static final int MINIBOSS_LEECH_SPEED_TICKS = 40;
    private static final double MINIBOSS_HEX_CHANCE = 0.22;
    private static final long MINIBOSS_HEX_COOLDOWN_MS = 9000L;
    private static final int MINIBOSS_HEX_TICKS = 80;
    private static final long FEAR_PARALYSIS_REAPPLY_GRACE_MS = 100L;
    // 統一主題色：Boss=紅橘，Miniboss=綠青
    private static final TextColor BOSS_THEME_FROM = TextColor.color(255, 92, 92);
    private static final TextColor BOSS_THEME_TO = TextColor.color(255, 170, 60);
    private static final TextColor MINIBOSS_THEME_FROM = TextColor.color(70, 235, 150);
    private static final TextColor MINIBOSS_THEME_TO = TextColor.color(70, 210, 255);
    private double minibossLeechChance = MINIBOSS_LEECH_CHANCE;
    private long minibossLeechCooldownMs = MINIBOSS_LEECH_COOLDOWN_MS;
    private double minibossLeechHealRatio = MINIBOSS_LEECH_HEAL_RATIO;
    private int minibossLeechSpeedTicks = MINIBOSS_LEECH_SPEED_TICKS;
    private double minibossHexChance = MINIBOSS_HEX_CHANCE;
    private long minibossHexCooldownMs = MINIBOSS_HEX_COOLDOWN_MS;
    private int minibossHexTicks = MINIBOSS_HEX_TICKS;

    private final Map<UUID, EditSession> editSessions = new HashMap<>();
    private final Map<String, RunState> runsByWorld = new HashMap<>();
    /** Exit 房（exit_room_f1/f2/f3）18 隻全滅後標記，key = worldName + ":" + cellX + ":" + cellY，供地圖顯示綠✓ */
    private final Set<String> exitRoomsCleared = ConcurrentHashMap.newKeySet();
    private RoomRewardManager roomRewardManager;

    public void setRoomRewardManager(RoomRewardManager rm) {
        this.roomRewardManager = rm;
    }

    private boolean hasClaimedLastBossChest(Player player) {
        if (player == null || player.getWorld() == null) return false;
        RunState run = runsByWorld.get(player.getWorld().getName());
        return run != null && run.lastBossChestClaimedPlayers.contains(player.getUniqueId());
    }

    private void markLastBossChestClaimed(Player player) {
        if (player == null || player.getWorld() == null) return;
        RunState run = runsByWorld.computeIfAbsent(player.getWorld().getName(), RunState::new);
        run.lastBossChestClaimedPlayers.add(player.getUniqueId());
    }

    private void sendAlreadyClaimedLastBossChestMessage(Player player) {
        if (player == null) return;
        player.sendMessage(Component.text("你本次副本已經開啟過一個最終寶箱，不能再開啟其他寶箱。", NamedTextColor.RED));
    }
    private final Map<UUID, BukkitRunnable> endermanReturnTasks = new HashMap<>();
    private final Map<UUID, Long> endermanAllowTeleportUntilMs = new HashMap<>();

    private final Map<MonsterType, MonsterTypeConfig> typeConfigs = new EnumMap<>(MonsterType.class);
    private long globalConfigLoadedAt = 0L;

    public DungeonMonsters(Test_dungeons plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "dungeon_monster_marker");
        this.markerTypeKey = new NamespacedKey(plugin, "dungeon_monster_type");
        this.markerTargetKey = new NamespacedKey(plugin, "dungeon_monster_target");
        this.markerHpKey = new NamespacedKey(plugin, "dungeon_monster_hp");
        this.markerDamageKey = new NamespacedKey(plugin, "dungeon_monster_damage");
        this.markerDefenseKey = new NamespacedKey(plugin, "dungeon_monster_defense");
        this.markerVariantKey = new NamespacedKey(plugin, "dungeon_monster_variant");
        this.markerSkinKey = new NamespacedKey(plugin, "dungeon_monster_skin");
        this.markerBabyKey = new NamespacedKey(plugin, "dungeon_monster_baby");

        this.toolKey = new NamespacedKey(plugin, "dungeon_monster_tool");
        this.toolTypeKey = new NamespacedKey(plugin, "dungeon_monster_tool_type");
        this.toolToggleKey = new NamespacedKey(plugin, "dungeon_monster_tool_toggle");

        this.entityKey = new NamespacedKey(plugin, "dungeon_monster_entity");
        this.entityGroupKey = new NamespacedKey(plugin, "dungeon_monster_group");
        this.entityTargetKey = new NamespacedKey(plugin, "dungeon_monster_is_target");
        this.entityDamageKey = new NamespacedKey(plugin, "dungeon_monster_entity_damage");
        this.entityTypeKey = new NamespacedKey(plugin, "dungeon_monster_entity_type");
        this.entityVariantKey = new NamespacedKey(plugin, "dungeon_monster_entity_variant");
        this.entityLevelKey = new NamespacedKey(plugin, "dungeon_monster_entity_level");
        this.entityOriginalLocationKey = new NamespacedKey(plugin, "dungeon_monster_original_location");
        this.entityNametagKey = new NamespacedKey(plugin, "dungeon_monster_nametag");

        this.endermanSkullKey = new NamespacedKey(plugin, "dungeon_enderman_skull");
        this.endermanSkullGroupKey = new NamespacedKey(plugin, "dungeon_enderman_skull_group");
        this.endermanSkullTargetKey = new NamespacedKey(plugin, "dungeon_enderman_skull_is_target");
        this.endermanSkullHpKey = new NamespacedKey(plugin, "dungeon_enderman_skull_hp");
        this.endermanSkullDamageKey = new NamespacedKey(plugin, "dungeon_enderman_skull_damage");
        this.endermanSkullDefenseKey = new NamespacedKey(plugin, "dungeon_enderman_skull_defense");
        this.endermanSkullVariantKey = new NamespacedKey(plugin, "dungeon_enderman_skull_variant");
        this.endermanSkullLevelKey = new NamespacedKey(plugin, "dungeon_enderman_skull_level");
        this.lastBossPhaseKey = new NamespacedKey(plugin, "last_boss_phase");
        this.lastBossFearChanceKey = new NamespacedKey(plugin, "last_boss_fear_chance");
        this.lastBossFearCooldownMsKey = new NamespacedKey(plugin, "last_boss_fear_cd_ms");
        this.lastBossFearDurationTicksKey = new NamespacedKey(plugin, "last_boss_fear_duration_ticks");
        this.lastBossFearDamagePerSecKey = new NamespacedKey(plugin, "last_boss_fear_dps");
        this.lastBossSlamChanceKey = new NamespacedKey(plugin, "last_boss_slam_chance");
        this.lastBossSlamCooldownMsKey = new NamespacedKey(plugin, "last_boss_slam_cd_ms");
        this.lastBossSlamWindupTicksKey = new NamespacedKey(plugin, "last_boss_slam_windup_ticks");
        this.lastBossSlamRadiusKey = new NamespacedKey(plugin, "last_boss_slam_radius");
        this.lastBossSlamDamageKey = new NamespacedKey(plugin, "last_boss_slam_damage");
        this.lastBossSummonChanceKey = new NamespacedKey(plugin, "last_boss_summon_chance");
        this.lastBossSummonCooldownMsKey = new NamespacedKey(plugin, "last_boss_summon_cd_ms");
        this.lastBossSummonCountKey = new NamespacedKey(plugin, "last_boss_summon_count");
        this.lastBossSummonHpKey = new NamespacedKey(plugin, "last_boss_summon_hp");
        this.lastBossSummonDamageKey = new NamespacedKey(plugin, "last_boss_summon_damage");
        this.lastBossSummonDefenseKey = new NamespacedKey(plugin, "last_boss_summon_defense");
        this.lastBossSummonTypesKey = new NamespacedKey(plugin, "last_boss_summon_types");
        this.lastBossSummonTypeStatsKey = new NamespacedKey(plugin, "last_boss_summon_type_stats");
        this.lastBossSummonedAddKey = new NamespacedKey(plugin, "last_boss_summoned_add");
        this.lastBossChestKey = new NamespacedKey(plugin, "last_boss_chest_id");
    }

    void reloadGlobalConfig() {
        globalConfigLoadedAt = 0L;
        typeConfigs.clear();
        maybeLoadGlobalConfig();
    }

    void beginRun(Test_dungeons.DungeonSnapshot snapshot) {
        if (snapshot == null) return;
        if (snapshot.worldName() == null) return;
        String wn = snapshot.worldName();
        exitRoomsCleared.removeIf(k -> k != null && k.startsWith(wn + ":"));
        RunState prev = runsByWorld.put(wn, new RunState(wn));
        if (prev != null) prev.stop();
        RunState run = runsByWorld.get(wn);
        if (run != null) ensureNameTask(run);
    }

    void registerRoomGroup(String worldName, String groupKey, String templateName, int cellX, int cellY) {
        if (worldName == null || groupKey == null || templateName == null) return;
        RunState run = runsByWorld.get(worldName);
        if (run == null) return;
        run.groups.computeIfAbsent(groupKey, k -> new GroupState(groupKey, templateName)).cells.add(packCell(cellX, cellY));
    }

    /** 檢查該房間是否已擊殺所有目標怪物（帶星標記）；含 Exit 房 18 隻全滅標記。 */
    public boolean isRoomMonstersCleared(String worldName, int cellX, int cellY) {
        if (worldName == null) return false;
        if (exitRoomsCleared.contains(worldName + ":" + cellX + ":" + cellY)) return true;
        RunState run = runsByWorld.get(worldName);
        if (run == null) return false;
        int packed = packCell(cellX, cellY);
        for (GroupState g : run.groups.values()) {
            if (g != null && g.cells.contains(packed) && g.cleared) return true;
        }
        return false;
    }

    /** Exit 房 18 隻標記怪全滅後呼叫，使地圖該格顯示綠✓ */
    public void markExitRoomCleared(String worldName, int cellX, int cellY) {
        if (worldName == null) return;
        exitRoomsCleared.add(worldName + ":" + cellX + ":" + cellY);
    }

    public List<String> listMonsterTypesForSummon() {
        maybeLoadGlobalConfig();
        List<String> out = new ArrayList<>();
        for (MonsterType type : MonsterType.values()) {
            out.add(type.name().toLowerCase(Locale.ROOT));
        }
        out.sort(String::compareToIgnoreCase);
        return out;
    }

    public List<String> listMonsterVariantsForSummon(String typeInput) {
        maybeLoadGlobalConfig();
        MonsterType type = parseMonsterTypeForInput(typeInput);
        if (type == null) return List.of();
        MonsterTypeConfig cfg = typeConfigs.get(type);
        if (cfg == null || cfg.variants == null || cfg.variants.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        for (MonsterVariant v : cfg.variants) {
            if (v == null || v.id == null || v.id.isBlank()) continue;
            out.add(v.id);
        }
        out.sort(String::compareToIgnoreCase);
        return out;
    }

    public ManualSummonResult summonMobForPlayer(Player player, String typeInput, String variantInput, boolean target) {
        if (player == null || !player.isOnline()) return new ManualSummonResult(false, "玩家不存在或不在線。");
        World world = player.getWorld();
        if (world == null) return new ManualSummonResult(false, "目前世界不可用。");

        maybeLoadGlobalConfig();
        MonsterType type = parseMonsterTypeForInput(typeInput);
        if (type == null) {
            String available = String.join(", ", listMonsterTypesForSummon());
            return new ManualSummonResult(false, "未知怪物類型: " + typeInput + "（可用: " + available + "）");
        }

        MonsterTypeConfig cfg = typeConfigs.get(type);
        EntityType entityType = cfg != null ? cfg.entityType : EntityType.ZOMBIE;
        if (entityType == null || !entityType.isAlive()) entityType = EntityType.ZOMBIE;

        String preferredVariant = variantInput != null && !variantInput.isBlank() ? variantInput.trim() : null;
        MonsterVariant variant = resolveVariant(cfg, preferredVariant);
        String resolvedVariant = variant != null && variant.id != null && !variant.id.isBlank() ? variant.id : null;
        if (preferredVariant != null && cfg != null && (variant == null || variant.id == null || !variant.id.equalsIgnoreCase(preferredVariant))) {
            return new ManualSummonResult(false, "未知變體: " + preferredVariant);
        }
        MonsterStats stats = resolveStats(cfg, variant);

        Location safe = findNearbySafe(player.getLocation(), 2, 16);
        Location spawnAt = (safe != null ? safe : player.getLocation()).clone().add(0.0, 0.2, 0.0);

        if (type == MonsterType.ENDERMAN) {
            ArmorStand skull = spawnEndermanSkull(world, spawnAt, "manual_summon", target, stats.hp(), stats.damage(), stats.defense(), stats.level(), resolvedVariant);
            if (skull == null) return new ManualSummonResult(false, "召喚失敗：無法建立終界骷髏體。");
            return new ManualSummonResult(true, "已召喚 " + type.name().toLowerCase(Locale.ROOT) + (resolvedVariant != null ? " 變體 " + resolvedVariant : "") + (target ? "（目標怪）" : ""));
        }

        LivingEntity spawned = spawnMob(world, spawnAt, entityType);
        if (spawned == null) return new ManualSummonResult(false, "召喚失敗：無法在當前位置生成生物。");

        spawned.setRemoveWhenFarAway(false);
        applyStats(spawned, stats.hp(), stats.damage(), stats.defense());

        boolean baby = variant != null ? variant.baby : (cfg != null && cfg.baby);
        if (spawned instanceof Zombie z) {
            z.setBaby(baby);
        }

        String skin = normalizeTextureId(variant != null ? variant.skinTexture : null);
        if (skin != null && type == MonsterType.MINIBOSS) {
            applyHeadTexture(spawned, skin);
        }

        PersistentDataContainer pdc = spawned.getPersistentDataContainer();
        pdc.set(entityKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(entityGroupKey, PersistentDataType.STRING, "manual_summon");
        pdc.set(entityTargetKey, PersistentDataType.BYTE, target ? (byte) 1 : (byte) 0);
        pdc.set(entityDamageKey, PersistentDataType.DOUBLE, stats.damage());
        pdc.set(entityTypeKey, PersistentDataType.STRING, type.name());
        if (resolvedVariant != null && !resolvedVariant.isBlank()) {
            pdc.set(entityVariantKey, PersistentDataType.STRING, resolvedVariant);
        } else {
            pdc.remove(entityVariantKey);
        }
        pdc.set(entityLevelKey, PersistentDataType.INTEGER, stats.level());

        Component text = updateSpawnedMonsterName(spawned, type, cfg, variant, target, stats.level());
        if (text != null) {
            spawned.customName(text);
            spawned.setCustomNameVisible(true);
        }
        return new ManualSummonResult(true, "已召喚 " + type.name().toLowerCase(Locale.ROOT) + (resolvedVariant != null ? " 變體 " + resolvedVariant : "") + (target ? "（目標怪）" : ""));
    }

    /** Exit 房 18 隻全滅時由 ExitRoomManager 呼叫：發送「房间通关」；giveReward 為 true 時才觸發 roomRewardManager.onRoomCleared（Exit 房傳 false，不掉落那三樣獎勵，改為只生成 2 個祝福盔甲架）。 */
    public void notifyExitRoomCleared(World world, Location at, int cellX, int cellY, boolean giveReward) {
        if (world == null) return;
        sendToNearby(world, at.getBlockX(), at.getBlockY(), at.getBlockZ(), Component.text("目标怪物已击杀，房间通关", NamedTextColor.GREEN));
        if (!giveReward || roomRewardManager == null || plugin == null) return;
        String groupKey = getGroupKeyForCell(world.getName(), cellX, cellY);
        Test_dungeons.DungeonSnapshot snapshot = plugin.getLastDungeon();
        boolean isMainPath = snapshot != null && snapshot.dungeon() != null
                && Test_dungeons.isAnyCellOnMainPath(snapshot.dungeon(), Set.of(packCell(cellX, cellY)));
        roomRewardManager.onRoomCleared(world, at != null ? at : new Location(world, 0, 0, 0), groupKey != null ? groupKey : "", isMainPath);
    }

    /** 取得該 cell 所屬的 groupKey（用於 Exit 房通知完成時傳給 roomRewardManager）。 */
    public String getGroupKeyForCell(String worldName, int cellX, int cellY) {
        if (worldName == null) return null;
        RunState run = runsByWorld.get(worldName);
        if (run == null) return null;
        int packed = packCell(cellX, cellY);
        for (Map.Entry<String, GroupState> e : run.groups.entrySet()) {
            if (e.getValue() != null && e.getValue().cells.contains(packed)) return e.getKey();
        }
        return null;
    }

    /** 將 Exit 房生成的實體註冊為該房間的標記怪：寫入 PDC、加入 targetEntityIds，全滅時會觸發「目标怪物已击杀，房间通关」與 group.cleared。 */
    public void markEntityAsExitRoomTarget(World world, int cellX, int cellY, LivingEntity entity) {
        if (world == null || entity == null) return;
        String groupKey = getGroupKeyForCell(world.getName(), cellX, cellY);
        if (groupKey == null) return;
        RunState run = runsByWorld.get(world.getName());
        if (run == null) return;
        GroupState group = run.groups.get(groupKey);
        if (group == null) return;

        entity.getPersistentDataContainer().set(entityKey, PersistentDataType.BYTE, (byte) 1);
        entity.getPersistentDataContainer().set(entityGroupKey, PersistentDataType.STRING, groupKey);
        entity.getPersistentDataContainer().set(entityTargetKey, PersistentDataType.BYTE, (byte) 1);
        group.targetEntityIds.add(entity.getUniqueId());
        group.hadAnyTarget = true;
        if (group.worldName == null || group.baseX == 0 && group.baseZ == 0) {
            group.worldName = world.getName();
            org.bukkit.Location loc = entity.getLocation();
            group.baseX = loc.getBlockX();
            group.baseY = loc.getBlockY();
            group.baseZ = loc.getBlockZ();
        }
    }

    void beginTemplateTestRun(World world, String groupKey, String templateName) {
        if (world == null) return;
        if (groupKey == null || groupKey.isBlank()) return;
        if (templateName == null || templateName.isBlank()) return;
        RunState run = runsByWorld.computeIfAbsent(world.getName(), wn -> new RunState(wn));
        run.groups.computeIfAbsent(groupKey, k -> new GroupState(groupKey, templateName)).cells.add(packCell(0, 0));
        ensureNameTask(run);
    }

    void spawnGroupMonsters(World world, Location baseOrigin, StructureTemplate.Rotation rotation, int sizeX, int sizeZ, String groupKey, String templateName) {
        spawnGroupMonsters(world, baseOrigin, rotation, sizeX, sizeZ, groupKey, templateName, -1, -1, -1, -1);
    }

    /** 可選 excluded 區（L 形缺口）：exMinX>=0 時在 startPresenceScan 前設定，避免玩家在缺口內觸發整間怪 */
    void spawnGroupMonsters(World world, Location baseOrigin, StructureTemplate.Rotation rotation, int sizeX, int sizeZ, String groupKey, String templateName, int exMinX, int exMaxX, int exMinZ, int exMaxZ) {
        if (world == null || baseOrigin == null || rotation == null) return;
        if (groupKey == null || templateName == null) return;
        RunState run = runsByWorld.get(world.getName());
        if (run == null) return;
        GroupState group = run.groups.get(groupKey);
        if (group == null) return;

        maybeLoadGlobalConfig();
        List<MonsterDef> defs = loadTemplateMonsters(templateName);
        boolean isTrapRoom01 = "trap_room_01".equalsIgnoreCase(templateName.trim());
        if (defs.isEmpty() && !isTrapRoom01) return;

        int baseX = baseOrigin.getBlockX();
        int baseY = baseOrigin.getBlockY();
        int baseZ = baseOrigin.getBlockZ();
        group.setPlacement(world.getName(), baseX, baseY, baseZ, rotation, sizeX, sizeZ, defs);
        if (exMinX >= 0 && exMaxX >= exMinX && exMinZ >= 0 && exMaxZ >= exMinZ) {
            group.setExcludedRegion(exMinX, exMaxX, exMinZ, exMaxZ);
        }
        if (isTrapRoom01) {
            monsterLog("trap_room_01 spawnGroupMonsters groupKey=" + groupKey
                    + " base=" + baseX + "," + baseY + "," + baseZ
                    + " rot=" + rotation.name()
                    + " size=" + sizeX + "x" + sizeZ
                    + " boundsX=" + group.minX + ".." + group.maxX
                    + " boundsZ=" + group.minZ + ".." + group.maxZ);
        }
        startPresenceScan(run, group);
    }

    boolean startEdit(Player player, String templateName, Location pos1, Location pos2) {
        if (player == null || templateName == null || templateName.isBlank()) return false;
        if (pos1 == null || pos2 == null || pos1.getWorld() == null || !Objects.equals(pos1.getWorld(), pos2.getWorld())) return false;

        World world = pos1.getWorld();
        if (world == null) return false;

        maybeLoadGlobalConfig();

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        exitEdit(player);

        PlayerInventory inv = player.getInventory();
        ItemStack[] saved = inv.getContents();
        int held = inv.getHeldItemSlot();
        inv.clear();
        inv.setItem(0, toolItem(Material.PLAYER_HEAD, "Miniboss", MonsterType.MINIBOSS));
        inv.setItem(1, toolItem(Material.ZOMBIE_SPAWN_EGG, "坦克僵尸", MonsterType.ZOMBIE_TANK));
        inv.setItem(2, toolItem(Material.ZOMBIE_SPAWN_EGG, "战士僵尸", MonsterType.ZOMBIE_WARRIOR));
        inv.setItem(3, toolItem(Material.SKELETON_SPAWN_EGG, "小白", MonsterType.SKELETON));
        inv.setItem(4, toolItem(Material.WITCH_SPAWN_EGG, "女巫", MonsterType.WITCH));
        inv.setItem(5, toolItem(Material.ENDERMAN_SPAWN_EGG, "末影人", MonsterType.ENDERMAN));
        inv.setItem(8, toggleItem(false));

        String normalized = templateName.toLowerCase(Locale.ROOT);
        editSessions.put(player.getUniqueId(), new EditSession(normalized, world.getName(),
                minX, minY, minZ,
                maxX, maxY, maxZ,
                false,
                saved, held));

        clearMarkersInBox(world, minX, minY, minZ, maxX, maxY, maxZ);
        List<MonsterDef> existing = loadTemplateMonsters(normalized);
        for (MonsterDef def : existing) {
            if (def == null) continue;
            int wx = minX + def.x();
            int wy = minY + def.y();
            int wz = minZ + def.z();

            ArmorStand as = spawnEditMarker(world, wx + 0.5, wy, wz + 0.5, def.type(), def.target());
            if (as == null) continue;

            PersistentDataContainer pdc = as.getPersistentDataContainer();
            if (def.variantId() != null && !def.variantId().isBlank()) {
                pdc.set(markerVariantKey, PersistentDataType.STRING, def.variantId());
            } else {
                pdc.remove(markerVariantKey);
            }
            if (def.skinTexture() != null && !def.skinTexture().isBlank()) {
                pdc.set(markerSkinKey, PersistentDataType.STRING, def.skinTexture());
            } else {
                pdc.remove(markerSkinKey);
            }
            if (def.hp() != null) pdc.set(markerHpKey, PersistentDataType.DOUBLE, def.hp());
            if (def.damage() != null) pdc.set(markerDamageKey, PersistentDataType.DOUBLE, def.damage());
            if (def.defense() != null) pdc.set(markerDefenseKey, PersistentDataType.DOUBLE, def.defense());
            if (def.baby() != null) setMarkerBaby(as, def.baby());
            updateMarkerName(as);
        }

        player.sendActionBar(Component.text("怪物点编辑: 目前放置=非目标", NamedTextColor.GRAY));
        return true;
    }

    boolean saveEdit(Player player) {
        if (player == null) return false;
        EditSession session = editSessions.get(player.getUniqueId());
        if (session == null) return false;
        World world = Bukkit.getWorld(session.worldName());
        if (world == null) return false;

        List<Map<String, Object>> list = new ArrayList<>();
        for (Entity e : world.getEntitiesByClass(ArmorStand.class)) {
            if (!(e instanceof ArmorStand as)) continue;
            if (!isMarker(as)) continue;
            Location l = as.getLocation();
            int bx = l.getBlockX();
            int by = l.getBlockY();
            int bz = l.getBlockZ();
            if (bx < session.minX() || bx > session.maxX()) continue;
            if (by < session.minY() || by > session.maxY()) continue;
            if (bz < session.minZ() || bz > session.maxZ()) continue;

            MonsterType type = getMarkerType(as);
            if (type == null) continue;

            Map<String, Object> m = new HashMap<>();
            m.put("type", type.name());
            m.put("x", bx - session.minX());
            m.put("y", by - session.minY());
            m.put("z", bz - session.minZ());
            m.put("target", isMarkerTarget(as));
            if (isMarkerBaby(as)) m.put("baby", true);

            Double hp = getMarkerDouble(as, markerHpKey);
            Double dmg = getMarkerDouble(as, markerDamageKey);
            Double def = getMarkerDouble(as, markerDefenseKey);
            String variant = getMarkerString(as, markerVariantKey);
            String skin = getMarkerString(as, markerSkinKey);
            if (hp != null) m.put("hp", hp);
            if (dmg != null) m.put("damage", dmg);
            if (def != null) m.put("defense", def);
            if (variant != null && !variant.isBlank()) m.put("variant", variant);
            if (skin != null && !skin.isBlank()) m.put("skin", skin);
            list.add(m);
        }

        saveTemplateMonsters(session.templateName(), list);
        exitEdit(player);
        return true;
    }

    void exitEdit(Player player) {
        if (player == null) return;
        EditSession session = editSessions.remove(player.getUniqueId());
        if (session == null) return;
        PlayerInventory inv = player.getInventory();
        inv.setContents(session.savedContents());
        inv.setHeldItemSlot(Math.max(0, Math.min(8, session.savedHeldSlot())));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEditPlace(PlayerInteractEvent event) {
        if (event == null) return;
        Player player = event.getPlayer();
        if (player == null) return;
        EditSession session = editSessions.get(player.getUniqueId());
        if (session == null) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        MonsterType type = toolType(event.getItem());
        if (type == null) return;

        Location loc = event.getClickedBlock().getLocation();
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        if (!session.worldName().equals(loc.getWorld() != null ? loc.getWorld().getName() : null)) return;

        if (bx < session.minX() || bx > session.maxX()
                || by < session.minY() || by > session.maxY()
                || bz < session.minZ() || bz > session.maxZ()) {
            event.setCancelled(true);
            player.sendMessage(Component.text("不在选区内", NamedTextColor.RED));
            return;
        }

        boolean target = session.placeAsTarget();
        spawnEditMarker(loc.getWorld(), bx + 0.5, by, bz + 0.5, type, target);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEditToggle(PlayerInteractEvent event) {
        if (event == null) return;
        Player player = event.getPlayer();
        if (player == null) return;
        EditSession session = editSessions.get(player.getUniqueId());
        if (session == null) return;
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        if (!isToggleItem(event.getItem())) return;
        boolean next = !session.placeAsTarget();

        PlayerInventory inv = player.getInventory();
        inv.setItem(8, toggleItem(next));
        editSessions.put(player.getUniqueId(), new EditSession(session.templateName(), session.worldName(),
                session.minX(), session.minY(), session.minZ(),
                session.maxX(), session.maxY(), session.maxZ(),
                next,
                session.savedContents(), session.savedHeldSlot()));

        player.sendActionBar(Component.text("怪物点编辑: 目前放置=" + (next ? "目标" : "非目标"),
                next ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onLastBossChestOpen(PlayerInteractAtEntityEvent event) {
        if (event == null || !(event.getRightClicked() instanceof ArmorStand as)) return;
        Player player = event.getPlayer();
        if (player == null) return;
        String chestId = as.getPersistentDataContainer().get(lastBossChestKey, PersistentDataType.STRING);
        if (chestId == null || chestId.isEmpty()) return;
        String normalizedChestId = ChestLootManager.normalizeChestId(chestId);
        if (normalizedChestId != null && !normalizedChestId.equalsIgnoreCase(chestId)) {
            as.getPersistentDataContainer().set(lastBossChestKey, PersistentDataType.STRING, normalizedChestId);
            chestId = normalizedChestId;
        }
        String treasureName = ChestLootManager.getTreasureNameByChestId(chestId);
        if (treasureName == null) return;
        Test_dungeons.DungeonSnapshot snapshot = plugin.getLastDungeon();
        if (snapshot == null || !player.getWorld().getName().equals(snapshot.worldName())) {
            player.sendMessage(Component.text("請在地牢內開啟寶箱。", NamedTextColor.RED));
            event.setCancelled(true);
            return;
        }
        maple.underground_caves.dungeon.ChestLootManager lootMgr = plugin.getChestLootManager();
        if (lootMgr == null) return;
        maple.underground_caves.dungeon.ChestLootManager.FloorLootConfig floorConfig = lootMgr.getFloorConfig(treasureName, snapshot.floor());
        if (floorConfig == null) {
            player.sendMessage(Component.text("此樓層尚未設定該寶箱戰利品。", NamedTextColor.RED));
            event.setCancelled(true);
            return;
        }
        if (hasClaimedLastBossChest(player)) {
            sendAlreadyClaimedLastBossChestMessage(player);
            event.setCancelled(true);
            return;
        }
        // 只要該箱在當前樓層有 GUI 配置，就打開 GUI（灰玻璃底 + 固定/隨機槽）
        if ((floorConfig.guiFixed() != null && !floorConfig.guiFixed().isEmpty())
                || (floorConfig.guiPool() != null && !floorConfig.guiPool().isEmpty())) {
            openChestGui(player, floorConfig, lootMgr, snapshot.floor(), treasureName);
            event.setCancelled(true);
            return;
        }
        java.util.List<String> selected = lootMgr.rollSelectedSlots(floorConfig);
        long cost = lootMgr.computeTotalCost(floorConfig, selected);
        if (!lootMgr.tryCharge(player, cost)) {
            player.sendMessage(Component.text("餘額不足，需要 " + cost + "。", NamedTextColor.RED));
            event.setCancelled(true);
            return;
        }
        java.util.List<ItemStack> rewards = lootMgr.getRewardsForSelectedSlots(floorConfig, selected);
        lootMgr.giveRewards(player, rewards);
        markLastBossChestClaimed(player);
        String displayName = lootMgr.getDisplayName(treasureName);
        player.sendMessage(Component.text("已開啟 " + displayName + "：花費 " + cost + "。", NamedTextColor.GREEN));
        event.setCancelled(true);
    }

    private void openChestGui(Player player, ChestLootManager.FloorLootConfig floorConfig, ChestLootManager lootMgr, String floor, String treasureName) {
        String title = lootMgr.getDisplayName(treasureName);
        if (title == null || title.isBlank()) title = treasureName;
        Inventory inv = Bukkit.createInventory(new LastBossChestHolder(treasureName, floor, floorConfig.basePrice(), new ArrayList<>()), 54, Component.text(title, NamedTextColor.GOLD));
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        if (bgMeta != null) {
            bgMeta.displayName(Component.text(" "));
            bg.setItemMeta(bgMeta);
        }
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, bg);

        // ID:4 箱子頭顱
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        if (head.getItemMeta() instanceof SkullMeta skullMeta) {
            DungeonSecrets.applySkullTextureToMeta(skullMeta, getChestTextureByTreasureName(treasureName), "DungeonMonsters.chest_gui." + treasureName);
            skullMeta.displayName(Component.text(title, NamedTextColor.YELLOW));
            skullMeta.lore(List.of(
                    Component.text("洞窟戰利品箱。", NamedTextColor.GRAY),
                    Component.text("可開出固定回聲與隨機額外獎勵。", NamedTextColor.GRAY)
            ));
            head.setItemMeta(skullMeta);
        }
        inv.setItem(4, head);

        Map<Integer, ChestLootManager.SlotEntry> rolledEntries = lootMgr.rollGuiEntries(floorConfig);
        Map<Integer, ItemStack> guiItems = new LinkedHashMap<>();
        Map<String, Set<String>> usedSetSlotsBySetId = new LinkedHashMap<>();
        long guiCost = floorConfig.basePrice();
        for (var e : rolledEntries.entrySet()) {
            ChestLootManager.SlotEntry entry = e.getValue();
            if (entry == null) continue;
            guiCost += Math.max(0L, entry.price());
            guiCost += Math.max(0L, entry.extraPrice());
            ItemStack resolved = lootMgr.resolveItem(entry.itemStr(), usedSetSlotsBySetId);
            if (resolved != null && !resolved.getType().isAir()) {
                guiItems.put(e.getKey(), resolved);
            }
        }
        List<ItemStack> rolledRewards = new ArrayList<>();
        for (int slot : List.of(20, 22, 24)) {
            ItemStack it = guiItems.get(slot);
            if (it != null && !it.getType().isAir()) {
                inv.setItem(slot, it.clone());
                rolledRewards.add(it.clone());
            }
        }

        // ID:31 領取
        ItemStack claim = new ItemStack(Material.CHEST, 1);
        ItemMeta cm = claim.getItemMeta();
        if (cm != null) {
            cm.displayName(Component.text("領取戰利品", NamedTextColor.GREEN));
            cm.lore(List.of(
                    Component.text("開箱價格: " + guiCost, NamedTextColor.GRAY),
                    Component.text("點擊後將扣費並發放上方戰利品。", NamedTextColor.GRAY),
                    Component.text("請先確認背包空間是否足夠。", NamedTextColor.DARK_GRAY)
            ));
            claim.setItemMeta(cm);
        }
        inv.setItem(31, claim);

        // ID:49 關閉
        ItemStack close = new ItemStack(Material.BARRIER, 1);
        ItemMeta closeMeta = close.getItemMeta();
        if (closeMeta != null) {
            closeMeta.displayName(Component.text("關閉頁面", NamedTextColor.RED));
            close.setItemMeta(closeMeta);
        }
        inv.setItem(49, close);

        player.openInventory(inv);
        openLastBossChestGui.put(player.getUniqueId(), new LastBossChestHolder(treasureName, floor, guiCost, rolledRewards));
    }

    private String getChestTextureByTreasureName(String treasureName) {
        return switch (treasureName == null ? "" : treasureName.toLowerCase(Locale.ROOT)) {
            case "silver" -> "c0c44d1a8cb4d7acbf86122acbd9fcfaf9dbe4a4edf389a887b24ce432a0844a";
            case "gold" -> "c3bdbaedd7d6444e79aa8222f8981240204cc3cc1c9655118687618db8cec";
            case "platinum" -> "215fff8d8e8dadc26df471b6c619267dfd2746723cc225c7b3bbf2f11e9aa2b9";
            case "red" -> "2c6233d57c95e85e11d4dbdc8ba821252d14d5bfafccd4ebfa6ac27079ab35f7";
            case "orange" -> "322d4be1abcf3832c916191d24f9607bf194eff8dfbf3b9520bd97240e7c8";
            default -> "3f19bdfdfaa39949da0667f4a89f36426ba8692aef51fc6051a9802327da7"; // bronze
        };
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEditInteractEntity(PlayerInteractAtEntityEvent event) {
        if (event == null) return;
        Player player = event.getPlayer();
        if (player == null) return;
        EditSession session = editSessions.get(player.getUniqueId());
        if (session == null) return;
        if (!(event.getRightClicked() instanceof ArmorStand as)) return;
        if (!isMarker(as)) return;

        Inventory inv = Bukkit.createInventory(new MonsterEditHolder(as.getUniqueId()), 27, Component.text(GUI_TITLE, NamedTextColor.YELLOW));
        fillMonsterEditInventory(inv, as);
        player.openInventory(inv);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEditDamageMarker(EntityDamageByEntityEvent event) {
        if (event == null) return;
        if (!(event.getEntity() instanceof ArmorStand as)) return;
        if (!isMarker(as)) return;
        if (!(event.getDamager() instanceof Player player)) return;
        EditSession session = editSessions.get(player.getUniqueId());
        if (session == null) return;
        event.setCancelled(true);
        if (player.isSneaking()) {
            as.remove();
            player.sendMessage(Component.text("已删除怪物点", NamedTextColor.GRAY));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMonsterEditClick(InventoryClickEvent event) {
        if (event == null) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (top == null) return;
        if (top.getHolder() instanceof LastBossChestHolder holder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot == 49) {
                player.closeInventory();
                openLastBossChestGui.remove(player.getUniqueId());
                return;
            }
            if (slot == 31) {
                var lootMgr = plugin.getChestLootManager();
                if (lootMgr == null) return;
                if (hasClaimedLastBossChest(player)) {
                    sendAlreadyClaimedLastBossChestMessage(player);
                    player.closeInventory();
                    openLastBossChestGui.remove(player.getUniqueId());
                    return;
                }
                if (!lootMgr.tryCharge(player, holder.cost())) {
                    player.sendMessage(Component.text("餘額不足，需要 " + holder.cost() + "。", NamedTextColor.RED));
                    return;
                }
                lootMgr.giveRewards(player, holder.rewards());
                markLastBossChestClaimed(player);
                player.sendMessage(Component.text("已領取戰利品，花費 " + holder.cost() + "。", NamedTextColor.GREEN));
                player.closeInventory();
                openLastBossChestGui.remove(player.getUniqueId());
            }
            return;
        }
        if (!(top.getHolder() instanceof MonsterEditHolder holder)) return;
        event.setCancelled(true);

        UUID asId = holder.armorStandId();
        if (asId == null) return;
        World world = player.getWorld();
        ArmorStand as = world != null ? findArmorStand(world, asId) : null;
        if (as == null || !as.isValid() || as.isDead()) {
            player.closeInventory();
            return;
        }
        if (!isMarker(as)) {
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) return;

        if (slot == 10) {
            setMarkerTarget(as, !isMarkerTarget(as));
        } else if (slot == 11) {
            setMarkerBaby(as, !isMarkerBaby(as));
            updateMarkerName(as);
        } else if (slot == 12) {
            cycleMarkerType(as, -1);
        } else if (slot == 13) {
            cycleMarkerType(as, +1);
        } else if (slot == 14) {
            cycleMarkerVariant(as);
        } else if (slot == 16) {
            clearMarkerOverrides(as);
        } else if (slot == 18) {
            as.remove();
            player.closeInventory();
            return;
        } else if (slot == 2 || slot == 3 || slot == 4 || slot == 5) {
            adjustMarkerStat(as, StatKind.HP, slot == 2 ? -100 : slot == 3 ? -10 : slot == 4 ? 10 : 100);
        } else if (slot == 6 || slot == 7 || slot == 8 || slot == 9) {
            adjustMarkerStat(as, StatKind.DAMAGE, slot == 6 ? -5 : slot == 7 ? -1 : slot == 8 ? 1 : 5);
        } else if (slot == 20 || slot == 21 || slot == 22 || slot == 23) {
            adjustMarkerStat(as, StatKind.DEFENSE, slot == 20 ? -5 : slot == 21 ? -1 : slot == 22 ? 1 : 5);
        }

        fillMonsterEditInventory(top, as);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMonsterEditDrag(InventoryDragEvent event) {
        if (event == null) return;
        Inventory top = event.getView().getTopInventory();
        if (top == null) return;
        if (top.getHolder() instanceof LastBossChestHolder) {
            event.setCancelled(true);
            return;
        }
        if (!(top.getHolder() instanceof MonsterEditHolder)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMonsterEditClose(InventoryCloseEvent event) {
        if (event == null) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory inv = event.getInventory();
        if (inv == null) return;
        if (inv.getHolder() instanceof LastBossChestHolder) {
            openLastBossChestGui.remove(player.getUniqueId());
            return;
        }
        if (!(inv.getHolder() instanceof MonsterEditHolder)) return;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event == null) return;
        Player player = event.getPlayer();
        if (player == null) return;
        exitEdit(player);
        clearFearParalysis(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMonsterDeath(EntityDeathEvent event) {
        if (event == null) return;
        LivingEntity ent = event.getEntity();
        if (ent == null) return;
        PersistentDataContainer pdc = ent.getPersistentDataContainer();
        lastBossFearBossCooldownUntilMs.remove(ent.getUniqueId());
        lastBossSlamCooldownUntilMs.remove(ent.getUniqueId());
        lastBossSummonCooldownUntilMs.remove(ent.getUniqueId());
        minibossLeechCooldownUntilMs.remove(ent.getUniqueId());
        minibossHexCooldownUntilMs.remove(ent.getUniqueId());
        String lastBossPhase = pdc.get(lastBossPhaseKey, PersistentDataType.STRING);
        if ("1".equals(lastBossPhase)) {
            Location at = ent.getLocation();
            if (at != null && at.getWorld() != null) spawnLastBossBossAt(at.getWorld(), at, "F1_last_boss_2", false);
            return;
        }
        if ("2".equals(lastBossPhase)) {
            spawnLastBossChestsOnSecondBossDeath(ent.getWorld());
            return;
        }
        Byte b = pdc.get(entityKey, PersistentDataType.BYTE);
        if (b == null || b != (byte) 1) return;
        String groupKey = pdc.get(entityGroupKey, PersistentDataType.STRING);
        Byte isTarget = pdc.get(entityTargetKey, PersistentDataType.BYTE);
        if (groupKey == null) return;
        RunState run = runsByWorld.get(ent.getWorld().getName());
        if (run == null) return;
        GroupState group = run.groups.get(groupKey);
        if (group == null) return;
        if (plugin != null && plugin.getDungeonScoreboard() != null && plugin.isDungeonWorld(ent.getWorld().getName())) {
            boolean skipSilverfishTrap = ent.getType() == EntityType.SILVERFISH && group.templateName != null
                    && "trap_room_01".equalsIgnoreCase(group.templateName.trim());
            if (!skipSilverfishTrap) {
                plugin.getDungeonScoreboard().recordMobKill(ent.getWorld().getName());
            }
        }
        if (ent.getType() == EntityType.SILVERFISH && group.templateName != null && "trap_room_01".equalsIgnoreCase(group.templateName.trim())) {
            String cause = ent.getLastDamageCause() != null ? ent.getLastDamageCause().getCause().name() : "null";
            String d = ent.getWorld() != null && ent.getWorld().getDifficulty() != null ? ent.getWorld().getDifficulty().name() : "null";
            monsterLog("trap_room_01 died id=" + ent.getUniqueId()
                    + " at=" + ent.getLocation().getBlockX() + "," + ent.getLocation().getBlockY() + "," + ent.getLocation().getBlockZ()
                    + " cause=" + cause
                    + " difficulty=" + d
                    + " hp=" + (int) Math.ceil(Math.max(0.0, ent.getHealth()))
                    + " maxHp=" + (int) Math.ceil(Math.max(0.0, ent.getMaxHealth())));
        }
        UUID nametagId = group.monsterToNametag.remove(ent.getUniqueId());
        if (nametagId != null) {
            group.nametagHiddenFrom.remove(nametagId);
            Entity nametag = ent.getWorld().getEntity(nametagId);
            if (nametag != null) nametag.remove();
        }
        group.spawnedEntityIds.remove(ent.getUniqueId());
        if (ent instanceof Enderman) {
            BukkitRunnable task = endermanReturnTasks.remove(ent.getUniqueId());
            if (task != null) task.cancel();
            endermanAllowTeleportUntilMs.remove(ent.getUniqueId());
        }
        if (isTarget != null && isTarget == (byte) 1) {
            group.targetEntityIds.remove(ent.getUniqueId());
            if (!group.cleared && group.hadAnyTarget && group.targetEntityIds.isEmpty()) {
                group.cleared = true;
                sendToNearby(ent.getWorld(), group.baseX, group.baseY, group.baseZ, Component.text("目标怪物已击杀，房间通关", NamedTextColor.GREEN));
                Location deathLoc = ent.getLocation();
                if (roomRewardManager != null && plugin != null) {
                    Test_dungeons.DungeonSnapshot snapshot = plugin.getLastDungeon();
                    boolean isMainPath = snapshot != null && snapshot.dungeon() != null
                            && Test_dungeons.isAnyCellOnMainPath(snapshot.dungeon(), group.cells);
                    roomRewardManager.onRoomCleared(ent.getWorld(), deathLoc, group.groupKey, isMainPath);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSilverfishChangeBlock(EntityChangeBlockEvent event) {
        if (event == null) return;
        if (event.getEntityType() != EntityType.SILVERFISH) return;
        if (!(event.getEntity() instanceof LivingEntity ent)) return;
        PersistentDataContainer pdc = ent.getPersistentDataContainer();
        Byte b = pdc.get(entityKey, PersistentDataType.BYTE);
        if (b == null || b != (byte) 1) return;
        String groupKey = pdc.get(entityGroupKey, PersistentDataType.STRING);
        if (groupKey == null) return;
        RunState run = runsByWorld.get(ent.getWorld().getName());
        if (run == null) return;
        GroupState group = run.groups.get(groupKey);
        if (group == null || group.templateName == null) return;
        if (!"trap_room_01".equalsIgnoreCase(group.templateName.trim())) return;
        event.setCancelled(true);
    }

    private void startPresenceScan(RunState run, GroupState group) {
        if (plugin == null || !plugin.isEnabled()) return;
        if (run == null || group == null) return;
        if (group.presenceTask != null) {
            group.presenceTask.cancel();
            group.presenceTask = null;
        }
        boolean isTrapRoom01 = group.templateName != null && "trap_room_01".equalsIgnoreCase(group.templateName.trim());
        if (isTrapRoom01) {
            monsterLog("trap_room_01 presenceScanStart groupKey=" + group.groupKey
                    + " world=" + group.worldName
                    + " base=" + group.baseX + "," + group.baseY + "," + group.baseZ
                    + " boundsX=" + group.minX + ".." + group.maxX
                    + " boundsZ=" + group.minZ + ".." + group.maxZ);
        }
        group.presenceTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (group.spawned || group.cleared) {
                    if (isTrapRoom01) {
                        monsterLog("trap_room_01 presenceScanStop groupKey=" + group.groupKey
                                + " spawned=" + group.spawned
                                + " cleared=" + group.cleared);
                    }
                    cancel();
                    group.presenceTask = null;
                    return;
                }
                World world = Bukkit.getWorld(run.worldName);
                if (world == null) return;
                for (Player p : world.getPlayers()) {
                    if (p == null) continue;
                    if (!insideRoom(group, p.getLocation())) continue;
                    int px = p.getLocation().getBlockX();
                    int pz = p.getLocation().getBlockZ();
                    if (group.isInExcludedRegion(px, pz)) continue;
                    if (isTrapRoom01) {
                        Location l = p.getLocation();
                        monsterLog("trap_room_01 presenceScanTrigger groupKey=" + group.groupKey
                                + " player=" + p.getName()
                                + " loc=" + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ());
                    }
                    spawnForGroup(world, group);
                    group.spawned = true;
                    cancel();
                    group.presenceTask = null;
                    break;
                }
            }
        };
        group.presenceTask.runTaskTimer(plugin, 5L, 10L);
    }

    private void ensureNameTask(RunState run) {
        if (plugin == null || !plugin.isEnabled()) return;
        if (run == null) return;
        if (run.nameTask != null) return;
        run.nameTask = new BukkitRunnable() {
            @Override
            public void run() {
                maybeLoadGlobalConfig();
                World world = Bukkit.getWorld(run.worldName);
                if (world == null) return;
                List<Player> players = world.getPlayers();
                for (GroupState group : run.groups.values()) {
                    if (group == null) continue;
                    processEndermanSkulls(world, group, players);
                    if (group.spawnedEntityIds.isEmpty()) continue;

                    for (Iterator<UUID> it = group.spawnedEntityIds.iterator(); it.hasNext(); ) {
                        UUID id = it.next();
                        if (id == null) {
                            it.remove();
                            continue;
                        }
                        Entity e = world.getEntity(id);
                        if (!(e instanceof LivingEntity le) || !le.isValid() || le.isDead()) {
                            it.remove();
                            group.targetEntityIds.remove(id);
                            UUID ntId = group.monsterToNametag.remove(id);
                            if (ntId != null) {
                                group.nametagHiddenFrom.remove(ntId);
                                Entity nt = world.getEntity(ntId);
                                if (nt != null) nt.remove();
                            }
                            continue;
                        }

                        Component text = updateSpawnedMonsterName(le);
                        if (text == null) continue;
                        le.customName(Component.empty());

                        UUID nametagId = group.monsterToNametag.get(id);
                        if (nametagId == null) {
                            spawnNametagForMonster(world, group, le, text);
                            nametagId = group.monsterToNametag.get(id);
                        }
                        Entity nametagEntity = nametagId != null ? world.getEntity(nametagId) : null;
                        if (nametagEntity instanceof ArmorStand nametag) {
                            nametag.teleport(le.getLocation().add(0, 1, 0));
                            if (!Objects.equals(nametag.customName(), text)) {
                                nametag.customName(text);
                            }
                            Set<UUID> hiddenFrom = group.nametagHiddenFrom.computeIfAbsent(nametagId, k -> new HashSet<>());
                            for (Player p : players != null ? players : List.<Player>of()) {
                                if (p == null || !p.isOnline()) continue;
                                if (p.getWorld() != world) continue;
                                UUID pid = p.getUniqueId();
                                boolean shouldSee;
                                try {
                                    shouldSee = p.hasLineOfSight(le);
                                } catch (Throwable ignored) {
                                    shouldSee = false;
                                }
                                boolean currentlyHidden = hiddenFrom.contains(pid);
                                if (shouldSee && currentlyHidden) {
                                    try {
                                        p.showEntity(plugin, nametag);
                                        hiddenFrom.remove(pid);
                                    } catch (Throwable ignored) {}
                                } else if (!shouldSee && !currentlyHidden) {
                                    try {
                                        p.hideEntity(plugin, nametag);
                                        hiddenFrom.add(pid);
                                    } catch (Throwable ignored) {}
                                }
                            }
                        }
                    }
                }
            }
        };
        run.nameTask.runTaskTimer(plugin, 1L, 1L);
    }

    /** 地牢怪物移動：有追逐目標時可跨房；無目標時僅限房間內，阻擋移動後引導怪物往房間內自主尋路，避免卡門口 */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDungeonMonsterMove(EntityMoveEvent event) {
        if (event == null || !event.hasChangedPosition()) return;
        LivingEntity ent = event.getEntity();
        PersistentDataContainer pdc = ent.getPersistentDataContainer();
        if (pdc.get(entityKey, PersistentDataType.BYTE) == null || pdc.get(entityKey, PersistentDataType.BYTE) != (byte) 1) return;
        String groupKey = pdc.get(entityGroupKey, PersistentDataType.STRING);
        if (groupKey == null) return;
        RunState run = runsByWorld.get(ent.getWorld().getName());
        if (run == null) return;
        GroupState group = run.groups.get(groupKey);
        if (group == null) return;
        Location to = event.getTo();
        if (to == null || insideRoom(group, to)) return;
        // 有追逐目標時允許跨房；無目標時取消移動，並引導怪物往房間內尋路，避免卡門口
        if (ent instanceof Mob mob && mob.getTarget() != null) return;
        event.setCancelled(true);
        if (ent instanceof Mob m) {
            try {
                m.getPathfinder().stopPathfinding();
            } catch (Throwable ignored) {}
            // 延遲一 tick 後設定新目標，讓怪物往房間內部自主尋路
            schedulePathfindIntoRoom(plugin, m, group);
        }
    }

    /** 排程：下一 tick 讓怪物往房間內部尋路，避免卡在門口 */
    private void schedulePathfindIntoRoom(Test_dungeons pl, Mob mob, GroupState group) {
        if (pl == null || mob == null || group == null || !mob.isValid()) return;
        Location inside = randomPointInsideRoom(mob.getWorld(), group, mob.getLocation());
        if (inside == null) return;
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!mob.isValid() || mob.isDead()) return;
            try {
                mob.getPathfinder().moveTo(inside, 0.5);
            } catch (Throwable ignored) {}
        }, 1L);
    }

    /** 在房間內取得一隨機點（偏向中心），用於引導怪物往內移動；避開 excluded 區域（L 形缺口） */
    private static Location randomPointInsideRoom(World world, GroupState group, Location fallback) {
        if (world == null || group == null) return fallback;
        double ty = fallback != null ? fallback.getY() : (group.baseY + 1.0);
        for (int attempt = 0; attempt < 20; attempt++) {
            int cx = (group.minX + group.maxX) / 2;
            int cz = (group.minZ + group.maxZ) / 2;
            int offsetX = ThreadLocalRandom.current().nextInt(-4, 5);
            int offsetZ = ThreadLocalRandom.current().nextInt(-4, 5);
            int tx = Math.max(group.minX + 2, Math.min(group.maxX - 2, cx + offsetX));
            int tz = Math.max(group.minZ + 2, Math.min(group.maxZ - 2, cz + offsetZ));
            if (group.isInExcludedRegion(tx, tz)) continue;
            return new Location(world, tx + 0.5, ty, tz + 0.5);
        }
        return fallback;
    }

    /** 將位置限制在房間邊界內（X/Z/Y 不超出房間），用於移動目標而非傳送 */
    private static Location clampToRoomBoundary(GroupState group, Location loc) {
        if (group == null || loc == null) return null;
        World w = loc.getWorld();
        if (w == null) return null;
        int minY = group.baseY - 5;
        int maxY = group.baseY + 80;
        if (group.templateName != null && "trap_room_01".equalsIgnoreCase(group.templateName.trim())) {
            minY = group.baseY - 32;
            maxY = group.baseY + 256;
        }
        minY = Math.max(w.getMinHeight(), minY);
        maxY = Math.min(w.getMaxHeight() - 1, maxY);
        double x = Math.max(group.minX + 0.5, Math.min(group.maxX + 0.5, loc.getX()));
        double y = Math.max(minY + 0.1, Math.min(maxY - 0.1, loc.getY()));
        double z = Math.max(group.minZ + 0.5, Math.min(group.maxZ + 0.5, loc.getZ()));
        Location out = loc.clone();
        out.setX(x);
        out.setY(y);
        out.setZ(z);
        return out;
    }

    private void processEndermanSkulls(World world, GroupState group, List<Player> players) {
        if (plugin == null || world == null || group == null) return;
        if (group.endermanSkullIds.isEmpty()) return;

        for (Iterator<UUID> it = group.endermanSkullIds.iterator(); it.hasNext(); ) {
            UUID id = it.next();
            if (id == null) {
                it.remove();
                continue;
            }
            Entity e = world.getEntity(id);
            if (!(e instanceof ArmorStand as) || !as.isValid() || as.isDead()) {
                it.remove();
                group.targetEntityIds.remove(id);
                continue;
            }
            if (!isEndermanSkull(as)) {
                it.remove();
                group.targetEntityIds.remove(id);
                continue;
            }

            Player nearest = null;
            double best = 25.0;
            if (players != null) for (Player p : players) {
                if (p == null || !p.isOnline()) continue;
                if (!insideRoom(group, p.getLocation())) continue;
                double d2 = p.getLocation().distanceSquared(as.getLocation());
                if (d2 <= best) {
                    best = d2;
                    nearest = p;
                }
            }
            if (nearest == null) continue;

            double hp = readDouble(as.getPersistentDataContainer(), endermanSkullHpKey, 20.0);
            double dmg = readDouble(as.getPersistentDataContainer(), endermanSkullDamageKey, 1.0);
            double armor = readDouble(as.getPersistentDataContainer(), endermanSkullDefenseKey, 0.0);
            int level = readInt(as.getPersistentDataContainer(), endermanSkullLevelKey, 1);
            boolean isTarget = readByte(as.getPersistentDataContainer(), endermanSkullTargetKey, (byte) 0) == (byte) 1;
            String variantId = as.getPersistentDataContainer().get(endermanSkullVariantKey, PersistentDataType.STRING);

            Location spawnAt = as.getLocation().clone().add(0.0, 0.95, 0.0);
            LivingEntity spawned = spawnMob(world, spawnAt, EntityType.ENDERMAN);
            if (spawned == null) continue;
            spawned.setRemoveWhenFarAway(false);
            applyStats(spawned, hp, dmg, armor);

            PersistentDataContainer pdc = spawned.getPersistentDataContainer();
            pdc.set(entityKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(entityGroupKey, PersistentDataType.STRING, group.groupKey);
            pdc.set(entityTargetKey, PersistentDataType.BYTE, isTarget ? (byte) 1 : (byte) 0);
            pdc.set(entityDamageKey, PersistentDataType.DOUBLE, dmg);
            pdc.set(entityTypeKey, PersistentDataType.STRING, MonsterType.ENDERMAN.name());
            if (variantId != null && !variantId.isBlank()) {
                pdc.set(entityVariantKey, PersistentDataType.STRING, variantId);
            } else {
                pdc.remove(entityVariantKey);
            }
            pdc.set(entityLevelKey, PersistentDataType.INTEGER, level);

            Component text = updateSpawnedMonsterName(spawned, MonsterType.ENDERMAN, typeConfigs.get(MonsterType.ENDERMAN), resolveVariant(typeConfigs.get(MonsterType.ENDERMAN), variantId), isTarget, level);
            if (text != null) {
                spawned.customName(Component.empty());
                spawned.setCustomNameVisible(false);
                spawnNametagForMonster(world, group, spawned, text);
            }

            if (spawned instanceof Mob mob) {
                mob.setTarget(nearest);
                mob.setAware(true);
            }

            group.spawnedEntityIds.add(spawned.getUniqueId());
            if (isTarget) {
                group.hadAnyTarget = true;
                group.targetEntityIds.remove(id);
                group.targetEntityIds.add(spawned.getUniqueId());
            }

            as.remove();
            it.remove();
        }
    }

    private static byte readByte(PersistentDataContainer pdc, NamespacedKey key, byte def) {
        if (pdc == null || key == null) return def;
        Byte b = pdc.get(key, PersistentDataType.BYTE);
        return b != null ? b : def;
    }

    private static int readInt(PersistentDataContainer pdc, NamespacedKey key, int def) {
        if (pdc == null || key == null) return def;
        Integer v = pdc.get(key, PersistentDataType.INTEGER);
        return v != null ? v : def;
    }

    private static double readDouble(PersistentDataContainer pdc, NamespacedKey key, double def) {
        if (pdc == null || key == null) return def;
        Double v = pdc.get(key, PersistentDataType.DOUBLE);
        return v != null ? v : def;
    }

    private Component updateSpawnedMonsterName(LivingEntity ent) {
        if (ent == null) return null;
        PersistentDataContainer pdc = ent.getPersistentDataContainer();
        Byte b = pdc.get(entityKey, PersistentDataType.BYTE);
        if (b == null || b != (byte) 1) return null;
        String typeStr = pdc.get(entityTypeKey, PersistentDataType.STRING);
        if (typeStr == null) return null;
        MonsterType type;
        try {
            type = MonsterType.valueOf(typeStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        MonsterTypeConfig cfg = typeConfigs.get(type);
        String variantId = pdc.get(entityVariantKey, PersistentDataType.STRING);
        MonsterVariant variant = cfg != null ? resolveVariant(cfg, variantId) : null;
        Byte targetByte = pdc.get(entityTargetKey, PersistentDataType.BYTE);
        boolean target = targetByte != null && targetByte == (byte) 1;
        Integer lvl = pdc.get(entityLevelKey, PersistentDataType.INTEGER);
        int level = lvl != null ? lvl : resolveStats(cfg, variant).level();
        if (lvl == null) {
            pdc.set(entityLevelKey, PersistentDataType.INTEGER, level);
        }
        return updateSpawnedMonsterName(ent, type, cfg, variant, target, level);
    }

    private void spawnForGroup(World world, GroupState group) {
        if (world == null || group == null) return;
        group.targetEntityIds.clear();
        group.hadAnyTarget = false;
        clearNametagStands(world, group);
        group.spawnedEntityIds.clear();
        clearEndermanSkulls(world, group);

        if (group.templateName != null && "trap_room_01".equalsIgnoreCase(group.templateName.trim())) {
            spawnTrapRoom01(world, group);
            return;
        }
        if (group.defs == null || group.defs.isEmpty()) return;

        for (MonsterDef def : group.defs) {
            if (def == null) continue;
            int[] rot = rotateLocalXZ(def.x(), def.z(), group.rotation, group.sizeX, group.sizeZ);
            int wx = group.baseX + rot[0];
            int wy = group.baseY + def.y();
            int wz = group.baseZ + rot[1];

            Location spawnAt = new Location(world, wx + 0.5, wy + 2.0, wz + 0.5);
            MonsterTypeConfig cfg = typeConfigs.get(def.type());
            EntityType entityType = cfg != null ? cfg.entityType : EntityType.ZOMBIE;

            String resolvedVariant = resolveVariantId(def, cfg);
            MonsterVariant variant = cfg != null ? resolveVariant(cfg, resolvedVariant) : null;

            MonsterStats baseStats = resolveStats(cfg, variant);
            double hp = def.hp() != null ? def.hp() : baseStats.hp();
            double dmg = def.damage() != null ? def.damage() : baseStats.damage();
            double armor = def.defense() != null ? def.defense() : baseStats.defense();
            int level = baseStats.level();

            if (def.type() == MonsterType.ENDERMAN) {
                Location skullAt = new Location(world, wx + 0.5, wy + 1.05, wz + 0.5);
                ArmorStand skull = spawnEndermanSkull(world, skullAt, group.groupKey, def.target(), hp, dmg, armor, level, resolvedVariant);
                if (skull != null) {
                    UUID skullId = skull.getUniqueId();
                    group.endermanSkullIds.add(skullId);
                    if (def.target()) {
                        group.hadAnyTarget = true;
                        group.targetEntityIds.add(skullId);
                    }
                }
                continue;
            }

            String skin = normalizeTextureId(def.skinTexture() != null ? def.skinTexture() : (variant != null ? variant.skinTexture : null));
            LivingEntity spawned = spawnMob(world, spawnAt, entityType);
            if (spawned == null) continue;

            spawned.setRemoveWhenFarAway(false);

            applyStats(spawned, hp, dmg, armor);
            if (skin != null && def.type() == MonsterType.MINIBOSS) {
                applyHeadTexture(spawned, skin);
            }

            PersistentDataContainer pdc = spawned.getPersistentDataContainer();
            pdc.set(entityKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(entityGroupKey, PersistentDataType.STRING, group.groupKey);
            pdc.set(entityTargetKey, PersistentDataType.BYTE, def.target() ? (byte) 1 : (byte) 0);
            pdc.set(entityDamageKey, PersistentDataType.DOUBLE, dmg);
            pdc.set(entityTypeKey, PersistentDataType.STRING, def.type().name());
            if (resolvedVariant != null && !resolvedVariant.isBlank()) {
                pdc.set(entityVariantKey, PersistentDataType.STRING, resolvedVariant);
            } else {
                pdc.remove(entityVariantKey);
            }
            pdc.set(entityLevelKey, PersistentDataType.INTEGER, level);

            Component text = updateSpawnedMonsterName(spawned, def.type(), cfg, variant, def.target(), level);
            if (text != null) {
                spawned.customName(Component.empty());
                spawned.setCustomNameVisible(false);
                spawnNametagForMonster(world, group, spawned, text);
            }
            group.spawnedEntityIds.add(spawned.getUniqueId());

            if (def.target()) {
                group.hadAnyTarget = true;
                group.targetEntityIds.add(spawned.getUniqueId());
            }
        }
    }

    private void spawnTrapRoom01(World world, GroupState group) {
        if (world == null || group == null) return;
        Difficulty diff = world.getDifficulty();
        if (diff != Difficulty.HARD) {
            world.setDifficulty(Difficulty.HARD);
            monsterLog("trap_room_01 forcedDifficulty world=" + world.getName() + " from=" + (diff != null ? diff.name() : "null") + " to=HARD");
        }
        monsterLog("trap_room_01 spawnStart groupKey=" + group.groupKey
                + " base=" + group.baseX + "," + group.baseY + "," + group.baseZ
                + " rot=" + (group.rotation != null ? group.rotation.name() : "null")
                + " size=" + group.sizeX + "x" + group.sizeZ
                + " difficulty=" + (world.getDifficulty() != null ? world.getDifficulty().name() : "null"));
        int localX = 9;
        int localY = 2;
        int localZ = 26;
        int[] rot = rotateLocalXZ(localX, localZ, group.rotation, group.sizeX, group.sizeZ);
        int wx = group.baseX + rot[0];
        int wy = group.baseY + localY;
        int wz = group.baseZ + rot[1];
        if (wy >= world.getMinHeight() && wy + 1 < world.getMaxHeight()) {
            Block b0 = world.getBlockAt(wx, wy, wz);
            Block b1 = world.getBlockAt(wx, wy + 1, wz);
            if (!b0.getType().isAir() || !b1.getType().isAir()) {
                Material t0 = b0.getType();
                Material t1 = b1.getType();
                b0.setType(Material.AIR, false);
                b1.setType(Material.AIR, false);
                monsterLog("trap_room_01 clearSpawnSpace at=" + wx + "," + wy + "," + wz
                        + " cleared=" + t0.name() + "," + t1.name());
            }
        }

        double[][] offsets = new double[][]{
                new double[]{0.25, 0.25},
                new double[]{0.75, 0.25},
                new double[]{0.25, 0.75},
                new double[]{0.75, 0.75}
        };
        for (double[] off : offsets) {
            Location spawnAt = new Location(world, wx + off[0], wy + 0.01, wz + off[1]);
            LivingEntity spawned = spawnMob(world, spawnAt, EntityType.SILVERFISH);
            if (spawned == null) {
                Block b = world.getBlockAt(spawnAt);
                monsterLog("trap_room_01 spawnFail at=" + spawnAt.getBlockX() + "," + spawnAt.getBlockY() + "," + spawnAt.getBlockZ()
                        + " block=" + (b != null ? b.getType().name() : "null"));
                continue;
            }
            disableSilverfishHideInBlocks(spawned);
            spawned.setRemoveWhenFarAway(false);
            applyStats(spawned, 1_000_000_000.0, 1_000_000.0, 0.0);

            PersistentDataContainer pdc = spawned.getPersistentDataContainer();
            pdc.set(entityKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(entityGroupKey, PersistentDataType.STRING, group.groupKey);
            pdc.set(entityTargetKey, PersistentDataType.BYTE, (byte) 0);
            pdc.set(entityDamageKey, PersistentDataType.DOUBLE, 1_000_000.0);

            group.spawnedEntityIds.add(spawned.getUniqueId());
            monsterLog("trap_room_01 spawned id=" + spawned.getUniqueId()
                    + " at=" + spawned.getLocation().getBlockX() + "," + spawned.getLocation().getBlockY() + "," + spawned.getLocation().getBlockZ());
        }
    }

    private static void disableSilverfishHideInBlocks(LivingEntity ent) {
        if (ent == null) return;
        if (!(ent instanceof org.bukkit.entity.Silverfish)) return;
        try {
            ent.getClass().getMethod("setCanHideInBlocks", boolean.class).invoke(ent, false);
            return;
        } catch (Throwable ignored) {
        }
        try {
            ent.getClass().getMethod("setCanHideInBlock", boolean.class).invoke(ent, false);
        } catch (Throwable ignored) {
        }
    }

    private void monsterLog(String message) {
        if (plugin == null) return;
        if (plugin.getLogger() == null) return;
        plugin.getLogger().info("monster_debug " + message);
    }

    private void clearNametagStands(World world, GroupState group) {
        if (world == null || group == null) return;
        for (UUID nametagId : group.monsterToNametag.values()) {
            if (nametagId == null) continue;
            Entity e = world.getEntity(nametagId);
            if (e != null) {
                e.remove();
            }
        }
        group.monsterToNametag.clear();
        group.nametagHiddenFrom.clear();
    }

    /** 為怪物生成可 per-player 顯示/隱藏的血條 ArmorStand */
    private void spawnNametagForMonster(World world, GroupState group, LivingEntity monster, Component text) {
        if (world == null || group == null || monster == null || text == null) return;
        Location loc = monster.getLocation().add(0, 1, 0);
        ArmorStand as = world.spawn(loc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setSmall(true);
            stand.setGravity(false);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setMarker(false);
            stand.setInvulnerable(true);
            stand.setCollidable(false);
            stand.setSilent(true);
            stand.setRemoveWhenFarAway(false);
            stand.customName(text);
            stand.setCustomNameVisible(true);
            stand.getPersistentDataContainer().set(entityNametagKey, PersistentDataType.BYTE, (byte) 1);
        });
        group.monsterToNametag.put(monster.getUniqueId(), as.getUniqueId());
    }

    private void clearEndermanSkulls(World world, GroupState group) {
        if (world == null || group == null) return;
        if (group.endermanSkullIds.isEmpty()) return;
        for (UUID id : group.endermanSkullIds) {
            if (id == null) continue;
            Entity e = world.getEntity(id);
            if (e != null) e.remove();
        }
        group.endermanSkullIds.clear();
    }

    private boolean isEndermanSkull(ArmorStand as) {
        if (as == null) return false;
        Byte b = as.getPersistentDataContainer().get(endermanSkullKey, PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }

    private ArmorStand spawnEndermanSkull(World world, Location at, String groupKey, boolean target, double hp, double dmg, double armor, int level, String variantId) {
        if (world == null || at == null || groupKey == null) return null;
        return world.spawn(at, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setSmall(true);
            as.setGravity(false);
            as.setSilent(true);
            as.setCollidable(false);
            as.setInvulnerable(true);

            ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
            ItemMeta meta = head.getItemMeta();
            if (meta instanceof SkullMeta skull) {
                try {
                    String textureId = getMhfEndermanTextureId();
                    if (textureId != null && !textureId.isBlank()) {
                        DungeonSecrets.applySkullTextureToMeta(skull, textureId, "DungeonMonsters.spawnEndermanSkull");
                    } else {
                        skull.setOwningPlayer(Bukkit.getOfflinePlayer("MHF_Enderman"));
                    }
                } catch (Throwable ignored) {
                }
                head.setItemMeta(skull);
            }
            try {
                as.getEquipment().setHelmet(head);
            } catch (Throwable ignored) {
            }

            PersistentDataContainer pdc = as.getPersistentDataContainer();
            pdc.set(endermanSkullKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(endermanSkullGroupKey, PersistentDataType.STRING, groupKey);
            pdc.set(endermanSkullTargetKey, PersistentDataType.BYTE, target ? (byte) 1 : (byte) 0);
            pdc.set(endermanSkullHpKey, PersistentDataType.DOUBLE, hp);
            pdc.set(endermanSkullDamageKey, PersistentDataType.DOUBLE, dmg);
            pdc.set(endermanSkullDefenseKey, PersistentDataType.DOUBLE, armor);
            pdc.set(endermanSkullLevelKey, PersistentDataType.INTEGER, Math.max(1, level));
            if (variantId != null && !variantId.isBlank()) {
                pdc.set(endermanSkullVariantKey, PersistentDataType.STRING, variantId);
            } else {
                pdc.remove(endermanSkullVariantKey);
            }
        });
    }

    private static String getMhfEndermanTextureId() {
        if (mhfEndermanTextureId != null && !mhfEndermanTextureId.isBlank()) return mhfEndermanTextureId;
        synchronized (DungeonMonsters.class) {
            if (mhfEndermanTextureId != null && !mhfEndermanTextureId.isBlank()) return mhfEndermanTextureId;
            mhfEndermanTextureCheckedAtMs = System.currentTimeMillis();
            mhfEndermanTextureId = MHF_ENDERMAN_TEXTURE_ID;
            return mhfEndermanTextureId;
        }
    }

    private static String fetchPlayerTextureId(String username) {
        if (username == null || username.isBlank()) return null;
        try {
            String profile = httpGet("https://api.mojang.com/users/profiles/minecraft/" + username.trim());
            if (profile == null || profile.isBlank()) return null;
            Matcher idm = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\"").matcher(profile);
            if (!idm.find()) return null;
            String uuidNoDash = idm.group(1);
            String session = httpGet("https://sessionserver.mojang.com/session/minecraft/profile/" + uuidNoDash + "?unsigned=false");
            if (session == null || session.isBlank()) return null;
            Matcher texVal = Pattern.compile("\"name\"\\s*:\\s*\"textures\"[\\s\\S]*?\"value\"\\s*:\\s*\"([^\"]+)\"").matcher(session);
            if (!texVal.find()) return null;
            String valueB64 = texVal.group(1);
            byte[] decoded = Base64.getDecoder().decode(valueB64);
            String decodedJson = new String(decoded, StandardCharsets.UTF_8);
            Matcher urlm = Pattern.compile("textures\\.minecraft\\.net/texture/([0-9a-fA-F]+)").matcher(decodedJson);
            if (!urlm.find()) return null;
            return urlm.group(1);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String httpGet(String url) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            int code = conn.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            if (in == null) return null;
            try (in) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMonsterDamage(EntityDamageByEntityEvent event) {
        if (event == null) return;
        Entity damager = event.getDamager();
        if (damager == null) return;
        LivingEntity source = null;
        if (damager instanceof LivingEntity le) {
            source = le;
        } else if (damager instanceof Projectile proj) {
            Object shooter = proj.getShooter();
            if (shooter instanceof LivingEntity le) source = le;
        }
        if (source == null) return;
        PersistentDataContainer pdc = source.getPersistentDataContainer();
        Byte b = pdc.get(entityKey, PersistentDataType.BYTE);
        if (b == null || b != (byte) 1) return;
        if (event.getEntity() instanceof LivingEntity victim) {
            Byte vb = victim.getPersistentDataContainer().get(entityKey, PersistentDataType.BYTE);
            if (vb != null && vb == (byte) 1) {
                event.setCancelled(true);
                return;
            }
        }
        if (source.getType() == EntityType.SILVERFISH && event.getEntity() instanceof Player victim) {
            String groupKey = pdc.get(entityGroupKey, PersistentDataType.STRING);
            RunState run = runsByWorld.get(source.getWorld().getName());
            if (run != null && groupKey != null) {
                GroupState group = run.groups.get(groupKey);
                if (group != null && group.templateName != null && "trap_room_01".equalsIgnoreCase(group.templateName.trim())) {
                    event.setDamage(Math.max(1.0, victim.getHealth() + 1000.0));
                    return;
                }
            }
        }
        Double dmg = pdc.get(entityDamageKey, PersistentDataType.DOUBLE);
        if (dmg == null) return;
        if (dmg <= 0) return;
        event.setDamage(dmg);
        if (event.getEntity() instanceof Player playerVictim) {
            maybeTriggerLastBossFearParalysis(source, playerVictim);
            maybeTriggerLastBossGroundSlam(source, playerVictim);
            maybeTriggerLastBossSummonAdds(source, playerVictim);
            maybeTriggerMinibossSkills(source, playerVictim);
        }
    }

    private void maybeTriggerMinibossSkills(LivingEntity source, Player victim) {
        if (source == null || victim == null) return;
        String type = source.getPersistentDataContainer().get(entityTypeKey, PersistentDataType.STRING);
        if (type == null || !MonsterType.MINIBOSS.name().equalsIgnoreCase(type)) return;
        maybeTriggerMinibossCorrosionMark(source, victim);
        maybeTriggerMinibossBloodRush(source, victim);
    }

    /**
     * MINIBOSS 技能 1：腐蝕印記（中毒 + 緩速）。
     */
    private void maybeTriggerMinibossCorrosionMark(LivingEntity source, Player victim) {
        long now = System.currentTimeMillis();
        Long until = minibossHexCooldownUntilMs.get(source.getUniqueId());
        if (until != null && now < until) return;
        if (ThreadLocalRandom.current().nextDouble() > minibossHexChance) return;
        minibossHexCooldownUntilMs.put(source.getUniqueId(), now + minibossHexCooldownMs);
        victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, minibossHexTicks, 0, true, true, true));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, minibossHexTicks, 0, true, true, true));
        victim.sendMessage(minibossThemeText("你被 Miniboss 施加【腐蝕印記】！"));
    }

    /**
     * MINIBOSS 技能 2：嗜血突襲（吸血 + 短暫加速）。
     */
    private void maybeTriggerMinibossBloodRush(LivingEntity source, Player victim) {
        long now = System.currentTimeMillis();
        Long until = minibossLeechCooldownUntilMs.get(source.getUniqueId());
        if (until != null && now < until) return;
        if (ThreadLocalRandom.current().nextDouble() > minibossLeechChance) return;
        minibossLeechCooldownUntilMs.put(source.getUniqueId(), now + minibossLeechCooldownMs);
        double max = source.getAttribute(Attribute.MAX_HEALTH) != null ? source.getAttribute(Attribute.MAX_HEALTH).getValue() : source.getMaxHealth();
        double heal = Math.max(4.0, max * minibossLeechHealRatio);
        source.setHealth(Math.min(max, source.getHealth() + heal));
        source.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, minibossLeechSpeedTicks, 0, true, true, true));
        victim.sendMessage(minibossThemeText("Miniboss 發動【嗜血突襲】恢復生命！"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFearParalysisMove(PlayerMoveEvent event) {
        if (event == null) return;
        Player player = event.getPlayer();
        if (player == null) return;
        FearParalysisState state = fearParalysisStates.get(player.getUniqueId());
        if (state == null) return;
        Location to = event.getTo();
        if (to == null) return;
        long now = System.currentTimeMillis();
        if (now > state.untilMs()) {
            clearFearParalysis(player.getUniqueId());
            return;
        }
        Location lock = state.lockLocation();
        if (lock == null) return;
        if (Math.abs(to.getX() - lock.getX()) < 0.001
                && Math.abs(to.getY() - lock.getY()) < 0.001
                && Math.abs(to.getZ() - lock.getZ()) < 0.001) {
            return;
        }
        Location fixed = lock.clone();
        fixed.setYaw(to.getYaw());
        fixed.setPitch(to.getPitch());
        event.setTo(fixed);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEndermanArrowHit(EntityDamageByEntityEvent event) {
        if (event == null) return;
        if (!(event.getEntity() instanceof Enderman enderman)) return;
        if (!(event.getDamager() instanceof Projectile proj)) return;
        if (!isDungeonEnderman(enderman)) return;

        PersistentDataContainer pdc = enderman.getPersistentDataContainer();
        pdc.set(entityOriginalLocationKey, PersistentDataType.STRING, encodeBlockLoc(enderman.getLocation()));

        Location from = enderman.getLocation();
        Location to = findNearbySafe(from, 8, 24);

        long now = System.currentTimeMillis();
        endermanAllowTeleportUntilMs.put(enderman.getUniqueId(), now + 2500L);

        BukkitRunnable prev = endermanReturnTasks.remove(enderman.getUniqueId());
        if (prev != null) prev.cancel();

        enderman.teleport(to);
        proj.remove();
        event.setCancelled(true);

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!enderman.isValid() || enderman.isDead()) {
                    endermanReturnTasks.remove(enderman.getUniqueId());
                    return;
                }
                String s = enderman.getPersistentDataContainer().get(entityOriginalLocationKey, PersistentDataType.STRING);
                Location orig = s != null ? decodeBlockLoc(enderman.getWorld(), s, enderman.getLocation()) : null;
                if (orig == null) {
                    endermanReturnTasks.remove(enderman.getUniqueId());
                    return;
                }
                long now2 = System.currentTimeMillis();
                endermanAllowTeleportUntilMs.put(enderman.getUniqueId(), now2 + 2500L);
                enderman.teleport(orig);
                endermanReturnTasks.remove(enderman.getUniqueId());
            }
        };
        task.runTaskLater(plugin, 20L);
        endermanReturnTasks.put(enderman.getUniqueId(), task);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEndermanTeleport(EntityTeleportEvent event) {
        if (event == null) return;
        if (!(event.getEntity() instanceof Enderman enderman)) return;
        if (!isDungeonEnderman(enderman)) return;
        long now = System.currentTimeMillis();
        Long allowUntil = endermanAllowTeleportUntilMs.get(enderman.getUniqueId());
        if (allowUntil != null && now < allowUntil) return;
        event.setCancelled(true);
    }

    private static LivingEntity spawnMob(World world, Location at, EntityType type) {
        if (world == null || at == null || type == null) return null;
        if (!type.isAlive()) return null;
        try {
            Entity e = world.spawnEntity(at, type);
            if (!(e instanceof LivingEntity le)) return null;
            if (le instanceof Zombie z) {
                z.setBaby(false);
            }
            return le;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean isDungeonEnderman(Enderman enderman) {
        if (enderman == null) return false;
        PersistentDataContainer pdc = enderman.getPersistentDataContainer();
        Byte b = pdc.get(entityKey, PersistentDataType.BYTE);
        if (b == null || b != (byte) 1) return false;
        String type = pdc.get(entityTypeKey, PersistentDataType.STRING);
        return type != null && type.equalsIgnoreCase(MonsterType.ENDERMAN.name());
    }

    private static String encodeBlockLoc(Location loc) {
        if (loc == null) return null;
        return loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private static Location decodeBlockLoc(World world, String s, Location fallback) {
        if (world == null || s == null) return fallback;
        String[] parts = s.split(",");
        if (parts.length != 3) return fallback;
        try {
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            int z = Integer.parseInt(parts[2].trim());
            float yaw = fallback != null ? fallback.getYaw() : 0.0f;
            float pitch = fallback != null ? fallback.getPitch() : 0.0f;
            return new Location(world, x + 0.5, y, z + 0.5, yaw, pitch);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Location findNearbySafe(Location from, int radius, int attempts) {
        if (from == null) return null;
        World world = from.getWorld();
        if (world == null) return from;
        int baseX = from.getBlockX();
        int baseY = from.getBlockY();
        int baseZ = from.getBlockZ();
        float yaw = from.getYaw();
        float pitch = from.getPitch();

        for (int i = 0; i < Math.max(1, attempts); i++) {
            int dx = ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int dz = ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int dy = ThreadLocalRandom.current().nextInt(-1, 2);
            int x = baseX + dx;
            int y = baseY + dy;
            int z = baseZ + dz;

            Block feet = world.getBlockAt(x, y, z);
            Block head = world.getBlockAt(x, y + 1, z);
            Block below = world.getBlockAt(x, y - 1, z);
            if (!feet.getType().isAir()) continue;
            if (!head.getType().isAir()) continue;
            if (below.getType().isAir()) continue;
            return new Location(world, x + 0.5, y, z + 0.5, yaw, pitch);
        }
        return from;
    }

    private static void applyStats(LivingEntity ent, double hp, double dmg, double armor) {
        if (ent == null) return;
        if (hp > 0) {
            if (ent.getAttribute(Attribute.MAX_HEALTH) != null) {
                try {
                    ent.getAttribute(Attribute.MAX_HEALTH).setBaseValue(hp);
                } catch (Throwable ignored) {
                }
            }
            try {
                double max = ent.getAttribute(Attribute.MAX_HEALTH) != null ? ent.getAttribute(Attribute.MAX_HEALTH).getValue() : ent.getMaxHealth();
                if (max <= 0) max = ent.getMaxHealth();
                ent.setHealth(Math.min(hp, max));
            } catch (Throwable ignored) {
            }
        }
        if (ent.getAttribute(Attribute.ATTACK_DAMAGE) != null && dmg > 0) {
            ent.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(dmg);
        }
        if (ent.getAttribute(Attribute.ARMOR) != null && armor >= 0) {
            ent.getAttribute(Attribute.ARMOR).setBaseValue(armor);
        }
    }

    private static void applyHeadTexture(LivingEntity ent, String textureId) {
        if (ent == null || textureId == null || textureId.isBlank()) return;
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        ItemMeta meta = head.getItemMeta();
        if (!(meta instanceof SkullMeta skullMeta)) return;
        DungeonSecrets.applySkullTextureToMeta(skullMeta, textureId, "DungeonMonsters.applyHeadTexture");
        head.setItemMeta(skullMeta);
        try {
            ent.getEquipment().setHelmet(head);
        } catch (Throwable ignored) {
        }
    }

    private static String resolveCategoryName(MonsterType type, MonsterTypeConfig cfg, MonsterVariant variant) {
        String base = cfg != null && cfg.displayName != null && !cfg.displayName.isBlank() ? cfg.displayName : type.name();
        String v = variant != null && variant.displayName != null && !variant.displayName.isBlank() ? variant.displayName : null;
        return v != null ? (base + " " + v) : base;
    }

    private static String buildBracketName(String categoryName, int level) {
        String cat = categoryName != null && !categoryName.isBlank() ? categoryName : "UNKNOWN";
        int lvl = Math.max(1, level);
        return "[" + cat + "/lvl" + lvl + "]";
    }

    private static String buildHpPart(LivingEntity ent) {
        if (ent == null) return " ❤ 0/0";
        double max = 0.0;
        if (ent.getAttribute(Attribute.MAX_HEALTH) != null) {
            try {
                max = ent.getAttribute(Attribute.MAX_HEALTH).getValue();
            } catch (Throwable ignored) {
            }
        }
        if (max <= 0.0) max = 1.0;
        int s = (int) Math.ceil(Math.max(0.0, ent.getHealth()));
        int m = (int) Math.ceil(max);
        return " ❤ " + s + "/" + m;
    }

    private Component updateSpawnedMonsterName(LivingEntity ent, MonsterType type, MonsterTypeConfig cfg, MonsterVariant variant, boolean target, int level) {
        if (ent == null || type == null) return null;
        String bracket = buildBracketName(resolveCategoryName(type, cfg, variant), level);
        String out = (target ? "⭐ " : "") + bracket + buildHpPart(ent);
        return Component.text(out, target ? NamedTextColor.GREEN : NamedTextColor.YELLOW);
    }

    private static MonsterStats resolveStats(MonsterTypeConfig cfg, MonsterVariant variant) {
        if (variant != null && variant.stats != null) return variant.stats;
        if (cfg != null && cfg.variants != null && !cfg.variants.isEmpty() && cfg.variants.get(0).stats != null) {
            return cfg.variants.get(0).stats;
        }
        return new MonsterStats(20, 1, 0, 1);
    }

    private static String resolveVariantId(MonsterDef def, MonsterTypeConfig cfg) {
        if (def == null) return null;
        if (def.variantId() != null && !def.variantId().isBlank()) return def.variantId();
        if (cfg != null && cfg.variants != null && cfg.variants.size() > 1) {
            int idx = ThreadLocalRandom.current().nextInt(cfg.variants.size());
            String id = cfg.variants.get(idx).id;
            return id != null && !id.isBlank() ? id : null;
        }
        return null;
    }

    private static MonsterVariant resolveVariant(MonsterTypeConfig cfg, String variantId) {
        if (cfg == null || cfg.variants == null || cfg.variants.isEmpty()) return null;
        if (variantId == null || variantId.isBlank()) return cfg.variants.get(0);
        for (MonsterVariant v : cfg.variants) {
            if (v == null || v.id == null) continue;
            if (v.id.equalsIgnoreCase(variantId)) return v;
        }
        return cfg.variants.get(0);
    }

    private static String normalizeTextureId(String skin) {
        if (skin == null) return null;
        String s = skin.trim();
        if (s.isEmpty()) return null;
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.startsWith(TEXTURE_BASE)) return s.substring(TEXTURE_BASE.length());
        if (lower.startsWith("http://textures.minecraft.net/texture/")) return s.substring("http://textures.minecraft.net/texture/".length());
        if (lower.startsWith("https://textures.minecraft.net/texture/")) return s.substring("https://textures.minecraft.net/texture/".length());
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            try {
                URL u = new URL(s);
                String path = u.getPath();
                int idx = path.lastIndexOf('/');
                if (idx >= 0 && idx + 1 < path.length()) return path.substring(idx + 1);
            } catch (Throwable ignored) {
            }
        }
        return s;
    }

    private static void sendToNearby(World world, int x, int y, int z, Component msg) {
        if (world == null || msg == null) return;
        Location center = new Location(world, x + 0.5, y + 0.5, z + 0.5);
        for (Player p : world.getPlayers()) {
            if (p == null) continue;
            if (p.getLocation().distanceSquared(center) > 48 * 48) continue;
            p.sendMessage(msg);
        }
    }

    private static int packCell(int x, int y) {
        return (x << 16) ^ (y & 0xFFFF);
    }

    private static MonsterType parseMonsterTypeForInput(String typeInput) {
        if (typeInput == null || typeInput.isBlank()) return null;
        String normalized = typeInput.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return MonsterType.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static int[] rotateLocalXZ(int lx, int lz, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
        if (rotation == null) return new int[]{lx, lz};
        return switch (rotation) {
            case R0 -> new int[]{lx, lz};
            case R90 -> new int[]{lz, sizeX - 1 - lx};
            case R180 -> new int[]{(sizeX - 1) - lx, (sizeZ - 1) - lz};
            case R270 -> new int[]{sizeZ - 1 - lz, lx};
        };
    }

    private static boolean insideRoom(GroupState group, Location loc) {
        if (group == null || loc == null) return false;
        World w = loc.getWorld();
        if (w == null) return false;
        if (group.worldName == null || !group.worldName.equals(w.getName())) return false;

        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        int minY = group.baseY - 5;
        int maxY = group.baseY + 80;
        if (group.templateName != null && "trap_room_01".equalsIgnoreCase(group.templateName.trim())) {
            minY = group.baseY - 32;
            maxY = group.baseY + 256;
        }
        minY = Math.max(w.getMinHeight(), minY);
        maxY = Math.min(w.getMaxHeight() - 1, maxY);
        if (x < group.minX || x > group.maxX || z < group.minZ || z > group.maxZ || y < minY || y > maxY) return false;
        if (group.isInExcludedRegion(x, z)) return false;
        return true;
    }

    private void clearMarkersInBox(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (world == null) return;
        for (Entity e : world.getEntitiesByClass(ArmorStand.class)) {
            if (!(e instanceof ArmorStand as)) continue;
            if (!isMarker(as)) continue;
            Location l = as.getLocation();
            int bx = l.getBlockX();
            int by = l.getBlockY();
            int bz = l.getBlockZ();
            if (bx < minX || bx > maxX) continue;
            if (by < minY || by > maxY) continue;
            if (bz < minZ || bz > maxZ) continue;
            as.remove();
        }
    }

    private ArmorStand spawnEditMarker(World world, double x, double y, double z, MonsterType type, boolean target) {
        if (world == null || type == null) return null;
        return world.spawn(new Location(world, x, y, z), ArmorStand.class, as -> {
            as.setVisible(true);
            as.setSmall(true);
            as.setGravity(false);
            as.setSilent(true);
            as.setCollidable(false);
            as.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
            as.getPersistentDataContainer().set(markerTypeKey, PersistentDataType.STRING, type.name());
            as.getPersistentDataContainer().set(markerTargetKey, PersistentDataType.BYTE, target ? (byte) 1 : (byte) 0);
            as.getPersistentDataContainer().set(markerBabyKey, PersistentDataType.BYTE, (byte) 0);
            updateMarkerName(as);
        });
    }

    private boolean isMarker(ArmorStand as) {
        if (as == null) return false;
        Byte b = as.getPersistentDataContainer().get(markerKey, PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }

    private MonsterType getMarkerType(ArmorStand as) {
        if (as == null) return null;
        String s = as.getPersistentDataContainer().get(markerTypeKey, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return MonsterType.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean isMarkerTarget(ArmorStand as) {
        if (as == null) return false;
        Byte b = as.getPersistentDataContainer().get(markerTargetKey, PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }

    private void setMarkerTarget(ArmorStand as, boolean target) {
        if (as == null) return;
        as.getPersistentDataContainer().set(markerTargetKey, PersistentDataType.BYTE, target ? (byte) 1 : (byte) 0);
        updateMarkerName(as);
    }

    private boolean isMarkerBaby(ArmorStand as) {
        if (as == null) return false;
        Byte b = as.getPersistentDataContainer().get(markerBabyKey, PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }

    private void setMarkerBaby(ArmorStand as, boolean baby) {
        if (as == null) return;
        as.getPersistentDataContainer().set(markerBabyKey, PersistentDataType.BYTE, baby ? (byte) 1 : (byte) 0);
    }

    private static String getMarkerString(ArmorStand as, NamespacedKey key) {
        if (as == null || key == null) return null;
        return as.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    private static Double getMarkerDouble(ArmorStand as, NamespacedKey key) {
        if (as == null || key == null) return null;
        return as.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
    }

    private void cycleMarkerType(ArmorStand as, int dir) {
        if (as == null) return;
        MonsterType cur = getMarkerType(as);
        MonsterType[] values = MonsterType.values();
        int idx = 0;
        if (cur != null) {
            for (int i = 0; i < values.length; i++) {
                if (values[i] == cur) {
                    idx = i;
                    break;
                }
            }
        }
        int next = Math.floorMod(idx + (dir >= 0 ? 1 : -1), values.length);
        as.getPersistentDataContainer().set(markerTypeKey, PersistentDataType.STRING, values[next].name());
        as.getPersistentDataContainer().remove(markerVariantKey);
        as.getPersistentDataContainer().remove(markerSkinKey);
        clearMarkerOverrides(as);
        updateMarkerName(as);
    }

    private void cycleMarkerVariant(ArmorStand as) {
        if (as == null) return;
        MonsterType type = getMarkerType(as);
        if (type == null) return;
        MonsterTypeConfig cfg = typeConfigs.get(type);
        if (cfg == null || cfg.variants == null || cfg.variants.size() <= 1) return;
        String cur = getMarkerString(as, markerVariantKey);
        int idx = -1;
        if (cur != null) {
            for (int i = 0; i < cfg.variants.size(); i++) {
                MonsterVariant v = cfg.variants.get(i);
                if (v != null && v.id != null && v.id.equalsIgnoreCase(cur)) {
                    idx = i;
                    break;
                }
            }
        }
        int next = (idx + 1) % cfg.variants.size();
        MonsterVariant v = cfg.variants.get(next);
        if (v != null && v.id != null) {
            as.getPersistentDataContainer().set(markerVariantKey, PersistentDataType.STRING, v.id);
        }
        updateMarkerName(as);
    }

    private void clearMarkerOverrides(ArmorStand as) {
        if (as == null) return;
        as.getPersistentDataContainer().remove(markerHpKey);
        as.getPersistentDataContainer().remove(markerDamageKey);
        as.getPersistentDataContainer().remove(markerDefenseKey);
        updateMarkerName(as);
    }

    private enum StatKind { HP, DAMAGE, DEFENSE }

    private void adjustMarkerStat(ArmorStand as, StatKind kind, double delta) {
        if (as == null || kind == null) return;
        MonsterType type = getMarkerType(as);
        MonsterTypeConfig cfg = type != null ? typeConfigs.get(type) : null;
        MonsterVariant variant = cfg != null ? resolveVariant(cfg, getMarkerString(as, markerVariantKey)) : null;
        MonsterStats base = resolveStats(cfg, variant);

        NamespacedKey key = switch (kind) {
            case HP -> markerHpKey;
            case DAMAGE -> markerDamageKey;
            case DEFENSE -> markerDefenseKey;
        };
        Double cur = getMarkerDouble(as, key);
        double baseVal = switch (kind) {
            case HP -> base.hp();
            case DAMAGE -> base.damage();
            case DEFENSE -> base.defense();
        };
        double next = (cur != null ? cur : baseVal) + delta;
        if (kind == StatKind.HP) next = Math.max(1, next);
        if (kind != StatKind.HP) next = Math.max(0, next);
        as.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, next);
        updateMarkerName(as);
    }

    private void updateMarkerName(ArmorStand as) {
        if (as == null) return;
        MonsterType type = getMarkerType(as);
        if (type == null) return;
        MonsterTypeConfig cfg = typeConfigs.get(type);
        String variantId = getMarkerString(as, markerVariantKey);
        MonsterVariant variant = cfg != null ? resolveVariant(cfg, variantId) : null;
        boolean target = isMarkerTarget(as);
        MonsterStats baseStats = resolveStats(cfg, variant);
        String name = (target ? "⭐ " : "") + buildBracketName(resolveCategoryName(type, cfg, variant), baseStats.level());

        Double hp = getMarkerDouble(as, markerHpKey);
        Double dmg = getMarkerDouble(as, markerDamageKey);
        Double def = getMarkerDouble(as, markerDefenseKey);

        List<Component> parts = new ArrayList<>();
        parts.add(Component.text(name, target ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        if (hp != null || dmg != null || def != null) {
            String s = " (" + (hp != null ? "HP=" + fmtNum(hp) : "")
                    + (dmg != null ? (hp != null ? " " : "") + "ATK=" + fmtNum(dmg) : "")
                    + (def != null ? ((hp != null || dmg != null) ? " " : "") + "DEF=" + fmtNum(def) : "")
                    + ")";
            parts.add(Component.text(s, NamedTextColor.GRAY));
        }

        Component out = parts.get(0);
        for (int i = 1; i < parts.size(); i++) out = out.append(parts.get(i));
        as.customName(out);
        as.setCustomNameVisible(true);
    }

    private static String fmtNum(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) return String.valueOf((long) Math.rint(v));
        return String.format(Locale.US, "%.2f", v);
    }

    private static ArmorStand findArmorStand(World world, UUID id) {
        if (world == null || id == null) return null;
        for (Entity e : world.getEntities()) {
            if (!(e instanceof ArmorStand as)) continue;
            if (id.equals(as.getUniqueId())) return as;
        }
        return null;
    }

    private void fillMonsterEditInventory(Inventory inv, ArmorStand as) {
        if (inv == null || as == null) return;
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, null);

        MonsterType type = getMarkerType(as);
        MonsterTypeConfig cfg = type != null ? typeConfigs.get(type) : null;
        MonsterVariant variant = cfg != null ? resolveVariant(cfg, getMarkerString(as, markerVariantKey)) : null;
        MonsterStats base = resolveStats(cfg, variant);

        inv.setItem(10, buttonItem(isMarkerTarget(as) ? Material.LIME_DYE : Material.GRAY_DYE, "切换目标", List.of(
                "当前: " + (isMarkerTarget(as) ? "目标" : "非目标")
        )));
        inv.setItem(11, buttonItem(isMarkerBaby(as) ? Material.LIME_DYE : Material.GRAY_DYE, "切换小怪", List.of(
                "当前: " + (isMarkerBaby(as) ? "小怪" : "普通")
        )));
        inv.setItem(12, buttonItem(Material.ARROW, "上一个怪物类型", List.of()));
        inv.setItem(13, buttonItem(monsterIcon(type), "当前类型: " + (cfg != null ? cfg.displayName : (type != null ? type.name() : "?")), List.of()));
        inv.setItem(14, buttonItem(Material.ARROW, "切换外观/变种", List.of(cfg != null && cfg.variants != null ? ("可选: " + cfg.variants.size()) : "可选: 1")));

        inv.setItem(16, buttonItem(Material.BARRIER, "清空自定义属性", List.of()));
        inv.setItem(18, buttonItem(Material.LAVA_BUCKET, "删除怪物点", List.of("也可以潜行左键删除")));

        inv.setItem(2, buttonItem(Material.RED_CONCRETE, "HP -100", List.of()));
        inv.setItem(3, buttonItem(Material.RED_CONCRETE, "HP -10", List.of()));
        inv.setItem(4, buttonItem(Material.LIME_CONCRETE, "HP +10", List.of()));
        inv.setItem(5, buttonItem(Material.LIME_CONCRETE, "HP +100", List.of()));

        inv.setItem(6, buttonItem(Material.RED_WOOL, "ATK -5", List.of()));
        inv.setItem(7, buttonItem(Material.RED_WOOL, "ATK -1", List.of()));
        inv.setItem(8, buttonItem(Material.LIME_WOOL, "ATK +1", List.of()));
        inv.setItem(9, buttonItem(Material.LIME_WOOL, "ATK +5", List.of()));

        inv.setItem(20, buttonItem(Material.RED_TERRACOTTA, "DEF -5", List.of()));
        inv.setItem(21, buttonItem(Material.RED_TERRACOTTA, "DEF -1", List.of()));
        inv.setItem(22, buttonItem(Material.LIME_TERRACOTTA, "DEF +1", List.of()));
        inv.setItem(23, buttonItem(Material.LIME_TERRACOTTA, "DEF +5", List.of()));

        Double hp = getMarkerDouble(as, markerHpKey);
        Double dmg = getMarkerDouble(as, markerDamageKey);
        Double def = getMarkerDouble(as, markerDefenseKey);
        List<String> now = List.of(
                "基础: HP=" + fmtNum(base.hp()) + " ATK=" + fmtNum(base.damage()) + " DEF=" + fmtNum(base.defense()),
                "当前: HP=" + fmtNum(hp != null ? hp : base.hp())
                        + " ATK=" + fmtNum(dmg != null ? dmg : base.damage())
                        + " DEF=" + fmtNum(def != null ? def : base.defense())
        );
        inv.setItem(15, buttonItem(Material.PAPER, "属性预览", now));
    }

    private static Material monsterIcon(MonsterType type) {
        if (type == null) return Material.BARRIER;
        return switch (type) {
            case MINIBOSS -> Material.PLAYER_HEAD;
            case ZOMBIE_TANK, ZOMBIE_WARRIOR -> Material.ZOMBIE_SPAWN_EGG;
            case SKELETON -> Material.SKELETON_SPAWN_EGG;
            case WITCH -> Material.WITCH_SPAWN_EGG;
            case ENDERMAN -> Material.ENDERMAN_SPAWN_EGG;
        };
    }

    private ItemStack buttonItem(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.YELLOW));
            if (loreLines != null && !loreLines.isEmpty()) {
                List<Component> lore = new ArrayList<>();
                for (String s : loreLines) {
                    if (s == null) continue;
                    lore.add(Component.text(s, NamedTextColor.GRAY));
                }
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack toolItem(Material material, String name, MonsterType type) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.YELLOW));
            meta.getPersistentDataContainer().set(toolKey, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(toolTypeKey, PersistentDataType.STRING, type.name());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack toggleItem(boolean target) {
        ItemStack item = new ItemStack(Material.NETHER_STAR, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("目前放置: " + (target ? "目标" : "非目标"),
                    target ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            meta.getPersistentDataContainer().set(toolKey, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(toolToggleKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private MonsterType toolType(ItemStack stack) {
        if (stack == null) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        Byte tool = meta.getPersistentDataContainer().get(toolKey, PersistentDataType.BYTE);
        if (tool == null || tool != (byte) 1) return null;
        String s = meta.getPersistentDataContainer().get(toolTypeKey, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return MonsterType.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean isToggleItem(ItemStack stack) {
        if (stack == null) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        Byte tool = meta.getPersistentDataContainer().get(toolKey, PersistentDataType.BYTE);
        if (tool == null || tool != (byte) 1) return false;
        Byte toggle = meta.getPersistentDataContainer().get(toolToggleKey, PersistentDataType.BYTE);
        return toggle != null && toggle == (byte) 1;
    }

    private File globalConfigFile() {
        File dir = plugin.getDataFolder();
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, GLOBAL_CONFIG_NAME);
    }

    private void maybeLoadGlobalConfig() {
        if (plugin == null) return;
        long now = System.currentTimeMillis();
        if (!typeConfigs.isEmpty() && now - globalConfigLoadedAt < 2000L) return;
        globalConfigLoadedAt = now;
        typeConfigs.clear();
        minibossLeechChance = MINIBOSS_LEECH_CHANCE;
        minibossLeechCooldownMs = MINIBOSS_LEECH_COOLDOWN_MS;
        minibossLeechHealRatio = MINIBOSS_LEECH_HEAL_RATIO;
        minibossLeechSpeedTicks = MINIBOSS_LEECH_SPEED_TICKS;
        minibossHexChance = MINIBOSS_HEX_CHANCE;
        minibossHexCooldownMs = MINIBOSS_HEX_COOLDOWN_MS;
        minibossHexTicks = MINIBOSS_HEX_TICKS;

        File file = globalConfigFile();
        if (!file.exists()) {
            try {
                plugin.saveResource(GLOBAL_CONFIG_NAME, false);
            } catch (Throwable ignored) {
            }
        }
        if (!file.exists()) return;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("monsters");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            if (key == null) continue;
            MonsterType type;
            try {
                type = MonsterType.valueOf(key.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) continue;

            String displayName = sec.getString("displayName", type.name());
            EntityType entityType = parseEntityType(sec.getString("entity", "ZOMBIE"), EntityType.ZOMBIE);
            int baseLevel = Math.max(1, sec.getInt("level", 1));
            boolean baseBaby = sec.getBoolean("baby", false);
            if (type == MonsterType.MINIBOSS) {
                ConfigurationSection skills = sec.getConfigurationSection("skills");
                if (skills != null) {
                    ConfigurationSection corrosion = skills.getConfigurationSection("corrosionMark");
                    if (corrosion != null) {
                        minibossHexChance = Math.max(0.0, Math.min(1.0, corrosion.getDouble("chance", minibossHexChance)));
                        minibossHexCooldownMs = Math.max(1000L, Math.round(corrosion.getDouble("cooldownSeconds", minibossHexCooldownMs / 1000.0) * 1000.0));
                        minibossHexTicks = Math.max(20, (int) Math.round(corrosion.getDouble("durationSeconds", minibossHexTicks / 20.0) * 20.0));
                    }
                    ConfigurationSection bloodRush = skills.getConfigurationSection("bloodRush");
                    if (bloodRush != null) {
                        minibossLeechChance = Math.max(0.0, Math.min(1.0, bloodRush.getDouble("chance", minibossLeechChance)));
                        minibossLeechCooldownMs = Math.max(1000L, Math.round(bloodRush.getDouble("cooldownSeconds", minibossLeechCooldownMs / 1000.0) * 1000.0));
                        minibossLeechSpeedTicks = Math.max(20, (int) Math.round(bloodRush.getDouble("durationSeconds", minibossLeechSpeedTicks / 20.0) * 20.0));
                        minibossLeechHealRatio = Math.max(0.0, Math.min(0.5, bloodRush.getDouble("healRatio", minibossLeechHealRatio)));
                    }
                }
            }

            List<MonsterVariant> variants = new ArrayList<>();
            List<Map<?, ?>> vs = sec.getMapList("variants");
            if (vs != null && !vs.isEmpty()) {
                for (Object o : vs) {
                    if (!(o instanceof Map<?, ?> m)) continue;
                    String id = str(m.get("id"));
                    String vName = str(m.get("name"));
                    Double hp = num(m.get("hp"));
                    Double dmg = num(m.get("damage"));
                    Double def = num(m.get("defense"));
                    Double lvlNum = num(m.get("level"));
                    Boolean baby = bool(m.get("baby"));
                    String skin = str(m.get("skin"));
                    int level = Math.max(1, lvlNum != null ? (int) Math.round(lvlNum) : baseLevel);
                    MonsterStats stats = new MonsterStats(
                            hp != null ? hp : 20,
                            dmg != null ? dmg : 1,
                            def != null ? def : 0,
                            level
                    );
                    variants.add(new MonsterVariant(id != null ? id : String.valueOf(variants.size() + 1), vName, stats, skin, baby != null ? baby : baseBaby));
                }
            } else {
                double hp = Math.max(1, sec.getDouble("hp", 20));
                double dmg = Math.max(0, sec.getDouble("damage", 1));
                double def = Math.max(0, sec.getDouble("defense", 0));
                String skin = sec.getString("skin");
                variants.add(new MonsterVariant("default", null, new MonsterStats(hp, dmg, def, baseLevel), skin, baseBaby));
            }
            typeConfigs.put(type, new MonsterTypeConfig(displayName, entityType, List.copyOf(variants), baseBaby));
        }
    }

    private static EntityType parseEntityType(String s, EntityType def) {
        if (s == null) return def;
        try {
            return EntityType.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return def;
        }
    }

    private static String str(Object o) {
        if (o == null) return null;
        if (o instanceof String s) return s;
        return String.valueOf(o);
    }

    private static Component gradientText(String text, TextColor from, TextColor to) {
        if (text == null || text.isEmpty()) return Component.empty();
        if (from == null || to == null) return Component.text(text);
        char[] chars = text.toCharArray();
        if (chars.length == 1) return Component.text(String.valueOf(chars[0]), from);
        Component out = Component.empty();
        for (int i = 0; i < chars.length; i++) {
            double t = (double) i / (double) (chars.length - 1);
            int r = (int) Math.round(from.red() + (to.red() - from.red()) * t);
            int g = (int) Math.round(from.green() + (to.green() - from.green()) * t);
            int b = (int) Math.round(from.blue() + (to.blue() - from.blue()) * t);
            out = out.append(Component.text(String.valueOf(chars[i]), TextColor.color(r, g, b)));
        }
        return out;
    }

    private static Component bossThemeText(String text) {
        return gradientText(text, BOSS_THEME_FROM, BOSS_THEME_TO);
    }

    private static Component minibossThemeText(String text) {
        return gradientText(text, MINIBOSS_THEME_FROM, MINIBOSS_THEME_TO);
    }

    private static Double num(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static Boolean bool(Object o) {
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.intValue() != 0;
        if (o instanceof String s) {
            String t = s.trim();
            if (t.equalsIgnoreCase("true") || t.equals("1") || t.equalsIgnoreCase("yes") || t.equalsIgnoreCase("y")) return true;
            if (t.equalsIgnoreCase("false") || t.equals("0") || t.equalsIgnoreCase("no") || t.equalsIgnoreCase("n")) return false;
        }
        return null;
    }

    private File templateMonstersFile(String templateName) {
        File dir = new File(plugin.getDataFolder(), "monsters");
        if (!dir.exists()) dir.mkdirs();
        String safe = templateName.toLowerCase(Locale.ROOT);
        return new File(dir, safe + ".yml");
    }

    private static boolean isBaseRoom1x1Template(String templateName) {
        return templateName != null && BASE_ROOM_1X1_TEMPLATE_PATTERN.matcher(templateName.trim()).matches();
    }

    private static List<MonsterDef> defaultBaseRoom1x1Monsters() {
        return List.of(
                new MonsterDef(MonsterType.ZOMBIE_WARRIOR, 10, 1, 10, false, null, null, null, null, "a", null),
                new MonsterDef(MonsterType.ZOMBIE_TANK, 20, 1, 10, false, null, null, null, null, "a", null),
                new MonsterDef(MonsterType.SKELETON, 10, 1, 20, false, null, null, null, null, null, null),
                new MonsterDef(MonsterType.WITCH, 20, 1, 20, false, null, null, null, null, null, null),
                new MonsterDef(MonsterType.ZOMBIE_WARRIOR, 15, 1, 15, true, null, null, null, null, "b", null)
        );
    }

    private List<MonsterDef> loadTemplateMonsters(String templateName) {
        if (templateName == null || templateName.isBlank()) return List.of();
        File file = templateMonstersFile(templateName);
        if (!file.exists()) {
            if (isBaseRoom1x1Template(templateName)) return defaultBaseRoom1x1Monsters();
            return List.of();
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        List<?> list = cfg.getList("monsters");
        if (list == null || list.isEmpty()) return List.of();
        List<MonsterDef> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            String t = str(m.get("type"));
            Double x = num(m.get("x"));
            Double y = num(m.get("y"));
            Double z = num(m.get("z"));
            if (t == null || x == null || y == null || z == null) continue;
            MonsterType type;
            try {
                type = MonsterType.valueOf(t.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            boolean target = false;
            Object tar = m.get("target");
            if (tar instanceof Boolean b) target = b;
            if (tar instanceof String s) target = s.trim().equalsIgnoreCase("true") || s.trim().equals("1");
            Boolean baby = bool(m.get("baby"));
            Double hp = num(m.get("hp"));
            Double dmg = num(m.get("damage"));
            Double def = num(m.get("defense"));
            String variant = str(m.get("variant"));
            String skin = str(m.get("skin"));
            out.add(new MonsterDef(type, x.intValue(), y.intValue(), z.intValue(), target, baby, hp, dmg, def, variant, skin));
        }
        return List.copyOf(out);
    }

    private void saveTemplateMonsters(String templateName, List<Map<String, Object>> monsters) {
        if (templateName == null || templateName.isBlank()) return;
        File file = templateMonstersFile(templateName);
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("version", 1);
        cfg.set("monsters", monsters != null ? monsters : List.of());
        try {
            cfg.save(file);
        } catch (IOException e) {
            if (plugin.getLogger() != null) plugin.getLogger().warning("Failed to save monsters for " + templateName + ": " + e.getMessage());
        }
    }

    private static final class RunState {
        private final String worldName;
        private final Map<String, GroupState> groups = new HashMap<>();
        private final Set<UUID> lastBossChestClaimedPlayers = ConcurrentHashMap.newKeySet();
        private BukkitRunnable nameTask;

        private RunState(String worldName) {
            this.worldName = worldName;
        }

        private void stop() {
            if (nameTask != null) {
                nameTask.cancel();
                nameTask = null;
            }
            World world = Bukkit.getWorld(worldName);
            for (GroupState g : groups.values()) {
                if (g == null) continue;
                if (g.presenceTask != null) {
                    g.presenceTask.cancel();
                    g.presenceTask = null;
                }
                if (world != null && g.endermanSkullIds != null && !g.endermanSkullIds.isEmpty()) {
                    for (UUID id : g.endermanSkullIds) {
                        if (id == null) continue;
                        Entity e = world.getEntity(id);
                        if (e != null) e.remove();
                    }
                    g.endermanSkullIds.clear();
                }
            }
        }
    }

    private static final class GroupState {
        private final String groupKey;
        private final String templateName;
        private final Set<Integer> cells = new HashSet<>();

        private boolean spawned;
        private boolean cleared;
        private boolean hadAnyTarget;

        private String worldName;
        private int baseX;
        private int baseY;
        private int baseZ;
        private int sizeX;
        private int sizeZ;
        private StructureTemplate.Rotation rotation;
        private int minX;
        private int maxX;
        private int minZ;
        private int maxZ;
        /** L 形缺口（如 start_room）的世界座標排除區，怪物不進入；-1 表示未設定 */
        private int excludedMinX = -1;
        private int excludedMaxX = -1;
        private int excludedMinZ = -1;
        private int excludedMaxZ = -1;

        private List<MonsterDef> defs = List.of();
        private BukkitRunnable presenceTask;
        private final Set<UUID> spawnedEntityIds = new HashSet<>();
        private final Set<UUID> targetEntityIds = new HashSet<>();
        private final Set<UUID> endermanSkullIds = new HashSet<>();
        /** 怪物 UUID -> 血條 ArmorStand UUID，用於 per-player 血條顯示 */
        private final Map<UUID, UUID> monsterToNametag = new HashMap<>();
        /** 血條 Stand UUID -> 已對其 hideEntity 的玩家 UUID 集合 */
        private final Map<UUID, Set<UUID>> nametagHiddenFrom = new HashMap<>();

        private GroupState(String groupKey, String templateName) {
            this.groupKey = groupKey;
            this.templateName = templateName;
        }

        private void setPlacement(String worldName, int baseX, int baseY, int baseZ, StructureTemplate.Rotation rotation, int sizeX, int sizeZ, List<MonsterDef> defs) {
            this.worldName = worldName;
            this.baseX = baseX;
            this.baseY = baseY;
            this.baseZ = baseZ;
            this.rotation = rotation;
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
            this.defs = defs != null ? List.copyOf(defs) : List.of();

            int rotSizeX = (rotation == StructureTemplate.Rotation.R90 || rotation == StructureTemplate.Rotation.R270) ? sizeZ : sizeX;
            int rotSizeZ = (rotation == StructureTemplate.Rotation.R90 || rotation == StructureTemplate.Rotation.R270) ? sizeX : sizeZ;
            this.minX = baseX;
            this.maxX = baseX + rotSizeX - 1;
            this.minZ = baseZ;
            this.maxZ = baseZ + rotSizeZ - 1;
        }

        private void setExcludedRegion(int exMinX, int exMaxX, int exMinZ, int exMaxZ) {
            this.excludedMinX = exMinX;
            this.excludedMaxX = exMaxX;
            this.excludedMinZ = exMinZ;
            this.excludedMaxZ = exMaxZ;
        }

        private boolean isInExcludedRegion(int x, int z) {
            if (excludedMinX < 0) return false;
            return x >= excludedMinX && x <= excludedMaxX && z >= excludedMinZ && z <= excludedMaxZ;
        }
    }

    private record FearParalysisState(Location lockLocation, long untilMs, BukkitRunnable task,
                                      boolean prevAllowFlight, boolean prevFlying) {}

    /** L 形結構缺口為 start_room 時，排除該缺口區域，避免怪物/血條進入導致懸空 */
    void setGroupExcludedRegion(String worldName, String groupKey, int exMinX, int exMaxX, int exMinZ, int exMaxZ) {
        if (worldName == null || groupKey == null) return;
        RunState run = runsByWorld.get(worldName);
        if (run == null) return;
        GroupState group = run.groups.get(groupKey);
        if (group != null) group.setExcludedRegion(exMinX, exMaxX, exMinZ, exMaxZ);
    }

    /**
     * 傳送到 last_boss_room 後呼叫：延遲 5 秒在該房生成第一隻 BOSS（逃避型），同一地牢只排程一次。
     */
    public void scheduleFirstBossSpawnAfterTeleport(World world, String floor) {
        if (world == null) return;
        String key = world.getName() + ":" + (floor != null && !floor.isBlank() ? floor : "F1");
        if (!lastBossFirstBossScheduledOrSpawned.add(key)) return;
        maple.underground_caves.dungeon.BossRoomManager bm = plugin.getBossRoomManager();
        if (bm == null) return;
        Location origin = bm.getLastBossRoomOrigin(world.getName(), floor != null ? floor : "F1");
        if (origin == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            spawnLastBossBoss(world, origin, "F1_last_boss_1", 29, 17, 29, true);
        }, LAST_BOSS_FIRST_SPAWN_DELAY_TICKS);
    }

    /**
     * last_boss_room 專用：從 monsters.yml 的 last_boss_f1 讀取 tag 對應配置，在 roomOrigin + (localX, localY, localZ) 生成 BOSS。
     * 玩家模型（頭盔用 skinUuid 頭顱）；evasive=true 時會逃避玩家，false 時主動攻擊。
     */
    public LivingEntity spawnLastBossBoss(World world, Location roomOrigin, String tag, int localX, int localY, int localZ, boolean evasive) {
        if (world == null || roomOrigin == null || tag == null || tag.isBlank()) return null;
        File file = new File(plugin.getDataFolder(), GLOBAL_CONFIG_NAME);
        if (!file.exists()) return null;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        List<?> list = cfg.getList("last_boss_f1");
        if (list == null || list.isEmpty()) return null;
        Map<?, ?> entry = null;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            if (tag.equalsIgnoreCase(str(m.get("tag")))) {
                entry = m;
                break;
            }
        }
        if (entry == null) return null;
        EntityType entityType = parseEntityType(str(entry.get("entity")), EntityType.ZOMBIE);
        if (!entityType.isAlive()) entityType = EntityType.ZOMBIE;
        double hp = Math.max(1, num(entry.get("hp")) != null ? num(entry.get("hp")) : 100);
        double dmg = Math.max(0, num(entry.get("damage")) != null ? num(entry.get("damage")) : 10);
        double def = Math.max(0, num(entry.get("defense")) != null ? num(entry.get("defense")) : 0);
        Double speed = num(entry.get("speed"));
        Double knockbackResistance = num(entry.get("knockbackResistance"));
        if (knockbackResistance == null) knockbackResistance = num(entry.get("knockback_resistance"));
        Double interactionRange = num(entry.get("entityInteractionRange"));
        if (interactionRange == null) interactionRange = num(entry.get("entity_interaction_range"));
        double fearChance = DEFAULT_LAST_BOSS_FEAR_CHANCE;
        long fearCooldownMs = DEFAULT_LAST_BOSS_FEAR_COOLDOWN_MS;
        int fearDurationTicks = DEFAULT_LAST_BOSS_FEAR_DURATION_TICKS;
        double fearDamagePerSec = DEFAULT_LAST_BOSS_FEAR_DAMAGE_PER_SEC;
        double slamChance = DEFAULT_LAST_BOSS_SLAM_CHANCE;
        long slamCooldownMs = DEFAULT_LAST_BOSS_SLAM_COOLDOWN_MS;
        int slamWindupTicks = DEFAULT_LAST_BOSS_SLAM_WINDUP_TICKS;
        double slamRadius = DEFAULT_LAST_BOSS_SLAM_RADIUS;
        double slamDamage = DEFAULT_LAST_BOSS_SLAM_DAMAGE;
        double summonChance = DEFAULT_LAST_BOSS_SUMMON_CHANCE;
        long summonCooldownMs = DEFAULT_LAST_BOSS_SUMMON_COOLDOWN_MS;
        int summonCount = DEFAULT_LAST_BOSS_SUMMON_COUNT;
        double summonHp = DEFAULT_LAST_BOSS_SUMMON_HP;
        double summonDamage = DEFAULT_LAST_BOSS_SUMMON_DAMAGE;
        double summonDefense = DEFAULT_LAST_BOSS_SUMMON_DEFENSE;
        List<EntityType> summonTypes = parseSummonEntityTypes(null);
        Map<EntityType, SummonAddStats> summonTypeStats = new EnumMap<>(EntityType.class);
        Object fearObj = entry.get("fearParalysis");
        if (fearObj instanceof Map<?, ?> fearMap) {
            Double c = num(fearMap.get("chance"));
            Double cd = num(fearMap.get("cooldownSeconds"));
            Double du = num(fearMap.get("durationSeconds"));
            Double dps = num(fearMap.get("damagePerSecond"));
            if (c != null) fearChance = Math.max(0.0, Math.min(1.0, c));
            if (cd != null) fearCooldownMs = Math.max(1000L, (long) (cd * 1000.0));
            if (du != null) fearDurationTicks = Math.max(20, (int) Math.round(du * 20.0));
            if (dps != null) fearDamagePerSec = Math.max(0.1, dps);
        }
        Object slamObj = entry.get("groundSlam");
        if (slamObj instanceof Map<?, ?> slamMap) {
            Double c = num(slamMap.get("chance"));
            Double cd = num(slamMap.get("cooldownSeconds"));
            Double wu = num(slamMap.get("windupSeconds"));
            Double r = num(slamMap.get("radius"));
            Double d = num(slamMap.get("damage"));
            if (c != null) slamChance = Math.max(0.0, Math.min(1.0, c));
            if (cd != null) slamCooldownMs = Math.max(1000L, (long) (cd * 1000.0));
            if (wu != null) slamWindupTicks = Math.max(10, (int) Math.round(wu * 20.0));
            if (r != null) slamRadius = Math.max(2.0, r);
            if (d != null) slamDamage = Math.max(1.0, d);
        }
        Object summonObj = entry.get("summonAdds");
        if (summonObj instanceof Map<?, ?> summonMap) {
            Double c = num(summonMap.get("chance"));
            Double cd = num(summonMap.get("cooldownSeconds"));
            Double ct = num(summonMap.get("count"));
            Double hpAdd = num(summonMap.get("hp"));
            Double dmgAdd = num(summonMap.get("damage"));
            Double defAdd = num(summonMap.get("defense"));
            summonTypes = parseSummonEntityTypes(summonMap.get("types"));
            if (c != null) summonChance = Math.max(0.0, Math.min(1.0, c));
            if (cd != null) summonCooldownMs = Math.max(1000L, (long) (cd * 1000.0));
            if (ct != null) summonCount = Math.max(1, Math.min(6, (int) Math.round(ct)));
            if (hpAdd != null) summonHp = Math.max(1.0, hpAdd);
            if (dmgAdd != null) summonDamage = Math.max(0.0, dmgAdd);
            if (defAdd != null) summonDefense = Math.max(0.0, defAdd);
            summonTypeStats = parseSummonTypeStats(summonMap.get("perType"), summonTypes, summonHp, summonDamage, summonDefense);
        }
        String displayName = str(entry.get("displayName"));
        String skinUuid = str(entry.get("skinUuid"));

        Location spawnAt = roomOrigin.clone().add(localX + 0.5, localY, localZ + 0.5);
        Entity e = world.spawnEntity(spawnAt, entityType);
        if (!(e instanceof LivingEntity living)) {
            e.remove();
            return null;
        }
        if (living instanceof Zombie z) z.setAdult();
        living.setRemoveWhenFarAway(false);
        if (living.getAttribute(Attribute.MAX_HEALTH) != null) living.getAttribute(Attribute.MAX_HEALTH).setBaseValue(hp);
        living.setHealth(Math.min(living.getMaxHealth(), hp));
        if (living.getAttribute(Attribute.ATTACK_DAMAGE) != null) living.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(dmg);
        if (living.getAttribute(Attribute.ARMOR) != null) living.getAttribute(Attribute.ARMOR).setBaseValue(def);
        Attribute attrMovementSpeed = resolveAttributeByKeys("movement_speed", "generic.movement_speed");
        Attribute attrKnockbackResistance = resolveAttributeByKeys("knockback_resistance", "generic.knockback_resistance");
        Attribute attrInteractionRange = resolveAttributeByKeys("entity_interaction_range", "generic.entity_interaction_range", "attack_range", "generic.attack_range", "follow_range", "generic.follow_range");
        if (speed != null && attrMovementSpeed != null && living.getAttribute(attrMovementSpeed) != null) {
            living.getAttribute(attrMovementSpeed).setBaseValue(Math.max(0.0, speed));
        }
        if (knockbackResistance != null && attrKnockbackResistance != null && living.getAttribute(attrKnockbackResistance) != null) {
            living.getAttribute(attrKnockbackResistance).setBaseValue(Math.max(0.0, knockbackResistance));
        }
        if (interactionRange != null && attrInteractionRange != null && living.getAttribute(attrInteractionRange) != null) {
            living.getAttribute(attrInteractionRange).setBaseValue(Math.max(1.0, interactionRange));
        }
        if (displayName != null && !displayName.isBlank()) living.customName(Component.text(displayName, NamedTextColor.RED));
        living.setCustomNameVisible(true);
        // last_boss 也要標記成地牢怪，否則 onMonsterDamage 會提早 return，技能不會觸發
        living.getPersistentDataContainer().set(entityKey, PersistentDataType.BYTE, (byte) 1);
        living.getPersistentDataContainer().set(entityDamageKey, PersistentDataType.DOUBLE, dmg);
        living.getPersistentDataContainer().set(lastBossFearChanceKey, PersistentDataType.DOUBLE, fearChance);
        living.getPersistentDataContainer().set(lastBossFearCooldownMsKey, PersistentDataType.LONG, fearCooldownMs);
        living.getPersistentDataContainer().set(lastBossFearDurationTicksKey, PersistentDataType.INTEGER, fearDurationTicks);
        living.getPersistentDataContainer().set(lastBossFearDamagePerSecKey, PersistentDataType.DOUBLE, fearDamagePerSec);
        living.getPersistentDataContainer().set(lastBossSlamChanceKey, PersistentDataType.DOUBLE, slamChance);
        living.getPersistentDataContainer().set(lastBossSlamCooldownMsKey, PersistentDataType.LONG, slamCooldownMs);
        living.getPersistentDataContainer().set(lastBossSlamWindupTicksKey, PersistentDataType.INTEGER, slamWindupTicks);
        living.getPersistentDataContainer().set(lastBossSlamRadiusKey, PersistentDataType.DOUBLE, slamRadius);
        living.getPersistentDataContainer().set(lastBossSlamDamageKey, PersistentDataType.DOUBLE, slamDamage);
        living.getPersistentDataContainer().set(lastBossSummonChanceKey, PersistentDataType.DOUBLE, summonChance);
        living.getPersistentDataContainer().set(lastBossSummonCooldownMsKey, PersistentDataType.LONG, summonCooldownMs);
        living.getPersistentDataContainer().set(lastBossSummonCountKey, PersistentDataType.INTEGER, summonCount);
        living.getPersistentDataContainer().set(lastBossSummonHpKey, PersistentDataType.DOUBLE, summonHp);
        living.getPersistentDataContainer().set(lastBossSummonDamageKey, PersistentDataType.DOUBLE, summonDamage);
        living.getPersistentDataContainer().set(lastBossSummonDefenseKey, PersistentDataType.DOUBLE, summonDefense);
        living.getPersistentDataContainer().set(lastBossSummonTypesKey, PersistentDataType.STRING, joinEntityTypesCsv(summonTypes));
        living.getPersistentDataContainer().set(lastBossSummonTypeStatsKey, PersistentDataType.STRING, encodeSummonTypeStats(summonTypeStats));

        if (skinUuid != null && !skinUuid.isBlank()) {
            try {
                UUID uuid = UUID.fromString(skinUuid.trim());
                ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
                if (head.getItemMeta() instanceof SkullMeta skullMeta) {
                    skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
                    head.setItemMeta(skullMeta);
                }
                if (living.getEquipment() != null) living.getEquipment().setHelmet(head);
            } catch (Throwable ignored) {}
        }

        if (evasive && living instanceof Mob mob) {
            BukkitRunnable runnable = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!mob.isValid() || mob.isDead()) {
                        cancel();
                        return;
                    }
                    Player nearest = null;
                    double best = 16 * 16;
                    for (Player p : world.getPlayers()) {
                        if (p == null || !p.isOnline()) continue;
                        double d = p.getLocation().distanceSquared(mob.getLocation());
                        if (d < best) {
                            best = d;
                            nearest = p;
                        }
                    }
                    if (nearest != null && best < 12 * 12) {
                        Vector away = mob.getLocation().toVector().subtract(nearest.getLocation().toVector()).normalize().multiply(0.35);
                        mob.setVelocity(away);
                    }
                }
            };
            runnable.runTaskTimer(plugin, 40L, 20L);
        }

        if ("F1_last_boss_1".equalsIgnoreCase(tag)) {
            living.getPersistentDataContainer().set(lastBossPhaseKey, PersistentDataType.STRING, "1");
        }
        return living;
    }

    /**
     * 在指定位置生成 last_boss BOSS（用於第一隻死後在原地生成第二隻）。
     */
    public LivingEntity spawnLastBossBossAt(World world, Location at, String tag, boolean evasive) {
        if (world == null || at == null || tag == null || tag.isBlank()) return null;
        File file = new File(plugin.getDataFolder(), GLOBAL_CONFIG_NAME);
        if (!file.exists()) return null;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        List<?> list = cfg.getList("last_boss_f1");
        if (list == null || list.isEmpty()) return null;
        Map<?, ?> entry = null;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            if (tag.equalsIgnoreCase(str(m.get("tag")))) {
                entry = m;
                break;
            }
        }
        if (entry == null) return null;
        EntityType entityType = parseEntityType(str(entry.get("entity")), EntityType.ZOMBIE);
        if (!entityType.isAlive()) entityType = EntityType.ZOMBIE;
        double hp = Math.max(1, num(entry.get("hp")) != null ? num(entry.get("hp")) : 100);
        double dmg = Math.max(0, num(entry.get("damage")) != null ? num(entry.get("damage")) : 10);
        double def = Math.max(0, num(entry.get("defense")) != null ? num(entry.get("defense")) : 0);
        Double speed = num(entry.get("speed"));
        Double knockbackResistance = num(entry.get("knockbackResistance"));
        if (knockbackResistance == null) knockbackResistance = num(entry.get("knockback_resistance"));
        Double interactionRange = num(entry.get("entityInteractionRange"));
        if (interactionRange == null) interactionRange = num(entry.get("entity_interaction_range"));
        double fearChance = DEFAULT_LAST_BOSS_FEAR_CHANCE;
        long fearCooldownMs = DEFAULT_LAST_BOSS_FEAR_COOLDOWN_MS;
        int fearDurationTicks = DEFAULT_LAST_BOSS_FEAR_DURATION_TICKS;
        double fearDamagePerSec = DEFAULT_LAST_BOSS_FEAR_DAMAGE_PER_SEC;
        double slamChance = DEFAULT_LAST_BOSS_SLAM_CHANCE;
        long slamCooldownMs = DEFAULT_LAST_BOSS_SLAM_COOLDOWN_MS;
        int slamWindupTicks = DEFAULT_LAST_BOSS_SLAM_WINDUP_TICKS;
        double slamRadius = DEFAULT_LAST_BOSS_SLAM_RADIUS;
        double slamDamage = DEFAULT_LAST_BOSS_SLAM_DAMAGE;
        double summonChance = DEFAULT_LAST_BOSS_SUMMON_CHANCE;
        long summonCooldownMs = DEFAULT_LAST_BOSS_SUMMON_COOLDOWN_MS;
        int summonCount = DEFAULT_LAST_BOSS_SUMMON_COUNT;
        double summonHp = DEFAULT_LAST_BOSS_SUMMON_HP;
        double summonDamage = DEFAULT_LAST_BOSS_SUMMON_DAMAGE;
        double summonDefense = DEFAULT_LAST_BOSS_SUMMON_DEFENSE;
        List<EntityType> summonTypes = parseSummonEntityTypes(null);
        Map<EntityType, SummonAddStats> summonTypeStats = new EnumMap<>(EntityType.class);
        Object fearObj = entry.get("fearParalysis");
        if (fearObj instanceof Map<?, ?> fearMap) {
            Double c = num(fearMap.get("chance"));
            Double cd = num(fearMap.get("cooldownSeconds"));
            Double du = num(fearMap.get("durationSeconds"));
            Double dps = num(fearMap.get("damagePerSecond"));
            if (c != null) fearChance = Math.max(0.0, Math.min(1.0, c));
            if (cd != null) fearCooldownMs = Math.max(1000L, (long) (cd * 1000.0));
            if (du != null) fearDurationTicks = Math.max(20, (int) Math.round(du * 20.0));
            if (dps != null) fearDamagePerSec = Math.max(0.1, dps);
        }
        Object slamObj = entry.get("groundSlam");
        if (slamObj instanceof Map<?, ?> slamMap) {
            Double c = num(slamMap.get("chance"));
            Double cd = num(slamMap.get("cooldownSeconds"));
            Double wu = num(slamMap.get("windupSeconds"));
            Double r = num(slamMap.get("radius"));
            Double d = num(slamMap.get("damage"));
            if (c != null) slamChance = Math.max(0.0, Math.min(1.0, c));
            if (cd != null) slamCooldownMs = Math.max(1000L, (long) (cd * 1000.0));
            if (wu != null) slamWindupTicks = Math.max(10, (int) Math.round(wu * 20.0));
            if (r != null) slamRadius = Math.max(2.0, r);
            if (d != null) slamDamage = Math.max(1.0, d);
        }
        Object summonObj = entry.get("summonAdds");
        if (summonObj instanceof Map<?, ?> summonMap) {
            Double c = num(summonMap.get("chance"));
            Double cd = num(summonMap.get("cooldownSeconds"));
            Double ct = num(summonMap.get("count"));
            Double hpAdd = num(summonMap.get("hp"));
            Double dmgAdd = num(summonMap.get("damage"));
            Double defAdd = num(summonMap.get("defense"));
            summonTypes = parseSummonEntityTypes(summonMap.get("types"));
            if (c != null) summonChance = Math.max(0.0, Math.min(1.0, c));
            if (cd != null) summonCooldownMs = Math.max(1000L, (long) (cd * 1000.0));
            if (ct != null) summonCount = Math.max(1, Math.min(6, (int) Math.round(ct)));
            if (hpAdd != null) summonHp = Math.max(1.0, hpAdd);
            if (dmgAdd != null) summonDamage = Math.max(0.0, dmgAdd);
            if (defAdd != null) summonDefense = Math.max(0.0, defAdd);
            summonTypeStats = parseSummonTypeStats(summonMap.get("perType"), summonTypes, summonHp, summonDamage, summonDefense);
        }
        String displayName = str(entry.get("displayName"));
        String skinUuid = str(entry.get("skinUuid"));

        Entity e = world.spawnEntity(at.clone(), entityType);
        if (!(e instanceof LivingEntity living)) {
            e.remove();
            return null;
        }
        if (living instanceof Zombie z) z.setAdult();
        living.setRemoveWhenFarAway(false);
        if (living.getAttribute(Attribute.MAX_HEALTH) != null) living.getAttribute(Attribute.MAX_HEALTH).setBaseValue(hp);
        living.setHealth(Math.min(living.getMaxHealth(), hp));
        if (living.getAttribute(Attribute.ATTACK_DAMAGE) != null) living.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(dmg);
        if (living.getAttribute(Attribute.ARMOR) != null) living.getAttribute(Attribute.ARMOR).setBaseValue(def);
        Attribute attrMovementSpeed = resolveAttributeByKeys("movement_speed", "generic.movement_speed");
        Attribute attrKnockbackResistance = resolveAttributeByKeys("knockback_resistance", "generic.knockback_resistance");
        Attribute attrInteractionRange = resolveAttributeByKeys("entity_interaction_range", "generic.entity_interaction_range", "attack_range", "generic.attack_range", "follow_range", "generic.follow_range");
        if (speed != null && attrMovementSpeed != null && living.getAttribute(attrMovementSpeed) != null) {
            living.getAttribute(attrMovementSpeed).setBaseValue(Math.max(0.0, speed));
        }
        if (knockbackResistance != null && attrKnockbackResistance != null && living.getAttribute(attrKnockbackResistance) != null) {
            living.getAttribute(attrKnockbackResistance).setBaseValue(Math.max(0.0, knockbackResistance));
        }
        if (interactionRange != null && attrInteractionRange != null && living.getAttribute(attrInteractionRange) != null) {
            living.getAttribute(attrInteractionRange).setBaseValue(Math.max(1.0, interactionRange));
        }
        if (displayName != null && !displayName.isBlank()) living.customName(Component.text(displayName, NamedTextColor.RED));
        living.setCustomNameVisible(true);
        // last_boss 也要標記成地牢怪，否則 onMonsterDamage 會提早 return，技能不會觸發
        living.getPersistentDataContainer().set(entityKey, PersistentDataType.BYTE, (byte) 1);
        living.getPersistentDataContainer().set(entityDamageKey, PersistentDataType.DOUBLE, dmg);
        living.getPersistentDataContainer().set(lastBossFearChanceKey, PersistentDataType.DOUBLE, fearChance);
        living.getPersistentDataContainer().set(lastBossFearCooldownMsKey, PersistentDataType.LONG, fearCooldownMs);
        living.getPersistentDataContainer().set(lastBossFearDurationTicksKey, PersistentDataType.INTEGER, fearDurationTicks);
        living.getPersistentDataContainer().set(lastBossFearDamagePerSecKey, PersistentDataType.DOUBLE, fearDamagePerSec);
        living.getPersistentDataContainer().set(lastBossSlamChanceKey, PersistentDataType.DOUBLE, slamChance);
        living.getPersistentDataContainer().set(lastBossSlamCooldownMsKey, PersistentDataType.LONG, slamCooldownMs);
        living.getPersistentDataContainer().set(lastBossSlamWindupTicksKey, PersistentDataType.INTEGER, slamWindupTicks);
        living.getPersistentDataContainer().set(lastBossSlamRadiusKey, PersistentDataType.DOUBLE, slamRadius);
        living.getPersistentDataContainer().set(lastBossSlamDamageKey, PersistentDataType.DOUBLE, slamDamage);
        living.getPersistentDataContainer().set(lastBossSummonChanceKey, PersistentDataType.DOUBLE, summonChance);
        living.getPersistentDataContainer().set(lastBossSummonCooldownMsKey, PersistentDataType.LONG, summonCooldownMs);
        living.getPersistentDataContainer().set(lastBossSummonCountKey, PersistentDataType.INTEGER, summonCount);
        living.getPersistentDataContainer().set(lastBossSummonHpKey, PersistentDataType.DOUBLE, summonHp);
        living.getPersistentDataContainer().set(lastBossSummonDamageKey, PersistentDataType.DOUBLE, summonDamage);
        living.getPersistentDataContainer().set(lastBossSummonDefenseKey, PersistentDataType.DOUBLE, summonDefense);
        living.getPersistentDataContainer().set(lastBossSummonTypesKey, PersistentDataType.STRING, joinEntityTypesCsv(summonTypes));
        living.getPersistentDataContainer().set(lastBossSummonTypeStatsKey, PersistentDataType.STRING, encodeSummonTypeStats(summonTypeStats));

        if (skinUuid != null && !skinUuid.isBlank()) {
            try {
                UUID uuid = UUID.fromString(skinUuid.trim());
                ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
                if (head.getItemMeta() instanceof SkullMeta skullMeta) {
                    skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
                    head.setItemMeta(skullMeta);
                }
                if (living.getEquipment() != null) living.getEquipment().setHelmet(head);
            } catch (Throwable ignored) {}
        }

        if (evasive && living instanceof Mob mob) {
            BukkitRunnable runnable = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!mob.isValid() || mob.isDead()) {
                        cancel();
                        return;
                    }
                    Player nearest = null;
                    double best = 16 * 16;
                    for (Player p : world.getPlayers()) {
                        if (p == null || !p.isOnline()) continue;
                        double d = p.getLocation().distanceSquared(mob.getLocation());
                        if (d < best) {
                            best = d;
                            nearest = p;
                        }
                    }
                    if (nearest != null && best < 12 * 12) {
                        Vector away = mob.getLocation().toVector().subtract(nearest.getLocation().toVector()).normalize().multiply(0.35);
                        mob.setVelocity(away);
                    }
                }
            };
            runnable.runTaskTimer(plugin, 40L, 20L);
        }
        if ("F1_last_boss_1".equalsIgnoreCase(tag)) {
            living.getPersistentDataContainer().set(lastBossPhaseKey, PersistentDataType.STRING, "1");
        } else if ("F1_last_boss_2".equalsIgnoreCase(tag)) {
            living.getPersistentDataContainer().set(lastBossPhaseKey, PersistentDataType.STRING, "2");
        }
        return living;
    }

    private static Attribute resolveAttributeByKeys(String... keys) {
        if (keys == null || keys.length == 0) return null;
        try {
            for (String k : keys) {
                if (k == null || k.isBlank()) continue;
                Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(k));
                if (a != null) return a;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static List<EntityType> parseSummonEntityTypes(Object raw) {
        List<EntityType> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                EntityType t = resolveSummonEntityType(o != null ? String.valueOf(o) : null);
                if (t != null && !out.contains(t)) out.add(t);
            }
        } else if (raw instanceof String s && !s.isBlank()) {
            String[] parts = s.split("[,|\\s]+");
            for (String p : parts) {
                EntityType t = resolveSummonEntityType(p);
                if (t != null && !out.contains(t)) out.add(t);
            }
        }
        if (out.isEmpty()) {
            out.add(EntityType.ZOMBIE);
            out.add(EntityType.SKELETON);
            out.add(EntityType.SPIDER);
            out.add(EntityType.HUSK);
            out.add(EntityType.SILVERFISH);
        }
        return out;
    }

    private static EntityType resolveSummonEntityType(String token) {
        if (token == null || token.isBlank()) return null;
        String key = token.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            EntityType t = EntityType.valueOf(key);
            return t.isAlive() ? t : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String joinEntityTypesCsv(List<EntityType> types) {
        List<EntityType> safe = (types == null || types.isEmpty()) ? parseSummonEntityTypes(DEFAULT_LAST_BOSS_SUMMON_TYPES_CSV) : types;
        StringBuilder sb = new StringBuilder();
        for (EntityType t : safe) {
            if (t == null) continue;
            if (!sb.isEmpty()) sb.append(",");
            sb.append(t.name());
        }
        return sb.isEmpty() ? DEFAULT_LAST_BOSS_SUMMON_TYPES_CSV : sb.toString();
    }

    private static Map<EntityType, SummonAddStats> parseSummonTypeStats(Object raw, List<EntityType> summonTypes, double defaultHp, double defaultDamage, double defaultDefense) {
        Map<EntityType, SummonAddStats> out = new EnumMap<>(EntityType.class);
        if (!(raw instanceof Map<?, ?> map)) return out;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            EntityType type = resolveSummonEntityType(entry.getKey() != null ? String.valueOf(entry.getKey()) : null);
            if (type == null) continue;
            if (!(entry.getValue() instanceof Map<?, ?> statMap)) continue;
            Double hpRaw = num(statMap.get("hp"));
            Double damageRaw = num(statMap.get("damage"));
            Double defenseRaw = num(statMap.get("defense"));
            double hp = hpRaw != null ? Math.max(1.0, hpRaw) : defaultHp;
            double damage = damageRaw != null ? Math.max(0.0, damageRaw) : defaultDamage;
            double defense = defenseRaw != null ? Math.max(0.0, defenseRaw) : defaultDefense;
            out.put(type, new SummonAddStats(hp, damage, defense));
            if (summonTypes != null && !summonTypes.contains(type)) summonTypes.add(type);
        }
        return out;
    }

    private static String encodeSummonTypeStats(Map<EntityType, SummonAddStats> statsByType) {
        if (statsByType == null || statsByType.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<EntityType, SummonAddStats> entry : statsByType.entrySet()) {
            EntityType type = entry.getKey();
            SummonAddStats stats = entry.getValue();
            if (type == null || stats == null) continue;
            if (!sb.isEmpty()) sb.append(';');
            sb.append(type.name()).append(':')
              .append(stats.hp()).append(',')
              .append(stats.damage()).append(',')
              .append(stats.defense());
        }
        return sb.toString();
    }

    private static Map<EntityType, SummonAddStats> decodeSummonTypeStats(String rawSpec) {
        Map<EntityType, SummonAddStats> out = new EnumMap<>(EntityType.class);
        if (rawSpec == null || rawSpec.isBlank()) return out;
        String[] entries = rawSpec.split(";");
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) continue;
            String[] pair = entry.split(":", 2);
            if (pair.length != 2) continue;
            EntityType type = resolveSummonEntityType(pair[0]);
            if (type == null) continue;
            String[] nums = pair[1].split(",", 3);
            if (nums.length != 3) continue;
            Double hp = parseDoubleSafe(nums[0]);
            Double damage = parseDoubleSafe(nums[1]);
            Double defense = parseDoubleSafe(nums[2]);
            if (hp == null || damage == null || defense == null) continue;
            out.put(type, new SummonAddStats(Math.max(1.0, hp), Math.max(0.0, damage), Math.max(0.0, defense)));
        }
        return out;
    }

    private static Double parseDoubleSafe(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Double.parseDouble(raw.trim());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void maybeTriggerLastBossFearParalysis(LivingEntity source, Player victim) {
        if (source == null || victim == null) return;
        String phase = source.getPersistentDataContainer().get(lastBossPhaseKey, PersistentDataType.STRING);
        if (!"1".equals(phase) && !"2".equals(phase)) return;
        if (!victim.isOnline() || victim.isDead()) return;

        double chance = source.getPersistentDataContainer().getOrDefault(lastBossFearChanceKey, PersistentDataType.DOUBLE, DEFAULT_LAST_BOSS_FEAR_CHANCE);
        long cooldownMs = source.getPersistentDataContainer().getOrDefault(lastBossFearCooldownMsKey, PersistentDataType.LONG, DEFAULT_LAST_BOSS_FEAR_COOLDOWN_MS);
        int durationTicks = source.getPersistentDataContainer().getOrDefault(lastBossFearDurationTicksKey, PersistentDataType.INTEGER, DEFAULT_LAST_BOSS_FEAR_DURATION_TICKS);
        double damagePerSec = source.getPersistentDataContainer().getOrDefault(lastBossFearDamagePerSecKey, PersistentDataType.DOUBLE, DEFAULT_LAST_BOSS_FEAR_DAMAGE_PER_SEC);
        chance = Math.max(0.0, Math.min(1.0, chance));
        cooldownMs = Math.max(1000L, cooldownMs);
        durationTicks = Math.max(20, durationTicks);
        damagePerSec = Math.max(0.1, damagePerSec);

        long now = System.currentTimeMillis();
        Long cdUntil = lastBossFearBossCooldownUntilMs.get(source.getUniqueId());
        if (cdUntil != null && now < cdUntil) return;
        if (ThreadLocalRandom.current().nextDouble() > chance) return;
        lastBossFearBossCooldownUntilMs.put(source.getUniqueId(), now + cooldownMs);
        applyFearParalysis(source, victim, durationTicks, damagePerSec);
    }

    private void maybeTriggerLastBossGroundSlam(LivingEntity source, Player victim) {
        if (source == null || victim == null) return;
        String phase = source.getPersistentDataContainer().get(lastBossPhaseKey, PersistentDataType.STRING);
        if (!"1".equals(phase) && !"2".equals(phase)) return;
        double chance = source.getPersistentDataContainer().getOrDefault(lastBossSlamChanceKey, PersistentDataType.DOUBLE, DEFAULT_LAST_BOSS_SLAM_CHANCE);
        long cooldownMs = source.getPersistentDataContainer().getOrDefault(lastBossSlamCooldownMsKey, PersistentDataType.LONG, DEFAULT_LAST_BOSS_SLAM_COOLDOWN_MS);
        int windupTicks = source.getPersistentDataContainer().getOrDefault(lastBossSlamWindupTicksKey, PersistentDataType.INTEGER, DEFAULT_LAST_BOSS_SLAM_WINDUP_TICKS);
        double radius = source.getPersistentDataContainer().getOrDefault(lastBossSlamRadiusKey, PersistentDataType.DOUBLE, DEFAULT_LAST_BOSS_SLAM_RADIUS);
        double damage = source.getPersistentDataContainer().getOrDefault(lastBossSlamDamageKey, PersistentDataType.DOUBLE, DEFAULT_LAST_BOSS_SLAM_DAMAGE);
        chance = Math.max(0.0, Math.min(1.0, chance));
        cooldownMs = Math.max(1000L, cooldownMs);
        windupTicks = Math.max(10, windupTicks);
        radius = Math.max(2.0, radius);
        damage = Math.max(1.0, damage);
        long now = System.currentTimeMillis();
        Long cdUntil = lastBossSlamCooldownUntilMs.get(source.getUniqueId());
        if (cdUntil != null && now < cdUntil) return;
        if (ThreadLocalRandom.current().nextDouble() > chance) return;
        lastBossSlamCooldownUntilMs.put(source.getUniqueId(), now + cooldownMs);
        castLastBossGroundSlam(source, windupTicks, radius, damage);
    }

    private void castLastBossGroundSlam(LivingEntity source, int windupTicks, double radius, double damage) {
        if (source == null || !source.isValid() || source.isDead()) return;
        World world = source.getWorld();
        if (world == null) return;
        Location center = source.getLocation().clone();
        Component warn = bossThemeText("【終關守護】地裂重擊蓄力中，快離開範圍！");
        for (Player p : world.getPlayers()) {
            if (p == null || !p.isOnline() || p.isDead()) continue;
            if (p.getLocation().distanceSquared(center) <= 24 * 24) p.sendMessage(warn);
        }
        new BukkitRunnable() {
            private int tick;
            @Override
            public void run() {
                if (source.isDead() || !source.isValid()) {
                    cancel();
                    return;
                }
                tick += 2;
                world.spawnParticle(Particle.SMOKE, center.clone().add(0, 0.2, 0), 18, radius * 0.3, 0.1, radius * 0.3, 0.01);
                if (tick < windupTicks) return;
                world.spawnParticle(Particle.EXPLOSION, center.clone().add(0, 0.2, 0), 2, 0.2, 0.1, 0.2, 0.0);
                for (Player p : world.getPlayers()) {
                    if (p == null || !p.isOnline() || p.isDead()) continue;
                    if (p.getLocation().distanceSquared(center) > radius * radius) continue;
                    try {
                        p.damage(damage, source);
                    } catch (Throwable ignored) {
                    }
                    Vector kb = p.getLocation().toVector().subtract(center.toVector());
                    if (kb.lengthSquared() < 1.0E-4) kb = new Vector(0, 0, 1);
                    kb.normalize().multiply(0.6).setY(0.35);
                    p.setVelocity(kb);
                    p.sendMessage(bossThemeText("你被【地裂重擊】命中！"));
                }
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void maybeTriggerLastBossSummonAdds(LivingEntity source, Player victim) {
        if (source == null || victim == null) return;
        String phase = source.getPersistentDataContainer().get(lastBossPhaseKey, PersistentDataType.STRING);
        if (!"1".equals(phase) && !"2".equals(phase)) return;
        double chance = source.getPersistentDataContainer().getOrDefault(lastBossSummonChanceKey, PersistentDataType.DOUBLE, DEFAULT_LAST_BOSS_SUMMON_CHANCE);
        long cooldownMs = source.getPersistentDataContainer().getOrDefault(lastBossSummonCooldownMsKey, PersistentDataType.LONG, DEFAULT_LAST_BOSS_SUMMON_COOLDOWN_MS);
        int count = source.getPersistentDataContainer().getOrDefault(lastBossSummonCountKey, PersistentDataType.INTEGER, DEFAULT_LAST_BOSS_SUMMON_COUNT);
        double hp = source.getPersistentDataContainer().getOrDefault(lastBossSummonHpKey, PersistentDataType.DOUBLE, DEFAULT_LAST_BOSS_SUMMON_HP);
        double damage = source.getPersistentDataContainer().getOrDefault(lastBossSummonDamageKey, PersistentDataType.DOUBLE, DEFAULT_LAST_BOSS_SUMMON_DAMAGE);
        double defense = source.getPersistentDataContainer().getOrDefault(lastBossSummonDefenseKey, PersistentDataType.DOUBLE, DEFAULT_LAST_BOSS_SUMMON_DEFENSE);
        String typesCsv = source.getPersistentDataContainer().getOrDefault(lastBossSummonTypesKey, PersistentDataType.STRING, DEFAULT_LAST_BOSS_SUMMON_TYPES_CSV);
        String typeStatsSpec = source.getPersistentDataContainer().getOrDefault(lastBossSummonTypeStatsKey, PersistentDataType.STRING, "");
        List<EntityType> summonTypes = parseSummonEntityTypes(typesCsv);
        Map<EntityType, SummonAddStats> summonTypeStats = decodeSummonTypeStats(typeStatsSpec);
        chance = Math.max(0.0, Math.min(1.0, chance));
        cooldownMs = Math.max(1000L, cooldownMs);
        count = Math.max(1, Math.min(6, count));
        hp = Math.max(1.0, hp);
        damage = Math.max(0.0, damage);
        defense = Math.max(0.0, defense);
        long now = System.currentTimeMillis();
        Long cdUntil = lastBossSummonCooldownUntilMs.get(source.getUniqueId());
        if (cdUntil != null && now < cdUntil) return;
        if (ThreadLocalRandom.current().nextDouble() > chance) return;
        lastBossSummonCooldownUntilMs.put(source.getUniqueId(), now + cooldownMs);
        spawnLastBossAdds(source, count, hp, damage, defense, summonTypes, summonTypeStats);
    }

    private void spawnLastBossAdds(LivingEntity source, int count, double hp, double damage, double defense, List<EntityType> summonTypes,
                                   Map<EntityType, SummonAddStats> summonTypeStats) {
        if (source == null || source.isDead() || !source.isValid()) return;
        World world = source.getWorld();
        if (world == null) return;
        List<EntityType> typePool = parseSummonEntityTypes(summonTypes);
        Location center = source.getLocation();
        int aliveAdds = 0;
        for (Entity e : world.getNearbyEntities(center, 16, 8, 16)) {
            if (!(e instanceof LivingEntity le)) continue;
            Byte mark = le.getPersistentDataContainer().get(lastBossSummonedAddKey, PersistentDataType.BYTE);
            if (mark != null && mark == (byte) 1 && !le.isDead()) aliveAdds++;
        }
        int canSpawn = Math.max(0, 6 - aliveAdds);
        if (canSpawn <= 0) return;
        count = Math.min(count, canSpawn);
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            Location at = findNearbySafeSpawnAround(center, 6, 24);
            if (at == null) continue;
            EntityType pick = typePool.get(ThreadLocalRandom.current().nextInt(typePool.size()));
            SummonAddStats pickStats = summonTypeStats != null ? summonTypeStats.get(pick) : null;
            double pickHp = pickStats != null ? pickStats.hp() : hp;
            double pickDamage = pickStats != null ? pickStats.damage() : damage;
            double pickDefense = pickStats != null ? pickStats.defense() : defense;
            Entity e = world.spawnEntity(at, pick);
            if (!(e instanceof LivingEntity add)) {
                e.remove();
                continue;
            }
            if (add instanceof Zombie z) z.setAdult();
            add.setRemoveWhenFarAway(false);
            if (add.getAttribute(Attribute.MAX_HEALTH) != null) add.getAttribute(Attribute.MAX_HEALTH).setBaseValue(pickHp);
            add.setHealth(Math.min(pickHp, add.getAttribute(Attribute.MAX_HEALTH) != null ? add.getAttribute(Attribute.MAX_HEALTH).getValue() : pickHp));
            if (add.getAttribute(Attribute.ATTACK_DAMAGE) != null) add.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(pickDamage);
            if (add.getAttribute(Attribute.ARMOR) != null) add.getAttribute(Attribute.ARMOR).setBaseValue(pickDefense);
            add.customName(Component.text("終關援軍", NamedTextColor.GRAY));
            add.setCustomNameVisible(true);
            PersistentDataContainer pdc = add.getPersistentDataContainer();
            pdc.set(entityKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(entityDamageKey, PersistentDataType.DOUBLE, pickDamage);
            pdc.set(lastBossSummonedAddKey, PersistentDataType.BYTE, (byte) 1);
            spawned++;
        }
        if (spawned <= 0) return;
        Component msg = bossThemeText("【終關守護】召喚了援軍！");
        for (Player p : world.getPlayers()) {
            if (p == null || !p.isOnline() || p.isDead()) continue;
            if (p.getLocation().distanceSquared(center) <= 28 * 28) p.sendMessage(msg);
        }
    }

    /**
     * 尋找可站立且頭部有空間的安全生成點，避免援軍出生在方塊內窒息。
     */
    private static Location findNearbySafeSpawnAround(Location center, int radius, int attempts) {
        if (center == null) return null;
        World world = center.getWorld();
        if (world == null) return null;
        int baseX = center.getBlockX();
        int baseY = center.getBlockY();
        int baseZ = center.getBlockZ();
        for (int i = 0; i < Math.max(1, attempts); i++) {
            int dx = ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int dz = ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int x = baseX + dx;
            int z = baseZ + dz;
            for (int dy = -2; dy <= 2; dy++) {
                int y = baseY + dy;
                Block feet = world.getBlockAt(x, y, z);
                Block head = world.getBlockAt(x, y + 1, z);
                Block below = world.getBlockAt(x, y - 1, z);
                if (!feet.isPassable()) continue;
                if (!head.isPassable()) continue;
                if (below.isPassable()) continue;
                return new Location(world, x + 0.5, y, z + 0.5, center.getYaw(), center.getPitch());
            }
        }
        return null;
    }

    private void applyFearParalysis(LivingEntity source, Player victim, int durationTicks, double damagePerSec) {
        if (victim == null || !victim.isOnline() || victim.isDead()) return;
        clearFearParalysis(victim.getUniqueId());
        Location lock = victim.getLocation().clone();
        long untilMs = System.currentTimeMillis() + Math.max(20, durationTicks) * 50L;
        boolean prevAllowFlight = victim.getAllowFlight();
        boolean prevFlying = victim.isFlying();
        // 防止玩家在半空被定身時觸發伺服器 anti-fly 踢出
        try {
            victim.setAllowFlight(true);
            victim.setFlying(false);
        } catch (Throwable ignored) {
        }
        BukkitRunnable task = new BukkitRunnable() {
            private int livedTicks = 0;

            @Override
            public void run() {
                FearParalysisState state = fearParalysisStates.get(victim.getUniqueId());
                if (state == null || state.task() != this) {
                    cancel();
                    return;
                }
                if (!victim.isOnline() || victim.isDead()) {
                    clearFearParalysis(victim.getUniqueId());
                    return;
                }
                victim.setFallDistance(0f);
                long now = System.currentTimeMillis();
                if (now > state.untilMs() + FEAR_PARALYSIS_REAPPLY_GRACE_MS) {
                    clearFearParalysis(victim.getUniqueId());
                    return;
                }
                livedTicks++;
                if (livedTicks % 20 == 0) {
                    int remainSec = Math.max(1, (int) Math.ceil((state.untilMs() - now) / 1000.0));
                    victim.sendActionBar(bossThemeText("【恐懼麻痺】剩餘 " + remainSec + " 秒"));
                } else {
                    return;
                }
                double hp = victim.getHealth();
                if (hp <= 0.1) return;
                double realDamage = Math.min(Math.max(0.1, damagePerSec), Math.max(0.0, hp - 0.1));
                if (realDamage <= 0.0) return;
                try {
                    if (source != null && source.isValid() && !source.isDead()) {
                        victim.damage(realDamage, source);
                    } else {
                        victim.damage(realDamage);
                    }
                } catch (Throwable ignored) {
                }
            }
        };
        fearParalysisStates.put(victim.getUniqueId(), new FearParalysisState(lock, untilMs, task, prevAllowFlight, prevFlying));
        task.runTaskTimer(plugin, 1L, 1L);
        victim.sendMessage(bossThemeText("你遭到【恐懼麻痺】綑綁，5秒內無法移動！"));
        World w = victim.getWorld();
        if (w != null) {
            Component msg = bossThemeText("玩家 " + victim.getName() + " 遭到【恐懼麻痺】綑綁！（5秒）");
            for (Player p : w.getPlayers()) {
                if (p == null || !p.isOnline()) continue;
                p.sendMessage(msg);
            }
        }
    }

    private void clearFearParalysis(UUID playerId) {
        if (playerId == null) return;
        FearParalysisState prev = fearParalysisStates.remove(playerId);
        if (prev == null) return;
        if (prev.task() != null) prev.task().cancel();
        Player p = Bukkit.getPlayer(playerId);
        if (p == null || !p.isOnline()) return;
        try {
            // 先關飛行狀態，再還原是否可飛，避免「flying=true 但 allowFlight=false」例外
            p.setFlying(prev.prevFlying() && prev.prevAllowFlight());
            p.setAllowFlight(prev.prevAllowFlight());
        } catch (Throwable ignored) {
        }
    }

    private static final int LAST_BOSS_CHEST_CENTER_X = 29;
    private static final int LAST_BOSS_CHEST_CENTER_Y = 16;
    private static final int LAST_BOSS_CHEST_CENTER_Z = 29;

    /** F1–F4 生成五箱（銅/銀/金/白金/紅）；F5+ 再加第六箱（橙）。第二隻 last boss 死亡時在 last_boss_room 生成箱子盔甲架。 */
    private void spawnLastBossChestsOnSecondBossDeath(World world) {
        if (world == null) return;
        Test_dungeons.DungeonSnapshot snapshot = plugin != null ? plugin.getLastDungeon() : null;
        if (snapshot == null || !world.getName().equals(snapshot.worldName())) return;
        maple.underground_caves.dungeon.BossRoomManager bm = plugin.getBossRoomManager();
        if (bm == null) return;
        Location origin = bm.getLastBossRoomOrigin(world.getName(), snapshot.floor());
        if (origin == null) return;

        String floor = snapshot.floor();
        boolean f1ToF4 = "F1".equals(floor) || "F2".equals(floor) || "F3".equals(floor) || "F4".equals(floor);

        // 順序：1 bronze 2 silver 3 gold 4 platinum 5 red 6 orange
        spawnLastBossChestStand(world, origin, 29.52, 13.70, 25.48, "3f19bdfdfaa39949da0667f4a89f36426ba8692aef51fc6051a9802327da7", "bronze");
        spawnLastBossChestStand(world, origin, 33.50, 13.7, 29.50, "c0c44d1a8cb4d7acbf86122acbd9fcfaf9dbe4a4edf389a887b24ce432a0844a", "silver");
        spawnLastBossChestStand(world, origin, 29.50, 13.7, 33.50, "c3bdbaedd7d6444e79aa8222f8981240204cc3cc1c9655118687618db8cec", "gold");
        spawnLastBossChestStand(world, origin, 25.50, 13.7, 29.50, "215fff8d8e8dadc26df471b6c619267dfd2746723cc225c7b3bbf2f11e9aa2b9", "platinum");
        // 第 5 箱：red，F1–F4 與 F5+ 都出
        spawnLastBossChestStand(world, origin, 21.50, 11.7, 29.50, "2c6233d57c95e85e11d4dbdc8ba821252d14d5bfafccd4ebfa6ac27079ab35f7", "red");
        // 第 6 箱：橙 (orange)，僅 F5+ 出
        if (!f1ToF4) {
            spawnLastBossChestStand(world, origin, 37.50, 11.7, 29.50, "322d4be1abcf3832c916191d24f9607bf194eff8dfbf3b9520bd97240e7c8", "orange");
        }
        setLastBossChestStand(world, "bronze", 29.52, 13.70, 25.48, yaw4TowardCenter(29.52, 25.48), false, 1.6);
        setLastBossChestStand(world, "silver", 33.50, 13.7, 29.50, yaw4TowardCenter(33.50, 29.50), false, 1.6);
        setLastBossChestStand(world, "gold", 29.50, 13.7, 33.50, yaw4TowardCenter(29.50, 33.50), false, 1.6);
        setLastBossChestStand(world, "platinum", 25.50, 13.7, 29.50, yaw4TowardCenter(25.50, 29.50), false, 1.6);
        setLastBossChestStand(world, "red", 21.50, 11.7, 29.50, yaw4TowardCenter(21.50, 29.50), false, 3.0);
        if (!f1ToF4) {
            setLastBossChestStand(world, "orange", 37.50, 11.7, 29.50, yaw4TowardCenter(37.50, 29.50), false, 3.0);
        }
    }

    /** 依 local (lx,lz) 計算朝向中心 (29,29) 的四向 yaw4（0/1/2/3）。 */
    private static int yaw4TowardCenter(double lx, double lz) {
        float yaw = yawTowardCenter4Dir((int) Math.round(lx), (int) Math.round(lz));
        return ((int) yaw / 90) % 4;
    }

    /** 朝向 (29,16,29) 的 yaw，只取四向 0/90/180/270。 */
    private static float yawTowardCenter4Dir(int lx, int lz) {
        double dx = LAST_BOSS_CHEST_CENTER_X - lx;
        double dz = LAST_BOSS_CHEST_CENTER_Z - lz;
        double yawDeg = Math.toDegrees(Math.atan2(-dx, dz));
        while (yawDeg < 0) yawDeg += 360;
        while (yawDeg >= 360) yawDeg -= 360;
        int quarter = (int) Math.round(yawDeg / 90.0) % 4;
        if (quarter < 0) quarter += 4;
        return quarter * 90f;
    }

    private void spawnLastBossChestStand(World world, Location origin, double lx, double ly, double lz, String textureId, String chestId) {
        Location at = origin.clone().add(lx, ly, lz);
        float yaw = yawTowardCenter4Dir((int) Math.round(lx), (int) Math.round(lz));
        at.setYaw(yaw);
        world.spawn(at, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setSmall(true);
            as.setGravity(false);
            as.setMarker(true);
            as.setBasePlate(false);
            as.setArms(false);
            as.setSilent(true);
            as.setCollidable(false);
            as.setInvulnerable(true);
            as.setRemoveWhenFarAway(false);
            as.teleport(at);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
            if (head.getItemMeta() instanceof SkullMeta skullMeta && textureId != null && !textureId.isEmpty()) {
                DungeonSecrets.applySkullTextureToMeta(skullMeta, textureId, "DungeonMonsters.lastBossChest." + chestId);
                head.setItemMeta(skullMeta);
            }
            if (as.getEquipment() != null) as.getEquipment().setHelmet(head);
            as.getPersistentDataContainer().set(lastBossChestKey, PersistentDataType.STRING, chestId);
        });
    }

    /** 透過 Registry 取得 scale 屬性（1.21 可能為 minecraft:scale，舊版為 generic.scale），不存在則回傳 null。 */
    private static Attribute getScaleAttribute() {
        try {
            Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("scale"));
            if (a != null) return a;
            return Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.scale"));
        } catch (Throwable t) {
            return null;
        }
    }

    /** 細調：將指定 id 的箱子盔甲架移動到 local(lx,ly,lz)、設定 yaw（四向）、可選 small 或 scale（高版本 scale 屬性）。lx,ly,lz 支援小數。 */
    public boolean setLastBossChestStand(World world, String chestId, double lx, double ly, double lz, int yaw4, boolean small, Double scale) {
        if (world == null || chestId == null) return false;
        String normalizedTargetId = ChestLootManager.normalizeChestId(chestId);
        if (normalizedTargetId == null || normalizedTargetId.isBlank()) normalizedTargetId = chestId.toLowerCase(java.util.Locale.ROOT);
        maple.underground_caves.dungeon.BossRoomManager bm = plugin.getBossRoomManager();
        if (bm == null) return false;
        Test_dungeons.DungeonSnapshot snapshot = plugin.getLastDungeon();
        if (snapshot == null || !world.getName().equals(snapshot.worldName())) return false;
        Location origin = bm.getLastBossRoomOrigin(world.getName(), snapshot.floor());
        if (origin == null) return false;
        int yaw = ((yaw4 % 4) + 4) % 4 * 90;
        for (Entity e : world.getEntitiesByClass(org.bukkit.entity.ArmorStand.class)) {
            if (!(e instanceof ArmorStand as)) continue;
            String id = as.getPersistentDataContainer().get(lastBossChestKey, PersistentDataType.STRING);
            String normalizedId = ChestLootManager.normalizeChestId(id);
            if (!normalizedTargetId.equalsIgnoreCase(normalizedId)) continue;
            if (normalizedId != null && id != null && !normalizedId.equalsIgnoreCase(id)) {
                as.getPersistentDataContainer().set(lastBossChestKey, PersistentDataType.STRING, normalizedId);
            }
            Location to = origin.clone().add(lx, ly, lz);
            to.setYaw(yaw);
            as.teleport(to);
            if (scale != null && scale > 0) {
                boolean scaleApplied = false;
                if (as instanceof org.bukkit.attribute.Attributable attr) {
                    org.bukkit.attribute.Attribute scaleAttr = getScaleAttribute();
                    if (scaleAttr != null && attr.getAttribute(scaleAttr) != null) {
                        attr.getAttribute(scaleAttr).setBaseValue(scale);
                        scaleApplied = true;
                    }
                }
                if (scaleApplied) as.setSmall(false);
                else as.setSmall(scale < 1);
            } else {
                as.setSmall(small);
            }
            return true;
        }
        return false;
    }

    /** 將當前世界內所有 last_boss 箱子盔甲架的位置/方向/大小輸出到 log，供複製給開發者做設定修改。 */
    public void dumpLastBossChestStands(Player player) {
        if (player == null || plugin == null) return;
        World world = player.getWorld();
        Test_dungeons.DungeonSnapshot snapshot = plugin.getLastDungeon();
        if (snapshot == null || !world.getName().equals(snapshot.worldName())) {
            player.sendMessage(Component.text("當前世界不是上次生成的地牢世界，無法計算 local 座標。", NamedTextColor.RED));
            return;
        }
        maple.underground_caves.dungeon.BossRoomManager bm = plugin.getBossRoomManager();
        if (bm == null) return;
        Location origin = bm.getLastBossRoomOrigin(world.getName(), snapshot.floor());
        if (origin == null) {
            player.sendMessage(Component.text("找不到 last_boss_room origin。", NamedTextColor.RED));
            return;
        }
        java.util.List<String> lines = new ArrayList<>();
        lines.add("=== Last boss chest stands dump (copy to developer for config) ===");
        for (Entity e : world.getEntitiesByClass(org.bukkit.entity.ArmorStand.class)) {
            if (!(e instanceof ArmorStand as)) continue;
            String id = as.getPersistentDataContainer().get(lastBossChestKey, PersistentDataType.STRING);
            if (id == null || id.isEmpty()) continue;
            Location loc = as.getLocation();
            int lx = (int) Math.floor(loc.getX() - origin.getX());
            int ly = (int) Math.floor(loc.getY() - origin.getY());
            int lz = (int) Math.floor(loc.getZ() - origin.getZ());
            int yaw = (int) Math.round(loc.getYaw() / 90) % 4;
            if (yaw < 0) yaw += 4;
            boolean small = as.isSmall();
            StringBuilder line = new StringBuilder("id=" + id + " local=(" + lx + "," + ly + "," + lz + ") yaw4=" + yaw + " small=" + small);
            if (as instanceof org.bukkit.attribute.Attributable attr) {
                org.bukkit.attribute.Attribute scaleAttr = getScaleAttribute();
                if (scaleAttr != null && attr.getAttribute(scaleAttr) != null) {
                    double scaleVal = attr.getAttribute(scaleAttr).getValue();
                    line.append(" scale=").append(scaleVal);
                }
            }
            lines.add(line.toString());
        }
        for (String line : lines) plugin.getLogger().info("[LastBossChests] " + line);
        player.sendMessage(Component.text("已輸出 " + (lines.size() - 1) + " 個箱子到 log，請查看伺服器 log 並複製給開發者。", NamedTextColor.GREEN));
    }
}
