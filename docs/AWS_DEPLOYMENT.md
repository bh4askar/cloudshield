# AWS deployment and acceptance runbook

## 1. Prerequisites

- An AWS account with permission to provision S3, DynamoDB, Lambda, IAM, EventBridge Scheduler, SNS, SQS and CloudWatch.
- AWS CLI v2, Terraform 1.6+ and the compiled worker JAR. The infrastructure was validated with Terraform 1.10.5 and AWS provider 5.100.0.
- Java 21 for running the dashboard, or Docker Engine with Compose for a container deployment.
- A group email address that can confirm an SNS subscription.
- For a public dashboard: a Linux host with Docker, a public DNS hostname pointing to it, and incoming ports 80/443. A small EC2 host can be used, but hosting infrastructure is deliberately separate from the backup resources.

Use temporary credentials through AWS IAM Identity Center or an IAM role. Do not use the AWS root user, hard-code access keys, or commit `.env.production`, `terraform.tfvars` or Terraform state. Enable an AWS Budget notification before sustained experimentation. Current account credits/free-tier rules and regional pricing must be checked in your account; this deployment is not guaranteed to be free.

## 2. Build and provision the backup resources

From the project root:

```powershell
mvn clean verify
aws sso login --profile cloudshield
$env:AWS_PROFILE = 'cloudshield'
aws sts get-caller-identity
Set-Location infra
Copy-Item terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars`: choose a unique project prefix, two distinct regions, and your email. Keep the worker JAR path at its default after a normal build. If using the prebuilt distribution, both JARs are already included.

```powershell
terraform init
terraform fmt -check
terraform validate
terraform plan -out=cloudshield.tfplan
terraform apply cloudshield.tfplan
```

Review the plan before applying. Applying creates chargeable resources. The default regions are Mumbai (`ap-south-1`) and Singapore (`ap-southeast-1`). The Lambda worker runs in the primary region, with a maximum execution time of 15 minutes, 1 GiB memory and reserved concurrency one. Some new AWS accounts need a concurrency quota increase before reserved concurrency can be assigned.

The plan creates four buckets, a metadata table, a worker function, daily/weekly schedules, roles and policies, email alerts, a failed-event queue, log retention and CloudWatch alarms. It does **not** create a public application server, domain or certificate; see section 5.

Store Terraform state securely. For group work, configure an encrypted remote state backend with locking before applying from multiple laptops. Do not independently apply the same infrastructure from separate unshared state files.

## 3. Seed the cloud application and confirm alerts

Open the **AWS Notifications - Subscription Confirmation** message and confirm the SNS subscription. Until confirmed, email delivery is not active even though publishing to SNS can succeed.

```powershell
$sourceBucket = terraform output -raw source_bucket
aws s3 cp ..\sample-data\application "s3://$sourceBucket/application/" --recursive --sse AES256
Set-Location ..
```

S3 objects are now the source of truth for the administrator's demo workspace. New Google accounts are initialized automatically in their own tenant prefixes. The dashboard's Add registration action queues a worker update to the current account's sample application in both local and AWS modes. You may also upload changed JSON directly as an operator. Do not upload real student data for a public demonstration.

## 4. Connect the Java dashboard from your laptop

Attach the generated `dashboard_policy_arn` policy to the role used by your dashboard session, or have the account administrator provide an equivalent role. The policy is created but not automatically attached to your AWS user.

```powershell
. .\scripts\load-aws-environment.ps1
$env:AWS_PROFILE = 'cloudshield'
$env:CLOUDSHIELD_PASSWORD = 'choose-your-own-unique-long-password'
.\runtime\windows\bin\java.exe -jar web\target\cloudshield-web-1.0.0.jar
```

Use `java` instead of the runtime path on another operating system. Open `http://127.0.0.1:8080`. The header must show **AWS CONNECTED**. This badge indicates configured mode, not a successful cloud health check; complete section 6 to prove connectivity.

Do **not** use `Start-CloudShield.cmd` for this step: that launcher explicitly selects local mode.

SSO credential support requires the SDK SSO modules included in this project. Refresh expired SSO credentials with `aws sso login`. If using an instance role, leave `AWS_PROFILE` unset. Do not expose the plain HTTP dashboard directly to the internet.

For public Google account creation, also follow `GOOGLE_SIGN_IN.md`, configure the dashboard OAuth credentials and publish the Google OAuth application.

## 5. Publish the dashboard with HTTPS

The recommended container setup runs the Java app as a non-root user on an internal Docker network. Caddy exposes ports 80/443 and obtains TLS certificates for your hostname. The app enables Secure, HTTP-only, SameSite cookies. The app filesystem is read-only except for its bounded `/tmp` mount.

On your Linux deployment host:

```bash
cp .env.production.example .env.production
# Edit .env.production with the Terraform outputs and a unique password.
chmod 600 .env.production
export CLOUDSHIELD_DOMAIN=backup.your-domain.example
docker compose -f compose.production.yaml config --quiet
docker compose -f compose.production.yaml up --build -d
docker compose -f compose.production.yaml ps
```

Replace the example hostname with a real domain you control; it must resolve to the host. Security-group/firewall rules should allow 80/443, with administration restricted separately. Port 8080 is not published in production.

On EC2, attach an instance role containing the generated dashboard policy. Require IMDSv2. If containers need instance-role credentials, configure the instance metadata response hop limit appropriately (typically 2 for Docker bridge networking). Restrict the host to trusted workloads, since other local containers could otherwise reach the same instance credentials. The worker has its own, broader role.

