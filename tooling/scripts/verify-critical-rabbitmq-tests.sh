#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

python3 -m unittest tooling/scripts/tests/test_verify_critical_rabbitmq_results.py -v

MANIFEST="tooling/quality/critical-rabbitmq-manifest.json"
VERIFIER="tooling/scripts/verify_critical_rabbitmq_results.py"
RESULT_ROOT="apps/ecommerce/backend/build/critical-rabbitmq-results"
START_MARKER="apps/ecommerce/backend/build/critical-rabbitmq.started"
SOURCE_ROOT="apps/ecommerce/backend/src/test/java"
EVIDENCE="$RESULT_ROOT/evidence.json"

rm -rf "$RESULT_ROOT"
mkdir -p "$(dirname "$START_MARKER")"
: > "$START_MARKER"

suite="$(python3 "$VERIFIER" --manifest "$MANIFEST" --list-suites)"

./gradlew :application:ecommerce:test \
  --tests "$suite" \
  -PcriticalRabbitGate=true

python3 "$VERIFIER" \
  --manifest "$MANIFEST" \
  --results "$RESULT_ROOT" \
  --started-after "$START_MARKER" \
  --source-root "$SOURCE_ROOT" \
  --source-sha "$(git rev-parse HEAD)" \
  --evidence-output "$EVIDENCE"
