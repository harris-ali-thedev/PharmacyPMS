import csv
from datetime import timedelta
from functools import wraps
from django.contrib import messages
from django.contrib.auth import get_user_model
from django.contrib.auth.decorators import login_required
from django.core.exceptions import PermissionDenied
from django.core.paginator import Paginator
from django.db import transaction, IntegrityError
from django.db.models import Q, F
from django.http import HttpResponse
from django.shortcuts import render, redirect, get_object_or_404
from django.utils import timezone
from django.views.decorators.http import require_POST
from rest_framework.exceptions import APIException
from . import models as m, forms, serializers as s
from .services.common import audit
from .services.inventory import product_stock, receive_batch, adjust_batch
from .services.purchasing import receive_order, cancel_order
from .services.prescriptions import verify_prescription
from .services.sales import refund_sale
from .services.reports import overview, report


def brand_context(request):
    return {"brand": m.StoreSettings.objects.filter(pk=1).first(), "currency": "PKR"}


def roles(*allowed):
    def decorate(view):
        @login_required
        @wraps(view)
        def wrapped(request, *args, **kwargs):
            if not request.user.allows(*allowed):
                raise PermissionDenied
            try:
                return view(request, *args, **kwargs)
            except APIException as exc:
                return render(
                    request,
                    "store/error.html",
                    {"error": str(exc.detail)},
                    status=exc.status_code,
                )
            except IntegrityError:
                return render(
                    request,
                    "store/error.html",
                    {
                        "error": "A unique field or database constraint conflicts with this request."
                    },
                    status=409,
                )

        return wrapped

    return decorate


def page(request, queryset):
    return Paginator(queryset, 30).get_page(request.GET.get("page"))


@login_required
def dashboard(request):
    if request.user.role == "CASHIER" and not request.user.is_superuser:
        return redirect("pos")
    low = product_stock(m.Product.objects.filter(active=True)).filter(
        stock__lte=F("reorder_level")
    )[:6]
    return render(
        request,
        "store/dashboard.html",
        {
            "stats": overview(),
            "low": low,
            "recent": m.Sale.objects.select_related("cashier")[:6],
            "expiring": m.Batch.objects.select_related("product").filter(
                quantity__gt=0,
                expiry_date__lte=timezone.localdate() + timedelta(days=90),
            )[:6],
            "today": timezone.localdate(),
        },
    )


@roles("ADMIN", "MANAGER", "PHARMACIST", "CASHIER")
def products(request):
    qs = product_stock()
    search = request.GET.get("q", "")[:180]
    if search:
        qs = qs.filter(
            Q(name__icontains=search)
            | Q(sku__icontains=search)
            | Q(barcode__icontains=search)
            | Q(generic_name__icontains=search)
        )
    kind = request.GET.get("kind", "")
    if kind in ["MEDICINE", "GENERAL"]:
        qs = qs.filter(kind=kind)
    if request.GET.get("low"):
        qs = qs.filter(stock__lte=F("reorder_level"), active=True)
    return render(
        request,
        "store/products.html",
        {
            "page": page(request, qs),
            "search": search,
            "kind": kind,
            "total_products": m.Product.objects.filter(active=True).count(),
            "medicines": m.Product.objects.filter(active=True, kind="MEDICINE").count(),
            "general": m.Product.objects.filter(active=True, kind="GENERAL").count(),
        },
    )


@roles("ADMIN", "MANAGER", "PHARMACIST")
def product_edit(request, pk=None):
    with transaction.atomic():
        obj = (
            get_object_or_404(m.Product.objects.select_for_update(), pk=pk)
            if pk
            else None
        )
        form = forms.ProductForm(request.POST or None, instance=obj)
        if request.method == "POST" and form.is_valid():
            product = form.save()
            audit(
                request.user,
                "product.updated" if pk else "product.created",
                "product",
                product.pk,
            )
            messages.success(request, "Product saved.")
            return redirect("products")
    return render(
        request,
        "store/form.html",
        {
            "form": form,
            "title": "Edit product" if pk else "Add product",
            "subtitle": "One catalog for medicines, personal care, and everyday essentials.",
            "back": "/products/",
        },
    )


@roles("ADMIN", "MANAGER", "PHARMACIST")
def batches(request):
    qs = m.Batch.objects.select_related("product").all()
    if request.GET.get("expiring"):
        qs = qs.filter(
            quantity__gt=0, expiry_date__lte=timezone.localdate() + timedelta(days=90)
        )
    return render(
        request,
        "store/batches.html",
        {"page": page(request, qs), "today": timezone.localdate()},
    )


@roles("ADMIN", "MANAGER", "PHARMACIST")
def receive(request):
    form = forms.ReceiveForm(
        request.POST or None, initial={"product": request.GET.get("product")}
    )
    if request.method == "POST" and form.is_valid():
        data = dict(form.cleaned_data)
        data["product_id"] = data.pop("product").pk
        receive_batch(request.user, **data)
        messages.success(request, "Stock received and recorded in the ledger.")
        return redirect("batches")
    return render(
        request,
        "store/form.html",
        {
            "form": form,
            "title": "Receive stock",
            "back": "/batches/",
            "subtitle": "Medicine lots require a future expiry date. Use a purchase order when tracking a supplier.",
        },
    )


