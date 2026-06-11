#!/usr/bin/env python3
"""单章完整循环：写章 → 更新进度 → 校验。

示例:
  python run_chapter.py --project my_novel --chapter 1
  python run_chapter.py --project my_novel --from 5 --to 10
"""

from __future__ import annotations

import argparse
import logging
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from novel_workflow.runner import StageRunner


def main() -> int:
    parser = argparse.ArgumentParser(description="小说工作流 — 按章写作循环")
    parser.add_argument("--project", "-p", default=None)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--chapter", "-c", type=int, help="单章")
    group.add_argument("--range", nargs=2, type=int, metavar=("FROM", "TO"), help="章节范围")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("-v", "--verbose", action="store_true")
    args = parser.parse_args()

    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s [%(levelname)s] %(message)s",
    )

    if args.range:
        start, end = args.range
        if start > end:
            parser.error("范围起始不能大于结束")
        chapters = range(start, end + 1)
    else:
        chapters = [args.chapter]

    runner = StageRunner(novel_id=args.project, dry_run=args.dry_run)
    for ch in chapters:
        logging.info("========== 开始第 %s 章 ==========", ch)
        runner.run_chapter_loop(ch)

    print(f"完成章节: {list(chapters)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
