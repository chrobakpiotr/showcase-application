# P-005 reconnect follow-up — BLOCKED

Status: **BLOCKED before AMQP interaction; no reconnect evidence produced.**

One bounded attempt ran on the recorded host at 2026-10-17T20:15Z. The runner created a private internal Docker network, started the pinned RabbitMQ image, and built the pinned Python probe image. The probe then failed its readiness deadline because it resolved the hard-coded DNS name `p005-rabbit-followup`, while the runner assigned the broker container the random name `p005-rabbit-49279ea241b7`. No AMQP connection was attempted.

The failure is an experiment setup defect, not a RabbitMQ behavior result. The probe did not create any channels or consumers, test same-principal reconnect, issue a stale connection DELETE, or check new-channel usability. No conclusion is drawn from this attempt.

RabbitMQ logs, probe stdout/stderr, command records and source hashes are retained alongside this report. The original runner stopped the broker and removed the network, but did not remove the stopped Rabbit container or local probe image. This cleanup gap was found during post-run inventory; both exact resources were then removed with 15-second command deadlines and recorded in [`cleanup-reconciliation.json`](cleanup-reconciliation.json). No Docker restart occurred. The later corrected attempt was separately authorized and is recorded in the adjacent run directory.

The corrected probe uses the same principal for both connections, requests an empty AMQP consumer tag and records `Basic.ConsumeOk`, creates consumers before and after reconnect, and checks the new channel after stale-ID DELETE. Its narrow observation is recorded in [`20261017T202943Z-9f3564a897-reconnect-followup/followup-report.md`](../20261017T202943Z-9f3564a897-reconnect-followup/followup-report.md). That run remains diagnostic only: it does not test principal blocking, establish settled drain, eliminate the read/delete TOCTOU gap, or qualify a fencer. The DELETE actor is `p005admin` with administrator-tagged management authority.
