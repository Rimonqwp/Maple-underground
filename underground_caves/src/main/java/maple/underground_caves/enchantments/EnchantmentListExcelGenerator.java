package maple.underground_caves.enchantments;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

/**
 * 產生附魔列表 Excel (.xlsx)，寫入 src/main/resources/enchantments_list.xlsx。
 * 欄位：附魔ID、名字、属性(效果說明)、最大等級、是否可在鐵砧升級、類型、適用裝備。
 * 執行：mvn compile exec:java -Dexec.mainClass="maple.underground_caves.enchantments.EnchantmentListExcelGenerator"
 */
public final class EnchantmentListExcelGenerator {

    private static final String[] HEADERS = { "附魔ID", "名字", "属性", "最大等級", "是否可在鐵砧升級", "類型", "適用裝備" };

    /** 不可在鐵砧使用（不可融合、不可打在裝備上）：洞窟祝福、孤注一擲、元气小收藏、殺戮回春 */
    private static final Set<String> NO_ANVIL_IDS = Set.of("cave_blessing", "all_in", "collection_bonus", "kill_heal");

    /** 鐵砧等級上限為 5，超過 5 級的附魔在鐵砧最高只能升到 5 級 */
    private static final int ANVIL_LEVEL_CAP = 5;

    private static String anvilUpgradeLabel(int maxLevel, String enchantId) {
        if (NO_ANVIL_IDS.contains(enchantId)) return "否";
        if (maxLevel <= ANVIL_LEVEL_CAP) return "是（可達滿級）";
        return "是（鐵砧最高" + ANVIL_LEVEL_CAP + "級）";
    }

