package com.aurastudio.backend.tenant;

import java.util.UUID;

import com.aurastudio.backend.common.ApiException;
import com.aurastudio.backend.common.Messages;
import com.aurastudio.backend.user.UserRole;

/**
 * Who is calling and WHICH SHOP every query must be limited to
 * (docs/API_AND_DATABASE.md §4.1). Built from the database on every request —
 * {@code shopId} is never taken from the client.
 */
public record ShopContext(UUID userId, UUID shopId, UserRole role, boolean platformAdmin) {

	public boolean isOwner() {
		return role == UserRole.OWNER;
	}

	/** Shop owner (or the platform admin, who owns the first shop). */
	public ShopContext requireOwner() {
		if (!isOwner()) {
			throw ApiException.forbidden(Messages.FORBIDDEN);
		}
		return this;
	}

	/** Only platform admins may act across shops ({@code /platform/...}). */
	public ShopContext requirePlatformAdmin() {
		if (!platformAdmin) {
			throw ApiException.forbidden(Messages.FORBIDDEN);
		}
		return this;
	}
}
