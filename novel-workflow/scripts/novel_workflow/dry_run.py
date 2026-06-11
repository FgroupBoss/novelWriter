"""试运行：不调用 API，检查各阶段上下文就绪情况与 Token 体量。"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Dict, List, Optional

from .config import load_stages, resolve_project
from .context import build_user_message, resolve_context_files, resolve_output_files
from .projects import STAGE_LABELS
from .state import ensure_idea_filled


def _stage_list() -> List[Dict[str, Any]]:
    """设定阶段 + 第 1 章写作循环。"""
    stages_cfg = load_stages()
    items = []
    for sid in stages_cfg.get("setup_order", []):
        items.append({"id": sid, "chapter": None, "chapter_stage": False})
    for sid in stages_cfg.get("chapter_loop", []):
        items.append({"id": sid, "chapter": 1, "chapter_stage": True})
    return items


def build_dry_run_report(project_id: Optional[str] = None) -> Dict[str, Any]:
    root = resolve_project(project_id)
    idea_ready = True
    idea_message = "创意已填写"

    try:
        ensure_idea_filled(root)
    except (ValueError, FileNotFoundError) as e:
        idea_ready = False
        idea_message = str(e)

    stage_results: List[Dict[str, Any]] = []
    runnable = 0
    total_chars_sum = 0

    for item in _stage_list():
        sid = item["id"]
        chapter = item["chapter"]
        chapter_stage = item["chapter_stage"]
        label = STAGE_LABELS.get(sid, sid)
        if chapter:
            label = f"第{chapter}章 · {label}"

        entry: Dict[str, Any] = {
            "stage": sid,
            "label": label,
            "chapter": chapter,
            "ready": False,
        }

        try:
            ctx_files = resolve_context_files(sid, chapter, chapter_stage=chapter_stage)
            out_files = resolve_output_files(sid, chapter, chapter_stage=chapter_stage)
            user_msg = build_user_message(root, sid, out_files, chapter)
            entry.update(
                {
                    "ready": True,
                    "context_files": ctx_files,
                    "output_files": out_files,
                    "context_chars": _estimate_context_chars(root, ctx_files),
                    "total_chars": len(user_msg),
                }
            )
            runnable += 1
            total_chars_sum += len(user_msg)
        except FileNotFoundError as e:
            missing = _parse_missing_files(str(e))
            entry["blocked_by"] = missing
            entry["error"] = str(e)
        except Exception as e:
            entry["error"] = str(e)

        stage_results.append(entry)

    report: Dict[str, Any] = {
        "project_id": root.name,
        "idea_ready": idea_ready,
        "idea_message": idea_message,
        "stages": stage_results,
        "summary": {
            "runnable_stages": runnable,
            "total_stages": len(stage_results),
            "runnable_prompt_chars": total_chars_sum,
            "note": "仅统计当前可运行阶段的 prompt 字符；前置未生成时后续阶段会 blocked。",
        },
    }

    _save_report(root, report)
    return report


def _estimate_context_chars(root: Path, ctx_files: List[str]) -> int:
    total = 0
    for rel in ctx_files:
        path = root / rel
        if path.is_file():
            total += len(path.read_text(encoding="utf-8"))
    return total


def _parse_missing_files(error_msg: str) -> List[str]:
    lines = error_msg.split("\n")
    result = []
    for line in lines:
        line = line.strip()
        if line.startswith("- "):
            result.append(line[2:].strip())
    return result


def _save_report(root: Path, report: Dict[str, Any]) -> None:
    meta = root / "meta"
    meta.mkdir(parents=True, exist_ok=True)

    json_path = meta / "dry_run_report.json"
    json_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    md_path = meta / "dry_run_report.md"
    md_path.write_text(format_report_markdown(report), encoding="utf-8")


def format_report_markdown(report: Dict[str, Any]) -> str:
    lines = [
        "# 试运行报告",
        "",
        f"- **项目**: {report['project_id']}",
        f"- **创意状态**: {'✓ 已就绪' if report['idea_ready'] else '✗ 未就绪'} — {report['idea_message']}",
        "",
        "## 摘要",
        "",
        f"- 可运行阶段: **{report['summary']['runnable_stages']} / {report['summary']['total_stages']}**",
        f"- 可运行阶段 prompt 总字符: **{report['summary']['runnable_prompt_chars']:,}**",
        "",
        "## 各阶段",
        "",
        "| 阶段 | 状态 | 上下文字符 | 总 prompt | 说明 |",
        "|------|------|------------|-----------|------|",
    ]

    for s in report["stages"]:
        if s.get("ready"):
            status = "✓ 可运行"
            ctx = f"{s.get('context_chars', 0):,}"
            total = f"{s.get('total_chars', 0):,}"
            note = f"输出 {len(s.get('output_files', []))} 个文件"
        else:
            status = "✗ 阻塞"
            ctx = "—"
            total = "—"
            blocked = s.get("blocked_by") or []
            note = "缺少: " + ", ".join(blocked[:3])
            if len(blocked) > 3:
                note += f" 等{len(blocked)}项"

        lines.append(f"| {s['label']} | {status} | {ctx} | {total} | {note} |")

    lines.extend(["", "## 说明", "", report["summary"]["note"], ""])
    return "\n".join(lines)
