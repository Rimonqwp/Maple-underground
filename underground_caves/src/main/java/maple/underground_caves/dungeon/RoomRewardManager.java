package maple.underground_caves.dungeon;

import maple.underground_caves.Test_dungeons;
import maple.underground_caves.party.PartyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.util.EulerAngle;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 房間通關後在最後一隻目標怪物死亡位置生成隱身無碰撞盔甲架獎勵。
 * 1. 門鑰匙（主路）- 盔甲架，靠近時存 DB
 * 2. 秘密（全部）- 盔甲架，靠近時發訊息「你已撿了」
 * 3. 復活石（主路）- 盔甲架，靠近時進背包
 */
public final class RoomRewardManager implements Listener {
    private static final String DOOR_KEY_TEXTURE = "c8ceb8631add7cbb565dcb705f1212349cccd755965c46a922852c9fd948b4bb";
    private static final String SECRET_TEXTURE = "e93e2068617872c542ecda1d27df4ece91c699907bf327c4ddb85309412d3939";
    private static final String REVIVE_STONE_TEXTURE = "b6a76cc22e7c2ab9c540d1244eadba581f5dd9e18f9adacf05280a5b48b8f618";
    private static final double PICKUP_RADIUS = 2.0;
    private static final double PICKUP_RADIUS_SQ = PICKUP_RADIUS * PICKUP_RADIUS;
    private static final long PICKUP_DELAY_MS = 1000L;
    /** 秘密祝福、門鑰匙：超過此時間未被人撿起則自動撿起（給最近玩家或移除）。復活石不自動撿。 */
    private static final long AUTO_PICKUP_MS = 10_000L;
    private static final double AUTO_PICKUP_RADIUS = 32.0;
    private static final double FLOAT_AMPLITUDE = 0.06;
    private static final double FLOAT_SPEED = 0.06;
    private static final double ROTATE_SPEED = 0.10;

    private final Test_dungeons plugin;
    private final DungeonRoomRewardDB db;
    private final NamespacedKey rewardTypeKey;
    private final NamespacedKey rewardGroupKey;
    private final NamespacedKey rewardWorldKey;
    private final NamespacedKey rewardBaseYKey;
    private final NamespacedKey rewardSpawnTimeKey;
    private final Set<UUID> rewardStandIds = new HashSet<>();
    /** standId -> (playerId -> 首次進入範圍的時間 ms)，須持續在範圍內滿 1 秒才可撿起 */
    private final Map<UUID, Map<UUID, Long>> nearSinceMs = new HashMap<>();
    private BukkitRunnable proximityTask;
    private BukkitRunnable animationTask;
    private long animationTick = 0;

    enum RewardType {
        DOOR_KEY,
        SECRET,
        REVIVE_STONE
    }

    public RoomRewardManager(Test_dungeons plugin, DungeonRoomRewardDB db) {
        this.plugin = plugin;
        this.db = db;
        this.rewardTypeKey = new NamespacedKey(plugin, "room_reward_type");
        this.rewardGroupKey = new NamespacedKey(plugin, "room_reward_group");
        this.rewardWorldKey = new NamespacedKey(plugin, "room_reward_world");
        this.rewardBaseYKey = new NamespacedKey(plugin, "room_reward_base_y");
        this.rewardSpawnTimeKey = new NamespacedKey(plugin, "room_reward_spawn_time_ms");
        startProximityTask();
        startAnimationTask();
    }

