#!/usr/bin/env python3
"""启动 Web 控制台。

用法:
  python run_web.py
  python run_web.py --port 8765
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WEB = ROOT / "web"
sys.path.insert(0, str(ROOT / "scripts"))


def main() -> int:
    parser = argparse.ArgumentParser(description="小说工作流 Web 控制台")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--reload", action="store_true", help="开发热重载")
    args = parser.parse_args()

    import uvicorn

    print(f"打开浏览器: http://{args.host}:{args.port}")
    uvicorn.run(
        "app:app",
        host=args.host,
        port=args.port,
        reload=args.reload,
        app_dir=str(WEB),
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
