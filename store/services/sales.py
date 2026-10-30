import hashlib
import json
import uuid
from decimal import Decimal, ROUND_HALF_UP
from django.contrib.auth import get_user_model
from django.db import transaction
from django.db.models import F
from django.utils import timezone
from rest_framework.exceptions import ValidationError
from store.models import (
    Product,
    Batch,
    Sale,
    SaleLine,
    Allocation,
    Movement,
    Prescription,
    PrescriptionItem,
)
from .common import require_role, audit, Conflict
from .inventory import available_batches


def money(value):
    return Decimal(value).quantize(Decimal(".01"), rounding=ROUND_HALF_UP)


@transaction.atomic
def checkout(user, data):
    require_role(user, "ADMIN", "MANAGER", "PHARMACIST", "CASHIER")
    # Serialize retries from the same operator before checking idempotency.
    get_user_model().objects.select_for_update().get(pk=user.pk)
    fingerprint = hashlib.sha256(
        json.dumps(data, sort_keys=True, default=str).encode()
    ).hexdigest()
    existing = Sale.objects.filter(
        cashier=user, idempotency_key=data["idempotency_key"]
    ).first()
    if existing:
        if existing.request_hash != fingerprint:
            raise Conflict(
                "This checkout key has already been used with a different basket."
            )
        return existing, False
    items = data["items"]
    quantities = {item["product_id"]: item["quantity"] for item in items}
    if (
        not quantities
        or len(items) != len(quantities)
        or any(q <= 0 for q in quantities.values())
    ):
        raise ValidationError("Use unique products and positive quantities.")
    # All stock-writing services lock products first in ascending ID order.
    products = list(
        Product.objects.select_for_update().filter(pk__in=quantities).order_by("pk")
    )
    if len(products) != len(quantities) or any(not p.active for p in products):
        raise ValidationError("One or more products are missing or archived.")
    rx = None
    if data.get("patient_id") or data.get("prescription_id"):
        require_role(user, "ADMIN", "PHARMACIST")
    if data.get("prescription_id"):
        rx = (
            Prescription.objects.select_for_update()
            .filter(pk=data["prescription_id"])
            .first()
        )
        if (
            not rx
            or rx.cancelled
            or not rx.verified_by_id
            or rx.issued_on > timezone.localdate()
            or rx.valid_until < timezone.localdate()
        ):
            raise Conflict("Prescription must be verified, current, and not cancelled.")
        if data.get("patient_id") != rx.patient_id:
            raise ValidationError("The patient must match the prescription.")
    rx_items = (
        {i.product_id: i for i in PrescriptionItem.objects.filter(prescription=rx)}
        if rx
        else {}
    )
    for product in products:
        if product.controlled_drug:
            raise Conflict(
                "Controlled-drug dispensing is not supported. Use your approved controlled-drug workflow."
            )
        if product.prescription_required:
            require_role(user, "ADMIN", "PHARMACIST")
            item = rx_items.get(product.pk)
            if not item or item.dispensed + quantities[product.pk] > item.quantity:
                raise Conflict(
                    f"A verified prescription with sufficient allowance is required for {product.name}."
                )
    subtotal = sum((p.price * quantities[p.pk] for p in products), Decimal("0"))
    discount = money(data.get("discount", 0))
    if discount < 0 or discount > subtotal:
        raise ValidationError(
            {"discount": "Discount must be between zero and the subtotal."}
        )
    if discount and not user.allows("ADMIN", "MANAGER"):
        raise ValidationError(
            {"discount": "Only administrators and managers can apply discounts."}
        )
    remaining_discount = discount
    remaining_subtotal = subtotal
    planned = []
    for index, product in enumerate(products):
        qty = quantities[product.pk]
        gross = product.price * qty
        share = (
            remaining_discount
            if index == len(products) - 1
            else min(
                remaining_discount,
                money(remaining_discount * gross / remaining_subtotal),
            )
        )
        remaining_discount -= share
        remaining_subtotal -= gross
        tax = money((gross - share) * product.tax_rate / 100)
        remaining = qty
        allocations = []
        for batch in available_batches().filter(product=product).select_for_update():
            take = min(batch.quantity, remaining)
            allocations.append((batch, take))
            remaining -= take
            if remaining == 0:
                break
        if remaining:
            raise Conflict(
                f"Insufficient saleable stock for {product.name}; expired and quarantined stock is excluded."
            )
        planned.append((product, qty, share, tax, allocations))
    tax_total = sum((row[3] for row in planned), Decimal("0"))
    total = subtotal - discount + tax_total
    maximum = Decimal("99999999999999.99")
    if (
        subtotal > maximum
        or total > maximum
        or any(
            sum((b.unit_cost * q for b, q in row[4]), Decimal("0")) > maximum
            for row in planned
        )
    ):
        raise ValidationError(
            "This basket exceeds the supported monetary limit. Split the transaction."
        )
    paid = money(data["amount_paid"])
    if paid < total:
        raise ValidationError({"amount_paid": f"Payment must cover PKR {total:.2f}."})
    if data["payment_method"] != Sale.Payment.CASH and paid != total:
        raise ValidationError(
            {"amount_paid": "Non-cash payments must equal the exact total."}
        )
    sale = Sale.objects.create(
        cashier=user,
        patient_id=data.get("patient_id"),
        prescription=rx,
        idempotency_key=data["idempotency_key"],
        request_hash=fingerprint,
        subtotal=subtotal,
        discount=discount,
        tax=tax_total,
        total=total,
        amount_paid=paid,
        payment_method=data["payment_method"],
    )
    for product, qty, share, tax, allocations in planned:
        line = SaleLine.objects.create(
            sale=sale,
            product=product,
            name=product.name,
            sku=product.sku,
            quantity=qty,
            unit_price=product.price,
            discount=share,
            tax_rate=product.tax_rate,
            tax=tax,
            total=product.price * qty - share + tax,
            cost=sum((b.unit_cost * q for b, q in allocations), Decimal("0")),
        )
        for batch, quantity in allocations:
            batch.quantity = F("quantity") - quantity
            batch.save(update_fields=["quantity", "updated_at"])
            Allocation.objects.create(line=line, batch=batch, quantity=quantity)
            Movement.objects.create(
                batch=batch,
                delta=-quantity,
                kind="SALE",
                actor=user,
                sale=sale,
                reason="Checkout",
            )
        # Account for every prescribed medicine in this sale, including OTC items listed on the Rx.
        if product.pk in rx_items:
            item = rx_items[product.pk]
            if item.dispensed + qty > item.quantity:
                raise Conflict("Prescription quantity would be exceeded.")
            item.dispensed += qty
            item.save(update_fields=["dispensed"])
    audit(user, "sale.completed", "sale", sale.pk, total=str(total))
    return sale, True


