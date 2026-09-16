create table clientes (
  id uuid primary key,
  empresa_id uuid not null references empresas(id),
  nome varchar(180) not null,
  cpf_cnpj varchar(20),
  telefone varchar(30),
  email varchar(254),
  endereco varchar(255),
  cidade varchar(120),
  observacoes varchar(1000),
  ativo boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint uq_clientes_id_empresa unique (id, empresa_id)
);
create index idx_clientes_empresa_nome on clientes (empresa_id, nome);

create table categorias_produtos (
  id uuid primary key,
  empresa_id uuid not null references empresas(id),
  nome varchar(120) not null,
  ordem integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint uq_categorias_id_empresa unique (id, empresa_id),
  constraint ck_categorias_ordem check (ordem >= 0)
);
create unique index uq_categorias_empresa_nome_ci on categorias_produtos (empresa_id, lower(nome));
create index idx_categorias_empresa_ordem on categorias_produtos (empresa_id, ordem);

create table produtos (
  id uuid primary key,
  empresa_id uuid not null references empresas(id),
  categoria_id uuid not null,
  nome varchar(180) not null,
  codigo varchar(80),
  descricao varchar(1000),
  custo numeric(14,2) not null default 0,
  preco_venda numeric(14,2) not null,
  controlar_estoque boolean not null default false,
  estoque_atual numeric(14,3) not null default 0,
  ativo boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint uq_produtos_id_empresa unique (id, empresa_id),
  constraint ck_produtos_custo check (custo >= 0),
  constraint ck_produtos_preco_venda check (preco_venda >= 0),
  constraint ck_produtos_estoque check (estoque_atual >= 0),
  constraint fk_produtos_categoria_empresa foreign key (categoria_id, empresa_id)
    references categorias_produtos(id, empresa_id)
);
create index idx_produtos_empresa_nome on produtos (empresa_id, nome);
create index idx_produtos_empresa_categoria on produtos (empresa_id, categoria_id);

create table produto_fotos (
  id uuid primary key,
  empresa_id uuid not null references empresas(id),
  produto_id uuid not null,
  object_key varchar(500) not null unique,
  content_type varchar(100) not null,
  tamanho_bytes bigint not null,
  ordem integer not null default 0,
  created_at timestamptz not null default now(),
  constraint ck_produto_fotos_tamanho check (tamanho_bytes >= 0),
  constraint ck_produto_fotos_ordem check (ordem >= 0),
  constraint fk_produto_fotos_produto_empresa foreign key (produto_id, empresa_id)
    references produtos(id, empresa_id)
);
create index idx_produto_fotos_produto on produto_fotos (empresa_id, produto_id, ordem);

create table cliente_produto_preco (
  id uuid primary key,
  empresa_id uuid not null references empresas(id),
  cliente_id uuid not null,
  produto_id uuid not null,
  preco numeric(14,2) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint uq_cliente_produto_preco unique (empresa_id, cliente_id, produto_id),
  constraint ck_cliente_produto_preco check (preco >= 0),
  constraint fk_cliente_preco_cliente_empresa foreign key (cliente_id, empresa_id)
    references clientes(id, empresa_id),
  constraint fk_cliente_preco_produto_empresa foreign key (produto_id, empresa_id)
    references produtos(id, empresa_id)
);
create index idx_cliente_produto_preco_cliente on cliente_produto_preco (empresa_id, cliente_id);
