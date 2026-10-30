# MediCare — Pharmacy & Store

A rebuilt, single-store web application for **Pakistan, PKR, and Asia/Karachi**. Medicines and general merchandise share a catalog, purchasing, batch inventory, POS, and stock ledger. Prescription-only medicines add patient-linked verification and quantity controls.

Built with **Python 3.12, Django 5.2 LTS, Django REST Framework, PostgreSQL 17, semantic HTML, CSS, and JavaScript**. No frontend build server, JavaFX, or browser CDN dependency. Gunicorn and WhiteNoise serve the production application.

## What works

- Named staff accounts, password verification, persistent login throttling, CSRF protection, session expiry, password changes, and four enforced roles.
- Searchable catalog: SKU, unique optional barcode, medicine/general classification, category, unit, price, tax, reorder level, archive status.
- Medicine expiry tracking; general goods can have no expiry. Batch receipts, counts, quarantine, expiry watch, and append-only application stock ledger.
- Supplier directory, multi-line purchase orders, full atomic receipt, and cancellation of open orders.
- POS product/barcode search, multi-product basket, server-authoritative prices/tax, manager discounts, cash/card/transfer recording, change calculation, and idempotent checkout.
- First-expiring-first-out allocation across multiple lots, PostgreSQL row locks, nonnegative stock constraints, and transactional audit events.
- Patient records, multi-item prescriptions, pharmacist/admin verification, validity checks, matching patients, and cumulative dispensing limits.
- Immutable receipt price/name/cost snapshots, browser printing/PDF, full refunds into separate quarantine lots, and unchanged prescription allowances after refunds.
- Net sales, refund-date accounting, gross profit based on sold batch costs, tax summaries, CSV export, audit history, and persisted store settings.

This is a deployable, tested implementation, **not a certification of regulatory compliance or an already deployed production service**. Read [release boundaries](docs/OPERATIONS.md#release-boundaries) before real dispensing. Controlled drugs are explicitly blocked. Card and wallet payments are recorded after settlement on an external terminal; there is no payment gateway integration.

## Run locally

```bash
python -m venv .venv
.venv/bin/pip install -r requirements-dev.txt
export DEBUG=true
.venv/bin/python manage.py migrate
.venv/bin/python manage.py createsuperuser
.venv/bin/python manage.py runserver
```

Open the displayed development-server address and sign in. A superuser has administrative access. For normal operators, use **Team & roles** to create named accounts. New stores start empty; no known default password is shipped.

SQLite is supported only for local exploration. To develop/test concurrent workflows, export `DATABASE_URL=postgresql://.../medicare` before migration and startup. Production refuses SQLite and requires a unique `SECRET_KEY`.

Optional **fictional development inventory**, without overwriting existing data:

```bash
# Supply a unique password securely in your environment; never commit it.
export DEMO_ADMIN_PASSWORD='your-unique-strong-development-password'
DEBUG=true .venv/bin/python manage.py seed_demo
```

This creates the `owner` account only if absent, three fictional suppliers, and twelve medicine/general SKUs. Existing passwords are never reset. Demo classification and zero tax defaults are not clinical or tax guidance.

## Verify

```bash
make test       # SQLite: database concurrency tests are explicitly skipped
make lint
make check
# With PostgreSQL DATABASE_URL set: all tests, including real concurrent checkout
DEBUG=true .venv/bin/python manage.py test --settings=config.test_settings
```

Browser smoke test (writes uniquely named test inventory and a sale; use a disposable development database):

```bash
# Uses installed system Chromium. Install Chromium if your OS does not have it.
export E2E_USERNAME=owner E2E_PASSWORD='your-development-password'
.venv/bin/python tests/browser_smoke.py
```

Screenshots go to ignored `test-results/`. [Verification evidence](docs/VERIFICATION.md) records the checks actually run. CI runs the suite against PostgreSQL.

## Production

Follow [operations and deployment](docs/OPERATIONS.md), including HTTPS, unique secrets, database backups, restore validation, role assignments, and local pharmacy/tax review. `Dockerfile` and `compose.yaml` are included; migrations are an explicit release step. Compose exposes the application on host loopback only for a trusted TLS reverse proxy.

## Repository map

```text
accounts/             Custom user roles, authentication, throttling, auth audit
config/               Settings, URL routing, WSGI, request logging/security headers
store/
  models.py            Catalog, lots, prescriptions, purchases, sales, ledgers
  serializers.py       API input/output contracts and validation
  api.py               Versioned REST endpoints and role enforcement
  forms.py, web.py     Staff forms and server-rendered application
  services/            Transactional inventory, sales, purchasing, Rx, reports
  migrations/          Version-controlled database schema
  management/commands/ Demo data and safe maintenance
static/                Responsive CSS and local browser JavaScript
templates/            Staff UI, login, forms, receipts, error pages
 tests/                Business, API, authorization, concurrency, browser checks
 deploy/               PostgreSQL application-role initialization
 docs/                 Audit, architecture, API, schema, deployment, verification
```

- [Original project audit and merge/removal decisions](docs/AUDIT.md)
- [Architecture, schema, invariants, and permissions](docs/ARCHITECTURE.md)
- [API contract and examples](docs/API.md)
- [Original file-by-file inventory](docs/LEGACY_FILES.md)

The previous desktop app is removed from the active tree. Its original files remain in Git history at `f0557ca59b0aad388a70dc69882a59902ac0b6ce`. No existing MySQL database was converted or deleted. Legacy onboarding scripts outside the repository are superseded by the new web workflow.
