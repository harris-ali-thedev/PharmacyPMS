# 💊 MediCare Pharmacy Management System (PMS)

A full-featured desktop application built with **Java 17 + JavaFX 21 + MySQL**, following the **MVC pattern**.

---

## 📁 Project Structure

```
PharmacyPMS/
├── pom.xml                          Maven build file
├── sql/
│   └── schema.sql                   Database schema + sample data
└── src/main/
    ├── java/
    │   ├── module-info.java
    │   └── com/pharmacy/
    │       ├── Main.java            Application entry point
    │       ├── model/               Plain data objects (User, Medicine, Sale …)
    │       ├── dao/                 JDBC data access layer
    │       ├── service/             PDF invoice + Excel/PDF report export
    │       ├── controller/          JavaFX event/business logic
    │       ├── view/                JavaFX scene builders
    │       └── util/                DB connection, session, audit, alerts
    └── resources/
        ├── db.properties            Database credentials (edit before running)
        └── css/styles.css           Global stylesheet
```

---

## ⚙️ Prerequisites

| Tool | Version |
|------|---------|
| JDK  | 17 or higher |
| Maven | 3.8+ |
| MySQL | 8.0+ (or MariaDB 10.6+) |

---

## 🚀 Setup & Run

### Step 1 — Create the database

```sql
-- In MySQL Workbench or terminal:
mysql -u root -p < sql/schema.sql
```

This creates `pharmacy_db`, all tables, and loads sample data including:
- **Default admin:** username `admin`, password `admin123`
- 10 sample medicines with batches
- 3 sample suppliers

### Step 2 — Configure database connection

Edit `src/main/resources/db.properties`:

```properties
db.url=jdbc:mysql://localhost:3306/pharmacy_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
db.user=root
db.password=YOUR_PASSWORD_HERE
```

### Step 3 — Build & Run

```bash
# Build
mvn clean package -DskipTests

# Run with JavaFX Maven plugin
mvn javafx:run

# OR run the shaded JAR directly
java --module-path /path/to/javafx-sdk/lib \
     --add-modules javafx.controls,javafx.fxml,javafx.graphics \
     -jar target/PharmacyPMS-1.0.0.jar
```

> **Tip:** If using IntelliJ IDEA, import as a Maven project. The IDE will resolve JavaFX automatically.

---

## 👥 User Roles

| Role | Access |
|------|--------|
| **ADMIN** | Full access — users, settings, all reports, override limits |
| **PHARMACIST** | Inventory, batch management, prescription verification |
| **CASHIER** | POS / billing only |

---

## 🧩 Modules

### 🛒 Point of Sale (POS)
- Search medicines by name, category, or barcode
- Dynamic cart with +/− quantity controls
- Percentage or fixed-amount discount (validated against 30% admin limit)
- GST/Tax calculation
- Payment modes: Cash, Card, Digital Wallet
- Change calculator
- Scheduled drug gate — requires verified prescription
- **PDF invoice** generation on checkout (select save directory)

### 📦 Inventory Management
- Full CRUD for medicines
- Category, barcode, batch, expiry, reorder level tracking
- Low-stock filter view
- Stock auto-deducted atomically on each sale

### 📊 Sales Reports
- Daily / Weekly / Weekly-breakdown / Monthly stats
- Recent sales table (last 100 transactions)
- **Export to Excel (.xlsx)** via Apache POI
- **Export to PDF** via iText

### 💊 Prescriptions
- Create & store patient/doctor records
- Attach scanned image path
- Pharmacist verification workflow
- POS blocks scheduled drugs unless a verified prescription is linked

### 🚚 Suppliers
- Vendor directory with lead times
- Deactivate (soft-delete) suppliers
- Foreign key linked to medicines

### 👥 User Management (Admin only)
- Create/edit users
- Assign roles (ADMIN / PHARMACIST / CASHIER)
- Password reset using BCrypt hashing

### 🔒 Security & Audit
- BCrypt-hashed passwords (cost factor 12)
- Prepared statements everywhere — SQL injection prevented
- Silent audit log for logins, deletions, sales, password resets

---

## 🗄️ Database Tables

| Table | Purpose |
|-------|---------|
| `users` | Auth + RBAC |
| `medicines` | Product catalogue |
| `batches` | FIFO batch/expiry tracking |
| `suppliers` | Vendor CRM |
| `sales` | Sale header (totals, payment) |
| `sale_items` | Line items per sale |
| `prescriptions` | Rx records with verification |
| `purchase_orders` | (schema ready for v2) |
| `audit_log` | Immutable action trail |

---

## 🔮 Roadmap (v2.0)
- [ ] Customer loyalty points
- [ ] Barcode scanner hardware integration (HID input)
- [ ] AI demand prediction (ML module)
- [ ] Automated daily backup scheduler
- [ ] Mobile companion app

---

## 📦 Key Dependencies

| Library | Purpose |
|---------|---------|
| JavaFX 21 | UI framework |
| MySQL Connector/J 8.3 | JDBC driver |
| BCrypt (Favre) | Password hashing |
| iText 5.5 | PDF invoice/report generation |
| Apache POI 5.2 | Excel (.xlsx) export |
| JFreeChart 1.5 | Chart support |
| SLF4J | Logging facade |

---

## 🐛 Troubleshooting

**"Cannot connect to database"**
→ Verify MySQL is running, credentials in `db.properties` are correct, and `pharmacy_db` exists.

**"Module not found" errors**
→ Ensure JDK 17+ is selected in project settings. Add `--add-opens` flags if needed for older runtimes.

**Blank invoice PDF**
→ iText requires write permission to the chosen directory. Run as administrator if necessary.
