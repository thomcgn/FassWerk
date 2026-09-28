-- Existing receipts retain their table classification and historical payment data.
ALTER TABLE table_orders ADD COLUMN sale_type varchar(16) NOT NULL DEFAULT 'TABLE';
ALTER TABLE table_orders ADD COLUMN payment_method varchar(16);
ALTER TABLE table_orders ALTER COLUMN table_id DROP NOT NULL;
ALTER TABLE table_orders ADD CONSTRAINT ck_order_sale_shape CHECK (
    (sale_type = 'TABLE' AND table_id IS NOT NULL)
    OR (sale_type = 'DIRECT' AND table_id IS NULL AND reservation_id IS NULL
        AND payment_method IS NOT NULL));
ALTER TABLE table_orders ADD CONSTRAINT ck_order_payment_method
    CHECK (payment_method IS NULL OR payment_method IN ('CASH', 'CARD'));

-- A cart may be assembled inside a transaction, but never committed unfinished.
CREATE FUNCTION assert_direct_sale_closed() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM table_orders WHERE id = NEW.id AND sale_type = 'DIRECT'
               AND (status <> 'CLOSED' OR NOT paid OR closed_at IS NULL OR closed_business_date IS NULL)) THEN
        RAISE EXCEPTION 'Direct sale must be paid and closed at commit' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER direct_sale_closed AFTER INSERT OR UPDATE ON table_orders
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION assert_direct_sale_closed();
