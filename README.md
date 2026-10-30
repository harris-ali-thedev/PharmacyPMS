# MediCare Store (Pharmacy + Retail) Management System

Production-oriented desktop PMS built with Java 17, JavaFX 21, MySQL, and JDBC.

## Core Modules

- Secure login with BCrypt password verification, account lockout, audit logging, and forced password change support
- Role-based workspace for main admin, admin, pharmacist, technician, and cashier
- Mixed medicine and general-retail POS billing with category/product tax and discounts, invoice generation,
  prescription checks for medicines, and atomic stock deduction
- Unified product catalog for medicines, beverages, ice cream, cosmetics, and general goods; medicine batches
  and optional perishable retail lots remain expiry-aware
- Inventory search and filters by category, brand, stock, and expiry; low-stock and category-based sales/value reports
- Prescription, patient, insurance, narcotic register, purchase order, reports, audit log, backup, and restore modules
- Export-ready reporting with PDF and Excel dependencies

## Prerequisites

- JDK 17+
- Maven 3.8+
- MySQL 8.0+ or MariaDB 10.6+
- `mysql` and `mysqldump` on `PATH` for backup and restore features

## Database Setup

`sql/schema.sql` is the single base schema (the old duplicate `schema.sql` was removed). Apply in this order
on a fresh database:

```bash
mysql -u root -p < sql/schema.sql
mysql -u root -p pharmacy_db < sql/migrations/001_unified_retail_inventory.sql
mysql -u root -p pharmacy_db < sql/migrations/002_stock_oneness.sql
mysql -u root -p pharmacy_db -e "SELECT * FROM v_stock_reconciliation WHERE status <> 'OK';"
```

For an existing database, back it up and run only the migrations that are missing (each runs once).

### Stock rules (one source of truth)
- `products.stock_qty` is the single on-hand figure (excludes quarantined units).
- `medicines.stock_qty` is a read-only mirror maintained by a trigger.
- For expiry-tracked products the non-quarantined batch totals must equal the stock figure.
- All stock changes go through `StockService` and are recorded in `stock_movements`.
- Admins can verify this on the **Stock Integrity** screen.

### First login
The seed admin account has no usable password hash. On first start the app sets the bootstrap password
`Admin@123` (see `AppConstants.BOOTSTRAP_ADMIN_PASSWORD`) and forces a password change at first login.
Change it immediately.

## Configuration

Copy `src/main/resources/db.properties.example` to `db.properties` and edit it.

For production, prefer environment variables so secrets are not stored in source:

```powershell
$env:PHARMACY_DB_URL="jdbc:mysql://localhost:3306/pharmacy_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
$env:PHARMACY_DB_USER="root"
$env:PHARMACY_DB_PASSWORD="your-password"
```

Optional backup and restore overrides:

```powershell
$env:PHARMACY_DB_HOST="localhost"
$env:PHARMACY_DB_PORT="3306"
$env:PHARMACY_MYSQLDUMP_PATH="mysqldump"
```

## Build And Run

```bash
mvn clean package
mvn javafx:run
```

To run the shaded jar:

```bash
java --module-path /path/to/javafx-sdk/lib --add-modules javafx.controls,javafx.fxml,javafx.graphics -jar target/PharmacyPMS-1.0.0.jar
```

## Notes For Production

- Do not commit real database credentials.
- Create separate database users for app runtime and backup/restore operations.
- Run backups to a protected directory and test restore regularly.
- Replace sample pharmacy identity values in `AppConstants`.
- Review local pharmacy regulatory requirements before live deployment.
