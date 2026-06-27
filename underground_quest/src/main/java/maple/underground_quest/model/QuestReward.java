package maple.underground_quest.model;

import maple.underground_quest.UndergroundQuest;
import maple.underground_state.UndergroundState;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Objects;

/**
 * 任務獎勵定義（從 quests.yml 讀取）。
 */
public final class QuestReward {

    private final QuestRewardType type;
    private final int amount;
    private final String payload;

    public QuestReward(QuestRewardType type, int amount, String payload) {
        this.type = Objects.requireNonNull(type, "type");
        this.amount = amount;
        this.payload = payload == null ? "" : payload;
    }

    public static QuestReward fromConfig(ConfigurationSection section) {
        if (section == null) {
            throw new IllegalArgumentException("reward section is null");
        }
        String rawType = section.getString("type", "COMMAND").toUpperCase(Locale.ROOT);
        QuestRewardType type;
        try {
            type = QuestRewardType.valueOf(rawType);
        } catch (IllegalArgumentException e) {
            type = QuestRewardType.COMMAND;
        }
        int amount = section.getInt("amount", 1);
        String payload = section.getString("payload", section.getString("value", ""));
        return new QuestReward(type, Math.max(0, amount), payload);
    }

    public QuestRewardType getType() { return type; }
    public int getAmount() { return amount; }
    public String getPayload() { return payload; }

    /** 發放獎勵給玩家。 */
    public void grant(UndergroundQuest plugin, Player player) {
        switch (type) {
            case COMMAND -> {
                String cmd = payload.replace("{player}", player.getName());
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd);
            }
            case MESSAGE -> player.sendMessage(
                    net.kyori.adventure.text.Component.text(payload,
                            net.kyori.adventure.text.format.NamedTextColor.GREEN));
            case EXPERIENCE -> player.giveExpLevels(Math.max(0, amount));
            case ITEM -> {
                Material mat = Material.matchMaterial(payload);
                if (mat != null) {
                    player.getInventory().addItem(new ItemStack(mat, Math.max(1, amount)));
                } else {
                    plugin.getLogger().warning("Unknown item material: " + payload);
                }
            }
            case MONEY -> {
                UndergroundState state = plugin.getState();
                if (state != null && state.getBankManager() != null) {
                    state.getBankManager().deposit(player, Math.max(0L, (long) amount));
                } else {
                    plugin.getLogger().warning("BankManager unavailable, cannot grant MONEY reward.");
                }
            }
            default -> plugin.getLogger().warning("Unknown reward type: " + type);
        }
    }

    @Override
    public String toString() {
        return "QuestReward{type=" + type + ", amount=" + amount + ", payload='" + payload + "'}";
    }
}