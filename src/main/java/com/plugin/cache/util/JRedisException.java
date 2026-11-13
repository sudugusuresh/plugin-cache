package com.plugin.cache.util;

/**
 * Checked exception thrown by {@link com.plugin.cache.redis.JRedisConnector} for configuration
 * errors and failed Redis operations.
 * <p>
 * Carries an optional {@code errorCode} alongside the human-readable {@code errorDesc}
 * (which is also the exception message). The connector itself always uses the
 * single-argument constructor, so {@code errorCode} is {@code null} for exceptions it raises.
 */
public class JRedisException  extends Exception {
	/**
	 *
	 */
	private static final long serialVersionUID = 4155346071087028191L;
	/** Optional application-defined error code; {@code null} if not supplied. */
	private String errorCode;
	/** Error description; same value as {@link #getMessage()}. */
	private String errorDesc;

	/**
	 * Creates an exception with a description and no error code.
	 *
	 * @param s error description, also used as the exception message
	 */
	public JRedisException(String s) {
		// Call constructor of parent Exception
		super(s);
		errorDesc = s;
	}

	/**
	 * Creates an exception with an error code and description.
	 *
	 * @param errorCode application-defined error code
	 * @param errorDesc error description, also used as the exception message
	 */
	public JRedisException(String errorCode, String errorDesc) {
		// Call constructor of parent Exception
		super(errorDesc);
		this.errorCode = errorCode;
		this.errorDesc = errorDesc;
	}

	/** @return the error code, or {@code null} if none was supplied */
	public String getErrorCode() {
		return errorCode;
	}

	/** @param errorCode the error code to set */
	public void setErrorCode(String errorCode) {
		this.errorCode = errorCode;
	}

	/** @return the error description */
	public String getErrorDesc() {
		return errorDesc;
	}

	/**
	 * Replaces the stored description. Does not change {@link #getMessage()}.
	 *
	 * @param errorDesc the description to set
	 */
	public void setErrorDesc(String errorDesc) {
		this.errorDesc = errorDesc;
	}

	/** @return {@code "errorCode: <code>, errorDesc: <desc>"} for logging */
	public String getErrorDetails() {
		return "errorCode: " + errorCode + ", errorDesc: " + errorDesc;
	}
}
