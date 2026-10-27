# 06 — Multi-shop isolation (every query scoped by the logged-in account)

**Source:** `API_AND_DATABASE.md` §4.1. Many shops share one database; a user must never read
or change another shop's data.

## Files

- `tenant/ShopContext.java` — `record ShopContext(userId, shopId, role, platformAdmin)` with
  `requireOwner()` and `requirePlatformAdmin()`
- `tenant/CurrentShop.java` — parameter annotation
- `tenant/ShopContextResolver.java` — builds the context from the JWT
- `config/WebConfig.java` — registers the resolver

## Steps

1. **Controllers never take a shop id from the client.** Every admin endpoint declares
   `@CurrentShop ShopContext ctx`; the `shopId` comes only from the verified token.
   A `shopId` sent in the JSON body is ignored.
2. **The resolver re-checks the database on each request** (a token is valid 15 min, the account
   can change in that time):
   - user exists and is `active` (else 403 `ACCOUNT_DISABLED` / `ACCOUNT_PENDING`);
   - user's `shop_id` equals the token's `shop` claim;
   - shop not `suspended` (else 403 `SHOP_SUSPENDED`) — except for the platform admin.
3. **Every repository method takes `shopId` as its first argument** and puts
   `AND shop_id = :shopId` in its SQL (`ProductRepository`, `CategoryRepository`,
   `UserRepository.findInShop/listByShop`, `UploadService.belongsToShop` ...).
4. **Other shop's id → 404, not 403.** `GET /admin/products/{id}` of another shop finds no row
   and returns NOT_FOUND, so ids of other shops can't even be confirmed to exist.
5. **References are checked against the shop:** category codes in a product are resolved with
   `CategoryRepository.findByCodes(shopId, codes)`; image URLs pointing to `/uploads/...` must
   belong to the shop (`UploadService.belongsToShop`).
6. **Database backstop:** composite foreign keys on `product_categories (shop_id, …)` make
   cross-shop links impossible even if code has a bug (see 02).
7. **Files** are stored under `uploads/shops/{shopId}/`.
8. **Roles inside a shop:** `ctx.requireOwner()` for staff and settings,
   `ctx.requirePlatformAdmin()` for `/api/v1/platform/**` (403 otherwise).
9. **Swagger:** `OpenApiConfig` calls `SpringDocUtils.getConfig().addRequestWrapperToIgnore(ShopContext.class)`
   so the context isn't shown as a request parameter.

## Tests

`TenancyApiTest`: another shop's product → 404 on GET/PUT/DELETE, lists and totals only contain
own products, foreign category or upload rejected, foreign staff id → 404, body `shopId`
ignored, platform endpoints → 403 for normal owners, public product URL scoped by shop slug.
