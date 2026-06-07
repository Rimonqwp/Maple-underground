# -*- coding: utf-8 -*-
"""把 chest_loot.yml 中所有箱子的 F2/F3 删除，只保留 F1。"""

from __future__ import annotations

import os
import yaml


def main() -> None:
    project_root = r"c:\Users\Rimon12\maple-underground_caves"
    yml_path = os.path.join(project_root, "src", "main", "resources", "chest_loot.yml")

    with open(yml_path, "r", encoding="utf-8") as f:
        cfg = yaml.safe_load(f) or {}

    chests = cfg.get("chests")
    if not isinstance(chests, dict):
        raise SystemExit("chests 节点未找到或类型不正确")

    for chest_id, chest_data in chests.items():
        if not isinstance(chest_data, dict):
            continue
        # 删除仅对 F2/F3 的配置
        chest_data.pop("F2", None)
        chest_data.pop("F3", None)
        # 兼容有些地方可能用小写
        chest_data.pop("f2", None)
        chest_data.pop("f3", None)

    with open(yml_path, "w", encoding="utf-8") as f:
        yaml.safe_dump(cfg, f, allow_unicode=True, sort_keys=False)

    print("已删除所有箱子的 F2/F3，只保留 F1：", yml_path)


if __name__ == "__main__":
    main()

