CREATE TABLE api_internal.estoque_lotes (
    id uuid PRIMARY KEY,
    empresa_id uuid NOT NULL REFERENCES public.empresas(id) ON DELETE CASCADE,
    produto_id uuid NOT NULL REFERENCES public.produtos(id) ON DELETE CASCADE,
    compra_id uuid REFERENCES public.compras(id) ON DELETE RESTRICT,
    quantidade_inicial numeric(14,3) NOT NULL,
    quantidade_restante numeric(14,3) NOT NULL,
    custo_unitario numeric(14,2) NOT NULL,
    data_entrada date NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_estoque_lotes_qtd CHECK (quantidade_inicial > 0 AND quantidade_restante >= 0 AND quantidade_restante <= quantidade_inicial),
    CONSTRAINT ck_estoque_lotes_custo CHECK (custo_unitario >= 0)
);
CREATE INDEX idx_estoque_lotes_fifo ON api_internal.estoque_lotes(empresa_id, produto_id, data_entrada, created_at, id) WHERE quantidade_restante > 0;

CREATE TABLE api_internal.venda_lote_consumos (
    id uuid PRIMARY KEY,
    empresa_id uuid NOT NULL REFERENCES public.empresas(id) ON DELETE CASCADE,
    venda_id uuid NOT NULL REFERENCES public.vendas(id) ON DELETE CASCADE,
    produto_id uuid NOT NULL REFERENCES public.produtos(id) ON DELETE RESTRICT,
    lote_id uuid NOT NULL REFERENCES api_internal.estoque_lotes(id) ON DELETE RESTRICT,
    quantidade numeric(14,3) NOT NULL,
    custo_unitario numeric(14,2) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_venda_lote_consumos_qtd CHECK (quantidade > 0)
);
CREATE INDEX idx_venda_lote_consumos_venda ON api_internal.venda_lote_consumos(empresa_id, venda_id);

-- Estoque que já existia antes do FIFO vira um lote inicial, sem alterar o saldo atual.
INSERT INTO api_internal.estoque_lotes
(id, empresa_id, produto_id, compra_id, quantidade_inicial, quantidade_restante, custo_unitario, data_entrada)
SELECT gen_random_uuid(), empresa_id, id, NULL, estoque, estoque, custo, CURRENT_DATE - 1
FROM public.produtos
WHERE controla_estoque = true AND estoque > 0;
