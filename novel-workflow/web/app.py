"""FastAPI Web 控制台。"""

from __future__ import annotations

import sys
from pathlib import Path
from typing import Any, Dict, List, Optional

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

# scripts/ 加入 path
SCRIPTS = Path(__file__).resolve().parent.parent
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

from novel_workflow.config import api_settings, load_config, load_stages  # noqa: E402
from novel_workflow.context import build_context_block, build_user_message, resolve_context_files, resolve_output_files  # noqa: E402
from novel_workflow.job_manager import job_manager  # noqa: E402
from novel_workflow.projects import (  # noqa: E402
    STAGE_LABELS,
    create_project,
    get_project_detail,
    list_files,
    list_projects,
    read_file,
    write_file,
)
from novel_workflow.runner import StageRunner  # noqa: E402

WEB_ROOT = Path(__file__).resolve().parent
STATIC_DIR = WEB_ROOT / "static"

app = FastAPI(title="小说 Prompt 工作流", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


class CreateProjectBody(BaseModel):
    id: str = Field(..., min_length=1, max_length=64)
    title: Optional[str] = None


class SaveFileBody(BaseModel):
    content: str


class RunSetupBody(BaseModel):
    stage: Optional[str] = None
    from_stage: Optional[str] = None
    dry_run: bool = False


class RunChapterBody(BaseModel):
    chapter: Optional[int] = None
    from_chapter: Optional[int] = None
    to_chapter: Optional[int] = None
    stage: Optional[str] = None
    dry_run: bool = False


class RunPipelineBody(BaseModel):
    setup_only: bool = False
    skip_setup: bool = False
    chapters: str = "1"
    dry_run: bool = False


class QuickstartBody(BaseModel):
    dry_run: bool = False


class SettingsBody(BaseModel):
    base_url: Optional[str] = None
    model: Optional[str] = None
    api_key: Optional[str] = None


def _mask_key(key: str) -> str:
    if not key:
        return ""
    if len(key) <= 8:
        return "***"
    return key[:4] + "..." + key[-4:]


@app.get("/api/health")
def health() -> Dict[str, str]:
    return {"status": "ok"}


@app.get("/api/config")
def get_config() -> Dict[str, Any]:
    cfg = load_config()
    api = api_settings(cfg)
    return {
        "novel": cfg.get("novel", {}),
        "api": {
            "model": api.get("model"),
            "base_url": api.get("base_url") or "https://api.openai.com/v1",
            "api_key_set": bool(api.get("api_key")),
            "api_key_masked": _mask_key(api.get("api_key", "")),
            "temperature": api.get("temperature"),
            "max_tokens": api.get("max_tokens"),
        },
        "setup_order": load_stages().get("setup_order", []),
        "stage_labels": STAGE_LABELS,
    }


@app.put("/api/settings")
def save_settings(body: SettingsBody) -> Dict[str, Any]:
    from novel_workflow.config import ROOT, load_dotenv

    env_path = ROOT / ".env"
    env: Dict[str, str] = {}
    if env_path.is_file():
        for line in env_path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            env[k.strip()] = v.strip()
    if body.base_url:
        env["OPENAI_BASE_URL"] = body.base_url
    if body.model:
        env["NOVEL_LLM_MODEL"] = body.model
    if body.api_key:
        env["OPENAI_API_KEY"] = body.api_key
    if not body.base_url and not body.model and not body.api_key:
        raise HTTPException(status_code=400, detail="无有效配置项")
    lines = [
        "# 小说工作流 API 配置",
        f"OPENAI_API_KEY={env.get('OPENAI_API_KEY', '')}",
        f"OPENAI_BASE_URL={env.get('OPENAI_BASE_URL', 'https://api.openai.com/v1')}",
    ]
    if env.get("NOVEL_LLM_MODEL"):
        lines.append(f"NOVEL_LLM_MODEL={env['NOVEL_LLM_MODEL']}")
    env_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    load_dotenv()
    return {"ok": True, "message": "配置已保存"}


@app.get("/api/projects")
def api_list_projects() -> List[Dict[str, Any]]:
    return list_projects()


@app.post("/api/projects")
def api_create_project(body: CreateProjectBody) -> Dict[str, Any]:
    try:
        return create_project(body.id, body.title)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e
    except FileExistsError as e:
        raise HTTPException(status_code=409, detail=str(e)) from e


@app.get("/api/projects/{project_id}")
def api_project_detail(project_id: str) -> Dict[str, Any]:
    try:
        return get_project_detail(project_id)
    except FileNotFoundError as e:
        raise HTTPException(status_code=404, detail=str(e)) from e


@app.get("/api/projects/{project_id}/files")
def api_list_files(project_id: str) -> List[Dict[str, Any]]:
    try:
        return list_files(project_id)
    except FileNotFoundError as e:
        raise HTTPException(status_code=404, detail=str(e)) from e


@app.get("/api/projects/{project_id}/file")
def api_read_file(project_id: str, path: str = Query(...)) -> Dict[str, Any]:
    try:
        return read_file(project_id, path)
    except (FileNotFoundError, ValueError) as e:
        raise HTTPException(status_code=404, detail=str(e)) from e


@app.put("/api/projects/{project_id}/file")
def api_write_file(project_id: str, body: SaveFileBody, path: str = Query(...)) -> Dict[str, Any]:
    try:
        return write_file(project_id, path, body.content)
    except (FileNotFoundError, ValueError) as e:
        raise HTTPException(status_code=400, detail=str(e)) from e


@app.get("/api/projects/{project_id}/context-preview")
def api_context_preview(
    project_id: str,
    stage: str = Query(...),
    chapter: Optional[int] = None,
) -> Dict[str, Any]:
    from novel_workflow.config import resolve_project

    def _analyze_file(root: Path, rel: str) -> Dict[str, Any]:
        from novel_workflow.context import read_project_file

        fp = root / rel
        if not fp.is_file():
            return {"path": rel, "exists": False, "ready": False, "chars": 0, "status": "missing"}
        raw = fp.read_text(encoding="utf-8")
        content = read_project_file(root, rel)
        if content is None:
            return {
                "path": rel,
                "exists": True,
                "ready": False,
                "chars": 0,
                "status": "placeholder",
                "preview": raw.strip()[:100],
            }
        return {
            "path": rel,
            "exists": True,
            "ready": True,
            "chars": len(raw),
            "status": "ok",
            "preview": content[:150],
        }

    try:
        root = resolve_project(project_id)
        stages = load_stages()
        chapter_stage = stage in stages.get("chapter_stages", {})
        ctx_files = resolve_context_files(stage, chapter, chapter_stage=chapter_stage)
        out_files = resolve_output_files(stage, chapter, chapter_stage=chapter_stage)
        file_details = [_analyze_file(root, rel) for rel in ctx_files]
        output_details = [_analyze_file(root, rel) for rel in out_files]
        missing_files = [f["path"] for f in file_details if not f["ready"]]

        try:
            block = build_context_block(root, ctx_files)
            user_msg = build_user_message(root, stage, out_files, chapter)
            context_chars = len(block)
            total_chars = len(user_msg)
            prompt_overhead = total_chars - context_chars
        except FileNotFoundError:
            from novel_workflow.context import load_system_prompt

            context_chars = sum(f["chars"] for f in file_details if f["ready"])
            prompt_overhead = len(load_system_prompt()) + 3300
            total_chars = context_chars + prompt_overhead

        return {
            "stage": stage,
            "chapter": chapter,
            "label": STAGE_LABELS.get(stage, stage),
            "context_files": ctx_files,
            "output_files": out_files,
            "file_details": file_details,
            "output_details": output_details,
            "missing_files": missing_files,
            "context_chars": context_chars,
            "prompt_overhead_chars": prompt_overhead,
            "total_chars": total_chars,
            "ready": len(missing_files) == 0,
        }
    except FileNotFoundError as e:
        raise HTTPException(status_code=404, detail=str(e)) from e
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e)) from e


