# 09 — Public storefront API

**Source:** `API_AND_DATABASE.md` §6 — read-only endpoints used by the Next.js shop pages
(`/` and `/{slug}`). No login.

## Endpoints (`storefront/StorefrontController`)

| GET | Returns |
|---|---|
| `/api/v1/shop-types` | the 4 shop types with their size options (sign-up form) |
| `/api/v1/shops` | active shops for the platform home page |
| `/api/v1/featured?perShop=4` | up to `perShop` (1–12) products of each active shop, interleaved |
| `/api/v1/shops/{slug}` | shop info: status, type (+ `sizeKind`), settings, categories, owner contact |
| `/api/v1/shops/{slug}/products?category=&q=&sort=&page=&pageSize=` | product list |
| `/api/v1/shops/{slug}/products/{productSlug}` | product detail + `related` |

## Steps

1. Resolve the shop by slug with `ShopRepository.findBySlug`; unknown → 404.
2. **Status rule:**
   - `active` → full data;
   - `pending` / `suspended` → `/shops/{slug}` returns only `{slug, name, status}` (frontend shows
     the "waiting for approval" / "suspended" notice); products endpoints return 404.
3. **Listing** — `ProductService.storefrontList` builds a `Filter`:
   - `category` = shop category code (`all` or missing = no filter);
   - `q` searches name, description and badge (`ILIKE`, `%`/`_` escaped);
   - `sort`: `curated` (default, `sort_order`), `price-asc`, `price-desc`;
   - page size clamped 1–60.
   `meta.categoryCounts` = `{ "all": n, "<code>": n, … }` for the filter chips.
4. **Detail** — product by `(shop_id, slug)`; `related` = up to 3 products sharing a category.
   If a clothing product has no sizes, the shop's default sizes are shown.
5. Every query passes the shop's id → products of other shops can never appear
   (`/shops/a/products/{slug-of-shop-b}` → 404).
6. `/uploads/{fileName}` serves images publicly with a 1-year cache (16).

## Tests

`ProductApiTest.storefrontListsFiltersSortsAndShowsRelated`, `ShopLifecycleApiTest.pendingShopIsHiddenUntilApproved`,
`TenancyApiTest` (public URL scoped by shop).
