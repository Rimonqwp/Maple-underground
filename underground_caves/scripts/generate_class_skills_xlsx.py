# -*- coding: utf-8 -*-
"""
產生 5 職業等級提升表 Excel (.xlsx)。
需先安裝: pip install openpyxl
執行: python scripts/generate_class_skills_xlsx.py
輸出: src/main/resources/class_skills_levels.xlsx
"""
import os
import sys
from typing import Callable, Dict, List, Tuple

try:
    from openpyxl import Workbook
    from openpyxl.styles import Font, Alignment, PatternFill
except ImportError:
    print("請先安裝 openpyxl: pip install openpyxl")
    sys.exit(1)


MAX_LEVEL = 50


def clamp(x, lo, hi):
    return max(lo, min(hi, x))


def fmt(v):
    if v is None:
        return ""
    if isinstance(v, (int,)):
        return str(v)
    if isinstance(v, float):
        if abs(v - round(v)) < 1e-9:
            return str(int(round(v)))
        return f"{v:.3f}".rstrip("0").rstrip(".")
    return str(v)


def delta(a, b):
    if a is None or b is None:
        return ""
    if isinstance(a, (int, float)) and isinstance(b, (int, float)):
        d = b - a
        if abs(d) < 1e-9:
            return "0"
        return fmt(d)
    return ""


def build_rows_for_metric(levels: List[int], metric_name: str, unit: str, fn: Callable[[int], float]) -> List[List[str]]:
    rows: List[List[str]] = []
    for lvl in levels:
        prev_lvl = max(1, lvl - 1)
        prev = fn(prev_lvl)
        cur = fn(lvl)
        if prev == cur:
            continue
        rows.append([str(lvl), metric_name, unit, fmt(prev), fmt(cur), delta(prev, cur)])
    return rows


def build_rows_for_table(levels: List[int], skill_name: str, unit: str, table_total: List[int]) -> List[List[str]]:
    rows: List[List[str]] = []
    for lvl in levels:
        prev = table_total[lvl - 1] if lvl - 1 >= 0 else table_total[0]
        cur = table_total[lvl]
        if prev == cur:
            continue
        rows.append([str(lvl), skill_name, unit, fmt(prev), fmt(cur), delta(prev, cur)])
    return rows


