#!/usr/bin/env python3
"""试运行：检查各阶段上下文与 prompt 体量，不调用 API。

示例:
  python run_dry_run.py --project my_novel
  python run_dry_run.py --project my_novel --json
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from novel_workflow.dry_run import build_dry_run_report, format_report_markdown


def main() -> int:
    parser = argparse.ArgumentParser(description="小说工作流 — 试运行（不调用 API）")
    parser.add_argument("--project", "-p", default=None)
    parser.add_argument("--json", action="store_true", help="输出 JSON")
    args = parser.parse_args()

    report = build_dry_run_report(args.project)

    if args.json:
        print(json.dumps(report, ensure_ascii=False, indent=2))
    else:
        print(format_report_markdown(report))
        print(f"\n报告已保存: projects/{report['project_id']}/meta/dry_run_report.md")

    return 0 if report["idea_ready"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
