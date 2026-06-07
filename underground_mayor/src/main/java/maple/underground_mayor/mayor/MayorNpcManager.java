package maple.underground_mayor.mayor;

import maple.underground_mayor.compat.CitizensCompat;
import maple.underground_mayor.reward.MayorBadgeDeliveryService;
import maple.underground_mayor.reward.MayorBadgeManager;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.SitTrait;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class MayorNpcManager implements Listener {
    private static final String ROLE_METADATA = "underground_mayor.role";
    private static final double FORWARD_OFFSET = 1.5D;
    private static final double SIDE_OFFSET = 1.5D;
    private static final String NPCS_FILE_NAME = "mayor-npcs.yml";
    private static final int NPC_POSITION_FORMAT_VERSION = 1;

    private final maple.underground_mayor.UndergroundMayor plugin;
    private final CitizensCompat citizensCompat;
    private final MayorCollectionManager collectionManager;
    private final MayorBadgeDeliveryService badgeDeliveryService;
    private final File npcsFile;
    private final YamlConfiguration npcsConfig;

    public MayorNpcManager(
            maple.underground_mayor.UndergroundMayor plugin,
            CitizensCompat citizensCompat,
            MayorCollectionManager collectionManager,
            MayorBadgeManager badgeManager,
            MayorBadgeDeliveryService badgeDeliveryService
    ) {
        this.plugin = plugin;
        this.citizensCompat = citizensCompat;
        this.collectionManager = collectionManager;
        this.badgeDeliveryService = badgeDeliveryService;
        this.npcsFile = new File(plugin.getDataFolder(), NPCS_FILE_NAME);
        this.npcsConfig = YamlConfiguration.loadConfiguration(npcsFile);
    }

    public boolean spawnNpcs(Player player) {
        if (!citizensCompat.isAvailable()) {
            player.sendMessage("Citizens 沒有載入，當前無法生成市長 NPC。");
            return false;
        }

        clearManagedNpcs();

        Location base = player.getLocation().clone();
        Vector forward = base.getDirection().setY(0);
        if (forward.lengthSquared() < 1.0E-4) {
            forward = new Vector(0, 0, 1);
        } else {
            forward.normalize();
        }
        Vector side = new Vector(-forward.getZ(), 0, forward.getX()).normalize();
        Location anchor = base.clone().add(forward.clone().multiply(FORWARD_OFFSET));

        MayorNpcRole[] roles = MayorNpcRole.values();
        double[] sideOffsets = {-SIDE_OFFSET, 0.0D, SIDE_OFFSET};
        int spawned = 0;

        for (int index = 0; index < roles.length; index++) {
            MayorNpcRole role = roles[index];
            Location desired = anchor.clone().add(side.clone().multiply(sideOffsets[index]));
            Location seat = findNearestSeatLocation(desired, base.getYaw());
            if (spawnMayorNpc(role, seat) != null) {
                spawned++;
            }
        }

        if (spawned == 0) {
            player.sendMessage("沒有成功生成任何 NPC。");
            return false;
        }

        saveNpcsConfig();
        player.sendMessage("已在你腳邊附近生成 " + spawned + " 個坐著的 NPC：莉迪、巴克、艾里斯。");
        return true;
    }

    public void restoreSavedNpcs() {
        if (!citizensCompat.isAvailable()) {
            return;
        }

        clearManagedNpcs();

        int restored = 0;
        for (MayorNpcRole role : MayorNpcRole.values()) {
            Location savedLocation = loadSavedLocation(role);
            if (savedLocation == null) {
                continue;
            }

            if (spawnMayorNpc(role, savedLocation) != null) {
                restored++;
            }
        }

        if (restored > 0) {
            saveNpcsConfig();
            plugin.getLogger().info("Restored " + restored + " mayor NPCs from saved positions.");
        }
    }

    public void sendProgress(CommandSender sender) {
        for (String line : collectionManager.describeProgressLines()) {
            sender.sendMessage(line);
        }
        if (sender instanceof Player player) {
            for (String line : collectionManager.describePlayerContributionLines(player.getUniqueId())) {
                sender.sendMessage(line);
            }
        }
    }

    public void shutdown() {
        clearManagedNpcs();
    }

    public boolean hideNpc(MayorNpcRole role) {
        if (role == null || !citizensCompat.isAvailable() || !CitizensAPI.hasImplementation()) {
            return false;
        }

        boolean removed = false;
        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        if (registry != null) {
            List<NPC> managed = new ArrayList<>();
            for (NPC npc : registry.sorted()) {
                managed.add(npc);
            }
            for (NPC npc : managed) {
                if (role == getManagedRole(npc)) {
                    citizensCompat.destroyNpc(npc);
                    removed = true;
                }
            }
        }

        npcsConfig.set("npcs." + role.name(), null);
        saveNpcsConfig();
        return removed;
    }

    public int hideAllNpcs() {
        int hidden = 0;
        for (MayorNpcRole role : MayorNpcRole.values()) {
            if (hideNpc(role)) {
                hidden++;
            }
        }
        return hidden;
    }

    @EventHandler
    public void onNpcRightClick(NPCRightClickEvent event) {
        if (handleNpcClick(event.getNPC(), event.getClicker(), true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onNpcLeftClick(NPCLeftClickEvent event) {
        if (handleNpcClick(event.getNPC(), event.getClicker(), false)) {
            event.setCancelled(true);
        }
    }

    private boolean handleNpcClick(NPC npc, Player player, boolean rightClick) {
        MayorNpcRole role = getManagedRole(npc);
        if (role == null || player == null) {
            return false;
        }

        if (role.isCollector()) {
            if (collectionManager.isRewardsFinalized()) {
                badgeDeliveryService.deliverPendingBadges(player, false);
            }

            sendCollectionPausedDialogue(player, role);
            return true;
        }

        sendCollectionPausedDialogue(player, role);
        return true;
    }

    private void sendCollectionPausedDialogue(Player player, MayorNpcRole role) {
        if (player == null || role == null) {
            return;
        }

        String speaker = role.displayName();
        player.sendMessage(speaker + "說：目前第一階段的材料已經收集完成了。");
        player.sendMessage(speaker + "說：接下來就先等巴克把後續的建造安排整理好。");
        player.sendMessage(speaker + "說：如果之後真的需要第二階段徵收建築材料，我們會再通知大家。");
    }

    private NPC spawnMayorNpc(MayorNpcRole role, Location seat) {
        if (role == null || seat == null) {
            return null;
        }

        NPC npc = citizensCompat.createPlayerNpc(seat, role.displayName(), null, role.skinData());
        if (npc == null) {
            return null;
        }

        npc.data().setPersistent(ROLE_METADATA, role.name());
        npc.setAlwaysUseNameHologram(false);
        npc.faceLocation(seat.clone().add(seat.getDirection()));
        saveLocation(role, seat);

        NPC finalNpc = npc;
        Location finalSeat = seat.clone();
        Bukkit.getScheduler().runTask(plugin, () -> {
            finalNpc.getOrAddTrait(SitTrait.class).setSitting(finalSeat);
            if (finalNpc.getEntity() != null) {
                finalNpc.getEntity().customName(Component.text(role.displayName()));
                finalNpc.getEntity().setCustomNameVisible(true);
            }
        });
        return npc;
    }

    private void clearManagedNpcs() {
        if (!citizensCompat.isAvailable() || !CitizensAPI.hasImplementation()) {
            return;
        }

        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        if (registry == null) {
            return;
        }

        List<NPC> managedNpcs = new ArrayList<>();
        for (NPC npc : registry.sorted()) {
            if (getManagedRole(npc) != null) {
                managedNpcs.add(npc);
            }
        }

        for (NPC npc : managedNpcs) {
            citizensCompat.destroyNpc(npc);
        }
    }

    private MayorNpcRole getManagedRole(NPC npc) {
        if (npc == null) {
            return null;
        }

        Object raw = npc.data().get(ROLE_METADATA);
        if (raw == null) {
            return null;
        }

        return MayorNpcRole.fromName(String.valueOf(raw));
    }

    private void saveLocation(MayorNpcRole role, Location location) {
        if (role == null) {
            return;
        }

        String basePath = "npcs." + role.name();
        if (location == null || location.getWorld() == null) {
            npcsConfig.set(basePath, null);
            return;
        }

        npcsConfig.set(basePath + ".world", location.getWorld().getName());
        npcsConfig.set(basePath + ".x", location.getX());
        npcsConfig.set(basePath + ".y", location.getY());
        npcsConfig.set(basePath + ".z", location.getZ());
        npcsConfig.set(basePath + ".yaw", location.getYaw());
        npcsConfig.set(basePath + ".pitch", location.getPitch());
        npcsConfig.set(basePath + ".format_version", NPC_POSITION_FORMAT_VERSION);
    }

    private Location loadSavedLocation(MayorNpcRole role) {
        String basePath = "npcs." + role.name();
        String worldName = npcsConfig.getString(basePath + ".world", "");
        if (worldName == null || worldName.isBlank()) {
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Saved mayor NPC world not found for " + role.name() + ": " + worldName);
            return null;
        }

        double x = npcsConfig.getDouble(basePath + ".x");
        double y = npcsConfig.getDouble(basePath + ".y");
        int formatVersion = npcsConfig.getInt(basePath + ".format_version", 0);
        if (formatVersion < NPC_POSITION_FORMAT_VERSION) {
            y += 1.0D;
        }
        double z = npcsConfig.getDouble(basePath + ".z");
        float yaw = (float) npcsConfig.getDouble(basePath + ".yaw");
        float pitch = (float) npcsConfig.getDouble(basePath + ".pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    private void saveNpcsConfig() {
        try {
            npcsConfig.save(npcsFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save mayor NPC positions: " + ex.getMessage());
        }
    }

    private Location findNearestSeatLocation(Location desired, float yaw) {
        World world = desired.getWorld();
        if (world == null) {
            return desired;
        }

        int x = desired.getBlockX();
        int z = desired.getBlockZ();
        int startY = Math.max(world.getMinHeight(), Math.min(world.getMaxHeight() - 3, desired.getBlockY()));

        for (int distance = 0; distance <= 24; distance++) {
            int lowerY = startY - distance;
            if (isSeatGround(world, x, lowerY, z)) {
                return new Location(world, x + 0.5D, lowerY + 1.0D, z + 0.5D, yaw, 0.0F);
            }

            if (distance == 0) {
                continue;
            }

            int upperY = startY + distance;
            if (upperY <= world.getMaxHeight() - 3 && isSeatGround(world, x, upperY, z)) {
                return new Location(world, x + 0.5D, upperY + 1.0D, z + 0.5D, yaw, 0.0F);
            }
        }

        int fallbackGroundY = Math.max(world.getMinHeight(), world.getHighestBlockYAt(x, z) - 1);
        return new Location(world, x + 0.5D, fallbackGroundY + 1.0D, z + 0.5D, yaw, 0.0F);
    }

    private boolean isSeatGround(World world, int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight() - 2) {
            return false;
        }

        Block ground = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block aboveHead = world.getBlockAt(x, y + 2, z);
        return ground.getType().isSolid() && head.isPassable() && aboveHead.isPassable();
    }
}
