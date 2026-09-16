CREATE TABLE empresas (
  id uuid PRIMARY KEY,
  slug varchar(120) NOT NULL UNIQUE,
  nome_fantasia varchar(180) NOT NULL,
  razao_social varchar(180),
  cpf_cnpj varchar(20),
  telefone varchar(30),
  email varchar(254),
  endereco varchar(255),
  cidade varchar(120),
  logo_key varchar(500),
  cor_primaria varchar(20) NOT NULL DEFAULT '#f59e0b',
  cor_secundaria varchar(20) NOT NULL DEFAULT '#101827',
  ativa boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE usuarios (
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
CREATE UNIQUE INDEX uq_usuarios_email_ci ON usuarios (lower(email));

CREATE TABLE empresa_usuarios (
  id uuid PRIMARY KEY,
  empresa_id uuid NOT NULL REFERENCES empresas(id),
  usuario_id uuid NOT NULL REFERENCES usuarios(id),
  perfil varchar(20) NOT NULL CHECK (perfil IN ('OWNER','ADMIN','VENDEDOR')),
  ativo boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_empresa_usuario UNIQUE (empresa_id, usuario_id),
  CONSTRAINT uq_usuario_contexto_v1 UNIQUE (usuario_id)
);

CREATE TABLE refresh_tokens (
  id uuid PRIMARY KEY,
  usuario_id uuid NOT NULL REFERENCES usuarios(id),
  token_hash varchar(64) NOT NULL UNIQUE,
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz,
  replaced_by_id uuid REFERENCES refresh_tokens(id),
  user_agent varchar(500),
  ip_address varchar(64),
  created_at timestamptz NOT NULL DEFAULT now(),
  last_used_at timestamptz
);
CREATE INDEX idx_refresh_tokens_usuario_active
  ON refresh_tokens(usuario_id, expires_at) WHERE revoked_at IS NULL;

CREATE TABLE password_reset_tokens (
  id uuid PRIMARY KEY,
  usuario_id uuid NOT NULL REFERENCES usuarios(id),
  token_hash varchar(64) NOT NULL UNIQUE,
  expires_at timestamptz NOT NULL,
  used_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE convites_empresa (
  id uuid PRIMARY KEY,
  empresa_id uuid NOT NULL REFERENCES empresas(id),
  owner_email varchar(254) NOT NULL,
  token_hash varchar(64) NOT NULL UNIQUE,
  expires_at timestamptz NOT NULL,
  used_at timestamptz,
  cancelled_at timestamptz,
  created_by uuid NOT NULL REFERENCES usuarios(id),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE convites_usuario (
  id uuid PRIMARY KEY,
  empresa_id uuid NOT NULL REFERENCES empresas(id),
  email varchar(254) NOT NULL,
  perfil varchar(20) NOT NULL CHECK (perfil IN ('ADMIN','VENDEDOR')),
  token_hash varchar(64) NOT NULL UNIQUE,
  expires_at timestamptz NOT NULL,
  used_at timestamptz,
  cancelled_at timestamptz,
  created_by uuid NOT NULL REFERENCES usuarios(id),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE auditoria (
  id uuid PRIMARY KEY,
  empresa_id uuid REFERENCES empresas(id),
  usuario_id uuid REFERENCES usuarios(id),
  acao varchar(80) NOT NULL,
  entidade_tipo varchar(80),
  entidade_id uuid,
  motivo varchar(500),
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_convites_empresa_pending
  ON convites_empresa(empresa_id, expires_at) WHERE used_at IS NULL AND cancelled_at IS NULL;
CREATE INDEX idx_convites_usuario_pending
  ON convites_usuario(empresa_id, expires_at) WHERE used_at IS NULL AND cancelled_at IS NULL;
CREATE INDEX idx_auditoria_empresa_created
  ON auditoria(empresa_id, created_at DESC);
