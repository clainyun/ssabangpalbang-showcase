#!/usr/bin/env bash
# INF-007 PostgreSQL backup helper.
# Does not target production containers by itself. Caller must supply host/port/DB
# via environment variables and obtain explicit approval before any real run.
# This script never schedules deletions and never touches postgres-prod-data.
set -euo pipefail

# Restrict newly created files to owner read/write only.
umask 077

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "ERROR: ${name} is required." >&2
    exit 1
  fi
}

is_safe_identifier() {
  local value="$1"
  [[ "${value}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]
}

is_valid_timestamp() {
  local value="$1"
  [[ "${value}" =~ ^[0-9]{8}T[0-9]{6}Z$ ]]
}

is_forbidden_path() {
  local path="$1"
  case "${path}" in
    /|/root|/etc|/var/lib/postgresql|/var/lib/postgresql/*|*/postgres-prod-data|*/postgres-prod-data/*)
      return 0
      ;;
  esac
  return 1
}

require_mode_600() {
  local file="$1"
  if ! "${CHMOD_BIN}" 600 "${file}"; then
    echo "ERROR: failed to set owner-only permissions on backup artifact." >&2
    exit 1
  fi
}

require_env BACKUP_DIR
require_env POSTGRES_HOST
require_env POSTGRES_PORT
require_env POSTGRES_DB
require_env POSTGRES_USER
require_env POSTGRES_PASSWORD

if ! is_safe_identifier "${POSTGRES_DB}"; then
  echo "ERROR: POSTGRES_DB must be a simple PostgreSQL identifier." >&2
  exit 1
fi

if [[ ! -d "${BACKUP_DIR}" ]]; then
  echo "ERROR: BACKUP_DIR does not exist: ${BACKUP_DIR}" >&2
  exit 1
fi

if [[ ! -w "${BACKUP_DIR}" ]]; then
  echo "ERROR: BACKUP_DIR is not writable." >&2
  exit 1
fi

resolved_backup_dir="$(cd "${BACKUP_DIR}" && pwd -P)"
if is_forbidden_path "${resolved_backup_dir}"; then
  echo "ERROR: BACKUP_DIR points to a forbidden path." >&2
  exit 1
fi

if [[ "${resolved_backup_dir}" == *"/var/lib/postgresql"* ]]; then
  echo "ERROR: refusing to write backups into PostgreSQL data directories." >&2
  exit 1
fi

# Optional overrides are for isolated fake-executable tests only.
DATE_BIN="${DATE_BIN:-date}"
SHA256SUM_BIN="${SHA256SUM_BIN:-sha256sum}"
MV_BIN="${MV_BIN:-mv}"
CHMOD_BIN="${CHMOD_BIN:-chmod}"
PG_DUMP_BIN="${PG_DUMP_BIN:-pg_dump}"

timestamp="$("${DATE_BIN}" -u +%Y%m%dT%H%M%SZ)"
if ! is_valid_timestamp "${timestamp}"; then
  echo "ERROR: backup timestamp must match YYYYMMDDTHHMMSSZ." >&2
  exit 1
fi

final_file="${resolved_backup_dir}/ssabangpalbang_${POSTGRES_DB}_${timestamp}.dump"
temp_file="${final_file}.tmp"
checksum_file="${final_file}.sha256"
checksum_temp="${checksum_file}.tmp"

if [[ -e "${final_file}" \
   || -e "${temp_file}" \
   || -e "${checksum_file}" \
   || -e "${checksum_temp}" ]]; then
  echo "ERROR: refusing to overwrite existing backup artifacts." >&2
  exit 1
fi

echo "Starting PostgreSQL custom-format dump."
echo "host=${POSTGRES_HOST} port=${POSTGRES_PORT} db=${POSTGRES_DB} user=${POSTGRES_USER}"
echo "output=${final_file}"

export PGPASSWORD="${POSTGRES_PASSWORD}"
backup_succeeded=0
created_final_dump=0
created_final_checksum=0

cleanup() {
  unset PGPASSWORD
  rm -f "${temp_file}" "${checksum_temp}"
  if [[ "${backup_succeeded}" != "1" ]]; then
    if [[ "${created_final_dump}" == "1" ]]; then
      rm -f "${final_file}"
    fi
    if [[ "${created_final_checksum}" == "1" ]]; then
      rm -f "${checksum_file}"
    fi
  fi
}
trap cleanup EXIT

if ! "${PG_DUMP_BIN}" \
  --format=custom \
  --no-owner \
  --no-acl \
  --file="${temp_file}" \
  --host="${POSTGRES_HOST}" \
  --port="${POSTGRES_PORT}" \
  --username="${POSTGRES_USER}" \
  --dbname="${POSTGRES_DB}"; then
  echo "ERROR: pg_dump failed." >&2
  exit 1
fi

if [[ ! -s "${temp_file}" ]]; then
  echo "ERROR: dump file is empty." >&2
  exit 1
fi

require_mode_600 "${temp_file}"

checksum="$("${SHA256SUM_BIN}" "${temp_file}" | awk '{print $1}')"
if [[ -z "${checksum}" || "${#checksum}" -ne 64 ]]; then
  echo "ERROR: failed to compute SHA-256 checksum." >&2
  exit 1
fi

printf '%s  %s\n' "${checksum}" "$(basename "${final_file}")" > "${checksum_temp}"
require_mode_600 "${checksum_temp}"

"${MV_BIN}" "${temp_file}" "${final_file}"
created_final_dump=1
require_mode_600 "${final_file}"

if ! "${MV_BIN}" "${checksum_temp}" "${checksum_file}"; then
  echo "ERROR: failed to finalize checksum file; incomplete dump will be removed." >&2
  exit 1
fi
created_final_checksum=1
require_mode_600 "${checksum_file}"

backup_succeeded=1

echo "Backup completed."
echo "dump=${final_file}"
echo "sha256=${checksum}"
# Recommended policy (not automated here): daily backup, retain 7 days.
