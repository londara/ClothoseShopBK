package com.aurastudio.backend.user;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL for {@code users}. Methods that act on shop data take {@code shopId} and
 * filter by it (docs/API_AND_DATABASE.md §4.1); only login/uniqueness lookups
 * are platform-wide on purpose.
 */
@Repository
public class UserRepository {

	private static final String COLUMNS = """
			id, shop_id, role::text AS role, is_platform_admin, status::text AS status,
			name, title, email, phone, avatar, password_hash""";

	private final JdbcClient jdbc;

	public UserRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	static UserRow map(ResultSet rs, int i) throws SQLException {
		return new UserRow(rs.getObject("id", UUID.class), rs.getObject("shop_id", UUID.class),
				UserRole.fromDb(rs.getString("role")), rs.getBoolean("is_platform_admin"),
				UserStatus.fromDb(rs.getString("status")), rs.getString("name"), rs.getString("title"),
				rs.getString("email"), rs.getString("phone"), rs.getString("avatar"), rs.getString("password_hash"));
	}

	public Optional<UserRow> findById(UUID id) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM users WHERE id = :id").param("id", id).query(UserRepository::map)
				.optional();
	}

	/** Login lookup across all shops: email (contains "@") or normalised phone. */
	public Optional<UserRow> findByIdentifier(String normalizedIdentifier) {
		String column = normalizedIdentifier.contains("@") ? "email" : "phone";
		return jdbc.sql("SELECT " + COLUMNS + " FROM users WHERE " + column + " = :value")
				.param("value", normalizedIdentifier).query(UserRepository::map).optional();
	}

	public boolean emailTaken(String email, UUID exceptUserId) {
		return email != null && jdbc.sql("SELECT count(*) FROM users WHERE email = :email AND id <> :except")
				.param("email", email).param("except", orNil(exceptUserId)).query(Long.class).single() > 0;
	}

	public boolean phoneTaken(String phone, UUID exceptUserId) {
		return phone != null && jdbc.sql("SELECT count(*) FROM users WHERE phone = :phone AND id <> :except")
				.param("phone", phone).param("except", orNil(exceptUserId)).query(Long.class).single() > 0;
	}

	public boolean anyUserExists() {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM users)").query(Boolean.class).single();
	}

	public UUID insert(UUID shopId, UserRole role, boolean platformAdmin, String name, String title, String email,
			String phone, String passwordHash) {
		return jdbc.sql("""
				INSERT INTO users (shop_id, role, is_platform_admin, status, name, title, email, phone, password_hash)
				VALUES (:shopId, CAST(:role AS user_role), :pa, 'active', :name, :title, :email, :phone, :hash)
				RETURNING id""")
				.param("shopId", shopId).param("role", role.db()).param("pa", platformAdmin).param("name", name)
				.param("title", title).param("email", email).param("phone", phone).param("hash", passwordHash)
				.query(UUID.class).single();
	}

	public void updatePassword(UUID userId, String passwordHash) {
		jdbc.sql("UPDATE users SET password_hash = :hash, updated_at = now() WHERE id = :id")
				.param("hash", passwordHash).param("id", userId).update();
	}

	/** Own profile — scoped by id AND shop. */
	public int updateProfile(UUID shopId, UUID userId, String name, String title, String email, String phone,
			String avatar) {
		return jdbc.sql("""
				UPDATE users SET name = :name, title = :title, email = :email, phone = :phone, avatar = :avatar,
				       updated_at = now()
				WHERE id = :id AND shop_id = :shopId""")
				.param("name", name).param("title", title).param("email", email).param("phone", phone)
				.param("avatar", avatar).param("id", userId).param("shopId", shopId).update();
	}

	/** Accounts of one shop: pending first, then by name. */
	public List<UserRow> listByShop(UUID shopId) {
		return jdbc.sql("SELECT " + COLUMNS + """
				 FROM users WHERE shop_id = :shopId
				ORDER BY (status = 'pending') DESC, name""").param("shopId", shopId).query(UserRepository::map).list();
	}

	public Optional<UserRow> findInShop(UUID shopId, UUID userId) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM users WHERE id = :id AND shop_id = :shopId")
				.param("id", userId).param("shopId", shopId).query(UserRepository::map).optional();
	}

	/** Staff only — the owner row never matches. */
	public int updateStaffStatus(UUID shopId, UUID userId, UserStatus status) {
		return jdbc.sql("""
				UPDATE users SET status = CAST(:status AS user_status), updated_at = now()
				WHERE id = :id AND shop_id = :shopId AND role = 'staff'""")
				.param("status", status.db()).param("id", userId).param("shopId", shopId).update();
	}

	public int deleteStaff(UUID shopId, UUID userId) {
		return jdbc.sql("DELETE FROM users WHERE id = :id AND shop_id = :shopId AND role = 'staff'")
				.param("id", userId).param("shopId", shopId).update();
	}

	public long countPending(UUID shopId) {
		return jdbc.sql("SELECT count(*) FROM users WHERE shop_id = :shopId AND status = 'pending'")
				.param("shopId", shopId).query(Long.class).single();
	}

	public Optional<UserRow> findOwner(UUID shopId) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM users WHERE shop_id = :shopId AND role = 'owner'")
				.param("shopId", shopId).query(UserRepository::map).optional();
	}

	public List<UUID> idsInShop(UUID shopId) {
		return jdbc.sql("SELECT id FROM users WHERE shop_id = :shopId").param("shopId", shopId).query(UUID.class)
				.list();
	}

	private static UUID orNil(UUID id) {
		return id == null ? new UUID(0, 0) : id;
	}
}
