# Deployment and operations

## Production prerequisites

Use a dedicated host/service, PostgreSQL 17, a DNS name, and a TLS reverse proxy. The software is prepared for one store in Pakistan, PKR with Asia/Karachi business dates. Do not deploy with DEBUG=true or use development/demo credentials for live data. Production refuses SQLite.

Create `.env` from `.env.example`. Generate independent secrets; the PostgreSQL application password must be generated lowercase hex to avoid ambiguous SQL/URI quoting. Values stay out of Git:

```bash
python -c "import secrets; print(secrets.token_urlsafe(64))"  # SECRET_KEY
python -c "import secrets; print(secrets.token_hex(32))"       # POSTGRES_PASSWORD
python -c "import secrets; print(secrets.token_hex(32))"       # APP_DB_PASSWORD
```

Set ALLOWED_HOSTS to your real hostname and CSRF_TRUSTED_ORIGINS to its `https://` origin. The supplied Compose app password is independent of the database superuser password. `deploy/init-db.sh` creates a non-superuser/non-createdb/non-createrole application login on an empty volume. It is not rerun for existing volumes; rotate passwords with an explicit DBA operation. Application migration ownership is still privileged within its schema; for stricter deployments, separate migration owner and runtime DML roles.

## Container release

```bash
docker compose build
# In the managed cloud, preserve proxy CA trust during image build instead:
# docker build --secret id=proxy_ca,src=/etc/ssl/certs/ca-certificates.crt -t medicare:local .
docker compose up -d db
docker compose run --rm web python manage.py migrate --noinput
docker compose run --rm web python manage.py createsuperuser
docker compose run --rm web python manage.py check --deploy --fail-level WARNING
docker compose up -d web
```

A tested source build collects static assets into the image. The web container runs as UID 10001, drops capabilities, uses a read-only filesystem and a writable temporary filesystem. It does not run migrations in each worker. Schema changes are an explicit release operation, with a pre-release backup and a tested upgrade plan.

Host binding is `127.0.0.1:8000`, not a public app port. Configure your host TLS reverse proxy to forward to this port, preserve Host, and **overwrite** `X-Forwarded-Proto` with `https`. Only then enable TRUST_PROXY=true. Never expose a trusted-header backend directly to untrusted clients. Enforce HTTPS and rate limits at the proxy, including account login and request body limits. Production Django redirects untrusted HTTP requests and sends secure cookies/HSTS. To check readiness through a local trusted proxy simulation:

```bash
curl --fail -H 'X-Forwarded-Proto: https' -H 'Host: pharmacy.example.com' http://127.0.0.1:8000/health/
```

The header simulation is a local diagnostic, not a replacement for an actual TLS endpoint. Do not send session cookies over public HTTP.

Set actual store identity in Store settings. Use the bootstrap superuser to create named operators, then minimize its use. Assign clinical privileges only after verifying the operator's professional eligibility. Verify product scheduling, saleable unit/pack definitions, reorder levels, and tax treatment. No medicine database, prescriber registry, or tax authority feed is included.

## Back up and restore

Database volumes are not backups. Use encrypted off-host backups with monitored jobs and a documented retention policy. The following creates a consistent PostgreSQL custom-format backup without a password on the command line (Compose's database container has local socket authentication):

```bash
mkdir -p backups
chmod 700 backups
docker compose exec -T db pg_dump -U postgres -d medicare -Fc > backups/medicare.dump
chmod 600 backups/medicare.dump
# Inspect the archive list before copying to your encrypted backup service.
docker compose exec -T db pg_restore --list < backups/medicare.dump
```

Rehearse restoration into a **separate** database/container, never over the live database:

```bash
docker compose exec -T db createdb -U postgres -O medicare medicare_restore_check
docker compose exec -T db pg_restore -U postgres -d medicare_restore_check --role=medicare --no-owner --exit-on-error < backups/medicare.dump
docker compose exec -T db psql -U postgres -d medicare_restore_check -c 'SELECT COUNT(*) FROM store_sale;'
```

A count alone is not full recovery verification: compare products, lot balances, ledger totals, sales, audit events, users, and Rx dispensing counters; start an isolated application against the restored DB and repeat a representative checkout on a copy. Track recovery time and data-loss window. Never run `docker compose down -v` on a live store unless intentionally destroying its volume after verified recovery planning. No automatic restore-to-live button is exposed.

## Maintenance and monitoring

- Readiness `/health/` checks connection and Product table availability; it returns no patient/business records. Monitor errors, latency, available storage, database connections, backups, replication if used, and certificate expiry.
- JSON stdout logs have request IDs, routes, status and duration. Aggregate securely off-host. Business audit and stock movements are protected by application permissions but remain editable by a database owner; use operational separation and tamper-evident external retention if required.
- Schedule `python manage.py maintenance` daily with the same runtime environment. It clears expired sessions and stale login throttle buckets, preserving all business records.
- Reorder/expiry warnings are live queries; no in-process timers must survive a restart. Stock remains excluded by expiry even if nobody opens the dashboard.
- Run PostgreSQL tests and deployment checks before each release. Keep dependencies/base images current; pinned Python dependencies are reproducible versions, not a promise that future vulnerability advisories never apply.
- Use `manage.py changepassword <username>` for secure operator-assisted recovery. Password values must not enter logs, URLs, saved scripts or source control. No email password-reset service has been invented without configured mail delivery.

## Legacy data cutover

The previous MySQL database has not been migrated or deleted. Audit/source archive and original Git history remain available. Plan a separate, rehearsed data migration with SKU/unit mapping, physical stock reconciliation, Rx allowance review, old-password resets, read-only financial history retention, and rollback. Never run the old destructive SQL scripts against a live store. Existing JavaFX onboarding helpers are obsolete for this release.

## Release boundaries

Implemented and tested: single-store catalog and batch inventory, receipts/adjustments/quarantine, purchasing, non-controlled Rx quantity workflows, role-protected POS, immutable sales snapshots, full refunds, reports, team management, settings, and audit.

Not implemented or certified:

- Controlled-drug dispensing is explicitly blocked. There is no DRAP register integration, license verification, or regulatory certification. Pakistan pharmacy, privacy, retention, invoice/tax and FBR integration requirements must be reviewed for the particular business before go-live.
- No clinical interaction/allergy decision engine, diagnosis, prescription image handling, automatic refills, or clinical recommendation service. Recorded allergy text and prescription directions require professional interpretation.
- No payment gateway, insurance submission, SMS gateway, email delivery, loyalty program, general-ledger accounting, or tax-authority e-invoicing integration. Recording a CARD/TRANSFER sale does not initiate payment; recording a refund does not move money.
- No multi-branch transfers, offline sync, partial refunds/PO receipts, fractional stock, or automatic pack conversions. Define whole saleable units correctly.
- No existing-production-data migration, external security audit, penetration test, large-store performance certification, or disaster recovery SLA. Those require real deployment infrastructure/data and validation.
- Current server-rendered selection lists are bounded (POS patient/Rx selectors 500, document forms 2,000); searchable/paginated APIs remain available beyond those limits. A store exceeding these bounds should extend those selectors before live use.

Production readiness is a release process, not a label inferred from successful unit tests. Before real operation, complete TLS/domain setup, securely enter secrets, verify backups/restore and access assignments, approve catalog classifications/tax rules, and rehearse the actual staff workflow with the responsible pharmacist and store owner.
