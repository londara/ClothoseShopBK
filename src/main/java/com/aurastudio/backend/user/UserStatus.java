package com.aurastudio.backend.user;

import java.util.Locale;

/** {@code user_status} enum: {@code active} | {@code pending} | {@code disabled}. */
public enum UserStatus {
	ACTIVE, PENDING, DISABLED;

	public String db() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static UserStatus fromDb(String value) {
		return valueOf(value.toUpperCase(Locale.ROOT));
	}
}
