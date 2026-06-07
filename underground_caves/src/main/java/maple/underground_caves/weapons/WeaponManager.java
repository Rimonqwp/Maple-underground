package maple.underground_caves.weapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 管理所有武器配置（每武器一 yml），建立/更新插件武器物品。
 */
public final class WeaponManager {

    public static final String WEAPONS_FOLDER = "weapons";
    public static final String WEAPON_ID_PDC = "weapon_id";

    private final JavaPlugin plugin;
    private final Logger log;
    private final NamespacedKey weaponIdKey;
    private final Map<String, WeaponConfig> configs = new HashMap<>();

    public WeaponManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.weaponIdKey = new NamespacedKey(plugin, WEAPON_ID_PDC);
    }

    /** 從 dataFolder/weapons/*.yml 載入所有武器配置 */
    public void reload() {
        configs.clear();
        File dir = new File(plugin.getDataFolder(), WEAPONS_FOLDER);
        if (!dir.exists()) {
            dir.mkdirs();
            return;
        }
        File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
        if (files == null) return;
        for (File f : files) {
            String baseName = f.getName();
            String id = baseName.substring(0, baseName.length() - 4);
            try {
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
                WeaponConfig cfg = loadWeaponConfig(id, yml);
                if (cfg != null) configs.put(id, cfg);
            } catch (Exception e) {
                log.warning("Failed to load weapon config " + f.getName() + ": " + e.getMessage());
            }
        }
    }

    private WeaponConfig loadWeaponConfig(String id, YamlConfiguration yml) {
        String displayName = yml.getString("display_name", id);
        String matName = yml.getString("material", "BLAZE_ROD");
        Material material;
        try {
            material = Material.valueOf(matName);
        } catch (IllegalArgumentException e) {
            material = Material.BLAZE_ROD;
        }
        List<String> lore = yml.getStringList("lore");
        WeaponConfig.SkillConfig skill = WeaponConfig.loadSkill(yml.getConfigurationSection("skill"));
        Map<String, String> stats = loadStats(yml.getConfigurationSection("stats"));
        loadAbilityIntoStats(yml.getConfigurationSection("ability"), stats);
        loadClassBonusIntoStats(yml.getConfigurationSection("class_bonus"), stats);
        return new WeaponConfig(id, displayName, material, lore, skill, stats);
    }

    /** 讀取 class_bonus 區塊併入 stats，供 lore 佔位符 {berserk_damage}、{healer_mending} 等替換 */
    private void loadClassBonusIntoStats(ConfigurationSection section, Map<String, String> stats) {
        if (section == null) return;
        for (String classKey : section.getKeys(false)) {
            ConfigurationSection classSection = section.getConfigurationSection(classKey);
            if (classSection == null) continue;
            for (String key : classSection.getKeys(false)) {
                String placeholderKey = classKey + "_" + key;
                Object val = classSection.get(key);
                if (val != null) stats.put(placeholderKey, String.valueOf(val));
            }
        }
    }

    /** 讀取 ability 區塊併入 stats，供 lore 佔位符 {skill_name}、{mana_cost}、{base_ability_damage}、{cooldown_seconds} 等替換 */
    private void loadAbilityIntoStats(ConfigurationSection section, Map<String, String> stats) {
        if (section == null) return;
        if (section.contains("name")) stats.put("skill_name", section.getString("name", ""));
        if (section.contains("mana_cost")) stats.put("mana_cost", String.valueOf(section.getInt("mana_cost", 0)));
        if (section.contains("base_ability_damage")) stats.put("base_ability_damage", String.valueOf((int) section.getDouble("base_ability_damage", 0)));
        if (section.contains("ability_scaling")) stats.put("ability_scaling", String.valueOf(section.getDouble("ability_scaling", 0)));
        if (section.contains("cooldown_seconds")) stats.put("cooldown_seconds", String.valueOf(section.getInt("cooldown_seconds", (int) section.getDouble("cooldown_seconds", 0))));
        if (section.contains("teleport_blocks")) stats.put("teleport_blocks", String.valueOf(section.getInt("teleport_blocks", 8)));
        if (section.contains("speed_bonus")) stats.put("speed_bonus", String.valueOf(section.getInt("speed_bonus", 50)));
        if (section.contains("speed_duration_seconds")) stats.put("speed_duration_seconds", String.valueOf(section.getDouble("speed_duration_seconds", 3)));
    }

    /** 讀取 stats 區塊，供 lore 佔位符 {damage}、{intelligence} 等替換 */
    private Map<String, String> loadStats(ConfigurationSection section) {
        Map<String, String> out = new HashMap<>();
        if (section == null) return out;
        for (String key : section.getKeys(false)) {
            Object val = section.get(key);
            if (val != null) out.put(key, String.valueOf(val));
        }
        return out;
    }

    public WeaponConfig getConfig(String weaponId) {
        return configs.get(weaponId);
    }

    public Set<String> getWeaponIds() {
        return new HashSet<>(configs.keySet());
    }

    public NamespacedKey getWeaponIdKey() {
        return weaponIdKey;
    }

    /** 取得物品上的武器 ID（若為本插件武器） */
    public String getWeaponId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(weaponIdKey, PersistentDataType.STRING);
    }

    /** 判斷是否為本插件的武器 */
    public boolean isPluginWeapon(ItemStack item) {
        return getWeaponId(item) != null;
    }

    /** 用當前配置建立一把武器物品 */
    public ItemStack createWeaponItem(String weaponId) {
        WeaponConfig cfg = configs.get(weaponId);
        if (cfg == null) return null;
        ItemStack item = new ItemStack(cfg.getMaterial(), 1);
        applyConfigToItem(item, cfg);
        return item;
    }

    /** 依當前配置更新物品的顯示名稱與 lore（reload 後更新背包內武器用） */
    public boolean updateItemFromConfig(ItemStack item) {
        String wid = getWeaponId(item);
        if (wid == null) return false;
        WeaponConfig cfg = configs.get(wid);
        if (cfg == null) return false;
        applyConfigToItem(item, cfg);
        return true;
    }

    private void applyConfigToItem(ItemStack item, WeaponConfig cfg) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(weaponIdKey, PersistentDataType.STRING, cfg.getId());
        String display = cfg.getDisplayName().replace('&', '§');
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize(display).colorIfAbsent(net.kyori.adventure.text.format.NamedTextColor.GOLD));
        List<Component> loreComponents = buildLore(cfg);
        meta.lore(loreComponents);
        item.setItemMeta(meta);
    }

    private List<Component> buildLore(WeaponConfig cfg) {
        List<String> raw = cfg.getLore();
        if (raw == null || raw.isEmpty()) return Collections.emptyList();
        Map<String, String> placeholders = buildPlaceholders(cfg);
        List<Component> out = new ArrayList<>();
        LegacyComponentSerializer ser = LegacyComponentSerializer.legacySection();
        for (String line : raw) {
            String replaced = line.replace('&', '§');
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                replaced = replaced.replace(e.getKey(), e.getValue());
            }
            out.add(ser.deserialize(replaced).colorIfAbsent(net.kyori.adventure.text.format.NamedTextColor.GRAY));
        }
        return out;
    }

    private Map<String, String> buildPlaceholders(WeaponConfig cfg) {
        Map<String, String> m = new HashMap<>();
        for (Map.Entry<String, String> e : cfg.getStats().entrySet()) {
            m.put("{" + e.getKey() + "}", e.getValue());
        }
        m.put("{skill_name}", "");
        m.put("{mana_cost}", "0");
        m.put("{base_ability_damage}", "0");
        m.put("{ability_scaling}", "0");
        m.put("{skill_damage}", "0");
        if (cfg.getSkill() instanceof WeaponConfig.FreezeSkillConfig freeze) {
            m.put("{skill_name}", freeze.getName());
            m.put("{mana_cost}", String.valueOf(freeze.getManaCost()));
            m.put("{base_ability_damage}", String.format("%.0f", freeze.getBaseAbilityDamage()));
            m.put("{ability_scaling}", String.valueOf(freeze.getAbilityScaling()));
            double exampleDmg = maple.underground_state.stats.PlayerStatsFormulas.abilityDamage(
                    freeze.getBaseAbilityDamage(), 200, freeze.getAbilityScaling(), 0, 1.0, 1.0);
            m.put("{skill_damage}", String.format("%.0f", exampleDmg));
        }
        if (cfg.getSkill() instanceof WeaponConfig.ShowtimeSkillConfig show) {
            m.put("{skill_name}", show.getName());
            m.put("{mana_cost}", String.valueOf(show.getManaCost()));
            m.put("{base_ability_damage}", String.format("%.0f", show.getBaseAbilityDamage()));
        }
        return m;
    }
}
