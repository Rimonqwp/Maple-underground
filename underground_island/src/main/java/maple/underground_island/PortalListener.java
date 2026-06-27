package maple.underground_island;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PortalListener implements Listener {
    private static final long TRANSFER_COOLDOWN_MS = 5000L;

    private final JavaPlugin plugin;
    private final IslandWorldService worldService;
    private final PortalItem portalItem;
    private final IslandVelocityBridge velocityBridge;
    private final Map<UUID, Long> lastTransferRequest = new ConcurrentHashMap<>();

    PortalListener(JavaPlugin plugin,
                   IslandWorldService worldService,
                   PortalItem portalItem,
                   IslandVelocityBridge velocityBridge) {
        this.plugin = plugin;
        this.worldService = worldService;
        this.portalItem = portalItem;
        this.velocityBridge = velocityBridge;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortalBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!portalItem.isPortalBlock(item)) {
            return;
        }
        Player player = event.getPlayer();
        World world = event.getBlockPlaced().getWorld();
        if (!worldService.isManagedIslandWorld(world)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("传送门方块只能放在岛屿世界。", NamedTextColor.RED));
            return;
        }

        event.setCancelled(true);
        consumeOne(player, event.getHand());
        buildPortal(event.getBlockPlaced(), player);
        world.playSound(event.getBlockPlaced().getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.8F, 1.0F);
        player.sendMessage(Component.text("通往主城的传送门已建造。", NamedTextColor.LIGHT_PURPLE));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            return;
        }
        if (!worldService.isManagedIslandWorld(event.getPlayer().getWorld())) {
            return;
        }
        event.setCancelled(true);
        requestTransfer(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMoveIntoPortal(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || !worldService.isManagedIslandWorld(to.getWorld())) {
            return;
        }
        if (event.getFrom().getBlockX() == to.getBlockX()
                && event.getFrom().getBlockY() == to.getBlockY()
                && event.getFrom().getBlockZ() == to.getBlockZ()) {
            return;
        }
        if (to.getBlock().getType() == Material.NETHER_PORTAL) {
            requestTransfer(event.getPlayer());
        }
    }

    private void requestTransfer(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastTransferRequest.get(player.getUniqueId());
        if (last != null && now - last < TRANSFER_COOLDOWN_MS) {
            return;
        }
        lastTransferRequest.put(player.getUniqueId(), now);
        player.sendMessage(Component.text("正在传送到主城...", NamedTextColor.AQUA));
        velocityBridge.sendToMainCity(player);
    }

    private void consumeOne(Player player, EquipmentSlot hand) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack stack = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (stack.getAmount() <= 1) {
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
            return;
        }
        stack.setAmount(stack.getAmount() - 1);
    }

    private void buildPortal(Block placedBlock, Player player) {
        BlockFace face = yawToFace(player.getLocation().getYaw());
        boolean widthAlongX = face == BlockFace.NORTH || face == BlockFace.SOUTH;
        int dx = widthAlongX ? 1 : 0;
        int dz = widthAlongX ? 0 : 1;
        Block base = placedBlock.getRelative(-dx, 0, -dz);

        for (int w = 0; w < 4; w++) {
            for (int y = 0; y < 5; y++) {
                Block block = base.getRelative(dx * w, y, dz * w);
                boolean frame = w == 0 || w == 3 || y == 0 || y == 4;
                if (frame) {
                    block.setType(Material.OBSIDIAN, false);
                } else {
                    setPortalBlock(block, widthAlongX ? Axis.X : Axis.Z);
                }
            }
        }
    }

    private void setPortalBlock(Block block, Axis axis) {
        block.setType(Material.NETHER_PORTAL, false);
        BlockData data = Bukkit.createBlockData(Material.NETHER_PORTAL);
        if (data instanceof Orientable orientable) {
            orientable.setAxis(axis);
            block.setBlockData(orientable, false);
        }
    }

    private BlockFace yawToFace(float yaw) {
        float normalized = (yaw % 360 + 360) % 360;
        if (normalized >= 45 && normalized < 135) {
            return BlockFace.WEST;
        }
        if (normalized >= 135 && normalized < 225) {
            return BlockFace.NORTH;
        }
        if (normalized >= 225 && normalized < 315) {
            return BlockFace.EAST;
        }
        return BlockFace.SOUTH;
    }
}
