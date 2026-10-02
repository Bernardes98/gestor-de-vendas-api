ALTER TABLE snack_orders ADD COLUMN IF NOT EXISTS payment_status VARCHAR(16) NOT NULL DEFAULT 'PAID';
ALTER TABLE snack_orders ADD COLUMN IF NOT EXISTS paid_at TIMESTAMPTZ;
UPDATE snack_orders SET paid_at=created_at WHERE payment_status='PAID' AND paid_at IS NULL;
