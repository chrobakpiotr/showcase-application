#!/usr/bin/env bash
set -Eeuo pipefail

BASE_URL="${BASE_URL:-http://localhost:9080/home}"
ORDER_NUMBER="${1:-}"
REASON="${2:-}"
DRY_RUN="${DRY_RUN:-1}"

[[ -n "$ORDER_NUMBER" ]] || { echo "usage: $0 ORDER_NUMBER REASON"; exit 2; }
[[ -n "$REASON" ]] || { echo "reason is required"; exit 2; }
[[ -n "${ACCESS_TOKEN:-}" ]] || { echo "ACCESS_TOKEN is required"; exit 2; }

if [[ "$DRY_RUN" == "1" ]]; then
  echo "DRY-RUN: would redrive cancellation for $ORDER_NUMBER"
  curl --fail --silent --show-error     -H "Authorization: Bearer $ACCESS_TOKEN"     "$BASE_URL/api/order/$ORDER_NUMBER" >/dev/null
  exit 0
fi

curl --fail --silent --show-error   -X POST   -H "Authorization: Bearer $ACCESS_TOKEN"   -H "X-Redrive-Reason: $REASON"   "$BASE_URL/api/order/$ORDER_NUMBER/cancel"
echo
