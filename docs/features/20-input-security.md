# 20 — Input security (hostile input from the frontend or scripts)

**Goal:** treat every request as hostile. Anything a browser, a script or a modified
frontend sends must end in a clean 4xx with a Khmer message, never a 500, and nothing
harmful is stored or served. The Next.js frontend also validates, but that is only for
convenience: an attacker can call the API directly, so **the API is the line of defence**.

## How it was found

`InputSecurityApiTest` was written **first**, with attack inputs. On the code at that time,
all 9 tests failed:

| Attack input | Before | After |
|---|---|---|
| Product name of 121+ chars, settings/profile text over the column size | **500** (`value too long for varchar`) | 400 + field + Khmer message |
| Price `1e15` or `10.123`; stock `10000001` | **500** (`numeric field overflow`) / stored | 400 |
| Password over 72 bytes (bcrypt limit) | **500** (`IllegalArgumentException`) | 400 `PASSWORD_TOO_LONG`; login → 401 |
| `?page=2147483647` (storefront and admin) | **500** (int overflow → `IndexOutOfBounds` / negative OFFSET) | 200, empty page |
| `http://…`, `"` or `<` inside an image URL, 2 KB+ URLs | stored | 400 |
| NUL / control characters in text | **500** (PostgreSQL rejects NUL) / stored | 400 `INVALID_CHARACTERS` |
| 500 categories, 30 images, 500 sizes in one product | processed | 400 `TOO_MANY_ITEMS` |
| Text file uploaded as `image/png` | **stored and served** | 415 (type checked from file bytes) |
| 2 MB JSON body | read into memory, then 400 | 413 `PAYLOAD_TOO_LARGE` before reading |
| 5,000-char login identifier, 100 KB refresh token | processed | 401 straight away |
| Unlimited sign-ups / logins from one IP | allowed | 429 `RATE_LIMITED` + `Retry-After` |

## Rules now enforced

### 1. Text fields (`Validators.text(value, field, max)` / `multiline(...)`)

- Trimmed. **Control characters are rejected** (NUL, BEL, U+2028/2029 …). Newlines and tabs
  are allowed only in multi-line fields (description, about me).
- **Maximum length = the database column**, counted in characters (Khmer included):

| Field | Max | Field | Max |
|---|---|---|---|
| product name | 120 | shop name | 80 |
| product description | 5,000 | announcement | 200 |
| badge | 40 | hero title / subtitle | 120 / 300 |
| atelier | 80 | about title / about me | 80 / 5,000 |
| colour name | 30 | user name / title | 80 / 80 |
| category label | 40 | email | 254 |
| search `q` | 100 | URL | 2,048 |

### 2. URLs (`Validators.isRemoteUrl`)

**https only**, at most 2,048 characters, a real host (`java.net.URI`), and no whitespace,
quotes, `<`, `>`, backslash or backtick, so a URL can never break out of an `src`/`href`
attribute. `javascript:`, `data:` and `http:` are refused. Uploaded images must be
`/uploads/{random-name}.{ext}` **of the same shop**.

### 3. Numbers and lists

- price: > 0, at most 2 decimals, ≤ 999,999,999.99 (fits `numeric(12,2)`).
- stock: 0 … 10,000,000.
- categories ≤ 20, images ≤ 10, sizes ≤ 20, colours ≤ 12.
- Paging: page size clamped; page number clamped (admin ≤ 100,000) or computed in `long`
  (storefront), so huge values give an empty page instead of an overflow.

### 4. Passwords and tokens

- New passwords: 8 characters minimum and **≤ 72 bytes** (bcrypt ignores the rest; Spring
  Security 7 throws). Longer passwords at login can never match, so the API answers the
  normal "wrong credentials".
- Login identifiers over 254 characters and refresh tokens over 200 characters are refused
  before any database work.

### 5. Request size (`security/RequestSizeFilter`)

Non-multipart bodies over **1 MB** get a 413 before anything reads them. Uploads keep their
own 5 MB limit.

### 6. Uploads (`upload/UploadService`, `UploadController`)

- The type is **detected from the file's first bytes** (JPEG `FF D8 FF`, PNG `89 50 4E 47 …`,
  GIF `GIF8?a`, WEBP `RIFF…WEBP`, AVIF `…ftypavif`). The browser's `Content-Type` is ignored,
  so HTML/SVG/script files are refused.
- Quota: 5,000 files per shop.
- Served with `X-Content-Type-Options: nosniff` and
  `Content-Security-Policy: default-src 'none'; img-src 'self'; sandbox`, so even a bad
  file opened directly can't run scripts.

### 7. Rate limits per client IP (`security/RateLimiter`)

| Endpoint | Default | Env var |
|---|---|---|
| `POST /api/v1/shops` (sign-up) | 10 per hour | `RATE_LIMIT_SIGNUPS_PER_HOUR` |
| `POST /api/v1/auth/login` | 30 per minute | `RATE_LIMIT_LOGINS_PER_MINUTE` |

The login limit adds to the per-account lockout (5 failures → 15 min). It stops **password
spraying** (one password tried against many accounts), which never trips a per-account lock.

**Client IP behind Next.js:** browsers don't call the API; the Next.js server does. So
`aura-studio/src/app/admin/actions.ts` sends the visitor's IP as `X-Forwarded-For` on login and
sign-up, and the API uses it thanks to `server.forward-headers-strategy=native`. Tomcat trusts
that header **only from internal addresses** (10.x, 172.16–31.x, 192.168.x, 127.x, ::1). If the
Next.js server is on another network, set `server.tomcat.remoteip.internal-proxies`.
Without this, all visitors would share one limit.

The counters are in memory per API instance. With many instances, also rate-limit at the load
balancer or API gateway.

### 8. Safety net (`GlobalExceptionHandler`)

If a database constraint still rejects something, the answer is 400 (or 409 for a duplicate
key, e.g. two sign-ups racing for one email) instead of 500. The detail goes to the log only.

## Already safe before this feature (verified)

- **SQL injection:** every query uses named parameters (`JdbcClient`). Dynamic SQL is built
  only from fixed fragments (sort order is an enum, never user text). A malformed slug such as
  `Bad Slug'--` is refused with a 4xx before any query runs.
- **Multi-shop:** the shop comes from the JWT, never from the body (06).
- **Path traversal:** upload names must match `^[a-z0-9-]+\.(jpg|png|webp|gif|avif)$` and the
  resolved path must stay inside the upload folder.
- **Errors:** `server.error.include-message: never`; the 500 handler returns a generic message.
- **XSS:** the API returns JSON and React escapes text when rendering. The remaining risk was
  URLs in attributes, now restricted as described in section 2.

## Tests

- `InputSecurityApiTest` (8): every row of the table above.
- `RateLimitApiTest` (2): sign-up 429 + `Retry-After` per IP (another IP is unaffected), and
  login spraying across accounts.

The suite sets very high limits in `IntegrationTest` (it signs up from one IP many times);
`RateLimitApiTest` overrides them with `@TestPropertySource`. Total: **53 tests**.

## Still recommended for production

- HTTPS everywhere, and HSTS at the proxy.
- Turn off Swagger in production: `SPRINGDOC_API_DOCS_ENABLED=false`,
  `SPRINGDOC_SWAGGER_UI_ENABLED=false`.
- A WAF/CDN rate limit in front of everything.
- Regular dependency updates (`mvn versions:display-dependency-updates`).
