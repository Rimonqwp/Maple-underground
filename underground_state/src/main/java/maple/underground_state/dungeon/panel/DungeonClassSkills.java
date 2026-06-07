package maple.underground_state.dungeon.panel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;

/**
 * 職業技能數據與 Lore 生成。每個技能依等級獨立運算，升級時可顯示 current -> next。
 */
public final class DungeonClassSkills {
    private DungeonClassSkills() {}

    /** 職業等級上限，所有技能數值依此線性計算至滿等 */
    private static final int MAX_LVL = DungeonLevelXP.MAX_LEVEL;

    /** 進度條每格「～ 」：刪除線穿過波浪與空格，空格也會上色、視覺無空隙 */
    private static final String PROGRESS_BAR_SEGMENT = "～ ";

    /**
     * 建構帶刪除線的進度條：每格為「～ 」、每格（含空格）都上色，整條有刪除線、視覺無空隙。
     * @param barLen 總格數
     * @param filled 已填滿格數（0～barLen）
     * @param filledColor 已填滿顏色
     * @param unfilledColor 未填滿顏色
     */
    public static Component buildStrikethroughProgressBar(int barLen, int filled, NamedTextColor filledColor, NamedTextColor unfilledColor) {
        Component out = Component.empty();
        int f = Math.min(Math.max(0, filled), barLen);
        for (int i = 0; i < barLen; i++) {
            out = out.append(Component.text(PROGRESS_BAR_SEGMENT, i < f ? filledColor : unfilledColor)
                .decoration(TextDecoration.STRIKETHROUGH, true));
        }
        return out;
    }

    private static final String[] CLASS_NAMES = {"治療師", "法師", "戰士", "弓箭手", "坦克"};

    /** 治療師技能：依等級回傳顯示字串。Lv1 基準：8格/1%、1.5x、25%/5秒/1.5%、2%/2秒 等 */
    private static final IntFunction<String>[] HEALER_SKILLS = new IntFunction[]{
        lvl -> (8 + (lvl - 1) / 5) + "格 / " + (1 + (lvl - 1) / 10) + "%",              // 治療光環
        lvl -> String.format(Locale.ROOT, "%.1fx", 1.5 + (lvl - 1) * 0.3),              // 生命共鳴 Lv1=1.5x, Lv2=1.8x
        lvl -> lvl <= 1 ? "25% / 5秒" : String.format(Locale.ROOT, "%d秒 / %.1f%%", 4 + lvl, 1.5 + (lvl - 1) * 0.05), // 鼓舞之愈
        lvl -> (2 + (lvl - 1) / 10) + "% / 2秒",                                        // 療癒圈
        lvl -> "20% / 25% / 10秒 / 120秒",                                              // 願望
        lvl -> "30格 / 0.5% / 5次 / 1%",                                                // 心連心
        lvl -> "50秒 / 1 玩家"                                                           // 復活
    };

    private static final String[] HEALER_SKILL_NAMES = {
        "治療光環", "生命共鳴", "鼓舞之愈", "療癒圈", "願望", "心連心", "復活"
    };

    /** 法師技能 */
    private static final IntFunction<String>[] MAGE_SKILLS = new IntFunction[]{
        lvl -> "", lvl -> "", lvl -> "", lvl -> ""
    };
    private static final String[] MAGE_SKILL_NAMES = {"法師法杖", "時間加速", "爆炸羊", "雷暴"};

    /** 戰士技能 */
    private static final IntFunction<String>[] WARRIOR_SKILLS = new IntFunction[]{
        lvl -> "", lvl -> "", lvl -> "", lvl -> "", lvl -> ""
    };
    private static final String[] WARRIOR_SKILL_NAMES = {"吸血", "血武器", "撕裂創口", "投擲斧", "狂戰怒吼"};

    /** 弓箭手技能 */
    private static final IntFunction<String>[] ARCHER_SKILLS = new IntFunction[]{
        lvl -> "", lvl -> "", lvl -> "", lvl -> "", lvl -> ""
    };
    private static final String[] ARCHER_SKILL_NAMES = {"彈跳箭", "雙倍射擊", "鷹眼聚焦", "爆炸射擊", "快速射擊"};

    /** 坦克技能 */
    private static final IntFunction<String>[] TANK_SKILLS = new IntFunction[]{
        lvl -> "", lvl -> "", lvl -> "", lvl -> ""
    };
    private static final String[] TANK_SKILL_NAMES = {"嘲諷", "堅若磐石", "我為人人", "挑釁怒吼"};

    @SuppressWarnings("unchecked")
    private static IntFunction<String>[] getSkills(int classIndex) {
        return switch (classIndex) {
            case 0 -> HEALER_SKILLS;
            case 1 -> MAGE_SKILLS;
            case 2 -> WARRIOR_SKILLS;
            case 3 -> ARCHER_SKILLS;
            case 4 -> TANK_SKILLS;
            default -> new IntFunction[0];
        };
    }

    private static String[] getSkillNames(int classIndex) {
        return switch (classIndex) {
            case 0 -> HEALER_SKILL_NAMES;
            case 1 -> MAGE_SKILL_NAMES;
            case 2 -> WARRIOR_SKILL_NAMES;
            case 3 -> ARCHER_SKILL_NAMES;
            case 4 -> TANK_SKILL_NAMES;
            default -> new String[0];
        };
    }

    private static Component formatSkillLine(String label, IntFunction<String> skill, int curLvl, int nextLevel) {
        int curForSkill = curLvl <= 0 ? 1 : curLvl;
        int nextForSkill = curLvl <= 0 ? 2 : (nextLevel > curLvl ? nextLevel : curForSkill);
        String curVal = skill.apply(curForSkill);
        String nextVal = (nextForSkill > curForSkill) ? skill.apply(nextForSkill) : null;
        Component lineComp;
        if (nextVal != null && !curVal.equals(nextVal)) {
            lineComp = Component.text(label, NamedTextColor.GRAY)
                .append(Component.text(curVal, NamedTextColor.WHITE))
                .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
                .append(Component.text(nextVal, NamedTextColor.YELLOW));
        } else {
            lineComp = Component.text(label + curVal, NamedTextColor.GRAY);
        }
        return lineComp.decoration(TextDecoration.ITALIC, false);
    }

