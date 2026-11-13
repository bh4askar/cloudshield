# Azure setup and operation

CloudShield uses Java 21 with Azure Blob Storage, Table Storage, Storage Queues and Azure Functions Flex Consumption. The dashboard runs on the operator's computer. Public application hosting is a separate, deferred step.

## Student deployment

The installed resource group is `cloudshield-g360`. Primary storage and the worker are in `centralindia`; the independent second backup account is in `koreacentral`. These regions are allowed by the current student subscription policy. The source/primary, secondary and recovery accounts are separate. Recovery is deliberately unversioned with soft deletion disabled so test cleanup removes its bytes.

The infrastructure uses Standard LRS storage, no VM, no premium plan and no always-ready Functions instances. Functions can scale to zero. The platform's minimum configurable maximum is 40 instances; per-instance queue processing and per-workspace metadata leases constrain concurrent work. Shared storage keys and anonymous blob access are disabled. The worker uses its managed identity; the operator uses Azure CLI sign-in.

## Reproduce in your own subscription

1. Install JDK 21, Maven 3.9+, Python 3.10+, PowerShell and Azure CLI.
2. Run `az login`, select the intended student subscription and inspect its allowed-region policy.
3. Run `mvn clean verify` from the project root.
4. Register `Microsoft.Storage`, `Microsoft.Web`, `Microsoft.Insights` and `Microsoft.OperationalInsights` resource providers if needed.
5. Run `scripts/deploy-azure.ps1 -BudgetEmail YOUR_EMAIL`. The script provisions the template and uploads the packaged worker. It leaves schedules disabled for acceptance testing.
6. Dot-source `scripts/load-azure-environment.ps1`. Set a unique `CLOUDSHIELD_PASSWORD` with at least 12 characters, then run `java -jar web/target/cloudshield-web-1.0.0.jar`.
7. Seed the administrator sample source using `az storage blob upload-batch --account-name YOUR_PRIMARY_ACCOUNT --auth-mode login --source sample-data/application --destination source --destination-path application --overwrite false`. Google accounts create their own synthetic workspaces through SETUP jobs.
8. Run a backup, a recovery test, a source change and a retained restore. Verify `REPLICATED`, `SUCCEEDED`, `CLEANED`, and that the old restored data differs from the unchanged live source.

`scripts/check-function-package.py` checks all four triggers. `scripts/package-function.py` creates the deployment ZIP without local settings or credentials. The template intentionally grants the operator broad project data access; the public web application still enforces account isolation. A separately hosted dashboard should receive a separate identity and only the permissions it needs.

## Automation

The worker contains RunJob and PoisonJob queue triggers and DailyBackup and WeeklyRecoveryTest timer triggers. Messages use Base64 JSON. Table conditional insertion prevents duplicate scheduled jobs; conditional ETags serialize each account's engine work. The engine lease lasts 960 seconds, longer than its 600-second function timeout. Queue delivery retries five times with a three-minute retry delay. Exhausted messages go through PoisonJob and remain visible as failed work.

Schedules are UTC expressions because India has a fixed UTC+05:30 offset:

| Job | Azure expression | Local time |
|---|---|---|
| Daily backup | `0 30 20 * * *` | Every day at 02:00 IST |
| Weekly recovery test | `0 0 21 * * 6` | Sunday at 02:30 IST |

Enable schedules after acceptance using `az functionapp config appsettings set -g cloudshield-g360 -n YOUR_FUNCTION_NAME --settings AzureWebJobs.DailyBackup.Disabled=false AzureWebJobs.WeeklyRecoveryTest.Disabled=false -o none`. Disable them with `true`. The dashboard's next-run times describe these configured schedules, not an independent guarantee of execution. Timer invocation and worker completion must be checked in Function logs and job records.

## Credit and cost controls

Azure for Students supplies **$100 total credit for 12 months**, not $100 every month. Keep its spending limit in place and do not upgrade to pay-as-you-go just to complete this project. The subscription can be disabled when the credit expires or is exhausted.

The resource-group budget is 5 units of the subscription's billing currency per month with an email at 80%. A budget sends alerts; it is not a hard cap. Check the currency and accumulated usage in Cost Management and the remaining balance in the Azure Sponsorships portal. Billing data is delayed.

The Log Analytics workspace caps ingestion at 0.1 GB/day and retains logs for 30 days. Sampling reduces normal telemetry. An Azure Monitor query alert checks worker failures and exceptions every five minutes and emails the configured project owner. Monitoring, stored objects, versions, requests and cross-region transfer can use credit even when compute is idle. The caps and small workload are cost controls, not a promise of a particular bill or a full year's availability.

The deployed account limit is 10; each snapshot is limited to 500 objects/20 MiB and each account to 240 backups. This accommodates daily student demonstrations through May 2027. The application stops creating backups at that limit; it does not automatically discard the oldest backup. Source and backup versions older than seven days are eligible for lifecycle cleanup; the current backup objects remain. Inspect retention before adding a new group of accounts or increasing data limits.

## Independent secondary recovery

If the primary metadata service or worker is unavailable, the complete manifest and data are still in the secondary account. This recovery path needs only access to that account:

```bash
python -m pip install azure-identity azure-storage-blob
az login
python scripts/recover-secondary.py --account YOUR_SECONDARY_ACCOUNT
python scripts/recover-secondary.py --account YOUR_SECONDARY_ACCOUNT \
  --tenant WORKSPACE_ID --backup EXACT_BACKUP_ID --destination recovered-copy \
  --manifest-sha256 TRUSTED_DIGEST_FROM_REPORT
```

Omit `--tenant` for the administrator workspace. The destination must be new. Every object is size- and checksum-checked before writing; an independently saved report supplies the trusted manifest digest. Without that digest the tool verifies only against the stored manifest. It never writes over the live source.

## Security, recovery limits and stopping

Google sign-in continues to use the same OAuth application. Keep its secrets in the local private configuration and register the exact callback URL. The current local callback is `http://localhost:8080/login/oauth2/code/google`. Secrets are excluded from Git and source archives. Azure authentication uses `DefaultAzureCredential`; never place account keys in JavaScript.

This is object-level recovery for synthetic Campus Register JSON data. It is not a transactional database backup, a VM image backup or automatic failover of the whole control plane. A regional outage is not implied by a corrupted-copy test. A hard worker kill can leave temporary recovery objects until a later reconciliation run; inspect the recovery container after failures.

To stop recurring work, disable both timer functions. To remove the deployment, first export the report and download required backups, then inspect the exact resource group before deleting it. Resource-group deletion destroys its storage; no teardown script runs automatically.

## Sources

- [Azure for Students](https://learn.microsoft.com/en-us/azure/education-hub/azure-dev-tools-teaching/azure-students-program)
- [Student subscription disabled](https://learn.microsoft.com/en-us/azure/cost-management-billing/manage/azurestudents-subscription-disabled)
- [Functions Flex Consumption](https://learn.microsoft.com/en-us/azure/azure-functions/flex-consumption-plan)
- [Functions identity-based connections](https://learn.microsoft.com/en-us/azure/azure-functions/functions-reference#configure-an-identity-based-connection)
- [Azure Storage Java libraries](https://learn.microsoft.com/en-us/azure/storage/blobs/storage-blob-java-get-started)
