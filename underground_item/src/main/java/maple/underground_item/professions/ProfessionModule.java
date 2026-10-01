package maple.underground_item.professions;

import maple.underground_state.UndergroundState;
import maple.underground_state.skills.SkillType;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.*;

public final class ProfessionModule {
    private final UndergroundState core;
    private final JavaPlugin plugin;
    private final ProfessionCrafting crafting;
    private final ProfessionEvents events;
    public ProfessionModule(JavaPlugin plugin,UndergroundState core) {
        this.plugin=plugin;this.core=core;
        File file=new File(plugin.getDataFolder(),"professions.yml");if(!file.exists())plugin.saveResource("professions.yml",false);
        ProfessionCatalog catalog=new ProfessionCatalog(YamlConfiguration.loadConfiguration(file));
        ProfessionEquipment equipment=new ProfessionEquipment(catalog,core);
        crafting=new ProfessionCrafting(plugin,catalog,core);events=new ProfessionEvents(plugin,core,catalog,equipment);
        ProfessionBook book=new ProfessionBook(core,catalog,crafting);
        core.getPlayerStatsRuntime().registerExtension(plugin,"profession-equipment",equipment);
        Bukkit.getPluginManager().registerEvents(crafting,plugin);Bukkit.getPluginManager().registerEvents(events,plugin);Bukkit.getPluginManager().registerEvents(book,plugin);
        core.getProfessionService().setMenu(book::open);
        var command=Objects.requireNonNull(plugin.getCommand("profession"));
        command.setExecutor((sender,cmd,label,args) -> {
            if(args.length>=2 && args[0].equalsIgnoreCase("give")) {
                if(!sender.hasPermission("underground_item.profession.admin")) { sender.sendMessage("没有管理权限。");return true; }
                Player target=args.length>=3 ? Bukkit.getPlayerExact(args[2]) : sender instanceof Player p ? p : null;
                if(target==null || catalog.get(args[1])==null) { sender.sendMessage("用法：/profession give <物品 ID> [玩家]");return true; }
                var leftovers=target.getInventory().addItem(catalog.create(args[1]));leftovers.values().forEach(i -> target.getWorld().dropItemNaturally(target.getLocation(),i));return true;
            }
            if(sender instanceof Player p) {
                if(args.length>0 && args[0].equalsIgnoreCase("register"))book.register(p);
                else book.open(p,args.length>0 && args[0].equalsIgnoreCase("mining") ? SkillType.MINING : SkillType.FARMING);
            } else sender.sendMessage("请在游戏内打开图鉴。");return true;
        });
        command.setTabCompleter((sender,cmd,label,args) -> {
            if(args.length==1)return List.of("farming","mining","register");
            if(args.length==2 && args[0].equalsIgnoreCase("give") && sender.hasPermission("underground_item.profession.admin"))return catalog.items().stream().map(ProfessionCatalog.Definition::id).filter(id -> id.startsWith(args[1])).toList();
            return List.of();
        });
        plugin.getLogger().info("职业系统已加载："+catalog.sets().size()+" 套装备，"+catalog.items().size()+" 个物品定义。");
    }
    public void close() { events.close();crafting.close();core.getProfessionService().setMenu(null);core.getPlayerStatsRuntime().unregisterExtension(plugin,"profession-equipment"); }
}
