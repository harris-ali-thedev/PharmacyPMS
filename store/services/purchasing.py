from django.db import transaction
from django.utils import timezone
from rest_framework.exceptions import ValidationError
from store.models import Supplier, Product, PurchaseOrder, PurchaseItem
from .common import require_role, audit, Conflict
from .inventory import receive_batch


@transaction.atomic
def create_order(user, data):
    require_role(user, "ADMIN", "MANAGER", "PHARMACIST")
    supplier = Supplier.objects.filter(pk=data["supplier_id"], active=True).first()
    if not supplier:
        raise ValidationError({"supplier_id": "Choose an active supplier."})
    ids = [i["product_id"] for i in data["items"]]
    if len(ids) != len(set(ids)) or Product.objects.filter(
        pk__in=ids, active=True
    ).count() != len(ids):
        raise ValidationError("Choose unique, active products.")
    order = PurchaseOrder.objects.create(supplier=supplier, created_by=user)
    PurchaseItem.objects.bulk_create(
        [
            PurchaseItem(
                order=order,
                product_id=i["product_id"],
                quantity=i["quantity"],
                unit_cost=i["unit_cost"],
            )
            for i in data["items"]
        ]
    )
    audit(user, "purchase.created", "purchase_order", order.pk)
    return order


@transaction.atomic
def receive_order(user, order_id, data):
    require_role(user, "ADMIN", "MANAGER", "PHARMACIST")
    order = PurchaseOrder.objects.select_for_update().get(pk=order_id)
    if order.status != PurchaseOrder.Status.OPEN:
        raise Conflict("Only open orders can be received.")
    items = list(order.items.order_by("product_id"))
    receipts = {i["product_id"]: i for i in data["items"]}
    if len(receipts) != len(data["items"]) or set(receipts) != {
        i.product_id for i in items
    }:
        raise ValidationError(
            "Supply exactly one lot for every ordered product. Partial receipts are not supported."
        )
    list(Product.objects.select_for_update().filter(pk__in=receipts).order_by("pk"))
    for item in items:
        receipt = receipts[item.product_id]
        receive_batch(
            user,
            item.product_id,
            receipt["number"],
            item.quantity,
            item.unit_cost,
            receipt.get("expiry_date"),
            order=order,
        )
    order.status, order.received_at = PurchaseOrder.Status.RECEIVED, timezone.now()
    order.save(update_fields=["status", "received_at", "updated_at"])
    audit(user, "purchase.received", "purchase_order", order.pk)
    return order


@transaction.atomic
def cancel_order(user, order_id):
    require_role(user, "ADMIN", "MANAGER")
    order = PurchaseOrder.objects.select_for_update().get(pk=order_id)
    if order.status != PurchaseOrder.Status.OPEN:
        raise Conflict("Only open orders can be cancelled.")
    order.status = PurchaseOrder.Status.CANCELLED
    order.save(update_fields=["status", "updated_at"])
    audit(user, "purchase.cancelled", "purchase_order", order.pk)
    return order
