package com.aurastudio.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.aurastudio.backend.support.IntegrationTest;

import tools.jackson.databind.JsonNode;

/**
 * docs/API_AND_DATABASE.md §4.1 "Tests to keep": logged in as shop A, every
 * attempt to read or change shop B's data must fail.
 */
class TenancyApiTest extends IntegrationTest {

	TestShop a;
	TestShop b;
	JsonNode productOfA;
	JsonNode productOfB;
	String staffOfB;

	@BeforeEach
	void twoShops() throws Exception {
		a = activeShop("couture");
		b = activeShop("couture");
		productOfA = createProduct(a.token(), product("A product", List.of("women"), List.of("M")));
		productOfB = createProduct(b.token(), product("B product", List.of("men"), List.of("L")));
		var staff = body(postJson("/api/v1/admin/staff", b.token(), Map.of("name", "B staff", "phone",
				phone(uniq()), "password", "staffpass1", "confirmPassword", "staffpass1")));
		staffOfB = staff.get("data").get("id").asString();
	}

	@Test
	void productsOfAnotherShopAreNotFound() throws Exception {
		String url = "/api/v1/admin/products/" + productOfB.get("id").asString();
		getJson(url, a.token()).andExpect(status().isNotFound());
		putJson(url, a.token(), product("hacked", List.of("women"), List.of("M"))).andExpect(status().isNotFound());
		deleteJson(url, a.token()).andExpect(status().isNotFound());

		// B's product is unchanged
		getJson(url, b.token()).andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("B product"));
	}

	@Test
	void listsAndTotalsOnlyShowMyShop() throws Exception {
		var list = body(getJson("/api/v1/admin/products", a.token())).get("data");
		assertThat(list).allSatisfy(p -> assertThat(p.get("name").asString()).isNotEqualTo("B product"));
		assertThat(list.size()).isEqualTo(1);

		getJson("/api/v1/admin/dashboard", a.token()).andExpect(jsonPath("$.data.totalProducts").value(1));
		var staff = body(getJson("/api/v1/admin/staff", a.token())).get("data");
		assertThat(staff.size()).isEqualTo(1); // only A's owner
	}

	@Test
	void categoriesAndUploadsOfAnotherShopAreRejected() throws Exception {
		String codeOnlyB = body(postJson("/api/v1/admin/categories", b.token(), Map.of("label", "ប្រភេទ B")))
				.get("data").get("code").asString();
		postJson("/api/v1/admin/products", a.token(), product("x", List.of(codeOnlyB), List.of("M")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.field").value("categories"));

		var file = new MockMultipartFile("file", "b.png", "image/png", new byte[] { (byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47, 0x0D, 0x0A, 0x1A, 0x0A });
		var upload = mvc.perform(multipart("/api/v1/uploads").file(file).header("Authorization", "Bearer " + b.token()))
				.andExpect(status().isCreated()).andReturn();
		String urlOfB = json.readTree(upload.getResponse().getContentAsString()).get("data").get("url").asString();

		var withForeignImage = new HashMap<>(product("x", List.of("women"), List.of("M")));
		withForeignImage.put("images", List.of(urlOfB));
		postJson("/api/v1/admin/products", a.token(), withForeignImage)
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.field").value("images"));

		// B itself may use its upload, and the file is served publicly
		var withOwnImage = new HashMap<>(product("own image", List.of("men"), List.of("M")));
		withOwnImage.put("images", List.of(urlOfB));
		postJson("/api/v1/admin/products", b.token(), withOwnImage).andExpect(status().isCreated());
		getJson(urlOfB, null).andExpect(status().isOk());
	}

	@Test
	void staffOfAnotherShopIsNotFound() throws Exception {
		patchJson("/api/v1/admin/staff/" + staffOfB, a.token(), Map.of("status", "disabled"))
				.andExpect(status().isNotFound());
		deleteJson("/api/v1/admin/staff/" + staffOfB, a.token()).andExpect(status().isNotFound());
	}

	@Test
	void aShopIdInTheBodyIsIgnored() throws Exception {
		var body = new HashMap<>(product("with shopId", List.of("women"), List.of("M")));
		body.put("shopId", b.shopId());
		var created = createProduct(a.token(), body);
		// it went to A, not B
		getJson("/api/v1/admin/products/" + created.get("id").asString(), a.token()).andExpect(status().isOk());
		getJson("/api/v1/admin/products/" + created.get("id").asString(), b.token()).andExpect(status().isNotFound());
	}

	@Test
	void platformEndpointsNeedThePlatformAdmin() throws Exception {
		getJson("/api/v1/platform/shops", a.token()).andExpect(status().isForbidden());
		patchJson("/api/v1/platform/shops/" + b.shopId(), a.token(), Map.of("status", "suspended"))
				.andExpect(status().isForbidden());
	}

	@Test
	void publicProductUrlsAreScopedByShop() throws Exception {
		getJson("/api/v1/shops/" + b.slug() + "/products/" + productOfA.get("slug").asString(), null)
				.andExpect(status().isNotFound());
		getJson("/api/v1/shops/" + a.slug() + "/products/" + productOfA.get("slug").asString(), null)
				.andExpect(status().isOk());
	}
}
