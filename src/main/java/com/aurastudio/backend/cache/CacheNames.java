package com.aurastudio.backend.cache;

/**
 * Names of the read caches (docs/features/19-caching.md). In Redis the keys are
 * {@code aura:{cache name}::{key}}.
 */
public final class CacheNames {

	/** All shop types. Key: {@link #ALL}. */
	public static final String SHOP_TYPES = "shop-types";

	/** Directory of active shops (home page). Key: {@link #ALL}. */
	public static final String ACTIVE_SHOPS = "storefront-shops";

	/** One shop's public page data (settings, categories, owner). Key: slug. */
	public static final String SHOP = "storefront-shop";

	/** All products of one shop, in curated order, as storefront DTOs. Key: shop id. */
	public static final String SHOP_PRODUCTS = "shop-products";

	/** Account + shop status checked on every authenticated request. Key: user id. */
	public static final String SHOP_CONTEXT = "shop-context";

	/** Key of caches that hold a single value. */
	public static final String ALL = "all";

	private CacheNames() {
	}
}
