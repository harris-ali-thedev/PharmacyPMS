from django.contrib import admin
from django.contrib.auth.admin import UserAdmin
from .models import User


@admin.register(User)
class StaffAdmin(UserAdmin):
    fieldsets = UserAdmin.fieldsets + (("Store role", {"fields": ("role",)}),)
    add_fieldsets = UserAdmin.add_fieldsets + (("Store role", {"fields": ("role",)}),)
    list_display = ("username", "role", "is_active", "is_superuser")

    def has_module_permission(self, request):
        return request.user.is_superuser

    def has_view_permission(self, request, obj=None):
        return request.user.is_superuser

    def has_change_permission(self, request, obj=None):
        return request.user.is_superuser

    def has_add_permission(self, request):
        return request.user.is_superuser

    def has_delete_permission(self, request, obj=None):
        return False
