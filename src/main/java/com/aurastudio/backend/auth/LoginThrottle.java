package com.aurastudio.backend.auth;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.aurastudio.backend.config.AuraProperties;

/**
 * Persistent login lockout ({@code login_attempts}): after {@code maxFailures}
 * wrong passwords for one identifier, block it for {@code lockDuration}.
 * Failures older than the lock window start a new count.
 */
@Component
public class LoginThrottle {

	private final JdbcClient jdbc;
	private final AuraProperties.Login props;

	public LoginThrottle(JdbcClient jdbc, AuraProperties properties) {
		this.jdbc = jdbc;
		this.props = properties.login();
	}

	private record Attempt(int failures, OffsetDateTime lockedUntil, OffsetDateTime updatedAt) {
	}

	/** Seconds until the identifier may try again, 0 when allowed. */
	public long lockedSeconds(String identifier) {
		return jdbc.sql("SELECT locked_until FROM login_attempts WHERE identifier = :id").param("id", identifier)
				.query((rs, i) -> rs.getObject("locked_until", OffsetDateTime.class)).optional()
				.map(until -> Math.max(0, Duration.between(now(), until).toSeconds())).orElse(0L);
	}

	public void recordFailure(String identifier) {
		var current = jdbc.sql("SELECT failures, locked_until, updated_at FROM login_attempts WHERE identifier = :id")
				.param("id", identifier)
				.query((rs, i) -> new Attempt(rs.getInt("failures"), rs.getObject("locked_until", OffsetDateTime.class),
						rs.getObject("updated_at", OffsetDateTime.class)))
				.optional();
		boolean windowExpired = current.map(a -> a.updatedAt().isBefore(now().minus(props.lockDuration())))
				.orElse(true);
		int failures = windowExpired ? 1 : current.get().failures() + 1;
		OffsetDateTime lockedUntil = failures >= props.maxFailures() ? now().plus(props.lockDuration()) : null;
		jdbc.sql("""
				INSERT INTO login_attempts (identifier, failures, locked_until, updated_at)
				VALUES (:id, :failures, :until, now())
				ON CONFLICT (identifier) DO UPDATE
				SET failures = EXCLUDED.failures, locked_until = EXCLUDED.locked_until, updated_at = now()""")
				.param("id", identifier).param("failures", failures).param("until", lockedUntil).update();
	}

	public void clear(String identifier) {
		jdbc.sql("DELETE FROM login_attempts WHERE identifier = :id").param("id", identifier).update();
	}

	private static OffsetDateTime now() {
		return OffsetDateTime.now(ZoneOffset.UTC);
	}
}
