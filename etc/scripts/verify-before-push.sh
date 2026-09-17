#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

MODE="backend"
case "${1:-}" in
  "") ;;
  --backend) MODE="backend" ;;
  --full) MODE="full" ;;
  --e2e) MODE="e2e" ;;
  -h|--help)
    cat <<'USAGE'
Usage: etc/scripts/verify-before-push.sh [--backend|--full|--e2e]

  --backend  Documentation links/punctuation, persistence XML formatting and the exact backend CI build. Default.
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

run_documentation_gate() {
  echo "==> Running Markdown link checker tests"
  python3 -m unittest discover -s etc/scripts/tests -p 'test_check_markdown_links.py' -v

  echo "==> Checking repository-local Markdown links"
  python3 etc/scripts/check_markdown_links.py
}

run_backend_ci_gate() {
  echo "==> Checking Java formatting"
  ./gradlew spotlessJavaCheck --no-configuration-cache --no-parallel

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
  local frontend_dir="apps/ecommerce/frontend"
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
  local frontend_dir="apps/ecommerce/frontend"
  local compose_file="infra/docker/e2e/docker-compose.yml"
  local project_name="showcase-e2e-local-$$"

  echo "==> Starting disposable Docker Compose stack for Playwright"
  docker compose -p "$project_name" -f "$compose_file" up -d --build
  trap "docker compose -p '$project_name' -f '$compose_file' down -v --remove-orphans" EXIT

  echo "==> Waiting for E2E app health"
  local status="starting"
  local app_id=""
  for _ in $(seq 1 30); do
    app_id=$(docker compose -p "$project_name" -f "$compose_file" ps -q app)
    status=$(docker inspect --format='{{.State.Health.Status}}' "$app_id" 2>/dev/null || echo starting)
    echo "app health: $status"
    if [[ "$status" == "healthy" ]]; then
      break
    fi
    sleep 10
  done
  if [[ "$status" != "healthy" ]]; then
    echo "ERROR: E2E app did not become healthy in time" >&2
    docker compose -p "$project_name" -f "$compose_file" logs app >&2 || true
    exit 1
  fi

  (
    cd "$frontend_dir"
    npm ci
    npx playwright install --with-deps chromium
    npm run e2e
  )

  docker compose -p "$project_name" -f "$compose_file" down -v --remove-orphans
  trap - EXIT
}

echo "==> Checking repository documentation policy"
check_ascii_markdown_punctuation
run_documentation_gate
run_backend_ci_gate

if [[ "$MODE" == "full" || "$MODE" == "e2e" ]]; then
  run_frontend_ci_gate
fi

if [[ "$MODE" == "e2e" ]]; then
  run_e2e_gate
fi

echo "Pre-push verification: PASS ($MODE)"
