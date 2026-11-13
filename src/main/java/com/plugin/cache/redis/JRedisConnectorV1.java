package com.plugin.cache.redis;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import com.plugin.cache.util.QUtil;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisConnectionException;

/**
 * Simple pooled Redis client configured from {@link Properties}, with retry on connection errors.
 * <p>
 * Supports get, set, set-with-expiry and delete. Each operation is retried up to
 * 3 times, 5 s apart ({@code MAX_RETRY_ATTEMPTS}, {@code RETRY_DELAY_SECONDS}), but <b>only</b> for
 * {@link JedisConnectionException}; other Redis errors propagate to the caller.
 * <p>
 * <b>Behaviour to be aware of:</b>
 * <ul>
 * <li>When all retries fail, write operations return silently and {@link #getValue(String)}
 * returns {@code null}, so the caller cannot tell a failure from a missing key.</li>
 * <li>The pool is rebuilt only if {@link #init(Properties)} itself failed. After a successful
 * init, recovery relies on Jedis opening fresh connections from the existing pool.</li>
 * <li>Data operations are safe to call from multiple threads (the Jedis pool is thread-safe),
 * but {@code init}, reconnection and {@link #close()} are not synchronised.</li>
 * <li>Missing properties fall back to the defaults below: {@code localhost:6379},
 * no password, database 1.</li>
 * </ul>
 * <p>
 * This class is independent of {@link JRedisConnector} and does not use
 * {@link com.plugin.cache.util.JRedisConfig}.
 *
 * @author Sudugu Suresh
 * @version 1.0
 */
public class JRedisConnectorV1 {

	/** Default Redis server host/IP address */
	private static final String REDIS_HOST = "localhost";

	/** Default Redis server port */
	private static final int REDIS_PORT = 6379;

	/** Default Redis server password: none (no {@code AUTH} is sent) */
	private static final String REDIS_PASSWORD = null;
	
	/** Default Redis database index (0-15) */
	private static final int REDIS_DB = 1;
	
	/** Maximum number of retry attempts for failed operations */
	private static final int MAX_RETRY_ATTEMPTS = 3;
	
	/** Delay in seconds between retry attempts */
	private static final int RETRY_DELAY_SECONDS = 5;

	/** Jedis connection pool for managing Redis connections */
	private JedisPool jedisPool;
	
	/** Connection properties for Redis configuration */
	private Properties props;
	
	/** Flag indicating current connection status */
	private boolean isConnected = false;

	/**
	 * Default constructor for JRedisConnectorV1.
	 * Note: init() must be called after construction to establish connection.
	 */
	public JRedisConnectorV1() {
	}

	/**
	 * Initializes the Redis connection with the provided properties.
	 * 
	 * @param props Properties containing Redis configuration:
	 *              - redis.max.threads: Maximum number of threads in pool (default: 5)
	 *              - redis.server.ip: Redis server IP (default: localhost)
	 *              - redis.server.port: Redis server port (default: 6379)
	 *              - redis.server.pwd: Redis server password (default: none; blank also means none)
	 *              - redis.server.db: Redis database index (default: 1)
	 *              Non-numeric values fall back to the defaults. The connection timeout is fixed at 2000 ms.
	 * @return true if a test {@code PING} succeeds, false if every attempt fails with a
	 *         connection error. Blocks for up to about 10 s while retrying.
	 * @throws redis.clients.jedis.exceptions.JedisException for non-connection errors such as
	 *         a wrong password (these are not retried)
	 */
	public boolean init(Properties props) {
		this.props = props;
		return connectWithRetry();
	}

	/**
	 * Attempts to establish a Redis connection with retry mechanism.
	 * Will retry up to MAX_RETRY_ATTEMPTS times with RETRY_DELAY_SECONDS delay between attempts.
	 * Each attempt creates a new {@link JedisPool}; a pool from an earlier failed attempt is not closed.
	 *
	 * @return true if connection is successful, false if all retry attempts fail
	 */
	private boolean connectWithRetry() {
		int attempts = 0;
		while (attempts < MAX_RETRY_ATTEMPTS) {
			try {
				JedisPoolConfig poolConfig = new JedisPoolConfig();
				int nMaxTotal = QUtil.intVal(props.getProperty("redis.max.threads"), 5);
				poolConfig.setMaxTotal(nMaxTotal);

				String strIP = props.getProperty("redis.server.ip", REDIS_HOST);
				int nport = QUtil.intVal(props.getProperty("redis.server.port"), REDIS_PORT);
				String strPwd = props.getProperty("redis.server.pwd", REDIS_PASSWORD);
				if (strPwd != null && strPwd.trim().isEmpty()) {
					strPwd = null; // blank means "no password"
				}
				int RedisDB = QUtil.intVal(props.getProperty("redis.server.db"), REDIS_DB);
				
				jedisPool = new JedisPool(poolConfig, strIP, nport, 2000, strPwd, RedisDB);
				
				// Test connection
				try (Jedis jedis = jedisPool.getResource()) {
					jedis.ping();
				}
				
				isConnected = true;
				System.out.println("Connected to Redis successfully");
				return true;
			} catch (JedisConnectionException e) {
				attempts++;
				System.out.println("Failed to connect to Redis. Attempt " + attempts + " of " + MAX_RETRY_ATTEMPTS);
				if (attempts < MAX_RETRY_ATTEMPTS) {
					try {
						TimeUnit.SECONDS.sleep(RETRY_DELAY_SECONDS);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						return false;
					}
				}
			}
		}
		isConnected = false;
		System.out.println("Failed to connect to Redis after " + MAX_RETRY_ATTEMPTS + " attempts");
		return false;
	}

