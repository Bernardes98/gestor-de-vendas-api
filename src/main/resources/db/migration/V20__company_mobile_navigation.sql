CREATE TABLE IF NOT EXISTS company_mobile_navigation (
  company_id UUID NOT NULL REFERENCES empresas(id) ON DELETE CASCADE,
  path VARCHAR(80) NOT NULL,
  position INTEGER NOT NULL,
  PRIMARY KEY (company_id, path),
  UNIQUE (company_id, position)
);
