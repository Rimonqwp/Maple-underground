# -*- coding: utf-8 -*-
"""產生附魔列表 Excel (.xlsx)。需先安裝: pip install openpyxl
執行: python generate_enchantments_xlsx.py
"""
import os
import sys

try:
    from openpyxl import Workbook
    from openpyxl.styles import Font, Alignment, PatternFill
except ImportError:
    print("請先安裝 openpyxl: pip install openpyxl")
    sys.exit(1)

HEADERS = ["附魔ID", "名字", "属性", "最大等級", "是否可在鐵砧升級", "類型", "適用裝備"]

NO_ANVIL_IDS = {"cave_blessing", "all_in", "collection_bonus", "kill_heal"}
ANVIL_LEVEL_CAP = 5

def anvil_upgrade_label(max_level, enchant_id):
    if enchant_id in NO_ANVIL_IDS:
        return "否"
    if int(max_level) <= ANVIL_LEVEL_CAP:
        return "是（可達滿級）"
    return "是（鐵砧最高%d級）" % ANVIL_LEVEL_CAP

# 先依「類型」分組：終極附魔 → 普通附魔，同類型內依名字排序
def _row_key(r):
    typ = r[5]   # 類型
    name = r[1] # 名字
    return (0 if typ == "終極附魔" else 1, name)

