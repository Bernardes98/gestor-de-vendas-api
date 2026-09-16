ALTER TABLE clientes ADD COLUMN pedido_token varchar(128);
UPDATE clientes
SET pedido_token = replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', '')
WHERE pedido_token IS NULL;
ALTER TABLE clientes ALTER COLUMN pedido_token SET NOT NULL;
CREATE UNIQUE INDEX uq_clientes_pedido_token ON clientes (pedido_token);

ALTER TABLE produtos ADD COLUMN marca varchar(120);
ALTER TABLE produtos ADD COLUMN estoque_minimo numeric(14,3) NOT NULL DEFAULT 0;
ALTER TABLE produtos ADD CONSTRAINT ck_produtos_estoque_minimo CHECK (estoque_minimo >= 0);

CREATE TABLE cliente_produto_oculto (
  id uuid PRIMARY KEY,
  empresa_id uuid NOT NULL REFERENCES empresas(id),
  cliente_id uuid NOT NULL,
  produto_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_cliente_produto_oculto UNIQUE (empresa_id, cliente_id, produto_id),
  CONSTRAINT fk_cliente_oculto_cliente_empresa FOREIGN KEY (cliente_id, empresa_id)
    REFERENCES clientes(id, empresa_id) ON DELETE CASCADE,
  CONSTRAINT fk_cliente_oculto_produto_empresa FOREIGN KEY (produto_id, empresa_id)
    REFERENCES produtos(id, empresa_id) ON DELETE CASCADE
);
CREATE INDEX idx_cliente_produto_oculto_cliente ON cliente_produto_oculto (empresa_id, cliente_id);

CREATE TABLE pedidos_cliente (
  id uuid PRIMARY KEY,
  empresa_id uuid NOT NULL REFERENCES empresas(id),
  cliente_id uuid NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'PENDENTE',
  visualizado_em timestamptz,
  observacoes varchar(1000),
  total numeric(14,2) NOT NULL,
  venda_id uuid,
  conversao_usuario_id uuid REFERENCES usuarios(id),
  conversao_iniciada_em timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_pedidos_cliente_id_empresa UNIQUE (id, empresa_id),
  CONSTRAINT ck_pedidos_cliente_status CHECK (status IN ('PENDENTE','CONVERTIDO','RECUSADO')),
  CONSTRAINT ck_pedidos_cliente_total CHECK (total >= 0),
  CONSTRAINT fk_pedidos_cliente_cliente_empresa FOREIGN KEY (cliente_id, empresa_id)
    REFERENCES clientes(id, empresa_id),
  CONSTRAINT fk_pedidos_cliente_venda_empresa FOREIGN KEY (venda_id, empresa_id)
    REFERENCES vendas(id, empresa_id)
);
CREATE INDEX idx_pedidos_cliente_empresa_status_created ON pedidos_cliente (empresa_id, status, created_at DESC);
CREATE INDEX idx_pedidos_cliente_cliente_created ON pedidos_cliente (empresa_id, cliente_id, created_at DESC);

CREATE TABLE pedido_cliente_itens (
  id uuid PRIMARY KEY,
  empresa_id uuid NOT NULL REFERENCES empresas(id),
  pedido_id uuid NOT NULL,
  produto_id uuid NOT NULL,
  produto_nome varchar(180) NOT NULL,
  quantidade numeric(14,3) NOT NULL,
  preco_unitario numeric(14,2) NOT NULL,
  total_linha numeric(14,2) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_pedido_cliente_itens_quantidade CHECK (quantidade > 0),
  CONSTRAINT ck_pedido_cliente_itens_preco CHECK (preco_unitario >= 0),
  CONSTRAINT ck_pedido_cliente_itens_total CHECK (total_linha >= 0),
  CONSTRAINT fk_pedido_cliente_itens_pedido_empresa FOREIGN KEY (pedido_id, empresa_id)
    REFERENCES pedidos_cliente(id, empresa_id) ON DELETE CASCADE,
  CONSTRAINT fk_pedido_cliente_itens_produto_empresa FOREIGN KEY (produto_id, empresa_id)
    REFERENCES produtos(id, empresa_id)
);
CREATE INDEX idx_pedido_cliente_itens_pedido ON pedido_cliente_itens (empresa_id, pedido_id);
