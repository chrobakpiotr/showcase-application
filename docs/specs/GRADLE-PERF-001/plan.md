# Plan - GRADLE-PERF-001

1. Preserve the existing Gradle parallel/build/configuration-cache defaults.
2. Use the existing setup-gradle Enhanced Caching and enable optional encrypted configuration-cache persistence.
3. Remove redundant `clean` and unnecessary cache/parallel opt-outs.
4. Force only the critical Postgres Test task fresh instead of using global `--rerun-tasks`.
5. Verify the required Postgres gate and full build.
