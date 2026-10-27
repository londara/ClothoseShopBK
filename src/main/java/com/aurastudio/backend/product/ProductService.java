package com.aurastudio.backend.product;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurastudio.backend.cache.CacheEvictions;
import com.aurastudio.backend.category.CategoryDto;
import com.aurastudio.backend.category.CategoryRepository;
import com.aurastudio.backend.common.ApiException;
import com.aurastudio.backend.common.Messages;
import com.aurastudio.backend.common.PageMeta;
import com.aurastudio.backend.common.Validators;
import com.aurastudio.backend.product.ProductDtos.ColorDto;
import com.aurastudio.backend.product.ProductDtos.FormOptions;
import com.aurastudio.backend.product.ProductDtos.ProductDto;
import com.aurastudio.backend.product.ProductDtos.ProductListItem;
import com.aurastudio.backend.product.ProductDtos.ProductRequest;
import com.aurastudio.backend.product.ProductDtos.ProductWithOptions;
import com.aurastudio.backend.product.ProductRepository.Filter;
import com.aurastudio.backend.product.ProductRepository.ProductFields;
import com.aurastudio.backend.product.ProductRepository.ProductRow;
import com.aurastudio.backend.product.ProductRepository.Sort;
import com.aurastudio.backend.shop.ShopRepository;
import com.aurastudio.backend.shoptype.SizeKind;
import com.aurastudio.backend.tenant.ShopContext;
import com.aurastudio.backend.upload.UploadService;

/**
 * Product rules shared by the admin and the storefront
 * (docs/API_AND_DATABASE.md §8.2–8.6, §12). Admin methods take a
 * {@link ShopContext}; nothing here accepts a shop id from the client.
 */
@Service
public class ProductService {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int MAX_COLORS = 12;
	private static final int MAX_IMAGES = 10;
	private static final int MAX_STOCK = 10_000_000;
	private static final int MAX_PAGE = 100_000;
	public static final int MAX_SEARCH = 100;
	private static final BigDecimal MAX_PRICE = new BigDecimal("999999999.99");

	private final ProductRepository products;
	private final CategoryRepository categories;
	private final ShopRepository shops;
	private final UploadService uploads;
	private final CacheEvictions evictions;

	public ProductService(ProductRepository products, CategoryRepository categories, ShopRepository shops,
			UploadService uploads, CacheEvictions evictions) {
		this.products = products;
		this.categories = categories;
		this.shops = shops;
		this.uploads = uploads;
		this.evictions = evictions;
	}

	// ── reads ──────────────────────────────────────────────────────────────

	public record Page<T>(List<T> items, PageMeta meta) {
	}

	/** Admin table: search name/slug, category filter, 10 per page by default. */
	public Page<ProductListItem> adminList(ShopContext ctx, String q, String category, int page, int pageSize) {
		var filter = new Filter(Validators.blankToNull(searchText(q)), false, categoryFilter(category), Sort.CURATED);
		int size = clamp(pageSize, 1, 100);
		// Bounded so the OFFSET (page - 1) * size can't overflow an int.
		int p = clamp(page, 1, MAX_PAGE);
		long total = products.count(ctx.shopId(), filter);
		var rows = products.page(ctx.shopId(), filter, size, (p - 1) * size);
		var children = products.children(ctx.shopId(), rows.stream().map(ProductRow::id).toList());
		var items = rows.stream()
				.map(r -> new ProductListItem(r.id(), r.slug(), r.name(), r.price(), r.stock(), r.badge(),
						children.categories(r.id()), children.images(r.id()).stream().findFirst().orElse(null)))
				.toList();
		return new Page<>(items, PageMeta.of(p, size, total));
	}

	public ProductWithOptions adminGet(ShopContext ctx, UUID id) {
		var row = products.findById(ctx.shopId(), id).orElseThrow(() -> ApiException.notFound(Messages.PRODUCT_NOT_FOUND));
		return new ProductWithOptions(toDto(ctx.shopId(), List.of(row), SizeKind.NONE).get(0), formOptions(ctx));
	}