    /** 建構職業箭的 Lore */
    public static List<Component> buildClassLore(int classIndex, long totalXP, boolean soloPlayer) {
        List<Component> lore = new ArrayList<>();
        if (classIndex < 0 || classIndex > 4) return lore;

        int currentLevel = DungeonLevelXP.getLevelFromCumulativeXP(totalXP);
        int curLvl = currentLevel;
        int nextLevel = Math.min(curLvl + 1, DungeonLevelXP.MAX_LEVEL);
        long xpCurrent = curLvl <= 0 ? 0 : DungeonLevelXP.getCumulativeXPForLevel(curLvl);
        long xpNext = DungeonLevelXP.getCumulativeXPForLevel(nextLevel);
        // 進度條區間：本級門檻～下一級門檻（50～125 = 0%～100%）
        long segmentStart = xpCurrent;
        long segmentEnd = xpNext;
        long xpNeededForLevel = segmentEnd - segmentStart;
        long xpInLevel = totalXP >= segmentEnd ? xpNeededForLevel
            : totalXP >= segmentStart ? totalXP - segmentStart : 0;
        if (curLvl >= DungeonLevelXP.MAX_LEVEL) {
            xpInLevel = xpNeededForLevel;
        }
        int pct = (xpNeededForLevel <= 0) ? 100 : (int) Math.min(100, (xpInLevel * 100 / xpNeededForLevel));

        IntFunction<String>[] skills = getSkills(classIndex);
        String[] names = getSkillNames(classIndex);

        if (classIndex == 0) {
            lore.add(Component.text("職業被動技能", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            int[] passiveIdx = {0, 1, 2};
            for (int i : passiveIdx) {
                if (i < names.length) {
                    lore.add(Component.text(" · " + names[i], NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                }
            }
            lore.add(Component.empty());
            lore.add(Component.text("洞窟主動技能", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            int[] activeIdx = {3, 4, 6};
            for (int i : activeIdx) {
                if (i < names.length) {
                    lore.add(Component.text(" · " + names[i], NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                }
            }
        } else if (classIndex == 1) {
            lore.add(Component.text("職業被動技能", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            int[] passiveIdx = {0, 1};
            for (int i : passiveIdx) {
                if (i < names.length) {
                    lore.add(Component.text(" · " + names[i], NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                }
            }
            lore.add(Component.empty());
            lore.add(Component.text("洞窟主動技能", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            int[] activeIdx = {2, 3};
            for (int i : activeIdx) {
                if (i < names.length) {
                    lore.add(Component.text(" · " + names[i], NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                }
            }
        } else if (classIndex == 2) {
            lore.add(Component.text("職業被動技能", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            int[] passiveIdx = {0, 1, 2};
            for (int i : passiveIdx) {
                if (i < names.length) {
                    lore.add(Component.text(" · " + names[i], NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                }
            }
            lore.add(Component.empty());
            lore.add(Component.text("洞窟主動技能", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            int[] activeIdx = {3, 4};
            for (int i : activeIdx) {
                if (i < names.length) {
                    lore.add(Component.text(" · " + names[i], NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                }
            }
        } else if (classIndex == 3) {
            lore.add(Component.text("職業被動技能", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            int[] passiveIdx = {0, 1, 2};
            for (int i : passiveIdx) {
                if (i < names.length) {
                    lore.add(Component.text(" · " + names[i], NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                }
            }
            lore.add(Component.empty());
            lore.add(Component.text("洞窟主動技能", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            int[] activeIdx = {3, 4};
            for (int i : activeIdx) {
                if (i < names.length) {
                    lore.add(Component.text(" · " + names[i], NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                }
            }
        } else if (classIndex == 4) {
            lore.add(Component.text("職業被動技能", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            int[] passiveIdx = {0, 1, 2};
            for (int i : passiveIdx) {
                if (i < names.length) {
                    lore.add(Component.text(" · " + names[i], NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                }
            }
            lore.add(Component.empty());
            lore.add(Component.text("洞窟主動技能", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            int[] activeIdx = {3};
            for (int i : activeIdx) {
                if (i < names.length) {
                    lore.add(Component.text(" · " + names[i], NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                }
            }
        }

        lore.add(Component.empty());
        String className = CLASS_NAMES[classIndex];
        lore.add(Component.text(className + "等級: ", NamedTextColor.DARK_GRAY)
            .append(Component.text(String.valueOf(curLvl), NamedTextColor.WHITE))
            .append(Component.text(" → ", NamedTextColor.GRAY))
            .append(Component.text(String.valueOf(nextLevel), NamedTextColor.GREEN))
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(className + "經驗: ", NamedTextColor.DARK_GRAY)
            .append(Component.text(formatNumber(xpCurrent), NamedTextColor.WHITE))
            .append(Component.text(" → ", NamedTextColor.GRAY))
            .append(Component.text(formatNumber(xpNext), NamedTextColor.YELLOW))
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        int barLen = 10;
        int filled = (int) Math.round(barLen * pct / 100.0);
        Component barComp = buildStrikethroughProgressBar(barLen, filled, NamedTextColor.YELLOW, NamedTextColor.DARK_GRAY)
            .append(Component.text(" ( ", NamedTextColor.DARK_GRAY))
            .append(Component.text(pct + "%", NamedTextColor.YELLOW))
            .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
            .append(Component.text(formatNumber(totalXP), NamedTextColor.GRAY))
            .append(Component.text(" )", NamedTextColor.DARK_GRAY));
        lore.add(barComp.decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("在洞窟中使用職業可進行升級", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("若只有一名玩家選擇職業，則所有屬性加成翻倍", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("點擊查看詳情！", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

        return lore;
    }

    /** 建構職業詳情頭顱（slot 45 寶珠）的 Lore */
    public static List<Component> buildClassDetailLore(int classIndex) {
        List<Component> lore = new ArrayList<>();
        if (classIndex == 0) {
            lore.add(Component.text("若只有一名玩家選擇職業，則所有屬性加成翻倍", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("- 治療光環 (被動)", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  被動治療半徑 ", NamedTextColor.DARK_GRAY).append(Component.text("8", NamedTextColor.WHITE)).append(Component.text(" 格內的所有存活隊友，", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  每秒恢復 ", NamedTextColor.DARK_GRAY).append(Component.text("1%", NamedTextColor.WHITE)).append(Component.text(" 生命值", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("- 生命共鳴 (被動)", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  你的任何主動治療技能命中目標時，", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  該次治療效果提升至 ", NamedTextColor.DARK_GRAY).append(Component.text("1.5", NamedTextColor.WHITE)).append(Component.text(" 倍", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("- 鼓舞之愈 (被動)", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  當你治療一名生命值低於 ", NamedTextColor.DARK_GRAY).append(Component.text("25%", NamedTextColor.WHITE)).append(Component.text(" 的隊友時，", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  其接下來 ", NamedTextColor.DARK_GRAY).append(Component.text("5秒", NamedTextColor.WHITE)).append(Component.text(" 內造成的傷害提升 ", NamedTextColor.DARK_GRAY)).append(Component.text("1.5%", NamedTextColor.WHITE)).append(Component.text("（不可疊加）", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("- 療癒圈 (主動)", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  發射一個治療球，球落地後會生成一個治療圈。", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  治療圈內友方玩家每秒恢復 ", NamedTextColor.DARK_GRAY).append(Component.text("2%", NamedTextColor.WHITE)).append(Component.text(" 的生命值", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(" 冷卻時間", NamedTextColor.GRAY).append(Component.text("2秒", NamedTextColor.WHITE)).append(Component.text("。此技能也可在潛行狀態下啟動", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("- 願望 (主動)", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  將隊伍中所有成員的生命值恢復至滿值，", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  並賦予他們相當於最大生命值 ", NamedTextColor.DARK_GRAY).append(Component.text("20-40%", NamedTextColor.WHITE)).append(Component.text(" 的護盾。", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  生命值低於 ", NamedTextColor.DARK_GRAY).append(Component.text("25%", NamedTextColor.WHITE)).append(Component.text(" 的玩家每有一個，冷卻時間減少", NamedTextColor.DARK_GRAY)).append(Component.text("10秒", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(" 冷卻時間 ", NamedTextColor.GRAY).append(Component.text("120秒", NamedTextColor.WHITE)).append(Component.text(" 。此技能也可在按下\"Q\"啟動", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("- 復活 (主動)", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  若有玩家死亡，每 ", NamedTextColor.DARK_GRAY).append(Component.text("50秒", NamedTextColor.WHITE)).append(Component.text(" 會復活 ", NamedTextColor.DARK_GRAY)).append(Component.text("1", NamedTextColor.WHITE)).append(Component.text(" 玩家", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false));
        } else if (classIndex == 1) {
            lore.add(Component.text("若只有一名玩家選擇職業，則所有屬性加成翻倍", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("- 法師法杖 (被動)", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  所有近戰武器攻擊都會變成遠程攻擊，這些攻擊的傷害為近戰傷害的 ", NamedTextColor.DARK_GRAY).append(Component.text("30%", NamedTextColor.WHITE)).append(Component.text("，有效距離為 ", NamedTextColor.DARK_GRAY)).append(Component.text("15", NamedTextColor.WHITE)).append(Component.text(" 格", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("- 時間加速 (被動)", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  所有技能冷卻時間縮短 10%", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("- 爆炸羊 (主動)", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  發射一隻爆炸羊，造成基於職業等級的傷害", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(" 冷卻時間 ", NamedTextColor.GRAY).append(Component.text("60", NamedTextColor.WHITE)).append(Component.text(" 秒。此技能需要左鍵武器", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("- 雷暴 (主動)", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  釋放雷電攻擊半徑 ", NamedTextColor.DARK_GRAY).append(Component.text("10", NamedTextColor.WHITE)).append(Component.text(" 格內的怪物，造成基於職業等級的傷害", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(" 冷卻時間 ", NamedTextColor.GRAY).append(Component.text("120", NamedTextColor.WHITE)).append(Component.text(" 秒。此技能也可以按下「Q」啟動", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
        } else if (classIndex == 2) {
            lore.add(Component.text("若只有一名玩家選擇職業，則所有屬性加成翻倍", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("- 吸血 (被動)", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  擊殺怪物後，你下一次攻擊會提高 ", NamedTextColor.DARK_GRAY).append(Component.text("20%", NamedTextColor.WHITE)).append(Component.text("，此狀態持續 ", NamedTextColor.DARK_GRAY)).append(Component.text("5", NamedTextColor.WHITE)).append(Component.text(" 秒，恢復你 ", NamedTextColor.DARK_GRAY)).append(Component.text("3%", NamedTextColor.WHITE)).append(Component.text(" 的已損失生命值", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("- 血武器 (被動)", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  每次擊中敵人，都會增加對敵人造成的傷害 ", NamedTextColor.DARK_GRAY).append(Component.text("10%", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("- 撕裂創口 (被動)", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  你的近戰攻擊有 ", NamedTextColor.DARK_GRAY).append(Component.text("10%", NamedTextColor.WHITE)).append(Component.text(" 機率使目標流血，", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  在 ", NamedTextColor.DARK_GRAY).append(Component.text("5", NamedTextColor.WHITE)).append(Component.text(" 秒內每秒造成本次傷害 ", NamedTextColor.DARK_GRAY)).append(Component.text("15%", NamedTextColor.WHITE)).append(Component.text(" 的持續傷害，", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  同一目標每 ", NamedTextColor.DARK_GRAY).append(Component.text("20", NamedTextColor.WHITE)).append(Component.text(" 秒只能觸發一次", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("- 投擲斧 (主動)", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  投擲斧頭，擊中怪物後會造成傷害。傷害來自你最後 ", NamedTextColor.DARK_GRAY).append(Component.text("1", NamedTextColor.WHITE)).append(Component.text(" 分鐘內的最高單次攻擊傷害。", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(" 冷卻時間 ", NamedTextColor.GRAY).append(Component.text("120", NamedTextColor.WHITE)).append(Component.text(" 秒。此技能可透過潛行觸發。", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("- 狂戰怒吼 (主動)", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  發出怒吼，使 ", NamedTextColor.DARK_GRAY).append(Component.text("5", NamedTextColor.WHITE)).append(Component.text(" 格內所有隊友獲得", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  ", NamedTextColor.DARK_GRAY).append(Component.text("5%", NamedTextColor.WHITE)).append(Component.text(" 傷害加成與 ", NamedTextColor.DARK_GRAY)).append(Component.text("5%", NamedTextColor.WHITE)).append(Component.text(" 傷害減免，持續 ", NamedTextColor.DARK_GRAY)).append(Component.text("5", NamedTextColor.WHITE)).append(Component.text(" 秒", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(" 冷卻時間 ", NamedTextColor.GRAY).append(Component.text("180", NamedTextColor.WHITE)).append(Component.text(" 秒。此技能也可以按下「Q」啟動", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
        } else if (classIndex == 3) {
            lore.add(Component.text("若只有一名玩家選擇職業，則所有屬性加成翻倍", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("- 彈跳箭 (被動)", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  箭矢有 ", NamedTextColor.DARK_GRAY).append(Component.text("0%", NamedTextColor.WHITE)).append(Component.text(" 的機率彈射到額外目標", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("- 雙倍射擊 (被動)", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  有 ", NamedTextColor.DARK_GRAY).append(Component.text("50%", NamedTextColor.WHITE)).append(Component.text(" 的機率射出第二支箭。", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("- 鷹眼聚焦 (被動)", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  站立不動超過 ", NamedTextColor.DARK_GRAY).append(Component.text("8", NamedTextColor.WHITE)).append(Component.text(" 秒後，進入聚焦狀態，箭矢傷害每 ", NamedTextColor.DARK_GRAY)).append(Component.text("1", NamedTextColor.WHITE)).append(Component.text(" 秒增加 ", NamedTextColor.DARK_GRAY)).append(Component.text("5%", NamedTextColor.WHITE)).append(Component.text("，最多疊加 ", NamedTextColor.DARK_GRAY)).append(Component.text("3", NamedTextColor.WHITE)).append(Component.text(" 層，移動後立即重置", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("- 爆炸射擊 (主動)", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  射出 ", NamedTextColor.DARK_GRAY).append(Component.text("1", NamedTextColor.WHITE)).append(Component.text(" 支箭，箭矢落地爆炸，對 ", NamedTextColor.DARK_GRAY)).append(Component.text("4", NamedTextColor.WHITE)).append(Component.text(" 格半徑範圍內的目標造成相當於你過去一分鐘內最高弓箭傷害的傷害。", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(" 冷卻時間 ", NamedTextColor.GRAY).append(Component.text("40", NamedTextColor.WHITE)).append(Component.text(" 秒。此技能可透過潛行觸發。", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("- 快速射擊 (主動)", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  每秒射出 ", NamedTextColor.DARK_GRAY).append(Component.text("2", NamedTextColor.WHITE)).append(Component.text(" 支箭，持續 ", NamedTextColor.DARK_GRAY)).append(Component.text("4", NamedTextColor.WHITE)).append(Component.text(" 秒。箭矢造成的傷害為過去一分鐘內最高弓箭傷害的 ", NamedTextColor.DARK_GRAY)).append(Component.text("30%", NamedTextColor.WHITE)).append(Component.text("。", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(" 冷卻時間 ", NamedTextColor.GRAY).append(Component.text("100", NamedTextColor.WHITE)).append(Component.text(" 秒。此技能也可以按下「Q」啟動", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
        } else if (classIndex == 4) {
            lore.add(Component.text("若只有一名玩家選擇職業，則所有屬性加成翻倍", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("- 嘲諷 (被動)", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  當你的生命值高於 ", NamedTextColor.DARK_GRAY).append(Component.text("50%", NamedTextColor.WHITE)).append(Component.text(" 時，增加怪物攻擊你的機率", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("- 堅若磐石 (被動)", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  當你持盾時，來自正面的傷害減少 ", NamedTextColor.DARK_GRAY).append(Component.text("15%", NamedTextColor.WHITE)).append(Component.text("，", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  來自背後的傷害增加 ", NamedTextColor.DARK_GRAY).append(Component.text("10%", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("- 我為人人 (被動)", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  半徑 ", NamedTextColor.DARK_GRAY).append(Component.text("5", NamedTextColor.WHITE)).append(Component.text(" 格內的隊友受到傷害時，", NamedTextColor.DARK_GRAY)).append(Component.text("80%", NamedTextColor.WHITE)).append(Component.text(" 的傷害將轉移至你身上", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("- 挑釁怒吼 (主動)", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  強制 ", NamedTextColor.DARK_GRAY).append(Component.text("5", NamedTextColor.WHITE)).append(Component.text(" 格內所有敵人攻擊你，持續 ", NamedTextColor.DARK_GRAY)).append(Component.text("4", NamedTextColor.WHITE)).append(Component.text(" 秒，", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  並使他們對你造成的傷害降低 ", NamedTextColor.DARK_GRAY).append(Component.text("15%", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(" 冷卻時間 ", NamedTextColor.GRAY).append(Component.text("120", NamedTextColor.WHITE)).append(Component.text(" 秒。此技能可透過潛行觸發。", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
        } else {
            String name = CLASS_NAMES[classIndex];
            lore.add(Component.text("若只有一名玩家選擇職業，則所有屬性加成翻倍", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text(name + " 技能詳情", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        return lore;
    }

    /** 治療師等級獎勵 Lore（用於等級格子）。僅顯示在該等級會升級的技能。 */
    public static List<Component> buildHealerLevelRewardsLore(int level) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("獎勵:", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(" + 500 楓葉幣", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));

        int step5 = level / 5;
        int step10 = level / 10;
        int prevStep5 = (level - 1) / 5;
        int prevStep10 = (level - 1) / 10;

        if (level % 5 == 0) {
            double cur = 1 + 0.1 * prevStep5;
            double next = 1 + 0.1 * step5;
            lore.add(Component.text(" + 治療光環: 每秒恢復 ", NamedTextColor.GRAY)
                .append(Component.text(String.format(Locale.ROOT, "%.1f%%", cur), NamedTextColor.WHITE))
                .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
                .append(Component.text(String.format(Locale.ROOT, "%.1f%%", next), NamedTextColor.YELLOW))
                .append(Component.text(" 生命值", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
        }

        double resonanceCur = 1.5 + 0.01 * (level - 1);
        double resonanceNext = 1.5 + 0.01 * level;
        lore.add(Component.text(" + 生命共鳴: 治療效果提升至 ", NamedTextColor.GRAY)
            .append(Component.text(String.format(Locale.ROOT, "%.2f", resonanceCur), NamedTextColor.WHITE))
            .append(Component.text("倍 → ", NamedTextColor.DARK_GRAY))
            .append(Component.text(String.format(Locale.ROOT, "%.2f", resonanceNext), NamedTextColor.YELLOW))
            .append(Component.text("倍", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));

        if (level % 5 == 0) {
            int thresholdCur = 25 + prevStep5;
            int thresholdNext = 25 + step5;
            double dmgCur = 1.1 + 0.05 * (step5 - 1);
            double dmgNext = 1.1 + 0.05 * step5;
            lore.add(Component.text(" + 鼓舞之愈: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("       治療一名生命值低於 ", NamedTextColor.GRAY)
                .append(Component.text(thresholdCur + "%", NamedTextColor.WHITE))
                .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
                .append(Component.text(thresholdNext + "%", NamedTextColor.YELLOW))
                .append(Component.text(" 的隊友時，", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("       接下來 5秒 內造成的傷害提升 ", NamedTextColor.GRAY)
                .append(Component.text(String.format(Locale.ROOT, "%.2f%%", dmgCur), NamedTextColor.WHITE))
                .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
                .append(Component.text(String.format(Locale.ROOT, "%.2f%%", dmgNext), NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
        }

        double circleHpCur = 2 + 0.1 * (level - 1);
        double circleHpNext = 2 + 0.1 * level;
        lore.add(Component.text(" + 療癒圈: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("       每秒恢復 ", NamedTextColor.GRAY)
            .append(Component.text(String.format(Locale.ROOT, "%.1f%%", circleHpCur), NamedTextColor.WHITE))
            .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
            .append(Component.text(String.format(Locale.ROOT, "%.1f%%", circleHpNext), NamedTextColor.YELLOW))
            .append(Component.text("的生命值，", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));

        double radiusCur = 4 + 0.5 * prevStep10;
        double radiusNext = 4 + 0.5 * step10;
        int durCur = 1 + prevStep5;
        int durNext = 1 + step5;
        if (radiusCur != radiusNext) {
            lore.add(Component.text("       療癒圈半徑 ", NamedTextColor.GRAY)
                .append(Component.text(String.format(Locale.ROOT, "%.1f", radiusCur), NamedTextColor.WHITE))
                .append(Component.text("格 → ", NamedTextColor.DARK_GRAY))
                .append(Component.text(String.format(Locale.ROOT, "%.1f", radiusNext), NamedTextColor.YELLOW))
                .append(Component.text("格 ，", NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("       療癒圈半徑 ", NamedTextColor.GRAY)
                .append(Component.text(String.format(Locale.ROOT, "%.1f", radiusCur), NamedTextColor.WHITE))
                .append(Component.text("格 ，", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
        }
        if (durCur != durNext) {
            lore.add(Component.text("       療癒圈持續時間 ", NamedTextColor.GRAY)
                .append(Component.text(durCur + "秒", NamedTextColor.WHITE))
                .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
                .append(Component.text(durNext + "秒", NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("       療癒圈持續時間 ", NamedTextColor.GRAY)
                .append(Component.text(durCur + "秒", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
        }

        double wishCur = 20 + 0.4 * (level - 1);
        double wishNext = 20 + 0.4 * level;
        lore.add(Component.text(" + 願望: 最大生命值 ", NamedTextColor.GRAY)
            .append(Component.text(String.format(Locale.ROOT, "%.1f%%", wishCur), NamedTextColor.WHITE))
            .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
            .append(Component.text(String.format(Locale.ROOT, "%.1f%%", wishNext), NamedTextColor.YELLOW))
            .append(Component.text(" 的護盾", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));

        return lore;
    }

    /** 法師等級獎勵 Lore。500 楓葉幣，法師法杖(每5等)、時間加速(每1等)。 */
    public static List<Component> buildMageLevelRewardsLore(int level) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("獎勵:", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(" + 500 楓葉幣", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(" + 法力值上限: +5", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        int step5 = level / 5;
        int step10 = level / 10;
        int prevStep5 = (level - 1) / 5;
        int prevStep10 = (level - 1) / 10;
        if (level % 5 == 0) {
            // 30%→40% 在 50 級內線性：每 5 等 +1%，Lv50 達 40%
            int dmgCur = Math.min(30 + (level - 1) / 5, 40);
            int dmgNext = Math.min(30 + level / 5, 40);
            lore.add(Component.text(" + 法師法杖: 傷害 ", NamedTextColor.GRAY)
                .append(Component.text(dmgCur + "%", NamedTextColor.WHITE))
                .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
                .append(Component.text(dmgNext + "%", NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
        }
        if (level % 10 == 0) {
            // 10→15 格在 50 級內：每 10 等 +1 格，Lv50 達 15 格
            int rangeCur = Math.min(10 + (level - 1) / 10, 15);
            int rangeNext = Math.min(10 + level / 10, 15);
            lore.add(Component.text(" + 法師法杖: 有效距離 ", NamedTextColor.GRAY)
                .append(Component.text(rangeCur + "格", NamedTextColor.WHITE))
                .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
                .append(Component.text(rangeNext + "格", NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
        }
        // 10%→30% 在 50 級內線性：Lv50 達 30%
        double speedCur = Math.min(10 + (30 - 10) * (level - 1) / (double) MAX_LVL, 30);
        double speedNext = Math.min(10 + (30 - 10) * level / (double) MAX_LVL, 30);
        lore.add(Component.text(" + 時間加速: 冷卻縮短 ", NamedTextColor.GRAY)
            .append(Component.text(String.format(Locale.ROOT, "%.1f%%", speedCur), NamedTextColor.WHITE))
            .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
            .append(Component.text(String.format(Locale.ROOT, "%.1f%%", speedNext), NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
        return lore;
    }

    /** 戰士等級獎勵 Lore。500 楓葉幣。 */
    public static List<Component> buildWarriorLevelRewardsLore(int level) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("獎勵:", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(" + 500 楓葉幣", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        int step5 = level / 5;
        int prevStep5 = (level - 1) / 5;
        if (level % 5 == 0) {
            // 20%→30% 在 50 級內：每 5 等 +1%，Lv50 達 30%
            int vampCur = Math.min(20 + (level - 1) / 5, 30);
            int vampNext = Math.min(20 + level / 5, 30);
            lore.add(Component.text(" + 吸血: 攻擊提高 ", NamedTextColor.GRAY)
                .append(Component.text(vampCur + "%", NamedTextColor.WHITE))
                .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
                .append(Component.text(vampNext + "%", NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
        }
        // 10%→30% 在 50 級內線性
        double bloodCur = Math.min(10 + (30 - 10) * (level - 1) / (double) MAX_LVL, 30);
        double bloodNext = Math.min(10 + (30 - 10) * level / (double) MAX_LVL, 30);
        lore.add(Component.text(" + 血武器: 傷害增加 ", NamedTextColor.GRAY)
            .append(Component.text(String.format(Locale.ROOT, "%.1f%%", bloodCur), NamedTextColor.WHITE))
            .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
            .append(Component.text(String.format(Locale.ROOT, "%.1f%%", bloodNext), NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
        if (level % 5 == 0) {
            // 觸發 10→30%、持續傷害 15→30%、冷卻 20→15 秒，每 5 等一階，Lv50 達標
            int riftPctCur = Math.min(10 + 2 * prevStep5, 30);
            int riftPctNext = Math.min(10 + 2 * step5, 30);
            int riftDotCur = Math.min(15 + (3 * prevStep5) / 2, 30);
            int riftDotNext = Math.min(15 + (3 * step5) / 2, 30);
            int riftCdCur = Math.max(20 - prevStep5 / 2, 15);
            int riftCdNext = Math.max(20 - step5 / 2, 15);
            lore.add(Component.text(" + 撕裂創口: 觸發 ", NamedTextColor.GRAY)
                .append(Component.text(riftPctCur + "%", NamedTextColor.WHITE))
                .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
                .append(Component.text(riftPctNext + "%", NamedTextColor.YELLOW))
                .append(Component.text("，持續傷害 ", NamedTextColor.GRAY))
                .append(Component.text(riftDotCur + "%", NamedTextColor.WHITE))
                .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
                .append(Component.text(riftDotNext + "%", NamedTextColor.YELLOW))
                .append(Component.text("，同一目標 ", NamedTextColor.GRAY))
                .append(Component.text(riftCdCur + "秒", NamedTextColor.WHITE))
                .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
                .append(Component.text(riftCdNext + "秒", NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
        }
        double radiusCur = Math.min(5 + 0.5 * (level - 1), 30);
        double radiusNext = Math.min(5 + 0.5 * level, 30);
        double dmgCur = Math.min(5 + 0.3 * (level - 1), 20);
        double dmgNext = Math.min(5 + 0.3 * level, 20);
        double redCur = Math.min(5 + 0.2 * (level - 1), 15);
        double redNext = Math.min(5 + 0.2 * level, 15);
        double durCur = Math.min(5 + 0.5 * (level - 1), 30);
        double durNext = Math.min(5 + 0.5 * level, 30);
        lore.add(Component.text(" + 狂戰怒吼: ", NamedTextColor.GRAY)
            .append(Component.text(String.format(Locale.ROOT, "%.1f", radiusCur), NamedTextColor.WHITE))
            .append(Component.text("格 → ", NamedTextColor.DARK_GRAY))
            .append(Component.text(String.format(Locale.ROOT, "%.1f", radiusNext), NamedTextColor.YELLOW))
            .append(Component.text("格，", NamedTextColor.GRAY))
            .append(Component.text(String.format(Locale.ROOT, "%.1f%%", dmgCur), NamedTextColor.WHITE))
            .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
            .append(Component.text(String.format(Locale.ROOT, "%.1f%%", dmgNext), NamedTextColor.YELLOW))
            .append(Component.text(" 傷害加成，", NamedTextColor.GRAY))
            .append(Component.text(String.format(Locale.ROOT, "%.1f%%", redCur), NamedTextColor.WHITE))
            .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
            .append(Component.text(String.format(Locale.ROOT, "%.1f%%", redNext), NamedTextColor.YELLOW))
            .append(Component.text(" 減免，持續 ", NamedTextColor.GRAY))
            .append(Component.text(String.format(Locale.ROOT, "%.1f", durCur), NamedTextColor.WHITE))
            .append(Component.text("秒 → ", NamedTextColor.DARK_GRAY))
            .append(Component.text(String.format(Locale.ROOT, "%.1f", durNext), NamedTextColor.YELLOW))
            .append(Component.text("秒", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
        return lore;
    }

    /** 弓箭手等級獎勵 Lore。500 楓葉幣。 */
    public static List<Component> buildArcherLevelRewardsLore(int level) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("獎勵:", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(" + 500 楓葉幣", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        int step5 = level / 5;
        int prevStep5 = (level - 1) / 5;
        double bounceCur = Math.min(2 * (level - 1), 100);
        double bounceNext = Math.min(2 * level, 100);
        lore.add(Component.text(" + 彈跳箭: 彈射機率 ", NamedTextColor.GRAY)
            .append(Component.text(String.format(Locale.ROOT, "%.0f%%", bounceCur), NamedTextColor.WHITE))
            .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
            .append(Component.text(String.format(Locale.ROOT, "%.0f%%", bounceNext), NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
        if (level % 5 == 0) {
            // 50%→100% 每 5 等一階，Lv50 達 100%
            int doubleCur = Math.min(50 + 5 * prevStep5, 100);
            int doubleNext = Math.min(50 + 5 * step5, 100);
            lore.add(Component.text(" + 雙倍射擊: 機率 ", NamedTextColor.GRAY)
                .append(Component.text(doubleCur + "%", NamedTextColor.WHITE))
                .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
                .append(Component.text(doubleNext + "%", NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
        }
        if (level % 5 == 0) {
            // 鷹眼聚焦：站立 8→3 秒、傷害 5→10%、層數 3→5，每 5 等一階，Lv50 達標
            double standCur = Math.max(8 - (8 - 3) * (level - 1) / (double) MAX_LVL, 3);
            double standNext = Math.max(8 - (8 - 3) * level / (double) MAX_LVL, 3);
            double focusCur = Math.min(5 + 5 * prevStep5 / 10.0, 10);
            double focusNext = Math.min(5 + 5 * step5 / 10.0, 10);
            int stackCur = Math.min(3 + prevStep5 / 5, 5);
            int stackNext = Math.min(3 + step5 / 5, 5);
            lore.add(Component.text(" + 鷹眼聚焦: 站立 ", NamedTextColor.GRAY)
                .append(Component.text(String.format(Locale.ROOT, "%.1f", standCur), NamedTextColor.WHITE))
                .append(Component.text("秒 → ", NamedTextColor.DARK_GRAY))
                .append(Component.text(String.format(Locale.ROOT, "%.1f", standNext), NamedTextColor.YELLOW))
                .append(Component.text("秒，傷害 ", NamedTextColor.GRAY))
                .append(Component.text(String.format(Locale.ROOT, "%.1f%%", focusCur), NamedTextColor.WHITE))
                .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
                .append(Component.text(String.format(Locale.ROOT, "%.1f%%", focusNext), NamedTextColor.YELLOW))
                .append(Component.text("，最多 ", NamedTextColor.GRAY))
                .append(Component.text(stackCur + "層", NamedTextColor.WHITE))
                .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
                .append(Component.text(stackNext + "層", NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
        }
        // 1→3 支箭在 50 級內：Lv25 約 2 支、Lv50 達 3 支
        int arrowsCur = Math.min(1 + (level - 1) / 25, 3);
        int arrowsNext = Math.min(1 + level / 25, 3);
        lore.add(Component.text(" + 爆炸射擊: ", NamedTextColor.GRAY)
            .append(Component.text(arrowsCur + "支箭", NamedTextColor.WHITE))
            .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
            .append(Component.text(arrowsNext + "支箭", NamedTextColor.YELLOW))
            .append(Component.text("，4格半徑", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
        // 快速射擊：2→5 支箭、4→8 秒、30%→75%，Lv50 達標
        int fastArrCur = Math.min(2 + (level - 1) * 3 / MAX_LVL, 5);
        int fastArrNext = Math.min(2 + level * 3 / MAX_LVL, 5);
        double fastDurCur = Math.min(4 + (8 - 4) * (level - 1) / (double) MAX_LVL, 8);
        double fastDurNext = Math.min(4 + (8 - 4) * level / (double) MAX_LVL, 8);
        double fastPctCur = Math.min(30 + (75 - 30) * (level - 1) / (double) MAX_LVL, 75);
        double fastPctNext = Math.min(30 + (75 - 30) * level / (double) MAX_LVL, 75);
        lore.add(Component.text(" + 快速射擊: ", NamedTextColor.GRAY)
            .append(Component.text(fastArrCur + "支箭", NamedTextColor.WHITE))
            .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
            .append(Component.text(fastArrNext + "支箭", NamedTextColor.YELLOW))
            .append(Component.text("，", NamedTextColor.GRAY))
            .append(Component.text(String.format(Locale.ROOT, "%.1f", fastDurCur), NamedTextColor.WHITE))
            .append(Component.text("秒 → ", NamedTextColor.DARK_GRAY))
            .append(Component.text(String.format(Locale.ROOT, "%.1f", fastDurNext), NamedTextColor.YELLOW))
            .append(Component.text("秒，傷害 ", NamedTextColor.GRAY))
            .append(Component.text(String.format(Locale.ROOT, "%.0f%%", fastPctCur), NamedTextColor.WHITE))
            .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
            .append(Component.text(String.format(Locale.ROOT, "%.0f%%", fastPctNext), NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
        return lore;
    }

    /** 坦克等級獎勵 Lore。500 楓葉幣。嘲諷為減法。 */
    public static List<Component> buildTankLevelRewardsLore(int level) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("獎勵:", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(" + 500 楓葉幣", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        int step10 = level / 10;
        int prevStep10 = (level - 1) / 10;
        int step5 = level / 5;
        int prevStep5 = (level - 1) / 5;
        double tauntCur = Math.max(50 - 0.5 * (level - 1), 25);
        double tauntNext = Math.max(50 - 0.5 * level, 25);
        lore.add(Component.text(" + 嘲諷: 生命值高於 ", NamedTextColor.GRAY)
            .append(Component.text(String.format(Locale.ROOT, "%.0f%%", tauntCur), NamedTextColor.WHITE))
            .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
            .append(Component.text(String.format(Locale.ROOT, "%.0f%%", tauntNext), NamedTextColor.YELLOW))
            .append(Component.text(" 時增加怪物攻擊你的機率", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
        if (level % 5 == 0) {
            // 15%→25% 每 5 等一階，Lv50 達 25%
            int frontCur = Math.min(15 + (level - 1) / 5, 25);
            int frontNext = Math.min(15 + level / 5, 25);
            lore.add(Component.text(" + 堅若磐石: 正面傷害減少 ", NamedTextColor.GRAY)
                .append(Component.text(frontCur + "%", NamedTextColor.WHITE))
                .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
                .append(Component.text(frontNext + "%", NamedTextColor.YELLOW))
                .append(Component.text("，背後傷害增加 10%", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
        }
        // 5→20 格在 50 級內線性
        double radiusCur = Math.min(5 + (20 - 5) * (level - 1) / (double) MAX_LVL, 20);
        double radiusNext = Math.min(5 + (20 - 5) * level / (double) MAX_LVL, 20);
        lore.add(Component.text(" + 我為人人: 半徑 ", NamedTextColor.GRAY)
            .append(Component.text(String.format(Locale.ROOT, "%.1f", radiusCur), NamedTextColor.WHITE))
            .append(Component.text("格 → ", NamedTextColor.DARK_GRAY))
            .append(Component.text(String.format(Locale.ROOT, "%.1f", radiusNext), NamedTextColor.YELLOW))
            .append(Component.text("格", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
        if (level % 10 == 0) {
            // 挑釁怒吼：5→10 格、4→8 秒、15%→30%，每 10 等一階，Lv50 達標
            int radCur = Math.min(5 + (level - 1) / 10, 10);
            int radNext = Math.min(5 + level / 10, 10);
            double durCur = Math.min(4 + 0.8 * prevStep10, 8);
            double durNext = Math.min(4 + 0.8 * step10, 8);
            int redCur = Math.min(15 + 3 * prevStep10, 30);
            int redNext = Math.min(15 + 3 * step10, 30);
            lore.add(Component.text(" + 挑釁怒吼: ", NamedTextColor.GRAY)
                .append(Component.text(radCur + "格", NamedTextColor.WHITE))
                .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
                .append(Component.text(radNext + "格", NamedTextColor.YELLOW))
                .append(Component.text("，", NamedTextColor.GRAY))
                .append(Component.text(String.format(Locale.ROOT, "%.1f", durCur), NamedTextColor.WHITE))
                .append(Component.text("秒 → ", NamedTextColor.DARK_GRAY))
                .append(Component.text(String.format(Locale.ROOT, "%.1f", durNext), NamedTextColor.YELLOW))
                .append(Component.text("秒，傷害降低 ", NamedTextColor.GRAY))
                .append(Component.text(redCur + "%", NamedTextColor.WHITE))
                .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
                .append(Component.text(redNext + "%", NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
        }
        return lore;
    }

    /** 地底洞窟「來自洞窟的物品提升」總百分比。Lv0=10%，Lv1=14%，Lv2=18%…，Lv50 總計 475% */
    public static int getCaveBonusPercent(int level) {
        if (level <= 0) return 10;
        int total = 10;
        for (int l = 1; l <= Math.min(level, 50); l++) {
            int add = l <= 5 ? 4 : l <= 10 ? 5 : l <= 15 ? 6 : l <= 20 ? 7 : l <= 25 ? 8
                : l <= 30 ? 9 : l <= 35 ? 10 : l <= 40 ? 12 : l <= 45 ? 14
                : l == 46 ? 16 : l == 47 ? 17 : l == 48 ? 18 : l == 49 ? 19 : 20;
            total += add;
        }
        return total;
    }

    /** 建構地底洞窟頭顱的 Lore */
    public static List<Component> buildCaveLevelLore(long totalXP) {
        List<Component> lore = new ArrayList<>();
        int currentLevel = DungeonLevelXP.getLevelFromCumulativeXP(totalXP);
        int curLvl = currentLevel;
        int nextLevel = Math.min(curLvl + 1, DungeonLevelXP.MAX_LEVEL);
        long xpCurrent = curLvl <= 0 ? 0 : DungeonLevelXP.getCumulativeXPForLevel(curLvl);
        long xpNext = DungeonLevelXP.getCumulativeXPForLevel(nextLevel);
        // 進度條區間：本級門檻～下一級門檻（例 50～125 = 0%～100%），50 XP 時為 0%
        long segmentStart = xpCurrent;
        long segmentEnd = xpNext;
        long xpNeededForLevel = segmentEnd - segmentStart;
        long xpInLevel = totalXP >= segmentEnd ? xpNeededForLevel
            : totalXP >= segmentStart ? totalXP - segmentStart : 0;
        if (curLvl >= DungeonLevelXP.MAX_LEVEL) {
            xpInLevel = xpNeededForLevel;
        }
        int pct = (xpNeededForLevel <= 0) ? 100 : (int) Math.min(100, (xpInLevel * 100 / xpNeededForLevel));

        int curPercent = curLvl <= 0 ? 10 : getCaveBonusPercent(curLvl);
        int nextPercent = getCaveBonusPercent(nextLevel);

        lore.add(Component.empty());
        lore.add(Component.text("提升你的基礎屬性", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("來自洞窟的物品提升 ", NamedTextColor.GRAY)
            .append(Component.text(curPercent + "%", NamedTextColor.WHITE))
            .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
            .append(Component.text(nextPercent + "%", NamedTextColor.YELLOW))
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("地底洞窟等級: ", NamedTextColor.DARK_GRAY)
            .append(Component.text(String.valueOf(curLvl <= 0 ? 0 : curLvl), NamedTextColor.WHITE))
            .append(Component.text(" → ", NamedTextColor.GRAY))
            .append(Component.text(String.valueOf(nextLevel), NamedTextColor.GREEN))
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("地底洞窟經驗: ", NamedTextColor.DARK_GRAY)
            .append(Component.text(formatNumber(xpCurrent), NamedTextColor.WHITE))
            .append(Component.text(" → ", NamedTextColor.GRAY))
            .append(Component.text(formatNumber(xpNext), NamedTextColor.YELLOW))
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        int barLen = 10;
        int filled = (int) Math.round(barLen * pct / 100.0);
        Component barComp = buildStrikethroughProgressBar(barLen, filled, NamedTextColor.YELLOW, NamedTextColor.DARK_GRAY)
            .append(Component.text(" ( ", NamedTextColor.DARK_GRAY))
            .append(Component.text(pct + "%", NamedTextColor.YELLOW))
            .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
            .append(Component.text(formatNumber(totalXP), NamedTextColor.GRAY))
            .append(Component.text(" )", NamedTextColor.DARK_GRAY));
        lore.add(barComp.decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("點擊查看詳情！", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

        return lore;
    }

    public static String formatNumber(long n) {
        if (n >= 1_000_000_000) return String.format(Locale.ROOT, "%.1fB", n / 1e9);
        if (n >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", n / 1e6);
        if (n >= 1_000) return String.format(Locale.ROOT, "%.0fK", n / 1e3);
        return String.valueOf(n);
    }

    // ---- Runtime formulas shared with listener (sync with lore) ----
    public static int clampLevel(int level) { return Math.min(Math.max(1, level), MAX_LVL); }
    public static int step5(int level) { return clampLevel(level) / 5; }
    public static int step10(int level) { return clampLevel(level) / 10; }

    // Healer
    public static double healerAuraRadius(int lvl) { return 8 + (clampLevel(lvl) - 1) / 5.0; }
    public static double healerAuraHealPctPerSec(int lvl) { return 1 + (clampLevel(lvl) - 1) / 10.0; }
    public static double healerCircleHealPctPerSec(int lvl) { return 2 + 0.1 * clampLevel(lvl); }
    public static double healerCircleRadius(int lvl) { return 4 + 0.5 * step10(lvl); }
    public static int healerCircleDurationSec(int lvl) { return 1 + step5(lvl); }
    public static double healerWishShieldPct(int lvl) { return Math.min(20 + 0.4 * clampLevel(lvl), 40); }

    // Mage
    public static int mageStaffDamagePercent(int lvl) { return Math.min(30 + step5(lvl), 40); }
    public static int mageStaffRange(int lvl) { return Math.min(10 + step10(lvl), 15); }
    public static double mageTimeAccelPercent(int lvl) {
        int l = clampLevel(lvl);
        return Math.min(10 + (30 - 10) * l / (double) MAX_LVL, 30);
    }

    // Warrior
    public static int warriorVampPercent(int lvl) { return Math.min(20 + step5(lvl), 30); }
    public static double warriorBloodPercent(int lvl) {
        int l = clampLevel(lvl);
        return Math.min(10 + (30 - 10) * l / (double) MAX_LVL, 30);
    }
    public static int warriorRiftChancePercent(int lvl) { return Math.min(10 + 2 * step5(lvl), 30); }
    public static int warriorRiftDotPercent(int lvl) { return Math.min(15 + (3 * step5(lvl)) / 2, 30); }
    public static int warriorRiftCooldownSec(int lvl) { return Math.max(20 - step5(lvl) / 2, 15); }
    public static double warriorShoutRadius(int lvl) { return Math.min(5 + 0.5 * clampLevel(lvl), 30); }
    public static double warriorShoutDamageBuffPercent(int lvl) { return Math.min(5 + 0.3 * clampLevel(lvl), 20); }
    public static double warriorShoutDamageReducePercent(int lvl) { return Math.min(5 + 0.2 * clampLevel(lvl), 15); }
    public static double warriorShoutDurationSec(int lvl) { return Math.min(5 + 0.5 * clampLevel(lvl), 30); }

    // Archer
    public static int archerBounceChancePercent(int lvl) { return (int) Math.min(2 * clampLevel(lvl), 100); }
    public static int archerDoubleShotChancePercent(int lvl) { return Math.min(50 + 5 * step5(lvl), 100); }
    public static double archerFocusStandSec(int lvl) {
        int l = clampLevel(lvl);
        return Math.max(8 - (8 - 3) * l / (double) MAX_LVL, 3);
    }
    public static double archerFocusDamagePerStackPercent(int lvl) {
        return Math.min(5 + 5 * step5(lvl) / 10.0, 10);
    }
    public static int archerFocusMaxStacks(int lvl) { return Math.min(3 + step5(lvl) / 5, 5); }
    public static int archerExplosiveArrowCount(int lvl) { return Math.min(1 + clampLevel(lvl) / 25, 3); }
    public static int archerRapidArrowCount(int lvl) { return Math.min(2 + clampLevel(lvl) * 3 / MAX_LVL, 5); }
    public static double archerRapidDurationSec(int lvl) {
        int l = clampLevel(lvl);
        return Math.min(4 + (8 - 4) * l / (double) MAX_LVL, 8);
    }
    public static double archerRapidDamagePercent(int lvl) {
        int l = clampLevel(lvl);
        return Math.min(30 + (75 - 30) * l / (double) MAX_LVL, 75);
    }

    // Tank
    public static double tankTauntHealthThresholdPercent(int lvl) {
        return Math.max(50 - 0.5 * (clampLevel(lvl) - 1), 25);
    }
    public static int tankFrontReducePercent(int lvl) { return Math.min(15 + step5(lvl), 25); }
    public static double tankRedirectRadius(int lvl) {
        int l = clampLevel(lvl);
        return Math.min(5 + (20 - 5) * l / (double) MAX_LVL, 20);
    }
    public static int tankProvokeRadius(int lvl) { return Math.min(5 + step10(lvl), 10); }
    public static double tankProvokeDurationSec(int lvl) { return Math.min(4 + 0.8 * step10(lvl), 8); }
    public static int tankProvokeDamageReducePercent(int lvl) { return Math.min(15 + 3 * step10(lvl), 30); }
}
