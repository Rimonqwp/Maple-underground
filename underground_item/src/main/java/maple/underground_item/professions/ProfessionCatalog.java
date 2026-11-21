package maple.underground_item.professions;

import maple.underground_caves.gear.LegacyRgbParser;
import maple.underground_item.util.ItemFlagUtil;
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
        if (d.kind().equals("material")) {
            meta.displayName(Component.text(d.name(), NamedTextColor.GOLD));
        } else {
            meta.displayName(LegacyRgbParser.parse(gradientText(d.name(), rarityPalette(rarityOf(d)), true)));
            meta.setUnbreakable(true);
            ItemFlagUtil.applyStandardItemFlags(meta);
        }
        meta.getPersistentDataContainer().set(ID, PersistentDataType.STRING, id);
        if(d.kind().equals("weapon"))meta.getPersistentDataContainer().set(new NamespacedKey("underground_item","weapon_id"),PersistentDataType.STRING,id);
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
    private static final String LINE_UNBREAKABLE = "&#D6FFB4不&#A7FF95可&#79FF76破&#4AFF57坏";
    private static final String LINE_SET_BONUS_HEADER = "&#FFE066◆ &#FFCB49套&#FFC13A装&#FFB72C奖&#FFAD1D励 &#FF9800◆";
    private static final String LINE_PASSIVE_HEADER = "&#D946EF✦ &#E46178被&#E76B50动&#EB783F效&#EE852E果 &#F59E0B✦";
    private static final String[] PALETTE_COMMON = {"E9FFF2","DDFBEE","D1F7EA","C5F3E6","BAEFE3","AEEBDF","A2E7DB","96E3D7"};
    private static final String[] PALETTE_RARE = {"7FAFFF","8AB2FF","96B6FF","A1B9FF","ADBDFF","B8C0FF","C4C4FF","CFC7FF"};
    private static final String[] PALETTE_EPIC = {"C09BFF","C79EF8","CFA2F1","D6A5EA","DDA9E3","E4ACDC","ECB0D5","F3B3CE"};
    private static final String[] PALETTE_LEGENDARY = {"FFE4B8","FFDFC0","FFD9C8","FFD4CF","FFCED7","FFC9DF","FFC3E8","FFBEF0"};
    private static final String[] PALETTE_MYTHIC = {"FFB6F0","FFAFE8","FFA9E0","FFA2D8","FF9CD0","FF95C8","FF8FC0","FF88B8"};
    private static final String[] PALETTE_ETERNAL = {"FFEDEE","FFE7EA","FFE1E6","FFDBE2","FFD5DE","FFCFDA","FFC9D6","FFC3D2"};

    public String rarityOf(Definition d) {
        if (d.kind().equals("material")) return "普通";
        var s = set(d.set());
        if (s == null) return "普通";
        String branch = s.getString("branch", "main");
        int tier = s.getInt("tier", 1);
        if ("main".equals(branch)) {
            return switch (tier) {
                case 1 -> "普通";
                case 2 -> "稀有";
                case 3 -> "史诗";
                case 4 -> "传说";
                case 5 -> "神话";
                default -> "永恒";
            };
        }
        return tier >= 2 ? "传说" : "史诗";
    }

    private static String[] rarityPalette(String rarity) {
        return switch (rarity) {
            case "稀有" -> PALETTE_RARE;
            case "史诗" -> PALETTE_EPIC;
            case "传说" -> PALETTE_LEGENDARY;
            case "神话" -> PALETTE_MYTHIC;
            case "永恒" -> PALETTE_ETERNAL;
            default -> PALETTE_COMMON;
        };
    }

    private static String gradientText(String text, String[] palette, boolean bold) {
        if (text == null || text.isEmpty()) return "";
        int[] cps = text.codePoints().toArray();
        StringBuilder sb = new StringBuilder(bold ? "&l" : "");
        for (int i = 0; i < cps.length; i++) {
            String hex = palette[(i * palette.length) / Math.max(1, cps.length)];
            sb.append("&#").append(hex).appendCodePoint(cps[i]);
        }
        return sb.toString();
    }

    private static String formatStatValue(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static String romanNumeral(int n) {
        return switch (n) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V"; default -> String.valueOf(n); };
    }

    public List<Component> description(Definition d) {
        List<Component> lore = new ArrayList<>();
        if (d.kind().equals("material")) {
            if (d.number("raw_value") > 0) lore.add(line("相当于 " + (int) d.number("raw_value") + " 个原料"));
            return lore;
        }
        var s = set(d.set());
        String jobCn = d.skill() == SkillType.FARMING ? "农业" : "挖矿";
        lore.add(LegacyRgbParser.parse("&8可放在铁砧/附魔台"));
        lore.add(LegacyRgbParser.parse("&7" + jobCn + "等级要求: &e" + (int) d.number("level")));
        if (d.kind().equals("armor") && s != null) {
            lore.add(LegacyRgbParser.parse("&7生命: &a+" + formatStatValue(s.getDouble("health") / 4.0)));
            lore.add(LegacyRgbParser.parse("&7防御: &a+" + formatStatValue(s.getDouble("defense") / 4.0)));
            for (String field : List.of("fortune", "xp", "rare", "event")) {
                if (s.getDouble(field) > 0) {
                    String color = switch (field) { case "fortune" -> "&a"; case "xp" -> "&b"; case "rare" -> "&d"; default -> "&6"; };
                    lore.add(LegacyRgbParser.parse("&7" + label(field) + ": " + color + "+" + formatStatValue(s.getDouble(field) / 5.0) + (field.equals("fortune") ? "" : "%")));
                }
            }
            lore.add(Component.empty());
            lore.add(LegacyRgbParser.parse(LINE_SET_BONUS_HEADER));
            lore.add(LegacyRgbParser.parse("&7四件同套：加成补足至整套数值"));
            if (s.getDouble("reduction") > 0) {
                lore.add(LegacyRgbParser.parse("&7同套职业怪物减伤 &a" + formatStatValue(s.getDouble("reduction")) + "%"));
            }
        } else if (d.kind().equals("tool")) {
            lore.add(LegacyRgbParser.parse("&7职业幸运: &a+" + formatStatValue(d.number("fortune"))));
            if (d.number("xp") > 0) lore.add(LegacyRgbParser.parse("&7职业经验: &b+" + formatStatValue(d.number("xp")) + "%"));
            if (d.number("efficiency") > 0) lore.add(LegacyRgbParser.parse("&7效率: &b" + romanNumeral((int) d.number("efficiency"))));
            if (d.config.getBoolean("replant")) {
                lore.add(Component.empty());
                lore.add(LegacyRgbParser.parse(LINE_PASSIVE_HEADER));
                lore.add(LegacyRgbParser.parse("&6能力：&e自动复种"));
                lore.add(LegacyRgbParser.parse("&7成熟作物自动复种，消耗背包种植材料。"));
            }
        } else if (d.kind().equals("weapon")) {
            lore.add(LegacyRgbParser.parse("&7伤害: &c+" + formatStatValue(d.number("damage"))));
            lore.add(LegacyRgbParser.parse("&7力量: &c+" + formatStatValue(d.number("strength"))));
            lore.add(Component.empty());
            lore.add(LegacyRgbParser.parse(LINE_PASSIVE_HEADER));
            lore.add(LegacyRgbParser.parse("&6能力：&e职业猎手"));
            lore.add(LegacyRgbParser.parse("&7对对应职业怪物造成伤害 &c+" + formatStatValue(d.number("mob_bonus")) + "%&7。"));
        }
        lore.add(Component.empty());
        lore.add(LegacyRgbParser.parse(LINE_UNBREAKABLE));
        String rarity = rarityOf(d);
        String tag = "[" + rarity + jobCn + "世界装备]";
        lore.add(LegacyRgbParser.parse(gradientText(tag, rarityPalette(rarity), false)));
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
