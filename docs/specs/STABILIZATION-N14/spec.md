# N14 — Provider reconciliation for unknown payment/refund outcomes

## Checkpoint N14.1

This checkpoint persists the provider-operation identity before capture/refund mutation.

### Invariants

- capture operation id is `ORDER-CAPTURE:<orderNumber>`;
- refund operation id is the durable refund id;
- the same operation id may be replayed only with the same immutable identity;
- known local completion marks the reconciliation row `COMPLETED`;
- a provider call that exits with an unknown technical outcome leaves the reconciliation row `PENDING`;
- N14.1 does not yet autonomously resolve `PENDING`; claiming, provider lookup and scheduler recovery are N14.2/N14.3.

### Durable fields reserved for follow-up

`ATTEMPTS`, `NEXT_ATTEMPT_DATE`, `CLAIM_ID`, `CLAIM_UNTIL`, `LAST_ERROR` and `MANUAL_REVIEW`
are persisted now so N14.2 can implement bounded multi-replica reconciliation without another
state-model rewrite.
