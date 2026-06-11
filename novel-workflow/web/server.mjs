#!/usr/bin/env node
/**
 * Node 部署入口：静态页面 + API（试运行/文件/项目纯 Node；LLM 任务调用 Python 子进程）
 */
import express from "express";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { spawn } from "child_process";
import yaml from "js-yaml";
import dotenv from "dotenv";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "..");
const PROJECTS = path.join(ROOT, "projects");
const SCRIPTS = path.join(ROOT, "scripts");
const STATIC = path.join(__dirname, "static");

dotenv.config({ path: path.join(ROOT, ".env") });

const STAGE_LABELS = {
  era_setting: "时代设定",
  characters: "角色设定",
  worldview: "世界观",
  relationships: "关系网",
  main_outline: "主线大纲",
  sub_outline: "支线大纲",
  foreshadowing: "伏笔与回收",
  material_library: "素材库",
  style_guide: "文风指南",
  timeline: "时间线",
  themes_symbols: "主题象征",
  pacing_notes: "节奏说明",
  context_index: "上下文索引",
  chapter_write: "撰写正文",
  plot_update: "更新剧情",
  chapter_review: "章节校验",
};

const jobs = new Map();
let jobSeq = 0;

function loadConfig() {
  return yaml.load(fs.readFileSync(path.join(ROOT, "config.yaml"), "utf8"));
}

function loadStages() {
  return yaml.load(fs.readFileSync(path.join(SCRIPTS, "stages.yaml"), "utf8"));
}

function findPython() {
  const candidates = [
    process.env.PYTHON,
    path.join(ROOT, ".venv/Scripts/python.exe"),
    path.join(process.env.LOCALAPPDATA || "", "Programs/Python/Python312/python.exe"),
    path.join(process.env.LOCALAPPDATA || "", "Programs/Python/Python311/python.exe"),
    "python",
    "python3",
  ].filter(Boolean);
  for (const p of candidates) {
    if (p.includes("/") || p.includes("\\")) {
      if (fs.existsSync(p)) return p;
    }
  }
  return "python";
}

function safeProjectFile(projectId, relPath) {
  if (!relPath || relPath.includes("..")) throw new Error("非法路径");
  const root = path.resolve(path.join(PROJECTS, projectId));
  const full = path.resolve(root, relPath.replace(/\\/g, "/"));
  if (!full.startsWith(root)) throw new Error("路径越界");
  return full;
}

function readEnvFile() {
  const envPath = path.join(ROOT, ".env");
  if (!fs.existsSync(envPath)) return {};
  const env = {};
  for (const line of fs.readFileSync(envPath, "utf8").split("\n")) {
    const t = line.trim();
    if (!t || t.startsWith("#")) continue;
    const i = t.indexOf("=");
    if (i > 0) env[t.slice(0, i).trim()] = t.slice(i + 1).trim();
  }
  return env;
}

function writeEnvFile(updates) {
  const envPath = path.join(ROOT, ".env");
  const env = readEnvFile();
  Object.assign(env, updates);
  const lines = [
    "# 小说工作流 API 配置（由 Web 设置页保存）",
    `OPENAI_API_KEY=${env.OPENAI_API_KEY || ""}`,
    `OPENAI_BASE_URL=${env.OPENAI_BASE_URL || "https://api.openai.com/v1"}`,
  ];
  if (env.NOVEL_LLM_MODEL) lines.push(`NOVEL_LLM_MODEL=${env.NOVEL_LLM_MODEL}`);
  fs.writeFileSync(envPath, lines.join("\n") + "\n", "utf8");
  dotenv.config({ path: envPath, override: true });
}

function isPlaceholder(text) {
  const lines = text.split("\n").map((l) => l.trim()).filter(
    (l) => l && !l.startsWith("#") && !l.startsWith(">"),
  );
  if (!lines.length) return true;
  const joined = lines.join(" ");
  return joined.includes("待生成") && joined.length <= 30;
}

function readProjectFile(root, rel) {
  const fp = path.join(root, rel);
  if (!fs.existsSync(fp)) return null;
  const text = fs.readFileSync(fp, "utf8").trim();
  if (!text || isPlaceholder(text)) return null;
  return text;
}

