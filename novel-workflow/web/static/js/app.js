const API = "";
let currentProject = null;
let currentProjectDetail = null;
let currentFile = null;
let currentTab = "workflow";
let config = null;
let pollTimer = null;
let selectedJobId = null;
let lastPolledJobStatus = null;
let selectedStageId = null;

const CHAPTER_STAGES = ["chapter_write", "plot_update", "chapter_review"];

const STAGE_GROUPS = [
  { name: "世界构建", ids: ["era_setting", "characters", "worldview", "relationships"] },
  { name: "剧情规划", ids: ["main_outline", "sub_outline", "foreshadowing", "material_library"] },
  { name: "写作辅助", ids: ["style_guide", "timeline", "themes_symbols", "pacing_notes", "context_index"] },
];

const STAGE_HINTS = {
  era_setting: "时代背景、社会结构与历史脉络",
  characters: "主要角色档案与人物弧光",
  worldview: "世界规则、势力与地理",
  relationships: "人物关系网与冲突线",
  main_outline: "四卷主线结构与关键节点",
  sub_outline: "支线剧情与副线交织",
  foreshadowing: "伏笔埋设与回收计划",
  material_library: "场景、道具、台词素材库",
  style_guide: "叙述视角、语言风格与禁忌",
  timeline: "故事时间轴与事件顺序",
  themes_symbols: "主题意象与象征体系",
  pacing_notes: "节奏控制与张力曲线",
  context_index: "上下文索引，供后续章节引用",
};

const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => document.querySelectorAll(sel);

