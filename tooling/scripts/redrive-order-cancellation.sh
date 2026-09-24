#!/usr/bin/env bash
set -Eeuo pipefail

BASE_URL="${BASE_URL:-http://localhost:9080/home}"
ORDER_NUMBER="${1:-}"
REASON="${2:-}"
COMMAND_ID="${3:-}"
DRY_RUN="${DRY_RUN:-1}"

[[ -n "$ORDER_NUMBER" ]] || { echo "usage: $0 ORDER_NUMBER REASON COMMAND_ID"; exit 2; }
[[ -n "$REASON" ]] || { echo "reason is required"; exit 2; }
[[ -n "$COMMAND_ID" ]] || { echo "commandId is required and must be reused for retries of the same operator action"; exit 2; }
[[ -n "${ACCESS_TOKEN:-}" ]] || { echo "ACCESS_TOKEN is required"; exit 2; }

if [[ "$DRY_RUN" == "1" ]]; then
  echo "DRY-RUN: would redrive cancellation manual review for $ORDER_NUMBER with commandId=$COMMAND_ID"
  curl --fail --silent --show-error \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    "$BASE_URL/api/order/$ORDER_NUMBER" >/dev/null
  exit 0
fi

curl --fail --silent --show-error \
  -X POST \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "X-Redrive-Command-Id: $COMMAND_ID" \
  -H "X-Redrive-Reason: $REASON" \
  "$BASE_URL/api/order/$ORDER_NUMBER/cancellation-redrive"
echo