function ensureIdea(root) {
  const ideaPath = path.join(root, "00_idea.md");
  if (!fs.existsSync(ideaPath)) throw new Error("缺少 00_idea.md");
  const text = fs.readFileSync(ideaPath, "utf8");
  if (text.includes("（例：") && text.includes("一句话梗概")) {
    const section = text.split("## 一句话梗概")[1]?.split("##")[0] || "";
    const lines = section.split("\n").map((l) => l.trim()).filter(
      (l) => l && !l.startsWith("（例："),
    );
    if (!lines.length) throw new Error("请先在 00_idea.md 填写「一句话梗概」");
  }
}

function formatPath(tpl, chapter) {
  if (chapter == null) return tpl;
  return tpl.replace(/\{chapter:03d\}/g, String(chapter).padStart(3, "0"))
    .replace(/\{prev:03d\}/g, String(chapter - 1).padStart(3, "0"))
    .replace(/\{chapter\}/g, String(chapter))
    .replace(/\{prev\}/g, String(chapter - 1));
}

const CONTEXT_SKIP_MARKER = "[本文件本阶段未加载]";

const CHAPTER_CONTEXT_CORE = [
  "11_style_guide.md",
  "meta/summaries/era_summary.md",
  "meta/summaries/characters_summary.md",
  "meta/summaries/worldview_summary.md",
  "meta/summaries/relationships_summary.md",
  "meta/summaries/main_outline_summary.md",
  "05_main_outline.md",
  "06_sub_outline.md",
  "08_material_library.md",
  "13_themes_symbols.md",
  "14_pacing_notes.md",
  "10_plot_progress.md",
  "07_foreshadowing.md",
  "12_timeline.md",
];

function chapterContextSlots(chapter) {
  const slots = [...CHAPTER_CONTEXT_CORE];
  if (chapter > 1) slots.push(formatPath("09_chapters/ch{prev:03d}_summary.md", chapter));
  slots.push(formatPath("09_chapters/ch{chapter:03d}.md", chapter));
  slots.push(formatPath("09_chapters/ch{chapter:03d}_summary.md", chapter));
  return slots;
}

function chapterAllowMissing(stage, chapter) {
  const ch = formatPath("09_chapters/ch{chapter:03d}.md", chapter);
  const summary = formatPath("09_chapters/ch{chapter:03d}_summary.md", chapter);
  if (stage === "chapter_write") return new Set([ch, summary]);
  return new Set();
}

function resolveContext(stage, chapter, chapterStage) {
  const cfg = loadStages();
  if (chapterStage && chapter != null) {
    return chapterContextSlots(chapter);
  }
  const spec = chapterStage ? cfg.chapter_stages[stage] : cfg.stages[stage];
  let files = [...(spec.context || [])];
  if (chapter > 1) {
    for (const t of spec.context_if_chapter_gt_1 || []) {
      files.push(formatPath(t, chapter));
    }
  }
  return files.map((f) => formatPath(f, chapter));
}

function resolveOutputs(stage, chapter, chapterStage) {
  const cfg = loadStages();
  const spec = chapterStage ? cfg.chapter_stages[stage] : cfg.stages[stage];
  return (spec.outputs || []).map((f) => formatPath(f, chapter));
}

function analyzeContextFile(root, rel) {
  const fp = path.join(root, rel);
  if (!fs.existsSync(fp)) {
    return { path: rel, exists: false, ready: false, chars: 0, status: "missing" };
  }
  const raw = fs.readFileSync(fp, "utf8");
  const text = raw.trim();
  if (!text || isPlaceholder(text)) {
    return {
      path: rel,
      exists: true,
      ready: false,
      chars: 0,
      status: "placeholder",
      preview: text.slice(0, 100),
    };
  }
  return {
    path: rel,
    exists: true,
    ready: true,
    chars: raw.length,
    status: "ok",
    preview: text.slice(0, 150),
  };
}

function loadTaskPromptChars(stage, chapter) {
  const cfg = loadStages();
  const chapterStage = !!cfg.chapter_stages?.[stage];
  const spec = chapterStage ? cfg.chapter_stages[stage] : cfg.stages[stage];
  let overhead = 1032; // system base + output format rules
  if (!spec?.prompt) return overhead + 2500;
  const promptPath = path.join(ROOT, "prompts", spec.prompt);
  if (!fs.existsSync(promptPath)) return overhead + 2500;
  let text = fs.readFileSync(promptPath, "utf8");
  if (chapter != null) {
    text = text.replace(/\{N\}/g, String(chapter))
      .replace(/\{NNN\}/g, String(chapter).padStart(3, "0"));
  }
  return overhead + text.length + 400;
}

