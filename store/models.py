import uuid
from decimal import Decimal
from django.conf import settings
from django.core.validators import MinValueValidator, MaxValueValidator, RegexValidator
from django.db import models
from django.db.models import Q, F


class Timestamped(models.Model):
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        abstract = True


class Supplier(Timestamped):
    name = models.CharField(max_length=160, unique=True)
    contact = models.CharField(max_length=160, blank=True)
    phone = models.CharField(max_length=30, blank=True)
    email = models.EmailField(blank=True)
    address = models.TextField(blank=True, max_length=1000)
    active = models.BooleanField(default=True)

    class Meta:
        ordering = ["name"]

    def __str__(self):
        return self.name


class Product(Timestamped):
    class Kind(models.TextChoices):
        MEDICINE = "MEDICINE", "Medicine"
        GENERAL = "GENERAL", "General inventory"

    sku = models.CharField(
        max_length=40, unique=True, validators=[RegexValidator(r"^[A-Za-z0-9_-]+$")]
    )
    barcode = models.CharField(max_length=80, unique=True, null=True, blank=True)
    name = models.CharField(max_length=180, db_index=True)
    kind = models.CharField(max_length=10, choices=Kind)
    category = models.CharField(max_length=80, blank=True)
    unit = models.CharField(max_length=30, default="unit")
    generic_name = models.CharField(max_length=180, blank=True)
    strength = models.CharField(max_length=60, blank=True)
    prescription_required = models.BooleanField(default=False)
    controlled_drug = models.BooleanField(
        default=False, help_text="Controlled drugs cannot be dispensed in this release."
    )
    price = models.DecimalField(
        max_digits=12, decimal_places=2, validators=[MinValueValidator(Decimal("0.01"))]
    )
    tax_rate = models.DecimalField(
        max_digits=5,
        decimal_places=2,
        default=0,
        validators=[MinValueValidator(0), MaxValueValidator(100)],
    )
    reorder_level = models.PositiveIntegerField(
        default=10, validators=[MaxValueValidator(1_000_000)]
    )
    active = models.BooleanField(default=True)

    class Meta:
        ordering = ["name", "pk"]
        constraints = [
            models.CheckConstraint(
                condition=Q(price__gt=0), name="product_price_positive"
            ),
            models.CheckConstraint(
                condition=Q(tax_rate__gte=0, tax_rate__lte=100),
                name="product_tax_range",
            ),
            models.CheckConstraint(
                condition=Q(kind="MEDICINE") | Q(prescription_required=False),
                name="rx_only_medicine",
            ),
        ]

    def clean(self):
        from django.core.exceptions import ValidationError

        if self.kind == self.Kind.GENERAL and self.prescription_required:
            raise ValidationError(
                {"prescription_required": "Only medicines can require prescriptions."}
            )
        if not self.barcode:
            self.barcode = None

    def __str__(self):
        return f"{self.name} ({self.sku})"


class Batch(Timestamped):
    product = models.ForeignKey(
        Product, on_delete=models.PROTECT, related_name="batches"
    )
    number = models.CharField(max_length=80)
    expiry_date = models.DateField(null=True, blank=True, db_index=True)
    quantity = models.PositiveIntegerField(default=0)
    unit_cost = models.DecimalField(
        max_digits=12, decimal_places=2, validators=[MinValueValidator(0)]
    )
    quarantined = models.BooleanField(default=False)

    class Meta:
        ordering = [F("expiry_date").asc(nulls_last=True), "pk"]
        constraints = [
            models.UniqueConstraint(
                fields=["product", "number"], name="unique_product_lot"
            ),
            models.CheckConstraint(
                condition=Q(quantity__gte=0), name="stock_nonnegative"
            ),
            models.CheckConstraint(
                condition=Q(unit_cost__gte=0), name="batch_cost_nonnegative"
            ),
        ]

    def __str__(self):
        return f"{self.product.sku} / {self.number}"


class Patient(Timestamped):
    name = models.CharField(max_length=160)
    phone = models.CharField(max_length=30, blank=True)
    birth_date = models.DateField(null=True, blank=True)
    allergies = models.TextField(blank=True, max_length=2000)

    class Meta:
        ordering = ["name"]

    def __str__(self):
        return f"{self.name} · #{self.pk}"


