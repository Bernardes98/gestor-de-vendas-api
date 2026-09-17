CREATE TABLE public.clientes (
                                 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                 nome varchar(150) NOT NULL,
                                 razao_social varchar(150),
                                 cpf_cnpj varchar(30),
                                 telefone varchar(30),
                                 whatsapp varchar(30),
                                 endereco text,
                                 cidade varchar(100),
                                 taxa_padrao numeric(10,4) DEFAULT 0,
                                 observacoes text,
                                 ativo boolean NOT NULL DEFAULT true,
                                 created_at timestamptz NOT NULL DEFAULT now(),
                                 empresa_id uuid NOT NULL REFERENCES public.empresas(id) ON DELETE RESTRICT,
                                 pedido_token uuid NOT NULL DEFAULT gen_random_uuid()
);
CREATE UNIQUE INDEX uq_clientes_pedido_token ON public.clientes(pedido_token);
CREATE INDEX idx_clientes_empresa_nome ON public.clientes(empresa_id, nome);

CREATE TABLE public.produto_grupos (
                                       id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                       empresa_id uuid NOT NULL REFERENCES public.empresas(id) ON DELETE CASCADE,
                                       nome text NOT NULL,
                                       ordem integer NOT NULL DEFAULT 1,
                                       created_at timestamptz NOT NULL DEFAULT now(),
                                       updated_at timestamptz NOT NULL DEFAULT now(),
                                       CONSTRAINT produto_grupos_id_empresa_id_key UNIQUE (id, empresa_id),
                                       CONSTRAINT produto_grupos_nome_check CHECK (length(btrim(nome)) > 0),
                                       CONSTRAINT produto_grupos_ordem_check CHECK (ordem > 0)
);
CREATE UNIQUE INDEX uq_produto_grupos_empresa_nome_ci
    ON public.produto_grupos(empresa_id, lower(btrim(nome)));
CREATE INDEX idx_produto_grupos_empresa_ordem
    ON public.produto_grupos(empresa_id, ordem, nome);

CREATE TABLE public.produtos (
                                 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                 nome varchar(150) NOT NULL,
                                 marca varchar(100),
                                 codigo varchar(50),
                                 descricao text,
                                 custo numeric(14,2) NOT NULL DEFAULT 0,
                                 preco_padrao numeric(14,2) NOT NULL DEFAULT 0,
                                 estoque numeric(14,3) NOT NULL DEFAULT 0,
                                 estoque_minimo numeric(14,3) NOT NULL DEFAULT 0,
                                 ativo boolean NOT NULL DEFAULT true,
                                 created_at timestamptz NOT NULL DEFAULT now(),
                                 controla_estoque boolean NOT NULL DEFAULT false,
                                 empresa_id uuid NOT NULL REFERENCES public.empresas(id) ON DELETE RESTRICT,
                                 grupo_id uuid,
                                 CONSTRAINT produtos_custo_check CHECK (custo >= 0),
                                 CONSTRAINT produtos_preco_padrao_check CHECK (preco_padrao >= 0),
                                 CONSTRAINT produtos_estoque_check CHECK (estoque >= 0),
                                 CONSTRAINT produtos_estoque_minimo_check CHECK (estoque_minimo >= 0),
                                 CONSTRAINT produtos_grupo_empresa_fk
                                     FOREIGN KEY (grupo_id, empresa_id)
                                         REFERENCES public.produto_grupos(id, empresa_id)
                                         ON DELETE SET NULL (grupo_id)
);
CREATE INDEX idx_produtos_empresa_nome ON public.produtos(empresa_id, nome);
CREATE INDEX idx_produtos_empresa_grupo ON public.produtos(empresa_id, grupo_id);

CREATE TABLE public.produto_fotos (
                                      id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                      empresa_id uuid NOT NULL REFERENCES public.empresas(id) ON DELETE CASCADE,
                                      produto_id uuid NOT NULL REFERENCES public.produtos(id) ON DELETE CASCADE,
                                      storage_path text NOT NULL UNIQUE,
                                      url text NOT NULL,
                                      ordem integer NOT NULL,
                                      created_at timestamptz NOT NULL DEFAULT now(),
                                      CONSTRAINT produto_fotos_ordem_check CHECK (ordem >= 1),
                                      CONSTRAINT produto_fotos_um_por_produto UNIQUE (produto_id)
);
CREATE INDEX idx_produto_fotos_produto
    ON public.produto_fotos(empresa_id, produto_id, ordem);

CREATE TABLE public.cliente_produto_preco (
                                              id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                              cliente_id uuid NOT NULL REFERENCES public.clientes(id) ON DELETE CASCADE,
                                              produto_id uuid NOT NULL REFERENCES public.produtos(id) ON DELETE CASCADE,
                                              tipo varchar(20) NOT NULL DEFAULT 'taxa',
                                              taxa numeric(10,4),
                                              preco_fixo numeric(14,2),
                                              created_at timestamptz NOT NULL DEFAULT now(),
                                              empresa_id uuid NOT NULL REFERENCES public.empresas(id) ON DELETE RESTRICT,
                                              CONSTRAINT cliente_produto_unico UNIQUE (cliente_id, produto_id),
                                              CONSTRAINT tipo_preco_valido CHECK (tipo IN ('taxa','preco_fixo'))
);
CREATE INDEX idx_precos_empresa ON public.cliente_produto_preco(empresa_id);

CREATE TABLE public.cliente_produtos_ocultos (
                                                 empresa_id uuid NOT NULL REFERENCES public.empresas(id) ON DELETE CASCADE,
                                                 cliente_id uuid NOT NULL REFERENCES public.clientes(id) ON DELETE CASCADE,
                                                 produto_id uuid NOT NULL REFERENCES public.produtos(id) ON DELETE CASCADE,
                                                 created_at timestamptz NOT NULL DEFAULT now(),
                                                 CONSTRAINT cliente_produtos_ocultos_pkey PRIMARY KEY (cliente_id, produto_id)
);
CREATE INDEX idx_cliente_produtos_ocultos_empresa_cliente
    ON public.cliente_produtos_ocultos(empresa_id, cliente_id);