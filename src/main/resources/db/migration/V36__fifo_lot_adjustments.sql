
CREATE TABLE api_internal.estoque_lote_ajustes (
                                                   id uuid PRIMARY KEY,

                                                   empresa_id uuid NOT NULL
                                                       REFERENCES public.empresas(id) ON DELETE CASCADE,

                                                   produto_id uuid NOT NULL
                                                       REFERENCES public.produtos(id) ON DELETE RESTRICT,

                                                   lote_id uuid NOT NULL
                                                       REFERENCES api_internal.estoque_lotes(id) ON DELETE RESTRICT,

                                                   movimentacao_id uuid
                                                       REFERENCES api_internal.movimentacoes_estoque(id)
                                                           ON DELETE RESTRICT,

                                                   regularizacao_id uuid,

                                                   origem varchar(40) NOT NULL,
                                                   quantidade_delta numeric(14,3) NOT NULL,
                                                   motivo varchar(500) NOT NULL,

                                                   created_at timestamptz NOT NULL DEFAULT now(),

                                                   CONSTRAINT ck_estoque_lote_ajustes_delta
                                                       CHECK (quantidade_delta <> 0),

                                                   CONSTRAINT ck_estoque_lote_ajustes_origem
                                                       CHECK (
                                                           (origem = 'AJUSTE_MANUAL'
                                                               AND movimentacao_id IS NOT NULL
                                                               AND regularizacao_id IS NULL)
                                                               OR
                                                           (origem = 'REGULARIZACAO_HISTORICA'
                                                               AND movimentacao_id IS NULL
                                                               AND regularizacao_id IS NOT NULL)
                                                           ),

                                                   CONSTRAINT uq_estoque_lote_ajustes_movimentacao_lote
                                                       UNIQUE (movimentacao_id, lote_id),

                                                   CONSTRAINT uq_estoque_lote_ajustes_regularizacao_lote
                                                       UNIQUE (regularizacao_id, lote_id)
);

CREATE INDEX idx_estoque_lote_ajustes_empresa_produto
    ON api_internal.estoque_lote_ajustes (
                                          empresa_id, produto_id, created_at
        );

CREATE INDEX idx_estoque_lote_ajustes_lote
    ON api_internal.estoque_lote_ajustes (lote_id);
