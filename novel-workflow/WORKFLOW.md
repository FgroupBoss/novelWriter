# 工作流操作指南（API 模式）

## 环境准备

```powershell
cd novel-workflow
python -m venv .venv && .\.venv\Scripts\Activate.ps1
pip install -r scripts\requirements.txt
Copy-Item .env.example .env   # 填写 OPENAI_API_KEY
```

### 启动 Web 控制台

```powershell
cd scripts
python run_web.py
# 浏览器 http://127.0.0.1:8765
```

---

## 阶段总览

### 设定阶段（自动顺序见 `scripts/stages.yaml` → `setup_order`）

| 阶段名 | Prompt | 输出 |
|--------|--------|------|
| `era_setting` | 01_era_setting.md | 01 + era_summary |
| `characters` | 02_characters.md | 02 + characters_summary |
| `worldview` | 03_worldview.md | 03 + worldview_summary |
| `relationships` | 04_relationships.md | 04 + relationships_summary |
| `main_outline` | 05_main_outline.md | 05 + main_outline_summary |
| `sub_outline` | 06_sub_outline.md | 06 |
| `foreshadowing` | 07_foreshadowing.md | 07 |
| `material_library` | 08_material_library.md | 08 |
| `style_guide` | 11_style_guide_gen.md | 11 |
| `timeline` | 12_timeline_gen.md | 12 |
| `themes_symbols` | 13_themes_symbols.md | 13 |
| `pacing_notes` | 14_pacing_notes.md | 14 |
| `context_index` | 16_update_context_index.md | meta/context_index.md |

```powershell
cd scripts
python run_setup.py --project my_novel
# 断点续跑
python run_setup.py --project my_novel --from main_outline
```

### 正文循环（每章）

| 阶段名 | 说明 |
|--------|------|
| `chapter_write` | 生成 ch{N}.md + ch{N}_summary.md |
| `plot_update` | 更新 10_plot_progress、07 伏笔、12 时间线 |
| `chapter_review` | 生成 ch{N}_review.md 校验报告 |

```powershell
python run_chapter.py --project my_novel --chapter 1
python run_chapter.py --project my_novel --range 2 10
```

---

## 端到端

```powershell
# 设定 + 写 1–5 章
python run_pipeline.py --project my_novel --chapters 1-5

# 仅设定
python run_pipeline.py --project my_novel --setup-only

# 设定已完成，从第 6 章继续
python run_pipeline.py --project my_novel --skip-setup --chapters 6-10
```

---

## 单阶段调试

```powershell
python run_stage.py --project my_novel --stage foreshadowing
python run_stage.py --project my_novel --stage chapter_write --chapter 3

# 不调用 API，只看上下文大小
python run_stage.py --project my_novel --stage chapter_write --chapter 3 --dry-run
python build_context.py --project my_novel --stage chapter_write --chapter 3 --stats-only
```

---

## 状态与断点

- `meta/workflow_state.json` — 自动更新 `current_stage`、`current_chapter`、`completed_stages`
- `meta/logs/` — 每次 API 调用的原始回复，解析失败时用于排障
- 建议对 `projects/my_novel` 使用 git 管理，便于回滚章节

---

## 解析失败处理

1. 查看 `meta/logs/{stage}_raw.md`
2. 若内容正确但格式不对，手动补上 `===FILE:...===` / `===END===` 标记
3. 或调整 `config.yaml` 中 `api.model` / `max_tokens` 后重跑该阶段

---

## 手动模式（备用）

若 API 不可用，仍可按 `prompts/context_packages.md` 在 Cursor 中手动 `@` 文件执行，与 API 模式共用同一套项目文件。
