# Legacy file inventory

Static inspection covers every original Java source, both SQL schemas, CSS, README and Maven manifest. Absence of external type references is a candidate for removal, not proof of runtime unreachability.

| File | Lines | Decision |
|---|---:|---|
| `src/main/java/com/pharmacy/Main.java` | 39 | Replace with framework infrastructure |
| `src/main/java/com/pharmacy/controller/InventoryController.java` | 178 | Merge into transactional application services |
| `src/main/java/com/pharmacy/controller/LoginController.java` | 91 | Merge into transactional application services |
| `src/main/java/com/pharmacy/controller/POSController.java` | 508 | Merge into transactional application services |
| `src/main/java/com/pharmacy/dao/BatchDAO.java` | 101 | Merge into transactional application services |
| `src/main/java/com/pharmacy/dao/MedicineDAO.java` | 144 | Merge into transactional application services |
| `src/main/java/com/pharmacy/dao/NarcoticRegisterDAO.java` | 157 | Merge into transactional application services |
| `src/main/java/com/pharmacy/dao/PatientDAO.java` | 301 | Merge into transactional application services |
| `src/main/java/com/pharmacy/dao/PrescriptionDAO.java` | 82 | Merge into transactional application services |
| `src/main/java/com/pharmacy/dao/PurchaseOrderDAO.java` | 229 | Merge into transactional application services |
| `src/main/java/com/pharmacy/dao/SaleDAO.java` | 261 | Merge into transactional application services |
| `src/main/java/com/pharmacy/dao/SupplierDAO.java` | 160 | Merge into transactional application services |
| `src/main/java/com/pharmacy/dao/UserDAO.java` | 325 | Merge into transactional application services |
| `src/main/java/com/pharmacy/model/Batch.java` | 42 | Replace with web domain models |
| `src/main/java/com/pharmacy/model/CartItem.java` | 40 | Replace with web domain models |
| `src/main/java/com/pharmacy/model/DrugInteractionAlert.java` | 103 | Replace with web domain models |
| `src/main/java/com/pharmacy/model/Medicine.java` | 152 | Replace with web domain models |
| `src/main/java/com/pharmacy/model/NarcoticRegisterEntry.java` | 124 | Replace with web domain models |
| `src/main/java/com/pharmacy/model/Patient.java` | 192 | Replace with web domain models |
| `src/main/java/com/pharmacy/model/Prescription.java` | 233 | Replace with web domain models |
| `src/main/java/com/pharmacy/model/Sale.java` | 129 | Replace with web domain models |
| `src/main/java/com/pharmacy/model/SaleItem.java` | 43 | Replace with web domain models |
| `src/main/java/com/pharmacy/model/ShiftSession.java` | 92 | Replace with web domain models |
| `src/main/java/com/pharmacy/model/Supplier.java` | 43 | Replace with web domain models |
| `src/main/java/com/pharmacy/model/User.java` | 158 | Replace with web domain models |
| `src/main/java/com/pharmacy/model/enums/BloodGroup.java` | 3 | Replace with web domain models |
| `src/main/java/com/pharmacy/model/enums/Gender.java` | 3 | Replace with web domain models |
| `src/main/java/com/pharmacy/scheduler/BackupScheduler.java` | 113 | Replace with framework infrastructure |
| `src/main/java/com/pharmacy/scheduler/PharmacyScheduler.java` | 183 | Replace with framework infrastructure |
| `src/main/java/com/pharmacy/service/BackupService.java` | 91 | Merge into transactional application services |
| `src/main/java/com/pharmacy/service/DrugSafetyService.java` | 338 | Merge into transactional application services |
| `src/main/java/com/pharmacy/service/InventoryService.java` | 350 | Merge into transactional application services |
| `src/main/java/com/pharmacy/service/InvoicePreview.java` | 282 | Merge into transactional application services |
| `src/main/java/com/pharmacy/service/InvoiceService.java` | 169 | Merge into transactional application services |
| `src/main/java/com/pharmacy/service/LabelPrintingService.java` | 189 | Merge into transactional application services |
| `src/main/java/com/pharmacy/service/NotificationService.java` | 319 | Merge into transactional application services |
| `src/main/java/com/pharmacy/service/PrescriptionService.java` | 237 | Merge into transactional application services |
| `src/main/java/com/pharmacy/service/ReportExportService.java` | 246 | Merge into transactional application services |
| `src/main/java/com/pharmacy/service/ReportingService.java` | 319 | Merge into transactional application services |
| `src/main/java/com/pharmacy/service/ShiftSessionService.java` | 199 | Merge into transactional application services |
| `src/main/java/com/pharmacy/util/AlertUtil.java` | 34 | Replace with framework infrastructure |
| `src/main/java/com/pharmacy/util/AppConstants.java` | 90 | Replace with framework infrastructure |
| `src/main/java/com/pharmacy/util/AuditLogger.java` | 163 | Replace with framework infrastructure |
| `src/main/java/com/pharmacy/util/DatabaseConnection.java` | 66 | Replace with framework infrastructure |
| `src/main/java/com/pharmacy/util/InputValidator.java` | 77 | Replace with framework infrastructure |
| `src/main/java/com/pharmacy/util/SessionManager.java` | 168 | Replace with framework infrastructure |
| `src/main/java/com/pharmacy/view/AppUIManager.java` | 297 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/DashboardView.java` | 664 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/LoginView.java` | 340 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/MainView.java` | 310 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/POSView.java` | 397 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/ViewManager.java` | 111 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/admin/AuditLogView.java` | 393 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/admin/BackupRestoreView.java` | 541 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/admin/UserManagementView.java` | 314 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/billing/BillingView.java` | 334 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/billing/InsuranceClaimView.java` | 97 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/billing/InvoiceView.java` | 64 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/components/AlertPanel.java` | 127 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/components/CustomTable.java` | 216 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/components/SearchBar.java` | 65 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/dialogs/BarcodeScannerDialog.java` | 114 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/dialogs/ConfirmationDialog.java` | 117 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/dialogs/MessageDialog.java` | 120 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/inventory/AddStockView.java` | 505 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/inventory/ExpiryAlertView.java` | 445 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/inventory/InventoryView.java` | 242 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/inventory/StockReorderView.java` | 388 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/patient/PatientFormDialog.java` | 252 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/patient/PatientHistoryView.java` | 571 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/patient/PatientManagementView.java` | 266 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/prescription/DrugInteractionView.java` | 162 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/prescription/PrescriptionFormView.java` | 141 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/prescription/PrescriptionView.java` | 200 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/reports/ExpiryReportView.java` | 123 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/reports/InvoiceHistoryView.java` | 311 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/reports/ProfitMarginView.java` | 115 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/reports/ReportsDashboardView.java` | 107 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/reports/SalesReportView.java` | 240 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/settings/SettingsView.java` | 294 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/supplier/PurchaseOrderView.java` | 783 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/supplier/SupplierManagementView.java` | 201 | Replace desktop presentation |
| `src/main/java/com/pharmacy/view/supplier/SupplierView.java` | 201 | Replace desktop presentation |
| `src/main/java/module-info.java` | 38 | Replace with framework infrastructure |
