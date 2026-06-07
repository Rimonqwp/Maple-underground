package maple.underground_caves.gear;

import maple.underground_caves.Test_dungeons;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.entity.Witch;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 僅在地牢世界內擊殺「地牢女巫」（monsters.yml 的 WITCH、帶 PDC 標記）時，
 * 依配置機率掉落一件篩子身體套裝（隨機部位）。
 */
public final class SieveSetDropListener implements Listener {

    private static final String PDC_ENTITY_TYPE = "dungeon_monster_entity_type";

    private final Test_dungeons plugin;
    private final SieveSetManager sieveSetManager;

    public SieveSetDropListener(Test_dungeons plugin, SieveSetManager sieveSetManager) {
        this.plugin = plugin;
        this.sieveSetManager = sieveSetManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Witch witch)) return;
        if (!isDungeonWitch(witch)) return;
        Player killer = witch.getKiller();
        if (killer == null) return;
        if (plugin == null || !plugin.isDungeonWorld(killer.getWorld().getName())) return;
        if (sieveSetManager.getConfig().getDropChance() <= 0) return;
        if (ThreadLocalRandom.current().nextDouble() >= sieveSetManager.getConfig().getDropChance()) return;
        String floor = resolveFloor(killer);
        String slot = SieveSetConfig.SLOTS[ThreadLocalRandom.current().nextInt(SieveSetConfig.SLOTS.length)];
        ItemStack piece = sieveSetManager.createSievePiece(slot, floor);
        if (piece != null) {
            witch.getWorld().dropItemNaturally(witch.getLocation(), piece);
        }
    }

    /** 依玩家所在地牢世界取得樓層（F1/F2/F3），非地牢或無法判定則回傳 F1。 */
    private String resolveFloor(Player player) {
        if (plugin == null || player == null) return "F1";
        return plugin.getFloorForDungeonWorld(player.getWorld().getName());
    }

    /** 是否為地牢生成的女巫（PDC 類型與配置 drop.entity_type 一致，對應 monsters.yml 的 WITCH）。 */
    private boolean isDungeonWitch(Witch entity) {
        if (plugin == null || entity == null) return false;
        String configType = sieveSetManager.getConfig().getDropEntityType();
        if (configType == null || configType.isBlank()) return false;
        String type = entity.getPersistentDataContainer().get(
                new NamespacedKey(plugin, PDC_ENTITY_TYPE), PersistentDataType.STRING);
        return configType.equals(type);
    }
}
