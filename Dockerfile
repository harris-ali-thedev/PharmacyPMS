# syntax=docker/dockerfile:1
FROM python:3.12-slim-bookworm
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PIP_NO_CACHE_DIR=1
WORKDIR /app
COPY requirements.txt ./
# Optional combined CA mount for managed-cloud builds; ordinary builds use system trust.
RUN --mount=type=secret,id=proxy_ca \
    if [ -f /run/secrets/proxy_ca ]; then PIP_CERT=/run/secrets/proxy_ca pip install -r requirements.txt; else pip install -r requirements.txt; fi
COPY accounts ./accounts
COPY config ./config
COPY store ./store
COPY static ./static
COPY templates ./templates
COPY manage.py ./
RUN DEBUG=true python manage.py collectstatic --noinput && \
    useradd --uid 10001 --create-home app && chown -R app:app /app
USER app
EXPOSE 8000
CMD ["gunicorn", "config.wsgi:application", "--bind", "0.0.0.0:8000", "--workers", "3", "--timeout", "45", "--max-requests", "1000", "--max-requests-jitter", "100", "--error-logfile", "-"]
