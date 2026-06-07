package maple.underground_caves.weapons.skill;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Firework;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * 取消 Showtime 煙花造成的傷害（傷害由 ShowtimeSkillRunner 依技能公式計算，避免重複結算）。
 */
public final class ShowtimeFireworkListener implements Listener {

    private final NamespacedKey showtimeFireworkKey;

    public ShowtimeFireworkListener(ShowtimeSkillRunner runner) {
        this.showtimeFireworkKey = runner.getShowtimeFireworkKey();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageByShowtimeFirework(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Firework fw)) return;
        if (fw.getPersistentDataContainer().has(showtimeFireworkKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }
}
