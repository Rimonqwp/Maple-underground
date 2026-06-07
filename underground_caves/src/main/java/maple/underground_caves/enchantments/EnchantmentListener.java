package maple.underground_caves.enchantments;

import maple.underground_caves.Test_dungeons;
import maple.underground_state.stats.PlayerStatsFormulas;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.util.Vector;

/**
 * 統一處理自定義附魔效果：攻擊傷害、受擊、擊殺、射箭、摔落、被動恢復等。
 */
public final class EnchantmentListener implements Listener {

    private static final long COMBO_RESET_MS = 5000L;
    private static final long COMBO_STORM_WINDOW_MS = 3000L;

    private final Test_dungeons plugin;
    private final EnchantmentRegistry registry;

    private final Map<UUID, ComboState> comboByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, KillLegacyState> killLegacyByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, ComboStormState> comboStormByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> noPainNoGainHits = new ConcurrentHashMap<>();
    private final Map<UUID, Long> caveBlessingLastHeal = new ConcurrentHashMap<>();
    /** killer -> (victim -> last damage dealt) for 獵殺傳承 */
    private final Map<UUID, Map<UUID, Double>> lastDamageByKillerVictim = new ConcurrentHashMap<>();
    /** 奇襲：玩家 -> 曾對該玩家造成過傷害的實體 UUID */
    private final Map<UUID, Set<UUID>> damagedByEntity = new ConcurrentHashMap<>();
    /** 先發制人/雷霆：玩家 -> (目標 -> 已命中次數) */
    private final Map<UUID, Map<UUID, Integer>> hitCountByPlayerTarget = new ConcurrentHashMap<>();

    private static final class ComboState {
        UUID lastTarget;
        int stacks;
        long lastHitMs;
    }
    private static final class KillLegacyState {
        double lastKillDamage;
        boolean pendingCrit;
    }
    private static final class ComboStormState {
        int stacks;
        long lastHitMs;
    }

    public EnchantmentListener(Test_dungeons plugin, EnchantmentRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        startCaveBlessingTask();
    }

