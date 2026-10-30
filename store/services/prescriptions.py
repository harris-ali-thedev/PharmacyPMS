from django.db import transaction
from django.utils import timezone
from rest_framework.exceptions import ValidationError
from store.models import Prescription, PrescriptionItem, Product
from .common import require_role, audit, Conflict


@transaction.atomic
def create_prescription(user, data):
    require_role(user, "ADMIN", "PHARMACIST")
    items = data.pop("items")
    if (
        data["issued_on"] > timezone.localdate()
        or data["valid_until"] < data["issued_on"]
    ):
        raise ValidationError("Prescription dates are invalid.")
    ids = [i["product_id"] for i in items]
    if len(ids) != len(set(ids)) or Product.objects.filter(
        pk__in=ids, kind="MEDICINE", active=True
    ).count() != len(ids):
        raise ValidationError("Choose unique, active medicines for a prescription.")
    prescription = Prescription.objects.create(**data)
    PrescriptionItem.objects.bulk_create(
        [PrescriptionItem(prescription=prescription, **i) for i in items]
    )
    audit(user, "prescription.created", "prescription", prescription.pk)
    return prescription


@transaction.atomic
def verify_prescription(user, prescription_id, cancel=False):
    require_role(user, "ADMIN", "PHARMACIST")
    rx = Prescription.objects.select_for_update().get(pk=prescription_id)
    if rx.cancelled:
        raise Conflict("This prescription has been cancelled.")
    if cancel:
        rx.cancelled = True
    else:
        if rx.verified_by_id:
            raise Conflict("This prescription is already verified.")
        if (
            rx.valid_until < timezone.localdate()
            or rx.issued_on > timezone.localdate()
            or not rx.items.exists()
        ):
            raise Conflict("Only current prescriptions with items can be verified.")
        rx.verified_by, rx.verified_at = user, timezone.now()
    rx.save()
    audit(
        user,
        "prescription.cancelled" if cancel else "prescription.verified",
        "prescription",
        rx.pk,
    )
    return rx
