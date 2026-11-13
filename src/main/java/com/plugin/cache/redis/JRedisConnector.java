package com.plugin.cache.redis;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.plugin.cache.util.JRedisConfig;
import com.plugin.cache.util.JRedisException;
import com.plugin.cache.util.JRedisPoolConfig;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Pooled Redis client for a single Redis server, configured through {@link JRedisConfig}.
 * <p>
 * Typical use:
 * <pre>
 * JRedisConnector redis = new JRedisConnector();
 * redis.init(config);            // builds the JedisPool
 * redis.setString("k", "v", 60); // SETEX with TTL in seconds
 * String v = redis.getString("k");
 * redis.deInit();                // closes the pool
 * </pre>
 * <p>
 * <b>Behaviour to be aware of:</b>
 * <ul>
 * <li>Every data operation holds a lock on {@code JRedisConnector.class} while it borrows the
 * connection and runs the command. Calls are therefore serialised across <i>all</i> instances
 * in the JVM, so the pool never serves more than one command at a time.</li>
 * <li>{@code increment}/{@code decrement} do nothing if the key does not exist; they never
 * create a counter.</li>
 * <li>Each borrow and return prints pool statistics to {@code System.out}.</li>
 * <li>Cluster mode is not supported (that code is commented out); a comma-separated host
 * list is not supported either.</li>
 * </ul>
 */
public class JRedisConnector {

	/** Class logger (currently unused; output goes to {@code System.out}). */
	Logger logger = LogManager.getLogger(JRedisConnector.class);
	/** Unused; this class is not a singleton. */
	private static JRedisConnector _instance = null;
	/** Pool settings built from {@link JRedisPoolConfig} during {@link #init(JRedisConfig)}. */
	private JedisPoolConfig g_objJedisPoolConfig = null;
	/** The underlying connection pool; {@code null} until {@link #init(JRedisConfig)} succeeds. */
	private JedisPool g_objJedisPool = null;
//	private JedisCluster g_objJedisCluster = null;
	/** Configuration supplied to {@link #init(JRedisConfig)}. */
	private JRedisConfig redisConfig = null;

	/** Creates an uninitialised connector. Call {@link #init(JRedisConfig)} before use. */
	public JRedisConnector() {
	}

	/** @return the same value as {@link #getConnectionDetails()} */
	@Override
	public String toString() {
		return getConnectionDetails();
	}

	/**
	 * Describes the configured connection (host, port, user, database, timeout) for logging.
	 * The password is not included.
	 *
	 * @return the connection summary, or an empty string before {@link #init(JRedisConfig)}
	 */
	public String getConnectionDetails() {
		StringBuilder sbRedisConnectionInfo = new StringBuilder();
		if (redisConfig != null) {
			sbRedisConnectionInfo.append("host=").append(redisConfig.getRedisHost()).append(", port=")
					.append(redisConfig.getRedisPort()).append(", user=").append(redisConfig.getRedisUser())
					.append(", database=").append(redisConfig.getRedisDatabase()).append(", connection-timeout=")
					.append(redisConfig.getRedisPoolConfig().getMaxWaitInMillis());
		}
		return sbRedisConnectionInfo.toString();
	}

