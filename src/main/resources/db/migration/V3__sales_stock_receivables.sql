create table compras (
  id uuid primary key,
  empresa_id uuid not null references empresas(id),
  data_compra timestamptz not null default now(),
  observacoes varchar(1000),
  status varchar(20) not null default 'ATIVA',
  created_by uuid not null references usuarios(id),
  cancelled_at timestamptz,
  cancelled_by uuid references usuarios(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint uq_compras_id_empresa unique (id, empresa_id),
  constraint ck_compras_status check (status in ('ATIVA','CANCELADA'))
);
create index idx_compras_empresa_data on compras (empresa_id, data_compra desc);

create table compra_itens (
  id uuid primary key,
  empresa_id uuid not null references empresas(id),
  compra_id uuid not null,
  produto_id uuid not null,
  quantidade numeric(14,3) not null,
  custo_unitario numeric(14,2) not null,
  movimenta_estoque boolean not null,
  created_at timestamptz not null default now(),
  constraint ck_compra_itens_quantidade check (quantidade > 0),
  constraint ck_compra_itens_custo check (custo_unitario >= 0),
  constraint fk_compra_itens_compra_empresa foreign key (compra_id, empresa_id)
    references compras(id, empresa_id),
  constraint fk_compra_itens_produto_empresa foreign key (produto_id, empresa_id)
    references produtos(id, empresa_id)
);
create index idx_compra_itens_compra on compra_itens (empresa_id, compra_id);

create table movimentacoes_estoque (
  id uuid primary key,
  empresa_id uuid not null references empresas(id),
  produto_id uuid not null,
  tipo varchar(30) not null,
  quantidade_delta numeric(14,3) not null,
  saldo_apos numeric(14,3) not null,
  referencia_tipo varchar(30),
  referencia_id uuid,
  motivo varchar(500),
  created_by uuid not null references usuarios(id),
  created_at timestamptz not null default now(),
  constraint ck_mov_estoque_tipo check (tipo in ('COMPRA','COMPRA_REVERSAO','VENDA','VENDA_REVERSAO','AJUSTE_ENTRADA','AJUSTE_SAIDA')),
  constraint ck_mov_estoque_saldo check (saldo_apos >= 0),
  constraint ck_mov_estoque_delta check (quantidade_delta <> 0),
  constraint fk_mov_estoque_produto_empresa foreign key (produto_id, empresa_id)
    references produtos(id, empresa_id)
);
create index idx_mov_estoque_empresa_produto_data on movimentacoes_estoque (empresa_id, produto_id, created_at desc);

create table venda_sequencias (
  empresa_id uuid primary key references empresas(id),
  ultimo_numero bigint not null default 0,
  constraint ck_venda_sequencias_numero check (ultimo_numero >= 0)
);

create table vendas (
  id uuid primary key,
  empresa_id uuid not null references empresas(id),
  numero bigint not null,
  cliente_id uuid,
  data_venda timestamptz not null default now(),
  forma_pagamento varchar(20) not null,
  status varchar(20) not null default 'ATIVA',
  total numeric(14,2) not null,
  custo_total numeric(14,2) not null,
  lucro_total numeric(14,2) not null,
  created_by uuid not null references usuarios(id),
  cancel_reason varchar(500),
  cancelled_at timestamptz,
  cancelled_by uuid references usuarios(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint uq_vendas_id_empresa unique (id, empresa_id),
  constraint uq_vendas_empresa_numero unique (empresa_id, numero),
  constraint ck_vendas_pagamento check (forma_pagamento in ('AVISTA','PRAZO')),
  constraint ck_vendas_status check (status in ('ATIVA','CANCELADA')),
  constraint ck_vendas_total check (total >= 0),
  constraint ck_vendas_custo check (custo_total >= 0),
  constraint fk_vendas_cliente_empresa foreign key (cliente_id, empresa_id)
    references clientes(id, empresa_id)
);
create index idx_vendas_empresa_data on vendas (empresa_id, data_venda desc);
create index idx_vendas_empresa_cliente on vendas (empresa_id, cliente_id);

create table venda_itens (
  id uuid primary key,
  empresa_id uuid not null references empresas(id),
  venda_id uuid not null,
  produto_id uuid not null,
  produto_nome varchar(180) not null,
  quantidade numeric(14,3) not null,
  preco_unitario numeric(14,2) not null,
  custo_unitario numeric(14,2) not null,
  total_linha numeric(14,2) not null,
  custo_linha numeric(14,2) not null,
  movimenta_estoque boolean not null,
  created_at timestamptz not null default now(),
  constraint ck_venda_itens_quantidade check (quantidade > 0),
  constraint ck_venda_itens_preco check (preco_unitario >= 0),
  constraint ck_venda_itens_custo check (custo_unitario >= 0),
  constraint fk_venda_itens_venda_empresa foreign key (venda_id, empresa_id)
    references vendas(id, empresa_id),
  constraint fk_venda_itens_produto_empresa foreign key (produto_id, empresa_id)
    references produtos(id, empresa_id)
);
create index idx_venda_itens_venda on venda_itens (empresa_id, venda_id);

create table venda_pagamentos (
  id uuid primary key,
  empresa_id uuid not null references empresas(id),
  venda_id uuid not null,
  valor numeric(14,2) not null,
  paid_at timestamptz not null default now(),
  created_by uuid not null references usuarios(id),
  constraint ck_venda_pagamentos_valor check (valor > 0),
  constraint fk_venda_pagamentos_venda_empresa foreign key (venda_id, empresa_id)
    references vendas(id, empresa_id)
);
create index idx_venda_pagamentos_venda on venda_pagamentos (empresa_id, venda_id, paid_at);

create table recebiveis_manuais (
  id uuid primary key,
  empresa_id uuid not null references empresas(id),
  cliente_id uuid,
  descricao varchar(500) not null,
  valor_total numeric(14,2) not null,
  status varchar(20) not null default 'ABERTO',
  created_by uuid not null references usuarios(id),
  settled_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint uq_recebiveis_manuais_id_empresa unique (id, empresa_id),
  constraint ck_recebiveis_manuais_valor check (valor_total > 0),
  constraint ck_recebiveis_manuais_status check (status in ('ABERTO','QUITADO')),
  constraint fk_recebiveis_manuais_cliente_empresa foreign key (cliente_id, empresa_id)
    references clientes(id, empresa_id)
);
create index idx_recebiveis_manuais_empresa_status on recebiveis_manuais (empresa_id, status, created_at desc);

create table recebivel_manual_pagamentos (
  id uuid primary key,
  empresa_id uuid not null references empresas(id),
  recebivel_id uuid not null,
  valor numeric(14,2) not null,
  paid_at timestamptz not null default now(),
  created_by uuid not null references usuarios(id),
  constraint ck_recebivel_manual_pagamentos_valor check (valor > 0),
  constraint fk_recebivel_manual_pagamentos_recebivel_empresa foreign key (recebivel_id, empresa_id)
    references recebiveis_manuais(id, empresa_id)
);
create index idx_recebivel_manual_pagamentos_recebivel on recebivel_manual_pagamentos (empresa_id, recebivel_id, paid_at);
