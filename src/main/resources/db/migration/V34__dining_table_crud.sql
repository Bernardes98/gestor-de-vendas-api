-- Nomes por empresa, mantendo números imutáveis utilizados pelas comandas existentes.
ALTER TABLE public.dining_tables
    ADD COLUMN label VARCHAR(80),
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE public.dining_tables SET label = CASE number
    WHEN 3 THEN 'MESA 3 - PEDRA'
    WHEN 6 THEN 'MESA 6 - RUA'
    WHEN 7 THEN 'MESA 7 - RUA'
    WHEN 8 THEN 'MESA 8 - RUA'
    WHEN 9 THEN 'MESA 9 - RUA'
    ELSE 'MESA ' || number
END;

ALTER TABLE public.dining_tables ALTER COLUMN label SET NOT NULL;

-- Retirar uma mesa da operação não remove o histórico de pedidos nem libera seu número para reutilização.
CREATE UNIQUE INDEX uq_dining_tables_active_label
    ON public.dining_tables (company_id, lower(trim(label))) WHERE active;

-- Guarda o nome da mesa no momento do pedido para que a impressão histórica não mude ao renomeá-la.
ALTER TABLE public.snack_orders ADD COLUMN table_label VARCHAR(80);
UPDATE public.snack_orders o
SET table_label = t.label
FROM public.dining_tables t
WHERE o.service_type = 'TABLE'
  AND o.table_number = t.number
  AND o.company_id = t.company_id;
