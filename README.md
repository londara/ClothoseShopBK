# AURA Studio — backend (Spring Boot 4)

This is the REST API for the AURA Studio multi-shop fashion platform. The Next.js website in
[`../aura-studio`](../aura-studio/README.md) uses it for all its data. The API contract is
[`../aura-studio/docs/API_AND_DATABASE.md`](../aura-studio/docs/API_AND_DATABASE.md).

- Spring Boot 4.1.2 · Java 17 · Spring Security 7 (OAuth2 resource server) · springdoc-openapi 3
- PostgreSQL with Flyway migrations; plain SQL through `JdbcClient` (no JPA)
- JWT login with **phone number or email**: HS256 access token (15 min) + rotating refresh token (30 days)
- **Multi-shop:** every admin query is scoped to the logged-in user's shop
- **Caching:** storefront and per-request checks are served from Caffeine or Redis (`CACHE_TYPE`); every write clears what it changed, see [19-caching.md](docs/features/19-caching.md)
- **Input security:** length limits that match the database, https-only URLs, uploads checked by file content, 1 MB body limit, per-IP rate limits; see [20-input-security.md](docs/features/20-input-security.md)
- Database diagram for [dbdiagram.io](https://dbdiagram.io/d): see [§4](#4-database-diagram-dbdiagramio)
- Khmer error messages · Swagger UI at <http://localhost:8080/swagger-ui.html>

---

## 1. Run

### Quick (development, no PostgreSQL install)

```bash
ADMIN_EMAIL=you@example.com ADMIN_PASSWORD=change-me-123 mvn spring-boot:test-run
```

This starts an embedded PostgreSQL 18 on port 54329 and keeps its data in `./data/dev-postgres`
(delete that folder to start over). Then start the frontend (`cd ../aura-studio && npm run dev`)
and log in at <http://localhost:3000/admin/login>.

### With your own PostgreSQL

```bash
psql -U postgres -c "CREATE DATABASE aura_studio"     # once

# PowerShell: $env:DB_PASSWORD="..."
export DB_URL="jdbc:postgresql://localhost:5432/aura_studio?stringtype=unspecified"
export DB_USERNAME=postgres
export DB_PASSWORD=your-password
export JWT_SECRET="$(openssl rand -base64 48)"        # at least 32 bytes
export ADMIN_EMAIL=admin@example.com                  # platform admin, created on first start
export ADMIN_PASSWORD=change-me-123

mvn spring-boot:run
```

On start, Flyway creates the tables. The first time, the platform admin and the first shop
(`/aura-studio`) are created from the `ADMIN_*` variables.

### Environment variables

| Variable | Default | Meaning |
|---|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/aura_studio?stringtype=unspecified` / `postgres` / empty | Database |
| `JWT_SECRET` | dev-only value | HS256 signing key, **≥ 32 bytes**; change it in production |
| `ADMIN_EMAIL`, `ADMIN_PHONE`, `ADMIN_PASSWORD`, `ADMIN_NAME` | — | First platform admin (only used while no user exists) |
| `FIRST_SHOP_SLUG`, `FIRST_SHOP_NAME`, `FIRST_SHOP_TELEGRAM` | `aura-studio`, … | The admin's shop |
| `UPLOAD_DIR` | `./data/uploads` | Where images are stored (`shops/{shopId}/…`) |
| `CORS_ORIGINS` | `http://localhost:3000` | Only for browser tools that call the API directly. The Next.js site calls it from its server, so browsers don't need CORS. |
| `CACHE_TYPE` | `caffeine` | Read cache: `caffeine` (in memory, one instance), `redis` (shared, **use with more than one instance**), `none` |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_DATABASE`, `SPRING_DATA_REDIS_PASSWORD` | `localhost`, `6379`, `0`, – | Redis connection (only with `CACHE_TYPE=redis`); `REDIS_HEALTH=true` adds it to `/actuator/health` |
| `CACHE_STOREFRONT_TTL`, `CACHE_SHOP_TYPES_TTL`, `CACHE_SESSION_TTL` | `10m`, `6h`, `60s` | Cache lifetimes. Changes made through the API clear the cache immediately anyway. |
| `RATE_LIMIT_SIGNUPS_PER_HOUR`, `RATE_LIMIT_LOGINS_PER_MINUTE` | `10`, `30` | Per client IP (`0` = off). The Next.js server forwards the visitor's IP. |
| `FORWARD_HEADERS_STRATEGY` | `native` | Trust `X-Forwarded-For` from internal proxies only (for the real client IP) |

### Test

```bash
mvn -B test        # 53 integration tests against an embedded PostgreSQL 18 (Redis cache tests run when Redis is on localhost:6379)
```

---

## 2. Project structure

```
aura-backend/
├── pom.xml                         dependencies (Boot 4 starters, springdoc, BouncyCastle, embedded-postgres for tests)
├── docs/
│   ├── database.dbml               all tables for dbdiagram.io (see §4)
│   └── features/                   one file per feature: how it was built, step by step (see §5)
└── src/
    ├── main/
    │   ├── resources/
    │   │   ├── application.yml         all settings, read from env vars (see §1)
    │   │   └── db/migration/
    │   │       ├── V1__init_schema.sql     enums + all tables (shops, users, products, tokens, uploads…)
    │   │       └── V2__seed_shop_types.sql the 4 shop types and their default categories
    │   └── java/com/aurastudio/backend/
    │       ├── AuraBackendApplication.java
    │       ├── config/         SecurityConfig (JWT, public routes, JSON 401/403), OpenApiConfig (Swagger),
    │       │                   WebConfig, AuraProperties (typed `aura.*` settings)
    │       ├── common/         ApiResponse {data,message,meta}, ApiException + ErrorCode, GlobalExceptionHandler,
    │       │                   Messages (every Khmer text), Validators (email/phone/hex/URL rules), PageMeta
    │       ├── auth/           AuthController (/auth/login, refresh, logout, me, password), AuthService,
    │       │   │               LoginThrottle (5 failures → 15 min lock), AuthDtos
    │       │   ├── jwt/            JwtConfig (Nimbus encoder/decoder, HS256), JwtService (claims)
    │       │   ├── token/          RefreshTokenService (hashed, rotated, reuse → revoke family)
    │       │   └── password/       bcrypt + NodeScryptPasswordEncoder (old Next.js hashes, upgraded on login)
    │       ├── cache/          CacheConfig (Caffeine or Redis, TTLs), CacheNames, CacheEvictions (clear on every write)
    │       ├── tenant/         ShopContext + @CurrentShop resolver: the user's shop from the JWT (multi-shop rule)
    │       │                   + TenantLookup (cached account/shop status check)
    │       ├── user/           UserRepository, UserRow/UserDto, roles and statuses
    │       ├── shop/           ShopSignupController (POST /shops), ShopRepository, ShopSettings, SlugPolicy
    │       ├── shoptype/       ShopTypeRepository, SizeKind (clothing / shoes / kids / none)
    │       ├── category/       per-shop categories: CategoryController, CategoryRepository
    │       ├── product/        AdminProductController, ProductService (validation), ProductRepository (SQL), DTOs
    │       ├── upload/         UploadController (POST /uploads, GET /uploads/{file}), UploadService
    │       ├── settings/       SettingsController (owner): shop name, hero, colours, about, Telegram
    │       ├── profile/        ProfileController: my name, title, email/phone, avatar
    │       ├── staff/          StaffController (owner): create / disable / delete staff
    │       ├── dashboard/      DashboardController: totals, stock value, low stock, latest products
    │       ├── platform/       PlatformShopController (platform admin): approve / suspend / delete shops
    │       ├── storefront/     StorefrontController (public API), StorefrontCatalog (cached reads),
    │       │                   StorefrontQuery (in-memory filter/sort/page), StorefrontDtos
    │       ├── security/       RequestSizeFilter (1 MB body limit), RateLimiter (per-IP sign-up/login limits)
    │       └── bootstrap/      PlatformAdminBootstrap: first admin + first shop on an empty database
    └── test/java/com/aurastudio/backend/
        ├── support/IntegrationTest.java    embedded PostgreSQL + MockMvc helpers
        ├── support/CacheBehaviourTest.java cache tests shared by Caffeine and Redis
        ├── AuthApiTest, TenancyApiTest, ProductApiTest, ShopLifecycleApiTest,
        │   StaffAndSettingsApiTest, SwaggerApiTest, CaffeineCacheApiTest, RedisCacheApiTest,
        │   InputSecurityApiTest (attack inputs), RateLimitApiTest
        └── DevAuraBackendApplication.java  `mvn spring-boot:test-run` (API on embedded PostgreSQL)
```

**How a request flows.** `SecurityConfig` checks the Bearer JWT. The controller receives
`@CurrentShop ShopContext`, which is resolved from the token and re-checked against the
database. The service validates the input, and the repository runs SQL with
`WHERE shop_id = :shopId`. The result goes back as `ApiResponse`. Errors become
`ApiException` and are returned as `{error:{code,message,field}}` with a Khmer message.

**Packages are organised by feature**, not by layer. Each folder holds its own
controller, service, repository and DTOs.

---

## 3. Endpoints overview

| Area | Endpoints | Auth |
|---|---|---|
| Auth | `POST /api/v1/auth/login` · `POST /auth/refresh` · `POST /auth/logout` · `GET /auth/me` · `PATCH /auth/password` | login/refresh/logout public |
| Storefront | `GET /api/v1/shop-types` · `GET /shops` · `GET /featured` · `GET /shops/{slug}` · `GET /shops/{slug}/products` · `GET /shops/{slug}/products/{productSlug}` | public |
| Sign-up | `POST /api/v1/shops` | public |
| Shop admin | `/api/v1/admin/dashboard` · `/admin/products` (+ `/options`, `/{id}`) · `/admin/categories` · `/admin/profile` | owner + staff |
| Owner only | `/api/v1/admin/settings` · `/admin/staff` (+ `/{id}`) | owner |
| Platform | `/api/v1/platform/shops` (+ `/{id}`) | platform admin |
| Files | `POST /api/v1/uploads` (Bearer) · `GET /uploads/{file}` (public) | |

Every request and response is documented in Swagger (<http://localhost:8080/swagger-ui.html>). Click
**Authorize** and paste the `accessToken` from login.

---

## 4. Database diagram (dbdiagram.io)

All 14 tables, 5 enums and every relation, including the two **composite foreign keys** that
stop a product from being linked to another shop's category. To see the diagram:

1. Open <https://dbdiagram.io/d>.
2. Delete the sample code on the left.
3. Paste the script below (it is also in [`docs/database.dbml`](docs/database.dbml)).

The source of truth is the Flyway migration `V1__init_schema.sql`; update this script when you
add a migration.

**Tables at a glance**

| Group | Tables |
|---|---|
| Shop types (seed data) | `shop_types` → `shop_type_categories` |
| Shops | `shops` → `shop_settings` (1:1), `shop_categories` |
| Accounts & login | `users` → `refresh_tokens`; `login_attempts` |
| Products | `products` → `product_images`, `product_sizes`, `product_colors`; `product_categories` (product ↔ category of the **same** shop) |
| Files | `uploads` |

```dbml
// AURA Studio — database schema (PostgreSQL), for https://dbdiagram.io/d
// Source of truth: src/main/resources/db/migration/V1__init_schema.sql

Project aura_studio {
  database_type: 'PostgreSQL'
  Note: 'Multi-shop fashion platform. Every shop-owned row carries shop_id.'
}

Enum size_kind {
  none
  clothing [note: 'XS–XXL']
  shoes [note: '36–45']
  kids [note: '0-1 … 11-12 years']
}

Enum shop_status {
  pending [note: 'waiting for platform admin approval']
  active
  suspended
}

Enum user_role {
  owner
  staff
}

Enum user_status {
  active
  pending
  disabled
}

Enum badge_tone {
  neutral
  gold
}

Table shop_types {
  id uuid [pk, default: `gen_random_uuid()`]
  code varchar(40) [not null, unique, note: 'couture, jewelry, ceramics, leather']
  label varchar(40) [not null, unique]
  size_kind size_kind [not null, default: 'none']
  sort_order int [not null, default: 0]
  created_at timestamptz [not null, default: `now()`]
  updated_at timestamptz [not null, default: `now()`]
}

Table shop_type_categories {
  id uuid [pk, default: `gen_random_uuid()`]
  shop_type_id uuid [not null]
  code varchar(40) [not null]
  label varchar(40) [not null]
  sort_order int [not null]

  indexes {
    (shop_type_id, code) [unique]
  }
  Note: 'Default categories copied to a new shop at sign-up'
}

Table shops {
  id uuid [pk, default: `gen_random_uuid()`]
  slug varchar(40) [not null, unique, note: 'URL /{slug}; ^[a-z0-9]+(-[a-z0-9]+)*$, 3–40 chars']
  status shop_status [not null, default: 'pending']
  shop_type_id uuid [not null]
  approved_at timestamptz
  approved_by uuid
  created_at timestamptz [not null, default: `now()`]
  updated_at timestamptz [not null, default: `now()`]

  indexes {
    status [name: 'shops_status_idx']
  }
}

Table shop_settings {
  shop_id uuid [pk]
  shop_name varchar(80) [not null]
  telegram varchar(32) [not null, note: '^[A-Za-z0-9_]{5,32}$']
  announcement varchar(200) [not null, default: '']
  hero_title varchar(120) [not null]
  hero_subtitle varchar(300) [not null, default: '']
  hero_image text [not null, note: 'https://… or /uploads/{file} of this shop']
  background_color char(7) [not null, default: '#f8f9fa', note: '^#[0-9a-f]{6}$']
  about_title varchar(80) [not null, default: 'អំពីយើង']
  about_me text [not null, default: '']
  updated_at timestamptz [not null, default: `now()`]
  Note: '1:1 with shops'
}

Table shop_categories {
  id uuid [pk, default: `gen_random_uuid()`]
  shop_id uuid [not null]
  code varchar(40) [not null, note: 'default codes or c-xxxxxx for added ones']
  label varchar(40) [not null]
  sort_order int [not null, note: 'best-sellers = 99 (always last)']
  created_at timestamptz [not null, default: `now()`]

  indexes {
    (shop_id, code) [unique]
    (shop_id, label) [unique]
    (shop_id, id) [unique, note: 'target of the tenant-safe composite FK']
  }
}

Table users {
  id uuid [pk, default: `gen_random_uuid()`]
  shop_id uuid [not null]
  role user_role [not null]
  is_platform_admin boolean [not null, default: false]
  status user_status [not null, default: 'active']
  name varchar(80) [not null]
  title varchar(80) [not null, default: '']
  email varchar(254) [unique, note: 'login; stored lower-case']
  phone varchar(15) [unique, note: 'login; digits only, +855 → 0']
  avatar text
  password_hash text [not null, note: '{bcrypt}… (old Next.js scrypt hashes upgraded on login)']
  created_at timestamptz [not null, default: `now()`]
  updated_at timestamptz [not null, default: `now()`]

  indexes {
    shop_id [unique, name: 'users_one_owner_per_shop', note: 'partial: WHERE role = owner']
    shop_id [name: 'users_shop_idx']
  }
  Note: 'CHECK (email IS NOT NULL OR phone IS NOT NULL)'
}

Table refresh_tokens {
  id uuid [pk, default: `gen_random_uuid()`]
  user_id uuid [not null]
  token_hash char(64) [not null, unique, note: 'SHA-256 of the token, never the token itself']
  family_id uuid [not null, note: 'one login; reuse of a rotated token revokes the family']
  expires_at timestamptz [not null]
  revoked_at timestamptz
  replaced_by uuid
  user_agent text
  ip text
  created_at timestamptz [not null, default: `now()`]

  indexes {
    user_id [name: 'refresh_tokens_active_user_idx', note: 'partial: WHERE revoked_at IS NULL']
    family_id [name: 'refresh_tokens_family_idx']
  }
}

Table login_attempts {
  identifier varchar(254) [pk, note: 'normalised email or phone']
  failures int [not null, default: 0]
  locked_until timestamptz [note: '5 failures → locked 15 minutes']
  updated_at timestamptz [not null, default: `now()`]
}

Table products {
  id uuid [pk, default: `gen_random_uuid()`]
  shop_id uuid [not null]
  slug varchar(80) [not null]
  name varchar(120) [not null]
  description text [not null, note: 'max 5,000 characters (API)']
  price numeric(12,2) [not null, note: '> 0']
  stock int [not null, default: 0, note: '>= 0']
  badge varchar(40)
  badge_tone badge_tone [not null, default: 'neutral']
  atelier varchar(80) [not null, default: '']
  sort_order int [not null, default: 0, note: 'new products get min - 1 (shown first)']
  created_at timestamptz [not null, default: `now()`]
  updated_at timestamptz [not null, default: `now()`]

  indexes {
    (shop_id, slug) [unique]
    (shop_id, id) [unique, note: 'target of the tenant-safe composite FK']
    (shop_id, sort_order) [name: 'products_shop_order_idx']
    (shop_id, price) [name: 'products_shop_price_idx']
  }
}

Table product_images {
  id uuid [pk, default: `gen_random_uuid()`]
  product_id uuid [not null]
  url text [not null, note: 'https://… or /uploads/{file} of the same shop']
  position int [not null, note: '0 = main image']

  indexes {
    (product_id, position) [unique]
  }
}

Table product_sizes {
  product_id uuid [not null]
  size varchar(10) [not null]
  position int [not null]

  indexes {
    (product_id, size) [pk]
  }
}

Table product_colors {
  id uuid [pk, default: `gen_random_uuid()`]
  product_id uuid [not null]
  name varchar(30) [not null]
  hex char(7) [not null, note: '^#[0-9a-f]{6}$']
  position int [not null]

  indexes {
    (product_id, name) [unique]
  }
}

Table product_categories {
  shop_id uuid [not null, note: 'in both composite FKs: product and category must be in the same shop']
  product_id uuid [not null]
  category_id uuid [not null]

  indexes {
    (product_id, category_id) [pk]
    (shop_id, category_id) [name: 'product_categories_shop_category_idx']
  }
}

Table uploads {
  id uuid [pk, default: `gen_random_uuid()`]
  file_name varchar(80) [not null, unique, note: 'random name, served at /uploads/{file_name}']
  mime_type varchar(30) [not null, note: 'detected from the file bytes']
  size_bytes int [not null, note: '<= 5 MB']
  shop_id uuid [not null]
  storage_key text [not null, note: 'shops/{shop_id}/{file_name}']
  uploaded_by uuid
  created_at timestamptz [not null, default: `now()`]

  indexes {
    shop_id [name: 'uploads_shop_idx']
  }
}

Ref: shop_type_categories.shop_type_id > shop_types.id [delete: cascade]
Ref: shops.shop_type_id > shop_types.id [delete: restrict]
Ref: shops.approved_by > users.id [delete: set null]
Ref: shop_settings.shop_id - shops.id [delete: cascade]
Ref: shop_categories.shop_id > shops.id [delete: cascade]
Ref: users.shop_id > shops.id [delete: cascade]
Ref: refresh_tokens.user_id > users.id [delete: cascade]
Ref: refresh_tokens.replaced_by > refresh_tokens.id [delete: set null]
Ref: products.shop_id > shops.id [delete: cascade]
Ref: product_images.product_id > products.id [delete: cascade]
Ref: product_sizes.product_id > products.id [delete: cascade]
Ref: product_colors.product_id > products.id [delete: cascade]
// Tenant-safe: the database refuses to link a product to another shop's category.
Ref: product_categories.(shop_id, product_id) > products.(shop_id, id) [delete: cascade]
Ref: product_categories.(shop_id, category_id) > shop_categories.(shop_id, id) [delete: cascade]
Ref: uploads.shop_id > shops.id [delete: cascade]
Ref: uploads.uploaded_by > users.id [delete: set null]
```

---

## 5. Implementation notes (one file per feature)

| # | Feature |
|---|---|
| 01 | [Project setup](docs/features/01-project-setup.md) |
| 02 | [Database schema & Flyway](docs/features/02-database-schema-flyway.md) |
| 03 | [Error handling & API response](docs/features/03-error-handling-api-response.md) |
| 04 | [JWT login with phone or email](docs/features/04-jwt-login-phone-email.md) |
| 05 | [Refresh token rotation](docs/features/05-refresh-token-rotation.md) |
| 06 | [Multi-shop isolation](docs/features/06-multi-shop-isolation.md) |
| 07 | [Swagger / OpenAPI](docs/features/07-swagger-openapi.md) |
| 08 | [Shop sign-up](docs/features/08-shop-signup.md) |
| 09 | [Public storefront API](docs/features/09-public-storefront-api.md) |
| 10 | [Product management](docs/features/10-product-management.md) |
| 11 | [Product categories](docs/features/11-product-categories.md) |
| 12 | [Shop settings & profile](docs/features/12-shop-settings-and-profile.md) |
| 13 | [Staff management](docs/features/13-staff-management.md) |
| 14 | [Bootstrap platform admin](docs/features/14-bootstrap-platform-admin.md) |
| 15 | [Platform admin: shops](docs/features/15-platform-admin-shops.md) |
| 16 | [File uploads](docs/features/16-file-uploads.md) |
| 17 | [Testing](docs/features/17-testing.md) |
| 18 | [Frontend integration](docs/features/18-frontend-integration.md) |
| 19 | [Caching (Caffeine / Redis)](docs/features/19-caching.md) |
| 20 | [Input security](docs/features/20-input-security.md) |
