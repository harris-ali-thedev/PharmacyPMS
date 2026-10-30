import json
import logging
import time
import uuid


class JsonFormatter(logging.Formatter):
    def format(self, record):
        return json.dumps(
            {
                "time": self.formatTime(record),
                "level": record.levelname,
                "logger": record.name,
                "message": record.getMessage(),
            }
        )


class RequestLogMiddleware:
    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        request.request_id = uuid.uuid4().hex
        started = time.monotonic()
        response = self.get_response(request)
        response["X-Request-ID"] = request.request_id
        response["Content-Security-Policy"] = (
            "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"
        )
        # Never log bodies, query strings, cookies, credentials or patient details.
        logging.getLogger("http").info(
            json.dumps(
                {
                    "request_id": request.request_id,
                    "method": request.method,
                    "route": getattr(request.resolver_match, "route", "unmatched"),
                    "status": response.status_code,
                    "duration_ms": round((time.monotonic() - started) * 1000),
                    "user_id": request.user.pk
                    if request.user.is_authenticated
                    else None,
                }
            )
        )
        return response
