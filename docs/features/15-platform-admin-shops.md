# 15 — Platform admin: approve, suspend, delete shops

**Source:** `API_AND_DATABASE.md` §9. Only users with `is_platform_admin = true`
(`ctx.requirePlatformAdmin()`, others → 403).

## Endpoints (`platform/PlatformShopController`)

| Method | Path | |
|---|---|---|
| GET | `/api/v1/platform/shops` | all shops, pending first; `meta.pending` count |
| PATCH | `/api/v1/platform/shops/{id}` | `{ "status": "active" \| "suspended" \| "pending" }` |
| DELETE | `/api/v1/platform/shops/{id}` | 204, removes shop + users + products |

## Steps

1. **List** — `ShopRepository.findAllForPlatform` joins type and owner contact, plus a product
   count; order `pending` → `active` → `suspended`, then by shop name.
2. **Approve** (`active`) — first activation sets `approved_at = now()`, `approved_by = admin`;
   message `SHOP_APPROVED`. The shop appears on `/api/v1/shops` and its storefront opens.
3. **Suspend** — status `suspended` + `RefreshTokenService.revokeAllForShop`. Effects:
   - existing access tokens → 403 `SHOP_SUSPENDED` (resolver re-checks the shop);
   - refresh → 401; login → 403 `SHOP_SUSPENDED`;
   - storefront shows only the "suspended" notice.
4. **Protect own shop** — the admin cannot suspend or delete the shop they belong to (403),
   so the platform can't lock itself out.
5. **Delete** — `DELETE FROM shops` cascades to settings, categories, users, tokens, products
   and upload rows. (Image files under `shops/{shopId}/` stay on disk — clean up manually if needed.)
6. `/me` returns `counts.pendingShops` for the admin's badge.

## Tests

`ShopLifecycleApiTest`: pending hidden → approve → visible + featured; suspend blocks token,
refresh and login; own shop protected; delete → 404 + login fails. `TenancyApiTest`: normal
owner → 403.
