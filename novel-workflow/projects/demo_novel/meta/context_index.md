# 上下文索引

> 自动生成索引，每完成一个设定阶段或每 10 章更新一次

## 项目状态

- 小说名/ID：末代符师 / demo_novel
- 当前阶段：pacing_notes（设定阶段已完成，正文写作待启动）
- 最新章节：0

## 文件摘要路由

| 文件 | 一行摘要 | 何时 @ |
|------|----------|--------|
| 00_idea.md | 用户原始创意 | 仅设定早期（已归档） |
| 01_era_setting.md | 时代背景与主要势力 | 修订时代设定时 |
| meta/summaries/*.md | 各设定压缩摘要（已自动生成？请确认） | **正文阶段优先 @** |
| 05_main_outline.md | 四卷80章主线大纲，含章标题与核心事件 | 写章时只读当前卷段落 |
| 06_sub_outline.md | 支线（SL-⋯）分卷分布与节点 | 写相关支线前 |
| 07_foreshadowing.md | 伏笔登记表（ID、内容、埋设章、回收章） | 每章写作与更新 |
| 08_timeline.md | 事件时间线（年月日） | 跨章时间校验 |
| 09_material_library.md | 设定素材库（阵法、符箓、丹药等） | 写相关内容查用 |
| 10_plot_progress.md | 滚动剧情状态（含已发布/待回收） | 每章写作 |
| 11_style_guide.md | 文风约束（用词、句式、对话格式） | 每章写作与校验 |
| 12_themes_symbols.md | 核心主题与象征物表 | 主题强化章节 |
| 13_pacing_notes.md | 全书情绪曲线与章节节奏标注 | 写章时参考节奏 |
| meta/summaries/era_summary.md | 时代背景摘要（含势力、经济、文化） | 正文阶段常用 |
| meta/summaries/characters_summary.md | 主要角色表格（姓名、身份、性格、弧光） | 写角色互动时 |
| meta/summaries/worldview_summary.md | 世界观规则摘要（符道、天机、灵气等） | 写修炼/战斗时 |
| meta/summaries/main_outline_vol1.md | 卷一主线摘要 | 写卷一每章时常用 |
| 09_chapters/ch*_summary.md | 单章摘要 | 写下一章时 @ 上一章 |

## 当前卷活跃信息

- **卷号**：卷一
- **章范围**：第 1 章 – 第 20 章
- **本卷大纲位置**：`05_main_outline.md` §卷一 ｜ `meta/summaries/main_outline_vol1.md`（若已生成）
- **活跃支线**：暂未定义（正文未动，支线待展开）
- **待回收伏笔（本卷）**：请查阅 `07_foreshadowing.md` 中埋设章 ≤ 20 且状态为「未回收」的条目

## 归档文件

| 文件 | 内容 | 何时 @ |
|------|------|--------|
| meta/summaries/plot_archive_vol*.md | 已完结卷事件压缩 | 当前卷 > 2 时可选（暂未生成） |

## 勿加载（除非修订）

- 已完成章节全文：`09_chapters/ch*.md` — 正文阶段仅 @ 对应 `ch*_summary.md`
- 陈旧设定草稿：若已汇总到 `meta/summaries/`，优先 @ 摘要