async function api(path, options = {}) {
  const res = await fetch(API + path, {
    headers: { "Content-Type": "application/json", ...options.headers },
    ...options,
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.detail || data.message || res.statusText);
  return data;
}

function toast(msg, isError = false) {
  const el = $("#toast");
  el.textContent = msg;
  el.style.background = isError ? "#a63d3d" : "#2a2218";
  el.classList.remove("hidden");
  setTimeout(() => el.classList.add("hidden"), 3500);
}

function showPanel(name) {
  currentTab = name;
  $$(".panel").forEach((p) => p.classList.add("hidden"));
  $("#empty-state").classList.add("hidden");
  $(`#panel-${name}`).classList.remove("hidden");
  $$(".tab").forEach((t) => t.classList.toggle("active", t.dataset.tab === name));
}

function formatChars(n) {
  if (n >= 10000) return `${(n / 1000).toFixed(1)}k`;
  return n.toLocaleString();
}

function statusLabel(s) {
  return { pending: "等待", running: "运行中", success: "成功", failed: "失败" }[s] || s;
}

function fileStatusLabel(status) {
  return { ok: "就绪", missing: "缺失", placeholder: "未生成" }[status] || status;
}

// --- Init ---
async function loadConfig() {
  config = await api("/api/config");
  updateApiStatus();
  fillPreviewStages();
  fillSettingsForm();
}

function updateApiStatus() {
  const status = $("#api-status");
  if (config.api.api_key_set) {
    status.textContent = `API ✓ ${config.api.model}`;
    status.className = "api-status ok";
  } else {
    status.textContent = "API 未配置 · 点击设置";
    status.className = "api-status warn";
  }
}

function fillSettingsForm() {
  if (!config) return;
  $("#settings-base-url").value = config.api.base_url || "";
  $("#settings-model").value = config.api.model || "";
  $("#settings-api-key").value = "";
  $("#settings-api-key").placeholder = config.api.api_key_set
    ? `已配置 ${config.api.api_key_masked}，留空则不修改`
    : "sk-...";
}

function updateNovelHeader(detail) {
  currentProjectDetail = detail;
  $("#novel-title").textContent = detail.title || detail.id;
  const stageLabel = config.stage_labels[detail.state.current_stage] || detail.state.current_stage || "idea";
  $("#novel-subtitle").textContent = `当前阶段：${stageLabel}`;

  const setupDone = detail.setup_done ?? detail.setup_progress.filter((s) => s.done).length;
  const setupTotal = detail.setup_total ?? detail.setup_progress.length;
  const chDone = detail.state.current_chapter || 0;
  const chTotal = detail.target_chapters || 80;

  $("#prog-setup").textContent = `${setupDone}/${setupTotal}`;
  $("#prog-chapter").textContent = `${chDone}/${chTotal}`;
  $("#bar-setup").style.width = `${Math.round((setupDone / setupTotal) * 100)}%`;
  $("#bar-chapter").style.width = `${Math.round((chDone / chTotal) * 100)}%`;

  $("#chapter-progress-text").textContent = `${chDone} / ${chTotal} 章`;
  $("#chapter-bar-fill").style.width = `${Math.round((chDone / chTotal) * 100)}%`;
  $("#chapter-num").max = chTotal;
  $("#chapter-end").max = chTotal;

  const nextCh = Math.max(1, chDone + 1);
  if (parseInt($("#chapter-num").value, 10) < 1) {
    $("#chapter-num").value = nextCh;
  }
}

async function refreshProjectData(id) {
  const detail = await api(`/api/projects/${id}`);
  updateNovelHeader(detail);
  renderSetupPipeline(detail.setup_progress);
  await loadIdeaEditor();
  checkIdeaReady();
  return detail;
}

async function selectProject(id, { keepTab = false } = {}) {
  currentProject = id;
  $$("#project-list li").forEach((li) => li.classList.toggle("active", li.dataset.id === id));
  await refreshProjectData(id);
  if (!keepTab) showPanel("workflow");
  loadFiles();
  loadJobs();
  startPolling();
}

async function loadProjects() {
  const list = await api("/api/projects");
  const ul = $("#project-list");
  ul.innerHTML = "";
  list.forEach((p) => {
    const li = document.createElement("li");
    li.dataset.id = p.id;
    const setup = p.setup_done != null ? `设定 ${p.setup_done}/${p.setup_total}` : "";
    const ch = `章 ${p.current_chapter || 0}/${p.target_chapters || 80}`;
    li.innerHTML = `
      <span class="novel-name">${p.title || p.id}</span>
      <span class="sub">${setup} · ${ch}</span>`;
    if (p.id === currentProject) li.classList.add("active");
    li.onclick = () => selectProject(p.id);
    ul.appendChild(li);
  });
}

async function loadIdeaEditor() {
  if (!currentProject) return;
  try {
    const data = await api(`/api/projects/${currentProject}/file?path=${encodeURIComponent("00_idea.md")}`);
    $("#idea-editor").value = data.content;
  } catch {
    $("#idea-editor").value = "";
  }
}

async function saveIdea() {
  if (!currentProject) return;
  await api(`/api/projects/${currentProject}/file?path=${encodeURIComponent("00_idea.md")}`, {
    method: "PUT",
    body: JSON.stringify({ content: $("#idea-editor").value }),
  });
  toast("创意已保存");
  checkIdeaReady();
  loadFiles();
}

async function checkIdeaReady() {
  const el = $("#idea-check-msg");
  if (!currentProject) {
    el.textContent = "";
    el.className = "idea-check-msg";
    return;
  }
  try {
    const data = await api(`/api/projects/${currentProject}/idea-check`);
    el.textContent = data.message;
    el.className = "idea-check-msg " + (data.ready ? "ok" : "warn");
    $("#btn-quickstart").disabled = !data.ready;
  } catch (e) {
    el.textContent = e.message;
    el.className = "idea-check-msg warn";
  }
}

// --- Setup pipeline graph ---
function getStageStatus(step, index, steps) {
  if (step.done) return "done";
  const firstUndone = steps.findIndex((s) => !s.done);
  if (index === firstUndone) return "current";
  return "pending";
}

function renderSetupPipeline(steps) {
  const box = $("#setup-pipeline");
  box.innerHTML = "";
  const stepMap = Object.fromEntries(steps.map((s) => [s.id, s]));

  STAGE_GROUPS.forEach((group, gi) => {
    const groupEl = document.createElement("div");
    groupEl.className = "pipeline-group";
    groupEl.innerHTML = `<div class="pipeline-group-label">${group.name}</div>`;
    const track = document.createElement("div");
    track.className = "pipeline-track";

    group.ids.forEach((id, i) => {
      const step = stepMap[id];
      if (!step) return;
      const idx = steps.findIndex((s) => s.id === id);
      const status = getStageStatus(step, idx, steps);

      if (i > 0) {
        const arrow = document.createElement("div");
        arrow.className = "pipeline-connector";
        arrow.innerHTML = "→";
        track.appendChild(arrow);
      }

      const node = document.createElement("button");
      node.type = "button";
      node.className = `pipeline-node ${status}`;
      node.dataset.stage = id;
      node.innerHTML = `
        <span class="node-index">${idx + 1}</span>
        <span class="node-label">${step.label}</span>`;
      node.title = STAGE_HINTS[id] || step.label;
      node.onclick = () => openStageDetail(id);
      track.appendChild(node);
    });

    groupEl.appendChild(track);
    box.appendChild(groupEl);
    if (gi < STAGE_GROUPS.length - 1) {
      const sep = document.createElement("div");
      sep.className = "pipeline-group-sep";
      box.appendChild(sep);
    }
  });
}

async function openStageDetail(stageId) {
  if (!currentProject) return;
  selectedStageId = stageId;
  const label = config.stage_labels[stageId] || stageId;
  $("#stage-detail-title").textContent = label;
  const body = $("#stage-detail-body");
  body.innerHTML = '<p class="hint">加载中…</p>';
  $("#dialog-stage-detail").showModal();

  try {
    const data = await api(`/api/projects/${currentProject}/stages/${stageId}`);
    const hint = STAGE_HINTS[stageId] || "";
    let html = `<p class="stage-hint">${hint}</p>`;
    html += `<p class="stage-status ${data.done ? "done" : data.ready ? "ready" : "blocked"}">
      ${data.done ? "✓ 已完成" : data.ready ? "● 可运行" : "✗ 上下文未就绪"}</p>`;

    html += `<h4>输入上下文</h4><ul class="file-status-list">`;
    (data.file_details || []).forEach((f) => {
      html += `<li class="file-st ${f.status}">
        <span class="file-st-name">${f.path}</span>
        <span class="file-st-meta">${fileStatusLabel(f.status)}${f.chars ? ` · ${formatChars(f.chars)} 字` : ""}</span>
        ${f.status === "ok" ? `<button type="button" class="link-btn" data-file="${f.path}">查看</button>` : ""}
      </li>`;
    });
    html += `</ul>`;

    html += `<h4>输出文件</h4><ul class="file-status-list outputs">`;
    (data.output_details || []).forEach((f) => {
      html += `<li class="file-st ${f.status}">
        <span class="file-st-name">${f.path}</span>
        <span class="file-st-meta">${f.ready ? `${formatChars(f.chars)} 字` : "待生成"}</span>
        ${f.ready ? `<button type="button" class="link-btn" data-file="${f.path}">查看</button>` : ""}
      </li>`;
    });
    html += `</ul>`;

    if (data.missing_files?.length) {
      html += `<p class="missing-note">缺失或未生成：${data.missing_files.join("、")}</p>`;
    }

    html += `<div class="token-estimate">估算 prompt：<strong>${formatChars(data.total_chars)}</strong> 字符
      （上下文 ${formatChars(data.context_chars)} + 指令 ${formatChars(data.prompt_overhead_chars || 0)}）</div>`;

    body.innerHTML = html;
    body.querySelectorAll(".link-btn").forEach((btn) => {
      btn.onclick = () => {
        $("#dialog-stage-detail").close();
        showPanel("files");
        openFile(btn.dataset.file);
      };
    });
  } catch (e) {
    body.innerHTML = `<p class="error-text">${e.message}</p>`;
  }
}

$("#btn-close-stage").onclick = () => $("#dialog-stage-detail").close();
$("#btn-run-stage-from-drawer").onclick = async () => {
  if (!selectedStageId) return;
  $("#dialog-stage-detail").close();
  await runSetupStage(selectedStageId);
};
$("#btn-preview-stage-from-drawer").onclick = async () => {
  if (!selectedStageId) return;
  $("#preview-stage").value = selectedStageId;
  $("#preview-chapter").classList.add("hidden");
  $("#dialog-stage-detail").close();
  await previewContext();
  document.getElementById("preview-result").scrollIntoView({ behavior: "smooth", block: "nearest" });
};

$("#btn-save-idea").onclick = saveIdea;
$("#btn-edit-idea-file").onclick = () => {
  showPanel("files");
  loadFiles().then(() => openFile("00_idea.md"));
};

$("#btn-quickstart").onclick = async () => {
  if (!currentProject) return;
  const check = await api(`/api/projects/${currentProject}/idea-check`);
  if (!check.ready) {
    toast(check.message, true);
    return;
  }
  if (!confirm("将依次执行：全部设定（13 步）→ 第 1 章写作循环。\n耗时与 Token 消耗较大，是否继续？")) {
    return;
  }
  const btn = $("#btn-quickstart");
  btn.disabled = true;
  try {
    const { job_id } = await api(`/api/projects/${currentProject}/run/quickstart`, {
      method: "POST",
      body: JSON.stringify({}),
    });
    toast(`一键任务已启动: ${job_id}`);
    showPanel("jobs");
    watchJob(job_id);
  } catch (e) {
    toast(e.message, true);
  } finally {
    btn.disabled = false;
    checkIdeaReady();
  }
};

function fillPreviewStages() {
  const sel = $("#preview-stage");
  sel.innerHTML = "";
  [...config.setup_order, ...CHAPTER_STAGES].forEach((id) => {
    const opt = document.createElement("option");
    opt.value = id;
    opt.textContent = config.stage_labels[id] || id;
    sel.appendChild(opt);
  });
  sel.onchange = () => {
    $("#preview-chapter").classList.toggle("hidden", !CHAPTER_STAGES.includes(sel.value));
  };
}

// --- Run actions ---
async function runSetupStage(stage) {
  if (!currentProject) return;
  try {
    const body = stage ? { stage } : {};
    const { job_id } = await api(`/api/projects/${currentProject}/run/setup`, {
      method: "POST",
      body: JSON.stringify(body),
    });
    toast(`任务已提交: ${job_id}`);
    showPanel("jobs");
    watchJob(job_id);
  } catch (e) {
    toast(e.message, true);
  }
}

$("#btn-run-all-setup").onclick = () => runSetupStage(null);

$("#btn-ch-dec").onclick = () => {
  const inp = $("#chapter-num");
  inp.value = Math.max(1, parseInt(inp.value, 10) - 1);
};
$("#btn-ch-inc").onclick = () => {
  const inp = $("#chapter-num");
  const max = parseInt(inp.max, 10) || 999;
  inp.value = Math.min(max, parseInt(inp.value, 10) + 1);
};

$("#btn-run-chapter").onclick = async () => {
  if (!currentProject) return;
  const from = parseInt($("#chapter-num").value, 10);
  const endVal = $("#chapter-end").value;
  const body = {};
  if (endVal) {
    body.from_chapter = from;
    body.to_chapter = parseInt(endVal, 10);
  } else {
    body.chapter = from;
  }
  try {
    const { job_id } = await api(`/api/projects/${currentProject}/run/chapter`, {
      method: "POST",
      body: JSON.stringify(body),
    });
    toast(`写作任务: ${job_id}`);
    showPanel("jobs");
    watchJob(job_id);
  } catch (e) {
    toast(e.message, true);
  }
};

$$(".flow-step").forEach((el) => {
  el.onclick = async () => {
    if (!currentProject) return;
    const stage = el.dataset.stage;
    const chapter = parseInt($("#chapter-num").value, 10);
    try {
      const { job_id } = await api(`/api/projects/${currentProject}/run/chapter`, {
        method: "POST",
        body: JSON.stringify({ chapter, stage }),
      });
      toast(`任务: ${job_id}`);
      showPanel("jobs");
      watchJob(job_id);
    } catch (e) {
      toast(e.message, true);
    }
  };
});

$("#btn-run-pipeline").onclick = async () => {
  if (!currentProject) return;
  try {
    const { job_id } = await api(`/api/projects/${currentProject}/run/pipeline`, {
      method: "POST",
      body: JSON.stringify({
        setup_only: $("#pipeline-setup-only").checked,
        skip_setup: $("#pipeline-skip-setup").checked,
        chapters: $("#pipeline-chapters").value || "1",
      }),
    });
    toast(`流水线: ${job_id}`);
    showPanel("jobs");
    watchJob(job_id);
  } catch (e) {
    toast(e.message, true);
  }
};

function renderContextPreview(data) {
  const box = $("#preview-result");
  const readyClass = data.ready ? "ready" : "blocked";
  let html = `
    <div class="preview-summary ${readyClass}">
      <strong>${data.label || data.stage}</strong>
      ${data.chapter ? ` · 第 ${data.chapter} 章` : ""}
      <span class="preview-badge">${data.ready ? "可运行" : "上下文未就绪"}</span>
    </div>
    <div class="preview-stats">
      <span>上下文字符 <strong>${formatChars(data.context_chars)}</strong></span>
      <span>指令模板 <strong>${formatChars(data.prompt_overhead_chars || 0)}</strong></span>
      <span>总 prompt <strong>${formatChars(data.total_chars)}</strong></span>
    </div>`;

  html += `<h4>上下文文件</h4><ul class="file-status-list">`;
  (data.file_details || data.context_files.map((p) => ({ path: p, status: "unknown" }))).forEach((f) => {
    html += `<li class="file-st ${f.status || "unknown"}">
      <span class="file-st-name">${f.path}</span>
      <span class="file-st-meta">${fileStatusLabel(f.status || "?")}${f.chars ? ` · ${formatChars(f.chars)} 字` : ""}</span>
    </li>`;
  });
  html += `</ul>`;

  html += `<h4>输出文件</h4><ul class="file-status-list outputs">`;
  (data.output_files || []).forEach((p) => {
    html += `<li class="file-st"><span class="file-st-name">${p}</span></li>`;
  });
  html += `</ul>`;

  if (data.missing_files?.length) {
    html += `<p class="missing-note">请先完成前置阶段，缺失：${data.missing_files.join("、")}</p>`;
  }

  box.innerHTML = html;
}

async function previewContext() {
  if (!currentProject) return;
  const stage = $("#preview-stage").value;
  let url = `/api/projects/${currentProject}/context-preview?stage=${stage}`;
  if (CHAPTER_STAGES.includes(stage)) {
    const ch = $("#preview-chapter").value || $("#chapter-num").value;
    if (ch) url += `&chapter=${ch}`;
  }
  const box = $("#preview-result");
  box.innerHTML = '<p class="hint">加载中…</p>';
  try {
    const data = await api(url);
    renderContextPreview(data);
  } catch (e) {
    box.innerHTML = `<p class="error-text">错误: ${e.message}</p>`;
  }
}

$("#btn-preview-context").onclick = previewContext;

// --- Files ---
async function loadFiles() {
  if (!currentProject) return;
  const files = await api(`/api/projects/${currentProject}/files`);
  const tree = $("#file-tree");
  tree.innerHTML = "";

  const pin = document.createElement("li");
  pin.className = "file pin";
  pin.textContent = "★ 00_idea.md（创意）";
  pin.title = "00_idea.md";
  pin.onclick = () => openFile("00_idea.md", pin);
  tree.appendChild(pin);

  files.forEach((f) => {
    if (f.path === "00_idea.md") return;
    const li = document.createElement("li");
    li.className = f.type;
    li.textContent = f.type === "dir" ? `📁 ${f.name}` : f.name;
    li.title = f.path;
    if (f.type === "file") li.onclick = () => openFile(f.path, li);
    tree.appendChild(li);
  });
}

async function openFile(path, liEl) {
  if (!currentProject) return;
  $$(".file-tree li.file").forEach((l) => l.classList.remove("active"));
  if (liEl) liEl.classList.add("active");
  currentFile = path;
  const data = await api(`/api/projects/${currentProject}/file?path=${encodeURIComponent(path)}`);
  $("#current-file-path").textContent = path;
  $("#file-content").value = data.content;
  $("#btn-save-file").disabled = false;
  if (path === "00_idea.md") $("#idea-editor").value = data.content;
}

$("#btn-save-file").onclick = async () => {
  if (!currentProject || !currentFile) return;
  try {
    await api(`/api/projects/${currentProject}/file?path=${encodeURIComponent(currentFile)}`, {
      method: "PUT",
      body: JSON.stringify({ content: $("#file-content").value }),
    });
    toast("已保存");
    if (currentFile === "00_idea.md") {
      $("#idea-editor").value = $("#file-content").value;
      checkIdeaReady();
    }
  } catch (e) {
    toast(e.message, true);
  }
};

// --- Jobs ---
async function loadJobs() {
  const url = currentProject ? `/api/jobs?project_id=${currentProject}` : "/api/jobs";
  const jobs = await api(url);
  const box = $("#job-list");
  box.innerHTML = "";
  if (!jobs.length) {
    box.innerHTML = '<p class="hint">暂无任务</p>';
    $("#btn-delete-job").classList.add("hidden");
    return;
  }
  jobs.forEach((j) => {
    const div = document.createElement("div");
    div.className = "job-item" + (j.id === selectedJobId ? " selected" : "");
    div.innerHTML = `
      <span>${j.label}</span>
      <span class="job-status ${j.status}">${statusLabel(j.status)}</span>
      <span class="job-time">${j.created_at?.slice(11, 19) || ""}</span>`;
    div.onclick = () => selectJob(j.id);
    box.appendChild(div);
  });
}

async function selectJob(id) {
  selectedJobId = id;
  $("#btn-delete-job").classList.remove("hidden");
  loadJobs();
  const job = await api(`/api/jobs/${id}`);
  const lines = [...(job.logs || [])];
  if (job.error) lines.push("\n[ERROR] " + job.error);
  if (job.result && !job.result.stages) {
    lines.push("\n[RESULT] " + JSON.stringify(job.result, null, 2));
  }
  $("#job-logs").textContent = lines.join("\n") || "无日志";
}

async function deleteJob() {
  if (!selectedJobId) return;
  if (!confirm("确定删除此任务记录？")) return;
  try {
    await api(`/api/jobs/${selectedJobId}`, { method: "DELETE" });
    selectedJobId = null;
    $("#job-logs").textContent = "选择任务查看日志";
    $("#btn-delete-job").classList.add("hidden");
    toast("任务已删除");
    loadJobs();
  } catch (e) {
    toast(e.message, true);
  }
}

$("#btn-delete-job").onclick = deleteJob;

function watchJob(id) {
  selectedJobId = id;
  lastPolledJobStatus = null;
  selectJob(id);
}

function startPolling() {
  if (pollTimer) clearInterval(pollTimer);
  pollTimer = setInterval(async () => {
    if (!currentProject) return;
    await loadJobs();
    if (!selectedJobId) return;
    const job = await api(`/api/jobs/${selectedJobId}`);
    if (job.status === "running" || job.status === "pending") {
      selectJob(selectedJobId);
      lastPolledJobStatus = job.status;
      return;
    }
    if (job.status === "success" && lastPolledJobStatus !== "success") {
      selectJob(selectedJobId);
      await refreshProjectData(currentProject);
      await loadProjects();
    }
    lastPolledJobStatus = job.status;
  }, 2500);
}

$("#btn-refresh-jobs").onclick = loadJobs;

// --- Tabs ---
$$(".tab").forEach((tab) => {
  tab.onclick = () => {
    if (tab.dataset.tab !== "settings" && !currentProject) {
      toast("请先选择或新建小说", true);
      return;
    }
    showPanel(tab.dataset.tab);
    if (tab.dataset.tab === "files") loadFiles();
    if (tab.dataset.tab === "jobs") loadJobs();
    if (tab.dataset.tab === "settings") fillSettingsForm();
  };
});

// --- Settings ---
$("#api-status").style.cursor = "pointer";
$("#api-status").onclick = () => {
  showPanel("settings");
  fillSettingsForm();
};

$("#btn-save-settings").onclick = async () => {
  try {
    await api("/api/settings", {
      method: "PUT",
      body: JSON.stringify({
        base_url: $("#settings-base-url").value.trim(),
        model: $("#settings-model").value.trim(),
        api_key: $("#settings-api-key").value.trim(),
      }),
    });
    config = await api("/api/config");
    updateApiStatus();
    fillSettingsForm();
    toast("API 配置已保存");
  } catch (e) {
    toast(e.message, true);
  }
};

// --- New project ---
$("#btn-new-project").onclick = () => $("#dialog-new-project").showModal();
$("#btn-cancel-project").onclick = () => $("#dialog-new-project").close();

$("#form-new-project").onsubmit = async (e) => {
  e.preventDefault();
  const fd = new FormData(e.target);
  try {
    await api("/api/projects", {
      method: "POST",
      body: JSON.stringify({ id: fd.get("id"), title: fd.get("title") || null }),
    });
    $("#dialog-new-project").close();
    toast("小说已创建");
    await loadProjects();
    selectProject(fd.get("id"));
  } catch (err) {
    toast(err.message, true);
  }
};

// --- Boot ---
loadConfig().then(loadProjects).catch((e) => toast(e.message, true));
