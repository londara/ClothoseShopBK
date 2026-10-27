package com.aurastudio.backend.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Success envelope: {@code { "data": ..., "message"?: "...", "meta"?: {...} }}.
 * {@code message} is the Khmer text the frontend shows in a toast.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data, String message, Object meta) {

	public static <T> ApiResponse<T> of(T data) {
		return new ApiResponse<>(data, null, null);
	}

	public static <T> ApiResponse<T> of(T data, String message) {
		return new ApiResponse<>(data, message, null);
	}

	public static <T> ApiResponse<T> page(T data, Object meta) {
		return new ApiResponse<>(data, null, meta);
	}

	public static ApiResponse<Void> message(String message) {
		return new ApiResponse<>(null, message, null);
	}
}
