package maple.underground_item.professions;

import maple.underground_state.UndergroundState;
import maple.underground_state.gui.CollectionInfoGui;
import maple.underground_state.skills.SkillType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** One catalog owns identity, crafting gates, attributes and book descriptions. */
public final class ProfessionCatalog {
    public static final NamespacedKey ID = new NamespacedKey("underground_item", "profession_id");
    private final Map<String, Definition> items = new LinkedHashMap<>();
    private final Map<String, ConfigurationSection> sets = new LinkedHashMap<>();
    public record Definition(String id, ConfigurationSection config) {
        public String kind() { return config.getString("kind", "material"); }
        public String set() { return config.getString("set", config.getString("gate", "")); }
        public SkillType skill() { return "farming".equals(config.getString("job")) ? SkillType.FARMING : SkillType.MINING; }
        public Material material() { return Material.valueOf(config.getString("material")); }
        public double number(String field) { return config.getDouble(field); }
        public String name() { return config.getString("name", id); }
    }
    public ProfessionCatalog(YamlConfiguration yaml) {
        var setSection = Objects.requireNonNull(yaml.getConfigurationSection("sets"));
        for (String id : setSection.getKeys(false)) sets.put(id, setSection.getConfigurationSection(id));
        var itemSection = Objects.requireNonNull(yaml.getConfigurationSection("items"));
        for (String id : itemSection.getKeys(false)) items.put(id, new Definition(id, itemSection.getConfigurationSection(id)));
        for (Definition d : items.values()) {
            d.material();
            if (!d.set().isEmpty() && !sets.containsKey(d.set())) throw new IllegalArgumentException("Unknown set: " + d.set());
            var recipe = d.config.getConfigurationSection("recipe");
            if (recipe != null) for (String key : List.of("a", "b", "n", "p")) materialOf(recipe.getString(key));
            if (d.config.contains("compression")) materialOf(d.config.getString("compression"));
        }
    }
    public Collection<Definition> items() { return Collections.unmodifiableCollection(items.values()); }
    public Map<String, ConfigurationSection> sets() { return Collections.unmodifiableMap(sets); }
    public Definition get(String id) { return items.get(id); }
    public ConfigurationSection set(String id) { return sets.get(id); }
    public Definition identify(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return null;
        Definition d = items.get(stack.getItemMeta().getPersistentDataContainer().get(ID, PersistentDataType.STRING));
        return d != null && d.material() == stack.getType() ? d : null;
    }
    public Material materialOf(String token) {
        if (token == null) throw new IllegalArgumentException("Missing recipe ingredient");
        return token.startsWith("vanilla:") ? Material.valueOf(token.substring(8)) : Objects.requireNonNull(items.get(token), token).material();
    }
    public boolean matches(ItemStack stack, String token) {
        if (stack == null || stack.getAmount() < 1 || stack.getType() != materialOf(token)) return false;
        if (!token.startsWith("vanilla:")) return identify(stack) != null && identify(stack).id.equals(token);
        if (!stack.hasItemMeta()) return true;
        // Vanilla lore's state-owned tags are allowed; plugin-owned equipment cannot masquerade as ingredients.
        return stack.getItemMeta().getPersistentDataContainer().getKeys().stream()
                .noneMatch(key -> key.getNamespace().equals("underground_item") || key.getNamespace().equals("underground_caves"));
    }
    public ItemStack create(String id) {
        Definition d = Objects.requireNonNull(items.get(id), id);
        ItemStack result = new ItemStack(d.material());
        var meta = result.getItemMeta();
        meta.displayName(Component.text(d.name(), NamedTextColor.GOLD));
        meta.getPersistentDataContainer().set(ID, PersistentDataType.STRING, id);
        if(d.kind().equals("weapon"))meta.getPersistentDataContainer().set(new NamespacedKey("underground_item","weapon_id"),PersistentDataType.STRING,id);
        if (!d.kind().equals("material")) meta.setUnbreakable(true);
        if (meta instanceof LeatherArmorMeta leather) leather.setColor(Color.fromRGB(d.config.getInt("color")));
        meta.lore(description(d));
        result.setItemMeta(meta);
        if (d.number("efficiency") > 0) result.addEnchantment(Objects.requireNonNull(Registry.ENCHANTMENT.get(NamespacedKey.minecraft("efficiency"))), (int)d.number("efficiency"));
        return result;
    }
    public ItemStack upgrade(Definition d, ItemStack previous) {
        ItemStack result = create(d.id);
        if (previous == null) return result;
        var old = previous.getItemMeta(); var meta = result.getItemMeta();
        Definition oldDefinition = identify(previous);
        if (old.hasDisplayName() && (oldDefinition == null || !Objects.equals(old.displayName(), create(oldDefinition.id).getItemMeta().displayName()))) meta.displayName(old.displayName());
        result.setItemMeta(meta);
        previous.getEnchantments().forEach((enchantment, level) -> {
            if (enchantment.canEnchantItem(result) && level <= enchantment.getMaxLevel() &&
                    result.getEnchantments().keySet().stream().noneMatch(other -> !other.equals(enchantment) && other.conflictsWith(enchantment))) {
                result.addEnchantment(enchantment, Math.max(level, result.getEnchantmentLevel(enchantment)));
            }
        });
        return result;
    }
    public List<Component> description(Definition d) {
        List<Component> lore = new ArrayList<>();
        var s = set(d.set());
        if (!d.kind().equals("material")) lore.add(line((d.skill()==SkillType.FARMING ? "农业" : "挖矿") + "等级要求：" + (int)d.number("level")));
        if (d.kind().equals("armor")) {
            lore.add(line("生命 +" + s.getDouble("health")/4 + "  防御 +" + s.getDouble("defense")/4));
            for (String field : List.of("fortune","xp","rare","event")) if (s.getDouble(field)>0)
                lore.add(line(label(field) + " +" + s.getDouble(field)/5 + (field.equals("fortune") ? "" : "%")));
            lore.add(line("四件同套：加成补足至整套数值"));
            if (s.getDouble("reduction")>0) lore.add(line("同套职业怪物减伤 " + s.getDouble("reduction") + "%"));
        } else if (d.kind().equals("tool")) {
            lore.add(line("职业幸运 +" + d.number("fortune") + "  职业经验 +" + d.number("xp") + "%"));
            if (d.config.getBoolean("replant")) lore.add(line("成熟作物自动复种，消耗背包种植材料"));
        } else if (d.kind().equals("weapon")) {
            lore.add(line("伤害 " + d.number("damage") + "  力量 +" + d.number("strength")));
            lore.add(line("对应职业怪物伤害 +" + d.number("mob_bonus") + "%"));
        } else if (d.number("raw_value")>0) lore.add(line("相当于 " + (int)d.number("raw_value") + " 个原料"));
        return lore;
    }
    public static String label(String field) {
        return switch(field) { case "fortune" -> "职业幸运"; case "xp" -> "职业经验"; case "rare" -> "稀有概率相对提升"; default -> "事件概率相对提升"; };
    }
    public List<String> requirements(Definition d) {
        List<String> result = new ArrayList<>();
        if (d.kind().equals("material")) return result;
        result.add("技能等级 " + (int)d.number("level"));
        var r = set(d.set()).getConfigurationSection("requirements");
        var collections = r.getConfigurationSection("collections");
        if (collections != null) for (String m : collections.getKeys(false)) result.add(m + " 收藏 " + collections.getLong(m));
        if (r.getInt("diverse_count")>0) result.add("指定作物任意 " + r.getInt("diverse_count") + " 种各 " + r.getLong("diverse_amount"));
        if (r.getInt("normal")>0) result.add("普通事件击败 " + r.getInt("normal") + " 次");
        if (r.getInt("elite")>0) result.add("精英事件击败 " + r.getInt("elite") + " 次");
        return result;
    }
    public boolean canCraft(Player player, Definition d, UndergroundState core) {
        if (d.kind().equals("material")) return true;
        var service=core.getProfessionService(); var uuid=player.getUniqueId();
        if (!service.store().ready(uuid) || service.level(player,d.skill()) < d.number("level")) return false;
        var category=d.skill()==SkillType.FARMING ? CollectionInfoGui.Category.FARMING : CollectionInfoGui.Category.MINING;
        String prefix=d.skill().name()+":";
        return canCraft(d,service.level(player,d.skill()),m -> core.getCollectionProgressManager().getAmount(uuid,CollectionInfoGui.collectionKey(category,m)),
                service.store().get(uuid,prefix+"normal_kills"),service.store().get(uuid,prefix+"elite_kills"));
    }
    boolean canCraft(Definition d,int level,java.util.function.ToLongFunction<Material> amounts,long normal,long elite) {
        if(d.kind().equals("material"))return true;
        if(level<d.number("level"))return false;
        var r=set(d.set()).getConfigurationSection("requirements");
        var collections=r.getConfigurationSection("collections");
        if(collections!=null)for(String m:collections.getKeys(false))if(amounts.applyAsLong(Material.valueOf(m))<collections.getLong(m))return false;
        long eligible=List.of(Material.WHEAT,Material.CARROT,Material.POTATO,Material.BEETROOT,Material.PUMPKIN,Material.SUGAR_CANE,Material.COCOA_BEANS).stream()
                .filter(m -> amounts.applyAsLong(m)>=r.getLong("diverse_amount")).count();
        return eligible>=r.getInt("diverse_count") && normal>=r.getLong("normal") && elite>=r.getLong("elite");
    }
    public static Component line(String text) { return Component.text(text,NamedTextColor.GRAY); }
}
