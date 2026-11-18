/* Curated product guidance. Matching and replies stay in the browser. */
const CloudShieldFAQ = (() => {
  const page = (route, label) => ({ href: `#${route}`, label });
  const topics = [];
  function add(id, title, rules, text, links = [], suggestions = []) {
    topics.push({ id, title, rules, text, links, suggestions });
  }
  const backupPage = page("backups", "Open backups →");
  const activityPage = page("activity", "Check activity →");
  const testsPage = page("tests", "Recovery tests →");
  const settingsPage = page("settings", "Configuration →");
  const alertsPage = page("alerts", "Review alerts →");
  const samplePage = page("sample", "Sample application →");
  const reportPage = page("reports", "Open reports →");

  add(
    "intro",
    "What is CloudShield?",
    [
      [/\b(what is|about|explain|help|getting started|start using)\b/, 2],
      [/\bcloudshield\b/, 3],
    ],
    "CloudShield backs up the Campus Register sample application and verifies that its data can be recovered. Start by creating a backup, run a recovery test, then inspect the result. In Azure mode, snapshots are copied to two configured regions.",
    [backupPage, samplePage],
    [
      "How do I create a backup?",
      "How do I test recovery?",
      "What does CloudShield protect?",
    ],
  );
  add(
    "assistant",
    "About this assistant",
    [
      [
        /\b(ai|artificial intelligence|chatbot|assistant|predefined|chat privacy|save my chat|chat history)\b/,
        8,
      ],
    ],
    "I’m Ask CloudShield, your FAQ assistant. I match your question to predefined CloudShield guidance and can explain the latest loaded workspace status. I don’t use a generative AI service or run jobs for you. Your questions stay in this browser tab and disappear when you reload or start a new chat.",
    [],
    [
      "Why is my readiness score low?",
      "How do I create a backup?",
      "How do I restore data?",
    ],
  );
  add(
    "scope",
    "What is protected?",
    [
      [
        /\b(protect|protected|support|upload|backup)\b.*\b(laptop|computer|files|database|vm|virtual machine|website|gmail|drive|production)\b/,
        12,
      ],
      [
        /\b(scope|antivirus|malware|ransomware|virus|cybersecurity|what.*protect|my own data|upload)\b/,
        9,
      ],
    ],
    "This project protects a bounded Campus Register dataset: registrations.json, catalog.json, and settings.json under application/. It demonstrates object backup and verified JSON recovery. It does not currently upload your personal files, scan threats, or provision full VM/database/application failover. Protecting another application needs an integration and a recovery plan.",
    [samplePage, settingsPage],
  );
  add(
    "backup",
    "Create a backup",
    [
      [/\b(backup|snapshot|backups)\b/, 3],
      [/\b(create|take|make|run|start|manual|first|how)\b/, 1],
    ],
    "Open Backups or Overview and choose Create backup. This queues a point-in-time copy of the current sample application. Follow the operation details until it finishes, then check the snapshot’s replication status. A successful two-region copy is marked REPLICATED. I can guide you, but you choose when to run the job.",
    [backupPage, activityPage],
    [
      "Why is my backup queued?",
      "What does replicated mean?",
      "How do I test recovery?",
    ],
  );
  add(
    "empty",
    "No backups yet",
    [
      [
        /\b(no|empty|missing|zero|0|cannot find|cant find|not showing|disappeared)\b.*\b(backups?|snapshots?)\b/,
        11,
      ],
      [
        /\b(backups?|snapshots?)\b.*\b(empty|missing|disappeared|not showing)\b/,
        11,
      ],
    ],
    (c) =>
      `If this is a new workspace, choose Create backup to make your first recovery point. Check Activity for a queued or failed backup. Confirm you signed into the account where it was created and clear any history filters.${c.data ? ` Your latest loaded workspace contains ${c.data.backupCount ?? c.data.backups?.length ?? 0} snapshot(s).` : " Workspace data is still unavailable, so I can’t confirm your snapshot count yet."}`,
    [backupPage, activityPage],
    [
      "How do I create a backup?",
      "Why did my job fail?",
      "Are accounts private?",
    ],
  );
  add(
    "stale",
    "An old backup needs attention",
    [
      [/\b(stale|outdated|old|older|24 hours|fresh|freshness|overdue)\b/, 8],
      [/\b(backup|snapshot)\b/, 1],
    ],
    "Freshness points require the latest recorded snapshot to be within 24 hours. Create a new backup, then test that new snapshot. If automatic backups are overdue, check Activity and Configuration, and ask the deployment administrator to check the scheduler and worker. An older copy can still be restored, but it contains older data.",
    [backupPage, settingsPage],
    ["When are backups scheduled?", "Why is my readiness score low?"],
  );
  add(
    "replication",
    "Two-region replication",
    [
      [
        /\b(replicat\w*|primary only|primary_only|second copy|secondary copy|incomplete regional)\b/,
        10,
      ],
    ],
    (c) =>
      `REPLICATED means the snapshot was verified in both configured backup stores. PRIMARY_ONLY means a primary copy exists but the second copy needs attention. Review the backup job’s error; the administrator should check secondary storage access and configuration, then create and verify a new backup.${c.data ? ` The configured regions are ${c.data.primaryRegion || "primary"} and ${c.data.secondaryRegion || "secondary"}.` : ""} Region cards reflect recorded results, rather than live Azure health probes.`,
    [backupPage, activityPage],
    ["What if the primary region fails?", "Why is my readiness score low?"],
  );
  add(
    "restore",
    "Restore a recovery copy",
    [
      [/\b(restore|recover|recovery|rollback|roll back|lost data)\b/, 4],
      [/\b(accidentally deleted|recover.*deleted|lost my data)\b/, 14],
    ],
    "Open Backups, choose the snapshot you need, and select Restore. When the job succeeds, use Open copy in Restored copies, or select that copy in Sample application. The recovered data is isolated and retained for inspection; the current source stays unchanged. This dashboard does not automatically replace the live source.",
    [backupPage, samplePage],
    [
      "Does restore overwrite my data?",
      "How do I test recovery?",
      "Where is my restored copy?",
    ],
  );
  add(
    "overwrite",
    "Your source stays unchanged",
    [
      [
        /\b(overwrite|replace.*source|replace.*live|affect.*source|affect.*live|erase|safe to restore|lose.*current)\b/,
        13,
      ],
      [/\b(restore|test)\b/, 1],
    ],
    "A Restore job creates a separate recovery copy. It does not overwrite or delete the live Campus Register source. A recovery test also uses a temporary isolated copy and removes its test objects afterward. Applying recovered data to a production source would require a separate, deliberate recovery procedure.",
    [backupPage, samplePage],
  );
  add(
    "copy",
    "Find a restored copy",
    [
      [
        /\b(where|open|view|find|inspect|location)\b.*\b(restored|recovered|restore copy|recovery copy)\b/,
        12,
      ],
      [/\b(retained|restore prefix|restored copies)\b/, 10],
    ],
    "Go to Backups → Restored copies and choose Open copy for a successful Restore job. You can also choose a restored entry in Sample application’s data-source selector. The job details show its restore location. TEST jobs use temporary copies that are cleaned up, so they do not create a retained entry to open.",
    [backupPage, samplePage],
    ["Does restore overwrite my data?", "Why did cleanup fail?"],
  );
  add(
    "test",
    "Test your recovery",
    [
      [/\b(test|verify|verification|drill)\b/, 4],
      [/\b(backup|restore|recovery)\b/, 2],
    ],
    "In Backups, select Test beside a snapshot, or run a test from Recovery tests. CloudShield restores an isolated copy, verifies object checksums and sample JSON health, compares it with the current source, and checks cleanup. Review the result and operation details. Test the latest snapshot to improve its readiness evidence.",
    [backupPage, testsPage],
    ["Why is my readiness score low?", "What is RTO?", "Why did cleanup fail?"],
  );
  add(
    "readiness",
    "Understand your readiness score",
    [[/\b(readiness|score|70|100|breakdown|recovery posture|untested)\b/, 12]],
    (c) => {
      const missing = c.readiness?.checks
        .filter((x) => !x.ok)
        .map((x) => x.label)
        .join("; ");
      const lead = c.readiness
        ? `Your latest loaded readiness score is ${c.readiness.score}/100. ${missing ? "Checks to improve: " + missing + "." : "All five evidence checks are satisfied."}\n\n`
        : "I can’t confirm a current score until your workspace has loaded.\n\n";
      return (
        lead +
        "The score awards 25 points for a snapshot, 25 for two-store replication, 20 for freshness within 24 hours, 20 for a successful recent test of that same snapshot, and 10 for confirmed cleanup. A common 70/100 case is a fresh replicated backup that hasn’t been tested yet. Open the score breakdown, test the latest snapshot, and resolve any failed checks. It’s a recovery evidence indicator, not a service guarantee."
      );
    },
    [page("overview", "View readiness →"), testsPage],
    [
      "How do I test recovery?",
      "What does replicated mean?",
      "Why did cleanup fail?",
    ],
  );
  add(
    "failure",
    "A job failed",
    [
      [
        /\b(fail\w*|error|unsuccessful|not working|doesnt work|cannot|cant|unable)\b/,
        5,
      ],
      [/\b(job|backup|restore|test|operation)\b/, 2],
    ],
    "Open Activity, select the failed operation, and read its message and timestamps. The fix depends on the error: storage access or configuration, incomplete replication, checksum verification, sample size limits, or cleanup. The deployment administrator can check the worker logs. Resolve the cause before retrying; I can explain a specific message if you paste it without passwords or keys.",
    [activityPage, alertsPage],
    [
      "Why is my job queued?",
      "What is a checksum error?",
      "What are the backup limits?",
    ],
  );
  add(
    "queued",
    "Queued or slow jobs",
    [
      [
        /\b(queued|queue|stuck|pending|processing|slow|taking too long|never finish|not finish|not starting|still running)\b/,
        11,
      ],
    ],
    (c) =>
      `A queued job is waiting for the worker; processing means it has started. Open Activity to check its timestamps and message. Avoid submitting repeated copies while one is pending. If it remains queued, the administrator should check Azure Functions/queue access, or the running local worker. A processing delay needs worker logs and storage checks; the UI has no cancel control.${c.data ? ` Your latest loaded overview reports ${c.data.pendingJobs ?? 0} pending job(s).` : " I can’t confirm live pending jobs until your workspace loads."}`,
    [activityPage, settingsPage],
    ["Why did my job fail?", "Will schedules run with the browser closed?"],
  );
  add(
    "checksum",
    "Checksum or manifest errors",
    [
      [
        /\b(checksum|sha.?256|manifest|corrupt\w*|hash mismatch|integrity)\b/,
        13,
      ],
    ],
    "CloudShield uses a SHA-256 manifest to verify the snapshot’s objects. A checksum/manifest error means the selected data could not be verified; don’t treat that recovery as successful. Recovery can use the secondary copy when the primary copy cannot be read or verified. Review the job message and region used, ask the administrator to inspect storage, and create and test a new snapshot after the cause is fixed.",
    [activityPage, testsPage],
    ["What if the primary region fails?", "How do I create a backup?"],
  );
  add(
    "fallback",
    "Primary-region failure",
    [
      [
        /\b(fallback|failover|outage|disaster|primary region fails|region.*down|region.*fail|primary.*unavailable|secondary recovery)\b/,
        13,
      ],
    ],
    "When a primary snapshot cannot be read or verified, the recovery worker can try its secondary copy. Job details show which region was used. This protects backup data, but the dashboard/control plane runs in the primary region; a complete regional outage needs the documented secondary recovery procedure. Two copies alone do not provide automatic full-application failover.",
    [testsPage, page("resources", "View resources →")],
    ["What does replicated mean?", "What does CloudShield protect?"],
  );
  add(
    "cleanup",
    "Cleanup needs attention",
    [
      [
        /\b(cleanup|clean up|cleaned|temporary objects|test environment|leftover)\b/,
        13,
      ],
    ],
    "A recovery test uses temporary objects and verifies their removal. CLEANED means cleanup was confirmed. A failed or unconfirmed cleanup needs the operation details and administrator review of storage permissions and worker logs; restoring successfully does not prove cleanup succeeded. Fix the cause and rerun the test. Manual Restore jobs intentionally retain their recovery copy. No persistent test compute is created.",
    [testsPage, activityPage],
    ["Where is my restored copy?", "Why is my readiness score low?"],
  );
  add(
    "notifications",
    "Email and notification problems",
    [[/\b(notification\w*|email|inbox|smtp|mail|delivery|log only)\b/, 10]],
    (c) =>
      `Check the job’s notification status and notification error in Activity. Delivery depends on the configured notification service; local/log-only settings record the result without sending email. A notification failure can occur even after a successful backup or recovery, so inspect the job status separately. Ask the administrator to check recipient and provider configuration.${c.data ? ` This workspace reports notifications as: ${c.data.notifications || "not configured"}.` : ""}`,
    [activityPage, settingsPage],
    ["Are accounts private?", "Why did my job fail?"],
  );
  add(
    "schedule",
    "Automatic schedules",
    [
      [
        /\b(schedule\w*|automatic\w*|automated|daily|weekly|when.*backup|when.*test|02:00|02:30|timezone|time zone|ist)\b/,
        10,
      ],
    ],
    "Backups are scheduled daily at 02:00 IST (Asia/Kolkata); recovery tests are scheduled Sunday at 02:30 IST. Overview and Configuration show the next scheduled times. Azure timers run independently when enabled. If a run is missing, check Activity, then have the administrator inspect the scheduler/worker. Changing schedules requires deployment configuration; this dashboard has no schedule editor.",
    [settingsPage, activityPage],
    ["Will schedules run with the browser closed?", "My backup is stale"],
  );
  add(
    "closed",
    "Browser closed or app sleeping",
    [
      [
        /\b(browser|tab|dashboard|app|computer)\b.*\b(closed|close|sleep\w*|off|running|shutdown)\b/,
        13,
      ],
      [/\b(free tier|f1|keep.*running)\b/, 10],
    ],
    (c) =>
      `${c.data?.mode === "local" ? "You’re using local mode: keep the application running for local schedules and jobs." : "In Azure mode, enabled Functions timers and workers run independently of this browser tab."} Closing the dashboard does not turn off Azure scheduling. The F1 dashboard can sleep and take time to wake; Azure Storage and Functions are separate services. For a missed job, check Activity and ask the administrator to inspect their configuration.`,
    [settingsPage, activityPage],
  );
  add(
    "rto",
    "Recovery time (RTO)",
    [
      [
        /\b(rto|recovery time|restore time|milliseconds|millis|recovery speed)\b/,
        13,
      ],
    ],
    "RTO here is the observed duration of restoring and verifying the sample data. Smaller values mean faster measured recovery for that test. It excludes later comparison with the live source, notifications, cleanup, and complete infrastructure failover. It is a measurement for this bounded dataset, not a promised production recovery time.",
    [testsPage, page("analytics", "View analytics →")],
    ["What is RPO?", "Why are metrics blank?"],
  );
  add(
    "rpo",
    "Backup age (RPO)",
    [
      [
        /\b(rpo|recovery point objective|backup age|data loss window|how much data.*lose)\b/,
        13,
      ],
    ],
    "RPO here is the age of the selected snapshot when the recovery test starts. It indicates how far back that recovery point is; a newer backup generally has a smaller age. It is an observation, not a guaranteed loss limit or SLA. Changed/deleted objects show differences against the source at comparison time, which is a separate measurement.",
    [testsPage, backupPage],
    ["What is RTO?", "What do changed objects mean?"],
  );
  add(
    "differences",
    "Changed or deleted objects",
    [
      [
        /\b(changed|deleted|modified|difference\w*|comparison|compare|lost bytes)\b.*\b(objects|bytes|records|data|source|backup)\b/,
        12,
      ],
      [/\b(changedbytes|changedobjects|deletedobjects)\b/, 12],
    ],
    "Recovery comparison checks the snapshot against the current source. Changed objects have different content; deleted objects existed in the snapshot but are absent from the current source. Changed bytes count whole current objects that changed, not exact changed characters or lost records. Add a demo registration after a backup and test that backup to see the difference.",
    [testsPage, samplePage],
    ["What is RPO?", "How do I demonstrate recovery?"],
  );
  add(
    "metrics",
    "Blank or unavailable metrics",
    [
      [
        /\b(blank|null|unmeasured|unavailable metrics|missing metrics|dash instead|no rto|no rpo)\b/,
        12,
      ],
    ],
    "A dash or Unmeasured means the metric is unavailable, not zero. Pending/failed jobs may not have reached measurement, and non-recovery jobs do not have the same metrics as a recovery test. Open operation details for status and errors. Run a successful recovery test to get measured restore/verification time and comparison results.",
    [activityPage, testsPage],
  );
  add(
    "reports",
    "Export a report",
    [[/\b(report\w*|export|csv|pdf|print|audit|download)\b/, 11]],
    "Open Reports to preview your workspace summary and operations. Date filters affect operation rows and totals; readiness still describes the latest snapshot. Export CSV downloads the filtered rows. Save as PDF opens your browser’s print dialog: choose a PDF destination. The complete JSON audit export is also available.",
    [reportPage],
    ["How do I filter history?", "What is RTO?"],
  );
  add(
    "filters",
    "Search and filter history",
    [
      [
        /\b(search|filter\w*|history|ctrl.?k|cmd.?k|find.*job|find.*snapshot)\b/,
        10,
      ],
    ],
    "Use Search workspace (Ctrl/Cmd+K) to find pages, backup IDs, and operations. Backups, Recovery tests, and Activity support search/status/date filters. Clear filters if expected rows are hidden. Today uses India Standard Time, and your filters survive live dashboard updates.",
    [backupPage, activityPage],
  );
  add(
    "theme",
    "Light and dark themes",
    [[/\b(theme|dark|light|appearance|color|colour)\b/, 12]],
    "Use the theme button in the top bar to switch between light and dark. Your preference is saved in this browser. Ask CloudShield follows the same theme, and the dashboard adapts to smaller screens.",
  );
  add(
    "alerts",
    "Review workspace alerts",
    [[/\b(alert\w*|warning\w*|mark reviewed|acknowledg\w*|dismiss)\b/, 9]],
    "Alerts centre highlights missing/stale backups, incomplete replication, untested snapshots, failed jobs, cleanup problems, and notification failures. Open an alert’s suggested page or job to investigate. Mark reviewed records an acknowledgement for this workspace in this browser; it doesn’t repair the issue. The condition remains until the underlying evidence improves.",
    [alertsPage],
    ["Why is my readiness score low?", "Why did my job fail?"],
  );
  add(
    "signin",
    "Sign-in or expired session",
    [
      [
        /\b(sign in|signin|login|log in|log out|logout|sign out|session|401|403|unauthorized|forbidden|password|google.*error)\b/,
        12,
      ],
    ],
    "If the session expires, sign in again using the same Google account, or the administrator/demo credentials if that login is enabled. Check that cookies are allowed for this site. For a Google login error, the administrator should check OAuth client settings and the configured redirect URL. Refresh an old tab before retrying a rejected request. I cannot reset credentials or access passwords.",
    [settingsPage],
    ["Are accounts private?", "Why can’t the dashboard connect?"],
  );
  add(
    "privacy",
    "Private account workspaces",
    [
      [
        /\b(private|privacy|accounts|another account|other users|google data|read.*gmail|access.*gmail|share.*data|different account|my data.*safe)\b/,
        13,
      ],
    ],
    "Google sign-in gives each account a separate CloudShield workspace for its sample data, backups, jobs, and reports. Sign in with the same account to see your prior history. Google is used for identity; CloudShield doesn’t read your Gmail inbox or Drive files. Chat queries are matched locally and aren’t sent to an AI provider. Shared browsers should be signed out when finished.",
    [settingsPage],
    ["What does CloudShield protect?", "About this assistant"],
  );
  add(
    "mode",
    "Azure and local mode",
    [
      [
        /\b(azure|local|simulation|geograph\w*|regions|storage|blob|table|architecture)\b/,
        7,
      ],
    ],
    (c) =>
      `Azure mode stores application objects and snapshots in Azure Blob Storage, with job metadata/queues and a Functions worker. Independent primary and secondary stores provide two-region backup copies. Local mode emulates them as folders on one computer, so it offers no geographical redundancy.${c.data ? ` Your latest loaded workspace is in ${c.data.mode} mode.` : " Your workspace hasn’t loaded yet, so I can’t confirm its mode."}`,
    [settingsPage, page("resources", "View resources →")],
  );
  add(
    "cost",
    "Costs and charges",
    [[/\b(cost\w*|price|pricing|charge\w*|bill\w*|free|subscription)\b/, 13]],
    "Ask CloudShield uses local predefined answers, so it adds no AI API charges. Azure costs depend on your App Service plan, storage copies/versions, requests, Functions usage, and other configured services. An F1 dashboard doesn’t make all Azure services free. Check Azure Cost Management for actual charges; the logical bytes shown here are not a billing estimate.",
    [settingsPage],
  );
  add(
    "limits",
    "Dataset and backup limits",
    [
      [
        /\b(limit\w*|maximum|max objects|max bytes|too large|too many|size cap|bounded)\b/,
        14,
      ],
    ],
    (c) =>
      `The project enforces object-count and byte limits for its bounded sample dataset.${c.data ? ` Configuration reports a maximum of ${c.data.maxObjects ?? "unavailable"} objects and ${c.data.maxBytes ?? "unavailable"} bytes.` : " Open Configuration for the configured limits once the workspace loads."} If a job exceeds a limit, review its message and reduce the dataset, or have the administrator deliberately revise configuration and capacity before retrying.`,
    [settingsPage, activityPage],
  );
  add(
    "retention",
    "Snapshot retention",
    [
      [
        /\b(retention|retain.*backup|keep.*backup|how long|delete.*backup|remove.*backup|delete.*snapshot|remove.*copy|delete.*copy)\b/,
        13,
      ],
    ],
    "Snapshots and successful manual recovery copies are retained until explicitly removed; this dashboard currently has no deletion or retention-policy editor. TEST copies are temporary and cleaned after verification. Storage cleanup should be performed by the administrator under a deliberate retention plan so recovery evidence and required copies aren’t removed accidentally.",
    [backupPage, settingsPage],
  );
  add(
    "demo",
    "Try the recovery demonstration",
    [
      [
        /\b(demo\w*|demonstrat\w*|campus|registration\w*|sample|tutorial|walkthrough)\b/,
        9,
      ],
    ],
    "Try this sequence in Sample application: 1) Create a backup. 2) Add demo registration to the live source. 3) Test the earlier backup and inspect changed objects. 4) Restore that snapshot and open its recovery copy. Use the data-source selector to compare restored data with the live source. These are synthetic course registrations in your workspace.",
    [samplePage, backupPage],
    ["How do I test recovery?", "Does restore overwrite my data?"],
  );
  add(
    "connection",
    "Dashboard connection problems",
    [
      [
        /\b(connection|connect|offline|network|fetch|loading|load|refresh|502|503|timeout|unreachable)\b/,
        12,
      ],
    ],
    "Check your network, then use Retry connection or reload the page. If redirected to login, sign in again. A sleeping Azure dashboard may take time to wake up. If errors persist, the administrator should check App Service health/logs and storage/worker configuration. I can still explain FAQs, but an unavailable connection means I cannot confirm current workspace status.",
    [activityPage, settingsPage],
  );
  add(
    "resources",
    "Resource status",
    [
      [
        /\b(resources?|health probe|healthy|region status|green.*region|region.*green)\b/,
        11,
      ],
    ],
    "Resources shows the sample application, configured backup stores, and successful retained recovery copies. Its indicators come from recorded snapshots and jobs; they are not live Azure availability probes. Use Azure monitoring and service health for infrastructure outages, and a new recovery test for fresh recovery evidence.",
    [page("resources", "View resources →"), testsPage],
  );
  add(
    "analytics",
    "Read your analytics",
    [
      [
        /\b(analytics|chart\w*|success rate|statistics|logical bytes|protected bytes|protected data)\b/,
        12,
      ],
    ],
    "Analytics shows recorded snapshot activity, logical bytes, completed-operation success rate, and measured successful recovery tests. Pending jobs are excluded from success rates. Logical bytes count one snapshot copy and exclude extra replicas, stored versions, and billing overhead. Date filters let you compare recorded periods.",
    [page("analytics", "View analytics →"), reportPage],
  );
  add(
    "security",
    "Storage and access security",
    [
      [
        /\b(encrypt\w*|secure|security|managed identity|access keys|credentials)\b/,
        12,
      ],
    ],
    "The Azure deployment uses HTTPS for the dashboard and Azure Blob Storage encryption at rest. Google accounts have private workspaces, and the dashboard uses a managed identity for its configured Azure access. Keep passwords and storage keys out of chat and reports. These controls support the demo’s access and storage protection; they are not a security certification or a guarantee against every threat.",
    [settingsPage],
    ["Are accounts private?", "What does CloudShield protect?"],
  );
  add(
    "greeting",
    "Hello!",
    [[/^(hi|hello|hey|thanks|thank you|good morning|good evening)[!. ]*$/, 20]],
    "Hi! I’m Ask CloudShield. Tell me what you’re trying to do, or choose a question below. I can help with backups, recovery tests, restores, scores, and common problems.",
    [],
    [
      "How do I create a backup?",
      "Why is my readiness score low?",
      "How do I restore data?",
    ],
  );

  function normalize(value) {
    return String(value ?? "")
      .slice(0, 500)
      .normalize("NFKC")
      .toLowerCase()
      .replace(/[’']/g, "")
      .replace(/cloud\s+shield/g, "cloudshield")
      .replace(/back[ -]+ups?/g, "backup")
      .replace(/\bbakcup\b|\bbackp\b/g, "backup")
      .replace(/\bcant\b/g, "cannot")
      .replace(/\s+/g, " ")
      .trim();
  }
  function match(value, previous) {
    const query = normalize(value);
    if (/<\/?[a-z][^>]*>/i.test(query)) return null;
    if (
      previous &&
      /^(how (do i |can i )?(fix|resolve|solve) (it|this|that)|tell me more|what should i do|and then)[?.!]*$/.test(
        query,
      )
    )
      return topics.find((t) => t.id === previous) || null;
    const ranked = topics
      .map((t) => ({
        t,
        score: t.rules.reduce(
          (s, [regex, weight]) => s + (regex.test(query) ? weight : 0),
          0,
        ),
      }))
      .sort((a, b) => b.score - a.score);
    return ranked[0].score >= 3 ? ranked[0].t : null;
  }
  function answer(value, context = {}, previous = null) {
    const clauses = String(value ?? "")
      .slice(0, 500)
      .split(/\?\s*|;\s*|\band\s+(?=(?:how|why|what|when|can|does|will)\b)/i)
      .filter((s) => s.trim())
      .slice(0, 3);
    const selected = [
      ...new Map(
        clauses
          .map((q) => match(q, previous))
          .filter(Boolean)
          .map((t) => [t.id, t]),
      ).values(),
    ].slice(0, 2);
    if (/\brto\b/i.test(value) && /\brpo\b/i.test(value)) {
      for (const id of ["rto", "rpo"])
        if (!selected.some((t) => t.id === id))
          selected.push(topics.find((t) => t.id === id));
      selected.splice(2);
    }
    if (!selected.length)
      return {
        id: "unknown",
        text: "I don’t have a reliable FAQ match for that yet. Try describing the CloudShield action, status, or error—for example ‘backup stuck in queue’ or ‘restore checksum error’. I can help with the topics below.",
        links: [],
        suggestions: [
          "How do I create a backup?",
          "Why did my job fail?",
          "What does CloudShield protect?",
        ],
      };
    return {
      id: selected[0].id,
      text: selected
        .map(
          (t) =>
            (selected.length > 1 ? t.title + "\n" : "") +
            (typeof t.text === "function" ? t.text(context) : t.text),
        )
        .join("\n\n"),
      links: [
        ...new Map(
          selected.flatMap((t) => t.links).map((l) => [l.href, l]),
        ).values(),
      ],
      suggestions: selected[0].suggestions.length
        ? selected[0].suggestions
        : [
            "Why is my readiness score low?",
            "Why did my job fail?",
            "How do I restore data?",
          ],
    };
  }
  return {
    answer,
    match,
    topics: topics.map(({ id, title }) => ({ id, title })),
  };
})();