class Prescription(Timestamped):
    patient = models.ForeignKey(
        Patient, on_delete=models.PROTECT, related_name="prescriptions"
    )
    reference = models.CharField(max_length=100, unique=True)
    prescriber = models.CharField(max_length=160)
    prescriber_registration = models.CharField(max_length=100)
    issued_on = models.DateField()
    valid_until = models.DateField()
    verified_by = models.ForeignKey(
        settings.AUTH_USER_MODEL, null=True, blank=True, on_delete=models.PROTECT
    )
    verified_at = models.DateTimeField(null=True, blank=True)
    cancelled = models.BooleanField(default=False)

    class Meta:
        ordering = ["-pk"]
        constraints = [
            models.CheckConstraint(
                condition=Q(valid_until__gte=F("issued_on")), name="rx_dates_ordered"
            )
        ]

    def __str__(self):
        return f"{self.reference} · {self.patient.name}"


class PrescriptionItem(models.Model):
    prescription = models.ForeignKey(
        Prescription, on_delete=models.CASCADE, related_name="items"
    )
    product = models.ForeignKey(Product, on_delete=models.PROTECT)
    quantity = models.PositiveIntegerField(
        validators=[MinValueValidator(1), MaxValueValidator(1_000_000)]
    )
    dispensed = models.PositiveIntegerField(default=0)
    directions = models.CharField(max_length=500)

    class Meta:
        constraints = [
            models.UniqueConstraint(
                fields=["prescription", "product"], name="one_rx_product"
            ),
            models.CheckConstraint(
                condition=Q(quantity__gt=0, dispensed__lte=F("quantity")),
                name="rx_quantity_valid",
            ),
        ]


class PurchaseOrder(Timestamped):
    class Status(models.TextChoices):
        OPEN = "OPEN", "Awaiting stock"
        RECEIVED = "RECEIVED", "Received"
        CANCELLED = "CANCELLED", "Cancelled"

    supplier = models.ForeignKey(Supplier, on_delete=models.PROTECT)
    created_by = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT)
    status = models.CharField(max_length=10, choices=Status, default=Status.OPEN)
    received_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        ordering = ["-pk"]

    @property
    def total(self):
        return sum((i.quantity * i.unit_cost for i in self.items.all()), Decimal("0"))


class PurchaseItem(models.Model):
    order = models.ForeignKey(
        PurchaseOrder, on_delete=models.CASCADE, related_name="items"
    )
    product = models.ForeignKey(Product, on_delete=models.PROTECT)
    quantity = models.PositiveIntegerField(
        validators=[MinValueValidator(1), MaxValueValidator(1_000_000)]
    )
    unit_cost = models.DecimalField(
        max_digits=12, decimal_places=2, validators=[MinValueValidator(0)]
    )

    class Meta:
        constraints = [
            models.UniqueConstraint(fields=["order", "product"], name="one_po_product"),
            models.CheckConstraint(
                condition=Q(quantity__gt=0, unit_cost__gte=0),
                name="po_quantity_cost_valid",
            ),
        ]


