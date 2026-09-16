# Gestor de Vendas — Backend próprio e migração completa do Supabase

Data: 2026-09-16
Status: design aprovado em conversa

## 1. Objetivo

Substituir integralmente o Supabase do Gestor de Vendas por uma arquitetura sob controle da aplicação, preservando os dados e comportamentos atuais e sem quebrar links públicos já enviados aos clientes.

Ao final da migração, o frontend Vue não dependerá de Supabase Auth, Database, RPCs, Storage, Edge Functions nem `supabase-js`. Toda regra de negócio, autenticação, autorização e persistência passará pelo backend Spring Boot.

A migração deve priorizar segurança, baixo custo operacional, isolamento entre empresas, preservação de histórico e reversibilidade durante a janela de transição.

## 2. Arquitetura alvo

### 2.1 Componentes

- Frontend: Vue 3 + TypeScript + Vite, mantido no Cloudflare.
- Backend: Java 21 + Spring Boot 3.
- Segurança: Spring Security.
- Autenticação: JWT de curta duração + refresh token seguro.
- Banco: PostgreSQL único, multiempresa.
- Migrations: Flyway.
- Deploy backend: Docker Compose em VPS.
- Reverse proxy/TLS: Caddy.
- Imagens: Cloudflare R2.
- Backups: Cloudflare R2, criptografados.
- E-mail transacional: Brevo Free.
- Repositórios Git separados:
  - `gestor-de-vendas` para o frontend.
  - `gestor-de-vendas-api` para o backend.

### 2.2 Desenho operacional

```text
Internet
  |
  v
Cloudflare
  |-- Vue frontend
  |-- R2: imagens e backups
  |
  v
api.<dominio>
  |
  v
VPS / Docker Compose
  |-- Caddy
  |-- Spring Boot
  `-- PostgreSQL
        
