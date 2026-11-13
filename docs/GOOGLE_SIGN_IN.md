# Public Google sign-up and private accounts

CloudShield supports **Sign in with Google** for any verified Google account, including Gmail and Google Workspace accounts. The first successful sign-in creates an account and a separate sample-application workspace, then queues its initial backup. There is no email allowlist.

On 13 November 2026, a Google OAuth web client was configured with an **External / In production** audience. A real sign-in created a private workspace and its first backup. A recovery test in that account succeeded and cleaned up its temporary objects. No billing/free-trial activation was needed.

Homepage: https://sites.google.com/view/cloudshield-g360/home

Privacy policy: https://sites.google.com/view/cloudshield-g360/privacy-policy

The public pages are hosted free on Google Sites. They do not host the Java application. Public access to the dashboard still requires a deployment host and its exact HTTPS OAuth callback. A second real Google account has not been tested; automated tests cover cross-account isolation.

Local credentials are stored in the gitignored `.env.local` and loaded by the Windows launcher. They are deliberately excluded from source and Windows distribution ZIPs. Keep that file private; configure credentials separately after extracting a shareable ZIP. Google sign-in is unavailable when credentials are absent.

## Configure Google

1. Create or select a project in the Google Cloud console and open its Google Auth Platform / OAuth configuration.
2. Set the application name to CloudShield and configure the support email and developer contact. For public users, choose an **External** audience.
3. Add the deployed home page and privacy page (`https://your-domain/privacy.html`) and verify your domain where Google requires it. Review and customize the included privacy notice and publish the operator's real support contact.
4. Request only `openid`, `profile`, and `email`. These identify the user; they do not read Gmail or Google Drive.
5. Create an OAuth client of type **Web application**.
6. Register the exact redirect URI for each environment you will use:

```text
http://localhost:8080/login/oauth2/code/google
http://127.0.0.1:8080/login/oauth2/code/google
https://YOUR_REAL_DOMAIN/login/oauth2/code/google
```

Only register loopback forms accepted by the Google console; `http://localhost:8080/...` is the standard development choice. Use the same hostname when opening the application; changing between `localhost` and `127.0.0.1` also changes browser cookies and redirect matching. Production redirect URLs must use HTTPS and match character-for-character.

7. Set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` on the Java dashboard. Never put the secret in frontend JavaScript or commit it to Git. In production, use the protected `.env.production` file or your deployment secret store.
8. Restart the dashboard. The Google sign-in button becomes available.
9. Google's **Testing** publishing status only admits configured test users. To let visitors create accounts, move the application to **In production / Published**, complete any required domain/brand checks and comply with Google's OAuth policies. This console step cannot be performed by source code alone.

For local testing in PowerShell:

```powershell
$env:GOOGLE_CLIENT_ID = 'your-client-id.apps.googleusercontent.com'
$env:GOOGLE_CLIENT_SECRET = 'your-client-secret'
.\Start-CloudShield.cmd
```

The launcher runs a local workspace with real Google identity. For cloud-backed workspaces, use Azure mode as described in `AZURE_SETUP.md` and start the JAR directly. OAuth credentials belong on the web dashboard, not the Functions worker. Keep `.env.local` outside version control, screenshots, shared archives and public hosting.

The authorization request uses `prompt=select_account`. Session cookies use **SameSite=Lax** so the top-level Google callback retains the state/nonce session; Strict cookies break this flow. CSRF protection still guards mutations. Tokens, claims and credentials are not included in authentication failure logs.

## Identity and workspace isolation

Spring Security performs the OAuth authorization-code and OpenID Connect flow. It manages state/nonce, validates Google's signed token and establishes a server-side session. The application additionally requires Google's expected issuer and a verified email claim.

The account ID is derived from the verified issuer and stable Google subject ID. It is **not** supplied by a browser parameter and does not change when the email address changes. The service stores a name, email, account ID and creation date. An account's data uses `tenants/<account-id>/...` object keys and its own Table Storage `TENANT#<account-id>` partition. Local simulation uses the equivalent private folder and metadata namespace.

Every authenticated API resolves the workspace from the signed-in principal. Backup, job, restore and report lookups remain in that namespace, including when a user guesses another account's object/job ID. The trusted web and Functions managed identities can access tenant namespaces because they serve all accounts; infrastructure operators therefore remain privileged. This is application-enforced tenancy, not a separate Azure account or managed identity for every visitor.

The project administrator's password account has a separate `demo` workspace. It is not a dashboard for browsing every user's data.

## Public service limits

Defaults protect a small project deployment: 10 registered accounts in the student deployment, 30 manual operations per account per hour, 240 retained backup snapshots in the student deployment per workspace, 500 objects and 20 MiB per snapshot. Operators should review capacity, retention, alerting and costs before increasing these limits. New-user capacity is configurable through `MAX_ACCOUNTS`; there is no restriction to specific email addresses. The single-host account-capacity check is a small-deployment limit, not a distributed billing quota.

Scheduled jobs fan out to account workspaces through durable Azure Storage Queues. A large deployment should add distributed per-tenant quotas, pagination, measured capacity and appropriate concurrency instead of treating this academic configuration as unlimited SaaS capacity.

The operator receives Azure Monitor failure notifications and optional webhook messages; users view their own results in the app. Signing in with an email address does not automatically subscribe that user to email alerts.

## Verify before publishing

- Sign in with two different real Google accounts and confirm each gets its own workspace.
- Add a registration in account A. Account B's data must remain unchanged.
- Create a backup and restore in A. Its IDs must not be readable from B.
- Sign out and sign in again; the same account should recover its own history.
- Confirm the callback uses the real HTTPS domain and that session cookies are Secure/HTTP-only.
- Confirm the Google consent screen requests no Gmail/mail scopes.
- Verify a new Google account not in a test-user list can sign up after the OAuth application is published.

Automated tests cover identity checks and tenant API isolation using test identities. They do not replace the real Google consent/token exchange and live deployment acceptance checks.
