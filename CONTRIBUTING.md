# Contributing to plugin-cache

Thanks for your interest in improving plugin-cache! Bug reports, documentation fixes and code
contributions are all welcome.

By participating you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Ways to contribute

- **Report a bug.** Open an [issue](https://github.com/sudugusuresh/plugin-cache/issues/new/choose)
  using the bug template, and include your Java, Redis and plugin-cache versions.
- **Suggest a feature.** Open an issue with the feature-request template. For anything larger
  than a small fix, please wait for agreement on the issue before writing code.
- **Fix something.** Issues labelled `good first issue` or `help wanted` are good starting
  points. The [known issues](docs/USAGE.md#7-known-issues) list in the usage guide is another.
- **Security problems:** please **don't** open a public issue. Follow [SECURITY.md](SECURITY.md).

## Development setup

Requirements:

- JDK 8 or newer (the library targets Java 8 bytecode)
- Maven 3.6+
- A Redis server for manual testing, e.g. `docker run --rm -p 6379:6379 redis:7`

```bash
git clone https://github.com/sudugusuresh/plugin-cache.git
cd plugin-cache
mvn clean verify
```

## Making a change

1. Fork the repository and create a branch from `main`:
   `git checkout -b fix/short-description`
2. Make your change. Please keep the following rules:
   - **Java 8 compatibility.** Don't use APIs or syntax newer than Java 8.
   - **No new runtime dependencies** without discussing it in an issue first.
   - **Public API stability.** Don't change or remove public methods in a minor or patch release.
     If a behaviour change is needed, say so clearly in the PR.
   - **Javadoc.** Document new public classes and methods, including units (seconds vs
     milliseconds), `null` handling and thrown exceptions.
   - **Tests.** Add or update tests under `src/test/java` for behaviour you change.
   - **Style.** Match the surrounding code (tabs for indentation, braces on the same line).
3. Update [CHANGELOG.md](CHANGELOG.md) under **Unreleased**.
4. Update [README.md](README.md) and [docs/USAGE.md](docs/USAGE.md) if usage changes.
5. Run `mvn clean verify` and make sure it passes.
6. Commit with a clear message, for example `Fix TTL unit in setValueWithExpiry`.
7. Open a pull request against `main` and fill in the template.

## Pull request review

- CI must pass on all tested JDK versions.
- At least one maintainer approval is required before merging.
- Maintainers may ask for changes. Please respond on the PR. Inactive PRs may be closed after
  30 days without a response.

## Licensing of contributions

plugin-cache is licensed under the [Apache License 2.0](LICENSE). By submitting a contribution
you agree that it is licensed under the same terms, as described in section 5 of the license.
You must have the right to submit the code you contribute.
