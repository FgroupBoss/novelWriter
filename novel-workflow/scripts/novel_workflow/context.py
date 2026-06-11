"""按阶段组装最小上下文（DeepSeek 等前缀缓存友好）。"""

from __future__ import annotations

from pathlib import Path
from typing import List, Optional, Set

from .config import PROMPTS, load_stages

# 未加载文件的固定占位符（字节级稳定，便于前缀缓存对齐）
CONTEXT_SKIP_MARKER = "[本文件本阶段未加载]"

# 设定阶段：上下文按此顺序排列，使相邻阶段共享最长前缀
SETUP_CONTEXT_ORDER: List[str] = [
    "00_idea.md",
    "01_era_setting.md",
    "02_characters.md",
    "03_worldview.md",
    "04_relationships.md",
    "05_main_outline.md",
    "06_sub_outline.md",
    "07_foreshadowing.md",
    "08_material_library.md",
    "meta/summaries/era_summary.md",
    "meta/summaries/characters_summary.md",
    "meta/summaries/worldview_summary.md",
    "meta/summaries/relationships_summary.md",
    "meta/summaries/main_outline_summary.md",
    "11_style_guide.md",
    "12_timeline.md",
    "13_themes_symbols.md",
    "14_pacing_notes.md",
    "meta/context_index.md",
    "meta/workflow_state.json",
]

# 正文阶段：核心上下文（各章内 write → plot_update → review 共享此前缀）
CHAPTER_CONTEXT_CORE: List[str] = [
    "11_style_guide.md",
    "meta/summaries/era_summary.md",
    "meta/summaries/characters_summary.md",
    "meta/summaries/worldview_summary.md",
    "meta/summaries/relationships_summary.md",
    "meta/summaries/main_outline_summary.md",
    "05_main_outline.md",
    "06_sub_outline.md",
    "08_material_library.md",
    "13_themes_symbols.md",
    "14_pacing_notes.md",
    "10_plot_progress.md",
    "07_foreshadowing.md",
    "12_timeline.md",
]


def _format_path(template: str, chapter: Optional[int]) -> str:
    if chapter is None:
        return template
    prev = chapter - 1
    return template.format(chapter=chapter, prev=prev)


def _sort_by_order(files: List[str], order: List[str]) -> List[str]:
    rank = {p: i for i, p in enumerate(order)}

    def key(path: str) -> tuple:
        if path in rank:
            return (0, rank[path])
        if path.startswith("meta/summaries/"):
            return (1, path)
        if path.startswith("09_chapters/"):
            return (3, path)
        if path.startswith("meta/"):
            return (2, path)
        return (1, path)

    return sorted(dict.fromkeys(files), key=key)


def _chapter_context_slots(chapter: int) -> List[str]:
    """同一章三次调用使用固定槽位顺序；动态文件置后以提高前缀命中率。"""
    slots = list(CHAPTER_CONTEXT_CORE)
    if chapter > 1:
        slots.append(_format_path("09_chapters/ch{prev:03d}_summary.md", chapter))
    slots.append(_format_path("09_chapters/ch{chapter:03d}.md", chapter))
    slots.append(_format_path("09_chapters/ch{chapter:03d}_summary.md", chapter))
    # review 为本阶段输出，不作为上下文加载
    return slots


def _chapter_allow_missing(stage_name: str, chapter: int) -> Set[str]:
    """尚未生成的文件用固定 SKIP 占位，保持槽位结构一致。"""
    ch = _format_path("09_chapters/ch{chapter:03d}.md", chapter)
    summary = _format_path("09_chapters/ch{chapter:03d}_summary.md", chapter)
    if stage_name == "chapter_write":
        return {ch, summary}
    return set()


def resolve_context_files(
    stage_name: str,
    chapter: Optional[int] = None,
    *,
    chapter_stage: bool = False,
    cache_friendly: bool = True,
) -> List[str]:
    stages = load_stages()
    if chapter_stage:
        if cache_friendly and chapter is not None:
            return _chapter_context_slots(chapter)
        spec = stages.get("chapter_stages", {}).get(stage_name, {})
    else:
        spec = stages.get("stages", {}).get(stage_name, {})

    if not spec:
        raise KeyError(f"未知阶段: {stage_name}")

    files: List[str] = list(spec.get("context", []))
    if chapter and chapter > 1:
        for tpl in spec.get("context_if_chapter_gt_1", []):
            files.append(_format_path(tpl, chapter))
    formatted = [_format_path(f, chapter) if "{chapter" in f or "{prev" in f else f for f in files]
    if chapter_stage or not cache_friendly:
        return formatted
    return _sort_by_order(formatted, SETUP_CONTEXT_ORDER)


def resolve_output_files(stage_name: str, chapter: Optional[int] = None, *, chapter_stage: bool = False) -> List[str]:
    stages = load_stages()
    if chapter_stage:
        spec = stages.get("chapter_stages", {}).get(stage_name, {})
    else:
        spec = stages.get("stages", {}).get(stage_name, {})
    return [_format_path(f, chapter) for f in spec.get("outputs", [])]


