# 快速上手（API 模式）

## 1. 安装 & 配置（一次性）

```powershell
cd d:\project\demo03\novel-workflow
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r scripts\requirements.txt
Copy-Item .env.example .env
notepad .env   # 填 OPENAI_API_KEY、OPENAI_BASE_URL
```

## 2. 建项目 & 写创意

```powershell
Copy-Item -Recurse projects\_template projects\my_novel
notepad projects\my_novel\00_idea.md
```

## 3. 启动（本机）

```powershell
cd d:\project\demo03\novel-workflow
npm install
.\deploy.ps1
# 或: npm start
```

浏览器 **http://127.0.0.1:8765** → 选择 `demo_novel` → **试运行**

1. 点击 **+** 新建项目（或选择已有项目）
2. 在 **文件** tab 编辑 `00_idea.md`
3. 在 **工作流** tab 点击 **「Idea → 第 1 章」** 一键启动

或命令行：

```powershell
python run_pipeline.py --project my_novel --quickstart
```

## 4. 命令行（可选）

```powershell

```powershell
python run_chapter.py --project my_novel --range 2 5
```

## 常用命令

| 需求 | 命令 / 操作 |
|------|------|
| **可视化操作** | `python run_web.py` → 浏览器 |
| 只跑设定 | Web 工作流页 / `run_setup.py` |
| 单章 | `python run_chapter.py -p my_novel -c 3` |
| 预览 token | `python build_context.py -p my_novel -s chapter_write -c 3 --stats-only` |
| **试运行** | Web 点「试运行」/ `python run_dry_run.py -p my_novel` |
| 重跑某设定 | `python run_stage.py -p my_novel -s main_outline` |

## DeepSeek 示例

```env
OPENAI_API_KEY=sk-...
OPENAI_BASE_URL=https://api.deepseek.com/v1
NOVEL_LLM_MODEL=deepseek-chat
```

**Token 节约口诀**：摘要进 `meta/summaries/`，章间用 `*_summary.md`，脚本按 `stages.yaml` 自动组装最小上下文。
