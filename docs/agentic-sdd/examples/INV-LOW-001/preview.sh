#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"
FEATURE="$SCRIPT_DIR"

cd "$REPO"
echo "== Agentic SDD example preview: INV-LOW-001 =="
echo
echo "1/5 Design preflight plan (no model)"
python3 tooling/agent-harness/design.py "$FEATURE" --plan
echo
echo "2/5 Validate inactive maintained example"
python3 tooling/agent-harness/harness.py validate "$FEATURE"
echo
echo "3/5 Initially ready tasks"
python3 tooling/agent-harness/harness.py ready "$FEATURE"
echo
echo "4/5 Specialist routing"
for task in T-001 T-002 T-003; do
  echo "-- $task"
  python3 tooling/agent-harness/harness.py reviewers "$FEATURE" "$task"
done
echo
echo "5/5 Orchestration plan (side-effect free; maintained example is not an active spec)"
python3 tooling/agent-harness/orchestrate.py "$FEATURE" --plan

echo
echo "Preview complete. No agent/model execution or application-code mutation was performed."
echo "To activate: ./docs/agentic-sdd/examples/INV-LOW-001/activate.sh"