@transaction.atomic
def refund_sale(user, sale_id, reason):
    require_role(user, "ADMIN", "MANAGER")
    sale = Sale.objects.select_for_update().get(pk=sale_id)
    if sale.refunded:
        raise Conflict("This sale has already been refunded.")
    if not reason.strip():
        raise ValidationError({"reason": "A reason is required."})
    lines = list(sale.lines.select_related("product"))
    list(
        Product.objects.select_for_update()
        .filter(pk__in=[i.product_id for i in lines])
        .order_by("pk")
    )
    # Returned goods enter separate quarantine lots; original saleable lots remain untouched.
    for line in lines:
        for allocation in line.allocations.select_related("batch"):
            original = allocation.batch
            batch = Batch.objects.create(
                product=line.product,
                number="RETURN-" + uuid.uuid4().hex,
                expiry_date=original.expiry_date,
                unit_cost=original.unit_cost,
                quantity=allocation.quantity,
                quarantined=True,
            )
            Movement.objects.create(
                batch=batch,
                delta=allocation.quantity,
                kind="RETURN",
                actor=user,
                sale=sale,
                reason=reason,
            )
    # Rx allowances are deliberately not restored by financial refunds.
    sale.refunded = True
    sale.refund_reason, sale.refunded_by, sale.refunded_at = (
        reason,
        user,
        timezone.now(),
    )
    sale.save(
        update_fields=[
            "refunded",
            "refund_reason",
            "refunded_by",
            "refunded_at",
            "updated_at",
        ]
    )
    audit(user, "sale.refunded", "sale", sale.pk, reason=reason, amount=str(sale.total))
    return sale
