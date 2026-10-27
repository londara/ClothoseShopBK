package com.aurastudio.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.aurastudio.backend.support.IntegrationTest;

/**
 * Hostile input from a browser or script (docs/features/20-input-security.md): every case
 * must end in a clean 4xx with a Khmer message, never a 500, and nothing harmful is stored.
 */
class InputSecurityApiTest extends IntegrationTest {

	private static String repeat(String s, int n) {
		return String.join("", Collections.nCopies(n, s));
	}

	private Map<String, Object> productWith(String key, Object value) {
		var body = new HashMap<>(product("ok", List.of("women"), List.of("M")));
		body.put(key, value);
		return body;
	}

	@Test
	void productTextFieldsAreLengthLimited() throws Exception {
		TestShop shop = activeShop("couture");
		for (var bad : List.of(Map.entry("name", repeat("ក", 121)), Map.entry("description", repeat("a", 5001)),
				Map.entry("badge", repeat("b", 41)), Map.entry("atelier", repeat("c", 81)))) {
			postJson("/api/v1/admin/products", shop.token(), productWith(bad.getKey(), bad.getValue()))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.field").value(bad.getKey()));
		}
	}

	@Test
	void productNumbersAndListsAreBounded() throws Exception {
		TestShop shop = activeShop("couture");
		postJson("/api/v1/admin/products", shop.token(), productWith("price", 1e15))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.field").value("price"));
		postJson("/api/v1/admin/products", shop.token(), productWith("price", 10.123))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.field").value("price"));
		postJson("/api/v1/admin/products", shop.token(), productWith("stock", 10_000_001))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.field").value("stock"));
		postJson("/api/v1/admin/products", shop.token(), productWith("categories", Collections.nCopies(500, "women")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.field").value("categories"));
		postJson("/api/v1/admin/products", shop.token(), productWith("images", Collections.nCopies(30, IMG)))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.field").value("images"));
		postJson("/api/v1/admin/products", shop.token(), productWith("sizes", Collections.nCopies(500, "M")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.field").value("sizes"));
	}

	@Test
	void dangerousUrlsAndControlCharactersAreRejected() throws Exception {
		TestShop shop = activeShop("couture");
		for (String url : List.of("javascript:alert(1)", "data:image/png;base64,AAAA", "http://example.com/a.jpg",
				"https://example.com/" + repeat("a", 2100), "https://x.com/a\".jpg onerror=alert(1)",
				"/uploads/../../etc/passwd")) {
			postJson("/api/v1/admin/products", shop.token(), productWith("images", List.of(url)))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.field").value("images"));
		}
		postJson("/api/v1/admin/products", shop.token(), productWith("name", "bad\u0000name"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.field").value("name"));
		postJson("/api/v1/admin/products", shop.token(), productWith("description", "a\u0007b"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.field").value("description"));
		// Newlines and tabs in a description are normal text.
		postJson("/api/v1/admin/products", shop.token(), productWith("description", "line 1\nline 2\tok"))
				.andExpect(status().isCreated());
	}

	@Test
	void settingsAndProfileFieldsAreLengthLimited() throws Exception {
		TestShop shop = activeShop("couture");
		var settings = new HashMap<String, Object>(Map.of("shopName", "ok", "telegram", "my_shop_tg", "heroImage",
				IMG, "backgroundColor", "#ffffff"));
		for (var bad : List.of(Map.entry("shopName", repeat("a", 81)), Map.entry("announcement", repeat("a", 201)),
				Map.entry("heroTitle", repeat("a", 121)), Map.entry("heroSubtitle", repeat("a", 301)),
				Map.entry("aboutTitle", repeat("a", 81)), Map.entry("aboutMe", repeat("a", 5001)))) {
			var body = new HashMap<>(settings);
			body.put(bad.getKey(), bad.getValue());
			putJson("/api/v1/admin/settings", shop.token(), body)
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.field").value(bad.getKey()));
		}
		putJson("/api/v1/admin/profile", shop.token(), Map.of("name", repeat("a", 81), "email", shop.email()))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.field").value("name"));
		putJson("/api/v1/admin/profile", shop.token(), Map.of("name", "ok", "email", repeat("a", 250) + "@x.com"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.field").value("email"));
	}

	@Test
	void passwordsAndTokensHaveSafeLimits() throws Exception {
		TestShop shop = activeShop("couture");
		// bcrypt only uses 72 bytes: longer passwords are refused instead of silently cut (or a 500).
		String longPw = repeat("ក", 30); // 90 bytes in UTF-8
		postJson("/api/v1/admin/staff", shop.token(), Map.of("name", "S", "email", "s" + uniq() + "@t.com",
				"password", longPw, "confirmPassword", longPw))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.field").value("password"));
		// Huge login input: a normal "wrong credentials" answer, not a 500.
		postJson("/api/v1/auth/login", null, Map.of("identifier", repeat("9", 5000), "password", repeat("p", 10000)))
				.andExpect(status().isUnauthorized());
		postJson("/api/v1/auth/refresh", null, Map.of("refreshToken", repeat("x", 100000)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void pagingAndPathParametersCannotBreakQueries() throws Exception {
		TestShop shop = activeShop("couture");
		createProduct(shop.token(), product("p", List.of("women"), List.of("M")));
		getJson("/api/v1/shops/" + shop.slug() + "/products?page=2147483647&pageSize=2147483647", null)
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
		getJson("/api/v1/admin/products?page=2147483647&pageSize=100", shop.token())
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
		getJson("/api/v1/admin/products?page=-5&pageSize=-1", shop.token()).andExpect(status().isOk());
		getJson("/api/v1/shops/" + shop.slug() + "/products?q=" + repeat("a", 500), null)
				.andExpect(status().isBadRequest());
		getJson("/api/v1/shops/" + repeat("a", 300), null).andExpect(status().isNotFound());
		getJson("/api/v1/shops/Bad%20Slug'--", null).andExpect(status().is4xxClientError());
		getJson("/api/v1/admin/products/not-a-uuid", shop.token()).andExpect(status().isBadRequest());
	}

	@Test
	void oversizedJsonBodiesAreRefused() throws Exception {
		TestShop shop = activeShop("couture");
		String big = "{\"name\":\"" + repeat("a", 2 * 1024 * 1024) + "\"}";
		mvc.perform(post("/api/v1/admin/products").header("Authorization", "Bearer " + shop.token())
				.contentType(MediaType.APPLICATION_JSON).content(big))
				.andExpect(status().isPayloadTooLarge())
				.andExpect(jsonPath("$.error.code").value("PAYLOAD_TOO_LARGE"));
	}

	@Test
	void uploadsMustReallyBeImagesAndAreServedSafely() throws Exception {
		TestShop shop = activeShop("couture");
		var fake = new MockMultipartFile("file", "x.png", "image/png",
				"<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));
		mvc.perform(multipart("/api/v1/uploads").file(fake).header("Authorization", "Bearer " + shop.token()))
				.andExpect(status().isUnsupportedMediaType());

		byte[] png = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R' };
		var real = new MockMultipartFile("file", "x.png", "image/png", png);
		String url = body(mvc.perform(multipart("/api/v1/uploads").file(real)
				.header("Authorization", "Bearer " + shop.token())).andExpect(status().isCreated()))
				.get("data").get("url").asString();
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string("Content-Security-Policy",
						org.hamcrest.Matchers.containsString("sandbox")));
	}

}
