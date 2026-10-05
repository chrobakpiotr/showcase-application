#!/usr/bin/env python3
"""Disposable abstract failure-schedule model for the S30 gate candidate.

This models candidate facts in memory. It is not a Redis/PostgreSQL client,
provider conformance test, or evidence of transactional/durability behavior.
"""

from dataclasses import dataclass, field


@dataclass
class RedisModel:
    leader_epoch: int = 4
    generation: int = 8
    state: str = "ACTIVE"
    commands: dict = field(default_factory=dict)
    audits: list = field(default_factory=list)
    durable: bool = True

    def transition(self, command_id, request_digest, action):
        existing = self.commands.get(command_id)
        if existing:
            if existing["digest"] != request_digest:
                return "COMMAND_ID_REUSED"
            return existing["result"]
        self.generation += 1
        self.state = action
        result = {"result": "COMMITTED", "generation": self.generation}
        self.commands[command_id] = {"digest": request_digest, **result}
        self.audits.append((command_id, action, self.generation))
        return result

    def install_leader(self, epoch):
        if epoch < self.leader_epoch:
            return False
        self.leader_epoch = epoch
        return True


@dataclass
class PostgresModel:
    latch_epoch: int = 12
    latch: str = "RECOVERY_REQUIRED"
    leader_epoch: int = 4
    high_water_generation: int = 8
    command_rows: dict = field(default_factory=dict)

    def finalize(self, command_id, result):
        self.command_rows[command_id] = {
            "status": "COMMITTED",
            "generation": result["generation"],
        }
        self.high_water_generation = max(self.high_water_generation, result["generation"])


def main():
    print("SIMULATION ONLY: in-memory transition rules; no provider behavior proved")

    # Schedule 1: Redis atomically stores state/result/audit; modeled WAITAOF
    # success is followed by loss of the PG finalization. A takeover must read
    # the exact tombstone and reconcile while the latch remains inhibited.
    redis = RedisModel()
    pg = PostgresModel()
    command = "resume-opaque-01"
    pg.command_rows[command] = {"status": "PREPARED", "generation": None}
    result = redis.transition(command, "mac-v1:abc", "ACTIVE")
    assert redis.durable and len(redis.audits) == 1
    print("S1 after Redis transition+fsync: state=%s generation=%d audit=%d; PG=%s/%d" %
          (redis.state, redis.generation, len(redis.audits), pg.latch, pg.high_water_generation))
    # Crash before PostgreSQL finalize: PREPARED row survives, with no result.
    assert pg.command_rows[command]["status"] == "PREPARED"

    # New active leader first advances durable recovery epoch and installs its
    # Redis epoch. It remains inhibited while reconciling the prior command.
    pg.leader_epoch += 1
    pg.latch_epoch += 1
    pg.latch = "RECOVERY_REQUIRED"
    assert redis.install_leader(pg.leader_epoch)
    tombstone = redis.commands.get(command)
    assert tombstone and tombstone["digest"] == "mac-v1:abc"
    assert tombstone["result"] == "COMMITTED"
    # Candidate requires reconciliation of command/generation before admission;
    # model the safe result as PG finalization, but do not clear recovery latch.
    pg.finalize(command, tombstone)
    assert pg.high_water_generation == redis.generation
    assert pg.latch == "RECOVERY_REQUIRED"
    assert redis.state == "ACTIVE"  # Redis state alone must never admit.
    print("S1 after takeover/reconcile: leader_epoch=%d latch=%s PG_hwm=%d Redis=%s/%d permits=DENIED" %
          (pg.leader_epoch, pg.latch, pg.high_water_generation, redis.state, redis.generation))

    # Distinguish commit-not-landed from commit-landed/response-lost. In the
    # latter case the takeover reads the already committed exact result.
    uncertain_pg = PostgresModel()
    uncertain_pg.command_rows[command] = {"status": "PREPARED", "generation": None}
    uncertain_pg.finalize(command, tombstone)
    pg_finalize_reply = "lost"
    assert uncertain_pg.command_rows[command]["status"] == "COMMITTED"
    assert uncertain_pg.command_rows[command]["generation"] == redis.generation
    print("S1b PG finalize: commit=landed reply=%s takeover_reads=COMMITTED/%d permits=DENIED_until_latch_RESUME" %
          (pg_finalize_reply, uncertain_pg.command_rows[command]["generation"]))

    # Schedule 2: WAITAOF may have succeeded at Redis but the reply is lost.
    # Application cannot infer durability from the lost response. A read of
    # state/tombstone is not itself a durability proof; deny permits until a
    # selected provider supports a fresh barrier + reconciliation procedure.
    redis2 = RedisModel()
    result2 = redis2.transition("pause-opaque-02", "mac-v1:def", "PAUSED")
    waiaof_effect = "fsynced"  # injected true, unknown to caller after timeout
    waiaof_reply = "lost"
    caller_knows_fsync = waiaof_reply != "lost"
    assert waiaof_effect == "fsynced" and not caller_knows_fsync
    observed = redis2.commands.get("pause-opaque-02")
    assert observed and observed["result"] == "COMMITTED"
    print("S2 injected WAITAOF: effect=%s reply=%s tombstone_visible=%s permits=DENIED" %
          (waiaof_effect, waiaof_reply, bool(observed)))
    print("S2 result: visible Redis data does not prove the required fsync reply/count; recovery needs provider-specific barrier evidence or remains inhibited")

    # Schedule 3: both stores are restored from a mutually consistent but stale
    # snapshot. No remaining local record says that a later command existed.
    # The simulation proves the information-theoretic gap unless an independent
    # monotonic anchor/provider anti-rollback guarantee is required.
    committed_pg = PostgresModel(latch_epoch=20, latch="CLEAR", leader_epoch=9,
                                 high_water_generation=40)
    committed_redis = RedisModel(leader_epoch=9, generation=40, state="ACTIVE")
    committed_redis.commands["resume-committed-99"] = {
        "digest": "mac-v1:old", "result": "COMMITTED", "generation": 40,
    }
    committed_redis.audits.append(("resume-committed-99", "ACTIVE", 40))
    # Coordinated stale restore drops later acknowledged state from both stores.
    restored_pg = PostgresModel(latch_epoch=19, latch="RECOVERY_REQUIRED",
                                leader_epoch=8, high_water_generation=39)
    restored_redis = RedisModel(leader_epoch=8, generation=39, state="PAUSED")
    assert "resume-committed-99" not in restored_redis.commands
    assert restored_pg.high_water_generation == restored_redis.generation
    assert restored_pg.leader_epoch == restored_redis.leader_epoch
    print("S3 before restore: committed command present at PG/Redis epoch=9 generation=40")
    print("S3 after coordinated stale restore: both stores internally agree at epoch=8 generation=39; lost command is undetectable locally")
    # A fresh leader epoch can advance from stale values, but cannot recreate
    # the missing historical proof; candidate must classify this provider/state
    # as unqualified or require an external monotonic restore anchor.
    restored_pg.leader_epoch += 1
    restored_pg.latch_epoch += 1
    restored_pg.latch = "RECOVERY_REQUIRED"
    assert restored_redis.install_leader(restored_pg.leader_epoch)
    print("S3 after ordinary takeover: epoch=%d latch=%s still cannot prove rollback; permits=DENIED pending external anti-rollback/reconciliation" %
          (restored_pg.leader_epoch, restored_pg.latch))

    print("PASS: all three abstract schedules reached the expected fail-closed decisions")
    print("LIMIT: assertions validate this model only; no Redis, WAITAOF, PostgreSQL, failover, or backup provider was exercised")


if __name__ == "__main__":
    main()
