from django.contrib import admin
from django.contrib.auth.views import (
    LogoutView,
    PasswordChangeView,
    PasswordChangeDoneView,
)
from django.urls import path, include
from rest_framework.routers import DefaultRouter
from accounts.views import sign_in
from store import api, web

router = DefaultRouter()
for prefix, view, basename in [
    ("products", api.ProductViewSet, "product"),
    ("suppliers", api.SupplierViewSet, "supplier"),
    ("patients", api.PatientViewSet, "patient"),
    ("batches", api.BatchViewSet, "batch"),
    ("sales", api.SaleViewSet, "sale"),
    ("purchases", api.PurchaseViewSet, "purchase"),
    ("prescriptions", api.PrescriptionViewSet, "prescription"),
    ("users", api.UserViewSet, "user"),
    ("movements", api.MovementViewSet, "movement"),
    ("audit", api.AuditViewSet, "audit"),
]:
    router.register(prefix, view, basename=basename)

urlpatterns = [
    path("health/", api.health),
    path("admin/", admin.site.urls),
    path("accounts/login/", sign_in, name="login"),
    path("accounts/logout/", LogoutView.as_view(), name="logout"),
    path(
        "accounts/password/",
        PasswordChangeView.as_view(
            template_name="registration/password.html",
            success_url="/accounts/password/done/",
        ),
        name="password_change",
    ),
    path(
        "accounts/password/done/",
        PasswordChangeDoneView.as_view(template_name="registration/password_done.html"),
        name="password_change_done",
    ),
    path("api/v1/me/", api.me),
    path("api/v1/dashboard/", api.dashboard),
    path("api/v1/reports/", api.reports),
    path("api/v1/settings/", api.settings_view),
    path("api/v1/", include(router.urls)),
    path("", web.dashboard, name="dashboard"),
    path("products/", web.products, name="products"),
    path("products/new/", web.product_edit, name="product-new"),
    path("products/<int:pk>/edit/", web.product_edit, name="product-edit"),
    path("batches/", web.batches, name="batches"),
    path("batches/receive/", web.receive, name="receive"),
    path("batches/<int:pk>/adjust/", web.adjust, name="adjust"),
    path("movements/", web.movements, name="movements"),
    path("pos/", web.pos, name="pos"),
    path("sales/", web.sales, name="sales"),
    path("sales/<uuid:pk>/", web.receipt, name="receipt"),
    path("purchases/", web.purchases, name="purchases"),
    path("purchases/new/", web.purchase_new, name="purchase-new"),
    path("purchases/<int:pk>/", web.purchase_detail, name="purchase-detail"),
    path("prescriptions/", web.prescriptions, name="prescriptions"),
    path("prescriptions/new/", web.prescription_new, name="prescription-new"),
    path(
        "prescriptions/<int:pk>/action/",
        web.prescription_action,
        name="prescription-action",
    ),
    path("reports/", web.reports, name="reports"),
    path("audit/", web.audit_log, name="audit"),
    path("settings/", web.settings_page, name="settings"),
]
for entity in ["suppliers", "patients", "team"]:
    urlpatterns += [
        path(f"{entity}/", web.entity_list, {"entity": entity}, name="entity-list"),
        path(f"{entity}/new/", web.entity_edit, {"entity": entity}, name="entity-new"),
        path(
            f"{entity}/<int:pk>/edit/",
            web.entity_edit,
            {"entity": entity},
            name="entity-edit",
        ),
    ]
