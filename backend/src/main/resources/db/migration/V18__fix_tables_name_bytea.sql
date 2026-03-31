-- Ensure legacy environments with a wrong bytea type can run case-insensitive table-name filters.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'tables'
          AND column_name = 'name'
          AND data_type = 'bytea'
    ) THEN
        ALTER TABLE tables
            ALTER COLUMN name TYPE varchar(128)
            USING convert_from(name, 'UTF8');
    END IF;
END $$;

