package maple.underground_item.professions;

import maple.underground_state.UndergroundState;
import maple.underground_state.professions.*;
import maple.underground_state.skills.SkillType;
import maple.underground_state.stats.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

public final class ProfessionEquipment implements PlayerStatsRuntimeExtension {
    private final ProfessionCatalog catalog;
    private final java.util.function.Predicate<Player> worldEnabled;
    private final java.util.function.ToIntBiFunction<Player,SkillType> level;
    public ProfessionEquipment(ProfessionCatalog catalog, UndergroundState core) {
        this(catalog,p -> core.getProfessionService().enabled(p.getWorld()),(p,s) -> core.getProfessionService().level(p,s));
    }
    ProfessionEquipment(ProfessionCatalog catalog, java.util.function.Predicate<Player> worldEnabled,
                        java.util.function.ToIntBiFunction<Player,SkillType> level) {
        this.catalog=catalog;this.worldEnabled=worldEnabled;this.level=level;
    }
    public boolean usable(Player p, ProfessionCatalog.Definition d) {
        return d != null && worldEnabled.test(p) && level.applyAsInt(p,d.skill())>=d.number("level");
    }
    @Override public boolean canUseProfessionItem(Player p, ItemStack item) {
        var d=catalog.identify(item);
        return d==null || d.kind().equals("material") || d.kind().equals("armor") || level.applyAsInt(p,d.skill())>=d.number("level");
    }
    private List<ProfessionCatalog.Definition> armor(Player p) {
        return Arrays.stream(p.getInventory().getArmorContents()).map(catalog::identify).filter(d -> usable(p,d) && d.kind().equals("armor")).toList();
    }
    private boolean full(List<ProfessionCatalog.Definition> armor) { return armor.size()==4 && armor.stream().map(ProfessionCatalog.Definition::set).distinct().count()==1 && armor.stream().map(d -> d.config().getString("part")).distinct().count()==4; }
    @Override public ProfessionBonuses modifyProfessionBonuses(Player p, SkillType skill, ProfessionBonuses base) {
        var armor=armor(p); var matching=armor.stream().filter(d -> d.skill()==skill).toList();
        double fortune=0,xp=0,rare=0,event=0;
        int eligibility=matching.size()==4 ? 2 : 0;
        for (var d:matching) {
            var s=catalog.set(d.set());
            fortune+=s.getDouble("fortune")/5; xp+=s.getDouble("xp")/5; rare+=s.getDouble("rare")/5; event+=s.getDouble("event")/5;
            eligibility=Math.min(eligibility,s.getInt("event_tier"));
        }
        if (full(matching)) { fortune*=1.25;xp*=1.25;rare*=1.25;event*=1.25; }
        var tool=catalog.identify(p.getInventory().getItemInMainHand());
        if (usable(p,tool) && tool.skill()==skill && tool.kind().equals("tool")) { fortune+=tool.number("fortune");xp+=tool.number("xp"); }
        return base.add(new ProfessionBonuses(fortune,xp,rare,event,eligibility));
    }
    @Override public double modifyMaxHealth(Player p, PlayerStatsData data, double base) { return base+armor(p).stream().mapToDouble(d -> catalog.set(d.set()).getDouble("health")/4).sum(); }
    @Override public double modifyDefense(Player p, PlayerStatsData data, double base) { return base+armor(p).stream().mapToDouble(d -> catalog.set(d.set()).getDouble("defense")/4).sum(); }
    @Override public double modifyStrength(Player p, PlayerStatsData data, double base) {
        var d=catalog.identify(p.getInventory().getItemInMainHand());
        return base+(usable(p,d) && d.kind().equals("weapon") ? d.number("strength") : 0);
    }
    @Override public double modifyOutgoingDamage(Player p, PlayerStatsData data, double base, EntityDamageByEntityEvent e, PlayerStatsRuntime runtime) {
        var d=catalog.identify(p.getInventory().getItemInMainHand());
        String job=e.getEntity().getPersistentDataContainer().get(ProfessionService.MOB_KEY,PersistentDataType.STRING);
        return usable(p,d) && d.kind().equals("weapon") && d.skill().name().equals(job) ? base*(1+d.number("mob_bonus")/100) : base;
    }
    public double reduction(Player p, SkillType skill) {
        var armor=armor(p);
        return full(armor) && armor.getFirst().skill()==skill ? catalog.set(armor.getFirst().set()).getDouble("reduction")/100 : 0;
    }
}
