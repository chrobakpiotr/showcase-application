# Plan - API-ACCESS-001

1. Record the R08 public/operator access decision and explicit API allowlist.
2. Replace implicit public fallback with `/api/**` deny-by-default.
3. Gate personalized recommendations with existing `ORDER_READ`.
4. Remove order lookup and chat memory from the anonymous support assistant.
5. Add deterministic negative authorization tests and structural AI capability checks.
6. Update superseded ADR statements.
7. Run focused security/AI/web tests and full repository gates.
