# 02 — Database schema with Flyway

**Source:** `API_AND_DATABASE.md` §2 (tables) and §3 (seed data).

## Files

- `src/main/resources/db/migration/V1__init_schema.sql` — enums + all tables
- `src/main/resources/db/migration/V2__seed_shop_types.sql` — the 4 shop types and their default categories

Flyway runs automatically at startup (`spring.flyway.enabled: true`). Never edit an applied
migration — add `V3__...sql` instead.

## Steps

1. **Enums** (`CREATE TYPE`): `size_kind`, `shop_status`, `user_role`, `user_status`, `badge_tone`.
   In SQL from Java write `CAST(:x AS shop_status)` and read with `status::text`.
2. **Catalogue of shop types:** `shop_types` (code, label, size_kind) and
   `shop_type_categories` (default categories copied to a new shop).
3. **Shops:** `shops` (slug `CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')`, status, approved_at/by)
   + `shop_settings` (1:1, hero, about, colours, telegram).
4. **Per-shop categories:** `shop_categories` with `UNIQUE (shop_id, id)` so other tables can
   reference `(shop_id, id)` together.
5. **Users:** `users` with `shop_id`, role, status, `email` (stored lower-case, `CHECK`),
   `phone` (`CHECK (phone ~ '^0[0-9]{8,9}$')`), unique on each, and a partial unique index
   *one owner per shop*. A `CHECK` requires email **or** phone.
6. **Auth tables:** `refresh_tokens` (token_hash, family_id, expires_at, revoked_at, replaced_by)
   and `login_attempts` (identifier, failures, locked_until).
7. **Products:** `products` (`UNIQUE (shop_id, slug)` and `UNIQUE (shop_id, id)`) with children
   `product_images`, `product_sizes`, `product_colors` (`ON DELETE CASCADE`).
8. **Product ↔ category:** `product_categories (shop_id, product_id, category_id)` with
   **composite foreign keys** `(shop_id, product_id) → products(shop_id, id)` and
   `(shop_id, category_id) → shop_categories(shop_id, id)`. The database itself refuses to link
   a product to another shop's category — the second line of defence for
   [06-multi-shop-isolation.md](06-multi-shop-isolation.md).
9. **Uploads:** `uploads` (file_name, shop_id, content_type, size).
10. **Seed** (`V2`): `couture` (clothing sizes), `jewelry`, `ceramics`, `leather` with their
    default categories; `best-sellers` always has `sort_order 99` so it stays last.

Phase-2 tables (orders, newsletter) from the doc are intentionally not created yet.

## Verify

Tests start an empty PostgreSQL 18 and Flyway applies V1 + V2 on every run
(see [17-testing.md](17-testing.md)).
