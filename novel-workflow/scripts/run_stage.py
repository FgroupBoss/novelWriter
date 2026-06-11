#!/usr/bin/env python3
"""单阶段执行入口。

示例:
  python run_stage.py --project my_novel --stage era_setting
  python run_stage.py --project my_novel --stage chapter_write --chapter 3
  python run_stage.py --project my_novel --stage plot_update --chapter 3
"""

from __future__ import annotations

import argparse
import logging
import sys
from pathlib import Path

# 允许从 scripts/ 目录直接运行
sys.path.insert(0, str(Path(__file__).resolve().parent))

from novel_workflow.config import load_stages
from novel_workflow.runner import StageRunner

SETUP_STAGES = set(load_stages().get("stages", {}).keys())
CHAPTER_STAGES = set(load_stages().get("chapter_stages", {}).keys())
ALL_STAGES = SETUP_STAGES | CHAPTER_STAGES


def main() -> int:
    parser = argparse.ArgumentParser(description="小说工作流 — 单阶段 API 执行")
    parser.add_argument("--project", "-p", default=None, help="项目 ID（projects 下目录名）")
    parser.add_argument("--stage", "-s", required=True, choices=sorted(ALL_STAGES), help="阶段名")
    parser.add_argument("--chapter", "-c", type=int, default=None, help="章节号（章节阶段必填）")
    parser.add_argument("--dry-run", action="store_true", help="只组装上下文，不调用 API")
    parser.add_argument("-v", "--verbose", action="store_true", help="DEBUG 日志")
    args = parser.parse_args()

    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s [%(levelname)s] %(message)s",
    )

    if args.stage in CHAPTER_STAGES and args.chapter is None:
        parser.error(f"阶段 {args.stage} 需要 --chapter")

    runner = StageRunner(novel_id=args.project, dry_run=args.dry_run)

    if args.stage in SETUP_STAGES:
        result = runner.run_setup_stage(args.stage)
    else:
        result = runner.run_chapter_stage(args.stage, args.chapter)

    print(f"完成: {result}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
