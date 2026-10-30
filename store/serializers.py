from django.contrib.auth import get_user_model
from django.contrib.auth.password_validation import validate_password
from django.utils import timezone
from rest_framework import serializers
from store import models as m


class ProductSerializer(serializers.ModelSerializer):
    stock = serializers.SerializerMethodField()

    def get_stock(self, obj):
        if "stock" in obj.__dict__:
            return obj.stock
        from django.db.models import Sum
        from .services.inventory import available_batches

        return (
            available_batches()
            .filter(product=obj)
            .aggregate(total=Sum("quantity"))["total"]
            or 0
        )

    class Meta:
        model = m.Product
        fields = [
            "id",
            "sku",
            "barcode",
            "name",
            "kind",
            "category",
            "unit",
            "generic_name",
            "strength",
            "prescription_required",
            "controlled_drug",
            "price",
            "tax_rate",
            "reorder_level",
            "active",
            "stock",
            "updated_at",
        ]
        read_only_fields = ["updated_at"]

    def validate(self, data):
        kind = data.get("kind", getattr(self.instance, "kind", None))
        rx = data.get(
            "prescription_required",
            getattr(self.instance, "prescription_required", False),
        )
        if kind == "GENERAL" and rx:
            raise serializers.ValidationError(
                {"prescription_required": "Only medicines can require prescriptions."}
            )
        if "barcode" in data and not data["barcode"]:
            data["barcode"] = None
        # Reclassification would invalidate historical expiry / dispensing guarantees.
        if (
            self.instance
            and self.instance.batches.exists()
            and any(
                data.get(f, getattr(self.instance, f)) != getattr(self.instance, f)
                for f in ["kind", "prescription_required", "controlled_drug"]
            )
        ):
            raise serializers.ValidationError(
                "Classification cannot change after receiving stock. Archive and create a new SKU."
            )
        return data


class SupplierSerializer(serializers.ModelSerializer):
    class Meta:
        model = m.Supplier
        fields = ["id", "name", "contact", "phone", "email", "address", "active"]


class PatientSerializer(serializers.ModelSerializer):
    class Meta:
        model = m.Patient
        fields = ["id", "name", "phone", "birth_date", "allergies"]

    def validate_birth_date(self, value):
        if value and value > timezone.localdate():
            raise serializers.ValidationError("Birth date cannot be in the future.")
        return value


class BatchSerializer(serializers.ModelSerializer):
    product_name = serializers.CharField(source="product.name", read_only=True)
    sku = serializers.CharField(source="product.sku", read_only=True)

    class Meta:
        model = m.Batch
        fields = [
            "id",
            "product_id",
            "product_name",
            "sku",
            "number",
            "expiry_date",
            "quantity",
            "unit_cost",
            "quarantined",
        ]


class ReceiptInput(serializers.Serializer):
    product_id = serializers.PrimaryKeyRelatedField(
        queryset=m.Product.objects.all(), source="product"
    )
    number = serializers.CharField(max_length=80)
    quantity = serializers.IntegerField(min_value=1, max_value=1_000_000)
    unit_cost = serializers.DecimalField(max_digits=12, decimal_places=2, min_value=0)
    expiry_date = serializers.DateField(required=False, allow_null=True)


class AdjustmentInput(serializers.Serializer):
    counted_quantity = serializers.IntegerField(min_value=0, max_value=1_000_000)
    reason = serializers.CharField(max_length=500)
    quarantined = serializers.BooleanField()


class BasketItem(serializers.Serializer):
    product_id = serializers.IntegerField(min_value=1)
    quantity = serializers.IntegerField(min_value=1, max_value=1_000_000)


class CheckoutInput(serializers.Serializer):
    idempotency_key = serializers.UUIDField()
    items = BasketItem(many=True, allow_empty=False, max_length=100)
    patient_id = serializers.PrimaryKeyRelatedField(
        queryset=m.Patient.objects.all(),
        required=False,
        allow_null=True,
        source="patient",
    )
    prescription_id = serializers.IntegerField(
        min_value=1, required=False, allow_null=True
    )
    discount = serializers.DecimalField(
        max_digits=12, decimal_places=2, min_value=0, default=0
    )
    amount_paid = serializers.DecimalField(max_digits=16, decimal_places=2, min_value=0)
    payment_method = serializers.ChoiceField(choices=m.Sale.Payment.choices)

    def validate(self, data):
        patient = data.pop("patient", None)
        data["patient_id"] = patient.pk if patient else None
        return data


class SaleLineSerializer(serializers.ModelSerializer):
    class Meta:
        model = m.SaleLine
        fields = [
            "id",
            "product_id",
            "name",
            "sku",
            "quantity",
            "unit_price",
            "discount",
            "tax_rate",
            "tax",
            "total",
        ]


class SaleSerializer(serializers.ModelSerializer):
    lines = SaleLineSerializer(many=True, read_only=True)
    cashier_name = serializers.CharField(source="cashier.username", read_only=True)
    change = serializers.DecimalField(max_digits=16, decimal_places=2, read_only=True)

    class Meta:
        model = m.Sale
        fields = [
            "id",
            "created_at",
            "cashier_name",
            "patient_id",
            "prescription_id",
            "subtotal",
            "discount",
            "tax",
            "total",
            "amount_paid",
            "change",
            "payment_method",
            "refunded",
            "refund_reason",
            "lines",
        ]


