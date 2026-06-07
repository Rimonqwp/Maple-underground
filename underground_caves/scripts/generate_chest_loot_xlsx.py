# -*- coding: utf-8 -*-
"""根据 chest_loot.yml 生成战利品试算表（xlsx）。

输出内容包含：
- 箱子、楼层、类型（gui_fixed/gui_pool/slots）
- 槽位
- 物品ID、数量
- 概率（百分比）
- 基础价格、条目价格、额外价格
- 命中该条目时总花费（基础 + 条目 + 额外）

运行：
  py -3 scripts/generate_chest_loot_xlsx.py
"""
from __future__ import annotations

import os
import sys
from typing import Any, Dict, List, Tuple

try:
    import yaml
except ImportError:
    print("请先安装 PyYAML: pip install pyyaml")
    sys.exit(1)

try:
    from openpyxl import Workbook
    from openpyxl.styles import Alignment, Font
except ImportError:
    print("请先安装 openpyxl: pip install openpyxl")
    sys.exit(1)


HEADERS = [
    "箱子ID",
    "箱子显示名",
    "楼层",
    "配置类型",
    "槽位",
    "物品ID",
    "数量",
    "概率(%)",
    "基础价格",
    "条目价格",
    "额外价格",
    "命中该条目总价",
    "附魔书名称",
    "附魔书介绍",
    "原始条目",
]

CHEST_ORDER = {
    "bronze": 1,
    "silver": 2,
    "gold": 3,
    "platinum": 4,
    "red": 5,
    "orange": 6,
}


def load_enchant_info() -> Dict[str, Tuple[str, str]]:
    """从 generate_enchantments_xlsx.py 读取附魔名与介绍。"""
    try:
        from generate_enchantments_xlsx import ROWS  # type: ignore
    except Exception:
        return {}
    info: Dict[str, Tuple[str, str]] = {}
    for row in ROWS:
        if not row or len(row) < 3:
            continue
        enchant_id = str(row[0]).strip()
        name = str(row[1]).strip()
        desc = str(row[2]).strip()
        if enchant_id:
            info[enchant_id] = (name, desc)
    return info


def chance_to_percent(chance: Any, default: float = 100.0) -> float:
    if chance is None:
        return default
    try:
        v = float(chance)
    except (TypeError, ValueError):
        return default
    if v <= 0:
        return 0.0
    # yml 约定：
    # - 0~1 为小数（0.22=22%）
    # - 1~100 为百分比整数（22=22%）
    # 因此：只有 v < 1 才是小数；v >= 1 直接当百分比整数。
    if v < 1:
        return v * 100.0
    return v


def parse_item_and_amount(item_str: str) -> Tuple[str, str]:
    if not item_str:
        return "", ""
    parts = str(item_str).strip().split()
    if not parts:
        return "", ""
    item_id = parts[0]
    amount = parts[1] if len(parts) >= 2 else "1"
    return item_id, amount


