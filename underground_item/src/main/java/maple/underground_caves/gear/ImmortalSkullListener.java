package maple.underground_caves.gear;

import maple.underground_state.stats.PlayerStatsData;
import maple.underground_item.UndergroundItem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 永恆的面具：抵擋一次怪物/BOSS 致死攻擊後，血量回满，並給予 3 秒傷害免疫與 +20 力量，冷卻 360 秒。
 */
public final class ImmortalSkullListener implements Listener {

    private final JavaPlugin plugin;
    private final ImmortalSkullManager skullManager;
    /** 冷卻結束時間（毫秒） */
    private final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();
    /** 傷害免疫結束時間（毫秒） */
    private final Map<UUID, Long> invulnerableUntil = new ConcurrentHashMap<>();

    public ImmortalSkullListener(JavaPlugin plugin, ImmortalSkullManager skullManager) {
        this.plugin = plugin;
        this.skullManager = skullManager;
    }

    /** 任何傷害：若玩家處於 3 秒免疫內則取消傷害。 */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Long until = invulnerableUntil.get(player.getUniqueId());
        if (until != null && System.currentTimeMillis() < until) {
            event.setCancelled(true);
            return;
        }
    }

    /** 實體攻擊：若為致死傷害且佩戴頭顱且冷卻已過，則免死並觸發增益。 */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getDamager() instanceof LivingEntity) || event.getDamager() instanceof Player) return;
        if (!skullManager.isWearingSkull(player)) return;

        long now = System.currentTimeMillis();
        Long cooldownEnd = cooldownUntil.get(player.getUniqueId());
        if (cooldownEnd != null && now < cooldownEnd) return;

        double finalDamage = event.getFinalDamage();
        double health = player.getHealth();
        if (health - finalDamage > 0) return;

        // 免死：取消此次傷害，血量回满，並觸發增益
        event.setCancelled(true);
        player.setHealth(player.getMaxHealth());
        // 若插件有自訂生命值（PlayerStats），一併回满至有效上限（基礎 + 頭顱 + 篩子等裝備加成）
        if (plugin instanceof UndergroundItem ui) {
            PlayerStatsData data = ui.getPlayerStatsManager().get(player);
            double maxH = ui.getEffectiveMaxHealth(player);
            data.setCurrentHealthWithCap(maxH, maxH);
            ui.getPlayerStatsManager().set(player.getUniqueId(), data);
        }

        ImmortalSkullConfig cfg = skullManager.getConfig();
        int cooldownMs = cfg.getCooldownSeconds() * 1000;
        int durationSec = cfg.getEffectDurationSeconds();
        int durationTicks = durationSec * 20;
        int strengthBonus = cfg.getEffectStrengthBonus();

        cooldownUntil.put(player.getUniqueId(), now + cooldownMs);
        invulnerableUntil.put(player.getUniqueId(), now + durationSec * 1000L);

        // 3 秒傷害免疫（Resistance 5 = 100% 減傷）
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, durationTicks, 4, false, true, true));
        // +20 力量為玩家屬性（stats），非藥水效果
        if (plugin instanceof UndergroundItem ui) {
            ui.getPlayerStatsManager().addTempStrengthBonus(player.getUniqueId(), strengthBonus, durationSec * 1000L);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                invulnerableUntil.remove(player.getUniqueId());
            }
        }.runTaskLater(plugin, durationTicks);
    }

    /** 永恆的面具不會掉落，僅能透過指令取得。 */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(skullManager::isImmortalSkull);
    }

    /** 手持永恆的面具右鍵空氣時，佩戴到頭盔欄位。 */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onRightClickAir(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        ItemStack inHand = event.getItem();
        if (inHand == null || inHand.isEmpty() || !skullManager.isImmortalSkull(inHand)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        PlayerInventory inv = player.getInventory();
        ItemStack helmet = inv.getHelmet();
        ItemStack oneSkull = inHand.clone();
        oneSkull.setAmount(1);

        if (helmet == null || helmet.getType().isAir()) {
            inv.setHelmet(oneSkull);
            inHand.setAmount(inHand.getAmount() - 1);
        } else {
            inv.setHelmet(oneSkull);
            inv.setItemInMainHand(helmet);
            if (inHand.getAmount() > 1) {
                ItemStack remainder = inHand.clone();
                remainder.setAmount(inHand.getAmount() - 1);
                inv.addItem(remainder);
            }
        }
    }
}
