package com.aurastudio.backend.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurastudio.backend.auth.AuthDtos.ChangePasswordRequest;
import com.aurastudio.backend.auth.AuthDtos.Counts;
import com.aurastudio.backend.auth.AuthDtos.LoginRequest;
import com.aurastudio.backend.auth.AuthDtos.MeResponse;
import com.aurastudio.backend.auth.AuthDtos.ShopSummary;
import com.aurastudio.backend.auth.AuthDtos.TokenResponse;
import com.aurastudio.backend.auth.jwt.JwtService;
import com.aurastudio.backend.auth.token.RefreshTokenService;
import com.aurastudio.backend.common.ApiException;
import com.aurastudio.backend.common.ErrorCode;
import com.aurastudio.backend.common.Messages;
import com.aurastudio.backend.common.Validators;
import com.aurastudio.backend.shop.ShopRepository;
import com.aurastudio.backend.shop.ShopStatus;
import com.aurastudio.backend.tenant.ShopContext;
import com.aurastudio.backend.user.UserDto;
import com.aurastudio.backend.user.UserRepository;
import com.aurastudio.backend.user.UserRow;
import com.aurastudio.backend.user.UserStatus;

/** JWT login with phone number or email (docs/API_AND_DATABASE.md §5). */
@Service
public class AuthService {

	private final UserRepository users;
	private final ShopRepository shops;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwt;
	private final RefreshTokenService refreshTokens;
	private final LoginThrottle throttle;

	public AuthService(UserRepository users, ShopRepository shops, PasswordEncoder passwordEncoder, JwtService jwt,
			RefreshTokenService refreshTokens, LoginThrottle throttle) {
		this.users = users;
		this.shops = shops;
		this.passwordEncoder = passwordEncoder;
		this.jwt = jwt;
		this.refreshTokens = refreshTokens;
		this.throttle = throttle;
	}

