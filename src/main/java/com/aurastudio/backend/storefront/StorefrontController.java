package com.aurastudio.backend.storefront;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aurastudio.backend.common.ApiException;
import com.aurastudio.backend.common.ApiResponse;
import com.aurastudio.backend.common.Messages;
import com.aurastudio.backend.product.ProductDtos.ProductDto;
import com.aurastudio.backend.shoptype.SizeKind;
import com.aurastudio.backend.storefront.StorefrontDtos.FeaturedItem;
import com.aurastudio.backend.storefront.StorefrontDtos.FeaturedShop;
import com.aurastudio.backend.storefront.StorefrontDtos.ShopCard;
import com.aurastudio.backend.storefront.StorefrontDtos.ShopDetail;
import com.aurastudio.backend.storefront.StorefrontDtos.ShopTypeDto;
import com.aurastudio.backend.storefront.StorefrontDtos.StorefrontShop;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Public storefront API (docs/API_AND_DATABASE.md §6). Every shop-specific
 * request first resolves {@code slug → shop} (status active), then reads only
 * that shop's data (rule 7 of §4.1). All reads come from {@link StorefrontCatalog}
 * (cached, docs/features/19-caching.md).
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "2. Public storefront")
public class StorefrontController {

	private final StorefrontCatalog catalog;

	public StorefrontController(StorefrontCatalog catalog) {
		this.catalog = catalog;
	}

	@GetMapping("/shop-types")
	@Operation(summary = "Shop types for the \"open a shop\" form")
	public ApiResponse<List<ShopTypeDto>> shopTypes() {
		return ApiResponse.of(catalog.shopTypes());
	}

	@GetMapping("/shops")
	@Operation(summary = "Active shops (home page directory)")
	public ApiResponse<List<ShopCard>> shops() {
		return ApiResponse.of(catalog.activeShops());
	}

	@GetMapping("/featured")
	@Operation(summary = "Home carousel: up to perShop products of each active shop, interleaved")
	public ApiResponse<List<FeaturedItem>> featured(@RequestParam(defaultValue = "4") int perShop) {
		int per = Math.max(1, Math.min(12, perShop));
		List<List<FeaturedItem>> lists = new ArrayList<>();
		for (ShopCard card : catalog.activeShops()) {
			StorefrontShop shop = catalog.shop(card.slug());
			if (shop == null || !shop.active()) {
				continue;
			}
			var featuredShop = new FeaturedShop(shop.slug(), shop.name(), shop.settings().telegram());
			lists.add(products(shop).stream().limit(per)
					.map(p -> new FeaturedItem(p.id(), p.slug(), p.name(), p.description(), p.price(), p.stock(),
							p.images().stream().findFirst().orElse(null), featuredShop))
					.toList());
		}
		List<FeaturedItem> interleaved = new ArrayList<>();
		for (int i = 0; i < per; i++) {
			for (var list : lists) {
				if (i < list.size()) {
					interleaved.add(list.get(i));
				}
			}
		}
		return ApiResponse.of(interleaved);
	}

	@GetMapping("/shops/{slug}")
	@Operation(summary = "One shop: settings, categories, owner (pending/suspended → slug, name, status only)")
	public ApiResponse<ShopDetail> shop(@PathVariable String slug) {
		StorefrontShop shop = find(slug);
		if (shop == null) {
			throw ApiException.notFound(Messages.SHOP_NOT_FOUND);
		}
		if (!shop.active()) {
			return ApiResponse.of(new ShopDetail(shop.slug(), shop.name(), shop.status(), null, null, null, null));
		}
		return ApiResponse.of(new ShopDetail(shop.slug(), shop.name(), shop.status(), shop.type(), shop.settings(),
				shop.categories(), shop.owner()));
	}

	@GetMapping("/shops/{slug}/products")
	@Operation(summary = "Products of an active shop",
			description = "category = code or all; sort = curated | price-asc | price-desc; q searches name/description/badge. "
					+ "meta.categoryCounts gives the numbers for the filter pills.")
	public ApiResponse<List<ProductDto>> products(@PathVariable String slug,
			@RequestParam(required = false) String category, @RequestParam(defaultValue = "curated") String sort,
			@RequestParam(required = false) String q, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "12") int pageSize) {
		StorefrontShop shop = activeShop(slug);
		var result = StorefrontQuery.list(products(shop), shop.categories(), q, category, sort, page, pageSize);
		return ApiResponse.page(result.items(), result.meta());
	}

	@GetMapping("/shops/{slug}/products/{productSlug}")
	@Operation(summary = "One product of an active shop + up to 3 related products")
	public ApiResponse<ProductDto> product(@PathVariable String slug, @PathVariable String productSlug) {
		var all = products(activeShop(slug));
		ProductDto product = StorefrontQuery.bySlug(all, productSlug)
				.orElseThrow(() -> ApiException.notFound(Messages.PRODUCT_NOT_FOUND));
		return ApiResponse.of(product.withRelated(StorefrontQuery.related(all, product, 3)));
	}

	/** slug → shop; unknown or not active shops are "not found" for the public. */
	private StorefrontShop activeShop(String slug) {
		StorefrontShop shop = find(slug);
		if (shop == null || !shop.active()) {
			throw ApiException.notFound(Messages.SHOP_NOT_FOUND);
		}
		return shop;
	}

	/**
	 * Only well-formed links reach the cache/database: anything else (bots scanning random
	 * URLs, injection attempts) is a cheap 404.
	 */
	private StorefrontShop find(String slug) {
		if (slug == null || slug.length() > 40 || !SLUG.matcher(slug).matches()) {
			return null;
		}
		return catalog.shop(slug);
	}

	private static final java.util.regex.Pattern SLUG = java.util.regex.Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

	private List<ProductDto> products(StorefrontShop shop) {
		return catalog.products(shop.id(), SizeKind.fromDb(shop.type().sizeKind()));
	}
}
