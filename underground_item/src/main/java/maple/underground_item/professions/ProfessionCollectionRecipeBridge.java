package maple.underground_item.professions;

import maple.underground_state.gui.CollectionInfoGui;
import maple.underground_state.professions.ProfessionMath;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bridges profession/compression 3x3 recipes into the collection center GUI. */
final class ProfessionCollectionRecipeBridge implements CollectionInfoGui.CollectionRecipeBridge {

    private static final Pattern COMPRESSED_ID_PATTERN = Pattern.compile("^([a-z0-9_]+)_compressed_([1-4])$");
    private static final String[] SHAPED_LAYOUT = {"a", "n", "b", "a", "p", "b", "a", "n", "b"};

    private final ProfessionCatalog catalog;
    private final ProfessionCrafting crafting;
    private final Map<Material, Map<Integer, List<CollectionInfoGui.RecipeEntry>>> recipesByMaterialTier = new EnumMap<>(Material.class);

    ProfessionCollectionRecipeBridge(@NotNull ProfessionCatalog catalog, @NotNull ProfessionCrafting crafting) {
        this.catalog = catalog;
        this.crafting = crafting;
        buildIndex();
    }

    private void buildIndex() {
        for (ProfessionCatalog.Definition def : catalog.items()) {
            Matcher matcher = COMPRESSED_ID_PATTERN.matcher(def.id());
            if (matcher.matches()) {
                Material baseMaterial = parseMaterial(matcher.group(1).toUpperCase(Locale.ROOT));
                int level = Integer.parseInt(matcher.group(2));
                int tier = switch (level) {
                    case 1 -> 2;
                    case 2 -> 5;
                    case 3 -> 8;
                    case 4 -> 10;
                    default -> 0;
                };
                String reason = switch (level) {
                    case 1 -> "一级压缩（9 合 1）";
                    case 2 -> "二级压缩（81 合 1）";
                    case 3 -> "三级压缩（729 合 1）";
                    case 4 -> "四级压缩（6,561 合 1）";
                    default -> "压缩合成";
                };
                if (baseMaterial != null && tier > 0) {
                    addEntry(baseMaterial, tier, def.id(), def.name(), reason);
                }
                continue;
            }

            ConfigurationSection recipeSection = def.config().getConfigurationSection("recipe");
            if (recipeSection == null) {
                continue;
            }

            ConfigurationSection setSection = catalog.set(def.set());
            ConfigurationSection reqSection = setSection != null ? setSection.getConfigurationSection("requirements") : null;
            ConfigurationSection collectionsSection = reqSection != null ? reqSection.getConfigurationSection("collections") : null;

            if (collectionsSection != null && !collectionsSection.getKeys(false).isEmpty()) {
                for (String matName : collectionsSection.getKeys(false)) {
                    Material mat = parseMaterial(matName);
                    long amount = collectionsSection.getLong(matName);
                    if (mat != null && amount > 0L) {
                        int tier = extendedTierFor(amount);
                        if (tier > 0) {
                            addEntry(mat, tier, def.id(), def.name(), "需要该收藏达到 " + formatComma(amount));
                        }
                    }
                }
            } else if (reqSection != null && reqSection.getInt("diverse_count") > 0 && reqSection.getLong("diverse_amount") > 0L) {
                int diverseCount = reqSection.getInt("diverse_count");
                long diverseAmount = reqSection.getLong("diverse_amount");
                int tier = extendedTierFor(diverseAmount);
                if (tier > 0) {
                    String reason = "套装要求任意 " + diverseCount + " 种作物各 " + formatComma(diverseAmount);
                    for (Material mat : recipeBaseMaterials(recipeSection)) {
                        addEntry(mat, tier, def.id(), def.name(), reason);
                    }
                }
            } else {
                for (Material mat : recipeBaseMaterials(recipeSection)) {
                    addEntry(mat, 1, def.id(), def.name(), "使用该材料合成");
                }
            }
        }
    }

    private void addEntry(@NotNull Material material,
                          int tier,
                          @NotNull String itemId,
                          @NotNull String name,
                          @NotNull String reason) {
        Map<Integer, List<CollectionInfoGui.RecipeEntry>> tierMap =
                recipesByMaterialTier.computeIfAbsent(material, ignored -> new TreeMap<>());
        List<CollectionInfoGui.RecipeEntry> list =
                tierMap.computeIfAbsent(tier, ignored -> new ArrayList<>());
        for (CollectionInfoGui.RecipeEntry existing : list) {
            if (existing.itemId().equals(itemId)) {
                return;
            }
        }
        list.add(new CollectionInfoGui.RecipeEntry(itemId, name, reason));
    }

    private @NotNull Set<Material> recipeBaseMaterials(@NotNull ConfigurationSection recipeSection) {
        Set<Material> result = new LinkedHashSet<>();
        Material a = resolveBaseVanillaMaterial(recipeSection.getString("a"));
        Material b = resolveBaseVanillaMaterial(recipeSection.getString("b"));
        if (a != null) {
            result.add(a);
        }
        if (b != null) {
            result.add(b);
        }
        return result;
    }

    @Override
    public @NotNull List<CollectionInfoGui.RecipeEntry> recipesForTier(@NotNull Material material, int tier) {
        Map<Integer, List<CollectionInfoGui.RecipeEntry>> tierMap = recipesByMaterialTier.get(material);
        if (tierMap == null) {
            return List.of();
        }
        List<CollectionInfoGui.RecipeEntry> list = tierMap.get(tier);
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }

    @Override
    public @NotNull List<Integer> recipeTiersForMaterial(@NotNull Material material) {
        Map<Integer, List<CollectionInfoGui.RecipeEntry>> tierMap = recipesByMaterialTier.get(material);
        if (tierMap == null || tierMap.isEmpty()) {
            return List.of();
        }
        return List.copyOf(tierMap.keySet());
    }

