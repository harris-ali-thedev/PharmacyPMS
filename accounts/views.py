import hashlib
from datetime import timedelta
from django.contrib.auth import authenticate, login
from django.contrib.auth.forms import AuthenticationForm
from django.db import transaction
from django.shortcuts import redirect, render
from django.utils import timezone
from django.views.decorators.debug import sensitive_post_parameters
from django.views.decorators.http import require_http_methods
from .models import LoginAttempt


@sensitive_post_parameters("password")
@require_http_methods(["GET", "POST"])
def sign_in(request):
    if request.user.is_authenticated:
        return redirect("dashboard")
    form = AuthenticationForm(request)
    error = None
    status = 200
    if request.method == "POST":
        username = request.POST.get("username", "")[:150]
        # Both account and IP buckets stop password spraying; no forwarded IP trust.
        keys = sorted(
            hashlib.sha256(v.encode()).hexdigest()
            for v in [
                "user:" + username.casefold(),
                "ip:" + request.META.get("REMOTE_ADDR", ""),
            ]
        )
        with transaction.atomic():
            buckets = []
            for key in keys:
                LoginAttempt.objects.get_or_create(
                    key=key, defaults={"window_start": timezone.now()}
                )
                bucket = LoginAttempt.objects.select_for_update().get(key=key)
                if bucket.window_start < timezone.now() - timedelta(minutes=15):
                    bucket.failures = 0
                    bucket.window_start = timezone.now()
                buckets.append(bucket)
            if any(b.failures >= 10 for b in buckets):
                error, status = "Too many attempts. Try again in 15 minutes.", 429
            else:
                user = authenticate(
                    request,
                    username=username,
                    password=request.POST.get("password", ""),
                )
                if user:
                    login(request, user)
                    # Leave IP failures in place to prevent rotating-account bypasses.
                    for b in buckets:
                        if (
                            b.key
                            == hashlib.sha256(
                                ("user:" + username.casefold()).encode()
                            ).hexdigest()
                        ):
                            b.failures = 0
                        b.save()
                    return redirect("dashboard")
                error = "The username or password is incorrect."
                for bucket in buckets:
                    bucket.failures += 1
                    bucket.save()
                status = 400
    return render(
        request,
        "registration/login.html",
        {"form": form, "error": error},
        status=status,
    )
