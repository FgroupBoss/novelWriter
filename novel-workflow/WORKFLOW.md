# 工作流操作指南

## 启动 Web 控制台

```powershell
cd novel-workflow
docker compose up -d
# 浏览器 http://localhost:8765
```

本地开发后端：`cd backend && mvn spring-boot:run`（需 MySQL，见 [DOCKER.md](./DOCKER.md)）

---

## 阶段总览

### 设定阶段（顺序见 `backend/src/main/resources/stages.yaml` → `setup_order`）

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

Web：**工作流** 页可逐阶段运行，或一键 **运行设定**。

### 正文循环（每章）

| 阶段名 | 说明 |
|--------|------|
| `chapter_write` | 生成 ch{N}.md + ch{N}_summary.md |
| `plot_update` | 更新 10_plot_progress、07 伏笔、12 时间线 |
| `chapter_review` | 生成 ch{N}_review.md 校验报告 |

Web：**章节** 页输入章号 → **运行本章**（自动执行 write → plot_update → review）。

---

## 端到端

| 操作 | Web 入口 |
|------|----------|
| Idea → 第 1 章 | 工作流 → **Idea → 第 1 章** |
| 仅设定 | 工作流 → **运行设定** |
| 写多章 | 章节 → 设置起止章号 → **运行范围** |
| 流水线 | 工作流 → **流水线**（可勾选仅设定 / 跳过设定） |

---

## 单阶段调试

- 工作流页点击某阶段 → 查看 **上下文预览**（字符数、缺失文件）
- **试运行**：不调用 API，检查各阶段是否可运行
- **dry-run 单阶段**：运行时可勾选试运行模式

---

## 状态与断点

- 工作流状态存 MySQL（`nw_project` + `nw_document` 中 `meta/workflow_state.json`）
- `meta/logs/` — 每次 API 调用的原始回复
- 项目数据在 MySQL 卷中持久化，容器重建不丢失

---

## 解析失败处理

1. 在 **文件** 页查看 `meta/logs/{stage}_raw.md`
2. 若内容正确但格式不对，手动补上 `===FILE:...===` / `===END===` 标记
3. 或在 **设置** 中调整模型 / 在 `backend/src/main/resources/config.yaml` 调整 `max_tokens` 后重跑

---

## 手动模式（备用）

若 API 不可用，仍可按 `backend/src/main/resources/prompts/context_packages.md` 在 Cursor 中手动 `@` 文件执行。