    @Override
    public @Nullable CollectionInfoGui.RecipeView describeRecipe(@NotNull String itemId) {
        ProfessionCatalog.Definition def = catalog.get(itemId);
        if (def == null) {
            return null;
        }
        boolean isCompression = def.config().contains("compression");
        String compressionToken = isCompression ? def.config().getString("compression") : null;
        ConfigurationSection recipeSection = def.config().getConfigurationSection("recipe");
        if (!isCompression && recipeSection == null) {
            return null;
        }

        List<String> matrixTokens = new ArrayList<>(9);
        if (isCompression && compressionToken != null) {
            for (int i = 0; i < 9; i++) {
                matrixTokens.add(compressionToken);
            }
        } else if (recipeSection != null) {
            for (String key : SHAPED_LAYOUT) {
                String tok = recipeSection.getString(key);
                matrixTokens.add(tok != null ? tok : "");
            }
        }

        ConfigurationSection setSection = catalog.set(def.set());
        String setName = setSection != null ? setSection.getString("name") : null;
        Material displayMaterial = resolveDisplayMaterial(def);
        boolean glow = true;
        Color leatherColor = resolveLeatherColor(def);
        List<Component> lore = buildItemLore(def);

        return new CollectionInfoGui.RecipeView(
                def.id(),
                def.name(),
                def.kind(),
                setName,
                isCompression,
                compressionToken,
                Collections.unmodifiableList(matrixTokens),
                displayMaterial,
                glow,
                leatherColor,
                lore
        );
    }

    @Override
    public @Nullable CollectionInfoGui.IngredientView describeToken(@NotNull String token) {
        if (token.isEmpty()) {
            return null;
        }
        if (token.startsWith("vanilla:")) {
            Material material = parseMaterial(token.substring(8));
            if (material == null) {
                return null;
            }
            CollectionInfoGui.CollectionItem colItem = CollectionInfoGui.findAnyItem(material);
            String displayName = colItem != null ? colItem.name() : readableMaterialName(material);
            return new CollectionInfoGui.IngredientView(
                    token,
                    material,
                    displayName,
                    null,
                    false,
                    false,
                    null,
                    List.of()
            );
        }

        ProfessionCatalog.Definition def = catalog.get(token);
        if (def == null) {
            return null;
        }
        boolean craftable = def.config().contains("compression") || def.config().contains("recipe");
        Material displayMaterial = resolveDisplayMaterial(def);
        boolean glow = def.config().contains("compression");
        Color leatherColor = resolveLeatherColor(def);
        return new CollectionInfoGui.IngredientView(
                token,
                displayMaterial,
                def.name(),
                def.id(),
                craftable,
                glow,
                leatherColor,
                buildItemLore(def)
        );
    }

    @Override
    public boolean selectRecipe(@NotNull Player player, @NotNull String itemId) {
        ProfessionCatalog.Definition def = catalog.get(itemId);
        if (def == null || (!def.config().contains("compression") && !def.config().contains("recipe"))) {
            return false;
        }
        crafting.select(player, itemId);
        return true;
    }

    private @NotNull Material resolveDisplayMaterial(@NotNull ProfessionCatalog.Definition def) {
        if (def.material() == Material.PAPER && def.config().contains("compression")) {
            Material base = resolveBaseVanillaMaterial(def.id());
            if (base != null) {
                return base;
            }
        }
        return def.material();
    }

    private @Nullable Color resolveLeatherColor(@NotNull ProfessionCatalog.Definition def) {
        if ("armor".equals(def.kind()) && def.config().contains("color")) {
            int rgb = def.config().getInt("color", -1);
            if (rgb >= 0) {
                return Color.fromRGB(rgb);
            }
        }
        return null;
    }

    private @NotNull List<Component> buildItemLore(@NotNull ProfessionCatalog.Definition def) {
        List<Component> lore = new ArrayList<>();
        for (Component line : catalog.description(def)) {
            lore.add(line.decoration(TextDecoration.ITALIC, false));
        }
        List<String> reqs = catalog.requirements(def);
        if (!reqs.isEmpty()) {
            lore.add(Component.empty());
            lore.add(Component.text("解锁/制作要求：", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            for (String req : reqs) {
                lore.add(Component.text(" • " + req, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            }
        }
        return Collections.unmodifiableList(lore);
    }

    private @Nullable Material resolveBaseVanillaMaterial(@Nullable String token) {
        String current = token;
        for (int depth = 0; depth < 10 && current != null; depth++) {
            if (current.startsWith("vanilla:")) {
                return parseMaterial(current.substring(8));
            }
            ProfessionCatalog.Definition def = catalog.get(current);
            if (def == null || !def.config().contains("compression")) {
                return null;
            }
            current = def.config().getString("compression");
        }
        return null;
    }

    private static int extendedTierFor(long amount) {
        int tier = 0;
        for (int req : ProfessionMath.COLLECTION_REQUIREMENTS) {
            if (amount < req) {
                break;
            }
            tier++;
        }
        return tier;
    }

    private static @Nullable Material parseMaterial(@NotNull String name) {
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static @NotNull String formatComma(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    private static @NotNull String readableMaterialName(@NotNull Material material) {
        return switch (material) {
            case LEATHER_HELMET -> "皮革头盔";
            case LEATHER_CHESTPLATE -> "皮革胸甲";
            case LEATHER_LEGGINGS -> "皮革护腿";
            case LEATHER_BOOTS -> "皮革靴子";
            case IRON_HOE -> "铁锄";
            case IRON_PICKAXE -> "铁镐";
            case IRON_SWORD -> "铁剑";
            default -> material.name();
        };
    }
}
