#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

TESTS=(
  "com.cp.ecommerce.application.OrderCancellationSagaPostgresIntegrationTest"
  "com.cp.ecommerce.application.StockReservationIdentityPostgresIntegrationTest"
  "com.cp.ecommerce.application.PaymentPartialRefundPostgresIntegrationTest"
  "com.cp.ecommerce.application.StockRetryTransactionBoundaryPostgresIntegrationTest"
  "com.cp.ecommerce.application.SagaCompensationRecoveryPostgresIntegrationTest"
  "com.cp.ecommerce.application.DurableNotificationRetryPostgresIntegrationTest"
  "com.cp.ecommerce.application.OutboxMultiWorkerClaimPostgresIntegrationTest"
  "com.cp.ecommerce.application.ReturnConcurrencyPostgresIntegrationTest"
)

args=()
for test_name in "${TESTS[@]}"; do
  args+=(--tests "$test_name")
done

./gradlew :application:ecommerce:test "${args[@]}" \
  --rerun-tasks \
  --no-configuration-cache \
  --no-parallel

python3 <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path("apps/ecommerce/backend/build/test-results/test")
files = list(root.glob("TEST-*.xml"))

if not files:
    raise SystemExit("No JUnit XML results found for required Postgres gate")

executed = 0
skipped = 0
failed = 0

for file in files:
    suite = ET.parse(file).getroot()
    name = suite.attrib.get("name", "")
    if "PostgresIntegrationTest" not in name and "MultiWorkerClaim" not in name:
        continue

    executed += int(suite.attrib.get("tests", "0"))
    skipped += int(suite.attrib.get("skipped", "0"))
    failed += int(suite.attrib.get("failures", "0"))
    failed += int(suite.attrib.get("errors", "0"))

if executed == 0:
    raise SystemExit("Required Postgres tests did not execute")
if skipped:
    raise SystemExit(f"Required Postgres gate has {skipped} skipped test(s)")
if failed:
    raise SystemExit(f"Required Postgres gate has {failed} failed/error test(s)")

print(f"Required Postgres gate: {executed} tests executed, 0 skipped, 0 failed")
PY
