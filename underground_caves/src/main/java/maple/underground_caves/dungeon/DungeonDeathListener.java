package maple.underground_caves.dungeon;

import maple.underground_caves.Test_dungeons;
import maple.underground_caves.party.PartyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Projectile;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 副本內倒地：虛擬血量歸零後不真死；背包暫存；幽靈狀態下存活者看不到倒地者、倒地者看不到彼此、
 * 可看存活隊友；怪物不鎖定幽靈；復活傳回 start_room 並還原背包與 stats 顯示。
 * 倒地前會暫存背包、飽食度、飽和度、總經驗；幽靈期間飽食度鎖滿，經驗等級顯示為治療師復活冷卻剩餘秒數，復活或滅團返還後還原暫存數值。
 * 幽靈期間禁止與方塊／實體的互動（點擊、放置、桶、吃、釣魚、講台取書、盔甲架、床、上載具等），與既有「不得攻擊／拾取／背包操作」並列。
 */
@SuppressWarnings("ALL")
public final class DungeonDeathListener implements Listener {
    /** 自動復活由「治療師-復活」技能驅動；此類別僅管理倒地/幽靈狀態與背包保存。 */

    private final Test_dungeons plugin;
    private final Map<UUID, SavedInventory> savedInventories = new ConcurrentHashMap<>();
    private final Set<UUID> dungeonGhosts = new CopyOnWriteArraySet<>();
    private final Set<UUID> resurrectionInProgress = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BukkitTask> ghostTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> manualHurtFeedbackAt = new ConcurrentHashMap<>();
    private final Map<UUID, Location> pendingRespawnLocations = new ConcurrentHashMap<>();
    private final Map<UUID, SavedInventory> pendingSavedInventories = new ConcurrentHashMap<>();
    private static final class DeathEntry {
        final UUID playerId;
        final int classIndexAtDeath;
        DeathEntry(UUID playerId, int classIndexAtDeath) {
            this.playerId = playerId;
            this.classIndexAtDeath = classIndexAtDeath;
        }
    }
    /** worldName -> 按死亡先後的倒地隊列（只保留仍在 ghost 的玩家） */
    private final Map<String, java.util.Deque<DeathEntry>> deathQueueByWorld = new ConcurrentHashMap<>();
    /** worldName -> 下一次可復活時間（每 50 秒 1 次；治療師倒地時會重置為 now+50s） */
    private final Map<String, Long> reviveCooldownUntilByWorld = new ConcurrentHashMap<>();
    /** 該副本世界正在進行「滅團倒數 → 傳回主世界」時為 true（此時幽靈 ActionBar 改由 PlayerStats 顯示） */
    private final Set<String> dungeonFailCountdownWorlds = ConcurrentHashMap.newKeySet();
    private static final int DUNGEON_FAIL_TELEPORT_DELAY_SEC = 10;

    public DungeonDeathListener(Test_dungeons plugin) {
        this.plugin = plugin;
    }

