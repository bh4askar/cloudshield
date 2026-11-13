# Verification evidence

Verified on **12 November 2026**, Windows x64. This is local execution and configuration evidence; no AWS resources were deployed.

| Check | Result |
|---|---|
| Java build and tests | PASS: 37 tests, no failures/errors/skips |
| Core backup engine | 22 tests |
| Tenant storage/recovery isolation | 3 tests |
| Concurrent metadata polling and updates | 1 regression test |
| Google identity/account chooser | 4 tests |
| Session/API/CSRF/security integration | 6 tests |
| Tenant API access | 1 test |
| Local HTTP workflow | PASS: 16 checks in `validation/local-smoke.json` |
| Real Google sign-in | PASS: account chooser, consent, callback, private workspace and first backup |
| Google-account recovery test | PASS: restored/verified objects and cleaned temporary data |
| Sign-out | PASS: returned to login |
| Google audience | External / In production |
| Public information pages | Published on Google Sites |
| Terraform | Configuration validation passed; official AWS provider 5.100.0 |
| AWS deployment / public HTTPS app | Not performed |
| Second real Google account | Not tested; automated tests exercise tenant isolation |

## Reproduce locally

On a normal JDK 21 and Maven 3.9+ installation:

```bash
mvn clean verify
java -jar web/target/cloudshield-web-1.0.0.jar
python scripts/smoke-test.py --output docs/validation/local-smoke.json
```

The smoke script targets a running LOCAL server and creates synthetic source changes, backups and restores in the administrator demonstration workspace. Do not run repeatedly on data you need to keep unchanged. Google browser testing uses a separate account workspace.

On the Windows test environment, javac ZIP canonicalization required the optional Eclipse compiler profile:

```text
mvn -Peclipse-compiler -Dmaven.compiler.release= -Dmaven.compiler.source=21 -Dmaven.compiler.target=21 -Djunit.jupiter.tempdir.cleanup.mode.default=NEVER verify
```

JUnit's automatic temporary-directory cleanup was disabled because of that environment's filesystem restrictions. Engine cleanup assertions and HTTP cleanup checks still ran and passed. Maven 3.9.9 and Microsoft OpenJDK 21.0.13.0.2 were used. The dependency/uber-JAR build emitted duplicate-resource warnings; the build and runtime checks completed successfully.

Terraform validation used the official AWS 5.100.0 provider ZIP with its SHA-256 checked against the vendor checksum list. The execution environment needed a temporary `dev_overrides` path, so Terraform printed an override warning. A normal deployment should use `terraform init` and the released provider configuration, then inspect its actual plan before applying.

## Bugs fixed and exercised

- Missing profile elements previously interrupted dashboard rendering. The HTML IDs and guarded updates now match.
- Windows JSON replacement could race with API polling. Reads/writes now share locks, including cached tenant repositories. The concurrent regression and HTTP workflow pass.
- Google callback lost its saved authorization request with SameSite=Strict cookies. SameSite=Lax preserves the top-level callback session. A real Google authorization-code exchange succeeded, and the smoke suite checks Lax/HTTP-only cookie attributes.
- Unexpected background exceptions now result in visible failed jobs and diagnostic logging instead of silently swallowed futures.

## Google and public pages

Homepage: https://sites.google.com/view/cloudshield-g360/home

Privacy: https://sites.google.com/view/cloudshield-g360/privacy-policy

Both local callback hostnames (`localhost` and `127.0.0.1`, port 8080) are registered. No Gmail/mail scopes, Google billing or free-trial activation were used. The public information site is separate from the still-local Java application. Credentials remain in the private `.env.local` and are excluded from shared distributions.

Browser screenshots are retained locally and excluded from Git because they contain account information. The JSON results in `validation/` record the automated checks. The synthetic HTTP run's measured durations are examples for this small local workload, not performance guarantees.

Before final cloud submission, complete the live AWS and HTTPS acceptance checklist in `AWS_DEPLOYMENT.md`, test a second independent Google account and record the deployment-specific results.
