CREATE TABLE public.compras (
                                id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                fornecedor varchar(150),
                                data_compra date NOT NULL DEFAULT CURRENT_DATE,
                                total numeric(14,2) NOT NULL DEFAULT 0,
                                observacoes text,
                                created_at timestamptz NOT NULL DEFAULT now(),
                                empresa_id uuid NOT NULL REFERENCES public.empresas(id) ON DELETE RESTRICT
);
CREATE INDEX idx_compras_empresa_data ON public.compras(empresa_id, data_compra DESC);

CREATE TABLE public.compra_itens (
                                     id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                     compra_id uuid NOT NULL REFERENCES public.compras(id) ON DELETE CASCADE,
                                     produto_id uuid NOT NULL REFERENCES public.produtos(id),
                                     quantidade numeric(14,3) NOT NULL,
                                     custo_unitario numeric(14,2) NOT NULL,
                                     total numeric(14,2) NOT NULL,
                                     empresa_id uuid NOT NULL REFERENCES public.empresas(id) ON DELETE RESTRICT,
                                     CONSTRAINT compra_itens_quantidade_check CHECK (quantidade > 0),
                                     CONSTRAINT compra_itens_custo_check CHECK (custo_unitario >= 0),
                                     CONSTRAINT compra_itens_total_check CHECK (total >= 0)
);
CREATE INDEX idx_compra_itens_compra ON public.compra_itens(empresa_id, compra_id);

CREATE TABLE api_internal.compra_estados (
                                             compra_id uuid PRIMARY KEY REFERENCES public.compras(id) ON DELETE CASCADE,
                                             status varchar(20) NOT NULL DEFAULT 'ATIVA',
                                             cancelled_at timestamptz,
                                             cancelled_by uuid REFERENCES api_internal.usuarios(id),
                                             CONSTRAINT compra_estados_status_check CHECK (status IN ('ATIVA','CANCELADA'))
);

CREATE TABLE api_internal.movimentacoes_estoque (
                                                    id uuid PRIMARY KEY,
                                                    empresa_id uuid NOT NULL REFERENCES public.empresas(id),
                                                    produto_id uuid NOT NULL REFERENCES public.produtos(id),
                                                    tipo varchar(30) NOT NULL,
                                                    quantidade_delta numeric(14,3) NOT NULL,
                                                    saldo_apos numeric(14,3) NOT NULL,
                                                    referencia_tipo varchar(30),
                                                    referencia_id uuid,
                                                    motivo varchar(500),
                                                    created_by uuid NOT NULL REFERENCES api_internal.usuarios(id),
                                                    created_at timestamptz NOT NULL DEFAULT now(),
                                                    CONSTRAINT ck_mov_estoque_tipo
                                                        CHECK (tipo IN ('COMPRA','COMPRA_REVERSAO','VENDA','VENDA_REVERSAO','AJUSTE_ENTRADA','AJUSTE_SAIDA')),
                                                    CONSTRAINT ck_mov_estoque_saldo CHECK (saldo_apos >= 0),
                                                    CONSTRAINT ck_mov_estoque_delta CHECK (quantidade_delta <> 0)
);
CREATE INDEX idx_mov_estoque_empresa_produto_data
    ON api_internal.movimentacoes_estoque(empresa_id, produto_id, created_at DESC);

CREATE TABLE public.vendas (
                               id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                               numero bigint GENERATED ALWAYS AS IDENTITY,
                               cliente_id uuid REFERENCES public.clientes(id),
                               data_venda timestamptz NOT NULL DEFAULT now(),
                               total_custo numeric(14,2) NOT NULL DEFAULT 0,
                               total_venda numeric(14,2) NOT NULL DEFAULT 0,
                               lucro numeric(14,2) NOT NULL DEFAULT 0,
                               observacoes text,
                               created_at timestamptz NOT NULL DEFAULT now(),
                               cliente_nome text,
                               empresa_id uuid NOT NULL REFERENCES public.empresas(id) ON DELETE RESTRICT,
                               numero_empresa bigint NOT NULL,
                               status_pagamento text NOT NULL DEFAULT 'PAGO',
                               pago_em timestamptz,
                               CONSTRAINT vendas_status_pagamento_check CHECK (status_pagamento IN ('PAGO','A_RECEBER'))
);
CREATE UNIQUE INDEX uq_vendas_empresa_numero
    ON public.vendas(empresa_id, numero_empresa);