class ReasonInput(serializers.Serializer):
    reason = serializers.CharField(max_length=500)


class PurchaseItemInput(BasketItem):
    unit_cost = serializers.DecimalField(max_digits=12, decimal_places=2, min_value=0)


class PurchaseInput(serializers.Serializer):
    supplier_id = serializers.IntegerField(min_value=1)
    items = PurchaseItemInput(many=True, allow_empty=False, max_length=100)


class LotInput(serializers.Serializer):
    product_id = serializers.IntegerField(min_value=1)
    number = serializers.CharField(max_length=80)
    expiry_date = serializers.DateField(required=False, allow_null=True)


class PurchaseReceiptInput(serializers.Serializer):
    items = LotInput(many=True, allow_empty=False, max_length=100)


class PurchaseItemSerializer(serializers.ModelSerializer):
    name = serializers.CharField(source="product.name")

    class Meta:
        model = m.PurchaseItem
        fields = ["id", "product_id", "name", "quantity", "unit_cost"]


class PurchaseSerializer(serializers.ModelSerializer):
    items = PurchaseItemSerializer(many=True, read_only=True)
    supplier_name = serializers.CharField(source="supplier.name")
    total = serializers.DecimalField(max_digits=16, decimal_places=2, read_only=True)

    class Meta:
        model = m.PurchaseOrder
        fields = [
            "id",
            "supplier_id",
            "supplier_name",
            "status",
            "created_at",
            "received_at",
            "total",
            "items",
        ]


class PrescriptionItemInput(BasketItem):
    directions = serializers.CharField(max_length=500)


class PrescriptionInput(serializers.Serializer):
    patient_id = serializers.PrimaryKeyRelatedField(
        queryset=m.Patient.objects.all(), source="patient"
    )
    reference = serializers.CharField(max_length=100)
    prescriber = serializers.CharField(max_length=160)
    prescriber_registration = serializers.CharField(max_length=100)
    issued_on = serializers.DateField()
    valid_until = serializers.DateField()
    items = PrescriptionItemInput(many=True, allow_empty=False, max_length=100)

    def validate(self, data):
        data["patient_id"] = data.pop("patient").pk
        if m.Prescription.objects.filter(reference=data["reference"]).exists():
            raise serializers.ValidationError(
                {"reference": "Prescription reference must be unique."}
            )
        return data


class PrescriptionItemSerializer(serializers.ModelSerializer):
    name = serializers.CharField(source="product.name")

    class Meta:
        model = m.PrescriptionItem
        fields = ["product_id", "name", "quantity", "dispensed", "directions"]


class PrescriptionSerializer(serializers.ModelSerializer):
    items = PrescriptionItemSerializer(many=True, read_only=True)
    patient_name = serializers.CharField(source="patient.name")

    class Meta:
        model = m.Prescription
        fields = [
            "id",
            "reference",
            "patient_id",
            "patient_name",
            "prescriber",
            "prescriber_registration",
            "issued_on",
            "valid_until",
            "verified_by_id",
            "verified_at",
            "cancelled",
            "items",
        ]


class UserSerializer(serializers.ModelSerializer):
    password = serializers.CharField(
        write_only=True, required=False, min_length=10, max_length=128
    )

    class Meta:
        model = get_user_model()
        fields = [
            "id",
            "username",
            "first_name",
            "last_name",
            "role",
            "is_active",
            "password",
        ]

    def validate(self, data):
        if not self.instance and not data.get("password"):
            raise serializers.ValidationError({"password": "A password is required."})
        if "password" in data:
            validate_password(data["password"], self.instance)
        return data

    def create(self, validated_data):
        return get_user_model().objects.create_user(**validated_data)

    def update(self, instance, validated_data):
        password = validated_data.pop("password", None)
        for key, value in validated_data.items():
            setattr(instance, key, value)
        if password:
            instance.set_password(password)
        instance.save()
        return instance


class SettingsSerializer(serializers.ModelSerializer):
    class Meta:
        model = m.StoreSettings
        fields = ["name", "address", "phone", "receipt_footer"]


class MovementSerializer(serializers.ModelSerializer):
    sku = serializers.CharField(source="batch.product.sku")
    lot = serializers.CharField(source="batch.number")
    actor_name = serializers.CharField(source="actor.username")

    class Meta:
        model = m.Movement
        fields = [
            "id",
            "sku",
            "lot",
            "delta",
            "kind",
            "reason",
            "actor_name",
            "sale_id",
            "order_id",
            "created_at",
        ]


class AuditSerializer(serializers.ModelSerializer):
    actor_name = serializers.CharField(source="actor.username", default="System")

    class Meta:
        model = m.AuditEvent
        fields = [
            "id",
            "actor_name",
            "action",
            "entity",
            "entity_id",
            "metadata",
            "created_at",
        ]
