package maple.underground_caves.gear;

import maple.underground_caves.Test_dungeons;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 掉落邏輯由 defeat_tank_set.yml 的 drop 區塊決定：僅在地牢世界內擊殺 drop.monster_type 對應的怪時，
 * 依 drop.chance 機率掉落一件堅壁套裝。樓層依玩家所在地牢：F1→F1，F2→F2，F3→F3。
 */
public final class DefeatTankDropListener implements Listener {

    private static final String PDC_ENTITY_TYPE = "dungeon_monster_entity_type";

    private final Test_dungeons plugin;
    private final GearManager gearManager;

    public DefeatTankDropListener(Test_dungeons plugin, GearManager gearManager) {
        this.plugin = plugin;
        this.gearManager = gearManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Zombie zombie)) return;
        if (!isDropTriggerMonster(zombie)) return;
        Player killer = zombie.getKiller();
        if (killer == null) return;
        if (plugin == null || !plugin.isDungeonWorld(killer.getWorld().getName())) return;
        if (gearManager.getDefeatTankConfig().getDropChance() <= 0) return;
        if (ThreadLocalRandom.current().nextDouble() >= gearManager.getDefeatTankConfig().getDropChance()) return;
        String floor = resolveFloor(killer);
        String slot = DefeatTankSetConfig.SLOTS[ThreadLocalRandom.current().nextInt(DefeatTankSetConfig.SLOTS.length)];
        ItemStack piece = gearManager.createDefeatTankPiece(slot, floor);
        if (piece != null) {
            zombie.getWorld().dropItemNaturally(zombie.getLocation(), piece);
        }
    }

    /** 是否為配置中指定可觸發掉落的怪物類型（依 PDC 標記與 drop.monster_type 比對）。 */
    private boolean isDropTriggerMonster(Zombie entity) {
        if (plugin == null || entity == null) return false;
        String configType = gearManager.getDefeatTankConfig().getDropMonsterType();
        if (configType == null || configType.isBlank()) return false;
        String type = entity.getPersistentDataContainer().get(
                new NamespacedKey(plugin, PDC_ENTITY_TYPE), PersistentDataType.STRING);
        return configType.equals(type);
    }

    /** 依玩家所在地牢世界取得樓層（F1/F2/F3），非地牢或無法判定則回傳 F1。 */
    private String resolveFloor(Player player) {
        if (plugin == null || player == null) return "F1";
        return plugin.getFloorForDungeonWorld(player.getWorld().getName());
    }
}
