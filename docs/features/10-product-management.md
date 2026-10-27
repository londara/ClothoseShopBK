# 10 — Product management (admin)

**Source:** `API_AND_DATABASE.md` §8.2–8.6. Owner **and** staff can manage products.

## Endpoints (`product/AdminProductController`)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/admin/products?q=&category=&page=1&pageSize=10` | paginated list, newest first |
| GET | `/api/v1/admin/products/options` | form options: categories, `sizeKind`, size options, default sizes |
| GET | `/api/v1/admin/products/{id}` | `{ data: product, options: … }` for the edit form |
| POST | `/api/v1/admin/products` | 201 |
| PUT | `/api/v1/admin/products/{id}` | full replace |
| DELETE | `/api/v1/admin/products/{id}` | 204 |

```json
{ "name": "អាវយឺតកប្បាស", "description": "...", "price": 25.5, "stock": 10,
  "badge": "ថ្មី", "badgeTone": "gold", "atelier": "...",
  "categories": ["women"], "sizes": ["S", "M", "L"],
  "colors": [{ "name": "ខ្មៅ", "hex": "#111111" }],
  "images": ["/uploads/lx9a2k-3f9c1a2b4d5e.jpg"] }
```

## Files

`ProductDtos`, `ProductRepository`, `ProductService`, `AdminProductController`,
`shoptype/SizeKind`.

## Steps

1. **Size systems** — `SizeKind` enum: `NONE` (jewelry/ceramics/leather), `CLOTHING` (XS–XXL),
   `SHOES` (36–45), `KIDS` (0-1 … 11-12), each with Khmer hints and default sizes.
   The shop's type decides which one applies.
2. **Validation** (`ProductService.validate`, Khmer message + field for each rule):
   - name and description required, price > 0, stock ≥ 0 (missing = 0);
   - at least one category, every code must exist **in this shop**;
   - at least one image: `https://…` or `/uploads/…` belonging to this shop (no `javascript:` etc.);
   - sizes: for shops with a size system, unknown values are dropped, the rest stored in
     size-system order (`L,S,M` → `S,M,L`), and at least one is required; **ignored** for `NONE` shops;
   - colours: name + `#rrggbb` (stored lower-case), no duplicate names, max 12;
   - badge tone ∈ `badge_tone` enum.
3. **Slug** — `uniqueSlug`: Latin letters/digits of the name + `-` + 6 random hex, unique per shop.
   Khmer-only names have no Latin characters, so they become `product-3f9a1b`.
4. **Create** (transaction): insert product with `sort_order = min − 1` (appears first),
   then `replaceChildren` inserts images, sizes, colours (with `position`) and category links.
5. **Update** — update the row `WHERE id AND shop_id` (0 rows → 404) and replace all children.
6. **Delete** — `DELETE … WHERE id AND shop_id`; children cascade.
7. **List** — `count` + `page` with the same `WHERE` (built by `where(filter)`), then
   `children(shopId, ids)` loads categories/images for the page in 3–4 queries (no N+1).
   `meta` = `{page, pageSize, total, pageCount}`.

> Gotcha found by the tests: a Java text block drops its leading space, so appending
> `""" AND …"""` produced `:shopIdAND`. The `where` builder appends `" "` explicitly.

## Tests

`ProductApiTest` — sizes/colours, Khmer validation messages, jewelry shop ignores sizes,
pagination/search/category filter, update replaces children, delete → 404.