	/**
	 * Builds the connection pool from the given configuration.
	 * <p>
	 * Authentication is chosen from what is set: user + password, password only, or none.
	 * Without a password the configured database index is ignored and database {@code 0} is used.
	 * {@link JRedisPoolConfig#getMaxWaitInMillis()} is used as the connection timeout.
	 * <p>
	 * The pool is created lazily by Jedis, so an unreachable server is <i>not</i> detected
	 * here; it surfaces on the first {@link #getConnection()}.
	 *
	 * @param redisConfig connection settings; must not be {@code null} and must have a port
	 * @throws JRedisException if the configuration is missing or invalid, or the pool cannot be created
	 */
	public void init(JRedisConfig redisConfig) throws JRedisException {
		try {
			if (redisConfig == null) {
				throw new JRedisException("oops....Redis Configuration not defined!!");
			}
			this.redisConfig = redisConfig;
			String strHost = redisConfig.getRedisHost();
			String strRedisUser = redisConfig.getRedisUser();
			String strRedisPwd = redisConfig.getRedisPwd();
			if (redisConfig.getRedisPort() == null || redisConfig.getRedisPort().isEmpty()) {
				throw new JRedisException("Redis port not defined. its mandatory.");
			}
			int nPort = Integer.parseInt(redisConfig.getRedisPort());
			Set<HostAndPort> setNodes = new HashSet();
			if (strHost.contains(",")) {
				String[] arrNodes = strHost.split(",");
				for (String strNode : arrNodes) {
					setNodes.add(new HostAndPort(strNode.toString(), nPort));
				}
			} else {
				setNodes.add(new HostAndPort(strHost.toString(), nPort));
			}
			/* create redis pool configurations */
			buildJedisPoolConfig(redisConfig.getRedisPoolConfig());
			/* check redis cluster mode enabled */
//			if (redisConfig.isRedisClusterModeEnabled()) {
//				/* create redis cluster */
//				createRedisCluster(setNodes);
//			}
//			if (g_objJedisCluster != null) {
//				g_objJedisPoolConfig.setMaxWait(Duration.ofMillis(nRedisConnectionTimeout));
//				g_objJedisPoolConfig.setMaxTotal(cacheConfig.getMaster().getMaxConnections());
//				g_objJedisPool = new JedisPool(g_objJedisPoolConfig);
//			} else 
			int nRedisConnectionTimeoutInMills = redisConfig.getRedisPoolConfig().getMaxWaitInMillis();
			if (strRedisUser != null && !strRedisUser.isEmpty() && strRedisPwd != null && !strRedisPwd.isEmpty()) {
				g_objJedisPool = new JedisPool(g_objJedisPoolConfig, strHost, nPort, nRedisConnectionTimeoutInMills,
						strRedisUser, strRedisPwd, redisConfig.getRedisDatabase());
			} else if (strRedisPwd != null && !strRedisPwd.isEmpty()) {
				g_objJedisPool = new JedisPool(g_objJedisPoolConfig, strHost, nPort, nRedisConnectionTimeoutInMills,
						strRedisPwd, redisConfig.getRedisDatabase());
			} else {
				g_objJedisPool = new JedisPool(g_objJedisPoolConfig, strHost, nPort, nRedisConnectionTimeoutInMills);
			}
			/* validate pool */
			if (g_objJedisPool == null) {
				deInit();
				throw new JRedisException("Unable to Create Redis Pool");
			}
			System.out.println("Redis Connector Initialization... Success");
		} catch (JRedisException e) {
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			throw new JRedisException("Redis Exception: " + e.getMessage());
		}
	}

	/**
	 * Closes the connection pool and discards the pool settings. Errors are silently ignored.
	 * The connector may be re-initialised afterwards with {@link #init(JRedisConfig)}.
	 */
	public void deInit() {
		try {
			g_objJedisPoolConfig = null;
			if (g_objJedisPool != null) {
				g_objJedisPool.close();
				g_objJedisPool.destroy();
			}
//			if (g_objJedisCluster != null) {
//				g_objJedisPool.close();
//				g_objJedisPool.destroy();
//			}
		} catch (Exception e) {
			// TODO: handle exception
		}
	}

