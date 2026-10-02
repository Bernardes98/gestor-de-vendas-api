ALTER TABLE company_features DROP CONSTRAINT IF EXISTS ck_company_feature;
ALTER TABLE company_features ADD CONSTRAINT ck_company_feature CHECK (feature IN ('DASHBOARD','CLIENTS','PRODUCTS','PURCHASES','NEW_SALE','SALES','RECEIVABLES','ORDERS','ROUTES','REPORTS','CHARTS','SETTINGS','FIXED_EXPENSES','PROMOTIONS','COURIERS','SNACKS','NOTES','KITCHEN','SNACK_COSTS','SNACK_STATS','CASH','BEVERAGES','TABLES'));
INSERT INTO company_features (id,company_id,feature,enabled)
SELECT gen_random_uuid(),e.id,'TABLES',false FROM empresas e WHERE NOT EXISTS (SELECT 1 FROM company_features cf WHERE cf.company_id=e.id AND cf.feature='TABLES');
CREATE TABLE dining_tables (id UUID PRIMARY KEY, company_id UUID NOT NULL REFERENCES empresas(id) ON DELETE CASCADE, number INTEGER NOT NULL, UNIQUE(company_id,number));
INSERT INTO dining_tables(id,company_id,number) SELECT gen_random_uuid(),e.id,n FROM empresas e CROSS JOIN generate_series(1,9) n;
ALTER TABLE snack_orders ADD COLUMN service_type VARCHAR(16) NOT NULL DEFAULT 'DELIVERY';
ALTER TABLE snack_orders ADD COLUMN table_number INTEGER;
ALTER TABLE snack_orders ADD COLUMN cancelled_at TIMESTAMPTZ;
ALTER TABLE snack_orders ADD COLUMN refunded_at TIMESTAMPTZ;
ALTER TABLE snack_orders ADD COLUMN settled_total NUMERIC(14,2);
ALTER TABLE snack_orders ADD COLUMN revision INTEGER NOT NULL DEFAULT 0;
UPDATE snack_orders SET settled_total=total WHERE payment_status='PAID';
CREATE INDEX idx_snack_table ON snack_orders(company_id,table_number,payment_status);