Spring Boot ---> Brevo
```

O frontend permanece no Cloudflare para reduzir custo e carga na VPS. A VPS hospeda somente API, banco e proxy HTTPS.

### 2.3 Estilo do backend

O backend será um monólito modular. Não serão usados microserviços na V1.

Os módulos terão responsabilidades claras, por exemplo:

- auth
- platform
- companies
- users
- clients
- products
- categories
- inventory
- purchases
- sales
- receivables
- public-orders
- files
- reports
- audit

A separação modular deverá impedir a criação de serviços monolíticos excessivamente grandes e facilitar testes independentes.

## 3. Multiempresa e isolamento

O sistema usará um único PostgreSQL. Dados de negócio serão associados a `empresa_id`.

O backend derivará a empresa do usuário autenticado e do vínculo existente em `empresa_usuarios`. O frontend não será fonte confiável de `empresa_id` e não poderá escolher livremente o tenant de uma operação.

Todas as consultas e alterações privadas deverão validar o tenant no servidor. Além da validação de aplicação, relacionamentos relevantes no banco deverão impedir associações entre registros de empresas diferentes.

Exemplos de invariantes:

- produto e categoria devem pertencer à mesma empresa;
- venda e cliente devem pertencer à mesma empresa;
- item de venda e produto devem pertencer à mesma empresa da venda;
- recebível e venda devem pertencer à mesma empresa;
- usuário só opera dentro das empresas às quais está vinculado.

## 4. Papéis e permissões

### 4.1 Administrador da plataforma

`PLATFORM_ADMIN` é um papel global separado dos papéis de empresa.

Responsabilidades:

- criar empresa;
- enviar convite inicial ao responsável;
- visualizar empresas;
- alternar empresa entre `ATIVA` e `BLOQUEADA`;
- reenviar ou cancelar convites pendentes.

Uma empresa bloqueada não é apagada. Seus usuários ficam impedidos de operar até a reativação.

### 4.2 Papéis da empresa

Papéis fixos:

- `OWNER`
- `ADMIN`
- `VENDEDOR`

Permissões aprovadas:

| Ação | OWNER | ADMIN | VENDEDOR |
| --- | --- | --- | --- |
| Realizar venda | Sim | Sim | Sim |
| Ver dados necessários para venda | Sim | Sim | Sim |
| Ver custo, lucro, margem e relatórios | Sim | Sim | Não |
| Cadastrar/editar/inativar clientes | Sim | Sim | Não |
| Cadastrar/editar/inativar produtos | Sim | Sim | Não |
| Criar/renomear/reordenar/excluir categorias | Sim | Sim | Não |
| Compras e estoque | Sim | Sim | Não |
| Prazo, abatimentos, recebimentos e quitação | Sim | Sim | Não |
| Editar/cancelar venda | Sim | Sim | Não |
| Configurações da empresa | Sim | Sim | Não |
| Convidar/criar usuários | Sim | Sim | Não |
| Bloquear/desbloquear usuários | Sim | Sim | Não |
| Alterar papel de usuário | Sim | Não | Não |
| Remover usuário | Sim | Não | Não |

Quando um ADMIN convida/cria um usuário, o backend cria obrigatoriamente como `VENDEDOR`, independentemente do que o frontend envie. Apenas o OWNER pode alterar papéis.

O VENDEDOR não deve receber custo, lucro ou margem nem mesmo nas respostas da API usadas para venda.

Na migração dos papéis existentes, `OWNER` permanece `OWNER`, `ADMIN` permanece `ADMIN`, `SELLER` vira `VENDEDOR` e `MANAGER` vira `ADMIN`, preservando o nível operacional mais próximo do sistema atual.

## 5. Convites de empresa e usuários

Não haverá cadastro público de empresas.

### 5.1 Convite inicial de empresa

Fluxo:

1. `PLATFORM_ADMIN` informa nome da empresa e e-mail do responsável.
2. Backend cria convite com token único e validade limitada.
3. Brevo envia o e-mail.
4. Responsável abre o link e conclui o cadastro.
5. Usuário é criado/vinculado como `OWNER`.
6. Convite é invalidado após o uso.

O painel da plataforma deve permitir reenviar e cancelar convites e mostrar o status do convite.

### 5.2 Convite de usuário interno

OWNER e ADMIN podem convidar novos usuários.

- convite criado por ADMIN sempre resulta em `VENDEDOR`;
- convite criado por OWNER pode definir `VENDEDOR` ou `ADMIN`;
- apenas OWNER pode promover/rebaixar usuários depois do cadastro, inclusive conceder ou retirar `OWNER`;
- apenas OWNER pode remover usuários;
- remover usuário significa encerrar seu vínculo ativo com a empresa e revogar suas sessões, preservando referências históricas e de auditoria.

## 6. Autenticação e sessões

### 6.1 Login

Fluxo aprovado:

- usuário envia e-mail e senha;
- Spring Security valida credenciais e status;
- backend emite access token JWT de aproximadamente 15 minutos;
- backend emite refresh token de aproximadamente 30 dias;
- refresh token é enviado em cookie `HttpOnly`, `Secure` e com política `SameSite` apropriada;
- o banco guarda somente uma representação segura do refresh token e metadados da sessão.

O access token será usado nas requisições da API. O refresh token não ficará acessível ao JavaScript do navegador.

### 6.2 Revogação

Sessões poderão ser revogadas em:

- logout;
- bloqueio do usuário;
- bloqueio da empresa;
- troca de senha;
- ação administrativa de encerramento de sessão.

O backend deve verificar status de usuário e empresa mesmo quando o access token ainda não expirou.

### 6.3 Migração de senhas

Senhas do Supabase não serão reaproveitadas.

Usuários migrados terão:

- mesmo e-mail;
- mesmos vínculos e papéis normalizados;
- status migrado;
- `must_reset_password = true`;
- nenhum hash de senha importado do Supabase.

No primeiro acesso, o usuário deverá redefinir a senha pelo fluxo de recuperação.

### 6.4 Redefinição de senha

- token aleatório de uso único;
- validade de 30 minutos;
- apenas o hash do token fica persistido;
- envio pelo Brevo;
- resposta do endpoint não revela se o e-mail existe;
- ao redefinir a senha, o token é invalidado e sessões anteriores são revogadas.

Senha será armazenada com BCrypt pelo Spring Security, usando fator de custo configurável e adequado ao ambiente de produção. O código não armazenará nem registrará senha em texto puro.

## 7. Modelo de dados

Todos os IDs principais usarão UUID. IDs existentes serão preservados na migração sempre que possível.

Valores monetários usarão PostgreSQL `NUMERIC` e Java `BigDecimal`, nunca `float` ou `double`.

Estruturas principais:

### Plataforma e segurança

- `empresas`
- `usuarios`
- `empresa_usuarios`
- `convites_empresa`
- `convites_usuario`
- `refresh_tokens`
- `password_reset_tokens`
- `auditoria`

### Clientes e catálogo

- `clientes`
- `cliente_produto_preco`
- `categorias_produtos`
- `produtos`
- `produto_fotos`

### Estoque e compras

- `compras`
- `compra_itens`
- `movimentacoes_estoque`

### Vendas e recebíveis

- `vendas`
- `venda_itens`
- `venda_pagamentos`
- `recebiveis_manuais`
- `recebivel_manual_pagamentos`

### Pedido público

- `pedidos_cliente`
- `pedido_cliente_itens`
- estrutura necessária para produtos ocultos por cliente e estado de conversão/recusa, conforme comportamento atual.

O schema final deverá reproduzir todos os dados realmente existentes no Supabase atual, mesmo que a nomenclatura final seja ajustada no backend novo.

## 8. Categorias de produto

Cada produto pertence a uma categoria.

Regras:

- categoria pertence a uma empresa;
- OWNER e ADMIN podem criar, renomear, reordenar e excluir;
- ordem é explícita por empresa;
- categorias aparecem uma abaixo da outra na venda e no pedido público;
- não usar abas como navegação principal de categoria;
- excluir categoria com produtos vinculados é proibido;
- os produtos devem ser movidos antes da exclusão;
- os produtos existentes na migração devem manter a categorização resultante da migration atual; quando ainda não categorizados, a regra aprovada anteriormente é agrupá-los em `Cigarros` por empresa.

## 9. Produtos, clientes e exclusão lógica

Clientes e produtos usados em histórico não serão apagados fisicamente.

A ação de excluir será implementada como inativação quando houver necessidade de preservar histórico.

Consequências:

- item inativo deixa de aparecer para novas operações normais;
- histórico permanece legível;
- relatórios antigos não quebram;
- relacionamentos de venda não são perdidos.

Para entidades ainda sem histórico, o backend pode manter uma política consistente de inativação em vez de hard delete para simplificar auditoria e comportamento.

## 10. Estoque e compras

Além do saldo atual, o sistema terá `movimentacoes_estoque` para explicar cada alteração.

Tipos de movimento incluem pelo menos:

- entrada por compra;
- saída por venda;
- reversão por cancelamento;
- ajuste manual;
- correção decorrente de edição de venda/compra.

Produtos com `controlar_estoque = false`:

- podem ser vendidos sem quantidade disponível;
- não bloqueiam venda por saldo;
- não precisam gerar movimentação de estoque.

Compra, edição e remoção devem atualizar estoque de forma transacional e auditável.

## 11. Vendas

### 11.1 Criação

Criar venda deve ocorrer em uma única transação de banco envolvendo:

- venda;
- itens;
- pagamentos;
- movimentações de estoque;
- eventual recebível a prazo;
- auditoria.

Se qualquer etapa falhar, toda a transação deve ser revertida.

### 11.2 Edição

Ao editar uma venda, o backend compara o estado anterior e o novo e aplica apenas a diferença necessária.

Exemplos:

- quantidade 2 -> 5: desconta mais 3;
- quantidade 5 -> 2: devolve 3;
- Prazo -> À vista: ajusta/remove recebível;
- À vista -> Prazo: cria recebível correspondente;
- troca de produto: reverte o produto antigo e aplica o novo.

### 11.3 Cancelamento

Venda finalizada não será apagada fisicamente.

A ação de excluir/cancelar terá:

- motivo obrigatório;
- status `CANCELADA`;
- usuário responsável;
- data/hora;
- reversão de estoque;
- remoção/ajuste do saldo em Prazo;
- reversão dos valores relacionados;
- exclusão da venda cancelada dos totais de faturamento/lucro válidos;
- auditoria completa.

Tudo deve ocorrer em uma única transação.

## 12. Prazo e recebimentos

OWNER e ADMIN podem:

- registrar pagamentos;
- registrar abatimentos;
- quitar valores em aberto;
- consultar histórico;
- lidar com recebíveis originados de venda e recebíveis manuais.

Pagamentos parciais devem preservar histórico dos abatimentos.

Alteração/cancelamento de venda deve recalcular ou remover o recebível relacionado de forma consistente.

## 13. Auditoria

Eventos sensíveis devem registrar, quando aplicável:

- empresa;
- usuário;
- ação;
- entidade afetada;
- ID da entidade;
- data/hora;
- motivo;
- metadados seguros relevantes.

Eventos mínimos:

- criação/edição/cancelamento de venda;
- alterações críticas de estoque;
- pagamentos e abatimentos;
- convite de usuário;
- alteração de papel;
- bloqueio/desbloqueio;
- remoção de usuário;
- redefinição de senha;
- ações administrativas de plataforma.

Logs e auditoria nunca devem armazenar senha, JWT, refresh token ou token bruto de redefinição/convite.

## 14. Imagens e Cloudflare R2

R2 armazenará:

- logos das empresas;
- fotos de produtos;
- backups criptografados.

O PostgreSQL guarda somente metadados e a chave do objeto.

Exemplo de chave:

```text
empresas/{empresaId}/produtos/{produtoId}/{uuid}.webp
```

O backend controla upload e exclusão. Deve validar:

- MIME type permitido;
- tamanho máximo;
- dimensões quando aplicável;
- empresa dona do objeto.

O fluxo deverá otimizar imagens para evitar arquivos excessivamente grandes. A URL pública ou assinada deve ser definida de forma consistente para o caso de uso.

## 15. Links públicos de pedido

Links existentes precisam continuar funcionando após a migração.

Regras:

- preservar UUID/token atual sempre que possível;
- migrar tokens e relacionamentos de cliente;
- manter o comportamento de catálogo público;
- manter produtos ocultos por cliente;
- manter criação de pedido público;
- manter estados atuais de pedido, visualização, conversão, recusa e exclusão quando aplicáveis;
- novos tokens devem ser criptograficamente aleatórios;
- regenerar link invalida o token anterior conforme a regra atual.

O endpoint público não deve revelar dados privados da empresa além do necessário para o pedido.

## 16. API REST

O frontend não acessará PostgreSQL diretamente.

A API terá convenções previsíveis. Exemplos ilustrativos:

```text
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/forgot-password
POST /api/auth/reset-password

