-- AURA Studio multi-shop schema (from docs/API_AND_DATABASE.md §3).
-- Differences from the document: plain varchar + lower-case checks instead of
-- the citext extension (works on any PostgreSQL), and the phase-2 tables
-- (orders, order_items, newsletter_subscribers) are not created yet.
-- gen_random_uuid() is built into PostgreSQL 13+.

CREATE TYPE size_kind   AS ENUM ('none', 'clothing', 'shoes', 'kids');
CREATE TYPE shop_status AS ENUM ('pending', 'active', 'suspended');
CREATE TYPE user_role   AS ENUM ('owner', 'staff');
CREATE TYPE user_status AS ENUM ('active', 'pending', 'disabled');
CREATE TYPE badge_tone  AS ENUM ('neutral', 'gold');

CREATE TABLE shop_types (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code        varchar(40) NOT NULL UNIQUE,
  label       varchar(40) NOT NULL UNIQUE,
  size_kind   size_kind   NOT NULL DEFAULT 'none',
  sort_order  int         NOT NULL DEFAULT 0,
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE shop_type_categories (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  shop_type_id  uuid NOT NULL REFERENCES shop_types(id) ON DELETE CASCADE,
  code          varchar(40) NOT NULL,
  label         varchar(40) NOT NULL,
  sort_order    int NOT NULL,
  UNIQUE (shop_type_id, code)
);

CREATE TABLE shops (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  slug          varchar(40) NOT NULL UNIQUE
                CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$' AND length(slug) BETWEEN 3 AND 40),
  status        shop_status NOT NULL DEFAULT 'pending',
  shop_type_id  uuid NOT NULL REFERENCES shop_types(id) ON DELETE RESTRICT,
  approved_at   timestamptz,
  approved_by   uuid,
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX shops_status_idx ON shops (status);

CREATE TABLE shop_settings (
  shop_id           uuid PRIMARY KEY REFERENCES shops(id) ON DELETE CASCADE,
  shop_name         varchar(80)  NOT NULL,
  telegram          varchar(32)  NOT NULL CHECK (telegram ~ '^[A-Za-z0-9_]{5,32}$'),
  announcement      varchar(200) NOT NULL DEFAULT '',
  hero_title        varchar(120) NOT NULL,
  hero_subtitle     varchar(300) NOT NULL DEFAULT '',
  hero_image        text         NOT NULL,
  background_color  char(7)      NOT NULL DEFAULT '#f8f9fa' CHECK (background_color ~ '^#[0-9a-f]{6}$'),
  about_title       varchar(80)  NOT NULL DEFAULT 'អំពីយើង',
  about_me          text         NOT NULL DEFAULT '',
  updated_at        timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE shop_categories (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  shop_id     uuid NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  code        varchar(40) NOT NULL,
  label       varchar(40) NOT NULL,
  sort_order  int NOT NULL,
  created_at  timestamptz NOT NULL DEFAULT now(),
  UNIQUE (shop_id, code),
  UNIQUE (shop_id, label),
  UNIQUE (shop_id, id)            -- target of tenant-safe composite FKs
);

CREATE TABLE users (
  id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  shop_id            uuid NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  role               user_role   NOT NULL,
  is_platform_admin  boolean     NOT NULL DEFAULT false,
  status             user_status NOT NULL DEFAULT 'active',
  name               varchar(80) NOT NULL,
  title              varchar(80) NOT NULL DEFAULT '',
  email              varchar(254) UNIQUE CHECK (email = lower(email)),
  phone              varchar(15)  UNIQUE CHECK (phone ~ '^[0-9]{8,15}$'),
  avatar             text,
  password_hash      text NOT NULL,
  created_at         timestamptz NOT NULL DEFAULT now(),
  updated_at         timestamptz NOT NULL DEFAULT now(),
  CHECK (email IS NOT NULL OR phone IS NOT NULL)
);
CREATE UNIQUE INDEX users_one_owner_per_shop ON users (shop_id) WHERE role = 'owner';
CREATE INDEX users_shop_idx ON users (shop_id);

ALTER TABLE shops ADD CONSTRAINT shops_approved_by_fk
  FOREIGN KEY (approved_by) REFERENCES users(id) ON DELETE SET NULL;

CREATE TABLE refresh_tokens (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash   char(64) NOT NULL UNIQUE,
  family_id    uuid NOT NULL,
  expires_at   timestamptz NOT NULL,
  revoked_at   timestamptz,
  replaced_by  uuid REFERENCES refresh_tokens(id) ON DELETE SET NULL,
  user_agent   text,
  ip           text,
  created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX refresh_tokens_active_user_idx ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
CREATE INDEX refresh_tokens_family_idx ON refresh_tokens (family_id);

CREATE TABLE login_attempts (
  identifier    varchar(254) PRIMARY KEY,
  failures      int NOT NULL DEFAULT 0,
  locked_until  timestamptz,
  updated_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE products (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  shop_id      uuid NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  slug         varchar(80)   NOT NULL,
  name         varchar(120)  NOT NULL,
  description  text          NOT NULL,
  price        numeric(12,2) NOT NULL CHECK (price > 0),
  stock        int           NOT NULL DEFAULT 0 CHECK (stock >= 0),
  badge        varchar(40),
  badge_tone   badge_tone    NOT NULL DEFAULT 'neutral',
  atelier      varchar(80)   NOT NULL DEFAULT '',
  sort_order   int           NOT NULL DEFAULT 0,
  created_at   timestamptz   NOT NULL DEFAULT now(),
  updated_at   timestamptz   NOT NULL DEFAULT now(),
  UNIQUE (shop_id, slug),
  UNIQUE (shop_id, id)            -- target of tenant-safe composite FKs
);
CREATE INDEX products_shop_order_idx ON products (shop_id, sort_order);
CREATE INDEX products_shop_price_idx ON products (shop_id, price);

CREATE TABLE product_images (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id  uuid NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  url         text NOT NULL,
  position    int  NOT NULL,
  UNIQUE (product_id, position)
);

CREATE TABLE product_sizes (
  product_id  uuid NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  size        varchar(10) NOT NULL,
  position    int NOT NULL,
  PRIMARY KEY (product_id, size)
);

CREATE TABLE product_colors (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id  uuid NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  name        varchar(30) NOT NULL,
  hex         char(7) NOT NULL CHECK (hex ~ '^#[0-9a-f]{6}$'),
  position    int NOT NULL,
  UNIQUE (product_id, name)
);

CREATE TABLE product_categories (
  shop_id      uuid NOT NULL,
  product_id   uuid NOT NULL,
  category_id  uuid NOT NULL,
  PRIMARY KEY (product_id, category_id),
  FOREIGN KEY (shop_id, product_id)  REFERENCES products (shop_id, id)        ON DELETE CASCADE,
  FOREIGN KEY (shop_id, category_id) REFERENCES shop_categories (shop_id, id) ON DELETE CASCADE
);
CREATE INDEX product_categories_shop_category_idx ON product_categories (shop_id, category_id);

CREATE TABLE uploads (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  file_name    varchar(80) NOT NULL UNIQUE,
  mime_type    varchar(30) NOT NULL,
  size_bytes   int NOT NULL CHECK (size_bytes <= 5242880),
  shop_id      uuid NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  storage_key  text NOT NULL,
  uploaded_by  uuid REFERENCES users(id) ON DELETE SET NULL,
  created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX uploads_shop_idx ON uploads (shop_id);
