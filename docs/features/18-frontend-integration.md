# 18 — Connecting the Next.js frontend (aura-studio)

The Next.js app no longer has its own data store (`data/store.json`). Every page and
server action calls this API. The **Next.js server** makes the calls; the browser never
talks to the API and never sees a token.

```
browser ──► Next.js (pages, server actions, proxy.ts) ──► Spring API :8080 ──► PostgreSQL
            cookies: aura_access, aura_refresh (httpOnly)
```

## Run both

```bash
# 1. backend: no PostgreSQL install needed for development
cd aura-backend
ADMIN_EMAIL=you@example.com ADMIN_PASSWORD=change-me-123 mvn spring-boot:test-run
#    (or mvn spring-boot:run with DB_URL / DB_USERNAME / DB_PASSWORD for a real database)

# 2. frontend
cd aura-studio
echo "API_URL=http://localhost:8080" >> .env.local
npm run dev
```

## Frontend files

| File | Role |
|---|---|
| `src/lib/api.ts` | `api()` fetch wrapper: reads `{data, message, meta}` and throws `ApiError(status, code, Khmer message)`; `apiAll()` loads every page of a list; API types |
| `src/lib/session-cookie.ts` | cookie names; access cookie expires 60 s before the JWT |
| `src/lib/auth.ts` | `getSession()` (one `GET /auth/me` per request), `requireAdmin/Owner/PlatformAdmin`, `authed()` = API call with the user's token, `saveTokens`, `destroySession` (calls `/auth/logout`) |
| `src/proxy.ts` | `/admin/*` gate + **token refresh** |
| `src/lib/data.ts` | public storefront reads (`/shops`, `/shops/{slug}`, `/shops/{slug}/products`, `/shop-types`), cached with `"use cache"` + tags |
| `src/app/admin/actions.ts` | server actions → API calls; images go to `POST /api/v1/uploads` first |
| `next.config.ts` | rewrite `/uploads/:file` → `{API_URL}/uploads/:file` |

## Steps

1. **API client.** One `api()` function sets `Authorization: Bearer`, sends JSON or
   multipart data, and turns error bodies into `ApiError`. A network failure becomes a Khmer
   "cannot reach the server" message.
2. **Login and sign-up.** The server actions call `/auth/login` or `POST /shops` and store
   `accessToken` and `refreshToken` in httpOnly cookies. The same `identifier` field takes a
   phone number or an email.
3. **Session.** `getSession()` calls `/auth/me` with the access cookie. On 401 or 403
   (expired, disabled, suspended) the user counts as logged out, so the API's checks apply
   on every request.
4. **Refresh in `proxy.ts`.** Server components can't set cookies, so the refresh happens
   before the page renders. If the access cookie is gone and a refresh cookie is present,
   `proxy.ts` calls `/auth/refresh`, sets the new cookies on the response, *and* updates the
   request's cookies so the page in this request already uses the new token.
5. **Concurrent refresh.** The API treats a second use of a rotated refresh token as theft
   (see 05), and browsers send several admin requests at once (prefetches). To avoid false
   alarms, `proxy.ts` shares one in-flight refresh per token and reuses its result for 60 s.
   This works for one Next.js instance; for several instances, route a user to the same
   one (sticky sessions).
6. **Mapping.** The API data is converted to the existing frontend types (`Product`,
   `Settings`, `PublicUser`), so the UI components are unchanged. Storefront products use the
   **slug** as `id` (it goes in the URL), and admin products use the **UUID** (for edit and
   delete links).
7. **Uploads.** The actions upload files to `/api/v1/uploads` and save the returned
   `/uploads/...` path. The browser loads `/uploads/...` from Next.js, which proxies it to
   the API.
8. **Caching.** Storefront data is cached. Admin actions call `updateTag("shops")` so their
   changes show at once. Changes made directly in the API (Swagger) show after a few minutes
   (`cacheLife("minutes")`).
9. **Form options.** `GET /admin/products/options` also returns `typeLabel`, the shop type's
   name shown in the product form.

## Verified

- storefront `/`, `/aura-studio` and its catalogue render from the API;
- `/admin` without a cookie → 307 to `/admin/login`;
- all admin pages render with a token;
- with only a refresh cookie, `/admin` returns 200 and sets new cookies;
- 4 parallel requests with the same refresh token all succeed (no logout);
- an image uploaded to the API loads through `http://localhost:3000/uploads/...`.
