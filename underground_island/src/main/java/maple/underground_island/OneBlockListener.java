package maple.underground_island;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

final class OneBlockListener implements Listener {
    private static final List<Material> STAGE_ONE_POOL = List.of(
            Material.DIRT,
            Material.STONE,
            Material.COBBLESTONE,
            Material.OAK_LOG,
            Material.BIRCH_LOG,
            Material.SPRUCE_LOG,
            Material.OAK_PLANKS
    );

    private final JavaPlugin plugin;
    private final IslandService islandService;
    private final IslandWorldService worldService;
    private final PortalItem portalItem;

    OneBlockListener(JavaPlugin plugin,
                     IslandService islandService,
                     IslandWorldService worldService,
                     PortalItem portalItem) {
        this.plugin = plugin;
        this.islandService = islandService;
        this.worldService = worldService;
        this.portalItem = portalItem;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCenterBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        IslandRecord island = worldService.islandForCenter(block).orElse(null);
        if (island == null) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        for (ItemStack drop : block.getDrops(tool)) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5D, 0.5D, 0.5D), drop);
        }

        block.setType(nextStageOneMaterial(), false);
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_STONE_PLACE, 0.7F, 1.15F);
        islandService.recordOneBlockMineAndReward(island, () -> spawnPortalReward(island));
    }

    private Material nextStageOneMaterial() {
        return STAGE_ONE_POOL.get(ThreadLocalRandom.current().nextInt(STAGE_ONE_POOL.size()));
    }

    private void spawnPortalReward(IslandRecord island) {
        World world = plugin.getServer().getWorld(island.worldName());
        if (world == null) {
            return;
        }
        Location center = island.center(world);
        Block chestBlock = findChestSpot(center);
        if (chestBlock == null) {
            world.dropItemNaturally(center.add(0.5D, 1.0D, 0.5D), portalItem.create());
            return;
        }
        chestBlock.setType(Material.CHEST, false);
        if (chestBlock.getState() instanceof Chest chest) {
            chest.getBlockInventory().clear();
            chest.getBlockInventory().addItem(portalItem.create());
            chest.update(true, false);
        }
        world.playSound(chestBlock.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
        for (Player player : world.getPlayers()) {
            player.sendMessage(Component.text("岛屿中心出现了一个奖励箱，里面有传送门方块。", NamedTextColor.LIGHT_PURPLE));
        }
    }

    private Block findChestSpot(Location center) {
        int[][] offsets = {
                {2, 0},
                {-2, 0},
                {0, 2},
                {0, -2},
                {2, 1},
                {-2, -1}
        };
        World world = center.getWorld();
        if (world == null) {
            return null;
        }
        for (int[] offset : offsets) {
            Block block = world.getBlockAt(center.getBlockX() + offset[0], center.getBlockY(), center.getBlockZ() + offset[1]);
            if (block.getType().isAir() || block.getType() == Material.CHEST) {
                Block floor = block.getRelative(0, -1, 0);
                if (floor.getType().isAir()) {
                    floor.setType(Material.OAK_PLANKS, false);
                }
                return block;
            }
        }
        return null;
    }
}
