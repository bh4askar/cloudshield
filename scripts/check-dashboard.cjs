// Run with Node.js 20+: node scripts/check-dashboard.cjs
// Checks the data-driven recovery UI without cloud access or external packages.
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");
const assert = require("node:assert/strict");
const now = Date.parse("2026-11-18T01:00:00Z");
class Clock extends Date {
  constructor(...args) {
    super(...(args.length ? args : [now]));
  }
  static now() {
    return now;
  }
}
const storage = new Map(),
  elements = new Map();
function element(selector) {
  if (!elements.has(selector))
    elements.set(selector, {
      innerHTML: "",
      textContent: "",
      hidden: false,
      dataset: {},
      value: "",
      classList: { add() {}, remove() {}, toggle() {} },
      addEventListener() {},
      setAttribute() {},
      removeAttribute() {},
      contains() {
        return false;
      },
      showModal() {
        this.open = true;
      },
      close() {
        this.open = false;
      },
      click() {},
      querySelector() {
        return element("nested");
      },
    });
  return elements.get(selector);
}
const context = vm.createContext({
  Date: Clock,
  Intl,
  console,
  structuredClone,
  document: {
    querySelector: element,
    querySelectorAll: () => [],
    getElementById: element,
    addEventListener() {},
    createElement: element,
    hidden: false,
  },
  window: { addEventListener() {} },
  location: { hash: "#overview" },
  localStorage: {
    getItem: (key) => storage.get(key),
    setItem: (key, value) => storage.set(key, value),
  },
  fetch: () => new Promise(() => {}),
  setTimeout() {},
  clearTimeout() {},
  setInterval() {},
  Blob: class {
    constructor(parts) {
      context.csv = parts.join("");
    }
  },
  URL: {
    createObjectURL() {
      return "blob:test";
    },
    revokeObjectURL() {},
  },
});
const staticPath = path.join(__dirname, "../web/src/main/resources/static");
for (const filename of ["features.js", "app.js"])
  vm.runInContext(
    fs.readFileSync(path.join(staticPath, filename), "utf8"),
    context,
    { filename },
  );
