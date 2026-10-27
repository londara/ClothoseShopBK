# 19 — Caching (Caffeine / Redis)

**Goal:** serve the read-heavy traffic, mostly anonymous shoppers browsing storefronts,
without querying PostgreSQL on every request, while keeping data correct right after every
change made through the API.

## What is cached

| Cache | Key | Holds | TTL (default) | Cleared when |
|---|---|---|---|---|
| `shop-types` | `all` | the shop types | 6 h (`CACHE_SHOP_TYPES_TTL`) | (seed data, rarely changes) |
| `storefront-shops` | `all` | home page directory + product counts | 10 min (`CACHE_STOREFRONT_TTL`) | product create/edit/delete, settings, category, shop status/delete |
| `storefront-shop` | slug | one shop: settings, categories, owner, type, status | 10 min | settings, category added, owner profile, shop status/delete |
| `shop-products` | shop id | **all** products of the shop as storefront DTOs, curated order | 10 min | product create/edit/delete, shop status/delete |
| `shop-context` | user id | account status, role, shop and shop status (checked on every authenticated request) | 60 s (`CACHE_SESSION_TTL`) | staff status change/delete, shop status/delete |

**Not cached:** admin lists, dashboard and settings forms (few users, must be exact; the
Next.js frontend caches them per user), login, refresh, and all writes.

**Effect:** a storefront page (list, filter, search, sort, page, product detail, related
products, category counts) runs **0 SQL queries** while the cache is warm. On a miss, one
shop costs about 5 queries, and the result then serves every visitor until the next change
or the TTL.

## Files

| File | Role |
|---|---|
| `cache/CacheNames.java` | cache names + key conventions |
| `cache/CacheConfig.java` | `@EnableCaching`; builds the Caffeine or Redis `CacheManager` with per-cache TTL, size and JSON type |
| `cache/CacheEvictions.java` | `productsChanged`, `shopChanged`, `userChanged`, `allSessionsChanged` |
| `storefront/StorefrontCatalog.java` | `@Cacheable` reads: `shopTypes()`, `activeShops()`, `shop(slug)`, `products(shopId)` |
| `storefront/StorefrontQuery.java` | filter / search / sort / page / category counts / related, **in memory** on the cached list |
| `storefront/StorefrontDtos.java` | storefront response records + `StorefrontShop` (the cached shop) |
| `tenant/TenantLookup.java` | `@Cacheable` account + shop status for `ShopContextResolver` |
| `application.yml` | `aura.cache.*`, `spring.data.redis.*`, `management.health.redis.enabled` |

## Implementation steps

