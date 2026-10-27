package com.aurastudio.backend.shoptype;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Platform-wide list of shop types (options of the "open a shop" form). */
@Repository
public class ShopTypeRepository {

	private final JdbcClient jdbc;

	public ShopTypeRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public record ShopTypeRow(UUID id, String code, String label, SizeKind sizeKind) {
	}

	public List<ShopTypeRow> findAll() {
		return jdbc.sql("SELECT id, code, label, size_kind::text AS size_kind FROM shop_types ORDER BY sort_order, label")
				.query((rs, i) -> new ShopTypeRow(rs.getObject("id", UUID.class), rs.getString("code"),
						rs.getString("label"), SizeKind.fromDb(rs.getString("size_kind"))))
				.list();
	}

	public Optional<ShopTypeRow> findByCode(String code) {
		return findAll().stream().filter(t -> t.code().equals(code)).findFirst();
	}

	public Optional<ShopTypeRow> findById(UUID id) {
		return findAll().stream().filter(t -> t.id().equals(id)).findFirst();
	}
}
