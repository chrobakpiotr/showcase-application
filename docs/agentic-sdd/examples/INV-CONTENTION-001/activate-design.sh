#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"
DEST="$REPO/docs/specs/INV-CONTENTION-001"
if [[ -e "$DEST" ]]; then
  echo "ERROR: $DEST already exists; refusing to overwrite it." >&2
  exit 1
fi
mkdir -p "$DEST"
cp "$SCRIPT_DIR/spec.md" "$DEST/spec.md"
cp "$SCRIPT_DIR/plan.md" "$DEST/plan.md"
cp "$SCRIPT_DIR/design.json" "$DEST/design.json"
echo "Activated design study at docs/specs/INV-CONTENTION-001"
echo "Run: python3 tooling/agent-harness/design.py docs/specs/INV-CONTENTION-001 --provider codex --reasoning high"
echo "This study intentionally has no tasks.json; a production DAG should be created only after a human accepts a resulting plan."