# 每行: 附魔ID, 名字, 属性, 最大等級, 是否可在鐵砧升級, 類型, 適用裝備
_ROWS = [
    ["combo", "連擊", "連續攻擊目標每次攻擊傷害增加，5秒未攻擊重置。每層+0.2%~0.5%，最多2/3/5/7/10層", "5", anvil_upgrade_label(5, "combo"), "終極附魔", "劍"],
    ["deadwood_revival", "枯木逢春", "最脆弱的木棍也能斬斷鋼鐵。+2/+3/+4/+5/+6 點傷害", "5", anvil_upgrade_label(5, "deadwood_revival"), "終極附魔", "劍"],
    ["fortune_coins", "招財進寶", "擊殺敵人掉落 0.5/1/1.5/2/2.5 金幣", "5", anvil_upgrade_label(5, "fortune_coins"), "終極附魔", "劍"],
    ["infinite_quiver", "無限箭袋", "射箭時 4%~40% 機率不消耗箭矢（每級+4%）", "10", anvil_upgrade_label(10, "infinite_quiver"), "普通附魔", "弓"],
    ["feather_wing", "羽毛翅膀", "減少摔落傷害 3%~30%，基礎免疫機率 0%~2.5%，高度免疫加成 4~8 格", "10", anvil_upgrade_label(10, "feather_wing"), "普通附魔", "鞋子"],
    ["cave_blessing", "洞窟祝福", "身處世界時每 30/25/20/18/12 秒 +1 生命值恢復", "5", anvil_upgrade_label(5, "cave_blessing"), "普通附魔", "裝備"],
    ["no_pain_no_gain", "不勞無獲", "每 10 次被怪物擊中時有 5%/15%/25%/35%/45% 機率獲得經驗球", "5", anvil_upgrade_label(5, "no_pain_no_gain"), "終極附魔", "裝備"],
    ["wisdom", "智慧", "每 5 經驗等級提升 1~5 點智力，上限 20~100 點", "5", anvil_upgrade_label(5, "wisdom"), "終極附魔", "裝備"],
    ["thrift", "節流", "降低該物品技能 10%~50% 的法力消耗量", "5", anvil_upgrade_label(5, "thrift"), "終極附魔", "武器"],
    ["all_in", "孤注一擲", "移除武器上所有其他附魔，傷害提高 500%", "1", anvil_upgrade_label(1, "all_in"), "終極附魔", "武器"],
    ["last_stand", "背水一戰", "生命值低於 35% 時受擊獲得額外 +5%~+25% 防禦力", "5", anvil_upgrade_label(5, "last_stand"), "終極附魔", "裝備"],
    ["armor_break", "碎甲", "攻擊時 0.3%~1.5% 機率無視目標防禦造成真實傷害", "5", anvil_upgrade_label(5, "armor_break"), "終極附魔", "劍/弓"],
    ["kill_legacy", "獵殺傳承", "武器獲得最近擊殺怪物的 1.5~4 倍傷害，下次暴擊時加上該傷害", "5", anvil_upgrade_label(5, "kill_legacy"), "終極附魔", "劍/弓"],
    ["companion_resonance", "伴生共鳴", "複製目前寵物的 20%~100% 屬性到自己身上", "5", anvil_upgrade_label(5, "companion_resonance"), "終極附魔", "劍"],
    ["combo_storm", "連擊風暴", "每次攻擊增加連擊層數，每層 +10%~+50% 傷害，最後一次攻擊後 3 秒內傷害上限 200%", "5", anvil_upgrade_label(5, "combo_storm"), "終極附魔", "劍"],
    ["lone_fighter", "孤軍奮戰", "每 10 格範圍內一名敵人傷害提高 +2%~+10%", "5", anvil_upgrade_label(5, "lone_fighter"), "終極附魔", "劍/弓"],
    ["ember_chase", "餘火追擊", "射擊時額外一箭造成主箭 3%~15% 傷害，目標受 1.1~1.5 倍持續火焰傷害 60 秒", "5", anvil_upgrade_label(5, "ember_chase"), "終極附魔", "弓"],
    ["war_aura", "征戰光環", "提升 +0.07%~+0.35% 所有戰鬥屬性，周圍 30 格內每位玩家 +0.07%~+0.35% 稀有掉落（最多 20 人）", "5", anvil_upgrade_label(5, "war_aura"), "終極附魔", "裝備"],
    ["death_pennies", "斂財", "死亡時節省 10%~50% 金幣", "5", anvil_upgrade_label(5, "death_pennies"), "終極附魔", "裝備"],
    ["collection_bonus", "元气小收藏", "每完成一個滿級收藏獲得 +0.1/+0.2 生命和力量", "2", anvil_upgrade_label(2, "collection_bonus"), "終極附魔", "飾品"],
    ["spider_killer", "蛛蛛殺手", "對蜘蛛、洞穴蜘蛛和蠹魚傷害增加 +2%~+14%（每級+2%）", "7", anvil_upgrade_label(7, "spider_killer"), "普通附魔", "劍"],
    ["mercenary", "傭兵生涯", "額外戰鬥經驗 +3%~+10%，連續攻擊賺取金幣與經驗球，累積戰鬥經驗可升級", "10", anvil_upgrade_label(10, "mercenary"), "普通附魔", "劍"],
    ["critical_strike", "致命一擊", "暴擊傷害 +10%~+50%", "5", anvil_upgrade_label(5, "critical_strike"), "普通附魔", "劍"],
    ["lucky", "幸運兒", "稀有物品掉落率 +1%~+5%", "5", anvil_upgrade_label(5, "lucky"), "普通附魔", "劍"],
    ["dragon_slayer", "屠龍者", "對終界龍傷害提升 +5%~+25%", "5", anvil_upgrade_label(5, "dragon_slayer"), "普通附魔", "劍/弓"],
    ["ender_slayer", "終界殺手", "對終界使者、終界蟎和終界龍傷害提升 +15%~+130%", "7", anvil_upgrade_label(7, "ender_slayer"), "普通附魔", "劍"],
    ["twin", "雙子附魔", "擊殺怪物時有 12.5%/25%/37.5%/50%/62.5% 機率獲得雙倍經驗值", "5", anvil_upgrade_label(5, "twin"), "普通附魔", "劍/鎬"],
    ["surprise_strike", "奇襲", "對尚未對你造成過傷害的怪物第一次近戰攻擊傷害 +10%~50%", "5", anvil_upgrade_label(5, "surprise_strike"), "普通附魔", "劍"],
    ["challenger", "挑戰者", "目標生命每比你高 5%/4.5%/4%/3.5%/3% 傷害 +0.1%~1.2%", "5", anvil_upgrade_label(5, "challenger"), "普通附魔", "劍"],
    ["overwhelming", "排山倒海", "攻擊 15%/30%/50% 機率強力擊退 3/4.5/6 格", "3", anvil_upgrade_label(3, "overwhelming"), "普通附魔", "劍"],
    ["life_steal", "生命汲取", "每次攻擊怪物恢復最大生命 0.5%~2.5%", "5", anvil_upgrade_label(5, "life_steal"), "普通附魔", "劍"],
    ["armor_shred", "剝甲專家", "擊殺穿盔甲怪物時盔甲掉落機率 +5%~35%", "5", anvil_upgrade_label(5, "armor_shred"), "普通附魔", "劍"],
    ["mana_steal", "法力汲取", "每次擊中回復最大法力 0.25%~0.75%", "5", anvil_upgrade_label(5, "mana_steal"), "普通附魔", "劍"],
    ["execute", "斬殺", "目標每失去 1% 生命傷害 +0.1%~1%", "5", anvil_upgrade_label(5, "execute"), "普通附魔", "劍"],
    ["bounty_hunter", "賞金獵人", "擊殺怪物獲得 0.3~1.8 枚金幣", "5", anvil_upgrade_label(5, "bounty_hunter"), "普通附魔", "劍"],
    ["blood_crit", "鮮血暴擊", "暴擊傷害每 100 點治療最大生命 0.2%~0.6%，最多 1000 點", "5", anvil_upgrade_label(5, "blood_crit"), "普通附魔", "劍"],
    ["armor_break_combo", "碎甲連擊", "每次命中降低目標防禦 1.2%~9% 持續 4 秒，最多 4 層", "5", anvil_upgrade_label(5, "armor_break_combo"), "普通附魔", "劍"],
    ["lone_wolf", "孤狼", "無寵物時 +2/2.5/3 武器傷害", "3", anvil_upgrade_label(3, "lone_wolf"), "普通附魔", "劍"],
    ["thunder_chain", "雷霆連擊", "每第 3 次擊中召喚閃電，周圍 2 格最多 10 怪受 4%~30% 傷害", "5", anvil_upgrade_label(5, "thunder_chain"), "普通附魔", "劍"],
    ["thunder_god_chain", "雷神連擊", "每第 3 次擊中召喚閃電，目標額外受 8%~60% 傷害", "5", anvil_upgrade_label(5, "thunder_god_chain"), "普通附魔", "劍"],
    ["wild_fury", "狂野之怒", "獲得 +3/4/5 combo", "3", anvil_upgrade_label(3, "wild_fury"), "普通附魔", "劍"],
    ["kill_heal", "殺戮回春", "擊殺敵人時恢復已損失生命 1%~6%", "5", anvil_upgrade_label(5, "kill_heal"), "普通附魔", "劍"],
    ["first_strike", "先發制人", "對同一怪物前 3 次近戰傷害 +10%~50%", "5", anvil_upgrade_label(5, "first_strike"), "普通附魔", "劍"],
]
ROWS = sorted(_ROWS, key=_row_key)

