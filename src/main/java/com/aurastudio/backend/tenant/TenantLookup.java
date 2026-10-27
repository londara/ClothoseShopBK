package com.aurastudio.backend.tenant;

import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.aurastudio.backend.cache.CacheNames;
import com.aurastudio.backend.shop.ShopRepository;
import com.aurastudio.backend.shop.ShopRow;
import com.aurastudio.backend.shop.ShopStatus;
import com.aurastudio.backend.user.UserRepository;
import com.aurastudio.backend.user.UserRole;
import com.aurastudio.backend.user.UserStatus;

/**
 * What {@link ShopContextResolver} checks on every authenticated request, cached per user
 * (cache {@code shop-context}, short TTL). Changes that matter — staff disabled/deleted,
 * shop suspended/deleted — evict the entry at once (see {@code CacheEvictions}).
 */
@Component
public class TenantLookup {

	private final UserRepository users;
	private final ShopRepository shops;

	public TenantLookup(UserRepository users, ShopRepository shops) {
		this.users = users;
		this.shops = shops;
	}

	/** {@code shopStatus} is null when the shop no longer exists. */
	public record TenantUser(UUID id, UUID shopId, UserRole role, UserStatus status, boolean platformAdmin,
			ShopStatus shopStatus) {
	}

	/** The user and their shop's status, or null for an unknown user (not cached). */
	@Cacheable(cacheNames = CacheNames.SHOP_CONTEXT, key = "#userId.toString()", unless = "#result == null")
	public TenantUser find(UUID userId) {
		return users.findById(userId)
				.map(u -> new TenantUser(u.id(), u.shopId(), u.role(), u.status(), u.platformAdmin(),
						shops.findById(u.shopId()).map(ShopRow::status).orElse(null)))
				.orElse(null);
	}
}
