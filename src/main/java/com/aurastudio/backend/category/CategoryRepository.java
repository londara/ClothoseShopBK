package com.aurastudio.backend.category;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL for {@code shop_categories}; every query is scoped by {@code shop_id}. */
@Repository
public class CategoryRepository {

	public static final String BEST_SELLERS = "best-sellers";

	private final JdbcClient jdbc;

	public CategoryRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<CategoryDto> list(UUID shopId) {
		return jdbc.sql("SELECT id, code, label, sort_order FROM shop_categories WHERE shop_id = :shopId ORDER BY sort_order, created_at")
				.param("shopId", shopId)
				.query((rs, i) -> new CategoryDto(rs.getObject("id", UUID.class), rs.getString("code"),
						rs.getString("label"), rs.getInt("sort_order")))
				.list();
	}

	/** New shops start with their type's default categories. */
	public void copyFromType(UUID shopId, UUID shopTypeId) {
		jdbc.sql("""
				INSERT INTO shop_categories (shop_id, code, label, sort_order)
				SELECT :shopId, code, label, sort_order FROM shop_type_categories WHERE shop_type_id = :typeId""")
				.param("shopId", shopId).param("typeId", shopTypeId).update();
	}

	public void insert(UUID shopId, String code, String label, int sortOrder) {
		jdbc.sql("INSERT INTO shop_categories (shop_id, code, label, sort_order) VALUES (:shopId, :code, :label, :sort)")
				.param("shopId", shopId).param("code", code).param("label", label).param("sort", sortOrder).update();
	}

	/** Move "best sellers" behind everything else so it stays the last filter. */
	public void moveBestSellersLast(UUID shopId) {
		jdbc.sql("""
				UPDATE shop_categories SET sort_order =
				  (SELECT coalesce(max(sort_order), 0) + 1 FROM shop_categories WHERE shop_id = :shopId)
				WHERE shop_id = :shopId AND code = :code""")
				.param("shopId", shopId).param("code", BEST_SELLERS).update();
	}

	/** Category ids of THIS shop for the given codes (codes of other shops never match). */
	public List<CategoryDto> findByCodes(UUID shopId, Collection<String> codes) {
		if (codes.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("SELECT id, code, label, sort_order FROM shop_categories WHERE shop_id = :shopId AND code IN (:codes)")
				.param("shopId", shopId).param("codes", codes)
				.query((rs, i) -> new CategoryDto(rs.getObject("id", UUID.class), rs.getString("code"),
						rs.getString("label"), rs.getInt("sort_order")))
				.list();
	}
}
