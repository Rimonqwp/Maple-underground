package maple.underground_item.professions;

import maple.underground_state.UndergroundState;
import maple.underground_state.gui.CollectionInfoGui;
import maple.underground_state.skills.SkillType;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.configuration.ConfigurationSection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import java.util.*;

public final class ProfessionBook implements Listener {
    private final UndergroundState core;
    private final ProfessionCatalog catalog;
    private final ProfessionCrafting crafting;
    private static final class Page implements InventoryHolder {
        final UUID owner;final SkillType skill;final String type,target;final int index;
        final List<String> history;
        final Map<Integer,String> actions=new HashMap<>();Inventory inventory;
        Page(Player p,SkillType skill,String type,String target,int index,List<String> history) { owner=p.getUniqueId();this.skill=skill;this.type=type;this.target=target;this.index=index;this.history=List.copyOf(history); }
        @Override public Inventory getInventory() { return inventory; }
    }
    public ProfessionBook(UndergroundState core,ProfessionCatalog catalog,ProfessionCrafting crafting) { this.core=core;this.catalog=catalog;this.crafting=crafting; }
    public void open(Player p,SkillType skill) { list(p,skill,"all"); }
    private Page page(Player p,SkillType skill,String type,String target,int index,String title) {
        return page(p,skill,type,target,index,title,List.of());
    }
    private Page page(Player p,SkillType skill,String type,String target,int index,String title,List<String> history) {
        Page page=new Page(p,skill,type,target,index,history);page.inventory=Bukkit.createInventory(page,54,Component.text(title));
        button(page,49,Material.BOOK,"登记背包与已穿戴装备",List.of("不消耗物品，交易所得也可登记"),"register");
        button(page,53,Material.BARRIER,"关闭",List.of(),"close");return page;
    }
    private void button(Page page,int slot,Material material,String title,List<String> lore,String action) {
        ItemStack item=new ItemStack(material);var meta=item.getItemMeta();meta.displayName(Component.text(title));meta.lore(lore.stream().map(ProfessionCatalog::line).toList());item.setItemMeta(meta);
        page.inventory.setItem(slot,item);page.actions.put(slot,action);
    }
    private String job(SkillType skill) { return skill==SkillType.FARMING ? "farming" : "mining"; }
    private void list(Player p,SkillType skill,String branch) {
        Page page=page(p,skill,"list",branch,0,(skill==SkillType.FARMING ? "农业" : "挖矿")+"职业装备图鉴");
        Set<String> known=core.getProfessionService().store().discoveries(p.getUniqueId());
        int slot=0,completedAlternative=0;
        for(var entry:catalog.sets().entrySet()) {
            var s=entry.getValue();if(!job(skill).equals(s.getString("job")))continue;
            var parts=catalog.items().stream().filter(d -> d.kind().equals("armor") && d.set().equals(entry.getKey())).toList();
            long collected=parts.stream().filter(d -> known.contains(d.id())).count();
            if(collected==4 && !s.getString("branch").equals("main"))completedAlternative++;
            if(!branch.equals("all") && !s.getString("branch").equals(branch))continue;
            List<String> lore=new ArrayList<>(List.of("已收集部位："+collected+" / 4", "使用等级："+s.getInt("level")));
            for(String field:List.of("fortune","xp","rare","event")) if(s.getDouble(field)>0) lore.add("整套"+ProfessionCatalog.label(field)+" +"+s.getDouble(field)+(field.equals("fortune") ? "" : "%"));
            lore.add("未登记："+String.join("、",parts.stream().filter(d -> !known.contains(d.id())).map(ProfessionCatalog.Definition::name).toList()));
            button(page,slot++,collected==4 ? Material.ENCHANTED_BOOK : Material.LEATHER_CHESTPLATE,s.getString("name"),lore,"set:"+entry.getKey());
        }
        int filter=36;
        for(String b:List.of("all","main","yield","experience","balanced","rare","event"))
            button(page,filter++,Material.PAPER,switch(b) { case "all"->"全部套装";case "main"->"六阶主线";case "yield"->"产量分支";case "experience"->"经验分支";case "balanced"->"综合分支";case "rare"->"稀有分支";default->"事件分支"; },List.of(),"branch:"+b);
        button(page,45,Material.COMPASS,"切换职业",List.of(),"switch");
        button(page,48,Material.PAPER,"压缩与事件材料配方",List.of("保留原版方块配方；点击选择职业压缩配方"),"materials:0");
        button(page,50,Material.NAME_TAG,"收藏徽记",List.of("副套装完整登记："+completedAlternative+" / 10", "五套徽记："+(completedAlternative>=5 ? "已获得" : "未获得"), "十套徽记："+(completedAlternative==10 ? "已获得" : "未获得"), "徽记仅供展示"),"noop");
        p.openInventory(page.inventory);
    }
    private void set(Player p,SkillType skill,String id) {
        ConfigurationSection set=catalog.set(id);if(set==null)return;
        Page page=page(p,skill,"set",id,0,set.getString("name"));int slot=10;
        var known=core.getProfessionService().store().discoveries(p.getUniqueId());
        for(var d:catalog.items()) if(d.set().equals(id) && !d.kind().equals("material")) {
            ItemStack display=catalog.create(d.id());var meta=display.getItemMeta();var lore=new ArrayList<>(meta.lore());
            lore.add(ProfessionCatalog.line(d.kind().equals("armor") ? known.contains(d.id()) ? "图鉴：已登记" : "图鉴：未登记" : "职业进阶物品"));
            catalog.requirements(d).forEach(r -> lore.add(ProfessionCatalog.line(r)));lore.add(ProfessionCatalog.line("点击查看合成配方"));meta.lore(lore);display.setItemMeta(meta);
            page.inventory.setItem(slot,display);page.actions.put(slot++,"item:"+d.id());
        }
        button(page,45,Material.ARROW,"返回套装目录",List.of(),"back");p.openInventory(page.inventory);
    }
    private void item(Player p,SkillType skill,String id) {
        item(p,skill,id,List.of());
    }
    private void item(Player p,SkillType skill,String id,List<String> history) {
        var d=catalog.get(id);if(d==null)return;
        String displayTitle=displayNameWithBadge(d)+" · 合成配方";
        Page page=page(p,skill,"item",id,0,displayTitle,history);
        page.inventory.setItem(4,createDisplayIcon(d,List.of()));
        String[] layout={"a","n","b","a","p","b","a","n","b"};int[] slots={10,11,12,19,20,21,28,29,30};
        for(int i=0;i<9;i++) {
            String token=d.config().contains("compression") ? d.config().getString("compression") : d.config().getString("recipe."+layout[i]);
            if(token==null)continue;
            if(token.startsWith("vanilla:")) {
                Material mat=catalog.materialOf(token);
                ItemStack ingredient=new ItemStack(mat);
                var meta=ingredient.getItemMeta();
                if(meta!=null) {
                    meta.displayName(Component.text(vanillaDisplayName(mat),NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC,false));
                    meta.lore(List.of(Component.text("基础原材料（"+mat.name()+"）",NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false)));
                    ingredient.setItemMeta(meta);
                }
                page.inventory.setItem(slots[i],ingredient);
            } else {
                var sub=catalog.get(token);
                if(sub==null)continue;
                boolean craftable=sub.config().contains("compression") || sub.config().contains("recipe");
                List<Component> hint=craftable
                        ? List.of(Component.empty(),Component.text("▶ 点击进入查看「"+sub.name()+"」的 3×3 合成配方",NamedTextColor.AQUA,TextDecoration.BOLD).decoration(TextDecoration.ITALIC,false))
                        : List.of(Component.empty(),Component.text("（基础掉落材料，不可继续向下拆分）",NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false));
                page.inventory.setItem(slots[i],createDisplayIcon(sub,hint));
                if(craftable)page.actions.put(slots[i],"drill:"+token);
            }
        }
        List<String> conditions=new ArrayList<>(catalog.requirements(d));conditions.add(catalog.canCraft(p,d,core) ? "个人条件已满足" : "个人条件尚未满足");
        button(page,34,Material.WRITABLE_BOOK,"制作条件",conditions,"noop");
        if(d.config().contains("recipe") || d.config().contains("compression"))button(page,40,Material.CRAFTING_TABLE,"选择此合成配方",List.of("随后使用原版合成台按左侧配方制作", "装备每次一件；压缩材料支持 Shift 批量制作"),"select:"+id);
        else button(page,40,Material.IRON_SWORD,"采集或事件产出",List.of("稀有碎片来自手动采集，核心来自职业事件"),"noop");
        if(!history.isEmpty()) {
            String prevId=history.get(history.size()-1);
            var prevDef=catalog.get(prevId);
            String prevName=prevDef!=null ? displayNameWithBadge(prevDef) : prevId;
            button(page,45,Material.ARROW,"返回上一层 ("+prevName+")",List.of("退回上一个正在查看的合成配方"),"pop");
            button(page,46,Material.COMPASS,"返回套装目录",List.of("直接回到职业装备图鉴首页"),"back");
        } else {
            button(page,45,Material.ARROW,"返回套装目录",List.of(),"back");
        }
        p.openInventory(page.inventory);
    }
    private void materials(Player p,SkillType skill,int index) {
        List<ProfessionCatalog.Definition> materials=catalog.items().stream().filter(d -> d.kind().equals("material") && (d.config().getString("job")==null || d.skill()==skill)).toList();
        int safe=Math.max(0,Math.min(index,(materials.size()-1)/36));Page page=page(p,skill,"materials","",safe,"职业材料 · "+(safe+1));
        for(int i=safe*36;i<Math.min(materials.size(),safe*36+36);i++) { var d=materials.get(i);page.inventory.setItem(i%36,createDisplayIcon(d,List.of()));page.actions.put(i%36,"item:"+d.id()); }
        button(page,45,Material.ARROW,"返回",List.of(),"back");
        if(safe>0)button(page,46,Material.ARROW,"上一页",List.of(),"materials:"+(safe-1));
        if((safe+1)*36<materials.size())button(page,48,Material.ARROW,"下一页",List.of(),"materials:"+(safe+1));p.openInventory(page.inventory);
    }
    private boolean isCompressedPaper(ProfessionCatalog.Definition d) {
        return d.material()==Material.PAPER && d.config().contains("compression");
    }
    private Material resolveBaseMaterial(String token) {
        String current=token;
        for(int depth=0;depth<10 && current!=null;depth++) {
            if(current.startsWith("vanilla:")) {
                try { return Material.valueOf(current.substring(8)); } catch(IllegalArgumentException ignored) { return null; }
            }
            var sub=catalog.get(current);
            if(sub==null || !sub.config().contains("compression"))return null;
            current=sub.config().getString("compression");
        }
        return null;
    }
    private String displayNameWithBadge(ProfessionCatalog.Definition d) {
        if(!isCompressedPaper(d))return d.name();
        double rv=d.number("raw_value");
        int tier=rv>=6561 ? 4 : rv>=729 ? 3 : rv>=81 ? 2 : rv>0 ? 1 : 0;
        return tier>0 ? d.name()+" [压"+CollectionInfoGui.roman(tier)+"]" : d.name();
    }
    private String vanillaDisplayName(Material mat) {
        var col=CollectionInfoGui.findAnyItem(mat);
        if(col!=null)return col.name();
        return switch(mat) {
            case LEATHER_HELMET -> "皮革头盔";
            case LEATHER_CHESTPLATE -> "皮革胸甲";
            case LEATHER_LEGGINGS -> "皮革护腿";
            case LEATHER_BOOTS -> "皮革靴子";
            case IRON_HOE -> "铁锄";
            case DIAMOND_HOE -> "钻石锄";
            case NETHERITE_HOE -> "下界合金锄";
            case IRON_PICKAXE -> "铁镐";
            case DIAMOND_PICKAXE -> "钻石镐";
            case NETHERITE_PICKAXE -> "下界合金镐";
            case IRON_SWORD -> "铁剑";
            case DIAMOND_SWORD -> "钻石剑";
            case NETHERITE_SWORD -> "下界合金剑";
            default -> mat.name();
        };
    }
    private ItemStack createDisplayIcon(ProfessionCatalog.Definition d,List<Component> extraLore) {
        if(isCompressedPaper(d)) {
            Material base=resolveBaseMaterial(d.config().getString("compression"));
            ItemStack stack=new ItemStack(base!=null ? base : d.material());
            var meta=stack.getItemMeta();
            if(meta!=null) {
                meta.displayName(Component.text(displayNameWithBadge(d),NamedTextColor.GOLD).decoration(TextDecoration.BOLD,true).decoration(TextDecoration.ITALIC,false));
                List<Component> lore=new ArrayList<>();
                for(Component line:catalog.description(d))lore.add(line.decoration(TextDecoration.ITALIC,false));
                lore.addAll(extraLore);
                meta.lore(lore);
                meta.addEnchant(Enchantment.UNBREAKING,1,true);
                meta.addItemFlags(ItemFlag.values());
                stack.setItemMeta(meta);
            }
            return stack;
        }
        ItemStack stack=catalog.create(d.id());
        var meta=stack.getItemMeta();
        if(meta!=null && !extraLore.isEmpty()) {
            List<Component> lore=meta.lore()!=null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.addAll(extraLore);
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }
    public int register(Player p) {
        var store=core.getProfessionService().store();if(!store.ready(p.getUniqueId())) { p.sendMessage("职业存档正在加载，请稍后再试。");return 0; }
        int count=0;for(ItemStack item:p.getInventory().getContents()) {
            var d=catalog.identify(item);if(d==null || !d.kind().equals("armor"))continue;
            String key="book:"+d.id();if(store.get(p.getUniqueId(),key)>0)continue;store.set(p.getUniqueId(),key,1);count++;
        }
        p.sendMessage("登记完成：新增 "+count+" 件装备，物品已保留。");return count;
    }
    @EventHandler public void clicked(InventoryClickEvent e) {
        if(!(e.getView().getTopInventory().getHolder() instanceof Page page))return;e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p) || !p.getUniqueId().equals(page.owner))return;
        String action=page.actions.get(e.getRawSlot());if(action==null)return;
        if(action.equals("close"))p.closeInventory();
        else if(action.equals("register")) { register(p);list(p,page.skill,"all"); }
        else if(action.equals("back"))open(p,page.skill);
        else if(action.equals("switch"))open(p,page.skill==SkillType.FARMING ? SkillType.MINING : SkillType.FARMING);
        else if(action.startsWith("branch:"))list(p,page.skill,action.substring(7));
        else if(action.startsWith("set:"))set(p,page.skill,action.substring(4));
        else if(action.startsWith("item:"))item(p,page.skill,action.substring(5));
        else if(action.startsWith("drill:")) {
            List<String> next=new ArrayList<>(page.history);
            if(page.target!=null && !page.target.isEmpty() && (next.isEmpty() || !next.get(next.size()-1).equals(page.target)))next.add(page.target);
            item(p,page.skill,action.substring(6),next);
        }
        else if(action.equals("pop")) {
            if(!page.history.isEmpty()) {
                String prev=page.history.get(page.history.size()-1);
                item(p,page.skill,prev,page.history.subList(0,page.history.size()-1));
            } else open(p,page.skill);
        }
        else if(action.startsWith("materials:"))materials(p,page.skill,Integer.parseInt(action.substring(10)));
        else if(action.startsWith("select:")) { crafting.select(p,action.substring(7));p.closeInventory();p.sendMessage("已选择职业配方，请使用原版合成台制作。"); }
    }
    @EventHandler public void drag(InventoryDragEvent e) { if(e.getView().getTopInventory().getHolder() instanceof Page)e.setCancelled(true); }
}