GET  /api/clients
POST /api/clients

GET  /api/products
POST /api/products

POST /api/sales
PUT  /api/sales/{id}
POST /api/sales/{id}/cancel

POST /api/receivables/{id}/payments

GET  /api/public/orders/{token}
POST /api/public/orders/{token}

GET  /api/platform/companies
POST /api/platform/companies/{id}/block
POST /api/platform/companies/{id}/activate
```

A nomenclatura final pode mudar no plano de implementação, mas as responsabilidades e regras aprovadas não.

## 17. Camada de API no frontend

O Vue deixará de chamar Supabase diretamente.

Será criada uma camada dedicada, por exemplo:

```text
src/api/
  http.ts
  authApi.ts
  clientsApi.ts
  productsApi.ts
  salesApi.ts
  purchasesApi.ts
  receivablesApi.ts
  ordersApi.ts
  usersApi.ts
  platformApi.ts
```

Views e stores devem usar essa camada e não detalhes de transporte.

O `http.ts` centralizará:

- URL base;
- access token;
- refresh automático quando cabível;
- tratamento padronizado de 401/403;
- request ID;
- erros de rede;
- logout em sessão inválida.

Ao final da migração, devem ser removidos:

- `@supabase/supabase-js`;
- `src/lib/supabase.ts`;
- variáveis `VITE_SUPABASE_*`;
- chamadas RPC;
- Supabase Storage;
- Supabase Auth;
- Edge Functions do Supabase usadas pelo app.

## 18. Erros e consistência

A API retornará erros consistentes, por exemplo:

```json
{
  "code": "INSUFFICIENT_STOCK",
  "message": "Estoque insuficiente para este produto.",
  "requestId": "..."
}
```

O frontend recebe mensagem adequada ao usuário. Detalhes técnicos ficam apenas no servidor associados ao `requestId`.

Nunca retornar ao navegador:

- stack trace;
- SQL;
- segredo;
- token;
- detalhe interno de infraestrutura.

## 19. Migração do Supabase

### 19.1 Estratégia

A migração será gradual. O Supabase continua funcionando durante o desenvolvimento do backend novo.

Ordem de alto nível:

1. construir API e schema PostgreSQL;
2. criar migrations Flyway;
3. mapear schema e comportamento atuais do Supabase;
4. desenvolver importadores/migrações de dados;
5. migrar imagens para R2 em ambiente de teste;
6. adaptar o frontend para a nova API;
7. executar testes de regressão e isolamento;
8. realizar ensaio completo de migração;
9. abrir janela de manutenção;
10. gerar backup/export final;
11. importar dados definitivos;
12. validar contagens e somatórios;
13. apontar frontend para API nova;
14. executar smoke tests por empresa;
15. reabrir sistema.

### 19.2 Preservação

Devem ser preservados, conforme existência no sistema atual:

- empresas;
- usuários/e-mails;
- vínculos e papéis;
- clientes;
- produtos;
- categorias;
- preços específicos por cliente;
- produtos ocultos por cliente;
- estoque;
- compras;
- vendas;
- itens;
- recebíveis;
- pagamentos/abatimentos;
- links públicos;
- pedidos públicos;
- configurações;
- logos;
- fotos de produto;
- históricos relevantes.

### 19.3 Validação

A migração só pode ser aprovada se os dados baterem por empresa.

Comparações mínimas:

- quantidade de clientes;
- quantidade de produtos;
- quantidade de categorias;
- quantidade de vendas;
- quantidade de itens de venda;
- quantidade de compras;
- estoque por produto controlado;
- recebíveis em aberto;
- pagamentos/abatimentos;
- faturamento válido;
- lucro válido;
- pedidos públicos;
- arquivos esperados no R2.

Diferença relevante bloqueia o cutover.

### 19.4 Janela de manutenção

Durante o cutover final, o sistema entra em manutenção para impedir escrita concorrente.

Não será adotado dual-write entre Supabase e PostgreSQL na V1. Para o volume atual, uma janela curta de manutenção é mais simples e menos arriscada.

### 19.5 Contingência

Após a virada, o Supabase será mantido intacto por 30 dias como fonte de conferência/contingência. O sistema novo não escreverá nele.

Somente após esse período e validação operacional será considerado o desligamento definitivo.

## 20. Deploy e operação

### 20.1 Docker Compose

Serviços mínimos:

- `caddy`
- `gestor-de-vendas-api`
- `postgres`

PostgreSQL não será exposto publicamente.

Segredos devem ficar fora do Git:

- credenciais do banco;
- chave de JWT/assinatura;
- credenciais Brevo;
- credenciais R2;
- parâmetros de criptografia dos backups.

### 20.2 CI/CD

Fluxo desejado:

```text
git push main
  -> GitHub Actions
  -> testes
  -> build
  -> imagem Docker
  -> deploy na VPS
  -> Flyway
  -> health check
