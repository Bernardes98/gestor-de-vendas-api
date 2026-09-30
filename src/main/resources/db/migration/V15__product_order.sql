ALTER TABLE public.produtos ADD COLUMN ordem integer;

WITH ranked AS (
    SELECT id, row_number() OVER (PARTITION BY empresa_id ORDER BY nome, id) AS position
    FROM public.produtos
)
UPDATE public.produtos p
SET ordem = ranked.position
FROM ranked
WHERE ranked.id = p.id;

ALTER TABLE public.produtos ALTER COLUMN ordem SET NOT NULL;
ALTER TABLE public.produtos ADD CONSTRAINT produtos_ordem_check CHECK (ordem > 0);
CREATE INDEX idx_produtos_empresa_ordem ON public.produtos(empresa_id, ordem, nome);
