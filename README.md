# plugin-cache

[![CI](https://github.com/sudugusuresh/plugin-cache/actions/workflows/ci.yml/badge.svg)](https://github.com/sudugusuresh/plugin-cache/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/java-8%2B-orange.svg)](#)

A small Java 8+ library that provides a pooled Redis client (built on
[Jedis](https://github.com/redis/jedis) 4.2.1) with retry on connection errors, plus a few
utility helpers. It was originally written for SMS gateway services.

| | |
|---|---|
| **Coordinates** | `com.plugin:plugin-cache:1.0.0` |
| **Java** | 1.8+ |
| **Dependencies** | `jedis` 4.2.1, `log4j-api` / `log4j-core` 2.17.2 |
| **License** | [Apache 2.0](LICENSE) |

## What's inside

| Class | Purpose |
|---|---|
| `com.plugin.cache.redis.JRedisConnectorV1` | **Recommended.** Redis client configured from `Properties`, with retry on connection errors. |
| `com.plugin.cache.redis.JRedisConnector` | Redis client configured from `JRedisConfig` / `JRedisPoolConfig`, with ACL user support, per-call database selection and counters. Read the caveats in the usage guide before using it under load. |
| `com.plugin.cache.util.JRedisConfig`, `JRedisPoolConfig` | Connection and pool settings for `JRedisConnector`. |
| `com.plugin.cache.util.JRedisException` | Checked exception thrown by `JRedisConnector`. |
| `com.plugin.cache.util.QUtil` | Static helpers: lenient int/long parsing, property loading, date formatting, query-string parsing, log-line building. |
| `com.plugin.cache.ICacheClient` | Interface only; no implementation ships in this library. |

## Installation

plugin-cache isn't on Maven Central. Use either option below.

**Option A: build from source**

```bash
git clone https://github.com/sudugusuresh/plugin-cache.git
cd plugin-cache
mvn clean install
```

Then add the dependency:

```xml
<dependency>
    <groupId>com.plugin</groupId>
    <artifactId>plugin-cache</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Option B: JitPack** (builds from a GitHub release tag)

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.sudugusuresh</groupId>
    <artifactId>plugin-cache</artifactId>
    <version>v1.0.0</version>
</dependency>
```

## Quick start

Start a local Redis if you don't have one: `docker run --rm -p 6379:6379 redis:7`

```properties
# application.properties
redis.server.ip=localhost
redis.server.port=6379
# blank = no password
redis.server.pwd=
redis.server.db=1
redis.max.threads=5
```

```java
Properties props = new Properties();
try (InputStream in = new FileInputStream("application.properties")) {
    props.load(in);
}
props.setProperty("redis.server.pwd", System.getenv().getOrDefault("REDIS_PASSWORD", ""));

JRedisConnectorV1 redis = new JRedisConnectorV1();
if (!redis.init(props)) {               // pings Redis, retries 3 times, 5 s apart
    throw new IllegalStateException("Redis unavailable");
}

redis.setValue("account:42", "{\"countryCode\":\"IN\"}");
redis.setValueWithExpiry("otp:42", "918273", 300_000);   // TTL in MILLISECONDS
String account = redis.getValue("account:42");           // null if missing OR if Redis is down
redis.deleteKey("otp:42");

redis.close();                          // on shutdown
```

## Read before you use it

- `setValueWithExpiry` takes the TTL in **milliseconds**, even though the parameter is named `..._SECONDS`.
- `JRedisConnectorV1` **fails silently.** Once its retries run out, writes return normally and `getValue` returns `null`.
- **Create one connector per application and share it.** Each `init` opens a new connection pool.
- **Missing properties fall back to defaults**: `localhost:6379`, no password, database 1. Keep real passwords out of property files in source control.

## Documentation

- **[docs/USAGE.md](docs/USAGE.md)**: full usage guide covering configuration reference, both connectors, `QUtil`, behaviour and caveats, troubleshooting and known issues.
- The Javadoc on each class in `src/main/java`.
- **[CHANGELOG.md](CHANGELOG.md)**: release history.

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) and the
[Code of Conduct](CODE_OF_CONDUCT.md) before opening an issue or pull request.
To report a security vulnerability, follow [SECURITY.md](SECURITY.md). Please don't open a public issue for it.

## License

Copyright 2024-2026 Sudugu Suresh.

Licensed under the [Apache License, Version 2.0](LICENSE). See [NOTICE](NOTICE) for
third-party attributions.
