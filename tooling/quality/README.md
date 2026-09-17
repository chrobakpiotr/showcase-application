# Quality tooling

Shared Gradle quality configuration is grouped by tool so module build files can reference stable repository-root paths.

- `checkstyle/` - Java style and unused-import checks.
- `pmd/` - static analysis rules.
- `spotbugs/` - bytecode bug analysis.
- `jacoco/` - coverage reporting and verification.
- `pitest/` - mutation testing.
- `spotless/` - Java, XML and frontend formatting.
- `dependencycheck/` - OWASP dependency vulnerability scanning.

Module build files use `rootProject.file(...)`; physical module depth therefore does not affect quality configuration resolution.
