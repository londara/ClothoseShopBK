package com.aurastudio.backend;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import com.aurastudio.backend.common.Messages;
import com.aurastudio.backend.support.IntegrationTest;

/** Sign-up → pending → approve → suspend → delete (docs/API_AND_DATABASE.md §7, §9). */
class ShopLifecycleApiTest extends IntegrationTest {

	@Test
	void signupValidatesAndSignsTheOwnerIn() throws Exception {
		var owner = Map.of("name", "សុភា", "email", "x" + uniq() + "@test.com", "password", "sophea1234",
				"confirmPassword", "sophea1234");
		postJson("/api/v1/shops", null, Map.of("shopName", "ហាង", "slug", "admin", "telegram", "my_shop", "shopType",
				"couture", "owner", owner))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.message").value(Messages.SLUG_RESERVED));
		postJson("/api/v1/shops", null, Map.of("shopName", "ហាង", "slug", "Bad Slug", "telegram", "my_shop", "shopType",
				"couture", "owner", owner))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.field").value("slug"));
		postJson("/api/v1/shops", null, Map.of("shopName", "ហាង", "slug", "aura-studio", "telegram", "my_shop",
				"shopType", "couture", "owner", owner))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error.message").value(Messages.SLUG_TAKEN));

		TestShop shop = signup("jewelry");
		getJson("/api/v1/admin/categories", shop.token())
				.andExpect(jsonPath("$.data[*].code", Matchers.hasItems("rings", "necklaces", "best-sellers")));
	}

	@Test
	void pendingShopIsHiddenUntilApproved() throws Exception {
		TestShop shop = signup("couture");
		// owner can prepare products while pending
		createProduct(shop.token(), product("first", List.of("women"), List.of("M")));

		getJson("/api/v1/shops/" + shop.slug(), null)
				.andExpect(jsonPath("$.data.status").value("pending"))
				.andExpect(jsonPath("$.data.settings").doesNotExist());
		getJson("/api/v1/shops/" + shop.slug() + "/products", null).andExpect(status().isNotFound());
		getJson("/api/v1/shops", null).andExpect(jsonPath("$.data[*].slug", Matchers.not(Matchers.hasItem(shop.slug()))));

		String admin = adminToken();
		getJson("/api/v1/platform/shops", admin)
				.andExpect(jsonPath("$.data[0].status").value("pending"))
				.andExpect(jsonPath("$.meta.pending").value(Matchers.greaterThanOrEqualTo(1)));
		patchJson("/api/v1/platform/shops/" + shop.shopId(), admin, Map.of("status", "active"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value(Messages.SHOP_APPROVED))
				.andExpect(jsonPath("$.data.approvedAt").isNotEmpty());

		getJson("/api/v1/shops", null).andExpect(jsonPath("$.data[*].slug", Matchers.hasItem(shop.slug())));
		getJson("/api/v1/shops/" + shop.slug() + "/products", null).andExpect(jsonPath("$.data[0].name").value("first"));
		getJson("/api/v1/featured", null).andExpect(jsonPath("$.data[*].shop.slug", Matchers.hasItem(shop.slug())));
	}

	@Test
	void suspendingAShopLocksItsAccounts() throws Exception {
		TestShop shop = activeShop("couture");
		String refresh = loginData(shop.email(), shop.password()).get("refreshToken").asString();

		patchJson("/api/v1/platform/shops/" + shop.shopId(), adminToken(), Map.of("status", "suspended"))
				.andExpect(status().isOk());

		getJson("/api/v1/admin/products", shop.token())
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("SHOP_SUSPENDED"));
		postJson("/api/v1/auth/refresh", null, Map.of("refreshToken", refresh)).andExpect(status().isUnauthorized());
		postJson("/api/v1/auth/login", null, Map.of("identifier", shop.email(), "password", shop.password()))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("SHOP_SUSPENDED"));
		getJson("/api/v1/shops/" + shop.slug(), null).andExpect(jsonPath("$.data.status").value("suspended"));
	}

	@Test
	void platformAdminCannotSuspendOrDeleteOwnShopButCanDeleteOthers() throws Exception {
		String admin = adminToken();
		String ownShop = body(getJson("/api/v1/auth/me", admin)).get("data").get("shop").get("id").asString();
		patchJson("/api/v1/platform/shops/" + ownShop, admin, Map.of("status", "suspended"))
				.andExpect(status().isForbidden());
		deleteJson("/api/v1/platform/shops/" + ownShop, admin).andExpect(status().isForbidden());

		TestShop shop = activeShop("leather");
		deleteJson("/api/v1/platform/shops/" + shop.shopId(), admin).andExpect(status().isNoContent());
		getJson("/api/v1/shops/" + shop.slug(), null).andExpect(status().isNotFound());
		postJson("/api/v1/auth/login", null, Map.of("identifier", shop.email(), "password", shop.password()))
				.andExpect(status().isUnauthorized());
	}
}
