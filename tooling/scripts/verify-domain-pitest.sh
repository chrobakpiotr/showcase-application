#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

REPORT_ROOT="modules/domain/build/reports/pitest"
START_MARKER="modules/domain/build/reports/pitest.started"
VERIFIER="tooling/scripts/verify_pit_report.py"
TARGET_CLASSES="com.cp.ecommerce.domain.*"
MUTATION_THRESHOLD="100"

java_major="$(java -version 2>&1 | sed -n '1s/.*version "\([^"]*\)".*/\1/p' | cut -d. -f1)"
[[ "$java_major" == "25" ]] || {
  echo "ERROR: domain PIT evidence requires Java 25; got ${java_major:-unknown}" >&2
  exit 1
}

grep -Fq "targetClasses = ['com.cp.ecommerce.domain.*']" modules/domain/domain.gradle \
  || {
    echo "ERROR: domain PIT targetClasses drifted" >&2
    exit 1
  }
grep -Fq "mutationThreshold = 100" modules/domain/domain.gradle \
  || {
    echo "ERROR: domain PIT mutationThreshold drifted" >&2
    exit 1
  }

rm -rf "$REPORT_ROOT"
mkdir -p "$(dirname "$START_MARKER")"
: > "$START_MARKER"

./gradlew :domain:pitest

xml="$(find "$REPORT_ROOT" -type f -name 'mutations.xml' -print -quit)"
html="$(find "$REPORT_ROOT" -type f -name 'index.html' -print -quit)"
[[ -n "$xml" ]] || {
  echo "ERROR: PIT mutations.xml not found under $REPORT_ROOT" >&2
  exit 1
}
[[ -n "$html" ]] || {
  echo "ERROR: PIT index.html not found under $REPORT_ROOT" >&2
  exit 1
}

java_version="$(java -version 2>&1 | head -n 1)"

python3 "$VERIFIER" \
  --xml "$xml" \
  --html "$html" \
  --started-after "$START_MARKER" \
  --source-sha "$(git rev-parse HEAD)" \
  --java-version "$java_version" \
  --target-classes "$TARGET_CLASSES" \
  --mutation-threshold "$MUTATION_THRESHOLD" \
  --metadata-output "$REPORT_ROOT/evidence.json"
