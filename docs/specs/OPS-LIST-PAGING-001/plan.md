# Plan - OPS-LIST-PAGING-001
1. Add local page query/result contracts.
2. Add paged port overloads and use-case delegates.
3. Translate to Spring Data `Pageable` in persistence adapters.
4. Return Spring HATEOAS `PagedModel` from operational list endpoints.
5. Run focused and full repository verification.