CREATE INDEX idx_vendas_empresa ON public.vendas(empresa_id);
CREATE INDEX idx_vendas_empresa_status_pagamento
    ON public.vendas(empresa_id, status_pagamento);

CREATE TABLE public.venda_itens (
                                    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                    venda_id uuid NOT NULL REFERENCES public.vendas(id) ON DELETE CASCADE,
                                    produto_id uuid NOT NULL REFERENCES public.produtos(id),
                                    quantidade numeric(14,3) NOT NULL,
                                    custo_unitario numeric(14,2) NOT NULL,
                                    preco_unitario numeric(14,2) NOT NULL,
                                    total_custo numeric(14,2) NOT NULL,
                                    total_venda numeric(14,2) NOT NULL,
                                    lucro numeric(14,2) NOT NULL,
                                    produto_nome text,
                                    empresa_id uuid NOT NULL REFERENCES public.empresas(id) ON DELETE RESTRICT,
                                    CONSTRAINT venda_itens_quantidade_check CHECK (quantidade > 0),
                                    CONSTRAINT venda_itens_preco_check CHECK (preco_unitario >= 0),
                                    CONSTRAINT venda_itens_custo_check CHECK (custo_unitario >= 0)
);
CREATE INDEX idx_venda_itens_venda ON public.venda_itens(empresa_id, venda_id);

CREATE TABLE public.venda_recebimentos (
                                           id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                           empresa_id uuid NOT NULL REFERENCES public.empresas(id) ON DELETE CASCADE,
                                           venda_id uuid NOT NULL REFERENCES public.vendas(id) ON DELETE CASCADE,
                                           valor numeric(14,2) NOT NULL,
                                           data_recebimento date NOT NULL DEFAULT CURRENT_DATE,
                                           observacoes text,
                                           created_by uuid,
                                           created_at timestamptz NOT NULL DEFAULT now(),
                                           CONSTRAINT venda_recebimentos_valor_check CHECK (valor > 0)
);
CREATE INDEX idx_venda_recebimentos_venda
    ON public.venda_recebimentos(empresa_id, venda_id, data_recebimento);

CREATE TABLE public.recebiveis_manuais (
                                           id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                           empresa_id uuid NOT NULL REFERENCES public.empresas(id) ON DELETE CASCADE,
                                           cliente_id uuid NOT NULL REFERENCES public.clientes(id) ON DELETE RESTRICT,
                                           valor_original numeric(14,2) NOT NULL,
                                           data_lancamento date NOT NULL DEFAULT CURRENT_DATE,
                                           observacoes text,
                                           status text NOT NULL DEFAULT 'ABERTO',
                                           quitado_em timestamptz,
                                           created_at timestamptz NOT NULL DEFAULT now(),
                                           CONSTRAINT recebiveis_manuais_status_check CHECK (status IN ('ABERTO','QUITADO')),
                                           CONSTRAINT recebiveis_manuais_valor_original_check CHECK (valor_original > 0)
);
CREATE INDEX idx_recebiveis_manuais_empresa_status
    ON public.recebiveis_manuais(empresa_id, status, created_at DESC);

CREATE TABLE public.recebivel_manual_pagamentos (
                                                    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                                    empresa_id uuid NOT NULL REFERENCES public.empresas(id) ON DELETE CASCADE,
                                                    recebivel_id uuid NOT NULL REFERENCES public.recebiveis_manuais(id) ON DELETE CASCADE,
                                                    valor numeric(14,2) NOT NULL,
                                                    data_recebimento date NOT NULL DEFAULT CURRENT_DATE,
                                                    observacoes text,
                                                    created_at timestamptz NOT NULL DEFAULT now(),
                                                    CONSTRAINT recebivel_manual_pagamentos_valor_check CHECK (valor > 0)
);
CREATE INDEX idx_recebivel_manual_pagamentos_recebivel
    ON public.recebivel_manual_pagamentos(empresa_id, recebivel_id, data_recebimento);

CREATE TABLE api_internal.venda_sequencias (
                                               empresa_id uuid PRIMARY KEY REFERENCES public.empresas(id),
                                               ultimo_numero bigint NOT NULL DEFAULT 0,
                                               CONSTRAINT ck_venda_sequencias_numero CHECK (ultimo_numero >= 0)
);