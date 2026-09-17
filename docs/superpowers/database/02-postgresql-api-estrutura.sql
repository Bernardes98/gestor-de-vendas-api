-- Gestor de Vendas - Estrutura PostgreSQL para a API
-- Gerado a partir do schema de negócio atual do Supabase.
-- SOMENTE ESTRUTURA: este arquivo não contém dados.

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE SCHEMA IF NOT EXISTS api_internal;

CREATE TABLE IF NOT EXISTS public.empresas (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  slug text NOT NULL UNIQUE,
  nome_fantasia text NOT NULL,
  razao_social text,
  cpf_cnpj text,
  telefone text,
  email text,
  endereco text,
  cidade text,
  logo_url text,
  cor_primaria text NOT NULL DEFAULT '#f59e0b',
  cor_secundaria text NOT NULL DEFAULT '#101827',
  ativa boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT empresas_cor_primaria_check CHECK (cor_primaria ~ '^#[0-9A-Fa-f]{6}$'),
  CONSTRAINT empresas_cor_secundaria_check CHECK (cor_secundaria ~ '^#[0-9A-Fa-f]{6}$')
);

CREATE TABLE IF NOT EXISTS public.empresa_usuarios (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  empresa_id uuid NOT NULL REFERENCES public.empresas(id) ON DELETE CASCADE,
  user_id uuid NOT NULL,
  perfil text NOT NULL DEFAULT 'SELLER',
  ativo boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT empresa_usuarios_perfil_check CHECK (perfil IN ('OWNER','ADMIN','MANAGER','SELLER')),
  CONSTRAINT empresa_usuarios_unique UNIQUE (empresa_id, user_id)
);

