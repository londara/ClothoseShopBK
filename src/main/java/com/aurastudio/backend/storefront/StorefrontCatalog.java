package com.aurastudio.backend.storefront;

import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.aurastudio.backend.cache.CacheNames;
import com.aurastudio.backend.category.CategoryRepository;
import com.aurastudio.backend.product.ProductDtos.ProductDto;
import com.aurastudio.backend.product.ProductRepository;
import com.aurastudio.backend.product.ProductRepository.Filter;
import com.aurastudio.backend.product.ProductRepository.Sort;
import com.aurastudio.backend.product.ProductService;
import com.aurastudio.backend.shop.ShopRepository;
import com.aurastudio.backend.shop.ShopSettings;
import com.aurastudio.backend.shoptype.ShopTypeRepository;
import com.aurastudio.backend.shoptype.SizeKind;
import com.aurastudio.backend.storefront.StorefrontDtos.OwnerProfile;
import com.aurastudio.backend.storefront.StorefrontDtos.ShopCard;
import com.aurastudio.backend.storefront.StorefrontDtos.ShopTypeDto;
import com.aurastudio.backend.storefront.StorefrontDtos.StorefrontShop;
import com.aurastudio.backend.storefront.StorefrontDtos.TypeDetail;
import com.aurastudio.backend.storefront.StorefrontDtos.TypeRef;
import com.aurastudio.backend.user.UserRepository;

/**
 * Cached database reads behind the public storefront (docs/features/19-caching.md).
 * Each method runs its queries only on a cache miss; writes evict through
 * {@code CacheEvictions}. Shop pages then filter/sort/page the cached product list
 * in memory ({@link StorefrontQuery}), so browsing a shop needs no database query.
 */
@Service
public class StorefrontCatalog {

	private final ShopRepository shops;
	private final ShopTypeRepository shopTypes;
	private final CategoryRepository categories;
	private final ProductRepository products;
	private final ProductService productService;
	private final UserRepository users;

	public StorefrontCatalog(ShopRepository shops, ShopTypeRepository shopTypes, CategoryRepository categories,
			ProductRepository products, ProductService productService, UserRepository users) {
		this.shops = shops;
		this.shopTypes = shopTypes;
		this.categories = categories;
		this.products = products;
		this.productService = productService;
		this.users = users;
	}

	@Cacheable(cacheNames = CacheNames.SHOP_TYPES, key = "'" + CacheNames.ALL + "'")
	public List<ShopTypeDto> shopTypes() {
		return shopTypes.findAll().stream().map(t -> new ShopTypeDto(t.code(), t.label(), t.sizeKind().db()))
				.toList();
	}

	/** Active shops for the home page directory, with their product counts. */
	@Cacheable(cacheNames = CacheNames.ACTIVE_SHOPS, key = "'" + CacheNames.ALL + "'")
	public List<ShopCard> activeShops() {
		return shops.findActive().stream().map(s -> {
			ShopSettings settings = shops.getSettings(s.id());
			long count = products.count(s.id(), new Filter(null, false, null, Sort.CURATED));
			return new ShopCard(s.slug(), s.name(), new TypeRef(s.typeCode(), s.typeLabel()), settings.heroImage(),
					settings.heroSubtitle(), count);
		}).toList();
	}

	/** Any shop by its link (also pending/suspended); null if unknown (not cached). */
	@Cacheable(cacheNames = CacheNames.SHOP, key = "#slug", unless = "#result == null")
	public StorefrontShop shop(String slug) {
		return shops.findBySlug(slug).map(s -> {
			var owner = users.findOwner(s.id())
					.map(o -> new OwnerProfile(o.name(), o.title(), o.email(), o.phone(), o.avatar())).orElse(null);
			return new StorefrontShop(s.id(), s.slug(), s.name(), s.status().db(),
					new TypeDetail(s.typeCode(), s.typeLabel(), s.sizeKind().db()), shops.getSettings(s.id()),
					categories.list(s.id()), owner);
		}).orElse(null);
	}

	/**
	 * Every product of the shop in curated order (sort order, newest first), as storefront
	 * DTOs (products without sizes get the size system's defaults).
	 */
	@Cacheable(cacheNames = CacheNames.SHOP_PRODUCTS, key = "#shopId.toString()")
	public List<ProductDto> products(UUID shopId, SizeKind sizeKind) {
		var rows = products.page(shopId, new Filter(null, false, null, Sort.CURATED), Integer.MAX_VALUE, 0);
		return productService.toDto(shopId, rows, sizeKind);
	}
}
