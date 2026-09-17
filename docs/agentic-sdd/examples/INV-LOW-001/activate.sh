#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"
DEST="$REPO/docs/specs/INV-LOW-001"

if [[ -e "$DEST" ]]; then
  echo "ERROR: $DEST already exists; refusing to overwrite it." >&2
  exit 1
fi

mkdir -p "$DEST"
cp "$SCRIPT_DIR/spec.md" "$DEST/spec.md"
cp "$SCRIPT_DIR/plan.md" "$DEST/plan.md"
cp "$SCRIPT_DIR/tasks.json" "$DEST/tasks.json"
cp "$SCRIPT_DIR/design.json" "$DEST/design.json"
mkdir -p "$DEST/evidence" "$DEST/packets"

cd "$REPO"
echo "Activated example at docs/specs/INV-LOW-001"
echo
echo "The active feature now requires a fresh design gate before orchestration."
echo "Preview design stages:"
echo "  python3 etc/agent-harness/design.py docs/specs/INV-LOW-001 --plan"
echo
echo "Live preflight (example with Codex):"
echo "  python3 etc/agent-harness/design.py docs/specs/INV-LOW-001 --provider codex --reasoning high"
echo
echo "Only after design/gate.json is PASS:"
echo "  python3 etc/agent-harness/harness.py validate docs/specs/INV-LOW-001"
echo "  python3 etc/agent-harness/orchestrate.py docs/specs/INV-LOW-001 --plan"
echo "  python3 etc/agent-harness/orchestrate.py docs/specs/INV-LOW-001 --provider codex --review-provider claude --evaluator-provider claude --reasoning high"
