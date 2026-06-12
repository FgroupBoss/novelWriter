# Docker 本地部署

Spring Boot 单体后端 + Nginx 前端 + MySQL 持久化。

## 前置条件

- Docker Desktop（或 Docker Engine + Compose v2）
- LLM API Key（DeepSeek / OpenAI 兼容端点）

## 快速启动

```powershell
cd novel-workflow

# 复制环境变量模板
copy .env.example .env

docker compose up -d --build
```

浏览器访问：**http://localhost:8765**

## 服务说明

| 服务 | 容器名 | 端口 | 说明 |
|------|--------|------|------|
| frontend | novel-frontend | 8765→80 | Nginx 静态页 + `/api` 反向代理 |
| backend | novel-backend | 8080（内部） | Spring Boot 单体 API |
| mysql | novel-mysql | 3306 | MySQL 8，数据卷 `mysql_data` |

## 数据持久化

- **MySQL 卷**：`mysql_data` 挂载至 `/var/lib/mysql`，重启容器数据不丢失
- **项目文档**：存入 `nw_document` 表（Markdown / JSON 内容）
- **工作流状态**：`nw_project.state_json` + `meta/workflow_state.json` 文档同步
- **LLM 日志**：`nw_llm_log` 记录 token 用量与 prefix cache 命中

首次启动若数据库为空，会自动从 `projects/demo_novel/` 导入示例项目。

## 环境变量

| 变量 | 说明 |
|------|------|
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 |
| `APP_PORT` | 前端对外端口，默认 8765 |

**LLM API Key / Base URL / Model** 不在环境变量中配置，请在 Web **设置** 页保存（写入 `nw_settings` 表，随 MySQL 卷持久化）。

DeepSeek 示例（在 Web 设置页填写）：

- Base URL: `https://api.deepseek.com/v1`
- Model: `deepseek-chat`
- API Key: `sk-...`

## Token / 前缀缓存优化

后端 `ContextAssemblyService` 实现以下策略：

- `SETUP_CONTEXT_ORDER` / `CHAPTER_CONTEXT_CORE` 固定槽位顺序
- 用户消息结构：**上下文块 → 任务指令 → 输出模板**（动态内容置后）
- `CONTEXT_SKIP_MARKER` 占位保持字节级稳定
- LLM 调用日志记录 `cache_hit_tokens` / `cache_miss_tokens`

建议使用 DeepSeek 等支持 prefix cache 的模型以获得最佳命中率。

## 常用命令

```powershell
# 查看日志
docker compose logs -f backend

# 停止
docker compose down

# 停止并删除 MySQL 卷（清空数据）
docker compose down -v

# 仅重建后端
docker compose up -d --build backend
```

## 本地开发（不用 Docker）

```powershell
# 启动 MySQL（或用 docker compose up -d mysql）
cd backend
mvn spring-boot:run
```

前端通过 Docker 访问，或自行代理到 `http://localhost:8080`。
