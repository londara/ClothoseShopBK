package com.aurastudio.backend.user;

import java.util.UUID;

/** Public view of a user (no password hash). */
public record UserDto(UUID id, String name, String title, String email, String phone, String avatar, String role,
		boolean isPlatformAdmin) {

	public static UserDto of(UserRow u) {
		return new UserDto(u.id(), u.name(), u.title(), u.email(), u.phone(), u.avatar(), u.role().db(),
				u.platformAdmin());
	}
}