class Sale(Timestamped):
    class Payment(models.TextChoices):
        CASH = "CASH", "Cash"
        CARD = "CARD", "Card (external terminal)"
        TRANSFER = "TRANSFER", "Bank / wallet transfer"

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    cashier = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT)
    patient = models.ForeignKey(
        Patient, null=True, blank=True, on_delete=models.PROTECT
    )
    prescription = models.ForeignKey(
        Prescription, null=True, blank=True, on_delete=models.PROTECT
    )
    idempotency_key = models.UUIDField()
    request_hash = models.CharField(max_length=64)
    subtotal = models.DecimalField(max_digits=16, decimal_places=2)
    discount = models.DecimalField(max_digits=16, decimal_places=2, default=0)
    tax = models.DecimalField(max_digits=16, decimal_places=2)
    total = models.DecimalField(max_digits=16, decimal_places=2)
    amount_paid = models.DecimalField(max_digits=16, decimal_places=2)
    payment_method = models.CharField(max_length=10, choices=Payment)
    refunded = models.BooleanField(default=False)
    refund_reason = models.CharField(max_length=500, blank=True)
    refunded_at = models.DateTimeField(null=True, blank=True)
    refunded_by = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        null=True,
        blank=True,
        on_delete=models.PROTECT,
        related_name="refunds",
    )

    class Meta:
        ordering = ["-created_at"]
        constraints = [
            models.UniqueConstraint(
                fields=["cashier", "idempotency_key"], name="one_checkout_request"
            ),
            models.CheckConstraint(
                condition=Q(subtotal__gte=0, discount__gte=0, tax__gte=0, total__gte=0)
                & Q(discount__lte=F("subtotal"), amount_paid__gte=F("total")),
                name="sale_amounts_valid",
            ),
        ]

    @property
    def number(self):
        return str(self.pk).upper()

    @property
    def change(self):
        return self.amount_paid - self.total


class SaleLine(models.Model):
    sale = models.ForeignKey(Sale, on_delete=models.PROTECT, related_name="lines")
    product = models.ForeignKey(Product, on_delete=models.PROTECT)
    name = models.CharField(max_length=180)
    sku = models.CharField(max_length=40)
    quantity = models.PositiveIntegerField()
    unit_price = models.DecimalField(max_digits=12, decimal_places=2)
    discount = models.DecimalField(max_digits=16, decimal_places=2)
    tax_rate = models.DecimalField(max_digits=5, decimal_places=2)
    tax = models.DecimalField(max_digits=16, decimal_places=2)
    total = models.DecimalField(max_digits=16, decimal_places=2)
    cost = models.DecimalField(max_digits=16, decimal_places=2)

    class Meta:
        constraints = [
            models.CheckConstraint(
                condition=Q(
                    quantity__gt=0,
                    unit_price__gte=0,
                    discount__gte=0,
                    tax__gte=0,
                    total__gte=0,
                    cost__gte=0,
                ),
                name="sale_line_valid",
            )
        ]


class Allocation(models.Model):
    line = models.ForeignKey(
        SaleLine, on_delete=models.PROTECT, related_name="allocations"
    )
    batch = models.ForeignKey(Batch, on_delete=models.PROTECT)
    quantity = models.PositiveIntegerField()

    class Meta:
        constraints = [
            models.CheckConstraint(
                condition=Q(quantity__gt=0), name="allocation_positive"
            )
        ]


class Movement(models.Model):
    batch = models.ForeignKey(Batch, on_delete=models.PROTECT, related_name="movements")
    delta = models.IntegerField()
    kind = models.CharField(
        max_length=12,
        choices=[(x, x.title()) for x in ["RECEIPT", "SALE", "ADJUSTMENT", "RETURN"]],
    )
    reason = models.CharField(max_length=500)
    actor = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT)
    sale = models.ForeignKey(Sale, null=True, blank=True, on_delete=models.PROTECT)
    order = models.ForeignKey(
        PurchaseOrder, null=True, blank=True, on_delete=models.PROTECT
    )
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["-pk"]
        constraints = [
            models.CheckConstraint(condition=~Q(delta=0), name="movement_nonzero")
        ]


class AuditEvent(models.Model):
    actor = models.ForeignKey(
        settings.AUTH_USER_MODEL, null=True, on_delete=models.PROTECT
    )
    action = models.CharField(max_length=60)
    entity = models.CharField(max_length=60)
    entity_id = models.CharField(max_length=64)
    metadata = models.JSONField(default=dict)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["-pk"]


class StoreSettings(models.Model):
    id = models.PositiveSmallIntegerField(primary_key=True, default=1, editable=False)
    name = models.CharField(max_length=160, default="MediCare Pharmacy & Store")
    address = models.CharField(max_length=300, blank=True)
    phone = models.CharField(max_length=30, blank=True)
    receipt_footer = models.CharField(
        max_length=300, default="Thank you for choosing MediCare."
    )

    class Meta:
        constraints = [
            models.CheckConstraint(condition=Q(pk=1), name="one_store_settings")
        ]
