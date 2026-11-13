# CloudShield

**Live dashboard: [https://cloudshield-g360-dashboard.azurewebsites.net](https://cloudshield-g360-dashboard.azurewebsites.net)**

Automated cloud backup and recovery for the Campus Register sample application. B.Tech CSE major project, Group G360, GLA University, 2026–27.

CloudShield uses **Azure Blob, Table, and Queue Storage with Azure Functions**. Its Java 21 / Spring Boot dashboard is deployed on **Azure App Service**. It creates timestamped SHA-256-verified snapshots, keeps independent copies in Central India and Korea Central, and restores into an isolated workspace. The dashboard shows backup history, recovery time, backup age, data changes, activity and cleanup results. Google sign-in gives each account its own workspace.

## Status

The public dashboard runs on **Azure App Service F1 (Free), Linux, Java 21, in Central India**, alongside the existing backend's primary region. Public HTTPS access and Google sign-in were verified on **13 November 2026**. The dashboard uses its own managed identity to read source/restored blobs, access Table Storage metadata and send jobs to the existing Azure Functions worker through Queue Storage.

The Free tier can sleep when idle, so the first visit may take longer. Scheduled backups and recovery tests run independently in Azure Functions. Backend storage, Functions and monitoring retain their own usage costs. See [validation](docs/VALIDATION.md) for recorded results and verification scope.

## Run the dashboard locally

Requirements: **Git, JDK 21 and Maven 3.9+**. The default local demonstration uses synthetic data and needs no Azure account. Python 3.10+ is only needed for the optional validation scripts.

Clone and build the project from a terminal:

```bash
git clone https://github.com/bh4askar/cloudshield.git
cd cloudshield
mvn clean verify
```

Start the dashboard from the repository root:

```bash
java -jar web/target/cloudshield-web-1.0.0.jar
```

Open **[http://localhost:8080](http://localhost:8080)** and sign in with username **`admin`** and password **`CloudShield-Demo-2026!`**. These credentials are for the local demonstration. Keep the terminal open; press **Ctrl+C** to stop. Local application data is stored in the gitignored `data/` directory.

Local mode is the default when `CLOUDSHIELD_MODE` is unset. If your terminal already has Azure mode configured, use `Start-CloudShield.cmd` on Windows or `sh ./start-local.sh` on macOS/Linux after building; these launchers select local mode explicitly. The Windows distribution package includes a Java runtime and built application.

To run the dashboard locally against real Azure storage, follow [Azure setup](docs/AZURE_SETUP.md) and use `Start-CloudShield-Azure.cmd` on Windows. Sign into Azure CLI and provide the documented storage settings and a unique administrator password. Google sign-in is optional locally; configure your own OAuth client using [Google sign-in setup](docs/GOOGLE_SIGN_IN.md). Credentials and private settings are excluded from the repository and distribution archives.

## Stack and components

The application uses **Java 21, Spring Boot 3.5.7, Spring Security, Google OpenID Connect, and HTML/CSS/JavaScript**. The Maven modules are `web` (dashboard and APIs), `core` (shared backup/recovery engine) and `worker` (Azure Functions).

| Function | Azure service |
|---|---|
| Public dashboard and web API | Azure App Service F1 (Free), Linux / Java 21, Central India |
| Source and verified regional copies | Blob Storage, two independent accounts/regions |
| Disposable and retained restores | A separate unversioned Blob Storage account |
| Account, job and snapshot metadata | Table Storage with conditional ETag leases |
| Background work and retries | Azure Queue Storage and Java Azure Functions (Flex Consumption) |
| Daily backup / weekly test | Azure Functions timer triggers, 02:00 / Sunday 02:30 IST |
| Logs and failure notifications | Application Insights, capped Log Analytics and Azure Monitor email alerts |
| Spending notifications | Resource-group monthly budget |

In Azure mode, the dashboard records a job in Table Storage and submits it to Queue Storage. The Functions worker performs the backup or recovery operation in Blob Storage and updates the job record. The dashboard then displays the result. See the [architecture diagram and API](docs/ARCHITECTURE.md) for the full flow.

## Run the tests

Run the Java unit/integration suite from the repository root, then check the packaged Functions bindings and Azure dependencies:

```bash
mvn clean verify
python scripts/check-function-package.py
```

**Current recorded Java result (13 November 2026): 41 passed, 4 optional Azure integration tests skipped, 0 failures.** The default build skips the four live tests because they require an authenticated Azure environment. After following [Azure setup](docs/AZURE_SETUP.md), opt in with `CLOUDSHIELD_AZURE_TESTS=true` and run `mvn -pl core -Dtest=AzureIntegrationTest test`; these tests create disposable synthetic workspaces in real Azure storage.

With the local dashboard running in another terminal, exercise the complete HTTP backup/recovery workflow:

```bash
python scripts/smoke-test.py --url http://localhost:8080
```

The HTTP smoke test changes the synthetic sample data and creates backups/restores. Use `--mode azure` only against a designated cloud test workspace. GitHub Actions runs the Java suite and checks the Function package and infrastructure JSON on pushes and pull requests.

## Verified public deployment

| Check on 13 November 2026 | Result |
|---|---|
| Public App Service deployment checks | 16 passed |
| Public HTTPS and Google sign-in | Verified against the deployed dashboard |
| Existing Functions worker recovery test | `SUCCEEDED` |
| Temporary recovery objects | `CLEANED` |
| Observed restore and verification time | 351 ms |
| Source data after verification | Unchanged; 0 changed objects |

The 351 ms measurement is one observed recovery run on the small synthetic dataset. [Deployment evidence](docs/validation/app-service.json) records the checks and job result; [validation notes](docs/VALIDATION.md) describe the earlier direct Azure tests, scheduled jobs and independent secondary recovery.

## Deployment and operation

The existing backend is defined in `infra/azure/main.json`. The dashboard is deployed separately as App Service `cloudshield-g360-dashboard` on the `cloudshield-dashboard-f1` plan in resource group `cloudshield-g360`. Both the dashboard and primary backend are in `centralindia`; the independent secondary backup account is in `koreacentral`.

[The setup guide](docs/AZURE_SETUP.md) includes the App Service configuration, backend deployment, scheduling, secondary-only recovery, limits, costs and teardown. The $100 student credit is a total balance for 12 months. Budget alerts are notifications, not spending caps.

## Project material

- [Architecture and API](docs/ARCHITECTURE.md)
- [Project report](docs/PROJECT_REPORT.md)
- [Demo and viva](docs/DEMO_AND_VIVA.md)
- [Google sign-in](docs/GOOGLE_SIGN_IN.md)
- [Validation](docs/VALIDATION.md)

The protected workload is bounded JSON object data, not arbitrary databases or virtual machines. Multi-object capture is not transactional, and full application/control-plane failover remains manual. Recovery measurements describe the observed test, not an availability guarantee.
