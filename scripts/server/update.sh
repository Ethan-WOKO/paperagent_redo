#!/usr/bin/env bash

set -Eeuo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/common.sh"

BRANCH="${BRANCH:-main}"

require_app_files
lock_deployment

cd "$APP_DIR"
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo "WARNING: tracked local changes exist; Git will preserve them or stop if they conflict."
fi

git -c http.version=HTTP/1.1 pull --ff-only origin "$BRANCH"
compose up -d --build
compose ps
