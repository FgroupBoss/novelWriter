"""后台任务管理（LLM 调用耗时较长，异步执行）。"""

from __future__ import annotations

import logging
import threading
import traceback
import uuid
from collections import deque
from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum
from typing import Any, Callable, Deque, Dict, List, Optional


class JobStatus(str, Enum):
    PENDING = "pending"
    RUNNING = "running"
    SUCCESS = "success"
    FAILED = "failed"


@dataclass
class Job:
    id: str
    project_id: str
    job_type: str
    label: str
    status: JobStatus = JobStatus.PENDING
    created_at: str = ""
    started_at: Optional[str] = None
    finished_at: Optional[str] = None
    result: Optional[Dict[str, Any]] = None
    error: Optional[str] = None
    logs: Deque[str] = field(default_factory=lambda: deque(maxlen=500))

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id": self.id,
            "project_id": self.project_id,
            "job_type": self.job_type,
            "label": self.label,
            "status": self.status.value,
            "created_at": self.created_at,
            "started_at": self.started_at,
            "finished_at": self.finished_at,
            "result": self.result,
            "error": self.error,
            "logs": list(self.logs),
        }


class _JobLogHandler(logging.Handler):
    def __init__(self, job: Job):
        super().__init__()
        self.job = job

    def emit(self, record: logging.LogRecord) -> None:
        try:
            msg = self.format(record)
            self.job.logs.append(msg)
        except Exception:
            pass


class JobManager:
    def __init__(self) -> None:
        self._jobs: Dict[str, Job] = {}
        self._lock = threading.Lock()

    def list_jobs(self, project_id: Optional[str] = None, limit: int = 50) -> List[Dict[str, Any]]:
        with self._lock:
            jobs = list(self._jobs.values())
        if project_id:
            jobs = [j for j in jobs if j.project_id == project_id]
        jobs.sort(key=lambda j: j.created_at, reverse=True)
        return [j.to_dict() for j in jobs[:limit]]

    def get_job(self, job_id: str) -> Optional[Job]:
        with self._lock:
            return self._jobs.get(job_id)

    def delete_job(self, job_id: str) -> bool:
        with self._lock:
            if job_id not in self._jobs:
                return False
            del self._jobs[job_id]
            return True

    def submit(
        self,
        project_id: str,
        job_type: str,
        label: str,
        fn: Callable[[], Any],
    ) -> str:
        job_id = uuid.uuid4().hex[:12]
        job = Job(
            id=job_id,
            project_id=project_id,
            job_type=job_type,
            label=label,
            created_at=_now(),
        )
        with self._lock:
            self._jobs[job_id] = job

        thread = threading.Thread(target=self._run, args=(job, fn), daemon=True)
        thread.start()
        return job_id

    def _run(self, job: Job, fn: Callable[[], Any]) -> None:
        job.status = JobStatus.RUNNING
        job.started_at = _now()

        handler = _JobLogHandler(job)
        handler.setFormatter(logging.Formatter("%(asctime)s [%(levelname)s] %(message)s", "%H:%M:%S"))
        root = logging.getLogger()
        root.addHandler(handler)
        old_level = root.level
        root.setLevel(logging.INFO)

        try:
            job.logs.append(f"开始: {job.label}")
            result = fn()
            job.result = result if isinstance(result, dict) else {"ok": True}
            job.status = JobStatus.SUCCESS
            job.logs.append("完成")
        except Exception as e:
            job.status = JobStatus.FAILED
            job.error = str(e)
            job.logs.append(f"失败: {e}")
            job.logs.append(traceback.format_exc())
        finally:
            job.finished_at = _now()
            root.removeHandler(handler)
            root.setLevel(old_level)


def _now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


job_manager = JobManager()
