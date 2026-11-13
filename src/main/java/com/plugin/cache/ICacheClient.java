package com.plugin.cache;

/**
 * Client contract for a queue-backed cache/log sink.
 * <p>
 * Entries are submitted with {@link #addLog(String, String, boolean)} and drained
 * asynchronously by the implementation.
 * <p>
 * <b>Note:</b> this module ships no implementation of this interface; the Redis access
 * classes in {@code com.plugin.cache.redis} do not implement it.
 */
public interface ICacheClient {

	/**
	 * Initialises the client and connects to the backing server.
	 *
	 * @param strIPHost  host name or IP address of the server
	 * @param nPort      server port
	 * @param strLogPath local path used for file fallback / file logging
	 * @return {@code true} if the client is ready for use, {@code false} otherwise
	 */
	public boolean init(String strIPHost, int nPort, String strLogPath);

	/**
	 * Submits one entry.
	 *
	 * @param strQueryID identifier of the target stream/queue the entry belongs to
	 * @param str        the payload to store
	 * @param bLogToFile {@code true} to also write the entry to the local log file
	 * @return an implementation-defined status code
	 * @throws Exception if the entry cannot be accepted
	 */
	public int addLog(String strQueryID, String str, boolean bLogToFile) throws Exception;

	/**
	 * @return the number of submitted entries not yet delivered to the backing server
	 */
	public int getPendingSize();

	/**
	 * Flushes/stops the client and releases its resources.
	 *
	 * @return {@code true} if shutdown completed cleanly
	 */
	public boolean deinit();
}
