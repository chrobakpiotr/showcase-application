#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

MODE="backend"
case "${1:-}" in
  "") ;;
  --backend) MODE="backend" ;;
  --full) MODE="full" ;;
  --e2e) MODE="e2e" ;;
  -h|--help)
    cat <<'USAGE'
Usage: scripts/verify-before-push.sh [--backend|--full|--e2e]

  --backend  Documentation punctuation, persistence XML formatting and the exact backend CI build. Default.
  --full     Backend checks plus Angular install, lint, unit tests and production build.
  --e2e      Full checks plus the Docker Compose Playwright end-to-end suite.
USAGE
    exit 0
    ;;
  *)
    echo "ERROR: unknown option: $1" >&2
    exit 2
    ;;
esac

if [[ ! -x ./gradlew ]]; then
  echo "ERROR: ./gradlew is missing or not executable. Run from the repository checkout." >&2
  exit 2
fi

check_ascii_markdown_punctuation() {
  python3 - <<'PY'
from pathlib import Path
import subprocess
import sys

raw = subprocess.check_output(["git", "ls-files", "-z", "*.md"])
paths = [Path(p.decode("utf-8")) for p in raw.split(b"\0") if p]
violations = []
for path in paths:
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        continue
    for line_no, line in enumerate(text.splitlines(), 1):
        if "\u2014" in line or "\u2013" in line:
            violations.append((path, line_no, line.strip()))

if violations:
    print("ERROR: long dash characters found in tracked Markdown files:", file=sys.stderr)
    for path, line_no, line in violations[:100]:
        print(f"  {path}:{line_no}: {line}", file=sys.stderr)
    if len(violations) > 100:
        print(f"  ... and {len(violations) - 100} more", file=sys.stderr)
    sys.exit(1)

print(f"ASCII punctuation check: PASS ({len(paths)} tracked Markdown files)")
PY
}

run_backend_ci_gate() {
  echo "==> Checking persistence XML formatting"
  ./gradlew :adapter:persistence:spotlessXmlCheck

  echo "==> Running the exact backend CI build command"
  ./gradlew clean build \
    -x :adapter:ecommerce-frontend:npm_run_build \
    -x :adapter:ecommerce-frontend:npm_run_test \
    -x :adapter:ecommerce-frontend:runESLintCheck \
    -x :adapter:ecommerce-frontend:runUnitTests \
    -x :adapter:ecommerce-frontend:spotlessStylingCheck \
    -x :adapter:ecommerce-frontend:spotlessStyling \
    --continue
}

run_frontend_ci_gate() {
  local frontend_dir="adapter/ecommerce-frontend"
  if [[ ! -f "$frontend_dir/package-lock.json" ]]; then
    echo "ERROR: $frontend_dir/package-lock.json is missing." >&2
    exit 2
  fi

  echo "==> Running Angular CI checks"
  (
    cd "$frontend_dir"
    npm ci
    npx ng lint
    npx ng test --no-watch --no-progress --browsers=ChromeHeadless --code-coverage
    npm run build
  )
}

run_e2e_gate() {
  local frontend_dir="adapter/ecommerce-frontend"
  echo "==> Starting Docker Compose stack for Playwright"
  docker compose up -d --build
  trap 'docker compose down' EXIT

  echo "==> Waiting for ecommerce-app health"
  local status="starting"
  for _ in $(seq 1 30); do
    status=$(docker inspect --format='{{.State.Health.Status}}' ecommerce-app 2>/dev/null || echo starting)
    echo "app health: $status"
    if [[ "$status" == "healthy" ]]; then
      break
    fi
    sleep 10
  done
  if [[ "$status" != "healthy" ]]; then
    echo "ERROR: ecommerce-app did not become healthy in time" >&2
    docker compose logs app >&2 || true
    exit 1
  fi

  (
    cd "$frontend_dir"
    npm ci
    npx playwright install --with-deps chromium
    npm run e2e
  )
}

echo "==> Checking repository punctuation policy"
check_ascii_markdown_punctuation
run_backend_ci_gate

if [[ "$MODE" == "full" || "$MODE" == "e2e" ]]; then
  run_frontend_ci_gate
fi

if [[ "$MODE" == "e2e" ]]; then
  run_e2e_gate
fi

echo "Pre-push verification: PASS ($MODE)"
