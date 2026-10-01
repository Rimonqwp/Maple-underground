package maple.underground_item.professions;

import maple.underground_state.UndergroundState;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

/** Validate identity and personal gates twice, then atomically consume one matrix. */
public final class ProfessionCrafting implements Listener {
    private final ProfessionCatalog catalog;
    private final java.util.function.BiPredicate<Player,ProfessionCatalog.Definition> gates;
    private final JavaPlugin plugin;
    private final Map<UUID,String> selected=new HashMap<>();
    private final Set<NamespacedKey> keys=new HashSet<>();
    public ProfessionCrafting(JavaPlugin plugin, ProfessionCatalog catalog, UndergroundState core) {
        this(plugin,catalog,core,(p,d) -> catalog.canCraft(p,d,core));
    }
    ProfessionCrafting(JavaPlugin plugin, ProfessionCatalog catalog, UndergroundState core,
                       java.util.function.BiPredicate<Player,ProfessionCatalog.Definition> gates) {
        this.plugin=plugin;this.catalog=catalog;this.gates=gates;
        for (var d:catalog.items()) {
            var cfg=d.config();
            NamespacedKey key=new NamespacedKey(plugin,"profession_"+d.id());
            if (cfg.contains("recipe")) {
                ShapedRecipe recipe=new ShapedRecipe(key,catalog.create(d.id())); recipe.shape("ANB","APB","ANB");
                for (String field:List.of("a","n","b","p")) recipe.setIngredient(field.toUpperCase(Locale.ROOT).charAt(0),new RecipeChoice.MaterialChoice(catalog.materialOf(cfg.getString("recipe."+field))));
                Bukkit.addRecipe(recipe); keys.add(key);
            } else if (cfg.contains("compression")) {
                ShapelessRecipe recipe=new ShapelessRecipe(key,catalog.create(d.id()));
                recipe.addIngredient(9,catalog.materialOf(cfg.getString("compression"))); Bukkit.addRecipe(recipe);keys.add(key);
            }
        }
    }
    public void select(Player p,String id) { selected.put(p.getUniqueId(),id); }
    public void close() { keys.forEach(Bukkit::removeRecipe);selected.clear(); }
    public boolean matches(ProfessionCatalog.Definition d, ItemStack[] matrix) {
        if (matrix.length!=9) return false;
        if (d.config().contains("compression")) return Arrays.stream(matrix).allMatch(i -> catalog.matches(i,d.config().getString("compression")));
        if (!d.config().contains("recipe")) return false;
        String[] layout={"a","n","b","a","p","b","a","n","b"};
        for (int i=0;i<9;i++) if (!catalog.matches(matrix[i],d.config().getString("recipe."+layout[i]))) return false;
        return true;
    }
    private ProfessionCatalog.Definition matching(Player player,CraftingInventory inv) {
        var requested=catalog.get(selected.get(player.getUniqueId()));
        if (requested!=null && matches(requested,inv.getMatrix())) return requested;
        // Preserve vanilla nine-item block recipes unless explicitly selected in the book.
        boolean custom=Arrays.stream(inv.getMatrix()).anyMatch(i -> catalog.identify(i)!=null);
        if (!custom && inv.getRecipe() instanceof Keyed keyed && !keys.contains(keyed.getKey())) return null;
        return catalog.items().stream().filter(d -> matches(d,inv.getMatrix())).findFirst().orElse(null);
    }
    private boolean customMatrix(ItemStack[] matrix) { return Arrays.stream(matrix).anyMatch(i -> catalog.identify(i)!=null); }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void prepare(PrepareItemCraftEvent e) {
        if (!(e.getView().getPlayer() instanceof Player p)) return;
        var inv=e.getInventory();var d=matching(p,inv);
        if (d!=null) inv.setResult(gates.test(p,d) ? catalog.upgrade(d,d.config().contains("recipe") ? inv.getMatrix()[4] : null) : null);
        else if (customMatrix(inv.getMatrix()) || inv.getRecipe() instanceof Keyed k && keys.contains(k.getKey())) inv.setResult(null);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void craft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        var inv=e.getInventory();var d=matching(p,inv);
        if (d==null) { if (customMatrix(inv.getMatrix()) || e.getRecipe() instanceof Keyed k && keys.contains(k.getKey())) e.setCancelled(true);return; }
        e.setCancelled(true);
        if (!gates.test(p,d)) { p.sendMessage("收藏、技能等级或个人事件挑战尚未满足。");return; }
        boolean bulk=d.config().contains("compression");
        if (e.getClick()!=ClickType.LEFT && e.getClick()!=ClickType.RIGHT && !(bulk && e.isShiftClick())) return;
        ItemStack[] matrix=inv.getMatrix();
        if (!matches(d,matrix)) return;
        int count=bulk && e.isShiftClick() ? Math.min(64,Arrays.stream(matrix).mapToInt(ItemStack::getAmount).min().orElse(1)) : 1;
        ItemStack output=catalog.upgrade(d,d.config().contains("recipe") ? matrix[4] : null);output.setAmount(count);
        if (e.isShiftClick()) {
            int room=0;
            for (ItemStack slot:p.getInventory().getStorageContents()) room+=slot==null || slot.getType().isAir() ? output.getMaxStackSize() : slot.isSimilar(output) ? Math.max(0,slot.getMaxStackSize()-slot.getAmount()) : 0;
            count=Math.min(count,room);if(count<1)return;output.setAmount(count);
        } else {
            ItemStack cursor=e.getCursor();
            if (cursor!=null && !cursor.getType().isAir()) {
                if (!cursor.isSimilar(output) || cursor.getAmount()+count>output.getMaxStackSize()) return;
                output.setAmount(cursor.getAmount()+count);
            }
        }
        // All conditions and output capacity have been checked before consuming any input.
        for (int i=0;i<9;i++) { matrix[i]=matrix[i].clone();matrix[i].setAmount(matrix[i].getAmount()-count);if(matrix[i].getAmount()==0)matrix[i]=null; }
        inv.setMatrix(matrix);
        if(e.isShiftClick()) p.getInventory().addItem(output);else p.setItemOnCursor(output);
        inv.setResult(null);
        Bukkit.getScheduler().runTask(plugin,p::updateInventory);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void automatic(org.bukkit.event.block.CrafterCraftEvent e) {
        var state=e.getBlock().getState();
        var result=catalog.identify(e.getResult());
        if(!(state instanceof org.bukkit.block.Crafter crafter)) { if(result!=null)e.setCancelled(true);return; }
        var matrix=crafter.getInventory().getContents();
        var compression=catalog.items().stream().filter(d -> d.config().contains("compression") && matches(d,matrix)).findFirst().orElse(null);
        if(customMatrix(matrix)) {
            if(compression==null)e.setCancelled(true);else e.setResult(catalog.create(compression.id()));
        } else if(result!=null) {
            if(compression==null || !result.kind().equals("material"))e.setCancelled(true);
            else e.setResult(catalog.create(compression.id()));
        }
    }
    @EventHandler public void quit(org.bukkit.event.player.PlayerQuitEvent e) { selected.remove(e.getPlayer().getUniqueId()); }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void smithing(PrepareSmithingEvent e) {
        if(Arrays.stream(e.getInventory().getContents()).anyMatch(i -> catalog.identify(i)!=null))e.setResult(null);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void smithing(SmithItemEvent e) {
        if(Arrays.stream(e.getInventory().getContents()).anyMatch(i -> catalog.identify(i)!=null))e.setCancelled(true);
    }
}
