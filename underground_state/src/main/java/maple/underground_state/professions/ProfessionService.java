package maple.underground_state.professions;

import maple.underground_state.UndergroundState;
import maple.underground_state.gui.CollectionInfoGui;
import maple.underground_state.skills.*;
import maple.underground_state.skills.xp.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.CaveVines;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

public final class ProfessionService implements Listener {
    public static final NamespacedKey MOB_KEY = new NamespacedKey("underground_state", "profession_mob");
    private static final NamespacedKey PLACED = new NamespacedKey("underground_state", "profession_placed");
    private static final List<NamespacedKey> LEGACY_PLACED = List.of(new NamespacedKey("underground_state", "placed_mining_blocks"),new NamespacedKey("underground_state", "placed_farming_blocks"));
    private final UndergroundState plugin;
    private final org.bukkit.plugin.java.JavaPlugin schedulerPlugin;
    private final ProfessionStore store;
    private final Set<String> worlds;
    private final Map<String, Pending> pending = new HashMap<>();
    private final Map<String, List<Segment>> chains = new HashMap<>();
    private final Set<String> heldPhysics = new HashSet<>();
    private boolean validatingChain;
    private record Segment(Block block, boolean placed, List<ItemStack> drops) {}
    private BiConsumer<Player, SkillType> menu;

