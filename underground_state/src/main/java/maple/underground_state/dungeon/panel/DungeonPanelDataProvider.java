package maple.underground_state.dungeon.panel;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface DungeonPanelDataProvider {
    int EXPERIENCE_COUNT = 6;

    long @NotNull [] fetchExperience(@NotNull UUID playerId);

    long @NotNull [] refreshExperience(@NotNull UUID playerId);

    void addExperience(@NotNull UUID playerId, int expIndex, long amount);

    boolean isSoloClassSelection(@NotNull Player player);
}
