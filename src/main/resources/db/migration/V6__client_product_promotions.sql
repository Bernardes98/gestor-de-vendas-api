CREATE TABLE IF NOT EXISTS cliente_produto_promocao (
    id UUID PRIMARY KEY,
    empresa_id UUID NOT NULL REFERENCES empresas(id) ON DELETE CASCADE,
    cliente_id UUID NOT NULL REFERENCES clientes(id) ON DELETE CASCADE,
    produto_id UUID NOT NULL REFERENCES produtos(id) ON DELETE CASCADE,
    preco_promocional NUMERIC(14,2) NOT NULL CHECK (preco_promocional >= 0),
    quantidade_minima NUMERIC(14,3) NOT NULL DEFAULT 1 CHECK (quantidade_minima > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_cliente_produto_promocao UNIQUE (empresa_id, cliente_id, produto_id)
);
CREATE INDEX IF NOT EXISTS idx_cliente_produto_promocao_cliente ON cliente_produto_promocao (empresa_id, cliente_id);
