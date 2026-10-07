ALTER TABLE produtos ADD COLUMN lucro_valor numeric(14,2) NOT NULL DEFAULT 0;
UPDATE produtos SET lucro_valor = GREATEST(preco_padrao - custo, 0);