function buildContextPreview(projectId, stage, chapter) {
  const root = path.join(PROJECTS, projectId);
  if (!fs.existsSync(root)) throw new Error("项目不存在");

  const cfg = loadStages();
  const chapterStage = !!cfg.chapter_stages?.[stage];
  if (!cfg.stages?.[stage] && !chapterStage) {
    throw new Error(`未知阶段: ${stage}`);
  }

  const ctxFiles = resolveContext(stage, chapter, chapterStage);
  const outFiles = resolveOutputs(stage, chapter, chapterStage);
  const allowMissing = chapterStage && chapter != null ? chapterAllowMissing(stage, chapter) : new Set();
  const fileDetails = ctxFiles.map((rel) => {
    const detail = analyzeContextFile(root, rel);
    if (!detail.ready && allowMissing.has(rel)) {
      return { ...detail, ready: true, status: "skip", chars: CONTEXT_SKIP_MARKER.length, preview: CONTEXT_SKIP_MARKER };
    }
    return detail;
  });
  const missingFiles = fileDetails.filter((f) => !f.ready).map((f) => f.path);
  const contextChars = fileDetails.filter((f) => f.ready).reduce((s, f) => s + f.chars, 0);
  const promptOverhead = loadTaskPromptChars(stage, chapter);
  const outputDetails = outFiles.map((rel) => analyzeContextFile(root, rel));

  return {
    stage,
    chapter: chapter ?? null,
    label: STAGE_LABELS[stage] || stage,
    context_files: ctxFiles,
    output_files: outFiles,
    file_details: fileDetails,
    output_details: outputDetails,
    missing_files: missingFiles,
    context_chars: contextChars,
    prompt_overhead_chars: promptOverhead,
    total_chars: contextChars + promptOverhead,
    ready: missingFiles.length === 0,
  };
}

function buildDryRunReport(projectId) {
  const root = path.join(PROJECTS, projectId);
  if (!fs.existsSync(root)) throw new Error(`项目不存在: ${projectId}`);

  let ideaReady = true;
  let ideaMessage = "创意已填写";
  try {
    ensureIdea(root);
  } catch (e) {
    ideaReady = false;
    ideaMessage = e.message;
  }

  const cfg = loadStages();
  const items = [
    ...(cfg.setup_order || []).map((id) => ({ id, chapter: null, chapterStage: false })),
    ...(cfg.chapter_loop || []).map((id) => ({ id, chapter: 1, chapterStage: true })),
  ];

  const stages = [];
  let runnable = 0;
  let totalChars = 0;

  for (const item of items) {
    let label = STAGE_LABELS[item.id] || item.id;
    if (item.chapter) label = `第${item.chapter}章 · ${label}`;
    const entry = { stage: item.id, label, chapter: item.chapter, ready: false };

    try {
      const ctxFiles = resolveContext(item.id, item.chapter, item.chapterStage);
      for (const rel of ctxFiles) {
        if (readProjectFile(root, rel) == null && !fs.existsSync(path.join(root, rel))) {
          throw new Error(`缺少: ${rel}`);
        }
        if (readProjectFile(root, rel) == null) {
          throw new Error(`缺少: ${rel}`);
        }
      }
      let ctxChars = 0;
      for (const rel of ctxFiles) {
        ctxChars += fs.readFileSync(path.join(root, rel), "utf8").length;
      }
      entry.ready = true;
      entry.context_files = ctxFiles;
      entry.output_files = resolveOutputs(item.id, item.chapter, item.chapterStage);
      entry.context_chars = ctxChars;
      entry.total_chars = ctxChars + 3000;
      runnable++;
      totalChars += entry.total_chars;
    } catch (e) {
      entry.error = e.message;
      entry.blocked_by = [e.message.replace(/^缺少:\s*/, "")];
    }
    stages.push(entry);
  }

  const report = {
    project_id: projectId,
    idea_ready: ideaReady,
    idea_message: ideaMessage,
    stages,
    summary: {
      runnable_stages: runnable,
      total_stages: stages.length,
      runnable_prompt_chars: totalChars,
      note: "试运行不调用 API。正式生成需 Python + OPENAI_API_KEY。",
    },
  };

  const meta = path.join(root, "meta");
  fs.mkdirSync(meta, { recursive: true });
  fs.writeFileSync(path.join(meta, "dry_run_report.json"), JSON.stringify(report, null, 2));
  return report;
}

