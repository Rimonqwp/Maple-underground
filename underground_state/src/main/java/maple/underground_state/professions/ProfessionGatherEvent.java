package maple.underground_state.professions;

import maple.underground_state.skills.SkillType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import java.util.List;

/** 验证并结算完成后仅发出一次，不允许监听者重新结算经验或普通产物。 */
public final class ProfessionGatherEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final SkillType skill;
    private final Material source;
    private final Location location;
    private final ItemStack tool;
    private final ProfessionBonuses bonuses;
    private final List<ItemStack> products;
    private final boolean stone;

    public ProfessionGatherEvent(Player player, SkillType skill, Material source, Location location,
                                 ItemStack tool, ProfessionBonuses bonuses, List<ItemStack> products,
                                 boolean stone) {
        this.player = player; this.skill = skill; this.source = source;
        this.location = location.clone(); this.tool = tool.clone(); this.bonuses = bonuses;
        this.products = products.stream().map(ItemStack::clone).toList(); this.stone = stone;
    }
    public Player player() { return player; }
    public SkillType skill() { return skill; }
    public Material source() { return source; }
    public Location location() { return location.clone(); }
    public ItemStack tool() { return tool.clone(); }
    public ProfessionBonuses bonuses() { return bonuses; }
    public List<ItemStack> products() { return products.stream().map(ItemStack::clone).toList(); }
    public boolean stone() { return stone; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
