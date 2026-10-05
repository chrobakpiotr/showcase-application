#!/usr/bin/env python3
"""Disposable PostgreSQL CAS experiment for S30-06b stale-handler fencing."""

from __future__ import annotations

import subprocess
import time
import uuid


IMAGE = (
    "postgres@sha256:ef257d85f76e48da1c64832459b59fcaba1a4dac97bf5d7450c77753542eee94"
)
PASSWORD = "prototype-only"
CONTAINER = f"s30-06b-cas-{uuid.uuid4().hex[:10]}"


def run(*args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, check=check, text=True, capture_output=True)


def sql(statement: str) -> str:
    result = run(
        "docker", "exec", "-e", f"PGPASSWORD={PASSWORD}", CONTAINER,
        "psql", "-X", "-q", "-A", "-t", "-v", "ON_ERROR_STOP=1",
        "-U", "postgres", "-d", "postgres", "-c", statement,
    )
    return result.stdout.strip()


def main() -> None:
    try:
        run(
            "docker", "run", "--detach", "--rm", "--name", CONTAINER,
            "-e", f"POSTGRES_PASSWORD={PASSWORD}", IMAGE,
        )
        for _ in range(60):
            ready = run(
                "docker", "exec", "-e", f"PGPASSWORD={PASSWORD}", CONTAINER,
                "pg_isready", "-U", "postgres", check=False,
            )
            if ready.returncode == 0:
                break
            time.sleep(0.5)
        else:
            raise RuntimeError("pinned PostgreSQL container did not become ready")

        sql("""
            CREATE TABLE operation_claim (
                operation_id text PRIMARY KEY,
                generation bigint NOT NULL,
                state text NOT NULL,
                outcome text
            );
        """)
        sql("INSERT INTO operation_claim VALUES ('op-1', 0, 'READY', NULL);")
        print(f"PostgreSQL image: {IMAGE}")

        # A's claim transaction commits; its handler then pauses outside the DB tx.
        a_claim = sql("""
            UPDATE operation_claim
               SET generation = generation + 1, state = 'CLAIMED'
             WHERE operation_id = 'op-1' AND generation = 0 AND state = 'READY'
         RETURNING generation;
        """)
        assert a_claim == "1", f"A did not claim generation 1: {a_claim!r}"
        print("A claims generation 1 and is held outside its claim transaction.")

        # The eligibility rule for a newer claim is intentionally abstracted;
        # this update models an external re-delivery/reclaim authority.
        b_claim = sql("""
            UPDATE operation_claim
               SET generation = generation + 1, state = 'CLAIMED'
             WHERE operation_id = 'op-1' AND generation = 1 AND state = 'CLAIMED'
         RETURNING generation;
        """)
        assert b_claim == "2", f"B did not claim generation 2: {b_claim!r}"
        print("B reclaims generation 2 after the configured eligibility event.")

        b_finalize = sql("""
            UPDATE operation_claim
               SET state = 'COMPLETED', outcome = 'B'
             WHERE operation_id = 'op-1' AND generation = 2 AND state = 'CLAIMED'
         RETURNING generation;
        """)
        assert b_finalize == "2", f"B finalization failed: {b_finalize!r}"
        b_ack_eligible = bool(b_finalize)  # Model only; runtime must enforce this coupling.
        assert b_ack_eligible, "B's ACK eligibility requires a successful finalization CAS"
        print("B finalizes generation 2; B's CAS permits its ACK under the model.")

        # A resumes with the generation captured before its handler was held.
        a_finalize = sql("""
            UPDATE operation_claim
               SET state = 'COMPLETED', outcome = 'A'
             WHERE operation_id = 'op-1' AND generation = 1 AND state = 'CLAIMED'
         RETURNING generation;
        """)
        a_ack_eligible = bool(a_finalize)
        assert a_finalize == "", f"stale A unexpectedly finalized: {a_finalize!r}"
        assert not a_ack_eligible, "stale A must not be ACK-eligible after zero-row CAS"

        final = sql("SELECT generation || '|' || state || '|' || outcome FROM operation_claim WHERE operation_id='op-1';")
        assert final == "2|COMPLETED|B", f"B is not authoritative: {final!r}"
        print("A resumes with generation 1; its conditional finalization affects zero rows.")
        print(f"Final row: generation|state|outcome = {final}")
        print("PASS: newer committed generation remains authoritative; stale A is rejected.")
    finally:
        run("docker", "rm", "--force", CONTAINER, check=False)


if __name__ == "__main__":
    main()
