package com.aurastudio.backend.auth.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurastudio.backend.common.ApiException;
import com.aurastudio.backend.common.ErrorCode;
import com.aurastudio.backend.common.Messages;
import com.aurastudio.backend.config.AuraProperties;

/**
 * Refresh tokens (docs/API_AND_DATABASE.md §2.7, §5.3): 64 random bytes,
 * only the SHA-256 is stored, rotated on every use. Using an already-rotated
 * token revokes its whole family (someone copied it).
 */
@Service
public class RefreshTokenService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final JdbcClient jdbc;
	private final AuraProperties.Jwt props;

	public RefreshTokenService(JdbcClient jdbc, AuraProperties properties) {
		this.jdbc = jdbc;
		this.props = properties.jwt();
	}

	/** Result of a successful rotation: the owner and the new raw token. */
	public record Rotation(UUID userId, String refreshToken) {
	}

	/** New login → new family. Returns the raw token (only time it exists in clear text). */
	public String issue(UUID userId, String userAgent, String ip) {
		return insert(userId, UUID.randomUUID(), userAgent, ip).raw();
	}

	/**
	 * The ApiException after a reuse must NOT roll back: revoking the family is
	 * the whole point, so the transaction commits and then the 401 is returned.
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public Rotation rotate(String rawToken, String userAgent, String ip) {
		var row = jdbc.sql("""
				SELECT id, user_id, family_id, expires_at, revoked_at FROM refresh_tokens
				WHERE token_hash = :hash FOR UPDATE""").param("hash", hash(rawToken))
				.query((rs, i) -> new Row(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
						rs.getObject("family_id", UUID.class), rs.getObject("expires_at", OffsetDateTime.class),
						rs.getObject("revoked_at", OffsetDateTime.class)))
				.optional().orElseThrow(RefreshTokenService::invalid);

		if (row.revokedAt() != null) {
			// Reuse of a rotated/revoked token: revoke every token of this login.
			revokeFamily(row.familyId());
			throw invalid();
		}
		if (row.expiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
			throw invalid();
		}
		Inserted next = insert(row.userId(), row.familyId(), userAgent, ip);
		jdbc.sql("UPDATE refresh_tokens SET revoked_at = now(), replaced_by = :next WHERE id = :id")
				.param("next", next.id()).param("id", row.id()).update();
		return new Rotation(row.userId(), next.raw());
	}

	/** Logout: revoke this token (silently ignore unknown ones). */
	public void revoke(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			return;
		}
		jdbc.sql("UPDATE refresh_tokens SET revoked_at = now() WHERE token_hash = :hash AND revoked_at IS NULL")
				.param("hash", hash(rawToken)).update();
	}

	/** Password change / account disabled. */
	public void revokeAllForUser(UUID userId) {
		jdbc.sql("UPDATE refresh_tokens SET revoked_at = now() WHERE user_id = :userId AND revoked_at IS NULL")
				.param("userId", userId).update();
	}

	/** Shop suspended: everyone of that shop is logged out. */
	public void revokeAllForShop(UUID shopId) {
		jdbc.sql("""
				UPDATE refresh_tokens SET revoked_at = now()
				WHERE revoked_at IS NULL AND user_id IN (SELECT id FROM users WHERE shop_id = :shopId)""")
				.param("shopId", shopId).update();
	}

	private void revokeFamily(UUID familyId) {
		jdbc.sql("UPDATE refresh_tokens SET revoked_at = now() WHERE family_id = :family AND revoked_at IS NULL")
				.param("family", familyId).update();
	}

	private Inserted insert(UUID userId, UUID familyId, String userAgent, String ip) {
		byte[] bytes = new byte[64];
		RANDOM.nextBytes(bytes);
		String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		UUID id = jdbc.sql("""
				INSERT INTO refresh_tokens (user_id, token_hash, family_id, expires_at, user_agent, ip)
				VALUES (:userId, :hash, :family, :expires, :ua, :ip) RETURNING id""")
				.param("userId", userId).param("hash", hash(raw)).param("family", familyId)
				.param("expires", OffsetDateTime.now(ZoneOffset.UTC).plus(props.refreshTokenTtl()))
				.param("ua", truncate(userAgent, 300)).param("ip", ip).query(UUID.class).single();
		return new Inserted(id, raw);
	}

	static String hash(String raw) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String truncate(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max);
	}

	private static ApiException invalid() {
		return new ApiException(ErrorCode.TOKEN_INVALID, Messages.TOKEN_INVALID);
	}

	private record Row(UUID id, UUID userId, UUID familyId, OffsetDateTime expiresAt, OffsetDateTime revokedAt) {
	}

	private record Inserted(UUID id, String raw) {
	}
}
