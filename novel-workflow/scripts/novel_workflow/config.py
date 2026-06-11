"""配置与工作区路径。"""

from __future__ import annotations

import os
from pathlib import Path
from typing import Any, Dict, Optional

import yaml

ROOT = Path(__file__).resolve().parents[2]  # novel-workflow/
SCRIPTS = ROOT / "scripts"
PROMPTS = ROOT / "prompts"
PROJECTS = ROOT / "projects"


def load_yaml(path: Path) -> Dict[str, Any]:
    with path.open("r", encoding="utf-8") as f:
        return yaml.safe_load(f) or {}


def load_config() -> Dict[str, Any]:
    return load_yaml(ROOT / "config.yaml")


def load_stages() -> Dict[str, Any]:
    return load_yaml(SCRIPTS / "stages.yaml")


def project_dir(novel_id: str) -> Path:
    return PROJECTS / novel_id


def resolve_project(novel_id: Optional[str]) -> Path:
    cfg = load_config()
    nid = novel_id or cfg.get("project", {}).get("default_id", "my_novel")
    path = project_dir(nid)
    if not path.is_dir():
        raise FileNotFoundError(f"项目目录不存在: {path}")
    return path


def load_dotenv() -> None:
    try:
        from dotenv import load_dotenv as _load

        for candidate in (ROOT / ".env", ROOT.parent / ".env"):
            if candidate.is_file():
                _load(candidate)
                return
        _load()
    except ImportError:
        pass


def api_settings(cfg: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    cfg = cfg or load_config()
    api = cfg.get("api", {})
    load_dotenv()

    key_env = api.get("api_key_env", "OPENAI_API_KEY")
    base_env = api.get("base_url_env", "OPENAI_BASE_URL")
    model_env = api.get("model_env", "NOVEL_LLM_MODEL")

    api_key = os.environ.get(key_env, "")
    base_url = os.environ.get(base_env, "") or None
    model = os.environ.get(model_env, "") or api.get("model", "gpt-4o")

    return {
        "api_key": api_key,
        "base_url": base_url,
        "model": model,
        "temperature": api.get("temperature", 0.7),
        "max_tokens": api.get("max_tokens", 16384),
        "timeout": api.get("timeout_seconds", 300),
        "retry_max": api.get("retry", {}).get("max_attempts", 3),
        "retry_delay": api.get("retry", {}).get("delay_seconds", 5),
    }
