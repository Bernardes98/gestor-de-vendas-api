CREATE TABLE public.pedidos_cliente (
                                        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                        empresa_id uuid NOT NULL REFERENCES public.empresas(id) ON DELETE CASCADE,
                                        cliente_id uuid NOT NULL REFERENCES public.clientes(id) ON DELETE RESTRICT,
                                        status text NOT NULL DEFAULT 'PENDENTE',
                                        visualizado_em timestamptz,
                                        observacoes text,
                                        total numeric(14,2) NOT NULL DEFAULT 0,
                                        venda_id uuid,
                                        conversao_por uuid,
                                        conversao_em timestamptz,
                                        created_at timestamptz NOT NULL DEFAULT now(),
                                        updated_at timestamptz NOT NULL DEFAULT now(),
                                        CONSTRAINT pedidos_cliente_status_check
                                            CHECK (status IN ('PENDENTE','CONVERTIDO','RECUSADO'))
);
CREATE INDEX idx_pedidos_cliente_empresa_status_created
    ON public.pedidos_cliente(empresa_id, status, created_at DESC);
CREATE INDEX idx_pedidos_cliente_cliente_created
    ON public.pedidos_cliente(empresa_id, cliente_id, created_at DESC);

-- venda_id fica sem FK propositalmente: o dump historico possui um pedido
-- convertido que referencia uma venda que ja nao existe mais no backup.

CREATE TABLE public.pedido_cliente_itens (
                                             id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                             pedido_id uuid NOT NULL REFERENCES public.pedidos_cliente(id) ON DELETE CASCADE,
                                             empresa_id uuid NOT NULL REFERENCES public.empresas(id) ON DELETE CASCADE,
                                             produto_id uuid NOT NULL REFERENCES public.produtos(id) ON DELETE RESTRICT,
                                             produto_nome text NOT NULL,
                                             quantidade numeric(14,3) NOT NULL,
                                             preco_unitario numeric(14,2) NOT NULL,
                                             total numeric(14,2) NOT NULL,
                                             created_at timestamptz NOT NULL DEFAULT now(),
                                             CONSTRAINT pedido_cliente_itens_pedido_id_produto_id_key UNIQUE (pedido_id, produto_id),
                                             CONSTRAINT pedido_cliente_itens_quantidade_check CHECK (quantidade > 0),
                                             CONSTRAINT pedido_cliente_itens_preco_unitario_check CHECK (preco_unitario >= 0),
                                             CONSTRAINT pedido_cliente_itens_total_check CHECK (total >= 0)
);
CREATE INDEX idx_pedido_cliente_itens_pedido
    ON public.pedido_cliente_itens(empresa_id, pedido_id);

CREATE TABLE public.plataforma_administradores (
                                                   user_id uuid PRIMARY KEY REFERENCES api_internal.usuarios(id) ON DELETE CASCADE,
                                                   ativo boolean NOT NULL DEFAULT true,
                                                   created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE public.profiles (
                                 id uuid PRIMARY KEY REFERENCES api_internal.usuarios(id) ON DELETE CASCADE,
                                 full_name text,
                                 created_at timestamptz NOT NULL DEFAULT now(),
                                 updated_at timestamptz NOT NULL DEFAULT now(),
                                 role text NOT NULL DEFAULT 'owner',
                                 student_id uuid,
                                 owner_id uuid REFERENCES api_internal.usuarios(id) ON DELETE CASCADE,
                                 CONSTRAINT profiles_role_check CHECK (role IN ('owner','student'))
);
CREATE INDEX idx_profiles_owner ON public.profiles(owner_id) WHERE owner_id IS NOT NULL;
CREATE UNIQUE INDEX uq_profiles_student ON public.profiles(student_id) WHERE student_id IS NOT NULL;