# CloudShield

Automated backup and recovery for a small cloud application. B.Tech CSE major project, Group G360, GLA University, 2026–27.

CloudShield creates timestamped backups, verifies them with SHA-256 checksums, and runs recovery tests in an isolated workspace. The dashboard shows backup history, restore time, backup age, changes since the last snapshot, and cleanup results. A restored copy can be opened alongside the live sample application without overwriting it.

## Current status

The application runs locally, including backups, recovery tests, Google sign-in, and separate user workspaces. The recorded local verification has **37 passing Java tests and 16 passing HTTP checks**.

AWS storage adapters, a Lambda worker, and Terraform configuration are included, but **live AWS integration and cloud acceptance testing are pending**. The current Terraform setup requires two regions. A Sydney-only configuration for the new AWS experience is planned; it is not implemented yet. Local backup folders do not provide geographical redundancy.

## Run locally

Requirements: **JDK 21** and **Maven 3.9+**.

```bash
mvn clean verify
java -jar web/target/cloudshield-web-1.0.0.jar
```

Open [localhost:8080](http://localhost:8080). The local demonstration account is `admin` with password `CloudShield-Demo-2026!`.

After building, Windows users can also run `Start-CloudShield.cmd`; macOS/Linux users can run `sh start-local.sh`. A source checkout does not include compiled JARs or a Java runtime.

To run with Docker:

```bash
docker compose up --build
```

The sample Campus Register application contains synthetic data. Create a backup, add a registration, then restore the earlier snapshot and compare the recovery copy with the live data.

## Features

- Manual backups and daily backups at 02:00 Asia/Kolkata.
- Verified primary and secondary copies, with fallback during recovery.
- Weekly recovery tests on Sunday at 02:30, including cleanup checks.
- Restore duration, backup age, changed objects, and an exportable activity report.
- Google OpenID Connect login with a separate workspace for each account.
- Dark and light themes, responsive layouts, and reduced-motion support.
- AWS support through S3, DynamoDB, Lambda, EventBridge Scheduler, SNS, and CloudWatch.

## Google sign-in

Configure a Google OAuth web client and register the local callback URL:

```text
http://localhost:8080/login/oauth2/code/google
```

Set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` before starting the server. The Windows launcher also reads these two values from `.env.local`. Google sign-in is disabled when credentials are absent; each installation needs its own configuration.

See [Google sign-in setup](docs/GOOGLE_SIGN_IN.md) for account isolation, callback URLs, and consent-screen configuration. Credentials, account data, and local settings are excluded from this repository.

## Project structure

| Directory | Purpose |
|---|---|
| `core` | Backup engine, storage adapters, integrity checks, metadata, and tests |
| `web` | Spring Boot API, authentication, dashboard, and local schedules |
| `worker` | Java Lambda handler using the shared engine |
| `infra` | Terraform configuration for AWS resources |
| `sample-data` | Synthetic Campus Register data |
| `scripts` | Smoke checks, AWS environment loading, and secondary-copy recovery |
| `deploy` | HTTPS reverse proxy configuration |
| `docs` | Architecture, setup guides, test evidence, and project report |

## Tests

```bash
mvn clean verify
```

With a separate local test server running:

```bash
python scripts/smoke-test.py --output docs/validation/local-smoke.json
```

The smoke test changes the administrator's sample workspace and creates backups and recovery copies. Use disposable local data for this check. GitHub Actions builds the Java modules, runs their tests, and validates Terraform; it does not provision AWS resources.

## AWS setup and scope

Follow the [AWS setup guide](docs/AWS_DEPLOYMENT.md) only after checking region support and reviewing the Terraform plan. Cloud services can incur charges. Public website hosting is a separate step and has not been set up.

The protected workload is the sample application's JSON objects. This is not a general database or virtual-machine backup tool. Multi-object snapshots are not transactional, recovery metrics are observations rather than guarantees, and full application failover remains manual. The default workload limit is 500 objects and 20 MiB per snapshot, with 30 retained backups per workspace.

## Documentation

- [Architecture and API](docs/ARCHITECTURE.md)
- [Google sign-in](docs/GOOGLE_SIGN_IN.md)
- [AWS setup and recovery runbook](docs/AWS_DEPLOYMENT.md)
- [Verification results](docs/VALIDATION.md)
- [Project report](docs/PROJECT_REPORT.md)
- [Demonstration and viva notes](docs/DEMO_AND_VIVA.md)
