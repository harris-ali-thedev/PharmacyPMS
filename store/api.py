import logging
from django.contrib.auth import get_user_model
from django.core.exceptions import (
    ObjectDoesNotExist,
    ValidationError as DjangoValidationError,
)
from django.db import IntegrityError, transaction
from django.db.models import Q, F
from django.http import Http404
from rest_framework import mixins, viewsets, status
from rest_framework.decorators import action, api_view, permission_classes
from rest_framework.exceptions import ValidationError, PermissionDenied
from rest_framework.response import Response
from rest_framework.views import exception_handler
from . import models as m, serializers as s
from .services.common import audit, require_role
from .services.inventory import product_stock, receive_batch, adjust_batch
from .services.sales import checkout, refund_sale
from .services.purchasing import create_order, receive_order, cancel_order
from .services.prescriptions import create_prescription, verify_prescription
from .services.reports import overview, report


def api_exception_handler(exc, context):
    if isinstance(exc, ObjectDoesNotExist):
        exc = Http404()
    if isinstance(exc, DjangoValidationError):
        exc = ValidationError(exc.messages)
    response = exception_handler(exc, context)
    if isinstance(exc, IntegrityError):
        response = Response(
            {
                "detail": "A unique value or database constraint conflicts with this request."
            },
            status=409,
        )
    if response is None:
        logging.getLogger("api").error("Unhandled API error (%s)", type(exc).__name__)
        response = Response(
            {
                "detail": "An unexpected error occurred. Contact your administrator with the request ID."
            },
            status=500,
        )
    response.data = {
        "error": {
            "status": response.status_code,
            "details": response.data,
            "request_id": getattr(context.get("request"), "request_id", None),
        }
    }
    return response


class RoleViewSet(viewsets.GenericViewSet):
    read_roles = ("ADMIN", "MANAGER", "PHARMACIST")
    write_roles = ("ADMIN", "MANAGER", "PHARMACIST")

    def initial(self, request, *args, **kwargs):
        super().initial(request, *args, **kwargs)
        require_role(
            request.user,
            *(
                self.read_roles
                if request.method in ["GET", "HEAD", "OPTIONS"]
                else self.write_roles
            ),
        )


class EditableViewSet(
    mixins.ListModelMixin,
    mixins.RetrieveModelMixin,
    mixins.CreateModelMixin,
    mixins.UpdateModelMixin,
    RoleViewSet,
):
    # Business records are archived rather than physically deleted.
    def perform_create(self, serializer):
        with transaction.atomic():
            obj = serializer.save()
            audit(self.request.user, self.basename + ".created", self.basename, obj.pk)

    def perform_update(self, serializer):
        with transaction.atomic():
            obj = self.get_queryset().select_for_update().get(pk=serializer.instance.pk)
            # Revalidate against the locked state rather than a stale pre-lock instance.
            serializer.instance = obj
            serializer.validate(serializer.validated_data)
            obj = serializer.save()
            audit(self.request.user, self.basename + ".updated", self.basename, obj.pk)


class ProductViewSet(EditableViewSet):
    serializer_class = s.ProductSerializer
    read_roles = ("ADMIN", "MANAGER", "PHARMACIST", "CASHIER")

    def get_queryset(self):
        qs = product_stock()
        q = self.request.query_params.get("q", "")[:180]
        if q:
            qs = qs.filter(
                Q(name__icontains=q)
                | Q(sku__icontains=q)
                | Q(barcode__icontains=q)
                | Q(generic_name__icontains=q)
            )
        if self.request.query_params.get("kind") in ["MEDICINE", "GENERAL"]:
            qs = qs.filter(kind=self.request.query_params["kind"])
        if self.request.query_params.get("low") == "true":
            qs = qs.filter(stock__lte=F("reorder_level"), active=True)
        return qs

    def perform_update(self, serializer):
        # PostgreSQL cannot lock the annotated aggregate query; lock the base row.
        with transaction.atomic():
            serializer.instance = m.Product.objects.select_for_update().get(
                pk=serializer.instance.pk
            )
            serializer.validate(serializer.validated_data)
            obj = serializer.save()
            audit(self.request.user, "product.updated", "product", obj.pk)


class SupplierViewSet(EditableViewSet):
    serializer_class = s.SupplierSerializer
    queryset = m.Supplier.objects.all()


class PatientViewSet(EditableViewSet):
    serializer_class = s.PatientSerializer
    queryset = m.Patient.objects.all()
    read_roles = write_roles = ("ADMIN", "PHARMACIST")


