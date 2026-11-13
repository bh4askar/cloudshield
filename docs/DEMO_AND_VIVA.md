# Demonstration and viva guide

## Eight-minute demonstration

1. **Problem (45 seconds).** A backup file is not enough if it is corrupt, stored in one region, or never restored. Introduce CloudShield's store-copy-test-report cycle.
2. **Architecture (60 seconds).** Show the Java dashboard, shared engine, two S3 regions, Lambda, EventBridge, DynamoDB and SNS. State whether the current session is local or AWS.
3. **Protect (60 seconds).** Show four sample registrations. Create a backup. Open backup history and point out timestamp, object count, size and both-region status.
4. **Change (45 seconds).** Add a registration locally, or upload an updated JSON object to S3 in AWS mode. Explain that the live data is now newer than the backup.
5. **Test (90 seconds).** Run a restore test. Open details. Explain RTO, RPO age, changed-object bytes, verified objects and cleanup. Show the test prefix is empty.
6. **Recover (60 seconds).** Restore the old backup. Open the recovery copy in Campus Register; it contains the earlier registrations. Switch back to the live source and show the newer data is still available.
7. **Audit (45 seconds).** Show activity, alert delivery, export a report and demonstrate the dark-mode toggle.
8. **Evidence and limits (75 seconds).** Show test results and, after real deployment, the S3 regional buckets, Scheduler resources, SNS email and CloudWatch logs. Explain bounded workload and manual control-plane disaster recovery.

Do not present a local folder as an actual AWS region, a `LOCAL_LOG` entry as a sent email, or local recovery times as AWS performance results.

## Suggested group ownership

These are suggested responsibilities, not a claim about work already performed by any member.

| Member | Suggested responsibility and viva preparation |
|---|---|
| Bhaskar Kumar | Java dashboard/API, authentication, application integration and release packaging |
| Arpit Singh | AWS bucket layout, cross-region copy verification, scoped permissions |
| Jai Kundu | Backup engine, manifests, integrity validation and recovery metrics |
| Yash Pratap Singh Bhadauriya | Lambda, scheduling, notifications, cleanup and monitoring |
| Abhishek Upadhyay | Test execution, cloud acceptance evidence, report and demonstration coordination |

Each member should be able to trace one backup and one restore through the entire system. Change these assignments to reflect the group's actual contributions.

## Viva questions and concise answers

**Why not simply copy files to S3?** Storage does not prove recoverability. This project restores the data, validates it, measures recovery and records cleanup.

**Why two regions?** A second-region copy reduces dependence on one regional storage location. This design still has a primary-region control plane, so complete regional failover needs the documented recovery procedure.

**Is this native S3 replication?** No. The Java worker copies a captured snapshot into both buckets and reads it back to verify checksums. This gives explicit copy outcomes and keeps the same algorithm testable locally.

**Why Java?** It matches the synopsis and supplies a shared, typed core for the web application and Lambda handler. Spring Boot supplies the HTTP application and Spring Security supplies session/CSRF protection.

**Why DynamoDB?** Lambda invocations are ephemeral. DynamoDB preserves job history, backup metadata and a conditional lease without a continuously running database server.

**What is the RTO here?** The observed time to load, write and verify restored data. It does not include provisioning a VM, network or production database.

**What is RPO?** In this implementation, it is the age of the selected backup at recovery start. A daily schedule suggests up to about a day between successful backups, but failures can make the age larger. The project does not claim a guaranteed SLA.

**Do changed bytes mean exact data loss?** No. They are the full current sizes of objects added or changed since the backup. Exact business-record loss would require application-specific comparison.

**How does integrity verification work?** Each file has a SHA-256 digest; the manifest has a digest in metadata. The stored and restored bytes must match. A digest detects corruption but is not independently authenticated against an attacker who controls both data and metadata.

**What if copying to the second region fails?** The operation fails and records a primary-only backup. Users can still restore the verified primary copy and create another backup after fixing replication.

**What if the primary copy is corrupt?** The engine retries the full verified snapshot from the secondary bucket. If both fail, the restore is reported as failed.

**What if cleanup fails?** The test fails even if recovery itself worked. A hard process termination may delay cleanup; stale-job recovery and a one-day S3 expiry rule are safeguards.

**Why is the recovery bucket not versioned?** Deleting a versioned object usually creates a delete marker while retaining bytes. An unversioned temporary store allows actual deletion of test contents.

**How are credentials protected?** The application uses environment configuration and the AWS SDK credential chain. IAM roles or SSO are preferred. The production app rejects the demo password and is served behind HTTPS.

**What happens if the user clicks twice?** Requests create distinct jobs; the dashboard disables a clicked button during dispatch. A single executor/worker lease serializes operations. Redelivery of the same completed job does not repeat it.

**Are the snapshots transaction-consistent?** Source objects use captured S3 version IDs, but listing multiple objects is not a database transaction. Quiesce application writes for application-level consistency.

**How are costs controlled?** Test compute is serverless, resources are bounded, tests remove their objects, logs have retention and there is no standby application stack. Backups, versions, transfers, requests and the dashboard host still cost money.

**What would you add next?** Immutable retention/Object Lock, customer-managed KMS keys, a durable job queue, pagination, MFA and user roles, signed manifests, record-level RPO, native database adapters and a secondary control plane.

## Final submission checklist

- Customize the report to the college's required template and mentor feedback.
- Review the source and replace suggested member responsibilities with actual contributions.
- Run the cloud acceptance checklist using your group's AWS account.
- Capture genuine screenshots, alert emails and exported test records.
- Cite actual documentation used; distinguish tests you ran from intended future work.
- Remove credentials, state files, local data and private logs from the submitted source archive.
- Keep a separate backup of the source and final report.
