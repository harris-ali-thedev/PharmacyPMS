# Implementation and verification

Implemented:

- Dashboard top-left Open Shift and Close Shift controls using the existing shift service, background loading, active-shift recovery and validated cash input.
- Prescription medicine entry with quantities, dosage, frequency, duration and instructions; transactional header/line inserts and item loading in all DAO read paths.
- Interaction checks use the prescription medicines and distinguish incomplete checks from an empty alert result.
- Per-medicine partial dispensing, remaining-quantity enforcement and transactional prescription/loyalty reversal on void.
- Retail expiry alerts and quarantine using StockService.
- Restore typed confirmation, complete-dump checks and validated pre-restore backups. Backup filenames cannot collide within a minute.
- Backup/restore derive the server, port and database from the JDBC URL, avoiding a backup of the wrong server when using a custom port or remote host.
- Generated temporary password resets with forced change on login.
- HikariCP connection pooling and cached database configuration.
- Decimal checkout calculations and consistent line discount allocation, including product tax rates.
- Exact-barcode priority on scan then Enter; ambiguous barcodes block the sale.
- POS keyboard shortcuts: F2 search, F3 patient, F4 type the selected item's quantity, F9 checkout.
- Narcotic register date filtering and CSV export under Reports.
- Stock Take for non-batch products; supplier returns and disposal with lot selection and witness capture for controlled stock.
- Persistent store name, tax mode (no tax or exclusive), session timeout and working password-change settings; store name appears on invoices.
- Saved insurance claims use the completed invoice's stored total and decimal co-pay calculation. Voiding the sale cancels its recorded claim.
- CheckoutService owns the sale/stock/prescription transaction. Shift closing and checkout serialize on the shift row.
- Additional report indexes in migration 003; preserve inventory-related audit records during purge.

## Tests

Apply migrations **003_reporting_indexes.sql** and **004_settings_and_claims.sql** after the existing 001 and 002 migrations, against a database copy first. These files do not replace schema.sql.

Run `mvn clean package`. CheckoutTotalsTest covers decimal prices, allocation remainders, tax rounding, discounts and invalid inputs. WorkflowValidationTest covers prescription and cash validation.

StockServiceMySqlTest is opt-in and rolls back its fixtures after each test. It never uses the application database pool for audit writes. Supply a migrated **test database whose name ends in `_test`** through:

```
PHARMACY_TEST_DB_URL=jdbc:mysql://localhost:3306/pharmacy_test
PHARMACY_TEST_DB_USER=...
PHARMACY_TEST_DB_PASSWORD=...
```

The test database needs schema.sql and migrations 001–004 applied, with the schema's database name changed to your test database before importing. **schema.sql drops its named database. Never import it against live data.** Tests cover receipt/ledger consistency, sale/void restoration, quarantine idempotence, returns into quarantined lots and failed-sale rollback. Without those environment variables these integration tests are explicitly skipped.

## Remaining recommendations

The complete roadmap is not finished. Remaining work includes full BigDecimal model/report migration; further background-query and style migrations; domain enums; automated migrations; cross-catalogue barcode constraints; removing the medicine stock mirror; last-sale reprints; held carts/refunds; 80mm printing; sidebar badges; and optional dark mode/Urdu support.

Current decimal totals convert to the existing double-based models at their boundaries. Register CSV is an export of the application's records, not a certification of a jurisdiction's inspection format. JavaFX interactions still require manual verification.

Verified locally with `mvn clean package`, Java 25 and an isolated MySQL 8.0.46 server: schema plus migrations 001–004 imported successfully; report EXPLAIN statements ran; all **22 tests passed**, including **nine MySQL tests**. Prescription coverage includes line persistence, cumulative partial fills, completion count and void reversal. Stock removal coverage includes supplier returns and disposal of quarantined units. Other database coverage verifies settings persistence, claim co-pay/cancellation and loyalty reversal. See `mysql-verification.log` and `mysql-schema-verification.log` for results. The isolated test server was shut down after verification.
