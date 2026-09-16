# Etapa 2 — Catálogo, Clientes, Preços e R2

## Objetivo
Adicionar ao backend próprio os módulos multiempresa de clientes, categorias, produtos, preços personalizados por cliente e mídia em Cloudflare R2, sem expor custo/margem a VENDEDOR.

## Regras
- `empresa_id` sempre vem do contexto autenticado; nunca do payload.
- OWNER e ADMIN podem criar/editar/inativar clientes, categorias e produtos.
- VENDEDOR pode apenas consultar dados ativos necessários à venda.
- Produtos e clientes são inativados por `DELETE`; não há exclusão física.
- Categoria só pode ser excluída se não houver produto vinculado.
- Um produto possui no máximo uma categoria.
- `controlar_estoque` é `false` por padrão.
- Dinheiro usa `BigDecimal`/`numeric(14,2)`.
- Preço personalizado é único por `(empresa, cliente, produto)`.
- GET de preço retorna o preço efetivo: override do cliente quando existir, senão preço padrão do produto.
- Respostas para VENDEDOR omitem `costPrice` e `marginPercent` por completo.
- Fotos e logo armazenam somente object key/metadados no PostgreSQL; bytes vão ao R2.
- Upload aceita JPEG/PNG/WebP, máximo 5 MiB.
- Chaves: `empresas/{empresaId}/produtos/{produtoId}/{uuid}.{ext}` e `empresas/{empresaId}/logo/{uuid}.{ext}`.

## Endpoints
- `GET/POST /api/clients`
- `GET/PUT/DELETE /api/clients/{id}`
- `GET/POST /api/categories`
- `PUT/DELETE /api/categories/{id}`
- `PATCH /api/categories/reorder`
- `GET/POST /api/products`
- `GET/PUT/DELETE /api/products/{id}`
- `POST /api/products/{id}/photos`
- `DELETE /api/products/{productId}/photos/{photoId}`
- `GET/PUT/DELETE /api/clients/{clientId}/product-prices/{productId}`
- `POST/DELETE /api/company/logo`

## Persistência
A migration `V2__catalog_clients_products.sql` cria `clientes`, `categorias_produtos`, `produtos`, `produto_fotos` e `cliente_produto_preco`, com FKs compostas que incluem `empresa_id` para impedir referências cruzadas entre tenants no banco.

## Testes
Cobrir schema V2, CRUD e soft-delete, isolamento entre empresas, permissões, ocultação de custo/margem, categoria vinculada, preço efetivo e validação/chave de mídia.