	/**
	 * Creates {@link #g_objJedisPoolConfig} from the given settings, once per {@code init}.
	 * <p>
	 * Uses maxTotal, maxIdle, minIdle and maxWait from {@code redisPoolConfig}. Eviction is
	 * fixed at 60 s minimum idle time and 30 s between runs (3 tests per run), regardless of
	 * the configured values. Connections are validated on borrow, on return and while idle,
	 * and borrowers always block when the pool is exhausted.
	 *
	 * @param redisPoolConfig pool settings; if {@code null}, defaults are created and stored back
	 *                        into the connector's {@link JRedisConfig}
	 * @throws JRedisException if the pool settings cannot be applied
	 */
	private void buildJedisPoolConfig(JRedisPoolConfig redisPoolConfig) throws JRedisException {
		try {
			if (g_objJedisPoolConfig == null) {
				if (redisPoolConfig == null) {
					redisPoolConfig = new JRedisPoolConfig();
					redisConfig.setRedisPoolConfig(redisPoolConfig);
				}
				/* create redis pool configuration */
				g_objJedisPoolConfig = new JedisPoolConfig();
				g_objJedisPoolConfig.setMaxTotal(redisPoolConfig.getMaxTotal());
				g_objJedisPoolConfig.setMaxIdle(redisPoolConfig.getMaxIdle());
				g_objJedisPoolConfig.setMinIdle(redisPoolConfig.getMinIdle());
				g_objJedisPoolConfig.setMaxWait(Duration.ofMillis(redisPoolConfig.getMaxWaitInMillis())); // in millis
				g_objJedisPoolConfig.setTimeBetweenEvictionRuns(
						Duration.ofMillis(redisPoolConfig.getTimeBetweenEvictionRunsMillis()));
				g_objJedisPoolConfig.setNumTestsPerEvictionRun(3);
				g_objJedisPoolConfig.setMinEvictableIdleTimeMillis(redisPoolConfig.getMinEvictableIdleTimeMillis());
				g_objJedisPoolConfig.setMinEvictableIdleTimeMillis(Duration.ofSeconds(60).toMillis());
				g_objJedisPoolConfig.setTimeBetweenEvictionRunsMillis(Duration.ofSeconds(30).toMillis());
				g_objJedisPoolConfig.setTestOnBorrow(true);
				g_objJedisPoolConfig.setTestOnReturn(true);
				g_objJedisPoolConfig.setTestWhileIdle(true);
				g_objJedisPoolConfig.setBlockWhenExhausted(true);
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new JRedisException("Exception in Building Redis Pool Configurations: " + e.getMessage());
		}
	}

	/**
	 * Borrows a connection from the pool. The caller must hand it back with
	 * {@link #returnConnection(Jedis)}, otherwise the pool leaks connections.
	 * Blocks up to the configured maxWait if the pool is exhausted.
	 *
	 * @return a pooled connection
	 * @throws JRedisException if the connector is not initialised, the pool is exhausted, or
	 *                         the server cannot be reached
	 */
	public Jedis getConnection() throws JRedisException {
		Jedis jedisCon = null;
		try {
			jedisCon = g_objJedisPool.getResource();
			System.out.println("Redis Pool Stats(On Get): " + getPoolStats());
			// do operations with jedis resource
		} catch (Exception e) {
			e.printStackTrace();
			throw new JRedisException("Exception in Getting Redis Connection: " + e.getMessage());
		}
		return jedisCon;
	}

	/**
	 * Returns a connection obtained from {@link #getConnection()} to the pool.
	 * Safe to call with {@code null}; errors are printed and swallowed.
	 *
	 * @param jedisCon the connection to return, or {@code null}
	 */
	public void returnConnection(Jedis jedisCon) {
		try {
			if (jedisCon != null) {
				jedisCon.close();
				System.out.println("Redis Pool Stats(On Return): " + getPoolStats());
			}
		} catch (Exception e) {
			System.out.println("Exception in Returning Redis Connection: " + e.getMessage());
			e.printStackTrace();
		}
	}

//	private void createRedisCluster(Set<HostAndPort> setNodes) throws JRedisConnectorException {
//		try {
//			g_objJedisCluster = new JedisCluster(setNodes);
//			// use the jedisCluster resource as if it was a normal Jedis resource
//		} catch (Exception e) {
//			e.printStackTrace();
//			throw new JRedisConnectorException("Exception in Creating Redis Cluster: " + e.getMessage());
//		}
//	}

	/**
	 * @return {@code "{Idle: n, Busy: m}"} for the pool, or {@code "Not Available"} before
	 *         {@link #init(JRedisConfig)}
	 */
	public String getPoolStats() {
		String strPoolStats = "Not Available";
		if (g_objJedisPool != null) {
			strPoolStats = "{Idle: " + g_objJedisPool.getNumIdle() + ", Busy: " + g_objJedisPool.getNumActive() + "}";
		}
		return strPoolStats;
	}

	/**
	 * Reads a string value ({@code GET}) from the configured database.
	 *
	 * @param strKey the key
	 * @return the value, or {@code null} if the key does not exist
	 * @throws JRedisException if the command fails
	 */
	public String getString(String strKey) throws JRedisException {
		Jedis jedisCon = null;
		String strValue = "";
		try {
			synchronized (JRedisConnector.class) {
				jedisCon = getConnection();
				if (jedisCon != null) {
					strValue = jedisCon.get(strKey);
				}
			}
		} catch (Exception e) {
			System.out.println("Exception in Getting Value from Redis: " + e.getMessage());
			e.printStackTrace();
			throw new JRedisException("Exception in Getting Value from Redis: " + e.getMessage());
		} finally {
			returnConnection(jedisCon);
		}
		return strValue;
	}

	/**
	 * Reads a string value ({@code GET}) from a specific database. The connection is switched
	 * to {@code dbNo} for the read and switched back afterwards.
	 *
	 * @param strKey the key
	 * @param dbNo   the database index to read from
	 * @return the value, or {@code null} if the key does not exist
	 * @throws JRedisException if the command fails
	 */
	public String getString(String strKey, int dbNo) throws JRedisException {
		Jedis jedisCon = null;
		String strValue = "";
		try {
			synchronized (JRedisConnector.class) {
				jedisCon = getConnection();
				if (jedisCon != null) {
					int prvDbNo = jedisCon.getDB();
					jedisCon.select(dbNo);
					strValue = jedisCon.get(strKey);
					jedisCon.select(prvDbNo);
				}
			}
		} catch (Exception e) {
			System.out.println("Exception in Getting Value from Redis: " + e.getMessage());
			e.printStackTrace();
			throw new JRedisException("Exception in Getting Value from Redis: " + e.getMessage());
		} finally {
			returnConnection(jedisCon);
		}
		return strValue;
	}

	/**
	 * Stores a string value with no expiry ({@code SET}) in the configured database.
	 *
	 * @param strKey   the key
	 * @param strValue the value
	 * @throws JRedisException if the command fails
	 */
	public void setString(String strKey, String strValue) throws JRedisException {
		Jedis jedisCon = null;
		try {
			synchronized (JRedisConnector.class) {
				jedisCon = getConnection();
				if (jedisCon != null) {
					jedisCon.set(strKey, strValue);
				}
			}
		} catch (Exception e) {
			System.out.println("Exception in Setting Value to Redis: " + e.getMessage());
			e.printStackTrace();
			throw new JRedisException("Exception in Setting Value to Redis: " + e.getMessage());
		} finally {
			returnConnection(jedisCon);
		}
	}

	/**
	 * Stores a string value with an expiry ({@code SETEX}) in the configured database.
	 *
	 * @param strKey   the key
	 * @param strValue the value
	 * @param ttl      time to live, in <b>seconds</b>; must be greater than 0
	 * @throws JRedisException if the command fails or Redis does not reply {@code OK}
	 */
	public void setString(String strKey, String strValue, int ttl) throws JRedisException {
		Jedis jedisCon = null;
		String strResponse = null;
		try {
			synchronized (JRedisConnector.class) {
				jedisCon = getConnection();
				if (jedisCon != null) {
					strResponse = jedisCon.setex(strKey, ttl, strValue);
				}
			}
			if (strResponse != null && !strResponse.equals("OK")) {
				throw new JRedisException("UNABLE TO SET VALUE TO CAHE");
			}
		} catch (Exception e) {
			System.out.println("Exception in Setting Value to Redis: " + e.getMessage());
			e.printStackTrace();
			throw new JRedisException("Exception in Setting Value to Redis: " + e.getMessage());
		} finally {
			returnConnection(jedisCon);
		}
	}

	/**
	 * Stores a string value with an expiry ({@code SETEX}) in a specific database. The
	 * connection is switched to {@code dbNo} for the write and switched back afterwards.
	 *
	 * @param strKey   the key
	 * @param strValue the value
	 * @param ttl      time to live, in <b>seconds</b>; must be greater than 0
	 * @param dbNo     the database index to write to
	 * @throws JRedisException if the command fails or Redis does not reply {@code OK}
	 */
	public void setString(String strKey, String strValue, int ttl, int dbNo) throws JRedisException {
		Jedis jedisCon = null;
		String strResponse = null;
		try {
			synchronized (JRedisConnector.class) {
				jedisCon = getConnection();
				if (jedisCon != null) {
					int prevDBNo = jedisCon.getDB();
					jedisCon.select(dbNo);
					strResponse = jedisCon.setex(strKey, ttl, strValue);
					jedisCon.select(prevDBNo);
				}
			}
			if (strResponse != null && !strResponse.equals("OK")) {
				throw new JRedisException("UNABLE TO SET VALUE TO CAHE");
			}
		} catch (Exception e) {
			System.out.println("Exception in Setting Value to Redis: " + e.getMessage());
			e.printStackTrace();
			throw new JRedisException("Exception in Setting Value to Redis: " + e.getMessage());
		} finally {
			returnConnection(jedisCon);
		}
	}

	/**
	 * Deletes a key ({@code DEL}) if it exists. Does nothing if the key is absent.
	 *
	 * @param strKey the key
	 * @throws JRedisException if the command fails
	 */
	public void delete(String strKey) throws JRedisException {
		Jedis jedisCon = null;
		try {
			synchronized (JRedisConnector.class) {
				jedisCon = getConnection();
				if (jedisCon != null && jedisCon.exists(strKey)) {
					jedisCon.del(strKey);
				}
			}
		} catch (Exception e) {
			System.out.println("Exception in Setting Value to Redis: " + e.getMessage());
			e.printStackTrace();
			throw new JRedisException("Exception in Setting Value to Redis: " + e.getMessage());
		} finally {
			returnConnection(jedisCon);
		}
	}

	/**
	 * Adds 1 to an existing integer value ({@code INCR}).
	 * <b>Does nothing if the key does not exist</b>; the counter is never created here.
	 *
	 * @param strKey the key
	 * @throws JRedisException if the command fails or the value is not an integer
	 */
	public void increment(String strKey) throws JRedisException {
		Jedis jedisCon = null;
		try {
			synchronized (JRedisConnector.class) {
				jedisCon = getConnection();
				if (jedisCon != null && jedisCon.exists(strKey)) {
					jedisCon.incr(strKey);
				}
			}
		} catch (Exception e) {
			System.out.println("Exception in Incrementing Value to Redis: " + e.getMessage());
			e.printStackTrace();
			throw new JRedisException("Exception in Incrementing Value to Redis: " + e.getMessage());
		} finally {
			returnConnection(jedisCon);
		}
	}

	/**
	 * Adds {@code incrBy} to an existing integer value ({@code INCRBY}).
	 * <b>Does nothing if the key does not exist</b>; the counter is never created here.
	 *
	 * @param strKey the key
	 * @param incrBy the amount to add
	 * @throws JRedisException if the command fails or the value is not an integer
	 */
	public void increment(String strKey, long incrBy) throws JRedisException {
		Jedis jedisCon = null;
		try {
			synchronized (JRedisConnector.class) {
				jedisCon = getConnection();
				if (jedisCon != null && jedisCon.exists(strKey)) {
					jedisCon.incrBy(strKey, incrBy);
				}
			}
		} catch (Exception e) {
			System.out.println("Exception in Incrementing Value to Redis: " + e.getMessage());
			e.printStackTrace();
			throw new JRedisException("Exception in Incrementing Value to Redis: " + e.getMessage());
		} finally {
			returnConnection(jedisCon);
		}
	}

	/**
	 * Subtracts 1 from an existing integer value ({@code DECR}).
	 * <b>Does nothing if the key does not exist</b>; the counter is never created here.
	 *
	 * @param strKey the key
	 * @throws JRedisException if the command fails or the value is not an integer
	 */
	public void decrement(String strKey) throws JRedisException {
		Jedis jedisCon = null;
		try {
			synchronized (JRedisConnector.class) {
				jedisCon = getConnection();
				if (jedisCon != null && jedisCon.exists(strKey)) {
					jedisCon.decr(strKey);
				}
			}
		} catch (Exception e) {
			System.out.println("Exception in Decrementing Value to Redis: " + e.getMessage());
			e.printStackTrace();
			throw new JRedisException("Exception in Decrementing Value to Redis: " + e.getMessage());
		} finally {
			returnConnection(jedisCon);
		}
	}

	/**
	 * Subtracts {@code decrBy} from an existing integer value ({@code DECRBY}).
	 * <b>Does nothing if the key does not exist</b>; the counter is never created here.
	 *
	 * @param strKey the key
	 * @param decrBy the amount to subtract
	 * @throws JRedisException if the command fails or the value is not an integer
	 */
	public void decrement(String strKey, long decrBy) throws JRedisException {
		Jedis jedisCon = null;
		try {
			synchronized (JRedisConnector.class) {
				jedisCon = getConnection();
				if (jedisCon != null && jedisCon.exists(strKey)) {
					jedisCon.decrBy(strKey, decrBy);
				}
			}
		} catch (Exception e) {
			System.out.println("Exception in Decrementing Value to Redis: " + e.getMessage());
			e.printStackTrace();
			throw new JRedisException("Exception in Decrementing Value to Redis: " + e.getMessage());
		} finally {
			returnConnection(jedisCon);
		}
	}

//	public void resetDb() throws JRedisConnectorException {
//		Jedis jedisCon = null;
//		try {
//			synchronized (JRedisConnector.class) {
//				jedisCon = getConnection();
//				if (jedisCon != null) {
//					jedisCon.select(0);
//				}
//			}
//		} catch (Exception e) {
//			System.out.println("Exception in ReSetting DB Number in Redis: " + e.getMessage());
//			e.printStackTrace();
//			throw new JRedisConnectorException("Exception in ReSetting DB Number in Redis: " + e.getMessage());
//		} finally {
//			returnConnection(jedisCon);
//		}
//	}

//	public void selectDb(int dbNo) throws JRedisConnectorException {
//		Jedis jedisCon = null;
//		try {
//			synchronized (JRedisConnector.class) {
//				jedisCon = getConnection();
//				if (jedisCon != null) {
//					jedisCon.select(dbNo);
//				}
//			}
//		} catch (Exception e) {
//			System.out.println("Exception in Setting DB Number in Redis: " + e.getMessage());
//			e.printStackTrace();
//			throw new JRedisConnectorException("Exception in Setting DB Number in Redis: " + e.getMessage());
//		} finally {
//			returnConnection(jedisCon);
//		}
//	}

//	private void testConnectToCluster() throws JRedisConnectorException {
//		try {
//			Set<HostAndPort> jedisClusterNodes = new HashSet();
//			/* Jedis Cluster will attempt to discover cluster nodes automatically */
//			jedisClusterNodes.add(new HostAndPort("127.0.0.1", 7379));
//			JedisCluster jc = new JedisCluster(jedisClusterNodes);
//			jc.set("foo", "bar");
//			String value = jc.get("foo");
//			jc.close();
//		} catch (Exception e) {
//			e.printStackTrace();
//			throw new JRedisConnectorException("Exception in Creating Redis Cluster: " + e.getMessage());
//		}
//	}
//
//	private void tesRedisConnect(String ip, int port) throws JRedisConnectorException {
//		try {
//			Jedis jedis = new Jedis(ip, port);
//			jedis.set("foo", "bar");
//			String value = jedis.get("foo");
//			jedis.close();
//		} catch (Exception e) {
//			e.printStackTrace();
//			throw new JRedisConnectorException("Exception in Creating Redis Cluster: " + e.getMessage());
//		}
//	}
}

