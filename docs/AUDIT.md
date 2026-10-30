# Original project audit and rebuild decisions

Baseline: `f0557ca59b0aad388a70dc69882a59902ac0b6ce` on `main`. The original tree was clean before work. Inspection covered 84 Java sources (approximately 17,800 lines), one CSS resource (1,064 lines), both SQL files, README, Maven dependencies, and navigation/module references. The machine-readable [inventory](legacy-inventory.json) and [per-source decisions](LEGACY_FILES.md) describe the inspected files. Reference/table extraction is static and includes comments; it is not a runtime coverage claim.

## Architecture found

JavaFX desktop MVC with three controllers, JDBC DAOs, mutable JavaFX-property models, singleton process session/database managers, JavaFX views, service helpers, and background schedulers. Maven compiles Java 17 source with JavaFX 21. The application opens a direct MySQL connection from a classpath `db.properties` file, absent from the tracked repository. There is no HTTP backend or automated test suite.

Business logic is split between controllers, DAOs, services, and views. `AddStockView`, `PatientHistoryView`, `AuditLogView`, and `DashboardView` run SQL directly. Per-process role/session checks cannot form a server security boundary. A desktop process needs database credentials with broad privileges. `DatabaseConnection.getConnection()` opens a new connection every call despite its singleton description; shared mutable connection fields are unreliable transaction boundaries.

## Verified defects / gaps

| Priority | Evidence in original source | Consequence and replacement |
|---|---|---|
| Critical | `dao/UserDAO.java:62–79` comments out BCrypt verification and success/failure bookkeeping, then returns the matching user | Passwords are not checked by this method. Replace authentication entirely with Django password hashing/session authentication; wrong-password and throttle tests added. |
| High | `SaleDAO.saveSale` accepts UI totals and prices; `BatchDAO.getOldestAvailableBatch` opens a separate connection and only selects a single sufficient lot | No authoritative server recalculation, no spanning lots, and no consistent locked stock read. Replace with decimal calculations, FEFO allocations, product/lot locks, unique idempotency keys and rollback tests. |
| High | `BatchDAO.deductFromBatch` subtracts without checking available quantity or quarantine; `SaleDAO` still reduces product stock when no lot is found | Batch and product totals may diverge; stock can become negative. Remove redundant product stock and derive it from saleable lots. |
| High | `PurchaseOrderDAO.receivePO` uses `batchDAO.insert`, which opens its own connection while PO/product changes use the caller's transaction | A rollback may leave lot rows behind. New receipt changes order, lots, movements and audit in one transaction. Original status transition does reject repeat receipt; this safeguard is retained and tested. |
| High | `schema.sql` and `schema_pro.sql` both start with `DROP DATABASE`; the latter includes a second large commented schema | Destructive setup and schema drift. Replace both with incremental Django migrations and generated PostgreSQL DDL documentation. |
| Medium | `SaleDAO.getMonthlyStats` sums sale headers after joining sale lines | A multi-line sale can inflate header aggregates. Reports aggregate headers and line costs independently; regression test added. |
| Medium | `SaleDAO.getTopMedicines` and `InventoryService.getSlowMovers` constrain the joined sale, but aggregate joined sale items independently | Out-of-period line quantities may enter results. Those report implementations are removed; rebuilt reports use bounded, explicit sale/refund timestamp filters. |
| Medium | `InsuranceClaimView` only validates text and displays a success dialog | Claims are not persisted/submitted. Remove the misleading workflow rather than carry a fake integration forward. |
| Medium | `SettingsView` explicitly states persistence is not implemented | Replace with persisted, audited store settings. |
| Medium | `BackupService` hardcodes a Windows `G:\\Mysql\\...\\mysqldump.exe` path and passes a password on the command line | Not portable/reliable for cloud deployment. Remove desktop backup schedulers; use PostgreSQL operations with verified restore instructions. |
| Medium | `AuditLogger` claims a bounded queue but uses `Executors.newSingleThreadExecutor`; it catches database failures independently | Audit can be lost or describe rolled-back actions. New business audit records commit with the business transaction. |
| Medium | `PrescriptionService`, `PrescriptionDAO`, POS handlers, and schema variants carry different dispensing/refill responsibilities | Inconsistent enforcement risk. Replace with one create/verify/cancel/dispense service, matching patient/product and cumulative allowances under locks. |
| Scope | SMS gateway is explicitly stubbed; clinical interaction/allergy tables appear only in the expanded schema; `Main` does not initialize `PharmacyScheduler` | Remove unproven notification/clinical automation. No clinical decision-support claims in the replacement. |

## Keep / merge / remove

| Original area | Decision | Result |
|---|---|---|
| Medicine catalog, suppliers, barcodes, reorder levels | Keep business intent; generalize | `Product.kind` distinguishes medicine/general without a second inventory engine. |
| Product stock, batches, stock receipt, expiry, stocktake, quarantine | Merge | `Batch` is the balance source, `Movement` is the ledger; one inventory service. |
| `POSView` / `POSController`, commented `BillingView`, `InvoiceView`, `InvoicePreview`, `InvoiceService`, invoice history | Merge | One checkout service, sales history, immutable receipt snapshots, browser print/PDF. |
| `SupplierView` / `SupplierManagementView` | Merge | Normalized source similarity is 99.92%; one supplier CRUD surface. |
| Supplier purchasing and suggested reorder screens | Keep purchasing; simplify suggestions | Manual multi-line POs plus catalog low-stock filters; no unsupported sales-forecast automation. |
| Prescription/patient screens, prescription and safety service fragments | Keep core records and authorization; merge | Patient-linked, dated, verified prescriptions with quantity allowances. Clinical interaction/allergy automation is excluded. |
| Daily/weekly/monthly/profit reports, Excel/PDF helpers | Merge | One date-bounded report service, CSV export, receipt print/PDF. No duplicated aggregations. |
| User/session/audit helpers | Replace | Framework authentication and CSRF, explicit roles, database throttle windows, transaction-coupled audit. |
| BackupService, BackupScheduler, PharmacyScheduler, restore UI | Remove desktop machinery | Operator-managed PostgreSQL backups/restore, documented maintenance command. |
| Insurance, SMS, loyalty, unsupported regulatory claims | Remove | No visible placeholder buttons or simulated submissions. |
| JavaFX style manager, dialogs, widgets, module descriptors, POM, SQL bootstrap scripts | Remove | Responsive server-rendered UI and versioned schema migrations. |

Static candidates with no references outside their own source: `InputValidator`, `PharmacyScheduler`, `InvoiceView`, `SupplierView`, `SearchBar`, `AlertPanel`, `CustomTable`. `BillingView` has a commented navigation reference; it is not an active navigation destination. These are removal candidates based on source inspection, not a claim that a profiler proved them unreachable. All desktop code is removed as part of the authorized full rewrite, including useful business code replaced by tested services.

## Preservation and migration

Original source remains in Git history. A separate Git archive was created at `/workspace/pharmacy-legacy-f0557ca.tar.gz`, and representative archive files were byte-compared to `git show HEAD:<path>` before removal. The archive is outside the replacement application and contains no generated local database configuration. Existing MySQL data/container was left untouched.

No production-data migration is claimed. For a real cutover: export the legacy database read-only; map SKU/unit/barcode identities; resolve duplicate products and batch/product balance disagreements by physical count; explicitly classify Rx/controlled items; import opening lots with ledger movements; review outstanding prescription quantities professionally; reset all staff passwords; retain legacy financial history separately; reconcile opening inventory and financial totals; rehearse rollback before switching store operations. This needs real data and sign-off, not an automatic destructive import of either SQL script.
