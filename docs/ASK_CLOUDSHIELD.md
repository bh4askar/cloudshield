# Ask CloudShield

The bottom-right launcher shows the supplied blue robot logo and **Ask CloudShield**
name. It opens a compact, nonmodal chat; the dashboard remains usable. The panel has
a scrollable conversation, typed questions, suggested follow-ups, a short typing
indicator, internal page links, minimize, and Start a new chat. Light/dark themes,
keyboard navigation, Escape, reduced motion, and phone layouts are supported.

## Answers and scope

This is a predefined FAQ assistant, not a generative model. Queries are matched
locally against curated, weighted phrase patterns. Unknown queries get an honest
fallback and examples. Multiple explicit questions can receive two topic answers;
short "how do I fix it?" follow-ups retain the previous issue. Answers link to
existing pages and never submit operations on the user's behalf.

Coverage includes creating/finding backups, stale snapshots, replication, restores,
source isolation, retained copies, recovery tests, readiness scoring, failed/queued
jobs, checksum/manifest errors, secondary fallback, cleanup, notifications, IST
schedules, closed/sleeping dashboards, RTO/RPO, comparison metrics, exports, filters,
themes, alerts, sign-in, private workspaces, modes/architecture, costs, dataset
limits, retention, the sample demo, connection problems, resources, analytics, and
storage/access security. The assistant describes CloudShield's bounded sample
application honestly and does not promise personal file backup or full stack failover.

Readiness answers reuse the dashboard's existing scoring function. Counts, mode,
regions, notification setting, and limits use only the latest loaded authenticated
overview. Replies clearly identify loaded data; unavailable data is not invented.
Replies are snapshots of that context at question time, not a continuously updating
monitor. Chat history stays intact during dashboard polling and page navigation.

## Privacy and implementation

No query leaves the browser, no AI API key is needed, and there are no assistant
usage charges. Conversations are held in memory in this tab, limited to 40 message
entries, and cleared by reload or Start a new chat. Queries are capped at 500
characters. Text is rendered with `textContent`; page links come from the static
knowledge list. No chat storage, external assets, inline handlers, or CSP exceptions
are introduced. Widget assets remain behind the dashboard's existing authentication.

The robot badge is cropped from the user-supplied screenshot, with only its outer
white background removed; the face and original blue logo are preserved.

## Validation

`node scripts/check-faq.cjs` checks common phrasings, specific error priorities,
multi-question/follow-up matching, unknown inputs, loading/live data, and safe
integration. GitHub CI runs it alongside the dashboard checks and Maven verification.
Browser QA checks real chat replies, bounded expansion, history/input surviving
polling and navigation, reset during a pending reply, escaped query display,
keyboard controls, both themes, and phone/landscape layouts under the existing CSP.
