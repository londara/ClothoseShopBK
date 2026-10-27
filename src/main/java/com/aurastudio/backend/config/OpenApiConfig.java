package com.aurastudio.backend.config;

import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aurastudio.backend.tenant.ShopContext;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;

/**
 * Swagger UI at {@code /swagger-ui.html}, OpenAPI JSON at {@code /v3/api-docs}.
 * Click "Authorize" and paste the {@code accessToken} from {@code POST /api/v1/auth/login}.
 */
@Configuration
public class OpenApiConfig {

	public static final String BEARER = "bearerAuth";

	static {
		// ShopContext is injected from the JWT, not sent by the client.
		SpringDocUtils.getConfig().addRequestWrapperToIgnore(ShopContext.class);
	}

	@Bean
	OpenAPI auraOpenApi() {
		return new OpenAPI()
				.info(new Info().title("AURA Studio API").version("v1").description("""
						Multi-shop platform API (docs/API_AND_DATABASE.md).

						**Login:** `POST /api/v1/auth/login` with `identifier` = **email or phone number**
						(`012 345 678`, `+855 12 345 678` and `85512345678` are the same phone) and `password`.
						Copy `accessToken`, click **Authorize** and paste it. When it expires (15 min) call
						`POST /api/v1/auth/refresh` with the `refreshToken`.

						**Multi-shop rule:** every `/admin/...` request works on the signed-in user's own shop only.
						Ids from another shop answer `404`. Only platform admins can use `/platform/...`.

						Errors: `{ "error": { "code", "message" (Khmer), "field"? } }`."""))
				.components(new Components().addSecuritySchemes(BEARER,
						new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
				.addTagsItem(new Tag().name("1. Auth").description("JWT login with phone number or email"))
				.addTagsItem(new Tag().name("2. Public storefront").description("No token needed"))
				.addTagsItem(new Tag().name("3. Shop sign-up").description("Open a new shop"))
				.addTagsItem(new Tag().name("4. Shop admin").description("Own shop only (token user's shop)"))
				.addTagsItem(new Tag().name("5. Platform admin").description("All shops — platform admins only"))
				.addTagsItem(new Tag().name("6. Uploads").description("Images, stored per shop"));
	}
}
