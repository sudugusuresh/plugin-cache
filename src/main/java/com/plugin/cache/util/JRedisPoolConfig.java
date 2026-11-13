package com.plugin.cache.util;

/**
 * Connection-pool settings for {@link com.plugin.cache.redis.JRedisConnector}.
 * <p>
 * Defaults: maxTotal 128, maxIdle 64, minIdle 16, maxWait 500 ms.
 * <p>
 * <b>How the connector applies these values:</b>
 * <ul>
 * <li>{@link #getMaxWaitInMillis()} is used both as the pool borrow wait and as the
 * Redis connection/socket timeout.</li>
 * <li>{@link #getMinEvictableIdleTimeMillis()} and {@link #getTimeBetweenEvictionRunsMillis()}
 * are overridden by fixed values (60 s and 30 s).</li>
 * <li>{@link #isBlockWhenExhausted()} is ignored; the pool always blocks when exhausted.</li>
 * </ul>
 */
public class JRedisPoolConfig {

	/** Maximum number of connections (active + idle) in the pool. */
	private int maxTotal; // maximum active connections (Default : 8)
	/** Maximum number of idle connections kept in the pool. */
	private int maxIdle; // maximum idle connections
	/** Minimum number of idle connections kept in the pool. */
	private int minIdle;
	/** Borrow wait and Redis connection/socket timeout, in milliseconds. */
	private int maxWaitInMillis;
	/** Minimum idle time before a connection may be evicted, in ms (overridden by the connector). */
	private int minEvictableIdleTimeMillis;
	/** Interval between idle-eviction runs, in ms (overridden by the connector). */
	private int timeBetweenEvictionRunsMillis;
	/** Whether borrowers block when the pool is exhausted (ignored by the connector). */
	private boolean blockWhenExhausted;

	/** Creates a configuration populated with the defaults listed in the class description. */
	public JRedisPoolConfig() {
		maxTotal = 128;
		maxIdle = 64;
		minIdle = 16;
		maxWaitInMillis = 500; // in millis (connection, socket and read timeout)
		minEvictableIdleTimeMillis = 200;
		timeBetweenEvictionRunsMillis = 100;
		blockWhenExhausted = true;
	}

	@Override
	public String toString() {
		return "JRedisPoolConfig [maxTotal=" + maxTotal + ", maxIdle=" + maxIdle + ", minIdle=" + minIdle
				+ ", maxWaitInMillis=" + maxWaitInMillis + ", minEvictableIdleTimeMillis=" + minEvictableIdleTimeMillis
				+ ", timeBetweenEvictionRunsMillis=" + timeBetweenEvictionRunsMillis + ", blockWhenExhausted="
				+ blockWhenExhausted + "]";
	}

	/** @return maximum total connections in the pool */
	public int getMaxTotal() {
		return maxTotal;
	}

	/** @param maxTotal maximum total connections in the pool */
	public void setMaxTotal(int maxTotal) {
		this.maxTotal = maxTotal;
	}

	/** @return maximum idle connections */
	public int getMaxIdle() {
		return maxIdle;
	}

	/** @param maxIdle maximum idle connections */
	public void setMaxIdle(int maxIdle) {
		this.maxIdle = maxIdle;
	}

	/** @return minimum idle connections */
	public int getMinIdle() {
		return minIdle;
	}

	/** @param minIdle minimum idle connections */
	public void setMinIdle(int minIdle) {
		this.minIdle = minIdle;
	}

	/** @return borrow wait and connection/socket timeout, in milliseconds */
	public int getMaxWaitInMillis() {
		return maxWaitInMillis;
	}

	/** @param maxWaitInMillis borrow wait and connection/socket timeout, in milliseconds */
	public void setMaxWaitInMillis(int maxWaitInMillis) {
		this.maxWaitInMillis = maxWaitInMillis;
	}

	/** @return minimum evictable idle time, in milliseconds */
	public int getMinEvictableIdleTimeMillis() {
		return minEvictableIdleTimeMillis;
	}

	/** @param minEvictableIdleTimeMillis minimum evictable idle time, in milliseconds */
	public void setMinEvictableIdleTimeMillis(int minEvictableIdleTimeMillis) {
		this.minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
	}

	/** @return interval between eviction runs, in milliseconds */
	public int getTimeBetweenEvictionRunsMillis() {
		return timeBetweenEvictionRunsMillis;
	}

	/** @param timeBetweenEvictionRunsMillis interval between eviction runs, in milliseconds */
	public void setTimeBetweenEvictionRunsMillis(int timeBetweenEvictionRunsMillis) {
		this.timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
	}

	/** @return whether borrowers should block when the pool is exhausted */
	public boolean isBlockWhenExhausted() {
		return blockWhenExhausted;
	}

	/** @param blockWhenExhausted whether borrowers should block when the pool is exhausted */
	public void setBlockWhenExhausted(boolean blockWhenExhausted) {
		this.blockWhenExhausted = blockWhenExhausted;
	}
}
