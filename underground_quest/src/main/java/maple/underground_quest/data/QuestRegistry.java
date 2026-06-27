package maple.underground_quest.data;

import maple.underground_quest.UndergroundQuest;
import maple.underground_quest.model.Quest;
import maple.underground_quest.model.QuestObjective;
import maple.underground_quest.model.QuestReward;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * 任務定義倉庫：從 quests.yml 載入所有任務定義，於啟用期唯讀。
 *
 * <p>重新載入（questadmin reload）會重建整張表，不影響已在玩家身上跑的進度副本。
 */
public final class QuestRegistry {

    private final UndergroundQuest plugin;
    private final Map<String, Quest> quests = new LinkedHashMap<>();

    public QuestRegistry(UndergroundQuest plugin) {
        this.plugin = plugin;
    }

    /** 從設定檔（resources/quests.yml）載入任務定義。 */
    public void load() {
        quests.clear();
        ConfigurationSection root = plugin.getConfig();
        if (root == null) {
            return;
        }
        ConfigurationSection section = root.getConfigurationSection("quests");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            try {
                Quest quest = parseQuest(id, section.getConfigurationSection(id));
                if (quest != null) {
                    quests.put(id, quest);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "載入任務定義失敗: " + id, e);
            }
        }
        plugin.getLogger().info("已載入 " + quests.size() + " 項任務定義。");
    }

    public void reload() {
        plugin.reloadConfig();
        load();
    }

    public Quest getQuest(String id) {
        return quests.get(id);
    }

    public Collection<Quest> all() {
        return Collections.unmodifiableCollection(quests.values());
    }

    public int getQuestCount() {
        return quests.size();
    }

    public List<String> getAllQuestIds() {
        return new ArrayList<>(quests.keySet());
    }

    private Quest parseQuest(String id, ConfigurationSection sec) {
        if (sec == null) {
            return null;
        }
        String displayName = sec.getString("display-name", id);
        List<String> description = sec.getStringList("description");
        List<QuestObjective> objectives = new ArrayList<>();
        ConfigurationSection objSec = sec.getConfigurationSection("objectives");
        if (objSec != null) {
            for (String key : objSec.getKeys(false)) {
                ConfigurationSection o = objSec.getConfigurationSection(key);
                if (o == null) {
                    continue;
                }
                objectives.add(QuestObjective.fromConfig(key, o));
            }
        }
        List<QuestReward> rewards = new ArrayList<>();
        ConfigurationSection rewSec = sec.getConfigurationSection("rewards");
        if (rewSec != null) {
            for (String key : rewSec.getKeys(false)) {
                ConfigurationSection r = rewSec.getConfigurationSection(key);
                if (r == null) {
                    continue;
                }
                rewards.add(QuestReward.fromConfig(r));
            }
        } else {
            // 備用：rewards 也可以是 list 形式
            for (Map<?, ?> raw : sec.getMapList("rewards")) {
                // 跳過，fromConfig 需要 ConfigurationSection；list 形式暫不支援
            }
        }
        List<String> prerequisites = sec.getStringList("prerequisites");
        return new Quest(id, displayName, description, objectives, rewards, prerequisites);
    }
}