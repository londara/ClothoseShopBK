package com.aurastudio.backend.security;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.aurastudio.backend.common.ApiError;
import com.aurastudio.backend.common.ErrorCode;
import com.aurastudio.backend.common.Messages;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Refuses non-upload request bodies over 1 MB before anything reads them
 * (docs/features/20-input-security.md). The biggest real JSON body (a product with a
 * 5,000-character description) is far below that. Uploads have their own 5 MB limit
 * ({@code spring.servlet.multipart}).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSizeFilter extends OncePerRequestFilter {

	public static final long MAX_JSON_BYTES = 1024 * 1024;

	private final JsonMapper json;

	public RequestSizeFilter(JsonMapper json) {
		this.json = json;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String type = request.getContentType();
		boolean multipart = type != null && type.toLowerCase().startsWith("multipart/");
		if (!multipart && request.getContentLengthLong() > MAX_JSON_BYTES) {
			response.setStatus(ErrorCode.PAYLOAD_TOO_LARGE.status().value());
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.setCharacterEncoding("UTF-8");
			json.writeValue(response.getOutputStream(),
					ApiError.of(ErrorCode.PAYLOAD_TOO_LARGE, Messages.PAYLOAD_TOO_LARGE, null));
			return;
		}
		chain.doFilter(request, response);
	}
}
