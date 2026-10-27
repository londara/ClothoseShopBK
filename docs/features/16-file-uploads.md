# 16 — Image uploads

**Source:** `API_AND_DATABASE.md` §2.12, §8.8.

## Endpoints (`upload/UploadController`)

```http
POST /api/v1/uploads           (Bearer, multipart/form-data, field "file")
→ 201 { "data": { "id": "...", "url": "/uploads/lx9a2k-3f9c1a2b4d5e.jpg", "mimeType": "image/jpeg", "sizeBytes": 123456 } }

GET /uploads/{fileName}        (public, Cache-Control: max-age=31536000, public, immutable)
```

## Steps (`upload/UploadService`)

1. **Type check** — allow only `image/jpeg`, `image/png`, `image/webp`, `image/gif`,
   `image/avif` (else 415 `UNSUPPORTED_FILE`); the extension comes from the type, not from the user's file name.
2. **Size check** — `aura.uploads.max-bytes` (5 MB) and `spring.servlet.multipart.max-file-size`;
   too large → 413 `FILE_TOO_LARGE` (handled in `GlobalExceptionHandler`).
3. **Name** — `base36(time)-12hex.ext`, never the original name (no path tricks, no collisions).
4. **Store** under `${UPLOAD_DIR}/shops/{shopId}/` and insert a row in `uploads` with `shop_id`.
5. **Serve** — `find(fileName)` validates the name with `Validators.uploadFileName`, resolves the
   path and refuses anything outside the upload dir (path traversal guard).
6. **Ownership** — product images / hero image using `/uploads/…` must belong to the same shop
   (`belongsToShop(shopId, fileName)`), otherwise 400 on `images`/`heroImage`.

## Tests

`TenancyApiTest` — using another shop's uploaded image in a product is rejected.
`IntegrationTest` points `UPLOAD_DIR` at a temp folder.
