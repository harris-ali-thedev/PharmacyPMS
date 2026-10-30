from django.contrib.auth.models import AbstractUser
from django.db import models


class User(AbstractUser):
    class Role(models.TextChoices):
        ADMIN = "ADMIN", "Administrator"
        MANAGER = "MANAGER", "Store manager"
        PHARMACIST = "PHARMACIST", "Pharmacist"
        CASHIER = "CASHIER", "Cashier"

    role = models.CharField(max_length=12, choices=Role, default=Role.CASHIER)

    def allows(self, *roles):
        return (
            self.is_authenticated
            and self.is_active
            and (self.is_superuser or self.role in roles)
        )


class LoginAttempt(models.Model):
    key = models.CharField(max_length=64, unique=True)
    failures = models.PositiveIntegerField(default=0)
    window_start = models.DateTimeField()
