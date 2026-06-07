package maple.underground_state.skills;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public enum SkillType {

    COMBAT("戰鬥", Material.COPPER_SWORD),
    MINING("挖礦", Material.COPPER_PICKAXE),
    FORAGING("伐木", Material.COPPER_AXE),
    FARMING("耕作", Material.COPPER_HOE),
    FISHING("釣魚", Material.FISHING_ROD),
    ENCHANTING("附魔", Material.ENCHANTING_TABLE),
    ALCHEMY("煉藥", Material.BREWING_STAND),
    EXPLORATION("探索", Material.ELYTRA),
    TAMING("馴養", Material.BUNDLE),
    CRAFTING("工藝", Material.CRAFTING_TABLE),
    SOCIAL("社交", Material.COPPER_GOLEM_STATUE);

    private final String displayName;
    private final Material icon;

    SkillType(String displayName, Material icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public @NotNull String displayName() {
        return displayName;
    }

    public @NotNull Material icon() {
        return icon;
    }

    public @NotNull String columnName() {
        return name().toLowerCase(Locale.ROOT) + "_xp";
    }
}
