# 17 — Integration tests

All tests call the real HTTP API (MockMvc) against a **real PostgreSQL 18** started in-process —
no Docker, no local database or password needed.

```bash
mvn -B test        # 43 tests, ~40 s (first run downloads the Postgres binaries)
mvn -B test -Dtest=AuthApiTest
```

## Setup (`src/test/java/.../support/IntegrationTest.java`)

1. `@SpringBootTest @AutoConfigureMockMvc` (Boot 4 package:
   `org.springframework.boot.webmvc.test.autoconfigure`).
2. A static `EmbeddedPostgres` (zonky) is started once per JVM; `@DynamicPropertySource` sets
   `spring.datasource.url = jdbc:postgresql://localhost:{port}/postgres?stringtype=unspecified`,
   the bootstrap admin (`admin@aura.test` / `Admin12345`) and a temp `UPLOAD_DIR`.
   Flyway migrates on startup, the bootstrap runner creates the platform admin.
3. Helpers: `getJson/postJson/putJson/patchJson/deleteJson(url, token, body)`, `login`,
   `adminToken`, `signup(type)` (pending shop + owner token), `activeShop(type)` (signup + approve),
   `product(...)`, `createProduct(...)`, unique emails/phones via `uniq()`.
4. Tests don't clean the DB; each test creates its own shop so they are independent.

> MockMvc treats the URL as a URI template and encodes it — write `?q=item 11`, not `item%2011`.

## Test classes

| Class | Feature docs |
|---|---|
| `AuthApiTest` (10) | 04, 05 |
| `TenancyApiTest` (7) | 06, 16 |
| `ProductApiTest` (7) | 09, 10, 11 |
| `ShopLifecycleApiTest` (4) | 08, 09, 15 |
| `StaffAndSettingsApiTest` (4) | 12, 13 |
| `SwaggerApiTest` (2) | 07 |
| `CaffeineCacheApiTest` (4), `RedisCacheApiTest` (5) | 19 (shared tests in `support/CacheBehaviourTest`; the Redis ones are skipped without Redis) |
