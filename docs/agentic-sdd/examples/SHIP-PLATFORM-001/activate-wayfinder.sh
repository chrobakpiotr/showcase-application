#!/usr/bin/env bash
set -euo pipefail
ROOT="$(git rev-parse --show-toplevel)"
SRC="$ROOT/docs/agentic-sdd/examples/SHIP-PLATFORM-001"
DST="$ROOT/docs/wayfinder/SHIP-PLATFORM-001"
if [[ -e "$DST" ]]; then
  echo "ERROR: refusing to overwrite existing map: $DST" >&2
  exit 1
fi
mkdir -p "$DST/decisions"
cp "$SRC/wayfinder.json" "$DST/wayfinder.json"
echo "Activated Wayfinder map at $DST"
python3 "$ROOT/tooling/agent-harness/wayfinder.py" validate "$DST"
