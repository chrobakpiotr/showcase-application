#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

TESTS=(
  "com.cp.ecommerce.application.OrderCancellationSagaPostgresIntegrationTest"
  "com.cp.ecommerce.application.OrderCancellationRecoveryPostgresIntegrationTest"
  "com.cp.ecommerce.application.ForwardMigrationUpgradePostgresIntegrationTest"
  "com.cp.ecommerce.application.OrderCancellationMultiWorkerClaimPostgresIntegrationTest"
  "com.cp.ecommerce.application.StockReservationIdentityPostgresIntegrationTest"
  "com.cp.ecommerce.application.PaymentPartialRefundPostgresIntegrationTest"
  "com.cp.ecommerce.application.StockRetryTransactionBoundaryPostgresIntegrationTest"
  "com.cp.ecommerce.application.SagaCompensationRecoveryPostgresIntegrationTest"
  "com.cp.ecommerce.application.DurableNotificationRetryPostgresIntegrationTest"
  "com.cp.ecommerce.application.OutboxMultiWorkerClaimPostgresIntegrationTest"
  "com.cp.ecommerce.application.ReturnConcurrencyPostgresIntegrationTest"
)

RESULT_ROOT="apps/ecommerce/backend/build/test-results/test"
rm -rf "$RESULT_ROOT"

args=()
for test_name in "${TESTS[@]}"; do
  args+=(--tests "$test_name")
done

./gradlew :application:ecommerce:test "${args[@]}" -PcriticalPostgresGate=true

expected_file="$(mktemp)"
trap 'rm -f "$expected_file"' EXIT
printf '%s
' "${TESTS[@]}" > "$expected_file"

EXPECTED_FILE="$expected_file" RESULT_ROOT="$RESULT_ROOT" python3 <<'CHECK'
from pathlib import Path
import os
import xml.etree.ElementTree as ET

expected = [line.strip() for line in Path(os.environ["EXPECTED_FILE"]).read_text().splitlines() if line.strip()]
root = Path(os.environ["RESULT_ROOT"])
files = list(root.glob("TEST-*.xml"))
if not files:
    raise SystemExit("No JUnit XML results found for required Postgres gate")

by_name = {}
for file in files:
    suite = ET.parse(file).getroot()
    name = suite.attrib.get("name", "")
    by_name.setdefault(name, []).append((file, suite))

total = 0
for required in expected:
    matches = by_name.get(required, [])
    if len(matches) != 1:
        raise SystemExit(f"Required Postgres suite {required} expected exactly once, found {len(matches)}")
    file, suite = matches[0]
    tests = int(suite.attrib.get("tests", "0"))
    skipped = int(suite.attrib.get("skipped", "0"))
    failures = int(suite.attrib.get("failures", "0"))
    errors = int(suite.attrib.get("errors", "0"))
    if tests <= 0:
        raise SystemExit(f"Required Postgres suite {required} executed zero tests: {file}")
    if skipped or failures or errors:
        raise SystemExit(
            f"Required Postgres suite {required} is not clean: "
            f"tests={tests} skipped={skipped} failures={failures} errors={errors}"
        )
    total += tests

print(f"Required Postgres gate: {len(expected)} suites / {total} tests, 0 skipped, 0 failed")
CHECK
