#!/usr/bin/env bash
# =============================================================================
# backup.sh - database dump + attachment volume archive for the ICP platform.
#
#   ./scripts/backup.sh              # backup to deploy/backups/<timestamp>/
#   ./scripts/backup.sh --keep 5     # keep only the latest 5 backup folders
#
# Produces:
#   backups/<ts>/db.sql              mysqldump (--single-transaction, utf8mb4)
#   backups/<ts>/attachments.tgz     /app/data/files volume archive
#   backups/<ts>/manifest.json       timestamp, sizes, sha256, key row counts
#
# NOT TESTED ON THE DEV MACHINE (no Docker). The native Windows equivalent is
# scripts/backup.ps1, which is fully tested.
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${DEPLOY_DIR}"

KEEP=10
while [ $# -gt 0 ]; do
  case "$1" in
    --keep) KEEP="${2:-10}"; shift 2 ;;
    -h|--help) sed -n '2,16p' "$0"; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

log()  { printf '[backup] %s\n' "$*"; }
fail() { printf '[backup] ERROR: %s\n' "$*" >&2; exit 1; }

command -v docker >/dev/null 2>&1 || fail "docker not found"
[ -f .env ] || fail ".env not found in ${DEPLOY_DIR}"
set -a
# shellcheck disable=SC1091
. ./.env
set +a

if docker compose version >/dev/null 2>&1; then COMPOSE="docker compose"; else COMPOSE="docker-compose"; fi
PROJECT="${COMPOSE_PROJECT_NAME:-$(sed -n 's/^name:[[:space:]]*//p' docker-compose.yml | head -n1)}"
PROJECT="${PROJECT:-icp-platform}"
DB_NAME="${MYSQL_DATABASE:-icp_platform}"
VOLUME="${PROJECT}_app-files"

TS="$(date +%Y%m%d-%H%M%S)"
DEST="${DEPLOY_DIR}/backups/${TS}"
mkdir -p "${DEST}"
log "target ${DEST}"

# 1. database dump (inside the mysql container, so no host client is required)
log "dumping database ${DB_NAME} ..."
${COMPOSE} exec -T mysql sh -c \
  'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --no-tablespaces --routines --triggers --default-character-set=utf8mb4 "$MYSQL_DATABASE"' \
  > "${DEST}/db.sql"
[ -s "${DEST}/db.sql" ] || fail "db.sql is empty"

# 2. attachments volume archive
log "archiving volume ${VOLUME} ..."
docker run --rm \
  -v "${VOLUME}:/data:ro" \
  -v "${DEST}:/backup" \
  alpine:3.20 tar czf /backup/attachments.tgz -C /data .
[ -s "${DEST}/attachments.tgz" ] || fail "attachments.tgz is empty"

# 3. manifest with sizes, checksums and key row counts
log "writing manifest.json ..."
COUNTS="$(${COMPOSE} exec -T mysql sh -c \
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B "$MYSQL_DATABASE" -e "
     SELECT CONCAT(\"sys_user=\", COUNT(*)) FROM sys_user WHERE deleted=0
     UNION ALL SELECT CONCAT(\"icp_policy=\", COUNT(*)) FROM icp_policy WHERE deleted=0
     UNION ALL SELECT CONCAT(\"icp_risk=\", COUNT(*)) FROM icp_risk WHERE deleted=0
     UNION ALL SELECT CONCAT(\"icp_defect=\", COUNT(*)) FROM icp_defect WHERE deleted=0
     UNION ALL SELECT CONCAT(\"coll_task=\", COUNT(*)) FROM coll_task WHERE deleted=0
     UNION ALL SELECT CONCAT(\"wf_business_link=\", COUNT(*)) FROM wf_business_link;" 2>/dev/null' || true)"
python3 - "${DEST}" "${TS}" "${DB_NAME}" "${COUNTS}" <<'PY'
import hashlib, json, os, sys
dest, ts, db, counts = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]

def sha256(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()

rows = {}
for line in counts.splitlines():
    line = line.strip()
    if '=' in line:
        k, v = line.split('=', 1)
        rows[k] = int(v) if v.isdigit() else v

manifest = {
    'timestamp': ts,
    'database': db,
    'db_sql_bytes': os.path.getsize(os.path.join(dest, 'db.sql')),
    'db_sql_sha256': sha256(os.path.join(dest, 'db.sql')),
    'attachments_bytes': os.path.getsize(os.path.join(dest, 'attachments.tgz')),
    'attachments_sha256': sha256(os.path.join(dest, 'attachments.tgz')),
    'row_counts': rows,
}
with open(os.path.join(dest, 'manifest.json'), 'w', encoding='utf-8') as f:
    json.dump(manifest, f, ensure_ascii=False, indent=2)
print(json.dumps(manifest, ensure_ascii=False, indent=2))
PY

# 4. retention: keep the newest ${KEEP} folders
if [ "${KEEP}" -gt 0 ]; then
  mapfile -t OLD < <(ls -1dt "${DEPLOY_DIR}"/backups/*/ 2>/dev/null | tail -n +$((KEEP + 1)) || true)
  for d in "${OLD[@]:-}"; do
    [ -n "${d}" ] || continue
    log "removing old backup ${d}"
    rm -rf "${d}"
  done
fi

log "done: ${DEST}"
