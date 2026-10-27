package com.aurastudio.backend.common;

/**
 * Thrown anywhere in a request; {@link GlobalExceptionHandler} turns it into
 * {@code { "error": { "code", "message", "field"? } }} with the code's HTTP status.
 */
public class ApiException extends RuntimeException {

	private final ErrorCode code;
	private final String field;
	private final Long retryAfterSeconds;

	public ApiException(ErrorCode code, String message) {
		this(code, message, null, null);
	}

	public ApiException(ErrorCode code, String message, String field, Long retryAfterSeconds) {
		super(message);
		this.code = code;
		this.field = field;
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public static ApiException validation(String field, String message) {
		return new ApiException(ErrorCode.VALIDATION, message, field, null);
	}

	public static ApiException validation(String message) {
		return validation(null, message);
	}

	public static ApiException notFound(String message) {
		return new ApiException(ErrorCode.NOT_FOUND, message);
	}

	public static ApiException conflict(String field, String message) {
		return new ApiException(ErrorCode.CONFLICT, message, field, null);
	}

	public static ApiException forbidden(String message) {
		return new ApiException(ErrorCode.FORBIDDEN, message);
	}

	public ErrorCode code() {
		return code;
	}

	public String field() {
		return field;
	}

	public Long retryAfterSeconds() {
		return retryAfterSeconds;
	}
}
