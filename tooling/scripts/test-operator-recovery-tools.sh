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

grep -Fq "PAYMENT_RECONCILIATION_OPERATION where STATUS = 'MANUAL_REVIEW' order by CREATION_DATE, OPERATION_ID" "$PSQL_LOG" \
  || { echo "payment reconciliation report must order by CREATION_DATE" >&2; exit 1; }

: > "$CURL_LOG"
ACCESS_TOKEN="token" DRY_RUN=1 \
  tooling/scripts/redrive-order-cancellation.sh ORDER-123 "incident-456" "cmd-001" >/dev/null

grep -Fq "http://localhost:9080/home/api/order/ORDER-123" "$CURL_LOG" \
  || { echo "DRY_RUN must verify the order through the local /home context" >&2; exit 1; }

if grep -Fq -- "-X POST" "$CURL_LOG"; then
  echo "DRY_RUN must never mutate state" >&2
  exit 1
fi

: > "$CURL_LOG"
ACCESS_TOKEN="token" DRY_RUN=0 \
  tooling/scripts/redrive-order-cancellation.sh ORDER-123 "incident-456" "cmd-001" >/dev/null

grep -Fq -- "-X POST" "$CURL_LOG" \
  || { echo "live redrive must POST" >&2; exit 1; }

grep -Fq "X-Redrive-Reason: incident-456" "$CURL_LOG" \
  || { echo "live redrive must carry durable reason" >&2; exit 1; }

grep -Fq "X-Redrive-Command-Id: cmd-001" "$CURL_LOG" \
  || { echo "live redrive must carry stable commandId" >&2; exit 1; }

grep -Fq "http://localhost:9080/home/api/order/ORDER-123/cancellation-redrive" "$CURL_LOG" \
  || { echo "live redrive must use the dedicated admin endpoint" >&2; exit 1; }

if grep -Eq '(^|[[:space:]])http://localhost:9080/home/api/order/ORDER-123/cancel([[:space:]]|$)' "$CURL_LOG"; then
  echo "live redrive must not call the ordinary cancellation endpoint" >&2
  exit 1
fi

echo "Operator recovery tooling smoke: PASS"
