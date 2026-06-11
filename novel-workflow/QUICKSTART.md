# 快速上手

## 1. 启动服务

```powershell
cd novel-workflow
copy .env.example .env

docker compose up -d --build
```

浏览器打开 **http://localhost:8765**

## 2. 配置 API（Web 设置页）

在 **设置** 中填写并保存（写入 MySQL `nw_settings` 表）：

- **API Key**
- **Base URL**（如 `https://api.deepseek.com/v1`）
- **Model**（如 `deepseek-chat`）

## 3. 使用 Web 控制台

1. 选择 **demo_novel** 或点击 **+** 新建项目
2. 在 **文件** 页编辑 `00_idea.md`（填写一句话梗概）
3. 在 **工作流** 页点击 **试运行** 检查各阶段上下文是否就绪
4. 点击 **Idea → 第 1 章** 一键启动

## 常用操作

| 需求 | Web 操作 |
|------|----------|
| 预览 token 体量 | 工作流 → 选择阶段 → 查看上下文预览 |
| 试运行 | 工作流 → 「试运行」 |
| 跑全部设定 | 工作流 → 「运行设定」 |
| 写单章 | 章节 → 输入章号 → 「运行本章」 |
| 重跑某设定阶段 | 工作流 → 点击对应阶段 → 「运行此阶段」 |

## Token 节约口诀

摘要进 `meta/summaries/`，章间用 `*_summary.md`，后端按 `stages.yaml` 自动组装最小上下文并保持前缀缓存友好顺序。

更多细节见 [WORKFLOW.md](./WORKFLOW.md) 与 [DOCKER.md](./DOCKER.md)。
