package com.aurastudio.backend.support;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Cache behaviour shared by every cache backend (docs/features/19-caching.md):
 * reads are served from the cache — a change made directly in the database is NOT
 * seen — while every change made through the API is seen immediately.
 * Subclasses pick the backend (Caffeine / Redis).
 */
public abstract class CacheBehaviourTest extends IntegrationTest {

	@Autowired
	protected JdbcClient jdbc;

	@Test
	void storefrontProductsComeFromTheCacheUntilChangedThroughTheApi() throws Exception {
		TestShop shop = activeShop("couture");
		var created = createProduct(shop.token(), product("Cotton shirt", List.of("women"), List.of("M")));
		String list = "/api/v1/shops/" + shop.slug() + "/products";
		String detail = list + "/" + created.get("slug").asString();

		getJson(list, null).andExpect(jsonPath("$.data[0].name").value("Cotton shirt"));
		getJson(detail, null).andExpect(jsonPath("$.data.name").value("Cotton shirt"));

		// Bypass the API: the cache doesn't know, so readers still get the cached copy (no DB query).
		jdbc.sql("UPDATE products SET name = 'changed in the database' WHERE id = :id")
				.param("id", UUID.fromString(created.get("id").asString())).update();
		getJson(list, null).andExpect(jsonPath("$.data[0].name").value("Cotton shirt"));
		getJson(detail, null).andExpect(jsonPath("$.data.name").value("Cotton shirt"));

		// Through the API: evicted, the next read is fresh.
		putJson("/api/v1/admin/products/" + created.get("id").asString(), shop.token(),
				product("Linen shirt", List.of("women"), List.of("M", "L"))).andExpect(status().isOk());
		getJson(list, null)
				.andExpect(jsonPath("$.data[0].name").value("Linen shirt"))
				.andExpect(jsonPath("$.data[0].sizes.length()").value(2));
	}

	@Test
	void shopPageComesFromTheCacheUntilSettingsChangeThroughTheApi() throws Exception {
		TestShop shop = activeShop("couture");
		String url = "/api/v1/shops/" + shop.slug();
		getJson(url, null).andExpect(jsonPath("$.data.status").value("active"));

		jdbc.sql("UPDATE shop_settings SET hero_title = 'changed in the database' WHERE shop_id = :id")
				.param("id", UUID.fromString(shop.shopId())).update();
		String cachedTitle = body(getJson(url, null)).get("data").get("settings").get("heroTitle").asString();
		org.assertj.core.api.Assertions.assertThat(cachedTitle).isNotEqualTo("changed in the database");

		var settings = new HashMap<String, Object>(Map.of("shopName", "ហាងថ្មី", "telegram", "new_shop_tg",
				"heroTitle", "ចំណងជើងថ្មី", "heroImage", "https://example.com/hero.jpg", "backgroundColor", "#f5f0ea"));
		putJson("/api/v1/admin/settings", shop.token(), settings).andExpect(status().isOk());
		getJson(url, null)
				.andExpect(jsonPath("$.data.name").value("ហាងថ្មី"))
				.andExpect(jsonPath("$.data.settings.heroTitle").value("ចំណងជើងថ្មី"));
		getJson("/api/v1/shops", null).andExpect(jsonPath("$.data[?(@.slug == '" + shop.slug() + "')].name")
				.value("ហាងថ្មី"));
	}

	@Test
	void accountChecksAreCachedButApiChangesApplyAtOnce() throws Exception {
		TestShop shop = activeShop("couture");
		String email = "staff" + uniq() + "@test.com";
		String staffId = body(postJson("/api/v1/admin/staff", shop.token(), Map.of("name", "S", "email", email,
				"password", "staffpass1", "confirmPassword", "staffpass1"))).get("data").get("id").asString();
		String staff = login(email, "staffpass1");
		getJson("/api/v1/admin/products", staff).andExpect(status().isOk());

		// Directly in the database: the cached account check still lets the request through.
		jdbc.sql("UPDATE users SET status = 'disabled' WHERE id = :id").param("id", UUID.fromString(staffId))
				.update();
		getJson("/api/v1/admin/products", staff).andExpect(status().isOk());

		// Through the API: the entry is evicted, blocked on the very next request.
		patchJson("/api/v1/admin/staff/" + staffId, shop.token(), Map.of("status", "disabled"))
				.andExpect(status().isOk());
		getJson("/api/v1/admin/products", staff)
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCOUNT_DISABLED"));
	}

	@Test
	void inMemorySearchAndPagingMatchTheApiRules() throws Exception {
		TestShop shop = activeShop("couture");
		var withBadge = new HashMap<>(product("Silk dress", List.of("women"), List.of("S")));
		withBadge.put("badge", "Organic COTTON");
		createProduct(shop.token(), withBadge);
		createProduct(shop.token(), product("Wool coat", List.of("men"), List.of("L")));
		createProduct(shop.token(), product("Cotton tee", List.of("men"), List.of("M")));
		String url = "/api/v1/shops/" + shop.slug() + "/products";

		// case-insensitive, in name or badge
		getJson(url + "?q=cotton", null).andExpect(jsonPath("$.meta.total").value(2));
		getJson(url + "?category=men&pageSize=1&page=2", null)
				.andExpect(jsonPath("$.meta.total").value(2))
				.andExpect(jsonPath("$.meta.pageCount").value(2))
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].name").value("Wool coat"));
		getJson(url + "?category=accessories", null)
				.andExpect(jsonPath("$.meta.total").value(0))
				.andExpect(jsonPath("$.meta.categoryCounts.accessories").value(0))
				.andExpect(jsonPath("$.meta.categoryCounts.all").value(3));
	}
}
