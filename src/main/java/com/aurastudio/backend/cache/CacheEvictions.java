package com.aurastudio.backend.cache;

import java.util.UUID;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import com.aurastudio.backend.shop.ShopRepository;

/**
 * Removes cached data when it changes, so readers never see stale data after a write
 * through this API. Called by every write that touches cached data. Inside a
 * transaction the eviction runs after the commit (transaction-aware caches).
 */
@Component
public class CacheEvictions {

	private final CacheManager caches;
	private final ShopRepository shops;

	public CacheEvictions(CacheManager caches, ShopRepository shops) {
		this.caches = caches;
		this.shops = shops;
	}

	/** A product of the shop was created, edited or deleted (also changes the directory's counts). */
	public void productsChanged(UUID shopId) {
		evict(CacheNames.SHOP_PRODUCTS, shopId.toString());
		evict(CacheNames.ACTIVE_SHOPS, CacheNames.ALL);
	}

	/**
	 * The shop's public data changed: settings, categories, owner profile, status, or the
	 * shop is about to be deleted (call before deleting: the slug is looked up here).
	 */
	public void shopChanged(UUID shopId) {
		shops.findById(shopId).ifPresent(shop -> evict(CacheNames.SHOP, shop.slug()));
		productsChanged(shopId);
	}

	/** One account's status changed or it was deleted. */
	public void userChanged(UUID userId) {
		evict(CacheNames.SHOP_CONTEXT, userId.toString());
	}

	/** A shop was suspended, re-opened or deleted: every account of it is affected (rare). */
	public void allSessionsChanged() {
		Cache cache = caches.getCache(CacheNames.SHOP_CONTEXT);
		if (cache != null) {
			cache.clear();
		}
	}

	private void evict(String name, String key) {
		Cache cache = caches.getCache(name);
		if (cache != null) {
			cache.evict(key);
		}
	}
}
