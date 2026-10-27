package com.aurastudio.backend.product;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.aurastudio.backend.product.ProductDtos.ColorDto;

/**
 * SQL for products and their child rows. EVERY method takes the shop id and
 * filters by it (docs/API_AND_DATABASE.md §4.1); child tables are only touched
 * after the product was matched with {@code id AND shop_id}.
 */
@Repository
public class ProductRepository {

	private static final String COLUMNS = """
			p.id, p.slug, p.name, p.description, p.price, p.stock, p.badge, p.badge_tone::text AS badge_tone,
			p.atelier, p.sort_order""";

	private final JdbcClient jdbc;

	public ProductRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public record ProductRow(UUID id, String slug, String name, String description, BigDecimal price, int stock,
			String badge, String badgeTone, String atelier, int sortOrder) {
	}

	/** Fields written by create/update. */
	public record ProductFields(String name, String description, BigDecimal price, int stock, String badge,
			String badgeTone, String atelier) {
	}

	/** Children of a set of products, keyed by product id. */
	public record Children(Map<UUID, List<String>> images, Map<UUID, List<String>> sizes,
			Map<UUID, List<ColorDto>> colors, Map<UUID, List<String>> categories) {

		public List<String> images(UUID id) {
			return images.getOrDefault(id, List.of());
		}

		public List<String> sizes(UUID id) {
			return sizes.getOrDefault(id, List.of());
		}

		public List<ColorDto> colors(UUID id) {
			return colors.getOrDefault(id, List.of());
		}

		public List<String> categories(UUID id) {
			return categories.getOrDefault(id, List.of());
		}
	}

	public enum Sort {
		CURATED("p.sort_order, p.created_at DESC"), PRICE_ASC("p.price, p.sort_order"),
		PRICE_DESC("p.price DESC, p.sort_order");

		final String sql;

		Sort(String sql) {
			this.sql = sql;
		}

		public static Sort parse(String value) {
			if ("price-asc".equals(value)) {
				return PRICE_ASC;
			}
			if ("price-desc".equals(value)) {
				return PRICE_DESC;
			}
			return CURATED;
		}
	}

	/** Filters of the admin table and the storefront list. */
	public record Filter(String q, boolean searchDescription, String category, Sort sort) {
	}

	static ProductRow map(ResultSet rs, int i) throws SQLException {
		return new ProductRow(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("name"),
				rs.getString("description"), rs.getBigDecimal("price"), rs.getInt("stock"), rs.getString("badge"),
				rs.getString("badge_tone"), rs.getString("atelier"), rs.getInt("sort_order"));
	}

	public List<ProductRow> page(UUID shopId, Filter filter, int limit, int offset) {
		var where = where(filter);
		var spec = jdbc.sql("SELECT " + COLUMNS + " FROM products p WHERE " + where + " ORDER BY " + filter.sort().sql
				+ " LIMIT :limit OFFSET :offset");
		return bind(spec, shopId, filter).param("limit", limit).param("offset", offset).query(ProductRepository::map)
				.list();
	}

	public long count(UUID shopId, Filter filter) {
		var spec = jdbc.sql("SELECT count(*) FROM products p WHERE " + where(filter));
		return bind(spec, shopId, filter).query(Long.class).single();
	}

	private static String where(Filter f) {
		StringBuilder sql = new StringBuilder("p.shop_id = :shopId");
		if (f.q() != null) {
			sql.append(f.searchDescription()
					? " AND (p.name ILIKE :q OR p.description ILIKE :q OR coalesce(p.badge, '') ILIKE :q)"
					: " AND (p.name ILIKE :q OR p.slug ILIKE :q)");
		}
		if (f.category() != null) {
			sql.append(" ").append("""
					AND EXISTS (SELECT 1 FROM product_categories pc JOIN shop_categories c ON c.id = pc.category_id
					             WHERE pc.product_id = p.id AND pc.shop_id = :shopId AND c.code = :category)""");
		}
		return sql.toString();
	}