def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    out_path = os.path.join(project_root, "src", "main", "resources", "enchantments_list.xlsx")
    os.makedirs(os.path.dirname(out_path), exist_ok=True)

    wb = Workbook()
    ws = wb.active
    ws.title = "附魔列表"

    # 標題列
    for c, h in enumerate(HEADERS, 1):
        cell = ws.cell(row=1, column=c, value=h)
        cell.font = Font(bold=True)
        cell.alignment = Alignment(horizontal="center", wrap_text=True)
    FILL_NO_ANVIL = PatternFill(start_color="D9D9D9", end_color="D9D9D9", fill_type="solid")
    FILL_ULTIMATE = PatternFill(start_color="FFE6F0", end_color="FFE6F0", fill_type="solid")
    FILL_NORMAL = PatternFill(start_color="E6FFE6", end_color="E6FFE6", fill_type="solid")

    for r, row_data in enumerate(ROWS, 2):
        for c, val in enumerate(row_data, 1):
            ws.cell(row=r, column=c, value=val)
        eid = row_data[0]
        typ = row_data[5]  # 類型
        fill = FILL_NO_ANVIL if eid in NO_ANVIL_IDS else (FILL_ULTIMATE if typ == "終極附魔" else FILL_NORMAL)
        for col in range(1, len(HEADERS) + 1):
            ws.cell(row=r, column=col).fill = fill
    # 欄寬：附魔ID, 名字, 属性, 最大等級, 是否可在鐵砧升級, 類型, 適用裝備
    ws.column_dimensions["A"].width = 22
    ws.column_dimensions["B"].width = 14
    ws.column_dimensions["C"].width = 65
    ws.column_dimensions["D"].width = 10
    ws.column_dimensions["E"].width = 18
    ws.column_dimensions["F"].width = 10
    ws.column_dimensions["G"].width = 10

    wb.save(out_path)
    print("已寫入:", out_path)

if __name__ == "__main__":
    main()
