#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="${APP_DIR:-$(cd -- "$SCRIPT_DIR/../.." && pwd)}"
COMPOSE_FILE="$APP_DIR/docker-compose.prod.yml"
ENV_FILE="$APP_DIR/.env"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

require_app_files() {
  [[ -f "$COMPOSE_FILE" ]] || fail "Missing compose file: $COMPOSE_FILE"
  [[ -f "$ENV_FILE" ]] || fail "Missing environment file: $ENV_FILE"
  command -v docker >/dev/null 2>&1 || fail "Docker is not installed or not on PATH"
}

compose() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

lock_deployment() {
  if command -v flock >/dev/null 2>&1; then
    exec 9>"$APP_DIR/.paperagent-deploy.lock"
    flock -n 9 || fail "Another PaperAgent management task is already running"
  fi
}
