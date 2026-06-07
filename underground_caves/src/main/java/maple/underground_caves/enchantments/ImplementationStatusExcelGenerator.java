package maple.underground_caves.enchantments;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 產生「裝備／武器／附魔實作一覽」Excel (.xlsx)。
 * 執行：mvn compile exec:java -Dexec.mainClass="maple.underground_caves.enchantments.ImplementationStatusExcelGenerator"
 * 輸出：src/main/resources/裝備武器附魔實作一覽.xlsx
 */
public final class ImplementationStatusExcelGenerator {

    private static final String[] HEADERS = { "類型", "配置ID/檔案", "顯示名稱(概略)", "是否已製作", "備註" };

    private static final List<String[]> ROWS = List.of(
        row("武器", "cyclone_stick", "旋風棒", "已製作", "右鍵冷凍技能 FreezeSkillRunner"),
        row("武器", "showtime_hook", "絆線鉤", "已製作", "右鍵 Showtime 氣球 ShowtimeSkillRunner"),
        row("武器", "tremor_blade_1", "餘波斬", "已製作", "WeaponSpecialEffectsListener cleave 3 格"),
        row("武器", "tremor_blade_2", "地鳴之鋒", "已製作", "WeaponSpecialEffectsListener cleave 4 格"),
        row("武器", "tremor_blade_3", "崩岩之刃", "已製作", "WeaponSpecialEffectsListener cleave 5 格"),
        row("武器", "scarf_adaptive_sword", "洞窟共鳴劍", "已製作", "WeaponSpecialEffectsListener 職業加傷/力量"),
        row("武器", "dreadlord_iron", "骷髏領主之刃", "已製作", "WeaponListener 右鍵骷髏頭 500 傷"),
        row("武器", "love_tap_stone", "憐觸石劍", "已製作", "WeaponSpecialEffectsListener 命中回血 +10"),
        row("武器", "zombie_knight_iron", "殭屍騎士劍", "已製作", "WeaponSpecialEffectsListener 篩網套裝 +30% 傷"),
        row("武器", "dragon_rage_diamond", "龍怒鑽石劍", "已製作", "WeaponListener 右鍵前方錐形 12000 傷"),
        row("武器", "emerald_blade", "翠玉之刃", "已製作", "WeaponSpecialEffectsListener 金幣增傷公式；詛咒待接經濟"),
        row("武器", "teleport_diamond", "瞬步鑽石劍", "已製作", "WeaponListener 右鍵瞬移 8 格 + 加速"),
        row("裝備", "defeat_tank_set", "堅壁套裝", "已製作", "GearManager + DefeatTankDropListener"),
        row("裝備", "immortal_skull", "永恆的面具", "已製作", "ImmortalSkullManager 免死效果"),
        row("裝備", "sieve_set", "篩網編織套裝", "已製作", "SieveSetManager 掉落與箭矢抗性"),
        row("裝備", "archer_set", "白骨箭手套裝", "已製作", "ArcherSetManager"),
        row("裝備", "skeleton_soldier_set", "白骨先鋒套裝", "已製作", "SkeletonSoldierSetManager"),
        row("裝備", "skeleton_master_set", "冥界套裝", "已製作", "SkeletonMasterSetManager + 弓不耗箭"),
        row("裝備", "underground_cave_helmet", "地下洞穴頭盔", "已製作", "UndergroundCaveHelmetManager (F1/F2/F3)"),
        row("裝備", "adaptive_set", "洞窟共鳴裝備(自適應)", "已製作", "AdaptiveSetManager 職業加成 + /gear give"),
        row("裝備", "cavern_explorer_set", "深窟探險者", "已製作", "CavernExplorerSetManager 移速+洞窟減傷"),
        row("裝備", "blood_holy_set", "祝福(傳說洞窟)", "已製作", "BloodSetManager 範圍回血倍率"),
        row("裝備", "blood_protective_set", "堅守(傳說洞窟)", "已製作", "BloodSetManager 缺血加防"),
        row("裝備", "blood_wise_set", "睿智(傳說洞窟)", "已製作", "BloodSetManager 魔力消耗減免"),
        row("裝備", "blood_unstable_set", "狂雷(傳說洞窟)", "已製作", "BloodSetManager 載入+基礎效果"),
        row("裝備", "blood_young_set", "青春(傳說洞窟)", "已製作", "BloodSetManager 高血移速"),
        row("裝備", "blood_old_set", "古舊(傳說洞窟)", "已製作", "BloodSetManager 載入+基礎效果"),
        row("裝備", "blood_strong_set", "強韌(傳說洞窟)", "已製作", "BloodSetManager 載入+基礎效果"),
        row("裝備", "blood_superior_set", "至尊(傳說洞窟)", "已製作", "BloodSetManager 戰鬥屬性%"),
        row("裝備", "second_wind_helmet", "絕境逢生頭盔", "已製作", "SecondWindHelmetManager + SecondWindListener 瀕死效果"),
        row("裝備", "arrow_distance_helmet", "遠程蓄勢頭盔", "已製作", "ArrowDistanceHelmetManager + ArrowDistanceListener 箭距增傷"),
        row("裝備", "mending_helmet_1", "癒合之冠", "已製作", "CaveSpecialHelmetManager 治療+活力(地牢)"),
        row("裝備", "mending_helmet_2", "守護療癒之冠", "已製作", "CaveSpecialHelmetManager 治療+活力(地牢)"),
        row("裝備", "mending_helmet_3", "堅韌療癒之冠", "已製作", "CaveSpecialHelmetManager 治療+活力(地牢)"),
        row("裝備", "ability_damage_helmet_1", "法術增幅頭盔", "已製作", "CaveSpecialHelmetManager 能力傷害+智力(地牢)"),
        row("裝備", "ability_damage_helmet_2", "法術精通頭盔", "已製作", "CaveSpecialHelmetManager 能力傷害+智力(地牢)"),
        row("裝備", "ability_damage_helmet_3", "法術宗師頭盔", "已製作", "CaveSpecialHelmetManager 能力傷害+智力(地牢)"),
        row("裝備", "golden_villager_helmet", "黃金村民頭盔", "已製作", "StatsDoubleHelmetManager 頭顱+戴上時屬性x2"),
        row("裝備", "golden_panda_helmet", "黃金熊貓頭盔", "已製作", "StatsDoubleHelmetManager 頭顱+戴上時屬性x2"),
        row("裝備", "golden_sheep_helmet", "黃金綿羊頭盔", "已製作", "StatsDoubleHelmetManager 頭顱+戴上時屬性x2"),
        row("裝備", "diamond_villager_helmet", "鑽石村民頭盔", "已製作", "StatsDoubleHelmetManager 頭顱+戴上時屬性x2"),
        row("裝備", "diamond_panda_helmet", "鑽石熊貓頭盔", "已製作", "StatsDoubleHelmetManager 頭顱+戴上時屬性x2"),
        row("裝備", "diamond_sheep_helmet", "鑽石綿羊頭盔", "已製作", "StatsDoubleHelmetManager 頭顱+戴上時屬性x2"),
        row("附魔書", "（全部為 Java 註冊）", "自定義附魔", "已製作", "EnchantmentRegistry 註冊 無 YAML 配置")
    );

    private static String[] row(String type, String id, String name, String status, String note) {
        return new String[] { type, id, name, status, note };
    }

    public static void main(String[] args) {
        Path outFile = Paths.get("src/main/resources/裝備武器附魔實作一覽.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("實作一覽");
            Row headerRow = sheet.createRow(0);
            for (int c = 0; c < HEADERS.length; c++) {
                headerRow.createCell(c).setCellValue(HEADERS[c]);
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
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile.toFile())) {
                wb.write(fos);
            }
            System.out.println("已寫入: " + outFile.toAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException("產生 " + outFile + " 失敗", e);
        }
    }
}
