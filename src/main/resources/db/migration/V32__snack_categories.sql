ALTER TABLE public.snacks ADD COLUMN IF NOT EXISTS category VARCHAR(80) NOT NULL DEFAULT 'Xis';
CREATE INDEX IF NOT EXISTS idx_snacks_company_category ON public.snacks (company_id, category);
