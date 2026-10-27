# 13 — Staff management (owner only)

**Source:** `API_AND_DATABASE.md` §8.12–8.15. The owner creates staff accounts; staff can manage
products but **cannot** create staff or change settings.

## Endpoints (`staff/StaffController`, all `ctx.requireOwner()`)

| Method | Path | |
|---|---|---|
| GET | `/api/v1/admin/staff` | owner + staff of this shop |
| POST | `/api/v1/admin/staff` | `{ name, email?, phone?, password, confirmPassword }` → 201 |
| PATCH | `/api/v1/admin/staff/{id}` | `{ "status": "active" \| "disabled" }` |
| DELETE | `/api/v1/admin/staff/{id}` | 204 |

## Steps

1. Staff role check: `requireOwner()` → staff get 403 `FORBIDDEN`.
2. **Create:** name, email **or** phone (normalised, e.g. `+855 12 345 678` → `012345678`),
   password ≥ 8 = confirm; email/phone unique across the platform (409 `EMAIL_TAKEN`/`PHONE_TAKEN`).
   Insert with role `staff`, status `active`, `shop_id = ctx.shopId()`.
3. **Disable / enable:** the account is looked up with `findInShop(shopId, id)` (other shop → 404);
   the owner account → 403 `OWNER_STATUS_LOCKED`. `UserRepository.updateStaffStatus` also has
   `role = 'staff' AND shop_id = :shopId` in its SQL as a second guard. Disabling also calls `revokeAllForUser`, and because
   `ShopContextResolver` re-reads the user, the staff's current access token stops working
   immediately (403 `ACCOUNT_DISABLED`).
4. **Delete:** same scope rule; refresh tokens cascade.

## Tests

`StaffAndSettingsApiTest`: staff created with +855 phone and logs in with it, duplicate phone → 409,
staff can create products but gets 403 on staff/settings, disable blocks the existing token,
owner can't be disabled. `TenancyApiTest`: other shop's staff → 404.
