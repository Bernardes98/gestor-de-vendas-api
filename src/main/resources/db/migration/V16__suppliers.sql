CREATE TABLE public.fornecedores (
    id uuid PRIMARY KEY,
    empresa_id uuid NOT NULL REFERENCES public.empresas(id) ON DELETE CASCADE,
    nome varchar(150) NOT NULL,
    ordem integer NOT NULL CHECK (ordem > 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_fornecedor_empresa_nome UNIQUE (empresa_id, nome)
);
CREATE INDEX idx_fornecedores_empresa_ordem ON public.fornecedores(empresa_id, ordem, nome);

INSERT INTO public.fornecedores (id, empresa_id, nome, ordem)
SELECT gen_random_uuid(), empresa_id, fornecedor,
       row_number() OVER (PARTITION BY empresa_id ORDER BY fornecedor)
FROM (SELECT DISTINCT empresa_id, trim(fornecedor) fornecedor FROM public.compras WHERE fornecedor IS NOT NULL AND trim(fornecedor) <> '') x;
