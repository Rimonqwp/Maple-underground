package maple.underground_caves.dungeon.classskill;

import maple.underground_caves.Test_dungeons;
import org.bukkit.entity.Player;

abstract class AbstractDungeonClassSkillListener {
    protected final Test_dungeons plugin;

    protected AbstractDungeonClassSkillListener(Test_dungeons plugin) {
        this.plugin = plugin;
    }

    protected boolean inDungeon(Player player) {
        return plugin != null && player != null && plugin.isDungeonWorld(player.getWorld().getName());
    }

    protected boolean dungeonStarted(Player player) {
        return player != null && plugin != null && plugin.isDungeonRunStarted(player.getWorld().getName());
    }

    protected int classIndex(Player player) {
        return plugin == null || player == null ? -1 : plugin.getDungeonClassIndex(player);
    }

    protected int classLevel(Player player) {
        return plugin == null || player == null ? 0 : plugin.getDungeonClassLevel(player);
    }

    /**
     * 副本倒地幽靈（靈魂狀態）：該玩家不得作為任何職業技能／職業被動的來源（主動、光環、轉傷、延遲結算 Runnable 等皆需攔截）。
     */
    protected boolean isDungeonSoulMode(Player player) {
        if (player == null || plugin == null) return false;
        var death = plugin.getDungeonDeathListener();
        return death != null && death.isDungeonGhost(player.getUniqueId());
    }
}

