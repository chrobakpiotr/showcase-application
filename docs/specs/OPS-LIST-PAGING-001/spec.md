# OPS-LIST-PAGING-001 - bounded operational list APIs

## Intent
Remove the remaining unbounded public list reads for returns, notifications and shipments.

## Acceptance criteria
- AC-001: all nine public list endpoints accept `page` and `size` with max size 100.
- AC-002: persistence uses Spring Data `Pageable` queries.
- AC-003: domain paging contracts stay framework-independent and bounded-context-local.
- AC-004: HAL responses expose page metadata and first/prev/next/last links.
- AC-005: existing frontend HAL `_embedded` compatibility is preserved.
- AC-006: domain, persistence and web tests remain green.
