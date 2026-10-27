package com.aurastudio.backend.common;

import org.springframework.http.HttpStatus;

/** Error codes of docs/API_AND_DATABASE.md §4 with their HTTP status. */
public enum ErrorCode {
	VALIDATION(HttpStatus.BAD_REQUEST),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
	TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
	TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
	FORBIDDEN(HttpStatus.FORBIDDEN),
	NOT_FOUND(HttpStatus.NOT_FOUND),
	CONFLICT(HttpStatus.CONFLICT),
	ACCOUNT_PENDING(HttpStatus.FORBIDDEN),
	ACCOUNT_DISABLED(HttpStatus.FORBIDDEN),
	SHOP_SUSPENDED(HttpStatus.FORBIDDEN),
	LOCKED(HttpStatus.LOCKED),
	FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
	PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
	UNSUPPORTED_FILE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
	RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
	INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR);

	private final HttpStatus status;

	ErrorCode(HttpStatus status) {
		this.status = status;
	}

	public HttpStatus status() {
		return status;
	}
}
