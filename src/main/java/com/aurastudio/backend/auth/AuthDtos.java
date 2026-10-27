package com.aurastudio.backend.auth;

import java.util.UUID;

import com.aurastudio.backend.shop.ShopRow;
import com.aurastudio.backend.user.UserDto;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/** Request/response bodies of /api/v1/auth (docs/API_AND_DATABASE.md §5). */
public final class AuthDtos {

	private AuthDtos() {
	}

	@Schema(description = "identifier = email (contains @) OR phone number")
	public record LoginRequest(
			@Schema(example = "012 345 678", description = "Email or phone number") String identifier,
			@Schema(example = "sophea1234") String password) {
	}

	public record RefreshRequest(@Schema(example = "q3Jx8m2Vb0Hn9Lk4TzR1yW7cP5eA6sD0fG2hJ8kL3mN") String refreshToken) {
	}

	public record ChangePasswordRequest(String currentPassword, String newPassword, String confirmPassword) {
	}

	public record ShopSummary(UUID id, String slug, String name, String status) {
		public static ShopSummary of(ShopRow s) {
			return new ShopSummary(s.id(), s.slug(), s.name(), s.status().db());
		}
	}

	/** Login / refresh / sign-up / password change: token pair, plus user and shop on login. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record TokenResponse(String tokenType, String accessToken, long expiresIn, String refreshToken,
			long refreshExpiresIn, UserDto user, ShopSummary shop) {
	}

	public record Counts(long pendingStaff, long pendingShops) {
	}

	public record MeResponse(UserDto user, ShopSummary shop, Counts counts) {
	}
}
