# JDK 25 upstream warning baseline

Status date: 2026-09-20.

This project does not hide JDK warnings with suppression flags. Repository-owned warnings should be fixed at their source.
The following warnings currently originate in upstream libraries and are tracked separately from repository regressions.

## Ehcache `sun.misc.Unsafe`

Ehcache currently emits JDK 25 terminal-deprecation warnings from `ThreadLocalRandomUtil` / `sun.misc.Unsafe`.
The issue is reproducible in the upstream 3.11 line and remains open:

- https://github.com/ehcache/ehcache3/issues/3316

The project currently uses Ehcache as the documented zero-infrastructure local cache provider, with Redis available as the
distributed opt-in provider. Replacing the provider solely to hide an upstream warning would be an architectural change, not a
warning cleanup, so no JVM warning-suppression flag is added.

## Protobuf `sun.misc.Unsafe`

Some transitive Protobuf code paths can still emit JDK 25 `Unsafe` warnings. Protobuf has been reducing `Unsafe` usage in the
standard Java runtime, but Lite/compatibility paths can still contain it:

- https://github.com/protocolbuffers/protobuf/issues/20760
- https://github.com/protocolbuffers/protobuf/releases

The dependency is transitive in this repository. No JDK Unsafe warning-suppression flag is added.

## OpenJDK CDS / instrumentation

When bytecode instrumentation is active, the JVM can print:

`Sharing is only supported for boot loader classes because bootstrap classpath has been appended`

This is a JVM runtime diagnostic rather than a repository deprecation. Mockito dynamic self-attach is nevertheless eliminated
in this repository by attaching `mockito-core` explicitly as a `-javaagent` for both Gradle Test tasks and PIT child JVMs.

## Policy

An optional-hardening run is considered warning-clean when:

1. no Mockito self-attach / dynamic-agent warning remains;
2. no Gradle-owned deprecation warning remains;
3. Netty/Testcontainers native-access warnings do not reappear;
4. any remaining `Unsafe` warnings match the upstream cases documented above;
5. Checkstyle `INFO` findings remain visible as advisory design metrics and are not suppressed or reclassified.
