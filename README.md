# Showcase Application
<!-- showcase-readme-refresh -->

[![CI](https://github.com/chrobakpiotr/showcase-application/actions/workflows/ci.yml/badge.svg)](https://github.com/chrobakpiotr/showcase-application/actions/workflows/ci.yml)
[![CodeQL](https://github.com/chrobakpiotr/showcase-application/actions/workflows/codeql.yml/badge.svg)](https://github.com/chrobakpiotr/showcase-application/actions/workflows/codeql.yml)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/chrobakpiotr/showcase-application/badge)](https://scorecard.dev/viewer/?uri=github.com/chrobakpiotr/showcase-application)
[![License: source-available](https://img.shields.io/badge/License-source--available-lightgrey.svg)](LICENSE)

A production-shaped e-commerce showcase built with Java 25, Spring Boot and Angular.

## Quick start

Requires JDK 25+ and Docker with Compose. Run from the repository root.

```bash
./gradlew clean build
docker compose up -d --build
```

Open [the application](http://localhost:9080/home) and sign in with the local demo account
`order-admin` / `password`. The [local development guide](docs/development.md) covers IDE setup,
service addresses and teardown.

## Documentation

- [Features and operator workflows](docs/domain-guide.md)
- [10-minute showcase](docs/showcase-demo.md) and [repeatable demo scenarios](docs/demo-guide.md)
- [Architecture and integration flows](docs/architecture.md)
- [API and access model](docs/api.md)
- [Full documentation index](docs/README.md)

## License

Source-available, not open source. From 2026-10-31, original additions and modifications
owned by Piotr Chrobak are proprietary: you may read and fork this repository on GitHub, and
no other right is granted without written permission. See [LICENSE](LICENSE). Material
published under MIT between 2026-09-01 and the baseline revision named in LICENSE remains
available under the [MIT License](LICENSE-MIT); that grant is not revoked. Third-party
dependencies retain their own licences.
