# 12 — Shop settings & personal profile

**Source:** `API_AND_DATABASE.md` §8.10–8.11.

## Endpoints

| Method | Path | Who |
|---|---|---|
| GET / PUT | `/api/v1/admin/settings` | **owner only** (`ctx.requireOwner()`, staff → 403) |
| PUT | `/api/v1/admin/profile` | every logged-in user (edits **their own** account) |

## Settings (`settings/SettingsController`, `shop/ShopSettings`)

Fields: `shopName, telegram, announcement, heroTitle, heroSubtitle, heroImage,
backgroundColor, aboutTitle, aboutMe`.

Steps:
1. Validate: shop name required; telegram username (strip `@`, 5–32 `[A-Za-z0-9_]`);
   `backgroundColor` must be `#rrggbb` (stored lower-case); `heroImage` must be an https URL
   or an `/uploads/…` file of this shop.
2. **Defaults for empty fields:** `heroTitle` → shop name, `aboutTitle` → "អំពីយើង",
   `telegram` → keeps the current one. (`ShopSettings.DEFAULT_HERO` is the hero image given to
   a new shop at sign-up.)
3. `ShopRepository.updateSettings(shopId, …)` also updates `shops.name`. The storefront shows the
   new values immediately (`GET /shops/{slug}`).

## Profile (`profile/ProfileController`)

Fields: `name, title, email, phone, avatar`.

Steps:
1. Name required; `Validators.contact` normalises email/phone and requires **at least one**
   (`PROFILE_EMAIL_OR_PHONE_REQUIRED`) — otherwise the user could not log in.
2. Email/phone must not belong to another user (409), checked with `emailTaken(email, exceptUserId)`.
3. `UserRepository.updateProfile(shopId, userId, …)`. The new email/phone works for login
   right away.

## Tests

`StaffAndSettingsApiTest.settingsApplyDefaultsAndShowOnTheStorefront`,
`profileChangesTheLoginIdentifier`, `ownerCreatesStaffWhoLogsInWithPhone` (staff → 403 on settings).
