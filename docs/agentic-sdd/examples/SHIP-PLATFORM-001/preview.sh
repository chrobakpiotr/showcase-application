#!/usr/bin/env bash
set -euo pipefail
ROOT="$(git rev-parse --show-toplevel)"
MAP="$ROOT/docs/agentic-sdd/examples/SHIP-PLATFORM-001"
cd "$ROOT"
echo "== SHIP-PLATFORM-001 safe Agentic SDD preview =="
echo
echo "1/6 Validate schema-v2 map + Decision Ledger"
python3 tooling/agent-harness/wayfinder.py validate "$MAP"
echo
echo "2/6 Status"
python3 tooling/agent-harness/wayfinder.py status "$MAP"
echo
echo "3/6 Leverage-ranked frontier"
python3 tooling/agent-harness/wayfinder.py frontier "$MAP"
echo
echo "4/6 Terminal reconciliation must block before discovery"
set +e
python3 tooling/agent-harness/wayfinder.py reconcile "$MAP"
rc=$?
set -e
if [[ "$rc" -ne 2 ]]; then
  echo "ERROR: expected reconcile to block with exit 2, got $rc" >&2
  exit 1
fi
echo "Expected: reconciliation is blocked while initial fog is open."
echo
echo "5/6 Context trust smoke"
python3 tooling/agent-harness/trust.py classify .agent-state/control-plane/SHIP-PLATFORM-001-intent.md
python3 tooling/agent-harness/trust.py classify docs/adr/0035-shipping-fulfillment-tracking-bounded-context.md
echo
echo "6/6 No-model preview complete"
echo "No provider/model execution or application-code mutation was performed."
echo "To run safe eval manifests (writes ignored .agent-runs only):"
echo "  python3 tooling/agent-harness/eval.py run --suite shipping-preflight --repeat 1"
echo "To activate the live decision map:"
echo "  ./docs/agentic-sdd/examples/SHIP-PLATFORM-001/activate-wayfinder.sh"
