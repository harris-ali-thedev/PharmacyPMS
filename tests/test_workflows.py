import uuid
from datetime import timedelta
from decimal import Decimal
from django.contrib.auth import get_user_model
from django.db import IntegrityError, transaction
from django.test import TestCase, Client
from django.utils import timezone
from rest_framework.test import APIClient
from store.models import (
    Product,
    Batch,
    Movement,
    Sale,
    AuditEvent,
    Supplier,
    Patient,
    PrescriptionItem,
)
from store.services.inventory import receive_batch, adjust_batch, product_stock
from store.services.sales import checkout, refund_sale
from store.services.purchasing import create_order, receive_order
from store.services.prescriptions import create_prescription, verify_prescription
from store.services.reports import report
from store.services.common import Conflict
from rest_framework.exceptions import ValidationError, PermissionDenied


class Workflows(TestCase):
    @classmethod
    def setUpTestData(cls):
        User = get_user_model()
        cls.admin = User.objects.create_user(
            "owner", password="GoodPassword!2026", role="ADMIN"
        )
        cls.cashier = User.objects.create_user(
            "cashier", password="GoodPassword!2026", role="CASHIER"
        )
        cls.pharmacist = User.objects.create_user(
            "pharmacist", password="GoodPassword!2026", role="PHARMACIST"
        )
        cls.manager = User.objects.create_user(
            "manager", password="GoodPassword!2026", role="MANAGER"
        )
        cls.product = Product.objects.create(
            sku="SOAP",
            name="Hand wash",
            kind="GENERAL",
            price="100.00",
            tax_rate="18.00",
        )
        cls.medicine = Product.objects.create(
            sku="RX",
            name="Prescription medicine",
            kind="MEDICINE",
            price="25.00",
            prescription_required=True,
        )
        cls.patient = Patient.objects.create(name="Fictional Patient")
        cls.supplier = Supplier.objects.create(name="Test Distributor")

    def setUp(self):
        self.api = APIClient()
        self.api.force_authenticate(self.admin)

    def stock(self, product=None, quantity=10, lot="LOT", days=100, cost="50.00"):
        product = product or self.product
        return receive_batch(
            self.admin,
            product.pk,
            lot,
            quantity,
            Decimal(cost),
            timezone.localdate() + timedelta(days=days)
            if product.kind == "MEDICINE"
            else None,
        )

    def basket(self, product=None, quantity=2, **kwargs):
        return {
            "idempotency_key": uuid.uuid4(),
            "items": [
                {"product_id": (product or self.product).pk, "quantity": quantity}
            ],
            "discount": Decimal("0"),
            "amount_paid": Decimal("1000.00"),
            "payment_method": "CASH",
            **kwargs,
        }

    def rx(self, qty=5):
        return create_prescription(
            self.pharmacist,
            {
                "patient_id": self.patient.pk,
                "reference": "RX-1",
                "prescriber": "Doctor",
                "prescriber_registration": "REG-1",
                "issued_on": timezone.localdate(),
                "valid_until": timezone.localdate() + timedelta(days=30),
                "items": [
                    {
                        "product_id": self.medicine.pk,
                        "quantity": qty,
                        "directions": "Professional directions",
                    }
                ],
            },
        )

    def test_general_stock_needs_no_expiry(self):
        self.stock()
        self.assertEqual(product_stock().get(pk=self.product.pk).stock, 10)
        self.assertEqual(Movement.objects.get().delta, 10)

    def test_medicine_requires_future_expiry(self):
        with self.assertRaises(ValidationError):
            receive_batch(self.admin, self.medicine.pk, "LOT", 4, Decimal("5"))
        with self.assertRaises(ValidationError):
            self.stock(self.medicine, days=0)

    def test_cashier_cannot_receive(self):
        with self.assertRaises(PermissionDenied):
            receive_batch(self.cashier, self.product.pk, "LOT", 1, Decimal("1"))

    def test_checkout_calculates_tax_and_change(self):
        lot = self.stock()
        sale, created = checkout(self.cashier, self.basket())
        self.assertTrue(created)
        self.assertEqual(sale.total, Decimal("236.00"))
        self.assertEqual(sale.change, Decimal("764.00"))
        lot.refresh_from_db()
        self.assertEqual(lot.quantity, 8)
        self.assertEqual(sale.lines.get().cost, Decimal("100"))

    def test_checkout_spans_batches_in_expiry_order(self):
        first = self.stock(self.medicine, quantity=2, lot="FIRST", days=20, cost="2")
        second = self.stock(self.medicine, quantity=4, lot="SECOND", days=50, cost="3")
        rx = self.rx()
        verify_prescription(self.pharmacist, rx.pk)
        sale, _ = checkout(
            self.pharmacist,
            self.basket(
                self.medicine, 5, patient_id=self.patient.pk, prescription_id=rx.pk
            ),
        )
        self.assertEqual(
            list(
                sale.lines.get()
                .allocations.order_by("pk")
                .values_list("batch_id", "quantity")
            ),
            [(first.pk, 2), (second.pk, 3)],
        )
        self.assertEqual(sale.lines.get().cost, Decimal("13.00"))

    def test_idempotent_retry_changes_stock_once(self):
        self.stock()
        data = self.basket()
        sale, _ = checkout(self.cashier, data)
        retry, created = checkout(self.cashier, data)
        self.assertEqual(sale.pk, retry.pk)
        self.assertFalse(created)
        self.assertEqual(Batch.objects.get().quantity, 8)

    def test_changed_idempotent_payload_conflicts(self):
        self.stock()
        data = self.basket()
        checkout(self.cashier, data)
        data["amount_paid"] = Decimal("2000")
        with self.assertRaises(Conflict):
            checkout(self.cashier, data)

    def test_insufficient_stock_rolls_back_everything(self):
        lot = self.stock(quantity=1)
        with self.assertRaises(Conflict):
            checkout(self.cashier, self.basket())
        lot.refresh_from_db()
        self.assertEqual(lot.quantity, 1)
        self.assertFalse(Sale.objects.exists())
        self.assertEqual(Movement.objects.count(), 1)

    def test_quarantined_stock_excluded(self):
        lot = self.stock()
        adjust_batch(self.admin, lot.pk, 10, "Quarantine", True)
        with self.assertRaises(Conflict):
            checkout(self.cashier, self.basket())

    def test_expired_stock_excluded(self):
        lot = self.stock(self.medicine)
        Batch.objects.filter(pk=lot.pk).update(expiry_date=timezone.localdate())
        rx = self.rx()
        verify_prescription(self.pharmacist, rx.pk)
        with self.assertRaises(Conflict):
            checkout(
                self.pharmacist,
                self.basket(
                    self.medicine, patient_id=self.patient.pk, prescription_id=rx.pk
                ),
            )

    def test_insufficient_payment_rejected(self):
        self.stock()
        with self.assertRaises(ValidationError):
            checkout(self.cashier, self.basket(amount_paid=Decimal("1")))
        self.assertFalse(Sale.objects.exists())

    def test_card_amount_must_match(self):
        self.stock()
        with self.assertRaises(ValidationError):
            checkout(self.cashier, self.basket(payment_method="CARD"))

    def test_discount_permission_and_calculation(self):
        self.stock()
        with self.assertRaises(ValidationError):
            checkout(self.cashier, self.basket(discount=Decimal("10")))
        sale, _ = checkout(self.manager, self.basket(discount=Decimal("10")))
        self.assertEqual(sale.total, Decimal("224.20"))

    def test_duplicate_products_rejected(self):
        self.stock()
        data = self.basket()
        data["items"] *= 2
        with self.assertRaises(ValidationError):
            checkout(self.cashier, data)

    def test_rx_verification_requires_pharmacist(self):
        rx = self.rx()
        with self.assertRaises(PermissionDenied):
            verify_prescription(self.manager, rx.pk)

    def test_unverified_rx_is_blocked(self):
        self.stock(self.medicine)
        rx = self.rx()
        with self.assertRaises(Conflict):
            checkout(
                self.pharmacist,
                self.basket(
                    self.medicine, patient_id=self.patient.pk, prescription_id=rx.pk
                ),
            )

    def test_rx_patient_must_match(self):
        self.stock(self.medicine)
        rx = self.rx()
        verify_prescription(self.pharmacist, rx.pk)
        with self.assertRaises(ValidationError):
            checkout(self.pharmacist, self.basket(self.medicine, prescription_id=rx.pk))

    def test_cashier_cannot_dispense_rx(self):
        self.stock(self.medicine)
        rx = self.rx()
        verify_prescription(self.pharmacist, rx.pk)
        with self.assertRaises(PermissionDenied):
            checkout(
                self.cashier,
                self.basket(
                    self.medicine, patient_id=self.patient.pk, prescription_id=rx.pk
                ),
            )

    def test_rx_allowance_cannot_be_reused(self):
        self.stock(self.medicine)
        rx = self.rx(qty=2)
        verify_prescription(self.pharmacist, rx.pk)
        checkout(
            self.pharmacist,
            self.basket(
                self.medicine, patient_id=self.patient.pk, prescription_id=rx.pk
            ),
        )
        with self.assertRaises(Conflict):
            checkout(
                self.pharmacist,
                self.basket(
                    self.medicine, patient_id=self.patient.pk, prescription_id=rx.pk
                ),
            )
        self.assertEqual(PrescriptionItem.objects.get().dispensed, 2)

    def test_refund_is_once_and_quarantined(self):
        self.stock()
        sale, _ = checkout(self.cashier, self.basket())
        refund_sale(self.manager, sale.pk, "Customer return")
        self.assertEqual(product_stock().get(pk=self.product.pk).stock, 8)
        self.assertEqual(Batch.objects.get(quarantined=True).quantity, 2)
        with self.assertRaises(Conflict):
            refund_sale(self.manager, sale.pk, "Again")

    def test_cashier_cannot_refund(self):
        self.stock()
        sale, _ = checkout(self.cashier, self.basket())
        with self.assertRaises(PermissionDenied):
            refund_sale(self.cashier, sale.pk, "Return")

    def test_purchase_receipt_is_atomic_and_once(self):
        order = create_order(
            self.manager,
            {
                "supplier_id": self.supplier.pk,
                "items": [
                    {
                        "product_id": self.product.pk,
                        "quantity": 8,
                        "unit_cost": Decimal("35"),
                    }
                ],
            },
        )
        data = {
            "items": [
                {"product_id": self.product.pk, "number": "PO-LOT", "expiry_date": None}
            ]
        }
        receive_order(self.manager, order.pk, data)
        with self.assertRaises(Conflict):
            receive_order(self.manager, order.pk, data)
        self.assertEqual(Batch.objects.get().quantity, 8)

    def test_report_does_not_duplicate_sale_totals(self):
        self.stock()
        self.stock(self.medicine)
        rx = self.rx()
        verify_prescription(self.pharmacist, rx.pk)
        data = self.basket(patient_id=self.patient.pk, prescription_id=rx.pk)
        data["items"].append({"product_id": self.medicine.pk, "quantity": 2})
        sale, _ = checkout(self.pharmacist, data)
        stats = report()
        self.assertEqual(stats["gross_sales"], sale.total)
        self.assertEqual(stats["transactions"], 1)
        refund_sale(self.admin, sale.pk, "Returned")
        self.assertEqual(report()["net_sales"], 0)
        self.assertEqual(report()["gross_profit"], 0)

    def test_database_prevents_negative_stock(self):
        lot = self.stock()
        with self.assertRaises(IntegrityError), transaction.atomic():
            Batch.objects.filter(pk=lot.pk).update(quantity=-1)

    def test_audit_written_with_business_transaction(self):
        self.stock()
        checkout(self.cashier, self.basket())
        self.assertTrue(
            AuditEvent.objects.filter(
                action="sale.completed", actor=self.cashier
            ).exists()
        )

    def test_api_rejects_anonymous_and_cashier_admin_access(self):
        self.api.force_authenticate(None)
        self.assertEqual(self.api.get("/api/v1/products/").status_code, 403)
        self.api.force_authenticate(self.cashier)
        for path in ["patients", "users", "audit", "purchases", "prescriptions"]:
            self.assertEqual(self.api.get(f"/api/v1/{path}/").status_code, 403)

    def test_api_validation_and_prices_not_client_controlled(self):
        self.stock()
        self.assertEqual(
            self.api.post("/api/v1/sales/", {"items": []}, format="json").status_code,
            400,
        )
        payload = self.basket()
        payload["idempotency_key"] = str(payload["idempotency_key"])
        payload["unit_price"] = "0.01"
        response = self.api.post("/api/v1/sales/", payload, format="json")
        self.assertEqual(response.status_code, 201, response.data)
        self.assertEqual(response.data["total"], "236.00")

    def test_general_product_cannot_require_rx(self):
        data = {
            "sku": "BAD",
            "name": "Soap",
            "kind": "GENERAL",
            "price": "10",
            "prescription_required": True,
        }
        self.assertEqual(
            self.api.post("/api/v1/products/", data, format="json").status_code, 400
        )

    def test_product_classification_immutable_after_stock(self):
        self.stock()
        response = self.api.patch(
            f"/api/v1/products/{self.product.pk}/", {"kind": "MEDICINE"}, format="json"
        )
        self.assertEqual(response.status_code, 400)

    def test_user_password_never_serialized(self):
        response = self.api.get("/api/v1/users/")
        self.assertEqual(response.status_code, 200)
        self.assertNotIn("password", response.data["results"][0])

    def test_cannot_deactivate_self(self):
        response = self.api.patch(
            f"/api/v1/users/{self.admin.pk}/", {"is_active": False}, format="json"
        )
        self.assertEqual(response.status_code, 400)

    def test_cashier_only_sees_own_sales(self):
        self.stock()
        checkout(self.admin, self.basket())
        self.api.force_authenticate(self.cashier)
        self.assertEqual(self.api.get("/api/v1/sales/").data["count"], 0)

    def test_bad_login_does_not_authenticate(self):
        response = self.client.post(
            "/accounts/login/", {"username": "owner", "password": "wrong"}
        )
        self.assertEqual(response.status_code, 400)
        self.assertNotIn("_auth_user_id", self.client.session)

    def test_login_throttles_persistent_failures(self):
        for _ in range(10):
            self.client.post(
                "/accounts/login/", {"username": "owner", "password": "wrong"}
            )
        response = self.client.post(
            "/accounts/login/", {"username": "owner", "password": "GoodPassword!2026"}
        )
        self.assertEqual(response.status_code, 429)

    def test_csrf_enforced_for_session_writes(self):
        client = APIClient(enforce_csrf_checks=True)
        client.force_login(self.admin)
        self.assertEqual(
            client.post("/api/v1/products/", {}, format="json").status_code, 403
        )
        web = Client(enforce_csrf_checks=True)
        self.assertEqual(
            web.post(
                "/accounts/login/",
                {"username": "owner", "password": "GoodPassword!2026"},
            ).status_code,
            403,
        )

    def test_all_staff_pages_render(self):
        self.client.force_login(self.admin)
        for path in [
            "/",
            "/products/",
            "/products/new/",
            "/batches/",
            "/batches/receive/",
            "/pos/",
            "/sales/",
            "/purchases/",
            "/purchases/new/",
            "/prescriptions/",
            "/prescriptions/new/",
            "/patients/",
            "/suppliers/",
            "/team/",
            "/reports/",
            "/audit/",
            "/settings/",
            "/movements/",
            "/accounts/password/",
        ]:
            with self.subTest(path=path):
                self.assertEqual(self.client.get(path).status_code, 200)

    def test_report_date_validation(self):
        self.assertEqual(
            self.api.get("/api/v1/reports/?start=not-a-date").status_code, 400
        )

    def test_api_unknown_record_has_error_envelope(self):
        response = self.api.get("/api/v1/products/99999/")
        self.assertEqual(response.status_code, 404)
        self.assertIn("error", response.data)

    def test_controlled_drug_dispensing_is_blocked(self):
        Product.objects.filter(pk=self.product.pk).update(controlled_drug=True)
        self.stock()
        with self.assertRaises(Conflict):
            checkout(self.admin, self.basket())

    def test_discount_rounding_never_makes_negative_lines(self):
        items = []
        for index in range(100):
            product = Product.objects.create(
                sku=f"PENNY-{index}",
                name=f"Penny {index}",
                kind="GENERAL",
                price="0.01",
            )
            self.stock(product, quantity=1, lot=str(index), cost="0")
            items.append({"product_id": product.pk, "quantity": 1})
        data = self.basket(discount=Decimal("0.49"))
        data["items"] = items
        sale, _ = checkout(self.admin, data)
        self.assertEqual(sale.total, Decimal("0.51"))
        self.assertEqual(
            sum(line.discount for line in sale.lines.all()), Decimal("0.49")
        )
        self.assertFalse(sale.lines.filter(total__lt=0).exists())

    def test_expired_prescription_blocked(self):
        self.stock(self.medicine)
        rx = self.rx()
        verify_prescription(self.pharmacist, rx.pk)
        from store.models import Prescription

        Prescription.objects.filter(pk=rx.pk).update(
            issued_on=timezone.localdate() - timedelta(days=30),
            valid_until=timezone.localdate() - timedelta(days=1),
        )
        with self.assertRaises(Conflict):
            checkout(
                self.pharmacist,
                self.basket(
                    self.medicine, patient_id=self.patient.pk, prescription_id=rx.pk
                ),
            )

    def test_invalid_second_po_lot_rolls_back_first_receipt(self):
        order = create_order(
            self.admin,
            {
                "supplier_id": self.supplier.pk,
                "items": [
                    {
                        "product_id": self.product.pk,
                        "quantity": 3,
                        "unit_cost": Decimal("5"),
                    },
                    {
                        "product_id": self.medicine.pk,
                        "quantity": 2,
                        "unit_cost": Decimal("2"),
                    },
                ],
            },
        )
        with self.assertRaises(ValidationError):
            receive_order(
                self.admin,
                order.pk,
                {
                    "items": [
                        {
                            "product_id": self.product.pk,
                            "number": "GENERAL",
                            "expiry_date": None,
                        },
                        {
                            "product_id": self.medicine.pk,
                            "number": "MISSING-EXPIRY",
                            "expiry_date": None,
                        },
                    ]
                },
            )
        self.assertFalse(Batch.objects.exists())
        self.assertFalse(Movement.objects.exists())
        order.refresh_from_db()
        self.assertEqual(order.status, "OPEN")

    def test_refund_does_not_restore_rx_allowance(self):
        self.stock(self.medicine)
        rx = self.rx(qty=2)
        verify_prescription(self.pharmacist, rx.pk)
        sale, _ = checkout(
            self.pharmacist,
            self.basket(
                self.medicine, patient_id=self.patient.pk, prescription_id=rx.pk
            ),
        )
        refund_sale(self.admin, sale.pk, "Returned")
        self.assertEqual(PrescriptionItem.objects.get().dispensed, 2)

    def test_archived_product_is_not_saleable(self):
        self.stock()
        Product.objects.filter(pk=self.product.pk).update(active=False)
        with self.assertRaises(ValidationError):
            checkout(self.admin, self.basket())

    def test_product_update_with_stock_keeps_other_fields_editable(self):
        self.stock()
        result = self.api.patch(
            f"/api/v1/products/{self.product.pk}/", {"price": "120.00"}, format="json"
        )
        self.assertEqual(result.status_code, 200, result.data)
        self.product.refresh_from_db()
        self.assertEqual(self.product.price, Decimal("120.00"))

    def test_refund_does_not_modify_original_receipt_prices(self):
        self.stock()
        sale, _ = checkout(self.admin, self.basket())
        Product.objects.filter(pk=self.product.pk).update(price="200", name="Renamed")
        refund_sale(self.admin, sale.pk, "Returned")
        self.assertEqual(sale.lines.get().name, "Hand wash")
        self.assertEqual(sale.lines.get().unit_price, Decimal("100"))
