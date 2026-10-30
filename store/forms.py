from django import forms
from django.contrib.auth import get_user_model
from django.core.exceptions import ValidationError
from . import models as m, serializers as s


class ProductForm(forms.ModelForm):
    class Meta:
        model = m.Product
        fields = [
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
        ]
        help_texts = {
            "unit": "Stock is counted in whole saleable units (for example tablet, strip, bottle, or piece).",
            "tax_rate": "Tax percentage for this SKU. Confirm applicable Pakistan tax rules with your accountant.",
            "prescription_required": "Prescription products may only be sold by a pharmacist or administrator.",
        }

    def clean(self):
        data = super().clean()
        serializer = s.ProductSerializer(
            instance=self.instance if self.instance.pk else None, data=data
        )
        if not serializer.is_valid():
            raise ValidationError(str(serializer.errors))
        return data


class SupplierForm(forms.ModelForm):
    class Meta:
        model = m.Supplier
        fields = ["name", "contact", "phone", "email", "address", "active"]


class PatientForm(forms.ModelForm):
    class Meta:
        model = m.Patient
        fields = ["name", "phone", "birth_date", "allergies"]
        widgets = {"birth_date": forms.DateInput(attrs={"type": "date"})}

    def clean_birth_date(self):
        from django.utils import timezone

        value = self.cleaned_data["birth_date"]
        if value and value > timezone.localdate():
            raise ValidationError("Birth date cannot be in the future.")
        return value


class SettingsForm(forms.ModelForm):
    class Meta:
        model = m.StoreSettings
        fields = ["name", "address", "phone", "receipt_footer"]


class UserForm(forms.Form):
    username = forms.CharField(max_length=150)
    first_name = forms.CharField(max_length=150, required=False)
    last_name = forms.CharField(max_length=150, required=False)
    role = forms.ChoiceField(choices=get_user_model().Role.choices)
    is_active = forms.BooleanField(required=False, initial=True)
    password = forms.CharField(
        widget=forms.PasswordInput,
        required=False,
        max_length=128,
        help_text="Required for new accounts. Leave blank to keep the current password.",
    )


class ReceiveForm(forms.Form):
    product = forms.ModelChoiceField(queryset=m.Product.objects.filter(active=True))
    number = forms.CharField(max_length=80, label="Lot / batch number")
    quantity = forms.IntegerField(min_value=1, max_value=1_000_000)
    unit_cost = forms.DecimalField(min_value=0, max_digits=12, decimal_places=2)
    expiry_date = forms.DateField(
        required=False, widget=forms.DateInput(attrs={"type": "date"})
    )


class AdjustForm(forms.Form):
    counted_quantity = forms.IntegerField(min_value=0, max_value=1_000_000)
    quarantined = forms.BooleanField(required=False)
    reason = forms.CharField(max_length=500, widget=forms.Textarea(attrs={"rows": 3}))
