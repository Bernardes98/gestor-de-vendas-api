ALTER TABLE public.produtos ADD COLUMN IF NOT EXISTS deleted_at timestamptz;
CREATE INDEX IF NOT EXISTS idx_produtos_empresa_deleted_at ON public.produtos (empresa_id, deleted_at);
