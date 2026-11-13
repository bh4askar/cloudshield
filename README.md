# CloudShield

Automated cloud backup and recovery for the Campus Register sample application. B.Tech CSE major project, Group G360, GLA University, 2026–27.

CloudShield uses **Microsoft Azure**. It creates timestamped SHA-256-verified snapshots, keeps independent copies in Central India and Korea Central, and restores into an isolated workspace. The dashboard shows backup history, recovery time, backup age, data changes, activity and cleanup results. Google sign-in gives each account its own workspace.

## Status

Azure storage and the Java Functions worker are deployed. Real Azure tests passed for cross-region backup, corrupted-primary fallback, immutable source reads, tenant isolation and concurrent locking. The complete HTTP workflow passed 16 checks. See [validation](docs/VALIDATION.md) for evidence and the precise verification scope.

The dashboard runs locally. Public website hosting remains deferred. The Google Sites homepage is an information page, not the Java application host.

## Run

Requirements for a source checkout: JDK 21 and Maven 3.9+.

```bash
mvn clean verify
java -jar web/target/cloudshield-web-1.0.0.jar
```

Local mode uses `admin` / `CloudShield-Demo-2026!` at http://localhost:8080. `Start-CloudShield.cmd` always starts this offline demonstration. The Windows package includes a Java runtime and built application.

For real Azure storage, follow [Azure setup](docs/AZURE_SETUP.md) and use `Start-CloudShield-Azure.cmd`. The configured installation reuses your Google OAuth credentials. Source archives exclude account credentials and all private settings. On another computer, sign into Azure CLI and configure OAuth separately.

## Cloud services

| Function | Azure service |
|---|---|
| Source and verified regional copies | Blob Storage, two independent accounts/regions |
| Disposable and retained restores | A separate unversioned Blob Storage account |
| Account, job and snapshot metadata | Table Storage with conditional ETag leases |
| Background work and retries | Storage Queues and Java Azure Functions |
| Daily backup / weekly test | Azure timer triggers, 02:00 / Sunday 02:30 IST |
| Logs and failure notifications | Application Insights, capped Log Analytics and Azure Monitor email alerts |
| Spending notifications | Resource-group monthly budget |

## Tests and deployment

```bash
mvn clean verify
python scripts/check-function-package.py
python scripts/smoke-test.py --url http://127.0.0.1:8080
```

The normal suite skips the four opt-in Azure integration tests. Set `CLOUDSHIELD_AZURE_TESTS=true` and the documented Azure environment to run them; they create disposable synthetic test workspaces. Use `--mode azure` for the HTTP smoke test only against a designated test workspace. GitHub Actions builds Java and checks the Function package/template syntax; it does not provision resources or run cloud tests.

Deployment is defined in `infra/azure/main.json`. [The setup guide](docs/AZURE_SETUP.md) includes deployment, scheduling, secondary-only recovery, limits, costs and teardown. The $100 student credit is a total balance for 12 months. Budget alerts are notifications, not spending caps.

## Project material

- [Architecture and API](docs/ARCHITECTURE.md)
- [Project report](docs/PROJECT_REPORT.md)
- [Demo and viva](docs/DEMO_AND_VIVA.md)
- [Google sign-in](docs/GOOGLE_SIGN_IN.md)
- [Validation](docs/VALIDATION.md)

The protected workload is bounded JSON object data, not arbitrary databases or virtual machines. Multi-object capture is not transactional, and full application/control-plane failover remains manual. Recovery measurements describe the observed test, not an availability guarantee.
