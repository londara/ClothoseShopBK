package com.aurastudio.backend.shop;

import java.util.Locale;

/** {@code shop_status} enum: {@code pending} | {@code active} | {@code suspended}. */
public enum ShopStatus {
	PENDING, ACTIVE, SUSPENDED;

	public String db() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static ShopStatus fromDb(String value) {
		return valueOf(value.toUpperCase(Locale.ROOT));
	}
}