@app.post("/api/projects/{project_id}/run/setup")
def run_setup(project_id: str, body: RunSetupBody) -> Dict[str, str]:
    stages_cfg = load_stages()
    order = stages_cfg.get("setup_order", [])

    def task() -> Dict[str, Any]:
        runner = StageRunner(novel_id=project_id, dry_run=body.dry_run)
        if body.stage:
            return runner.run_setup_stage(body.stage)
        start = 0
        if body.from_stage:
            start = order.index(body.from_stage)
        for stage in order[start:]:
            runner.run_setup_stage(stage)
        return {"stages": order[start:]}

    if body.stage:
        label = STAGE_LABELS.get(body.stage, body.stage)
    elif body.from_stage:
        label = f"设定（从 {STAGE_LABELS.get(body.from_stage, body.from_stage)} 起）"
    else:
        label = "全部设定阶段"

    job_id = job_manager.submit(project_id, "setup", label, task)
    return {"job_id": job_id}


@app.post("/api/projects/{project_id}/run/chapter")
def run_chapter(project_id: str, body: RunChapterBody) -> Dict[str, str]:
    def task() -> Dict[str, Any]:
        runner = StageRunner(novel_id=project_id, dry_run=body.dry_run)
        if body.stage and body.chapter:
            return runner.run_chapter_stage(body.stage, body.chapter)
        if body.chapter:
            runner.run_chapter_loop(body.chapter)
            return {"chapter": body.chapter}
        if body.from_chapter and body.to_chapter:
            for ch in range(body.from_chapter, body.to_chapter + 1):
                runner.run_chapter_loop(ch)
            return {"chapters": list(range(body.from_chapter, body.to_chapter + 1))}
        raise ValueError("请指定 chapter 或 from_chapter/to_chapter")

    if body.stage and body.chapter:
        label = f"第{body.chapter}章 · {STAGE_LABELS.get(body.stage, body.stage)}"
    elif body.from_chapter and body.to_chapter:
        label = f"第{body.from_chapter}–{body.to_chapter}章"
    else:
        label = f"第{body.chapter}章（完整循环）"

    job_id = job_manager.submit(project_id, "chapter", label, task)
    return {"job_id": job_id}


