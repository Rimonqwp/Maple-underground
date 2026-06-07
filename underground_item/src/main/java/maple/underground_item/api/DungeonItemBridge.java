package maple.underground_item.api;

import org.bukkit.entity.Player;

import java.util.UUID;

public interface DungeonItemBridge {

    boolean isDungeonWorld(String worldName);

    String getFloorForDungeonWorld(String worldName);

    int getDungeonClassIndex(Player player);

    boolean isDungeonGhost(UUID playerId);

    double getCooldownMultiplier(Player player);

    double applyHealthBlessing(Player player, double base);

    double applyDefenseBlessing(Player player, double base);

    double applyIntelligenceBlessing(Player player, double base);

    double applyDamageBlessing(Player player, double base);

    void syncVirtualHealthBar(Player player);
}
