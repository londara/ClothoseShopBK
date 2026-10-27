# 11 — Per-shop product categories

**Source:** `API_AND_DATABASE.md` §2.4, §8.7. Each shop has its own categories, copied from its
shop type at sign-up; the owner or staff can add more from the product form
("បន្ថែមប្រភេទថ្មី").

## Endpoints (`category/CategoryController`)

```http
GET  /api/v1/admin/categories
POST /api/v1/admin/categories   { "label": "អាវយឺត" }   → 201 { data: { code: "c-3f9a1b", label, sortOrder }, message }
```

## Steps

1. `CategoryRepository.copyFromType(shopId, typeId)` — used by sign-up (08).
2. **Add:**
   - label required, trimmed, cut to 40 chars;
   - same label already in the shop → 409 `CATEGORY_TAKEN`;
   - max 20 categories per shop;
   - code = `c-` + 6 random hex chars (labels are Khmer, so they can't be used as URL codes);
   - insert, then `moveBestSellersLast` keeps `best-sellers` at the end of the list.
3. `CategoryDto` hides the internal UUID (`@JsonIgnore`); the API speaks in `code`s only.
4. Products refer to categories by code; codes are resolved **within the shop** (06).

## Tests

`ProductApiTest.newCategoryGoesBeforeBestSellersAndNamesAreUnique`, `TenancyApiTest` (foreign category rejected).
