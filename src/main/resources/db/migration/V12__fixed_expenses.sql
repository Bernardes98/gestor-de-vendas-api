ALTER TABLE company_features DROP CONSTRAINT IF EXISTS ck_company_feature;
ALTER TABLE company_features ADD CONSTRAINT ck_company_feature CHECK (feature IN ('DASHBOARD','CLIENTS','PRODUCTS','PURCHASES','NEW_SALE','SALES','RECEIVABLES','ORDERS','ROUTES','REPORTS','CHARTS','SETTINGS','FIXED_EXPENSES'));

CREATE TABLE fixed_expenses (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES empresas(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    amount NUMERIC(15,2) NOT NULL CHECK (amount >= 0),
    due_day INTEGER NULL CHECK (due_day BETWEEN 1 AND 31),
    notes VARCHAR(500) NULL
);
CREATE INDEX idx_fixed_expenses_company ON fixed_expenses(company_id);
