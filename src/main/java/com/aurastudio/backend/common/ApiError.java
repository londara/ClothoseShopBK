package com.aurastudio.backend.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Error envelope: {@code { "error": { "code", "message", "field"? } }}. */
public record ApiError(Body error) {

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Body(String code, String message, String field) {
	}

	public static ApiError of(ErrorCode code, String message, String field) {
		return new ApiError(new Body(code.name(), message, field));
	}
}
