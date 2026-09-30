CREATE TABLE company_features (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES empresas(id) ON DELETE CASCADE,
    feature VARCHAR(30) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_company_feature UNIQUE (company_id, feature),
    CONSTRAINT ck_company_feature CHECK (feature IN ('ROUTES', 'CHARTS', 'REPORTS'))
);

CREATE INDEX idx_company_features_company ON company_features(company_id);
