-- Ensure old demo categories are not visible in existing databases.
update drink_categories
set active = false,
    updated_at = now()
where name in ('Beer', 'Softdrinks');