function listProjects() {
  if (!fs.existsSync(PROJECTS)) return [];
  const order = loadStages().setup_order || [];
  const targetChapters = loadConfig().novel?.target_chapters || 80;
  return fs.readdirSync(PROJECTS, { withFileTypes: true })
    .filter((d) => d.isDirectory() && !d.name.startsWith("_"))
    .map((d) => {
      const statePath = path.join(PROJECTS, d.name, "meta/workflow_state.json");
      let state = {};
      if (fs.existsSync(statePath)) state = JSON.parse(fs.readFileSync(statePath, "utf8"));
      const completed = state.completed_stages || [];
      const setupDone = completed.filter((s) => order.includes(s)).length;
      return {
        id: d.name,
        title: state.title || d.name,
        current_stage: state.current_stage || "idea",
        current_chapter: state.current_chapter || 0,
        completed_stages: completed,
        setup_done: setupDone,
        setup_total: order.length,
        target_chapters: targetChapters,
      };
    });
}

function submitJob(projectId, type, label, fn) {
  const id = `j${++jobSeq}${Date.now().toString(36)}`;
  const job = {
    id,
    project_id: projectId,
    job_type: type,
    label,
    status: "running",
    created_at: new Date().toISOString(),
    logs: [`开始: ${label}`],
  };
  jobs.set(id, job);

  Promise.resolve()
    .then(fn)
    .then((result) => {
      job.status = "success";
      job.result = result;
      job.logs.push("完成");
    })
    .catch((e) => {
      job.status = "failed";
      job.error = e.message;
      job.logs.push(`失败: ${e.message}`);
    })
    .finally(() => {
      job.finished_at = new Date().toISOString();
    });

  return id;
}

function runPython(args) {
  return new Promise((resolve, reject) => {
    const py = findPython();
    const proc = spawn(py, args, { cwd: SCRIPTS, env: process.env, shell: true });
    let out = "";
    let err = "";
    proc.stdout.on("data", (d) => { out += d; });
    proc.stderr.on("data", (d) => { err += d; });
    proc.on("close", (code) => {
      if (code === 0) resolve(out);
      else reject(new Error(err || out || `Python 退出码 ${code}`));
    });
    proc.on("error", () => reject(new Error("未找到 Python。请安装 Python 3.12 或将 PYTHON 环境变量指向 python.exe")));
  });
}

// CLI dry-run only
if (process.argv.includes("--dry-run-cli")) {
  const p = process.argv[process.argv.indexOf("--project") + 1] || "demo_novel";
  console.log(JSON.stringify(buildDryRunReport(p), null, 2));
  process.exit(0);
}

const app = express();
app.use(express.json({ limit: "10mb" }));

app.get("/api/health", (_, res) => res.json({ status: "ok", runtime: "node" }));

app.get("/api/config", (_, res) => {
  const cfg = loadConfig();
  const key = process.env.OPENAI_API_KEY || "";
  res.json({
    novel: cfg.novel,
    api: {
      model: process.env.NOVEL_LLM_MODEL || cfg.api?.model,
      base_url: process.env.OPENAI_BASE_URL || "https://api.openai.com/v1",
      api_key_set: !!key,
      api_key_masked: key ? key.slice(0, 4) + "..." + key.slice(-4) : "",
    },
    setup_order: loadStages().setup_order,
    stage_labels: STAGE_LABELS,
    python: findPython(),
  });
});

app.put("/api/settings", (req, res) => {
  const { base_url, model, api_key } = req.body || {};
  const updates = {};
  if (base_url) updates.OPENAI_BASE_URL = base_url;
  if (model) updates.NOVEL_LLM_MODEL = model;
  if (api_key) updates.OPENAI_API_KEY = api_key;
  if (!Object.keys(updates).length) {
    return res.status(400).json({ detail: "无有效配置项" });
  }
  writeEnvFile(updates);
  res.json({ ok: true, message: "配置已保存" });
});

app.get("/api/projects", (_, res) => res.json(listProjects()));

