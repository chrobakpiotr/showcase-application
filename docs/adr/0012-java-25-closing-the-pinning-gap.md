# 0012. Java 25 upgrade: closing the virtual-thread pinning gap

## Context

[ADR 0011](0011-java-21-virtual-threads.md) adopted Java 21 and enabled virtual threads
(`spring.threads.virtual.enabled`), but its pinning-risk review and HikariCP-sizing discussion both flagged
the same caveat: many JDBC drivers perform blocking socket reads inside internally `synchronized` code paths,
and on Java 21 a virtual thread that blocks while holding a `synchronized` monitor is pinned to its carrier
platform thread for the duration of that call. This is a JVM-level limitation
([JEP 491](https://openjdk.org/jeps/491), "Synchronize Virtual Threads without Pinning") that ADR 0011
explicitly deferred as "not fully resolved until JDK 24" - meaning this app's JDBC-bound request threads could
still occasionally consume a platform thread for the duration of a blocked DB call, capping how far virtual
threads alone could reduce Tomcat's effective platform-thread pressure under DB-heavy load.

JEP 491 has since shipped: status Closed/Delivered, released in JDK 24, and included (a JDK feature, not a
preview) in JDK 25, the next LTS release after 21. It removes pinning for both cases previously caused by
`synchronized`: blocking to acquire/hold a monitor, and blocking in `Object.wait()`/`notify()`. The only
pinning scenario the JDK still deliberately reports (via the retained `jdk.VirtualThreadPinned` JFR event) is
a virtual thread calling native code (JNI or the Foreign Function & Memory API) that calls back into Java code
which then blocks or waits on a monitor - this codebase has no JNI/FFM usage anywhere, so it does not apply
here.

Gradle 9.6.1 (this project's wrapper, unchanged since the Java 21 upgrade) already fully supports JDK 25, both
for running Gradle itself and as a toolchain/compilation target (per Gradle's own compatibility table, support
for running on 25 and for 25 toolchains both landed in Gradle 9.1.0). `eclipse-temurin:25-jdk`/`25-jre-alpine`
images are published. No tooling blocker exists to moving straight to 25.

## Decision

Move to Java 25 everywhere the version was pinned for the Java 21 upgrade in ADR 0011 - the same set of
places, no new ones:

- `build.gradle` (`sourceCompatibility`/`targetCompatibility`/`idea.project.languageLevel`): 21 -> 25.
- `Dockerfile`: builder stage `eclipse-temurin:21-jdk` -> `25-jdk`; runtime stage `21-jre-alpine` ->
  `25-jre-alpine`.
- `.github/workflows/ci.yml`: `java-version` 21 -> 25 in both the backend-build and dependency-check jobs.
- `README.md` (prerequisites, tools/libraries list, "App containerization" and "Load testing" sections) and
  `docs/architecture/README.md` updated accordingly.

No application code changes were needed beyond the version bump. `spring.threads.virtual.enabled`, the
`MessagingConfiguration` virtual-thread executor wiring, and the Java 21 pattern-matching refactors from ADR
0011 all continue to work unchanged - this is a pure JDK version bump, not a new feature adoption in
application code. The `PdfGenerator` `synchronized` block reviewed in ADR 0011 remains fine either way (it was
already assessed there as carrying no meaningful pinning risk even under the old semantics).

This supersedes the JDBC-driver-pinning part of ADR 0011's "considered, not applied" HikariCP note: on JDK 25,
a virtual thread that blocks on JDBC socket I/O while inside a driver's internally `synchronized` code no
longer pins its carrier. This doesn't change the HikariCP pool-sizing decision itself (still left at its
default, still the real backpressure point for JDBC-bound concurrency, still deferred to real load testing
rather than changed reflexively) - it changes *why* that's true: the pool size is now the sole practical
ceiling for concurrent DB work, not "the pool size, compounded by occasional driver-level pinning."

## Consequences

- Virtual threads now deliver on their scalability promise across effectively all of this app's blocking I/O,
  not just the non-JDBC portion (`RestTemplate` calls, RabbitMQ listener dispatch, Tomcat's own request
  handling) called out in ADR 0011 - JDBC-bound request threads no longer risk consuming a platform thread for
  the duration of a blocked DB call.
- Manual pinning audits for `synchronized`-based blocking are no longer needed for this codebase's own code
  (moot already, per ADR 0011's review) or for third-party JDBC drivers (newly moot as of this ADR) - the only
  remaining pinning scenario (blocking/waiting inside native-code callbacks) doesn't apply here.
- No behavioral or test changes were needed: this is a transparent JVM/tooling version bump, validated by the
  existing test suite (including full-Spring-context boot tests) passing unchanged on the new target.
- HikariCP pool sizing remains explicitly out of scope here, same as ADR 0011 - still worth revisiting under
  real load testing, but for a simpler reason now (pure connection-count backpressure, no longer compounded by
  driver-level pinning).
