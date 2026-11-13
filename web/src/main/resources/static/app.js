"use strict";
let state = null,
  csrf = null,
  refreshing = false,
  route = "overview",
  sampleRows = null,
  sampleSelection = "",
  toastTimer,
  lastRender = "",
  lastRoute = "";
const titles = {
  overview: "Overview",
  backups: "Backups",
  tests: "Recovery tests",
  sample: "Sample application",
  activity: "Activity & alerts",
  settings: "Configuration",
};
const $ = (selector) => document.querySelector(selector);
const esc = (value) =>
  String(value ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
const date = (value, short = false) =>
  value
    ? new Intl.DateTimeFormat("en-IN", {
        day: "2-digit",
        month: "short",
        ...(short ? {} : { hour: "2-digit", minute: "2-digit" }),
        timeZone: "Asia/Kolkata",
      }).format(new Date(value))
    : "—";
function size(n) {
  if (n == null) return "—";
  if (n < 1024) return `${n} B`;
  if (n < 1048576) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / 1048576).toFixed(1)} MB`;
}
function duration(ms) {
  if (ms == null) return "—";
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(2)} s`;
}
function age(seconds) {
  if (seconds == null) return "—";
  if (seconds < 60) return `${seconds} s`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
  return `${(seconds / 3600).toFixed(1)} h`;
}
function badge(status) {
  let good = ["SUCCEEDED", "REPLICATED", "CLEANED", "DELIVERED"].includes(
      status,
    ),
    bad = ["FAILED", "PRIMARY_ONLY"].includes(status),
    pending = ["PENDING", "RUNNING", "QUEUED"].includes(status);
  let label =
    {
      SUCCEEDED: "Succeeded",
      FAILED: "Failed",
      REPLICATED: "Both regions",
      PRIMARY_ONLY: "Primary only",
      CLEANED: "Cleaned up",
      RETAINED: "Retained",
      NOT_APPLICABLE: "Not applicable",
      LOCAL_LOG: "Local event log",
      DELIVERED: "Delivered",
      PENDING: "Pending",
      QUEUED: "Queued",
      RUNNING: "Running",
    }[status] || status;
  return `<span class="badge ${bad ? "bad" : pending ? "warn" : good ? "" : "neutral"}">${esc(label)}</span>`;
}
function cloudIcon() {
  return '<svg viewBox="0 0 40 40" fill="none" aria-hidden="true"><path d="M12 28h18a7 7 0 0 0 1-14 10 10 0 0 0-19-2A8 8 0 0 0 12 28Z" stroke="currentColor" stroke-width="1.4"/><path d="M20 19v13m-4-4 4 4 4-4" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/></svg>';
}
function toast(message, error = false) {
  clearTimeout(toastTimer);
  $("#toast").textContent = message;
  $("#toast").className = `toast${error ? " error" : ""}`;
  $("#toast").hidden = false;
  toastTimer = setTimeout(() => ($("#toast").hidden = true), 6500);
}
async function api(path, options = {}) {
  const headers = { ...options.headers };
  if (options.method && options.method !== "GET") {
    if (!csrf) csrf = await (await fetch("/api/csrf")).json();
    headers[csrf.headerName] = csrf.token;
    headers["Content-Type"] = "application/json";
  }
  const response = await fetch(path, { ...options, headers });
  if (response.status === 401) {
    location.href = "/login.html";
    throw new Error("Your session ended. Sign in again.");
  }
  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    throw new Error(data.message || `Request failed (${response.status})`);
  }
  return response.json();
}
async function refresh() {
  if (refreshing) return;
  refreshing = true;
  try {
    state = await api("/api/overview");
    $("#connection-status").innerHTML = "<i></i>Connected";
    $("#mode-badge").textContent =
      state.mode === "local" ? "LOCAL DEMO" : "AZURE STORAGE";
    $("#nav-count").textContent = state.backupCount;
    render();
    if (route === "sample") await loadSample();
  } catch (e) {
    $("#connection-status").textContent = "Disconnected";
    if (!state || !lastRender)
      $("#content").innerHTML =
        `<div class="empty-state"><h2>Connection unavailable</h2><p>${esc(e.message)}</p><button class="button" data-action="refresh">Retry connection</button></div>`;
  } finally {
    refreshing = false;
  }
}
function pageHead(title, description, buttons = "") {
  return `<div class="page-head"><div><h1>${title}</h1><p>${description}</p></div><div class="page-actions">${buttons}</div></div>`;
}
function actions() {
  return '<a class="button" href="/api/report" download>↓ &nbsp; Export report</a><button class="button primary" data-action="backup">＋ &nbsp; Create backup</button>';
}
function metric(label, value, note, icon = "◫", green = false) {
  return `<article class="metric"><div class="metric-top">${label}<span class="metric-icon">${icon}</span></div><div class="metric-value">${value}</div><div class="metric-note${green ? " green" : ""}">${note}</div></article>`;
}
function testChart() {
  const tests = state.jobs
    .filter(
      (j) =>
        j.type === "TEST" && j.status === "SUCCEEDED" && j.rtoMillis != null,
    )
    .slice(0, 8)
    .reverse();
  if (!tests.length)
    return '<div class="empty-chart">Your recovery story starts here.<small>Run a restore test to measure recovery time.</small></div>';
  const max = Math.max(...tests.map((t) => t.rtoMillis), 10);
  const width = 480,
    height = 138,
    step = 420 / Math.max(tests.length, 4),
    bars = tests
      .map((t, i) => {
        const h = Math.max(3, (t.rtoMillis / max) * 89),
          x = 43 + i * step;
        return `<g><rect x="${x}" y="${106 - h}" width="${Math.min(32, step - 18)}" height="${h}" rx="3" fill="#78b79a"><title>${esc(date(t.startedAt))}: ${duration(t.rtoMillis)}</title></rect><text x="${x + 10}" y="125" text-anchor="middle">${i + 1}</text><text x="${x + 10}" y="${101 - h}" text-anchor="middle">${t.rtoMillis}</text></g>`;
      })
      .join("");
  return `<div class="chart"><svg role="img" aria-label="Measured recovery time in milliseconds for the latest ${tests.length} successful tests" viewBox="0 0 ${width} ${height}"><path d="M28 20H475 M28 63H475 M28 106H475" stroke="#edf3ef" stroke-width="1"/>${bars}</svg></div><div class="chart-legend"><span><i></i>Recovery time (ms)</span><span>Oldest → newest successful test</span></div>`;
}
function backupTable(backups, compact = false) {
  if (!backups.length)
    return '<div class="empty-state"><div class="empty-symbol">▤</div><h3>No backups yet</h3><p>Create a snapshot of Campus Register to start protecting your data.</p><button class="button primary" data-action="backup">Create first backup</button></div>';
  return `<div class="table-scroll"><table><thead><tr><th>BACKUP SNAPSHOT</th><th>CREATED · IST</th><th>SIZE</th><th>REPLICATION</th><th>ACTIONS</th></tr></thead><tbody>${backups.map((b) => `<tr><td><div class="name-cell"><span class="file-icon">▤</span><div>Campus Register<span class="table-id">${esc(b.id)}</span></div></div></td><td>${date(b.createdAt)}</td><td>${size(b.bytes)}<span class="table-id">${b.objectCount} objects</span></td><td>${badge(b.status)}</td><td><button class="button text" data-action="test" data-id="${esc(b.id)}" aria-label="Test backup ${esc(b.id)}">Test</button><button class="button small" data-action="restore" data-id="${esc(b.id)}" aria-label="Restore backup ${esc(b.id)}">↶ Restore</button></td></tr>`).join("")}</tbody></table></div><div class="table-footer"><span>${backups.length} ${compact ? "recent " : ""}snapshots · SHA-256 verified</span><span>Two-region storage</span></div>`;
}
function overview() {
  const last = state.lastTest,
    latest = state.backups[0],
    failed = state.jobs.filter((j) => j.status === "FAILED").length;
  const protectedBoth = latest && latest.status === "REPLICATED";
  return (
    pageHead(
      "Recovery, under control.",
      "Your backups, recovery checks, and cloud resilience in one place.",
      actions(),
    ) +
    `<section class="hero"><div><span class="eyebrow mint">YOUR RECOVERY POSTURE</span><h2>${protectedBoth ? "One backup. Two safe places." : latest ? "Your second copy needs attention." : "A little preparation. A lot of peace of mind."}</h2><p>${protectedBoth ? "Your latest snapshot is verified in both regions. Run a restore test to prove it is ready when you need it." : latest ? "The primary backup is available. Review the failed job and reconnect the secondary region." : "Create your first backup, then restore it in an isolated test environment. Know your recovery works before you need it."}</p><span class="hero-status"><i></i>${state.mode === "local" ? "Local simulation · no cloud resources in use" : "Azure Blob Storage · encrypted at rest"}</span></div><div class="region-flow"><div class="region-node">${cloudIcon()}<strong>${esc(state.primaryRegion)}</strong><small>PRIMARY REGION</small></div><span class="flow-link">⇢</span><div class="region-node">${cloudIcon()}<strong>${esc(state.secondaryRegion)}</strong><small>SECONDARY REGION</small></div></div></section>` +
    `<section class="metrics">${metric("Total snapshots", state.backupCount, `${state.replicatedCount} verified in both regions`, "▤", !!state.backupCount)}${metric("Logical backup data", size(state.protectedBytes), "One copy · excludes stored versions", "▧")}${metric("Latest test recovery time", last?.rtoMillis != null ? duration(last.rtoMillis) : "—", last ? "Measured restore + verification" : "Run your first recovery test", "↶")}${metric("Test environment", last ? badge(last.cleanupStatus) : "—", last?.cleanupStatus === "CLEANED" ? "Temporary objects removed" : "Created only for restore tests", "◇", last?.cleanupStatus === "CLEANED")}</section>` +
    `<section class="two-columns"><div class="panel"><div class="panel-head"><div><h2>Recovery performance</h2><p>Observed recovery time, measured on each successful test</p></div><span class="subtle-label">LATEST TESTS</span></div><div class="panel-body">${testChart()}</div></div><div class="panel"><div class="panel-head"><div><h2>Always on the schedule</h2><p>All schedules use India Standard Time</p></div><span class="subtle-label">IST</span></div><div class="panel-body"><div class="schedule-row"><span class="schedule-icon">▤</span><div><strong>Automatic backup</strong><small>Daily, 02:00 · next ${date(state.nextBackup)}</small></div><span class="schedule-tag">DAILY</span></div><div class="schedule-row"><span class="schedule-icon purple">↶</span><div><strong>Restore & verify</strong><small>Sunday, 02:30 · next ${date(state.nextTest)}</small></div><span class="schedule-tag">WEEKLY</span></div><p class="explain">${state.mode === "local" ? "Keep this app running for local schedules. Azure timers run independently when enabled." : "Azure Functions runs scheduled jobs independently when enabled, even with this dashboard closed."}</p></div></div></section>` +
    (failed
      ? `<div class="notice warning">${failed} failed operation${failed > 1 ? "s" : ""} recorded. Open <a href="#activity"><strong>Activity & alerts</strong></a> to review the cause.</div>`
      : "") +
    `<section class="panel"><div class="panel-head"><div><h2>Recent backups</h2><p>Point-in-time copies of your sample cloud application</p></div><a class="button text" href="#backups">View all backups →</a></div>${backupTable(state.backups.slice(0, 4), true)}</section>`
  );
}
function backups() {
  return (
    pageHead(
      "Your recovery points.",
      "Every snapshot keeps a timestamp, object manifest, and verified second copy.",
      actions(),
    ) +
    `<div class="notice">Restore creates an isolated recovery copy you can open in the sample application. Existing source data stays available.</div><section class="panel"><div class="panel-head"><h2>Backup history</h2><span class="subtle-label">${state.backupCount} SNAPSHOTS</span></div>${backupTable(state.backups)}</section><section class="panel section-gap"><div class="panel-head"><h2>Restored copies</h2><p>Retained for inspection until you remove them</p></div>${restoreRows()}</section>`
  );
}
function restoreRows() {
  let rows = state.jobs.filter((j) => j.type === "RESTORE");
  if (!rows.length)
    return '<div class="empty-state">Restore any backup to create a recovery copy.</div>';
  return `<div class="table-scroll"><table><thead><tr><th>RESTORE</th><th>STATUS</th><th>RECOVERY TIME</th><th>LOCATION</th><th></th></tr></thead><tbody>${rows.map((j) => `<tr><td>${date(j.createdAt)}</td><td>${badge(j.status)}</td><td>${duration(j.rtoMillis)}</td><td class="code">${esc(j.restorePrefix || "Pending")}</td><td>${j.status === "SUCCEEDED" ? `<button class="button small" data-action="view-restore" data-id="${esc(j.id)}">Open copy →</button>` : `<button class="button text" data-action="job" data-id="${esc(j.id)}">Details</button>`}</td></tr>`).join("")}</tbody></table></div>`;
}
function tests() {
  let rows = state.jobs.filter((j) => j.type === "TEST");
  return (
    pageHead(
      "Prove you can recover.",
      "Automatic restore, checksum checks, application validation, and cleanup.",
      `<a class="button" href="/api/report" download>↓ Export results</a><button class="button primary" data-action="test" ${!state.backupCount ? "disabled" : ""}>↶ Run recovery test</button>`,
    ) +
    `<div class="notice">RTO is observed restore + verification time. RPO is the backup age at test start. Changed objects measure the live data that differs from the recovery point.</div><div class="two-columns"><section class="panel"><div class="panel-head"><h2>Recovery time by test</h2></div><div class="panel-body">${testChart()}</div></section><section class="panel"><div class="panel-head"><h2>What each test does</h2></div><div class="panel-body"><div class="schedule-row"><span class="schedule-icon">1</span><div><strong>Recover & validate</strong><small>Read a verified copy; check every restored object</small></div></div><div class="schedule-row"><span class="schedule-icon">2</span><div><strong>Measure & clean up</strong><small>Record metrics; delete and check the test prefix</small></div></div><p class="explain">No persistent test compute is provisioned. Restore objects exist only for the duration of the test.</p></div></section></div><section class="panel"><div class="panel-head"><h2>Test history</h2><span class="subtle-label">${rows.length} RUNS</span></div>${!rows.length ? '<div class="empty-state"><div class="empty-symbol">◉</div><h3>No recovery tests yet</h3><p>Create a backup, then run your first restore test.</p></div>' : `<div class="table-scroll"><table><thead><tr><th>TEST · IST</th><th>RESULT</th><th>RTO</th><th>RPO AGE</th><th>CHANGED DATA</th><th>CLEANUP</th><th></th></tr></thead><tbody>${rows.map((j) => `<tr><td>${date(j.createdAt)}</td><td>${badge(j.status)}</td><td>${duration(j.rtoMillis)}</td><td>${age(j.rpoSeconds)}</td><td>${j.changedObjects == null ? "Unmeasured" : `${j.changedObjects} objects · ${size(j.changedBytes)}`}</td><td>${badge(j.cleanupStatus)}</td><td><button class="button text" data-action="job" data-id="${esc(j.id)}">Details →</button></td></tr>`).join("")}</tbody></table></div>`}</section>`
  );
}
function sample() {
  const restores = state.jobs.filter(
    (j) => j.type === "RESTORE" && j.status === "SUCCEEDED",
  );
  return (
    pageHead(
      "Meet Campus Register.",
      "A small cloud application with real data to back up, change, and recover.",
      `<button class="button" data-action="sample-refresh">↻ Reload data</button><button class="button primary" data-action="sample-change" ${sampleSelection ? "disabled" : ""}>＋ Add demo registration</button>`,
    ) +
    `<div class="notice">${state.mode === "local" ? "Local mode emulates cloud object storage. Add a registration after a backup, run a test, then open the restored copy to compare." : "Your private cloud workspace stores this sample application in Azure Blob Storage. Add a registration, back it up, then recover any earlier copy."}</div><section class="panel"><div class="sample-head"><span class="app-logo">▦</span><div><h2>Campus Register</h2><p>${sampleSelection ? "Recovered application data · isolated copy" : "Current source data · course registrations"}</p></div><select class="select" id="sample-source" aria-label="Application data source"><option value="">Live source application</option>${restores.map((j) => `<option value="${esc(j.id)}" ${sampleSelection === j.id ? "selected" : ""}>Restored · ${date(j.createdAt)}</option>`).join("")}</select></div><div id="sample-table">${sampleTable()}</div><div class="report-note">Protected dataset: registrations.json · catalog.json · settings.json &nbsp; / &nbsp; Prefix: application/</div></section><section class="panel section-gap"><div class="panel-head"><h2>A 5-minute disaster recovery demonstration</h2></div><div class="panel-body detail-grid"><div class="detail-item"><small>01 / PROTECT</small><strong>Create a backup of the sample application.</strong></div><div class="detail-item"><small>02 / CHANGE</small><strong>Add a registration to the live source.</strong></div><div class="detail-item"><small>03 / VERIFY</small><strong>Test the backup and review changed objects.</strong></div><div class="detail-item"><small>04 / RECOVER</small><strong>Restore the earlier copy and open it here.</strong></div></div></section>`
  );
}
function sampleTable() {
  if (sampleRows === null)
    return '<div class="empty-state">Loading application data…</div>';
  if (sampleRows.error)
    return `<div class="empty-state">${esc(sampleRows.error)}</div>`;
  const rows = sampleRows.registrations || [];
  return `<div class="table-scroll"><table><thead><tr><th>REGISTRATION</th><th>STUDENT</th><th>COURSE</th><th>STATUS</th></tr></thead><tbody>${rows.map((r) => `<tr><td class="code">${esc(r.id)}</td><td><strong>${esc(r.student)}</strong></td><td>${esc(r.course)}</td><td><span class="badge ${r.status === "Pending" ? "warn" : ""}">${esc(r.status)}</span></td></tr>`).join("")}</tbody></table></div><div class="table-footer"><span>${rows.length} registrations</span><span>Schema v${esc(sampleRows.schemaVersion)}</span></div>`;
}
async function loadSample() {
  try {
    sampleRows = await api(
      "/api/sample" +
        (sampleSelection
          ? "?restoreJobId=" + encodeURIComponent(sampleSelection)
          : ""),
    );
  } catch (e) {
    sampleRows = { error: e.message };
  }
  if (route === "sample") render();
}
function activity() {
  return (
    pageHead(
      "Every action, accounted for.",
      "Backup outcomes, recovery evidence, cleanup status, and notification delivery.",
      `<a class="button" href="/api/report" download>↓ Export audit report</a>`,
    ) +
    `<div class="notice">Alert destination: <strong>${esc(state.notifications)}</strong>. ${state.mode === "local" ? "Local demo alerts are recorded here and in the application log." : ""}</div><section class="panel"><div class="panel-head"><h2>Operation log</h2><span class="subtle-label">LATEST ${state.jobs.length} OPERATIONS</span></div>${!state.jobs.length ? '<div class="empty-state">Your backup and recovery operations will appear here.</div>' : state.jobs.map((j) => `<div class="activity-row"><span class="activity-dot">${j.status === "SUCCEEDED" ? "✓" : j.status === "FAILED" ? "!" : "↻"}</span><div><h3>${esc({ BACKUP: "Backup snapshot", TEST: "Recovery test", RESTORE: "Restore copy", SETUP: "Workspace setup", CHANGE: "Application update" }[j.type])} &nbsp; ${badge(j.status)}</h3><p>${esc(j.message)}</p><small>${date(j.createdAt)} IST · ${esc(j.trigger)} · Alert: ${esc(j.notificationStatus)}</small></div><button class="button text" data-action="job" data-id="${esc(j.id)}">Details →</button></div>`).join("")}</section>`
  );
}
function settings() {
  return (
    pageHead(
      "A clear view of your setup.",
      "Configuration is managed through environment variables and infrastructure code.",
    ) +
    `<section class="panel"><div class="panel-head"><h2>Environment</h2>${badge(state.mode === "local" ? "LOCAL_LOG" : "DELIVERED")}</div><div class="panel-body detail-grid">${[
      [
        "Execution mode",
        state.mode === "local"
          ? "Local object-store simulation"
          : "Azure Blob + Table + Queue + Functions",
      ],
      ["Protected application", "Campus Register / application/"],
      ["Primary region", state.primaryRegion],
      ["Secondary region", state.secondaryRegion],
      ["Backup schedule", "Daily at 02:00 Asia/Kolkata"],
      ["Recovery test schedule", "Sunday at 02:30 Asia/Kolkata"],
      [
        "Snapshot safety limit",
        `${state.maxObjects} objects / ${size(state.maxBytes)}`,
      ],
      ["Alerts", state.notifications],
      ["Integrity", "SHA-256 manifest and per-object verification"],
      [
        "Encryption",
        state.mode === "local"
          ? "Operating-system storage (local demo)"
          : "Azure Storage encryption at rest",
      ],
      ["Retention", "Backups retained until explicitly removed"],
      [
        "Temporary environment",
        "Isolated tests/<job-id>/ prefix, deleted after each test",
      ],
    ]
      .map(
        ([k, v]) =>
          `<div class="detail-item"><small>${esc(k)}</small><strong>${esc(v)}</strong></div>`,
      )
      .join(
        "",
      )}</div></section><div class="notice section-gap">${state.mode === "local" ? "Local mode does not provide geographical redundancy. Both simulated regions are folders on this computer." : "Two-region copies protect backup data. The control plane runs in the primary region; full regional failover requires the documented secondary recovery procedure."}</div><section class="panel"><div class="panel-head"><h2>Project scope</h2></div><div class="panel-body"><p class="explain">This project protects a bounded sample application stored as cloud objects. It verifies data recovery and sample JSON health. Recovery time does not include provisioning a complete VM, database, network, or production application stack. Backup age is an RPO observation, not a guaranteed service-level objective.</p></div></section>`
  );
}
function render() {
  if (!state) return;
  if (state.account) {
    const personal = state.account.id !== "demo";
    $("#workspace-name").textContent = personal
      ? "My CloudShield"
      : "Group G360";
    $("#workspace-description").textContent = personal
      ? "Private workspace"
      : "Academic workspace";
    if ($("#profile-name")) $("#profile-name").textContent = state.account.name;
    if ($("#profile-email"))
      $("#profile-email").textContent =
        state.account.email || "GLA University � CSE";
    document.querySelectorAll(".avatar").forEach(
      (a) =>
        (a.textContent = personal
          ? state.account.name
              .split(/\s+/)
              .slice(0, 2)
              .map((x) => x[0])
              .join("")
              .toUpperCase()
          : "G3"),
    );
  }
  route = location.hash.slice(1);
  if (!titles[route]) route = "overview";
  $("#breadcrumb").textContent = titles[route];
  document.title = `${titles[route]} · CloudShield`;
  document.querySelectorAll("[data-nav]").forEach((a) => {
    a.classList.toggle("active", a.dataset.nav === route);
    if (a.dataset.nav === route) a.setAttribute("aria-current", "page");
    else a.removeAttribute("aria-current");
  });
  const next = { overview, backups, tests, sample, activity, settings }[
    route
  ]();
  if (next !== lastRender) {
    $("#content").innerHTML = next;
    lastRender = next;
  }
  if (lastRoute !== route) {
    $("#content").classList.remove("content-enter");
    void $("#content").offsetWidth;
    $("#content").classList.add("content-enter");
    setTimeout(() => $("#content").classList.remove("content-enter"), 650);
    lastRoute = route;
  }
  refreshThemeButtons();
}
async function submit(type, backupId) {
  const job = await api("/api/jobs", {
    method: "POST",
    body: JSON.stringify({ type, backupId: backupId || null }),
  });
  toast(
    `${type === "BACKUP" ? "Backup" : type === "TEST" ? "Recovery test" : "Restore"} queued. Track the result in Activity & alerts.`,
  );
  await refresh();
  return job;
}
async function showJob(id) {
  const job = await api("/api/jobs/" + encodeURIComponent(id));
  const details = [
    ["Operation", job.type],
    ["Status", job.status],
    ["Started", date(job.startedAt)],
    ["Completed", date(job.finishedAt)],
    ["Backup", job.backupId],
    ["Region used", job.regionUsed],
    ["Measured recovery time", duration(job.rtoMillis)],
    ["Backup age at start (RPO)", age(job.rpoSeconds)],
    ["Added / changed objects", job.changedObjects ?? "Unmeasured"],
    ["Current bytes in changed objects", size(job.changedBytes)],
    ["Objects deleted since backup", job.deletedObjects ?? "Unmeasured"],
    ["Verified objects", job.verifiedObjects],
    ["Test cleanup", job.cleanupStatus],
    ["Alert delivery", job.notificationStatus],
  ];
  $("#dialog-content").innerHTML =
    `<h2>Operation details</h2><span class="code muted">${esc(job.id)}</span><div class="notice ${job.status === "FAILED" ? "warning" : ""}">${esc(job.message)}</div><div class="detail-grid">${details.map(([k, v]) => `<div class="detail-item"><small>${k}</small><strong>${esc(v ?? "—")}</strong></div>`).join("")}</div>${job.notificationError ? `<p class="error-text">${esc(job.notificationError)}</p>` : ""}<p class="explain">Changed bytes count whole current objects, not record-level or byte-level loss. Recovery time excludes the later live-source comparison and cleanup.</p>`;
  $("#job-dialog").showModal();
}
document.addEventListener("click", async (event) => {
  const button = event.target.closest("[data-action]");
  if (!button) return;
  const action = button.dataset.action,
    id = button.dataset.id;
  button.disabled = true;
  button.dataset.busy = "true";
  try {
    if (action === "backup") await submit("BACKUP");
    if (action === "test") await submit("TEST", id);
    if (action === "restore") await submit("RESTORE", id);
    if (action === "refresh") await refresh();
    if (action === "job") await showJob(id);
    if (action === "sample-change") {
      const result = await api("/api/sample/change", {
        method: "POST",
        body: "{}",
      });
      toast(result.message);
      await loadSample();
    }
    if (action === "sample-refresh") await loadSample();
    if (action === "view-restore") {
      sampleSelection = id;
      sampleRows = null;
      location.hash = "sample";
      await loadSample();
    }
  } catch (e) {
    toast(e.message, true);
  } finally {
    button.disabled = false;
    delete button.dataset.busy;
  }
});
document.addEventListener("change", (event) => {
  if (event.target.id === "sample-source") {
    sampleSelection = event.target.value;
    sampleRows = null;
    render();
    loadSample();
  }
});
window.addEventListener("hashchange", () => {
  render();
  if (route === "sample") loadSample();
});
$("#close-dialog").addEventListener("click", () => $("#job-dialog").close());
$("#logout").addEventListener("click", async () => {
  if (!csrf) csrf = await api("/api/csrf");
  const form = document.createElement("form");
  form.method = "post";
  form.action = "/logout";
  const input = document.createElement("input");
  input.type = "hidden";
  input.name = csrf.parameterName;
  input.value = csrf.token;
  form.append(input);
  document.body.append(form);
  form.submit();
});
refresh().then(() => {
  if (route === "sample") loadSample();
});
setInterval(() => {
  if (!document.hidden && !$("#job-dialog").open) refresh();
}, 4000);

const navIcons = {
  overview:
    '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
  backups:
    '<ellipse cx="12" cy="5" rx="8" ry="3"/><path d="M4 5v7c0 4 16 4 16 0V5M4 12v7c0 4 16 4 16 0v-7"/>',
  tests: '<path d="M3 11a9 9 0 1 1 2.5 7M3 4v7h7m-1 1 3 3 5-6"/>',
  sample:
    '<rect x="3" y="3" width="18" height="18" rx="3"/><path d="M3 9h18M9 9v12"/>',
  activity: '<path d="M3 12h4l3-8 4 16 3-8h4"/>',
  settings:
    '<path d="M4 7h16M4 17h16"/><circle cx="9" cy="7" r="3" fill="var(--paper)"/><circle cx="15" cy="17" r="3" fill="var(--paper)"/>',
};
function refreshThemeButtons() {
  document.querySelectorAll("[data-nav]").forEach((link) => {
    link.querySelector("span").innerHTML =
      '<svg viewBox="0 0 24 24" aria-hidden="true">' +
      navIcons[link.dataset.nav] +
      "</svg>";
  });
}
