package com.aurastudio.backend;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.aurastudio.backend.common.Messages;
import com.aurastudio.backend.support.IntegrationTest;

/** Staff, settings and profile (docs/API_AND_DATABASE.md §8.9–8.15). */
class StaffAndSettingsApiTest extends IntegrationTest {

	@Test
	void ownerCreatesStaffWhoLogsInWithPhone() throws Exception {
		TestShop shop = activeShop("couture");
		String phone = phone(uniq());
		postJson("/api/v1/admin/staff", shop.token(), Map.of("name", "វណ្ណា", "phone", "+855 " + phone.substring(1),
				"password", "staffpass1", "confirmPassword", "staffpass1"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.phone").value(phone))
				.andExpect(jsonPath("$.data.role").value("staff"))
				.andExpect(jsonPath("$.message").value(Messages.staffCreated("វណ្ណា")));

		// duplicate phone across the whole platform
		postJson("/api/v1/admin/staff", shop.token(), Map.of("name", "x", "phone", phone, "password", "staffpass1",
				"confirmPassword", "staffpass1"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error.message").value(Messages.PHONE_TAKEN));

		String staff = login(phone, "staffpass1");
		// staff: products yes, staff page / settings no
		postJson("/api/v1/admin/products", staff, product("by staff", List.of("women"), List.of("M")))
				.andExpect(status().isCreated());
		getJson("/api/v1/admin/staff", staff).andExpect(status().isForbidden());
		getJson("/api/v1/admin/settings", staff).andExpect(status().isForbidden());
	}

	@Test
	void disablingStaffBlocksThemAtOnce() throws Exception {
		TestShop shop = activeShop("couture");
		String email = "staff" + uniq() + "@test.com";
		String staffId = body(postJson("/api/v1/admin/staff", shop.token(), Map.of("name", "S", "email", email,
				"password", "staffpass1", "confirmPassword", "staffpass1"))).get("data").get("id").asString();
		String staffToken = login(email, "staffpass1");

		patchJson("/api/v1/admin/staff/" + staffId, shop.token(), Map.of("status", "disabled"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.message").value(Messages.STAFF_DISABLED));
		getJson("/api/v1/admin/products", staffToken)
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCOUNT_DISABLED"));

		String ownerId = body(getJson("/api/v1/auth/me", shop.token())).get("data").get("user").get("id").asString();
		patchJson("/api/v1/admin/staff/" + ownerId, shop.token(), Map.of("status", "disabled"))
				.andExpect(status().isForbidden());
		deleteJson("/api/v1/admin/staff/" + staffId, shop.token()).andExpect(status().isNoContent());
	}

	@Test
	void settingsApplyDefaultsAndShowOnTheStorefront() throws Exception {
		TestShop shop = activeShop("couture");
		var settings = new HashMap<String, Object>(Map.of("shopName", "ហាងថ្មី", "telegram", "@new_shop_tg",
				"announcement", "ដឹកជញ្ជូនឥតគិតថ្លៃ!", "heroTitle", "", "heroSubtitle", "ម៉ូដប្រណីត", "heroImage",
				"https://example.com/hero.jpg", "backgroundColor", "#F5F0EA", "aboutTitle", "", "aboutMe", "អំពីហាង"));
		putJson("/api/v1/admin/settings", shop.token(), settings)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.telegram").value("new_shop_tg"))
				.andExpect(jsonPath("$.data.heroTitle").value("ហាងថ្មី"))
				.andExpect(jsonPath("$.data.aboutTitle").value("អំពីយើង"))
				.andExpect(jsonPath("$.data.backgroundColor").value("#f5f0ea"));
		getJson("/api/v1/shops/" + shop.slug(), null).andExpect(jsonPath("$.data.settings.shopName").value("ហាងថ្មី"));

		settings.put("backgroundColor", "red");
		putJson("/api/v1/admin/settings", shop.token(), settings)
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.field").value("backgroundColor"));
	}

	@Test
	void profileChangesTheLoginIdentifier() throws Exception {
		TestShop shop = activeShop("couture");
		String newEmail = "renamed" + uniq() + "@test.com";
		putJson("/api/v1/admin/profile", shop.token(), Map.of("name", "សុខា", "title", "ស្ថាបនិក", "email", newEmail,
				"phone", ""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.email").value(newEmail))
				.andExpect(jsonPath("$.data.phone").doesNotExist());
		postJson("/api/v1/auth/login", null, Map.of("identifier", newEmail, "password", shop.password()))
				.andExpect(status().isOk());

		putJson("/api/v1/admin/profile", shop.token(), Map.of("name", "x", "email", "", "phone", ""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.message").value(Messages.PROFILE_EMAIL_OR_PHONE_REQUIRED));
		putJson("/api/v1/admin/profile", shop.token(), Map.of("name", "x", "email", ADMIN_EMAIL))
				.andExpect(status().isConflict());
	}
}
