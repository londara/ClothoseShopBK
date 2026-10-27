package com.aurastudio.backend.security;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.aurastudio.backend.common.ApiException;
import com.aurastudio.backend.common.ErrorCode;
import com.aurastudio.backend.common.Messages;
import com.aurastudio.backend.config.AuraProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Per-client-IP request limits for the public, abusable endpoints: shop sign-up (fake
 * shops, email/phone enumeration) and login (password spraying over many accounts, on
 * top of the per-account lockout). Fixed windows in memory, per instance; behind a load
 * balancer use sticky sessions or also limit at the proxy (docs/features/20-input-security.md).
 * <p>
 * The client IP is {@code request.getRemoteAddr()}: with
 * {@code server.forward-headers-strategy=native}, Tomcat takes it from X-Forwarded-For
 * only when the request comes from a trusted internal proxy (like the Next.js server).
 */
@Component
public class RateLimiter {

	private final AuraProperties.RateLimit limits;
	private final Cache<String, AtomicInteger> signups;
	private final Cache<String, AtomicInteger> logins;

	public RateLimiter(AuraProperties properties) {
		this.limits = properties.rateLimit();
		this.signups = window(Duration.ofHours(1));
		this.logins = window(Duration.ofMinutes(1));
	}

	private static Cache<String, AtomicInteger> window(Duration length) {
		// The counter is created by the first request and expires one window later.
		return Caffeine.newBuilder().expireAfterWrite(length.toMillis(), TimeUnit.MILLISECONDS)
				.maximumSize(1_000_000).build();
	}

	public void checkSignup(HttpServletRequest request) {
		check(signups, request.getRemoteAddr(), limits.signupsPerHour(), 3600);
	}

	public void checkLogin(HttpServletRequest request) {
		check(logins, request.getRemoteAddr(), limits.loginsPerMinute(), 60);
	}

	private static void check(Cache<String, AtomicInteger> window, String ip, int max, long retryAfter) {
		if (max <= 0) {
			return;
		}
		int count = window.get(ip == null ? "unknown" : ip, k -> new AtomicInteger()).incrementAndGet();
		if (count > max) {
			throw new ApiException(ErrorCode.RATE_LIMITED, Messages.RATE_LIMITED, null, retryAfter);
		}
	}
}
