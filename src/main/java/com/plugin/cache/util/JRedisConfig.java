package com.plugin.cache.util;

/**
 * Connection settings for {@link com.plugin.cache.redis.JRedisConnector}.
 * <p>
 * Authentication is chosen from what is set: user + password (Redis 6 ACL),
 * password only, or none. The database index defaults to {@code 0}.
 * <p>
 * <b>Caution:</b> {@link #toString()} includes the password in plain text, so do not log
 * this object as-is.
 */
public class JRedisConfig {

	/** Redis host name or IP address. Must be a single host. */
	private String redisHost;
	/** Redis port as a string; mandatory, parsed with {@link Integer#parseInt(String)}. */
	private String redisPort;
	/** ACL user name; optional, used only when a password is also set. */
	private String redisUser;
	/** Redis password; optional. */
	private String redisPwd;
	/** Database index selected on each connection (default {@code 0}). */
	private int redisDatabase;
	/** Cluster-mode flag. Currently not used by the connector (cluster support is disabled). */
	private boolean redisClusterModeEnabled;
	/** Pool settings; created with defaults on first access if not set. */
	private JRedisPoolConfig redisPoolConfig;

	/** Returns all fields, <b>including the password</b>. */
	@Override
	public String toString() {
		return "JRedisConfig [redisHost=" + redisHost + ", redisPort=" + redisPort + ", redisUser=" + redisUser
				+ ", redisPwd=" + redisPwd + ", redisDatabase=" + redisDatabase + ", redisClusterModeEnabled="
				+ redisClusterModeEnabled + ", redisPoolConfig=" + redisPoolConfig + "]";
	}

	/** Creates a configuration with database {@code 0} and cluster mode off. */
	public JRedisConfig() {
		redisClusterModeEnabled = false;
		redisDatabase = 0;
	}

	/** @return the Redis host name or IP address */
	public String getRedisHost() {
		return redisHost;
	}

	/** @param redisHost the Redis host name or IP address (single host) */
	public void setRedisHost(String redisHost) {
		this.redisHost = redisHost;
	}

	/** @return the Redis port as a string */
	public String getRedisPort() {
		return redisPort;
	}

	/** @param redisPort the Redis port as a numeric string (mandatory) */
	public void setRedisPort(String redisPort) {
		this.redisPort = redisPort;
	}

	/** @return the ACL user name, or {@code null} */
	public String getRedisUser() {
		return redisUser;
	}

	/** @param redisUser the ACL user name; ignored unless a password is also set */
	public void setRedisUser(String redisUser) {
		this.redisUser = redisUser;
	}

	/** @return the Redis password, or {@code null} */
	public String getRedisPwd() {
		return redisPwd;
	}

	/** @param redisPwd the Redis password */
	public void setRedisPwd(String redisPwd) {
		this.redisPwd = redisPwd;
	}

	/** @return the database index selected on each connection */
	public int getRedisDatabase() {
		return redisDatabase;
	}

	/**
	 * Sets the database index. Applied only when a password is configured; without a
	 * password the connector always uses database {@code 0}.
	 *
	 * @param redisDatabase the database index
	 */
	public void setRedisDatabase(int redisDatabase) {
		this.redisDatabase = redisDatabase;
	}

	/** @return the cluster-mode flag (currently not used by the connector) */
	public boolean isRedisClusterModeEnabled() {
		return redisClusterModeEnabled;
	}

	/** @param redisClusterModeEnabled the cluster-mode flag (currently not used by the connector) */
	public void setRedisClusterModeEnabled(boolean redisClusterModeEnabled) {
		this.redisClusterModeEnabled = redisClusterModeEnabled;
	}

	/** Sets the cluster-mode flag to {@code true} (currently not used by the connector). */
	public void enableRedisClusterMode() {
		this.redisClusterModeEnabled = true;
	}

	/**
	 * Returns the pool settings, creating a default {@link JRedisPoolConfig} on first call
	 * if none was set. Never returns {@code null}.
	 *
	 * @return the pool settings
	 */
	public JRedisPoolConfig getRedisPoolConfig() {
		if (redisPoolConfig == null) {
			redisPoolConfig = new JRedisPoolConfig();
		}
		return redisPoolConfig;
	}

	/** @param redisPoolConfig the pool settings; {@code null} means use defaults */
	public void setRedisPoolConfig(JRedisPoolConfig redisPoolConfig) {
		this.redisPoolConfig = redisPoolConfig;
	}
}
