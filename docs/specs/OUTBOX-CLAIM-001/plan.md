# Plan - OUTBOX-CLAIM-001

1. Document the multi-worker claim, lease and fencing rules.
2. Add `PROCESSING`, `CLAIM_ID` and `CLAIM_UNTIL` to the order outbox.
3. Claim placement work in a short transaction and run external work afterward.
4. Fence every placement completion/failure transition by claim token.
5. Extend cancellation arbitration for active and expired placement leases.
6. Lease and fence `COMPENSATING` recovery work.
7. Prove active-lease exclusion and expired-lease recovery on PostgreSQL.
8. Run R01/R02/R04/R06/R07 regressions, persistence coverage and full repository gates.