	/**
	 * Ensures that a valid Redis connection exists.
	 * If not connected, attempts to reconnect using connectWithRetry().
	 */
	private void ensureConnection() {
		if (!isConnected) {
			connectWithRetry();
		}
	}

	/**
	 * Sets a value in Redis for the specified key, with no expiry ({@code SET}).
	 * Includes automatic reconnection and retry logic. Returns silently if all attempts fail.
	 * 
	 * @param key The key to set
	 * @param value The value to associate with the key
	 */
	public void setValue(String key, String value) {
		ensureConnection();
		int attempts = 0;
		while (attempts < MAX_RETRY_ATTEMPTS) {
			try (Jedis jedis = jedisPool.getResource()) {
				jedis.set(key, value);
				return;
			} catch (JedisConnectionException e) {
				attempts++;
				System.out.println("Failed to set value in Redis. Attempt " + attempts + " of " + MAX_RETRY_ATTEMPTS);
				if (attempts < MAX_RETRY_ATTEMPTS) {
					try {
						TimeUnit.SECONDS.sleep(RETRY_DELAY_SECONDS);
						ensureConnection();
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						return;
					}
				}
			}
		}
	}

	/**
	 * Sets a value in Redis with an expiration time ({@code PSETEX}).
	 * Includes automatic reconnection and retry logic.
	 *
	 * @param key The key to set
	 * @param value The value to associate with the key
	 * @param MULTIPART_TTL_SECONDS The time-to-live in <b>milliseconds</b> ({@code PSETEX} is used),
	 *        despite the parameter name
	 */
	public void setValueWithExpiry(String key, String value, long MULTIPART_TTL_SECONDS) {
		ensureConnection();
		int attempts = 0;
		while (attempts < MAX_RETRY_ATTEMPTS) {
			try (Jedis jedis = jedisPool.getResource()) {
				jedis.psetex(key, MULTIPART_TTL_SECONDS, value);
				return;
			} catch (JedisConnectionException e) {
				attempts++;
				System.out.println("Failed to set value with expiry in Redis. Attempt " + attempts + " of " + MAX_RETRY_ATTEMPTS);
				if (attempts < MAX_RETRY_ATTEMPTS) {
					try {
						TimeUnit.SECONDS.sleep(RETRY_DELAY_SECONDS);
						ensureConnection();
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						return;
					}
				}
			}
		}
	}

	/**
	 * Retrieves a value from Redis for the specified key.
	 * Includes automatic reconnection and retry logic.
	 * 
	 * @param key The key to retrieve
	 * @return The value associated with the key, or null if not found or if all retry attempts fail
	 */
	public String getValue(String key) {
		ensureConnection();
		int attempts = 0;
		while (attempts < MAX_RETRY_ATTEMPTS) {
			try (Jedis jedis = jedisPool.getResource()) {
				return jedis.get(key);
			} catch (JedisConnectionException e) {
				attempts++;
				System.out.println("Failed to get value from Redis. Attempt " + attempts + " of " + MAX_RETRY_ATTEMPTS);
				if (attempts < MAX_RETRY_ATTEMPTS) {
					try {
						TimeUnit.SECONDS.sleep(RETRY_DELAY_SECONDS);
						ensureConnection();
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						return null;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Deletes a key from Redis ({@code DEL}). No-op if the key does not exist.
	 * Includes automatic reconnection and retry logic. Returns silently if all attempts fail.
	 * 
	 * @param key The key to delete
	 */
	public void deleteKey(String key) {
		ensureConnection();
		int attempts = 0;
		while (attempts < MAX_RETRY_ATTEMPTS) {
			try (Jedis jedis = jedisPool.getResource()) {
				jedis.del(key);
				return;
			} catch (JedisConnectionException e) {
				attempts++;
				System.out.println("Failed to delete key from Redis. Attempt " + attempts + " of " + MAX_RETRY_ATTEMPTS);
				if (attempts < MAX_RETRY_ATTEMPTS) {
					try {
						TimeUnit.SECONDS.sleep(RETRY_DELAY_SECONDS);
						ensureConnection();
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						return;
					}
				}
			}
		}
	}

	/**
	 * Closes the Redis connection pool and releases all resources.
	 * Should be called when the connector is no longer needed.
	 */
	public void close() {
		if (jedisPool != null) {
			jedisPool.close();
			isConnected = false;
		}
	}
}
