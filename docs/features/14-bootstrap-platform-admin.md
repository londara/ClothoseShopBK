# 14 — Bootstrap the platform admin

**Problem:** new shops start `pending` and only the platform admin can approve them — but on a
fresh database there is no admin.

## File

`bootstrap/PlatformAdminBootstrap.java` (`ApplicationRunner`), config `aura.bootstrap` in `application.yml`.

## Env vars

| Var | Example |
|---|---|
| `ADMIN_EMAIL` | `admin@aura-studio.com` |
| `ADMIN_PHONE` | `012345678` (optional) |
| `ADMIN_PASSWORD` | ≥ 8 chars |
| `ADMIN_NAME` | display name |
| `FIRST_SHOP_SLUG` / `FIRST_SHOP_NAME` / `FIRST_SHOP_TELEGRAM` | `aura-studio` / … |

## Steps

1. On startup, if **any** user exists → do nothing (safe to restart).
2. If no users and `ADMIN_PASSWORD` + email or phone are set:
   - create the first shop (`couture`) with status **`active`** and default settings/categories;
   - create its owner with `is_platform_admin = true`, bcrypt password.
3. If the variables are missing, log a warning explaining which variables to set.
4. Log in with the email or phone like any user; the response has `user.isPlatformAdmin = true`
   and `/api/v1/platform/**` is now accessible (15).

To change the admin's password later use `PATCH /api/v1/auth/password` — the env vars are only
read on the very first start.