@roles("ADMIN", "MANAGER", "PHARMACIST")
def adjust(request, pk):
    batch = get_object_or_404(m.Batch.objects.select_related("product"), pk=pk)
    form = forms.AdjustForm(
        request.POST or None,
        initial={"counted_quantity": batch.quantity, "quarantined": batch.quarantined},
    )
    if request.method == "POST" and form.is_valid():
        adjust_batch(request.user, pk, **form.cleaned_data)
        messages.success(request, "Stock count and quarantine status updated.")
        return redirect("batches")
    return render(
        request,
        "store/form.html",
        {
            "form": form,
            "title": "Count / quarantine stock",
            "back": "/batches/",
            "subtitle": f"{batch.product.name} · Lot {batch.number} · Current quantity: {batch.quantity}",
        },
    )


ENTITY_CONFIG = {
    "suppliers": (
        m.Supplier,
        forms.SupplierForm,
        ("ADMIN", "MANAGER", "PHARMACIST"),
        ["name", "contact", "phone", "email", "active"],
    ),
    "patients": (
        m.Patient,
        forms.PatientForm,
        ("ADMIN", "PHARMACIST"),
        ["name", "phone", "birth_date", "allergies"],
    ),
    "team": (
        get_user_model(),
        forms.UserForm,
        ("ADMIN",),
        ["username", "first_name", "last_name", "role", "is_active"],
    ),
}


@login_required
def entity_list(request, entity):
    model, _, allowed, fields = ENTITY_CONFIG[entity]
    if not request.user.allows(*allowed):
        raise PermissionDenied
    qs = model.objects.order_by("username" if entity == "team" else "name")
    search = request.GET.get("q", "")[:160]
    if search:
        qs = qs.filter(
            **{("username" if entity == "team" else "name") + "__icontains": search}
        )
    selected = page(request, qs)
    rows = [
        {"id": obj.pk, "values": [getattr(obj, field) for field in fields]}
        for obj in selected
    ]
    return render(
        request,
        "store/entities.html",
        {
            "title": entity.title(),
            "entity": entity,
            "fields": fields,
            "rows": rows,
            "page": selected,
            "search": search,
        },
    )


@roles("ADMIN", "MANAGER", "PHARMACIST")
def entity_edit(request, entity, pk=None):
    model, form_type, allowed, fields = ENTITY_CONFIG[entity]
    if not request.user.allows(*allowed):
        raise PermissionDenied
    with transaction.atomic():
        obj = (
            get_object_or_404(model.objects.select_for_update(), pk=pk) if pk else None
        )
        if entity == "team":
            if obj and obj.is_superuser and not request.user.is_superuser:
                raise PermissionDenied
            form = form_type(
                request.POST or None,
                initial={f: getattr(obj, f) for f in fields} if obj else None,
            )
        else:
            form = form_type(request.POST or None, instance=obj)
        if request.method == "POST" and form.is_valid():
            if entity == "team":
                data = dict(form.cleaned_data)
                if not data["password"]:
                    del data["password"]
                if (
                    obj
                    and obj.pk == request.user.pk
                    and (data["role"] != obj.role or not data["is_active"])
                ):
                    form.add_error(
                        None, "You cannot demote or deactivate your own account."
                    )
                else:
                    serializer = s.UserSerializer(instance=obj, data=data)
                    if serializer.is_valid():
                        obj = serializer.save()
                    else:
                        form.add_error(None, str(serializer.errors))
            else:
                obj = form.save()
            if not form.errors:
                audit(request.user, entity + ".saved", entity, obj.pk)
                messages.success(request, "Record saved.")
                return redirect("entity-list", entity=entity)
    return render(
        request,
        "store/form.html",
        {
            "form": form,
            "title": ("Edit " if pk else "Add ") + entity.rstrip("s"),
            "back": f"/{entity}/",
        },
    )


@roles("ADMIN", "MANAGER", "PHARMACIST", "CASHIER")
def pos(request):
    clinical = request.user.allows("ADMIN", "PHARMACIST")
    return render(
        request,
        "store/pos.html",
        {
            "patients": m.Patient.objects.all()[:500] if clinical else [],
            "prescriptions": m.Prescription.objects.filter(
                cancelled=False,
                verified_by__isnull=False,
                valid_until__gte=timezone.localdate(),
            ).select_related("patient")[:500]
            if clinical
            else [],
        },
    )


@roles("ADMIN", "MANAGER", "PHARMACIST", "CASHIER")
def sales(request):
    qs = m.Sale.objects.select_related("cashier")
    if not request.user.allows("ADMIN", "MANAGER", "PHARMACIST"):
        qs = qs.filter(cashier=request.user)
    return render(request, "store/sales.html", {"page": page(request, qs)})


