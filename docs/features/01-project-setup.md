# 01 — Project setup (Spring Boot 4)

**Goal:** a Spring Boot backend for the Aura Studio multi-shop platform described in
`aura-studio/docs/API_AND_DATABASE.md`.

## Stack (latest versions at time of writing)

| Piece | Version | Why |
|---|---|---|
| Spring Boot | 4.1.2 | latest release; modular starters |
| Java | 17 | minimum for Boot 4 |
| Spring Security | 7.1.x | JWT resource server |
| PostgreSQL driver + Flyway | 12.x | schema migrations |
| springdoc-openapi | 3.2.0 | Swagger UI for Boot 4 |
| BouncyCastle `bcprov-jdk18on` | 1.86 | scrypt for legacy password hashes |
| zonky embedded-postgres (test) | 2.2.2 / PG 18.6 | real Postgres in tests, no Docker |

## Steps

1. **Generate the project** with Spring Initializr: Maven, Java 17, group `com.aurastudio`,
   artifact `aura-backend`, package `com.aurastudio.backend`.
   Starters: `webmvc`, `validation`, `security-oauth2-resource-server`, `flyway`, `actuator`, `postgresql`.
2. **Swap JPA for JDBC.** The API doc is written in SQL (composite keys, enums, `FOR UPDATE`),
   so data access uses `JdbcClient` with plain SQL — replace `spring-boot-starter-data-jpa`
   with `spring-boot-starter-jdbc` (and `-jdbc-test`).
3. **Add dependencies** in `pom.xml`: `springdoc-openapi-starter-webmvc-ui`, `bcprov-jdk18on`,
   and `io.zonky.test:embedded-postgres` (test scope) + import `embedded-postgres-binaries-bom`
   in `<dependencyManagement>` to get PostgreSQL 18 binaries.
4. **Remove** the generated `application.properties` and `HELP.md`; create
   `src/main/resources/application.yml` (all config comes from env vars with dev defaults).
5. **Typed config:** `config/AuraProperties.java` is one `@ConfigurationProperties("aura")` record
   with nested records `Jwt`, `Login`, `Uploads`, `Cors`, `Bootstrap`.
   `AuraBackendApplication` has `@ConfigurationPropertiesScan` so the record is picked up.
6. **Package by feature** (not by layer):

```
auth/        login, refresh, logout, me, password  (+ jwt/, password/, token/)
tenant/      ShopContext + @CurrentShop resolver  (multi-shop isolation)
common/      ApiResponse, ApiException, ErrorCode, Messages (Khmer), Validators
config/      Security, OpenAPI, Web, AuraProperties
shop/ shoptype/ category/ product/ upload/ settings/ profile/ staff/
dashboard/ platform/ storefront/ bootstrap/ user/
```

## Configuration (`application.yml`)

| Env var | Default | Used for |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/aura_studio?stringtype=unspecified` | database |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / empty | database |
| `JWT_SECRET` | dev-only value | HS256 key, **≥ 32 bytes**, change in production |
| `UPLOAD_DIR` | `./data/uploads` | image files |
| `CORS_ORIGINS` | `http://localhost:3000` | the Next.js frontend |
| `ADMIN_EMAIL`, `ADMIN_PHONE`, `ADMIN_PASSWORD`, `ADMIN_NAME` | — | see [14-bootstrap-platform-admin.md](14-bootstrap-platform-admin.md) |

`?stringtype=unspecified` lets PostgreSQL cast plain strings to `uuid`/enum columns.

## Verify

```bash
mvn -B test          # 34 integration tests on embedded PostgreSQL
mvn spring-boot:run  # needs DB_URL/DB_USERNAME/DB_PASSWORD
```
