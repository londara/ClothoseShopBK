package com.aurastudio.backend.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Turns every exception into the documented error envelope. */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiError> api(ApiException e) {
		var builder = ResponseEntity.status(e.code().status());
		if (e.retryAfterSeconds() != null) {
			builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSeconds()));
		}
		return builder.body(ApiError.of(e.code(), e.getMessage(), e.field()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e) {
		var first = e.getBindingResult().getFieldErrors().stream().findFirst();
		String message = first.map(f -> f.getDefaultMessage()).orElse(Messages.BAD_REQUEST);
		String field = first.map(f -> f.getField()).orElse(null);
		return error(ErrorCode.VALIDATION, message, field);
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
			MissingServletRequestParameterException.class, MissingServletRequestPartException.class })
	ResponseEntity<ApiError> badRequest(Exception e) {
		return error(ErrorCode.VALIDATION, Messages.BAD_REQUEST, null);
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException e) {
		return error(ErrorCode.FILE_TOO_LARGE, Messages.FILE_TOO_LARGE, "file");
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ApiError> noResource(NoResourceFoundException e) {
		return error(ErrorCode.NOT_FOUND, Messages.NOT_FOUND, null);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException e) {
		return error(ErrorCode.NOT_FOUND, Messages.NOT_FOUND, null);
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ApiError> denied(AccessDeniedException e) {
		return error(ErrorCode.FORBIDDEN, Messages.FORBIDDEN, null);
	}

	/**
	 * Safety net (docs/features/20-input-security.md): validation should catch bad input first,
	 * but if a database constraint still rejects a value it is the client's input, not a 500.
	 * A duplicate key (e.g. two sign-ups racing for the same email) is a conflict.
	 */
	@ExceptionHandler(DuplicateKeyException.class)
	ResponseEntity<ApiError> duplicate(DuplicateKeyException e) {
		log.warn("Duplicate key: {}", e.getMostSpecificCause().getMessage());
		return error(ErrorCode.CONFLICT, Messages.BAD_REQUEST, null);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ApiError> constraint(DataIntegrityViolationException e) {
		log.warn("Rejected by a database constraint: {}", e.getMostSpecificCause().getMessage());
		return error(ErrorCode.VALIDATION, Messages.BAD_REQUEST, null);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> unexpected(Exception e) {
		log.error("Unhandled error", e);
		return error(ErrorCode.INTERNAL, Messages.INTERNAL, null);
	}

	private static ResponseEntity<ApiError> error(ErrorCode code, String message, String field) {
		return ResponseEntity.status(code.status()).body(ApiError.of(code, message, field));
	}
}
