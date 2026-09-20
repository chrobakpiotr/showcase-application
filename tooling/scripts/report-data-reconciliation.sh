#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  cat <<'USAGE'
Usage: report-data-reconciliation.sh

Read-only operational report for reconciliation/manual-review state.

Required environment:
  DATABASE_URL  PostgreSQL connection URL

Optional environment:
  DB_SCHEMA     Database schema (default: test_db)

Options:
  -h, --help    Show this help and exit without connecting to PostgreSQL
USAGE
}

case "${1:-}" in
  -h|--help)
    usage
    exit 0
    ;;
esac

: "${DATABASE_URL:?DATABASE_URL is required, e.g. postgresql://user:pass@host:5432/db}"
DB_SCHEMA="${DB_SCHEMA:-test_db}"
command -v psql >/dev/null || { echo "psql is required" >&2; exit 2; }

echo "== Data reconciliation issues =="
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -c \
  "select ISSUE_KEY, ISSUE_TYPE, DETAILS, CREATED_DATE from ${DB_SCHEMA}.DATA_RECONCILIATION_ISSUE order by CREATED_DATE, ISSUE_KEY"
echo "== Payment reconciliation manual review =="
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -c \
  "select OPERATION_ID, ORDER_NUMBER, OPERATION_TYPE, REFUND_ID, ATTEMPTS, LAST_ERROR, CREATION_DATE from ${DB_SCHEMA}.PAYMENT_RECONCILIATION_OPERATION where STATUS = 'MANUAL_REVIEW' order by CREATED_DATE, OPERATION_ID"
echo "== Order/cancellation manual review =="
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -c \
  "select ID, ORDER_NUMBER, STATUS, LAST_ERROR, CANCELLATION_ATTEMPTS, CANCELLATION_LAST_ERROR, CREATED_DATE from ${DB_SCHEMA}.OUTBOX_EVENT where STATUS = 'MANUAL_REVIEW' order by CREATED_DATE, ID"
