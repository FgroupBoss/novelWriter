#!/usr/bin/env python3
"""预览某阶段将加载的上下文（不调用 API）。

示例:
  python build_context.py --project my_novel --stage chapter_write --chapter 2
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from novel_workflow.config import load_stages, resolve_project
from novel_workflow.context import (
    build_context_block,
    build_user_message,
    resolve_context_files,
    resolve_output_files,
)
from run_stage import ALL_STAGES, CHAPTER_STAGES


def main() -> int:
    parser = argparse.ArgumentParser(description="预览阶段上下文包")
    parser.add_argument("--project", "-p", default=None)
    parser.add_argument("--stage", "-s", required=True, choices=sorted(ALL_STAGES))
    parser.add_argument("--chapter", "-c", type=int, default=None)
    parser.add_argument("--stats-only", action="store_true", help="只打印统计")
    args = parser.parse_args()

    if args.stage in CHAPTER_STAGES and args.chapter is None:
        parser.error(f"阶段 {args.stage} 需要 --chapter")

    root = resolve_project(args.project)
    chapter_stage = args.stage in CHAPTER_STAGES
    ctx_files = resolve_context_files(args.stage, args.chapter, chapter_stage=chapter_stage)
    out_files = resolve_output_files(args.stage, args.chapter, chapter_stage=chapter_stage)

    if args.stats_only:
        block = build_context_block(root, ctx_files)
        print(f"阶段: {args.stage}")
        print(f"上下文文件 ({len(ctx_files)}): {', '.join(ctx_files)}")
        print(f"输出文件 ({len(out_files)}): {', '.join(out_files)}")
        print(f"上下文总字符: {len(block)}")
        return 0

    msg = build_user_message(root, args.stage, out_files, args.chapter)
    print(msg)
    print("\n---\n统计: 用户消息总字符", len(msg))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
