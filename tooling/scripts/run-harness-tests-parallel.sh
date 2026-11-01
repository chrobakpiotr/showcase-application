#!/usr/bin/env bash
# Run the agent-harness test suite (tooling/agent-harness/tests) as independent unittest processes, several at a time,
# and fail unless every process exits 0 and the processes together ran exactly the tests `unittest discover` would.
#
# The suite mostly waits on file locks and git/python subprocesses, so one process per unit scales almost linearly.
# Units: one per test module; test_harness.py (a third of the suite) is split per class, and its largest class
# (HarnessTest) into chunks of CHUNK tests. Each process gets the tests directory and the harness directory on
# PYTHONPATH explicitly (single-process `discover` only gets them implicitly through earlier modules).
#
# Units whose tests hold wall time to an accepted target (EXCLUSIVE, default test_verification_benchmark: median <= 10 s
# on Linux CI) run alone after the pool has drained, as they did in the single-process run; sharing the CPU with other
# units made that benchmark measure 15.8 s once (Harness AH5-15 rehearsal, unmodified Showcase on the released side).
#
# usage: tooling/scripts/run-harness-tests-parallel.sh [<out dir>]   (JOBS=<n>, default 4; CHUNK=<n>, default 20;
#        EXCLUSIVE="<unit> ...", default test_verification_benchmark)
# Exit status: 0 only when every unit exited 0 (status 5, "no tests ran", counts as a failure) and the summed test
# count equals the discovered count. Logs of every unit are kept in <out dir>/logs.
set -euo pipefail
cd "$(dirname "$0")/../.."
tests=tooling/agent-harness/tests
out=${1:-"${RUNNER_TEMP:-/tmp}/harness-tests"}
mkdir -p "$out/logs"
export PYTHONDONTWRITEBYTECODE=1  # test_design/test_wayfinder copy the tooling tree; parallel .pyc writes raced with it
export PYTHONPATH="$tests:tooling/agent-harness${PYTHONPATH:+:$PYTHONPATH}"

python3 - "$tests" "${CHUNK:-20}" > "$out/units.txt" <<'PY'
import ast, pathlib, sys, unittest
tests, chunk = pathlib.Path(sys.argv[1]), int(sys.argv[2])
sys.path[:0] = [str(tests), str(tests.parent)]
units = []
for module in sorted(tests.glob("test_*.py")):
    name = module.stem
    if name != "test_harness":
        if unittest.defaultTestLoader.loadTestsFromName(name).countTestCases():
            units.append(name)
        else:
            print(f"not a test module (skipped): {module.name}", file=sys.stderr)
        continue
    for node in ast.parse(module.read_text()).body:
        if not isinstance(node, ast.ClassDef):
            continue
        tests_in_class = [n.name for n in node.body if isinstance(n, ast.FunctionDef) and n.name.startswith("test_")]
        if not tests_in_class:
            continue
        if node.name == "HarnessTest":
            for i in range(0, len(tests_in_class), chunk):
                units.append(" ".join(f"test_harness.{node.name}.{t}" for t in tests_in_class[i:i + chunk]))
        else:
            units.append(f"test_harness.{node.name}")
print("\n".join(units))
PY
expected=$(python3 -c "
import sys, unittest; sys.path[:0] = ['$tests', 'tooling/agent-harness']
print(unittest.defaultTestLoader.discover('$tests').countTestCases())")
echo "units: $(wc -l < "$out/units.txt"), tests discovered: $expected, jobs: ${JOBS:-4}"

run_unit() {  # $1 unit (one or more unittest names), $2 out dir
  log="$2/logs/$(printf '%s' "$1" | cut -c1-120 | tr ' /' '+_').log"
  # shellcheck disable=SC2086
  python3 -m unittest $1 > "$log" 2>&1; status=$?
  printf '%s\t%s\t%s\n' "$status" "$log" "$1"
}
export -f run_unit
printf '%s\n' ${EXCLUSIVE-test_verification_benchmark} > "$out/exclusive-names.txt"
grep -vxF -f "$out/exclusive-names.txt" "$out/units.txt" > "$out/pool.txt" || true
grep -xF -f "$out/exclusive-names.txt" "$out/units.txt" > "$out/exclusive.txt" || true
echo "pool units: $(grep -c . "$out/pool.txt" || true), run alone afterwards: $(tr '\n' ' ' < "$out/exclusive.txt")"
xargs -P "${JOBS:-4}" -d '\n' -I{} bash -c 'run_unit "$1" "$2"' _ {} "$out" < "$out/pool.txt" > "$out/exit-codes.tsv"
while IFS= read -r unit; do  # in a child shell, as under xargs: no errexit, so a failing unit is recorded, not fatal
  bash -c 'run_unit "$1" "$2"' _ "$unit" "$out" >> "$out/exit-codes.tsv"
done < "$out/exclusive.txt"

failed=$(awk -F'\t' '$1 != 0' "$out/exit-codes.tsv")
ran=$(cat "$out"/logs/*.log | awk '/^Ran [0-9]+ test/ { n += $2 } END { print n + 0 }')
cat "$out"/logs/*.log | awk '/^(OK|FAILED)( \(|$)/ { while (match($0, /(failures|errors|skipped)=[0-9]+/)) {
    split(substr($0, RSTART, RLENGTH), kv, "="); s[kv[1]] += kv[2]; $0 = substr($0, RSTART + RLENGTH) } }
  END { printf "failures: %d, errors: %d, skipped: %d\n", s["failures"], s["errors"], s["skipped"] }'
echo "tests ran: $ran (discovered: $expected); units with non-zero exit: $(printf '%s' "$failed" | grep -c . || true)"
if [ -n "$failed" ]; then
  diag=$(printf 'uid=%s home=%s cwd=%s owner(cwd)=%s owner(.git)=%s safe.directory=[%s] common-dir: %s' \
    "$(id -u)" "$HOME" "$PWD" "$(stat -c %u "$PWD")" "$(stat -c %u "$PWD/.git")" \
    "$(git config --global --get-all safe.directory 2>&1 | tr '\n' ' ')" \
    "$(git -C "$PWD" rev-parse --path-format=absolute --git-common-dir 2>&1 | tr '\n' ' ')")
  echo "::notice title=harness-tests environment::$diag"
  printf '%s\n' "$failed" | while IFS=$'\t' read -r status log unit; do
    echo "::group::exit $status - $unit"; tail -60 "$log"; echo "::endgroup::"
    # One annotation per failed unit (visible in the checks UI and through the API without the raw log): the failing
    # test names and the last exception/assertion line of the first failure. Newlines are encoded as %0A.
    names=$(grep -E '^(FAIL|ERROR): ' "$log" | cut -c1-200 | head -8)
    first=$(awk '/^(ERROR|FAIL): /{n++} n==1' "$log" | grep -v '^=\{20\}\|^-\{20\}' | head -40 | cut -c1-300)
    message=$(printf '%s\n%s' "$names" "$first" | cut -c1-3800 | sed ':a;N;$!ba;s/\n/%0A/g')
    echo "::error title=harness unit exit $status ($unit)::$message"
  done
  exit 1
fi
[ "$ran" = "$expected" ] || { echo "::error::ran $ran tests but discover finds $expected"; exit 1; }
