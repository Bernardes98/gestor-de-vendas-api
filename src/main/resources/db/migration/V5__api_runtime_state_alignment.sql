UPDATE public.empresa_usuarios SET perfil = 'VENDEDOR' WHERE perfil = 'SELLER';
UPDATE public.empresa_usuarios SET perfil = 'ADMIN' WHERE perfil = 'MANAGER';

ALTER TABLE public.empresa_usuarios
  DROP CONSTRAINT IF EXISTS empresa_usuarios_perfil_check;
ALTER TABLE public.empresa_usuarios
  ADD CONSTRAINT empresa_usuarios_perfil_check
  CHECK (perfil IN ('OWNER','ADMIN','VENDEDOR'));

UPDATE api_internal.convites_usuario SET perfil = 'VENDEDOR' WHERE perfil = 'SELLER';
UPDATE api_internal.convites_usuario SET perfil = 'ADMIN' WHERE perfil = 'MANAGER';

ALTER TABLE api_internal.convites_usuario
  DROP CONSTRAINT IF EXISTS convites_usuario_perfil_check;
ALTER TABLE api_internal.convites_usuario
  ADD CONSTRAINT convites_usuario_perfil_check
  CHECK (perfil IN ('ADMIN','VENDEDOR'));

CREATE TABLE api_internal.venda_estados (
  venda_id uuid PRIMARY KEY REFERENCES public.vendas(id) ON DELETE CASCADE,
  status varchar(20) NOT NULL DEFAULT 'ATIVA',
  forma_pagamento varchar(20),
  created_by uuid REFERENCES api_internal.usuarios(id),
  cancel_reason varchar(500),
  cancelled_at timestamptz,
  cancelled_by uuid REFERENCES api_internal.usuarios(id),
  CONSTRAINT venda_estados_status_check CHECK (status IN ('ATIVA','CANCELADA')),
  CONSTRAINT venda_estados_forma_pagamento_check
    CHECK (forma_pagamento IS NULL OR forma_pagamento IN ('AVISTA','PRAZO'))
);
CREATE INDEX idx_venda_estados_status ON api_internal.venda_estados(status);

CREATE TABLE api_internal.pedido_locks (
  pedido_id uuid PRIMARY KEY REFERENCES public.pedidos_cliente(id) ON DELETE CASCADE,
  locked_by uuid NOT NULL REFERENCES api_internal.usuarios(id) ON DELETE CASCADE,
  locked_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_pedido_locks_locked_by_at
  ON api_internal.pedido_locks(locked_by, locked_at);
