#!/usr/bin/env bash
# INF-007 isolated PostgreSQL restore helper.
# Fail-closed: restores only into a brand-new database on a local isolated instance.
#
# Limits:
# - CONFIRM_* flags are operator acknowledgements, not technical isolation proofs.
# - ISOLATED_RESTORE_VOLUME is an operator-declared name; this script cannot verify
#   which Docker volume a live PostgreSQL process actually mounts.
# - This script never creates/deletes/changes Docker containers or volumes.
# - Requires separate explicit approval before any real run.
set -euo pipefail

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "ERROR: ${name} is required." >&2
    exit 1
  fi
}

require_true() {
  local name="$1"
  if [[ "${!name:-}" != "true" ]]; then
    echo "ERROR: ${name} must be exactly true (operator confirmation)." >&2
    exit 1
  fi
}

is_safe_identifier() {
  local value="$1"
  [[ "${value}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]
}

require_env BACKUP_FILE
require_env ISOLATED_RESTORE_HOST
require_env ISOLATED_RESTORE_PORT
require_env ISOLATED_RESTORE_DB
require_env ISOLATED_RESTORE_USER
require_env ISOLATED_RESTORE_PASSWORD
require_env ISOLATED_RESTORE_VOLUME
require_env PRODUCTION_POSTGRES_DB
require_env PRODUCTION_POSTGRES_VOLUME

require_true CONFIRM_ISOLATED_RESTORE
require_true CONFIRM_APPLICATION_DISCONNECTED
require_true CONFIRM_SEPARATE_VOLUME

checksum_file="${BACKUP_FILE}.sha256"

if [[ ! -f "${BACKUP_FILE}" ]]; then
  echo "ERROR: backup file not found." >&2
  exit 1
fi

if [[ ! -f "${checksum_file}" ]]; then
  echo "ERROR: checksum file not found: ${checksum_file}" >&2
  exit 1
fi

if ! is_safe_identifier "${ISOLATED_RESTORE_DB}"; then
  echo "ERROR: ISOLATED_RESTORE_DB must be a simple identifier." >&2
  exit 1
fi

if ! is_safe_identifier "${PRODUCTION_POSTGRES_DB}"; then
  echo "ERROR: PRODUCTION_POSTGRES_DB must be a simple identifier." >&2
  exit 1
fi

if [[ "${ISOLATED_RESTORE_DB}" == "${PRODUCTION_POSTGRES_DB}" ]]; then
  echo "ERROR: restore DB name equals production DB name." >&2
  exit 1
fi

if [[ "${PRODUCTION_POSTGRES_VOLUME}" != "postgres-prod-data" ]]; then
  echo "ERROR: PRODUCTION_POSTGRES_VOLUME must be declared as postgres-prod-data." >&2
  exit 1
fi

if [[ -z "${ISOLATED_RESTORE_VOLUME}" ]]; then
  echo "ERROR: ISOLATED_RESTORE_VOLUME is required." >&2
  exit 1
fi

if [[ "${ISOLATED_RESTORE_VOLUME}" == "${PRODUCTION_POSTGRES_VOLUME}" \
   || "${ISOLATED_RESTORE_VOLUME}" == "postgres-prod-data" \
   || "${ISOLATED_RESTORE_VOLUME}" == *postgres-prod-data* ]]; then
  echo "ERROR: postgres-prod-data must never be used as restore target." >&2
  exit 1
fi

if [[ "${ISOLATED_RESTORE_HOST}" != "localhost" \
   && "${ISOLATED_RESTORE_HOST}" != "127.0.0.1" ]]; then
  echo "ERROR: isolated restore host must be localhost or 127.0.0.1." >&2
  exit 1
fi

if [[ "${ISOLATED_RESTORE_PORT}" == "5432" ]]; then
  echo "ERROR: refusing production-default PostgreSQL port 5432." >&2
  exit 1
fi

if [[ ! "${ISOLATED_RESTORE_PORT}" =~ ^[0-9]+$ ]] \
   || [[ "${ISOLATED_RESTORE_PORT}" -lt 1 ]] \
   || [[ "${ISOLATED_RESTORE_PORT}" -gt 65535 ]]; then
  echo "ERROR: ISOLATED_RESTORE_PORT is invalid." >&2
  exit 1
fi

SHA256SUM_BIN="${SHA256SUM_BIN:-sha256sum}"

expected_checksum="$(awk '{print $1}' "${checksum_file}")"
if [[ -z "${expected_checksum}" || ! "${expected_checksum}" =~ ^[a-fA-F0-9]{64}$ ]]; then
  echo "ERROR: checksum file format is invalid." >&2
  exit 1
fi

actual_checksum="$("${SHA256SUM_BIN}" "${BACKUP_FILE}" | awk '{print $1}')"
if [[ "${expected_checksum}" != "${actual_checksum}" ]]; then
  echo "ERROR: checksum verification failed." >&2
  exit 1
fi

echo "Checksum verified before any database operation."
echo "Restoring into a brand-new isolated database only."
echo "host=${ISOLATED_RESTORE_HOST} port=${ISOLATED_RESTORE_PORT} db=${ISOLATED_RESTORE_DB}"
echo "volume(declared)=${ISOLATED_RESTORE_VOLUME}"
echo "NOTE: volume name is operator-declared; mount attachment is not proven by this script."

export PGPASSWORD="${ISOLATED_RESTORE_PASSWORD}"
cleanup() {
  unset PGPASSWORD
}
trap cleanup EXIT

PSQL_BIN="${PSQL_BIN:-psql}"
PG_RESTORE_BIN="${PG_RESTORE_BIN:-pg_restore}"

exists="$(
  "${PSQL_BIN}" \
    --host="${ISOLATED_RESTORE_HOST}" \
    --port="${ISOLATED_RESTORE_PORT}" \
    --username="${ISOLATED_RESTORE_USER}" \
    --dbname=postgres \
    --tuples-only \
    --no-align \
    --command="SELECT 1 FROM pg_database WHERE datname='${ISOLATED_RESTORE_DB}'"
)"

if [[ "${exists}" == "1" ]]; then
  echo "ERROR: target database already exists; refusing overwrite." >&2
  exit 1
fi

"${PSQL_BIN}" \
  --host="${ISOLATED_RESTORE_HOST}" \
  --port="${ISOLATED_RESTORE_PORT}" \
  --username="${ISOLATED_RESTORE_USER}" \
  --dbname=postgres \
  --command="CREATE DATABASE \"${ISOLATED_RESTORE_DB}\""

if ! "${PG_RESTORE_BIN}" \
  --no-owner \
  --no-acl \
  --host="${ISOLATED_RESTORE_HOST}" \
  --port="${ISOLATED_RESTORE_PORT}" \
  --username="${ISOLATED_RESTORE_USER}" \
  --dbname="${ISOLATED_RESTORE_DB}" \
  "${BACKUP_FILE}"; then
  echo "ERROR: pg_restore failed after creating isolated DB ${ISOLATED_RESTORE_DB}." >&2
  echo "NOTE: a partially restored isolated DB may remain; automatic deletion is not performed." >&2
  exit 1
fi

echo "Running post-restore checks on the isolated database only."
"${PSQL_BIN}" \
  --host="${ISOLATED_RESTORE_HOST}" \
  --port="${ISOLATED_RESTORE_PORT}" \
  --username="${ISOLATED_RESTORE_USER}" \
  --dbname="${ISOLATED_RESTORE_DB}" \
  --command="
SELECT 'apartment' AS table_name, COUNT(*) AS row_count FROM apartment
UNION ALL
SELECT 'member', COUNT(*) FROM member
UNION ALL
SELECT 'study', COUNT(*) FROM study
UNION ALL
SELECT 'field_session', COUNT(*) FROM field_session
UNION ALL
SELECT 'field_record', COUNT(*) FROM field_record
UNION ALL
SELECT 'report', COUNT(*) FROM report;
SELECT COUNT(*) AS flyway_rows FROM flyway_schema_history;
"

echo "Isolated restore completed."
echo "Never use this script against production PostgreSQL or postgres-prod-data."
