package com.aurastudio.backend.shop;

import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aurastudio.backend.security.RateLimiter;
import com.aurastudio.backend.auth.AuthDtos.TokenResponse;
import com.aurastudio.backend.auth.AuthService;
import com.aurastudio.backend.auth.token.RefreshTokenService;
import com.aurastudio.backend.category.CategoryRepository;
import com.aurastudio.backend.common.ApiException;
import com.aurastudio.backend.common.ApiResponse;
import com.aurastudio.backend.common.Messages;
import com.aurastudio.backend.common.Validators;
import com.aurastudio.backend.shoptype.ShopTypeRepository;
import com.aurastudio.backend.user.UserRepository;
import com.aurastudio.backend.user.UserRole;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/** "បើកហាងអនឡាញរបស់អ្នក" — public shop sign-up (docs/API_AND_DATABASE.md §7). */
@RestController
@RequestMapping("/api/v1/shops")
@Tag(name = "3. Shop sign-up")
public class ShopSignupController {

	private final ShopRepository shops;
	private final ShopTypeRepository shopTypes;
	private final CategoryRepository categories;
	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final RefreshTokenService refreshTokens;
	private final AuthService auth;
	private final RateLimiter rateLimiter;

	public ShopSignupController(ShopRepository shops, ShopTypeRepository shopTypes, CategoryRepository categories,
			UserRepository users, PasswordEncoder passwordEncoder, RefreshTokenService refreshTokens,
			AuthService auth, RateLimiter rateLimiter) {
		this.rateLimiter = rateLimiter;
		this.shops = shops;
		this.shopTypes = shopTypes;
		this.categories = categories;
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.refreshTokens = refreshTokens;
		this.auth = auth;
	}

	public record OwnerRequest(@Schema(example = "សុភា") String name,
			@Schema(example = "sophea@example.com") String email, @Schema(example = "012 345 678") String phone,
			@Schema(example = "sophea1234") String password, @Schema(example = "sophea1234") String confirmPassword) {
	}

	public record SignupRequest(@Schema(example = "ហាងសុភា") String shopName,
			@Schema(example = "sophea-shop") String slug, @Schema(example = "@sophea_shop") String telegram,
			@Schema(example = "jewelry") String shopType, OwnerRequest owner) {
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	@Operation(summary = "Open a new shop (pending until a platform admin approves it)",
			description = "Creates the shop, copies its type's default categories, creates the owner "
					+ "(login = email OR phone) and signs them in.")
	public ApiResponse<TokenResponse> signup(@RequestBody SignupRequest body, HttpServletRequest http) {
		rateLimiter.checkSignup(http); // public endpoint: stop scripted fake shops
		if (body == null) {
			throw ApiException.validation(Messages.BAD_REQUEST);
		}
		String shopName = Validators.text(body.shopName(), "shopName", 80);
		if (shopName.isEmpty()) {
			throw ApiException.validation("shopName", Messages.SHOP_NAME_REQUIRED);
		}
		String slug = Validators.text(body.slug()).toLowerCase(Locale.ROOT);
		SlugPolicy.check(slug);
		String telegram = Validators.text(body.telegram()).replaceFirst("^@", "");
		if (!telegram.matches("^[A-Za-z0-9_]{5,32}$")) {
			throw ApiException.validation("telegram", Messages.TELEGRAM_INVALID);
		}
		var type = shopTypes.findByCode(Validators.text(body.shopType()))
				.orElseThrow(() -> ApiException.validation("shopType", Messages.SHOP_TYPE_REQUIRED));

		OwnerRequest owner = body.owner();
		if (owner == null || Validators.text(owner.name(), "owner.name", 80).isEmpty()) {
			throw ApiException.validation("owner.name", Messages.NAME_REQUIRED);
		}
		var contact = Validators.contact(owner.email(), owner.phone(), Messages.EMAIL_OR_PHONE_REQUIRED);
		Validators.newPassword(owner.password(), owner.confirmPassword());

		if (shops.slugExists(slug)) {
			throw ApiException.conflict("slug", Messages.SLUG_TAKEN);
		}
		if (users.emailTaken(contact.email(), null)) {
			throw ApiException.conflict("owner.email", Messages.EMAIL_TAKEN);
		}
		if (users.phoneTaken(contact.phone(), null)) {
			throw ApiException.conflict("owner.phone", Messages.PHONE_TAKEN);
		}

		UUID shopId = shops.insert(slug, type.id(), ShopStatus.PENDING);
		shops.insertSettings(shopId, new ShopSettings(shopName, telegram, "សូមស្វាគមន៍! បញ្ជាទិញងាយៗតាមតេឡេក្រាម។",
				shopName, "សូមស្វាគមន៍មកកាន់ហាងរបស់យើង។", ShopSettings.DEFAULT_HERO, "#f8f9fa", "អំពីយើង", ""));
		categories.copyFromType(shopId, type.id());
		UUID userId = users.insert(shopId, UserRole.OWNER, false, Validators.text(owner.name()), "ម្ចាស់ហាង",
				contact.email(), contact.phone(), passwordEncoder.encode(owner.password()));

		var user = users.findById(userId).orElseThrow();
		String refresh = refreshTokens.issue(userId, http.getHeader(HttpHeaders.USER_AGENT), http.getRemoteAddr());
		return ApiResponse.of(auth.tokens(user, refresh, true), Messages.SHOP_CREATED);
	}
}
