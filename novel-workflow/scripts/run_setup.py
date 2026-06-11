#!/usr/bin/env python3
"""依次执行全部设定阶段。

示例:
  python run_setup.py --project my_novel
  python run_setup.py --project my_novel --from foreshadowing
"""

from __future__ import annotations

import argparse
import logging
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from novel_workflow.config import load_stages
from novel_workflow.runner import StageRunner

ORDER = load_stages().get("setup_order", [])


def main() -> int:
    parser = argparse.ArgumentParser(description="小说工作流 — 批量设定阶段")
    parser.add_argument("--project", "-p", default=None)
    parser.add_argument("--from", dest="from_stage", default=None, choices=ORDER, help="从某阶段开始")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("-v", "--verbose", action="store_true")
    args = parser.parse_args()

    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s [%(levelname)s] %(message)s",
    )

    runner = StageRunner(novel_id=args.project, dry_run=args.dry_run)
    start_idx = 0
    if args.from_stage:
        start_idx = ORDER.index(args.from_stage)

    for stage in ORDER[start_idx:]:
        logging.info("=== 设定阶段: %s ===", stage)
        runner.run_setup_stage(stage)

    print("全部设定阶段完成")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