    private void startCaveBlessingTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.isEnabled()) return;
                long now = System.currentTimeMillis();
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    if (p == null || !p.isOnline() || p.isDead()) continue;
                    int level = getMaxEnchantLevelOnArmor(p, "cave_blessing");
                    if (level <= 0) continue;
                    EnchantCaveBlessing ench = (EnchantCaveBlessing) registry.getById("cave_blessing");
                    if (ench == null) continue;
                    int intervalSec = ench.getIntervalSeconds(level);
                    Long last = caveBlessingLastHeal.get(p.getUniqueId());
                    if (last == null) caveBlessingLastHeal.put(p.getUniqueId(), now);
                    last = caveBlessingLastHeal.get(p.getUniqueId());
                    if (now - last >= intervalSec * 1000L) {
                        double max = p.getAttribute(Attribute.MAX_HEALTH) != null ? p.getAttribute(Attribute.MAX_HEALTH).getValue() : 20.0;
                        double heal = PlayerStatsFormulas.totalHealthGained(1.0, getVitality(p));
                        var bloodMgr = plugin.getBloodSetManager();
                        if (bloodMgr != null) {
                            double mult = 1.0;
                            if (bloodMgr.getHolyRegenMultiplier(p) > 1) mult = Math.max(mult, bloodMgr.getHolyRegenMultiplier(p));
                            for (Player q : p.getWorld().getPlayers()) {
                                if (q.equals(p)) continue;
                                if (bloodMgr.getHolyRadiusBlocks(q) > 0 && p.getLocation().distance(q.getLocation()) <= bloodMgr.getHolyRadiusBlocks(q))
                                    mult = Math.max(mult, bloodMgr.getHolyRegenMultiplier(q));
                            }
                            heal *= mult;
                        }
                        p.setHealth(Math.min(max, p.getHealth() + heal));
                        caveBlessingLastHeal.put(p.getUniqueId(), now);
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 20L);
    }

    private ItemStack getWeapon(Entity damager) {
        if (damager instanceof Player p) {
            ItemStack main = p.getInventory().getItemInMainHand();
            if (main.getType() != Material.AIR) return main;
            return p.getInventory().getItemInOffHand();
        }
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            ItemStack bow = p.getInventory().getItemInMainHand();
            if (bow.getType() == Material.BOW || bow.getType() == Material.CROSSBOW) return bow;
            return p.getInventory().getItemInOffHand();
        }
        return null;
    }

    private LivingEntity getAttacker(Entity damager) {
        if (damager instanceof LivingEntity le) return le;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof LivingEntity le) return le;
        return null;
    }

    private int getMaxEnchantLevelOnArmor(Player p, String enchantId) {
        int max = 0;
        for (ItemStack piece : p.getInventory().getArmorContents()) {
            if (piece == null || piece.getType() == Material.AIR) continue;
            int lvl = registry.getLevel(piece, enchantId);
            if (lvl > max) max = lvl;
        }
        return max;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        LivingEntity attacker = getAttacker(event.getDamager());
        if (attacker == null) return;
        ItemStack weapon = attacker instanceof Player ? getWeapon(event.getDamager()) : null;

        if (event.getEntity() instanceof LivingEntity victim) {
            double damage = event.getDamage();
            boolean isCrit = event.getDamager() instanceof Player && ((Player) event.getDamager()).getAttackCooldown() > 0.9f;

            if (attacker instanceof Player player && weapon != null && weapon.getType() != Material.AIR) {
                damage = applyWeaponEnchantsDamage(player, weapon, victim, damage, isCrit, event);
            }

            if (event.getEntity() instanceof Player victimPlayer) {
                damage = applyArmorEnchantsWhenHit(victimPlayer, damage, event);
                if (getAttacker(event.getDamager()) instanceof LivingEntity damagerLiving) {
                    damagedByEntity.computeIfAbsent(victimPlayer.getUniqueId(), u -> ConcurrentHashMap.newKeySet()).add(damagerLiving.getUniqueId());
                    if (damagerLiving instanceof Monster) {
                        int noPainLevel = getMaxEnchantLevelOnArmor(victimPlayer, "no_pain_no_gain");
                        applyNoPainNoGainOnHit(victimPlayer, noPainLevel, noPainNoGainHits);
                    }
                }
            }

            event.setDamage(Math.max(0.0001, damage));

            if (attacker instanceof Player player && weapon != null && weapon.getType() != Material.AIR && event.getEntity() instanceof LivingEntity victimLiving) {
                applyWeaponEnchantsPostDamage(player, weapon, victimLiving, damage, isCrit, event);
            }
        }
    }

    private double applyWeaponEnchantsDamage(Player player, ItemStack weapon, LivingEntity victim, double damage, boolean isCrit, EntityDamageByEntityEvent event) {
        double d = damage;
        UUID victimId = victim.getUniqueId();
        long now = System.currentTimeMillis();

        int allIn = registry.getLevel(weapon, "all_in");
        if (allIn > 0) {
            d *= ((EnchantAllIn) registry.getById("all_in")).getDamageMultiplier();
            return d;
        }

        int deadwood = registry.getLevel(weapon, "deadwood_revival");
        if (deadwood > 0) {
            EnchantDeadwoodRevival e = (EnchantDeadwoodRevival) registry.getById("deadwood_revival");
            d += e.getBonusDamage(deadwood);
        }

        int combo = registry.getLevel(weapon, "combo");
        int wildFury = registry.getLevel(weapon, "wild_fury");
        int effectiveComboStacks = 0;
        if (combo > 0) {
            EnchantCombo ec = (EnchantCombo) registry.getById("combo");
            ComboState state = comboByPlayer.computeIfAbsent(player.getUniqueId(), u -> new ComboState());
            if (state.lastTarget != null && state.lastTarget.equals(victimId) && (now - state.lastHitMs) <= COMBO_RESET_MS) {
                state.stacks = Math.min(state.stacks + 1, ec.getMaxStacks(combo));
            } else {
                state.stacks = 1;
            }
            state.lastTarget = victimId;
            state.lastHitMs = now;
            effectiveComboStacks = state.stacks;
            if (wildFury > 0) {
                effectiveComboStacks += ((EnchantWildFury) registry.getById("wild_fury")).getBonusCombo(wildFury);
            }
            d *= 1.0 + (ec.getPerStackPercent(combo) / 100.0) * effectiveComboStacks;
        }

        int comboStorm = registry.getLevel(weapon, "combo_storm");
        if (comboStorm > 0 && CustomEnchantment.SWORDS.contains(weapon.getType())) {
            EnchantComboStorm ecs = (EnchantComboStorm) registry.getById("combo_storm");
            ComboStormState state = comboStormByPlayer.computeIfAbsent(player.getUniqueId(), u -> new ComboStormState());
            if (now - state.lastHitMs > COMBO_STORM_WINDOW_MS) state.stacks = 0;
            state.stacks++;
            state.lastHitMs = now;
            int perStack = ecs.getPerStackPercent(comboStorm);
            double mult = 1.0 + (perStack / 100.0) * state.stacks;
            mult = Math.min(mult, 2.0);
            d *= mult;
        }

        int armorBreak = registry.getLevel(weapon, "armor_break");
        if (armorBreak > 0 && Math.random() < ((EnchantArmorBreak) registry.getById("armor_break")).getTrueDamageChance(armorBreak)) {
            if (victim instanceof Player) {
                PlayerStatsFormulas.markNextHitAsTrueDamage(victim);
            }
            event.setDamage(EntityDamageEvent.DamageModifier.ARMOR, 0);
            event.setDamage(EntityDamageEvent.DamageModifier.RESISTANCE, 0);
        }

        int killLegacy = registry.getLevel(weapon, "kill_legacy");
        if (killLegacy > 0 && isCrit) {
            KillLegacyState state = killLegacyByPlayer.get(player.getUniqueId());
            if (state != null && state.pendingCrit) {
                double mult = ((EnchantKillLegacy) registry.getById("kill_legacy")).getDamageMultiplier(killLegacy);
                d += state.lastKillDamage * (mult - 1.0);
                state.pendingCrit = false;
            }
        }

        int spiderKiller = registry.getLevel(weapon, "spider_killer");
        if (spiderKiller > 0) {
            EntityType t = victim.getType();
            if (t == EntityType.SPIDER || t == EntityType.CAVE_SPIDER || t == EntityType.SILVERFISH) {
                double bonus = ((EnchantSpiderKiller) registry.getById("spider_killer")).getDamageBonusPercent(spiderKiller);
                d *= 1.0 + bonus;
            }
        }

        int dragonSlayer = registry.getLevel(weapon, "dragon_slayer");
        if (dragonSlayer > 0 && victim.getType() == EntityType.ENDER_DRAGON) {
            d *= 1.0 + ((EnchantDragonSlayer) registry.getById("dragon_slayer")).getDamageBonusPercent(dragonSlayer);
        }

        int enderSlayer = registry.getLevel(weapon, "ender_slayer");
        if (enderSlayer > 0) {
            EntityType t = victim.getType();
            if (t == EntityType.ENDERMAN || t == EntityType.ENDERMITE || t == EntityType.ENDER_DRAGON) {
                d *= 1.0 + ((EnchantEnderSlayer) registry.getById("ender_slayer")).getDamageBonusPercent(enderSlayer);
            }
        }

        int loneFighter = registry.getLevel(weapon, "lone_fighter");
        if (loneFighter > 0) {
            EnchantLoneFighter elf = (EnchantLoneFighter) registry.getById("lone_fighter");
            int nearby = 0;
            for (Entity e : player.getNearbyEntities(elf.getRange(), elf.getRange(), elf.getRange())) {
                if (e instanceof Monster) nearby++;
            }
            int stacks = Math.max(0, (int) (nearby / 10));
            d *= 1.0 + (elf.getDamagePercentPerEnemy(loneFighter) / 100.0) * stacks;
        }

        int critStrike = registry.getLevel(weapon, "critical_strike");
        if (critStrike > 0 && isCrit) {
            d *= 1.0 + ((EnchantCriticalStrike) registry.getById("critical_strike")).getCritDamagePercent(critStrike);
        }

        int surpriseStrike = registry.getLevel(weapon, "surprise_strike");
        if (surpriseStrike > 0 && victim instanceof Monster) {
            Set<UUID> hitUs = damagedByEntity.get(player.getUniqueId());
            if (hitUs == null || !hitUs.contains(victim.getUniqueId())) {
                d *= 1.0 + ((EnchantSurpriseStrike) registry.getById("surprise_strike")).getDamageBonusPercent(surpriseStrike);
            }
        }

        int challenger = registry.getLevel(weapon, "challenger");
        if (challenger > 0) {
            double playerHp = player.getHealth();
            double victimHp = victim.getHealth();
            if (playerHp > 0.01) {
                EnchantChallenger ec = (EnchantChallenger) registry.getById("challenger");
                double ratio = ec.getHpRatioPerPercent(challenger) / 100.0;
                double excess = (victimHp - playerHp) / playerHp;
                if (excess > 0 && ratio > 0) {
                    double percentBonus = (excess / ratio) * ec.getDamagePerPercent(challenger);
                    d *= 1.0 + percentBonus;
                }
            }
        }

        int execute = registry.getLevel(weapon, "execute");
        if (execute > 0) {
            double maxHp = victim.getAttribute(Attribute.MAX_HEALTH) != null ? victim.getAttribute(Attribute.MAX_HEALTH).getValue() : 20.0;
            double currentHp = victim.getHealth();
            if (maxHp > 0) {
                double missingPercent = (maxHp - currentHp) / maxHp;
                if (missingPercent > 0) {
                    EnchantExecute ee = (EnchantExecute) registry.getById("execute");
                    d *= 1.0 + missingPercent * 100.0 * ee.getDamagePerMissingHpPercent(execute);
                }
            }
        }

        Map<UUID, Integer> hits = hitCountByPlayerTarget.computeIfAbsent(player.getUniqueId(), u -> new ConcurrentHashMap<>());
        int hitNum = hits.merge(victim.getUniqueId(), 1, Integer::sum);

        int firstStrike = registry.getLevel(weapon, "first_strike");
        if (firstStrike > 0 && hitNum <= 3) {
            EnchantFirstStrike efs = (EnchantFirstStrike) registry.getById("first_strike");
            d *= 1.0 + efs.getBonusPercent(firstStrike);
        }

        int thunderChain = registry.getLevel(weapon, "thunder_chain");
        int thunderGodChain = registry.getLevel(weapon, "thunder_god_chain");
        if (thunderChain > 0 && hitNum % 3 == 0) {
            EnchantThunderChain etc = (EnchantThunderChain) registry.getById("thunder_chain");
            double pct = etc.getDamagePercent(thunderChain);
            double finalD = d;
            org.bukkit.Location loc = victim.getLocation();
            double radius = etc.getRadius();
            int maxTargets = etc.getMaxTargets();
            new BukkitRunnable() {
                @Override
                public void run() {
                    player.getWorld().strikeLightningEffect(loc);
                    int n = 0;
                    for (Entity nearby : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
                        if (n >= maxTargets) break;
                        if (nearby instanceof LivingEntity le && le != player && !(le instanceof Player)) {
                            le.damage(finalD * pct, player);
                            n++;
                        }
                    }
                }
            }.runTaskLater(plugin, 1L);
        }
        if (thunderGodChain > 0 && hitNum % 3 == 0) {
            EnchantThunderGodChain etgc = (EnchantThunderGodChain) registry.getById("thunder_god_chain");
            double extra = d * etgc.getExtraDamagePercent(thunderGodChain);
            org.bukkit.Location loc = victim.getLocation();
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (victim.isValid()) victim.damage(extra, player);
                    victim.getWorld().strikeLightningEffect(loc);
                }
            }.runTaskLater(plugin, 1L);
        }

        int loneWolf = registry.getLevel(weapon, "lone_wolf");
        if (loneWolf > 0) {
            d += ((EnchantLoneWolf) registry.getById("lone_wolf")).getBonusDamage(loneWolf);
        }

        lastDamageByKillerVictim.computeIfAbsent(player.getUniqueId(), u -> new ConcurrentHashMap<>()).put(victim.getUniqueId(), d);
        return d;
    }

    private void applyWeaponEnchantsPostDamage(Player player, ItemStack weapon, LivingEntity victim, double damage, boolean isCrit, EntityDamageByEntityEvent event) {
        int overwhelming = registry.getLevel(weapon, "overwhelming");
        if (overwhelming > 0) {
            EnchantOverwhelming eo = (EnchantOverwhelming) registry.getById("overwhelming");
            if (Math.random() < eo.getKnockbackChance(overwhelming)) {
                Vector away = victim.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
                double blocks = eo.getKnockbackBlocks(overwhelming);
                away.multiply(blocks * 0.6);
                away.setY(0.35);
                victim.setVelocity(victim.getVelocity().add(away));
            }
        }
        int lifeSteal = registry.getLevel(weapon, "life_steal");
        if (lifeSteal > 0 && victim instanceof Monster) {
            EnchantLifeSteal els = (EnchantLifeSteal) registry.getById("life_steal");
            double maxHp = plugin.getPlayerStatsRuntime() != null ? plugin.getPlayerStatsRuntime().getEffectiveMaxHealth(player) : 20.0;
            double heal = PlayerStatsFormulas.totalHealthGained(maxHp * els.getHealPercentOfMaxHp(lifeSteal), getVitality(player));
            if (heal > 0) {
                if (plugin.getPlayerStatsRuntime() != null) plugin.getPlayerStatsRuntime().heal(player, heal);
                else player.setHealth(Math.min(maxHp, player.getHealth() + heal));
            }
        }
        int manaSteal = registry.getLevel(weapon, "mana_steal");
        if (manaSteal > 0) {
            EnchantManaSteal ems = (EnchantManaSteal) registry.getById("mana_steal");
            if (plugin.getPlayerStatsRuntime() != null && ems != null) {
                double maxMana = plugin.getPlayerStatsRuntime().getEffectiveMaxIntelligence(player);
                plugin.getPlayerStatsRuntime().restoreMana(player, maxMana * ems.getManaPercentOfMax(manaSteal));
            } else {
                player.giveExp(1);
            }
        }
        int bloodCrit = registry.getLevel(weapon, "blood_crit");
        if (bloodCrit > 0 && isCrit && damage > 0) {
            EnchantBloodCrit ebc = (EnchantBloodCrit) registry.getById("blood_crit");
            double maxHp = plugin.getPlayerStatsRuntime() != null ? plugin.getPlayerStatsRuntime().getEffectiveMaxHealth(player) : 20.0;
            double critCap = Math.min(damage, EnchantBloodCrit.MAX_CRIT_CAP);
            double heal = PlayerStatsFormulas.totalHealthGained((critCap / 100.0) * ebc.getHealPercentPer100Crit(bloodCrit) * maxHp, getVitality(player));
            if (heal > 0) {
                if (plugin.getPlayerStatsRuntime() != null) plugin.getPlayerStatsRuntime().heal(player, heal);
                else player.setHealth(Math.min(maxHp, player.getHealth() + heal));
            }
        }
    }

    /** 每 10 次被怪物擊中時有機率獲得經驗球 */
    private void applyNoPainNoGainOnHit(Player player, int level, Map<UUID, Integer> hitCount) {
        if (level <= 0) return;
        EnchantNoPainNoGain ench = (EnchantNoPainNoGain) registry.getById("no_pain_no_gain");
        if (ench == null) return;
        int count = hitCount.getOrDefault(player.getUniqueId(), 0) + 1;
        hitCount.put(player.getUniqueId(), count);
        if (count >= ench.getHitsThreshold()) {
            hitCount.put(player.getUniqueId(), 0);
            if (Math.random() < ench.getExpOrbChance(level)) {
                player.getWorld().spawn(player.getLocation(), ExperienceOrb.class, orb -> orb.setExperience(3 + level));
            }
        }
    }

    private double applyArmorEnchantsWhenHit(Player player, double damage, EntityDamageByEntityEvent event) {
        int lastStand = getMaxEnchantLevelOnArmor(player, "last_stand");
        if (lastStand > 0) {
            double hpRatio = player.getHealth() / (player.getAttribute(Attribute.MAX_HEALTH) != null ? player.getAttribute(Attribute.MAX_HEALTH).getValue() : 20.0);
            if (hpRatio <= ((EnchantLastStand) registry.getById("last_stand")).getHpThreshold()) {
                double bonus = ((EnchantLastStand) registry.getById("last_stand")).getDefenseBonusPercent(lastStand);
                damage *= 1.0 / (1.0 + bonus);
            }
        }
        return damage;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack boots = player.getInventory().getBoots();
        if (boots == null || boots.getType() == Material.AIR) return;
        int level = registry.getLevel(boots, "feather_wing");
        if (level <= 0) return;
        EnchantFeatherWing ench = (EnchantFeatherWing) registry.getById("feather_wing");
        if (ench == null) return;
        double reduce = ench.getDamageReducePercent(level) / 100.0;
        double immuneChance = ench.getBaseImmuneChance(level);
        if (Math.random() < immuneChance) {
            event.setCancelled(true);
            return;
        }
        event.setDamage(event.getDamage() * (1.0 - reduce));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;
        ItemStack weapon = killer.getInventory().getItemInMainHand();
        if (weapon.getType() == Material.AIR) weapon = killer.getInventory().getItemInOffHand();

        if (weapon != null && weapon.getType() != Material.AIR) {
            int fortuneCoins = registry.getLevel(weapon, "fortune_coins");
            if (fortuneCoins > 0) {
                double coins = ((EnchantFortuneCoins) registry.getById("fortune_coins")).getCoinsPerKill(fortuneCoins);
                for (int i = 0; i < (int) coins; i++)
                    killer.getWorld().spawn(killer.getLocation(), ExperienceOrb.class, orb -> orb.setExperience(1));
                if (coins % 1.0 > 0 && Math.random() < coins % 1.0)
                    killer.getWorld().spawn(killer.getLocation(), ExperienceOrb.class, orb -> orb.setExperience(1));
            }

            int killLegacy = registry.getLevel(weapon, "kill_legacy");
            if (killLegacy > 0) {
                KillLegacyState state = killLegacyByPlayer.computeIfAbsent(killer.getUniqueId(), u -> new KillLegacyState());
                Double lastDmg = lastDamageByKillerVictim.getOrDefault(killer.getUniqueId(), Map.of()).get(victim.getUniqueId());
                state.lastKillDamage = lastDmg != null ? lastDmg : 0.0;
                state.pendingCrit = true;
            }

            int twin = registry.getLevel(weapon, "twin");
            if (twin > 0) {
                EnchantTwin ench = (EnchantTwin) registry.getById("twin");
                if (ench != null) {
                    double chance = ench.getDoubleExpChance(twin);
                    if (chance > 0 && Math.random() < chance) {
                        int dropped = event.getDroppedExp();
                        if (dropped > 0) killer.giveExp(dropped);
                    }
                }
            }

            int bountyHunter = registry.getLevel(weapon, "bounty_hunter");
            if (bountyHunter > 0) {
                EnchantBountyHunter ebh = (EnchantBountyHunter) registry.getById("bounty_hunter");
                double coins = ebh.getCoinsPerKill(bountyHunter);
                for (int i = 0; i < (int) coins; i++)
                    killer.getWorld().spawn(killer.getLocation(), ExperienceOrb.class, orb -> orb.setExperience(1));
                if (coins % 1.0 > 0 && Math.random() < coins % 1.0)
                    killer.getWorld().spawn(killer.getLocation(), ExperienceOrb.class, orb -> orb.setExperience(1));
            }

            int killHeal = registry.getLevel(weapon, "kill_heal");
            if (killHeal > 0) {
                EnchantKillHeal ekh = (EnchantKillHeal) registry.getById("kill_heal");
                double maxHp = killer.getAttribute(Attribute.MAX_HEALTH) != null ? killer.getAttribute(Attribute.MAX_HEALTH).getValue() : 20.0;
                double missing = maxHp - killer.getHealth();
                if (missing > 0) {
                    double heal = PlayerStatsFormulas.totalHealthGained(missing * ekh.getHealMissingHpPercent(killHeal), getVitality(killer));
                    killer.setHealth(Math.min(maxHp, killer.getHealth() + heal));
                }
            }

            int armorShred = registry.getLevel(weapon, "armor_shred");
            if (armorShred > 0 && victim.getEquipment() != null) {
                EnchantArmorShred eas = (EnchantArmorShred) registry.getById("armor_shred");
                double bonus = eas.getArmorDropBonusPercent(armorShred);
                for (ItemStack piece : victim.getEquipment().getArmorContents()) {
                    if (piece != null && piece.getType() != Material.AIR && Math.random() < bonus)
                        event.getDrops().add(piece.clone());
                }
            }
        }
        hitCountByPlayerTarget.remove(killer.getUniqueId());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack bow = event.getBow();
        if (bow == null || bow.getType() == Material.AIR) return;

        int infiniteQuiver = registry.getLevel(bow, "infinite_quiver");
        if (infiniteQuiver > 0) {
            double chance = ((EnchantInfiniteQuiver) registry.getById("infinite_quiver")).getNoConsumeChance(infiniteQuiver);
            if (Math.random() < chance) {
                event.setConsumeArrow(false);
            }
        }

        int emberChase = registry.getLevel(bow, "ember_chase");
        if (emberChase > 0 && event.getProjectile() instanceof AbstractArrow mainArrow) {
            EnchantEmberChase ench = (EnchantEmberChase) registry.getById("ember_chase");
            double extraPercent = ench.getExtraArrowDamagePercent(emberChase) / 100.0;
            org.bukkit.scheduler.BukkitRunnable run = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!mainArrow.isValid() || mainArrow.isOnGround()) return;
                    AbstractArrow extra = player.getWorld().spawn(mainArrow.getLocation(), AbstractArrow.class, arr -> {
                        arr.setDamage(mainArrow.getDamage() * extraPercent);
                        arr.setShooter(player);
                        arr.setCritical(mainArrow.isCritical());
                    });
                    extra.setVelocity(mainArrow.getVelocity().clone());
                    cancel();
                }
            };
            run.runTaskLater(plugin, 1L);
        }
    }

    /** 不可使用鐵砧（不可融合、不可打在裝備上）：洞窟祝福、孤注一擲、元气小收藏、殺戮回春 */
    private static final Set<String> NO_ANVIL_ENCHANTS = Set.of("cave_blessing", "all_in", "collection_bonus", "kill_heal");

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack first = inv.getFirstItem();
        ItemStack second = inv.getSecondItem();
        if (first == null || first.getType() == Material.AIR || second == null || second.getType() == Material.AIR) return;

        if (registry.isCustomEnchantBook(first) && registry.isCustomEnchantBook(second)) {
            String id1 = registry.getEnchantBookId(first);
            String id2 = registry.getEnchantBookId(second);
            if (id1 != null && id1.equals(id2) && !NO_ANVIL_ENCHANTS.contains(id1)) {
                int l1 = registry.getEnchantBookLevel(first);
                int l2 = registry.getEnchantBookLevel(second);
                CustomEnchantment enchant = registry.getById(id1);
                if (enchant != null && l1 > 0 && l2 > 0) {
                    int anvilCap = 5;
                    int newLevel = Math.min(l1 + l2, Math.min(enchant.getMaxLevel(), anvilCap));
                    event.setResult(registry.createEnchantBook(id1, newLevel));
                    inv.setRepairCost(1);
                }
            }
            return;
        }

        if (!registry.isCustomEnchantBook(second)) return;
        String enchantId = registry.getEnchantBookId(second);
        if (enchantId != null && NO_ANVIL_ENCHANTS.contains(enchantId)) return;
        int level = registry.getEnchantBookLevel(second);
        if (enchantId == null || level < 1) return;
        CustomEnchantment enchant = registry.getById(enchantId);
        if (enchant == null || !enchant.appliesTo(first)) return;
        ItemStack result = first.clone();
        registry.addEnchant(result, enchantId, level);
        event.setResult(result);
        inv.setRepairCost(1);
    }

    /** 自身獲得治療用有效 Vitality：與 {@link Test_dungeons#getEffectiveVitalityForSelfHeal(org.bukkit.entity.Player)} 相同。 */
    private double getVitality(Player p) {
        if (p == null) return 100;
        return plugin.getEffectiveVitalityForHealing(p);
    }

    public EnchantmentRegistry getRegistry() { return registry; }
}
