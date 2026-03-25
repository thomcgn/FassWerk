-- Remove capacity column from tables table
-- Making tables simpler: just name + area, no seats
ALTER TABLE tables DROP COLUMN IF EXISTS capacity;

