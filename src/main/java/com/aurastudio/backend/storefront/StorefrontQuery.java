package com.aurastudio.backend.storefront;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.aurastudio.backend.category.CategoryDto;
import com.aurastudio.backend.common.PageMeta;
import com.aurastudio.backend.common.Validators;
import com.aurastudio.backend.product.ProductDtos.ProductDto;
import com.aurastudio.backend.product.ProductDtos.RelatedDto;
import com.aurastudio.backend.product.ProductService;
import com.aurastudio.backend.product.ProductService.Page;

/**
 * Storefront list operations on a shop's cached product list (curated order). Same rules
 * as the SQL version in ProductRepository: search in name/description/badge (case-insensitive),
 * category filter, sort, page size 1–60, category counts.
 */
final class StorefrontQuery {

	private static final int MAX_PAGE_SIZE = 60;

	private StorefrontQuery() {
	}

	static Page<ProductDto> list(List<ProductDto> all, List<CategoryDto> shopCategories, String q, String category,
			String sort, int page, int pageSize) {
		String query = Validators.blankToNull(ProductService.searchText(q));
		String needle = query == null ? null : query.toLowerCase(Locale.ROOT);
		String cat = Validators.blankToNull(category);
		String code = cat == null || "all".equals(cat) ? null : cat;

		List<ProductDto> matches = new ArrayList<>();
		for (ProductDto p : all) {
			if (code != null && !p.categories().contains(code)) {
				continue;
			}
			if (needle != null && !(contains(p.name(), needle) || contains(p.description(), needle)
					|| contains(p.badge(), needle))) {
				continue;
			}
			matches.add(p);
		}
		// List.sort is stable: equal prices keep the curated order.
		if ("price-asc".equals(sort)) {
			matches.sort(Comparator.comparing(ProductDto::price));
		}
		else if ("price-desc".equals(sort)) {
			matches.sort(Comparator.comparing(ProductDto::price).reversed());
		}

		int size = Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
		int p = Math.max(1, page);
		// long arithmetic: page=2147483647 must give an empty page, not an overflow.
		int from = (int) Math.min(matches.size(), (p - 1L) * size);
		int to = (int) Math.min(matches.size(), (long) from + size);
		var meta = PageMeta.of(p, size, matches.size()).withCategoryCounts(categoryCounts(all, shopCategories));
		return new Page<>(List.copyOf(matches.subList(from, to)), meta);
	}

	/** {@code all} first, then every category of the shop in its order (zeros included). */
	static Map<String, Long> categoryCounts(List<ProductDto> all, List<CategoryDto> shopCategories) {
		Map<String, Long> counts = new LinkedHashMap<>();
		counts.put("all", (long) all.size());
		for (CategoryDto c : shopCategories) {
			counts.put(c.code(), all.stream().filter(p -> p.categories().contains(c.code())).count());
		}
		return counts;
	}

	static Optional<ProductDto> bySlug(List<ProductDto> all, String slug) {
		return all.stream().filter(p -> p.slug().equals(slug)).findFirst();
	}

	/** Up to {@code limit} other products sharing a category, in curated order. */
	static List<RelatedDto> related(List<ProductDto> all, ProductDto product, int limit) {
		return all.stream()
				.filter(p -> !p.id().equals(product.id()))
				.filter(p -> p.categories().stream().anyMatch(product.categories()::contains))
				.limit(limit)
				.map(p -> new RelatedDto(p.slug(), p.name(), p.price(), p.images().stream().findFirst().orElse(null),
						p.atelier(), p.badge()))
				.toList();
	}

	private static boolean contains(String text, String needle) {
		return text != null && text.toLowerCase(Locale.ROOT).contains(needle);
	}
}
