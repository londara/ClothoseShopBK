# 08 — Shop sign-up (register a new shop + owner)

**Source:** `API_AND_DATABASE.md` §7, §8 "POST /shops". The person who creates a shop becomes
the **owner (admin) of that shop**.

## Endpoint

```http
POST /api/v1/shops           (public)
{
  "shopName": "ហាងសុភា", "slug": "sophea-shop", "telegram": "sophea_shop", "shopType": "couture",
  "owner": { "name": "សុភា", "email": "sophea@mail.com", "phone": "", "password": "sophea1234", "confirmPassword": "sophea1234" }
}
```
→ `201` with `message: SHOP_CREATED` and the same token body as login (owner is signed in).

## Files

`shop/ShopSignupController`, `shop/SlugPolicy`, `shop/ShopRepository`,
`category/CategoryRepository.copyFromType`, `user/UserRepository.insert`.

## Steps

1. **Validate input** (all errors carry `field`):
   - shop name required; `shopType` must exist in `shop_types`;
   - `SlugPolicy.check`: lower-case letters/digits/dashes, 3–40 chars, not a reserved word
     (`admin`, `api`, `uploads`, `login`, `register`, `shops`, `_next`, …) → `SLUG_RESERVED`;
   - telegram username (leading `@` removed);
   - owner: name, **email or phone** (`Validators.contact`), password ≥ 8 and equals confirm.
2. **Uniqueness** → 409: slug taken, email taken, phone taken (checked across the whole platform,
   because email/phone are login identifiers).
3. **One transaction** (`@Transactional`):
   1. insert `shops` with status **`pending`**;
   2. insert `shop_settings` with defaults (hero title = shop name, default hero image);
   3. copy the shop type's default categories into `shop_categories`;
   4. insert the user with role `owner`, status `active`, bcrypt password.
4. Issue access + refresh tokens and return them (201).
5. The shop is **hidden from the public until the platform admin approves it**
   ([15-platform-admin-shops.md](15-platform-admin-shops.md)); the owner can already add products.

## Tests

`ShopLifecycleApiTest.signupValidatesAndSignsTheOwnerIn` — reserved/invalid/taken slug,
categories copied for a jewelry shop.
