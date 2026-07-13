#!/usr/bin/env bash

set -Eeuo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/common.sh"

require_app_files
compose ps

port="$(sed -n 's/^APP_HTTP_PORT=//p' "$ENV_FILE" | tail -n 1)"
port="${port:-18080}"
[[ "$port" =~ ^[0-9]+$ ]] || fail "APP_HTTP_PORT must be a numeric port"

echo
echo "Health check: http://127.0.0.1:${port}/actuator/health"
curl --fail --silent --show-error "http://127.0.0.1:${port}/actuator/health"
echo
