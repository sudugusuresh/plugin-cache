# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this
project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0] - 2026-11-13

First public release.

### Added
- `JRedisConnectorV1`: Properties-configured pooled Redis client with retry on connection errors.
- `JRedisConnector`: Redis client configured from `JRedisConfig` / `JRedisPoolConfig`, with ACL
  user support, per-call database selection and counters.
- `QUtil` utility helpers.
- Javadoc for all classes, a README, a usage guide (`docs/USAGE.md`) and community files.

### Changed
- `JRedisConnectorV1` defaults are now `localhost:6379` with **no password**. Previously a
  placeholder host and password were compiled in. A blank `redis.server.pwd` now means "no password".
- Upgraded Log4j 2 from 2.17.0 to 2.17.2 (fixes CVE-2021-44832).

### Removed
- Unused dependencies: `lettuce-core`, `gson`, `commons-io`, `junit` 3.8.1.
- Private repository configuration and the `maven-shade-plugin` execution.

### Known issues
See [docs/USAGE.md § Known issues](docs/USAGE.md#7-known-issues).

[Unreleased]: https://github.com/sudugusuresh/plugin-cache/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/sudugusuresh/plugin-cache/releases/tag/v1.0.0
