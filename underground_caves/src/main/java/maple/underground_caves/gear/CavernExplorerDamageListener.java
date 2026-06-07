package maple.underground_caves.gear;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/** 深窟探險者套裝：洞窟內穿齊四件時受到傷害減少 5% */
public final class CavernExplorerDamageListener implements Listener {

    private final CavernExplorerSetManager manager;

    public CavernExplorerDamageListener(CavernExplorerSetManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        int reduction = manager.getDamageReductionPercent(p);
        if (reduction <= 0) return;
        double mult = 1.0 - reduction / 100.0;
        e.setDamage(e.getDamage() * mult);
    }
}
