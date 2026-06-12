# 上下文包速查（各阶段应 @ 的文件）

> **DeepSeek 前缀缓存**：实际 API 请求由后端 `ContextAssemblyService` 组装——**上下文块在前、阶段任务在后**；正文三阶段（写/更新/校验）共用固定槽位顺序，动态章节文件置后。日志中关注 `cache_hit` / `cache_miss`。

路径均相对于 `projects/{novel_id}/`。**只 @ 列出的文件**，不要加载全部章节正文。

---

## 设定阶段

### 1 时代设定
```
@00_idea.md
@config.yaml（可选，了解目标篇幅）
```

### 2 角色设定
```
@00_idea.md
@01_era_setting.md
@meta/summaries/era_summary.md（若已有）
```

### 3 世界观
```
@00_idea.md
@meta/summaries/era_summary.md
@meta/summaries/characters_summary.md
@02_characters.md（需要细节时）
```

### 4 关系网
```
@meta/summaries/characters_summary.md
@meta/summaries/worldview_summary.md
@02_characters.md
@03_worldview.md
```

### 5 主线大纲（长篇）
```
@00_idea.md
@meta/summaries/era_summary.md
@meta/summaries/characters_summary.md
@meta/summaries/worldview_summary.md
@meta/summaries/relationships_summary.md
@04_relationships.md（可选）
```

### 6 支线大纲
```
@05_main_outline.md
@meta/summaries/characters_summary.md
@meta/summaries/relationships_summary.md
```

### 7 伏笔与回收
```
@05_main_outline.md
@06_sub_outline.md
@meta/summaries/main_outline_summary.md
```

### 8 素材库
```
@meta/summaries/era_summary.md
@meta/summaries/characters_summary.md
@meta/summaries/worldview_summary.md
@05_main_outline.md
@07_foreshadowing.md
```

### 9 文风指南
```
@00_idea.md
@01_era_setting.md
@02_characters.md
@meta/summaries/worldview_summary.md
```

### 10 时间线
```
@05_main_outline.md
@06_sub_outline.md
@01_era_setting.md
```

### 11 主题与象征
```
@00_idea.md
@05_main_outline.md
@08_material_library.md
```

### 12 节奏说明
```
@05_main_outline.md
@06_sub_outline.md
@14_pacing_notes.md（若修订）
```

---

## 正文：第 N 章（核心 — 最小上下文包）

写 **ch{N}** 时 @ 以下文件（**不要 @ 已完成章节的完整正文**）：

```
@11_style_guide.md
@meta/summaries/era_summary.md
@meta/summaries/characters_summary.md
@meta/summaries/worldview_summary.md
@meta/summaries/relationships_summary.md
@meta/summaries/main_outline_summary.md
@10_plot_progress.md
@07_foreshadowing.md          # 仅阅读「本章相关」行，可在对话中注明
@12_timeline.md               # 确认本章时间点
@13_themes_symbols.md         # 可选
@14_pacing_notes.md           # 当前卷/章节奏提示
@05_main_outline.md           # 仅阅读第 N 章对应段落（对话中指定章号）
@06_sub_outline.md            # 仅阅读与本章相关的支线
@08_material_library.md       # 按需取用素材，不必全文加载时可只 @ 相关小节
@09_chapters/ch{N-1}_summary.md   # N>1 时；第一章跳过
```

若 `10_plot_progress.md` 过长，额外 @ `meta/summaries/plot_archive_vol{V}.md`（已归档卷）。

---

## 剧情推进更新（第 N 章写完后）

```
@09_chapters/ch{N}.md
@09_chapters/ch{N}_summary.md
@10_plot_progress.md
@07_foreshadowing.md
@12_timeline.md
@05_main_outline.md           # 第 N 章段落，核对是否偏离
```

---

## 章节校验（第 N 章）

```
@09_chapters/ch{N}.md
@10_plot_progress.md
@07_foreshadowing.md
@12_timeline.md
@05_main_outline.md           # 第 N 章
@11_style_guide.md
```

---

## 更新 context_index

```
@meta/context_index.md
@projects 下各已完成的设定/摘要文件（按需）
@prompts/16_update_context_index.md
```