class BatchViewSet(mixins.ListModelMixin, mixins.RetrieveModelMixin, RoleViewSet):
    serializer_class = s.BatchSerializer
    queryset = m.Batch.objects.select_related("product").all()

    @action(detail=False, methods=["post"])
    def receive(self, request):
        serializer = s.ReceiptInput(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = dict(serializer.validated_data)
        data["product_id"] = data.pop("product").pk
        batch = receive_batch(request.user, **data)
        return Response(s.BatchSerializer(batch).data, status=201)

    @action(detail=True, methods=["post"])
    def adjust(self, request, pk=None):
        serializer = s.AdjustmentInput(data=request.data)
        serializer.is_valid(raise_exception=True)
        batch = adjust_batch(
            request.user, self.get_object().pk, **serializer.validated_data
        )
        return Response(s.BatchSerializer(batch).data)


class SaleViewSet(mixins.ListModelMixin, mixins.RetrieveModelMixin, RoleViewSet):
    serializer_class = s.SaleSerializer
    read_roles = write_roles = ("ADMIN", "MANAGER", "PHARMACIST", "CASHIER")

    def get_queryset(self):
        qs = m.Sale.objects.select_related("cashier").prefetch_related("lines")
        return (
            qs
            if self.request.user.allows("ADMIN", "MANAGER", "PHARMACIST")
            else qs.filter(cashier=self.request.user)
        )

    def create(self, request):
        serializer = s.CheckoutInput(data=request.data)
        serializer.is_valid(raise_exception=True)
        sale, created = checkout(request.user, serializer.validated_data)
        return Response(s.SaleSerializer(sale).data, status=201 if created else 200)

    @action(detail=True, methods=["post"])
    def refund(self, request, pk=None):
        serializer = s.ReasonInput(data=request.data)
        serializer.is_valid(raise_exception=True)
        sale = refund_sale(
            request.user, self.get_object().pk, **serializer.validated_data
        )
        return Response(s.SaleSerializer(sale).data)


class PurchaseViewSet(mixins.ListModelMixin, mixins.RetrieveModelMixin, RoleViewSet):
    queryset = m.PurchaseOrder.objects.select_related("supplier").prefetch_related(
        "items__product"
    )
    serializer_class = s.PurchaseSerializer

    def create(self, request):
        serializer = s.PurchaseInput(data=request.data)
        serializer.is_valid(raise_exception=True)
        return Response(
            s.PurchaseSerializer(
                create_order(request.user, serializer.validated_data)
            ).data,
            status=201,
        )

    @action(detail=True, methods=["post"])
    def receive(self, request, pk=None):
        serializer = s.PurchaseReceiptInput(data=request.data)
        serializer.is_valid(raise_exception=True)
        return Response(
            s.PurchaseSerializer(
                receive_order(
                    request.user, self.get_object().pk, serializer.validated_data
                )
            ).data
        )

    @action(detail=True, methods=["post"])
    def cancel(self, request, pk=None):
        return Response(
            s.PurchaseSerializer(cancel_order(request.user, self.get_object().pk)).data
        )


class PrescriptionViewSet(
    mixins.ListModelMixin, mixins.RetrieveModelMixin, RoleViewSet
):
    queryset = m.Prescription.objects.select_related("patient").prefetch_related(
        "items__product"
    )
    serializer_class = s.PrescriptionSerializer
    read_roles = write_roles = ("ADMIN", "PHARMACIST")

    def create(self, request):
        serializer = s.PrescriptionInput(data=request.data)
        serializer.is_valid(raise_exception=True)
        return Response(
            s.PrescriptionSerializer(
                create_prescription(request.user, dict(serializer.validated_data))
            ).data,
            status=201,
        )

    @action(detail=True, methods=["post"])
    def verify(self, request, pk=None):
        return Response(
            s.PrescriptionSerializer(
                verify_prescription(request.user, self.get_object().pk)
            ).data
        )

    @action(detail=True, methods=["post"])
    def cancel(self, request, pk=None):
        return Response(
            s.PrescriptionSerializer(
                verify_prescription(request.user, self.get_object().pk, cancel=True)
            ).data
        )


class UserViewSet(EditableViewSet):
    queryset = get_user_model().objects.order_by("username")
    serializer_class = s.UserSerializer
    read_roles = write_roles = ("ADMIN",)

    def perform_update(self, serializer):
        if serializer.instance.pk == self.request.user.pk and (
            serializer.validated_data.get("role", serializer.instance.role)
            != serializer.instance.role
            or not serializer.validated_data.get("is_active", True)
        ):
            raise ValidationError("You cannot demote or deactivate your own account.")
        if serializer.instance.is_superuser and not self.request.user.is_superuser:
            raise PermissionDenied("Only a superuser may modify another superuser.")
        super().perform_update(serializer)


class MovementViewSet(mixins.ListModelMixin, RoleViewSet):
    queryset = m.Movement.objects.select_related("batch__product", "actor")
    serializer_class = s.MovementSerializer


class AuditViewSet(mixins.ListModelMixin, RoleViewSet):
    queryset = m.AuditEvent.objects.select_related("actor")
    serializer_class = s.AuditSerializer
    read_roles = ("ADMIN",)


@api_view(["GET"])
def me(request):
    return Response(s.UserSerializer(request.user).data)


@api_view(["GET"])
def dashboard(request):
    require_role(request.user, "ADMIN", "MANAGER", "PHARMACIST")
    return Response(overview())


@api_view(["GET"])
def reports(request):
    require_role(request.user, "ADMIN", "MANAGER")
    return Response(
        report(request.query_params.get("start"), request.query_params.get("end"))
    )


@api_view(["GET", "PATCH"])
def settings_view(request):
    require_role(request.user, "ADMIN")
    obj, _ = m.StoreSettings.objects.get_or_create(pk=1)
    if request.method == "PATCH":
        serializer = s.SettingsSerializer(obj, data=request.data, partial=True)
        serializer.is_valid(raise_exception=True)
        with transaction.atomic():
            serializer.save()
            audit(request.user, "settings.updated", "settings", 1)
    return Response(s.SettingsSerializer(obj).data)


@api_view(["GET"])
@permission_classes([])
def health(request):
    from django.db import connection

    try:
        with connection.cursor() as cursor:
            cursor.execute("SELECT 1")
        m.Product.objects.only("pk").first()
        return Response({"status": "ok"})
    except Exception:
        return Response(
            {"status": "unavailable"}, status=status.HTTP_503_SERVICE_UNAVAILABLE
        )
