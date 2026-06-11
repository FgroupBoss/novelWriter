"""OpenAI 兼容 API 客户端。"""

from __future__ import annotations

import logging
import time
from typing import Any, Dict, List, Optional

from .config import api_settings

log = logging.getLogger(__name__)


class LLMClient:
    def __init__(self, settings: Optional[Dict[str, Any]] = None):
        self.settings = settings or api_settings()
        if not self.settings.get("api_key"):
            raise ValueError(
                "未设置 API Key。请复制 .env.example 为 .env 并填写 OPENAI_API_KEY，"
                "或 export OPENAI_API_KEY=..."
            )
        self._client = self._build_client()

    def _build_client(self):
        from openai import OpenAI

        kwargs: Dict[str, Any] = {"api_key": self.settings["api_key"]}
        if self.settings.get("base_url"):
            kwargs["base_url"] = self.settings["base_url"]
        return OpenAI(**kwargs, timeout=self.settings.get("timeout", 300))

    def chat(self, system: str, user: str) -> str:
        max_attempts = int(self.settings.get("retry_max", 3))
        delay = float(self.settings.get("retry_delay", 5))
        last_err: Optional[Exception] = None

        for attempt in range(1, max_attempts + 1):
            try:
                log.info(
                    "调用 LLM model=%s attempt=%s/%s",
                    self.settings["model"],
                    attempt,
                    max_attempts,
                )
                resp = self._client.chat.completions.create(
                    model=self.settings["model"],
                    temperature=self.settings.get("temperature", 0.7),
                    max_tokens=self.settings.get("max_tokens", 16384),
                    messages=[
                        {"role": "system", "content": system},
                        {"role": "user", "content": user},
                    ],
                )
                content = resp.choices[0].message.content or ""
                if not content.strip():
                    raise RuntimeError("模型返回空内容")
                usage = getattr(resp, "usage", None)
                if usage:
                    hit = getattr(usage, "prompt_cache_hit_tokens", None)
                    miss = getattr(usage, "prompt_cache_miss_tokens", None)
                    if hit is not None or miss is not None:
                        log.info(
                            "Token usage: prompt=%s completion=%s total=%s cache_hit=%s cache_miss=%s",
                            getattr(usage, "prompt_tokens", "?"),
                            getattr(usage, "completion_tokens", "?"),
                            getattr(usage, "total_tokens", "?"),
                            hit if hit is not None else "?",
                            miss if miss is not None else "?",
                        )
                    else:
                        log.info(
                            "Token usage: prompt=%s completion=%s total=%s",
                            getattr(usage, "prompt_tokens", "?"),
                            getattr(usage, "completion_tokens", "?"),
                            getattr(usage, "total_tokens", "?"),
                        )
                return content
            except Exception as e:
                last_err = e
                log.warning("LLM 调用失败 attempt=%s: %s", attempt, e)
                if attempt < max_attempts:
                    time.sleep(delay)
        raise RuntimeError(f"LLM 调用失败（已重试 {max_attempts} 次）: {last_err}") from last_err
