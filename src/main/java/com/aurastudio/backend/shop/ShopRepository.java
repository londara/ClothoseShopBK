package com.aurastudio.backend.shop;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.aurastudio.backend.shoptype.SizeKind;

/** SQL for {@code shops} and {@code shop_settings}. */
@Repository
public class ShopRepository {

	private static final String SELECT = """
			SELECT s.id, s.slug, s.status::text AS status, st.shop_name, t.code AS type_code, t.label AS type_label,
			       t.size_kind::text AS size_kind, s.created_at, s.approved_at
			FROM shops s
			JOIN shop_settings st ON st.shop_id = s.id
			JOIN shop_types t ON t.id = s.shop_type_id
			""";

	private final JdbcClient jdbc;

	public ShopRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	static ShopRow map(ResultSet rs, int i) throws SQLException {
		return new ShopRow(rs.getObject("id", UUID.class), rs.getString("slug"),
				ShopStatus.fromDb(rs.getString("status")), rs.getString("shop_name"), rs.getString("type_code"),
				rs.getString("type_label"), SizeKind.fromDb(rs.getString("size_kind")),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("approved_at", OffsetDateTime.class));
	}

	public Optional<ShopRow> findById(UUID id) {
		return jdbc.sql(SELECT + " WHERE s.id = :id").param("id", id).query(ShopRepository::map).optional();
	}

	public Optional<ShopRow> findBySlug(String slug) {
		return jdbc.sql(SELECT + " WHERE s.slug = :slug").param("slug", slug).query(ShopRepository::map).optional();
	}

	public List<ShopRow> findActive() {
		return jdbc.sql(SELECT + " WHERE s.status = 'active' ORDER BY st.shop_name").query(ShopRepository::map).list();
	}

	/** Platform admin only: every shop, pending → active → suspended, then by name. */
	public List<ShopRow> findAllForPlatform() {
		return jdbc.sql(SELECT + """
				 ORDER BY CASE s.status WHEN 'pending' THEN 0 WHEN 'active' THEN 1 ELSE 2 END, st.shop_name""")
				.query(ShopRepository::map).list();
	}

	public boolean slugExists(String slug) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM shops WHERE slug = :slug)").param("slug", slug)
				.query(Boolean.class).single();
	}

	public UUID insert(String slug, UUID shopTypeId, ShopStatus status) {
		return jdbc.sql("""
				INSERT INTO shops (slug, shop_type_id, status, approved_at)
				VALUES (:slug, :typeId, CAST(:status AS shop_status), CASE WHEN :status = 'active' THEN now() END)
				RETURNING id""")
				.param("slug", slug).param("typeId", shopTypeId).param("status", status.db()).query(UUID.class)
				.single();
	}

	public void insertSettings(UUID shopId, ShopSettings s) {
		jdbc.sql("""
				INSERT INTO shop_settings (shop_id, shop_name, telegram, announcement, hero_title, hero_subtitle,
				                           hero_image, background_color, about_title, about_me)
				VALUES (:shopId, :name, :telegram, :announcement, :heroTitle, :heroSubtitle, :heroImage, :bg,
				        :aboutTitle, :aboutMe)""")
				.param("shopId", shopId).param("name", s.shopName()).param("telegram", s.telegram())
				.param("announcement", s.announcement()).param("heroTitle", s.heroTitle())
				.param("heroSubtitle", s.heroSubtitle()).param("heroImage", s.heroImage())
				.param("bg", s.backgroundColor()).param("aboutTitle", s.aboutTitle()).param("aboutMe", s.aboutMe())
				.update();
	}

	public ShopSettings getSettings(UUID shopId) {
		return jdbc.sql("""
				SELECT shop_name, telegram, announcement, hero_title, hero_subtitle, hero_image, background_color,
				       about_title, about_me
				FROM shop_settings WHERE shop_id = :shopId""").param("shopId", shopId)
				.query((rs, i) -> new ShopSettings(rs.getString("shop_name"), rs.getString("telegram"),
						rs.getString("announcement"), rs.getString("hero_title"), rs.getString("hero_subtitle"),
						rs.getString("hero_image"), rs.getString("background_color"), rs.getString("about_title"),
						rs.getString("about_me")))
				.single();
	}

	public void updateSettings(UUID shopId, ShopSettings s) {
		jdbc.sql("""
				UPDATE shop_settings SET shop_name = :name, telegram = :telegram, announcement = :announcement,
				       hero_title = :heroTitle, hero_subtitle = :heroSubtitle, hero_image = :heroImage,
				       background_color = :bg, about_title = :aboutTitle, about_me = :aboutMe, updated_at = now()
				WHERE shop_id = :shopId""")
				.param("shopId", shopId).param("name", s.shopName()).param("telegram", s.telegram())
				.param("announcement", s.announcement()).param("heroTitle", s.heroTitle())
				.param("heroSubtitle", s.heroSubtitle()).param("heroImage", s.heroImage())
				.param("bg", s.backgroundColor()).param("aboutTitle", s.aboutTitle()).param("aboutMe", s.aboutMe())
				.update();
	}

	/** First activation records who approved it and when. */
	public void updateStatus(UUID shopId, ShopStatus status, UUID byUserId) {
		jdbc.sql("""
				UPDATE shops SET status = CAST(:status AS shop_status), updated_at = now(),
				       approved_at = CASE WHEN :status = 'active' AND approved_at IS NULL THEN now() ELSE approved_at END,
				       approved_by = CASE WHEN :status = 'active' AND approved_by IS NULL THEN :by ELSE approved_by END
				WHERE id = :id""")
				.param("status", status.db()).param("by", byUserId).param("id", shopId).update();
	}

	/** Cascades to settings, categories, products (+ children), users, uploads rows. */
	public void delete(UUID shopId) {
		jdbc.sql("DELETE FROM shops WHERE id = :id").param("id", shopId).update();
	}

	public long countPending() {
		return jdbc.sql("SELECT count(*) FROM shops WHERE status = 'pending'").query(Long.class).single();
	}
}
