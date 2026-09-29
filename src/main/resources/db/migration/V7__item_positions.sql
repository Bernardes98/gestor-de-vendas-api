ALTER TABLE public.pedido_cliente_itens ADD COLUMN posicao integer;
WITH ranked AS (
    SELECT id, row_number() OVER (PARTITION BY pedido_id ORDER BY created_at, id) - 1 AS ordinal
    FROM public.pedido_cliente_itens
)
UPDATE public.pedido_cliente_itens item SET posicao = ranked.ordinal
FROM ranked WHERE item.id = ranked.id;
ALTER TABLE public.pedido_cliente_itens ALTER COLUMN posicao SET NOT NULL;
ALTER TABLE public.pedido_cliente_itens ADD CONSTRAINT pedido_cliente_itens_posicao_check CHECK (posicao >= 0);
CREATE UNIQUE INDEX uq_pedido_cliente_itens_posicao ON public.pedido_cliente_itens(pedido_id, posicao);

ALTER TABLE public.venda_itens ADD COLUMN posicao integer;
WITH ranked AS (
    SELECT id, row_number() OVER (PARTITION BY venda_id ORDER BY id) - 1 AS ordinal
    FROM public.venda_itens
)
UPDATE public.venda_itens item SET posicao = ranked.ordinal
FROM ranked WHERE item.id = ranked.id;
ALTER TABLE public.venda_itens ALTER COLUMN posicao SET NOT NULL;
ALTER TABLE public.venda_itens ADD CONSTRAINT venda_itens_posicao_check CHECK (posicao >= 0);
CREATE UNIQUE INDEX uq_venda_itens_posicao ON public.venda_itens(venda_id, posicao);
