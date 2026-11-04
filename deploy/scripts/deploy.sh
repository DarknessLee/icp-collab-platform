#!/usr/bin/env bash
# =============================================================================
# deploy.sh - one-command Docker Compose deployment for the ICP platform.
#
#   ./scripts/deploy.sh              # build images + start + wait for health
#   ./scripts/deploy.sh --offline    # load pre-exported images, no rebuild
#
# NOT TESTED ON THE DEV MACHINE (no Docker available); the compose file is
# delivered for review. See deploy/README.md for the manual steps.
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${DEPLOY_DIR}"

OFFLINE=0
for arg in "$@"; do
  case "${arg}" in
    --offline) OFFLINE=1 ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    *) echo "unknown argument: ${arg}" >&2; exit 2 ;;
  esac
done

log()  { printf '[deploy] %s\n' "$*"; }
fail() { printf '[deploy] ERROR: %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------------------
# 1. prerequisites
# ---------------------------------------------------------------------------
log "checking docker / docker compose ..."
command -v docker >/dev/null 2>&1 || fail "docker not found; install Docker Engine first"
docker info >/dev/null 2>&1 || fail "docker daemon is not reachable (start Docker / check permissions)"
if docker compose version >/dev/null 2>&1; then
  COMPOSE="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE="docker-compose"
else
  fail "docker compose (v2) or docker-compose (v1) not found"
fi
log "compose command: ${COMPOSE}"

# ---------------------------------------------------------------------------
# 2. .env
# ---------------------------------------------------------------------------
if [ ! -f .env ]; then
  [ -f .env.example ] || fail ".env and .env.example are both missing"
  cp .env.example .env
  log "created .env from .env.example - EDIT THE PASSWORDS before a real deployment"
fi
set -a
# shellcheck disable=SC1091
. ./.env
set +a
log "database=${MYSQL_DATABASE:-icp_platform} app_user=${APP_DB_USER:-icp_app} frontend_port=${FRONTEND_PORT:-8080}"

# ---------------------------------------------------------------------------
# 3. images
# ---------------------------------------------------------------------------
if [ "${OFFLINE}" -eq 1 ]; then
  if [ -d images ]; then
    for f in images/*.tar; do
      [ -f "${f}" ] || continue
      log "loading image archive ${f}"
      docker load -i "${f}"
    done
  else
    log "WARN: --offline given but ./images/*.tar not found; trying to start with local images"
  fi
else
  log "building images ..."
  ${COMPOSE} build
fi

# ---------------------------------------------------------------------------
# 4. start + wait for health
# ---------------------------------------------------------------------------
log "starting stack ..."
${COMPOSE} up -d
BACKEND_SERVICE="backend"
TIMEOUT_S=600
log "waiting up to ${TIMEOUT_S}s for backend health ..."
deadline=$(( $(date +%s) + TIMEOUT_S ))
while :; do
  if command -v curl >/dev/null 2>&1; then
    http="$(curl -fsS "http://127.0.0.1:${BACKEND_PORT:-8081}/actuator/health" 2>/dev/null || true)"
  else
    http=""
  fi
  if printf '%s' "${http}" | grep -q '"status":"UP"'; then
    log "backend health: UP"
    break
  fi
  if [ "$(date +%s)" -ge "${deadline}" ]; then
    ${COMPOSE} ps || true
    ${COMPOSE} logs --tail 60 "${BACKEND_SERVICE}" || true
    fail "backend did not become healthy within ${TIMEOUT_S}s"
  fi
  sleep 5
done

# ---------------------------------------------------------------------------
# 5. summary
# ---------------------------------------------------------------------------
${COMPOSE} ps || true
cat <<EOF

[deploy] deployment finished
  Frontend : http://localhost:${FRONTEND_PORT:-8080}
  Backend  : http://localhost:${BACKEND_PORT:-8081}/actuator/health (direct, debug only)
  MySQL    : 127.0.0.1:${MYSQL_PORT:-13307} (database ${MYSQL_DATABASE:-icp_platform})
  Accounts : admin / icpadmin / auditor / exec / dept01..dept06 / staff01..staff20
  Password : Icp@2026  (initial seed data, change in production)
  Swagger  : http://localhost:${BACKEND_PORT:-8081}/swagger-ui/index.html (direct backend port)
  Logs     : ${COMPOSE} logs -f backend
  Stop     : ${COMPOSE} down
  Backup   : ./scripts/backup.sh
EOF