app.post("/api/projects", (req, res) => {
  const { id, title } = req.body;
  if (!id || !/^[a-zA-Z0-9_-]+$/.test(id) || id.startsWith("_")) {
    return res.status(400).json({ detail: "无效项目 ID" });
  }
  const dst = path.join(PROJECTS, id);
  if (fs.existsSync(dst)) return res.status(409).json({ detail: "项目已存在" });
  copyDir(path.join(PROJECTS, "_template"), dst);
  if (title) {
    const sp = path.join(dst, "meta/workflow_state.json");
    const st = JSON.parse(fs.readFileSync(sp, "utf8"));
    st.title = title;
    st.novel_id = id;
    fs.writeFileSync(sp, JSON.stringify(st, null, 2));
  }
  res.json({ id, title: title || id });
});

app.get("/api/projects/:id", (req, res) => {
  const root = path.join(PROJECTS, req.params.id);
  if (!fs.existsSync(root)) return res.status(404).json({ detail: "项目不存在" });
  const state = JSON.parse(fs.readFileSync(path.join(root, "meta/workflow_state.json"), "utf8"));
  const order = loadStages().setup_order || [];
  const done = new Set(state.completed_stages || []);
  const setupProgress = order.map((s) => ({ id: s, label: STAGE_LABELS[s] || s, done: done.has(s) }));
  res.json({
    id: req.params.id,
    title: state.title || req.params.id,
    state,
    setup_progress: setupProgress,
    setup_done: setupProgress.filter((s) => s.done).length,
    setup_total: setupProgress.length,
    target_chapters: loadConfig().novel?.target_chapters || 80,
    chapter_loop: (loadStages().chapter_loop || []).map((s) => ({ id: s, label: STAGE_LABELS[s] || s })),
  });
});

app.get("/api/projects/:id/context-preview", (req, res) => {
  try {
    const chapter = req.query.chapter ? parseInt(req.query.chapter, 10) : null;
    res.json(buildContextPreview(req.params.id, req.query.stage, chapter));
  } catch (e) {
    res.status(400).json({ detail: e.message });
  }
});

app.get("/api/projects/:id/stages/:stageId", (req, res) => {
  try {
    const root = path.join(PROJECTS, req.params.id);
    if (!fs.existsSync(root)) return res.status(404).json({ detail: "项目不存在" });
    const chapter = req.query.chapter ? parseInt(req.query.chapter, 10) : null;
    const preview = buildContextPreview(req.params.id, req.params.stageId, chapter);
    const state = JSON.parse(fs.readFileSync(path.join(root, "meta/workflow_state.json"), "utf8"));
    const done = (state.completed_stages || []).includes(req.params.stageId);
    res.json({ ...preview, done });
  } catch (e) {
    res.status(400).json({ detail: e.message });
  }
});

app.get("/api/projects/:id/idea-check", (req, res) => {
  try {
    ensureIdea(path.join(PROJECTS, req.params.id));
    res.json({ ready: true, message: "创意已填写，可以一键启动" });
  } catch (e) {
    res.json({ ready: false, message: e.message });
  }
});

app.get("/api/projects/:id/files", (req, res) => {
  const root = path.join(PROJECTS, req.params.id);
  const out = [];
  function walk(dir, prefix = "") {
    for (const name of fs.readdirSync(dir).sort()) {
      const rel = prefix ? `${prefix}/${name}` : name;
      const fp = path.join(dir, name);
      if (fs.statSync(fp).isDirectory()) {
        out.push({ path: rel, name, type: "dir" });
        walk(fp, rel);
      } else if (/\.(md|json|yaml|yml)$/i.test(name)) {
        out.push({ path: rel.replace(/\\/g, "/"), name, type: "file" });
      }
    }
  }
  walk(root);
  res.json(out);
});

app.get("/api/projects/:id/file", (req, res) => {
  try {
    const fp = safeProjectFile(req.params.id, req.query.path);
    if (!fs.existsSync(fp)) return res.status(404).json({ detail: "文件不存在" });
    res.json({ path: req.query.path, content: fs.readFileSync(fp, "utf8") });
  } catch (e) {
    res.status(400).json({ detail: e.message });
  }
});

app.put("/api/projects/:id/file", (req, res) => {
  try {
    const fp = safeProjectFile(req.params.id, req.query.path);
    fs.mkdirSync(path.dirname(fp), { recursive: true });
    fs.writeFileSync(fp, req.body.content, "utf8");
    res.json({ path: req.query.path, size: Buffer.byteLength(req.body.content) });
  } catch (e) {
    res.status(400).json({ detail: e.message });
  }
});

