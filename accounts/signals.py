from django.contrib.auth.signals import user_logged_in, user_logged_out
from django.dispatch import receiver


@receiver(user_logged_in)
def login_audit(sender, request, user, **kwargs):
    from store.services.common import audit

    audit(user, "auth.signed_in", "user", user.pk)


@receiver(user_logged_out)
def logout_audit(sender, request, user, **kwargs):
    if user:
        from store.services.common import audit

        audit(user, "auth.signed_out", "user", user.pk)
