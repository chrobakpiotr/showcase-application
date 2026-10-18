# P-006 storage and cleanup errata — 2026-10-18

The pinned RabbitMQ image declares `VOLUME /var/lib/rabbitmq`. Prior P-006 reports saying “no volume” or “no persistent volumes” meant no explicit mount was supplied; Docker still created an anonymous volume. The prior `docker rm --force` commands did not remove that anonymous volume. The P-006 historical reports are retained; see the [volume reconciliation report](20261018T084252Z-volume-reconciliation/run-report.md) for the six volumes attributable by run timestamp and the 107 remaining unassigned dangling volumes.

The 2026-10-17 initial blocked run had two `--tmpfs` attempts, which override the image volume, followed by one writable-layer/default-volume attempt. Its run report is corrected by this erratum for the third attempt. Later P-006 runs without an explicit mount also created anonymous volumes, even though they had no host bind or named data mount.

This erratum does not change startup or fencer findings. The P-006 cookie-args-only startup reached readiness, but no plugin acceptance probe ran.

## Startup comparison interpretation

The baseline EACCES at 21:14Z and earlier successful P-005 startup used the
same recorded broker argv. The host-state difference is unknown. The
cookie-args-only run reached readiness on this host, so those arguments are a
working host-specific bypass; they do not identify the cause of EACCES and do
not establish a general RabbitMQ fix. The phrase “resolving the startup
blocker” in the immutable cookie-run report is superseded by this narrower
interpretation. Startup is not currently blocking the proposed fencer
experiment; defer further startup diagnostics unless that experiment cannot
start. No fencer check is implied by broker readiness.

Never retain cookie values in command arguments, logs, inspection output,
reports, or source. Retain only environment-variable names and redacted
metadata. Generate a fresh synthetic cookie for a later disposable run and
remove that run's Rabbit container with `docker rm --force --volumes`.
