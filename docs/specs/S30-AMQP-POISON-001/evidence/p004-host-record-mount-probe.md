# S30-06 P-004 host-control propagation prototype — 2026-10-06

Status: **bounded local evidence; candidate A recommended for the next
architecture review, not selected as an accepted implementation design.**
The S30 design gate remains open and no consumer or restore workflow is
implemented or enabled.

## Question and setup

Does a root-updated host-control record become safely observable by a gate
process through a read-only Docker Desktop mount, and does mounting the file
itself behave differently from mounting its containing directory?

The probe ran on macOS 15.8.1 x86_64 with Docker Desktop Engine 29.8.2
(`linux/amd64`, 8 CPUs, 8,322,727,936 bytes available to Docker). It used
`python:3.13-slim`, resolved locally to
`python@sha256:3dd7cc108ec1493442514f5c2a871af6af0ec31d768ff6e378a93340c3b3db5f`.
Each disposable container mounted either the individual record file or its
directory read-only. A host writer fsynced a complete replacement file, used
`os.replace`, then fsynced the containing directory. The container polled the
mounted record every 50 ms. Two trials ran for each mount shape.

## Results

| Mount shape | Trial 1 | Trial 2 | Observation |
|---|---:|---:|---|
| Individual file bind mount | No complete generation-2 record within 4 s | No complete generation-2 record within 4 s | The container observed `{"generation":2,"state":"INHIBITED` without the closing brace/newline; it did not observe a valid replacement before the deadline. |
| Containing directory bind mount | 0.048 s | 0.045 s | The container observed the complete generation-2 `INHIBITED` record; its log showed `ACTIVE → INHIBITED`. |

The observation makes the individual-file mount unsuitable for this reference
target unless a different update/read protocol is proven. A read-only directory
mount with atomic file replacement is the simpler candidate. Gate code must
still parse each current record synchronously at permit issuance and deny on
missing, malformed, stale, or unreadable state; a watcher/cache alone is not
safe. The wrapper must wait for each issuer's exact episode acknowledgement
and each application instance's drain confirmation before restoring either
service store.

The API-only candidate has a direct lost-notification counterexample: a gate
that has not received the PAUSE can continue admitting from its old ACTIVE
state. It meets the accepted P-004 criteria only if some independent fresh
host-control check also prevents that stale admission, which makes the
read-only host record part of its admission path as well.

## Reproduction and limits

Run from the repository root:

```bash
python3 docs/specs/S30-AMQP-POISON-001/evidence/p004-host-record-mount-probe.py
python3 docs/specs/S30-AMQP-POISON-001/evidence/test_p004_restore_barrier_model.py
```

The mount probe source is in this directory. The companion deterministic state
model (`p004_restore_barrier.py` plus `test_p004_restore_barrier_model.py`)
passes seven assertions for lost/delayed notifications, exact episode
acknowledgements, stale gate restart, partial restore, unavailable control,
stale RESUME, and drain-before-release. It is only an abstract model; it does
not exercise a real permit issuer or application consumer. Model hashes at the
time of this record: `p004_restore_barrier.py`
`c71f306776046c8a9163625e7cf295722acfb877891b0b1001bf276802c99e7e` and
`test_p004_restore_barrier_model.py`
`8772c68eec9ec4a69bf771e03d5a3c58b4525715af8d3aec980a880404bea847`.

The live mount evidence is two trials per shape on one Docker Desktop host. It
does not prove propagation bounds on Linux-native hosts, multi-node or
production storage, behavior under host crash/power loss, record integrity or
anti-rollback, filesystem ACL correctness, or end-to-end permit linearization.
Root compromise, full-host rollback, direct/provider restore, and wrapper
bypass remain outside REF-Q. A fresh architecture grill and integrated
reference-target test are still required before design PASS.
