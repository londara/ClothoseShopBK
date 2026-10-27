package com.aurastudio.backend.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aurastudio.backend.security.RateLimiter;
import com.aurastudio.backend.auth.AuthDtos.ChangePasswordRequest;
import com.aurastudio.backend.auth.AuthDtos.LoginRequest;
import com.aurastudio.backend.auth.AuthDtos.MeResponse;
import com.aurastudio.backend.auth.AuthDtos.RefreshRequest;
import com.aurastudio.backend.auth.AuthDtos.TokenResponse;
import com.aurastudio.backend.common.ApiResponse;
import com.aurastudio.backend.common.Messages;
import com.aurastudio.backend.config.OpenApiConfig;
import com.aurastudio.backend.tenant.CurrentShop;
import com.aurastudio.backend.tenant.ShopContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "1. Auth")
public class AuthController {

	private final AuthService auth;
	private final RateLimiter rateLimiter;

	public AuthController(AuthService auth, RateLimiter rateLimiter) {
		this.auth = auth;
		this.rateLimiter = rateLimiter;
	}

	@PostMapping("/login")
	@Operation(summary = "Login with phone number or email",
			description = "identifier = email (contains @) or phone (012 345 678 / +855 12 345 678 / 85512345678). "
					+ "Returns a 15-minute JWT access token and a 30-day refresh token. "
					+ "5 wrong passwords → 423 LOCKED for 15 minutes.")
	public ApiResponse<TokenResponse> login(@RequestBody LoginRequest request, HttpServletRequest http) {
		rateLimiter.checkLogin(http); // per IP, on top of the per-account lockout
		return ApiResponse.of(auth.login(request, userAgent(http), http.getRemoteAddr()));
	}

	@PostMapping("/refresh")
	@Operation(summary = "Get a new token pair", description = "The old refresh token stops working (rotation). "
			+ "Re-using an old one revokes the whole login.")
	public ApiResponse<TokenResponse> refresh(@RequestBody RefreshRequest request, HttpServletRequest http) {
		return ApiResponse.of(auth.refresh(request == null ? null : request.refreshToken(), userAgent(http),
				http.getRemoteAddr()));
	}

	@PostMapping("/logout")
	@Operation(summary = "Logout (revoke the refresh token)")
	public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest request) {
		auth.logout(request == null ? null : request.refreshToken());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/me")
	@Operation(summary = "Signed-in user, shop and pending counts", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
	public ApiResponse<MeResponse> me(@CurrentShop ShopContext ctx) {
		return ApiResponse.of(auth.me(ctx));
	}

	@PatchMapping("/password")
	@Operation(summary = "Change own password", description = "Revokes all refresh tokens and returns a new pair.",
			security = @SecurityRequirement(name = OpenApiConfig.BEARER))
	public ApiResponse<TokenResponse> changePassword(@CurrentShop ShopContext ctx,
			@RequestBody ChangePasswordRequest request, HttpServletRequest http) {
		return ApiResponse.of(auth.changePassword(ctx, request, userAgent(http), http.getRemoteAddr()),
				Messages.PASSWORD_CHANGED);
	}

	private static String userAgent(HttpServletRequest http) {
		return http.getHeader(HttpHeaders.USER_AGENT);
	}
}