For non-EC2 hosting, supply a supported AWS credential provider securely. Do not store long-lived keys in an image or Git repository. A secrets manager and federated credentials are preferable for a real multi-user service.

The liveness check `/health` proves that the Java server is answering. It does not validate S3, DynamoDB, Lambda permissions or email subscriptions.

## 6. Cloud acceptance checklist

Perform these checks and retain screenshots plus the exported JSON report for your project evaluation. Local tests cannot replace this evidence.

| Check | Required observable result |
|---|---|
| First manual backup | Job succeeds; manifest and matching file objects exist in both S3 regions |
| Replication metadata | Backup is labelled `REPLICATED`; object sizes and counts are recorded |
| Manual restore test | Job succeeds, checksums pass, RTO/RPO and region are recorded |
| Cleanup | `cleanupStatus=CLEANED`; no keys remain under that job's test prefix |
| Source change | Upload an added registration; testing the older backup reports a changed object |
| Manual restore | Restore copy contains the original registration data; current source remains intact |
| Alerts | Confirmed SNS mailbox receives the backup/test report; failures also generate an alert |
| Schedule | Inspect both Scheduler resources; verify an actual scheduled invocation and job record |
| Lambda failure handling | Inspect log group, error alarm and failed-event queue configuration |
| Password/security | Demo password rejected in AWS mode; anonymous API blocked; HTTPS and Secure session cookie present |

Use a dedicated test backup to exercise corrupted-copy recovery. Preserve its original object or version before changing it. Do not corrupt a backup your group needs. A unit test covers automatic primary-read failure and secondary fallback; a live regional outage is not simulated by the dashboard.

When a job is queued for a long time, inspect Lambda throttles and the failed-event queue. The dashboard shows only the newest 500 jobs/backups. A Lambda event that never starts can remain `QUEUED`; queue alarms are the source for investigating dispatch failures. Stale running jobs are reconciled on a subsequent worker invocation.

## 7. Direct recovery when the primary control plane is unavailable

The secondary bucket holds its own complete manifests. A recovery operator with read access to that bucket can recover data independently of primary-region DynamoDB and Lambda. Commands default to the administrator demo workspace. For a Google account, add `--tenant WORKSPACE_ID` using the 32-character account ID from its exported report:

```bash
python -m pip install boto3
python scripts/recover-secondary.py --bucket YOUR_SECONDARY_BUCKET --region ap-southeast-1
python scripts/recover-secondary.py --bucket YOUR_SECONDARY_BUCKET --region ap-southeast-1 \
  --backup EXACT_BACKUP_ID --destination recovered-campus-copy \
  --manifest-sha256 TRUSTED_DIGEST_FROM_EXPORTED_REPORT
```

The listing command prints available backup IDs. The restore command requires a new destination, validates every object, rejects unsafe names, and enforces 20 MiB/500-object bounds. If no independently trusted manifest digest is available, omit the last option; the tool clearly reports that it checked against the stored manifest only.

This produces recovered data locally. To start a replacement cloud application, upload it to a **new** source bucket in the surviving region, configure a replacement control plane and dashboard, then redirect the application after health checks. This operational failover is manual. The project does not promise automatic recovery of the complete control plane during a regional outage.

## 8. Restore-copy promotion and retention

Each manual restore is under `restores/<job-id>/application/` in the recovery bucket. Inspect it in the dashboard first. For a real cutover, copy verified objects to a newly provisioned application location and change its configuration. Avoid blindly syncing an old backup over the active source: newer registrations could be overwritten.

Normal test jobs delete their own prefix immediately. The recovery bucket's temporary-object tag expiry rule is a backup mechanism for a hard timeout; it is not an immediate cleanup guarantee. Check AWS lifecycle behavior and CloudWatch alarms after failures.

Backups and versions are deliberately retained by default. Decide a retention policy with the mentor before a long-running deployment. Cross-region transfer, storage, versions, request charges, Lambda execution, DynamoDB PITR, monitoring and dashboard hosting continue to matter even without idle test compute.

## 9. Stop or remove the deployment

To stop schedules without removing backups, set `enable_schedules=false`, review the Terraform plan and apply it. To stop the web host, use `docker compose -f compose.production.yaml down`; certificate volumes remain for future startup.

To remove cloud resources, first export required reports and copy any data that must survive. Disable schedules, review the Terraform destroy plan, and empty only the exact project buckets after verifying their names and backup retention requirements. Versioned buckets require deletion of versions and delete markers before they can be removed. Terraform does not enable `force_destroy`, so it will refuse to silently purge stored backups. Then use `terraform destroy` and inspect the account for remaining project resources.

## Troubleshooting

| Symptom | Action |
|---|---|
| `AccessDenied` reading a bucket | Confirm the role policy, bucket name, prefix and region; source versions require `s3:GetObjectVersion` |
| Backup remains primary-only | Inspect secondary-region permissions/connectivity; create a new backup after repair |
| No email | Confirm SNS subscription, check mailbox spam and the configured topic ARN |
| UI login fails after deployment | Set a unique password; confirm HTTPS and proxy configuration; clear the old session and sign in again |
| `No source objects` | Upload the sample files under exactly `application/` |
| Test fails parsing JSON | Source may contain invalid JSON; fix data and create a new backup; do not relabel a bad backup |
| Cleanup fails | Inspect recovery-prefix delete permission; keep job marked failed until objects are removed |
| Port 8080 already used | Stop the previous demo process or set `PORT=8081` and open that port |
| Build uses Java 8 | Set `JAVA_HOME` to JDK 21; the bundled runtime is for running, not compiling |