	public FormOptions formOptions(ShopContext ctx) {
		var shop = shops.findById(ctx.shopId()).orElseThrow(() -> ApiException.notFound(Messages.SHOP_NOT_FOUND));
		SizeKind sizeKind = shop.sizeKind();
		return new FormOptions(categories.list(ctx.shopId()), shop.typeLabel(), sizeKind.db(), sizeKind.options(),
				sizeKind.defaults());
	}

	/**
	 * Rows → DTOs with children (the storefront lists come from the cached result of this,
	 * see StorefrontCatalog). For the storefront ({@code sizeKind} with a size
	 * system) products saved without sizes show that system's defaults.
	 */
	public List<ProductDto> toDto(UUID shopId, List<ProductRow> rows, SizeKind sizeKind) {
		var children = products.children(shopId, rows.stream().map(ProductRow::id).toList());
		return rows.stream().map(r -> {
			List<String> sizes = children.sizes(r.id());
			if (sizes.isEmpty() && sizeKind.hasSizes()) {
				sizes = sizeKind.defaults();
			}
			return new ProductDto(r.id(), r.slug(), r.name(), r.description(), r.price(), r.stock(), r.badge(),
					r.badgeTone(), r.atelier(), children.categories(r.id()), children.images(r.id()), sizes,
					children.colors(r.id()), null);
		}).toList();
	}

	// ── writes ─────────────────────────────────────────────────────────────

	@Transactional
	public ProductDto create(ShopContext ctx, ProductRequest request) {
		var valid = validate(ctx.shopId(), request);
		String slug = uniqueSlug(ctx.shopId(), valid.fields().name());
		UUID id = products.insert(ctx.shopId(), slug, valid.fields());
		products.replaceChildren(ctx.shopId(), id, valid.images(), valid.sizes(), valid.colors(), valid.categoryIds());
		evictions.productsChanged(ctx.shopId());
		return reload(ctx, id);
	}

	@Transactional
	public ProductDto update(ShopContext ctx, UUID id, ProductRequest request) {
		var valid = validate(ctx.shopId(), request);
		if (products.update(ctx.shopId(), id, valid.fields()) == 0) {
			throw ApiException.notFound(Messages.PRODUCT_NOT_FOUND);
		}
		// The update matched id AND shop_id, so replacing children is safe.
		products.replaceChildren(ctx.shopId(), id, valid.images(), valid.sizes(), valid.colors(), valid.categoryIds());
		evictions.productsChanged(ctx.shopId());
		return reload(ctx, id);
	}

	@Transactional
	public void delete(ShopContext ctx, UUID id) {
		if (products.delete(ctx.shopId(), id) == 0) {
			throw ApiException.notFound(Messages.PRODUCT_NOT_FOUND);
		}
		evictions.productsChanged(ctx.shopId());
	}

	private ProductDto reload(ShopContext ctx, UUID id) {
		var row = products.findById(ctx.shopId(), id).orElseThrow();
		return toDto(ctx.shopId(), List.of(row), SizeKind.NONE).get(0);
	}

	private record Valid(ProductFields fields, List<String> images, List<String> sizes, List<ColorDto> colors,
			List<UUID> categoryIds) {
	}

