# P-005/P-006 Docker anonymous-volume reconciliation — 2026-10-18

The pinned RabbitMQ image declares `VOLUME /var/lib/rabbitmq` (recorded in `evidence/image-volume-declaration.json`). Therefore a broker `docker run` with no explicit mount still creates an anonymous volume. Prior reports saying “no volume(s)” meant no user-specified bind/named mount and were inaccurate as statements about Docker storage. `docker rm --force` without `--volumes` leaves the anonymous volume behind.

The before inventory contained 113 dangling volumes. The after inventory contains 107. I removed only six volume IDs whose `CreatedAt` timestamps match retained P-005/P-006 broker-run start records to the second or within the run's recorded startup window. Each mapping and `docker volume rm` result is in `evidence/removal-record.json`. The full metadata inventories are retained before and after.

## Attributed removals

- `51ad527ea80062cf2109c2258e5e211e615b30489a5d2620a17e82fdf1e05d5a` — P-005 blocked reconnect follow-up; CreatedAt 2026-10-17T20:15:13Z; former container `container p005-rabbit-49279ea241b7`; removal exit 0.
- `7f0352109341b0009b24f87d85a98b19041a6df4dadf77b6c28bf9e695b1c048` — P-005 successful reconnect follow-up; CreatedAt 2026-10-17T20:29:44Z; former container `container p005-rabbit-5fdec701f640`; removal exit 0.
- `d35ca15a450c4657f05b42beed1d3d146b329c8aa0d7e53b62d36d7697916412` — P-006 original startup run, writable-layer attempt; CreatedAt 2026-10-17T21:02:34Z; former container `container p006-rabbit-20261017`; removal exit 0.
- `cbe777351bb4374ce838f884df7bad8fa9164092e6a68dbd61d93df0a8de81c5` — P-006 explicit-cookie readiness-timeout retry; CreatedAt 2026-10-17T21:09:28Z; former container `container p006-rabbit-20261017t210906z`; removal exit 0.
- `080b8f28c898d81d85e4c5ec1a690a833c583ca408ef980e49b5660a49eb78fb` — P-006 P-005-argv baseline replay; CreatedAt 2026-10-17T21:15:04Z; former container `container p006-rabbit-p005-baseline-20261017t211452z`; removal exit 0.
- `41eb0696898eee9111ffe374bf6593b27ab80a204d40ec6eb2828f1ec17e0884` — P-006 cookie-args-only comparison; CreatedAt 2026-10-17T21:16:59Z; former container `container p006-rabbit-cookie-20261017t211649z`; removal exit 0.

These were disposable RabbitMQ test volumes from isolated runs using only synthetic test broker state; the listed containers had already been removed. The six volumes are absent from the after-inventory. No other volume was removed.

## Unresolved inventory

107 dangling volumes remain. Twenty-four volumes were created in the 2026-10-17 19:28–19:55 UTC cluster, which may overlap P-005's earlier exploratory setup; those invocations lack retained container IDs/timestamps sufficient to associate individual volumes with former containers. One remaining volume dates to 2026-10-17 12:39 UTC, and 82 date to 2026-10-04 through 2026-10-06. I did not remove or attribute those volumes. A creation-time coincidence is not enough to identify former ownership.

## Limits

This reconciles only the six P-005/P-006 volumes with direct timestamp linkage. It does not establish the owner or contents of the remaining dangling volumes, inspect or delete their contents, or prove encrypted storage. No Docker Desktop restart was performed. The volume created by the successful P-006 cookie-args startup run is also removed here; its broker logs and command records remain in the repository.

## Host-state comparison

P-005's successful run metadata and P-006's Docker records both identify Docker Desktop/Engine 29.8.2. A read-only macOS unified-log query for Docker backend/supervisor/vmnetd lifecycle and update messages over the relevant local-time window returned no matching lines after filtering. That is not proof that no restart or update occurred. Current `df` reports the Data volume at 58% used (93 GiB available); there is no retained historical disk-usage snapshot from the two run times. Therefore no host-state change between the successful P-005 run and the failing P-006 baseline is established. The explicit-cookie startup result is a working bypass on this host, not an identified cause of the time-dependent EACCES.
