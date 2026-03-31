-- Fix searchArchive JPA query: ensure tables.name is VARCHAR to allow lower() function
-- and fix similar issues in table_order_items if they exist

DO $$
BEGIN
    -- Explicitly convert tables.name from BYTEA to VARCHAR if still needed
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
        RAISE NOTICE 'Converted tables.name from BYTEA to VARCHAR';
    END IF;
END $$;

-- Ensure all search-relevant string columns are VARCHAR, not BYTEA
DO $$
BEGIN
    -- Check and fix any other BYTEA columns that should be text
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'tables'
          AND column_name = 'area'
          AND data_type = 'bytea'
    ) THEN
        ALTER TABLE tables
            ALTER COLUMN area TYPE varchar(32)
            USING convert_from(area, 'UTF8');
        RAISE NOTICE 'Converted tables.area from BYTEA to VARCHAR';
    END IF;
END $$;

