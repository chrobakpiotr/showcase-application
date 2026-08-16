# 0010. Redis as an opt-in distributed cache alongside the default Ehcache

## Context

Order lookups (`GET /api/order/{orderNumber}`) are cached via Ehcache (`PersistenceCacheConfiguration`,
toggled by `cache.enabled`), an in-process, heap-based JCache provider. That is a good zero-dependency
default for a single instance, but it is fundamentally local to each JVM: it holds no state shared across
instances. The Helm chart (`etc/k8s/helm/ecommerce`) supports `replicaCount > 1` and a
`HorizontalPodAutoscaler`, but with Ehcache, every pod keeps its own independent cache. Two pods can then
observe different data for the same order number - most concretely, the existing `@CachePut` on
`OrderEntityRepository.save` (added to avoid serving a stale cached order once `SEQ_ORDER_NUMBER` wraps
back to `1000` and a number is reused) only keeps *that one pod's* cache correct; any other pod's cache
still holds the stale entry until it naturally expires. A single-instance deployment never hits this, but
a horizontally scaled one silently can.

## Decision

Keep Ehcache as the default, zero-dependency provider, and add Redis as a second, opt-in provider for the
same `CacheManager` abstraction - the same "safe default + opt-in real adapter" shape already established
by [ADR 0007](0007-optional-cloud-integrations-as-opt-in-adapters.md), generalized here to a *swappable
backend* rather than an on/off toggle:

- New `cache.provider` property (`ehcache` default, `redis`), independent of the existing `cache.enabled`
  toggle (`false` still means "no caching at all", regardless of provider).
- `PersistenceCacheConfiguration` gains two nested `@Configuration` classes, both still gated by
  `cache.enabled=true`: `EhcacheCacheManagerConfiguration` (the existing logic, unchanged, now additionally
  gated on `cache.provider=ehcache`/missing) and `RedisCacheManagerConfiguration` (new, gated on
  `cache.provider=redis`), each producing the single active `CacheManager` bean. Per-cache TTL is still
  read from the existing `CacheProperties` enum; Ehcache's per-cache `maxEntries` bound has no Redis
  equivalent and is intentionally not carried over - Redis capacity is a server-side concern
  (`maxmemory` + eviction policy), not a per-cache client setting.
- Cache values are serialized as JSON (`OrderEntity` is a plain JPA entity, not `Serializable`), bound to
  each cache's own value type via the type-safe, non-polymorphic `Jackson2JsonRedisSerializer`, not the
  untyped `GenericJackson2JsonRedisSerializer` - the latter's default/no-arg form was deprecated upstream
  specifically over the security risk of embedding polymorphic type metadata in cached JSON. Both
  Jackson-2-based serializers are additionally deprecated in favor of a Jackson-3-based replacement, which
  this project does not otherwise use anywhere (it depends on Jackson 2 throughout); adopting Jackson 3 for
  this one serializer would add a second, incompatible major Jackson version to the classpath purely for
  this feature, which is out of proportion to the goal here. The deprecated-for-removal API is used
  deliberately (`@SuppressWarnings("removal")`, with a comment), accepting that it must be revisited if a
  future Spring Data Redis release actually removes it.
- Adding `spring-boot-starter-data-redis` to the classpath makes Spring Boot's `RedisAutoConfiguration`
  fire unconditionally (it is triggered by class presence, not by `cache.provider`), which would register a
  Redis health indicator regardless of whether Redis is actually the active provider. Since the Helm
  chart's startup/liveness/readiness probes all target the aggregate `/actuator/health`, an idle/unreachable
  Redis would otherwise flip an Ehcache-backed deployment's health to `DOWN`. `management.health.redis.enabled`
  therefore defaults to `false` wherever `cache.enabled`/`cache.provider` default (`application-persistence.yml`,
  and its duplicate in `application.yml` for the postgres/docker/k8s profiles that don't import it - the
  same duplication precedent `cache.enabled` already required), and is only re-enabled by the opt-in
  `application-cache-redis.yml` profile, mirroring the existing `management.health.rabbit.enabled` pattern
  used for RabbitMQ. This risk turned out not to be purely theoretical: full-build validation caught
  `adapter-security`'s and `adapter-mail`'s own `@SpringBootTest` suites failing `/actuator/health` assertions
  with `503`, because `:adapter:persistence` (now carrying `spring-boot-starter-data-redis`) sits on their
  test classpath without importing any of the profile-gated ymls above - the exact same exposure the existing
  RabbitMQ precedent already has, and already solves the same way. Fixed by adding the identical
  `management.health.redis.enabled: false` line to those modules' own `src/test/resources/application.yml`,
  next to their existing `management.health.rabbit.enabled: false`.
- New `application-cache-redis[.yml|-local.yml|-docker.yml]` profiles, following the established
  `application-<tech>` convention (e.g. `application-amqp*.yml`): the base file sets `cache.provider=redis`
  and re-enables the health indicator; `-local`/`-docker` add the host for running against the standalone
  `etc/docker/redis/docker-compose.yml` or the full containerized stack, respectively.
- Redis joins the full stack in the root `docker-compose.yml` (always-on, like Postgres/RabbitMQ/Kafka, not
  behind an opt-in `--profile` like the AWS/chaos add-ons) and `etc/k8s/dev-dependencies.yaml`/the Helm
  chart, with `application-k8s.yml` defaulting `cache.provider=redis` - this is precisely the deployment
  target the multi-replica correctness gap applies to. No authentication is configured for the demo Redis
  instance, matching the existing throwaway/dev nature of the other `etc/k8s/dev-dependencies.yaml` and
  standalone `etc/docker/*` services.

## Consequences

- Horizontally scaled deployments (`replicaCount > 1` or `autoscaling.enabled`) can now use a cache that is
  actually consistent across pods by setting one property, with no code changes.
- The default (`cache.provider=ehcache`, or `cache.enabled=false`) is completely unaffected: no new runtime
  dependency is contacted, and the Redis health indicator stays off so `/actuator/health` cannot be
  affected by a Redis instance that was never meant to be running.
- A new runtime dependency (`spring-boot-starter-data-redis`, pulling in Lettuce) is now always on the
  `adapter:persistence` classpath, even for deployments that never enable it - consistent with how the
  amqp/kafka/aws adapters are already always on the classpath but only activated via a property.
- Not addressed here (left as future work, same spirit as ADR 0009's "deliberately out of scope" list):
  Redis authentication/TLS for anything beyond local experimentation, Redis Sentinel/Cluster topologies,
  and revisiting the serializer choice if Jackson-2-based Spring Data Redis serializers are actually
  removed in a future release.
