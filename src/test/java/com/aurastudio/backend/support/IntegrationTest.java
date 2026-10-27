package com.aurastudio.backend.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base for API tests: one real PostgreSQL 18 (embedded, no Docker, no password)
 * for the whole test run, Flyway migrations, the platform admin created by the
 * bootstrap runner, and small HTTP helpers. Tests use unique slugs/emails, so
 * they can share the database.
 */
@SpringBootTest
@AutoConfigureMockMvc
// The suite signs up and logs in many times from one IP; RateLimitApiTest overrides these.
@org.springframework.test.context.TestPropertySource(properties = { "aura.rate-limit.signups-per-hour=100000",
		"aura.rate-limit.logins-per-minute=100000" })
public abstract class IntegrationTest {

	public static final String ADMIN_EMAIL = "admin@aura.test";
	public static final String ADMIN_PASSWORD = "Admin12345";

	private static final EmbeddedPostgres POSTGRES;
	private static final AtomicInteger SEQ = new AtomicInteger();

	static {
		try {
			POSTGRES = EmbeddedPostgres.builder().start();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) throws IOException {
		registry.add("spring.datasource.url",
				() -> "jdbc:postgresql://localhost:" + POSTGRES.getPort() + "/postgres?stringtype=unspecified");
		registry.add("spring.datasource.username", () -> "postgres");
		registry.add("spring.datasource.password", () -> "");
		registry.add("aura.bootstrap.admin-email", () -> ADMIN_EMAIL);
		registry.add("aura.bootstrap.admin-password", () -> ADMIN_PASSWORD);
		String uploads = Files.createTempDirectory("aura-uploads").toString();
		registry.add("aura.uploads.dir", () -> uploads);
	}

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected JsonMapper json;

	/** Unique suffix for slugs, emails and phones. */
	protected static int uniq() {
		return SEQ.incrementAndGet() * 7 + (int) (System.nanoTime() % 1000);
	}

	protected static String phone(int n) {
		return String.format("09%07d", Math.abs(n) % 10_000_000);
	}

	// ── HTTP helpers ──

	protected ResultActions call(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
		}
		return mvc.perform(request);
	}

	protected ResultActions getJson(String url, String token) throws Exception {
		return call(get(url), token, null);
	}

	protected ResultActions postJson(String url, String token, Object body) throws Exception {
		return call(post(url), token, body);
	}

	protected ResultActions putJson(String url, String token, Object body) throws Exception {
		return call(put(url), token, body);
	}

	protected ResultActions patchJson(String url, String token, Object body) throws Exception {
		return call(patch(url), token, body);
	}

	protected ResultActions deleteJson(String url, String token) throws Exception {
		return call(delete(url), token, null);
	}

	protected JsonNode body(ResultActions result) throws Exception {
		return json.readTree(result.andReturn().getResponse().getContentAsString());
	}

	// ── scenario helpers ──

	protected JsonNode loginData(String identifier, String password) throws Exception {
		return body(postJson("/api/v1/auth/login", null, Map.of("identifier", identifier, "password", password)))
				.get("data");
	}

	protected String login(String identifier, String password) throws Exception {
		return loginData(identifier, password).get("accessToken").asString();
	}

	protected String adminToken() throws Exception {
		return login(ADMIN_EMAIL, ADMIN_PASSWORD);
	}

	/** A signed-up shop: owner token, ids and the owner's login. */
	public record TestShop(String slug, String shopId, String token, String email, String phone, String password) {
	}

	protected TestShop signup(String type) throws Exception {
		int n = uniq();
		String slug = "shop-" + n;
		String email = "owner" + n + "@test.com";
		String phone = phone(n);
		var owner = Map.of("name", "ម្ចាស់ " + n, "email", email, "phone", phone, "password", "owner12345",
				"confirmPassword", "owner12345");
		var data = body(postJson("/api/v1/shops", null, Map.of("shopName", "ហាង " + n, "slug", slug, "telegram",
				"shop_" + n, "shopType", type, "owner", owner))).get("data");
		return new TestShop(slug, data.get("shop").get("id").asString(), data.get("accessToken").asString(), email,
				phone, "owner12345");
	}

	/** Signed-up AND approved by the platform admin. */
	protected TestShop activeShop(String type) throws Exception {
		TestShop shop = signup(type);
		patchJson("/api/v1/platform/shops/" + shop.shopId(), adminToken(), Map.of("status", "active"));
		return shop;
	}

	protected static final String IMG = "https://example.com/image.jpg";

	protected Map<String, Object> product(String name, List<String> categories, List<String> sizes) {
		return Map.of("name", name, "description", "ការពិពណ៌នា " + name, "price", 25, "stock", 10, "categories",
				categories, "images", List.of(IMG), "sizes", sizes, "colors", List.of());
	}

	/** Creates a product, returns its JSON (data). */
	protected JsonNode createProduct(String token, Map<String, Object> product) throws Exception {
		return body(postJson("/api/v1/admin/products", token, product)).get("data");
	}
}
