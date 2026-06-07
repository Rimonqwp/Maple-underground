# -*- coding: utf-8 -*-
"""Dump chest_loot_report.xlsx 的 F1 工作表，按箱子与槽位分组输出 YAML 结构。"""

from __future__ import annotations

from collections import defaultdict
from openpyxl import load_workbook
from decimal import Decimal


ORDER = ["bronze", "silver", "gold", "platinum", "red", "orange"]


def main() -> None:
    # 如果报表被占用，generate 脚本会写到 latest，这里优先读取 latest
    xlsx_path = r"c:\Users\Rimon12\maple-underground_caves\src\main\resources\chest_loot_report.xlsx"
    latest = r"c:\Users\Rimon12\maple-underground_caves\src\main\resources\chest_loot_report_latest.xlsx"
    try:
        import os

        if os.path.exists(latest):
            xlsx_path = latest
    except Exception:
        pass
    wb = load_workbook(xlsx_path, data_only=True)
    ws = wb["F1"]

    # out[chest]['base'] = int
    # out[chest]['fixed'][slot] = [(item, amount, extra_price, chance_pct, total_cost)]
    # out[chest]['pool'][slot] = [(item, amount, extra_price, chance_pct, total_cost), ...]
    out = defaultdict(
        lambda: {
            "base": None,
            "fixed": defaultdict(list),
            "pool": defaultdict(list),
            "slots": defaultdict(list),
        }
    )

    for r in range(2, ws.max_row + 1):
        chest = ws.cell(row=r, column=1).value
        floor = ws.cell(row=r, column=3).value
        cfg_type = ws.cell(row=r, column=4).value
        slot = ws.cell(row=r, column=5).value
        item_id = ws.cell(row=r, column=6).value
        amount = ws.cell(row=r, column=7).value
        chance = ws.cell(row=r, column=8).value
        floor_base = ws.cell(row=r, column=9).value
        slot_price = ws.cell(row=r, column=10).value
        extra_price = ws.cell(row=r, column=11).value
        total_cost = ws.cell(row=r, column=12).value

        if chest is None:
            continue
        c = str(chest).lower()
        if str(floor).upper() != "F1":
            continue

        if out[c]["base"] is None and floor_base is not None:
            out[c]["base"] = int(floor_base)

        slot_s = str(slot)
        item_s = "" if item_id is None else str(item_id)
        amount_s = "" if amount is None else str(amount)

        if cfg_type == "gui_fixed":
            out[c]["fixed"][slot_s].append((item_s, amount_s, extra_price, chance, total_cost))
        elif cfg_type == "gui_pool":
            out[c]["pool"][slot_s].append((item_s, amount_s, extra_price, chance, total_cost))
        elif cfg_type == "slots":
            out[c]["slots"][slot_s].append((item_s, amount_s, slot_price, extra_price, chance, total_cost))

    for c in ORDER:
        if c not in out or out[c]["base"] is None:
            continue
        base = out[c]["base"]
        print(f"{c}:")
        print("  F1:")
        print(f"    base_price: {base}")

        # fixed
        if out[c]["fixed"]:
            print("    gui:")
            print("      fixed:")
            for slot_s in sorted(out[c]["fixed"].keys(), key=lambda x: int(x) if x.isdigit() else 999):
                # gui_fixed 在当前报表里通常只有 1 个条目
                item_s, amount_s, extra_price, chance, total_cost = out[c]["fixed"][slot_s][0]
                extra_part = ""
                if extra_price is not None:
                    try:
                        ep = int(extra_price)
                        if ep != 0:
                            extra_part = f", extra_price: {ep}"
                    except Exception:
                        pass
                print(f'        "{slot_s}": {{ item: "{item_s}", amount: "{amount_s}"{extra_part} }}')

        # pool
        if out[c]["pool"]:
            # 如果前面没打印 gui.fixed，就需要先打印 gui
            if not out[c]["fixed"]:
                print("    gui:")
            print("      pool:")
            for slot_s in sorted(out[c]["pool"].keys(), key=lambda x: int(x) if x.isdigit() else 999):
                pool_entries = out[c]["pool"][slot_s]
                # chance 来自 xlsx 的“概率(%)”列（等价于抽取权重的百分比值）
                # guiPool 抽取用加权选择，因此最终命中概率(%) = chance% / sum(chance%) * 100
                parsed_entries = []
                total_chance_pct = 0.0
                # 用 Decimal 计算最终归一化概率，避免浮点舍入误差
                parsed_prob_list = []
                for item_s, amount_s, extra_price, chance, total_cost in pool_entries:
                    try:
                        chance_pct = float(chance)
                    except Exception:
                        chance_pct = 0.0
                    chance_pct = max(0.0, chance_pct)
                    parsed_entries.append((item_s, amount_s, extra_price, chance_pct, total_cost))
                    total_chance_pct += chance_pct

                print(f'        "{slot_s}":')
                # 归一化：最终概率% = chance% / sum(chance%) * 100
                total_chance_dec = Decimal(str(total_chance_pct)) if total_chance_pct > 0 else Decimal("0")
                raw_probs: list[Decimal] = []
                for _, _, _, chance_pct, _ in parsed_entries:
                    if total_chance_dec <= 0:
                        raw_probs.append(Decimal("0"))
                    else:
                        p = Decimal(str(chance_pct)) / total_chance_dec * Decimal("100")
                        raw_probs.append(p)

                # round 到 4 位小数，最后一项补齐到 100.0000
                q = Decimal("0.0001")
                rounded_probs = [p.quantize(q) for p in raw_probs]
                sum_rounded = sum(rounded_probs, Decimal("0"))
                diff = (Decimal("100.0000") - sum_rounded).quantize(q)
                if rounded_probs:
                    rounded_probs[-1] = (rounded_probs[-1] + diff).quantize(q)

                for idx, (item_s, amount_s, extra_price, _, total_cost) in enumerate(parsed_entries):
                    extra_part = ""
                    if extra_price is not None:
                        try:
                            ep = int(extra_price)
                            if ep != 0:
                                extra_part = f", extra_price: {ep}"
                        except Exception:
                            pass
                    # 输出：最终命中概率（百分比），保证本组加起来=100%
                    prob_dec = rounded_probs[idx] if idx < len(rounded_probs) else Decimal("0")
                    chance_out = str(prob_dec)
                    if "." in chance_out:
                        chance_out = chance_out.rstrip("0").rstrip(".")

                    print(f'          - {{ item: "{item_s}", amount: "{amount_s}", chance: {chance_out}{extra_part} }}')
        print()

        # old slots structure（如果你的报表里还存在）
        if out[c]["slots"]:
            print("    slots:")
            for slot_s in sorted(out[c]["slots"].keys(), key=lambda x: int(x) if x.isdigit() else x):
                # slots 在当前报表里通常只有 1 个条目
                item_s, amount_s, slot_price, extra_price, chance, total_cost = out[c]["slots"][slot_s][0]
                item_token = f"{item_s} {amount_s}".strip()
                extra_part = ""
                if extra_price is not None:
                    try:
                        ep = int(extra_price)
                        if ep != 0:
                            extra_part = f", extra_price: {ep}"
                    except Exception:
                        pass
                # 原 yml 的 slots 结构不带 chance；只输出 price + item（以及额外 extra_price 占位）
                print(f'      "{slot_s}": {{ price: {slot_price}, item: "{item_token}"{extra_part} }}')



if __name__ == "__main__":
    main()

