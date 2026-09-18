# Plan - RMA-CONCURRENCY-001

1. Cover the remaining R03 compensation-claim catch branch.
2. Add R05 spec and ADR.
3. Introduce one atomic return-state persistence port.
4. Serialize RMA creation with the persisted order row.
5. Lock RMA moderation transitions and add a version column.
6. Remove the controller's read-then-create entitlement calculation.
7. Map entitlement exhaustion to an explicit return quantity conflict.
8. Add PostgreSQL concurrency acceptance tests.
9. Re-run R04 refund regression, persistence coverage and full repository gates.