```

Falha em teste/build impede deploy.

Migrations Flyway devem ser compatíveis com rollback operacional via restauração/versão anterior. Migrations destrutivas exigem cuidado especial e backup confirmado.

## 21. Backups e recuperação

- backup automático diário do PostgreSQL;
- backup criptografado antes do upload;
- armazenamento no R2;
- retenção de 30 dias;
- remoção automática de backups expirados;
- teste periódico de restauração;
- documentação do procedimento de recuperação completa em uma VPS nova.

Backup só é considerado válido quando um procedimento de restauração é testado.

## 22. Ambientes

Ambientes lógicos:

- local;
- homologação;
- produção.

Homologação não exige uma segunda VPS dedicada inicialmente. Pode usar uma solução de menor custo, desde que não compartilhe dados reais sem necessidade e não coloque produção em risco.

## 23. Testes obrigatórios

### 23.1 Unitários

Cobrir pelo menos:

- cálculos financeiros;
- margem/lucro;
- regras de estoque;
- permissões;
- regras de status;
- transformação de venda em prazo/à vista;
- regras de cancelamento.

### 23.2 Integração

Usar PostgreSQL real em container durante os testes de integração.

Cenários mínimos:

```text
VENDEDOR consulta custo                   -> 403
VENDEDOR exclui produto                   -> 403
ADMIN altera papel                        -> 403
ADMIN convida usuário                     -> criado como VENDEDOR
OWNER altera papel                        -> permitido
empresa BLOQUEADA opera                   -> bloqueado
usuário da empresa A acessa empresa B     -> bloqueado

