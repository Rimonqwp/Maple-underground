package maple.underground_caves.gear;

import org.bukkit.Location;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 遠程蓄勢頭盔：記錄箭矢發射點，命中時依飛行距離增傷 */
public final class ArrowDistanceListener implements Listener {

    private final ArrowDistanceHelmetManager manager;
    private final Map<UUID, Location> arrowLaunchLocation = new ConcurrentHashMap<>();

    public ArrowDistanceListener(ArrowDistanceHelmetManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity() instanceof Arrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player p)) return;
        if (!manager.isWearingArrowDistanceHelmet(p)) return;
        arrowLaunchLocation.put(arrow.getUniqueId(), arrow.getLocation().clone());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamageByArrow(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Arrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player shooter)) return;
        if (!manager.isWearingArrowDistanceHelmet(shooter)) return;

        Location start = arrowLaunchLocation.remove(arrow.getUniqueId());
        if (start == null) return;

        double distance = start.distance(e.getEntity().getLocation());
        double mult = manager.getDamageMultiplier(distance);
        if (mult <= 1.0) return;

        e.setDamage(e.getDamage() * mult);
    }
}
