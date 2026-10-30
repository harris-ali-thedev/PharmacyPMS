# Verification record

Executed in the managed cloud workspace on 30 October 2026. These results describe the implementation and disposable validation databases, not a deployed customer store or independently restored cloud snapshot.

| Check | Observed result |
|---|---|
| PostgreSQL domain/API/security tests | **48 passed**, no skips, using PostgreSQL 17.11. Includes two real simultaneous-transaction tests: competing cashiers cannot oversell, and concurrent retries of one checkout create one sale. |
| SQLite development test run | 46 passed, 2 PostgreSQL-only concurrency tests explicitly skipped (48 discovered). SQLite is not accepted in production. |
| Wrong-password / persistent login throttle / CSRF / roles | Passed in the automated suite. Cashiers cannot access patients, prescriptions, users, audit or purchase APIs; own-sale restriction and self-deactivation protection tested. |
| Inventory / money / Rx / refunds | Passed: multiple-lot FEFO, expired/quarantined exclusion, rollback, idempotency conflict/replay, payment coverage, discounts, 100 penny-line rounding, nonnegative DB constraint, Rx validity/patient/quantity, controlled-product block, once-only refund/quarantined returns, immutable snapshots. |
| Purchase receipt atomicity | Passed: repeat receipt rejected; a bad second medicine lot rolls the first receipt back. |
| Browser workflow | System Chromium + Playwright: sign-in → create general product → receive lot → checkout (PKR 118.00, change PKR 82.00) → create supplier/PO → receive PO → create fictional Rx medicine/patient/prescription → verify → dispense → full refund. No browser JavaScript errors. |
| Responsive UI | Desktop 1440px and mobile 390px screenshots; mobile catalog had no document-level horizontal overflow. Tables have intentional internal horizontal scrolling. |
| Production image | Docker build passed with proxy CA mount and TLS verification. Image uses non-root Gunicorn, packaged static assets and the patched REST framework. |
| Fresh production Compose setup | Empty test volume initialized, restricted application DB role created, all migrations applied, web started. Initial bind-mounted initializer permission failure was corrected and the fresh installation repeated successfully. |
| Database role isolation | Production app role observed with `rolsuper=false`, `rolcreatedb=false`, `rolcreaterole=false`. |
| Production checks | `manage.py check --deploy --fail-level WARNING`: no issues. `makemigrations --check --dry-run`: no changes detected. |
| Readiness/security headers | Packaged app `/health/` returned 200 through a local trusted-proxy simulation; CSP, DENY framing, nosniff, HSTS and request ID headers observed. This is not a public TLS/domain test. |
| Backup/restore | Consistent PostgreSQL custom dump restored into a separate database, with no restore errors. Restored snapshot had 15 products, 3 sales, 20 movements. Reconciled lot balances against movement sums, sale-line quantities against allocations, then successfully checked out on the restored copy. |
| Dependency advisory audit | `pip-audit --vulnerability-service pypi` on the final pinned production requirements: **no known vulnerabilities found**. Initial DRF 3.16.1 advisories caused an upgrade to **3.18.3**; tests were rerun after upgrading. Machine-readable [audit](dependency-audit.json). This is point-in-time package-advisory evidence, not a penetration test. |
| Static checks | Ruff lint and formatting, JavaScript syntax checks, Django checks, and migration drift checks passed. |

Browser artifacts are generated under ignored `test-results/` (`dashboard.png`, `pos.png`, `receipt.png`, `mobile.png`). Reproduce the browser check with the command in README against a disposable development database; it intentionally creates test inventory and transactions.

No real payment gateway, production customer data import, controlled-drug workflow, FBR integration, clinical decision support, public-domain HTTPS deployment, external security audit, load certification, or fresh cloud-task restoration was tested or claimed. See [release boundaries](OPERATIONS.md#release-boundaries).
