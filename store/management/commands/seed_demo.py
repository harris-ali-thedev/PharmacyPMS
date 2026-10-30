import os
from datetime import timedelta
from decimal import Decimal
from django.conf import settings
from django.contrib.auth import get_user_model
from django.core.management.base import BaseCommand, CommandError
from django.db import transaction
from django.utils import timezone
from store.models import Product, Supplier, StoreSettings
from store.services.inventory import receive_batch


class Command(BaseCommand):
    help = "Add fictional demo catalog in local development. Never overwrites existing records."

    @transaction.atomic
    def handle(self, *args, **options):
        if not settings.DEBUG:
            raise CommandError("Demo data is only available with DEBUG=true.")
        password = os.getenv("DEMO_ADMIN_PASSWORD")
        if not password or len(password) < 12:
            raise CommandError(
                "Set DEMO_ADMIN_PASSWORD to a unique password of at least 12 characters."
            )
        from django.contrib.auth.password_validation import validate_password

        validate_password(password)
        user, created = get_user_model().objects.get_or_create(
            username="owner",
            defaults={"role": "ADMIN", "first_name": "Store", "last_name": "Owner"},
        )
        if created:
            user.set_password(password)
            user.save()
        StoreSettings.objects.get_or_create(pk=1)
        for name in [
            "Careline Distributors",
            "Everyday Wholesale",
            "Healthway Supplies",
        ]:
            Supplier.objects.get_or_create(name=name)
        entries = [
            (
                "MED-001",
                "Paracetamol 500mg",
                "MEDICINE",
                "Pain relief",
                "tablet",
                "3.50",
                180,
                False,
            ),
            (
                "MED-002",
                "Amoxicillin 500mg",
                "MEDICINE",
                "Antibiotics",
                "capsule",
                "24.00",
                65,
                True,
            ),
            (
                "MED-003",
                "Cetirizine 10mg",
                "MEDICINE",
                "Allergy care",
                "tablet",
                "8.00",
                8,
                False,
            ),
            (
                "MED-004",
                "Oral Rehydration Salts",
                "MEDICINE",
                "Digestive health",
                "sachet",
                "35.00",
                40,
                False,
            ),
            (
                "MED-005",
                "Vitamin C 500mg",
                "MEDICINE",
                "Vitamins",
                "tablet",
                "15.00",
                100,
                False,
            ),
            (
                "MED-006",
                "Antiseptic Solution 100ml",
                "MEDICINE",
                "First aid",
                "bottle",
                "240.00",
                7,
                False,
            ),
            (
                "GEN-001",
                "Gentle Hand Wash 250ml",
                "GENERAL",
                "Personal care",
                "bottle",
                "350.00",
                26,
                False,
            ),
            (
                "GEN-002",
                "Digital Thermometer",
                "GENERAL",
                "Health devices",
                "piece",
                "850.00",
                12,
                False,
            ),
            (
                "GEN-003",
                "Cotton Wool 100g",
                "GENERAL",
                "Everyday essentials",
                "pack",
                "160.00",
                32,
                False,
            ),
            (
                "GEN-004",
                "Baby Wipes · 80 Count",
                "GENERAL",
                "Baby care",
                "pack",
                "420.00",
                18,
                False,
            ),
            (
                "GEN-005",
                "Surgical Face Masks · 50",
                "GENERAL",
                "Protective care",
                "box",
                "300.00",
                6,
                False,
            ),
            (
                "GEN-006",
                "Soft Toothbrush",
                "GENERAL",
                "Oral care",
                "piece",
                "180.00",
                25,
                False,
            ),
        ]
        for index, (sku, name, kind, category, unit, price, qty, rx) in enumerate(
            entries
        ):
            product, created = Product.objects.get_or_create(
                sku=sku,
                defaults={
                    "name": name,
                    "kind": kind,
                    "category": category,
                    "unit": unit,
                    "price": Decimal(price),
                    "prescription_required": rx,
                    "barcode": f"99000000{index:05d}",
                    "reorder_level": 10,
                },
            )
            if created:
                receive_batch(
                    user,
                    product.pk,
                    "DEMO-" + sku,
                    qty,
                    Decimal(price) * Decimal(".65"),
                    timezone.localdate() + timedelta(days=45 if index == 2 else 540)
                    if kind == "MEDICINE"
                    else None,
                )
        self.stdout.write(
            self.style.SUCCESS(
                "Demo catalog ready. Sign in as owner using the supplied password. Existing accounts and catalog records were preserved."
            )
        )
