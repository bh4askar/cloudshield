# Dashboard features

The November 2026 interface update adds visible recovery controls to the existing
Java/Spring Boot and Azure application. It uses the authenticated overview and job
APIs; it does not introduce simulated threats, invented security measurements, or
new cloud resources.

## Recovery readiness

Click the score ring or "View score breakdown" to inspect the evidence checklist
for the latest recorded snapshot:

| Check | Points |
| --- | ---: |
| A snapshot exists | 25 |
| Latest snapshot is verified in both configured backup stores | 25 |
| Latest snapshot is within 24 hours | 20 |
| Most recent test of that snapshot succeeded within seven days | 20 |
| That test confirmed cleanup | 10 |

A newer failed or pending test supersedes an older success for this snapshot. A test
of a different snapshot cannot validate the latest one. The score is a recovery
evidence indicator, not a cybersecurity audit or an availability guarantee. In local
mode the two stores are simulation folders on one computer.

## Alerts and live operations

The alerts centre identifies missing or stale backups, incomplete regional replication,
untested recovery points, failed jobs, unconfirmed cleanup, and notification failures.
Search and severity chips help review the evidence. "Mark reviewed" acknowledges an
alert in browser storage scoped to the workspace; it does not repair the underlying
condition or update Azure metadata. Conditions remain visible until fixed.

Creating a backup, recovery test, or restore opens operation progress. Queued,
processing, and completion stages follow actual API timestamps and status. The
dashboard and open operation details refresh every four seconds while the tab is
visible. No fictional percentage or individual worker substage is shown.

## Analytics, resources, and search

Analytics supports Today (India Standard Time), last seven days, last 30 days, and
all recorded history. Charts show snapshots per day, daily logical snapshot bytes,
completed-operation success rate, and measured successful recovery tests. Pending
jobs are excluded from success-rate calculations. Logical bytes exclude replica
copies, stored versions, and Azure billing overhead. Days with snapshots are shown.

Resource cards describe Campus Register, the configured primary and secondary
stores, and successful retained recovery copies. Their status comes from job and
snapshot records; the page does not probe service availability.

Workspace search (Ctrl/Cmd+K) finds pages, snapshots, and operations. Snapshot history,
recovery-test history, and activity combine search, status, and date filters. Input
focus and filters survive live refreshes. Icon navigation has accessible names and
tooltips at compact widths.

## Reports

The reports page previews the current workspace report and filters operation totals
and rows by date range. Current readiness always refers to the latest snapshot across
recorded history. CSV export downloads the filtered operation rows, with quoted cells
and formula neutralization. "Save as PDF" opens the browser print dialog; choose its
PDF destination. Print styles hide navigation, repeat table headers, and retain the
workspace summary and recovery evidence. The original complete JSON audit export
remains available.

## Validation

- `node scripts/check-dashboard.cjs`: score, alert, date-range, search, CSV,
  strict-CSP graphic, and empty/populated rendering checks, without dependencies.
- `mvn verify`: existing core, web, security, tenant-isolation, and worker tests.
- `python scripts/smoke-test.py --url http://127.0.0.1:8090`: synthetic local
  backup, recovery test, source mutation, retained restore, cleanup, and audit export.
- Browser review: actual operation progress, workspace search, date filters, CSV
  download, PDF print invocation, resource/alerts/report pages, and phone layout.

Score and outcome visuals use SVG presentation attributes and external CSS so the
application's strict content security policy remains effective.