    private static final List<String[]> ROWS = List.of(
        new String[] { "combo", "連擊", "連續攻擊目標每次攻擊傷害增加，5秒未攻擊重置。每層+0.2%~0.5%，最多2/3/5/7/10層", "5", anvilUpgradeLabel(5, "combo"), "終極附魔", "劍" },
        new String[] { "deadwood_revival", "枯木逢春", "最脆弱的木棍也能斬斷鋼鐵。+2/+3/+4/+5/+6 點傷害", "5", anvilUpgradeLabel(5, "deadwood_revival"), "終極附魔", "劍" },
        new String[] { "fortune_coins", "招財進寶", "擊殺敵人掉落 0.5/1/1.5/2/2.5 金幣", "5", anvilUpgradeLabel(5, "fortune_coins"), "終極附魔", "劍" },
        new String[] { "infinite_quiver", "無限箭袋", "射箭時 4%~40% 機率不消耗箭矢（每級+4%）", "10", anvilUpgradeLabel(10, "infinite_quiver"), "普通附魔", "弓" },
        new String[] { "feather_wing", "羽毛翅膀", "減少摔落傷害 3%~30%，基礎免疫機率 0%~2.5%，高度免疫加成 4~8 格", "10", anvilUpgradeLabel(10, "feather_wing"), "普通附魔", "鞋子" },
        new String[] { "cave_blessing", "洞窟祝福", "身處世界時每 30/25/20/18/12 秒 +1 生命值恢復", "5", anvilUpgradeLabel(5, "cave_blessing"), "普通附魔", "裝備" },
        new String[] { "no_pain_no_gain", "不勞無獲", "每 10 次被怪物擊中時有 5%/15%/25%/35%/45% 機率獲得經驗球", "5", anvilUpgradeLabel(5, "no_pain_no_gain"), "終極附魔", "裝備" },
        new String[] { "wisdom", "智慧", "每 5 經驗等級提升 1~5 點智力，上限 20~100 點", "5", anvilUpgradeLabel(5, "wisdom"), "終極附魔", "裝備" },
        new String[] { "thrift", "節流", "降低該物品技能 10%~50% 的法力消耗量", "5", anvilUpgradeLabel(5, "thrift"), "終極附魔", "武器" },
        new String[] { "all_in", "孤注一擲", "移除武器上所有其他附魔，傷害提高 500%", "1", anvilUpgradeLabel(1, "all_in"), "終極附魔", "武器" },
        new String[] { "last_stand", "背水一戰", "生命值低於 35% 時受擊獲得額外 +5%~+25% 防禦力", "5", anvilUpgradeLabel(5, "last_stand"), "終極附魔", "裝備" },
        new String[] { "armor_break", "碎甲", "攻擊時 0.3%~1.5% 機率無視目標防禦造成真實傷害", "5", anvilUpgradeLabel(5, "armor_break"), "終極附魔", "劍/弓" },
        new String[] { "kill_legacy", "獵殺傳承", "武器獲得最近擊殺怪物的 1.5~4 倍傷害，下次暴擊時加上該傷害", "5", anvilUpgradeLabel(5, "kill_legacy"), "終極附魔", "劍/弓" },
        new String[] { "companion_resonance", "伴生共鳴", "複製目前寵物的 20%~100% 屬性到自己身上", "5", anvilUpgradeLabel(5, "companion_resonance"), "終極附魔", "劍" },
        new String[] { "combo_storm", "連擊風暴", "每次攻擊增加連擊層數，每層 +10%~+50% 傷害，最後一次攻擊後 3 秒內傷害上限 200%", "5", anvilUpgradeLabel(5, "combo_storm"), "終極附魔", "劍" },
        new String[] { "lone_fighter", "孤軍奮戰", "每 10 格範圍內一名敵人傷害提高 +2%~+10%", "5", anvilUpgradeLabel(5, "lone_fighter"), "終極附魔", "劍/弓" },
        new String[] { "ember_chase", "餘火追擊", "射擊時額外一箭造成主箭 3%~15% 傷害，目標受 1.1~1.5 倍持續火焰傷害 60 秒", "5", anvilUpgradeLabel(5, "ember_chase"), "終極附魔", "弓" },
        new String[] { "war_aura", "征戰光環", "提升 +0.07%~+0.35% 所有戰鬥屬性，周圍 30 格內每位玩家 +0.07%~+0.35% 稀有掉落（最多 20 人）", "5", anvilUpgradeLabel(5, "war_aura"), "終極附魔", "裝備" },
        new String[] { "death_pennies", "斂財", "死亡時節省 10%~50% 金幣", "5", anvilUpgradeLabel(5, "death_pennies"), "終極附魔", "裝備" },
        new String[] { "collection_bonus", "元气小收藏", "每完成一個滿級收藏獲得 +0.1/+0.2 生命和力量", "2", anvilUpgradeLabel(2, "collection_bonus"), "終極附魔", "飾品" },
        new String[] { "spider_killer", "蛛蛛殺手", "對蜘蛛、洞穴蜘蛛和蠹魚傷害增加 +2%~+14%（每級+2%）", "7", anvilUpgradeLabel(7, "spider_killer"), "普通附魔", "劍" },
        new String[] { "mercenary", "傭兵生涯", "額外戰鬥經驗 +3%~+10%，連續攻擊賺取金幣與經驗球，累積戰鬥經驗可升級", "10", anvilUpgradeLabel(10, "mercenary"), "普通附魔", "劍" },
        new String[] { "critical_strike", "致命一擊", "暴擊傷害 +10%~+50%", "5", anvilUpgradeLabel(5, "critical_strike"), "普通附魔", "劍" },
        new String[] { "lucky", "幸運兒", "稀有物品掉落率 +1%~+5%", "5", anvilUpgradeLabel(5, "lucky"), "普通附魔", "劍" },
        new String[] { "dragon_slayer", "屠龍者", "對終界龍傷害提升 +5%~+25%", "5", anvilUpgradeLabel(5, "dragon_slayer"), "普通附魔", "劍/弓" },
        new String[] { "ender_slayer", "終界殺手", "對終界使者、終界蟎和終界龍傷害提升 +15%~+130%", "7", anvilUpgradeLabel(7, "ender_slayer"), "普通附魔", "劍" },
        new String[] { "twin", "雙子附魔", "擊殺怪物時有 12.5%/25%/37.5%/50%/62.5% 機率獲得雙倍經驗值", "5", anvilUpgradeLabel(5, "twin"), "普通附魔", "劍/鎬" },
        new String[] { "surprise_strike", "奇襲", "對尚未對你造成過傷害的怪物第一次近戰攻擊傷害 +10%~50%", "5", anvilUpgradeLabel(5, "surprise_strike"), "普通附魔", "劍" },
        new String[] { "challenger", "挑戰者", "目標生命每比你高 5%/4.5%/4%/3.5%/3% 傷害 +0.1%~1.2%", "5", anvilUpgradeLabel(5, "challenger"), "普通附魔", "劍" },
        new String[] { "overwhelming", "排山倒海", "攻擊 15%/30%/50% 機率強力擊退 3/4.5/6 格", "3", anvilUpgradeLabel(3, "overwhelming"), "普通附魔", "劍" },
        new String[] { "life_steal", "生命汲取", "每次攻擊怪物恢復最大生命 0.5%~2.5%", "5", anvilUpgradeLabel(5, "life_steal"), "普通附魔", "劍" },
        new String[] { "armor_shred", "剝甲專家", "擊殺穿盔甲怪物時盔甲掉落機率 +5%~35%", "5", anvilUpgradeLabel(5, "armor_shred"), "普通附魔", "劍" },
        new String[] { "mana_steal", "法力汲取", "每次擊中回復最大法力 0.25%~0.75%", "5", anvilUpgradeLabel(5, "mana_steal"), "普通附魔", "劍" },
        new String[] { "execute", "斬殺", "目標每失去 1% 生命傷害 +0.1%~1%", "5", anvilUpgradeLabel(5, "execute"), "普通附魔", "劍" },
        new String[] { "bounty_hunter", "賞金獵人", "擊殺怪物獲得 0.3~1.8 枚金幣", "5", anvilUpgradeLabel(5, "bounty_hunter"), "普通附魔", "劍" },
        new String[] { "blood_crit", "鮮血暴擊", "暴擊傷害每 100 點治療最大生命 0.2%~0.6%，最多 1000 點", "5", anvilUpgradeLabel(5, "blood_crit"), "普通附魔", "劍" },
        new String[] { "armor_break_combo", "碎甲連擊", "每次命中降低目標防禦 1.2%~9% 持續 4 秒，最多 4 層", "5", anvilUpgradeLabel(5, "armor_break_combo"), "普通附魔", "劍" },
        new String[] { "lone_wolf", "孤狼", "無寵物時 +2/2.5/3 武器傷害", "3", anvilUpgradeLabel(3, "lone_wolf"), "普通附魔", "劍" },
        new String[] { "thunder_chain", "雷霆連擊", "每第 3 次擊中召喚閃電，周圍 2 格最多 10 怪受 4%~30% 傷害", "5", anvilUpgradeLabel(5, "thunder_chain"), "普通附魔", "劍" },
        new String[] { "thunder_god_chain", "雷神連擊", "每第 3 次擊中召喚閃電，目標額外受 8%~60% 傷害", "5", anvilUpgradeLabel(5, "thunder_god_chain"), "普通附魔", "劍" },
        new String[] { "wild_fury", "狂野之怒", "獲得 +3/4/5 combo", "3", anvilUpgradeLabel(3, "wild_fury"), "普通附魔", "劍" },
        new String[] { "kill_heal", "殺戮回春", "擊殺敵人時恢復已損失生命 1%~6%", "5", anvilUpgradeLabel(5, "kill_heal"), "普通附魔", "劍" },
        new String[] { "first_strike", "先發制人", "對同一怪物前 3 次近戰傷害 +10%~50%", "5", anvilUpgradeLabel(5, "first_strike"), "普通附魔", "劍" }
    );

    public static void main(String[] args) {
        Path outDir = Paths.get("src", "main", "resources").toAbsolutePath();
        Path outFile = outDir.resolve("enchantments_list.xlsx");
        try {
            Files.createDirectories(outDir);
            try (Workbook wb = new XSSFWorkbook()) {
                Sheet sheet = wb.createSheet("附魔列表");
                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < HEADERS.length; i++) {
                    headerRow.createCell(i).setCellValue(HEADERS[i]);
                }
                for (int r = 0; r < ROWS.size(); r++) {
                    Row row = sheet.createRow(r + 1);
                    String[] cells = ROWS.get(r);
                    for (int c = 0; c < cells.length; c++) {
                        row.createCell(c).setCellValue(cells[c]);
                    }
                }
                for (int c = 0; c < HEADERS.length; c++) {
                    sheet.autoSizeColumn(c);
                }
                try (FileOutputStream fos = new FileOutputStream(outFile.toFile())) {
                    wb.write(fos);
                }
            }
            System.out.println("已寫入: " + outFile);
        } catch (Exception e) {
            throw new RuntimeException("產生 " + outFile + " 失敗", e);
        }
    }
}
