# 05 — Refresh tokens with rotation

**Source:** `API_AND_DATABASE.md` §2.7 (`refresh_tokens`) and §5.3.

The access token lives 15 minutes. To stay logged in, the frontend exchanges a refresh token
(30 days) for a new pair.

## Endpoints

```http
POST /api/v1/auth/refresh   { "refreshToken": "..." }   → 200 same body as login
POST /api/v1/auth/logout    { "refreshToken": "..." }   → 204
```

## File

`auth/token/RefreshTokenService.java`

## Steps

1. **Issue** — 64 random bytes from `SecureRandom`, Base64URL encoded and returned **once**.
   Only `SHA-256(token)` is stored in `refresh_tokens.token_hash`, so a database leak doesn't
   leak usable tokens. Each login starts a new `family_id`.
2. **Rotate** (`/refresh`):
   - `SELECT ... WHERE token_hash = :hash FOR UPDATE` — locks the row so two parallel refreshes
     can't both succeed;
   - not found or expired → 401 `TOKEN_INVALID`;
   - already revoked → **reuse detected**: someone copied the token. Revoke every token in the
     family (forces a new login on all copies) and return 401;
   - otherwise insert a new token in the same family and mark the old one
     `revoked_at = now(), replaced_by = new id`.
3. **Transaction detail** — `rotate` is `@Transactional(noRollbackFor = ApiException.class)`.
   With the default, throwing the 401 after revoking the family would roll the revocation back
   (and, nested inside `AuthService`, cause `UnexpectedRollbackException` → 500).
4. After rotation `AuthService.refresh` reloads the user and shop and re-applies the account
   checks (disabled user / suspended shop can't refresh).
5. **Revoke**:
   - `revoke(token)` — logout of this device (unknown tokens are ignored, still 204);
   - `revokeAllForUser` — password change, staff disabled;
   - `revokeAllForShop` — shop suspended by the platform admin.

## Tests

`AuthApiTest`: refresh returns a new token, old token reuse → 401 and the new one is dead too,
logout makes the token unusable. `ShopLifecycleApiTest`: refresh fails after suspension.
