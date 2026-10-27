-- Starting list of shop types and their default product categories
-- (docs/API_AND_DATABASE.md §3 seed). New shops copy their type's categories.

INSERT INTO shop_types (code, label, size_kind, sort_order) VALUES
  ('couture',  'ម៉ូដសម្លៀកបំពាក់',        'clothing', 1),
  ('jewelry',  'គ្រឿងអលង្ការ',            'none',     2),
  ('ceramics', 'សេរ៉ាមិច និងវត្ថុតុបតែង',   'none',     3),
  ('leather',  'ផលិតផលស្បែក',            'none',     4);

INSERT INTO shop_type_categories (shop_type_id, code, label, sort_order)
SELECT t.id, c.code, c.label, c.sort_order
FROM shop_types t
JOIN (VALUES
  ('couture',  'women',        'នារី',            1),
  ('couture',  'men',          'បុរស',            2),
  ('couture',  'accessories',  'គ្រឿងបន្លាស់',      3),
  ('couture',  'best-sellers', 'លក់ដាច់បំផុត',     99),
  ('jewelry',  'rings',        'ចិញ្ចៀន',          1),
  ('jewelry',  'necklaces',    'ខ្សែក',            2),
  ('jewelry',  'earrings',     'ក្រវិល',           3),
  ('jewelry',  'bracelets',    'ខ្សែដៃ',           4),
  ('jewelry',  'best-sellers', 'លក់ដាច់បំផុត',     99),
  ('ceramics', 'tableware',    'ចាន និងចានគោម',    1),
  ('ceramics', 'cups',         'ពែង និងកែវ',       2),
  ('ceramics', 'vases',        'ថូផ្កា',           3),
  ('ceramics', 'decor',        'វត្ថុតុបតែង',       4),
  ('ceramics', 'best-sellers', 'លក់ដាច់បំផុត',     99),
  ('leather',  'handbags',     'កាបូបដៃ',          1),
  ('leather',  'wallets',      'កាបូបលុយ',         2),
  ('leather',  'belts',        'ខ្សែក្រវាត់',        3),
  ('leather',  'shoes',        'ស្បែកជើង',         4),
  ('leather',  'best-sellers', 'លក់ដាច់បំផុត',     99)
) AS c(type_code, code, label, sort_order) ON c.type_code = t.code;
