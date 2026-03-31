-- =============================================================================
-- V20__inventory_soft_delete_with_unlink.sql
-- Implement soft delete strategy: instead of hard delete, unlink drinks and deactivate
-- This prevents foreign key violations and allows UI to show "unlinked items" warning
-- =============================================================================

-- No schema changes needed - the code now uses soft delete (set linkedDrinkId=NULL, active=false)
-- This migration serves as documentation that we switched to soft delete pattern

-- Verification: Check for any inactive items (should be empty initially)
-- SELECT * FROM inventory_items WHERE active = false;

