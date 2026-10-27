# 07 — Swagger / OpenAPI

## URLs (when running locally)

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>

## Files

- `pom.xml` — `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0` (the 3.x line supports Spring Boot 4)
- `config/OpenApiConfig.java`
- `application.yml` — `springdoc.*`

## Steps

1. Add the springdoc starter.
2. In `application.yml`:
   ```yaml
   springdoc:
     api-docs.path: /v3/api-docs
     swagger-ui:
       path: /swagger-ui.html
       persist-authorization: true   # keeps the token after page reload
       tags-sorter: alpha
   ```
3. `OpenApiConfig` defines the `OpenAPI` bean:
   - title, version and a description explaining *login with phone or email* and the
     multi-shop rule;
   - security scheme **`bearerAuth`** (HTTP, `bearer`, format `JWT`);
   - tags `1. Auth`, `2. Public storefront`, `3. Shop sign-up`, `4. Shop admin`,
     `5. Platform admin`, `6. Uploads`.
4. Controllers use `@Tag` and `@Operation(summary = ...)`. Protected controllers (and the protected
   auth operations `/me`, `/password`) add `@SecurityRequirement(name = OpenApiConfig.BEARER)`,
   so only they show a lock; public endpoints don't.
5. Hide the injected `ShopContext` parameter (see 06).
6. Permit `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**` in `SecurityConfig`.

## How to use

1. Open Swagger UI → **1. Auth → POST /api/v1/auth/login → Try it out**.
2. Body `{ "identifier": "012345678", "password": "..." }` → copy `data.accessToken`.
3. Click **Authorize**, paste the token (without "Bearer"), and call the admin endpoints.

## Tests

`SwaggerApiTest`: `/v3/api-docs` lists all main paths and the `bearerAuth` scheme, does not
expose `ctx`; `/swagger-ui/index.html` returns 200 without login.
