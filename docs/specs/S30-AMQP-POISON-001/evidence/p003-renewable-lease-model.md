# P-003 renewable-lease abstract model — 2026-10-05

The deterministic local model captures only the accepted lease state semantics:

- one installed lease may authorize multiple handler starts before its deadline;
- an expired lease rejects new starts but does not cancel active handlers;
- replayed renewal responses and responses arriving after PAUSE cannot install;
- PAUSE blocks new work, while drain completes only after active handlers finish.

Run with `python3 p003-renewable-lease-model.py`. Ten assertions pass on five
identical runs; stdout SHA-256:
`f4ee9190addb3d8ee5ac9989f52d80a0359d6a01d91403cde68c8a6da2b5db8b`.

This is an abstract sequential state model. It does not prove concurrent
linearization, cryptographic/JWT validation, HTTP behavior, actual monotonic
time or suspend handling, AMQP listener behavior, provider fencing, or capacity.
It does not clear P-003 or the S30 design gate.