def resolve_prompt_file(stage_name: str, chapter: Optional[int] = None, *, chapter_stage: bool = False) -> str:
    stages = load_stages()
    if chapter_stage:
        spec = stages.get("chapter_stages", {}).get(stage_name, {})
    else:
        spec = stages.get("stages", {}).get(stage_name, {})
    return spec.get("prompt", "")


def read_project_file(project_root: Path, rel_path: str) -> Optional[str]:
    path = project_root / rel_path
    if not path.is_file():
        return None
    text = path.read_text(encoding="utf-8").strip()
    if not text:
        return None
    if _is_placeholder_content(text):
        return None
    return text


def _is_placeholder_content(text: str) -> bool:
    """判断文件是否仍为模板占位（未生成）。"""
    substantive: List[str] = []
    for line in text.splitlines():
        s = line.strip()
        if not s or s.startswith("#") or s.startswith(">") or s.startswith("<!--"):
            continue
        substantive.append(s)
    if not substantive:
        return True
    joined = " ".join(substantive)
    if joined in ("（待生成）", "(待生成)"):
        return True
    if len(joined) <= 20 and "待生成" in joined:
        return True
    return False


def build_context_block(
    project_root: Path,
    rel_paths: List[str],
    *,
    allow_missing: Optional[Set[str]] = None,
) -> str:
    allow_missing = allow_missing or set()
    parts: List[str] = []
    missing: List[str] = []
    for rel in rel_paths:
        content = read_project_file(project_root, rel)
        if content is None:
            if rel in allow_missing:
                parts.append(
                    f"===CONTEXT_FILE: {rel}===\n{CONTEXT_SKIP_MARKER}\n===END_CONTEXT==="
                )
                continue
            missing.append(rel)
            continue
        parts.append(f"===CONTEXT_FILE: {rel}===\n{content}\n===END_CONTEXT===")
    if missing:
        raise FileNotFoundError(
            "以下上下文文件缺失或尚未生成，请先完成前置阶段:\n  - " + "\n  - ".join(missing)
        )
    return "\n\n".join(parts)


def load_system_prompt() -> str:
    """系统消息：静态规则 + 通用输出格式（前缀稳定，利于缓存）。"""
    base = (PROMPTS / "00_system_base.md").read_text(encoding="utf-8")
    output_rules = (PROMPTS / "00_output_format_api.md").read_text(encoding="utf-8")
    return f"{base.strip()}\n\n---\n\n{output_rules.strip()}\n"


def load_task_prompt(stage_name: str, chapter: Optional[int] = None) -> str:
    stages = load_stages()
    chapter_stage = stage_name in stages.get("chapter_stages", {})
    setup_stage = stage_name in stages.get("stages", {})
    if chapter_stage:
        prompt_file = stages["chapter_stages"][stage_name]["prompt"]
    elif setup_stage:
        prompt_file = stages["stages"][stage_name]["prompt"]
    else:
        raise KeyError(f"未知阶段: {stage_name}")

    text = (PROMPTS / prompt_file).read_text(encoding="utf-8")
    if chapter is not None:
        text = text.replace("{N}", str(chapter)).replace("{NNN}", f"{chapter:03d}")
    return text


def build_user_message(
    project_root: Path,
    stage_name: str,
    output_files: List[str],
    chapter: Optional[int] = None,
) -> str:
    """
    用户消息结构（DeepSeek 前缀缓存优化）：
    1. 上下文块（固定顺序，动态文件置后）
    2. 当前任务（阶段指令、章号、输出清单 — 置后以便命中缓存）
    """
    stages = load_stages()
    chapter_stage = stage_name in stages.get("chapter_stages", {})
    context_files = resolve_context_files(
        stage_name, chapter, chapter_stage=chapter_stage, cache_friendly=True
    )
    allow_missing: Set[str] = set()
    if chapter_stage and chapter is not None:
        allow_missing = _chapter_allow_missing(stage_name, chapter)

    context_block = build_context_block(project_root, context_files, allow_missing=allow_missing)
    task = load_task_prompt(stage_name, chapter)

    output_spec = "\n".join(f"  - {p}" for p in output_files)
    file_format = "\n".join(
        f"===FILE:{p}===\n（此处写入 {p} 的完整 Markdown 内容）\n===END==="
        for p in output_files
    )

    chapter_line = ""
    if chapter is not None:
        chapter_line = f"chapter: {chapter}  (ch{chapter:03d})\n"

    return f"""## 已加载的上下文文件

{context_block}

---

## 当前任务

> **落盘格式**：上文任务描述中的 markdown 代码块仅为**内容结构示例**；你必须使用下方 `===FILE:路径===` 块输出，不要用纯 Markdown 正文或 ``` 代码块代替。

stage_id: {stage_name}
{chapter_line}
{task.strip()}

### 本阶段输出文件（共 {len(output_files)} 个）

{output_spec}

格式模板（逐块输出，不要省略标记）：

{file_format}
"""
