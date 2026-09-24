#!/usr/bin/env bash
set -Eeuo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO_ROOT"

TMP="$(mktemp -d)"
cleanup() { rm -rf "$TMP"; }
trap cleanup EXIT

mkdir -p "$TMP/bin"
PSQL_LOG="$TMP/psql.log"
CURL_LOG="$TMP/curl.log"
export PSQL_LOG CURL_LOG

cat > "$TMP/bin/psql" <<'MOCK'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$PSQL_LOG"
MOCK
chmod +x "$TMP/bin/psql"

cat > "$TMP/bin/curl" <<'MOCK'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$CURL_LOG"
printf '{"ok":true}\n'
MOCK
chmod +x "$TMP/bin/curl"

PATH="$TMP/bin:$PATH"

tooling/scripts/report-data-reconciliation.sh --help >/dev/null

DATABASE_URL="postgresql://user:pass@localhost:5432/db" \
DB_SCHEMA="test_db" \
tooling/scripts/report-data-reconciliation.sh >/dev/null

grep -Fq "DATA_RECONCILIATION_ISSUE order by CREATED_DATE, ISSUE_KEY" "$PSQL_LOG" \
  || { echo "missing DATA_RECONCILIATION_ISSUE report query" >&2; exit 1; }

grep -Fq "PAYMENT_RECONCILIATION_OPERATION where STATUS = 'MANUAL_REVIEW' order by CREATION_DATE, OPERATION_ID" "$PSQL_LOG" \
  || { echo "payment reconciliation report must order by CREATION_DATE" >&2; exit 1; }

if grep -Fq "PAYMENT_RECONCILIATION_OPERATION where STATUS = 'MANUAL_REVIEW' order by CREATED_DATE" "$PSQL_LOG"; then
  echo "stale CREATED_DATE reference remains in payment reconciliation report" >&2
  exit 1
fi

grep -Fq "OUTBOX_EVENT where STATUS = 'MANUAL_REVIEW' order by CREATED_DATE, ID" "$PSQL_LOG" \
  || { echo "missing cancellation manual-review report query" >&2; exit 1; }

: > "$CURL_LOG"
ACCESS_TOKEN="token" DRY_RUN=1 \
  tooling/scripts/redrive-order-cancellation.sh ORDER-123 "incident-456" >/dev/null

grep -Fq "http://localhost:9080/home/api/order/ORDER-123" "$CURL_LOG" \
  || { echo "default redrive URL must include port 9080 and /home context path" >&2; exit 1; }

if grep -Fq -- "-X POST" "$CURL_LOG"; then
  echo "DRY_RUN must not perform POST" >&2
  exit 1
fi

: > "$CURL_LOG"
ACCESS_TOKEN="token" DRY_RUN=0 \
  tooling/scripts/redrive-order-cancellation.sh ORDER-123 "incident-456" >/dev/null

grep -Fq -- "-X POST" "$CURL_LOG" \
  || { echo "live legacy cancellation path must issue POST" >&2; exit 1; }

grep -Fq "X-Redrive-Reason: incident-456" "$CURL_LOG" \
  || { echo "live legacy cancellation path must carry X-Redrive-Reason" >&2; exit 1; }

grep -Fq "http://localhost:9080/home/api/order/ORDER-123/cancel" "$CURL_LOG" \
  || { echo "live legacy cancellation URL is wrong" >&2; exit 1; }

echo "Operator recovery tooling smoke: PASS"