    public ProfessionService(UndergroundState plugin) {
        this(plugin,plugin,new ProfessionStore(plugin.getLogger()),configuredWorlds(plugin));
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, store::saveAll, 1200, 1200);
        for (Player p : Bukkit.getOnlinePlayers()) load(p.getUniqueId());
    }
    ProfessionService(UndergroundState plugin,org.bukkit.plugin.java.JavaPlugin schedulerPlugin,ProfessionStore store,Set<String> worlds) {
        this.plugin=plugin;this.schedulerPlugin=schedulerPlugin;this.store=store;this.worlds=Set.copyOf(worlds);
    }
    private static Set<String> configuredWorlds(UndergroundState plugin) {
        File config = new File(plugin.getDataFolder(), "professions.yml");
        if (!config.exists()) plugin.saveResource("professions.yml", false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(config);
        Set<String> worlds = new HashSet<>(yaml.getStringList("enabled-worlds"));
        if (worlds.isEmpty() && !Bukkit.getWorlds().isEmpty()) {
            worlds.add(Bukkit.getWorlds().getFirst().getName());
        }
        return worlds;
    }
    public ProfessionStore store() { return store; }
    public boolean enabled(World world) { return world != null && worlds.contains(world.getName()); }
    public int level(Player player, SkillType skill) {
        return SkillLevelTables.levelForXp(skill, plugin.getSkillProgressManager().getXp(player.getUniqueId(), skill));
    }
    public void setMenu(BiConsumer<Player, SkillType> menu) { this.menu = menu; }
    public void openMenu(Player player, SkillType skill) {
        if (menu != null) menu.accept(player, skill);
        else player.sendMessage("职业装备图鉴需要安装 underground_item。");
    }
    public ProfessionBonuses bonuses(Player player, SkillType skill) {
        return plugin.getPlayerStatsRuntime().getProfessionBonuses(player, skill);
    }
    public double collectionFortune(Player player, SkillType skill) {
        CollectionInfoGui.Category category = skill == SkillType.FARMING ? CollectionInfoGui.Category.FARMING : CollectionInfoGui.Category.MINING;
        int total = 0;
        for (var item : category.items()) {
            long amount = plugin.getCollectionProgressManager().getAmount(player.getUniqueId(), CollectionInfoGui.collectionKey(category, item.material()));
            for (long threshold : new long[]{5000, 50000, 250000}) if (amount >= threshold) total++;
        }
        return Math.min(30, total);
    }
    public void shutdown() { store.saveAll(); pending.clear(); chains.clear(); heldPhysics.clear(); harvests.clear(); menu = null; }
    private void load(UUID uuid) { Bukkit.getScheduler().runTaskAsynchronously(schedulerPlugin, () -> store.load(uuid)); }
    @EventHandler public void join(PlayerJoinEvent e) { load(e.getPlayer().getUniqueId()); }
    @EventHandler public void quit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(schedulerPlugin, () -> store.save(uuid));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void placed(BlockPlaceEvent e) {
        if (trackedPlacement(e.getBlock().getType())) mark(e.getBlock(), true);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void piston(BlockPistonExtendEvent e) { moveMarkers(e.getBlocks(), e.getDirection()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void piston(BlockPistonRetractEvent e) { moveMarkers(e.getBlocks(), e.getDirection()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void formed(BlockFormEvent e) {
        if (MiningXpService.baseXpTenths(e.getNewState().getType()) > 0) mark(e.getBlock(), true);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void exploded(EntityExplodeEvent e) { e.blockList().forEach(b -> mark(b, false)); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void exploded(BlockExplodeEvent e) { e.blockList().forEach(b -> mark(b, false)); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void checkTool(BlockBreakEvent e) {
        if(validatingChain)return;
        if(enabled(e.getBlock().getWorld()) && survival(e.getPlayer()) && !store.ready(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);e.getPlayer().sendActionBar(net.kyori.adventure.text.Component.text("职业存档尚未加载，请稍后采集。"));return;
        }
        if (!plugin.getPlayerStatsRuntime().canUseProfessionItem(e.getPlayer(), e.getPlayer().getInventory().getItemInMainHand())) {
            e.setCancelled(true); e.getPlayer().sendActionBar(net.kyori.adventure.text.Component.text("技能等级不足，无法使用这件职业工具。"));
            return;
        }
        if(enabled(e.getBlock().getWorld()) && stackCrop(e.getBlock().getType())) {
            validatingChain=true;
            try {
                for(Block upper=e.getBlock().getRelative(0,1,0);upper.getType()==e.getBlock().getType();upper=upper.getRelative(0,1,0)) {
                    BlockBreakEvent permission=new BlockBreakEvent(upper,e.getPlayer());Bukkit.getPluginManager().callEvent(permission);
                    if(permission.isCancelled()) { e.setCancelled(true);break; }
                }
            } finally { validatingChain=false; }
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void physics(BlockPhysicsEvent e) { if(heldPhysics.contains(key(e.getBlock())))e.setCancelled(true); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void broken(BlockBreakEvent e) {
        if(validatingChain)return;
        Player p = e.getPlayer(); Block b = e.getBlock(); Material source = b.getType();
        boolean wasPlaced = marked(b); mark(b, false);
        if (!enabled(b.getWorld()) || !survival(p) || !store.ready(p.getUniqueId()) || wasPlaced && !stackCrop(source) || !e.isDropItems()) return;
        int mining = MiningXpService.baseXpTenths(source), farming = FarmingXpService.baseXpTenths(source);
        boolean mineral=mining>0 || Set.of(Material.SAND,Material.RED_SAND,Material.GRAVEL,Material.DIRT,Material.CLAY).contains(source);
        SkillType skill = mineral ? SkillType.MINING : SkillType.FARMING;
        int xp = mining > 0 ? mining : farming;
        boolean collectionOnly=mineral || CollectionInfoGui.hasCollectionMaterial(CollectionInfoGui.Category.FARMING,source);
        if (xp <= 0 && !collectionOnly || (skill == SkillType.FARMING && !mature(b))) return;
        ItemStack tool = p.getInventory().getItemInMainHand().clone();
        boolean silk = skill == SkillType.MINING && tool.getEnchantmentLevel(Registry.ENCHANTMENT.get(NamespacedKey.minecraft("silk_touch"))) > 0;
        ItemStack calculation = tool.clone();
        int fortune = calculation.getEnchantmentLevel(Registry.ENCHANTMENT.get(NamespacedKey.minecraft("fortune")));
        calculation.removeEnchantment(Registry.ENCHANTMENT.get(NamespacedKey.minecraft("fortune")));
        List<ItemStack> raw = new ArrayList<>(b.getDrops(calculation, p).stream().map(ItemStack::clone).toList());
        List<Segment> segments=new ArrayList<>();
        if(stackCrop(source)) {
            for(Block upper=b.getRelative(0,1,0);upper.getType()==source;upper=upper.getRelative(0,1,0)) {
                boolean placed=marked(upper);
                List<ItemStack> drops=upper.getDrops(calculation,p).stream().map(ItemStack::clone).toList();
                segments.add(new Segment(upper,placed,drops));heldPhysics.add(key(upper));
                if(!placed)xp+=farming;
            }
            if(wasPlaced) { raw.clear();xp-=farming; }
            if(!segments.isEmpty())chains.put(key(b),segments);
        }
        if(xp<=0 && !collectionOnly) { segments.forEach(s -> heldPhysics.remove(key(s.block)));chains.remove(key(b));return; }
        if (raw.isEmpty() && segments.stream().allMatch(Segment::placed)) { releaseChain(key(b),false,source);return; }
        ProfessionBonuses bonus = bonuses(p, skill);
        if (!silk) bonus = new ProfessionBonuses(bonus.fortune() + Math.min(3, fortune) * 20, bonus.xpPercent(), bonus.rarePercent(), bonus.eventPercent(), bonus.eventTier());
        Pending snapshot = new Pending(p, skill, source, b.getLocation(), tool, bonus, raw, xp, level(p, skill), silk, fortune > 0);
        pending.put(key(b), snapshot);
        Bukkit.getScheduler().runTask(schedulerPlugin, () -> {
            if(pending.remove(key(b),snapshot))releaseChain(key(b),false,source);
        });
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void dropped(BlockDropItemEvent e) {
        Pending p = pending.remove(key(e.getBlock()));
        if (p == null || !e.getPlayer().equals(p.player) || e.getItems().isEmpty()) { if(p!=null)releaseChain(key(e.getBlock()),false,p.source);return; }
        List<Segment> chain=chains.getOrDefault(key(e.getBlock()),List.of());
        List<ItemStack> products = p.replaceFortune || !chain.isEmpty() ? new ArrayList<>(p.raw.stream().map(ItemStack::clone).toList()) : e.getItems().stream().map(i -> i.getItemStack().clone()).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<ItemStack> untouched=new ArrayList<>();
        for(var segment:chain) (segment.placed ? untouched : products).addAll(segment.drops.stream().map(ItemStack::clone).toList());
        if(stackCrop(p.source) && p.raw.isEmpty()) untouched.addAll(e.getItems().stream().map(i -> i.getItemStack().clone()).toList());
        if (!p.silk) boost(products, p.bonuses);
        List<ItemStack> all=new ArrayList<>(products);all.addAll(untouched);
        // Reuse pending native drop entities. Overflow is spawned only after the event completes.
        List<Item> original = new ArrayList<>(e.getItems());
        e.getItems().clear();
        for (int i=0;i<Math.min(all.size(),original.size());i++) {
            Item entity = original.get(i);
            entity.setItemStack(all.get(i)); e.getItems().add(entity);
        }
        for(int i=all.size();i<original.size();i++)original.get(i).remove();
        Bukkit.getScheduler().runTask(schedulerPlugin, () -> {
            releaseChain(key(e.getBlock()),!e.isCancelled(),p.source);
            if(e.isCancelled())return;
            for(int i=original.size();i<all.size();i++)drop(p.location,all.get(i));
            finish(p,products);
        });
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void harvested(PlayerHarvestBlockEvent e) {
        Player p = e.getPlayer(); Block b = e.getHarvestedBlock();
        if (!plugin.getPlayerStatsRuntime().canUseProfessionItem(p, p.getInventory().getItemInMainHand())) { e.setCancelled(true); return; }
        if(enabled(b.getWorld()) && survival(p) && !store.ready(p.getUniqueId())) { e.setCancelled(true);p.sendActionBar(net.kyori.adventure.text.Component.text("职业存档尚未加载，请稍后采集。"));return; }
        if (!enabled(b.getWorld()) || !survival(p) || !store.ready(p.getUniqueId()) || !mature(b)) return;
        int xp = FarmingXpService.baseXpTenths(b.getType());
        if (xp <= 0 || e.getItemsHarvested().isEmpty()) return;
        ProfessionBonuses bonus = bonuses(p, SkillType.FARMING);
        int fortune=p.getInventory().getItemInMainHand().getEnchantmentLevel(Registry.ENCHANTMENT.get(NamespacedKey.minecraft("fortune")));
        bonus=new ProfessionBonuses(bonus.fortune()+Math.min(3,fortune)*20,bonus.xpPercent(),bonus.rarePercent(),bonus.eventPercent(),bonus.eventTier());
        Pending snapshot = new Pending(p, SkillType.FARMING, b.getType(), b.getLocation(), p.getInventory().getItemInMainHand().clone(), bonus,
                List.of(), xp, level(p, SkillType.FARMING), false, false);
        // 等其他监听者确认权限后，修改实际收获并结算。
        harvests.put(e, snapshot);
    }
    private final Map<PlayerHarvestBlockEvent, Pending> harvests = new IdentityHashMap<>();
    @EventHandler(priority = EventPriority.MONITOR)
    public void finishHarvest(PlayerHarvestBlockEvent e) {
        Pending p = harvests.remove(e);
        if (p == null || e.isCancelled() || e.getItemsHarvested().isEmpty()) return;
        boost(e.getItemsHarvested(), p.bonuses);
        finish(p, e.getItemsHarvested());
    }
    private void finish(Pending p, List<ItemStack> products) {
        if(p.xp>0)grantXp(p.player, p.skill, p.xp, p.level, p.bonuses.xpPercent());
        if (p.silk) return;
        for (ItemStack stack : products) {
            if (stack.hasItemMeta() && !stack.getItemMeta().getPersistentDataContainer().isEmpty()) continue;
            Material material = stack.getType();
            if (material == Material.COBBLESTONE || material == Material.COBBLED_DEEPSLATE) material = Material.STONE;
            if (material == Material.MELON_SLICE) material = Material.MELON;
            if (material == Material.CLAY_BALL) material = Material.CLAY;
            if (material == Material.FLINT) material = Material.GRAVEL;
            if (material == Material.GLOWSTONE_DUST) material = Material.GLOWSTONE;
            if (material == Material.GOLD_NUGGET && p.source==Material.NETHER_GOLD_ORE) material = Material.GOLD_INGOT;
            if (material == Material.ANCIENT_DEBRIS) material = Material.NETHERITE_SCRAP;
            plugin.getCollectionProgressManager().addMaterial(p.player.getUniqueId(), material, stack.getAmount());
        }
        if(p.xp>0)Bukkit.getPluginManager().callEvent(new ProfessionGatherEvent(p.player, p.skill, p.source, p.location, p.tool,
                p.bonuses, products, p.skill == SkillType.MINING && !p.source.name().contains("ORE") && p.source != Material.AMETHYST_CLUSTER));
    }
    public void grantXp(Player player, SkillType skill, int baseTenths, int level, double bonus) {
        UUID uuid = player.getUniqueId(); String remainder = skill.name() + ":xp_remainder";
        if (store.get(uuid, skill.name() + ":remainder_migrated") == 0) {
            int old = skill == SkillType.FARMING ? plugin.getSkillProgressManager().getFarmingXpRemainderTenths(uuid) : plugin.getSkillProgressManager().getMiningXpRemainderTenths(uuid);
            store.set(uuid, remainder, old * 1000L); store.set(uuid, skill.name() + ":remainder_migrated", 1);
            if (skill == SkillType.FARMING) plugin.getSkillProgressManager().setFarmingXpRemainderTenths(uuid, 0);
            else plugin.getSkillProgressManager().setMiningXpRemainderTenths(uuid, 0);
        }
        long award = ProfessionMath.xpUnits(baseTenths, skill, level, bonus);
        long units = award + store.get(uuid, remainder);
        store.set(uuid, remainder, units % 10000);
        if (units >= 10000) plugin.getSkillRewardService().addSkillXp(uuid, skill, units / 10000);
        plugin.getCombatXpOverlayService().showSkillOverlay(player, skill, (int) Math.min(Integer.MAX_VALUE, award / 1000),
                plugin.getSkillProgressManager().getXp(uuid, skill) * 10 + units % 10000 / 1000);
    }
    private void boost(List<ItemStack> products, ProfessionBonuses bonus) {
        List<ItemStack> extra = new ArrayList<>();
        for (ItemStack stack : products) {
            if (stack.getType().name().endsWith("SEEDS") || stack.getType() == Material.POISONOUS_POTATO || stack.hasItemMeta() && !stack.getItemMeta().getPersistentDataContainer().isEmpty()) continue;
            int amount = stack.getAmount() + ProfessionMath.extraAmount(stack.getAmount(), bonus.fortune(), ThreadLocalRandom.current().nextDouble());
            stack.setAmount(Math.min(64, amount)); amount -= Math.min(64, amount);
            while (amount > 0) { ItemStack overflow = stack.clone(); overflow.setAmount(Math.min(64, amount)); extra.add(overflow); amount -= overflow.getAmount(); }
        }
        products.addAll(extra);
    }
    public static void drop(Location location, ItemStack stack) {
        int remaining = stack.getAmount();
        while (remaining > 0) { ItemStack part = stack.clone(); part.setAmount(Math.min(part.getMaxStackSize(), remaining)); location.getWorld().dropItemNaturally(location.clone().add(.5, .5, .5), part); remaining -= part.getAmount(); }
    }
    private static boolean survival(Player p) { return p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE; }
    private static boolean mature(Block b) {
        // Sugar cane/cactus age is a growth timer, not crop maturity.
        if(stackCrop(b.getType()))return true;
        if (b.getBlockData() instanceof Ageable age) return age.getAge() == age.getMaximumAge();
        if (b.getBlockData() instanceof CaveVines vines) return vines.isBerries();
        return true;
    }
    private static boolean trackedPlacement(Material m) {
        return MiningXpService.baseXpTenths(m) > 0 || CollectionInfoGui.hasCollectionMaterial(CollectionInfoGui.Category.MINING,m) || Set.of(Material.KELP,Material.BROWN_MUSHROOM,Material.RED_MUSHROOM,Material.SUGAR_CANE, Material.CACTUS, Material.BAMBOO, Material.PUMPKIN, Material.MELON, Material.BROWN_MUSHROOM_BLOCK, Material.RED_MUSHROOM_BLOCK, Material.MUSHROOM_STEM).contains(m);
    }
    private static String key(Block b) { return b.getWorld().getUID() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ(); }
    private static String local(Block b) { return (b.getX() & 15) + "," + b.getY() + "," + (b.getZ() & 15); }
    private static Set<String> markers(Block b) {
        String value = b.getChunk().getPersistentDataContainer().get(PLACED, PersistentDataType.STRING);
        return value == null || value.isBlank() ? new HashSet<>() : new HashSet<>(Arrays.asList(value.split(";")));
    }
    private static boolean marked(Block b) {
        if(markers(b).contains(local(b)))return true;
        for(var key:LEGACY_PLACED) {
            String raw=b.getChunk().getPersistentDataContainer().get(key,PersistentDataType.STRING);
            if(raw!=null && Arrays.asList(raw.split(";")).contains(local(b)))return true;
        }
        return false;
    }
    private static void mark(Block b, boolean placed) {
        Set<String> set = markers(b); if (placed) set.add(local(b)); else set.remove(local(b));
        var pdc = b.getChunk().getPersistentDataContainer();
        if (set.isEmpty()) pdc.remove(PLACED); else pdc.set(PLACED, PersistentDataType.STRING, String.join(";", set));
        if(!placed)for(var key:LEGACY_PLACED) {
            String raw=pdc.get(key,PersistentDataType.STRING);if(raw==null)continue;
            Set<String> legacy=new HashSet<>(Arrays.asList(raw.split(";")));legacy.remove(local(b));
            if(legacy.isEmpty())pdc.remove(key);else pdc.set(key,PersistentDataType.STRING,String.join(";",legacy));
        }
    }
    private static void moveMarkers(List<Block> blocks, org.bukkit.block.BlockFace direction) {
        List<Block> tracked = blocks.stream().filter(ProfessionService::marked).filter(b -> b.getPistonMoveReaction()!=org.bukkit.block.PistonMoveReaction.BREAK).toList();
        blocks.forEach(b -> mark(b, false)); tracked.forEach(b -> mark(b.getRelative(direction), true));
    }
    private static boolean stackCrop(Material material) { return material==Material.SUGAR_CANE || material==Material.CACTUS || material==Material.BAMBOO; }
    private void releaseChain(String key,boolean harvested,Material material) {
        var segments=chains.remove(key);if(segments==null)return;
        for(var segment:segments) {
            heldPhysics.remove(key(segment.block));
            if(harvested && segment.block.getType()==material) { mark(segment.block,false);segment.block.setType(Material.AIR,false); }
            else segment.block.getState().update(true,true);
        }
    }
    private record Pending(Player player, SkillType skill, Material source, Location location, ItemStack tool,
                           ProfessionBonuses bonuses, List<ItemStack> raw, int xp, int level, boolean silk, boolean replaceFortune) {}
}
