"""workflow_state.json 读写。"""

from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional

from .config import load_stages


def _now_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def load_state(project_root: Path) -> Dict[str, Any]:
    path = project_root / "meta" / "workflow_state.json"
    if not path.is_file():
        return {}
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def save_state(project_root: Path, state: Dict[str, Any]) -> None:
    path = project_root / "meta" / "workflow_state.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    state["last_updated"] = _now_iso()
    with path.open("w", encoding="utf-8") as f:
        json.dump(state, f, ensure_ascii=False, indent=2)


def mark_stage_complete(project_root: Path, stage: str, chapter: Optional[int] = None) -> Dict[str, Any]:
    state = load_state(project_root)
    completed: List[str] = state.setdefault("completed_stages", [])
    if stage not in completed:
        completed.append(stage)

    state["current_stage"] = stage
    if chapter is not None:
        state["current_chapter"] = chapter
        _update_volume_progress(state, chapter)

    save_state(project_root, state)
    return state


def _update_volume_progress(state: Dict[str, Any], chapter: int) -> None:
    volumes = state.get("volumes", {})
    for vol in volumes.values():
        start = vol.get("chapter_start", 0)
        end = vol.get("chapter_end", 0)
        if start <= chapter <= end:
            vol["chapters_done"] = chapter - start + 1
            break


def next_setup_stage(project_root: Path) -> Optional[str]:
    stages = load_stages()
    order = stages.get("setup_order", [])
    state = load_state(project_root)
    completed = set(state.get("completed_stages", []))
    for s in order:
        if s not in completed:
            return s
    return None


def ensure_idea_filled(project_root: Path) -> None:
    idea = project_root / "00_idea.md"
    if not idea.is_file():
        raise FileNotFoundError(f"缺少创意文件: {idea}")
    text = idea.read_text(encoding="utf-8")
    # 模板未编辑：仍含占位例句且「一句话梗概」下无实质内容
    if "（例：" in text and "## 一句话梗概" in text:
        section = text.split("## 一句话梗概", 1)[-1].split("##", 1)[0]
        lines = [
            ln.strip()
            for ln in section.splitlines()
            if ln.strip() and not ln.strip().startswith("（例：")
        ]
        if not lines:
            raise ValueError("请先在 00_idea.md 中填写「一句话梗概」后再运行")
