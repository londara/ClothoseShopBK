package com.aurastudio.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.aurastudio.backend.support.IntegrationTest;

/** Swagger UI and the OpenAPI document are public and list every endpoint. */
class SwaggerApiTest extends IntegrationTest {

	@Test
	void openApiDocumentDescribesTheApi() throws Exception {
		var doc = body(getJson("/v3/api-docs", null).andExpect(status().isOk()));
		assertThat(doc.get("components").get("securitySchemes").get("bearerAuth").get("bearerFormat").asString())
				.isEqualTo("JWT");
		var paths = doc.get("paths");
		for (String path : new String[] { "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/me",
				"/api/v1/shops", "/api/v1/shops/{slug}/products", "/api/v1/admin/products",
				"/api/v1/admin/products/{id}", "/api/v1/admin/categories", "/api/v1/admin/settings",
				"/api/v1/admin/staff", "/api/v1/platform/shops", "/api/v1/uploads" }) {
			assertThat(paths.has(path)).as(path).isTrue();
		}
		// ShopContext comes from the JWT, it must not appear as a request parameter
		assertThat(paths.get("/api/v1/admin/products").get("get").get("parameters").toString()).doesNotContain("ctx");
	}

	@Test
	void swaggerUiIsServed() throws Exception {
		getJson("/swagger-ui/index.html", null).andExpect(status().isOk());
	}
}
