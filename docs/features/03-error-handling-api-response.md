# 03 — API response envelope & error handling

**Source:** `API_AND_DATABASE.md` §4 (conventions) and §12 (Khmer messages).

## Shape

```json
// success
{ "data": { ... }, "message": "បានរក្សាទុក", "meta": { "page": 1, "pageSize": 10, "total": 12, "pageCount": 2 } }
// error
{ "error": { "code": "VALIDATION", "message": "សូមបញ្ចូលតម្លៃ", "field": "price" } }
```

## Files (`common/`)

| File | Role |
|---|---|
| `ApiResponse` | record `{data, message, meta}`, `@JsonInclude(NON_NULL)`; factories `of`, `page`, `message` |
| `PageMeta` | `page, pageSize, total, pageCount, categoryCounts` |
| `ErrorCode` | enum → HTTP status (VALIDATION 400, INVALID_CREDENTIALS 401, TOKEN_EXPIRED 401, SHOP_SUSPENDED 403, NOT_FOUND 404, CONFLICT 409, LOCKED 423, FILE_TOO_LARGE 413, …) |
| `ApiException` | runtime exception carrying code, Khmer message, optional field and `Retry-After` |
| `ApiError` | the `error` object |
| `Messages` | **every** user-facing string, in Khmer, in one place |
| `Khmer` | converts digits to Khmer numerals for messages |
| `Validators` | trimming, email/phone normalisation, hex colours, URL checks, password rules |
| `GlobalExceptionHandler` | `@RestControllerAdvice` that turns everything into the error shape |

## Steps

1. Write the response records and the `ErrorCode` enum.
2. Collect all Khmer messages from §12 of the doc into `Messages` as constants
   (methods for parameterised ones: `locked(minutes)`, `staffCreated(name)`, `categoryAdded(label)`).
3. Services throw `ApiException.validation(field, msg)`, `notFound()`, `conflict(msg)`, `forbidden()`.
4. `GlobalExceptionHandler` maps:
   - `ApiException` → its code/status (adds `Retry-After` header for LOCKED);
   - Bean Validation / unreadable JSON / wrong types → 400 VALIDATION;
   - `MaxUploadSizeExceededException` → 413; `NoResourceFoundException` → 404;
   - `HttpRequestMethodNotSupported` → 405; `AccessDenied` → 403;
   - anything else → 500 INTERNAL with a generic Khmer message (details only in the log).
5. Security errors (missing/expired token) happen *before* controllers, so `SecurityConfig`
   writes the same JSON shape itself — see [04-jwt-login-phone-email.md](04-jwt-login-phone-email.md).

## Rule

Never leak internal details (SQL, stack traces) in `message`. Validation errors always set
`field` so the frontend can highlight the input.
