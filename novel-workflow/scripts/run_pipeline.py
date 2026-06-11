#!/usr/bin/env python3
"""端到端流水线：设定 → 按章写作。

示例:
  python run_pipeline.py --project my_novel --chapters 1-3
  python run_pipeline.py --project my_novel --setup-only
"""

from __future__ import annotations

import argparse
import logging
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from novel_workflow.config import load_config
from novel_workflow.runner import StageRunner


def parse_chapter_range(spec: str) -> list:
    """解析 1-5 或 1,3,5 或 7。"""
    spec = spec.strip()
    if re.match(r"^\d+$", spec):
        return [int(spec)]
    if re.match(r"^\d+-\d+$", spec):
        a, b = map(int, spec.split("-"))
        if a > b:
            raise ValueError(f"无效范围: {spec}")
        return list(range(a, b + 1))
    if re.match(r"^\d+(,\d+)+$", spec):
        return [int(x) for x in spec.split(",")]
    raise ValueError(f"无法解析章节范围: {spec}")


def main() -> int:
    cfg = load_config()
    default_chapters = cfg.get("novel", {}).get("target_chapters", 80)

    parser = argparse.ArgumentParser(description="小说工作流 — 完整流水线")
    parser.add_argument("--project", "-p", default=None)
    parser.add_argument("--quickstart", action="store_true", help="从 idea 一键跑到第一章")
    parser.add_argument("--setup-only", action="store_true", help="只跑设定阶段")
    parser.add_argument("--chapters", default="1", help="章节范围，如 1-3 或 1,5,7（默认 1）")
    parser.add_argument("--skip-setup", action="store_true", help="跳过设定，直接写章")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("-v", "--verbose", action="store_true")
    args = parser.parse_args()

    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s [%(levelname)s] %(message)s",
    )

    runner = StageRunner(novel_id=args.project, dry_run=args.dry_run)

    if args.quickstart:
        logging.info("========== 一键：Idea → 第 1 章 ==========")
        result = runner.run_idea_to_chapter_one()
        print(f"一键完成: {result}")
        return 0

    if not args.skip_setup:
        logging.info("========== 开始设定阶段 ==========")
        runner.run_all_setup()

    if args.setup_only:
        print("设定阶段完成（--setup-only）")
        return 0

    chapters = parse_chapter_range(args.chapters)
    for ch in chapters:
        if ch < 1 or ch > default_chapters:
            logging.warning("章节 %s 超出 config 目标 %s，仍继续", ch, default_chapters)
        runner.run_chapter_loop(ch)

    print(f"流水线完成，章节: {chapters}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
