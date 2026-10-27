package com.aurastudio.backend.staff;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aurastudio.backend.cache.CacheEvictions;
import com.aurastudio.backend.auth.token.RefreshTokenService;
import com.aurastudio.backend.common.ApiException;
import com.aurastudio.backend.common.ApiResponse;
import com.aurastudio.backend.common.Messages;
import com.aurastudio.backend.common.Validators;
import com.aurastudio.backend.config.OpenApiConfig;
import com.aurastudio.backend.tenant.CurrentShop;
import com.aurastudio.backend.tenant.ShopContext;
import com.aurastudio.backend.user.UserDto;
import com.aurastudio.backend.user.UserRepository;
import com.aurastudio.backend.user.UserRole;
import com.aurastudio.backend.user.UserRow;
import com.aurastudio.backend.user.UserStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Staff accounts of my shop — owners only (docs/API_AND_DATABASE.md §8.12–8.15).
 * Every query filters by the owner's shop; other shops' ids answer 404.
 */
@RestController
@RequestMapping("/api/v1/admin/staff")
@Tag(name = "4. Shop admin")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class StaffController {

	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final RefreshTokenService refreshTokens;
	private final CacheEvictions evictions;

	public StaffController(UserRepository users, PasswordEncoder passwordEncoder, RefreshTokenService refreshTokens,
			CacheEvictions evictions) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.refreshTokens = refreshTokens;
		this.evictions = evictions;
	}

	public record StaffDto(UUID id, String name, String title, String email, String phone, String avatar, String role,
			String status, boolean isMe) {
		static StaffDto of(UserRow u, UUID me) {
			return new StaffDto(u.id(), u.name(), u.title(), u.email(), u.phone(), u.avatar(), u.role().db(),
					u.status().db(), u.id().equals(me));
		}
	}

	public record NewStaff(@Schema(example = "វណ្ណា") String name, @Schema(example = "បុគ្គលិកលក់") String title,
			String email, @Schema(example = "098 765 432") String phone, @Schema(example = "staffpass1") String password,
			@Schema(example = "staffpass1") String confirmPassword) {
	}

	public record StatusChange(@Schema(allowableValues = { "active", "disabled", "pending" }) String status) {
	}

	public record StatusResult(UUID id, String status) {
	}

	@GetMapping
	@Operation(summary = "Accounts of my shop (pending first)")
	public ApiResponse<List<StaffDto>> list(@CurrentShop ShopContext ctx) {
		ctx.requireOwner();
		return ApiResponse.of(users.listByShop(ctx.shopId()).stream().map(u -> StaffDto.of(u, ctx.userId())).toList());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	@Operation(summary = "Create a staff account (active immediately)",
			description = "The staff member logs in with this email OR phone + password.")
	public ApiResponse<UserDto> create(@CurrentShop ShopContext ctx, @RequestBody NewStaff body) {
		ctx.requireOwner();
		if (body == null) {
			throw ApiException.validation(Messages.BAD_REQUEST);
		}
		String name = Validators.text(body.name(), "name", 80);
		if (name.isEmpty()) {
			throw ApiException.validation("name", Messages.STAFF_NAME_REQUIRED);
		}
		var contact = Validators.contact(body.email(), body.phone(), Messages.EMAIL_OR_PHONE_REQUIRED);
		Validators.newPassword(body.password(), body.confirmPassword());
		if (users.emailTaken(contact.email(), null)) {
			throw ApiException.conflict("email", Messages.EMAIL_TAKEN);
		}
		if (users.phoneTaken(contact.phone(), null)) {
			throw ApiException.conflict("phone", Messages.PHONE_TAKEN);
		}
		String title = Validators.text(body.title(), "title", 80);
		UUID id = users.insert(ctx.shopId(), UserRole.STAFF, false, name, title.isEmpty() ? "បុគ្គលិក" : title,
				contact.email(), contact.phone(), passwordEncoder.encode(body.password()));
		return ApiResponse.of(UserDto.of(users.findById(id).orElseThrow()), Messages.staffCreated(name));
	}

	@PatchMapping("/{id}")
	@Transactional
	@Operation(summary = "Approve / suspend / set pending a staff account",
			description = "Disabling also revokes the account's refresh tokens. The owner cannot be changed.")
	public ApiResponse<StatusResult> changeStatus(@CurrentShop ShopContext ctx, @PathVariable UUID id,
			@RequestBody StatusChange body) {
		ctx.requireOwner();
		UserStatus status;
		try {
			status = UserStatus.fromDb(Validators.text(body == null ? null : body.status()));
		}
		catch (IllegalArgumentException e) {
			throw ApiException.validation("status", Messages.BAD_REQUEST);
		}
		UserRow target = users.findInShop(ctx.shopId(), id)
				.orElseThrow(() -> ApiException.notFound(Messages.ACCOUNT_NOT_FOUND));
		if (target.isOwner() || target.id().equals(ctx.userId())) {
			throw ApiException.forbidden(Messages.OWNER_STATUS_LOCKED);
		}
		users.updateStaffStatus(ctx.shopId(), id, status);
		// Takes effect on the staff member's very next request.
		evictions.userChanged(id);
		if (status != UserStatus.ACTIVE) {
			refreshTokens.revokeAllForUser(id);
		}
		String message = switch (status) {
			case ACTIVE -> Messages.STAFF_APPROVED;
			case DISABLED -> Messages.STAFF_DISABLED;
			case PENDING -> Messages.STAFF_PENDING;
		};
		return ApiResponse.of(new StatusResult(id, status.db()), message);
	}

	@DeleteMapping("/{id}")
	@Transactional
	@Operation(summary = "Delete a staff account of my shop")
	public ResponseEntity<Void> delete(@CurrentShop ShopContext ctx, @PathVariable UUID id) {
		ctx.requireOwner();
		UserRow target = users.findInShop(ctx.shopId(), id)
				.orElseThrow(() -> ApiException.notFound(Messages.ACCOUNT_NOT_FOUND));
		if (target.isOwner() || target.id().equals(ctx.userId())) {
			throw ApiException.forbidden(Messages.OWNER_DELETE_LOCKED);
		}
		users.deleteStaff(ctx.shopId(), id);
		evictions.userChanged(id);
		return ResponseEntity.noContent().build();
	}
}
