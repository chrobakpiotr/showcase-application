#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
target=""
all_checks=false
workload_image="${SHOWCASE_WORKLOAD_IMAGE:-python@sha256:9d72651cf7018c1f6a1dd6fd02bd68286631c33620bc0f37b0675b21aab915d5}"

while (($#)); do
  case "$1" in
    --target)
      (($# >= 2)) || { echo "--target requires a value" >&2; exit 2; }
      target="$2"
      shift 2
      ;;
    --all-checks)
      all_checks=true
      shift
      ;;
    --workload-image)
      (($# >= 2)) || { echo "--workload-image requires a value" >&2; exit 2; }
      workload_image="$2"
      shift 2
      ;;
    *)
      echo "unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

if [[ "$all_checks" != true || ( "$target" != docker-desktop && "$target" != github-runner ) ]]; then
  echo "usage: $0 --target {docker-desktop|github-runner} --all-checks [--workload-image NAME@sha256:DIGEST]" >&2
  exit 2
fi

if [[ -n "${GITHUB_RUN_ID:-}" ]]; then
  job_id="${GITHUB_RUN_ID}-attempt-${GITHUB_RUN_ATTEMPT:-1}-${GITHUB_JOB:-job}"
else
  job_id="local-$(python3 -c 'import datetime,uuid; print(datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:12])')"
fi
target_id="showcase-docker-desktop-linux-guest"
if [[ "$target" == github-runner ]]; then
  target_id="showcase-github-hosted-ubuntu-runner"
fi
evidence_root="${SHOWCASE_QUALIFICATION_EVIDENCE_ROOT:-$repo_root/docs/specs/AH5-04B-QUAL-001/evidence/$target/$job_id}"

if ! docker pull "$workload_image"; then
  echo "workload image pull failed; qualification will record a non-passing preflight report" >&2
fi
export PYTHONPATH="$repo_root/tooling/agent-harness${PYTHONPATH:+:$PYTHONPATH}"
python3 -m qualification.report run \
  --target "$target" \
  --target-id "$target_id" \
  --job-id "$job_id" \
  --workload-image "$workload_image" \
  --repository-root "$repo_root" \
  --sandbox-source "$repo_root/tooling/agent-harness/verification_sandbox.py" \
  --evidence-root "$evidence_root" \
  --report "$evidence_root/report.json" \
  --capability-report "$evidence_root/capability.json"
