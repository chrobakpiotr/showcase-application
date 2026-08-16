# 0011. Java 21 upgrade with opt-in virtual threads

## Context

The project targeted Java 17 (`build.gradle`'s `sourceCompatibility`/`targetCompatibility`, and the `idea`
plugin's `languageLevel`), even though the `Dockerfile`'s runtime image was already `eclipse-temurin:21-jdk`
(builder stage) / `21-jre-alpine` (runtime stage) - the README even called out this exact mismatch under
"Prerequisites". Since 17-compiled bytecode runs unchanged on a 21 JVM, this was harmless but left the
actual language level and CI build JDK behind the runtime the app already ships on, and left Java 21's
features - most notably virtual threads (JEP 444, GA since 21) - unused.

This backend is a classic blocking Spring MVC application on embedded Tomcat (`spring-webmvc`,
`tomcat-embed-core`; there is no WebFlux/reactive stack anywhere), with blocking I/O throughout: JDBC/JPA
persistence adapters (`@Transactional` in `SaveOrderAdapter`, `CancelOrderAdapter`, etc.), a blocking
`RestTemplate` bean, and a RabbitMQ `SimpleMessageListenerContainer` (`MessagingConfiguration`). This is
precisely the workload shape virtual threads target: cheap, JVM-managed threads that let a thread-per-task
model (Tomcat's request handling, in particular) scale far beyond the platform-thread pool sizes that were
previously needed, without any reactive/async rewrite of application code.

## Decision

Move to Java 21 everywhere the version was pinned, and enable virtual threads for the parts of the stack
that benefit from them, having first checked for known virtual-thread pitfalls in this codebase:

- `build.gradle` (`sourceCompatibility`/`targetCompatibility`/`idea.project.languageLevel`),
  `.github/workflows/ci.yml` (`java-version` in both the backend-build and dependency-check jobs), and the
  `Dockerfile` (already on 21) now agree on Java 21 end-to-end. `README.md` and
  `docs/architecture/README.md` updated accordingly.
- **Pinning check**: virtual threads get pinned to their carrier platform thread if they block while holding
  a `synchronized` monitor (not fully resolved until JDK 24's JEP 491). The only `synchronized` block in
  main source is `PdfGenerator`'s one-time Apache FOP ICC color profile initialization guard: after the
  first call flips `iccColorsInitialized`, every subsequent call enters the block only to check a boolean
  and exit - no blocking I/O happens while the monitor is held in the steady state, so there is no
  meaningful pinning risk. No other `synchronized` usage exists in `domain`/`adapter`/`application` main
  source, so this was the only case to review.
- `spring.threads.virtual.enabled: true` added to `application/ecommerce/src/main/resources/application.yml`
  (the bootable app's base config - unlike `cache.enabled`/`cache.provider`, there's no profile-import-chain
  gap to duplicate this across, since this is the one config file every runtime profile combination shares).
  This is a single Spring Boot-native switch: Tomcat's request-handling executor and Boot's
  auto-configured task executor/scheduler both pick it up automatically, with no other code changes needed
  (virtual threads are transparent to application code - same `Thread`/`Runnable`/`ExecutorService` APIs).
- `MessagingConfiguration`'s `SimpleMessageListenerContainer` is hand-built (per [ADR 0003](0003-resilience4j-without-spring-boot-autoconfig.md)'s
  preference for explicit wiring over starter autoconfiguration), so unlike Boot's own auto-configured
  executors it does **not** automatically inherit `spring.threads.virtual.enabled`. Added a
  `@ConditionalOnThreading(Threading.VIRTUAL)`-gated `AsyncTaskExecutor` bean (`VirtualThreadTaskExecutor`),
  wired into the container via `ObjectProvider<AsyncTaskExecutor>.ifAvailable(...)` - the exact same
  conditional Spring Boot's own internal autoconfiguration uses for this decision, so the listener container
  follows the same on/off switch as the rest of the app instead of needing its own separate flag, and falls
  back to Spring AMQP's own default executor untouched when virtual threads are off.
- Applied Java 21 pattern matching for `switch` (JEP 441) in `FreeMarkerCustomObjectWrapper
  .handleUnknownType`, replacing an if/else-if `instanceof`-with-cast chain, and pattern-matching
  `instanceof` in `OrderCustomObjectWrapperFactory.wrap`'s single cast - both pure, behavior-preserving
  readability improvements in code already being touched for this change's investigation.

**Explicitly considered, not applied:**

- **Records for existing value objects/DTOs** (e.g. `Order`, the FTL mapper classes): the codebase's
  domain value objects use Lombok `@Value`/`@Builder` specifically because they extend the mutable,
  reusable `ValidDomainObject<T>` self-validation base class - records cannot extend a class (only
  implicitly extend `java.lang.Record`), and JPA entities cannot be records either (JPA requires a mutable,
  no-arg-constructible, proxyable class). Converting these would need a broader redesign of the validation
  base-class hierarchy, unrelated to "adopt Java 21", so it is out of scope here. New, genuinely simple
  immutable carriers unrelated to that hierarchy are still reasonable record candidates going forward.
- **Increasing RabbitMQ listener concurrency** to actually exploit virtual threads' cheap-parallelism story:
  the container is single-consumer today. Raising concurrency is a throughput/ordering decision in its own
  right (message processing ordering guarantees would need separate scrutiny) and is orthogonal to *which
  kind* of thread runs the existing single listener - left as future work, not bundled into this change.
- **HikariCP pool sizing**: not explicitly configured anywhere (Spring Boot/HikariCP default of 10 applies).
  Left unchanged deliberately - virtual threads remove the Tomcat *request-thread* ceiling, not the
  *database connection* ceiling, which remains the real backpressure point for JDBC-bound work. This is
  also why virtual threads are not a blanket win for this app: several JDBC drivers still perform blocking
  socket reads inside internally `synchronized` code paths, which can pin a virtual thread to its carrier
  for the duration of that call (an ecosystem-wide limitation, not specific to this project, only fully
  addressed by JEP 491 in JDK 24). Virtual threads here primarily benefit the non-JDBC I/O (the
  `RestTemplate` call, RabbitMQ listener dispatch, Tomcat's own connection/request handling) and remove the
  need to hand-tune Tomcat's platform-thread pool size for concurrency - they are not, on their own, a
  guaranteed database-throughput improvement, and pool sizing should be validated under real load rather
  than changed reflexively alongside this switch.

## Consequences

- The app now runs on the same Java 21 language level it already shipped on at runtime; no more
  17-vs-21 discrepancy between local/CI builds and the container image.
- Tomcat request handling, Boot's auto-configured task executor/scheduler, and the RabbitMQ listener
  container all run on virtual threads, without any reactive rewrite - existing blocking code
  (`@Transactional` repositories, `RestTemplate`, the AMQP listener) is unchanged and unaffected in
  behavior, only in the kind of thread it executes on.
- Disabling `spring.threads.virtual.enabled` (or running on a hypothetical older JDK) cleanly reverts both
  the Boot-managed executors and the RabbitMQ listener's executor to platform threads via the same
  `Threading.VIRTUAL` condition - there is exactly one switch to flip, not two independent ones.
- No behavioral test changes were needed: virtual threads are transparent to application code, so the
  existing test suite (including full-Spring-context boot tests) validates the change as-is.
- Not addressed here (future work, same spirit as prior ADRs' "deliberately out of scope" lists): actually
  scaling RabbitMQ listener concurrency to exploit virtual threads' cheap parallelism, revisiting HikariCP
  pool sizing under real load, and a broader Lombok-value-object-to-record migration if the
  `ValidDomainObject<T>` inheritance model is ever redesigned.
