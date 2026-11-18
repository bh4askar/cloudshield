// Dependency-free checks for curated matching, live context, and safe UI integration.
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const assert = require("node:assert/strict");
const root = path.join(__dirname, "../web/src/main/resources/static");
const context = vm.createContext({});
vm.runInContext(
  fs.readFileSync(path.join(root, "ask-cloudshield-faq.js"), "utf8"),
  context,
);
const faq = vm.runInContext("CloudShieldFAQ", context);
let count = 0,
  failures = 0;
function check(name, fn) {
  try {
    fn();
    count++;
    console.log(`PASS ${name}`);
  } catch (error) {
    failures++;
    console.error(`FAIL ${name}: ${error.message}`);
  }
}
const queries = [
  ["What is CloudShield?", "intro"],
  ["Hi!", "greeting"],
  ["Is this real AI?", "assistant"],
  ["How can I make a back up?", "backup"],
  ["create bakcup", "backup"],
  ["I have no snapshots", "empty"],
  ["My backups disappeared", "empty"],
  ["My backup is stale", "stale"],
  ["My backup is older than a day", "stale"],
  ["What does PRIMARY_ONLY mean?", "replication"],
  ["What is replicated?", "replication"],
  ["How do I restore data?", "restore"],
  ["I accidentally deleted data", "restore"],
  ["Will a restore overwrite my current data?", "overwrite"],
  ["Is it safe to restore?", "overwrite"],
  ["Where is my restored copy?", "copy"],
  ["How do I open a recovered copy?", "copy"],
  ["How do I test a backup?", "test"],
  ["run a recovery drill", "test"],
  ["Why is my readiness score 70?", "readiness"],
  ["Why isn't it 100?", "readiness"],
  ["My backup failed", "failure"],
  ["Restore is not working", "failure"],
  ["My job is stuck in the queue", "queued"],
  ["Backup taking too long", "queued"],
  ["restore checksum error", "checksum"],
  ["manifest hash mismatch", "checksum"],
  ["What if the primary region fails?", "fallback"],
  ["primary unavailable", "fallback"],
  ["Why did cleanup fail?", "cleanup"],
  ["What is CLEANED?", "cleanup"],
  ["Email didn't arrive", "notifications"],
  ["Notification error", "notifications"],
  ["When are backups scheduled?", "schedule"],
  ["Can I change the timezone?", "schedule"],
  ["Will schedules run with the browser closed?", "closed"],
  ["The dashboard sleeps", "closed"],
  ["What is RTO?", "rto"],
  ["What is backup age?", "rpo"],
  ["What do changed objects mean?", "differences"],
  ["Explain deleted objects", "differences"],
  ["Why are metrics blank?", "metrics"],
  ["What does unmeasured mean?", "metrics"],
  ["Download PDF report", "reports"],
  ["How do I export CSV?", "reports"],
  ["How can I search history?", "filters"],
  ["How to enable dark theme?", "theme"],
  ["What does mark reviewed do?", "alerts"],
  ["Why do I have warnings?", "alerts"],
  ["Google login error", "signin"],
  ["My session expired", "signin"],
  ["Are accounts private?", "privacy"],
  ["Does it read my Gmail?", "privacy"],
  ["Explain local simulation", "mode"],
  ["Does this cost money?", "cost"],
  ["Backup exceeded max bytes", "limits"],
  ["How long do you keep backups?", "retention"],
  ["Can I delete a backup?", "retention"],
  ["How do I demonstrate recovery?", "demo"],
  ["Dashboard connection unavailable", "connection"],
  ["The page won't load", "connection"],
  ["Do resource cards probe health?", "resources"],
  ["Explain success rate", "analytics"],
  ["Can I backup my laptop files?", "scope"],
  ["Does it protect against malware?", "scope"],
  ["How to cook pasta?", "unknown"],
  ["weather in Delhi", "unknown"],
  ["", "unknown"],
];
queries.forEach(([q, expected]) =>
  check(`match: ${q}`, () => assert.equal(faq.answer(q).id, expected)),
);
check("multi-question replies cover both requested metrics", () => {
  const reply = faq.answer("What is RTO? And what is RPO?");
  assert.match(reply.text, /observed duration/);
  assert.match(reply.text, /age of the selected snapshot/);
});
check("RTO versus RPO covers both definitions", () => {
  const reply = faq.answer("RTO vs RPO");
  assert.match(reply.text, /observed duration/);
  assert.match(reply.text, /age of the selected snapshot/);
});
check("follow-up keeps the previous problem", () =>
  assert.equal(faq.answer("how do i fix it?", {}, "cleanup").id, "cleanup"),
);
check("unknown question doesn't reuse unrelated prior answer", () =>
  assert.equal(faq.answer("weather?", {}, "backup").id, "unknown"),
);
check("loading status never invents a live score", () =>
  assert.match(
    faq.answer("readiness score").text,
    /can’t confirm a current score/,
  ),
);
check("live score lists only incomplete checks", () => {
  const reply = faq.answer("score", {
    readiness: {
      score: 70,
      checks: [
        { label: "Existing snapshot", ok: true },
        { label: "Latest snapshot tested", ok: false },
      ],
    },
  });
  assert.match(reply.text, /70\/100/);
  assert.match(reply.text, /Checks to improve: Latest snapshot tested/);
  assert.doesNotMatch(reply.text, /Checks to improve: Existing snapshot/);
});
check("fully ready score doesn't invent missing checks", () =>
  assert.match(
    faq.answer("score", {
      readiness: { score: 100, checks: [{ label: "Tested", ok: true }] },
    }).text,
    /All five evidence checks/,
  ),
);
check("live pending jobs and mode", () => {
  assert.match(
    faq.answer("queued", { data: { pendingJobs: 2 } }).text,
    /2 pending/,
  );
  assert.match(
    faq.answer("browser closed", { data: { mode: "local" } }).text,
    /keep the application running/,
  );
});
check("limits and empty snapshot count come from this workspace", () => {
  assert.match(
    faq.answer("max bytes", { data: { maxObjects: 100, maxBytes: 5000 } }).text,
    /100 objects and 5000 bytes/,
  );
  assert.match(
    faq.answer("no backups", { data: { backupCount: 0 } }).text,
    /contains 0 snapshot/,
  );
});
check("all topics return guidance and internal navigation only", () => {
  assert.ok(faq.topics.length >= 30);
  for (const [q] of queries) {
    const reply = faq.answer(q);
    assert.ok(reply.text.length > 40);
    for (const link of reply.links)
      assert.match(
        link.href,
        /^#(backups|activity|tests|settings|alerts|sample|reports|overview|resources|analytics)$/,
      );
  }
});
check("unsafe query is never interpolated into an answer", () =>
  assert.ok(!faq.answer("<img src=x onerror=alert(1)>").text.includes("<img")),
);
check("oversize queries are bounded", () =>
  assert.equal(faq.answer("weather ".repeat(100000)).id, "unknown"),
);
check("widget remains outside content replaced by polling", () => {
  const html = fs.readFileSync(path.join(root, "index.html"), "utf8");
  assert.ok(html.indexOf('id="ask-cloudshield"') > html.indexOf("</main>"));
  assert.match(html, /maxlength="500"/);
});
check("chat uses textContent and has no network or unsafe HTML sink", () => {
  const js = fs.readFileSync(path.join(root, "ask-cloudshield.js"), "utf8");
  assert.doesNotMatch(
    js,
    /innerHTML|insertAdjacentHTML|\bfetch\s*\(|XMLHttpRequest|localStorage|sessionStorage/,
  );
  assert.match(js, /body.textContent = text/);
  assert.match(js, /clearTimeout\(timer\)/);
});
console.log(`${count} FAQ checks passed`);
if (failures) process.exitCode = 1;
