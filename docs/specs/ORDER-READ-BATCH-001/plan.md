# Plan - ORDER-READ-BATCH-001

1. Add batch read contracts to the payment incoming/outgoing ports.
2. Resolve a page of payment rows with one `findAllById`.
3. Fill missing rows with the existing PENDING placeholder.
4. Enrich the order page from the batch map.
5. Preserve the single-order read contract.
6. Run focused and final repository gates.
