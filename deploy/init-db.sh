#!/bin/sh
set -eu
case "$APP_DB_PASSWORD" in ''|*[!0-9a-f]*) echo 'APP_DB_PASSWORD must be a generated lowercase hex string' >&2; exit 1;; esac
[ "${#APP_DB_PASSWORD}" -ge 32 ] || { echo 'APP_DB_PASSWORD must have at least 32 hex characters' >&2; exit 1; }
# Runs once in the official PostgreSQL entrypoint. Passwords travel on stdin, not CLI arguments.
psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set=ON_ERROR_STOP=1 <<SQL
CREATE ROLE medicare LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD '$APP_DB_PASSWORD';
GRANT CONNECT ON DATABASE medicare TO medicare;
GRANT USAGE, CREATE ON SCHEMA public TO medicare;
SQL
