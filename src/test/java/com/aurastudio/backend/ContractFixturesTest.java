package com.aurastudio.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.aurastudio.backend.support.IntegrationTest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Frontend ↔ backend contract, backend side (docs/features/21-contract-tests.md).
 * <p>
 * Calls every endpoint the Next.js app uses and
 * <ol>
 * <li>asserts that each field the frontend reads is present (if a field is renamed or
 * removed here, this test fails), and</li>
 * <li>records the real responses into {@code ../aura-studio/src/test/contract/*.json}.
 * The frontend's unit tests ({@code npm test}) feed exactly these responses into its
 * mappers and API client — so both sides are tested against the same data, without
 * running servers.</li>
 * </ol>
 */
class ContractFixturesTest extends IntegrationTest {

	private static final Path FIXTURES = Path.of("..", "aura-studio", "src", "test", "contract");

	/** Fails when one of {@code fields} is missing on {@code node}. */
	private static void requireFields(String what, JsonNode node, String... fields) {
		for (String field : fields) {
			assertThat(node.has(field)).as(what + " must have \"" + field + "\" (read by the frontend)").isTrue();
		}
	}

	/** Saves the whole response body (envelope included); tokens are replaced by placeholders. */
	private JsonNode record(String name, ResultActions result) throws Exception {
		JsonNode body = body(result);
		if (body.has("data") && body.get("data").isObject() && body.get("data").has("accessToken")) {
			((ObjectNode) body.get("data")).put("accessToken", "<access-token>");
			((ObjectNode) body.get("data")).put("refreshToken", "<refresh-token>");
		}
		if (Files.isDirectory(FIXTURES.getParent().getParent())) {
			Files.createDirectories(FIXTURES);
			Files.writeString(FIXTURES.resolve(name + ".json"),
					json.writerWithDefaultPrettyPrinter().writeValueAsString(body) + "\n");
		}
		return body;
	}

	@Test
	void recordAndCheckEveryResponseTheFrontendReads() throws Exception {
		// ── a shop with data ─────────────────────────────────────────────────
		TestShop shop = activeShop("couture");
		var full = new HashMap<String, Object>(product("អាវធំកាស្មៀរ", List.of("women", "best-sellers"),
				List.of("S", "M")));
		full.put("price", 890.5);
		full.put("badge", "កាស្មៀរសុទ្ធ");
		full.put("badgeTone", "gold");
		full.put("atelier", "អាតេលីយេ ប៊ីយ៉ែឡា");
		full.put("colors", List.of(Map.of("name", "ខ្មៅ", "hex", "#111111")));
		var main = createProduct(shop.token(), full);
		createProduct(shop.token(), product("Plain tee", List.of("women"), List.of("M"))); // no badge, no colours
		postJson("/api/v1/admin/staff", shop.token(), Map.of("name", "វណ្ណា", "phone", phone(uniq()), "password",
				"staffpass1", "confirmPassword", "staffpass1")).andExpect(status().isCreated());
		putJson("/api/v1/admin/profile", shop.token(), Map.of("name", "សុភា", "title", "ម្ចាស់ហាង", "email",
				shop.email(), "phone", "")).andExpect(status().isOk());

		// ── auth ─────────────────────────────────────────────────────────────
		var login = record("login", postJson("/api/v1/auth/login", null,
				Map.of("identifier", shop.email(), "password", shop.password())).andExpect(status().isOk()));
		requireFields("login.data", login.get("data"), "tokenType", "accessToken", "expiresIn", "refreshToken",
				"refreshExpiresIn", "user", "shop");

		var me = record("me", getJson("/api/v1/auth/me", shop.token()).andExpect(status().isOk()));
		requireFields("me.data", me.get("data"), "user", "shop", "counts");
		requireFields("me.data.user", me.get("data").get("user"), "id", "name", "title", "email", "phone",
				"avatar", "role", "isPlatformAdmin");
		requireFields("me.data.shop", me.get("data").get("shop"), "id", "slug", "name", "status");
		requireFields("me.data.counts", me.get("data").get("counts"), "pendingStaff", "pendingShops");

		// ── storefront (public) ──────────────────────────────────────────────
		var types = record("shop-types", getJson("/api/v1/shop-types", null));
		requireFields("shop-types[0]", types.get("data").get(0), "code", "label", "sizeKind");

		var shops = record("shops", getJson("/api/v1/shops", null));
		requireFields("shops[0]", shops.get("data").get(0), "slug");

		var detail = record("shop", getJson("/api/v1/shops/" + shop.slug(), null).andExpect(status().isOk()));
		requireFields("shop.data", detail.get("data"), "slug", "name", "status", "type", "settings", "categories",
				"owner");
		requireFields("shop.data.type", detail.get("data").get("type"), "code", "label", "sizeKind");
		requireFields("shop.data.settings", detail.get("data").get("settings"), "shopName", "telegram",
				"announcement", "heroTitle", "heroSubtitle", "heroImage", "backgroundColor", "aboutTitle", "aboutMe");
		requireFields("shop.data.categories[0]", detail.get("data").get("categories").get(0), "code", "label");
		requireFields("shop.data.owner", detail.get("data").get("owner"), "name", "title", "email", "phone",
				"avatar");

		TestShop pending = signup("jewelry");
		var pendingShop = record("shop-pending", getJson("/api/v1/shops/" + pending.slug(), null));
		requireFields("shop-pending.data", pendingShop.get("data"), "slug", "name", "status");

		var products = record("storefront-products",
				getJson("/api/v1/shops/" + shop.slug() + "/products?page=1&pageSize=60", null));
		// "badge" is optional: ProductDto is @JsonInclude(NON_NULL), so a product without a badge has no
		// "badge" key at all (the frontend's ApiProduct.badge is optional and maps it to undefined).
		for (JsonNode p : products.get("data")) {
			requireFields("storefront product", p, "id", "slug", "name", "description", "price", "stock", "badgeTone",
					"atelier", "categories", "images", "sizes", "colors");
		}
		requireFields("meta", products.get("meta"), "page", "pageSize", "total", "pageCount", "categoryCounts");

		record("storefront-product",
				getJson("/api/v1/shops/" + shop.slug() + "/products/" + main.get("slug").asString(), null));

		// ── admin (owner) ────────────────────────────────────────────────────
		var adminProducts = record("admin-products",
				getJson("/api/v1/admin/products?page=1&pageSize=100", shop.token()));
		requireFields("admin product row", adminProducts.get("data").get(0), "id", "slug", "name", "price", "stock",
				"badge", "categories", "image");
		requireFields("meta", adminProducts.get("meta"), "pageCount");

		var adminProduct = record("admin-product",
				getJson("/api/v1/admin/products/" + main.get("id").asString(), shop.token()));
		requireFields("admin-product", adminProduct, "data", "options");
		requireFields("admin-product.options", adminProduct.get("options"), "categories", "typeLabel", "sizeKind",
				"defaultSizes");

		record("product-options", getJson("/api/v1/admin/products/options", shop.token()));
		record("categories", getJson("/api/v1/admin/categories", shop.token()));

		var dashboard = record("dashboard", getJson("/api/v1/admin/dashboard", shop.token()));
		requireFields("dashboard.data", dashboard.get("data"), "totalProducts", "totalStock", "stockValue",
				"lowStock", "recentProducts", "pendingStaff");

		var staff = record("staff", getJson("/api/v1/admin/staff", shop.token()));
		requireFields("staff row", staff.get("data").get(0), "id", "name", "title", "email", "phone", "avatar",
				"role", "status");

		var settings = record("settings", getJson("/api/v1/admin/settings", shop.token()));
		requireFields("settings.data", settings.get("data"), "shopName", "telegram", "heroImage",
				"backgroundColor");

		var platformShops = record("platform-shops", getJson("/api/v1/platform/shops", adminToken()));
		requireFields("platform shop row", platformShops.get("data").get(0), "id", "slug", "name", "status", "type",
				"productCount", "accountCount", "owner", "createdAt", "isMine");

		// ── errors (the frontend shows error.message) ────────────────────────
		var noPrice = new HashMap<>(product("x", List.of("women"), List.of("M")));
		noPrice.put("price", 0);
		var validation = record("error-validation",
				postJson("/api/v1/admin/products", shop.token(), noPrice).andExpect(status().isBadRequest()));
		requireFields("error", validation.get("error"), "code", "message", "field");

		var unauthorized = record("error-unauthorized",
				getJson("/api/v1/admin/products", "not-a-jwt").andExpect(status().isUnauthorized()));
		requireFields("error", unauthorized.get("error"), "code", "message");

		record("error-not-found", getJson("/api/v1/shops/no-such-shop", null).andExpect(status().isNotFound()));
	}
}
