package com.aurastudio.backend.storefront;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.aurastudio.backend.category.CategoryDto;
import com.aurastudio.backend.shop.ShopSettings;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Response shapes of the public storefront API; most of them are also cached. */
public final class StorefrontDtos {

	public record ShopTypeDto(String code, String label, String sizeKind) {
	}

	public record TypeRef(String code, String label) {
	}

	public record ShopCard(String slug, String name, TypeRef type, String heroImage, String heroSubtitle,
			long productCount) {
	}

	public record FeaturedShop(String slug, String name, String telegram) {
	}

	public record FeaturedItem(UUID id, String slug, String name, String description, BigDecimal price, int stock,
			String image, FeaturedShop shop) {
	}

	public record TypeDetail(String code, String label, String sizeKind) {
	}

	public record OwnerProfile(String name, String title, String email, String phone, String avatar) {
	}

	/** Active shop: everything; pending/suspended: only slug, name, status (the page shows a notice). */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ShopDetail(String slug, String name, String status, TypeDetail type, ShopSettings settings,
			List<CategoryDto> categories, OwnerProfile owner) {
	}

	/**
	 * Cached form of one shop (cache {@code storefront-shop}): everything the storefront
	 * needs about it, including the id used to look up its products.
	 */
	public record StorefrontShop(UUID id, String slug, String name, String status, TypeDetail type,
			ShopSettings settings, List<CategoryDto> categories, OwnerProfile owner) {

		public boolean active() {
			return "active".equals(status);
		}
	}

	private StorefrontDtos() {
	}
}