	private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, UUID shopId, Filter f) {
		spec = spec.param("shopId", shopId);
		if (f.q() != null) {
			spec = spec.param("q", "%" + f.q().replace("%", "\\%").replace("_", "\\_") + "%");
		}
		if (f.category() != null) {
			spec = spec.param("category", f.category());
		}
		return spec;
	}

	public Optional<ProductRow> findById(UUID shopId, UUID id) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM products p WHERE p.id = :id AND p.shop_id = :shopId")
				.param("id", id).param("shopId", shopId).query(ProductRepository::map).optional();
	}

	public Optional<ProductRow> findBySlug(UUID shopId, String slug) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM products p WHERE p.slug = :slug AND p.shop_id = :shopId")
				.param("slug", slug).param("shopId", shopId).query(ProductRepository::map).optional();
	}

	public boolean slugExists(UUID shopId, String slug) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM products WHERE shop_id = :shopId AND slug = :slug)")
				.param("shopId", shopId).param("slug", slug).query(Boolean.class).single();
	}

	/** New products get the smallest sort order, so they are listed first ("new arrivals"). */
	public UUID insert(UUID shopId, String slug, ProductFields f) {
		return jdbc.sql("""
				INSERT INTO products (shop_id, slug, name, description, price, stock, badge, badge_tone, atelier, sort_order)
				VALUES (:shopId, :slug, :name, :description, :price, :stock, :badge, CAST(:tone AS badge_tone), :atelier,
				        (SELECT coalesce(min(sort_order), 1) - 1 FROM products WHERE shop_id = :shopId))
				RETURNING id""")
				.param("shopId", shopId).param("slug", slug).param("name", f.name()).param("description", f.description())
				.param("price", f.price()).param("stock", f.stock()).param("badge", f.badge()).param("tone", f.badgeTone())
				.param("atelier", f.atelier()).query(UUID.class).single();
	}

	/** 0 rows = not in this shop → caller answers 404. */
	public int update(UUID shopId, UUID id, ProductFields f) {
		return jdbc.sql("""
				UPDATE products SET name = :name, description = :description, price = :price, stock = :stock,
				       badge = :badge, badge_tone = CAST(:tone AS badge_tone), atelier = :atelier, updated_at = now()
				WHERE id = :id AND shop_id = :shopId""")
				.param("name", f.name()).param("description", f.description()).param("price", f.price())
				.param("stock", f.stock()).param("badge", f.badge()).param("tone", f.badgeTone())
				.param("atelier", f.atelier()).param("id", id).param("shopId", shopId).update();
	}

	public int delete(UUID shopId, UUID id) {
		return jdbc.sql("DELETE FROM products WHERE id = :id AND shop_id = :shopId").param("id", id)
				.param("shopId", shopId).update();
	}

	/** Replace all children. Call only after the product matched {@code id AND shop_id}. */
	public void replaceChildren(UUID shopId, UUID productId, List<String> images, List<String> sizes,
			List<ColorDto> colors, Collection<UUID> categoryIds) {
		jdbc.sql("DELETE FROM product_images WHERE product_id = :id").param("id", productId).update();
		jdbc.sql("DELETE FROM product_sizes WHERE product_id = :id").param("id", productId).update();
		jdbc.sql("DELETE FROM product_colors WHERE product_id = :id").param("id", productId).update();
		jdbc.sql("DELETE FROM product_categories WHERE product_id = :id AND shop_id = :shopId").param("id", productId)
				.param("shopId", shopId).update();
		for (int i = 0; i < images.size(); i++) {
			jdbc.sql("INSERT INTO product_images (product_id, url, position) VALUES (:id, :url, :pos)")
					.param("id", productId).param("url", images.get(i)).param("pos", i).update();
		}
		for (int i = 0; i < sizes.size(); i++) {
			jdbc.sql("INSERT INTO product_sizes (product_id, size, position) VALUES (:id, :size, :pos)")
					.param("id", productId).param("size", sizes.get(i)).param("pos", i).update();
		}
		for (int i = 0; i < colors.size(); i++) {
			jdbc.sql("INSERT INTO product_colors (product_id, name, hex, position) VALUES (:id, :name, :hex, :pos)")
					.param("id", productId).param("name", colors.get(i).name()).param("hex", colors.get(i).hex())
					.param("pos", i).update();
		}
		// shop_id in both composite FKs: a category of another shop is rejected by the database too.
		for (UUID categoryId : categoryIds) {
			jdbc.sql("INSERT INTO product_categories (shop_id, product_id, category_id) VALUES (:shopId, :id, :cat)")
					.param("shopId", shopId).param("id", productId).param("cat", categoryId).update();
		}
	}

	/** Children of products that were already loaded with a shop filter. */
	public Children children(UUID shopId, Collection<UUID> productIds) {
		Map<UUID, List<String>> images = new HashMap<>();
		Map<UUID, List<String>> sizes = new HashMap<>();
		Map<UUID, List<ColorDto>> colors = new HashMap<>();
		Map<UUID, List<String>> categories = new HashMap<>();
		if (productIds.isEmpty()) {
			return new Children(images, sizes, colors, categories);
		}
		jdbc.sql("SELECT product_id, url FROM product_images WHERE product_id IN (:ids) ORDER BY position")
				.param("ids", productIds).query((rs, i) -> {
					images.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>()).add(rs.getString(2));
					return null;
				}).list();
		jdbc.sql("SELECT product_id, size FROM product_sizes WHERE product_id IN (:ids) ORDER BY position")
				.param("ids", productIds).query((rs, i) -> {
					sizes.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>()).add(rs.getString(2));
					return null;
				}).list();
		jdbc.sql("SELECT product_id, name, hex FROM product_colors WHERE product_id IN (:ids) ORDER BY position")
				.param("ids", productIds).query((rs, i) -> {
					colors.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
							.add(new ColorDto(rs.getString(2), rs.getString(3)));
					return null;
				}).list();
		jdbc.sql("""
				SELECT pc.product_id, c.code FROM product_categories pc
				JOIN shop_categories c ON c.id = pc.category_id
				WHERE pc.shop_id = :shopId AND pc.product_id IN (:ids) ORDER BY c.sort_order""")
				.param("shopId", shopId).param("ids", productIds).query((rs, i) -> {
					categories.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>()).add(rs.getString(2));
					return null;
				}).list();
		return new Children(images, sizes, colors, categories);
	}

	public record Stats(long totalProducts, long totalStock, BigDecimal stockValue) {
	}

	public Stats stats(UUID shopId) {
		return jdbc.sql("""
				SELECT count(*), coalesce(sum(stock), 0), coalesce(sum(stock * price), 0)
				FROM products WHERE shop_id = :shopId""").param("shopId", shopId)
				.query((rs, i) -> new Stats(rs.getLong(1), rs.getLong(2), rs.getBigDecimal(3))).single();
	}

	public List<ProductRow> lowStock(UUID shopId, int threshold) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM products p WHERE p.shop_id = :shopId AND p.stock <= :t ORDER BY p.stock, p.name")
				.param("shopId", shopId).param("t", threshold).query(ProductRepository::map).list();
	}
}
