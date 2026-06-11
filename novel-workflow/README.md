# 小说 Prompt 工作流

基于 **分阶段 Prompt + 摘要上下文 + LLM API** 的长篇小说创作工作流。每个阶段只加载必要上下文，用摘要替代全量正文，在保持剧情连贯的前提下控制 token 消耗。

技术栈：**Spring Boot 单体后端** + **Vue-free 静态前端** + **MySQL** 持久化，Docker 一键部署。

## 快速开始

```powershell
cd novel-workflow
copy .env.example .env

docker compose up -d --build
```

浏览器访问 **http://localhost:8765**，在 **设置** 页配置 API Key / Base URL / Model（写入数据库）。

浏览器访问 **http://localhost:8765**

详细说明见 [DOCKER.md](./DOCKER.md)。

## 目录结构

```
novel-workflow/
├── backend/                 # Spring Boot 单体 API
│   └── src/main/resources/
│       ├── config.yaml      # 篇幅 + API 默认配置
│       ├── stages.yaml        # 阶段 → 上下文/输出 映射
│       ├── prompts/           # Prompt 模板
│       └── _template/         # 新建项目模板
├── frontend/
│   ├── static/              # Web 控制台（HTML/CSS/JS）
│   ├── nginx.conf
│   └── Dockerfile
├── projects/
│   └── demo_novel/          # 示例数据（首次启动导入 MySQL）
├── docker-compose.yml
├── .env.example
├── DOCKER.md
├── WORKFLOW.md
└── QUICKSTART.md
```

## Web 控制台功能

- 项目管理、工作流进度、阶段执行
- 上下文预览（估算 prompt 字符数）
- 文件在线编辑
- 后台任务轮询（设定 / 章节 / 流水线 / 试运行）
- API 设置（模型、Base URL、Key）

## Token 优化策略

- **最小上下文包**：每阶段仅加载 `stages.yaml` 白名单文件
- **摘要替代正文**：设定摘要存 `meta/summaries/`，章间用 `ch{N}_summary.md`
- **前缀缓存友好**：固定槽位顺序 + 上下文块置前、任务指令置后（DeepSeek prefix cache）
- **用量可观测**：LLM 日志记录 `cache_hit_tokens` / `cache_miss_tokens`

## API 输出格式

模型须按以下格式返回：

```
===FILE:01_era_setting.md===
（Markdown 内容）
===END===
```

原始回复保存在 `meta/logs/{stage}_raw.md`，解析失败时可人工修复后重跑。

## 兼容 API

| 厂商 | OPENAI_BASE_URL 示例 |
|------|---------------------|
| OpenAI | `https://api.openai.com/v1` |
| DeepSeek | `https://api.deepseek.com/v1` |
| 本地 Ollama | `http://localhost:11434/v1` |

通过 Web **设置** 页配置模型名，或修改数据库 `nw_settings.model`。

## 相关文档

- [DOCKER.md](./DOCKER.md) — Docker 部署与运维
- [QUICKSTART.md](./QUICKSTART.md) — 5 分钟上手
- [WORKFLOW.md](./WORKFLOW.md) — 阶段说明与操作指南
- [backend/src/main/resources/prompts/context_packages.md](./backend/src/main/resources/prompts/context_packages.md) — 上下文包（手动模式参考）
