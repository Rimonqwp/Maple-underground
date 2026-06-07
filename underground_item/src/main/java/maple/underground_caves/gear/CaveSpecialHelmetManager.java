package maple.underground_caves.gear;

import maple.underground_item.util.SkullTextureUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 洞窟特殊頭盔：癒合之冠／守護療癒／堅韌療癒（地牢內治療+活力）、法術增幅／精通／宗師（能力傷害+智力）。
 */
public final class CaveSpecialHelmetManager {

    private static final String PDC_CAVE_SPECIAL_HELMET_ID = "cave_special_helmet_id";

    public static final String[] HELMET_IDS = {
            "mending_helmet_1",
            "mending_helmet_2",
            "mending_helmet_3",
            "ability_damage_helmet_1",
            "ability_damage_helmet_2",
            "ability_damage_helmet_3",
            "cave_echo_motherrock",
            "cave_echo_stalactite",
            "cave_echo_firefly",
            "cave_echo_leyline",
            "cave_echo_obsidian"
    };

    private final JavaPlugin plugin;
    private final NamespacedKey keyHelmetId;
    private final Map<String, CaveSpecialHelmetConfig> configs = new HashMap<>();

    public CaveSpecialHelmetManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.keyHelmetId = new NamespacedKey(plugin, PDC_CAVE_SPECIAL_HELMET_ID);
        reload();
    }

    public void reload() {
        configs.clear();
        for (String id : HELMET_IDS) {
            String path = "gear/" + id + ".yml";
            File f = new File(plugin.getDataFolder(), path);
            if (!f.exists()) {
                plugin.saveResource(path, false);
                f = new File(plugin.getDataFolder(), path);
            }
            configs.put(id, new CaveSpecialHelmetConfig(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f)));
        }
    }

    public NamespacedKey getKeyHelmetId() {
        return keyHelmetId;
    }

    private static boolean isInDungeon(Player player) {
        return player != null && player.getWorld() != null
                && player.getWorld().getName() != null
                && player.getWorld().getName().startsWith("dungeon_");
    }

    public boolean isCaveSpecialHelmet(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD || !item.hasItemMeta()) return false;
        String id = item.getItemMeta().getPersistentDataContainer().get(keyHelmetId, PersistentDataType.STRING);
        return id != null && configs.containsKey(id);
    }

    public String getHelmetId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(keyHelmetId, PersistentDataType.STRING);
    }

    /** 裝備時額外生命（僅 mending 型頭盔有 stats.health）。 */
    public int getBonusHealth(Player player) {
        if (player == null) return 0;
        String id = getHelmetId(player.getInventory().getHelmet());
        if (id == null || !configs.containsKey(id)) return 0;
        return configs.get(id).getStatHealth();
    }

    /** 地牢內裝備時，治療公式有效活力加算（治療量 = HealthGained * (Vitality + 此值)/100）。 */
    public int getMendingInDungeon(Player player) {
        if (player == null || !isInDungeon(player)) return 0;
        String id = getHelmetId(player.getInventory().getHelmet());
        if (id == null || !configs.containsKey(id)) return 0;
        return configs.get(id).getMendingInDungeon();
    }

    /** 地牢內裝備時，活力加算。 */
    public int getVitalityInDungeon(Player player) {
        if (player == null || !isInDungeon(player)) return 0;
        String id = getHelmetId(player.getInventory().getHelmet());
        if (id == null || !configs.containsKey(id)) return 0;
        return configs.get(id).getVitalityInDungeon();
    }

    /** 裝備時能力傷害百分比加算（25/35/45）。 */
    public int getBonusAbilityDamagePercent(Player player) {
        if (player == null) return 0;
        String id = getHelmetId(player.getInventory().getHelmet());
        if (id == null || !configs.containsKey(id)) return 0;
        return configs.get(id).getAbilityDamagePercent();
    }

    /** 裝備時智力加算。 */
    public int getBonusIntelligence(Player player) {
        if (player == null) return 0;
        String id = getHelmetId(player.getInventory().getHelmet());
        if (id == null || !configs.containsKey(id)) return 0;
        return configs.get(id).getIntelligence();
    }

    /** 治療用：有效活力 = 基礎活力 + 地牢內頭盔的 mending + vitality 加算。 */
    public double getEffectiveVitalityBonusForHealing(Player player) {
        return getMendingInDungeon(player) + getVitalityInDungeon(player);
    }

    public List<String> getHelmetIds() {
        return List.of(HELMET_IDS);
    }

    public ItemStack createHelmet(String id) {
        if (id == null || !configs.containsKey(id)) return null;
        CaveSpecialHelmetConfig cfg = configs.get(id);
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.getPersistentDataContainer().set(keyHelmetId, PersistentDataType.STRING, id);
        meta.displayName(LegacyRgbParser.parse(cfg.getDisplayName()));
        int health = cfg.getStatHealth();
        int mending = cfg.getMendingInDungeon();
        int vitality = cfg.getVitalityInDungeon();
        int abilityDmg = cfg.getAbilityDamagePercent();
        int intelligence = cfg.getIntelligence();
        List<Component> lore = new ArrayList<>();
        for (String line : cfg.getLore()) {
            String replaced = line
                    .replace("{health}", String.valueOf(health))
                    .replace("{ability_damage_percent}", String.valueOf(abilityDmg))
                    .replace("{intelligence}", String.valueOf(intelligence));
            lore.add(LegacyRgbParser.parse(replaced));
        }
        meta.lore(lore);
        maple.underground_item.util.ItemFlagUtil.applyStandardItemFlags(meta);
        if (meta instanceof SkullMeta skullMeta) {
            String textureId = cfg.getTextureId();
            if (textureId != null && !textureId.isBlank()) {
                SkullTextureUtil.applySkullTextureToMeta(skullMeta, textureId.trim(), "CaveSpecialHelmetManager." + id);
            }
        }
        item.setItemMeta(meta);
        return item;
    }
}