def sheet_specs() -> Dict[str, List[List[str]]]:
    levels = list(range(1, MAX_LEVEL + 1))

    # 以 UI lore 的 build*LevelRewardsLore 規則為準（不是實裝 listener）

    # --- 治療師 ---
    def healer_aura_heal_pct(lvl: int) -> float:
        return 1.0 + 0.1 * (lvl // 5)

    def healer_resonance_mult(lvl: int) -> float:
        return 1.5 + 0.01 * lvl

    def healer_inspire_threshold(lvl: int) -> int:
        return 25 + (lvl // 5)

    def healer_inspire_dmg_pct(lvl: int) -> float:
        return 1.10 + 0.05 * (lvl // 5)

    def healer_circle_heal_pct(lvl: int) -> float:
        return 2.0 + 0.1 * lvl

    def healer_circle_radius(lvl: int) -> float:
        return 4.0 + 0.5 * (lvl // 10)

    def healer_circle_duration(lvl: int) -> int:
        return 1 + (lvl // 5)

    def healer_wish_shield_pct(lvl: int) -> float:
        return 20.0 + 0.4 * lvl

    healer_rows: List[List[str]] = []
    healer_rows += build_rows_for_metric(levels, "治療光環: 每秒恢復", "%", healer_aura_heal_pct)
    healer_rows += build_rows_for_metric(levels, "生命共鳴: 治療倍率", "倍", healer_resonance_mult)
    healer_rows += build_rows_for_metric(levels, "鼓舞之愈: 觸發門檻", "%", healer_inspire_threshold)
    healer_rows += build_rows_for_metric(levels, "鼓舞之愈: 增傷", "%", healer_inspire_dmg_pct)
    healer_rows += build_rows_for_metric(levels, "療癒圈: 每秒恢復", "%", healer_circle_heal_pct)
    healer_rows += build_rows_for_metric(levels, "療癒圈: 半徑", "格", healer_circle_radius)
    healer_rows += build_rows_for_metric(levels, "療癒圈: 持續", "秒", healer_circle_duration)
    healer_rows += build_rows_for_metric(levels, "願望: 護盾", "%", healer_wish_shield_pct)

    # --- 法師 ---
    def mage_staff_damage_pct(lvl: int) -> int:
        return min(30 + (lvl // 5), 40)

    def mage_staff_range(lvl: int) -> int:
        return min(10 + (lvl // 10), 15)

    def mage_time_accel_pct(lvl: int) -> float:
        return min(10.0 + (30.0 - 10.0) * (lvl / MAX_LEVEL), 30.0)

    mage_rows: List[List[str]] = []
    mage_rows += build_rows_for_metric(levels, "法師法杖: 傷害", "%", mage_staff_damage_pct)
    mage_rows += build_rows_for_metric(levels, "法師法杖: 有效距離", "格", mage_staff_range)
    mage_rows += build_rows_for_metric(levels, "時間加速: 冷卻縮短", "%", mage_time_accel_pct)
    for lvl in levels:
        mage_rows.append([str(lvl), "等級獎勵: 法力值上限", "點", "", "+5", "+5"])

    # --- 戰士 ---
    def warrior_vamp_pct(lvl: int) -> int:
        return min(20 + (lvl // 5), 30)

    def warrior_blood_pct(lvl: int) -> float:
        return min(10.0 + (30.0 - 10.0) * (lvl / MAX_LEVEL), 30.0)

    def warrior_rift_chance(lvl: int) -> int:
        return min(10 + 2 * (lvl // 5), 30)

    def warrior_rift_dot(lvl: int) -> int:
        return min(15 + (3 * (lvl // 5)) // 2, 30)

    def warrior_rift_cd(lvl: int) -> int:
        return max(20 - (lvl // 5) // 2, 15)

    def warrior_shout_radius(lvl: int) -> float:
        return min(5.0 + 0.5 * lvl, 30.0)

    def warrior_shout_dmg(lvl: int) -> float:
        return min(5.0 + 0.3 * lvl, 20.0)

    def warrior_shout_reduce(lvl: int) -> float:
        return min(5.0 + 0.2 * lvl, 15.0)

    def warrior_shout_dur(lvl: int) -> float:
        return min(5.0 + 0.5 * lvl, 30.0)

    warrior_rows: List[List[str]] = []
    warrior_rows += build_rows_for_metric(levels, "吸血: 攻擊提高", "%", warrior_vamp_pct)
    warrior_rows += build_rows_for_metric(levels, "血武器: 傷害增加", "%", warrior_blood_pct)
    warrior_rows += build_rows_for_metric(levels, "撕裂創口: 觸發", "%", warrior_rift_chance)
    warrior_rows += build_rows_for_metric(levels, "撕裂創口: 持續傷害", "%", warrior_rift_dot)
    warrior_rows += build_rows_for_metric(levels, "撕裂創口: 同一目標冷卻", "秒", warrior_rift_cd)
    warrior_rows += build_rows_for_metric(levels, "狂戰怒吼: 半徑", "格", warrior_shout_radius)
    warrior_rows += build_rows_for_metric(levels, "狂戰怒吼: 傷害加成", "%", warrior_shout_dmg)
    warrior_rows += build_rows_for_metric(levels, "狂戰怒吼: 減免", "%", warrior_shout_reduce)
    warrior_rows += build_rows_for_metric(levels, "狂戰怒吼: 持續", "秒", warrior_shout_dur)

    # --- 弓箭手 ---
    def archer_bounce_chance(lvl: int) -> float:
        return min(2.0 * lvl, 100.0)

    def archer_double_chance(lvl: int) -> int:
        return min(50 + 5 * (lvl // 5), 100)

    def archer_focus_stand(lvl: int) -> float:
        return max(8.0 - (8.0 - 3.0) * (lvl / MAX_LEVEL), 3.0)

    def archer_focus_dmg(lvl: int) -> float:
        return min(5.0 + 5.0 * (lvl // 5) / 10.0, 10.0)

    def archer_focus_stacks(lvl: int) -> int:
        return min(3 + (lvl // 5) // 5, 5)

    def archer_explosive_arrows(lvl: int) -> int:
        return min(1 + (lvl // 25), 3)

    def archer_fast_arrows(lvl: int) -> int:
        return min(2 + (lvl * 3) // MAX_LEVEL, 5)

    def archer_fast_dur(lvl: int) -> float:
        return min(4.0 + (8.0 - 4.0) * (lvl / MAX_LEVEL), 8.0)

    def archer_fast_pct(lvl: int) -> float:
        return min(30.0 + (75.0 - 30.0) * (lvl / MAX_LEVEL), 75.0)

    archer_rows: List[List[str]] = []
    archer_rows += build_rows_for_metric(levels, "彈跳箭: 彈射機率", "%", archer_bounce_chance)
    archer_rows += build_rows_for_metric(levels, "雙倍射擊: 機率", "%", archer_double_chance)
    archer_rows += build_rows_for_metric(levels, "鷹眼聚焦: 站立", "秒", archer_focus_stand)
    archer_rows += build_rows_for_metric(levels, "鷹眼聚焦: 傷害", "%", archer_focus_dmg)
    archer_rows += build_rows_for_metric(levels, "鷹眼聚焦: 最多層數", "層", archer_focus_stacks)
    archer_rows += build_rows_for_metric(levels, "爆炸射擊: 箭數", "支", archer_explosive_arrows)
    archer_rows += build_rows_for_metric(levels, "快速射擊: 箭數", "支", archer_fast_arrows)
    archer_rows += build_rows_for_metric(levels, "快速射擊: 持續", "秒", archer_fast_dur)
    archer_rows += build_rows_for_metric(levels, "快速射擊: 傷害", "%", archer_fast_pct)

    # --- 坦克 ---
    def tank_taunt_threshold(lvl: int) -> float:
        return max(50.0 - 0.5 * (lvl - 1), 25.0)

    def tank_front_reduce(lvl: int) -> int:
        return min(15 + (lvl // 5), 25)

    def tank_redirect_radius(lvl: int) -> float:
        return min(5.0 + (20.0 - 5.0) * (lvl / MAX_LEVEL), 20.0)

    def tank_provoke_radius(lvl: int) -> int:
        return min(5 + (lvl // 10), 10)

    def tank_provoke_dur(lvl: int) -> float:
        return min(4.0 + 0.8 * (lvl // 10), 8.0)

    def tank_provoke_reduce(lvl: int) -> int:
        return min(15 + 3 * (lvl // 10), 30)

    tank_rows: List[List[str]] = []
    tank_rows += build_rows_for_metric(levels, "嘲諷: 生命值高於", "%", tank_taunt_threshold)
    tank_rows += build_rows_for_metric(levels, "堅若磐石: 正面減傷", "%", tank_front_reduce)
    tank_rows += build_rows_for_metric(levels, "我為人人: 半徑", "格", tank_redirect_radius)
    tank_rows += build_rows_for_metric(levels, "挑釁怒吼: 半徑", "格", tank_provoke_radius)
    tank_rows += build_rows_for_metric(levels, "挑釁怒吼: 持續", "秒", tank_provoke_dur)
    tank_rows += build_rows_for_metric(levels, "挑釁怒吼: 傷害降低", "%", tank_provoke_reduce)

    return {
        "治療師": healer_rows,
        "法師": mage_rows,
        "戰士": warrior_rows,
        "弓箭手": archer_rows,
        "坦克": tank_rows,
    }


HEADERS = ["等級", "技能/項目", "單位", "上一級", "本級", "提升"]


def write_sheet(ws, title: str, rows: List[List[str]], fill: PatternFill):
    ws.title = title
    for c, h in enumerate(HEADERS, 1):
        cell = ws.cell(row=1, column=c, value=h)
        cell.font = Font(bold=True)
        cell.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)
        cell.fill = fill

    for r, row in enumerate(rows, 2):
        for c, val in enumerate(row, 1):
            cell = ws.cell(row=r, column=c, value=val)
            cell.alignment = Alignment(vertical="center", wrap_text=True)

    ws.freeze_panes = "A2"
    ws.column_dimensions["A"].width = 8
    ws.column_dimensions["B"].width = 34
    ws.column_dimensions["C"].width = 14
    ws.column_dimensions["D"].width = 14
    ws.column_dimensions["E"].width = 14
    ws.column_dimensions["F"].width = 12


def main():
    project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out_path = os.path.join(project_root, "src", "main", "resources", "class_skills_levels_ui.xlsx")
    os.makedirs(os.path.dirname(out_path), exist_ok=True)

    wb = Workbook()
    # remove default
    wb.remove(wb.active)

    fills = {
        "治療師": PatternFill(start_color="FFE6F0", end_color="FFE6F0", fill_type="solid"),
        "法師": PatternFill(start_color="E6F3FF", end_color="E6F3FF", fill_type="solid"),
        "戰士": PatternFill(start_color="FFEDE6", end_color="FFEDE6", fill_type="solid"),
        "弓箭手": PatternFill(start_color="E6FFE6", end_color="E6FFE6", fill_type="solid"),
        "坦克": PatternFill(start_color="FFF9E6", end_color="FFF9E6", fill_type="solid"),
    }

    specs = sheet_specs()
    for name, rows in specs.items():
        ws = wb.create_sheet(title=name)
        write_sheet(ws, name, rows, fills.get(name, PatternFill(fill_type=None)))

    wb.save(out_path)
    print("已寫入:", out_path)


if __name__ == "__main__":
    main()

