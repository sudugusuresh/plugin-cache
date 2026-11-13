# Security Policy

## Supported versions

| Version | Supported |
|---|---|
| 1.0.x | ✅ |
| < 1.0 | ❌ |

## Reporting a vulnerability

**Please don't report security vulnerabilities through public GitHub issues.**

Instead, use one of these private channels:

1. **GitHub private vulnerability reporting** (preferred): go to the repository's
   **Security** tab and choose **Report a vulnerability**.
2. **Email:** skact.tech@gmail.com, with the subject `[plugin-cache security]`.

Please include:

- the affected version(s)
- a description of the issue and its impact
- steps to reproduce, or a proof of concept
- any suggested fix, if you have one

You can expect an acknowledgement within **5 business days**, and an update on the assessment
and planned fix within **14 days**. Once a fix is released we will credit you in the release
notes, unless you ask us not to.

## Security considerations for users

- **Never commit Redis credentials.** Load `redis.server.pwd` from an environment variable or
  a secrets manager, not from a file in source control.
- `JRedisConfig.toString()` includes the password. Don't log `JRedisConfig` objects.
- Use TLS and Redis ACLs where your Redis deployment supports them. plugin-cache does not
  currently enable TLS for you.
- Keep the transitive dependencies (Jedis, Log4j 2) up to date. Dependabot is configured for
  this repository.