    private void applyDungeonNightVision(Player player) {
        if (player == null || !player.isOnline()) return;
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false, false));
    }

    private void clearDungeonNightVision(Player player) {
        if (player == null) return;
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
    }

    private void armReviveCooldownIfIdle(String worldName, String playerName, int classIdx) {
        if (worldName == null || worldName.isBlank()) return;
        long now = System.currentTimeMillis();
        long current = reviveCooldownUntilByWorld.getOrDefault(worldName, 0L);
        if (current > now) return;
        reviveCooldownUntilByWorld.put(worldName, now + 50_000L);
        plugin.getLogger().info("[DungeonDeath] revive cooldown armed world=" + worldName
                + " player=" + playerName
                + " class=" + classIdx
                + " to +50s");
    }

    public static final class SavedInventory {
        public final ItemStack[] contents;
        public final ItemStack[] armor;
        public final ItemStack[] extraContents;
        public final int heldItemSlot;
        public final int foodLevel;
        public final float saturation;
        /** 倒地前總經驗（等級+進度），復活時用 {@link Player#setTotalExperience(int)} 還原 */
        public final int totalExperience;

        public SavedInventory(Player player) {
            if (player == null) {
                this.contents = new ItemStack[0];
                this.armor = new ItemStack[0];
                this.extraContents = new ItemStack[0];
                this.heldItemSlot = 0;
                this.foodLevel = 20;
                this.saturation = 5f;
                this.totalExperience = 0;
                return;
            }
            this.contents = cloneArray(player.getInventory().getStorageContents());
            this.armor = cloneArray(player.getInventory().getArmorContents());
            this.extraContents = cloneArray(player.getInventory().getExtraContents());
            this.heldItemSlot = Math.max(0, player.getInventory().getHeldItemSlot());
            this.foodLevel = Math.max(0, Math.min(20, player.getFoodLevel()));
            this.saturation = Math.max(0f, player.getSaturation());
            this.totalExperience = Math.max(0, player.getTotalExperience());
        }

        private static ItemStack[] cloneArray(ItemStack[] a) {
            if (a == null) return new ItemStack[0];
            ItemStack[] out = new ItemStack[a.length];
            for (int i = 0; i < a.length; i++) out[i] = a[i] != null ? a[i].clone() : null;
            return out;
        }
    }

    /** 傷害會致死時由 VirtualHealthListener 呼叫：不讓玩家真死，改為倒地狀態並啟動 60 秒復活。 */
    public void handleDungeonDeath(Player player) {
        if (player == null || !player.isOnline()) return;
        UUID playerId = player.getUniqueId();
        if (dungeonGhosts.contains(playerId) || resurrectionInProgress.contains(playerId)) {
            plugin.getLogger().info("[DungeonDeath] skip duplicate death handling player=" + player.getName()
                    + " ghost=" + dungeonGhosts.contains(playerId)
                    + " resurrecting=" + resurrectionInProgress.contains(playerId));
            return;
        }
        String worldName = player.getWorld().getName();
        if (!plugin.isDungeonWorld(worldName)) return;

        if (plugin.getPlayerStatsManager() != null) {
            double current = plugin.getPlayerStatsManager().get(player).getCurrentHealth();
            double max = plugin.getPlayerStatsRuntime() != null
                    ? plugin.getPlayerStatsRuntime().getEffectiveHealthCap(player)
                    : plugin.getEffectiveMaxHealth(player);
            plugin.getLogger().info(String.format(
                    "[DungeonDeath] down player=%s world=%s class=%d current=%.2f cap=%.2f queueSize=%d",
                    player.getName(),
                    worldName,
                    plugin.getDungeonClassIndex(player),
                    current,
                    max,
                    deathQueueByWorld.containsKey(worldName) ? deathQueueByWorld.get(worldName).size() : 0));
        }

        saveAndClearInventory(player);
        applyGhostState(player);
        int classIdx = plugin.getDungeonClassIndex(player);
        if (plugin.getDungeonScoreboard() != null) {
            plugin.getDungeonScoreboard().recordDeath(worldName);
        }
        enqueueDeath(worldName, player.getUniqueId(), classIdx);
        armReviveCooldownIfIdle(worldName, player.getName(), classIdx);
        // 若治療師倒地：復活計時立刻重置為 50 秒，並保證先復活治療師
        if (classIdx == 0) {
            reviveCooldownUntilByWorld.put(worldName, System.currentTimeMillis() + 50_000L);
            plugin.getLogger().info("[DungeonDeath] healer down, reset revive cooldown world=" + worldName + " to +50s");
        }
        sendDeathMessageToParty(player, Component.text(player.getName() + " 在副本中倒地，等待治療師復活。", NamedTextColor.GRAY));
        setupGhostAppearance(player);
        showDungeonDownTitle(player);
        clearMobTargetsOnPlayer(player);
        applySameWorldGhostRules(player.getWorld());
        scheduleCheckPartyWipe(player.getWorld());
    }

    /** 單人倒地提示（虛擬死亡與原版死亡後轉幽靈皆會呼叫；滅團 Title 另由 {@link #tryStartDungeonFailSequence} 負責） */
    private static void showDungeonDownTitle(Player player) {
        if (player == null || !player.isOnline()) return;
        Title t = Title.title(
                Component.text("你已死亡", NamedTextColor.DARK_RED),
                Component.text("等待治療師的復活", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(2800), Duration.ofMillis(600)));
        player.showTitle(t);
    }

    /** 副本內：該世界是否正在顯示滅團倒數 Title（此期間幽靈應顯示 PlayerStats ActionBar）。 */
    public boolean isDungeonFailCountdownActive(String worldName) {
        return worldName != null && dungeonFailCountdownWorlds.contains(worldName);
    }

    private void scheduleCheckPartyWipe(World world) {
        if (world == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> tryStartDungeonFailSequence(world));
    }

    /**
     * 若該世界內「同一隊伍」在線成員全為幽靈，則開始 10 秒倒數：Title 通關失敗 + 副標倒數，結束後傳送至主世界並復原。
     */
    private void tryStartDungeonFailSequence(World world) {
        if (world == null || !plugin.isDungeonWorld(world.getName())) return;
        String wname = world.getName();
        if (dungeonFailCountdownWorlds.contains(wname)) return;
        if (!noLivingPartyMemberInDungeonWorld(world)) return;
        dungeonFailCountdownWorlds.add(wname);
        for (Player p : world.getPlayers()) {
            cancelGhostHudTask(p.getUniqueId());
        }
        new BukkitRunnable() {
            /** 每秒顯示一次：10 → 1，下一週期傳送（共 10 秒） */
            int secLeft = DUNGEON_FAIL_TELEPORT_DELAY_SEC;

            @Override
            public void run() {
                World w = Bukkit.getWorld(wname);
                if (w == null || !plugin.isDungeonWorld(wname)) {
                    dungeonFailCountdownWorlds.remove(wname);
                    cancel();
                    return;
                }
                if (!noLivingPartyMemberInDungeonWorld(w)) {
                    dungeonFailCountdownWorlds.remove(wname);
                    for (Player p : w.getPlayers()) {
                        if (p != null && p.isOnline() && dungeonGhosts.contains(p.getUniqueId())) {
                            startGhostReviveCooldownHudTask(p);
                        }
                    }
                    cancel();
                    return;
                }
                if (secLeft >= 1) {
                    Title t = Title.title(
                            Component.text("通關失敗", NamedTextColor.DARK_RED),
                            Component.text(secLeft + "秒後，將會傳送你到主世界", NamedTextColor.GRAY),
                            Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(900), Duration.ofMillis(300)));
                    for (Player p : w.getPlayers()) {
                        if (p != null && p.isOnline()) p.showTitle(t);
                    }
                    secLeft--;
                } else {
                    executeDungeonFailTeleport(w, wname);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /** 該世界內，參考第一位玩家的隊伍：在線且身在本世界的成員是否「至少一人且全為幽靈」。 */
    private boolean noLivingPartyMemberInDungeonWorld(World w) {
        PartyManager pm = plugin.getPartyManager();
        List<Player> inWorld = new ArrayList<>(w.getPlayers());
        if (inWorld.isEmpty()) return false;
        Player ref = inWorld.get(0);
        List<UUID> ids = pm != null ? pm.getPartyMembers(ref) : List.of(ref.getUniqueId());
        boolean anyMemberInWorld = false;
        for (UUID id : ids) {
            if (id == null) continue;
            Player m = Bukkit.getPlayer(id);
            if (m == null || !m.isOnline() || !w.equals(m.getWorld())) continue;
            anyMemberInWorld = true;
            if (!dungeonGhosts.contains(id)) return false;
        }
        return anyMemberInWorld;
    }

    private void executeDungeonFailTeleport(World dungeonWorld, String worldName) {
        dungeonFailCountdownWorlds.remove(worldName);
        deathQueueByWorld.remove(worldName);
        reviveCooldownUntilByWorld.remove(worldName);
        DungeonBridgeListener bridgeListener = plugin.getDungeonBridgeListener();
        if (bridgeListener != null) {
            bridgeListener.sendEndRunForWorld(worldName, "通关失败，正在返回大厅。");
        }
        Location spawn = plugin.getMainWorldSpawnLocation();
        if (spawn == null || spawn.getWorld() == null) return;
        for (Player p : new ArrayList<>(dungeonWorld.getPlayers())) {
            if (p == null || !p.isOnline()) continue;
            cancelGhostHudTask(p.getUniqueId());
            UUID id = p.getUniqueId();
            if (dungeonGhosts.contains(id)) {
                dungeonGhosts.remove(id);
                p.setAllowFlight(false);
                p.setFlying(false);
                clearDungeonNightVision(p);
                p.teleport(spawn);
                p.getInventory().clear();
                p.getInventory().setArmorContents(new ItemStack[4]);
                restoreInventory(p);
                if (plugin.getPlayerStatsManager() != null) {
                    double maxH = plugin.getEffectiveMaxHealth(p);
                    plugin.getPlayerStatsManager().get(p).setCurrentHealthWithCap(maxH, maxH);
                }
                plugin.setWishShieldExtra(id, 0);
                p.setInvulnerable(false);
                plugin.syncVirtualHealthBar(p);
            } else {
                clearDungeonNightVision(p);
                p.teleport(spawn);
            }
        }
        applySameWorldGhostRules(dungeonWorld);
    }

    private void enqueueDeath(String worldName, UUID playerId, int classIndexAtDeath) {
        if (worldName == null || playerId == null) return;
        java.util.Deque<DeathEntry> q = deathQueueByWorld.computeIfAbsent(worldName, k -> new java.util.concurrent.ConcurrentLinkedDeque<>());
        // 避免重複入隊
        for (DeathEntry e : q) {
            if (e != null && playerId.equals(e.playerId)) return;
        }
        q.addLast(new DeathEntry(playerId, classIndexAtDeath));
    }

    public boolean isGhost(UUID playerId) {
        return playerId != null && dungeonGhosts.contains(playerId);
    }

    public boolean isResurrectionInProgress(UUID playerId) {
        return playerId != null && resurrectionInProgress.contains(playerId);
    }

    /** 取出該世界中「優先治療師」的最早倒地且仍為 ghost 的玩家 UUID，無則回傳 null */
    public UUID pollNextGhostPreferHealer(String worldName) {
        if (worldName == null || worldName.isBlank()) return null;
        java.util.Deque<DeathEntry> q = deathQueueByWorld.get(worldName);
        if (q == null) return null;
        // 先找最早倒地的治療師
        for (Iterator<DeathEntry> it = q.iterator(); it.hasNext(); ) {
            DeathEntry e = it.next();
            if (e == null || e.playerId == null) { it.remove(); continue; }
            if (e.classIndexAtDeath != 0) continue;
            if (!dungeonGhosts.contains(e.playerId)) { it.remove(); continue; }
            it.remove();
            return e.playerId;
        }
        // 再找最早倒地的任意玩家
        while (true) {
            DeathEntry e = q.pollFirst();
            if (e == null) return null;
            if (e.playerId == null) continue;
            if (dungeonGhosts.contains(e.playerId)) return e.playerId;
        }
    }

    public long getReviveCooldownUntil(String worldName) {
        if (worldName == null || worldName.isBlank()) return 0L;
        return reviveCooldownUntilByWorld.getOrDefault(worldName, 0L);
    }

    public void setReviveCooldownUntil(String worldName, long untilMs) {
        if (worldName == null || worldName.isBlank()) return;
        reviveCooldownUntilByWorld.put(worldName, untilMs);
    }

    /** 由外部技能呼叫：復活指定倒地玩家（仍為 ghost 才會生效） */
    public void resurrectGhostNow(UUID deadId, String worldName, Player healer) {
        if (deadId == null || worldName == null || worldName.isBlank()) return;
        if (!dungeonGhosts.contains(deadId)) return;
        Player p = Bukkit.getPlayer(deadId);
        if (p == null || !p.isOnline()) return;
        Location resurrectAt = resolveResurrectionLocation(p, worldName, healer);
        String healerName = healer != null ? healer.getName() : "null";
        plugin.getLogger().info("[DungeonDeath] resurrect target=" + p.getName()
                + " world=" + worldName
                + " healer=" + healerName
                + " location=" + (resurrectAt != null ? resurrectAt : "null"));
        completeResurrection(p, worldName, resurrectAt);
    }

    private void saveAndClearInventory(Player player) {
        UUID id = player.getUniqueId();
        SavedInventory saved = new SavedInventory(player);
        savedInventories.put(id, saved);
        clearInventory(player);
    }

    private void applyGhostState(Player player) {
        UUID id = player.getUniqueId();
        dungeonGhosts.add(id);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setGameMode(GameMode.SURVIVAL);
        applyDungeonNightVision(player);
    }

    private void setupGhostAppearance(Player player) {
        if (player == null || !player.isOnline()) return;
        startGhostReviveCooldownHudTask(player);
        fillGhostHotbar(player);
        plugin.syncVirtualHealthBar(player);
    }

    /**
     * 幽靈底部 ActionBar：死亡中 | 治疗师复活冷却: N 秒（大於 5 秒灰色，≤5 秒黃色；冷卻結束後由治療師自動復活任務處理）。
     */
    private void startGhostReviveCooldownHudTask(Player player) {
        if (player == null) return;
        UUID id = player.getUniqueId();
        cancelGhostHudTask(id);
        final int[] actionBarTick = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !dungeonGhosts.contains(id)) {
                cancelGhostHudTask(id);
                return;
            }
            World w = player.getWorld();
            if (w == null || !plugin.isDungeonWorld(w.getName())) {
                cancelGhostHudTask(id);
                return;
            }
            // 幽靈期間飽食度鎖滿（含滅團倒數 Title 期間）
            player.setFoodLevel(20);
            player.setSaturation(20f);

            if (dungeonFailCountdownWorlds.contains(w.getName())) {
                player.setLevel(0);
                player.setExp(0f);
                return;
            }

            long now = System.currentTimeMillis();
            long until = getReviveCooldownUntil(w.getName());
            long remainMs = until - now;
            int seconds = remainMs > 0 ? (int) Math.ceil(remainMs / 1000.0) : 0;
            // 經驗「等級」顯示復活冷卻剩餘秒數；進度條清空以便辨識
            player.setLevel(Math.min(seconds, 99999999));
            player.setExp(0f);

            if (actionBarTick[0]++ % 20 == 0) {
                NamedTextColor color = seconds <= 5 ? NamedTextColor.YELLOW : NamedTextColor.GRAY;
                player.sendActionBar(Component.text("死亡中 | 治疗师复活冷却: " + seconds + "秒", color));
            }
        }, 0L, 1L);
        ghostTasks.put(id, task);
    }

    private void cancelGhostHudTask(UUID playerId) {
        if (playerId == null) return;
        BukkitTask t = ghostTasks.remove(playerId);
        if (t != null) t.cancel();
    }

    /** 幽靈熱鍵：0 指南針（存活隊友）、8 副本地圖；由 {@link #refreshGhostHotbar} 週期更新指南針 */
    private void fillGhostHotbar(Player player) {
        if (player == null) return;
        ItemStack compass = new ItemStack(Material.COMPASS, 1);
        ItemMeta cMeta = compass.getItemMeta();
        if (cMeta != null) {
            cMeta.displayName(Component.text("存活隊友", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            compass.setItemMeta(cMeta);
        }
        player.getInventory().setItem(0, compass);
        ItemStack map = plugin.createDungeonMapItemForCurrentWorld(player.getWorld());
        if (map != null) {
            player.getInventory().setItem(8, map);
        } else {
            player.getInventory().setItem(8, null);
        }
    }

    /** 供 ActionBar 任務呼叫：更新指南針說明上的存活玩家列表 */
    public void refreshGhostHotbar(Player player) {
        if (player == null || !player.isOnline() || !isGhost(player.getUniqueId())) return;
        ItemStack compass = player.getInventory().getItem(0);
        if (compass == null || compass.getType() != Material.COMPASS) {
            fillGhostHotbar(player);
            compass = player.getInventory().getItem(0);
        }
        if (compass == null || compass.getType() != Material.COMPASS) return;
        ItemMeta meta = compass.getItemMeta();
        if (meta == null) return;
        meta.displayName(Component.text("存活隊友", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        World w = player.getWorld();
        if (w != null) {
            for (Player o : w.getPlayers()) {
                if (o.equals(player)) continue;
                if (isGhost(o.getUniqueId())) continue;
                lore.add(Component.text("• " + o.getName(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            }
        }
        if (lore.isEmpty()) {
            lore.add(Component.text("（無存活隊友於此世界）", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        compass.setItemMeta(meta);
        player.getInventory().setItem(0, compass);
    }

    private void clearMobTargetsOnPlayer(Player victim) {
        if (victim == null || victim.getWorld() == null) return;
        for (Entity e : victim.getWorld().getNearbyEntities(victim.getLocation(), 48, 48, 48, en -> en instanceof Mob)) {
            Mob mob = (Mob) e;
            if (victim.equals(mob.getTarget())) {
                mob.setTarget(null);
            }
        }
    }

    private void sendDeathMessageToParty(Player dead, Component customMessage) {
        PartyManager pm = plugin.getPartyManager();
        Component msg = customMessage != null ? customMessage : Component.text(dead.getName() + " 在副本中倒地，60 秒後將傳送至隊友復活。", NamedTextColor.GRAY);
        List<UUID> members = pm != null ? pm.getPartyMembers(dead) : List.of(dead.getUniqueId());
        for (UUID mid : members) {
            Player m = Bukkit.getPlayer(mid);
            if (m != null && m.isOnline()) m.sendMessage(msg);
        }
    }

    private Location resolveResurrectionLocation(Player target, String worldName, Player healer) {
        Location start = plugin.getDungeonStartRoomLocation(worldName);
        if (target == null) return start;
        if (healer == null || !healer.isOnline()) return start;
        if (!worldName.equals(healer.getWorld().getName())) return start;
        if (healer.getUniqueId().equals(target.getUniqueId())) return start;
        return healer.getLocation().clone();
    }

    private void completeResurrection(Player player, String worldName, Location resurrectAt) {
        UUID id = player.getUniqueId();
        resurrectionInProgress.add(id);
        cancelGhostHudTask(id);
        plugin.getLogger().info("[DungeonDeath] complete resurrection player=" + player.getName() + " world=" + worldName);
        try {
            if (!player.isOnline()) return;
            player.clearTitle();
            player.setInvulnerable(true);

            if (resurrectAt != null) {
                player.teleport(resurrectAt);
            }
            applyDungeonNightVision(player);
            player.setAllowFlight(false);
            player.setFlying(false);
            clearInventory(player);
            restoreInventory(player);
            if (plugin.getPlayerStatsManager() != null) {
                double maxH = plugin.getEffectiveMaxHealth(player);
                plugin.getPlayerStatsManager().get(player).setCurrentHealthWithCap(maxH, maxH);
            }
            plugin.setWishShieldExtra(id, 0);
            dungeonGhosts.remove(id);
            plugin.syncVirtualHealthBar(player);
            World w = player.getWorld();
            if (w != null && plugin.isDungeonWorld(w.getName())) {
                applySameWorldGhostRules(w);
            }
        } finally {
            resurrectionInProgress.remove(id);
        }
    }

    private void restoreInventory(Player player) {
        UUID id = player.getUniqueId();
        SavedInventory saved = savedInventories.remove(id);
        if (saved == null) return;
        clearInventory(player);
        if (saved.contents.length > 0) player.getInventory().setStorageContents(saved.contents);
        if (saved.armor.length > 0) player.getInventory().setArmorContents(saved.armor);
        if (saved.extraContents.length > 0) player.getInventory().setExtraContents(saved.extraContents);
        player.getInventory().setHeldItemSlot(Math.min(8, Math.max(0, saved.heldItemSlot)));
        player.setFoodLevel(Math.max(0, Math.min(20, saved.foodLevel)));
        player.setSaturation(Math.max(0f, saved.saturation));
        player.setTotalExperience(Math.max(0, saved.totalExperience));
    }

    private void clearInventory(Player player) {
        if (player == null) return;
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[player.getInventory().getArmorContents().length]);
        player.getInventory().setExtraContents(new ItemStack[player.getInventory().getExtraContents().length]);
        player.getInventory().setHeldItemSlot(0);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        String worldName = player.getWorld().getName();
        if (!plugin.isDungeonWorld(worldName)) return;
        if (resurrectionInProgress.contains(player.getUniqueId())) {
            event.deathMessage(null);
            event.getDrops().clear();
            event.setKeepInventory(true);
            plugin.getLogger().warning("[DungeonDeath] suppressed vanilla death during resurrection player=" + player.getName()
                    + " world=" + worldName);
            return;
        }

        double virtualHealth = -1D;
        double effectiveCap = -1D;
        if (plugin.getPlayerStatsManager() != null) {
            virtualHealth = plugin.getPlayerStatsManager().get(player).getCurrentHealth();
            effectiveCap = plugin.getPlayerStatsRuntime() != null
                    ? plugin.getPlayerStatsRuntime().getEffectiveHealthCap(player)
                    : plugin.getEffectiveMaxHealth(player);
        }
        EntityDamageEvent lastDamage = player.getLastDamageCause();
        String cause = "UNKNOWN";
        if (lastDamage != null) {
            cause = lastDamage.getCause().name();
            if (lastDamage instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() != null) {
                cause += ", damager=" + byEntity.getDamager().getType().name();
            }
        }
        plugin.getLogger().info(String.format(
                "[DungeonDeath] vanilla death player=%s world=%s class=%d virtual=%.2f cap=%.2f vanilla=%.2f ghost=%s cause=%s",
                player.getName(),
                worldName,
                plugin.getDungeonClassIndex(player),
                virtualHealth,
                effectiveCap,
                player.getHealth(),
                dungeonGhosts.contains(player.getUniqueId()),
                cause));

        event.deathMessage(null);
        event.getDrops().clear();
        event.setKeepInventory(true);

        UUID id = player.getUniqueId();
        SavedInventory saved = new SavedInventory(player);
        pendingSavedInventories.put(id, saved);
        pendingRespawnLocations.put(id, player.getLocation().clone());
        sendDeathMessageToParty(player, Component.text(player.getName() + " 在副本中倒地，60 秒後將傳送至隊友復活。", NamedTextColor.GRAY));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        Location savedLoc = pendingRespawnLocations.get(id);
        SavedInventory savedInv = pendingSavedInventories.get(id);
        if (savedLoc == null || savedInv == null) return;

        String worldName = savedLoc.getWorld() != null ? savedLoc.getWorld().getName() : player.getWorld().getName();
        int classIdx = plugin.getDungeonClassIndex(player);
        plugin.getLogger().info("[DungeonDeath] convert vanilla death to ghost player=" + player.getName()
                + " world=" + worldName + " class=" + classIdx);

        event.setRespawnLocation(savedLoc);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            pendingRespawnLocations.remove(id);
            pendingSavedInventories.remove(id);
            clearInventory(player);
            savedInventories.put(id, savedInv);
            applyGhostState(player);
            if (plugin.getDungeonScoreboard() != null) {
                plugin.getDungeonScoreboard().recordDeath(worldName);
            }
            enqueueDeath(worldName, id, classIdx);
            armReviveCooldownIfIdle(worldName, player.getName(), classIdx);
            if (classIdx == 0) {
                reviveCooldownUntilByWorld.put(worldName, System.currentTimeMillis() + 50_000L);
                plugin.getLogger().info("[DungeonDeath] healer down via vanilla death, reset revive cooldown world="
                        + worldName + " to +50s");
            }
            setupGhostAppearance(player);
            showDungeonDownTitle(player);
            clearMobTargetsOnPlayer(player);
            applySameWorldGhostRules(player.getWorld());
            scheduleCheckPartyWipe(player.getWorld());
        }, 2L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.isDungeonWorld(player.getWorld().getName())) return;

        if (dungeonGhosts.contains(player.getUniqueId())) {
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                event.setCancelled(true);
            }
            return;
        }

        if (player.isDead()) return;
        if (plugin.getPlayerStatsManager() == null) return;

        double virtualHealth = plugin.getPlayerStatsManager().get(player).getCurrentHealth();
        if (virtualHealth <= 0D) return;

        double vanillaBefore = player.getHealth();
        double finalDamage = event.getFinalDamage();
        if (finalDamage <= 0D) return;
        if (vanillaBefore - finalDamage > 0.0001D) return;

        double effectiveCap = plugin.getPlayerStatsRuntime() != null
                ? plugin.getPlayerStatsRuntime().getEffectiveHealthCap(player)
                : plugin.getEffectiveMaxHealth(player);
        event.setCancelled(true);
        playManualHurtFeedback(player);
        plugin.getLogger().warning(String.format(
                "[DungeonDeath] prevented vanilla lethal player=%s world=%s virtual=%.2f cap=%.2f vanillaBefore=%.2f final=%.2f cause=%s",
                player.getName(),
                player.getWorld().getName(),
                virtualHealth,
                effectiveCap,
                vanillaBefore,
                finalDamage,
                event.getCause().name()));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.isDead()) return;
            plugin.syncVirtualHealthBar(player);
            applySameWorldGhostRules(player.getWorld());
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onVanillaLethalMonitor(EntityDamageEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.isDungeonWorld(player.getWorld().getName())) return;
        if (dungeonGhosts.contains(player.getUniqueId())) return;
        if (player.isDead()) return;
        if (plugin.getPlayerStatsManager() == null) return;

        double virtualHealth = plugin.getPlayerStatsManager().get(player).getCurrentHealth();
        if (virtualHealth <= 0D) return;

        double vanillaBefore = player.getHealth();
        double finalDamage = event.getFinalDamage();
        if (finalDamage <= 0D) return;
        if (vanillaBefore - finalDamage > 0.0001D) return;

        double effectiveCap = plugin.getPlayerStatsRuntime() != null
                ? plugin.getPlayerStatsRuntime().getEffectiveHealthCap(player)
                : plugin.getEffectiveMaxHealth(player);
        event.setCancelled(true);
        playManualHurtFeedback(player);
        plugin.getLogger().warning(String.format(
                "[DungeonDeath] monitor prevented vanilla lethal player=%s world=%s virtual=%.2f cap=%.2f vanillaBefore=%.2f final=%.2f cause=%s",
                player.getName(),
                player.getWorld().getName(),
                virtualHealth,
                effectiveCap,
                vanillaBefore,
                finalDamage,
                event.getCause().name()));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.isDead()) return;
            plugin.syncVirtualHealthBar(player);
            applySameWorldGhostRules(player.getWorld());
        });
    }

    private void playManualHurtFeedback(Player player) {
        if (player == null || !player.isOnline()) return;
        long now = System.currentTimeMillis();
        long last = manualHurtFeedbackAt.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 175L) return;
        manualHurtFeedbackAt.put(player.getUniqueId(), now);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 0.9f, 1.0f);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyTabVisibilityForDungeonWorld(event.getPlayer()), 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        Player gone = event.getPlayer();
        UUID id = gone.getUniqueId();
        cancelGhostHudTask(id);
        dungeonGhosts.remove(id);
        World w = gone.getWorld();
        if (w != null && plugin.isDungeonWorld(w.getName())) {
            Bukkit.getScheduler().runTask(plugin, () -> applySameWorldGhostRules(w));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player p && isGhost(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGhostInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (!isGhost(p.getUniqueId()) || !plugin.isDungeonWorld(p.getWorld().getName())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGhostInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (!isGhost(p.getUniqueId()) || !plugin.isDungeonWorld(p.getWorld().getName())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGhostDrop(PlayerDropItemEvent event) {
        Player p = event.getPlayer();
        if (!isGhost(p.getUniqueId()) || !plugin.isDungeonWorld(p.getWorld().getName())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGhostPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (!isGhost(p.getUniqueId()) || !plugin.isDungeonWorld(p.getWorld().getName())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGhostSwapHand(PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        if (!isGhost(p.getUniqueId()) || !plugin.isDungeonWorld(p.getWorld().getName())) return;
        event.setCancelled(true);
    }

    /** 幽靈期間飽食度由任務鎖滿，禁止其他因素扣飢餓 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGhostFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (!isGhost(p.getUniqueId()) || !plugin.isDungeonWorld(p.getWorld().getName())) return;
        event.setCancelled(true);
    }

    /** 幽靈期間經驗條用於顯示復活秒數，禁止額外獲得/失去經驗（由 HUD 任務覆寫） */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGhostExpChange(PlayerExpChangeEvent event) {
        Player p = event.getPlayer();
        if (!isGhost(p.getUniqueId()) || !plugin.isDungeonWorld(p.getWorld().getName())) return;
        event.setAmount(0);
    }

    /** 幽靈不得對任何實體造成傷害（含近戰、箭、三叉戟、投擲藥水等） */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGhostAttackEntity(EntityDamageByEntityEvent event) {
        Player attacker = resolveDamageAttackerPlayer(event.getDamager());
        if (attacker == null) return;
        if (!isGhost(attacker.getUniqueId()) || !plugin.isDungeonWorld(attacker.getWorld().getName())) return;
        event.setCancelled(true);
    }

    /** 幽靈不得發射投射物（避免箭／藥水／末影珍珠等） */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGhostLaunchProjectile(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Projectile proj)) return;
        if (!(proj.getShooter() instanceof Player p)) return;
        if (!isGhost(p.getUniqueId()) || !plugin.isDungeonWorld(p.getWorld().getName())) return;
        event.setCancelled(true);
    }

    /** 幽靈不得破壞方塊 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGhostBreakBlock(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (p == null) return;
        if (!isGhost(p.getUniqueId()) || !plugin.isDungeonWorld(p.getWorld().getName())) return;
        event.setCancelled(true);
    }

    private static Player resolveDamageAttackerPlayer(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    /** 副本內幽靈：用於攔截互動（不含滅團傳送等非玩家操作） */
    private boolean isDungeonGhostBlocking(Player p) {
        return p != null && p.isOnline() && isGhost(p.getUniqueId()) && plugin.isDungeonWorld(p.getWorld().getName());
    }

    /** 左／右鍵方塊與空氣、使用物品（門、箱、按鈕、地圖等） */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onGhostPlayerInteract(PlayerInteractEvent event) {
        if (!isDungeonGhostBlocking(event.getPlayer())) return;
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
    }

    /** 左／右鍵實體（村民、盔甲架、物品展示框等） */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onGhostInteractEntity(PlayerInteractEntityEvent event) {
        if (!isDungeonGhostBlocking(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onGhostPlaceBlock(BlockPlaceEvent event) {
        if (!isDungeonGhostBlocking(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onGhostBucketFill(PlayerBucketFillEvent event) {
        if (!isDungeonGhostBlocking(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onGhostBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!isDungeonGhostBlocking(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onGhostHarvest(PlayerHarvestBlockEvent event) {
        if (!isDungeonGhostBlocking(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onGhostShear(PlayerShearEntityEvent event) {
        if (!isDungeonGhostBlocking(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onGhostLeash(PlayerLeashEntityEvent event) {
        if (!isDungeonGhostBlocking(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onGhostConsume(PlayerItemConsumeEvent event) {
        if (!isDungeonGhostBlocking(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onGhostFish(PlayerFishEvent event) {
        if (!isDungeonGhostBlocking(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onGhostLecternBook(PlayerTakeLecternBookEvent event) {
        if (!isDungeonGhostBlocking(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onGhostArmorStand(PlayerArmorStandManipulateEvent event) {
        if (!isDungeonGhostBlocking(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onGhostBedEnter(PlayerBedEnterEvent event) {
        if (!isDungeonGhostBlocking(event.getPlayer())) return;
        event.setCancelled(true);
    }

    /** 禁止幽靈坐上船、礦車、馬等 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onGhostMountVehicle(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (!isDungeonGhostBlocking(p)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World from = event.getFrom();
        World to = player.getWorld();
        if (plugin.isDungeonWorld(from.getName()) && !plugin.isDungeonWorld(to.getName())) {
            clearDungeonNightVision(player);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.equals(player)) continue;
                player.showPlayer(plugin, other);
                other.showPlayer(plugin, player);
            }
            return;
        }
        if (plugin.isDungeonWorld(to.getName())) {
            applyDungeonNightVision(player);
            isolatePlayerFromOtherWorlds(player, to);
            applySameWorldGhostRules(to);
        }
    }

    /** 進入副本：與非本世界玩家雙向隱藏 */
    private void isolatePlayerFromOtherWorlds(Player viewer, World dungeonWorld) {
        if (viewer == null || dungeonWorld == null) return;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(viewer)) continue;
            if (!dungeonWorld.equals(other.getWorld())) {
                viewer.hidePlayer(plugin, other);
                other.hidePlayer(plugin, viewer);
            }
        }
    }

    /**
     * 同副本世界內：存活者看不到倒地者；倒地者可看存活者、看不到其他倒地者；兩存活互相可見。
     */
    public void applySameWorldGhostRules(World world) {
        if (world == null || !plugin.isDungeonWorld(world.getName())) return;
        List<Player> list = new ArrayList<>(world.getPlayers());
        for (Player a : list) {
            for (Player b : list) {
                if (a.equals(b)) continue;
                boolean ga = isGhost(a.getUniqueId());
                boolean gb = isGhost(b.getUniqueId());
                boolean see = (!gb || ga) && !(ga && gb);
                if (see) {
                    a.showPlayer(plugin, b);
                } else {
                    a.hidePlayer(plugin, b);
                }
            }
        }
    }

    /** 玩家加入時若在副本世界，套用跨世界隔離與幽靈可見性 */
    public void applyTabVisibilityForDungeonWorld(Player player) {
        if (player == null || !plugin.isDungeonWorld(player.getWorld().getName())) return;
        applyDungeonNightVision(player);
        isolatePlayerFromOtherWorlds(player, player.getWorld());
        applySameWorldGhostRules(player.getWorld());
    }

    public boolean isDungeonGhost(UUID playerId) {
        return dungeonGhosts.contains(playerId);
    }
}
