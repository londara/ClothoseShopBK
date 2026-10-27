package com.aurastudio.backend.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Everything under {@code aura:} in application.yml. */
@ConfigurationProperties("aura")
public record AuraProperties(Jwt jwt, Login login, Uploads uploads, Cors cors, Bootstrap bootstrap, Cache cache,
		RateLimit rateLimit) {

	/** Per client IP; 0 turns a limit off (docs/features/20-input-security.md). */
	public record RateLimit(int signupsPerHour, int loginsPerMinute) {
	}

	/**
	 * Read cache (docs/features/19-caching.md). {@code type}: {@code caffeine} (in memory, one
	 * instance), {@code redis} (shared by all instances) or {@code none}.
	 */
	public record Cache(String type, Duration storefrontTtl, Duration shopTypesTtl, Duration sessionTtl) {
	}

	/** HS256 secret, issuer/audience claims and token lifetimes. */
	public record Jwt(String secret, String issuer, String audience, Duration accessTokenTtl,
			Duration refreshTokenTtl) {
	}

	/** Lockout after {@code maxFailures} wrong passwords for {@code lockDuration}. */
	public record Login(int maxFailures, Duration lockDuration) {
	}

	/** Uploaded images are stored under {@code dir/shops/{shopId}/}. */
	public record Uploads(String dir, long maxBytes) {
	}

	public record Cors(List<String> allowedOrigins) {
	}

	/** First-run platform admin + first shop (only when no user exists). */
	public record Bootstrap(String adminEmail, String adminPhone, String adminPassword, String adminName,
			String shopSlug, String shopName, String shopTelegram, String shopType) {
	}
}
