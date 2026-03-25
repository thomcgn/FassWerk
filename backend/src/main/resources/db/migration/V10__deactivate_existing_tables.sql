-- Existing table records are deactivated so teams can re-create their live layout.
UPDATE tables
SET active = false,
    status = 'FREE',
    updated_at = now()
WHERE active = true;

