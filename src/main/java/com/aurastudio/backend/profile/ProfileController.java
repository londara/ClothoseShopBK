package com.aurastudio.backend.profile;

import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aurastudio.backend.cache.CacheEvictions;
import com.aurastudio.backend.common.ApiException;
import com.aurastudio.backend.common.ApiResponse;
import com.aurastudio.backend.common.Messages;
import com.aurastudio.backend.common.Validators;
import com.aurastudio.backend.config.OpenApiConfig;
import com.aurastudio.backend.tenant.CurrentShop;
import com.aurastudio.backend.tenant.ShopContext;
import com.aurastudio.backend.upload.UploadService;
import com.aurastudio.backend.user.UserDto;
import com.aurastudio.backend.user.UserRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Own profile (docs/API_AND_DATABASE.md §8.11). Open to every signed-in user
 * (the doc's "recommended" option); the email/phone set here is the login.
 */
@RestController
@RequestMapping("/api/v1/admin/profile")
@Tag(name = "4. Shop admin")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class ProfileController {

	private final UserRepository users;
	private final UploadService uploads;
	private final CacheEvictions evictions;

	public ProfileController(UserRepository users, UploadService uploads, CacheEvictions evictions) {
		this.users = users;
		this.uploads = uploads;
		this.evictions = evictions;
	}

	public record ProfileRequest(@Schema(example = "សុខា") String name, @Schema(example = "ស្ថាបនិក") String title,
			@Schema(example = "sokha@example.com") String email, @Schema(example = "+855 12 999 888") String phone,
			@Schema(description = "/uploads/{file} of your shop, or null to remove") String avatar) {
	}

	@PutMapping
	@Operation(summary = "Update my profile", description = "At least one of email/phone (they are the login).")
	public ApiResponse<UserDto> update(@CurrentShop ShopContext ctx, @RequestBody ProfileRequest body) {
		if (body == null) {
			throw ApiException.validation(Messages.BAD_REQUEST);
		}
		String name = Validators.text(body.name(), "name", 80);
		if (name.isEmpty()) {
			throw ApiException.validation("name", Messages.NAME_REQUIRED);
		}
		var contact = Validators.contact(body.email(), body.phone(), Messages.PROFILE_EMAIL_OR_PHONE_REQUIRED);
		if (users.emailTaken(contact.email(), ctx.userId())) {
			throw ApiException.conflict("email", Messages.EMAIL_TAKEN);
		}
		if (users.phoneTaken(contact.phone(), ctx.userId())) {
			throw ApiException.conflict("phone", Messages.PHONE_TAKEN);
		}
		String avatar = Validators.blankToNull(body.avatar());
		if (avatar != null) {
			String file = Validators.uploadFileName(avatar);
			if (file == null || !uploads.belongsToShop(ctx.shopId(), file)) {
				throw ApiException.validation("avatar", Messages.IMAGE_INVALID_PREFIX + avatar);
			}
		}
		users.updateProfile(ctx.shopId(), ctx.userId(), name, Validators.text(body.title(), "title", 80), contact.email(),
				contact.phone(), avatar);
		if (ctx.isOwner()) {
			// The owner's profile is shown in the shop's public "About" section.
			evictions.shopChanged(ctx.shopId());
		}
		return ApiResponse.of(UserDto.of(users.findById(ctx.userId()).orElseThrow()), Messages.PROFILE_SAVED);
	}
}