	/** Validation rules of §12, in the same order as the Next.js app. */
	private Valid validate(UUID shopId, ProductRequest r) {
		if (r == null) {
			throw ApiException.validation(Messages.BAD_REQUEST);
		}
		// Limits = column sizes in V1__init_schema.sql (docs/features/20-input-security.md).
		String name = Validators.text(r.name(), "name", 120);
		if (name.isEmpty()) {
			throw ApiException.validation("name", Messages.PRODUCT_NAME_REQUIRED);
		}
		// numeric(12,2): positive, at most 2 decimals, below the column's maximum.
		if (r.price() == null || r.price().compareTo(BigDecimal.ZERO) <= 0 || r.price().compareTo(MAX_PRICE) > 0
				|| r.price().stripTrailingZeros().scale() > 2) {
			throw ApiException.validation("price", Messages.PRICE_INVALID);
		}
		int stock = r.stock() == null ? 0 : r.stock();
		if (stock < 0 || stock > MAX_STOCK) {
			throw ApiException.validation("stock", Messages.STOCK_INVALID);
		}
		String description = Validators.multiline(r.description(), "description", 5000);
		if (description.isEmpty()) {
			throw ApiException.validation("description", Messages.DESCRIPTION_REQUIRED);
		}
		String badge = Validators.blankToNull(Validators.text(r.badge(), "badge", 40));
		String atelier = Validators.text(r.atelier(), "atelier", 80);

		// Categories must exist in THIS shop.
		var codes = new LinkedHashSet<>(Validators.list(r.categories(), "categories", 20));
		List<CategoryDto> found = categories.findByCodes(shopId, codes);
		if (codes.isEmpty() || found.size() != codes.size()) {
			throw ApiException.validation("categories", Messages.CATEGORY_REQUIRED);
		}

		// Images: https URLs or /uploads files of THIS shop.
		List<String> images = new ArrayList<>();
		for (String raw : Validators.list(r.images(), "images", MAX_IMAGES)) {
			String url = Validators.text(raw);
			if (url.isEmpty()) {
				continue;
			}
			String file = Validators.uploadFileName(url);
			boolean ok = file != null ? uploads.belongsToShop(shopId, file) : Validators.isRemoteUrl(url);
			if (!ok) {
				throw ApiException.validation("images", Messages.IMAGE_INVALID_PREFIX + url);
			}
			images.add(url);
		}
		if (images.isEmpty()) {
			throw ApiException.validation("images", Messages.IMAGE_REQUIRED);
		}

		// Sizes from the shop type's size system only.
		SizeKind sizeKind = sizeKind(shopId);
		List<String> sizes = List.of();
		if (sizeKind.hasSizes()) {
			sizes = sizeKind.ordered(Validators.list(r.sizes(), "sizes", 20));
			if (sizes.isEmpty()) {
				throw ApiException.validation("sizes", Messages.SIZE_REQUIRED);
			}
		}

		List<ColorDto> colors = new ArrayList<>();
		var names = new HashSet<String>();
		if (r.colors() != null && r.colors().size() > MAX_COLORS) {
			throw ApiException.validation("colors", Messages.COLORS_TOO_MANY);
		}
		for (ColorDto c : Validators.list(r.colors(), "colors", MAX_COLORS)) {
			String colorName = Validators.text(c == null ? null : c.name(), "colors", 30);
			if (colorName.isEmpty()) {
				throw ApiException.validation("colors", Messages.COLOR_NAME_REQUIRED);
			}
			if (!Validators.isHexColor(c.hex())) {
				throw ApiException.validation("colors", Messages.COLOR_HEX_INVALID);
			}
			if (!names.add(colorName)) {
				throw ApiException.validation("colors", Messages.COLOR_NAMES_DUPLICATE);
			}
			colors.add(new ColorDto(colorName, c.hex().toLowerCase(Locale.ROOT)));
		}

		String tone = "gold".equals(r.badgeTone()) ? "gold" : "neutral";
		var fields = new ProductFields(name, description, r.price(), stock, badge, tone, atelier);
		return new Valid(fields, images, sizes, colors, found.stream().map(CategoryDto::id).toList());
	}

	private SizeKind sizeKind(UUID shopId) {
		return shops.findById(shopId).map(s -> s.sizeKind()).orElse(SizeKind.NONE);
	}

	/** Latin part of the name + 6 random hex; "product-xxxxxx" for all-Khmer names. */
	private String uniqueSlug(UUID shopId, String name) {
		String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
		if (base.isEmpty()) {
			base = "product";
		}
		if (base.length() > 60) {
			base = base.substring(0, 60).replaceAll("-+$", "");
		}
		String slug;
		do {
			byte[] bytes = new byte[3];
			RANDOM.nextBytes(bytes);
			slug = base + "-" + HexFormat.of().formatHex(bytes);
		}
		while (products.slugExists(shopId, slug));
		return slug;
	}

	/** A search box value: at most {@link #MAX_SEARCH} characters, else 400. */
	public static String searchText(String q) {
		String value = Validators.text(q);
		if (value.codePointCount(0, value.length()) > MAX_SEARCH) {
			throw ApiException.validation("q", Messages.SEARCH_TOO_LONG);
		}
		return value;
	}

	private static String categoryFilter(String category) {
		String c = Validators.blankToNull(category);
		return c == null || "all".equals(c) ? null : c;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
