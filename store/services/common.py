from rest_framework.exceptions import APIException, PermissionDenied
from store.models import AuditEvent


class Conflict(APIException):
    status_code = 409
    default_code = "conflict"


def require_role(user, *roles):
    if not user.allows(*roles):
        raise PermissionDenied("Your role cannot perform this action.")


def audit(actor, action, entity, entity_id, **metadata):
    AuditEvent.objects.create(
        actor=actor,
        action=action,
        entity=entity,
        entity_id=str(entity_id),
        metadata=metadata,
    )