venda -2 unidades                         -> estoque -2
cancelamento da venda                     -> estoque +2
venda a prazo                             -> gera saldo
pagamento parcial                         -> reduz saldo
cancelamento da venda a prazo             -> ajusta/remove saldo
produto sem controle de estoque           -> venda permitida
cliente/produto inativado                 -> histórico preservado
```

### 23.3 Teste explícito de isolamento multiempresa

Criar pelo menos duas empresas com registros propositalmente parecidos e testar automaticamente que consultas, alterações, relatórios e IDs manipulados não atravessam o tenant.

Esse teste é bloqueador para produção.

### 23.4 Regressão frontend

Checklist antes do cutover:

- login;
- redefinição de senha;
- convite;
- clientes;
- produtos;
- categorias;
- fotos;
- compras;
- estoque;
- venda à vista;
- venda a prazo;
- recebimentos;
- abatimentos;
- edição/cancelamento de venda;
- impressão;
- comprovante WhatsApp;
- relatórios;
- pedidos públicos;
- configurações;
- usuários;
- painel da plataforma.

## 24. Segurança operacional

Medidas mínimas:

- HTTPS obrigatório;
- PostgreSQL privado;
- SSH por chave;
- rate limit em login e recuperação de senha;
- validação de upload;
- tokens de uso único;
- refresh token revogável;
- cookies seguros;
- checagem de empresa ativa e usuário ativo no backend;
- nenhuma confiança em `empresa_id` enviado pelo cliente;
- nenhuma exposição de custo/lucro para VENDEDOR na resposta da API;
- logs sem segredos;
- auditoria de ações críticas.

## 25. Decisões explicitamente fora da V1

Para manter custo e complexidade baixos, não fazem parte desta primeira versão:

- microserviços;
- Kubernetes;
- cadastro público de empresas;
- cobrança automática de mensalidade;
- integração com gateway de pagamento da assinatura;
- papéis/permissões customizáveis por empresa;
- dual-write Supabase/PostgreSQL;
- banco separado por cliente.

A mensalidade será controlada externamente. No sistema haverá apenas o controle manual `ATIVA/BLOQUEADA` da empresa.

## 26. Critérios de conclusão da migração

O Supabase pode ser considerado removido do sistema somente quando:

1. frontend não possui dependência funcional de Supabase;
2. autenticação nova funciona com redefinição obrigatória para usuários migrados;
3. todos os dados relevantes foram migrados e validados;
4. imagens foram copiadas para R2;
5. links públicos existentes continuam funcionando;
6. permissões OWNER/ADMIN/VENDEDOR estão cobertas por testes;
7. isolamento multiempresa está coberto por teste de integração;
8. venda, estoque, prazo e cancelamento passam nos testes de consistência;
9. backup diário e restauração foram testados;
10. produção operou satisfatoriamente durante o período de contingência de 30 dias.

## 27. Sequenciamento recomendado

Este design deve ser implementado por etapas, nesta ordem geral:

1. criar repositório `gestor-de-vendas-api` e fundação Spring Boot;
2. definir schema base e Flyway;
3. implementar autenticação/sessões/convites;
4. implementar multiempresa e autorização;
5. migrar clientes/produtos/categorias;
6. implementar estoque/compras;
7. implementar vendas/recebíveis/auditoria;
8. implementar pedidos públicos;
9. integrar R2 e Brevo;
10. criar camada `src/api` no Vue;
11. migrar telas gradualmente para REST;
12. construir scripts de migração de dados e arquivos;
13. executar ensaio de migração;
14. realizar cutover final;
15. manter Supabase somente como contingência por 30 dias;
16. desligar Supabase após validação final.

O plano de implementação detalhado será escrito separadamente após aprovação deste documento.
