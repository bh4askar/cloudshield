# Validation — Azure migration

Verified on 13 November 2026. The evidence below distinguishes local tests, direct Azure adapter tests and the full deployed-worker path.

| Check | Result |
|---|---|
| Java unit/integration suite without live cloud tests | 41 passed; 4 opt-in Azure tests skipped |
| Direct real-Azure adapter suite | 4 passed |
| Dashboard → Table/Queue → deployed Function → Blob Storage HTTP workflow | 16 checks passed |
| Function package | Four triggers and required Azure dependencies verified |
| Azure infrastructure | ARM validation and deployment succeeded |
| Source/primary and secondary regions | Central India and Korea Central |
| Public application hosting | Deferred by the project owner |

The four real-Azure contract tests check backup/restore and fallback after corrupting a disposable primary object, immutable captured source-version reads, tenant isolation and newest-first metadata, and concurrent conditional locking with rejection of wrong-owner release. They use random synthetic workspaces, not the owner's existing application data.

The HTTP suite checks anonymous access, login/session cookies, CSRF protection, queue dispatch, verified backup copies, restore-test cleanup, source mutation, retained restore and preservation of the live source. It uses the administrator's synthetic sample workspace. See `validation/azure-http.json` for observations and durations. Small-sample timings are not throughput benchmarks.

Reproduce local checks with `mvn clean verify`. Run direct Azure tests after setting the environment from the setup guide:

```powershell
$env:CLOUDSHIELD_AZURE_TESTS='true'
mvn -pl core -Dtest=AzureIntegrationTest test
```

With a running dashboard and a strong administrator password in the environment:

```bash
python scripts/smoke-test.py --mode azure --url http://127.0.0.1:8080 --output docs/validation/azure-http.json
```

In the restricted Windows authoring environment the build used the existing Eclipse compiler profile with `-Dmaven.compiler.release= -Dmaven.compiler.source=21 -Dmaven.compiler.target=21 -Djunit.jupiter.tempdir.cleanup.mode.default=NEVER`. A normal JDK checkout uses the standard build command. Private token caches, OAuth credentials, logs containing account details and local settings are excluded from the archives and repository.

Google sign-in was tested again on 13 November 2026 against the Azure-backed dashboard. The owner's account created a private Azure workspace, completed a two-region backup and passed a recovery test with cleanup. A cloud-provider migration does not itself prove an OAuth callback on a new public hostname. Full regional outage, automatic control-plane failover, a second live Google identity, and guaranteed year-long credit coverage are outside these tests. Azure Monitor alert configuration does not prove mailbox delivery; verify the recipient's inbox separately.

## Final timer and independent recovery evidence

Both Azure timer functions fired at temporary acceptance times on 13 November 2026. Their resulting backup and recovery jobs succeeded with correct occurrence-based IDs. The recovery test cleaned its temporary objects. See `validation/azure-schedules.json`. The final settings are enabled: daily backup at 02:00 IST, and weekly recovery test on Sunday at 02:30 IST.

The independent Python recovery command downloaded and verified three objects (787 bytes) using only the Korea Central account and a trusted manifest digest from an exported report. It wrote a new local recovery directory and did not contact the primary metadata service. This confirms the data-recovery command, not automatic application failover.
