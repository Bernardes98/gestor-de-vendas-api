CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE SCHEMA IF NOT EXISTS api_internal;

CREATE TABLE public.empresas (
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

CREATE TABLE api_internal.usuarios (
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
CREATE UNIQUE INDEX uq_api_internal_usuarios_email_ci
    ON api_internal.usuarios (lower(email));

CREATE TABLE public.empresa_usuarios (
                                         id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                         empresa_id uuid NOT NULL REFERENCES public.empresas(id) ON DELETE CASCADE,
                                         user_id uuid NOT NULL REFERENCES api_internal.usuarios(id) ON DELETE CASCADE,
                                         perfil text NOT NULL DEFAULT 'SELLER',
                                         ativo boolean NOT NULL DEFAULT true,
                                         created_at timestamptz NOT NULL DEFAULT now(),
                                         CONSTRAINT empresa_usuarios_perfil_check
                                             CHECK (perfil IN ('OWNER','ADMIN','MANAGER','SELLER')),
                                         CONSTRAINT empresa_usuarios_unique UNIQUE (empresa_id, user_id)
);
CREATE INDEX idx_empresa_usuarios_empresa ON public.empresa_usuarios(empresa_id);
CREATE INDEX idx_empresa_usuarios_user ON public.empresa_usuarios(user_id);

CREATE TABLE api_internal.refresh_tokens (
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
CREATE INDEX idx_refresh_tokens_usuario_active
    ON api_internal.refresh_tokens(usuario_id, expires_at)
    WHERE revoked_at IS NULL;

CREATE TABLE api_internal.password_reset_tokens (
                                                    id uuid PRIMARY KEY,
                                                    usuario_id uuid NOT NULL REFERENCES api_internal.usuarios(id),
                                                    token_hash varchar(64) NOT NULL UNIQUE,
                                                    expires_at timestamptz NOT NULL,
                                                    used_at timestamptz,
                                                    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE api_internal.convites_empresa (
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
CREATE INDEX idx_convites_empresa_pending
    ON api_internal.convites_empresa(empresa_id, expires_at)
    WHERE used_at IS NULL AND cancelled_at IS NULL;

CREATE TABLE api_internal.convites_usuario (
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
                                               CONSTRAINT convites_usuario_perfil_check
                                                   CHECK (perfil IN ('ADMIN','MANAGER','SELLER'))
);
CREATE INDEX idx_convites_usuario_pending
    ON api_internal.convites_usuario(empresa_id, expires_at)
    WHERE used_at IS NULL AND cancelled_at IS NULL;

CREATE TABLE api_internal.auditoria (
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
CREATE INDEX idx_auditoria_empresa_created
    ON api_internal.auditoria(empresa_id, created_at DESC);