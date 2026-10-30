from django.db import transaction
from django.db.models import Q, F, Sum, Value, IntegerField
from django.db.models.functions import Coalesce
from django.utils import timezone
from rest_framework.exceptions import ValidationError
from store.models import Product, Batch, Movement
from .common import require_role, audit, Conflict


def available_batches():
    return Batch.objects.filter(quarantined=False, quantity__gt=0).filter(
        Q(expiry_date__isnull=True) | Q(expiry_date__gt=timezone.localdate())
    )


def product_stock(queryset=None):
    queryset = Product.objects.all() if queryset is None else queryset
    return queryset.annotate(
        stock=Coalesce(
            Sum(
                "batches__quantity",
                filter=Q(batches__quarantined=False)
                & (
                    Q(batches__expiry_date__isnull=True)
                    | Q(batches__expiry_date__gt=timezone.localdate())
                ),
            ),
            Value(0),
            output_field=IntegerField(),
        )
    ).order_by("name", "pk")


@transaction.atomic
def receive_batch(
    user, product_id, number, quantity, unit_cost, expiry_date=None, order=None
):
    require_role(user, "ADMIN", "MANAGER", "PHARMACIST")
    product = Product.objects.select_for_update().get(pk=product_id)
    if not product.active:
        raise Conflict("Cannot receive an archived product.")
    if quantity <= 0 or unit_cost < 0:
        raise ValidationError("Quantity must be positive and cost cannot be negative.")
    if product.kind == Product.Kind.MEDICINE and not expiry_date:
        raise ValidationError(
            {"expiry_date": "An expiry date is required for medicines."}
        )
    if expiry_date and expiry_date <= timezone.localdate():
        raise ValidationError(
            {"expiry_date": "Cannot receive stock expiring today or earlier."}
        )
    batch, created = Batch.objects.get_or_create(
        product=product,
        number=number,
        defaults={"unit_cost": unit_cost, "expiry_date": expiry_date},
    )
    if not created and (
        batch.unit_cost != unit_cost
        or batch.expiry_date != expiry_date
        or batch.quarantined
    ):
        raise Conflict(
            "Existing lot has different cost, expiry, or quarantine status; use a new lot number."
        )
    batch.quantity = F("quantity") + quantity
    batch.save(update_fields=["quantity", "updated_at"])
    Movement.objects.create(
        batch=batch,
        delta=quantity,
        kind="RECEIPT",
        actor=user,
        order=order,
        reason="Purchase receipt" if order else "Stock receipt",
    )
    audit(user, "stock.received", "batch", batch.pk, quantity=quantity)
    batch.refresh_from_db()
    return batch


@transaction.atomic
def adjust_batch(user, batch_id, counted_quantity, reason, quarantined):
    require_role(user, "ADMIN", "MANAGER", "PHARMACIST")
    initial = Batch.objects.get(pk=batch_id)
    Product.objects.select_for_update().get(pk=initial.product_id)
    batch = Batch.objects.select_for_update().get(pk=batch_id)
    if counted_quantity < 0 or not reason.strip():
        raise ValidationError("A nonnegative count and a reason are required.")
    delta = counted_quantity - batch.quantity
    batch.quantity, batch.quarantined = counted_quantity, quarantined
    batch.save(update_fields=["quantity", "quarantined", "updated_at"])
    if delta:
        Movement.objects.create(
            batch=batch, delta=delta, kind="ADJUSTMENT", reason=reason, actor=user
        )
    audit(
        user,
        "stock.adjusted",
        "batch",
        batch.pk,
        delta=delta,
        quarantined=quarantined,
        reason=reason,
    )
    return batch
