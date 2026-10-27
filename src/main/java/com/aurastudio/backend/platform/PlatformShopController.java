package com.aurastudio.backend.platform;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aurastudio.backend.cache.CacheEvictions;
import com.aurastudio.backend.auth.token.RefreshTokenService;
import com.aurastudio.backend.common.ApiException;
import com.aurastudio.backend.common.ApiResponse;
import com.aurastudio.backend.common.Messages;
import com.aurastudio.backend.common.Validators;
import com.aurastudio.backend.config.OpenApiConfig;
import com.aurastudio.backend.shop.ShopRepository;
import com.aurastudio.backend.shop.ShopStatus;
import com.aurastudio.backend.tenant.CurrentShop;
import com.aurastudio.backend.tenant.ShopContext;
import com.aurastudio.backend.user.UserRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * All shops — the ONLY place that works across shops, and only for platform
 * admins (docs/API_AND_DATABASE.md §9, rule 8 of §4.1).
 */
@RestController
@RequestMapping("/api/v1/platform/shops")
@Tag(name = "5. Platform admin")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class PlatformShopController {

	private final ShopRepository shops;
	private final UserRepository users;
	private final RefreshTokenService refreshTokens;
	private final JdbcClient jdbc;
	private final CacheEvictions evictions;

	public PlatformShopController(ShopRepository shops, UserRepository users, RefreshTokenService refreshTokens,
			JdbcClient jdbc, CacheEvictions evictions) {
		this.shops = shops;
		this.users = users;
		this.refreshTokens = refreshTokens;
		this.jdbc = jdbc;
		this.evictions = evictions;
	}

	public record Owner(String name, String email, String phone) {
	}

	public record PlatformShop(UUID id, String slug, String name, String status, String type, long productCount,
			long accountCount, Owner owner, OffsetDateTime createdAt, boolean isMine) {
	}

	public record StatusChange(@Schema(allowableValues = { "active", "suspended", "pending" }) String status) {
	}

	public record StatusResult(UUID id, String status, OffsetDateTime approvedAt) {
	}

	@GetMapping
	@Operation(summary = "All shops (pending first) with owner and counts")
	public ApiResponse<List<PlatformShop>> list(@CurrentShop ShopContext ctx) {
		ctx.requirePlatformAdmin();
		Map<UUID, Long> productCounts = counts("SELECT shop_id, count(*) FROM products GROUP BY shop_id");
		Map<UUID, Long> accountCounts = counts("SELECT shop_id, count(*) FROM users GROUP BY shop_id");
		var rows = shops.findAllForPlatform().stream().map(s -> {
			var owner = users.findOwner(s.id()).map(o -> new Owner(o.name(), o.email(), o.phone())).orElse(null);
			return new PlatformShop(s.id(), s.slug(), s.name(), s.status().db(), s.typeLabel(),
					productCounts.getOrDefault(s.id(), 0L), accountCounts.getOrDefault(s.id(), 0L), owner,
					s.createdAt(), s.id().equals(ctx.shopId()));
		}).toList();
		long pending = rows.stream().filter(r -> "pending".equals(r.status())).count();
		return ApiResponse.page(rows, Map.of("pending", pending));
	}

	@PatchMapping("/{id}")
	@Transactional
	@Operation(summary = "Approve / suspend / set pending a shop",
			description = "Suspending revokes the refresh tokens of the shop's users; own shop cannot be suspended.")
	public ApiResponse<StatusResult> changeStatus(@CurrentShop ShopContext ctx, @PathVariable UUID id,
			@RequestBody StatusChange body) {
		ctx.requirePlatformAdmin();
		ShopStatus status;
		try {
			status = ShopStatus.fromDb(Validators.text(body == null ? null : body.status()));
		}
		catch (IllegalArgumentException e) {
			throw ApiException.validation("status", Messages.BAD_REQUEST);
		}
		shops.findById(id).orElseThrow(() -> ApiException.notFound(Messages.SHOP_NOT_FOUND));
		if (id.equals(ctx.shopId()) && status != ShopStatus.ACTIVE) {
			throw ApiException.forbidden(Messages.OWN_SHOP_SUSPEND);
		}
		shops.updateStatus(id, status, ctx.userId());
		// Storefront shows/hides the shop; its accounts are blocked/unblocked on their next request.
		evictions.shopChanged(id);
		evictions.allSessionsChanged();
		if (status == ShopStatus.SUSPENDED) {
			refreshTokens.revokeAllForShop(id);
		}
		var updated = shops.findById(id).orElseThrow();
		String message = switch (status) {
			case ACTIVE -> Messages.SHOP_APPROVED;
			case SUSPENDED -> Messages.SHOP_SUSPENDED_DONE;
			case PENDING -> Messages.SHOP_PENDING_DONE;
		};
		return ApiResponse.of(new StatusResult(id, updated.status().db(), updated.approvedAt()), message);
	}

	@DeleteMapping("/{id}")
	@Transactional
	@Operation(summary = "Delete a shop with its products and accounts (not your own)")
	public ResponseEntity<Void> delete(@CurrentShop ShopContext ctx, @PathVariable UUID id) {
		ctx.requirePlatformAdmin();
		shops.findById(id).orElseThrow(() -> ApiException.notFound(Messages.SHOP_NOT_FOUND));
		if (id.equals(ctx.shopId())) {
			throw ApiException.forbidden(Messages.OWN_SHOP_DELETE);
		}
		evictions.shopChanged(id); // before the delete: it looks up the slug
		evictions.allSessionsChanged();
		shops.delete(id);
		return ResponseEntity.noContent().build();
	}

	private Map<UUID, Long> counts(String sql) {
		Map<UUID, Long> result = new java.util.HashMap<>();
		jdbc.sql(sql).query((rs, i) -> result.put(rs.getObject(1, UUID.class), rs.getLong(2))).list();
		return result;
	}
}
