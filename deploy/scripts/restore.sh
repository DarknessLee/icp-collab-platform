#!/usr/bin/env bash
# =============================================================================
# restore.sh - restore a backup produced by backup.sh (database + attachments).
#
#   ./scripts/restore.sh backups/20261104-120000
#
# Steps: stop backend -> recreate schema -> import db.sql -> restore volume ->
#        start backend -> wait for /actuator/health UP.
#
# NOT TESTED ON THE DEV MACHINE (no Docker). The native Windows equivalent is
# scripts/restore.ps1, which is fully tested (see docs/13 M4 report).
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${DEPLOY_DIR}"

SRC="${1:-}"
[ -n "${SRC}" ] || { echo "usage: $0 <backup-dir>" >&2; exit 2; }
[ -d "${SRC}" ] || { echo "ERROR: backup dir not found: ${SRC}" >&2; exit 2; }
SRC="$(cd "${SRC}" && pwd)"
[ -s "${SRC}/db.sql" ] || { echo "ERROR: ${SRC}/db.sql missing/empty" >&2; exit 2; }
[ -s "${SRC}/attachments.tgz" ] || { echo "ERROR: ${SRC}/attachments.tgz missing/empty" >&2; exit 2; }

log()  { printf '[restore] %s\n' "$*"; }
fail() { printf '[restore] ERROR: %s\n' "$*" >&2; exit 1; }

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
BACKEND_PORT="${BACKEND_PORT:-8081}"

log "restoring ${SRC} into ${DB_NAME}"
${COMPOSE} ps mysql >/dev/null 2>&1 || fail "stack is not running; start it with docker compose up -d first"

# 1. stop backend so no connection survives the schema recreation
log "stopping backend ..."
${COMPOSE} stop backend >/dev/null

# 2. recreate database and import the dump
log "recreating database and importing db.sql ..."
${COMPOSE} exec -T mysql sh -c \
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE IF EXISTS \`$MYSQL_DATABASE\`; CREATE DATABASE \`$MYSQL_DATABASE\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci; GRANT ALL PRIVILEGES ON \`$MYSQL_DATABASE\`.* TO \"$MYSQL_USER\"@\"%\"; FLUSH PRIVILEGES;"'
${COMPOSE} exec -T mysql sh -c \
  'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' < "${SRC}/db.sql"

# 3. restore attachments volume
log "restoring attachments volume ${VOLUME} ..."
docker run --rm \
  -v "${VOLUME}:/data" \
  -v "${SRC}:/backup:ro" \
  alpine:3.20 sh -c 'rm -rf /data/* && tar xzf /backup/attachments.tgz -C /data'

# 4. start backend and wait for health
log "starting backend ..."
${COMPOSE} start backend >/dev/null
deadline=$(( $(date +%s) + 300 ))
while :; do
  if command -v curl >/dev/null 2>&1 && curl -fsS "http://127.0.0.1:${BACKEND_PORT}/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
    log "backend health: UP"
    break
  fi
  if [ "$(date +%s)" -ge "${deadline}" ]; then
    ${COMPOSE} logs --tail 60 backend || true
    fail "backend did not become healthy after restore"
  fi
  sleep 5
done

log "done. Verify with: curl -s http://127.0.0.1:${BACKEND_PORT}/actuator/health"
