# Gestor de Vendas — Supabase Removal Roadmap

**Spec:** `docs/superpowers/specs/2026-09-16-backend-proprio-migracao-supabase-design.md`

A especificação aprovada cobre vários subsistemas independentes. Para manter entregas revisáveis e testáveis, a implementação será dividida em seis planos sequenciais. Cada plano deve terminar com software funcional e testes automatizados antes do próximo começar.

1. **Backend foundation, auth, tenancy and user management**  
   Arquivo: `2026-09-16-backend-foundation-auth-tenancy.md`  
   Entrega: repositório `gestor-de-vendas-api`, PostgreSQL/Flyway, autenticação própria, refresh token, redefinição via Brevo, empresas, vínculos, convites, PLATFORM_ADMIN, OWNER/ADMIN/VENDEDOR, bloqueios e isolamento multiempresa.

2. **Clients, products, categories and R2**  
   Entrega: CRUD/inativação de clientes e produtos, preço por cliente, categorias ordenadas, fotos/logos em Cloudflare R2, respostas específicas para VENDEDOR sem custo/lucro e testes de tenant.

3. **Purchases and inventory ledger**  
   Entrega: compras, itens, movimentos de estoque, ajustes, produtos sem controle de estoque, edição/remoção transacional de compras e auditoria.

4. **Sales, receivables, reports and transactional cancellation**  
   Entrega: vendas, itens, recebimentos, recebíveis manuais, pagamentos parciais, edição/cancelamento com reversão, faturamento/lucro, auditoria e testes de consistência financeira.

5. **Public orders and Vue REST migration**  
   Entrega: links públicos preserváveis, catálogo público, pedidos/conversão/recusa, camada `src/api`, migração gradual de stores/views para REST, nova autenticação no Vue e remoção funcional das chamadas Supabase.

6. **Data migration, cutover, VPS operations and Supabase shutdown**  
   Entrega: export/import idempotente, migração de imagens para R2, validação por empresa, ensaio, janela de manutenção, Docker Compose/Caddy de produção, CI/CD, backup diário criptografado para R2, teste de restore, 30 dias de contingência e desligamento final do Supabase.

## Regra de progressão

Não iniciar o plano seguinte com testes vermelhos no plano atual. Mudanças de contrato entre planos devem atualizar este roadmap, o plano afetado e, se alterarem uma decisão aprovada, a especificação principal.
