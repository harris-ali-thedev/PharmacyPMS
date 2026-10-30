from datetime import datetime, time, timedelta
from decimal import Decimal
from django.db.models import Sum, F, DecimalField, ExpressionWrapper
from django.utils import timezone
from rest_framework.exceptions import ValidationError
from store.models import Sale, SaleLine, Batch, Product
from .inventory import product_stock, available_batches


def reporting_period(start=None, end=None):
    today = timezone.localdate()
    try:
        start = (
            datetime.strptime(start, "%Y-%m-%d").date()
            if start
            else today - timedelta(days=29)
        )
        end = datetime.strptime(end, "%Y-%m-%d").date() if end else today
    except (TypeError, ValueError):
        raise ValidationError("Use YYYY-MM-DD report dates.")
    if end < start or (end - start).days > 366:
        raise ValidationError("Choose an ordered date range of at most 367 days.")
    return (
        start,
        end,
        timezone.make_aware(datetime.combine(start, time.min)),
        timezone.make_aware(datetime.combine(end + timedelta(days=1), time.min)),
    )


def report(start=None, end=None):
    start, end, lower, upper = reporting_period(start, end)
    sales = Sale.objects.filter(created_at__gte=lower, created_at__lt=upper)
    refunds = Sale.objects.filter(refunded_at__gte=lower, refunded_at__lt=upper)
    sums = sales.aggregate(gross=Sum("total"), tax=Sum("tax"), discount=Sum("discount"))
    refunded = refunds.aggregate(total=Sum("total"), tax=Sum("tax"))
    cost = SaleLine.objects.filter(sale__in=sales).aggregate(v=Sum("cost"))[
        "v"
    ] or Decimal("0")
    returned_cost = SaleLine.objects.filter(sale__in=refunds).aggregate(v=Sum("cost"))[
        "v"
    ] or Decimal("0")
    gross, refund = sums["gross"] or Decimal("0"), refunded["total"] or Decimal("0")
    tax = (sums["tax"] or Decimal("0")) - (refunded["tax"] or Decimal("0"))
    return {
        "start": start,
        "end": end,
        "transactions": sales.count(),
        "gross_sales": gross,
        "refunds": refund,
        "net_sales": gross - refund,
        "tax": tax,
        "discounts": sums["discount"] or Decimal("0"),
        "cost_of_goods": cost - returned_cost,
        "gross_profit": gross - refund - tax - cost + returned_cost,
    }


def overview():
    today = timezone.localdate().isoformat()
    stats = report(today, today)
    stats["products"] = Product.objects.filter(active=True).count()
    stats["low_stock"] = (
        product_stock(Product.objects.filter(active=True))
        .filter(stock__lte=F("reorder_level"))
        .count()
    )
    stats["expiring"] = Batch.objects.filter(
        quantity__gt=0, expiry_date__lte=timezone.localdate() + timedelta(days=90)
    ).count()
    value = available_batches().aggregate(
        v=Sum(
            ExpressionWrapper(
                F("quantity") * F("unit_cost"),
                output_field=DecimalField(max_digits=20, decimal_places=2),
            )
        )
    )["v"]
    stats["inventory_value"] = value or Decimal("0")
    return stats
