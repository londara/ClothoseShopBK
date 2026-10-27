package com.aurastudio.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;

import com.aurastudio.backend.support.IntegrationTest;

/** Per-IP limits on the public endpoints (docs/features/20-input-security.md). */
@TestPropertySource(properties = { "aura.rate-limit.signups-per-hour=3", "aura.rate-limit.logins-per-minute=5" })
class RateLimitApiTest extends IntegrationTest {


	private MockHttpServletResponse postFrom(String ip, String url, Object body) throws Exception {
		return mvc.perform(post(url).with(r -> {
			r.setRemoteAddr(ip);
			return r;
		}).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andReturn().getResponse();
	}

	private Map<String, Object> signupBody() {
		var owner = Map.of("name", "x", "email", "rl" + uniq() + "@t.com", "password", "password1",
				"confirmPassword", "password1");
		return Map.of("shopName", "x", "slug", "rl-" + uniq(), "telegram", "rl_shop_tg", "shopType", "couture",
				"owner", owner);
	}

	@Test
	void signupIsLimitedPerIpWithRetryAfter() throws Exception {
		for (int i = 0; i < 3; i++) {
			assertThat(postFrom("203.0.113.7", "/api/v1/shops", signupBody()).getStatus()).isEqualTo(201);
		}
		var blocked = postFrom("203.0.113.7", "/api/v1/shops", signupBody());
		assertThat(blocked.getStatus()).isEqualTo(429);
		assertThat(blocked.getHeader("Retry-After")).isEqualTo("3600");
		assertThat(blocked.getContentAsString()).contains("RATE_LIMITED");
		// Another client is not affected.
		assertThat(postFrom("203.0.113.8", "/api/v1/shops", signupBody()).getStatus()).isEqualTo(201);
	}

	@Test
	void loginIsLimitedPerIpAcrossAccounts() throws Exception {
		// Password spraying: a different account each time, so the per-account lockout never triggers.
		int last = 0;
		for (int i = 0; i < 6; i++) {
			last = postFrom("198.51.100.9", "/api/v1/auth/login",
					Map.of("identifier", "spray" + uniq() + "@t.com", "password", "Password1")).getStatus();
		}
		assertThat(last).isEqualTo(429);
		assertThat(postFrom("198.51.100.10", "/api/v1/auth/login",
				Map.of("identifier", ADMIN_EMAIL, "password", ADMIN_PASSWORD)).getStatus()).isEqualTo(200);
	}
}
