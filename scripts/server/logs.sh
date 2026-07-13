#!/usr/bin/env bash

set -Eeuo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/common.sh"

TAIL="${TAIL:-200}"
FOLLOW="${FOLLOW:-0}"
SERVICE="${1:-}"

require_app_files

args=(logs --tail "$TAIL")
if [[ "$FOLLOW" == "1" ]]; then
  args+=(-f)
fi
if [[ -n "$SERVICE" ]]; then
  args+=("$SERVICE")
fi

compose "${args[@]}"