const evaluate = (code) => vm.runInContext(code, context);
const snapshot = {
  id: "snapshot-a",
  createdAt: "2026-11-18T00:00:00Z",
  status: "REPLICATED",
  bytes: 1000,
  objectCount: 3,
};
const testJob = {
  id: "test-a",
  type: "TEST",
  backupId: "snapshot-a",
  status: "SUCCEEDED",
  createdAt: "2026-11-18T00:10:00Z",
  startedAt: "2026-11-18T00:10:01Z",
  finishedAt: "2026-11-18T00:10:02Z",
  cleanupStatus: "CLEANED",
  rtoMillis: 250,
  message: "Verified",
  trigger: "DASHBOARD",
};
function load(backups = [snapshot], jobs = [testJob]) {
  context.fixture = {
    mode: "local",
    account: { id: "demo", name: "Demo", email: "" },
    backups: structuredClone(backups),
    jobs: structuredClone(jobs),
    backupCount: backups.length,
    replicatedCount: backups.filter((b) => b.status === "REPLICATED").length,
    primaryRegion: "centralindia",
    secondaryRegion: "koreacentral",
    protectedBytes: 1000,
    lastTest: jobs[0],
    notifications: "Local event log",
    nextBackup: "2026-11-19T20:30:00Z",
    nextTest: "2026-11-21T21:00:00Z",
    maxObjects: 100,
    maxBytes: 10000,
  };
  evaluate("state = fixture");
}
let passed = 0;
function check(name, fn) {
  fn();
  passed++;
  console.log("PASS " + name);
}
load();
check("Verified current snapshot and cleaned test earn 100", () =>
  assert.equal(evaluate("readiness().score"), 100),
);
check("Healthy history produces no recovery alerts", () =>
  assert.equal(evaluate("workspaceAlerts().length"), 0),
);
load([], []);
check("Empty workspace earns zero and requests a first backup", () => {
  assert.equal(evaluate("readiness().score"), 0);
  assert.equal(evaluate("workspaceAlerts()[0].action"), "backup");
});
load([{ ...snapshot, status: "PRIMARY_ONLY" }]);
check("Missing secondary copy reduces score and raises critical alert", () => {
  assert.equal(evaluate("readiness().score"), 75);
  assert.equal(evaluate("workspaceAlerts()[0].severity"), "critical");
});
load([{ ...snapshot, createdAt: "2026-11-16T00:00:00Z" }]);
check("Stale backup loses freshness points", () =>
  assert.equal(evaluate("readiness().score"), 80),
);
load([snapshot], [{ ...testJob, backupId: "older-snapshot" }]);
check("Tests of older snapshots do not validate the latest snapshot", () =>
  assert.equal(evaluate("readiness().score"), 70),
);
load([snapshot], [{ ...testJob, status: "FAILED" }, testJob]);
check("A newer failed test supersedes an older successful test", () =>
  assert.equal(evaluate("readiness().score"), 70),
);
load([snapshot], [{ ...testJob, cleanupStatus: "FAILED" }]);
check("Unconfirmed cleanup lowers score and raises a cleanup warning", () => {
  assert.equal(evaluate("readiness().score"), 90);
  assert.ok(evaluate('workspaceAlerts().some(a => a.id === "cleanup-test-a")'));
});
load([snapshot], [{ ...testJob, finishedAt: "2026-11-09T00:10:02Z" }]);
check("Tests older than seven days lose test and cleanup points", () =>
  assert.equal(evaluate("readiness().score"), 70),
);
load([snapshot], [{ ...testJob, notificationError: "Webhook failed" }]);
check("Notification errors appear as actionable alerts", () =>
  assert.ok(
    evaluate(
      'workspaceAlerts().some(a => a.id === "notification-test-a" && a.action === "job")',
    ),
  ),
);
storage.set("cloudshield-reviewed-demo", '{"corrupt":true}');
check("Invalid browser acknowledgement data is ignored", () =>
  assert.equal(evaluate("reviewedAlerts().length"), 0),
);
storage.set("cloudshield-reviewed-demo", '["notification-test-a"]');
check("Reviewing an alert preserves its evidence", () => {
  assert.equal(evaluate("workspaceAlerts()[0].reviewed"), true);
  assert.equal(evaluate("readiness().score"), 100);
});
load();
context.rangeRows = [
  { id: "today-ist", createdAt: "2026-11-17T19:00:00Z" },
  { id: "yesterday-ist", createdAt: "2026-11-17T18:00:00Z" },
];
check("Today filters at IST midnight, not UTC midnight", () =>
  assert.equal(
    evaluate('recentRows(rangeRows, "today").map(r => r.id).join(",")'),
    "today-ist",
  ),
);
context.rangeRows.push({ id: "old", createdAt: "2026-10-01T00:00:00Z" });
check("Seven-day and all-history filters include the right rows", () => {
  assert.equal(evaluate('recentRows(rangeRows, "7").length'), 2);
  assert.equal(evaluate('recentRows(rangeRows, "all").length'), 3);
});
evaluate('filterFor("backups").query = "SNAPSHOT-A"');
check("Backup search is case-insensitive", () =>
  assert.equal(evaluate('matchingRows(state.backups, "backups").length'), 1),
);
evaluate('filterFor("backups").status = "PRIMARY_ONLY"');
check("Status filters combine with search", () =>
  assert.equal(evaluate('matchingRows(state.backups, "backups").length'), 0),
);
check("Queued progress has no fictional completed result", () => {
  context.pending = {
    ...testJob,
    status: "QUEUED",
    startedAt: null,
    finishedAt: null,
  };
  const html = evaluate("jobProgress(pending)");
  assert.ok(html.includes("Awaiting worker"));
  assert.ok(!html.includes("<strong>Completed</strong>"));
});
check("Failed operation progress reports failure", () => {
  context.pending.status = "FAILED";
  assert.ok(
    evaluate("jobProgress(pending)").includes("<strong>Failed</strong>"),
  );
});
load([snapshot], [{ ...testJob, message: '<script>alert("x")</script>' }]);
check("User-controlled messages are escaped in activity and reports", () => {
  assert.ok(!evaluate("operationsPage()").includes("<script>alert"));
  assert.ok(!evaluate("reportsPage()").includes("<script>alert"));
});
load(
  [snapshot],
  [{ ...testJob, message: '=HYPERLINK("https://example.com")' }],
);
check("CSV neutralizes spreadsheet formulas and escapes quotes", () => {
  evaluate("exportReportCsv()");
  assert.ok(context.csv.includes("'=HYPERLINK"));
  assert.ok(context.csv.includes('""https://example.com""'));
});
check("Each new page renders empty and populated workspaces", () => {
  for (const empty of [false, true]) {
    load(empty ? [] : [snapshot], empty ? [] : [testJob]);
    for (const fn of [
      "overview",
      "backups",
      "recoveryTestsPage",
      "operationsPage",
      "alertsPage",
      "analyticsPage",
      "resourcesPage",
      "reportsPage",
    ])
      assert.ok(evaluate(`${fn}()`).length > 100, fn);
  }
});
check("Report distinguishes current posture from period totals", () =>
  assert.ok(
    evaluate("reportsPage()").includes(
      "latest snapshot across recorded history",
    ),
  ),
);
check("Score and outcome graphics work with the strict content security policy", () => {
  load();
  assert.ok(evaluate("readinessPanel()").includes('stroke-dasharray="100 100"'));
  for (const fn of ["readinessPanel", "analyticsPage"]) assert.ok(!evaluate(`${fn}()`).includes("style="));
});
console.log(`Dashboard checks: ${passed} passed.`);
