"""项目文件树与 CRUD。"""

from __future__ import annotations

import re
import shutil
from pathlib import Path
from typing import Any, Dict, List, Optional

from .config import PROJECTS, load_config, load_stages, project_dir
from .state import load_state

STAGE_LABELS: Dict[str, str] = {
    "era_setting": "时代设定",
    "characters": "角色设定",
    "worldview": "世界观",
    "relationships": "关系网",
    "main_outline": "主线大纲",
    "sub_outline": "支线大纲",
    "foreshadowing": "伏笔与回收",
    "material_library": "素材库",
    "style_guide": "文风指南",
    "timeline": "时间线",
    "themes_symbols": "主题象征",
    "pacing_notes": "节奏说明",
    "context_index": "上下文索引",
    "chapter_write": "撰写正文",
    "plot_update": "更新剧情",
    "chapter_review": "章节校验",
}

PROJECT_ID_RE = re.compile(r"^[a-zA-Z0-9_-]+$")


def validate_project_id(project_id: str) -> None:
    if not project_id or project_id.startswith("_") or not PROJECT_ID_RE.match(project_id):
        raise ValueError("项目 ID 仅允许字母、数字、下划线、连字符，且不能以 _ 开头")


def safe_rel_path(rel_path: str) -> str:
    rel = rel_path.replace("\\", "/").lstrip("/")
    if ".." in rel.split("/"):
        raise ValueError("非法路径")
    return rel


def safe_file_path(root: Path, rel_path: str) -> Path:
    rel = safe_rel_path(rel_path)
    full = (root / rel).resolve()
    if not str(full).startswith(str(root.resolve())):
        raise ValueError("路径越界")
    return full


def list_projects() -> List[Dict[str, Any]]:
    if not PROJECTS.is_dir():
        return []
    stages = load_stages()
    setup_order = stages.get("setup_order", [])
    cfg = load_config()
    items = []
    for p in sorted(PROJECTS.iterdir()):
        if not p.is_dir() or p.name.startswith("_"):
            continue
        state = load_state(p)
        items.append(
            {
                "id": p.name,
                "title": state.get("title") or p.name,
                "current_stage": state.get("current_stage", "idea"),
                "current_chapter": state.get("current_chapter", 0),
                "completed_stages": state.get("completed_stages", []),
                "setup_done": len([s for s in state.get("completed_stages", []) if s in setup_order]),
                "setup_total": len(setup_order),
                "target_chapters": cfg.get("novel", {}).get("target_chapters", 80),
            }
        )
    return items


def create_project(project_id: str, title: Optional[str] = None) -> Dict[str, Any]:
    validate_project_id(project_id)
    template = PROJECTS / "_template"
    target = project_dir(project_id)
    if target.exists():
        raise FileExistsError(f"项目已存在: {project_id}")
    if not template.is_dir():
        raise FileNotFoundError("模板目录 _template 不存在")
    shutil.copytree(template, target)
    if title:
        from .state import load_state, save_state

        state = load_state(target)
        state["title"] = title
        state["novel_id"] = project_id
        save_state(target, state)
    return get_project_detail(project_id)


def get_project_detail(project_id: str) -> Dict[str, Any]:
    root = project_dir(project_id)
    if not root.is_dir():
        raise FileNotFoundError(f"项目不存在: {project_id}")
    state = load_state(root)
    stages = load_stages()
    setup_order = stages.get("setup_order", [])
    completed = set(state.get("completed_stages", []))
    setup_progress = [
        {
            "id": s,
            "label": STAGE_LABELS.get(s, s),
            "done": s in completed,
        }
        for s in setup_order
    ]
    cfg = load_config()
    return {
        "id": project_id,
        "title": state.get("title") or project_id,
        "state": state,
        "setup_progress": setup_progress,
        "setup_done": len([s for s in setup_progress if s["done"]]),
        "setup_total": len(setup_progress),
        "target_chapters": cfg.get("novel", {}).get("target_chapters", 80),
        "chapter_loop": [
            {"id": s, "label": STAGE_LABELS.get(s, s)}
            for s in stages.get("chapter_loop", [])
        ],
    }


def list_files(project_id: str) -> List[Dict[str, Any]]:
    root = project_dir(project_id)
    if not root.is_dir():
        raise FileNotFoundError(f"项目不存在: {project_id}")
    nodes: List[Dict[str, Any]] = []

    def walk(dir_path: Path, prefix: str = "") -> None:
        for item in sorted(dir_path.iterdir(), key=lambda x: (not x.is_dir(), x.name)):
            rel = f"{prefix}{item.name}" if not prefix else f"{prefix}/{item.name}"
            if item.is_dir():
                nodes.append({"path": rel, "name": item.name, "type": "dir"})
                walk(item, rel)
            elif item.suffix in (".md", ".json", ".yaml", ".yml"):
                nodes.append({"path": rel.replace("\\", "/"), "name": item.name, "type": "file"})

    walk(root)
    return nodes


def read_file(project_id: str, rel_path: str) -> Dict[str, Any]:
    root = project_dir(project_id)
    path = safe_file_path(root, rel_path)
    if not path.is_file():
        raise FileNotFoundError(f"文件不存在: {rel_path}")
    return {
        "path": safe_rel_path(rel_path),
        "content": path.read_text(encoding="utf-8"),
        "size": path.stat().st_size,
    }


def write_file(project_id: str, rel_path: str, content: str) -> Dict[str, Any]:
    root = project_dir(project_id)
    path = safe_file_path(root, rel_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    return {"path": safe_rel_path(rel_path), "size": len(content.encode("utf-8"))}
