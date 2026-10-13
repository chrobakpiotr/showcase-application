#!/bin/sh
cd /workspace || exit 90
check() {
  name=$1
  shift
  printf '%s\n' "$*" > "/evidence/$name.command.txt"
  "$@" > "/evidence/$name.stdout.txt" 2> "/evidence/$name.stderr.txt"
  rc=$?
  printf '%s\n' "$rc" > "/evidence/$name.exit.txt"
  printf '%s=%s\n' "$name" "$rc"
}
check 02-validate-all python3 tooling/agent-harness/harness.py validate-all docs/specs
check 03-orchestrate-worktree python3 tooling/agent-harness/orchestrate.py docs/specs/SDD-OBS-001 --plan
check 04-verification-contract python3 tooling/agent-harness/verification_contract.py validate docs/specs/SDD-OBS-001
check 05-baseline python3 tooling/agent-harness/eval.py run --suite baseline --repeat 1
check 06-adversarial python3 tooling/agent-harness/eval.py run --suite adversarial --repeat 1
check 07-shipping-preflight python3 tooling/agent-harness/eval.py run --suite shipping-preflight --repeat 1
check 08-markdown-links python3 tooling/scripts/check_markdown_links.py
check 09-handbook-pdf python3 tooling/scripts/generate-agentic-sdd-handbook-pdf.py --check