@roles("ADMIN", "MANAGER", "PHARMACIST", "CASHIER")
def receipt(request, pk):
    sale = get_object_or_404(
        m.Sale.objects.select_related("cashier", "patient").prefetch_related(
            "lines__allocations__batch"
        ),
        pk=pk,
    )
    if (
        not request.user.allows("ADMIN", "MANAGER", "PHARMACIST")
        and sale.cashier_id != request.user.pk
    ):
        raise PermissionDenied
    if request.method == "POST":
        if not request.user.allows("ADMIN", "MANAGER"):
            raise PermissionDenied
        refund_sale(request.user, pk, request.POST.get("reason", "")[:500])
        messages.success(
            request,
            "Full refund recorded. Returned goods are quarantined. Settle the payment externally.",
        )
        return redirect("receipt", pk=pk)
    return render(request, "store/receipt.html", {"sale": sale})


@roles("ADMIN", "MANAGER", "PHARMACIST")
def purchases(request):
    qs = m.PurchaseOrder.objects.select_related(
        "supplier", "created_by"
    ).prefetch_related("items")
    return render(request, "store/purchases.html", {"page": page(request, qs)})


@roles("ADMIN", "MANAGER", "PHARMACIST")
def purchase_new(request):
    return render(
        request,
        "store/workbench.html",
        {
            "mode": "purchase",
            "title": "Create purchase order",
            "suppliers": m.Supplier.objects.filter(active=True),
            "products": m.Product.objects.filter(active=True)[:2000],
        },
    )


@roles("ADMIN", "MANAGER", "PHARMACIST")
def purchase_detail(request, pk):
    order = get_object_or_404(
        m.PurchaseOrder.objects.select_related("supplier").prefetch_related(
            "items__product"
        ),
        pk=pk,
    )
    if request.method == "POST":
        if request.POST.get("action") == "cancel":
            cancel_order(request.user, pk)
        else:
            data = {
                "items": [
                    {
                        "product_id": i.product_id,
                        "number": request.POST.get(f"number_{i.pk}", ""),
                        "expiry_date": request.POST.get(f"expiry_{i.pk}") or None,
                    }
                    for i in order.items.all()
                ]
            }
            serializer = s.PurchaseReceiptInput(data=data)
            serializer.is_valid(raise_exception=True)
            receive_order(request.user, pk, serializer.validated_data)
        messages.success(request, "Purchase order updated.")
        return redirect("purchase-detail", pk=pk)
    return render(request, "store/purchase_detail.html", {"order": order})


@roles("ADMIN", "PHARMACIST")
def prescriptions(request):
    return render(
        request,
        "store/prescriptions.html",
        {
            "page": page(
                request,
                m.Prescription.objects.select_related(
                    "patient", "verified_by"
                ).prefetch_related("items__product"),
            )
        },
    )


@roles("ADMIN", "PHARMACIST")
def prescription_new(request):
    return render(
        request,
        "store/workbench.html",
        {
            "mode": "prescription",
            "title": "Record prescription",
            "patients": m.Patient.objects.all()[:2000],
            "products": m.Product.objects.filter(active=True, kind="MEDICINE")[:2000],
            "today": timezone.localdate().isoformat(),
        },
    )


@require_POST
@roles("ADMIN", "PHARMACIST")
def prescription_action(request, pk):
    get_object_or_404(m.Prescription, pk=pk)
    verify_prescription(request.user, pk, cancel=request.POST.get("action") == "cancel")
    messages.success(request, "Prescription updated.")
    return redirect("prescriptions")


@roles("ADMIN", "MANAGER")
def reports(request):
    data = report(request.GET.get("start"), request.GET.get("end"))
    if request.GET.get("export") == "csv":
        response = HttpResponse(content_type="text/csv")
        response["Content-Disposition"] = 'attachment; filename="sales-summary.csv"'
        writer = csv.writer(response)
        writer.writerow(["Metric", "Value (PKR for amounts)"])
        writer.writerows(data.items())
        return response
    return render(request, "store/reports.html", {"report": data})


@roles("ADMIN", "MANAGER", "PHARMACIST")
def movements(request):
    return render(
        request,
        "store/movements.html",
        {
            "page": page(
                request, m.Movement.objects.select_related("batch__product", "actor")
            )
        },
    )


@roles("ADMIN")
def audit_log(request):
    return render(
        request,
        "store/audit.html",
        {"page": page(request, m.AuditEvent.objects.select_related("actor"))},
    )


@roles("ADMIN")
def settings_page(request):
    obj, _ = m.StoreSettings.objects.get_or_create(pk=1)
    form = forms.SettingsForm(request.POST or None, instance=obj)
    if request.method == "POST" and form.is_valid():
        with transaction.atomic():
            form.save()
            audit(request.user, "settings.updated", "settings", 1)
        messages.success(request, "Store settings saved.")
        return redirect("settings")
    return render(
        request,
        "store/form.html",
        {
            "form": form,
            "title": "Store settings",
            "back": "/",
            "subtitle": "Pakistan · PKR · Asia/Karachi. Prices and receipts use two decimal places.",
        },
    )
