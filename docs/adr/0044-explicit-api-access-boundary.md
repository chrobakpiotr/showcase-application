# 0044. Public API access is explicit and anonymous AI has no customer-data tools

## Context

The application intentionally mixes anonymous storefront endpoints with authenticated
operator endpoints.

The previous Spring Security chain ended with `anyRequest().permitAll()`. That meant a
new `/api/**` controller became anonymous unless its author also remembered to extend
the security matcher list.

Two AI features exposed a more specific issue:

- recommendations accepted any customer e-mail anonymously and built context from
  purchase and review history;
- the public support assistant had an order lookup tool that accepted any order
  number.

ADR 0017 explicitly models Keycloak identities as back-office operators, not
customers. There is no trustworthy customer identity or ownership relation available
to authorize either anonymous data lookup.

## Decision

### API paths fail closed

All public API paths are explicitly allowlisted.

After all public and role-based matchers, `/api/**` is denied. Only after that API
fallback does the chain use `anyRequest().permitAll()` for the SPA and other non-API
resources.

Adding a new controller no longer silently makes it public.

### Public support AI is policy-only

`POST /api/support-assistant/questions` remains public.

The support assistant keeps RAG over bundled policy documents, rate limiting,
resilience and fixed fallback behavior, but it receives no Spring AI tools capable of
reading customer-specific state.

`OrderLookupTool` is removed.

Server-side support chat memory is also removed. In an anonymous stateless API there
is no identity/session value to which a client-supplied `conversationId` can be
safely bound. The request field remains accepted for compatibility but does not key a
server-side memory store.

A prompt injection therefore cannot gain order lookup access because that capability
does not exist in the public assistant.

### Personalized recommendations are operator-only

`GET /api/recommendations?email=...` requires `ORDER_READ`.

This reuses the established operator model instead of inventing a recommendation
role. Operators with order-read access already have access to the underlying customer
order data. Anonymous users and authenticated principals without `ORDER_READ` cannot
query another e-mail's history through recommendations.

## Consequences

- public storefront APIs remain public by explicit decision;
- unknown/new API paths fail closed;
- the anonymous AI surface cannot read orders even if prompted to do so;
- anonymous support questions are stateless between requests;
- recommendations are no longer anonymous customer self-service;
- no customer identity model is invented;
- R16 remains responsible for Authorization Code + PKCE and browser session/token
  policy;
- R17 remains responsible for shared frontend capability/route UX.
