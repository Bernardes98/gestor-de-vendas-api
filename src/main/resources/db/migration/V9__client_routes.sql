CREATE TABLE client_routes (
 id UUID PRIMARY KEY,
 company_id UUID NOT NULL REFERENCES empresas(id) ON DELETE CASCADE,
 client_id UUID NOT NULL REFERENCES clientes(id) ON DELETE CASCADE,
 route_day VARCHAR(12) NOT NULL,
 position INTEGER NOT NULL,
 CONSTRAINT ck_client_routes_day CHECK (route_day IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')),
 CONSTRAINT uk_client_route_day UNIQUE (company_id, route_day, client_id)
);
CREATE INDEX idx_client_routes_company_day_position ON client_routes(company_id, route_day, position);