def collect_rows(cfg: Dict[str, Any], enchant_info: Dict[str, Tuple[str, str]]) -> List[List[Any]]:
    rows: List[List[Any]] = []
    chests = cfg.get("chests", {})
    for chest_id, chest_data in chests.items():
        if not isinstance(chest_data, dict):
            continue
        display_name = chest_data.get("display_name", chest_id)
        chest_base = int(chest_data.get("base_price", 0) or 0)

        for floor in ("F1", "F2", "F3"):
            floor_data = chest_data.get(floor)
            if not isinstance(floor_data, dict):
                continue
            floor_base = int(floor_data.get("base_price", chest_base) or chest_base)

            # 旧结构 slots
            slots = floor_data.get("slots")
            if isinstance(slots, dict):
                for slot_id, slot_obj in slots.items():
                    if not isinstance(slot_obj, dict):
                        continue
                    item_str = str(slot_obj.get("item", "") or "")
                    item_id, amount = parse_item_and_amount(item_str)
                    slot_price = int(slot_obj.get("price", 0) or 0)
                    extra_price = int(slot_obj.get("extra_price", 0) or 0)
                    chance_pct = chance_to_percent(slot_obj.get("chance"), default=100.0)
                    total_cost = floor_base + slot_price + extra_price
                    ench_name, ench_desc = enchant_info.get(item_id, ("", ""))
                    rows.append([
                        chest_id,
                        display_name,
                        floor,
                        "slots",
                        str(slot_id),
                        item_id,
                        amount,
                        chance_pct,
                        floor_base,
                        slot_price,
                        extra_price,
                        total_cost,
                        ench_name,
                        ench_desc,
                        str(slot_obj),
                    ])

            gui = floor_data.get("gui")
            if not isinstance(gui, dict):
                continue

            # gui.fixed
            fixed = gui.get("fixed")
            if isinstance(fixed, dict):
                for slot_id, slot_obj in fixed.items():
                    if not isinstance(slot_obj, dict):
                        continue
                    item_id = str(slot_obj.get("item", "") or "")
                    amount = str(slot_obj.get("amount", "1") or "1")
                    slot_price = int(slot_obj.get("price", 0) or 0)
                    extra_price = int(slot_obj.get("extra_price", 0) or 0)
                    chance_pct = 100.0
                    total_cost = floor_base + slot_price + extra_price
                    ench_name, ench_desc = enchant_info.get(item_id, ("", ""))
                    rows.append([
                        chest_id,
                        display_name,
                        floor,
                        "gui_fixed",
                        str(slot_id),
                        item_id,
                        amount,
                        chance_pct,
                        floor_base,
                        slot_price,
                        extra_price,
                        total_cost,
                        ench_name,
                        ench_desc,
                        str(slot_obj),
                    ])

            # gui.pool
            pool = gui.get("pool")
            if isinstance(pool, dict):
                for slot_id, options in pool.items():
                    if not isinstance(options, list):
                        continue
                    for option in options:
                        if not isinstance(option, dict):
                            continue
                        item_id = str(option.get("item", "") or "")
                        amount = str(option.get("amount", "1") or "1")
                        slot_price = int(option.get("price", 0) or 0)
                        extra_price = int(option.get("extra_price", 0) or 0)
                        chance_pct = chance_to_percent(option.get("chance"), default=100.0)
                        total_cost = floor_base + slot_price + extra_price
                        ench_name, ench_desc = enchant_info.get(item_id, ("", ""))
                        rows.append([
                            chest_id,
                            display_name,
                            floor,
                            "gui_pool",
                            str(slot_id),
                            item_id,
                            amount,
                            chance_pct,
                            floor_base,
                            slot_price,
                            extra_price,
                            total_cost,
                            ench_name,
                            ench_desc,
                            str(option),
                        ])

    # 排序：箱子 -> 楼层 -> 类型 -> 槽位 -> 物品
    def floor_key(v: str) -> int:
        if v == "F1":
            return 1
        if v == "F2":
            return 2
        if v == "F3":
            return 3
        return 99

    rows.sort(
        key=lambda r: (
            CHEST_ORDER.get(str(r[0]).lower(), 99),
            floor_key(str(r[2])),
            str(r[3]),
            str(r[4]),
            str(r[5]),
        )
    )
    return rows


def _fill_sheet(ws, sheet_rows: List[List[Any]]) -> None:
    for col, h in enumerate(HEADERS, 1):
        c = ws.cell(row=1, column=col, value=h)
        c.font = Font(bold=True)
        c.alignment = Alignment(horizontal="center", vertical="center")

    for r_idx, row in enumerate(sheet_rows, 2):
        for c_idx, val in enumerate(row, 1):
            cell = ws.cell(row=r_idx, column=c_idx, value=val)
            if c_idx == 8:  # 概率
                cell.number_format = "0.00"
            if c_idx in (9, 10, 11, 12):  # 价格
                cell.number_format = "#,##0"
        ws.cell(row=r_idx, column=13).alignment = Alignment(wrap_text=True, vertical="top")
        ws.cell(row=r_idx, column=14).alignment = Alignment(wrap_text=True, vertical="top")
        ws.cell(row=r_idx, column=15).alignment = Alignment(wrap_text=True, vertical="top")

    widths = {
        "A": 14, "B": 26, "C": 8, "D": 12, "E": 8, "F": 24, "G": 10,
        "H": 10, "I": 12, "J": 12, "K": 12, "L": 14, "M": 16, "N": 44, "O": 44,
    }
    for k, v in widths.items():
        ws.column_dimensions[k].width = v
    ws.freeze_panes = "A2"


def write_xlsx(rows: List[List[Any]], out_path: str) -> None:
    wb = Workbook()

    # 总表
    ws_all = wb.active
    ws_all.title = "All"
    _fill_sheet(ws_all, rows)

    # 分楼层表
    for floor in ("F1", "F2", "F3"):
        ws = wb.create_sheet(title=floor)
        floor_rows = [r for r in rows if str(r[2]) == floor]
        _fill_sheet(ws, floor_rows)

    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    wb.save(out_path)


def main() -> None:
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    yml_path = os.path.join(project_root, "src", "main", "resources", "chest_loot.yml")
    out_path = os.path.join(project_root, "src", "main", "resources", "chest_loot_report.xlsx")

    if not os.path.exists(yml_path):
        print("找不到文件:", yml_path)
        sys.exit(1)

    with open(yml_path, "r", encoding="utf-8") as f:
        cfg = yaml.safe_load(f) or {}

    enchant_info = load_enchant_info()
    rows = collect_rows(cfg, enchant_info)
    final_out = out_path
    try:
        write_xlsx(rows, out_path)
    except PermissionError:
        final_out = os.path.join(project_root, "src", "main", "resources", "chest_loot_report_latest.xlsx")
        write_xlsx(rows, final_out)
        print("原文件占用中，已改写入:", final_out)
    else:
        print("已写入:", final_out)
    print("总行数:", len(rows))


if __name__ == "__main__":
    main()

