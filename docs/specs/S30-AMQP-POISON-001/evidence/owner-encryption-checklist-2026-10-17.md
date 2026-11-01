# REF-CORRECTNESS host encryption owner checklist

Status: **owner action pending; no host security setting was changed**. The
inventory on 2026-10-17 observed FileVault OFF and APFS `Encrypted: No`. S30-06
remains OPEN / NOT QUALIFIED, and the application AMQP consumer remains
disabled.

## Owner actions (Piotr)

1. Decide separately whether to enable FileVault on this Mac. This checklist
   does not authorize or perform that change. Before enabling it, confirm the
   recovery-key custody and recovery procedure appropriate for this machine.
2. If approved, enable FileVault through macOS settings and let encryption
   finish. Record the completion time and the host identity used for the
   subsequent verification.
3. Identify the actual Docker Desktop backing disk/data location and all
   persistent volumes used by the proposed S30 Compose target. Do not infer
   their location or encryption from Docker's read-only mount view.
4. Inventory every persistent store and every retained copy: RabbitMQ queue
   and quarantine data, application and gate PostgreSQL, gate Redis AOF,
   Docker Desktop VM/backing disk, local exports, backups, snapshots and any
   copied evidence containing payloads. Record owner, path/provider, retention,
   encryption mechanism, key custody and deletion mechanism for each.
5. Verify backup/export destinations independently. FileVault on the laptop
   does not establish encryption of a remote backup, removable copy, exported
   archive, or provider-managed snapshot. Record each destination and its
   encryption/key evidence.

## Independent verification after owner action

The verifier should collect separate evidence for each claim; success on one
does not imply the others:

| Claim | Required evidence |
|---|---|
| Host volume encryption | Fresh `fdesetup status` plus `diskutil apfs list` output identifying the exact startup/data volume and reporting encryption complete; redact recovery secrets. |
| Docker Desktop storage location | Docker Desktop's configured disk image path and a read-only host volume/device mapping proving which APFS volume contains it. Confirm all Docker named volumes and bind-mounted persistent directories resolve under the inventoried storage. |
| Persistent service data | Enumerated RabbitMQ, application/gate PostgreSQL, Redis AOF and quarantine data paths, with a mapping to the encrypted backing volume and exact container/volume IDs. No unlisted persistent mount is accepted. |
| Backups, exports and snapshots | Per-copy inventory, destination encryption configuration, key custody, retention/deletion evidence and restore test. An encrypted source does not prove encrypted copies. |
| Access and key control | Host path ownership, ACLs and parent-directory permissions; Docker daemon/desktop access; backup access; key recovery and revocation procedure. Root ownership alone is not proof of protection against root or host compromise. |
| Transport encryption | Separate TLS configuration and peer-validation evidence for app and audited-tool RabbitMQ connections. TLS does not establish at-rest encryption. |

The prior P-004 observation that a directory-mounted atomic replacement became
visible in 45–48 ms establishes only visibility on that test. It does not
establish crash durability, integrity/authenticity, permissions, anti-rollback
or encryption. Test and report those properties independently.

## Host-control record evidence separation

| Property | Current evidence | Status / next verification |
|---|---|---|
| Replacement visibility | P-004 observed complete directory-mounted replacements in 45 ms and 48 ms on two trials. Individual-file bind mounts exposed incomplete JSON for up to four seconds. | Bounded visibility observation only; repeat on the exact target and pin the run environment. |
| Crash durability | The probe fsynced the temporary file and parent directory but did not kill the host/Docker VM at each boundary or test power loss/reboot. | Not established. Add crash cuts before/after file fsync, rename and directory fsync; verify after process, Docker Desktop and host restart. Host power-loss durability requires its own evidence. |
| Integrity/authenticity | No adversarial tampering, forged-record, or record-signature/MAC validation test was run. | Not established. Specify trusted writer/key boundary first; test byte modification, replacement and malformed content. A plain digest stored beside the record is not an authenticity proof. |
| Permissions/path protection | The proposed path has not been created or qualified. Root ownership and a read-only container mount are not evidence for every parent ACL, symlink/race defense or host privileged actor. | Not established. Verify every path component, ACL/mode, mount identity, gate UID/GID/capabilities and write denial from gate/container identities; test path substitution. |
| Anti-rollback | The record was not tested against restoration of an older host-record copy or a full host rollback. | Not established. Name and test the independent freshness anchor and stale-record rejection. Full-host rollback remains explicitly out of REF-Q unless separately controlled. |

## Scope exclusions

Even after the checks pass, the REF-Q proposal does not protect direct or
provider-managed restore outside the wrapper, wrapper bypass, full-host rollback,
loss or rollback of the host control-record volume, or root/host compromise.
Root ownership on macOS and a read-only container mount do not establish
protection of every ancestor or prevent a privileged host actor from replacing
the record. Those exclusions must remain in the accepted contract and the
qualification report. A completed checklist is evidence for owner review; it
does not itself qualify the target or close S30-06.
