# plugin-cache usage guide

This guide is for developers using `plugin-cache` in their applications. For a short
overview and build steps, see the [README](../README.md).

- [1. Choosing a connector](#1-choosing-a-connector)
- [2. JRedisConnectorV1 (Properties-based)](#2-jredisconnectorv1-properties-based)
- [3. JRedisConnector (config-object based)](#3-jredisconnector-config-object-based)
- [4. QUtil helpers](#4-qutil-helpers)
- [5. Logging](#5-logging)
- [6. Troubleshooting](#6-troubleshooting)
- [7. Known issues](#7-known-issues)

---

## 1. Choosing a connector

| | `JRedisConnectorV1` | `JRedisConnector` |
|---|---|---|
| Configured with | `java.util.Properties` | `JRedisConfig` + `JRedisPoolConfig` |
| Operations | get, set, set-with-TTL, delete | get, set, set-with-TTL, delete, incr/decr, per-call database |
| Failure handling | Retries connection errors (3 × 5 s), then **fails silently** | Throws `JRedisException` immediately |
| Checks Redis on init | Yes (`PING`) | No; errors appear on first use |
| Concurrency | Commands run in parallel across the pool | **All commands serialised JVM-wide** |
| ACL user (Redis 6+) | No | Yes |

**Use `JRedisConnectorV1`** unless you need counters, ACL users or per-call database
selection, and can accept the single-command-at-a-time throughput of `JRedisConnector`.

---

## 2. JRedisConnectorV1 (Properties-based)

### 2.1 Configuration

| Property | Default if missing or not numeric | Description |
|---|---|---|
| `redis.server.ip` | `localhost` | Redis host name or IP. |
| `redis.server.port` | `6379` | Redis port. |
| `redis.server.pwd` | none | Redis password (`AUTH`). Blank means no password. |
| `redis.server.db` | `1` | Database index. |
| `redis.max.threads` | `5` | Maximum connections in the pool. Size it to the number of threads that call Redis concurrently. |

The connection timeout is fixed at 2000 ms. The other pool settings are the Jedis defaults.

> **Set the `redis.server.*` properties explicitly in production.** A typo in a property name
> silently falls back to the default, e.g. connecting to `localhost` without a password.
> Load the password from an environment variable or secrets manager, not from a committed file.

A template is in [`src/main/resources/application.properties`](../src/main/resources/application.properties).

### 2.2 Lifecycle

```java
import com.plugin.cache.redis.JRedisConnectorV1;

public final class Cache {
    private static final JRedisConnectorV1 REDIS = new JRedisConnectorV1();

    /** Call once at application start-up. */
    public static void start(Properties props) {
        if (!REDIS.init(props)) {
            throw new IllegalStateException("Cannot connect to Redis at " + props.getProperty("redis.server.ip"));
        }
    }

    public static JRedisConnectorV1 redis() { return REDIS; }

    /** Call once at shutdown. */
    public static void stop() { REDIS.close(); }
}
```

- `init(Properties)` builds the pool and sends `PING`. If the connection fails it retries up to
  3 times, 5 s apart, so a down server **blocks the calling thread for about 10 s**. It returns
  `false` if every attempt fails.
- Non-connection errors such as a wrong password are **not** retried. They are thrown from
  `init` as an unchecked `JedisException`.
- `close()` closes the pool. Call it on shutdown.
- **Share one instance.** Every `init` creates a new pool. Creating a connector per request or
  per job leaks connections unless you call `close()` each time.

### 2.3 Operations

| Method | Redis command | Returns / notes |
|---|---|---|
| `setValue(key, value)` | `SET` | No expiry. |
| `setValueWithExpiry(key, value, ttl)` | `PSETEX` | **`ttl` is in milliseconds.** Pass `300_000` for 5 minutes. |
| `getValue(key)` | `GET` | The value, or `null`. |
| `deleteKey(key)` | `DEL` | No-op if the key is missing. |

```java
JRedisConnectorV1 redis = Cache.redis();

// Cache a JSON blob
redis.setValue("provider:7", "{\"smsTmdfId\":\"1102\"}");

// Short-lived value: 5 minutes = 300 000 ms
redis.setValueWithExpiry("dlr:pending:abc123", "1", TimeUnit.MINUTES.toMillis(5));

// Read it back. Always null-check.
String provider = redis.getValue("provider:7");
if (provider != null && provider.startsWith("{")) {
    // parse JSON
}

redis.deleteKey("dlr:pending:abc123");
```

### 2.4 Error behaviour

Each operation retries **connection errors only**, 3 times, 5 s apart. After the last failure:

- `setValue`, `setValueWithExpiry` and `deleteKey` **return normally**. The write was lost.
- `getValue` returns `null`, the same as for a missing key.

The only sign of the failure is a `Failed to … Attempt n of 3` line on `System.out`.
Other Redis errors, such as `WRONGTYPE`, are thrown to the caller as unchecked `JedisException`.

A Redis outage can therefore stall the calling thread for about 10 s per call, and data loss is
silent. If a write must succeed, follow it with a `getValue` check or use `JRedisConnector`.

---

## 3. JRedisConnector (config-object based)

### 3.1 Configuration

**`JRedisConfig`**

| Setter | Required | Description |
|---|---|---|
| `setRedisHost(String)` | Yes | A single host name or IP. Comma-separated lists are **not** supported. |
| `setRedisPort(String)` | Yes | Port as a string, e.g. `"6379"`. |
| `setRedisPwd(String)` | No | Password. |
| `setRedisUser(String)` | No | ACL user name (Redis 6+). Used only if a password is set too. |
| `setRedisDatabase(int)` | No | Default `0`. **Ignored unless a password is set.** |
| `setRedisPoolConfig(JRedisPoolConfig)` | No | Defaults are applied if not set. |
| `setRedisClusterModeEnabled` / `enableRedisClusterMode()` | — | Has no effect; cluster support is disabled. |

**`JRedisPoolConfig`**

| Setter | Default | Applied? |
|---|---|---|
| `setMaxTotal` | 128 | Yes |
| `setMaxIdle` | 64 | Yes |
| `setMinIdle` | 16 | Yes |
| `setMaxWaitInMillis` | 500 | Yes. Used as both the borrow wait **and** the connection/socket timeout. |
| `setMinEvictableIdleTimeMillis` | 200 | No. Always 60 s. |
| `setTimeBetweenEvictionRunsMillis` | 100 | No. Always 30 s. |
| `setBlockWhenExhausted` | true | No. Always blocks. |

Connections are tested on borrow, on return and while idle.

> Don't log a `JRedisConfig` object. Its `toString()` includes the password.
> Use `connector.getConnectionDetails()` instead.

### 3.2 Lifecycle and operations

```java
import com.plugin.cache.redis.JRedisConnector;
import com.plugin.cache.util.*;

JRedisPoolConfig pool = new JRedisPoolConfig();
pool.setMaxTotal(32);
pool.setMaxWaitInMillis(1000);

JRedisConfig cfg = new JRedisConfig();
cfg.setRedisHost("10.0.0.12");
cfg.setRedisPort("6379");
cfg.setRedisUser("gateway");     // optional, Redis 6 ACL
cfg.setRedisPwd("change-me");
cfg.setRedisDatabase(1);
cfg.setRedisPoolConfig(pool);

JRedisConnector redis = new JRedisConnector();
try {
    redis.init(cfg);                                   // does NOT contact Redis yet
    redis.setString("session:9", "active", 1800);      // SETEX, TTL in SECONDS
    redis.setString("counter:sms", "0");               // SET, no expiry
    redis.increment("counter:sms");                    // INCR (key must exist)
    redis.increment("counter:sms", 10);                // INCRBY
    String v  = redis.getString("session:9");          // null if missing
    String v2 = redis.getString("other", 3);           // GET from database 3
    redis.setString("k", "v", 60, 3);                  // SETEX in database 3
    redis.delete("session:9");
} catch (JRedisException e) {
    log.error("Redis failure: {}", e.getErrorDesc());
} finally {
    redis.deInit();
}
```

| Method | Redis command | Notes |
|---|---|---|
| `getString(key)` / `getString(key, db)` | `GET` | `null` if missing. The `db` variant switches database for the call only. |
| `setString(key, value)` | `SET` | No expiry. |
| `setString(key, value, ttl)` / `(…, ttl, db)` | `SETEX` | **`ttl` in seconds**, must be > 0. Throws if Redis does not reply `OK`. |
| `delete(key)` | `EXISTS` + `DEL` | No-op if missing. |
| `increment(key)` / `increment(key, n)` | `INCR` / `INCRBY` | **Does nothing if the key doesn't exist.** Create the counter with `setString(key, "0")` first. |
| `decrement(key)` / `decrement(key, n)` | `DECR` / `DECRBY` | Same as above. |
| `getConnection()` / `returnConnection(jedis)` | — | Raw Jedis access. Always return the connection in a `finally` block. |
| `getPoolStats()` | — | `{Idle: n, Busy: m}`. |
| `deInit()` | — | Closes the pool; you can call `init` again afterwards. |

### 3.3 Things to know

- **Throughput:** every data method holds a lock on `JRedisConnector.class`, so only one Redis
  command runs at a time in the whole JVM, however large the pool is. Don't use it on hot paths.
- **Noisy output:** each call prints pool stats to `System.out` twice.
- **Errors:** all failures are wrapped in `JRedisException`. `getErrorCode()` is always `null`.
  Use `getErrorDesc()` / `getMessage()`.

---

## 4. QUtil helpers

`com.plugin.cache.util.QUtil` contains static helpers shared across gateway plugins.

| Method | Description |
|---|---|
| `intVal(str, default)` / `longVal(str, default)` | Parses a number; returns `default` for `null` or invalid input. |
| `loadProperties(path, props)` | Loads a file into `props`. Returns `null` if the file can't be read. |
| `now(withMillis)` / `now(withMillis, date)` | Formats as `yyyy-MM-dd HH:mm:ss[.SSS]`. ⚠ not thread-safe |
| `getDate(withMillis, str)` | Parses the format above; `null` on error. ⚠ not thread-safe |
| `getDateString(millis)` | Epoch millis to `yyyy-MM-dd HH:mm:ss`. ⚠ not thread-safe |
| `millisToDate(millis)` | Same output as `getDateString`, but thread-safe. |
| `compareAndGetMaxDate(d1, d2, withMillis)` | Returns the later timestamp string; `d1` if either can't be parsed. |
| `getDiffMinutes(millis)` | Whole minutes elapsed since `millis`. |
| `getExtraparamslist("a=1&b=2")` | Returns `{a=1, b=2}`. Values are not URL-decoded. Pairs like `k=`, `k` or `k=a=b` are **dropped**. |
| `getExtraparamslistMap("a=1\|b=2")` | Returns one map per `\|`-separated segment. |
| `strEscape(str)` | MySQL-style backslash escaping. Not a substitute for prepared statements. |
| `addToBuffer(buf, n, comma)` | Appends a number, plus `,` if `comma` is true. |
| `addToBufferLog(buf, value, delim)` | Appends a log field. Strings are wrapped in `'…'` with inner `'` replaced by `"`. The separator is the log delimiter, described below. |

**Log delimiter.** It is chosen once, at class load time:

| `-Dqmanager.logger.enable` | `-Djava.logger.delimiter` | Delimiter |
|---|---|---|
| `1` (default) | unset | `0x16` (ASCII SYN control character) |
| `1` (default) | set | that value |
| anything else | — | `,` |

The `now`/`getDate`/`getDateString`/`compareAndGetMaxDate` methods share static
`SimpleDateFormat` instances. Under concurrent use they can return corrupted dates or throw.
Use `millisToDate` or `java.time.format.DateTimeFormatter` from multi-threaded code.

**Constants:** queue event ids `QCREATE_EVENTID` (1000), `QDELETE_EVENTID` (1001),
`QINACTIVE_EVENTID` (1002) with matching names (`"QCREATE"`, …), and severity bit flags
`SEVERITY_CRITICAL` (1), `SEVERITY_DEBUG` (2), `SEVERITY_WARNING` (4), `SEVERITY_INFO` (8).

---

## 5. Logging

- The connectors write status and errors to **`System.out`** and stack traces to `System.err`,
  not through log4j. Capture stdout in your service's log collection.
- `QUtil` logs date-parsing errors through **log4j2** (`LogManager.getLogger(QUtil.class)`).
  Configure it with a `log4j2.xml` in your service.
- The bundled `src/main/resources/log4j.xml` uses the log4j **1.x** format, which log4j2 ignores.

---

## 6. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `init` takes ~10 s and returns `false` | Redis host/port unreachable | Check `redis.server.ip` / `redis.server.port`, firewall and security groups. |
| `init` throws a `JedisDataException` (e.g. `JedisAccessControlException: WRONGPASS`, or `NOAUTH`) | Wrong or missing password | Check `redis.server.pwd`. A missing or blank property means no password is sent. |
| Keys expire almost immediately | Passed seconds to `setValueWithExpiry` | It takes **milliseconds**. Multiply by 1000. |
| `getValue` returns `null` for a key that exists | Redis was unreachable for all retries, or wrong `redis.server.db` | Look for `Failed to get value` on stdout. Check the database index. |
| Counter never increases (`JRedisConnector`) | Key didn't exist | Initialise it with `setString(key, "0")`. |
| Data lands in database 0 (`JRedisConnector`) | No password configured | The database index is applied only when a password is set. |
| `JRedisException: Exception in Getting Redis Connection` | Pool exhausted within `maxWaitInMillis`, or `init` wasn't called | Raise `maxWaitInMillis`/`maxTotal`; make sure every `getConnection()` has a matching `returnConnection()`. |
| Slow throughput with `JRedisConnector` | JVM-wide lock on every call | Use `JRedisConnectorV1`, or Jedis directly. |
| Connection count on the Redis server keeps growing | A new `JRedisConnectorV1` is created per job without `close()` | Share one instance, or call `close()` when finished. |

---

## 7. Known issues

These are current behaviours of version 1.0.0, documented so callers aren't surprised.

1. `JRedisConnectorV1.setValueWithExpiry` uses milliseconds although its parameter is named `MULTIPART_TTL_SECONDS`.
2. `JRedisConnectorV1` reports nothing when every retry fails.
3. `JRedisConnector` serialises all Redis calls through a single class-level lock.
4. `JRedisConnector.increment`/`decrement` don't create missing keys.
5. `JRedisConfig.toString()` prints the password.
6. Several `JRedisPoolConfig` settings and the cluster-mode flag are ignored.
7. `QUtil` date helpers aren't thread-safe.
8. `ICacheClient` has no implementation.
9. There are no automated tests yet.

Pull requests addressing any of these are welcome. See [CONTRIBUTING.md](../CONTRIBUTING.md).
