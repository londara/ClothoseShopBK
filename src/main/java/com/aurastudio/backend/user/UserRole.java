package com.aurastudio.backend.user;

import java.util.Locale;

/** {@code user_role} enum: {@code owner} | {@code staff}. */
public enum UserRole {
	OWNER, STAFF;

	public String db() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static UserRole fromDb(String value) {
		return valueOf(value.toUpperCase(Locale.ROOT));
	}
}
