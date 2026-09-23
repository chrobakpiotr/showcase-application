#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

MANIFEST="tooling/quality/critical-postgres-manifest.json"
VERIFIER="tooling/scripts/verify_critical_postgres_results.py"
RESULT_ROOT="apps/ecommerce/backend/build/critical-postgres-results"
START_MARKER="apps/ecommerce/backend/build/critical-postgres.started"
SOURCE_ROOT="apps/ecommerce/backend/src/test/java"
EVIDENCE="$RESULT_ROOT/evidence.json"

rm -rf "$RESULT_ROOT"
mkdir -p "$(dirname "$START_MARKER")"
: > "$START_MARKER"

args=()
while IFS= read -r suite; do
  [[ -n "$suite" ]] && args+=(--tests "$suite")
done < <(python3 "$VERIFIER" --manifest "$MANIFEST" --list-suites)

[[ "${#args[@]}" -gt 0 ]] || {
  echo "ERROR: critical Postgres manifest produced no suites" >&2
  exit 1
}

if [[ "${CRITICAL_POSTGRES_BACKEND_ONLY:-false}" == "true" ]]; then
  ./gradlew :application:ecommerce:test "${args[@]}" \
    -x :adapter:ecommerce-frontend:npmInstall \
    -x :adapter:ecommerce-frontend:npm_run_build \
    -x :adapter:ecommerce-frontend:processGeneratedResources \
    -PcriticalPostgresGate=true
else
  ./gradlew :application:ecommerce:test "${args[@]}" -PcriticalPostgresGate=true
fi

python3 "$VERIFIER" \
  --manifest "$MANIFEST" \
  --results "$RESULT_ROOT" \
  --started-after "$START_MARKER" \
  --source-root "$SOURCE_ROOT" \
  --source-sha "$(git rev-parse HEAD)" \
  --evidence-output "$EVIDENCE"
