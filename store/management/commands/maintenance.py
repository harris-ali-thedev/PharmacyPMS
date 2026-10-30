from datetime import timedelta
from django.contrib.sessions.models import Session
from django.core.management.base import BaseCommand
from django.utils import timezone
from accounts.models import LoginAttempt


class Command(BaseCommand):
    help = "Remove expired sessions and stale login throttle windows; never removes business history."

    def handle(self, *args, **options):
        sessions, _ = Session.objects.filter(expire_date__lt=timezone.now()).delete()
        attempts, _ = LoginAttempt.objects.filter(
            window_start__lt=timezone.now() - timedelta(days=2)
        ).delete()
        self.stdout.write(
            f"Removed {sessions} expired sessions and {attempts} stale throttle windows."
        )
