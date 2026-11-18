"use strict";
// Presentation helpers consume the authenticated workspace's existing API data.
const viewFilters = {};
let selectedJob = null,
  reportSnapshot = null;
const jobNames = {
  BACKUP: "Backup snapshot",
  TEST: "Recovery test",
  RESTORE: "Restore copy",
  SETUP: "Workspace setup",
  CHANGE: "Application update",
};
const pendingStatuses = ["QUEUED", "PENDING", "RUNNING"];
function filterFor(key) {
  return (viewFilters[key] ||= { query: "", status: "all", range: "30" });
}
function recentRows(rows, range, field = "createdAt") {
  if (range === "all") return rows;
  const now = new Date();
  const cutoff =
    range === "today"
      ? Date.parse(
          new Intl.DateTimeFormat("en-CA", {
            timeZone: "Asia/Kolkata",
            year: "numeric",
            month: "2-digit",
            day: "2-digit",
          }).format(now) + "T00:00:00+05:30",
        )
      : now.getTime() - Number(range) * 86400000;
  return rows.filter((row) => Date.parse(row[field]) >= cutoff);
}
function matchingRows(rows, key) {
  const f = filterFor(key),
    q = f.query.trim().toLowerCase();
  return recentRows(rows, f.range).filter(
    (row) =>
      (f.status === "all" || row.status === f.status) &&
      (!q ||
        [
          row.id,
          row.type,
          jobNames[row.type],
          row.status,
          row.message,
          row.backupId,
          "Campus Register",
        ]
          .join(" ")
          .toLowerCase()
          .includes(q)),
  );
}
function rangeOptions(value) {
  return [
    ["today", "Today · IST"],
    ["7", "Last 7 days"],
    ["30", "Last 30 days"],
    ["all", "All recorded history"],
  ]
    .map(
      ([key, label]) =>
        `<option value="${key}" ${key === value ? "selected" : ""}>${label}</option>`,
    )
    .join("");
}
function filterToolbar(key, statuses = [], placeholder = "Search operations…") {
  const f = filterFor(key);
  return `<div class="filter-toolbar no-print"><label class="search-field"><span aria-hidden="true">⌕</span><input type="search" id="${key}-query" data-filter="${key}" data-field="query" value="${esc(f.query)}" placeholder="${esc(placeholder)}" aria-label="${esc(placeholder)}" /></label>${statuses.length ? `<select class="select" id="${key}-status" data-filter="${key}" data-field="status" aria-label="Filter by status"><option value="all">All statuses</option>${statuses.map(([value, label]) => `<option value="${value}" ${f.status === value ? "selected" : ""}>${label}</option>`).join("")}</select>` : ""}<select class="select" id="${key}-range" data-filter="${key}" data-field="range" aria-label="Date range">${rangeOptions(f.range)}</select><button class="button text" data-action="clear-filters" data-id="${key}">Reset</button></div>`;
}
function noMatches(key) {
  return `<div class="empty-state"><div class="empty-symbol">⌕</div><h3>No matching results</h3><p>Try another search, status, or date range.</p><button class="button" data-action="clear-filters" data-id="${key}">Reset filters</button></div>`;
}
function readiness(data = state) {
  const latest = data.backups[0];
  const latestTest =
    latest &&
    data.jobs.find((j) => j.type === "TEST" && j.backupId === latest.id);
  const fresh =
    !!latest && Date.now() - Date.parse(latest.createdAt) <= 86400000;
  const tested =
    latestTest?.status === "SUCCEEDED" &&
    Date.now() - Date.parse(latestTest.finishedAt || latestTest.createdAt) <=
      7 * 86400000;
  const checks = [
    {
      label: "Recovery point available",
      points: 25,
      ok: !!latest,
      note: latest
        ? "A recorded snapshot is available."
        : "Create your first backup.",
      href: "#backups",
    },
    {
      label: "Verified regional copies",
      points: 25,
      ok: latest?.status === "REPLICATED",
      note:
        latest?.status === "REPLICATED"
          ? "Latest snapshot verified in both configured stores."
          : "Latest snapshot needs a verified second copy.",
      href: "#backups",
    },
    {
      label: "Backup within 24 hours",
      points: 20,
      ok: fresh,
      note: fresh
        ? "Latest recovery point is less than a day old."
        : "Create a fresh recovery point.",
      href: "#backups",
    },
    {
      label: "Latest snapshot tested",
      points: 20,
      ok: tested,
      note: tested
        ? "Most recent test of this snapshot succeeded within 7 days."
        : "Run a recovery test on the latest snapshot.",
      href: "#tests",
    },
    {
      label: "Test cleanup verified",
      points: 10,
      ok: tested && latestTest.cleanupStatus === "CLEANED",
      note:
        tested && latestTest.cleanupStatus === "CLEANED"
          ? "Temporary restore objects were removed."
          : "A successful test must confirm cleanup.",
      href: "#tests",
    },
  ];
  const score = checks.reduce(
    (sum, check) => sum + (check.ok ? check.points : 0),
    0,
  );
  return {
    score,
    checks,
    label: !latest
      ? "Not configured"
      : score === 100
        ? "Recovery ready"
        : "Needs attention",
    latest,
    latestTest,
  };
}
function readinessPanel() {
  const r = readiness();
  return `<section class="readiness-card"><button class="score-ring" data-action="readiness" aria-label="Recovery readiness ${r.score} out of 100. View score breakdown"><svg viewBox="0 0 126 126" aria-hidden="true"><circle class="score-track" cx="63" cy="63" r="58"/><circle class="score-fill" cx="63" cy="63" r="58" pathLength="100" stroke-dasharray="${r.score} 100"/></svg><span><strong>${r.score}</strong><small>/ 100</small></span></button><div class="readiness-copy"><span class="eyebrow">RECOVERY READINESS</span><h2>${r.label}</h2><p>${r.score === 100 ? "Your latest backup has two verified copies and a successful recovery test." : "Turn your backup history into evidence that you can recover."}</p><small>Last backup: ${date(r.latest?.createdAt)} IST · Last test of this snapshot: ${date(r.latestTest?.createdAt)} IST</small><button class="button text" data-action="readiness">View score breakdown →</button></div><div class="readiness-checks">${r.checks.map((c) => `<a href="${c.href}" class="check-line ${c.ok ? "complete" : ""}"><span>${c.ok ? "✓" : "○"}</span>${c.label}</a>`).join("")}</div></section>`;
}
function showReadiness() {
  const r = readiness();
  selectedJob = null;
  $("#dialog-content").innerHTML =
    `<span class="eyebrow">RECOVERY READINESS</span><h2>What makes up your ${r.score}/100 score</h2><p class="explain">An evidence checklist for the latest snapshot. This is a recovery readiness indicator, not a security audit or availability guarantee.${state.mode === "local" ? " Local mode checks simulated stores on this computer." : ""}</p><div class="score-breakdown">${r.checks.map((c) => `<div class="score-check"><span class="activity-dot ${c.ok ? "" : "attention"}">${c.ok ? "✓" : "!"}</span><div><h3>${c.label}</h3><p>${c.note}</p></div><strong>${c.ok ? c.points : 0}/${c.points}</strong></div>`).join("")}</div>`;
  if (!$("#job-dialog").open) $("#job-dialog").showModal();
}
function alertKey() {
  return "cloudshield-reviewed-" + (state.account?.id || "demo");
}
function reviewedAlerts() {
  try {
    const items = JSON.parse(localStorage.getItem(alertKey()) || "[]");
    return Array.isArray(items) ? items : [];
  } catch (_) {
    return [];
  }
}
function workspaceAlerts() {
  const r = readiness(),
    alerts = [],
    reviewed = reviewedAlerts();
  function add(id, severity, title, message, action, jobId) {
    alerts.push({
      id,
      severity,
      title,
      message,
      action,
      jobId,
      reviewed: reviewed.includes(id),
    });
  }
  if (!r.latest)
    add(
      "no-backup",
      "warning",
      "Create your first recovery point",
      "This workspace has no recorded backup yet.",
      "backup",
    );
  if (r.latest && r.latest.status !== "REPLICATED")
    add(
      "replication-" + r.latest.id,
      "critical",
      "Second backup copy needs attention",
      "The latest snapshot is recorded in the primary store only. Review the backup operation and regional configuration.",
      "backups",
    );
  if (r.latest && !r.checks[2].ok)
    add(
      "stale-" + r.latest.id,
      "warning",
      "Your latest backup is over 24 hours old",
      "Create a fresh snapshot to reduce the age of your recovery point.",
      "backup",
    );
  if (r.latest && !r.checks[3].ok)
    add(
      "test-" + r.latest.id,
      "warning",
      "Verify your latest recovery point",
      "The latest snapshot needs a recent successful recovery test.",
      "test",
    );
  state.jobs.forEach((j) => {
    if (j.status === "FAILED")
      add(
        "failed-" + j.id,
        "critical",
        `${jobNames[j.type] || j.type} failed`,
        j.message || "Review operation details for the recorded cause.",
        "job",
        j.id,
      );
    if (
      j.cleanupStatus === "FAILED" ||
      (j.type === "TEST" &&
        j.status === "SUCCEEDED" &&
        j.cleanupStatus !== "CLEANED")
    )
      add(
        "cleanup-" + j.id,
        "warning",
        "Test cleanup needs review",
        "This completed test has not confirmed removal of temporary restore objects.",
        "job",
        j.id,
      );
    if (j.notificationError)
      add(
        "notification-" + j.id,
        "warning",
        "Notification delivery needs review",
        j.notificationError,
        "job",
        j.id,
      );
  });
  return alerts;
}
function alertsPage() {
  const f = filterFor("alerts"),
    all = workspaceAlerts();
  const rows = all.filter(
    (a) =>
      (f.status === "all" ||
        (f.status === "reviewed"
          ? a.reviewed
          : !a.reviewed && a.severity === f.status)) &&
      [a.title, a.message]
        .join(" ")
        .toLowerCase()
        .includes(f.query.toLowerCase()),
  );
  return (
    pageHead(
      "Know what needs attention.",
      "Recovery issues, their evidence, and your next step.",
      '<button class="button" data-action="refresh">↻ Refresh</button>',
    ) +
    `<section class="alert-summary">${metric("Critical issues", all.filter((a) => a.severity === "critical" && !a.reviewed).length, "Unreviewed operation and replication issues", "!")}${metric("Warnings", all.filter((a) => a.severity === "warning" && !a.reviewed).length, "Backup freshness, test and cleanup checks", "△")}${metric("Reviewed", all.filter((a) => a.reviewed).length, "Recorded as reviewed in this browser", "✓")}</section><div class="notice">Reviewing records your acknowledgement in this browser. The underlying recovery condition remains visible until a new backup or test fixes it.</div><section class="panel"><div class="filter-toolbar"><label class="search-field"><span>⌕</span><input type="search" id="alerts-query" data-filter="alerts" data-field="query" value="${esc(f.query)}" placeholder="Search alerts…" aria-label="Search alerts" /></label><div class="filter-chips">${[
      ["all", "All"],
      ["critical", "Critical"],
      ["warning", "Warning"],
      ["reviewed", "Reviewed"],
    ]
      .map(
        ([value, label]) =>
          `<button class="chip ${f.status === value ? "selected" : ""}" data-action="alert-filter" data-id="${value}" aria-pressed="${f.status === value}">${label}</button>`,
      )
      .join(
        "",
      )}</div></div>${rows.length ? rows.map((a) => `<article class="alert-item ${a.severity}"><span class="activity-dot attention">${a.severity === "critical" ? "!" : "△"}</span><div><div class="alert-heading"><h3>${esc(a.title)}</h3><span class="badge ${a.severity === "critical" ? "bad" : "warn"}">${a.severity}</span>${a.reviewed ? '<span class="badge neutral">Reviewed</span>' : ""}</div><p>${esc(a.message)}</p><div class="alert-actions">${["backup", "test", "job"].includes(a.action) ? `<button class="button small" data-action="${a.action}" ${a.jobId ? `data-id="${esc(a.jobId)}"` : ""}>${{ backup: "Create backup", test: "Run recovery test", job: "View details" }[a.action]}</button>` : `<a class="button small" href="#${a.action}">Review backups</a>`}<button class="button text" data-action="review-alert" data-id="${esc(a.id)}">${a.reviewed ? "Mark unreviewed" : "Mark reviewed"}</button></div></div></article>`).join("") : `<div class="empty-state"><div class="empty-symbol">✓</div><h3>${all.length ? "No matching alerts" : "No recovery issues detected"}</h3><p>${all.length ? "Try a different search or severity." : "Recorded backups and tests have no outstanding checks."}</p></div>`}</section>`
  );
}
function operationTimeline(rows) {
  return rows
    .map(
      (j) =>
        `<article class="activity-row"><span class="activity-dot ${j.status === "FAILED" ? "attention" : ""}">${j.status === "SUCCEEDED" ? "✓" : j.status === "FAILED" ? "!" : "↻"}</span><div><h3>${esc(jobNames[j.type] || j.type)} ${badge(j.status)}</h3><p>${esc(j.message || "Waiting for the worker to update this operation.")}</p><small>${date(j.createdAt)} IST · ${esc(j.trigger)} · ${esc(j.id)}</small></div><button class="button text" data-action="job" data-id="${esc(j.id)}">Details →</button></article>`,
    )
    .join("");
}
function liveActivityPanel() {
  return `<section class="panel section-gap"><div class="panel-head"><div><h2>Live workspace activity</h2><p>Latest recorded operations · refreshes every 4 seconds</p></div><a class="button text" href="#activity">View timeline →</a></div>${state.jobs.length ? operationTimeline(state.jobs.slice(0, 4)) : '<div class="empty-state">Create a backup to start your activity timeline.</div>'}</section>`;
}
function operationsPage() {
  const rows = matchingRows(state.jobs, "activity");
  return (
    pageHead(
      "Every operation, in one timeline.",
      "Search backup, recovery, application and setup events.",
      '<a class="button" href="#reports">Open reports →</a>',
    ) +
    `<section class="panel"><div class="panel-head"><div><h2>Activity timeline</h2><p>Live updates every 4 seconds · ${rows.length} matching operations</p></div><span class="live-label"><i></i>LIVE</span></div>${filterToolbar(
      "activity",
      [
        ["SUCCEEDED", "Succeeded"],
        ["FAILED", "Failed"],
        ["RUNNING", "Running"],
        ["QUEUED", "Queued"],
      ],
    )}${rows.length ? operationTimeline(rows) : state.jobs.length ? noMatches("activity") : '<div class="empty-state">Your operations will appear here after your first backup.</div>'}</section>`
  );
}
function recoveryTestsPage() {
  const all = state.jobs.filter((j) => j.type === "TEST"),
    rows = matchingRows(all, "tests");
  return (
    pageHead(
      "Prove you can recover.",
      "Restore, verify, measure, and clean up — with results you can inspect.",
      `<a class="button" href="#reports">Open reports</a><button class="button primary" data-action="test" ${!state.backupCount ? "disabled" : ""}>↶ Run recovery test</button>`,
    ) +
    `<div class="notice">Recovery time measures restore and verification. Backup age is measured at test start. Click a result to inspect its verification and cleanup evidence.</div><section class="panel"><div class="panel-head"><h2>Recovery performance</h2></div><div class="panel-body">${testChart(rows)}</div></section><section class="panel section-gap"><div class="panel-head"><h2>Recovery test history</h2><span class="subtle-label">${rows.length} / ${all.length} TESTS</span></div>${filterToolbar(
      "tests",
      [
        ["SUCCEEDED", "Succeeded"],
        ["FAILED", "Failed"],
        ["RUNNING", "Running"],
        ["QUEUED", "Queued"],
      ],
      "Search test ID, snapshot, or result…",
    )}${rows.length ? `<div class="table-scroll"><table><thead><tr><th>TEST · IST</th><th>RESULT</th><th>RECOVERY TIME</th><th>BACKUP AGE</th><th>CLEANUP</th><th></th></tr></thead><tbody>${rows.map((j) => `<tr><td>${date(j.createdAt)}<small class="table-id">${esc(j.id)}</small></td><td>${badge(j.status)}</td><td>${duration(j.rtoMillis)}</td><td>${age(j.rpoSeconds)}</td><td>${badge(j.cleanupStatus)}</td><td><button class="button text" data-action="job" data-id="${esc(j.id)}">Details →</button></td></tr>`).join("")}</tbody></table></div>` : all.length ? noMatches("tests") : '<div class="empty-state"><div class="empty-symbol">◉</div><h3>No recovery tests yet</h3><p>Create a snapshot, then test it to verify that you can recover.</p></div>'}</section>`
  );
}
function activeOperations() {
  const jobs = state.jobs.filter((j) => pendingStatuses.includes(j.status));
  if (!jobs.length) return "";
  return `<section class="operation-banner" aria-label="Active operations"><span class="loading-orbit" aria-hidden="true"></span><div><strong>${jobs.length} operation${jobs.length === 1 ? "" : "s"} in progress</strong><p>${jobs
    .slice(0, 3)
    .map(
      (j) =>
        `${esc(jobNames[j.type] || j.type)} · ${esc(j.status.toLowerCase())}`,
    )
    .join(
      " &nbsp; / &nbsp; ",
    )}</p></div><button class="button" data-action="job" data-id="${esc(jobs[0].id)}">Track progress →</button></section>`;
}
function jobProgress(job) {
  const running = !!job.startedAt || job.status === "RUNNING",
    complete = ["SUCCEEDED", "FAILED"].includes(job.status);
  return `<div class="operation-progress"><div class="progress-stage done"><span>✓</span><strong>Queued</strong><small>${date(job.createdAt)}</small></div><div class="progress-stage ${running ? "done" : ""}"><span>${running ? "✓" : "2"}</span><strong>${complete ? "Processed" : running ? "Processing" : "Awaiting worker"}</strong><small>${job.startedAt ? date(job.startedAt) : "Waiting to start"}</small></div><div class="progress-stage ${complete ? (job.status === "FAILED" ? "failed" : "done") : ""}"><span>${complete ? (job.status === "FAILED" ? "!" : "✓") : "3"}</span><strong>${complete ? (job.status === "FAILED" ? "Failed" : "Completed") : "Result & verification"}</strong><small>${job.finishedAt ? date(job.finishedAt) : "Waiting for result"}</small></div></div>${!complete ? '<p class="explain">Live worker status updates every 4 seconds. Verification results appear when processing finishes.</p>' : ""}`;
}
function resourcesPage() {
  const latest = state.backups[0],
    restored = state.jobs.filter(
      (j) => j.type === "RESTORE" && j.status === "SUCCEEDED",
    );
  const cards = [
    {
      icon: "▦",
      name: "Campus Register",
      type: "PROTECTED APPLICATION",
      status: latest ? "Snapshot available" : "Backup needed",
      good: !!latest,
      detail: latest
        ? `${latest.objectCount} objects in latest snapshot · ${size(latest.bytes)}`
        : "registrations.json · catalog.json · settings.json",
      note: "Source prefix: application/",
      href: "sample",
      action: "Open application",
    },
    {
      icon: "▤",
      name: state.primaryRegion,
      type: "PRIMARY BACKUP STORE",
      status: latest ? "Copy recorded" : "No snapshots",
      good: !!latest,
      detail: `${state.backupCount} recorded snapshots`,
      note: `Latest backup: ${date(latest?.createdAt)} IST`,
      href: "backups",
      action: "Inspect backups",
    },
    {
      icon: "◇",
      name: state.secondaryRegion,
      type: "SECONDARY BACKUP STORE",
      status:
        latest?.status === "REPLICATED"
          ? "Latest copy verified"
          : latest
            ? "Needs attention"
            : "No snapshots",
      good: latest?.status === "REPLICATED",
      detail: `${state.replicatedCount} snapshots verified in both stores`,
      note: "Independent regional copy in Azure mode",
      href: "backups",
      action: "Inspect replication",
    },
    {
      icon: "↶",
      name: "Recovery workspace",
      type: "ISOLATED RESTORES",
      status: restored.length ? "Copies available" : "Ready for a restore",
      good: true,
      detail: `${restored.length} successful retained restore${restored.length === 1 ? "" : "s"}`,
      note: "Test copies are disposable; manual restores are retained",
      href: "backups",
      action: "Open restored copies",
    },
  ];
  return (
    pageHead(
      "A home for every recovery resource.",
      "Your protected application, backup stores, and isolated restores.",
    ) +
    `<div class="notice">Status reflects recorded snapshots and operation results.${state.mode === "local" ? " Both regional stores are local simulation folders on this computer." : " This view does not probe Azure service availability."}</div><section class="resource-grid">${cards.map((c) => `<article class="resource-card"><div class="resource-top"><span class="resource-icon">${c.icon}</span><span class="badge ${c.good ? "" : "warn"}">${c.status}</span></div><span class="eyebrow">${c.type}</span><h2>${esc(c.name)}</h2><p>${esc(c.detail)}</p><small>${esc(c.note)}</small><a class="button" href="#${c.href}">${c.action} →</a></article>`).join("")}</section>`
  );
}
function trendChart(rows, kind, range) {
  const dated = rows.filter((r) => Number.isFinite(Date.parse(r.createdAt)));
  if (!dated.length)
    return '<div class="empty-chart">No data in this date range.<small>Create a backup or expand the date range.</small></div>';
  const key = (value) =>
    new Intl.DateTimeFormat("en-CA", {
      timeZone: "Asia/Kolkata",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).format(new Date(value));
  const groups = new Map();
  dated.forEach((row) => {
    const day = key(row.createdAt);
    groups.set(
      day,
      (groups.get(day) || 0) + (kind === "bytes" ? row.bytes : 1),
    );
  });
  const points = [...groups].sort(([a], [b]) => a.localeCompare(b));
  const max = Math.max(...points.map(([, n]) => n), 1),
    step = 450 / points.length;
  return `<div class="chart analytics-chart"><svg viewBox="0 0 520 210" role="img" aria-label="${kind === "bytes" ? "Logical snapshot bytes" : "Snapshots created"} by day in India Standard Time"><path d="M38 25H508 M38 90H508 M38 155H508" stroke="var(--line)"/>${points
    .map(([day, n], i) => {
      const height = (n / max) * 115,
        x = 45 + i * step;
      return `<g tabindex="0" aria-label="${esc(day)}: ${kind === "bytes" ? size(n) : n + " snapshots"}"><title>${esc(day)} · ${kind === "bytes" ? size(n) : n + " snapshots"}</title><rect x="${x}" y="${155 - height}" width="${Math.max(2, Math.min(36, step - 5))}" height="${Math.max(2, height)}" rx="4" fill="var(--green)"/>${points.length <= 10 || i === 0 || i === points.length - 1 ? `<text x="${x + Math.min(36, step - 5) / 2}" y="178" text-anchor="middle">${esc(day.slice(5))}</text>` : ""}</g>`;
    })
    .join(
      "",
    )}<text x="38" y="200">Date · IST</text><text x="505" y="200" text-anchor="end">Daily max: ${kind === "bytes" ? size(max) : max}</text></svg></div><p class="chart-caption">Hover or focus a bar for its recorded value. Days with snapshots are shown.</p>`;
}
function analyticsPage() {
  const f = filterFor("analytics"),
    snapshots = recentRows(state.backups, f.range),
    jobs = recentRows(state.jobs, f.range);
  const completed = jobs.filter((j) =>
      ["SUCCEEDED", "FAILED"].includes(j.status),
    ),
    good = completed.filter((j) => j.status === "SUCCEEDED");
  const tests = jobs.filter(
    (j) => j.type === "TEST" && j.status === "SUCCEEDED" && j.rtoMillis != null,
  );
  return (
    pageHead(
      "See your recovery story.",
      "Recorded snapshot growth, operation outcomes, and recovery performance.",
      `<select class="select" id="analytics-range" data-filter="analytics" data-field="range" aria-label="Analytics date range">${rangeOptions(f.range)}</select>`,
    ) +
    `<section class="metrics">${metric("Snapshots in range", snapshots.length, `${snapshots.filter((b) => b.status === "REPLICATED").length} verified in both stores`, "▤")}${metric("Completed operation success", completed.length ? Math.round((good.length / completed.length) * 100) + "%" : "—", `${good.length} succeeded / ${completed.length} completed`, "✓")}${metric("Average test recovery", tests.length ? duration(Math.round(tests.reduce((sum, j) => sum + j.rtoMillis, 0) / tests.length)) : "—", `${tests.length} successful measured tests`, "↶")}${metric("Logical snapshot data", size(snapshots.reduce((sum, b) => sum + b.bytes, 0)), "One logical copy; excludes versions and replicas", "▧")}</section><section class="two-columns"><article class="panel"><div class="panel-head"><div><h2>Snapshot activity</h2><p>Backups created per day</p></div></div><div class="panel-body">${trendChart(snapshots, "count", f.range)}</div></article><article class="panel"><div class="panel-head"><div><h2>Operation outcomes</h2><p>Completed operations in the selected period</p></div></div><div class="panel-body"><div class="outcome-bar" role="img" aria-label="${good.length} succeeded, ${completed.length - good.length} failed">${completed.length ? `<svg viewBox="0 0 100 25" preserveAspectRatio="none" aria-hidden="true"><rect width="100" height="25" class="outcome-failed"/><rect width="${(good.length / completed.length) * 100}" height="25" class="outcome-succeeded"/></svg>` : ""}</div><div class="outcome-labels"><span><i class="good-dot"></i>${good.length} succeeded</span><span><i class="bad-dot"></i>${completed.length - good.length} failed</span></div><p class="explain">${jobs.filter((j) => pendingStatuses.includes(j.status)).length} pending operations are excluded from the success rate.</p><a class="button text" href="#activity">Inspect operation history →</a></div></article></section><section class="two-columns"><article class="panel"><div class="panel-head"><h2>Recovery time by test</h2></div><div class="panel-body">${testChart(jobs)}</div></article><article class="panel"><div class="panel-head"><div><h2>Logical snapshot growth</h2><p>Snapshot bytes captured each day</p></div></div><div class="panel-body">${trendChart(snapshots, "bytes", f.range)}</div></article></section>`
  );
}
function reportsPage() {
  const data = reportSnapshot || state,
    f = filterFor("reports"),
    backups = recentRows(data.backups, f.range),
    jobs = recentRows(data.jobs, f.range),
    r = readiness(data);
  return (
    pageHead(
      "Evidence you can share.",
      "Preview your recovery report, choose a period, and export it.",
      `<select class="select no-print" id="reports-range" data-filter="reports" data-field="range" aria-label="Report date range">${rangeOptions(f.range)}</select><button class="button" data-action="export-csv">↓ CSV</button><button class="button primary" data-action="print-report">↓ Save as PDF</button>`,
    ) +
    `<article class="panel report-preview"><div class="report-masthead"><div><span class="eyebrow">CLOUDSHIELD / RECOVERY REPORT</span><h2>${esc(data.account?.name || "Group G360")} workspace</h2><p>Generated ${date(reportSnapshot?.capturedAt || new Date().toISOString())} IST · ${data.mode === "local" ? "Local simulation" : "Azure storage"}</p></div><img src="/favicon.svg" alt="CloudShield" /></div><div class="report-period">Reporting period: ${esc({ today: "Today (IST)", 7: "Last 7 days", 30: "Last 30 days", all: "All recorded history" }[f.range])}</div><div class="report-stats"><div><small>Current readiness</small><strong>${r.score}/100</strong></div><div><small>Snapshots in period</small><strong>${backups.length}</strong></div><div><small>Verified regional copies</small><strong>${backups.filter((b) => b.status === "REPLICATED").length}</strong></div><div><small>Failed operations</small><strong>${jobs.filter((j) => j.status === "FAILED").length}</strong></div></div><div class="panel-body"><h3>Current recovery readiness checklist</h3><div class="report-checks">${r.checks.map((c) => `<p>${c.ok ? "✓" : "○"} ${c.label} · ${c.ok ? c.points : 0}/${c.points}</p>`).join("")}</div><h3>Operations in the selected period</h3></div>${jobs.length ? `<div class="table-scroll"><table><thead><tr><th>DATE · IST</th><th>OPERATION</th><th>RESULT</th><th>RECOVERY TIME</th><th>CLEANUP</th></tr></thead><tbody>${jobs.map((j) => `<tr><td>${date(j.createdAt)}</td><td>${esc(jobNames[j.type] || j.type)}<small class="table-id">${esc(j.id)}</small></td><td>${badge(j.status)}</td><td>${duration(j.rtoMillis)}</td><td>${badge(j.cleanupStatus)}</td></tr>`).join("")}</tbody></table></div>` : '<div class="empty-state">No operations recorded in this period.</div>'}<div class="report-disclaimer"><p>Readiness reflects the latest snapshot across recorded history; the reporting period filters the totals and operation table. RTO measures observed restore and verification time. This report covers the bounded Campus Register dataset.${data.mode === "local" ? " Regional stores are simulated folders on this computer." : ""}</p></div></article><div class="notice section-gap no-print">Save as PDF opens your browser’s print dialog. Select “Save as PDF” for a formatted report. <a class="button text" href="/api/report" download>Download complete JSON audit data →</a></div>`
  );
}
function exportReportCsv() {
  const rows = recentRows(state.jobs, filterFor("reports").range);
  const cell = (value) =>
    '"' +
    String(value ?? "")
      .replace(/^[=+\-@\t\r]/, "'$&")
      .replace(/"/g, '""') +
    '"';
  const body = [
    [
      "Operation ID",
      "Type",
      "Status",
      "Created (UTC)",
      "Backup ID",
      "Recovery time (ms)",
      "Backup age (s)",
      "Cleanup",
      "Message",
    ],
    ...rows.map((j) => [
      j.id,
      j.type,
      j.status,
      j.createdAt,
      j.backupId,
      j.rtoMillis,
      j.rpoSeconds,
      j.cleanupStatus,
      j.message,
    ]),
  ]
    .map((row) => row.map(cell).join(","))
    .join("\r\n");
  const url = URL.createObjectURL(
      new Blob(["\uFEFF" + body], { type: "text/csv;charset=utf-8" }),
    ),
    a = document.createElement("a");
  a.href = url;
  a.download = "cloudshield-recovery-report.csv";
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  toast(`Exported ${rows.length} operations as CSV.`);
}
function renderSearch() {
  if (!state) return;
  const q = $("#global-search").value.toLowerCase().trim(),
    out = $("#search-results");
  const pages = Object.entries(titles).filter(
    ([, name]) => !q || name.toLowerCase().includes(q),
  );
  const backups = q
    ? state.backups
        .filter((b) =>
          [b.id, b.status, "Campus Register backup"]
            .join(" ")
            .toLowerCase()
            .includes(q),
        )
        .slice(0, 6)
    : [];
  const jobs = q
    ? state.jobs
        .filter((j) =>
          [j.id, j.type, jobNames[j.type], j.status, j.message]
            .join(" ")
            .toLowerCase()
            .includes(q),
        )
        .slice(0, 6)
    : [];
  out.innerHTML = `<div class="search-group">PAGES</div>${pages.map(([key, name]) => `<a class="search-result" href="#${key}" data-search-close><span>${name}</span><small>Open page →</small></a>`).join("")}${backups.length ? '<div class="search-group">BACKUPS</div>' : ""}${backups.map((b) => `<button class="search-result" data-action="find-backup" data-id="${esc(b.id)}"><span>${esc(b.id)}</span><small>${date(b.createdAt)} · ${esc(b.status)}</small></button>`).join("")}${jobs.length ? '<div class="search-group">OPERATIONS</div>' : ""}${jobs.map((j) => `<button class="search-result" data-action="search-job" data-id="${esc(j.id)}"><span>${esc(jobNames[j.type] || j.type)}</span><small>${esc(j.id)} · ${esc(j.status)}</small></button>`).join("")}${!pages.length && !backups.length && !jobs.length ? '<p class="empty-state">No matching pages, backups, or operations.</p>' : ""}`;
}
function showSearch() {
  $("#search-dialog").showModal();
  $("#global-search").value = "";
  renderSearch();
  $("#global-search").focus();
}
function updateWorkspaceChrome() {
  const count = workspaceAlerts().filter((a) => !a.reviewed).length;
  $("#alert-count").textContent = count;
  $("#alert-count").hidden = count === 0;
  $("#alerts-shortcut").setAttribute(
    "aria-label",
    `Open alerts, ${count} unreviewed`,
  );
  $("#alerts-shortcut-count").textContent = count;
  $("#alerts-shortcut-count").hidden = count === 0;
  if (count !== Number($("#alerts-shortcut").dataset.count || 0))
    $("#alerts-shortcut").dataset.count = count;
}
document.addEventListener("input", (event) => {
  const el = event.target;
  if (el.id === "global-search") {
    renderSearch();
    return;
  }
  if (el.dataset.filter && el.dataset.field === "query") {
    filterFor(el.dataset.filter).query = el.value;
    render();
  }
});
document.addEventListener("change", (event) => {
  const el = event.target;
  if (el.dataset.filter) {
    filterFor(el.dataset.filter)[el.dataset.field] = el.value;
    render();
  }
});
document.addEventListener("click", async (event) => {
  if (event.target.closest("[data-search-close]")) $("#search-dialog").close();
  const el = event.target.closest("[data-action]");
  if (!el) return;
  const { action, id } = el.dataset;
  if (action === "readiness") showReadiness();
  if (action === "search") showSearch();
  if (action === "close-search") $("#search-dialog").close();
  if (action === "clear-filters") {
    delete viewFilters[id];
    render();
  }
  if (action === "alert-filter") {
    filterFor("alerts").status = id;
    render();
  }
  if (action === "review-alert") {
    const items = reviewedAlerts(),
      next = items.includes(id)
        ? items.filter((x) => x !== id)
        : [...items, id];
    try {
      localStorage.setItem(alertKey(), JSON.stringify(next));
      render();
      toast("Review status updated in this browser.");
    } catch (_) {
      toast(
        "Browser storage is unavailable. Review status could not be saved.",
        true,
      );
    }
  }
  if (action === "export-csv") exportReportCsv();
  if (action === "print-report") {
    reportSnapshot = {
      ...structuredClone(state),
      capturedAt: new Date().toISOString(),
    };
    render();
    window.print();
    reportSnapshot = null;
    render();
  }
  if (action === "find-backup") {
    filterFor("backups").query = id;
    filterFor("backups").status = "all";
    filterFor("backups").range = "all";
    $("#search-dialog").close();
    location.hash = "backups";
    render();
  }
  if (action === "search-job") {
    $("#search-dialog").close();
    try {
      await showJob(id);
    } catch (e) {
      toast(e.message, true);
    }
  }
});
document.addEventListener("keydown", (event) => {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
    event.preventDefault();
    if (!$("#search-dialog").open && state) showSearch();
  }
});