1. **Dependencies:** `spring-boot-starter-cache`, `caffeine` and `spring-boot-starter-data-redis`.
2. **Choose the backend with `CACHE_TYPE`.** `CacheConfig` defines the `CacheManager` itself
   (Boot's auto-configuration backs off):
   - `caffeine` (default): in memory, per instance; one Caffeine cache per name with
     `expireAfterWrite(ttl)` and `maximumSize`.
   - `redis`: shared by **all** API instances. Keys look like `aura:{cache}::{key}`, values
     are JSON (Jackson 3) with a **typed serializer per cache** (`JacksonJsonRedisSerializer`
     with the cache's `JavaType`), so no class names are stored in Redis.
   - `none`: caching off (debugging).
3. **Transaction-aware.** Both managers delay evictions made inside a `@Transactional` write
   until **after commit**, so a parallel reader can't re-cache the old rows in between.
4. **Redis writer settings** (learned from testing):
   - `immediateWrites()`: by default Spring Data Redis 4 evicts **asynchronously with
     `UNLINK`**. On a failure (e.g. Redis < 4.0, or a network blip) the error is **silently
     dropped** and stale data stays cached. Synchronous `DEL` makes a failed eviction an
     error on the request that changed the data.
   - `BatchStrategies.scan(1000)`: `clear()` uses `SCAN` instead of `KEYS`, which would block
     the whole Redis server on a large keyspace.
5. **Cache whole-shop product lists, query them in memory.** Caching each
   `?q=&category=&sort=&page=` combination would create unbounded keys that can't be evicted
   per shop. Instead `shop-products` holds the shop's full list (one key per shop) and
   `StorefrontQuery` applies the same rules as the old SQL:
   - case-insensitive search in name, description and badge;
   - category filter, `all` = no filter;
   - sort `curated` / `price-asc` / `price-desc` (stable, so ties keep the curated order);
   - page size 1–60;
   - `categoryCounts` = `all` plus every shop category in order, zeros included;
   - related = up to 3 other products sharing a category.
6. **Evict on every write** through `CacheEvictions`:

   | Write | Call |
   |---|---|
   | `ProductService.create / update / delete` | `productsChanged(shopId)` |
   | `CategoryController.add`, `SettingsController.save`, `ProfileController.update` (owner) | `shopChanged(shopId)` |
   | `StaffController.changeStatus / delete` | `userChanged(staffId)` |
   | `PlatformShopController.changeStatus` | `shopChanged(id)` + `allSessionsChanged()` |
   | `PlatformShopController.delete` | the same, **before** deleting (the slug is looked up first) |

7. **Per-request account check.** `ShopContextResolver` now calls `TenantLookup.find(userId)`
   (cached) instead of 2 queries per request. A disabled staff member or suspended shop is
   still blocked **on the next request**, because those writes evict the entry.
8. **Nulls and errors are never cached** (`unless = "#result == null"`,
   `disableCachingNullValues`), so an unknown slug is not remembered as missing and a shop
   created later is found.

## Configuration

```bash
CACHE_TYPE=redis                 # caffeine (default) | redis | none
REDIS_HOST=redis.internal  REDIS_PORT=6379  REDIS_DATABASE=0
SPRING_DATA_REDIS_PASSWORD=...   # if Redis needs a password
REDIS_HEALTH=true                # include Redis in /actuator/health
CACHE_STOREFRONT_TTL=10m  CACHE_SHOP_TYPES_TTL=6h  CACHE_SESSION_TTL=60s
```

## Running many instances (large scale)

- **Use `CACHE_TYPE=redis`** as soon as there is more than one API instance. With Caffeine
  each instance has its own cache and an eviction only reaches the instance that handled the
  write; the others stay stale until the TTL.
- Use **Redis ≥ 6** in production (a managed Redis or Redis Cluster). Size its memory for
  (number of shops × products per shop × ~2 KB) and set `maxmemory-policy allkeys-lru`.
- Caching alone doesn't make the platform scale to very large traffic. Also plan for:
  - a CDN in front of the Next.js storefront and `/uploads`;
  - PostgreSQL read replicas and connection pooling (PgBouncer);
  - several API instances behind a load balancer (the API is stateless: JWT);
  - rate limiting on login and on unknown slugs;
  - load tests before launch.
- **Limits of this design:** a shop's whole product list is one cache value, which is fine
  for thousands of products per shop. For shops with tens of thousands, switch that shop to
  paged SQL or a search engine.

## Tests

`support/CacheBehaviourTest` runs four tests on **both** backends: `CaffeineCacheApiTest`,
and `RedisCacheApiTest`, which uses Redis database 15 on localhost and is skipped when no
Redis is running.

1. Storefront product list and detail stay cached when the DB is changed **directly**, and
   are fresh right after an **API** update.
2. The shop page and the directory: same for settings.
3. The account check is cached, but disabling staff through the API blocks them on the next
   request.
4. In-memory search, filter, paging and category counts follow the API rules.

`RedisCacheApiTest` also checks that values really live in Redis under `aura:*` with a TTL
and come back identical (prices, sizes, colours, Khmer text). All earlier tests pass
unchanged with the cache on: 43 tests in total.