	/**
	 * 1. identifier → email (has "@") or normalised phone; 2. lockout check;
	 * 3. verify password (same error for unknown account and wrong password);
	 * 4. status checks; 5. issue access + refresh token.
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public TokenResponse login(LoginRequest request, String userAgent, String ip) {
		String identifier = Validators.text(request == null ? null : request.identifier());
		String password = request == null || request.password() == null ? "" : request.password();
		if (identifier.isEmpty() || password.isEmpty()) {
			throw ApiException.validation(Messages.LOGIN_REQUIRED_FIELDS);
		}

		String key = Validators.normalizeIdentifier(identifier);
		// Longer than any stored email/phone: can't be an account (and wouldn't fit login_attempts).
		if (key.length() > Validators.MAX_EMAIL) {
			throw new ApiException(ErrorCode.INVALID_CREDENTIALS, Messages.INVALID_CREDENTIALS);
		}
		long lockedSeconds = throttle.lockedSeconds(key);
		if (lockedSeconds > 0) {
			throw new ApiException(ErrorCode.LOCKED, Messages.locked((lockedSeconds + 59) / 60), null, lockedSeconds);
		}

		UserRow user = key.isEmpty() ? null : users.findByIdentifier(key).orElse(null);
		// Passwords over bcrypt's 72 bytes are never set, so they can't match (and bcrypt would throw).
		if (user == null || Validators.passwordTooLong(password)
				|| !passwordEncoder.matches(password, user.passwordHash())) {
			throttle.recordFailure(key);
			throw new ApiException(ErrorCode.INVALID_CREDENTIALS, Messages.INVALID_CREDENTIALS);
		}
		throttle.clear(key);

		if (user.status() == UserStatus.PENDING) {
			throw new ApiException(ErrorCode.ACCOUNT_PENDING, Messages.ACCOUNT_PENDING);
		}
		if (user.status() == UserStatus.DISABLED) {
			throw new ApiException(ErrorCode.ACCOUNT_DISABLED, Messages.ACCOUNT_DISABLED);
		}
		var shop = shops.findById(user.shopId()).orElseThrow();
		if (shop.status() == ShopStatus.SUSPENDED && !user.platformAdmin()) {
			throw new ApiException(ErrorCode.SHOP_SUSPENDED, Messages.SHOP_SUSPENDED);
		}

		// Hashes migrated from the Next.js app (scrypt) are upgraded to BCrypt here.
		if (passwordEncoder.upgradeEncoding(user.passwordHash())) {
			users.updatePassword(user.id(), passwordEncoder.encode(password));
		}
		return tokens(user, refreshTokens.issue(user.id(), userAgent, ip), true);
	}

	/** Rotates the refresh token; the old one stops working. */
	@Transactional(noRollbackFor = ApiException.class)
	public TokenResponse refresh(String refreshToken, String userAgent, String ip) {
		// Real tokens are 86 characters; anything else can't match, so skip the work.
		if (refreshToken == null || refreshToken.isBlank() || refreshToken.length() > MAX_TOKEN) {
			throw new ApiException(ErrorCode.TOKEN_INVALID, Messages.TOKEN_INVALID);
		}
		var rotation = refreshTokens.rotate(refreshToken, userAgent, ip);
		UserRow user = users.findById(rotation.userId())
				.orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID, Messages.TOKEN_INVALID));
		if (user.status() != UserStatus.ACTIVE) {
			refreshTokens.revokeAllForUser(user.id());
			throw new ApiException(ErrorCode.ACCOUNT_DISABLED, Messages.ACCOUNT_DISABLED);
		}
		var shop = shops.findById(user.shopId()).orElseThrow();
		if (shop.status() == ShopStatus.SUSPENDED && !user.platformAdmin()) {
			refreshTokens.revokeAllForUser(user.id());
			throw new ApiException(ErrorCode.SHOP_SUSPENDED, Messages.SHOP_SUSPENDED);
		}
		return tokens(user, rotation.refreshToken(), false);
	}

	public void logout(String refreshToken) {
		if (refreshToken != null && refreshToken.length() <= MAX_TOKEN) {
			refreshTokens.revoke(refreshToken);
		}
	}

	private static final int MAX_TOKEN = 200;

	public MeResponse me(ShopContext ctx) {
		UserRow user = users.findById(ctx.userId()).orElseThrow();
		var shop = shops.findById(ctx.shopId()).orElseThrow();
		long pendingStaff = ctx.isOwner() ? users.countPending(ctx.shopId()) : 0;
		long pendingShops = ctx.platformAdmin() ? shops.countPending() : 0;
		return new MeResponse(UserDto.of(user), ShopSummary.of(shop), new Counts(pendingStaff, pendingShops));
	}

	/** Revokes every refresh token of the user, then returns a fresh pair for this device. */
	@Transactional
	public TokenResponse changePassword(ShopContext ctx, ChangePasswordRequest request, String userAgent, String ip) {
		UserRow user = users.findById(ctx.userId()).orElseThrow();
		String current = request == null ? null : request.currentPassword();
		if (current == null || Validators.passwordTooLong(current)
				|| !passwordEncoder.matches(current, user.passwordHash())) {
			throw ApiException.validation("currentPassword", Messages.CURRENT_PASSWORD_WRONG);
		}
		String next = request.newPassword();
		if (next == null || next.length() < 8) {
			throw ApiException.validation("newPassword", Messages.NEW_PASSWORD_TOO_SHORT);
		}
		if (Validators.passwordTooLong(next)) {
			throw ApiException.validation("newPassword", Messages.PASSWORD_TOO_LONG);
		}
		if (!next.equals(request.confirmPassword())) {
			throw ApiException.validation("confirmPassword", Messages.NEW_PASSWORDS_DIFFER);
		}
		users.updatePassword(user.id(), passwordEncoder.encode(next));
		refreshTokens.revokeAllForUser(user.id());
		return tokens(user, refreshTokens.issue(user.id(), userAgent, ip), false);
	}

	/** Access + refresh token pair; {@code withProfile} adds user and shop (login, sign-up). */
	public TokenResponse tokens(UserRow user, String refreshToken, boolean withProfile) {
		var shop = withProfile ? shops.findById(user.shopId()).map(ShopSummary::of).orElse(null) : null;
		return new TokenResponse("Bearer", jwt.issueAccessToken(user), jwt.accessTokenSeconds(), refreshToken,
				jwt.refreshTokenSeconds(), withProfile ? UserDto.of(user) : null, shop);
	}
}
