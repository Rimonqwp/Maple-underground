# -*- coding: utf-8 -*-
"""
将 chest_loot.yml 里 F1 各箱子的 gui.pool 槽位 chance 归一化到严格 100%。

归一化规则（与 ChestLootManager.parseChance 语义一致）：
- chance 数值 v：
  - v < 1：视为小数概率（如 0.22 表示 22%）
  - v >= 1：视为百分比整数/小数（如 22 表示 22%）
- 权重 weight：
  - v < 1 => weight = v
  - v >= 1 => weight = v / 100
- 最终写回 chance：
  - prob% = weight / sum(weight) * 100
- 四舍五入到 4 位小数，并把舍入误差加到最后一条，确保 sum(chance)=100.0000

只修改：
- chests.*.F1.gui.pool.*[].chance
不修改：
- gui.fixed
- slots（旧结构）
- F2/F3
"""

from __future__ import annotations

from decimal import Decimal, ROUND_HALF_UP
from typing import Any, Dict, List, Tuple

import os

import yaml


def parse_weight(v: Any) -> Decimal:
    if v is None:
        # 未填 chance：Java 默认 1.0
        return Decimal("1")
    if isinstance(v, (int, float)):
        dv = Decimal(str(v))
    else:
        dv = Decimal(str(v).strip())

    if dv <= 0:
        return Decimal("0")
    if dv < 1:
        # 小数概率
        return dv
    # 百分比
    return dv / Decimal("100")


def fmt_chance(prob_pct: Decimal) -> str:
    # 输出最多 4 位小数，去掉多余 0
    s = f"{prob_pct.quantize(Decimal('0.0001'), rounding=ROUND_HALF_UP):f}"
    if "." in s:
        s = s.rstrip("0").rstrip(".")
    return s


def normalize_pool(options: List[Dict[str, Any]]) -> None:
    weights: List[Decimal] = [parse_weight(opt.get("chance")) for opt in options]
    total = sum(weights, Decimal("0"))
    if total <= 0:
        # 全为 0：平均分配到 100（避免除0）
        n = len(options)
        if n == 0:
            return
        avg = Decimal("100") / Decimal(str(n))
        rounded = [avg.quantize(Decimal("0.0001"), rounding=ROUND_HALF_UP) for _ in options]
        diff = (Decimal("100.0000") - sum(rounded, Decimal("0"))).quantize(
            Decimal("0.0001"), rounding=ROUND_HALF_UP
        )
        if rounded:
            rounded[-1] = (rounded[-1] + diff).quantize(Decimal("0.0001"), rounding=ROUND_HALF_UP)
        for opt, p in zip(options, rounded):
            opt["chance"] = float(fmt_chance(p))
        return

    raw_probs: List[Decimal] = [(w / total) * Decimal("100") for w in weights]
    q = Decimal("0.0001")
    rounded = [p.quantize(q, rounding=ROUND_HALF_UP) for p in raw_probs]
    diff = (Decimal("100.0000") - sum(rounded, Decimal("0"))).quantize(q, rounding=ROUND_HALF_UP)
    if rounded:
        rounded[-1] = (rounded[-1] + diff).quantize(q, rounding=ROUND_HALF_UP)

    for opt, p in zip(options, rounded):
        opt["chance"] = float(fmt_chance(p))


def main() -> None:
    project_root = r"c:\Users\Rimon12\maple-underground_caves"
    yml_path = os.path.join(project_root, "src", "main", "resources", "chest_loot.yml")
    with open(yml_path, "r", encoding="utf-8") as f:
        cfg = yaml.safe_load(f) or {}

    chests = cfg.get("chests", {})
    if not isinstance(chests, dict):
        raise SystemExit("chests 未找到/不是 dict")

    floors_to_process = ("F1",)
    for chest_id, chest_data in chests.items():
        if not isinstance(chest_data, dict):
            continue
        for floor in floors_to_process:
            floor_data = chest_data.get(floor)
            if not isinstance(floor_data, dict):
                continue
            gui = floor_data.get("gui")
            if not isinstance(gui, dict):
                continue
            pool = gui.get("pool")
            if not isinstance(pool, dict):
                continue
            for slot_id, options in pool.items():
                if not isinstance(options, list):
                    continue
                # 只处理含 chance 的 pool option 列表
                normalize_pool(options)

    with open(yml_path, "w", encoding="utf-8") as f:
        yaml.safe_dump(cfg, f, allow_unicode=True, sort_keys=False)

    print("已归一化写回：", yml_path)


if __name__ == "__main__":
    main()

