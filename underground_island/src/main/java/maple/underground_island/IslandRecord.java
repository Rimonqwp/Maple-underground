package maple.underground_island;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

record IslandRecord(
        long islandId,
        UUID ownerUuid,
        String worldName,
        String memberRole,
        int blocksMined,
        boolean portalRewarded,
        int centerX,
        int centerY,
        int centerZ
) {
    boolean isOwner(UUID playerId) {
        return playerId != null
                && ownerUuid.equals(playerId)
                && "OWNER".equalsIgnoreCase(memberRole);
    }

    Location center(World world) {
        return new Location(world, centerX, centerY, centerZ);
    }

    Location home(World world) {
        return new Location(world, centerX + 0.5D, centerY + 1.2D, centerZ + 2.5D, 180.0F, 0.0F);
    }

    IslandRecord withMineState(int newBlocksMined, boolean newPortalRewarded) {
        return new IslandRecord(
                islandId,
                ownerUuid,
                worldName,
                memberRole,
                Math.max(blocksMined, newBlocksMined),
                portalRewarded || newPortalRewarded,
                centerX,
                centerY,
                centerZ
        );
    }
}