app.post("/api/projects/:id/run/dry-run", (req, res) => {
  const id = submitJob(req.params.id, "dry_run", "试运行报告", () =>
    buildDryRunReport(req.params.id),
  );
  res.json({ job_id: id });
});

app.post("/api/projects/:id/run/quickstart", (req, res) => {
  const { dry_run } = req.body || {};
  if (dry_run) {
    const id = submitJob(req.params.id, "quickstart", "一键试运行", () =>
      buildDryRunReport(req.params.id),
    );
    return res.json({ job_id: id });
  }
  const id = submitJob(req.params.id, "quickstart", "一键 · Idea → 第1章", async () => {
    await runPython(["run_pipeline.py", "--project", req.params.id, "--quickstart"]);
    return { ok: true };
  });
  res.json({ job_id: id });
});

app.post("/api/projects/:id/run/setup", (req, res) => {
  const { stage, dry_run } = req.body || {};
  const args = ["run_stage.py", "--project", req.params.id];
  if (stage) args.push("--stage", stage);
  if (dry_run) args.push("--dry-run");
  else if (!stage) args = ["run_setup.py", "--project", req.params.id];
  const id = submitJob(req.params.id, "setup", stage || "全部设定", async () => {
    await runPython(args);
    return { ok: true };
  });
  res.json({ job_id: id });
});

app.post("/api/projects/:id/run/chapter", (req, res) => {
  const { chapter, from_chapter, to_chapter, stage, dry_run } = req.body || {};
  const args = ["run_stage.py", "--project", req.params.id];
  if (stage && chapter) {
    args.push("--stage", stage, "--chapter", String(chapter));
  } else if (chapter) {
    args.splice(0, 1, "run_chapter.py");
    args.push("--chapter", String(chapter));
  } else if (from_chapter && to_chapter) {
    args.splice(0, 1, "run_chapter.py");
    args.push("--range", String(from_chapter), String(to_chapter));
  }
  if (dry_run) args.push("--dry-run");
  const id = submitJob(req.params.id, "chapter", `第${chapter || ""}章`, async () => {
    await runPython(args);
    return { ok: true };
  });
  res.json({ job_id: id });
});

app.post("/api/projects/:id/run/pipeline", (req, res) => {
  const { setup_only, skip_setup, chapters, dry_run } = req.body || {};
  const args = ["run_pipeline.py", "--project", req.params.id];
  if (setup_only) args.push("--setup-only");
  if (skip_setup) args.push("--skip-setup");
  if (chapters) args.push("--chapters", chapters);
  if (dry_run) args.push("--dry-run");
  const id = submitJob(req.params.id, "pipeline", "流水线", async () => {
    await runPython(args);
    return { ok: true };
  });
  res.json({ job_id: id });
});

app.get("/api/jobs", (req, res) => {
  let list = [...jobs.values()];
  if (req.query.project_id) list = list.filter((j) => j.project_id === req.query.project_id);
  res.json(list.sort((a, b) => b.created_at.localeCompare(a.created_at)).slice(0, 50));
});

app.get("/api/jobs/:id", (req, res) => {
  const j = jobs.get(req.params.id);
  if (!j) return res.status(404).json({ detail: "任务不存在" });
  res.json(j);
});

app.delete("/api/jobs/:id", (req, res) => {
  if (!jobs.has(req.params.id)) {
    return res.status(404).json({ detail: "任务不存在" });
  }
  jobs.delete(req.params.id);
  res.json({ ok: true });
});

app.use("/static", express.static(STATIC));
app.get("/", (_, res) => res.sendFile(path.join(STATIC, "index.html")));

function copyDir(src, dst) {
  fs.mkdirSync(dst, { recursive: true });
  for (const e of fs.readdirSync(src, { withFileTypes: true })) {
    const s = path.join(src, e.name);
    const d = path.join(dst, e.name);
    if (e.isDirectory()) copyDir(s, d);
    else fs.copyFileSync(s, d);
  }
}

const PORT = process.env.PORT || 8765;
app.listen(PORT, "127.0.0.1", () => {
  console.log(`小说工作流已启动: http://127.0.0.1:${PORT}`);
  console.log(`项目目录: ${PROJECTS}`);
  console.log(`Python: ${findPython()}（LLM 任务需要 Python + pip 依赖）`);
});
