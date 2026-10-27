package com.aurastudio.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.aurastudio.backend.common.Messages;
import com.aurastudio.backend.support.IntegrationTest;

/** docs/API_AND_DATABASE.md §6.5–6.6, §8.2–8.8 — products, categories, storefront. */
class ProductApiTest extends IntegrationTest {

	@Test
	void createProductWithSizesAndColours() throws Exception {
		TestShop shop = activeShop("couture");
		var body = new HashMap<>(product("អាវយឺតកប្បាស", List.of("women"), List.of("L", "S", "M")));
		body.put("colors", List.of(Map.of("name", "ខ្មៅ", "hex", "#111111"), Map.of("name", "ក្រហមចាស់", "hex", "#7F1D1D")));
		body.put("badgeTone", "gold");
		postJson("/api/v1/admin/products", shop.token(), body)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.message").value(Messages.PRODUCT_CREATED))
				.andExpect(jsonPath("$.data.slug").value(org.hamcrest.Matchers.startsWith("product-")))
				.andExpect(jsonPath("$.data.sizes[0]").value("S")) // size-system order, not request order
				.andExpect(jsonPath("$.data.sizes[2]").value("L"))
				.andExpect(jsonPath("$.data.colors[1].hex").value("#7f1d1d"))
				.andExpect(jsonPath("$.data.badgeTone").value("gold"))
				.andExpect(jsonPath("$.data.categories[0]").value("women"));
	}

	@Test
	void validationMessagesAreKhmerWithTheField() throws Exception {
		TestShop shop = activeShop("couture");
		var noPrice = new HashMap<>(product("x", List.of("women"), List.of("M")));
		noPrice.put("price", 0);
		postJson("/api/v1/admin/products", shop.token(), noPrice).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.field").value("price"))
				.andExpect(jsonPath("$.error.message").value(Messages.PRICE_INVALID));

		postJson("/api/v1/admin/products", shop.token(), product("x", List.of("women"), List.of()))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.field").value("sizes"));

		var badColour = new HashMap<>(product("x", List.of("women"), List.of("M")));
		badColour.put("colors", List.of(Map.of("name", "ខ្មៅ", "hex", "black")));
		postJson("/api/v1/admin/products", shop.token(), badColour).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.message").value(Messages.COLOR_HEX_INVALID));

		var badImage = new HashMap<>(product("x", List.of("women"), List.of("M")));
		badImage.put("images", List.of("javascript:alert(1)"));
		postJson("/api/v1/admin/products", shop.token(), badImage).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.field").value("images"));
	}

	@Test
	void shopsWithoutASizeSystemIgnoreSizes() throws Exception {
		TestShop jewelry = activeShop("jewelry");
		postJson("/api/v1/admin/products", jewelry.token(), product("ចិញ្ចៀន", List.of("rings"), List.of("M")))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.sizes").isEmpty());
		// clothing categories don't exist in a jewelry shop
		postJson("/api/v1/admin/products", jewelry.token(), product("x", List.of("women"), List.of()))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.field").value("categories"));
	}

	@Test
	void newCategoryGoesBeforeBestSellersAndNamesAreUnique() throws Exception {
		TestShop shop = activeShop("couture");
		postJson("/api/v1/admin/categories", shop.token(), Map.of("label", "អាវយឺត"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.code").value(org.hamcrest.Matchers.startsWith("c-")))
				.andExpect(jsonPath("$.message").value(Messages.categoryAdded("អាវយឺត")));
		var list = body(getJson("/api/v1/admin/categories", shop.token())).get("data");
		assertThat(list.get(list.size() - 1).get("code").asString()).isEqualTo("best-sellers");
		assertThat(list.get(list.size() - 2).get("label").asString()).isEqualTo("អាវយឺត");

		postJson("/api/v1/admin/categories", shop.token(), Map.of("label", "នារី"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error.message").value(Messages.CATEGORY_TAKEN));
	}

	@Test
	void adminListIsPaginatedAndSearchable() throws Exception {
		TestShop shop = activeShop("couture");
		for (int i = 1; i <= 12; i++) {
			createProduct(shop.token(), product("item " + i, List.of(i % 2 == 0 ? "women" : "men"), List.of("M")));
		}
		getJson("/api/v1/admin/products?page=2&pageSize=10", shop.token())
				.andExpect(jsonPath("$.meta.total").value(12)).andExpect(jsonPath("$.meta.pageCount").value(2))
				.andExpect(jsonPath("$.data.length()").value(2));
		getJson("/api/v1/admin/products?category=women", shop.token()).andExpect(jsonPath("$.meta.total").value(6));
		getJson("/api/v1/admin/products?q=item 11", shop.token()).andExpect(jsonPath("$.meta.total").value(1));
		// newest first
		getJson("/api/v1/admin/products", shop.token()).andExpect(jsonPath("$.data[0].name").value("item 12"));
	}

	@Test
	void updateReplacesChildrenAndDeleteRemoves() throws Exception {
		TestShop shop = activeShop("couture");
		var created = createProduct(shop.token(), product("old", List.of("women"), List.of("S", "M")));
		String url = "/api/v1/admin/products/" + created.get("id").asString();

		putJson(url, shop.token(), product("new", List.of("men", "best-sellers"), List.of("XL")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.message").value(Messages.PRODUCT_UPDATED));
		getJson(url, shop.token())
				.andExpect(jsonPath("$.data.name").value("new"))
				.andExpect(jsonPath("$.data.sizes.length()").value(1))
				.andExpect(jsonPath("$.data.categories.length()").value(2))
				.andExpect(jsonPath("$.options.sizeKind").value("clothing"))
				.andExpect(jsonPath("$.options.defaultSizes[0]").value("S"));

		deleteJson(url, shop.token()).andExpect(status().isNoContent());
		getJson(url, shop.token()).andExpect(status().isNotFound());
	}

	@Test
	void storefrontListsFiltersSortsAndShowsRelated() throws Exception {
		TestShop shop = activeShop("couture");
		var cheap = new HashMap<>(product("cheap", List.of("women"), List.of("M")));
		cheap.put("price", 10);
		var dear = new HashMap<>(product("dear", List.of("women"), List.of("M")));
		dear.put("price", 900);
		createProduct(shop.token(), cheap);
		var dearProduct = createProduct(shop.token(), dear);
		createProduct(shop.token(), product("man", List.of("men"), List.of("L")));

		getJson("/api/v1/shops/" + shop.slug() + "/products?category=women&sort=price-desc", null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].name").value("dear"))
				.andExpect(jsonPath("$.meta.categoryCounts.all").value(3))
				.andExpect(jsonPath("$.meta.categoryCounts.women").value(2))
				.andExpect(jsonPath("$.meta.categoryCounts.men").value(1));

		getJson("/api/v1/shops/" + shop.slug() + "/products/" + dearProduct.get("slug").asString(), null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.related[0].name").value("cheap"));

		getJson("/api/v1/shops/" + shop.slug(), null)
				.andExpect(jsonPath("$.data.status").value("active"))
				.andExpect(jsonPath("$.data.type.sizeKind").value("clothing"))
				.andExpect(jsonPath("$.data.categories[0].code").value("women"))
				.andExpect(jsonPath("$.data.owner.email").value(shop.email()));
	}
}