    private void startProximityTask() {
        if (proximityTask != null) proximityTask.cancel();
        proximityTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickProximity();
            }
        };
        proximityTask.runTaskTimer(plugin, 20L, 5L);
    }

    private void startAnimationTask() {
        if (animationTask != null) animationTask.cancel();
        animationTask = new BukkitRunnable() {
            @Override
            public void run() {
                animationTick++;
                tickAnimation();
            }
        };
        animationTask.runTaskTimer(plugin, 20L, 1L);
    }

    private void tickAnimation() {
        double t = animationTick * 0.05;
        double floatY = Math.sin(t * FLOAT_SPEED) * FLOAT_AMPLITUDE;
        double headYaw = t * ROTATE_SPEED;

        for (UUID id : List.copyOf(rewardStandIds)) {
            org.bukkit.entity.Entity e = Bukkit.getEntity(id);
            if (!(e instanceof ArmorStand stand) || !stand.isValid()) continue;

            Double baseY = stand.getPersistentDataContainer().get(rewardBaseYKey, PersistentDataType.DOUBLE);
            if (baseY == null) continue;

            Location loc = stand.getLocation();
            loc.setY(baseY + floatY);
            stand.teleport(loc);
            stand.setHeadPose(new EulerAngle(0, headYaw, 0));

            if (animationTick % 8 == 0) {
                Location pLoc = loc.clone().add(0, 0.5, 0);
                stand.getWorld().spawnParticle(Particle.ENCHANT, pLoc, 3, 0.2, 0.2, 0.2, 0.02);
                stand.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, pLoc, 1, 0.15, 0.15, 0.15, 0);
            }
        }
    }

    /** 由 DungeonMonsters 在最後一隻目標怪物死亡時呼叫 */
    void onRoomCleared(World world, Location deathLoc, String groupKey, boolean isMainPath) {
        if (world == null || deathLoc == null || plugin == null || !plugin.isEnabled()) return;
        if (plugin.getDungeonBlessingManager() != null) {
            DungeonBlessingManager.BlessingType type = plugin.getDungeonBlessingManager().onRoomCleared(world.getName());
            int lv = plugin.getDungeonBlessingManager().getLevel(world.getName(), type);
            plugin.getDungeonBlessingManager().broadcastGain(world, type, lv);
        }

        List<RewardType> types = new ArrayList<>();
        types.add(RewardType.SECRET);
        if (isMainPath) {
            types.add(RewardType.DOOR_KEY);
            types.add(RewardType.REVIVE_STONE);
        }

        double offset = 0.85;
        double[][] fallbackOffsets = {{0.5, 0}, {-0.5, 0}, {0, 0.5}, {0, -0.5}, {0.5, 0.5}, {-0.5, -0.5}, {0.5, -0.5}, {-0.5, 0.5}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int i = 0; i < types.size(); i++) {
            double dx = (i % 3 - 1) * offset;
            double dz = (i / 3) * offset;
            Location spawnLoc = findSpawnLocationNotInWall(world, deathLoc.clone().add(dx, 0.1, dz), fallbackOffsets);
            if (spawnLoc != null) {
                spawnRewardStand(world, spawnLoc, types.get(i), groupKey != null ? groupKey : "");
            }
        }
    }

    /** 找一個不卡在牆內的生成位置，優先原點，否則依序嘗試 offsets */
    private Location findSpawnLocationNotInWall(World world, Location base, double[][] fallbackOffsets) {
        if (isPassableForReward(world, base)) return base;
        for (double[] off : fallbackOffsets) {
            Location tryLoc = base.clone().add(off[0], 0, off[1]);
            if (isPassableForReward(world, tryLoc)) return tryLoc;
        }
        for (int dy = 1; dy <= 3; dy++) {
            Location tryLoc = base.clone().add(0, dy, 0);
            if (isPassableForReward(world, tryLoc)) return tryLoc;
        }
        return base;
    }

    private boolean isPassableForReward(World world, Location loc) {
        if (loc == null) return false;
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return world.getBlockAt(x, y, z).isPassable() && world.getBlockAt(x, y + 1, z).isPassable();
    }

    private void spawnRewardStand(World world, Location loc, RewardType type, String groupKey) {
        if (world == null || loc == null || type == null) return;

        String texture = switch (type) {
            case DOOR_KEY -> DOOR_KEY_TEXTURE;
            case SECRET -> SECRET_TEXTURE;
            case REVIVE_STONE -> REVIVE_STONE_TEXTURE;
        };

        ArmorStand stand = world.spawn(loc, ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setSmall(true);
            as.setGravity(false);
            as.setMarker(true);
            as.setSilent(true);
            as.setCollidable(false);
            as.setPersistent(false);

            ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
            if (head.getItemMeta() instanceof SkullMeta meta) {
                DungeonSecrets.applySkullTextureToMeta(meta, texture, "RoomRewardManager." + type.name());
                head.setItemMeta(meta);
            }
            if (as.getEquipment() != null) as.getEquipment().setHelmet(head);

            as.getPersistentDataContainer().set(rewardTypeKey, PersistentDataType.STRING, type.name());
            as.getPersistentDataContainer().set(rewardGroupKey, PersistentDataType.STRING, groupKey != null ? groupKey : "");
            as.getPersistentDataContainer().set(rewardWorldKey, PersistentDataType.STRING, world.getName());
            as.getPersistentDataContainer().set(rewardBaseYKey, PersistentDataType.DOUBLE, loc.getY());
            as.getPersistentDataContainer().set(rewardSpawnTimeKey, PersistentDataType.LONG, System.currentTimeMillis());
        });

        rewardStandIds.add(stand.getUniqueId());
    }

    /** 在指定位置生成秘密祝福盔甲架（會停在該處直到玩家撿起或 10 秒後自動撿起）。用於 exit_room 等。 */
    public void spawnSecretBlessing(World world, Location loc) {
        if (world == null || loc == null) return;
        spawnRewardStand(world, loc, RewardType.SECRET, "");
    }

    private void tickProximity() {
        long now = System.currentTimeMillis();
        for (UUID id : List.copyOf(rewardStandIds)) {
            org.bukkit.entity.Entity e = Bukkit.getEntity(id);
            if (!(e instanceof ArmorStand stand) || !stand.isValid()) {
                rewardStandIds.remove(id);
                nearSinceMs.remove(id);
                continue;
            }

            String typeStr = stand.getPersistentDataContainer().get(rewardTypeKey, PersistentDataType.STRING);
            String worldName = stand.getPersistentDataContainer().get(rewardWorldKey, PersistentDataType.STRING);
            if (typeStr == null || worldName == null) continue;

            RewardType type;
            try {
                type = RewardType.valueOf(typeStr);
            } catch (Exception ex) {
                continue;
            }

            World world = stand.getWorld();
            if (world == null) continue;

            if (type == RewardType.SECRET || type == RewardType.DOOR_KEY) {
                Long spawnTime = stand.getPersistentDataContainer().get(rewardSpawnTimeKey, PersistentDataType.LONG);
                if (spawnTime != null && (now - spawnTime) >= AUTO_PICKUP_MS) {
                    Location standLoc = stand.getLocation();
                    double bestSq = AUTO_PICKUP_RADIUS * AUTO_PICKUP_RADIUS;
                    Player nearest = null;
                    for (Player p : world.getPlayers()) {
                        if (p == null || !p.isOnline()) continue;
                        double dSq = p.getLocation().distanceSquared(standLoc);
                        if (dSq < bestSq) {
                            bestSq = dSq;
                            nearest = p;
                        }
                    }
                    if (nearest != null) {
                        if (type == RewardType.SECRET) {
                            nearest.sendMessage(Component.text("你已撿了", NamedTextColor.GREEN));
                        } else {
                            PartyManager pm = plugin.getPartyManager();
                            if (pm != null) {
                                Player leader = pm.getLeader(nearest);
                                if (leader == null) leader = nearest;
                                db.addDoorKey(worldName, leader.getUniqueId());
                            }
                        }
                    }
                    stand.remove();
                    rewardStandIds.remove(id);
                    nearSinceMs.remove(id);
                    continue;
                }
            }

            Map<UUID, Long> playerTimes = nearSinceMs.computeIfAbsent(id, k -> new HashMap<>());
            Location standLoc = stand.getLocation();
            boolean pickedUp = false;

            for (Player player : world.getPlayers()) {
                if (player == null || !player.isOnline()) continue;
                boolean inRange = player.getLocation().distanceSquared(standLoc) <= PICKUP_RADIUS_SQ;
                UUID pid = player.getUniqueId();
                if (!inRange) {
                    playerTimes.remove(pid);
                    continue;
                }
                playerTimes.putIfAbsent(pid, now);
                long elapsed = now - playerTimes.get(pid);
                if (elapsed >= PICKUP_DELAY_MS) {
                    boolean success = tryPickup(player, type, worldName, stand);
                    if (success) {
                        stand.remove();
                        rewardStandIds.remove(id);
                        nearSinceMs.remove(id);
                        pickedUp = true;
                        break;
                    }
                }
            }
            if (!pickedUp) {
                for (Iterator<Map.Entry<UUID, Long>> it = playerTimes.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<UUID, Long> entry = it.next();
                    Player p = Bukkit.getPlayer(entry.getKey());
                    if (p == null || !p.isOnline() || p.getLocation().distanceSquared(standLoc) > PICKUP_RADIUS_SQ) {
                        it.remove();
                    }
                }
            }
        }
    }

    private boolean tryPickup(Player player, RewardType type, String worldName, ArmorStand stand) {
        switch (type) {
            case DOOR_KEY -> {
                PartyManager pm = plugin.getPartyManager();
                if (pm == null) return true;
                Player leader = pm.getLeader(player);
                if (leader == null) leader = player;
                db.addDoorKey(worldName, leader.getUniqueId());
                return true;
            }
            case SECRET -> {
                player.sendMessage(Component.text("你已撿了", NamedTextColor.GREEN));
                return true;
            }
            case REVIVE_STONE -> {
                ItemStack reviveItem = createReviveStone();
                if (player.getInventory().addItem(reviveItem).isEmpty()) {
                    return true;
                }
                return false;
            }
        }
        return true;
    }

    private static ItemStack createReviveStone() {
        ItemStack item = new ItemStack(Material.NETHER_STAR, 1);
        if (item.getItemMeta() != null) {
            item.editMeta(meta -> meta.displayName(Component.text("復活石", NamedTextColor.LIGHT_PURPLE)));
        }
        return item;
    }

    public void onDisable() {
        if (proximityTask != null) {
            proximityTask.cancel();
            proximityTask = null;
        }
        if (animationTask != null) {
            animationTask.cancel();
            animationTask = null;
        }
        for (UUID id : List.copyOf(rewardStandIds)) {
            org.bukkit.entity.Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        rewardStandIds.clear();
        nearSinceMs.clear();
    }

    DungeonRoomRewardDB getDb() {
        return db;
    }
}