@app.post("/api/projects/{project_id}/run/pipeline")
def run_pipeline(project_id: str, body: RunPipelineBody) -> Dict[str, str]:
    from run_pipeline import parse_chapter_range

    def task() -> Dict[str, Any]:
        runner = StageRunner(novel_id=project_id, dry_run=body.dry_run)
        if not body.skip_setup:
            runner.run_all_setup()
        if body.setup_only:
            return {"setup_only": True}
        chapters = parse_chapter_range(body.chapters)
        for ch in chapters:
            runner.run_chapter_loop(ch)
        return {"chapters": chapters}

    if body.setup_only:
        label = "流水线 · 仅设定"
    else:
        label = f"流水线 · 章节 {body.chapters}"

    job_id = job_manager.submit(project_id, "pipeline", label, task)
    return {"job_id": job_id}


@app.post("/api/projects/{project_id}/run/quickstart")
def run_quickstart(project_id: str, body: QuickstartBody) -> Dict[str, str]:
    """从 idea 一键跑到第一章；dry_run 时仅生成试运行报告。"""

    def task() -> Dict[str, Any]:
        runner = StageRunner(novel_id=project_id, dry_run=body.dry_run)
        return runner.run_idea_to_chapter_one()

    label = "一键 · Idea → 第1章" + ("（试运行）" if body.dry_run else "")
    job_id = job_manager.submit(project_id, "quickstart", label, task)
    return {"job_id": job_id}


@app.post("/api/projects/{project_id}/run/dry-run")
def run_dry_run(project_id: str) -> Dict[str, Any]:
    """试运行：检查各阶段上下文就绪与 prompt 体量，不调用 API。"""
    from novel_workflow.dry_run import build_dry_run_report

    def task() -> Dict[str, Any]:
        return build_dry_run_report(project_id)

    job_id = job_manager.submit(project_id, "dry_run", "试运行报告", task)
    return {"job_id": job_id, "message": "试运行已提交"}


@app.get("/api/projects/{project_id}/idea-check")
def idea_check(project_id: str) -> Dict[str, Any]:
    """检查 00_idea.md 是否已填写，供一键启动前提示。"""
    from novel_workflow.config import resolve_project
    from novel_workflow.state import ensure_idea_filled

    try:
        root = resolve_project(project_id)
        ensure_idea_filled(root)
        content = (root / "00_idea.md").read_text(encoding="utf-8")
        return {"ready": True, "message": "创意已填写，可以一键启动"}
    except ValueError as e:
        return {"ready": False, "message": str(e)}
    except FileNotFoundError as e:
        raise HTTPException(status_code=404, detail=str(e)) from e


@app.get("/api/jobs")
def api_list_jobs(project_id: Optional[str] = None) -> List[Dict[str, Any]]:
    return job_manager.list_jobs(project_id=project_id)


@app.get("/api/jobs/{job_id}")
def api_get_job(job_id: str) -> Dict[str, Any]:
    job = job_manager.get_job(job_id)
    if not job:
        raise HTTPException(status_code=404, detail="任务不存在")
    return job.to_dict()


@app.delete("/api/jobs/{job_id}")
def api_delete_job(job_id: str) -> Dict[str, Any]:
    if not job_manager.delete_job(job_id):
        raise HTTPException(status_code=404, detail="任务不存在")
    return {"ok": True}


@app.get("/")
def index() -> FileResponse:
    return FileResponse(STATIC_DIR / "index.html")


app.mount("/static", StaticFiles(directory=str(STATIC_DIR)), name="static")
