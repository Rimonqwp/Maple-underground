package maple.underground_mayor.mayor;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MayorCollectionManager {
    public static final int TOTAL_BADGES = 100;

    private static final Map<Material, Integer> TARGETS = new LinkedHashMap<>();

    static {
        TARGETS.put(Material.SPRUCE_PLANKS, 4000);
        TARGETS.put(Material.STONE, 10000);
        TARGETS.put(Material.ANDESITE, 3000);
        TARGETS.put(Material.STONE_BRICKS, 3000);
        TARGETS.put(Material.LIGHT_GRAY_CONCRETE, 1000);
        TARGETS.put(Material.POLISHED_ANDESITE, 1252);
        TARGETS.put(Material.COBBLESTONE_STAIRS, 463);
        TARGETS.put(Material.STONE_BRICK_STAIRS, 463);
        TARGETS.put(Material.MANGROVE_STAIRS, 82);
        TARGETS.put(Material.OAK_LEAVES, 100);
        TARGETS.put(Material.BIRCH_PLANKS, 4000);
        TARGETS.put(Material.ANVIL, 30);
    }

    private final maple.underground_mayor.UndergroundMayor plugin;
    private final File progressFile;
    private final YamlConfiguration progressConfig;
    private final EnumMap<Material, Integer> progress = new EnumMap<>(Material.class);
    private final Map<UUID, Integer> playerContribution = new HashMap<>();
    private final Map<UUID, Integer> playerBadgeEntitlement = new HashMap<>();
    private final Map<UUID, Integer> playerBadgesAwarded = new HashMap<>();

    private boolean rewardsFinalized;

    public MayorCollectionManager(maple.underground_mayor.UndergroundMayor plugin) {
        this.plugin = plugin;
        this.progressFile = new File(plugin.getDataFolder(), "collection-progress.yml");
        this.progressConfig = YamlConfiguration.loadConfiguration(progressFile);
        load();
    }

    public synchronized TurnInResult collectFrom(Player player) {
        boolean wasComplete = isComplete();
        Map<Material, Integer> collected = new LinkedHashMap<>();

        for (Map.Entry<Material, Integer> entry : TARGETS.entrySet()) {
            Material material = entry.getKey();
            int remaining = getRemaining(material);
            if (remaining <= 0) {
                continue;
            }

            int removed = removeUpTo(player.getInventory(), material, remaining);
            if (removed <= 0) {
                continue;
            }

            progress.put(material, getCollected(material) + removed);
            collected.put(material, removed);
        }

        boolean changed = !collected.isEmpty();
        int totalCollected = collected.values().stream().mapToInt(Integer::intValue).sum();
        int playerContributionTotal = getPlayerContribution(player.getUniqueId());
        boolean rewardsFinalizedNow = false;

        if (changed) {
            playerContributionTotal = playerContributionTotal + totalCollected;
            playerContribution.put(player.getUniqueId(), playerContributionTotal);
            player.updateInventory();
        }

        boolean isNowComplete = isComplete();
        if (isNowComplete && !rewardsFinalized) {
            finalizeBadgeEntitlements();
            rewardsFinalizedNow = true;
        }

        if (changed || rewardsFinalizedNow) {
            saveNow();
        }

        return new TurnInResult(
                collected,
                changed,
                !wasComplete && isNowComplete,
                rewardsFinalizedNow,
                totalCollected,
                playerContributionTotal
        );
    }

    public synchronized int claimPendingBadges(UUID playerId) {
        PlayerRewardStatus status = getPlayerRewardStatus(playerId);
        int pending = status.pendingBadges();
        if (pending <= 0) {
            return 0;
        }

        playerBadgesAwarded.put(playerId, status.awardedBadges() + pending);
        saveNow();
        return pending;
    }

    public synchronized int getCollected(Material material) {
        return progress.getOrDefault(material, 0);
    }

    public synchronized int getTarget(Material material) {
        return TARGETS.getOrDefault(material, 0);
    }

    public synchronized int getRemaining(Material material) {
        return Math.max(0, getTarget(material) - getCollected(material));
    }

    public synchronized boolean isComplete() {
        for (Map.Entry<Material, Integer> entry : TARGETS.entrySet()) {
            if (getCollected(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    public synchronized boolean isRewardsFinalized() {
        return rewardsFinalized;
    }

    public synchronized List<String> describeProgressLines() {
        List<String> lines = new ArrayList<>();
        lines.add("當前共同收集進度：");
        for (Map.Entry<Material, Integer> entry : TARGETS.entrySet()) {
            Material material = entry.getKey();
            lines.add("- " + materialName(material) + ": " + getCollected(material) + "/" + entry.getValue());
        }
        lines.add("- 徽章總量: " + TOTAL_BADGES);
        if (rewardsFinalized) {
            lines.add("- 100 枚徽章已按最終貢獻比例完成精確結算");
        } else {
            lines.add("- 材料全部收集完成後，將按最終貢獻比例精確分配 100 枚徽章");
        }
        if (isComplete()) {
            lines.add("所有材料都已經收集完成了。");
        }
        return lines;
    }

    public synchronized List<String> describePlayerContributionLines(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }

        PlayerRewardStatus status = getPlayerRewardStatus(playerId);
        List<String> lines = new ArrayList<>();
        lines.add("你的累計提交: " + status.playerContribution());

        if (rewardsFinalized) {
            lines.add("你的結算徽章: " + status.entitledBadges());
            lines.add("你的已領取徽章: " + status.awardedBadges());
            lines.add("你的待領取徽章: " + status.pendingBadges());
        } else {
            lines.add("徽章將在材料全部收集完成後按貢獻比例統一結算。");
        }
        return lines;
    }

    public synchronized PlayerRewardStatus getPlayerRewardStatus(UUID playerId) {
        if (playerId == null) {
            return new PlayerRewardStatus(0, 0, 0, 0);
        }

        int contribution = playerContribution.getOrDefault(playerId, 0);
        int entitled = playerBadgeEntitlement.getOrDefault(playerId, 0);
        int awarded = playerBadgesAwarded.getOrDefault(playerId, 0);
        int pending = Math.max(0, entitled - awarded);
        return new PlayerRewardStatus(contribution, entitled, awarded, pending);
    }

    public synchronized int getPlayerContribution(UUID playerId) {
        return getPlayerRewardStatus(playerId).playerContribution();
    }

    public synchronized int getTotalRequiredAmount() {
        return TARGETS.values().stream().mapToInt(Integer::intValue).sum();
    }

    public synchronized void saveNow() {
        progressConfig.set("progress", null);
        progressConfig.set("players", null);
        progressConfig.set("rewards", null);

        for (Map.Entry<Material, Integer> entry : TARGETS.entrySet()) {
            progressConfig.set("progress." + entry.getKey().name(), getCollected(entry.getKey()));
        }

        for (Map.Entry<UUID, Integer> entry : playerContribution.entrySet()) {
            String path = "players." + entry.getKey();
            progressConfig.set(path + ".contribution", entry.getValue());
            progressConfig.set(path + ".badge_entitlement", playerBadgeEntitlement.getOrDefault(entry.getKey(), 0));
            progressConfig.set(path + ".badges_awarded", playerBadgesAwarded.getOrDefault(entry.getKey(), 0));
        }

        progressConfig.set("rewards.finalized", rewardsFinalized);

        try {
            progressConfig.save(progressFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save collection progress: " + ex.getMessage());
        }
    }

    private void load() {
        for (Map.Entry<Material, Integer> entry : TARGETS.entrySet()) {
            int amount = Math.max(0, progressConfig.getInt("progress." + entry.getKey().name(), 0));
            progress.put(entry.getKey(), Math.min(amount, entry.getValue()));
        }

        rewardsFinalized = progressConfig.getBoolean("rewards.finalized", false);

        if (progressConfig.isConfigurationSection("players")) {
            for (String rawUuid : progressConfig.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(rawUuid);
                    int contribution = Math.max(0, progressConfig.getInt("players." + rawUuid + ".contribution", 0));
                    int entitlement = Math.max(0, progressConfig.getInt("players." + rawUuid + ".badge_entitlement", 0));
                    int awarded = Math.max(0, progressConfig.getInt("players." + rawUuid + ".badges_awarded", 0));
                    playerContribution.put(uuid, contribution);
                    playerBadgeEntitlement.put(uuid, entitlement);
                    playerBadgesAwarded.put(uuid, awarded);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        if (isComplete() && !rewardsFinalized) {
            finalizeBadgeEntitlements();
            saveNow();
        }
    }

    private void finalizeBadgeEntitlements() {
        playerBadgeEntitlement.clear();

        int totalRequired = getTotalRequiredAmount();
        if (totalRequired <= 0) {
            rewardsFinalized = true;
            return;
        }

        List<BadgeRemainder> remainders = new ArrayList<>();
        int allocated = 0;

        for (Map.Entry<UUID, Integer> entry : playerContribution.entrySet()) {
            UUID playerId = entry.getKey();
            int contribution = Math.max(0, entry.getValue());
            if (contribution <= 0) {
                continue;
            }

            long weighted = (long) contribution * TOTAL_BADGES;
            int base = (int) (weighted / totalRequired);
            long remainder = weighted % totalRequired;

            playerBadgeEntitlement.put(playerId, base);
            allocated += base;
            remainders.add(new BadgeRemainder(playerId, remainder, contribution));
        }

        int remaining = Math.max(0, TOTAL_BADGES - allocated);
        remainders.sort(
                Comparator.comparingLong(BadgeRemainder::remainder).reversed()
                        .thenComparing(Comparator.comparingInt(BadgeRemainder::contribution).reversed())
                        .thenComparing(entry -> entry.playerId().toString())
        );

        for (int i = 0; i < remaining && i < remainders.size(); i++) {
            BadgeRemainder remainder = remainders.get(i);
            playerBadgeEntitlement.merge(remainder.playerId(), 1, Integer::sum);
        }

        rewardsFinalized = true;
    }

    private int removeUpTo(Inventory inventory, Material material, int limit) {
        int removed = 0;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length && removed < limit; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType() != material) {
                continue;
            }

            int need = limit - removed;
            int take = Math.min(need, item.getAmount());
            removed += take;

            if (take == item.getAmount()) {
                inventory.setItem(slot, null);
            } else {
                item.setAmount(item.getAmount() - take);
                inventory.setItem(slot, item);
            }
        }
        return removed;
    }

    public static String materialName(Material material) {
        return switch (material) {
            case SPRUCE_PLANKS -> "杉木材";
            case STONE -> "石頭";
            case ANDESITE -> "安山岩";
            case STONE_BRICKS -> "石磚";
            case LIGHT_GRAY_CONCRETE -> "淺灰色混凝土";
            case POLISHED_ANDESITE -> "拋光安山岩";
            case COBBLESTONE_STAIRS -> "鵝卵石階梯";
            case STONE_BRICK_STAIRS -> "石磚階梯";
            case MANGROVE_STAIRS -> "紅樹林木階梯";
            case OAK_LEAVES -> "橡木樹葉";
            case BIRCH_PLANKS -> "樺木材";
            case ANVIL -> "鐵砧";
            default -> material.name();
        };
    }

    public record TurnInResult(
            Map<Material, Integer> collected,
            boolean changed,
            boolean completedNow,
            boolean rewardsFinalizedNow,
            int totalCollected,
            int playerContributionTotal
    ) {
    }

    public record PlayerRewardStatus(
            int playerContribution,
            int entitledBadges,
            int awardedBadges,
            int pendingBadges
    ) {
    }

    private record BadgeRemainder(UUID playerId, long remainder, int contribution) {
    }
}
