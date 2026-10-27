# 04 — JWT login with phone number or email

**Source:** `API_AND_DATABASE.md` §5 (auth) and §8.1.

## Endpoints

| Method | Path | Auth |
|---|---|---|
| POST | `/api/v1/auth/login` | public |
| GET | `/api/v1/auth/me` | Bearer |
| PATCH | `/api/v1/auth/password` | Bearer |

```http
POST /api/v1/auth/login
{ "identifier": "+855 12 345 678", "password": "owner12345" }
```
```json
{ "data": {
  "tokenType": "Bearer", "accessToken": "eyJ...", "expiresIn": 900,
  "refreshToken": "q8V...", "refreshExpiresIn": 2592000,
  "user": { "id": "...", "name": "...", "email": null, "phone": "012345678", "role": "owner", "isPlatformAdmin": false },
  "shop": { "id": "...", "slug": "my-shop", "name": "...", "status": "active" } } }
```

## Files

- `auth/AuthController`, `auth/AuthService`, `auth/AuthDtos`
- `auth/jwt/JwtConfig` (encoder/decoder), `auth/jwt/JwtService` (issues tokens)
- `auth/LoginThrottle` (lockout), `auth/password/*` (hashing)
- `config/SecurityConfig` (filter chain)
- `common/Validators.normalizeIdentifier / normalizeEmail / normalizePhone`

## Steps

1. **One `identifier` field.** `Validators.normalizeIdentifier`:
   - contains `@` → email, trimmed and lower-cased (`Sok@Mail.COM` = `sok@mail.com`);
   - otherwise → phone: drop spaces/dashes/brackets, `+855` or `855` prefix → leading `0`
     (`+855 12 345 678`, `85512345678`, `012-345-678`, `012345678` are all `012345678`).
   `UserRepository.findByIdentifier` then looks up `email` **or** `phone`.
2. **Passwords** — `PasswordConfig` builds a `DelegatingPasswordEncoder` with bcrypt as the
   default (`{bcrypt}$2a$...`). Old hashes from the Next.js prototype (`saltHex:hashHex`,
   Node `crypto.scrypt`, N=16384 r=8 p=1, 64 bytes) are verified by
   `NodeScryptPasswordEncoder` and **re-hashed to bcrypt** after a successful login.
3. **Same error for everything** — unknown account and wrong password both return
   401 `INVALID_CREDENTIALS` with the same Khmer message, so nobody can probe which
   phones/emails are registered. Failures for unknown accounts count towards the lockout too.
4. **Lockout** — `LoginThrottle` counts failures per normalised identifier in `login_attempts`.
   After `aura.login.max-failures` (5) the account is locked for `lock-duration` (15 min):
   423 `LOCKED` with a `Retry-After` header. A successful login clears the counter.
   `AuthService.login` is `@Transactional(noRollbackFor = ApiException.class)` so the failure
   counter is committed even though the request ends in an error.
5. **Account checks after the password:** `pending` user → 403 `ACCOUNT_PENDING`,
   `disabled` → 403 `ACCOUNT_DISABLED`, shop suspended (and not platform admin) → 403 `SHOP_SUSPENDED`.
6. **Access token** — `JwtConfig` creates a `NimbusJwtEncoder` with an HS256 `ImmutableSecret`
   from `JWT_SECRET` (startup fails if shorter than 32 bytes). `JwtService.issueAccessToken` claims:

   | claim | value |
   |---|---|
   | `sub` | user id |
   | `shop` | shop id |
   | `role` | `owner` / `staff` |
   | `pa` | platform admin flag |
   | `iss` / `aud` | `aura-studio` / `aura-admin` |
   | `jti`, `iat`, `exp` | random id, now, now + 15 min |

7. **Verification** — `NimbusJwtDecoder` with HS256 + validators for timestamp, issuer and audience.
   `SecurityConfig` enables `oauth2ResourceServer(jwt)`, stateless sessions, no CSRF (no cookies),
   CORS from `CORS_ORIGINS`. Its entry point returns JSON 401: `TOKEN_EXPIRED` when the token is
   expired (frontend should call refresh), `TOKEN_INVALID` otherwise.
8. **Public vs protected routes** in `SecurityConfig`: login/refresh/logout, shop sign-up,
   storefront GETs, `/uploads/**`, Swagger and `/actuator/health` are public; everything else
   needs a valid Bearer token.
9. **`GET /me`** returns user, shop and counts (pending shops for the platform admin).
10. **Change password** — checks the current password, validates the new one, stores bcrypt,
    **revokes all refresh tokens** of the user and returns a fresh token pair for this device.

## Tests

`AuthApiTest`: email in any case, phone in 4 formats, identical error for unknown/wrong,
lockout + Retry-After, expired token → TOKEN_EXPIRED, password change logs out other devices,
legacy scrypt hash → login works and is upgraded to `{bcrypt}`.
