package com.aurastudio.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import com.aurastudio.backend.common.Messages;
import com.aurastudio.backend.support.IntegrationTest;

/** docs/API_AND_DATABASE.md §5 — JWT login with phone number or email. */
class AuthApiTest extends IntegrationTest {

	@Autowired
	JwtEncoder jwtEncoder;

	@Autowired
	JdbcClient jdbc;

	@Test
	void loginWithEmailInAnyCase() throws Exception {
		TestShop shop = signup("couture");
		postJson("/api/v1/auth/login", null, Map.of("identifier", shop.email().toUpperCase(), "password", shop.password()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.data.expiresIn").value(900))
				.andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
				.andExpect(jsonPath("$.data.refreshExpiresIn").value(2592000))
				.andExpect(jsonPath("$.data.user.email").value(shop.email()))
				.andExpect(jsonPath("$.data.user.role").value("owner"))
				.andExpect(jsonPath("$.data.shop.slug").value(shop.slug()))
				.andExpect(jsonPath("$.data.shop.status").value("pending"));
	}

	@Test
	void loginWithPhoneInEveryFormat() throws Exception {
		TestShop shop = signup("jewelry");
		String local = shop.phone(); // 09xxxxxxx
		String spaced = local.substring(0, 3) + " " + local.substring(3, 6) + " " + local.substring(6);
		String international = "+855 " + local.substring(1);
		String digits855 = "855" + local.substring(1);
		for (String identifier : List.of(local, spaced, international, digits855)) {
			postJson("/api/v1/auth/login", null, Map.of("identifier", identifier, "password", shop.password()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.user.phone").value(local));
		}
	}

	@Test
	void unknownAccountAndWrongPasswordGiveTheSameError() throws Exception {
		TestShop shop = signup("couture");
		postJson("/api/v1/auth/login", null, Map.of("identifier", shop.email(), "password", "wrong-pass"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
				.andExpect(jsonPath("$.error.message").value(Messages.INVALID_CREDENTIALS));
		postJson("/api/v1/auth/login", null, Map.of("identifier", "nobody@test.com", "password", "whatever1"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.message").value(Messages.INVALID_CREDENTIALS));
		postJson("/api/v1/auth/login", null, Map.of("identifier", "", "password", ""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION"));
	}

	@Test
	void fiveWrongPasswordsLockTheLogin() throws Exception {
		TestShop shop = signup("couture");
		for (int i = 0; i < 5; i++) {
			postJson("/api/v1/auth/login", null, Map.of("identifier", shop.phone(), "password", "wrong-" + i))
					.andExpect(status().isUnauthorized());
		}
		postJson("/api/v1/auth/login", null, Map.of("identifier", shop.phone(), "password", shop.password()))
				.andExpect(status().isLocked())
				.andExpect(jsonPath("$.error.code").value("LOCKED"))
				.andExpect(header().exists("Retry-After"));
		// the lock is per identifier: the email of the same account still works
		postJson("/api/v1/auth/login", null, Map.of("identifier", shop.email(), "password", shop.password()))
				.andExpect(status().isOk());
	}

	@Test
	void refreshRotatesAndReuseRevokesTheWholeLogin() throws Exception {
		TestShop shop = signup("couture");
		String first = loginData(shop.email(), shop.password()).get("refreshToken").asString();

		var rotated = body(postJson("/api/v1/auth/refresh", null, Map.of("refreshToken", first)));
		String second = rotated.get("data").get("refreshToken").asString();
		assertThat(rotated.get("data").get("accessToken").asString()).isNotBlank();
		assertThat(second).isNotEqualTo(first);

		// the old token is dead, and re-using it kills the new one too (copied token)
		postJson("/api/v1/auth/refresh", null, Map.of("refreshToken", first))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
		postJson("/api/v1/auth/refresh", null, Map.of("refreshToken", second))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void logoutRevokesTheRefreshToken() throws Exception {
		TestShop shop = signup("couture");
		String refresh = loginData(shop.email(), shop.password()).get("refreshToken").asString();
		postJson("/api/v1/auth/logout", null, Map.of("refreshToken", refresh)).andExpect(status().isNoContent());
		postJson("/api/v1/auth/refresh", null, Map.of("refreshToken", refresh)).andExpect(status().isUnauthorized());
	}

	@Test
	void meNeedsAValidToken() throws Exception {
		getJson("/api/v1/auth/me", null)
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
		getJson("/api/v1/auth/me", "not-a-jwt").andExpect(status().isUnauthorized());

		TestShop shop = signup("couture");
		getJson("/api/v1/auth/me", shop.token())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.user.email").value(shop.email()))
				.andExpect(jsonPath("$.data.shop.slug").value(shop.slug()))
				.andExpect(jsonPath("$.data.counts.pendingStaff").value(0));
	}

	@Test
	void expiredAccessTokenSaysTokenExpired() throws Exception {
		TestShop shop = signup("couture");
		var userId = jdbc.sql("SELECT id FROM users WHERE email = :e").param("e", shop.email()).query(UUID.class).single();
		Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
		var claims = JwtClaimsSet.builder().issuer("aura-studio").audience(List.of("aura-admin"))
				.subject(userId.toString()).issuedAt(past.minusSeconds(900)).expiresAt(past)
				.claim("shop", shop.shopId()).claim("role", "owner").claim("pa", false).build();
		String expired = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
		getJson("/api/v1/auth/me", expired)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"));
	}

	@Test
	void changingThePasswordRevokesOtherLogins() throws Exception {
		TestShop shop = signup("couture");
		String otherDevice = loginData(shop.email(), shop.password()).get("refreshToken").asString();

		patchJson("/api/v1/auth/password", shop.token(),
				Map.of("currentPassword", "nope-nope", "newPassword", "newpass123", "confirmPassword", "newpass123"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.field").value("currentPassword"));

		patchJson("/api/v1/auth/password", shop.token(), Map.of("currentPassword", shop.password(), "newPassword",
				"newpass123", "confirmPassword", "newpass123"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value(Messages.PASSWORD_CHANGED))
				.andExpect(jsonPath("$.data.refreshToken").isNotEmpty());

		postJson("/api/v1/auth/refresh", null, Map.of("refreshToken", otherDevice)).andExpect(status().isUnauthorized());
		postJson("/api/v1/auth/login", null, Map.of("identifier", shop.email(), "password", "newpass123"))
				.andExpect(status().isOk());
	}

	/** Hash made by Node's crypto.scrypt — the format of the Next.js app (src/lib/password.ts). */
	@Test
	void migratedNextJsPasswordHashStillWorksAndIsUpgraded() throws Exception {
		TestShop shop = signup("couture");
		String nodeHash = "9f3a1c2b7d4e5f60a1b2c3d4e5f60718:fe1244e6415093fb915767ce99a45819beeffb98e86a82a233ed7a1da8326bd5f3fb96d7e0ea30243a8010b0c2992fde30d9d13ac5d8e8b327bef5c632908e62";
		jdbc.sql("UPDATE users SET password_hash = :h WHERE email = :e").param("h", nodeHash).param("e", shop.email())
				.update();

		postJson("/api/v1/auth/login", null, Map.of("identifier", shop.email(), "password", "legacy-pass-123"))
				.andExpect(status().isOk());
		String stored = jdbc.sql("SELECT password_hash FROM users WHERE email = :e").param("e", shop.email())
				.query(String.class).single();
		assertThat(stored).startsWith("{bcrypt}");
		postJson("/api/v1/auth/login", null, Map.of("identifier", shop.email(), "password", "legacy-pass-123"))
				.andExpect(status().isOk());
	}
}