CREATE TABLE IF NOT EXISTS public.clientes (
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
CREATE UNIQUE INDEX IF NOT EXISTS uq_clientes_pedido_token ON public.clientes(pedido_token);
CREATE INDEX IF NOT EXISTS idx_clientes_empresa_nome ON public.clientes(empresa_id, nome);

CREATE TABLE IF NOT EXISTS public.produto_grupos (
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
CREATE UNIQUE INDEX IF NOT EXISTS uq_produto_grupos_empresa_nome_ci ON public.produto_grupos(empresa_id, lower(nome));
CREATE INDEX IF NOT EXISTS idx_produto_grupos_empresa_ordem ON public.produto_grupos(empresa_id, ordem);

CREATE TABLE IF NOT EXISTS public.produtos (
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
  CONSTRAINT produtos_grupo_empresa_fk FOREIGN KEY (grupo_id, empresa_id)
    REFERENCES public.produto_grupos(id, empresa_id) ON DELETE SET NULL (grupo_id)
);
CREATE INDEX IF NOT EXISTS idx_produtos_empresa_nome ON public.produtos(empresa_id, nome);
CREATE INDEX IF NOT EXISTS idx_produtos_empresa_grupo ON public.produtos(empresa_id, grupo_id);

CREATE TABLE IF NOT EXISTS public.produto_fotos (
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
CREATE INDEX IF NOT EXISTS idx_produto_fotos_produto ON public.produto_fotos(empresa_id, produto_id, ordem);

CREATE TABLE IF NOT EXISTS public.cliente_produto_preco (
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
CREATE INDEX IF NOT EXISTS idx_precos_empresa ON public.cliente_produto_preco(empresa_id);

CREATE TABLE IF NOT EXISTS public.cliente_produtos_ocultos (
  empresa_id uuid NOT NULL REFERENCES public.empresas(id) ON DELETE CASCADE,
  cliente_id uuid NOT NULL REFERENCES public.clientes(id) ON DELETE CASCADE,
  produto_id uuid NOT NULL REFERENCES public.produtos(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT cliente_produtos_ocultos_pkey PRIMARY KEY (cliente_id, produto_id)
);
CREATE INDEX IF NOT EXISTS idx_cliente_produtos_ocultos_empresa_cliente ON public.cliente_produtos_ocultos(empresa_id, cliente_id);

CREATE TABLE IF NOT EXISTS public.compras (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  fornecedor varchar(150),
  data_compra date NOT NULL DEFAULT CURRENT_DATE,
  total numeric(14,2) NOT NULL DEFAULT 0,
  observacoes text,
  created_at timestamptz NOT NULL DEFAULT now(),
  empresa_id uuid NOT NULL REFERENCES public.empresas(id) ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_compras_empresa_data ON public.compras(empresa_id, data_compra DESC);

CREATE TABLE IF NOT EXISTS public.compra_itens (
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
CREATE INDEX IF NOT EXISTS idx_compra_itens_compra ON public.compra_itens(empresa_id, compra_id);

CREATE TABLE IF NOT EXISTS public.vendas (
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
CREATE UNIQUE INDEX IF NOT EXISTS uq_vendas_empresa_numero ON public.vendas(empresa_id, numero_empresa);
CREATE INDEX IF NOT EXISTS idx_vendas_empresa ON public.vendas(empresa_id);
CREATE INDEX IF NOT EXISTS idx_vendas_empresa_status_pagamento ON public.vendas(empresa_id, status_pagamento);

CREATE TABLE IF NOT EXISTS public.venda_itens (
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
CREATE INDEX IF NOT EXISTS idx_venda_itens_venda ON public.venda_itens(empresa_id, venda_id);

CREATE TABLE IF NOT EXISTS public.venda_recebimentos (
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
CREATE INDEX IF NOT EXISTS idx_venda_recebimentos_venda ON public.venda_recebimentos(empresa_id, venda_id, data_recebimento);

CREATE TABLE IF NOT EXISTS public.recebiveis_manuais (
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
CREATE INDEX IF NOT EXISTS idx_recebiveis_manuais_empresa_status ON public.recebiveis_manuais(empresa_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS public.recebivel_manual_pagamentos (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  empresa_id uuid NOT NULL REFERENCES public.empresas(id) ON DELETE CASCADE,
  recebivel_id uuid NOT NULL REFERENCES public.recebiveis_manuais(id) ON DELETE CASCADE,
  valor numeric(14,2) NOT NULL,
  data_recebimento date NOT NULL DEFAULT CURRENT_DATE,
  observacoes text,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT recebivel_manual_pagamentos_valor_check CHECK (valor > 0)
);
CREATE INDEX IF NOT EXISTS idx_recebivel_manual_pagamentos_recebivel ON public.recebivel_manual_pagamentos(empresa_id, recebivel_id, data_recebimento);

CREATE TABLE IF NOT EXISTS public.pedidos_cliente (
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
  CONSTRAINT pedidos_cliente_status_check CHECK (status IN ('PENDENTE','CONVERTIDO','RECUSADO'))
);
CREATE INDEX IF NOT EXISTS idx_pedidos_cliente_empresa_status_created ON public.pedidos_cliente(empresa_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pedidos_cliente_cliente_created ON public.pedidos_cliente(empresa_id, cliente_id, created_at DESC);

CREATE TABLE IF NOT EXISTS public.pedido_cliente_itens (
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
CREATE INDEX IF NOT EXISTS idx_pedido_cliente_itens_pedido ON public.pedido_cliente_itens(empresa_id, pedido_id);

CREATE TABLE IF NOT EXISTS public.plataforma_administradores (
  user_id uuid PRIMARY KEY,
  ativo boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.profiles (
  id uuid PRIMARY KEY,
  full_name text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  role text NOT NULL DEFAULT 'owner',
  student_id uuid,
  owner_id uuid,
  CONSTRAINT profiles_role_check CHECK (role IN ('owner','student'))
);

-- Backend-only identity and operational metadata.
CREATE TABLE IF NOT EXISTS api_internal.usuarios (
  id uuid PRIMARY KEY,
  email varchar(254) NOT NULL,
  password_hash varchar(100),
  nome varchar(180),
  ativo boolean NOT NULL DEFAULT true,
  platform_admin boolean NOT NULL DEFAULT false,
  must_reset_password boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_api_internal_usuarios_email_ci ON api_internal.usuarios(lower(email));

CREATE TABLE IF NOT EXISTS api_internal.refresh_tokens (
  id uuid PRIMARY KEY,
  usuario_id uuid NOT NULL REFERENCES api_internal.usuarios(id),
  token_hash varchar(64) NOT NULL UNIQUE,
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz,
  replaced_by_id uuid REFERENCES api_internal.refresh_tokens(id),
  user_agent varchar(500),
  ip_address varchar(64),
  created_at timestamptz NOT NULL DEFAULT now(),
  last_used_at timestamptz
);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_usuario_active
  ON api_internal.refresh_tokens(usuario_id, expires_at) WHERE revoked_at IS NULL;

CREATE TABLE IF NOT EXISTS api_internal.password_reset_tokens (
  id uuid PRIMARY KEY,
  usuario_id uuid NOT NULL REFERENCES api_internal.usuarios(id),
  token_hash varchar(64) NOT NULL UNIQUE,
  expires_at timestamptz NOT NULL,
  used_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS api_internal.convites_empresa (
  id uuid PRIMARY KEY,
  empresa_id uuid NOT NULL REFERENCES public.empresas(id),
  owner_email varchar(254) NOT NULL,
  token_hash varchar(64) NOT NULL UNIQUE,
  expires_at timestamptz NOT NULL,
  used_at timestamptz,
  cancelled_at timestamptz,
  created_by uuid NOT NULL REFERENCES api_internal.usuarios(id),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS api_internal.convites_usuario (
  id uuid PRIMARY KEY,
  empresa_id uuid NOT NULL REFERENCES public.empresas(id),
  email varchar(254) NOT NULL,
  perfil varchar(20) NOT NULL,
  token_hash varchar(64) NOT NULL UNIQUE,
  expires_at timestamptz NOT NULL,
  used_at timestamptz,
  cancelled_at timestamptz,
  created_by uuid NOT NULL REFERENCES api_internal.usuarios(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT convites_usuario_perfil_check CHECK (perfil IN ('ADMIN','MANAGER','SELLER'))
);

CREATE TABLE IF NOT EXISTS api_internal.auditoria (
  id uuid PRIMARY KEY,
  empresa_id uuid REFERENCES public.empresas(id),
  usuario_id uuid REFERENCES api_internal.usuarios(id),
  acao varchar(80) NOT NULL,
  entidade_tipo varchar(80),
  entidade_id uuid,
  motivo varchar(500),
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS api_internal.compra_estados (
  compra_id uuid PRIMARY KEY,
  status varchar(20) NOT NULL DEFAULT 'ATIVA',
  cancelled_at timestamptz,
  cancelled_by uuid REFERENCES api_internal.usuarios(id),
  CONSTRAINT compra_estados_status_check CHECK (status IN ('ATIVA','CANCELADA'))
);

CREATE TABLE IF NOT EXISTS api_internal.movimentacoes_estoque (
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
  CONSTRAINT ck_mov_estoque_tipo CHECK (tipo IN ('COMPRA','COMPRA_REVERSAO','VENDA','VENDA_REVERSAO','AJUSTE_ENTRADA','AJUSTE_SAIDA')),
  CONSTRAINT ck_mov_estoque_saldo CHECK (saldo_apos >= 0),
  CONSTRAINT ck_mov_estoque_delta CHECK (quantidade_delta <> 0)
);

CREATE TABLE IF NOT EXISTS api_internal.venda_sequencias (
  empresa_id uuid PRIMARY KEY REFERENCES public.empresas(id),
  ultimo_numero bigint NOT NULL DEFAULT 0,
  CONSTRAINT ck_venda_sequencias_numero CHECK (ultimo_numero >= 0)
);

-- Índices técnicos usados pela API.
CREATE INDEX IF NOT EXISTS idx_auditoria_empresa_created ON api_internal.auditoria(empresa_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_mov_estoque_empresa_produto_data ON api_internal.movimentacoes_estoque(empresa_id, produto_id, created_at DESC);

-- Relacionamentos entre o legado do Supabase e a identidade da API.
ALTER TABLE public.empresa_usuarios
  ADD CONSTRAINT empresa_usuarios_api_user_fk
  FOREIGN KEY (user_id) REFERENCES api_internal.usuarios(id) ON DELETE CASCADE;

ALTER TABLE public.plataforma_administradores
  ADD CONSTRAINT plataforma_administradores_api_user_fk
  FOREIGN KEY (user_id) REFERENCES api_internal.usuarios(id) ON DELETE CASCADE;

ALTER TABLE public.profiles
  ADD CONSTRAINT profiles_api_user_fk
  FOREIGN KEY (id) REFERENCES api_internal.usuarios(id) ON DELETE CASCADE;

ALTER TABLE public.profiles
  ADD CONSTRAINT profiles_owner_api_user_fk
  FOREIGN KEY (owner_id) REFERENCES api_internal.usuarios(id) ON DELETE CASCADE;

ALTER TABLE api_internal.compra_estados
  ADD CONSTRAINT compra_estados_compra_fk
  FOREIGN KEY (compra_id) REFERENCES public.compras(id) ON DELETE CASCADE;
