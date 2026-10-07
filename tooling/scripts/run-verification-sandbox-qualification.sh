#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

python_bin="${PYTHON:-python3.13}"
if ! command -v "$python_bin" >/dev/null 2>&1; then
  python_bin="python3"
fi

target="${QUALIFICATION_TARGET:-showcase-docker-desktop}"
job_id="${QUALIFICATION_JOB_ID:-}"
workload_image="${QUALIFICATION_WORKLOAD_IMAGE:-python:3.12@sha256:4d1caded1f729ae443eb803f26ffde7b61e696aeaef62f099abb6dd6b14257c7}"
harness_cli="${QUALIFICATION_HARNESS_CLI:-agent-harness}"
output_root="${QUALIFICATION_OUTPUT_ROOT:-artifacts/verification-sandbox-qualification/${job_id:-local-$(date -u +%Y%m%dT%H%M%SZ)}}"

args=(
  --target "$target"
  --workload-image "$workload_image"
  --harness-cli "$harness_cli"
  --evidence-root "$output_root/evidence"
  --report "$output_root/report.json"
  --capability-report "$output_root/capability.json"
)
if [[ -n "$job_id" ]]; then
  args+=(--job-id "$job_id")
fi
if [[ -n "${QUALIFICATION_HOST:-}" ]]; then
  args+=(--host "$QUALIFICATION_HOST")
fi

PYTHONPATH="$repo_root/tooling/agent-harness${PYTHONPATH:+:$PYTHONPATH}" \
  "$python_bin" -m qualification.report "${args[@]}"
