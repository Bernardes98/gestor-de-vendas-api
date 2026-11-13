-- NULL = historical item requiring audit; never infer FALSE from absence of a movement.
ALTER TABLE public.venda_itens ADD COLUMN estoque_movimentado boolean;
ALTER TABLE public.compra_itens ADD COLUMN estoque_movimentado boolean;

-- Evidence of a movement is sufficient to mark an item as stock-tracked.
UPDATE public.venda_itens vi SET estoque_movimentado = TRUE
WHERE EXISTS (
 SELECT 1 FROM api_internal.movimentacoes_estoque m
 WHERE m.empresa_id = vi.empresa_id AND m.produto_id = vi.produto_id
   AND m.referencia_tipo = 'VENDA' AND m.referencia_id = vi.venda_id
   AND m.tipo IN ('VENDA', 'VENDA_REVERSAO')
);
UPDATE public.compra_itens ci SET estoque_movimentado = TRUE
WHERE EXISTS (
 SELECT 1 FROM api_internal.movimentacoes_estoque m
 WHERE m.empresa_id = ci.empresa_id AND m.produto_id = ci.produto_id
   AND m.referencia_tipo = 'COMPRA' AND m.referencia_id = ci.compra_id
   AND m.tipo IN ('COMPRA', 'COMPRA_REVERSAO')
);
-- Historical NULLs must be classified using reliable evidence before editing/cancelling.
-- New items are always written with explicit TRUE/FALSE by the application.
