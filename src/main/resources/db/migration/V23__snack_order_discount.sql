ALTER TABLE snack_orders ADD COLUMN discount NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE snack_orders ADD COLUMN discount_reason VARCHAR(500);
ALTER TABLE snack_orders ADD CONSTRAINT ck_snack_discount CHECK (discount >= 0 AND discount <= items_total + delivery_fee);
