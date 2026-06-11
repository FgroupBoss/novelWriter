"""阶段执行器。"""

from __future__ import annotations

import logging
from pathlib import Path
from typing import Any, Dict, Optional

from .config import load_stages, resolve_project
from .context import (
    build_user_message,
    load_system_prompt,
    resolve_output_files,
)
from .llm import LLMClient
from .parser import ParseError, parse_file_blocks, validate_outputs
from .state import ensure_idea_filled, mark_stage_complete

log = logging.getLogger(__name__)


class StageRunner:
    def __init__(self, novel_id: Optional[str] = None, dry_run: bool = False):
        self.project_root = resolve_project(novel_id)
        self.novel_id = self.project_root.name
        self.dry_run = dry_run
        self.llm: Optional[LLMClient] = None if dry_run else LLMClient()

    def run_setup_stage(self, stage_name: str) -> Dict[str, Any]:
        stages = load_stages()
        if stage_name not in stages.get("stages", {}):
            raise KeyError(f"未知设定阶段: {stage_name}")

        if stage_name == "era_setting":
            ensure_idea_filled(self.project_root)

        output_files = resolve_output_files(stage_name)
        return self._execute(stage_name, output_files, chapter=None, chapter_stage=False)

    def run_chapter_stage(self, stage_name: str, chapter: int) -> Dict[str, Any]:
        stages = load_stages()
        if stage_name not in stages.get("chapter_stages", {}):
            raise KeyError(f"未知章节阶段: {stage_name}")
        if chapter < 1:
            raise ValueError("章节号须 >= 1")

        output_files = resolve_output_files(stage_name, chapter, chapter_stage=True)
        return self._execute(stage_name, output_files, chapter=chapter, chapter_stage=True)

    def run_chapter_loop(self, chapter: int) -> None:
        loop = load_stages().get("chapter_loop", ["chapter_write", "plot_update", "chapter_review"])
        for stage in loop:
            log.info("=== 第 %s 章 · %s ===", chapter, stage)
            self.run_chapter_stage(stage, chapter)
        mark_stage_complete(self.project_root, "chapter_review", chapter=chapter)

    def run_all_setup(self, stop_on_error: bool = True) -> None:
        order = load_stages().get("setup_order", [])
        for stage in order:
            log.info("=== 设定阶段: %s ===", stage)
            try:
                self.run_setup_stage(stage)
            except Exception:
                if stop_on_error:
                    raise
                log.exception("阶段 %s 失败，继续下一阶段", stage)

    def run_idea_to_chapter_one(self) -> Dict[str, Any]:
        """从 idea 一键执行：全部设定 → 第 1 章（写/更新/校验）。"""
        if self.dry_run:
            from .dry_run import build_dry_run_report

            return build_dry_run_report(self.novel_id)

        ensure_idea_filled(self.project_root)
        self.run_all_setup()
        self.run_chapter_loop(1)
        return {"setup_completed": True, "chapter": 1}

    def _execute(
        self,
        stage_name: str,
        output_files: list,
        chapter: Optional[int],
        chapter_stage: bool,
    ) -> Dict[str, Any]:
        system = load_system_prompt()
        user = build_user_message(self.project_root, stage_name, output_files, chapter)

        if self.dry_run:
            log.info("[dry-run] 跳过 LLM 调用 stage=%s chapter=%s", stage_name, chapter)
            log.info("上下文用户消息长度: %s 字符", len(user))
            return {
                "stage": stage_name,
                "chapter": chapter,
                "dry_run": True,
                "total_chars": len(user),
                "output_files": output_files,
            }

        assert self.llm is not None
        raw = self.llm.chat(system, user)
        self._save_raw(stage_name, chapter, raw)

        parsed = parse_file_blocks(raw, expected=output_files)
        validated, missing = validate_outputs(output_files, parsed)
        if missing:
            raise ParseError(f"模型输出缺少文件: {missing}。原始回复已保存，可重试。")

        written = self._write_files(validated)
        if not chapter_stage:
            mark_stage_complete(self.project_root, stage_name)
        elif stage_name == "chapter_review" and chapter is not None:
            mark_stage_complete(self.project_root, stage_name, chapter=chapter)

        log.info("阶段完成 stage=%s 写入 %s 个文件", stage_name, len(written))
        return {"stage": stage_name, "chapter": chapter, "files": written}

    def _write_files(self, files: Dict[str, str]) -> list:
        written = []
        for rel, content in files.items():
            path = self.project_root / rel
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content.strip() + "\n", encoding="utf-8")
            written.append(rel)
            log.info("已写入: %s (%s 字符)", rel, len(content))
        return written

    def _save_raw(self, stage_name: str, chapter: Optional[int], raw: str) -> None:
        log_dir = self.project_root / "meta" / "logs"
        log_dir.mkdir(parents=True, exist_ok=True)
        suffix = f"_ch{chapter:03d}" if chapter else ""
        path = log_dir / f"{stage_name}{suffix}_raw.md"
        path.write_text(raw, encoding="utf-8")
        log.info("原始回复已保存: %s", path.relative_to(self.project_root))
