package com.aurastudio.backend.user;

import java.util.UUID;

/** One row of {@code users}. Never serialised directly (it holds the password hash). */
public record UserRow(UUID id, UUID shopId, UserRole role, boolean platformAdmin, UserStatus status, String name,
		String title, String email, String phone, String avatar, String passwordHash) {

	public boolean isOwner() {
		return role == UserRole.OWNER;
	}
}
