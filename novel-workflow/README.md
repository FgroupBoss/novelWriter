# 小说 Prompt 工作流

基于 **文件缓存 + 分阶段 Prompt + LLM API** 的长篇小说创作工作流。每个阶段只加载必要上下文并落盘，用摘要替代全量正文，在保持剧情连贯的前提下控制 token 消耗。

## 快速开始

### 1. 安装依赖

```powershell
cd d:\project\demo03\novel-workflow
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r scripts\requirements.txt
```

### 2. 配置 API

```powershell
Copy-Item .env.example .env
# 编辑 .env，填写 OPENAI_API_KEY 与 OPENAI_BASE_URL（兼容 DeepSeek、通义等）
```

`config.yaml` 中可调整 `api.model`、`temperature`、`max_tokens`。

### 3. 新建项目

```powershell
Copy-Item -Recurse projects\_template projects\my_novel
```

编辑 `projects/my_novel/00_idea.md`，写入创意。

### 4. 运行

**Web 控制台（Node，推荐）**

```powershell
cd d:\project\demo03\novel-workflow
npm install
npm start
# 或: .\deploy.ps1
# 浏览器 http://127.0.0.1:8765
```

- 试运行 / 文件 / 项目管理：**只需 Node.js**
- 正式 LLM 生成：另需 Python + `pip install -r scripts/requirements.txt` + `.env` 配置 API Key

**Python CLI（可选）**

```powershell
cd scripts
python run_pipeline.py --project my_novel --chapters 1-3
python run_setup.py --project my_novel
python run_dry_run.py --project demo_novel
```

## CLI 命令一览

| 命令 | 说明 |
|------|------|
| `npm start` / `deploy.ps1` | **Web 控制台（Node）** http://127.0.0.1:8765 |
| `run_web.py` | Web 控制台（Python / FastAPI Alternate） |
| `run_pipeline.py` | 设定 + 按章写作；`--setup-only` / `--skip-setup` / `--chapters 1-5` |
| `run_setup.py` | 依次跑完 12 个设定阶段；`--from foreshadowing` 断点续跑 |
| `run_chapter.py` | 单章或范围：`--chapter 3` / `--range 5 10` |
| `run_stage.py` | 单阶段：`--stage era_setting` 或 `--stage chapter_write --chapter 3` |
| `build_context.py` | 预览将发送的上下文；`--stats-only` 只看字符数 |

## 目录结构

```
novel-workflow/
├── README.md / WORKFLOW.md / QUICKSTART.md
├── config.yaml              # 篇幅 + API 配置
├── .env.example             # API 密钥（复制为 .env）
├── prompts/                 # Prompt 模板（与 API 共用）
├── scripts/
│   ├── requirements.txt
│   ├── run_web.py           # Web 控制台入口
│   ├── stages.yaml          # 阶段 → 上下文/输出 映射
│   ├── run_*.py             # CLI 入口
│   └── novel_workflow/      # 核心库
├── package.json             # Node 部署
├── deploy.ps1               # 本机一键部署
├── web/
│   ├── server.mjs           # Node API 服务（默认）
│   ├── app.py               # FastAPI（可选）
│   └── static/              # 前端页面
└── projects/{novel_id}/
    ├── 00_idea.md … 14_pacing_notes.md
    ├── 09_chapters/
    ├── 10_plot_progress.md
    └── meta/
        ├── workflow_state.json
        ├── context_index.md
        ├── summaries/
        └── logs/              # API 原始回复（排障用）
```

## API 输出格式

模型须按以下格式返回（脚本自动注入 Prompt）：

```
===FILE:01_era_setting.md===
（Markdown 内容）
===END===
===FILE:meta/summaries/era_summary.md===
（摘要内容）
===END===
```

原始回复保存在 `meta/logs/{stage}_raw.md`，解析失败时可人工修复后重跑。

## 长篇适配

- 默认 **80 章 / 4 卷**，见 `config.yaml`
- 写第 N 章时只加载 `meta/summaries/` + 上一章摘要 + 剧情推进记录
- 阶段与上下文映射见 `scripts/stages.yaml`（与 `prompts/context_packages.md` 一致）

## 兼容 API

任意 **OpenAI 兼容** 端点均可：

| 厂商 | OPENAI_BASE_URL 示例 |
|------|---------------------|
| OpenAI | `https://api.openai.com/v1` |
| DeepSeek | `https://api.deepseek.com/v1` |
| 本地 Ollama | `http://localhost:11434/v1` |

通过 `NOVEL_LLM_MODEL` 环境变量覆盖模型名。

## 相关文档

- [WORKFLOW.md](./WORKFLOW.md) — 阶段说明与断点续跑
- [QUICKSTART.md](./QUICKSTART.md) — 5 分钟上手
- [prompts/context_packages.md](./prompts/context_packages.md) — 上下文包（手动模式参考）
